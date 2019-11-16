# PlayScala社区贡献指南 v1.2.1

> 更新历史

版本 | 更新日期 | 更新内容
:---|:---|:---
1.2.1 | 2019-11-14 | 新增社区版块设计。
1.2 | 2019-11-06 | 新增开发规范。
1.1 | 2019-07-19 | 增加领域层设计。
1.0 | 2018-11-21 | 核心数据模型说明。

> 术语说明

术语 | 说明
:---|:---
`表` | 此处借用关系数据库`表`的概念描述MongoDB的文档集合(Collection)，直接使用集合容易让人联想到Java的集合。
`存储库` | 存储库(Repository)是领域驱动领域的概念，用于屏蔽底层存储的数据读写层。
`分类树` | 分类树是全局共享的一棵树，用于分类社区资源，包括文档、分享、问答等。分类树数据存储在`common-category`表中。

## 1. 贡献入门
### 1.1 加入Github开发团队
如果你希望参与贡献代码，请先申请加入开发团队。加入开发团队是为了方便领取开发任务。 请在下方任选一种方式提交`Github用户名`和`邮箱`信息，
- 加入社区QQ交流群(851236949)，私信`南京-金融-沐风(416861875)`
- 发邮件至`joymufeng@163.com`
- 加微信`huangruchun`，备注`申请加入团队`

管理员在接收到提交信息后，会发送Github邀请。访问以下链接可以查看并接受邀请：
```
https://github.com/playcommunity
```

### 1.2 开发环境配置
#### 1.2.1 安装Git
访问官网下载并安装Git：
```
https://git-scm.com/
```
安装成功后，将`GIT_HOME/usr/bin`目录添加至环境变量。下面生成SSH Key时会用到。

