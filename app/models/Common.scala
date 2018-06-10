package models

import java.time.{Instant}
import cn.playscala.mongo.annotations.Entity
import play.api.libs.json.{JsArray, JsObject, Json}
import utils.DateTimeUtil

trait BaseResource {
  val _id: String
  val title: String
  val keywords: String
  val content: String
  val editor: String
  val author: Author
  val replies: List[Reply]
  val lastReply: Option[Reply]
  val viewStat: ViewStat
  val voteStat: VoteStat
  val replyStat: ReplyStat
  val collectStat: CollectStat
  val createTime: Instant
  val updateTime: Instant
  val top: Boolean // 置顶
  val recommended: Boolean // 精华
  val closed: Boolean // 是否关闭
  val resType: String  // 资源类型
}

// 将问答、分享和文档抽象为 Resource
@Entity("common-resource")
case class Resource (
  _id: String,
  title: String,
  keywords: String,
  content: String,
  editor: String,
  author: Author,
  replies: List[Reply],
  lastReply: Option[Reply],
  viewStat: ViewStat,
  voteStat: VoteStat,
  replyStat: ReplyStat,
  collectStat: CollectStat,
  createTime: Instant,
  updateTime: Instant,
  top: Boolean, // 置顶
  recommended: Boolean, // 精华
  closed: Boolean, // 是否关闭
  resType: String  // 资源类型
) extends BaseResource

object Resource {
  val QA = "qa"
  val Doc = "doc"
  val Article = "article"
}

@Entity("common-resource")
case class Doc (
  _id: String,
  title: String,
  keywords: String,
  content: String,
  editor: String,
  author: Author,
  replies: List[Reply],
  lastReply: Option[Reply],
  viewStat: ViewStat,
  voteStat: VoteStat,
  replyStat: ReplyStat,
  collectStat: CollectStat,
  createTime: Instant,
  updateTime: Instant,
  top: Boolean, // 置顶
  recommended: Boolean, // 精华
  closed: Boolean, // 是否关闭
  resType: String,  // 资源类型
  catalogId: String // 文档目录
) extends BaseResource

// 分享
@Entity("common-resource")
case class Article(
  _id: String,
  title: String,
  keywords: String,
  content: String,
  editor: String,
  author: Author,
  replies: List[Reply],
  lastReply: Option[Reply],
  viewStat: ViewStat,
  voteStat: VoteStat,
  replyStat: ReplyStat,
  collectStat: CollectStat,
  createTime: Instant,
  updateTime: Instant,
  top: Boolean, // 置顶
  recommended: Boolean, // 精华
  closed: Boolean, // 是否关闭
  resType: String,  // 资源类型
  categoryPath: String,
  categoryName: String
) extends BaseResource

// 问答
@Entity("common-resource")
case class QA (
  _id: String,
  title: String,
  keywords: String,
  content: String,
  editor: String,
  author: Author,
  replies: List[Reply],
  lastReply: Option[Reply],
  viewStat: ViewStat,
  voteStat: VoteStat,
  replyStat: ReplyStat,
  collectStat: CollectStat,
  createTime: Instant,
  updateTime: Instant,
  top: Boolean, // 置顶
  recommended: Boolean, // 精华
  closed: Boolean, // 是否关闭
  resType: String,  // 资源类型
  categoryPath: String,
  categoryName: String,
  answer: Option[Reply] // 答案
) extends BaseResource

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
case class Tweet(_id: String, author: Author, title: String, content: String, images: List[String], createTime: Instant, voteStat: VoteStat, replyStat: ReplyStat, replies: List[Reply]){
  def toJson: JsObject = {
    Json.obj("_id" -> _id, "author" -> author, "content" -> content, "images" -> images, "replyCount" -> replies.size, "voteCount" -> voteStat.count, "time" -> DateTimeUtil.toPrettyString(createTime))
  }
}

/**
  * 消息提醒
  * @param source system/article/qa
  * @param action view/vote/reply/comment
  */
@Entity("common-message")
case class Message(_id: String, uid: String, source: String, sourceId: String, sourceTitle: String, actor: Author, action: String, content: String, createTime: Instant, read: Boolean)

case class Author(_id: String, login: String, name: String, headImg: String)
case class Reply(_id: String, content: String, editor: String, author: Author, at: List[String], createTime: Instant, updateTime: Instant, voteStat: VoteStat, comments: List[Comment])
case class Comment(_id: String, content: String, editor: String, commentator: Author, at: List[String], createTime: Instant, updateTime: Instant)

case class IPLocation(country: String, province: String, city: String)
case class UserSetting(name: String, gender: String, introduction: String, headImg: String, city: String)

object UserStat { val DEFAULT = UserStat(0, 0, 0, 0, 0, 0, 0, 0, 0, DateTimeUtil.now, DateTimeUtil.now, DateTimeUtil.now, DateTimeUtil.now()) }
case class UserStat(resCount: Int, docCount: Int, articleCount: Int, qaCount: Int, fileCount: Int, replyCount: Int, commentCount: Int, voteCount: Int, votedCount: Int, createTime: Instant, updateTime: Instant, lastLoginTime: Instant, lastReplyTime: Instant)
case class ViewStat(count: Int, bitmap: String)
case class VoteStat(count: Int, bitmap: String)
case class ReplyStat(count: Int, userCount: Int, bitmap: String)
case class CollectStat(count: Int, bitmap: String)