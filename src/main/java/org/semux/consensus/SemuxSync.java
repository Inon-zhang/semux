/*
 * Copyright (c) 2017 The Semux Developers
 *
 * Distributed under the MIT software license, see the accompanying file
 * LICENSE or https://opensource.org/licenses/mit-license.php
 */
package org.semux.consensus;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.semux.Config;
import org.semux.core.Account;
import org.semux.core.Block;
import org.semux.core.Blockchain;
import org.semux.core.Delegate;
import org.semux.core.TransactionExecutor;
import org.semux.core.TransactionResult;
import org.semux.core.state.AccountState;
import org.semux.core.state.DelegateState;
import org.semux.crypto.EdDSA;
import org.semux.crypto.EdDSA.Signature;
import org.semux.crypto.Hash;
import org.semux.net.Channel;
import org.semux.net.ChannelManager;
import org.semux.net.msg.Message;
import org.semux.net.msg.consensus.BlockMessage;
import org.semux.net.msg.consensus.GetBlockMessage;
import org.semux.utils.ByteArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SemuxSync {

    private static final Logger logger = LoggerFactory.getLogger(SemuxSync.class);

    private static final int MAX_BATCH_SIZE = 32;
    private static final long MAX_DOWNLOAD_TIME = 2 * 60 * 1000;

    private Blockchain chain;
    private ChannelManager channelMgr;

    private ScheduledExecutorService exec;
    private ScheduledFuture<?> download;
    private ScheduledFuture<?> process;

    // task queues
    private TreeSet<Long> toDownload = new TreeSet<>();
    private Map<Long, Long> toComplete = new HashMap<>();
    private TreeSet<Block> toProcess = new TreeSet<>();
    private long target;
    private Object lock = new Object();

    // sync state and notifier
    private boolean isRunning;
    private Object done = new Object();

    private static SemuxSync instance;

    private static ThreadFactory factory = new ThreadFactory() {
        private AtomicInteger cnt = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "sync-mgr-" + cnt.getAndIncrement());
        }
    };

    /**
     * Get the singleton instance of sync manager.
     * 
     * @return
     */
    public static synchronized SemuxSync getInstance() {
        if (instance == null) {
            instance = new SemuxSync();
        }

        return instance;
    }

    private SemuxSync() {
    }

    /**
     * Initialize the sync manager.
     * 
     * @param chain
     * @param channelMgr
     */
    public void init(Blockchain chain, ChannelManager channelMgr) {
        this.chain = chain;
        this.channelMgr = channelMgr;

        this.exec = Executors.newSingleThreadScheduledExecutor(factory);
    }

    /**
     * Start sync manager, and sync blocks in [height, targetHeight).
     * 
     * @param targetHeight
     *            the target height, exclusive
     */
    public void start(long targetHeight) {
        if (!isRunning()) {
            // [1] set up queues
            synchronized (lock) {
                toDownload.clear();
                toComplete.clear();
                toProcess.clear();

                target = targetHeight;
                for (long i = chain.getLatestBlockNumber() + 1; i < target; i++) {
                    toDownload.add(i);
                }
            }

            // [2] start tasks
            download = exec.scheduleAtFixedRate(() -> {
                download();
            }, 0, 500, TimeUnit.MILLISECONDS);
            process = exec.scheduleAtFixedRate(() -> {
                process();
            }, 0, 200, TimeUnit.MILLISECONDS);

            isRunning = true;
            logger.info("Sync manager started");
            logger.info("Height: current = {}, target = {}", chain.getLatestBlockNumber(), target);

            // [3] wait until the sync is done
            synchronized (done) {
                try {
                    done.wait();
                } catch (InterruptedException e) {
                    logger.info("Sync manager got interrupted");
                }
            }

            // [4] cancel tasks
            download.cancel(true);
            process.cancel(false);
            isRunning = false;
            logger.info("Sync manager stopped");
        }
    }

    /**
     * Stop sync manager.
     */
    public void stop() {
        if (isRunning()) {
            synchronized (done) {
                done.notifyAll();
            }
        }
    }

    /**
     * Check if the sync manager is running.
     * 
     * @return
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Callback for receiving messages.
     * 
     * @param channel
     * 
     * @param channel
     *            the channel where the message comes from
     * @param msg
     *            the message
     * @return true if the message is processed, otherwise false
     */
    public boolean onMessage(Channel channel, Message msg) {
        if (!isRunning()) {
            return false;
        }

        switch (msg.getCode()) {
        case BLOCK:
            BlockMessage blockMsg = (BlockMessage) msg;
            Block block = blockMsg.getBlock();
            if (block != null) {
                synchronized (lock) {
                    toComplete.remove(block.getNumber());
                    toProcess.add(block);
                }
            }
            break;
        case BLOCK_HEADER:
            // TODO implement block header
            break;
        default:
            return false;
        }

        return true;
    }

    private void download() {
        List<Channel> channels = channelMgr.getIdleChannels();
        logger.trace("Idle peers = {}", channels.size());

        if (channels.size() > MAX_BATCH_SIZE) {
            Collections.shuffle(channels);
            channels = channels.subList(0, MAX_BATCH_SIZE);
        }

        synchronized (lock) {
            // quit if too many unfinished jobs
            if (toComplete.size() > MAX_BATCH_SIZE) {
                return;
            }

            // filter all expired tasks
            long now = System.currentTimeMillis();
            for (Long k : toComplete.keySet()) {
                Long v = toComplete.get(k);

                if (v + MAX_DOWNLOAD_TIME > now) {
                    toDownload.add(k);
                }
            }

            // sending messages to queue
            for (Channel c : channels) {
                if (toDownload.isEmpty()) {
                    break;
                }
                Long task = toDownload.first();
                logger.debug("Requesting for block #{}", task);
                c.getMessageQueue().sendMessage(new GetBlockMessage(task));

                toDownload.remove(task);
                toComplete.put(task, System.currentTimeMillis());
            }
        }
    }

    private void process() {
        long latest = chain.getLatestBlockNumber();
        if (latest + 1 == target) {
            stop();
        }

        Block nextBlock = null;
        synchronized (lock) {
            Iterator<Block> iter = toProcess.iterator();
            while (iter.hasNext()) {
                Block b = iter.next();

                if (b.getNumber() <= latest) {
                    iter.remove();
                } else if (b.getNumber() == latest + 1) {
                    iter.remove();
                    nextBlock = b;
                    break;
                } else {
                    toProcess.add(b);
                    break;
                }
            }
        }

        if (nextBlock != null) {
            logger.info(nextBlock.toString());

            if (validateAndCommit(nextBlock)) {
                // [5] add block to chain
                chain.addBlock(nextBlock);

                // [6] flush state changes to disk
                chain.getAccountState().commit();
                chain.getDeleteState().commit();

                synchronized (lock) {
                    toDownload.remove(nextBlock.getNumber());
                    toComplete.remove(nextBlock.getNumber());
                }
            } else {
                synchronized (lock) {
                    toDownload.add(nextBlock.getNumber());
                }
            }
        }
    }

    /**
     * Validate a block, and commit state change if valid.
     * 
     * @param block
     * @return
     */
    private boolean validateAndCommit(Block block) {
        // [0] check block number and prevHash
        Block latest = chain.getLatestBlock();
        if (block.getNumber() != latest.getNumber() + 1 || !Arrays.equals(block.getPrevHash(), latest.getHash())) {
            return false;
        }

        AccountState as = chain.getAccountState().track();
        DelegateState ds = chain.getDeleteState().track();

        // [1] execute all transactions
        TransactionExecutor exec = TransactionExecutor.getInstance();
        List<TransactionResult> results = exec.execute(block.getTransactions(), as, ds, false);
        for (int i = 0; i < results.size(); i++) {
            if (!results.get(i).isSuccess()) {
                return false;
            }
        }

        // [2] check validator votes
        List<Delegate> validators = ds.getValidators();
        int twoThirds = (int) Math.ceil(validators.size() * 2.0 / 3.0);
        if (block.getVotes().size() < twoThirds) {
            return false;
        }

        Set<ByteArray> set = new HashSet<>();
        for (Delegate d : validators) {
            set.add(ByteArray.of(d.getAddress()));
        }
        Vote vote = new Vote(VoteType.PRECOMMIT, Vote.VALUE_APPROVE, block.getHash(), block.getNumber(),
                block.getView());
        byte[] encoded = vote.getEncoded();
        for (Signature sig : block.getVotes()) {
            ByteArray addr = ByteArray.of(Hash.h160(sig.getPublicKey()));

            if (!set.contains(addr) || !EdDSA.verify(encoded, sig)) {
                return false;
            }
        }

        // [3] apply block reward
        long reward = Config.getBlockReward(block.getNumber());
        if (reward > 0) {
            Account acc = as.getAccount(block.getCoinbase());
            acc.setBalance(acc.getBalance() + reward);
        }

        // [4] commit the updates
        as.commit();
        ds.commit();
        return true;
    }
}
