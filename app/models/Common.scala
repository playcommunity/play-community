package models

import java.time.{Instant}
import cn.playscala.mongo.annotations.Entity
import play.api.libs.json.{JsArray, JsObject, Json}
import utils.DateTimeUtil

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

object Resource {
  val Resource = "resource"
  val QA = "qa"
  val Doc = "doc"
  val Article = "article"
  val Exam = "exam"
  val Tweet = "tweet"
  val Corporation = "corporation"
}

//额外文档信息
case class DocInfo(title: String, catalogId: String)

//额外试题信息
case class ExamInfo(
  options: List[String], //试题选项
  answer: String, //试题答案
  answers: List[ExamAnswer], //用户提交答案
  explain: String //试题答案
)
//用户提交答案
case class ExamAnswer(
  uid: String, //答题用户标识
  option: String, //提交答案
  createTime: Instant = Instant.now() //答题时间
)

@Entity("common-user")
case class User(
  _id: String,
  role: String,
  login: String,
  password: String,
  setting: UserSetting,
  stat: UserStat,
  score: Int,
  enabled: Boolean,
  from: String,
  ip: String,
  ipLocation: Option[IPLocation],
  channels: List[Channel],
  activeCode: Option[String]
)
case class Channel(id: String, name: String, url: String)

@Entity("common-category")
case class Category(_id: String, name: String, path: String, parentPath: String, index: Int, disabled: Boolean)

// 已整理文档目录
@Entity("doc-catalog")
case class DocCatalog(
  _id: String,
  nodes: JsArray,
  isDefault: Boolean,
  createTime: Instant,
  updateTime: Instant
)


@Entity("common-event")
case class Event(
  _id: String,
  actor: Author,
  action: String,
  resId: String,
  resType: String,
  resTitle: String,
  createTime: Instant
)

@Entity("common-tweet")
case class Tweet(_id: String, author: Author, title: String, content: String, images: List[String], createTime: Instant, voteStat: VoteStat, replyCount: Int, replies: List[Reply]){
  def toJson: JsObject = {
    Json.obj("_id" -> _id, "author" -> author, "content" -> content, "images" -> images, "replyCount" -> replies.size, "voteCount" -> voteStat.count, "time" -> DateTimeUtil.toPrettyString(createTime))
  }
}

@Entity("common-corporation")
case class Corporation(_id: String, title: String, city: String, grade: String, url: String, logo: String, description: String, author: Author, isChinese: Boolean, voteStat: VoteStat, viewStat: ViewStat, active: Boolean, createTime: Instant, updateTime: Instant)

/**
  * 消息提醒
  * @param source system/article/qa
  * @param action view/vote/reply/comment
  */
@Entity("common-message")
case class Message(_id: String, uid: String, source: String, sourceId: String, sourceTitle: String, actor: Author, action: String, content: String, createTime: Instant, read: Boolean)

case class Author(_id: String, login: String, name: String, headImg: String)
case class Reply(_id: String, content: String, editorType: String, author: Author, at: List[String], createTime: Instant, updateTime: Instant, voteStat: VoteStat, comments: List[Comment])
case class Comment(_id: String, content: String, editorType: String, commentator: Author, at: List[String], createTime: Instant, updateTime: Instant)

case class IPLocation(country: String, province: String, city: String)
case class UserSetting(name: String, gender: String, introduction: String, headImg: String, city: String)

object UserStat { val DEFAULT = UserStat(0, 0, 0, 0, 0, 0, 0, 0, 0, DateTimeUtil.now, DateTimeUtil.now, DateTimeUtil.now, DateTimeUtil.now()) }
case class UserStat(resCount: Int, docCount: Int, articleCount: Int, qaCount: Int, fileCount: Int, replyCount: Int, commentCount: Int, voteCount: Int, votedCount: Int, createTime: Instant, updateTime: Instant, lastLoginTime: Instant, lastReplyTime: Instant)
case class ReplyStat(replyCount: Int, replies: List[Reply], bestReply: Option[Reply], lastReply: Option[Reply])
case class ViewStat(count: Int, bitmap: String)
case class VoteStat(count: Int, bitmap: String)
case class CollectStat(count: Int, bitmap: String)