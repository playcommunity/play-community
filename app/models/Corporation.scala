package models

import java.time.Instant
import cn.playscala.mongo.annotations.Entity
import models.{Author, ViewStat, VoteStat}

@Entity("common-corporation")
case class Corporation(
  _id: String,
  title: String,
  city: String,
  grade: String,
  url: String,
  logo: String,
  description: String,
  author: Author,
  isChinese: Boolean,
  voteStat: VoteStat,
  viewStat: ViewStat,
  active: Boolean,
  createTime: Instant,
  updateTime: Instant
)
