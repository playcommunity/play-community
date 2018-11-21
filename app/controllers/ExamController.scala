package controllers

import cn.playscala.mongo.Mongo
import javax.inject._
import models._
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import play.api.libs.json.Json
import play.api.mvc._
import services.{CommonService, EventService}
import utils.{DateTimeUtil, RequestHelper}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ExamController @Inject()(cc: ControllerComponents, mongo: Mongo, commonService: CommonService, eventService: EventService)(implicit ec: ExecutionContext, parser: BodyParsers.Default) extends AbstractController(cc) {

  //添加试题
  def add = checkLogin(parser, ec) { implicit request: Request[AnyContent] =>
    Ok(views.html.resource.exam.edit(None))
  }

  //编辑试题
  def edit(_id: String) = checkLogin.async { implicit request: Request[AnyContent] =>
    Future.successful(Ok(views.html.resource.exam.edit(None)))
  }

  //保存试题
  def doEdit = checkLogin.async { implicit request: Request[AnyContent] =>
    Future.successful(Ok(""))
  }

  //提交试题答案
  def doSubmitAnswer = checkLogin.async { implicit request: Request[AnyContent] =>
    Form(tuple("_id" -> nonEmptyText, "option" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(Json.obj("status" -> 1, "msg" -> "您的输入有误！"))),
      tuple => {
        val uid = RequestHelper.getUid
        val (_id, option) = tuple
        mongo.findById[Resource](_id).map{
          case Some(r) =>
            if (r.exam.get.answers.exists(_.uid == uid)) {
              Ok(Json.obj("status" -> 1, "msg" -> "很抱歉，您已参与过答题！"))
            } else {
              mongo.updateById[Resource](_id, Json.obj("$set" -> Json.obj("exam" -> r.exam.get.copy(answers = r.exam.get.answers :+ ExamAnswer(uid, option)))))
              Ok(Json.obj("status" -> 0))
            }
          case None =>
            Ok(Json.obj("status" -> 1, "msg" -> "您的输入有误！"))
        }
      }
    )
  }

}
