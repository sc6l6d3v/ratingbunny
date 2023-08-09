#
# Scala and sbt Dockerfile
#
# original file from
# https://github.com/hseeberger/scala-sbt
#

# Pull base image
FROM bellsoft/liberica-openjdk-alpine:17.0.8

# Env variables
ENV SCALA_VERSION 2.13.8
ENV SBT_VERSION   1.0.2
ENV APP_NAME      ratingslave
ENV APP_VERSION   1.0

# ENV variables for App
RUN \
   apk add --no-cache curl bash busybox-extras

# Define working directory
WORKDIR /root
ENV PROJECT_HOME /usr/src

RUN mkdir -p $PROJECT_HOME/data

WORKDIR $PROJECT_HOME/data

# We are running http4s on this port so expose it
EXPOSE 8080
EXPOSE 5050
# Expose this port if you want to enable remote debugging: 5005

COPY target/scala-2.13/${APP_NAME}-assembly-$APP_VERSION.jar $PROJECT_HOME/data/$APP_NAME.jar

# This will run at start, it points to the .sh file in the bin directory to start the play app
ENTRYPOINT java -jar $PROJECT_HOME/data/$APP_NAME.jar -Djava.net.preferIPv4Stack=true -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5050
# Add this arg to the script if you want to enable remote debugging: -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005
