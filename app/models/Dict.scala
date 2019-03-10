package models

import java.time.Instant

import cn.playscala.mongo.annotations.Entity

@Entity("common-dict")
case class Word(_id: String, content: String, pronounce: String, relateWords: List[String], tags: List[String], audioUrl: String, audioType: String, viewCount: Long, isReviewed: Boolean, creator: String, reviewer: String, createTime: Instant, updateTime: Instant)
