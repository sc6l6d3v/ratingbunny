#
# Scala and sbt Dockerfile
#
# original file from
# https://github.com/hseeberger/scala-sbt
#

# Pull base image
FROM bellsoft/liberica-openjdk-alpine:21-cds

# Env variables
ENV SCALA_VERSION=3.4.2
ENV SBT_VERSION=1.10.0
ENV APP_NAME=ratingbunny
ENV APP_VERSION=1.0

# ENV variables for App
RUN apk add --no-cache curl bash busybox-extras

# Define working directory
WORKDIR /root
ENV PROJECT_HOME=/usr/src

RUN mkdir -p $PROJECT_HOME/data

RUN mkdir -p $PROJECT_HOME/data/logs

WORKDIR $PROJECT_HOME/data

# We are running http4s on this port so expose it
EXPOSE 8080
EXPOSE 5050
# Expose this port if you want to enable remote debugging: 5005

COPY target/scala-${SCALA_VERSION}/${APP_NAME}-assembly-$APP_VERSION.jar $PROJECT_HOME/data/$APP_NAME.jar

# This will run at start, it points to the .sh file in the bin directory to start the play app
ENTRYPOINT [ "java", "-Djava.net.preferIPv4Stack=true", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5050", "-jar", "$PROJECT_HOME/data/$APP_NAME.jar" ]
# Add this arg to the script if you want to enable remote debugging: -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005
