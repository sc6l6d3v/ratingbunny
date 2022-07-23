#
# Scala and sbt Dockerfile
#
# original file from
# https://github.com/hseeberger/scala-sbt
#

# Pull base image
FROM openjdk:8

# Env variables
ENV SCALA_VERSION 2.13.1
ENV SBT_VERSION   1.0.2
ENV APP_NAME      ratingslave
ENV APP_VERSION   0.1-SNAPSHOT

# ENV variables for App

# Install Scala
## Piping curl directly in tar
RUN \
  curl -fsL https://downloads.typesafe.com/scala/$SCALA_VERSION/scala-$SCALA_VERSION.tgz | tar xfz - -C /root/ && \
  echo >> /root/.bashrc && \
  echo "export PATH=~/scala-$SCALA_VERSION/bin:$PATH" >> /root/.bashrc

# Install sbt
#RUN \
#  curl -L -o sbt-$SBT_VERSION.deb https://dl.bintray.com/sbt/debian/sbt-$SBT_VERSION.deb && \
#  dpkg -i sbt-$SBT_VERSION.deb && \
#  rm sbt-$SBT_VERSION.deb && \
#  apt-get update && \
#  apt-get install sbt && \
#   sbt sbtVersion

# Define working directory
WORKDIR /root
ENV PROJECT_HOME /usr/src

COPY ["build.sbt", "/tmp/build/"]
COPY ["project/Dependencies.scala", "project/plugins.sbt", "project/build.properties", "/tmp/build/project/"]
# RUN cd /tmp/build && \
#  sbt update && \
#  sbt compile && \
#  sbt assembly

RUN mkdir -p $PROJECT_HOME/data

WORKDIR $PROJECT_HOME/data

# We are running http4s on this port so expose it
EXPOSE 8080
EXPOSE 5005
# Expose this port if you want to enable remote debugging: 5005

COPY target/scala-2.13/${APP_NAME}-assembly-$APP_VERSION.jar $PROJECT_HOME/data/$APP_NAME.jar

# This will run at start, it points to the .sh file in the bin directory to start the play app
ENTRYPOINT java -jar $PROJECT_HOME/data/$APP_NAME.jar -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5050
# Add this arg to the script if you want to enable remote debugging: -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005
