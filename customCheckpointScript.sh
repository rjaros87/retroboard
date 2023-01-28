#!/usr/bin/env sh

echo "Custom checkpoint script"
java \
-XX:CRaCCheckpointTo=cr \
-XX:+UnlockDiagnosticVMOptions \
-XX:+CRTraceStartupTime \
-Djdk.crac.trace-startup-time=true \
-jar build/libs/retroboard-01.jar