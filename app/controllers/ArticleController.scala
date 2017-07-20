package controllers

import javax.inject._

import models._
import models.JsonFormats._
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import play.modules.reactivemongo.{MongoController, ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.play.json.collection.JSONCollection
import utils.{BitmapUtil, DateTimeUtil, HashUtil}
import reactivemongo.play.json._
import models.JsonFormats._
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import reactivemongo.api.QueryOpts
import reactivemongo.bson.BSONObjectID

import scala.concurrent.Future
import java.time._

import services.ViewHelper

@Singleton
class ArticleController @Inject()(val reactiveMongoApi: ReactiveMongoApi)(implicit viewHelper: ViewHelper) extends Controller {
  def articleColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-article"))
  def categoryColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-category"))
  def userColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-user"))
  def msgColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-message"))

  def index(nav: String, page: Int) = Action.async { implicit request: Request[AnyContent] =>

    //println(Json.obj("time" -> OffsetDateTime.now(ZoneOffset.ofHours(8))))

    val cPage = if(page < 1){1}else{page}
    var q = Json.obj()
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
                    articleCol.insert(Article(BSONObjectID.generate().stringify, title, content, "lay-editor", Author(request.session("uid"), request.session("login"), request.session("name"), request.session("headImg")), categoryPath, category.map(_.name).getOrElse("-"), List.empty[String], List.empty[Reply], None, ViewStat(0, ""), VoteStat(0, ""), ReplyStat(0, 0, ""),  CollectStat(0, ""), ArticleTimeStat(DateTimeUtil.now, DateTimeUtil.now, DateTimeUtil.now, DateTimeUtil.now), false, false))
                }
        } yield {
          Redirect(routes.ArticleController.index("0", 1))
        }
      }
    )
  }

  def doReply = Action.async { implicit request: Request[AnyContent] =>
    Form(tuple("_id" -> nonEmptyText, "content" -> nonEmptyText, "at" -> text)).bindFromRequest().fold(
      errForm => Future.successful(Ok("err")),
      tuple => {
        val (_id, content, at) = tuple
        val reply = Reply(BSONObjectID.generate().stringify, content, "lay-editor", Author(request.session("uid"), request.session("login"), request.session("name"), request.session("headImg")), DateTimeUtil.now(), ViewStat(0, ""), VoteStat(0, ""), List.empty[Comment])
        val uid = request.session("uid").toInt
        for{
          articleCol <- articleColFuture
          Some(articleObj) <- articleCol.find(Json.obj("_id" -> _id), Json.obj("replyStat" -> 1, "author" -> 1, "title" -> 1)).one[JsObject]
          articleAuthor = (articleObj \ "author").as[Author]
          articleTitle = (articleObj \ "title").as[String]
          replyStat = (articleObj \ "replyStat").as[ReplyStat]
          replyBitmap = BitmapUtil.fromBase64String(replyStat.bitmap)
          newReplyStat =
          if (replyBitmap.contains(uid)) {
            replyStat.copy(count = replyStat.count + 1)
          } else {
            replyBitmap.add(uid)
            replyStat.copy(count = replyStat.count + 1, userCount = replyStat.userCount + 1, bitmap = BitmapUtil.toBase64String(replyBitmap))
          }
          wr <- articleCol.update(
            Json.obj("_id" -> _id),
            Json.obj(
              "$push" -> Json.obj("replies" -> reply),
              "$set" -> Json.obj("lastReply" -> reply, "replyStat" -> newReplyStat)
            ))
        } yield {
          // 消息提醒
          msgColFuture.map(_.insert(Message(BSONObjectID.generate().stringify, articleAuthor._id, "article", _id, articleTitle, viewHelper.getAuthorOpt.get, "reply", content, DateTimeUtil.now(), false)))
          val atIds = at.split(",").filter(_.trim != "")
          atIds.foreach{ uid =>
            msgColFuture.map(_.insert(Message(BSONObjectID.generate().stringify, uid, "article", _id, articleTitle, viewHelper.getAuthorOpt.get, "at", content, DateTimeUtil.now(), false)))
          }
          userColFuture.map(_.update(Json.obj("_id" -> request.session("uid")), Json.obj("$inc" -> Json.obj("userStat.replyCount" -> 1))))

          Redirect(routes.ArticleController.view(_id))
        }
      }
    )
  }

  def editReply(aid: String, rid: String) = Action.async { implicit request: Request[AnyContent] =>
    for {
      articleCol <- articleColFuture
      reply <- articleCol.find(Json.obj("_id" -> aid), Json.obj("replies" -> Json.obj("$elemMatch" -> Json.obj("_id" -> rid)))).one[JsObject].map(objOpt => (objOpt.get)("replies")(0).as[Reply])
    } yield {
      Ok(Json.obj("status" -> 0, "rows" -> Json.obj("content" -> reply.content)))
    }
  }

  def doEditReply = Action.async { implicit request: Request[AnyContent] =>
    Form(tuple("aid" -> nonEmptyText, "rid" ->nonEmptyText, "content" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok("err")),
      tuple => {
        val (aid, rid, content) = tuple
        val reply = Reply(BSONObjectID.generate().stringify, content, "lay-editor", Author(request.session("uid"), request.session("login"), request.session("name"), request.session("headImg")), DateTimeUtil.now(), ViewStat(0, ""), VoteStat(0, ""), List.empty[Comment])
        val uid = request.session("uid").toInt
        for{
          articleCol <- articleColFuture
          wr <- articleCol.update(Json.obj("_id" -> aid, "replies._id" -> rid), Json.obj("$set" -> Json.obj("replies.$.content" -> content)))
        } yield {
          Ok(Json.obj("status" -> 0))
        }
      }
    )
  }

  def doRemoveReply = Action.async { implicit request: Request[AnyContent] =>
    Form(tuple("aid" -> nonEmptyText, "rid" ->nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok("err")),
      tuple => {
        val (aid, rid) = tuple
        val uid = request.session("uid").toInt
        for{
          articleCol <- articleColFuture
          wr <- articleCol.update(Json.obj("_id" -> aid), Json.obj("$pull" -> Json.obj("replies" -> Json.obj("_id" -> rid)), "$inc" -> Json.obj("replyStat.count" -> -1)))
        } yield {
          userColFuture.map(_.update(Json.obj("_id" -> request.session("uid")), Json.obj("$inc" -> Json.obj("userStat.replyCount" -> -1))))
          Ok(Json.obj("status" -> 0))
        }
      }
    )
  }

  def view(_id: String) = Action.async { implicit request: Request[AnyContent] =>
    for {
      articleCol <- articleColFuture
      article <- articleCol.find(Json.obj("_id" -> _id)).one[Article]
      topViewArticles <- articleCol.find(Json.obj()).sort(Json.obj("viewStat.count" -> -1)).cursor[Article]().collect[List](10)
      topReplyArticles <- articleCol.find(Json.obj()).sort(Json.obj("replyStat.count" -> -1)).cursor[Article]().collect[List](10)
    } yield {
      article match {
        case Some(a) =>
          request.session.get("uid") match{
            case Some(uid) =>
              val uid = request.session("uid").toInt
              val viewBitmap = BitmapUtil.fromBase64String(a.viewStat.bitmap)
              if (!viewBitmap.contains(uid)) {
                viewBitmap.add(uid)
                articleCol.update(Json.obj("_id" -> _id), Json.obj("$set" -> Json.obj("viewStat" -> ViewStat(a.viewStat.count + 1, BitmapUtil.toBase64String(viewBitmap)))))
                Ok(views.html.article.detail(a.copy(viewStat = a.viewStat.copy(count = a.viewStat.count + 1)), topViewArticles, topReplyArticles))
              } else {
                Ok(views.html.article.detail(a, topViewArticles, topReplyArticles))
              }
            case None =>
              Ok(views.html.article.detail(a, topViewArticles, topReplyArticles))
          }
        case None => Redirect(routes.Application.notFound)
      }
    }
  }

  def doVoteReply = Action.async { implicit request: Request[AnyContent] =>
    Form(tuple("aid" -> nonEmptyText,"rid" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(Json.obj("success" -> false, "message" -> "invalid args."))),
      tuple => {
        val (aid, rid) = tuple
        val uid = request.session("uid").toInt
        for{
          articleCol <- articleColFuture
          reply <- articleCol.find(Json.obj("_id" -> aid), Json.obj("replies" -> Json.obj("$elemMatch" -> Json.obj("_id" -> rid)))).one[JsObject].map(objOpt => (objOpt.get \ "replies")(0).as[Reply])
        } yield {
          val bitmap = BitmapUtil.fromBase64String(reply.voteStat.bitmap)
          // 投票
          if (!bitmap.contains(uid)) {
            bitmap.add(uid)
            articleCol.update(Json.obj("_id" -> aid, "replies._id" -> rid), Json.obj("$set" -> Json.obj("replies.$.voteStat" -> VoteStat(reply.voteStat.count + 1, BitmapUtil.toBase64String(bitmap)))))
            Ok(Json.obj("status" -> 0))
          }
          // 取消投票
          else {
            bitmap.remove(uid)
            articleCol.update(Json.obj("_id" -> aid, "replies._id" -> rid), Json.obj("$set" -> Json.obj("replies.$.voteStat" -> VoteStat(reply.voteStat.count - 1, BitmapUtil.toBase64String(bitmap)))))
            Ok(Json.obj("status" -> 0))
          }
        }
      }
    )
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
