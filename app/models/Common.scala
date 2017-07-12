package models

import java.time.OffsetDateTime

case class User(
  _id: String,
  role: String,
  login: String,
  password: String,
  setting: UserSetting,
  ip: String,
  timeStat: UserTimeStat,
  score: Int,
  enabled: Boolean,
  verifyCode: String
)
case class Category(_id: String, name: String, path: String, parentPath: String, index: Int, disabled: Boolean)

// 已整理文档
case class Document(
  _id: String,
  title: String,
  content: String,
  editorType: String,
  author: Author,
  categoryPath: String,
  tags: List[String],
  createTime: OffsetDateTime,
  upOffsetDateTime: OffsetDateTime,
  replies: List[Reply],
  viewStat: ViewStat,
  voteStat: VoteStat,
  index: Int // 显示排序
)

// 问答
case class Question(
  _id: String,
  title: String,
  content: String,
  editorType: String,
  author: Author,
  categoryPath: String,
  tags: List[String],
  timeStat: QuestionTimeStat,
  replies: List[Reply],
  lastReply: Option[Reply],
  answer: Option[Reply],
  viewStat: ViewStat,
  voteStat: VoteStat
)

// 分享
case class Article(
  _id: String,
  title: String,
  content: String,
  editorType: String,
  author: Author,
  categoryPath: String,
  categoryName: String,
  tags: List[String],
  replies: List[Reply],
  lastReply: Option[Reply],
  viewStat: ViewStat,
  voteStat: VoteStat,
  timeStat: ArticleTimeStat,
  top: Boolean, // 置顶
  recommended: Boolean // 精华
)

case class UserSetting(name: String, gender: String, introduction: String, headImg: String, city: String)
case class Author(_id: String, login: String, name: String, headImg: String)
case class ViewStat(count: Int, bitMap: String)
case class VoteStat(count: Int, bitMap: String)
case class UserTimeStat(createTime: OffsetDateTime, upOffsetDateTime: OffsetDateTime, lastLoginTime: OffsetDateTime, lastReplyTime: OffsetDateTime)
case class QuestionTimeStat(createTime: OffsetDateTime, upOffsetDateTime: OffsetDateTime, lastViewTime: OffsetDateTime, lastVoteTime: OffsetDateTime)
case class ArticleTimeStat(createTime: OffsetDateTime, upOffsetDateTime: OffsetDateTime, lastViewTime: OffsetDateTime, lastVoteTime: OffsetDateTime)
case class Reply(_id: String, content: String, editorType: String, author: Author, replyTime: OffsetDateTime, viewStat: ViewStat, voteStat: VoteStat, comments: List[Comment])
case class Comment(_id: String, content: String, editorType: String, commentator: Author, commentTime: OffsetDateTime)

