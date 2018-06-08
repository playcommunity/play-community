package controllers.admin

import java.time.OffsetDateTime
import javax.inject._

import akka.stream.Materializer
import cn.playscala.mongo.Mongo
import com.hankcs.hanlp.HanLP
import controllers.checkAdmin
import models.JsonFormats.siteSettingFormat
import models._
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import play.api.libs.json.Json._
import play.api.libs.json._
import play.api.mvc._
import services.{CommonService, ElasticService, MailerService}
import utils.HashUtil

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MigrateController @Inject()(cc: ControllerComponents, mongo: Mongo, commonService: CommonService, elasticService: ElasticService, mailer: MailerService)(implicit ec: ExecutionContext, mat: Materializer, parser: BodyParsers.Default) extends AbstractController(cc) {
  val settingCol = mongo.collection("common-setting")

  def migrateToOneDotTwo = checkAdmin.async {
    for {
      users <- mongo.collection[User].find[JsObject](obj("stat.createTime" -> obj("$type" -> "string"))).list()
      articles <- mongo.collection[Article].find[JsObject](obj("timeStat.createTime" -> obj("$type" -> "string"))).list()
      news <- mongo.collection[News].find[JsObject](obj("createTime" -> obj("$type" -> "string"))).list()
      docs <- mongo.collection[Doc].find[JsObject](obj("timeStat.createTime" -> obj("$type" -> "string"))).list()
      docCatalogs <- mongo.collection[DocCatalog].find[JsObject](obj("createTime" -> obj("$type" -> "string"))).list()
      qas <- mongo.collection[QA].find[JsObject](obj("timeStat.createTime" -> obj("$type" -> "string"))).list()
      events <- mongo.collection[Event].find[JsObject](obj("createTime" -> obj("$type" -> "string"))).list()
      tweets <- mongo.collection[Tweet].find[JsObject](obj("createTime" -> obj("$type" -> "string"))).list()
      messages <- mongo.collection[Message].find[JsObject](obj("createTime" -> obj("$type" -> "string"))).list()
    } yield {
      articles.foreach { a =>
        val replies = (a \ "replies").as[JsArray].value.map{ r =>
          val comments = (r \ "comments").as[JsArray].value.map{ c =>
            c.as[JsObject] ++ Json.obj("commentTime" -> OffsetDateTime.parse((r \ "commentTime").as[String]).toInstant)
          }
          r.as[JsObject] ++ Json.obj("replyTime" -> OffsetDateTime.parse((r \ "replyTime").as[String]).toInstant)
          r.as[JsObject] ++ Json.obj("comments" -> comments)
        }

        mongo.updateOne[Article](
          obj("_id" -> (a \ "_id").as[String]),
          obj("$set" -> obj(
                "timeStat.createTime" -> OffsetDateTime.parse((a \ "timeStat" \ "createTime").as[String]).toInstant,
                "timeStat.updateTime" -> OffsetDateTime.parse((a \ "timeStat" \ "updateTime").as[String]).toInstant,
                "timeStat.lastViewTime" -> OffsetDateTime.parse((a \ "timeStat" \ "lastViewTime").as[String]).toInstant,
                "timeStat.lastVoteTime" -> OffsetDateTime.parse((a \ "timeStat" \ "lastVoteTime").as[String]).toInstant,
                "replies" -> replies
              ),
              "$unset" -> Json.obj("lastReply" -> 1)
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

      news.foreach { a =>
        mongo.updateOne[News](
          obj("_id" -> (a \ "_id").as[String]),
          obj("$set" ->
            obj(
              "createTime" -> OffsetDateTime.parse((a \ "createTime").as[String]).toInstant
            )
          )
        )
      }

      docs.foreach { a =>
        val replies = (a \ "replies").as[JsArray].value.map{ r =>
          val comments = (r \ "comments").as[JsArray].value.map{ c =>
            c.as[JsObject] ++ Json.obj("commentTime" -> OffsetDateTime.parse((r \ "commentTime").as[String]).toInstant)
          }
          r.as[JsObject] ++ Json.obj("replyTime" -> OffsetDateTime.parse((r \ "replyTime").as[String]).toInstant)
          r.as[JsObject] ++ Json.obj("comments" -> comments)
        }

        mongo.updateOne[Doc](
          obj("_id" -> (a \ "_id").as[String]),
          obj("$set" ->
            obj(
              "timeStat.createTime" -> OffsetDateTime.parse((a \ "timeStat" \ "createTime").as[String]).toInstant,
              "replies" -> replies
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

      qas.foreach { a =>
        val replies = (a \ "replies").as[JsArray].value.map{ r =>
          val comments = (r \ "comments").as[JsArray].value.map{ c =>
            c.as[JsObject] ++ Json.obj("commentTime" -> OffsetDateTime.parse((r \ "commentTime").as[String]).toInstant)
          }
          r.as[JsObject] ++ Json.obj("replyTime" -> OffsetDateTime.parse((r \ "replyTime").as[String]).toInstant)
          r.as[JsObject] ++ Json.obj("comments" -> comments)
        }

        mongo.updateOne[QA](
          obj("_id" -> (a \ "_id").as[String]),
          obj("$set" -> obj(
                "timeStat.createTime" -> OffsetDateTime.parse((a \ "timeStat" \ "createTime").as[String]).toInstant,
                "timeStat.updateTime" -> OffsetDateTime.parse((a \ "timeStat" \ "updateTime").as[String]).toInstant,
                "timeStat.lastViewTime" -> OffsetDateTime.parse((a \ "timeStat" \ "lastViewTime").as[String]).toInstant,
                "timeStat.lastVoteTime" -> OffsetDateTime.parse((a \ "timeStat" \ "lastVoteTime").as[String]).toInstant,
                "replies" -> replies
              ),
              "$unset" -> Json.obj("lastReply" -> 1)
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

      tweets.foreach { a =>
        val replies = (a \ "replies").as[JsArray].value.map{ r =>
          val comments = (r \ "comments").as[JsArray].value.map{ c =>
            c.as[JsObject] ++ Json.obj("commentTime" -> OffsetDateTime.parse((r \ "commentTime").as[String]).toInstant)
          }
          r.as[JsObject] ++ Json.obj("replyTime" -> OffsetDateTime.parse((r \ "replyTime").as[String]).toInstant)
          r.as[JsObject] ++ Json.obj("comments" -> comments)
        }

        mongo.updateOne[Tweet](
          obj("_id" -> (a \ "_id").as[String]),
          obj("$set" ->
            obj(
              "createTime" -> OffsetDateTime.parse((a \ "createTime").as[String]).toInstant,
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

      Ok("Finish.")
    }
  }

}
