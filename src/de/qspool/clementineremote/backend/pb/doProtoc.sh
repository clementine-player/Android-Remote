#!/bin/bash

protoc -I./ --java_out=./../../../../../ remotecontrolmessages.proto
