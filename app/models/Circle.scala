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

@Entity("common-leader")
case class Leader(
  _id: String,
  name: String,
  avatar: String,
  description: String,
  email: Option[String],
  github: Option[String],
  site: Option[Site],
  voteStat: VoteStat,
  active: Boolean,
  createTime: Instant,
  updateTime: Instant
)

case class Site(url: String, crawlUrl: String, crawlType: String, crawlSelector: String, selectorType:String, updateTime: Instant, createTime: Instant)

object SiteUpdateType {
  val SELECTOR = "selector"
  val TIME_TAG_SELECTOR = "time-tag-selector"
  val RSS = "rss"
  val ATOM = "atom"
  val CNBLOGS = "cnblogs"
  val ZHIHU = "zhihu"
  val SCALA = "scala"
  val SCALACOOL = "scala-cool"
  val JUEJIN = "juejin"
}