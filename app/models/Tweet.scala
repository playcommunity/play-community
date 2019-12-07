package models

import java.time.Instant
import cn.playscala.mongo.annotations.Entity
import models.{Author, Reply, VoteStat}
import play.api.libs.json.{JsObject, Json}
import utils.DateTimeUtil

@Entity("common-tweet")
case class Tweet(_id: String, author: Author, title: String, content: String, images: List[String], createTime: Instant, voteStat: VoteStat, replyStat: ReplyStat = ReplyStat(0, Nil, None, None)){
  def toJson: JsObject = {
    Json.obj("_id" -> _id, "author" -> author, "content" -> content, "images" -> images, "replyCount" -> replyStat.replies.size, "voteCount" -> voteStat.count, "time" -> DateTimeUtil.toPrettyString(createTime))
  }
}