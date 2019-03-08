package models

import java.time.Instant

import cn.playscala.mongo.annotations.Entity

@Entity("common-dict")
case class Word(_id: String, content: String, pronounce: String, relateWords: List[String], tags: List[String], audioUrl: Option[String], audioType: Option[String], viewCount: Long, createTime: Instant, updateTime: Instant)
