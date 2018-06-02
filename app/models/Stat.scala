package models

import java.time.OffsetDateTime
import cn.playscala.mongo.annotations.Entity

/**
  * 记录用户收藏的资源
  * @param resType article/question/document/file
  */
@Entity("stat-collect")
case class StatCollect(_id: String, uid: String, resType: String, resId: String, resOwner: Author, resTitle: String, resCreateTime: OffsetDateTime, collectTime: OffsetDateTime)

@Entity("stat-traffic")
case class StatTraffic(_id: String, hourStr: String,  count: Long, userCount: Long, visitorCount: Long,  createTime: OffsetDateTime, updateTime: OffsetDateTime)

@Entity("stat-ip")
case class StatIP(_id: String, ip: String, hourStr: String,  count: Long, userCount: Long, visitorCount: Long,  createTime: OffsetDateTime, updateTime: OffsetDateTime)

@Entity("stat-visitor")
case class StatVisitor(_id: String, uid: String, isVisitor: Boolean, hourStr: String, count: Long, createTime: OffsetDateTime, updateTime: OffsetDateTime)