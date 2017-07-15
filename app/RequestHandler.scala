import javax.inject.Inject

import play.api.http._
import play.api.mvc.Results._
import play.api.mvc._
import play.api.routing.Router

class RequestHandler @Inject() (router: Router, errorHandler: HttpErrorHandler,
  configuration: HttpConfiguration, filters: HttpFilters) extends DefaultHttpRequestHandler(router, errorHandler, configuration, filters) {

  override def routeRequest(request: RequestHeader) = {
    //println(request.method + " " + request.path)
    if(request.session.get("login").nonEmpty){
      super.routeRequest(request)
      //未登录
    } else {
      if(request.path == null) {
        println("NotAcceptable Request ...")
        Some(Action(Results.NotAcceptable))
        //公开请求
      } else if(request.path.startsWith("/favicon.ico") ||
        request.path.startsWith("/assets/")||
        request.path.startsWith("/message")||
        request.path.startsWith("/404")||
        request.path.startsWith("/test/")||
        request.path.startsWith("/register") ||
        request.path.startsWith("/doRegister") ||
        request.path.startsWith("/login") ||
        request.path.startsWith("/doLogin") ||
        request.path.startsWith("/forgetPassword") ||
        request.path.startsWith("/doForgetPassword") ||
        request.path.startsWith("/resetPassword") ||
        request.path.startsWith("/doResetPassword") ||
        request.path.startsWith("/sendActiveMail") ||
        request.path.startsWith("/doSendActiveMail")
      ) {
        super.routeRequest(request)
      } else {
        Some(Action(Redirect("/login")))
      }
    }

    /*
    if(request.path.startsWith("/cloud/assets/") || request.path.startsWith("/cloud/robot/webjs/")){
      super.routeRequest(request)
    } else {
      Some(Action(Ok("系统升级中...")))
    }
    */

  }
}
