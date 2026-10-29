#
# Scala and sbt Dockerfile
#
# original file from
# https://github.com/hseeberger/scala-sbt
#

# Pull base image
FROM bellsoft/liberica-openjdk-alpine:21-cds

# Env variables
ENV SCALA_VERSION=3.5.0
ENV SBT_VERSION=1.10.0
ENV APP_NAME=ratingbunny
ENV APP_VERSION=1.0

# ENV variables for App
RUN apk add --no-cache curl bash busybox-extras

# CVE-2026-91403: distrust the compromised eMudhra/emSign roots and the Sectigo "AAA Certificate
# Services" cross-sign root in the JDK trust store; the build fails if any of them remain.
RUN keytool -list -v -cacerts -storepass changeit 2>/dev/null \
      | awk -F': ' '/^Alias name:/ {a = $2} /^Owner:/ && /emSign|AAA Certificate Services/ {print a}' \
      | while IFS= read -r a; do keytool -delete -cacerts -storepass changeit -alias "$a"; done; \
    ! keytool -list -v -cacerts -storepass changeit 2>/dev/null | grep -E '^Owner:.*(emSign|AAA Certificate Services)'

# Define working directory
WORKDIR /root
ENV PROJECT_HOME=/usr/src

RUN mkdir -p $PROJECT_HOME/data

RUN mkdir -p $PROJECT_HOME/data/logs

WORKDIR $PROJECT_HOME/data

# We are running http4s on this port so expose it
EXPOSE 8080

COPY target/scala-${SCALA_VERSION}/${APP_NAME}-assembly-$APP_VERSION.jar $PROJECT_HOME/data/$APP_NAME.jar

# The Java debugger is off unless JDWP_PORT is set (DEV sets 5050). Anyone who can reach that port can run code in the JVM,
# so PROD leaves it unset.
ENTRYPOINT [ "sh", "-c", "exec java -Djava.net.preferIPv4Stack=true ${JDWP_PORT:+\"-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:$JDWP_PORT\"} -jar \"$PROJECT_HOME/data/$APP_NAME.jar\"" ]
