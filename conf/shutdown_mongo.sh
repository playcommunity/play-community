#!/bin/bash

echo "if exists , we will shut down all mongo process"

ps -ef | grep mongo | awk '{print $2}' | xargs   kill -9
ps -ef | grep mongod | awk '{print $2}' | xargs   kill -9

echo "close mongo & mongod successfully,bye"