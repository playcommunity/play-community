package controllers

import java.time.Instant

import akka.stream.Materializer
import cn.playscala.mongo.Mongo
import javax.inject._
import models._
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import services.{CommonService, EventService}
import utils.{DateTimeUtil, RequestHelper}

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json.Json._

@Singleton
class DictController @Inject()(cc: ControllerComponents, mongo: Mongo, commonService: CommonService, eventService: EventService)(implicit ec: ExecutionContext, mat: Materializer, parser: BodyParsers.Default) extends AbstractController(cc) {
  val dictQueryCol = mongo.collection("dict-query")
  val dictCol = mongo.collection("common-dict")


  def index = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.dict.index())
  }

  def add(_id: String) = checkLogin(parser, ec) { implicit request: Request[AnyContent] =>
    Ok(views.html.dict.edit(_id, None))
  }

  def edit(_id: String) = checkLogin(parser, ec).async { implicit request: Request[AnyContent] =>
    for {
      opt <- mongo.findById[Word](_id)
    } yield {
      opt match {
        case Some(word) =>
          if (word.creator == RequestHelper.getLogin || RequestHelper.isAdmin) {
            Ok(views.html.dict.edit(_id, opt))
          } else {
            Ok("抱歉，您无权编辑该词")
          }
        case None => Ok("该词不存在")
      }
    }
  }

  /**
    * 匿名添加新词
    */
  def doAdd = Action { implicit request: Request[AnyContent] =>
    (for {
      js <- request.body.asJson
      _id <- (js \ "_id").asOpt[String]
      pronounce <- (js \ "pronounce").asOpt[String]
      tags <- (js \ "tags").asOpt[String]
      audioUrl <- (js \ "audioUrl").asOpt[String]
      audioType <- (js \ "audioType").asOpt[String]
      content <- (js \ "content").asOpt[String]
    } yield {
      if (_id.trim != "") {
        mongo.insertOne[Word](Word(_id.toLowerCase(), content, pronounce, Nil, tags.split("\\,|，").toList.filter(_.trim != ""), audioUrl, audioType, 0, false, RequestHelper.getLogin, "", Instant.now(), Instant.now()))
        Ok(obj("code" -> "0"))
      } else {
        Ok(obj("code" -> "1", "msg" -> "单词不能为空"))
      }
    }).getOrElse(Ok(obj("code" -> "1", "msg" -> "无效参数")))
  }

  /**
    * 编辑必须实名
    */
  def doEdit = checkLogin(parser, ec) { implicit request: Request[AnyContent] =>
    (for {
      js <- request.body.asJson
      _id <- (js \ "_id").asOpt[String]
      pronounce <- (js \ "pronounce").asOpt[String]
      tags <- (js \ "tags").asOpt[String]
      audioUrl <- (js \ "audioUrl").asOpt[String]
      audioType <- (js \ "audioType").asOpt[String]
      content <- (js \ "content").asOpt[String]
    } yield {
      if (_id.trim != "") {
        mongo.updateById[Word](
          _id.toLowerCase(),
          obj(
            "$set" -> obj(
              "pronounce" -> pronounce,
              "tags" -> tags.split("\\,|，").toList.filter(_.trim != ""),
              "audioUrl" -> audioUrl,
              "audioType" -> audioType,
              "content" -> content
            )
          )
        )
        Ok(obj("code" -> "0"))
      } else {
        Ok(obj("code" -> "1", "msg" -> "单词不能为空"))
      }
    }).getOrElse(Ok(obj("code" -> "1", "msg" -> "无效参数")))
  }

  def pass(_id: String) = checkAdmin(parser, ec).async { implicit request: Request[AnyContent] =>
    mongo.updateById[Word](_id, obj("$set" -> obj("isReviewed" -> true)))
    mongo.find[Word](obj("isReviewed" -> false)).limit(1).list().map { list =>
      if (list.nonEmpty) {
        Redirect(routes.DictController.query(list.head._id))
      } else {
        Ok("无待审核单词")
      }
    }
  }

  def remove(_id: String) = checkAdmin(parser, ec).async { implicit request: Request[AnyContent] =>
    mongo.deleteById[Word](_id)
    mongo.find[Word](obj("isReviewed" -> false)).limit(1).list().map { list =>
      if (list.nonEmpty) {
        Redirect(routes.DictController.query(list.head._id))
      } else {
        Ok("无待审核单词")
      }
    }
  }

  def query(_id: String) = Action.async { implicit request: Request[AnyContent] =>
    val word = _id.toLowerCase
    for {
      opt <- mongo.findById[Word](word)
      topList <- dictCol.find[JsObject](obj("isReviewed" -> true)).projection(Json.obj("_id" -> 1)).sort(Json.obj("viewCount" -> -1)).limit(15).list()
    } yield {
      if (opt.isEmpty) {
        dictQueryCol.updateOne(
          obj("_id" -> word),
          obj(
            "$inc" -> obj("count" -> 1),
            "$set" -> obj("updateTime" -> Instant.now()),
            "$setOnInsert" -> obj("createTime" -> Instant.now())
          ),
          true
        )
      } else {
        dictCol.updateOne(obj("_id" -> word), obj("$inc" -> obj("viewCount" -> 1)))
      }

      Ok(views.html.dict.search(word, opt, topList.map(obj => (obj \ "_id").as[String])))
    }
  }

  def viewTag(tag: String) = Action.async { implicit request: Request[AnyContent] =>
    val q = obj("isReviewed" -> true, "tags" -> tag)
    for {
      list <- dictCol.find[Word](q).sort(Json.obj("viewCount" -> -1)).limit(100).list()
    } yield {
      Ok(views.html.dict.viewTag(tag, list))
    }
  }

}
