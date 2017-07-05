package controllers

import javax.inject._

import play.api._
import play.api.mvc._
import play.modules.reactivemongo.{MongoController, ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.play.json.collection.JSONCollection
import play.api.libs.concurrent.Execution.Implicits.defaultContext

@Singleton
class HomeController @Inject()(val reactiveMongoApi: ReactiveMongoApi) extends Controller {
  def robotColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-robot"))

  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }

  def login(login: Option[String]) = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.login())
  }

  def doLogin = Action { implicit request: Request[AnyContent] =>
    val login = request.body.asFormUrlEncoded.get.get("login").getOrElse(List(""))(0)
    val password = request.body.asFormUrlEncoded.get.get("password").getOrElse(List(""))(0)
    Redirect(routes.HomeController.index()).withSession("login" -> login, "name" -> login)
  }
}
