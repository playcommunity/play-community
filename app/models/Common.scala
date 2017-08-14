package models

import java.time.OffsetDateTime

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
  activeCode: Option[String]
)
case class Category(_id: String, name: String, path: String, parentPath: String, index: Int, disabled: Boolean)

// 新闻资讯
case class News(
  _id: String,
  title: String,
  url: String,
  author: Author,
  from: String,
  createTime: OffsetDateTime
)

// 已整理文档
case class Doc (
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
  viewStat: ViewStat,
  voteStat: VoteStat,
  replyStat: ReplyStat,
  collectStat: CollectStat,
  timeStat: DocTimeStat,
  index: Int // 显示排序
)

// 分享
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

case class Event(
  _id: String,
  actor: Author,
  action: String,
  resId: String,
  resType: String,
  resTitle: String,
  createTime: OffsetDateTime
)

/**
  * 消息提醒
  * @param source system/article/qa
  * @param action view/vote/reply/comment
  */
case class Message(_id: String, uid: String, source: String, sourceId: String, sourceTitle: String, actor: Author, action: String, content: String, createTime: OffsetDateTime, read: Boolean)

case class IPLocation(country: String, province: String, city: String)
case class UserSetting(name: String, gender: String, introduction: String, headImg: String, city: String)
case class Author(_id: String, login: String, name: String, headImg: String)
case class ViewStat(count: Int, bitmap: String)
case class VoteStat(count: Int, bitmap: String)
case class ReplyStat(count: Int, userCount: Int, bitmap: String)
case class CollectStat(count: Int, bitmap: String)
case class UserStat(articleCount: Int, questionCount: Int, replyCount: Int, commentCount: Int, voteCount: Int, votedCount: Int, createTime: OffsetDateTime, updateTime: OffsetDateTime, lastLoginTime: OffsetDateTime, lastReplyTime: OffsetDateTime)
case class ArticleTimeStat(createTime: OffsetDateTime, updateTime: OffsetDateTime, lastViewTime: OffsetDateTime, lastVoteTime: OffsetDateTime)
case class DocTimeStat(createTime: OffsetDateTime, updateTime: OffsetDateTime)
case class QATimeStat(createTime: OffsetDateTime, updateTime: OffsetDateTime, lastViewTime: OffsetDateTime, lastVoteTime: OffsetDateTime)
case class Reply(_id: String, content: String, editorType: String, author: Author, replyTime: OffsetDateTime, viewStat: ViewStat, voteStat: VoteStat, comments: List[Comment])
case class Comment(_id: String, content: String, editorType: String, commentator: Author, commentTime: OffsetDateTime)

