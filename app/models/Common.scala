package models

import java.time.Instant

case class Owner(_id: String, login: String, name: String, headImg: String, description: String)

case class Author(_id: String, login: String, name: String, headImg: String)

case class Reply(_id: String, content: String, editorType: String, author: Author, at: List[String], createTime: Instant, updateTime: Instant, voteStat: VoteStat, comments: List[Comment])

case class Comment(_id: String, content: String, editorType: String, commentator: Author, at: List[String], createTime: Instant, updateTime: Instant)

case class ReplyStat(replyCount: Int, replies: List[Reply], bestReply: Option[Reply], lastReply: Option[Reply])

case class ViewStat(count: Int, bitmap: String)

case class VoteStat(count: Int, bitmap: String)

case class CollectStat(count: Int, bitmap: String)