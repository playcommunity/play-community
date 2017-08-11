# PlayCommunity 社区系统

PlayCommunity社区系统基于Play Framework 2.6.x开发而成，目前正在运营[PlayScala社区](http://www.playscala.cn)。

## 下载&编译
```
git clone https://github.com/joymufeng/play-community.git
```
打开命令行，进入play-community目录，执行命令:
```
sbt dist
```
最终生成的Play应用路径为：
```
play-community\target\universal\play-community-1.0.0.zip
```

## 配置
将play-community-1.0.0.zip文件上传至服务器并解压，打开配置文件conf/application.conf，配置MongoDB连接：
```
mongodb.uri = "mongodb://user:password@host:port/play-community?authMode=scram-sha1"
```
配置发送邮件账户:
```
play.mailer {
  host = "smtp.163.com"
  port = 25
  user = "xxx@163.com"
  password = "xxx"
}
```
配置外部ES：
```
es {
  esIndexName = "play-community"
  useExternalES = true
  externalESServer = "127.0.0.1:9200"
}
```
如果设置useExternalES为false，则需要下载[elasticsearch-5.5.0.zip](http://pan.baidu.com/s/1jIijkrW)，并将其放到play-community-1.0.0/embed目录下。

## 启动
执行下面命令启动应用：
```
nohup ./play-community -J-Xms1g -J-Xmx1g -Dhttp.port=80 > ../log.txt &
```

## 管理
系统的管理入口为：
```
http://服务器地址/admin
```
默认管理员账户为：admin@playscala.cn 123456

