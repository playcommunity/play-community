package models

import java.time.Instant
import cn.playscala.mongo.annotations.Entity

/**
  * 记录用户收藏的资源
  * @param resType article/question/document/file
  */
@Entity("stat-collect")
case class StatCollect(_id: String, uid: String, resType: String, resId: String, resOwner: Author, resTitle: String, resCreateTime: Instant, collectTime: Instant)

@Entity("stat-traffic")
case class StatTraffic(_id: String, hourStr: String,  count: Long, userCount: Long, visitorCount: Long,  createTime: Instant, updateTime: Instant)

@Entity("stat-ip")
case class StatIP(_id: String, ip: String, hourStr: String,  count: Long, userCount: Long, visitorCount: Long,  createTime: Instant, updateTime: Instant)

@Entity("stat-visitor")
case class StatVisitor(_id: String, uid: String, isVisitor: Boolean, hourStr: String, count: Long, createTime: Instant, updateTime: Instant)

@Entity("stat-board")
case class StatBoard(boardPath: String, followers: Option[Seq[String]])

@Entity("stat-board-traffic")
case class StatBoardTraffic(boardPath: String, uid: String, dayStr: String, count: Long, createTime: Instant, updateTime: Instant)