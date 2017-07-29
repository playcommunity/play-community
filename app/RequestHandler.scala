import java.util.regex.Pattern
import javax.inject.Inject

import play.api.{Configuration, Environment}
import play.api.http._
import play.api.inject.Modules
import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.mvc._
import play.api.routing.Router
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.collection.JSONCollection
import utils.DateTimeUtil

import scala.concurrent.ExecutionContext
import reactivemongo.play.json._

class RequestHandler @Inject() (router: Router, errorHandler: HttpErrorHandler, env: Environment, config: Configuration, configuration: HttpConfiguration, filters: HttpFilters, val reactiveMongoApi: ReactiveMongoApi)(implicit ec: ExecutionContext) extends DefaultHttpRequestHandler(router, errorHandler, configuration, filters) {
  val publicPathPattern = Pattern.compile("/favicon[.]ico|/assets/.*|/resource/.*|/message|/404")
  val protectPathPattern = Pattern.compile("/|/verifyCode|/search|/register|/login|/logout|/forgetPassword|/resetPassword|/sendActiveMail|/article.*|/user.*")
  def statTrafficColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("stat-traffic"))
  def statIPColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("stat-ip"))
  def statVisitorColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("stat-visitor"))

  override def routeRequest(request: RequestHeader) = {
    // 忽略非PV请求
    if (publicPathPattern.matcher(request.path).matches()) {
      super.routeRequest(request)
    } else {
      val ip = request.remoteAddress
      val hasLogin = request.session.get("login").nonEmpty
      val hourStr = DateTimeUtil.toString(DateTimeUtil.now(), "yyyy-MM-dd HH")

      // 统计总流量
      statTrafficColFuture.map(_.update(
        Json.obj("hourStr" -> hourStr),
        Json.obj(
          "$inc" -> Json.obj("count" -> 1, "userCount" -> (if(hasLogin){1}else{0}), "visitorCount" -> (if(hasLogin){0}else{1})),
          "$set" -> Json.obj("updateTime" -> DateTimeUtil.now()),
          "$setOnInsert" -> Json.obj("_id" -> BSONObjectID.generate().stringify, "createTime" -> DateTimeUtil.now())
        ),
        upsert = true
      ))

      // 统计ip流量
      statIPColFuture.map(_.update(
        Json.obj("ip" -> ip, "hourStr" -> hourStr),
        Json.obj(
          "$inc" -> Json.obj("count" -> 1, "userCount" -> (if(hasLogin){1}else{0}), "visitorCount" -> (if(hasLogin){0}else{1})),
          "$set" -> Json.obj("updateTime" -> DateTimeUtil.now()),
          "$setOnInsert" -> Json.obj("_id" -> BSONObjectID.generate().stringify, "createTime" -> DateTimeUtil.now())
        ),
        upsert = true
      ))

      request.session.get("uid") match {
        // 已登录用户
        case Some(uid) =>
          // 统计用户流量
          statVisitorColFuture.map(_.update(
            Json.obj("uid" -> uid, "hourStr" -> hourStr),
            Json.obj(
              "$inc" -> Json.obj("count" -> 1),
              "$set" -> Json.obj("isVisitor" -> false, "updateTime" -> DateTimeUtil.now()),
              "$setOnInsert" -> Json.obj("_id" -> BSONObjectID.generate().stringify, "createTime" -> DateTimeUtil.now())
            ),
            upsert = true
          ))
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
              if (protectPathPattern.matcher(request.path).matches()) {
                if (request.session.get("visitor").nonEmpty) {
                  // 统计访客流量
                  statVisitorColFuture.map(_.update(
                    Json.obj("uid" -> request.session.get("visitor").get, "hourStr" -> hourStr),
                    Json.obj(
                      "$inc" -> Json.obj("count" -> 1),
                      "$set" -> Json.obj("isVisitor" -> true, "updateTime" -> DateTimeUtil.now()),
                      "$setOnInsert" -> Json.obj("_id" -> BSONObjectID.generate().stringify, "createTime" -> DateTimeUtil.now())
                    ),
                    upsert = true
                  ))
                  super.routeRequest(request)
                } else {
                  val visitorId = BSONObjectID.generate().stringify
                  Some(Action(Redirect(request.path, request.queryString, 302).withSession("visitor" -> visitorId)))
                }
              } else {
                Some(Action(Forbidden))
              }
          }
      }
    }
  }
}
