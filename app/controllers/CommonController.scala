package controllers

import javax.inject._

import models.JsonFormats._
import models._
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.QueryOpts
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json._
import reactivemongo.play.json.collection.JSONCollection
import utils.{BitmapUtil, DateTimeUtil, HashUtil, RequestHelper}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CommonController @Inject()(cc: ControllerComponents, val reactiveMongoApi: ReactiveMongoApi) (implicit ec: ExecutionContext, parser: BodyParsers.Default) extends AbstractController(cc) {
  def docColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-doc"))
  def categoryColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-category"))
  def userColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-user"))
  def msgColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-message"))


  def doRemove = checkAdminOrOwner("_id").async { implicit request: Request[AnyContent] =>
    Form(single("_id" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(Json.obj("status" -> 1, "msg" -> "输入有误！"))),
      _id => {
        for{
          docCol <- docColFuture
          wr <- docCol.remove(Json.obj("_id" -> _id))
        } yield {
          Ok(Json.obj("status" -> 0))
        }
      }
    )
  }

  def doReply = (checkLogin andThen checkActive).async { implicit request: Request[AnyContent] =>
    Form(tuple("_id" -> nonEmptyText, "content" -> nonEmptyText, "at" -> text)).bindFromRequest().fold(
      errForm => Future.successful(Ok("err")),
      tuple => {
        val (_id, content, at) = tuple
        val reply = Reply(RequestHelper.generateId, content, "lay-editor", Author(request.session("uid"), request.session("login"), request.session("name"), request.session("headImg")), DateTimeUtil.now(), ViewStat(0, ""), VoteStat(0, ""), List.empty[Comment])
        val uid = request.session("uid").toInt
        for{
          docCol <- docColFuture
          Some(docObj) <- docCol.find(Json.obj("_id" -> _id), Json.obj("replyStat" -> 1, "author" -> 1, "title" -> 1)).one[JsObject]
          docAuthor = (docObj \ "author").as[Author]
          docTitle = (docObj \ "title").as[String]
          replyStat = (docObj \ "replyStat").as[ReplyStat]
          replyBitmap = BitmapUtil.fromBase64String(replyStat.bitmap)
          newReplyStat =
          if (replyBitmap.contains(uid)) {
            replyStat.copy(count = replyStat.count + 1)
          } else {
            replyBitmap.add(uid)
            replyStat.copy(count = replyStat.count + 1, userCount = replyStat.userCount + 1, bitmap = BitmapUtil.toBase64String(replyBitmap))
          }
          wr <- docCol.update(
            Json.obj("_id" -> _id),
            Json.obj(
              "$push" -> Json.obj("replies" -> reply),
              "$set" -> Json.obj("lastReply" -> reply, "replyStat" -> newReplyStat)
            ))
        } yield {
          // 消息提醒
          val read = if (docAuthor._id != RequestHelper.getUidOpt.get) { false } else { true }
          msgColFuture.map(_.insert(Message(BSONObjectID.generate().stringify, docAuthor._id, "doc", _id, docTitle, RequestHelper.getAuthorOpt.get, "reply", content, DateTimeUtil.now(), read)))

          val atIds = at.split(",").filter(_.trim != "")
          atIds.foreach{ uid =>
            msgColFuture.map(_.insert(Message(BSONObjectID.generate().stringify, uid, "doc", _id, docTitle, RequestHelper.getAuthorOpt.get, "at", content, DateTimeUtil.now(), false)))
          }
          userColFuture.map(_.update(Json.obj("_id" -> request.session("uid")), Json.obj("$inc" -> Json.obj("userStat.replyCount" -> 1))))

          Redirect(routes.DocController.view(_id))
        }
      }
    )
  }

  def editReply(aid: String, rid: String) = checkOwner("rid").async { implicit request: Request[AnyContent] =>
    for {
      docCol <- docColFuture
      reply <- docCol.find(Json.obj("_id" -> aid), Json.obj("replies" -> Json.obj("$elemMatch" -> Json.obj("_id" -> rid)))).one[JsObject].map(objOpt => (objOpt.get)("replies")(0).as[Reply])
    } yield {
      Ok(Json.obj("status" -> 0, "rows" -> Json.obj("content" -> reply.content)))
    }
  }

  def doEditReply = checkOwner("rid").async { implicit request: Request[AnyContent] =>
    Form(tuple("aid" -> nonEmptyText, "rid" ->nonEmptyText, "content" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok("err")),
      tuple => {
        val (aid, rid, content) = tuple
        val reply = Reply(BSONObjectID.generate().stringify, content, "lay-editor", Author(request.session("uid"), request.session("login"), request.session("name"), request.session("headImg")), DateTimeUtil.now(), ViewStat(0, ""), VoteStat(0, ""), List.empty[Comment])
        val uid = request.session("uid").toInt
        for{
          docCol <- docColFuture
          wr <- docCol.update(Json.obj("_id" -> aid, "replies._id" -> rid), Json.obj("$set" -> Json.obj("replies.$.content" -> content)))
        } yield {
          Ok(Json.obj("status" -> 0))
        }
      }
    )
  }

  def doRemoveReply = checkAdminOrOwner("rid").async { implicit request: Request[AnyContent] =>
    Form(tuple("aid" -> nonEmptyText, "rid" ->nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok("err")),
      tuple => {
        val (aid, rid) = tuple
        val uid = request.session("uid").toInt
        for{
          docCol <- docColFuture
          wr <- docCol.update(Json.obj("_id" -> aid), Json.obj("$pull" -> Json.obj("replies" -> Json.obj("_id" -> rid)), "$inc" -> Json.obj("replyStat.count" -> -1)))
        } yield {
          userColFuture.map(_.update(Json.obj("_id" -> request.session("uid")), Json.obj("$inc" -> Json.obj("userStat.replyCount" -> -1))))
          Ok(Json.obj("status" -> 0))
        }
      }
    )
  }

  def doVoteReply = (checkLogin andThen checkActive).async { implicit request: Request[AnyContent] =>
    Form(tuple("aid" -> nonEmptyText,"rid" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(Json.obj("success" -> false, "message" -> "invalid args."))),
      tuple => {
        val (aid, rid) = tuple
        val uid = request.session("uid").toInt
        for{
          docCol <- docColFuture
          reply <- docCol.find(Json.obj("_id" -> aid), Json.obj("replies" -> Json.obj("$elemMatch" -> Json.obj("_id" -> rid)))).one[JsObject].map(objOpt => (objOpt.get \ "replies")(0).as[Reply])
        } yield {
          val bitmap = BitmapUtil.fromBase64String(reply.voteStat.bitmap)
          // 投票
          if (!bitmap.contains(uid)) {
            bitmap.add(uid)
            docCol.update(Json.obj("_id" -> aid, "replies._id" -> rid), Json.obj("$set" -> Json.obj("replies.$.voteStat" -> VoteStat(reply.voteStat.count + 1, BitmapUtil.toBase64String(bitmap)))))
            Ok(Json.obj("status" -> 0))
          }
          // 取消投票
          else {
            bitmap.remove(uid)
            docCol.update(Json.obj("_id" -> aid, "replies._id" -> rid), Json.obj("$set" -> Json.obj("replies.$.voteStat" -> VoteStat(reply.voteStat.count - 1, BitmapUtil.toBase64String(bitmap)))))
            Ok(Json.obj("status" -> 0))
          }
        }
      }
    )
  }

  def vote(docId: String, up: Boolean) = Action.async { implicit request: Request[AnyContent] =>

    val login = "joymufeng1"
    (for {
      docCol <- docColFuture
      objOpt <- docCol.find(Json.obj("_id" -> docId)).one[JsObject]
    } yield {
      //docCol.insert(Doc("0", "", "", "", "", "", "", "", "", List.empty[String], LocalDateTime.now(LocalDateTimeZone.UTC), LocalDateTime.now(LocalDateTimeZone.UTC), List.empty[Reply], 0, List.empty[Long], 0, List.empty[Long], 0)).foreach(println _)

      objOpt.fold(Ok(Json.obj("success" -> false))){ obj =>
        val bitmapStr = (obj \ "viewBitMap").as[String].trim
        val bitmap = BitmapUtil.fromBase64String(bitmapStr)
        val userHash = HashUtil.toInt(login)
        println(bitmap.contains(userHash))
        if (!bitmap.contains(userHash)) {
          println("add user")
          bitmap.add(userHash)
          docCol.update(Json.obj("_id" -> docId), Json.obj("$inc" -> Json.obj("voteCount" -> 1), "$set" -> Json.obj("viewBitMap" -> BitmapUtil.toBase64String(bitmap))))
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
