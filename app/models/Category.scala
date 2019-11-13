package models

import java.time.Instant

import cn.playscala.mongo.annotations.Entity

@Entity("common-category")
case class Category(
  _id: String,
  name: String,
  path: String,
  parentPath: String,
  index: Int,
  disabled: Boolean,
  ownerId: Option[String],
  ownerName: Option[String],
  ownerAvatarUrl: Option[String],
  createTime: Instant,
  updateTime: Instant
)
