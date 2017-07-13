package controllers

import javax.inject._

import models._
import models.JsonFormats._
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import play.modules.reactivemongo.{MongoController, ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.play.json.collection.JSONCollection
import utils.{DateTimeUtil, HashUtil, BitmapUtil}
import reactivemongo.play.json._
import models.JsonFormats._
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import reactivemongo.api.QueryOpts
import reactivemongo.bson.BSONObjectID

import scala.concurrent.Future
import java.time._

@Singleton
class ArticleController @Inject()(val reactiveMongoApi: ReactiveMongoApi) extends Controller {
  def articleColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-article"))
  def categoryColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-category"))
  def userColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-user"))


  def index(nav: String, page: Int) = Action.async { implicit request: Request[AnyContent] =>

    //println(Json.obj("time" -> OffsetDateTime.now(ZoneOffset.ofHours(8))))

    val cPage = if(page < 1){1}else{page}
    var q = Json.obj()
    var sort = Json.obj("timeStat.createTime" -> -1)
    nav match {
      case "1" =>
        q ++= Json.obj()
        sort = Json.obj("timeStat.lastReplyTime" -> -1)
      case "2" =>
        q ++= Json.obj()
        sort = Json.obj("timeStat.createTime" -> -1)
      case "3" =>
        q ++= Json.obj("replies.0" -> Json.obj("$exists" -> false))
        sort = Json.obj("timeStat.createTime" -> -1)
      case "4" =>
        q ++= Json.obj("recommended" -> true)
        sort = Json.obj("timeStat.lastReplyTime" -> -1)
      case _ =>
    }
    for {
      articleCol <- articleColFuture
      articles <- articleCol.find(q).sort(sort).options(QueryOpts(skipN = (cPage-1) * 15, batchSizeN = 15)).cursor[Article]().collect[List](15)
      total <- articleCol.count(None)
    } yield {
      if (total > 0 && cPage > math.ceil(total/15.0).toInt) {
        Redirect(routes.ArticleController.index(nav, math.ceil(total/15.0).toInt))
      } else {
        Ok(views.html.article.index(nav, articles, cPage, total))
      }
    }
  }

  def add = Action.async { implicit request: Request[AnyContent] =>
    for {
      categoryCol <- categoryColFuture
      categoryList <- categoryCol.find(Json.obj("parentPath" -> "/")).cursor[Category]().collect[List]()
    } yield {
      Ok(views.html.article.add(None, categoryList))
    }
  }

  def edit(_id: String) = Action.async { implicit request: Request[AnyContent] =>
    for {
      articleCol <- articleColFuture
      article <- articleCol.find(Json.obj("_id" -> _id)).one[Article]
      categoryCol <- categoryColFuture
      categoryList <- categoryCol.find(Json.obj("parentPath" -> "/")).cursor[Category]().collect[List]()
    } yield {
      article match {
        case Some(a) => Ok(views.html.article.add(Some(a), categoryList))
        case None => Redirect(routes.Application.notFound)
      }
    }
  }

  def doAdd = Action.async { implicit request: Request[AnyContent] =>
    Form(tuple("_id" -> optional(text), "title" -> nonEmptyText,"content" -> nonEmptyText, "categoryPath" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok("err")),
      tuple => {
        val (_idOpt, title, content, categoryPath) = tuple
        for {
          articleCol <- articleColFuture
          categoryCol <- categoryColFuture
          category <- categoryCol.find(Json.obj("path" -> categoryPath)).one[Category]
          wr <-  _idOpt match {
                  case Some(_id) =>
                    articleCol.update(Json.obj("_id" -> _id), Json.obj("$set" -> Json.obj(
                      "title" -> title,
                      "content" -> content,
                      "categoryPath" -> categoryPath,
                      "categoryName" -> category.map(_.name).getOrElse[String]("-"),
                      "author.name" -> request.session("name"),
                      "author.headImg" -> request.session("headImg"),
                      "timeStat.updateTime" -> DateTimeUtil.now()
                    )))
                  case None =>
                    articleCol.insert(Article(BSONObjectID.generate().stringify, title, content, "lay-editor", Author(request.session("uid"), request.session("login"), request.session("name"), request.session("headImg")), categoryPath, category.map(_.name).getOrElse("-"), List.empty[String], List.empty[Reply], None, ViewStat(0, ""), VoteStat(0, ""), CollectStat(0, ""), ArticleTimeStat(DateTimeUtil.now, DateTimeUtil.now, DateTimeUtil.now, DateTimeUtil.now), false, false))
                }
        } yield {
          Redirect(routes.ArticleController.index("0", 1))
        }
      }
    )
  }

  def doReply = Action.async { implicit request: Request[AnyContent] =>
    Form(tuple("_id" -> nonEmptyText,"content" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok("err")),
      tuple => {
        val (_id, content) = tuple
        val reply = Reply(BSONObjectID.generate().stringify, content, "lay-editor", Author("", request.session("login"), "沐风", ""), DateTimeUtil.now(), ViewStat(0, ""), VoteStat(0, ""), List.empty[Comment])
        articleColFuture.flatMap(_.update(
          Json.obj("_id" -> _id),
          Json.obj(
            "$push" -> Json.obj("replies" -> reply),
            "$set" -> Json.obj("lastReply" -> reply)
          ))).map{ wr =>
          Redirect(routes.ArticleController.index("0", 1))
        }
      }
    )
  }

  def view(_id: String) = Action.async { implicit request: Request[AnyContent] =>
    for {
      articleCol <- articleColFuture
      article <- articleCol.find(Json.obj("_id" -> _id)).one[Article]
    } yield {
      article match {
        case Some(a) => Ok(views.html.article.detail(a))
        case None => Redirect(routes.Application.notFound)
      }
    }
  }

  def vote(articleId: String, up: Boolean) = Action.async { implicit request: Request[AnyContent] =>

    val login = "joymufeng1"
    (for {
      articleCol <- articleColFuture
      objOpt <- articleCol.find(Json.obj("_id" -> articleId)).one[JsObject]
    } yield {
      //articleCol.insert(Article("0", "", "", "", "", "", "", "", "", List.empty[String], LocalDateTime.now(LocalDateTimeZone.UTC), LocalDateTime.now(LocalDateTimeZone.UTC), List.empty[Reply], 0, List.empty[Long], 0, List.empty[Long], 0)).foreach(println _)

      objOpt.fold(Ok(Json.obj("success" -> false))){ obj =>
        val bitmapStr = (obj \ "viewBitMap").as[String].trim
        val bitmap = BitmapUtil.fromBase64String(bitmapStr)
        val userHash = HashUtil.toInt(login)
        println(bitmap.contains(userHash))
        if (!bitmap.contains(userHash)) {
          println("add user")
          bitmap.add(userHash)
          articleCol.update(Json.obj("_id" -> articleId), Json.obj("$inc" -> Json.obj("voteCount" -> 1), "$set" -> Json.obj("viewBitMap" -> BitmapUtil.toBase64String(bitmap))))
        } else {
          println("Already voted.")
        }

        Ok(Json.obj("success" -> false))
      }

    }).recover{ case t: Throwable =>
      println(t.getMessage)
      t.printStackTrace()
      Ok("error")
    }
  }
}
