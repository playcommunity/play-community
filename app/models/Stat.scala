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
