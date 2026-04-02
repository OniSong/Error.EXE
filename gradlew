#!/usr/bin/env sh

##############################################################################
##
##  Gradle start up script for UN*X
##
##############################################################################

# Attempt to set APP_HOME
PRG="$0"
while [ -h "$PRG" ] ; do
    PRG=`readlink "$PRG"`
done
SAVED="`pwd`"
cd "`dirname "$PRG"`/" >/dev/null
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

# --- FALLBACK DOWNLOADER ---
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ ! -e "$CLASSPATH" ]; then
    echo "gradle-wrapper.jar is missing. Attempting to download..."
    mkdir -p "$APP_HOME/gradle/wrapper"
    curl -L -o "$CLASSPATH" \
    https://raw.githubusercontent.com/gradle/gradle/v8.12.1/gradle/wrapper/gradle-wrapper.jar
    
    if [ ! -e "$CLASSPATH" ]; then
        echo "ERROR: Failed to download gradle-wrapper.jar. Please check your internet connection."
        exit 1
    fi
fi
# --- END FALLBACK ---

# Determine the Java command to use
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/bin/java" ] ; then
        JAVACMD="$JAVA_HOME/jre/bin/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
else
    JAVACMD="java"
fi

# Collect and run
exec "$JAVACMD" \
    "-Dorg.gradle.appname=$APP_BASE_NAME" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
