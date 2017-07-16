package models

import java.time.OffsetDateTime

/**
  * Created by joymufeng on 2017/7/14.
  */
/**
  * 记录用户收藏的资源
  * @param resType article/question/document/file
  */
case class StatCollect(_id: String, uid: Author, resType: String, resId: String, resOwner: Author, resTitle: String, resCreateTime: OffsetDateTime, collectTime: OffsetDateTime)

case class StatTraffic(_id: String, hourStr: String,  count: Long, userCount: Long, visitorCount: Long,  createTime: OffsetDateTime, updateTime: OffsetDateTime)
case class StatIP(_id: String, ip: String, hourStr: String,  count: Long, userCount: Long, visitorCount: Long,  createTime: OffsetDateTime, updateTime: OffsetDateTime)
case class StatVisitor(_id: String, uid: String, isVisitor: Boolean, hourStr: String, count: Long, createTime: OffsetDateTime, updateTime: OffsetDateTime)