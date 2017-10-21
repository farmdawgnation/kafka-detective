#!/usr/bin/env bash

export JAVA_OPTS=$JAVA_OPTS

exec java -cp *:lib/* $JAVA_OPTS com.mailchimp.kafka.detective.Main "$@"
