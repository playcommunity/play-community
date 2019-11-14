package controllers

import cn.playscala.mongo.Mongo
import infrastructure.repository.mongo.MongoBoardRepository
import javax.inject._
import models._
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import play.api.libs.json.Json
import play.api.mvc._
import services.{CommonService, EventService}
import utils.RequestHelper

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BoardController @Inject()(cc: ControllerComponents, boardRepo: MongoBoardRepository)(implicit ec: ExecutionContext, parser: BodyParsers.Default) extends AbstractController(cc) {


  def recordTraffic(boardPath: String) = checkLogin.async { implicit request: Request[AnyContent] =>
    val uid = RequestHelper.getUid
    boardRepo.recordTraffic(boardPath, uid).map {
      case true => Ok(Json.obj("code" -> 0))
      case true => Ok(Json.obj("code" -> 1, "message" -> "error"))
    }
  }

}
