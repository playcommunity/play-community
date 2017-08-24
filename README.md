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
### 配置MongoDB连接
将play-community-1.0.0.zip文件上传至服务器并解压，打开配置文件conf/application.conf，配置如下：
```
# 配置MongoDB
mongodb.uri = "mongodb://user:password@host:port/play-community?authMode=scram-sha1"
```

### 配置发送邮件账户:
打开配置文件conf/application.conf，配置如下：
```
# 配置发送邮件账户
play.mailer {
  host = "smtp.163.com"
  port = 25
  user = "xxx@163.com"
  password = "xxx"
}
```

### 配置搜索服务ElasticSearch
配置ElasticSearch有两种方式，可以任选一种。
#### 方式1: 内置方式
> 内置方式是指将ElasticSearch安装包拷贝到指定目录，play-community在启动时自动完成ElasticSearch的安装和启动。

下载[elasticsearch-5.5.0.zip](http://pan.baidu.com/s/1jIijkrW)，并将其放到play-community-1.0.0/embed目录下。 然后打开配置文件conf/application.conf，配置如下，
```
# 配置ElasticSearch
es {
  esIndexName = "play-community"
  useExternalES = false
  externalESServer = "127.0.0.1:9200"
}
```

#### 方式2: 外置方式
> 外置方式是指在play-community的外部配置并启动一个ElasticSearch服务，然后配置play-community直接使用它

当使用外置方式时，建议您不要从ElasticSearch官网下载安装包，官方的安装包由于安全问题不允许在root账户下启动， 而是下载这个移除root限制的版本[elasticsearch-5.5.0.zip](http://pan.baidu.com/s/1jIijkrW)，下载到您的服务器后，解压并执行启动命令：
```
cd elasticsearch-5.5.0/bin
nohup ./elasticsearch > ./log.txt&
```
打开配置文件conf/application.conf，配置如下:
```
# 配置ElasticSearch
es {
  esIndexName = "play-community"
  useExternalES = true
  externalESServer = "127.0.0.1:9200"
}
```

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

