#!/bin/sh
# Select Java 17 only for this build; do not change the shell default.
set -eu
if [ -n "${JAVA17_HOME:-}" ]; then
    JAVA_HOME="$JAVA17_HOME"
elif [ -x /opt/homebrew/opt/openjdk@17/bin/java ]; then
    JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
elif [ -x /usr/libexec/java_home ]; then
    JAVA_HOME=$(/usr/libexec/java_home -v 17)
else
    : "${JAVA_HOME:?Set JAVA17_HOME to a JDK 17 installation}"
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
exec "$(dirname "$0")/gradlew-original" "$@"
