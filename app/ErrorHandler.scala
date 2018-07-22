/**
  * Created by joymufeng on 2017/7/16.
  */
import javax.inject._

import play.api.http.DefaultHttpErrorHandler
import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.routing.Router
import scala.concurrent._

@Singleton
class ErrorHandler @Inject() (env: Environment, config: Configuration, sourceMapper: OptionalSourceMapper, router: Provider[Router]) extends DefaultHttpErrorHandler(env, config, sourceMapper, router) {
  override def onProdServerError(request: RequestHeader, exception: UsefulException) = {
    Logger.error(exception.getMessage, exception)
    Future.successful(
      Ok(views.html.message("系统错误", "很抱歉，系统繁忙请稍后再试！")(request))
    )
  }

  override def onForbidden(request: RequestHeader, message: String) = {
    Future.successful(
      Forbidden("You're not allowed to access this resource.")
    )
  }
}
