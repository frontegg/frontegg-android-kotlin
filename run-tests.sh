#!/usr/bin/env bash

./gradlew :app:connectedCheck -Pandroid.testInstrumentationRunnerArguments.class=com.frontegg.demo.LoginWithPasswordTest
./gradlew :app:connectedCheck -Pandroid.testInstrumentationRunnerArguments.class=com.frontegg.demo.LoginWithSAMLTest