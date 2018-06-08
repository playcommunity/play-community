package controllers

import java.time.{Instant, OffsetDateTime}
import javax.inject._

import akka.stream.Materializer
import cn.playscala.mongo.Mongo
import models._
import models.JsonFormats._
import org.bson.types.ObjectId
import play.api._
import play.api.data.Form
import play.api.mvc.{Action, _}
import play.api.data.Forms.{tuple, _}
import play.api.libs.json.{JsObject, Json}
import services.{CommonService, EventService}

import scala.concurrent.{ExecutionContext, Future}
import utils.{BitmapUtil, DateTimeUtil, HashUtil, RequestHelper}

import scala.concurrent.duration._
import play.api.libs.json.Json._

@Singleton
class UserController @Inject()(cc: ControllerComponents, mongo: Mongo, resourceController: ResourceController, userAction: UserAction, eventService: EventService, commonService: CommonService)(implicit ec: ExecutionContext, mat: Materializer, parser: BodyParsers.Default) extends AbstractController(cc) {

  def index() = checkLogin.async { implicit request: Request[AnyContent] =>
    for {
      articles <- mongo.find[Article](obj("author._id" -> request.session("uid"))).sort(obj("timeStat.updateTime" -> -1)).limit(15).list
      articlesCount <- mongo.count[Article](obj("author._id" -> request.session("uid")))
      qas <- mongo.find[QA](obj("author._id" -> request.session("uid"))).sort(obj("timeStat.updateTime" -> -1)).limit(15).list
      qaCount <- mongo.count[QA](obj("author._id" -> request.session("uid")))
      collectRes <- mongo.find[StatCollect](obj("uid" -> request.session("uid"))).sort(obj("collectTime" -> -1)).limit(15).list
      collectResCount <- mongo.count[StatCollect](obj("uid" -> request.session("uid")))
    } yield {
      Ok(views.html.user.index(articles, articlesCount.toInt, qas, qaCount.toInt, collectRes, collectResCount.toInt))
    }
  }

  def home(uidOpt: Option[String]) = Action.async { implicit request: Request[AnyContent] =>
    (uidOpt orElse RequestHelper.getUidOpt) match {
      case Some(uid) =>
        for {
          createEvents <- mongo.find[Event](obj("actor._id" -> uid, "action" -> "create")).sort(obj("createTime" -> -1)).limit(30).list
          events <- mongo.find[Event](obj("actor._id" -> uid)).sort(obj("createTime" -> -1)).limit(30).list
          userOpt <- mongo.find[User](obj("_id" -> uid)).first
        } yield {
          Ok(views.html.user.home(uidOpt, userOpt, createEvents, events))
        }
      case None =>
        Future.successful(Ok(views.html.message("系统提示", "您查看的用户不存在！")))
    }
  }

  def message() = checkLogin.async { implicit request: Request[AnyContent] =>
    for {
      messages <- mongo.find[Message](obj("uid" -> request.session("uid"))).sort(obj("createTime" -> -1)).limit(15).list
      count <- mongo.count[Message](obj("uid" -> request.session("uid")))
    } yield {
      Ok(views.html.user.message(messages, count.toInt))
    }
  }

  def messageCount() = checkLogin.async { implicit request: Request[AnyContent] =>
    for {
      count <- mongo.count[Message](obj("uid" -> request.session("uid"), "read" -> false))
    } yield {
      Ok(Json.obj("status" -> 0, "count" -> count))
    }
  }

  def readMessage() = checkLogin.async { implicit request: Request[AnyContent] =>
    for {
      wr <- mongo.updateMany[Message](obj("uid" -> request.session("uid"), "read" -> false), obj("$set" -> Json.obj("read" -> true)))
    } yield {
      Ok(Json.obj("status" -> 0))
    }
  }

  def removeMessage = checkLogin.async { implicit request: Request[AnyContent] =>
    Form(single("_id" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Redirect(routes.Application.message("系统提示", "您的输入有误！" + errForm.errors))),
      _id => {
        for {
          wr <- mongo.deleteMany[Message](obj("_id" -> _id, "uid" -> request.session("uid")))
        } yield {
          Ok(Json.obj("status" -> 0))
        }
      }
    )
  }

  def clearMessage() = checkLogin.async { implicit request: Request[AnyContent] =>
    for {
      wr <- mongo.deleteMany[Message](obj("uid" -> request.session("uid")))
    } yield {
      Ok(Json.obj("status" -> 0))
    }
  }

  def activate() = (checkLogin andThen userAction) { implicit request =>
    Ok(views.html.user.activate(request.user))
  }

  def setting(focus: String) = (checkLogin andThen userAction) { implicit request =>
    Ok(views.html.user.setting(request.user, focus))
  }

