import javax.inject.Inject
import cn.playscala.mongo.Mongo
import models.User
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import utils.RequestHelper
import scala.concurrent.{ExecutionContext, Future}

package object controllers {

  class UserRequest[A](val user: User, request: Request[A]) extends WrappedRequest[A](request)
  class UserAction @Inject()(val parser: BodyParsers.Default, mongo: Mongo)(implicit val ec: ExecutionContext) extends ActionBuilder[UserRequest, AnyContent] with ActionRefiner[Request, UserRequest] {
    def executionContext = ec
    def refine[A](input: Request[A]) = {
      mongo.findById[User](input.session("uid")).map{
        case Some(u) =>
          Right(new UserRequest(u, input))
        case None    =>
          Left(Results.NotFound)
      }
    }
  }

  def checkLogin[A](implicit parser: BodyParser[A], ec: ExecutionContext): ActionBuilderImpl[A] = new ActionBuilderImpl(parser) with Rendering with AcceptExtractors {
    override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]) = {
      if (RequestHelper.isLogin(request)) {
        block(request)
      } else {
        Future.successful {
          render {
            case Accepts.Html() => Results.Ok(views.html.message("系统提示", "您尚未登录，无权执行该操作！")(request))
            case Accepts.Json() => Results.Ok(Json.obj("status" -> 1, "msg" -> "您尚未登录，无权执行该操作！"))
          }(request)
        }
      }
    }
  }

  def checkActive[A](implicit parser: BodyParser[A], ec: ExecutionContext): ActionBuilderImpl[A] = new ActionBuilderImpl(parser) with Rendering with AcceptExtractors {
    override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]) = {
      if (RequestHelper.isActive(request)) {
        block(request)
      } else {
        Future.successful {
          render {
            case Accepts.Html() => Results.Redirect(routes.UserController.activate())
            case Accepts.Json() => Results.Ok(Json.obj("status" -> 1, "msg" -> "您的账户尚未激活！"))
          }(request)
        }
      }
    }
  }

  def checkOwner[A](_idField: String) (implicit parser: BodyParser[A], ec: ExecutionContext): ActionBuilderImpl[A] = new ActionBuilderImpl(parser) {
    override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]) = {
      parseField[A](_idField, request) match {
        case Some(_id) =>
          if (_id.startsWith(RequestHelper.getUid(request) + "-")) {
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
      if (RequestHelper.isAdmin(request)) {
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
          if (RequestHelper.isAdmin(request) || _id.startsWith(RequestHelper.getUid(request) + "-")) {
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
