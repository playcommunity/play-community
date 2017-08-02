package controllers

import java.time.OffsetDateTime
import javax.inject._

import akka.stream.Materializer
import models._
import models.JsonFormats._
import reactivemongo.play.json._
import play.api._
import play.api.data.Form
import play.api.mvc.{Action, _}
import play.api.data.Forms.{tuple, _}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.play.json.collection.JSONCollection
import play.api.libs.json.{JsObject, Json}
import reactivemongo.bson.BSONObjectID

import scala.concurrent.{ExecutionContext, Future}
import utils.{BitmapUtil, DateTimeUtil, HashUtil, RequestHelper}

import scala.concurrent.duration._

@Singleton
class UserController @Inject()(cc: ControllerComponents, reactiveMongoApi: ReactiveMongoApi, resourceController: ResourceController, userAction: UserAction)(implicit ec: ExecutionContext, mat: Materializer, parser: BodyParsers.Default) extends AbstractController(cc) {
  def robotColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-robot"))
  def userColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-user"))
  def articleColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-article"))
  def docColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-doc"))
  def collectColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("stat-collect"))
  def msgColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-message"))
  def getColFuture(name: String) = reactiveMongoApi.database.map(_.collection[JSONCollection](name))

  def index() = Action.async { implicit request: Request[AnyContent] =>
    for {
      articleCol <- articleColFuture
      collectCol <- collectColFuture
      articles <- articleCol.find(Json.obj("author._id" -> request.session("uid"))).sort(Json.obj("timeStat.updateTime" -> -1)).cursor[Article]().collect[List](15)
      articlesCount <- articleCol.count(Some(Json.obj("author._id" -> request.session("uid"))))
      collectRes <- collectCol.find(Json.obj("uid" -> request.session("uid"))).sort(Json.obj("collectTime" -> -1)).cursor[StatCollect]().collect[List](15)
      collectResCount <- collectCol.count(Some(Json.obj("uid" -> request.session("uid"))))
    } yield {
      Ok(views.html.user.index(articles, articlesCount, collectRes, collectResCount))
    }
  }

  def home(uidOpt: Option[String]) = Action.async { implicit request: Request[AnyContent] =>
    (uidOpt orElse RequestHelper.getUidOpt) match {
      case Some(uid) =>
        for {
          articleCol <- articleColFuture
          myArticles <- articleCol.find(Json.obj("author._id" -> uid)).sort(Json.obj("timeStat.updateTime" -> -1)).cursor[Article]().collect[List](15)
          myReplyArticles <- articleCol.find(Json.obj("replies.author._id" -> uid)).sort(Json.obj("replies.replyTime" -> -1)).cursor[Article]().collect[List](15)
          userOpt <- userColFuture.flatMap(_.find(Json.obj("_id" -> uid)).one[User])
        } yield {
          Ok(views.html.user.home(uidOpt, userOpt, myArticles, myReplyArticles))
        }
      case None =>
        Future.successful(Ok(views.html.message("系统提示", "您查看的用户不存在！")))
    }
  }

  def message() = checkLogin.async { implicit request: Request[AnyContent] =>
    for {
      msgCol <- msgColFuture
      messages <- msgCol.find(Json.obj("uid" -> request.session("uid"))).sort(Json.obj("createTime" -> -1)).cursor[Message]().collect[List](15)
      count <- msgCol.count(Some(Json.obj("uid" -> request.session("uid"))))
    } yield {
      Ok(views.html.user.message(messages, count))
    }
  }

  def messageCount() = checkLogin.async { implicit request: Request[AnyContent] =>
    for {
      msgCol <- msgColFuture
      count <- msgCol.count(Some(Json.obj("uid" -> request.session("uid"), "read" -> false)))
    } yield {
      Ok(Json.obj("status" -> 0, "count" -> count))
    }
  }

  def readMessage() = checkLogin.async { implicit request: Request[AnyContent] =>
    for {
      msgCol <- msgColFuture
      wr <- msgCol.update(Json.obj("uid" -> request.session("uid"), "read" -> false), Json.obj("$set" -> Json.obj("read" -> true)), multi = true)
    } yield {
      Ok(Json.obj("status" -> 0))
    }
  }

  def removeMessage = checkLogin.async { implicit request: Request[AnyContent] =>
    Form(single("_id" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Redirect(routes.Application.message("系统提示", "您的输入有误！" + errForm.errors))),
      _id => {
        for {
          msgCol <- msgColFuture
          wr <- msgCol.remove(Json.obj("_id" -> _id, "uid" -> request.session("uid")))
        } yield {
          Ok(Json.obj("status" -> 0))
        }
      }
    )
  }

  def clearMessage() = checkLogin.async { implicit request: Request[AnyContent] =>
    for {
      msgCol <- msgColFuture
      wr <- msgCol.remove(Json.obj("uid" -> request.session("uid")))
    } yield {
      Ok(Json.obj("status" -> 0))
    }
  }

  def activate() = userAction { implicit request =>
    Ok(views.html.user.activate(request.user))
  }

  def setting() = (checkLogin andThen userAction) { implicit request =>
    Ok(views.html.user.setting(request.user))
  }

  def doSetting() = checkLogin.async { implicit request: Request[AnyContent] =>
    Form(tuple("name" -> nonEmptyText, "gender" -> optional(text), "city" -> text, "introduction" -> text)).bindFromRequest().fold(
      errForm => Future.successful(Redirect(routes.Application.message("系统提示", "您的输入有误！" + errForm.errors))),
      tuple => {
        val (name, gender, city, introduction) = tuple
        userColFuture.flatMap(_.update(
          Json.obj("_id" -> request.session("uid")),
          Json.obj(
            "$set" -> Json.obj("setting.name" -> name, "setting.gender" -> gender.getOrElse[String](""), "setting.city" -> city, "setting.introduction" -> introduction)
          ))).map{ wr =>
          Redirect(routes.UserController.setting())
            .addingToSession("name" -> name)
        }
      }
    )
  }

  // 演示如何将请求转发至ResourceController.saveResource
  /*def doSetHeadImg() = Action.async { implicit request: Request[AnyContent] =>
    resourceController.saveResource("")(request).mapFuture(_.body.consumeData.map(_.utf8String)).run().map(s => (Json.parse(s) \ "rid").asOpt[String]).map{
      case Some(rid) => Ok(Json.obj("success" -> true, "url" -> s"/resource/${rid}"))
      case None      => Ok(Json.obj("success" -> false, "message" -> "Upload failed."))
    }
  }*/

  def doSetHeadImg = checkLogin.async { implicit request: Request[AnyContent] =>
    Form(single("url" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Redirect(routes.Application.message("系统提示", "您的输入有误！" + errForm.errors))),
      url => {
        userColFuture.flatMap(_.update(
          Json.obj("_id" -> request.session("uid")),
          Json.obj(
            "$set" -> Json.obj("setting.headImg" -> url)
          ))).map{ wr =>
          Redirect(routes.UserController.setting()).addingToSession("headImg" -> url)
        }
      }
    )
  }

  def doSetPassword() = (checkLogin andThen userAction) { implicit request =>
    Form(tuple("password" -> nonEmptyText, "password1" -> nonEmptyText, "password2" -> nonEmptyText).verifying("两次输入不一致！", t => t._2 == t._3)).bindFromRequest().fold(
      errForm => Redirect(routes.Application.message("系统提示", "您的输入有误！" + errForm.errors.map(_.message).mkString("|"))),
      tuple => {
        val (password, password1, _) = tuple
        if (HashUtil.sha256(password) == request.user.password) {
          userColFuture.flatMap(_.update(
            Json.obj("_id" -> request.session("uid")),
            Json.obj(
              "$set" -> Json.obj("password" -> HashUtil.sha256(password1))
            )))
          Redirect(routes.Application.message("系统提示", "密码修改成功！"))
        } else {
          Redirect(routes.Application.message("系统提示", "您的输入有误！"))
        }
      }
    )
  }

  def doCollect() = checkLogin.async { implicit request: Request[AnyContent] =>
    Form(tuple("resType" -> nonEmptyText,"resId" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(Json.obj("success" -> false, "message" -> "invalid args."))),
      tuple => {
        val (resType, resId) = tuple
        val uid = request.session("uid").toInt
        for {
          collectCol <- collectColFuture
          resCol <- getColFuture("common-" + resType)
          Some(resObj) <- resCol.find(Json.obj("_id" -> resId), Json.obj("title" -> 1, "author" -> 1, "timeStat" -> 1, "collectStat" -> 1)).one[JsObject]
        } yield {
          val collectStat = resObj("collectStat").as[CollectStat]
          val resOwner = resObj("author").as[Author]
          val resTitle = resObj("title").as[String]
          val resCreateTime = resObj("timeStat")("createTime").as[OffsetDateTime]
          val bitmap = BitmapUtil.fromBase64String(collectStat.bitmap)
          // 收藏
          if (!bitmap.contains(uid)) {
            bitmap.add(uid)
            resCol.update(Json.obj("_id" -> resId), Json.obj("$set" -> Json.obj("collectStat" -> CollectStat(collectStat.count + 1, BitmapUtil.toBase64String(bitmap)))))
            collectCol.insert(StatCollect(BSONObjectID.generate().stringify, request.session("uid"), resType, resId, resOwner, resTitle, resCreateTime, DateTimeUtil.now()))
            Ok(Json.obj("status" -> 0))
          }
          // 取消收藏
          else {
            bitmap.remove(uid)
            resCol.update(Json.obj("_id" -> resId), Json.obj("$set" -> Json.obj("collectStat" -> CollectStat(collectStat.count - 1, BitmapUtil.toBase64String(bitmap)))))
            collectCol.remove(Json.obj("uid" -> request.session("uid"), "resId" -> resId, "resType" -> resType))
            Ok(Json.obj("status" -> 0))
          }
        }
      }
    )
  }


  def doReply = (checkLogin andThen checkActive).async { implicit request: Request[AnyContent] =>
    Form(tuple("resId" -> nonEmptyText, "resType" -> nonEmptyText, "content" -> nonEmptyText, "at" -> text)).bindFromRequest().fold(
      errForm => Future.successful(Ok("err")),
      tuple => {
        val (resId, resType, content, at) = tuple
        val reply = Reply(RequestHelper.generateId, content, "lay-editor", Author(request.session("uid"), request.session("login"), request.session("name"), request.session("headImg")), DateTimeUtil.now(), ViewStat(0, ""), VoteStat(0, ""), List.empty[Comment])
        val uid = request.session("uid").toInt
        for{
          resCol <- getColFuture("common-" + resType)
          Some(resObj) <- resCol.find(Json.obj("_id" -> resId), Json.obj("replyStat" -> 1, "author" -> 1, "title" -> 1)).one[JsObject]
          resAuthor = (resObj \ "author").as[Author]
          resTitle = (resObj \ "title").as[String]
          replyStat = (resObj \ "replyStat").as[ReplyStat]
          replyBitmap = BitmapUtil.fromBase64String(replyStat.bitmap)
          newReplyStat =
          if (replyBitmap.contains(uid)) {
            replyStat.copy(count = replyStat.count + 1)
          } else {
            replyBitmap.add(uid)
            replyStat.copy(count = replyStat.count + 1, userCount = replyStat.userCount + 1, bitmap = BitmapUtil.toBase64String(replyBitmap))
          }
          wr <- resCol.update(
            Json.obj("_id" -> resId),
            Json.obj(
              "$push" -> Json.obj("replies" -> reply),
              "$set" -> Json.obj("lastReply" -> reply, "replyStat" -> newReplyStat)
            ))
        } yield {
          // 消息提醒
          val read = if (resAuthor._id != RequestHelper.getUidOpt.get) { false } else { true }
          msgColFuture.map(_.insert(Message(BSONObjectID.generate().stringify, resAuthor._id, "article", resId, resTitle, RequestHelper.getAuthorOpt.get, "reply", content, DateTimeUtil.now(), read)))

          val atIds = at.split(",").filter(_.trim != "")
          atIds.foreach{ uid =>
            msgColFuture.map(_.insert(Message(BSONObjectID.generate().stringify, uid, "article", resId, resTitle, RequestHelper.getAuthorOpt.get, "at", content, DateTimeUtil.now(), false)))
          }
          userColFuture.map(_.update(Json.obj("_id" -> request.session("uid")), Json.obj("$inc" -> Json.obj("userStat.replyCount" -> 1))))

          Redirect(s"/${resType}/view?_id=${resId}")
        }
      }
    )
  }

}
