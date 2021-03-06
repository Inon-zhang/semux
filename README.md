# Welcome to Semux!

[![Build Status](https://travis-ci.org/semuxproject/semux.svg?branch=develop)](https://travis-ci.org/semuxproject/semux)
[![Build status](https://ci.appveyor.com/api/projects/status/y0kgfqch4u79er1k?svg=true)](https://ci.appveyor.com/project/semux/semux)
[![Coverage Status](https://coveralls.io/repos/github/semuxproject/semux/badge.svg?branch=develop)](https://coveralls.io/github/semuxproject/semux)
[![Scrutinizer-CI Status](https://scrutinizer-ci.com/g/semuxproject/semux/badges/quality-score.png?b=develop)](https://scrutinizer-ci.com/g/semuxproject/semux/)
[![Crowdin](https://d322cqt584bo4o.cloudfront.net/semux/localized.svg)](https://crowdin.com/project/semux)


## What is Semux

Semux is an experimental high-performance blockchain platform that powers decentralized application. It's written purely in Java and powered by Semux BFT consensus algorithm.

More info can be found at our [Wiki page](https://github.com/semuxproject/semux/wiki).


## Get started

1. Download and install [Java SE Runtime Environment 9](http://www.oracle.com/technetwork/java/javase/downloads/jre9-downloads-3848532.html)
2. *(Windows user) Download and install [Microsoft Visual C++ 2010 Redistributable Package](https://www.microsoft.com/en-us/download/details.aspx?id=14632)*
3. Download the [Latest Release](https://github.com/semuxproject/semux/releases) and unpack it to a desired directory.
4. Run ``semux.exe`` if you're on Windows; run ``./semux-gui.sh`` or ``./semux-cli.sh`` if you're on Linux or macOS.


## Build from source

Prerequisites:
```
Java SE Development Kit 9
Apache Maven 3.5.2
```

Build:
```
git clone https://github.com/semuxproject/semux
cd semux
mvn install -DskipTests
```

Run:
```
./dist/linux/semux-cli.sh
```

## Contribute

Anyone is welcome to contribute to this open source project in the form of peer review, testing and patches. Please see the [contributing](./.github/contributing.md) guide for more details.

If you found a bug, please submit it to [issues](https://github.com/semuxproject/semux/issues).


## Wallet Localization

If you want to add new language, review/update existing translation or help to finish specific translations, you can join and do that by following link:
https://crowdin.com/project/semux


## License

[The MIT License](./LICENSE)
