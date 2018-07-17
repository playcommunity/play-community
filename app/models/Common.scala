package models

import java.time.{Instant}
import cn.playscala.mongo.annotations.Entity
import play.api.libs.json.{JsArray, JsObject, Json}
import utils.DateTimeUtil

case class Channel(id: String, name: String, url: String)

@Entity("test")
case class Test(_id: String, time: Instant)

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

@Entity("common-category")
case class Category(_id: String, name: String, path: String, parentPath: String, index: Int, disabled: Boolean)

// 新闻资讯
@Entity("common-news")
case class News(
  _id: String,
  title: String,
  url: String,
  author: Author,
  from: String,
  top: Option[Boolean],
  createTime: Instant
)

// 已整理文档
@Entity("common-doc")
case class Doc (
  _id: String,
  title: String,
  content: String,
  keywords: String,
  author: Author,
  replies: List[Reply],
  viewStat: ViewStat,
  voteStat: VoteStat,
  replyStat: ReplyStat,
  collectStat: CollectStat,
  timeStat: DocTimeStat,
  catalogId: String
)

// 已整理文档目录
@Entity("doc-catalog")
case class DocCatalog(
  _id: String,
  nodes: JsArray,
  isDefault: Boolean,
  createTime: Instant,
  updateTime: Instant
)

// 分享
@Entity("common-article")
case class Article(
  _id: String,
  title: String,
  content: String,
  keywords: String,
  editorType: String,
  author: Author,
  categoryPath: String,
  categoryName: String,
  tags: List[String],
  replies: List[Reply],
  lastReply: Option[Reply],
  viewStat: ViewStat,
  voteStat: VoteStat,
  replyStat: ReplyStat,
  collectStat: CollectStat,
  timeStat: ArticleTimeStat,
  top: Boolean, // 置顶
  recommended: Boolean // 精华
)

// 问答
@Entity("common-qa")
case class QA(
  _id: String,
  title: String,
  content: String,
  editorType: String,
  author: Author,
  categoryPath: String,
  categoryName: String,
  score: Int,
  tags: List[String],
  replies: List[Reply],
  lastReply: Option[Reply],
  answer: Option[Reply],
  viewStat: ViewStat,
  voteStat: VoteStat,
  replyStat: ReplyStat,
  collectStat: CollectStat,
  timeStat: QATimeStat
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

case class IPLocation(country: String, province: String, city: String)
case class UserSetting(name: String, gender: String, introduction: String, headImg: String, city: String)
object UserStat { val DEFAULT = UserStat(0, 0, 0, 0, 0, 0, 0, 0, 0, DateTimeUtil.now, DateTimeUtil.now, DateTimeUtil.now, DateTimeUtil.now()) }
case class UserStat(resCount: Int, docCount: Int, articleCount: Int, qaCount: Int, fileCount: Int, replyCount: Int, commentCount: Int, voteCount: Int, votedCount: Int, createTime: Instant, updateTime: Instant, lastLoginTime: Instant, lastReplyTime: Instant)
case class Author(_id: String, login: String, name: String, headImg: String)
case class ViewStat(count: Int, bitmap: String)
case class VoteStat(count: Int, bitmap: String)
case class ReplyStat(count: Int, userCount: Int, bitmap: String)
case class CollectStat(count: Int, bitmap: String)
case class ArticleTimeStat(createTime: Instant, updateTime: Instant, lastViewTime: Instant, lastVoteTime: Instant)
case class DocTimeStat(createTime: Instant, updateTime: Instant)
case class QATimeStat(createTime: Instant, updateTime: Instant, lastViewTime: Instant, lastVoteTime: Instant)
case class Reply(_id: String, content: String, editorType: String, author: Author, replyTime: Instant, viewStat: ViewStat, voteStat: VoteStat, comments: List[Comment])
case class Comment(_id: String, content: String, editorType: String, commentator: Author, commentTime: Instant)
