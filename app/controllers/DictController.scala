package controllers

import java.time.Instant

import akka.stream.Materializer
import cn.playscala.mongo.Mongo
import domain.infrastructure.repository.mongo.MongoWordRepository
import javax.inject._
import models._
import play.api.libs.json.{JsNull, JsObject, Json}
import play.api.mvc._
import services.{CommonService, EventService}
import utils.{DateTimeUtil, RequestHelper}

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json.Json._

@Singleton
class DictController @Inject()(cc: ControllerComponents, mongo: Mongo, commonService: CommonService, eventService: EventService, wordRepository: MongoWordRepository)(implicit ec: ExecutionContext, mat: Materializer, parser: BodyParsers.Default) extends AbstractController(cc) {

  private val DEFAULT_LIMIT_SIZE_15 = 15
  private val DEFAULT_LIMIT_SIZE_100 = 100

  def index = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.dict.index())
  }

  def add(_id: String) = checkLogin(parser, ec) { implicit request: Request[AnyContent] =>
    Ok(views.html.dict.edit(_id, None))
  }

  def edit(_id: String) = checkLogin(parser, ec).async { implicit request: Request[AnyContent] =>
    wordRepository.findById(_id).map {
      case Some(word) =>
        if (word.creator == RequestHelper.getLogin || RequestHelper.isAdmin) {
          Ok(views.html.dict.edit(_id, Some(word)))
        } else {
          Ok("抱歉，您无权编辑该词")
        }
      case None => Ok("该词不存在")
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
        wordRepository.add(Word(_id.toLowerCase(), content, pronounce, Nil, tags.split("\\,|，").toList.filter(_.trim != ""), audioUrl, audioType, 0, false, RequestHelper.getLogin, "", Instant.now(), Instant.now()))
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
        wordRepository.update(
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
    wordRepository.update(_id, obj("$set" -> obj("isReviewed" -> true)))
    wordRepository.findReviewedList(1).map { list =>
      if (list.nonEmpty) {
        Redirect(routes.DictController.query(list.head._id))
      } else {
        Ok("无待审核单词")
      }
    }
  }

  def remove(_id: String) = checkAdmin(parser, ec).async { implicit request: Request[AnyContent] =>
    wordRepository.deleteById(_id)
    wordRepository.findReviewedList(1).map { list =>
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
      opt <- wordRepository.findById(word)
      topList <- commonService.getObjListByDictCol(obj("isReviewed" -> true), Some(Json.obj("_id" -> 1)), Json.obj("viewCount" -> -1), DEFAULT_LIMIT_SIZE_15)
    } yield {
      if (opt.isEmpty) {
        commonService.updateOneByDictQuery(word, obj(
          "$inc" -> obj("count" -> 1),
          "$set" -> obj("updateTime" -> Instant.now()),
          "$setOnInsert" -> obj("createTime" -> Instant.now())
        ))
      } else {
        commonService.updateOneByDictQuery(word, obj("$inc" -> obj("viewCount" -> 1)))
      }

      Ok(views.html.dict.search(word, opt, topList.map(obj => (obj \ "_id").as[String])))
    }
  }

  def viewTag(tag: String) = Action.async { implicit request: Request[AnyContent] =>
    val q = obj("isReviewed" -> true, "tags" -> tag)
    commonService.getWordListByDictCol(q, None, Json.obj("viewCount" -> -1), DEFAULT_LIMIT_SIZE_100).map(list => {
      Ok(views.html.dict.viewTag(tag, list))
    })
  }

}
