package controllers

import javax.inject._

import models._
import models.JsonFormats._
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import play.modules.reactivemongo.{MongoController, ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.play.json.collection.JSONCollection
import utils.{BitmapUtil, DateTimeUtil, HashUtil, RequestHelper}
import reactivemongo.play.json._
import models.JsonFormats._
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import reactivemongo.api.QueryOpts
import reactivemongo.bson.BSONObjectID

import scala.concurrent.{ExecutionContext, Future}
import java.time._

import services.EventService

@Singleton
class ArticleController @Inject()(cc: ControllerComponents, reactiveMongoApi: ReactiveMongoApi, eventService: EventService) (implicit ec: ExecutionContext, parser: BodyParsers.Default) extends AbstractController(cc) {
  def articleColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-article"))
  def categoryColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-category"))
  def userColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-user"))
  def msgColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-message"))

  def index(nav: String, path: String, page: Int) = Action.async { implicit request: Request[AnyContent] =>
    val cPage = if(page < 1){1}else{page}
    var q = Json.obj("categoryPath" -> Json.obj("$regex" -> s"^${path}"))
    var sort = Json.obj("timeStat.createTime" -> -1)
    nav match {
      case "1" =>
        q ++= Json.obj()
        sort = Json.obj("lastReply.replyTime" -> -1)
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
      topArticles <- articleCol.find(Json.obj("$or" -> Json.arr(Json.obj("top" -> true), Json.obj("recommended" -> true)))).cursor[Article]().collect[List](5)
      articles <- articleCol.find(q).sort(sort).options(QueryOpts(skipN = (cPage-1) * 15, batchSizeN = 15)).cursor[Article]().collect[List](15)
      topViewArticles <- articleCol.find(Json.obj()).sort(Json.obj("viewStat.count" -> -1)).cursor[Article]().collect[List](10)
      topReplyArticles <- articleCol.find(Json.obj()).sort(Json.obj("replyStat.count" -> -1)).cursor[Article]().collect[List](10)
      total <- articleCol.count(None)
    } yield {
      if (total > 0 && cPage > math.ceil(total/15.0).toInt) {
        Redirect(routes.ArticleController.index(nav, path, math.ceil(total/15.0).toInt))
      } else {
        Ok(views.html.article.index(nav, path, topArticles, articles, topViewArticles, topReplyArticles, cPage, total))
      }
    }
  }

  def add = (checkLogin andThen checkActive).async { implicit request: Request[AnyContent] =>
    for {
      categoryCol <- categoryColFuture
      categoryList <- categoryCol.find(Json.obj("parentPath" -> "/", "disabled" -> false)).cursor[Category]().collect[List]()
    } yield {
      Ok(views.html.article.add(None, categoryList))
    }
  }

  def edit(_id: String) = checkAdminOrOwner("_id").async { implicit request: Request[AnyContent] =>
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

  def doAdd = (checkActive andThen checkAdminOrOwner("_id")).async { implicit request: Request[AnyContent] =>
    Form(tuple("_id" -> optional(text), "title" -> nonEmptyText,"content" -> nonEmptyText, "keywords" -> nonEmptyText, "categoryPath" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(views.html.message("系统提示", "您的输入有误！"))),
      tuple => {
        val (_idOpt, title, content, keywords, categoryPath) = tuple
        for {
          articleCol <- articleColFuture
          categoryCol <- categoryColFuture
          category <- categoryCol.find(Json.obj("path" -> categoryPath)).one[Category]
          wr <-  _idOpt match {
                  case Some(_id) =>
                    eventService.updateResource(RequestHelper.getAuthor, _id, "article", title)
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
                    val _id = RequestHelper.generateId
                    eventService.createResource(RequestHelper.getAuthor, _id, "article", title)
                    articleCol.insert(Article(_id, title, content, keywords, "lay-editor", RequestHelper.getAuthor, categoryPath, category.map(_.name).getOrElse("-"), List.empty[String], List.empty[Reply], None, ViewStat(0, ""), VoteStat(0, ""), ReplyStat(0, 0, ""),  CollectStat(0, ""), ArticleTimeStat(DateTimeUtil.now, DateTimeUtil.now, DateTimeUtil.now, DateTimeUtil.now), false, false))
                }
        } yield {
          Redirect(routes.ArticleController.index("0", categoryPath, 1))
        }
      }
    )
  }

  def doSetTop = checkAdmin.async { implicit request: Request[AnyContent] =>
    Form(tuple("_id" -> nonEmptyText, "field" -> nonEmptyText, "status" -> boolean)).bindFromRequest().fold(
      errForm => Future.successful(Ok(Json.obj("status" -> 1, "msg" -> "输入有误！"))),
      tuple => {
        val (_id, field, status) = tuple
        val modifier = field match {
          case "stick" => Json.obj("$set" -> Json.obj("top" -> status))
          case _ => Json.obj("$set" -> Json.obj("recommended" -> status))
        }
        for{
          articleCol <- articleColFuture
          wr <- articleCol.update(Json.obj("_id" -> _id), modifier)
        } yield {
          Ok(Json.obj("status" -> 0))
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
        case Some(a) =>
          request.session.get("uid") match {
            case Some(uid) =>
              val uid = request.session("uid").toInt
              val viewBitmap = BitmapUtil.fromBase64String(a.viewStat.bitmap)
              if (!viewBitmap.contains(uid)) {
                viewBitmap.add(uid)
                articleCol.update(Json.obj("_id" -> _id), Json.obj("$set" -> Json.obj("viewStat" -> ViewStat(a.viewStat.count + 1, BitmapUtil.toBase64String(viewBitmap)))))
                Ok(views.html.article.detail(a.copy(viewStat = a.viewStat.copy(count = a.viewStat.count + 1))))
              } else {
                Ok(views.html.article.detail(a))
              }
            case None =>
              Ok(views.html.article.detail(a))
          }
        case None => Redirect(routes.Application.notFound)
      }
    }
  }

}
