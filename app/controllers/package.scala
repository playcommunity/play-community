import javax.inject.Inject

import models.User
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.play.json.collection.JSONCollection
import utils.UserHelper
import scala.concurrent.{ExecutionContext, Future}
import reactivemongo.play.json._
import models.JsonFormats.userFormat

package object controllers {

  class UserRequest[A](val user: User, request: Request[A]) extends WrappedRequest[A](request)
  class UserAction @Inject()(val parser: BodyParsers.Default, reactiveMongoApi: ReactiveMongoApi)(implicit val ec: ExecutionContext) extends ActionBuilder[UserRequest, AnyContent] with ActionRefiner[Request, UserRequest] {
    def userColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-user"))
    def executionContext = ec
    def refine[A](input: Request[A]) = {
      userColFuture.flatMap(_.find(Json.obj("_id" -> input.session("uid"))).one[User]).map{
        case Some(u) =>
          Right(new UserRequest(u, input))
        case None    =>
          Left(Results.NotFound)
      }
    }
  }

  def checkLogin[A](implicit parser: BodyParser[A], ec: ExecutionContext): ActionBuilderImpl[A] = new ActionBuilderImpl(parser) {
    override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]) = {
      if (UserHelper.isLogin(request)) {
        block(request)
      } else {
        Future.successful(Results.Ok(views.html.message("系统提示", "您无权执行该操作！")(request)))
      }
    }
  }

  def checkOwner[A](_idField: String) (implicit parser: BodyParser[A], ec: ExecutionContext): ActionBuilderImpl[A] = new ActionBuilderImpl(parser) {
    override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]) = {
      parseField[A](_idField, request) match {
        case Some(_id) =>
          if (_id.startsWith(UserHelper.getUid(request) + "-")) {
            block(request)
          } else {
            Future.successful(Results.Ok(views.html.message("系统提示", "您无权执行该操作！")(request)))
          }
        case None =>
          block(request)
      }
    }
  }

  def checkAdmin[A](implicit parser: BodyParser[A], ec: ExecutionContext): ActionBuilderImpl[A] = new ActionBuilderImpl(parser) {
    override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]) = {
      if (UserHelper.isAdmin(request)) {
        block(request)
      } else {
        Future.successful(Results.Ok(views.html.message("系统提示", "您无权执行该操作！")(request)))
      }
    }
  }

  def checkAdminOrOwner[A](_idField: String) (implicit parser: BodyParser[A], ec: ExecutionContext): ActionBuilderImpl[A] = new ActionBuilderImpl(parser) {
    override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]) = {
      parseField[A](_idField, request) match {
        case Some(_id) =>
          if (UserHelper.isAdmin(request) || _id.startsWith(UserHelper.getUid(request) + "-")) {
            block(request)
          } else {
            Future.successful(Results.Ok(views.html.message("系统提示", "您无权执行该操作！")(request)))
          }
        case None =>
          block(request)
      }
    }
  }

  def parseField[A](field: String, request: Request[A]): Option[String] = {
    val _idOptInQuery = request.getQueryString(field)
    val _idOptInBody = request.body match {
      case body: AnyContent =>
        val _idOptInUrlEncoded: Option[String] = body.asFormUrlEncoded.flatMap(_.get(field).flatMap(_.headOption))
        val _idOptInMultipart: Option[String] = body.asMultipartFormData.flatMap(_.dataParts.get(field).flatMap(_.headOption))
        _idOptInUrlEncoded orElse _idOptInMultipart
      case body: JsValue =>
        (body \ field).asOpt[String]
      case _ => None
    }

    _idOptInQuery orElse _idOptInBody
  }

}
