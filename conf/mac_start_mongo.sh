#!/bin/bash

echo "if exists mongo, will shut down all mongo process"
ps -ef | grep -w mongod | grep -v grep | awk '{print $2}' | xargs kill -9
sleep 2

# if need more, add after it
host=(127.0.0.1:27001 127.0.0.1:27002)
path=`pwd`"/logs/mongo/data"
logPath=`pwd`"/logs/mongo/logs"

if [ ! -f $path ];then
  echo "the folder exists: ["$path"], then remove them"
  rm -rf $path
fi
	# db folder name is port, unique
for(( i=0;i<${#host[@]};i++)) do
	var=${host[i]}
	port=`echo ${var##*:}`
	echo "will create default db folder for mongo data: ["$path/$port"]"
	mkdir -p $path/$port
done

if [ ! -d $logPath ];then
mkdir -p  $logPath
echo "will create default folder for mongo logs: ["$logPath"]"
else
echo "the folder exists: ["$logPath"], then remove them"
rm -rf $logPath
mkdir -p  $logPath
fi


for(( i=0;i<${#host[@]};i++)) do
var=${host[i]}
echo "mongo host: [$var]"
port=`echo ${var##*:}`
nohup mongod --port $port --oplogSize 100 --dbpath $path/$port --logpath $logPath"/$port.log" --replSet rs/$var --journal > $logPath"/mongo.log"  2>&1  &
done
echo "please wait a few for starting mongo server"

sleep 5

# use first port
var=${host[0]}
port=`echo ${var##*:}`
mongo --port $port


# run after start this in order
# config={_id:"rs",members:[{_id:0,host:"127.0.0.1:27001"},{_id:1,host:"127.0.0.1:27002"}]}
# rs.initiate(config)
# rs.status()
