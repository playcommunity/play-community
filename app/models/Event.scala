package models

import java.time.Instant
import cn.playscala.mongo.annotations.Entity

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