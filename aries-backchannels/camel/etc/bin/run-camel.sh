#!/bin/bash

PRG="$0"

# Get absolute path of the HOMEDIR
HOMEDIR="$(dirname ${PRG})/.."
HOMEDIR="$(realpath ${HOMEDIR})"

if [ -z $JAVA_OPTS ]; then
    JAVA_OPTS="-server"
fi

exec java $JAVA_OPTS -jar ${HOMEDIR}/lib/@project.artifactId@-@project.version@.jar "$@" 
