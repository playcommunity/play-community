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
  owner: Option[Owner], // 版主
  createTime: Instant,
  updateTime: Instant
) {
  def getCategories() {}


}