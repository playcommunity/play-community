package controllers

import javax.inject._

import models._
import models.JsonFormats._
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import play.modules.reactivemongo.{MongoController, ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.play.json.collection.JSONCollection
import utils.{HashUtil, RoaringBitmapUtil}
import reactivemongo.play.json._
import models.JsonFormats._
import org.joda.time.DateTime
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import reactivemongo.api.QueryOpts
import reactivemongo.bson.BSONObjectID

import scala.concurrent.Future

@Singleton
class ArticleController @Inject()(val reactiveMongoApi: ReactiveMongoApi) extends Controller {
  def articleColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-article"))
  def categoryColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-category"))

  def index(nav: String, page: Int) = Action.async { implicit request: Request[AnyContent] =>
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
      Ok(views.html.article.add(categoryList))
    }
  }

  def doAdd = Action.async { implicit request: Request[AnyContent] =>
    Form(tuple("title" -> nonEmptyText,"content" -> nonEmptyText, "categoryPath" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok("err")),
      tuple => {
        val (title, content, categoryPath) = tuple
        for {
          articleCol <- articleColFuture
          categoryCol <- categoryColFuture
          category <- categoryCol.find(Json.obj("path" -> categoryPath)).one[Category]
          wr <- articleCol.insert(Article(BSONObjectID.generate().stringify, title, content, "lay-editor", Author("", request.session("login"), "沐风", ""), categoryPath, category.map(_.name).getOrElse("-"), List.empty[String], List.empty[Reply], ViewStat(0, ""), VoteStat(0, ""), ArticleTimeStat(DateTime.now, DateTime.now, DateTime.now, DateTime.now, DateTime.now), false, false))
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
        articleColFuture.flatMap(_.update(Json.obj("_id" -> _id), Json.obj("$push" -> Json.obj("replies" -> Reply(BSONObjectID.generate().stringify, content, "lay-editor", Author("", request.session("login"), "沐风", ""), DateTime.now(), ViewStat(0, ""), VoteStat(0, ""), List.empty[Comment]))))).map{ wr =>
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
      //articleCol.insert(Article("0", "", "", "", "", "", "", "", "", List.empty[String], DateTime.now(DateTimeZone.UTC), DateTime.now(DateTimeZone.UTC), List.empty[Reply], 0, List.empty[Long], 0, List.empty[Long], 0)).foreach(println _)

      objOpt.fold(Ok(Json.obj("success" -> false))){ obj =>
        val bitmapStr = (obj \ "viewBitMap").as[String].trim
        val bitmap = RoaringBitmapUtil.fromBase64String(bitmapStr)
        val userHash = HashUtil.toInt(login)
        println(bitmap.contains(userHash))
        if (!bitmap.contains(userHash)) {
          println("add user")
          bitmap.add(userHash)
          articleCol.update(Json.obj("_id" -> articleId), Json.obj("$inc" -> Json.obj("voteCount" -> 1), "$set" -> Json.obj("viewBitMap" -> RoaringBitmapUtil.toBase64String(bitmap))))
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
