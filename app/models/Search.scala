package models

/**
  * Created by joymufeng on 2017/7/15.
  */

case class IndexedDocument(id: String, plate: String, title: String, content: String, author: String, authorId: String, createTime: Long, viewCount: Int, replyCount: Int, voteCount: Int, highlight: Option[String])