#### 1.2.2 安装IDEA
> 请参考：[第一个Play项目](https://www.playscala.cn/doc/catalog?_id=j1_11)

### 1.3 开始贡献
如果你还不熟悉Play开发框架，请先阅读[Play For Scala 开发指南](https://www.playscala.cn/docs)

#### 1.3.1 领取开发任务
目前通过Github issue管理开发任务。名称以`【开发任务】`开头的issue为可认领的开发任务，其它issue为待定任务，尚未发布为`【开发任务】`。打开下方链接可以查看所有的开发任务：
```
https://github.com/playcommunity/play-community/issues
```
单击感兴趣的开发任务进入详情页面，单击右侧的`assign yourself`按钮领取任务，并将任务的`Labels`改成`Developing`如下图：
![image](https://www.playscala.cn/resource/5dc3b5f9eeab565236ea6985)

> 小提示：如果右侧`Assignees`下方已经有人了，就不可以再认领了哦！

#### 1.3.2 克隆社区代码
如果你本地还没有SSH Key，则执行如下命令生成，
```
ssh-keygen -t rsa -b 4096 -C "your_email@example.com"
```
编辑`~/.ssh/config`文件，内容如下，
```
Host playscala
User git
HostName github.com
IdentityFile /Users/joymufeng/.ssh/id_rsa
```
请将`SSH Key`的私钥路径换成你自己的，然后执行如下命令克隆社区代码，
```
git clone git@playscala:playcommunity/play-community.git
```

> 注意社区开发成员请直接 clone 社区的资源库(`git@github.com:playcommunity/play-community.git`)，不要 clone 自己 fork 的资源库。

#### 1.3.3 开始任务
假设你领取了一个`Id`为10的`Issue`，在开始编码前，你需要先基于`master`分支创建一个私有的开发分支，开发分支的命名规则为`用户标识-issue-任务编号`, 
其中`用户标识`可以使用姓名首字母缩写或是英文昵称，`任务编号`固定为`issue id`，例如对于开发者`joymufeng`来说，针对`Issue 10`的开发分支可以命名为`joymufeng-issue-10`。   

首先确认当前分支是`master`分支，并且已与远程代码保持同步，然后执行如下命令创建并切换至开发分支：
```
git checkout -b 用户标识-issue-任务编号
```
完成功能开发后，在开发分支上创建`Commit`, `Commit Message`格式为：`Fixes #任务编号: xxx`，例如`Fixes #10: 完成微信扫码登录功能。`。 然后将该开发分支推送至远程Github仓库，如果你使用IntelliJ IDEA，
依次单击菜单 `VCS`-`Git`-`Push...`即可。 另外，你也可以通过命令行方式手动推送：
```
git push -u origin 用户标识-issue-任务编号
```
推送成功后，登录Github，打开社区项目地址 [https://github.com/playcommunity/play-community](https://github.com/playcommunity/play-community)， 选择`用户标识-issue-任务编号`分支，并单击右侧的`New pull request`按钮：

![image](https://www.playscala.cn/resource/5dc39348eeab565236ea62e5)

成功创建`pull request`后，请注意关注审查意见，并及时改进。请注意，如果在提交`pull request`后需要修改代码，请直接在`用户标识-issue-任务编号`分支上修改并推送至远程，之前的`pull request`会自动更新。

在审查通过后，你的代码会被合并至`master`分支，恭喜你成功完成一次贡献！

在完成开发任务后需要清理开发分支，首先切换至`master`分支，
```
git checkout master
```
然后删除本地开发分支,
```
git branch -d 用户标识-issue-任务编号
```
最后删除Github上的远程开发分支，
```
git push origin --delete 用户标识-issue-任务编号
```

## 2. 开发规范
### 2.1 编码风格
待定。

### 2.2 编码原则
### 2.2.1 领域驱动介绍
领域驱动是解决复杂性的利器，可以让你近距离接触事物的本质。领域驱动在开发上很容易上手，跟传统方式相比，领域驱动在开发方式上具有以下特点：
- 领域实体包含丰富的领域方法，属于充血模型；
- 领域驱动不关注底层数据存储，而是抽象出存储库(Repository)用于读写数据，使得切换底层数据库变的比较方便；
- 领域实体也具有数据读写能力，开发方式更加灵活
- 完成领域层建模后，系统更容易进行微服务拆分，也更适合横向分工
- 引入领域层后，系统分层更加清晰，适合纵向分工，例如让熟悉MongoDB的同学负责Repository层构建，上层直接基于Repository开发即可。

### 2.2.2 编码原则
- Controller实现是很薄的一层，只包含参数校验和领域层接口调用，不要包含任何业务逻辑实现。
- 在Controller层，如果确实有一些业务逻辑需要实现，但是又无法放置到领域实体，请在服务层(app/services)实现。
- 在Controller层尽量避免直接访问数据库，例如通过Mongo实例访问数据库。正确的做法是通过存储库(Repository)进行读写操作，例如使用`MongoUserRepository`读写用户数据。存储库(Repository)对上层屏蔽了底层的持久化细节。
- 领域实体实例是运行时动态创建的，无法享受依赖注入便利，故提供一个领域层调用入口DomainRegistry，通过DomainRegistry可以非常方便地访问存储库。
- 在实现新的业务逻辑时，请按优先级选择以下三种方式：
  - 是否可以在实体内部实现，如实现为`models.User`的业务方法
  - 是否可以在存储库(Repository)内部实现，如果实现为`MongoUserRepository`的某个方法
  - 如上述两个地方均不合适，则在服务层(app/services)实现一个领域服务


### 2.3 开发注意事项
#### 2.3.1 实体层重构
在变更`models`包下类属性后，请执行一次`sbt clean compile`，否则可能会报JSON编解码异常。

## 3. 系统设计
### 3.1 系统架构

![image](https://www.playscala.cn/resource/5dc2b10beeab565236ea4633)

### 3.2 领域模型设计
领域驱动设计的概念中包含以下5个基本元素：
- `实体(entity)`：具有持久化ID的对象，在JPA中通常使用`@Entity`注解标识，在领域驱动中又称为聚合根。
- `值对象(value object)`：仅作为值的对象。
- `工厂(factory)`：负责实现对象创建逻辑的对象或方法，通常用于创建逻辑较为复杂的场景。
- `存储库(repository)`：用来访问持久化实体的对象，封装了数据库访问逻辑。在本项目中，`mongo`实例即实现了存储库功能。
- `服务(service)`：服务对象用于实现不属于实体的业务逻辑。

初步设计的领域模型如下，其中每个虚线框代表一个聚合，`aggregate root`表示聚合根，`value object`表示值对象。

> 图片正在修订中

### 3.3 社区版块(Board)设计
#### 3.3.1 数据模型设计
社区所有资源都挂在一棵分类树上，分类树数据存在`common-category`表中，该树的第0层为根节点(/)，第1层节点为版块(Board)节点，第2层及以下为分类(Category)节点。社区所有资源，包括文档、问答、分享等都可以挂到某个分类节点上。

虽然本质上版块(Board)节点就是分类(Category)节点，但是为了更贴近业务，所以单独将版块抽象为Board实体，Board实体与Category实体类似，但是会包含更丰富的业务方法。

> 在实现Board实体和Category实体的资源库时，数据读写共用common-category表。

#### 3.2.2 业务流程设计
- 创建一个新版块，其实就是在分类树的第1层新增一个节点
- 每个版块可以设定一个Logo，方便展示
- 每个版块可以设定一个版主，负责版块的维护与运营
- 未设定版主的版块，可以通过投票方式竞选版主，候选者通过报名参加竞选，然后通过投票确定版主

### 3.2.3 功能页面设计
- 首页增加一个版块导航菜单
- 首页右侧栏增加一个版块导航区域
- 新增版块详情页
- 社区资源创建/编辑页面新增分类树选择功能

### 3.3 社区资源(Resource)设计
目前社区核心资源主要包括：
- 分享
- 文档
- 问答
- 题库

由于以上资源在结构上高度相似，所以暂时使用通一张表`common-resource`存储以上所有资源，对应实体类`Resource`如下：
```
// 抽象[问答/分享/文档/题库]
@Entity("common-resource")
case class Resource (
  _id: String,  //资源唯一标识，格式为：用户ID+时间序列
  title: String, //资源标题
  keywords: String = "", // 关键字
  content: String, //内容
  editorType: String = "quill", //富文本编辑器类型
  author: Author, //资源作者
  replyStat: ReplyStat = ReplyStat(0, Nil, None, None), //回复统计
  viewStat: ViewStat = ViewStat(0, ""), //查看统计
  voteStat: VoteStat = VoteStat(0, ""), //投票统计
  collectStat: CollectStat = CollectStat(0, ""), //收藏统计
  createTime: Instant = Instant.now, //创建时间
  updateTime: Instant = Instant.now, //更新时间
  top: Boolean = false, // 置顶
  recommended: Boolean = false, // 精华
  closed: Boolean = false, // 是否关闭
  visible: Boolean = true, // 是否发布
  resType: String, // 资源类型
  categoryPath: String = "/", //所属分类路径
  categoryName: String = "", //所属分类名称
  doc: Option[DocInfo] = None, //额外文档信息(resType == Resource.Doc)
  exam: Option[ExamInfo] = None //额外试题信息(resType == Resource.Exam)
)
```


## 4. 社区题库设计 
### 4.1 社区题库介绍
题库模块可以增强用户之间学习和交流的乐趣，同时也可以帮助初学者检测学习成果，在一定程度上可以增强社区粘性。   
题库模块目前包含如下功能：
- 做题功能(`已完成`)
- 出题功能

#### 4.2 出题功能设计
#### 4.2.1 路径规划
- 路由：`conf/routes` - `## Exam`
- Controller: `controllers.ExamController`
- Views: `app/views/resource/exam/`

#### 4.2.2 模型层设计
试题数据的通用部分存在`Resource`类中，额外信息存在`ExamInfo`类中，
```
//额外试题信息
case class ExamInfo(
  options: List[String], //试题选项
  answer: String, //试题答案
  answers: List[ExamAnswer], //用户提交答案
  explain: String //试题答案解析
)
//用户提交答案
case class ExamAnswer(
  uid: String, //答题用户标识
  option: String, //提交答案
  createTime: Instant = Instant.now() //答题时间
)
```
目前的设计支持`单选题`和`多选题`两种题型。`ExamInfo.answer`和`ExamAnswer.option`两个字段存放选项的索引号。如果是多选题，`ExamInfo.answer`中存放的序号之间使用英文逗号分隔。在页面上可以通过判断`ExamInfo.answer`是否包含多个选项，从而可以判断题型是否为多选。

#### 4.2.3 前后端设计  
在开发时尽量采用前后端分离，在保存试题时，前端页面生成试题的JSON数据并提交至后台，提交时页面不跳转。后台接口返回JSON响应，前端根据响应中的`status`字段判断是否成功，`status`为0表示成功，为非0表示失败。

为了简化前端页面设计，试题答案表单项可以直接使用文本框让用户输入正确答案序号，多个序号之间使用逗号分隔。后台在处理时，要做容错和有效性处理。例如先将所有中文逗号替换为英文逗号，然后检测答案格式是否有效。答案中选项序号升序排列。

试题答案解析字段`ExamInfo.explain`对应的表单字段为富文本编辑器，提交时字段值为HTML富文本格式。


