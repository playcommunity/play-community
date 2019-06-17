#!/bin/bash

echo "if exists mongo , we will shut down all mongo process"
ps -ef | grep mongo | awk '{print $2}' | xargs   kill -9
ps -ef | grep mongod | awk '{print $2}' | xargs   kill -9

sleep 1

default1=127.0.0.1:27001
default2=127.0.0.1:27002
path="C:/mongo/data"
logPath="C:/mongo/logs"

if [ ! -d $path ];then
    echo "create default folder for mongo data:"$path
    mkdir -p $path/$db1
    mkdir -p $path/$db2
    echo "create two db folders"
else
    echo "folder exists:" $path "we will rm them"
    rm -rf $path
    mkdir -p $path/db1
    mkdir -p $path/db2
fi

if [ ! -d $logPath ];then
    mkdir -p  $logPath
    echo "create default folder for mongo logs:"$logPath
else
    echo "folder exists:" $logPath "we will rm them"
    rm -rf $logPath
    mkdir -p  $logPath
fi

echo "default ip1 is $default1"
echo "default ip2 is $default2"
nohup mongod --port 27001 --oplogSize 100 --dbpath $path"/db1" --logpath $logPath"/log1.log" --replSet rs/$default1 --journal > ../logs/mongo.log  2>&1  &
nohup mongod --port 27002 --oplogSize 100 --dbpath $path"/db2"  --logpath $logPath"/log2.log" --replSet rs/$default2 --journal >> ../logs/mongo.log  2>&1  &
mongo --port 27001


# run after start this in order
# config={_id:"rs",members:[{_id:0,host:"127.0.0.1:27001"},{_id:1,host:"127.0.0.1:27002"}]}
# rs.initiate(config)
# rs.status()