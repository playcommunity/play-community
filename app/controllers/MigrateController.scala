package controllers

import java.time.OffsetDateTime
import javax.inject._

import akka.stream.Materializer
import cn.playscala.mongo.Mongo
import models._
import play.api.libs.json.Json._
import play.api.libs.json._
import play.api.mvc._
import services.{CommonService, ElasticService, MailerService}

import scala.concurrent.ExecutionContext

@Singleton
class MigrateController @Inject()(cc: ControllerComponents, mongo: Mongo, commonService: CommonService, elasticService: ElasticService, mailer: MailerService)(implicit ec: ExecutionContext, mat: Materializer, parser: BodyParsers.Default) extends AbstractController(cc) {
  val settingCol = mongo.collection("common-setting")

  def migrateForm110To120 = Action.async {
    for {
      users <- mongo.collection[User].find[JsObject](obj("stat.createTime" -> obj("$type" -> "string"))).list()
      resources <- mongo.collection[Resource].find[JsObject](obj("timeStat.createTime" -> obj("$type" -> "string"))).list()
      tweets <- mongo.collection[Tweet].find[JsObject](obj("createTime" -> obj("$type" -> "string"))).list()
      docCatalogs <- mongo.collection[DocCatalog].find[JsObject](obj("createTime" -> obj("$type" -> "string"))).list()
      events <- mongo.collection[Event].find[JsObject](obj("createTime" -> obj("$type" -> "string"))).list()
      messages <- mongo.collection[Message].find[JsObject](obj("createTime" -> obj("$type" -> "string"))).list()
    } yield {
      resources.foreach { a =>
        val replies = (a \ "replies").as[JsArray].value.map{ r =>
          var rObj = r.as[JsObject]
          val comments = (r \ "comments").as[JsArray].value.map{ c =>
            var obj = c.as[JsObject]
            val time = OffsetDateTime.parse((c \ "commentTime").as[String]).toInstant
            obj ++= Json.obj("createTime" -> time)
            obj ++= Json.obj("updateTime" -> time)
            obj
          }

          rObj ++= Json.obj("at" -> List.empty[String])
          rObj ++= Json.obj("comments" -> comments)

          val time = OffsetDateTime.parse((r \ "replyTime").as[String]).toInstant
          rObj ++= Json.obj("createTime" -> time)
          rObj ++= Json.obj("updateTime" -> time)
          rObj
        }

        mongo.updateOne[Resource](
          obj("_id" -> (a \ "_id").as[String]),
          obj("$set" -> obj(
                "createTime" -> OffsetDateTime.parse((a \ "timeStat" \ "createTime").as[String]).toInstant,
                "updateTime" -> OffsetDateTime.parse((a \ "timeStat" \ "updateTime").as[String]).toInstant,
                "replyCount" -> replies.size,
                "replies" -> replies,
                "top" -> false,
                "recommended" -> false,
                "closed" -> false
              ),
              "$unset" -> Json.obj("lastReply" -> 1, "timeStat" -> 1)
          )
        )
      }

      users.foreach { a =>
        mongo.updateOne[User](
          obj("_id" -> (a \ "_id").as[String]),
          obj("$set" ->
            obj(
              "stat.createTime" -> OffsetDateTime.parse((a \ "stat" \ "createTime").as[String]).toInstant,
              "stat.updateTime" -> OffsetDateTime.parse((a \ "stat" \ "updateTime").as[String]).toInstant,
              "stat.lastLoginTime" -> OffsetDateTime.parse((a \ "stat" \ "lastLoginTime").as[String]).toInstant,
              "stat.lastReplyTime" -> OffsetDateTime.parse((a \ "stat" \ "lastReplyTime").as[String]).toInstant
            )
          )
        )
      }



      docCatalogs.foreach { a =>
        mongo.updateOne[DocCatalog](
          obj("_id" -> (a \ "_id").as[String]),
          obj("$set" ->
            obj(
              "createTime" -> OffsetDateTime.parse((a \ "createTime").as[String]).toInstant,
              "updateTime" -> OffsetDateTime.parse((a \ "updateTime").as[String]).toInstant
            )
          )
        )
      }

      tweets.foreach { a =>
        val replies = (a \ "replies").as[JsArray].value.map{ r =>
          var rObj = r.as[JsObject]
          val comments = (r \ "comments").as[JsArray].value.map{ c =>
            var obj = c.as[JsObject]
            val time = OffsetDateTime.parse((c \ "commentTime").as[String]).toInstant
            obj ++= Json.obj("createTime" -> time)
            obj ++= Json.obj("updateTime" -> time)
            obj
          }

          rObj ++= Json.obj("at" -> List.empty[String])
          rObj ++= Json.obj("comments" -> comments)

          val time = OffsetDateTime.parse((r \ "replyTime").as[String]).toInstant
          rObj ++= Json.obj("createTime" -> time)
          rObj ++= Json.obj("updateTime" -> time)
          rObj
        }

        mongo.updateOne[Tweet](
          obj("_id" -> (a \ "_id").as[String]),
          obj("$set" ->
            obj(
              "createTime" -> OffsetDateTime.parse((a \ "createTime").as[String]).toInstant,
              "updateTime" -> OffsetDateTime.parse((a \ "createTime").as[String]).toInstant,
              "replyCount" -> replies.size,
              "replies" -> replies
            )
          )
        )
      }

      messages.foreach { a =>
        mongo.updateOne[Message](
          obj("_id" -> (a \ "_id").as[String]),
          obj("$set" ->
            obj(
              "createTime" -> OffsetDateTime.parse((a \ "createTime").as[String]).toInstant
            )
          )
        )
      }

      events.foreach { a =>
        mongo.updateOne[Event](
          obj("_id" -> (a \ "_id").as[String]),
          obj("$set" ->
            obj(
              "createTime" -> OffsetDateTime.parse((a \ "createTime").as[String]).toInstant
            )
          )
        )
      }

      Ok("Finish.")
    }
  }

}
