package controllers

import java.time.Instant
import javax.inject._
import models._
import play.api.libs.json.Json
import play.api.mvc._
import utils.{BitmapUtil, RequestHelper}
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import scala.concurrent.{ExecutionContext, Future}
import cn.playscala.mongo.Mongo
import services.EventService

@Singleton
class ArticleController @Inject()(cc: ControllerComponents, mongo: Mongo, eventService: EventService) (implicit ec: ExecutionContext, parser: BodyParsers.Default) extends AbstractController(cc) {

  def test = Action { implicit request =>

    mongo.find[Article].fetch[Author]("authorId").list().map{ _.map{ t =>
        val (article, author) = t
      }
    }
    /*mongo.insertOne[Test](Test("1", Instant.now())).map(_ => println("inserted.")).recover{ case t: Throwable =>
      println(t.getMessage)
      t.printStackTrace()
    }*/

    //mongo.find()

    /*try{
      mongo.find()
    } catch { case t: Throwable =>
      println(t.getMessage)
      t.printStackTrace()
    }*/

    Ok("Finish.")
  }

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
      topArticles <- mongo.find[Article](Json.obj("$or" -> Json.arr(Json.obj("top" -> true), Json.obj("recommended" -> true)))).limit(5).list()
      articles <- mongo.find[Article](q).sort(sort).skip((cPage-1) * 15).limit(15).list()
      topViewArticles <- mongo.find[Article]().sort(Json.obj("viewStat.count" -> -1)).limit(10).list()
      topReplyArticles <- mongo.find[Article]().sort(Json.obj("replyStat.count" -> -1)).limit(10).list()
      total <- mongo.count[Article]()
    } yield {
      if (total > 0 && cPage > math.ceil(total/15.0).toInt) {
        Redirect(routes.ArticleController.index(nav, path, math.ceil(total/15.0).toInt))
      } else {
        Ok(views.html.article.index(nav, path, topArticles, articles, topViewArticles, topReplyArticles, cPage, total.toInt))
      }
    }
  }

  def add = (checkLogin andThen checkActive).async { implicit request: Request[AnyContent] =>
    for {
      categoryList <- mongo.find[Category](Json.obj("parentPath" -> "/", "disabled" -> false)).list()
    } yield {
      Ok(views.html.article.add(None, categoryList))
    }
  }

  def edit(_id: String) = checkAdminOrOwner("_id").async { implicit request: Request[AnyContent] =>
    (for {
      article <- mongo.find[Article](Json.obj("_id" -> _id)).first
      categoryList <- mongo.find[Category](Json.obj("parentPath" -> "/")).list()
    } yield {
      article match {
        case Some(a) => Ok(views.html.article.add(Some(a), categoryList))
        case None => Redirect(routes.Application.notFound)
      }
    }).recover{ case t: Throwable =>
      println(t.getMessage)
      t.printStackTrace()
      Ok(t.getMessage)
    }
  }

  def doAdd = (checkActive andThen checkAdminOrOwner("_id")).async { implicit request: Request[AnyContent] =>
    Form(tuple("_id" -> optional(text), "title" -> nonEmptyText,"content" -> nonEmptyText, "keywords" -> nonEmptyText, "categoryPath" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(views.html.message("系统提示", "您的输入有误！"))),
      tuple => {
        val (_idOpt, title, content, keywords, categoryPath) = tuple
        for {
          category <- mongo.find[Category](Json.obj("path" -> categoryPath)).first
          wr <-  _idOpt match {
                  case Some(_id) =>
                    eventService.updateResource(RequestHelper.getAuthor, _id, "article", title)
                    mongo.updateOne[Article](Json.obj("_id" -> _id), Json.obj("$set" -> Json.obj(
                      "title" -> title,
                      "content" -> content,
                      "keywords" -> keywords,
                      "categoryPath" -> categoryPath,
                      "categoryName" -> category.map(_.name).getOrElse[String]("-"),
                      "author.name" -> request.session("name"),
                      "author.headImg" -> request.session("headImg"),
                      "timeStat.updateTime" -> Instant.now()
                    )))
                  case None =>
                    val _id = RequestHelper.generateId
                    eventService.createResource(RequestHelper.getAuthor, _id, "article", title)
                    mongo.updateOne[User](Json.obj("_id" -> RequestHelper.getUid), Json.obj("$inc" -> Json.obj("stat.resCount" -> 1, "stat.articleCount" -> 1)))
                    mongo.insertOne[Article](Article(_id, title, content, keywords, "quill", RequestHelper.getAuthor, categoryPath, category.map(_.name).getOrElse("-"), List.empty[String], List.empty[Reply], None, ViewStat(0, ""), VoteStat(0, ""), ReplyStat(0, 0, ""),  CollectStat(0, ""), ArticleTimeStat(Instant.now, Instant.now, Instant.now, Instant.now), false, false))
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
          wr <- mongo.updateOne[Article](Json.obj("_id" -> _id), modifier)
        } yield {
          Ok(Json.obj("status" -> 0))
        }
      }
    )
  }

  def view(_id: String) = Action.async { implicit request: Request[AnyContent] =>
    for {
      article <- mongo.find[Article](Json.obj("_id" -> _id)).first
    } yield {
      article match {
        case Some(a) =>
          request.session.get("uid") match {
            case Some(uid) =>
              val uid = request.session("uid").toInt
              val viewBitmap = BitmapUtil.fromBase64String(a.viewStat.bitmap)
              if (!viewBitmap.contains(uid)) {
                viewBitmap.add(uid)
                mongo.updateOne[Article](Json.obj("_id" -> _id), Json.obj("$set" -> Json.obj("viewStat" -> ViewStat(a.viewStat.count + 1, BitmapUtil.toBase64String(viewBitmap)))))
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
