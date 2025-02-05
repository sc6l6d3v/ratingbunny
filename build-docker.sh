#!/bin/bash
# load env vars
export  $(cat .env | grep -v ^\# | xargs)
PORT=8080 \
  docker build -t $HUBUSER/ratingslave:`date +"%Y%m%d%H%M"` \
               -t $HUBUSER/ratingslave:latest .
