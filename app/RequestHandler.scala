import java.util.regex.Pattern
import javax.inject.Inject

import cn.playscala.mongo.Mongo
import models.{StatIP, StatTraffic, StatVisitor}
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

  def isStaticRequest(request: RequestHeader): Boolean = {
    val path = request.path.toLowerCase
    if(path.endsWith(".css") || path.endsWith(".js") || path.endsWith(".jpg") ||
      path.endsWith(".jpeg") || path.endsWith(".png") || path.endsWith(".gif") || path.endsWith(".ico")) {
      true
    } else {
      false
    }
  }

  override def routeRequest(request: RequestHeader) = {
    val ip = request.remoteAddress
    val hasLogin = request.session.get("login").nonEmpty
    val hourStr = DateTimeUtil.toString(DateTimeUtil.now(), "yyyy-MM-dd HH")

    /*// 统计总流量
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
    mongo.updateOne[StatIP](
      Json.obj("ip" -> ip, "hourStr" -> hourStr),
      Json.obj(
        "$inc" -> Json.obj("count" -> 1, "userCount" -> (if(hasLogin){1}else{0}), "visitorCount" -> (if(hasLogin){0}else{1})),
        "$set" -> Json.obj("updateTime" -> DateTimeUtil.now()),
        "$setOnInsert" -> Json.obj("_id" -> ObjectId.get.toHexString, "createTime" -> DateTimeUtil.now())
      ),
      upsert = true
    )*/

    request.session.get("uid") match {
      // 已登录用户
      case Some(uid) =>
        // 统计用户流量
        if (!isStaticRequest(request)) {
          mongo.updateOne[StatVisitor](
            Json.obj("uid" -> uid, "hourStr" -> hourStr),
            Json.obj(
              "$inc" -> Json.obj("count" -> 1),
              "$set" -> Json.obj("isVisitor" -> false, "updateTime" -> DateTimeUtil.now()),
              "$setOnInsert" -> Json.obj("_id" -> ObjectId.get.toHexString, "createTime" -> DateTimeUtil.now())
            ),
            upsert = true
          )
        }
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
            val visitorId = request.session.get("visitor").getOrElse(ObjectId.get.toHexString)
            if (!isStaticRequest(request)) {
              // 统计访客流量
              mongo.updateOne[StatVisitor](
                Json.obj("uid" -> visitorId, "hourStr" -> hourStr),
                Json.obj(
                  "$inc" -> Json.obj("count" -> 1),
                  "$set" -> Json.obj("isVisitor" -> true, "updateTime" -> DateTimeUtil.now()),
                  "$setOnInsert" -> Json.obj("_id" -> ObjectId.get.toHexString, "createTime" -> DateTimeUtil.now())
                ),
                upsert = true
              )
            }
            if (request.session.get("visitor").nonEmpty) {
              super.routeRequest(request)
            } else {
              Some(Action(Redirect(request.path, request.queryString, 302).withSession("visitor" -> visitorId)))
            }
        }
    }
  }
}
