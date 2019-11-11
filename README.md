# PlayCommunity 社区系统

PlayCommunity 社区系统基于 [Play Framework 2.6.x](https://www.playframework.com/documentation/2.6.x/Home) 开发而成，目前正在运营[PlayScala社区](http://www.playscala.cn)。

## 参与贡献
[Play For Scala 开发指南](https://www.playscala.cn/docs)   
[PlayScala社区贡献指南](https://github.com/playcommunity/play-community/blob/master/CONTRIBUTE.md)   

## 下载&编译
```
git clone https://github.com/playcommunity/play-community.git
```
打开命令行，进入 play-community 目录，执行命令:
```
sbt dist
```
最终生成的Play应用路径为：
```
play-community\target\universal\play-community-<version>.zip
```

## 配置
### 配置 MongoDB 连接

将 `play-community-<version>.zip` 文件上传至服务器并解压，打开配置文件 `conf/application.conf`，配置如下：
```
# 配置 MongoDB
mongodb.uri = "mongodb://user:password@host:port/play-community?authMode=scram-sha1"
```
需要注意的是，MongoDB需要开启ReplicaSet功能，开启方法可以参考官方文档：[Deploy Replica Set](https://docs.mongodb.com/manual/tutorial/deploy-replica-set/index.html)，也可以使用群友`梦境迷离`贡献的一键启动脚本，该脚本需要在`Git Bash`中运行。启动脚本的使用方法如下：
- 打开`Git Bash`，切换当前目录至项目根目录；
- 进入配置文件目录：`cd conf`
- 执行启动脚本：`bash start_mongo.sh`，该脚本会启动`mongod`进程，并随后自动进入`mongo shell`；
- 在`mongo shell`中输入如下命令初始化ReplicaSet：
```
var config = {_id:"rs",members:[{_id:0,host:"127.0.0.1:27001"},{_id:1,host:"127.0.0.1:27002"}]};
rs.initiate(config);
```
- 检查ReplicaSet状态：`rs.status()`

以上启动脚本会启动两个MongoDB节点，分别是`127.0.0.1:27001`和`127.0.0.1:27002`，数据库文件位置为`C:/mongo/data`，日志路径为：`C:/mongo/logs/log1.log`、`C:/mongo/logs/log2.log`。

### 配置发送邮件账户:
打开配置文件 `conf/application.conf`，配置如下：
```
# 配置发送邮件账户
play.mailer {
  host = "smtp.163.com"
  port = 25
  user = "xxx@163.com"
  password = "xxx"
}
```

### 配置搜索服务 ElasticSearch
**安装 ElasticSearch**   
下载任意一个版本，解压并执行启动命令：
- [官方版本](https://www.elastic.co/products/elasticsearch)
- [移除root限制版本](http://pan.baidu.com/s/1jIijkrW) 
> 官方的安装包由于安全问题不允许在 root 账户下启动。
```
cd elasticsearch-5.5.0/bin
nohup ./elasticsearch > ./log.txt&
```
打开配置文件 `conf/application.conf` ，配置如下:
```
# 配置 ElasticSearch
es {
  enabled = true
  index = "play-community"
  host = "127.0.0.1:9200"
}
```

## 启动
执行下面命令启动应用：
```
nohup ./play-community -J-Xms1g -J-Xmx1g -Dhttp.port=80 > ../log.txt &
```
> 关于Play应用的启动、停止以及升级，请参考：[Play For Scala 开发指南 - 第5章 第一个Play项目 - 发布Play项目](https://www.playscala.cn/doc/catalog?_id=j1_11)

## 管理
系统的管理入口为：
```
http://服务器地址/admin
```
默认管理员账户为：admin@playscala.cn 123456

