#!/usr/bin/env bash

export JAVA_OPTS=$JAVA_OPTS

exec java -cp *:lib/* $JAVA_OPTS me.frmr.kafka.detective.Main "$@"
