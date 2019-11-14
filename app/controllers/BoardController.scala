package controllers

import cn.playscala.mongo.Mongo
import infrastructure.repository.mongo.{MongoBoardRepository, MongoCategoryRepository, MongoResourceRepository, MongoUserRepository}
import javax.inject._
import models._
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import play.api.libs.json.Json
import play.api.libs.json.Json.obj
import play.api.mvc._
import services.{CommonService, EventService}
import utils.{AppUtil, RequestHelper}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BoardController @Inject()(cc: ControllerComponents, boardRepo: MongoBoardRepository, resourceRepo: MongoResourceRepository, userRepo: MongoUserRepository, categoryRepo: MongoCategoryRepository)(implicit ec: ExecutionContext, parser: BodyParsers.Default) extends AbstractController(cc) {

  // 分页大小
  val PAGE_SIZE = 15

  /**
    * 按分页查看资源
    */
  def index(category: String, resType: String, status: String, page: Int) = Action.async { implicit request: Request[AnyContent] =>
    val cPage = AppUtil.parsePage(page)
    var q = Json.obj("resType" -> resType, "categoryPath" -> Json.obj("$regex" -> s"^${category}"))
    status match {
      case "0" =>
      case "1" =>
        q ++= Json.obj("closed" -> false)
      case "2" =>
        q ++= Json.obj("closed" -> true)
      case "3" =>
        q ++= Json.obj("recommended" -> true)
      case _ =>
    }

    for {
      resources <- resourceRepo.findList(q, Json.obj("createTime" -> -1), (cPage-1) * PAGE_SIZE, PAGE_SIZE)
      topViewResources <- resourceRepo.findTopViewList(resType, 10)
      topReplyResources <- resourceRepo.findTopReplyList(resType, 10)
      total <- resourceRepo.count(q)
    } yield {
      if (total > 0 && cPage > math.ceil(1.0*total/PAGE_SIZE).toInt) {
        Redirect(s"/${resType}s")
      } else {
        Ok(views.html.resource.index(category, resType, status, resources, topViewResources, topReplyResources, cPage, total.toInt))
      }
    }
  }

  def viewAllBoards() = Action.async { implicit request: Request[AnyContent] =>
    boardRepo.findAll().map { list =>
      Ok(views.html.board.viewAllBoards(list))
    }
  }

  def recordTraffic(boardPath: String) = checkLogin.async { implicit request: Request[AnyContent] =>
    val uid = RequestHelper.getUid
    boardRepo.recordTraffic(boardPath, uid).map {
      case true => Ok(Json.obj("code" -> 0))
      case true => Ok(Json.obj("code" -> 1, "message" -> "error"))
    }
  }

}
