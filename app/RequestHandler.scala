import java.util.regex.Pattern
import javax.inject.Inject
import cn.playscala.mongo.Mongo
import models.{StatTraffic, StatVisitor}
import org.bson.types.ObjectId
import play.api.{Configuration, Environment}
import play.api.http._
import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.mvc._
import play.api.routing.Router
import utils.DateTimeUtil
import scala.concurrent.ExecutionContext

/**
  * RequestHandler的主要功能是流量统计，为后台统计报表以及恶意攻击提供分析数据。
  */
class RequestHandler @Inject() (mongo: Mongo, router: Router, errorHandler: HttpErrorHandler, env: Environment, config: Configuration, configuration: HttpConfiguration, filters: HttpFilters)(implicit ec: ExecutionContext) extends DefaultHttpRequestHandler(router, errorHandler, configuration, filters) with Rendering with AcceptExtractors {
  val assetsPathPattern = Pattern.compile("/favicon[.]ico|/assets/.*|/resource/.*|/message|/404|/todo|/baidu_verify_5vujE3CYCX.html|/socket.io/.*|/temp/.*")
  val apiPathPattern = Pattern.compile("/api/.*")
  val trafficPathPattern = Pattern.compile("/|/verifyCode|/search|/register|/login|/logout|/forgetPassword|/resetPassword|/sendActiveMail|/user.*|/admin.*|/article.*|/doc.*|/qa.*|/tweet.*")

  override def routeRequest(request: RequestHeader) = {
    // 忽略非PV和Api请求
    if (assetsPathPattern.matcher(request.path).matches() || apiPathPattern.matcher(request.path).matches()) {
      super.routeRequest(request)
    } else {
      val ip = request.remoteAddress
      val hasLogin = request.session.get("login").nonEmpty
      val hourStr = DateTimeUtil.toString(DateTimeUtil.now(), "yyyy-MM-dd HH")

      // 统计总流量
      mongo.updateOne[StatTraffic](
        Json.obj("hourStr" -> hourStr),
        Json.obj(
          "$inc" -> Json.obj("count" -> 1, "userCount" -> (if(hasLogin){1}else{0}), "visitorCount" -> (if(hasLogin){0}else{1})),
          "$set" -> Json.obj("updateTime" -> DateTimeUtil.now()),
          "$setOnInsert" -> Json.obj("_id" -> ObjectId.get.toHexString, "createTime" -> DateTimeUtil.now())
        ),
        upsert = true
      )

      // 统计ip流量
      mongo.updateOne(
        Json.obj("ip" -> ip, "hourStr" -> hourStr),
        Json.obj(
          "$inc" -> Json.obj("count" -> 1, "userCount" -> (if(hasLogin){1}else{0}), "visitorCount" -> (if(hasLogin){0}else{1})),
          "$set" -> Json.obj("updateTime" -> DateTimeUtil.now()),
          "$setOnInsert" -> Json.obj("_id" -> ObjectId.get.toHexString, "createTime" -> DateTimeUtil.now())
        ),
        upsert = true
      )

      request.session.get("uid") match {
        // 已登录用户
        case Some(uid) =>
          // 统计用户流量
          mongo.updateOne[StatVisitor](
            Json.obj("uid" -> uid, "hourStr" -> hourStr),
            Json.obj(
              "$inc" -> Json.obj("count" -> 1),
              "$set" -> Json.obj("isVisitor" -> false, "updateTime" -> DateTimeUtil.now()),
              "$setOnInsert" -> Json.obj("_id" -> ObjectId.get.toHexString, "createTime" -> DateTimeUtil.now())
            ),
            upsert = true
          )
          super.routeRequest(request)

        // 未登录
        case None =>
          request.session.get("login") match {
            // 已在其他地方登录
            case Some(login) =>
              if (request.path == "/autoRegister") {
                super.routeRequest(request)
              } else {
                Some(Action(Redirect("/autoRegister")))
              }
            case None =>
              if (trafficPathPattern.matcher(request.path).matches()) {
                if (request.session.get("visitor").nonEmpty) {
                  // 统计访客流量
                  mongo.updateOne[StatVisitor](
                    Json.obj("uid" -> request.session.get("visitor").get, "hourStr" -> hourStr),
                    Json.obj(
                      "$inc" -> Json.obj("count" -> 1),
                      "$set" -> Json.obj("isVisitor" -> true, "updateTime" -> DateTimeUtil.now()),
                      "$setOnInsert" -> Json.obj("_id" -> ObjectId.get.toHexString, "createTime" -> DateTimeUtil.now())
                    ),
                    upsert = true
                  )
                  super.routeRequest(request)
                } else {
                  val visitorId = ObjectId.get.toHexString
                  Some(Action(Redirect(request.path, request.queryString, 302).withSession("visitor" -> visitorId)))
                }
              } else {
                /**
                  *  views.html.message模板不能使用当前的request，否则会报错：
                  *  [RuntimeException: No CSRF token was generated for this request! Is the CSRF filter installed?]
                  */
                Some(Action{ implicit request =>
                  render {
                    case Accepts.Html() => Results.Ok(views.html.message("系统提示", "您尚未登录，无权执行该操作！"))
                    case Accepts.Json() => Results.Ok(Json.obj("status" -> 1, "msg" -> "您尚未登录，无权执行该操作！"))
                  }(request)
                })
              }
          }
      }
    }
  }
}
