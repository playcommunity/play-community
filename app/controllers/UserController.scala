package controllers

import javax.inject._

import models.User
import models.JsonFormats.userFormat
import reactivemongo.play.json._
import play.api._
import play.api.mvc._
import play.modules.reactivemongo.{MongoController, ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.play.json.collection.JSONCollection
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json

@Singleton
class UserController @Inject()(val reactiveMongoApi: ReactiveMongoApi) extends Controller {
  def robotColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-robot"))
  def userColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-user"))

  def home() = Action.async { implicit request: Request[AnyContent] =>
    for{
      userCol <- userColFuture
      userOpt <- userCol.find(Json.obj("_id" -> request.session("uid").toInt)).one[User]
    } yield {
      userOpt match {
        case Some(u) =>
          Ok(views.html.user.home(u))
        case None =>
          Redirect(routes.Application.login(request.session.get("login")))
      }
    }
  }

  def activate() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.user.activate())
  }

}
