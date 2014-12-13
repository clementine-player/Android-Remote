#!/bin/bash

protoc -I./ --java_out=./src ./src/de/qspool/clementineremote/backend/pb/remotecontrolmessages.proto