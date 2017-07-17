package models

import java.time.OffsetDateTime

case class User(
  _id: String,
  role: String,
  login: String,
  password: String,
  setting: UserSetting,
  ip: String,
  userStat: UserStat,
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
  updateTime: OffsetDateTime,
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
  replyStat: ReplyStat,
  collectStat: CollectStat,
  timeStat: ArticleTimeStat,
  top: Boolean, // 置顶
  recommended: Boolean // 精华
)

/**
  * 消息提醒
  * @param source system/article/question
  * @param action view/vote/reply/comment
  */
case class Message(_id: String, uid: String, source: String, sourceId: String, sourceTitle: String, actor: Author, action: String, content: String, createTime: OffsetDateTime, read: Boolean)

case class UserSetting(name: String, gender: String, introduction: String, headImg: String, city: String)
case class Author(_id: String, login: String, name: String, headImg: String)
case class ViewStat(count: Int, bitmap: String)
case class VoteStat(count: Int, bitmap: String)
case class ReplyStat(count: Int, userCount: Int, bitmap: String)
case class CollectStat(count: Int, bitmap: String)
case class UserStat(articleCount: Int, questionCount: Int, replyCount: Int, commentCount: Int, voteCount: Int, votedCount: Int, createTime: OffsetDateTime, updateTime: OffsetDateTime, lastLoginTime: OffsetDateTime, lastReplyTime: OffsetDateTime)
case class QuestionTimeStat(createTime: OffsetDateTime, updateTime: OffsetDateTime, lastViewTime: OffsetDateTime, lastVoteTime: OffsetDateTime)
case class ArticleTimeStat(createTime: OffsetDateTime, updateTime: OffsetDateTime, lastViewTime: OffsetDateTime, lastVoteTime: OffsetDateTime)
case class Reply(_id: String, content: String, editorType: String, author: Author, replyTime: OffsetDateTime, viewStat: ViewStat, voteStat: VoteStat, comments: List[Comment])
case class Comment(_id: String, content: String, editorType: String, commentator: Author, commentTime: OffsetDateTime)

