package controllers

import java.util.concurrent.atomic.AtomicInteger
import javax.inject._

import akka.stream.Materializer
import models.{Role, User, UserSetting, UserTimeStat}
import models.JsonFormats.userFormat
import reactivemongo.play.json._
import play.api._
import play.api.data.Form
import play.api.mvc._
import play.api.data.Forms._
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.play.json.collection.JSONCollection
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}
import utils.{DateTimeUtil, HashUtil}

import scala.concurrent.duration._

@Singleton
class UserController @Inject()(cc: ControllerComponents, val reactiveMongoApi: ReactiveMongoApi, resourceController: ResourceController)(implicit ec: ExecutionContext, mat: Materializer) extends AbstractController(cc) {
  def robotColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-robot"))
  def userColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-user"))
  val userAction = new UserAction(new BodyParsers.Default())

  class UserRequest[A](val user: User, request: Request[A]) extends WrappedRequest[A](request)
  class UserAction @Inject()(val parser: BodyParsers.Default)(implicit val ec: ExecutionContext)
    extends ActionBuilder[UserRequest, AnyContent] {
    def executionContext = ec
    override def invokeBlock[A](request: Request[A], block: (UserRequest[A]) => Future[Result]): Future[Result] = {
      /*userColFuture.flatMap(_.find(Json.obj("_id" -> request.session("uid").toInt)).one[User]).flatMap{
        case Some(u) =>
          block(new UserRequest(u, request))
        case None    =>
          Future.successful(Results.Ok("Error"))
      }*/
      Future.successful("1").flatMap{ s => println(s); Future.successful("2")}.flatMap{ s => println(s);block(new UserRequest(null, request)) }
    }
  }
  /*class UserAction @Inject()(val parser: BodyParsers.Default)(implicit val ec: ExecutionContext) extends ActionBuilder[UserRequest, AnyContent] with ActionRefiner[Request, UserRequest] {
    def executionContext = ec
    def refine[A](input: Request[A]) = {
      /*println("refine " + count.addAndGet(1))
      userColFuture.flatMap(_.find(Json.obj("_id" -> input.session("uid").toInt)).one[User]).map{
        case Some(u) =>
          println("get user " + count.get())
          Right(new UserRequest(u, input))
        case None    =>
          println("not found")
          Left(Results.NotFound)
      }.withTimeout(3 seconds).recover{
        case t: Throwable =>
          Left(Results.Ok("Error: " + t.getMessage))
      }*/

      Future.successful(Right(new UserRequest(User(0, Role.COMMON_USER, "", "", UserSetting("", "", "", "", ""), "", UserTimeStat(DateTimeUtil.now, DateTimeUtil.now, DateTimeUtil.now, DateTimeUtil.now()), 0, true, ""), input)))
    }
  }*/


  def home() = Action.async { implicit request: Request[AnyContent] =>
    getUser(request.session("uid")).map{ u =>
      Ok(views.html.user.home(u))
    }
  }

  def activate() = Action.async { implicit request: Request[AnyContent] =>
    getUser(request.session("uid")).map{ u =>
      Ok(views.html.user.activate(u))
    }
  }

  def setting() = Action.async { implicit request: Request[AnyContent] =>
    getUser(request.session("uid")).map{ u =>
      Ok(views.html.user.setting(u))
    }
  }

  def doSetting() = Action.async { implicit request: Request[AnyContent] =>
    Form(tuple("name" -> nonEmptyText, "gender" -> nonEmptyText, "city" -> nonEmptyText, "introduction" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Redirect(routes.Application.message("系统提示", "您的输入有误！" + errForm.errors))),
      tuple => {
        val (name, gender, city, introduction) = tuple
        userColFuture.flatMap(_.update(
          Json.obj("_id" -> request.session("uid")),
          Json.obj(
            "$set" -> Json.obj("setting.name" -> name, "setting.gender" -> gender, "setting.city" -> city, "setting.introduction" -> introduction)
          ))).map{ wr =>
          Redirect(routes.UserController.setting())
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

  def doSetHeadImg = Action.async { implicit request: Request[AnyContent] =>
    Form(single("url" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Redirect(routes.Application.message("系统提示", "您的输入有误！" + errForm.errors))),
      url => {
        userColFuture.flatMap(_.update(
          Json.obj("_id" -> request.session("uid")),
          Json.obj(
            "$set" -> Json.obj("setting.headImg" -> url)
          ))).map{ wr =>
          Redirect(routes.UserController.setting())
        }
      }
    )
  }

  def doSetPassword() = Action.async { implicit request: Request[AnyContent] =>
    Form(tuple("password" -> nonEmptyText, "password1" -> nonEmptyText, "password2" -> nonEmptyText).verifying("两次输入不一致！", t => t._2 == t._3)).bindFromRequest().fold(
      errForm => Future.successful(Redirect(routes.Application.message("系统提示", "您的输入有误！" + errForm.errors.map(_.message).mkString("|")))),
      tuple => {
        val (password, password1, _) = tuple
        getUser(request.session("uid")).map{ u =>
          if (HashUtil.sha256(password) == u.password) {
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
      }
    )
  }

  def getUser(_id: String) : Future[User] = {
    userColFuture.flatMap(_.find(Json.obj("_id" -> _id)).one[User]).map(_.get)
  }

}
