/**
 * Copyright (c) 2017-2018 The Semux Developers
 *
 * Distributed under the MIT software license, see the accompanying file
 * LICENSE or https://opensource.org/licenses/mit-license.php
 */
package org.semux.api.response;

import org.semux.api.ApiHandlerResponse;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DoTransactionResponse extends ApiHandlerResponse {

    @JsonProperty("result")
    public final String txHash;

    public DoTransactionResponse(
            @JsonProperty("success") Boolean success,
            @JsonProperty("message") String message,
            @JsonProperty("result") String txHash) {
        super(success, message);
        this.txHash = txHash;
    }
}
