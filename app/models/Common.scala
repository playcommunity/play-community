package models

import org.joda.time.DateTime

case class User(_id: Int, login: String, password: String, name: String, gender: String, introduction: String, headImg: String, ip: String, createTime: DateTime, updateTime: DateTime, lastLoginTime: DateTime, enabled: Boolean)
case class Category(_id: String, name: String, path: String, parentPath: String, disabled: Boolean)

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
  answer: Option[Reply],
  replies: List[Reply],
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
  tags: List[String],
  createTime: DateTime,
  updateTime: DateTime,
  replies: List[Reply],
  viewStat: ViewStat,
  voteStat: VoteStat,
  top: Boolean, // 置顶
  recommended: Boolean // 精华
)

case class Author(_id: String, login: String, name: String, headImg: String)
case class ViewStat(viewCount: Int, viewBitMap: String)
case class VoteStat(voteCount: Int, voteBitMap: String)
case class Reply(_id: String, content: String, editorType: String, replier: Author, replyTime: DateTime, viewStat: ViewStat, comments: List[Comment])
case class Comment(_id: String, content: String, editorType: String, commentator: Author, commentTime: DateTime)

