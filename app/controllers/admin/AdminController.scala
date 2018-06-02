package controllers.admin

import java.io.ByteArrayOutputStream
import java.time.OffsetDateTime
import javax.inject._
import akka.stream.Materializer
import akka.util.ByteString
import cn.playscala.mongo.Mongo
import com.hankcs.hanlp.HanLP
import controllers.routes
import models.JsonFormats._
import models._
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import play.api.libs.json._
import play.api.libs.json.Json._
import play.api.mvc._
import models.JsonFormats.siteSettingFormat
import services.{CommonService, ElasticService, MailerService}
import utils.{DateTimeUtil, HashUtil}
import scala.concurrent.{ExecutionContext, Future}
import controllers.checkAdmin
import play.api.libs.json.Json._

@Singleton
class AdminController @Inject()(cc: ControllerComponents, mongo: Mongo, commonService: CommonService, elasticService: ElasticService, mailer: MailerService)(implicit ec: ExecutionContext, mat: Materializer, parser: BodyParsers.Default) extends AbstractController(cc) {
  val settingCol = mongo.getCollection("common-setting")

  def index = checkAdmin.async { implicit request: Request[AnyContent] =>
    Future.successful(Ok(views.html.admin.index()))
  }

  def base = checkAdmin.async { implicit request: Request[AnyContent] =>
    for {
      opt <- settingCol.findById("siteSetting")
    } yield {
      Ok(views.html.admin.setting.base(opt.map(_.as[SiteSetting]).getOrElse(App.siteSetting)))
    }
  }

  def doBaseSetting = checkAdmin.async { implicit request: Request[AnyContent] =>
    request.body.asJson match {
      case Some(obj) =>
        obj.validate[SiteSetting] match {
          case JsSuccess(s, _) =>
            App.siteSetting = s
            settingCol.updateOne(Json.obj("_id" -> "siteSetting"), Json.obj("$set" -> obj), upsert = true).map{ wr =>
              println(wr)
              Ok(Json.obj("status" -> 0))
            }
          case JsError(_) =>
            Future.successful(Ok(Json.obj("status" -> 1, "msg" -> "请求格式有误！")))
        }
      case None => Future.successful(Ok(Json.obj("status" -> 1, "msg" -> "请求格式有误！")))
    }
  }

  def category = checkAdmin.async { implicit request: Request[AnyContent] =>
    for{
      list <- mongo.find[Category]().list
    } yield {
      Ok(views.html.admin.category(list))
    }
  }

  // 临时实现，重命名时允许重名
  def doAddCategory = checkAdmin.async { implicit request: Request[AnyContent] =>
    Form(tuple("_id" -> optional(nonEmptyText), "name" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(views.html.message("系统提示", "您的输入有误！"))),
      tuple => {
        val (_idOpt, name) = tuple
        for{
          opt <- mongo.find[Category](Json.obj("name" -> name)).first
        } yield {
          val _id = _idOpt.getOrElse(HashUtil.md5(name.trim))
          mongo.updateOne[Category](
            Json.obj("_id" -> _id),
            Json.obj(
              "$set" -> Json.obj("name" -> name.trim),
              "$setOnInsert" -> Json.obj(
                "path" -> s"/${HanLP.convertToPinyinString(name.trim, "-", false)}",
                "parentPath" -> "/",
                "index" -> 0,
                "disabled" -> false
              )
            )
          )
          Ok(Json.obj("status" -> 0))
        }
      }
    )
  }

  def doRemoveCategory = checkAdmin.async { implicit request: Request[AnyContent] =>
    Form(single("_id" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(views.html.message("系统提示", "您的输入有误！"))),
      _id => {
        mongo.deleteById[Category](_id).map{ wr =>
          Ok(Json.obj("status" -> 0))
        }
      }
    )
  }

  def migrateToOneDotTwo = checkAdmin.async {
    for {
      articles <- mongo.getCollection[Article].find[JsObject](obj("timeStat.createTime" -> obj("$type" -> "string"))).list()
    } yield {
      /*articles.foreach { a =>
        mongo.updateOne[Article](
          obj("_id" -> (a \ "_id").as[String]),
          obj("$set" ->
            obj(
              /*"replyStat.count" -> BigDecimal((a \ "replyStat" \ "count").as[Int]),
              "replyStat.count" -> BigDecimal((a \ "replyStat" \ "count").as[Int]),
              "voteStat.count" -> BigDecimal((a \ "voteStat" \ "count").as[Int]),*/
              "timeStat.createTime" -> OffsetDateTime.parse((a \ "timeStat" \ "createTime").as[String]).toInstant,
              "timeStat.updateTime" -> OffsetDateTime.parse((a \ "timeStat" \ "updateTime").as[String]).toInstant,
              "timeStat.lastViewTime" -> OffsetDateTime.parse((a \ "timeStat" \ "lastViewTime").as[String]).toInstant,
              "timeStat.lastVoteTime" -> OffsetDateTime.parse((a \ "timeStat" \ "lastVoteTime").as[String]).toInstant
            )
          )
        )
      }*/
      mongo.updateOne[Article](obj("_id" -> "1-5b05bcb46700008800fc1f4c"), obj("$set" -> obj("viewStat.count" -> 111)))
      //articleCol.update(obj("_id" -> "1-5b05bcb46700008800fc1f4c"), obj("$set" -> obj("viewStat.count" -> 11231.10)))
      Ok("Finish.")
    }
  }

}
