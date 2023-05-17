#!/usr/bin/env bash

./gradlew :app:connectedCheck --stacktrace -Pandroid.testInstrumentationRunnerArguments.class=com.frontegg.demo.LoginWithPasswordTest
./gradlew :app:connectedCheck --stacktrace -Pandroid.testInstrumentationRunnerArguments.class=com.frontegg.demo.LoginWithSAMLTest