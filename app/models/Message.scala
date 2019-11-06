package models

import java.time.Instant
import cn.playscala.mongo.annotations.Entity

/**
  * 消息提醒
  * @param source system/article/qa
  * @param action view/vote/reply/comment
  */
@Entity("common-message")
case class Message(
  _id: String,
  uid: String,
  source: String,
  sourceId: String,
  sourceTitle: String,
  actor: Author,
  action: String,
  content: String,
  createTime: Instant,
  read: Boolean
)