  def doSetting() = checkLogin.async { implicit request: Request[AnyContent] =>
    Form(tuple("name" -> nonEmptyText, "gender" -> optional(text), "city" -> text, "introduction" -> text)).bindFromRequest().fold(
      errForm => Future.successful(Redirect(routes.Application.message("系统提示", "您的输入有误！" + errForm.errors))),
      tuple => {
        val (name, gender, city, introduction) = tuple
        mongo.updateOne[User](
          obj("_id" -> request.session("uid")),
          obj(
            "$set" -> obj("setting.name" -> name, "setting.gender" -> gender.getOrElse[String](""), "setting.city" -> city, "setting.introduction" -> introduction)
        )).map{ wr =>
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
        mongo.updateOne[User](
          obj("_id" -> request.session("uid")),
          obj(
            "$set" -> Json.obj("setting.headImg" -> url)
        )).map{ wr =>
          Redirect(routes.UserController.setting()).addingToSession("headImg" -> url)
        }
      }
    )
  }

  def doSetPassword() = (checkLogin andThen userAction) { implicit request =>
    Form(tuple("password" -> optional(text), "password1" -> nonEmptyText, "password2" -> nonEmptyText).verifying("两次输入不一致！", t => t._2 == t._3)).bindFromRequest().fold(
      errForm => Redirect(routes.Application.message("系统提示", "您的输入有误！" + errForm.errors.map(_.message).mkString("|"))),
      tuple => {
        val (password, password1, _) = tuple
        if (password.isEmpty && request.user.password == "" || HashUtil.sha256(password.get) == request.user.password) {
          mongo.updateOne[User](
            Json.obj("_id" -> request.session("uid")),
            Json.obj(
              "$set" -> Json.obj("password" -> HashUtil.sha256(password1))
          ))
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
        val resCol = mongo.collection(s"common-${resType}")
        for {
          Some(resObj) <- mongo.collection("common-" + resType).find(Json.obj("_id" -> resId), Json.obj("title" -> 1, "author" -> 1, "timeStat" -> 1, "collectStat" -> 1)).first
        } yield {
          val collectStat = resObj("collectStat").as[CollectStat]
          val resOwner = resObj("author").as[Author]
          val resTitle = resObj("title").as[String]
          val resCreateTime = resObj("timeStat")("createTime").as[Long]
          val bitmap = BitmapUtil.fromBase64String(collectStat.bitmap)
          // 收藏
          if (!bitmap.contains(uid)) {
            eventService.collectResource(RequestHelper.getAuthor, resId, resType, resTitle)
            bitmap.add(uid)
            resCol.updateOne(Json.obj("_id" -> resId), Json.obj("$set" -> Json.obj("collectStat" -> CollectStat(collectStat.count + 1, BitmapUtil.toBase64String(bitmap)))))
            mongo.insertOne[StatCollect](StatCollect(ObjectId.get.toHexString, request.session("uid"), resType, resId, resOwner, resTitle, Instant.ofEpochMilli(resCreateTime), DateTimeUtil.now()))
            Ok(Json.obj("status" -> 0))
          }
          // 取消收藏
          else {
            bitmap.remove(uid)
            resCol.updateOne(Json.obj("_id" -> resId), Json.obj("$set" -> Json.obj("collectStat" -> CollectStat(collectStat.count - 1, BitmapUtil.toBase64String(bitmap)))))
            mongo.deleteMany[StatCollect](Json.obj("uid" -> request.session("uid"), "resId" -> resId, "resType" -> resType))
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
        val resCol = mongo.collection(s"common-${resType}")
        for{
          Some(resObj) <- mongo.collection("common-" + resType).find(Json.obj("_id" -> resId), Json.obj("replyStat" -> 1, "author" -> 1, "title" -> 1)).first
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
          wr <- resCol.updateOne(
            Json.obj("_id" -> resId),
            Json.obj(
              "$push" -> Json.obj("replies" -> reply),
              "$set" -> Json.obj("lastReply" -> reply, "replyStat" -> newReplyStat)
            ))
        } yield {
          // 记录回复事件
          eventService.replyResource(RequestHelper.getAuthor, resId, resType, resTitle)

          // 消息提醒
          val read = if (resAuthor._id != RequestHelper.getUidOpt.get) { false } else { true }
          mongo.insertOne[Message](Message(ObjectId.get().toHexString, resAuthor._id, resType, resId, resTitle, RequestHelper.getAuthorOpt.get, "reply", content, DateTimeUtil.now(), read))

          val atIds = at.split(",").filter(_.trim != "")
          atIds.foreach{ uid =>
            mongo.insertOne[Message](Message(ObjectId.get().toHexString, uid, resType, resId, resTitle, RequestHelper.getAuthorOpt.get, "at", content, DateTimeUtil.now(), false))
          }
          mongo.updateOne[User](Json.obj("_id" -> request.session("uid")), Json.obj("$inc" -> Json.obj("stat.replyCount" -> 1), "$set" -> Json.obj("stat.lastReplyTime" -> DateTimeUtil.now())))

          Redirect(s"/${resType}/view?_id=${resId}")
        }
      }
    )
  }


  def editReply(aid: String, rid: String) = checkOwner("rid").async { implicit request: Request[AnyContent] =>
    for {
      reply <- mongo.collection("common-article").find(Json.obj("_id" -> aid), Json.obj("replies" -> Json.obj("$elemMatch" -> Json.obj("_id" -> rid)))).first.map(objOpt => (objOpt.get)("replies")(0).as[Reply])
    } yield {
      Ok(Json.obj("status" -> 0, "rows" -> Json.obj("content" -> reply.content)))
    }
  }

  def doEditReply = checkOwner("rid").async { implicit request: Request[AnyContent] =>
    Form(tuple("aid" -> nonEmptyText, "rid" ->nonEmptyText, "content" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok("err")),
      tuple => {
        val (aid, rid, content) = tuple
        val reply = Reply(ObjectId.get().toHexString, content, "lay-editor", Author(request.session("uid"), request.session("login"), request.session("name"), request.session("headImg")), DateTimeUtil.now(), ViewStat(0, ""), VoteStat(0, ""), List.empty[Comment])
        val uid = request.session("uid").toInt
        for{
          wr <- mongo.updateOne[Article](Json.obj("_id" -> aid, "replies._id" -> rid), Json.obj("$set" -> Json.obj("replies.$.content" -> content)))
        } yield {
          Ok(Json.obj("status" -> 0))
        }
      }
    )
  }

  def doRemoveReply = checkAdminOrOwner("rid").async { implicit request: Request[AnyContent] =>
    Form(tuple("resType" -> nonEmptyText, "resId" -> nonEmptyText, "rid" ->nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok("err")),
      tuple => {
        val (resType, resId, rid) = tuple
        val uid = request.session("uid").toInt
        val resCol = mongo.collection(s"common-${resType}")
        for{
          answerObjOpt <- resCol.find(Json.obj("_id" -> resId), Json.obj("answer" -> 1)).first
        } yield {
          val answerId = answerObjOpt.flatMap(obj => (obj \ "answer" \ "_id").asOpt[String]).getOrElse("")
          if (answerId != rid) {
            resCol.updateOne(Json.obj("_id" -> resId), Json.obj("$pull" -> Json.obj("replies" -> Json.obj("_id" -> rid)), "$inc" -> Json.obj("replyStat.count" -> -1)))
            mongo.updateOne[User](Json.obj("_id" -> request.session("uid")), Json.obj("$inc" -> Json.obj("stat.replyCount" -> -1)))
            Ok(Json.obj("status" -> 0))
          } else {
            Ok(Json.obj("status" -> 1, "msg" -> "已采纳回复不允许删除！"))
          }
        }
      }
    )
  }

  def doVoteReply = (checkLogin andThen checkActive).async { implicit request: Request[AnyContent] =>
    Form(tuple("resType" -> nonEmptyText, "resId" -> nonEmptyText, "rid" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(Json.obj("success" -> false, "message" -> "invalid args."))),
      tuple => {
        val (resType, resId, rid) = tuple
        val resCol = mongo.collection(s"common-${resType}")
        for{
          reply <- resCol.find(Json.obj("_id" -> resId), Json.obj("replies" -> Json.obj("$elemMatch" -> Json.obj("_id" -> rid)))).first.map(objOpt => (objOpt.get \ "replies")(0).as[Reply])
        } yield {
          val uid = RequestHelper.getUid.toInt
          val bitmap = BitmapUtil.fromBase64String(reply.voteStat.bitmap)
          // 投票
          if (!bitmap.contains(uid)) {
            bitmap.add(uid)
            resCol.updateOne(Json.obj("_id" -> resId, "replies._id" -> rid), Json.obj("$set" -> Json.obj("replies.$.voteStat" -> VoteStat(reply.voteStat.count + 1, BitmapUtil.toBase64String(bitmap)))))
            mongo.updateOne[User](Json.obj("_id" -> RequestHelper.getUid), Json.obj("$inc" -> Json.obj("stat.voteCount" -> 1)))
            mongo.updateOne[User](Json.obj("_id" -> resId.split("-")(0)), Json.obj("$inc" -> Json.obj("stat.votedCount" -> 1)))
            Ok(Json.obj("status" -> 0))
          }
          // 取消投票
          else {
            bitmap.remove(uid)
            resCol.updateOne(Json.obj("_id" -> resId, "replies._id" -> rid), Json.obj("$set" -> Json.obj("replies.$.voteStat" -> VoteStat(reply.voteStat.count - 1, BitmapUtil.toBase64String(bitmap)))))
            mongo.updateOne[User](Json.obj("_id" -> RequestHelper.getUid), Json.obj("$inc" -> Json.obj("stat.voteCount" -> -1)))
            mongo.updateOne[User](Json.obj("_id" -> resId.split("-")(0)), Json.obj("$inc" -> Json.obj("stat.votedCount" -> -1)))
            Ok(Json.obj("status" -> 0))
          }
        }
      }
    )
  }

  def doVote = (checkLogin andThen checkActive).async { implicit request: Request[AnyContent] =>
    Form(tuple("resType" -> nonEmptyText,"resId" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(Json.obj("status" -> 1, "msg" -> "invalid args."))),
      tuple => {
        val (resType, resId) = tuple
        val resCol = mongo.collection(s"common-${resType}")
        for{
          objOpt <- resCol.find(Json.obj("_id" -> resId), Json.obj("voteStat" -> 1, "title" -> 1)).first
        } yield {
          val voteStat = (objOpt.get \ "voteStat").as[VoteStat]
          val resTitle = (objOpt.get \ "title").as[String]

          val uid = RequestHelper.getUid.toInt
          val bitmap = BitmapUtil.fromBase64String(voteStat.bitmap)
          // 投票
          if (!bitmap.contains(uid)) {
            eventService.voteResource(RequestHelper.getAuthor, resId, resType, resTitle)
            bitmap.add(uid)
            resCol.updateOne(Json.obj("_id" -> resId), Json.obj("$set" -> Json.obj("voteStat" -> VoteStat(voteStat.count + 1, BitmapUtil.toBase64String(bitmap)))))
            mongo.updateOne[User](Json.obj("_id" -> RequestHelper.getUid), Json.obj("$inc" -> Json.obj("stat.voteCount" -> 1)))
            mongo.updateOne[User](Json.obj("_id" -> resId.split("-")(0)), Json.obj("$inc" -> Json.obj("stat.votedCount" -> 1)))
            Ok(Json.obj("status" -> 0, "count" -> 1))
          }
          // 取消投票
          else {
            bitmap.remove(uid)
            resCol.updateOne(Json.obj("_id" -> resId), Json.obj("$set" -> Json.obj("voteStat" -> VoteStat(voteStat.count - 1, BitmapUtil.toBase64String(bitmap)))))
            mongo.updateOne[User](Json.obj("_id" -> RequestHelper.getUid), Json.obj("$inc" -> Json.obj("stat.voteCount" -> -1)))
            mongo.updateOne[User](Json.obj("_id" -> resId.split("-")(0)), Json.obj("$inc" -> Json.obj("stat.votedCount" -> -1)))
            Ok(Json.obj("status" -> 0, "count" -> -1))
          }
        }
      }
    )
  }

  def doRemove = checkAdminOrOwner("_id").async { implicit request: Request[AnyContent] =>
    Form(tuple("resType" -> nonEmptyText,"resId" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(Json.obj("status" -> 1, "msg" -> "输入有误！"))),
      tuple => {
        val (resType, resId) = tuple
        val resCol = mongo.collection(s"common-${resType}")
        for{
          objOpt <- resCol.find(Json.obj("_id" -> resId), Json.obj("title" -> 1)).first
          wr <- resCol.deleteOne(Json.obj("_id" -> resId))
        } yield {
          val resTitle = (objOpt.get \ "title").as[String]
          eventService.removeResource(RequestHelper.getAuthor, resId, resType, resTitle)
          mongo.updateOne[User](Json.obj("_id" -> resId.split("-")(0)), Json.obj("$inc" -> Json.obj("stat.resCount" -> -1, s"stat.${resType}Count" -> -1)))
          Ok(Json.obj("status" -> 0))
        }
      }
    )
  }

  // 将资源推至首页
  def doPush = checkAdmin.async { implicit request: Request[AnyContent] =>
    Form(tuple("resType" -> nonEmptyText,"resId" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(Json.obj("status" -> 1, "msg" -> "输入有误！"))),
      tuple => {
        val (resType, resId) = tuple
        val resCol = mongo.collection(s"common-${resType}")
        for{
          Some(resObj) <- resCol.find(Json.obj("_id" -> resId), Json.obj("title" -> 1, "author" -> 1)).first
        } yield {
          val resOwner = resObj("author").as[Author]
          val resTitle = resObj("title").as[String]
          commonService.getNextSequence("common-news").map{index =>
            mongo.insertOne[News](News(index.toString, resTitle, s"/${resType}/view?_id=${resId}", resOwner, resType, Some(false), DateTimeUtil.now()))
          }
          Ok(Json.obj("status" -> 0))
        }
      }
    )
  }


}
