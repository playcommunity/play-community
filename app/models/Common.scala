package models

import org.joda.time.DateTime

case class User(
  _id: Int,
  role: String,
  login: String,
  password: String,
  name: String,
  gender: String,
  introduction: String,
  headImg: String,
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
  createTime: DateTime,
  updateTime: DateTime,
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
  createTime: DateTime,
  updateTime: DateTime,
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

case class Author(_id: String, login: String, name: String, headImg: String)
case class ViewStat(count: Int, bitMap: String)
case class VoteStat(count: Int, bitMap: String)
case class UserTimeStat(createTime: DateTime, updateTime: DateTime, lastLoginTime: DateTime, lastReplyTime: DateTime)
case class ArticleTimeStat(createTime: DateTime, updateTime: DateTime, lastViewTime: DateTime, lastVoteTime: DateTime, lastReplyTime: DateTime)
case class Reply(_id: String, content: String, editorType: String, author: Author, replyTime: DateTime, viewStat: ViewStat, voteStat: VoteStat, comments: List[Comment])
case class Comment(_id: String, content: String, editorType: String, commentator: Author, commentTime: DateTime)

