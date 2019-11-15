package models

import java.time.Instant

import cn.playscala.mongo.annotations.Entity

@Entity("common-category")
case class Board (
  _id: String,
  name: String,
  description: String,
  avatarUrl: String,
  path: String,
  parentPath: String,
  index: Int,
  disabled: Boolean,
  owner: Option[Owner],
  createTime: Instant,
  updateTime: Instant
) {
  def getCategories() {}


}