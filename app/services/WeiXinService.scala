package services

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Query
import cn.playscala.mongo.Mongo
import javax.inject.Inject
import play.api.cache.AsyncCacheApi
import play.api.http.HttpEntity
import play.api.libs.json.{ JsObject, Json }
import play.api.{ Configuration, Environment, Logger }
import play.api.libs.ws.WSClient

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class WeiXinService @Inject()(env: Environment, config: Configuration, mongo: Mongo, ws: WSClient, counterService: CommonService, cache: AsyncCacheApi) {

  private val appId = config.getOptional[String]("oauth.weixin.appId").getOrElse("")
  private val appKey = config.getOptional[String]("oauth.weixin.appKey").getOrElse("")

  val headers = Seq("Accept" -> "application/json", "Content-Type" -> "application/json")

  def getAccessToken: Future[Option[String]] = {
    val key = "wx_access_token"
    cache.get[String](key) flatMap {
      case Some(token) => Future.successful(Some(token))
      case None =>
        ws.url("https://api.weixin.qq.com/cgi-bin/token")
          .withHttpHeaders(headers: _*)
          .addQueryStringParameters(
            "grant_type" -> "client_credential",
            "appid" -> appId,
            "secret" -> appKey
          ).get() flatMap { resp =>
          Logger.info("WeiXinService getAccessToken: " + resp.body)
          val obj = Json.parse(resp.body).as[JsObject]
          resp.status match {
            case 200 if obj.keys.exists(_ == "access_token") =>
              val token = (obj \ "access_token").as[String]
              val secs = (obj \ "expires_in").as[Long]
              cache.set(key, token, secs.seconds).map{ _ => Some(token) }
            case e: Any =>
              Logger.error("WeiXinService get tokrn failed: " + e)
              Future.successful(None)
          }
        }
    }
    //val token = "27_y9rCY8JL8oa4COpaQXaV3XyriLUine6yHbpWftgakWlrsARE6JlTQt-kUzpKMYg-_XnsQhxoaZMdwvaMaLSuFgS1p5QykvF2vUKae2FxUaUTXiLX0vH6MFR9kM0rpwx6FIzbw2fLXI4Oxt-fGCMjAIACYI"
    //Future.successful(Some(token))
  }


  /**
    * 获取小程序码图片数据
    * https://developers.weixin.qq.com/miniprogram/dev/api-backend/open-api/qr-code/wxacode.getUnlimited.html
    * @param uuid 全局唯一的回话标识
    */
  def getAppCodeImage(uuid: String): Future[Option[HttpEntity]] = {
    getAccessToken flatMap {
      case Some(token) =>
        ws.url("https://api.weixin.qq.com/wxa/getwxacodeunlimit")
          .withHttpHeaders(headers: _*)
          .addQueryStringParameters(
            "access_token" -> token
          ).post(Json.obj(
            "scene" -> uuid,
            //"page" -> "pages/index/index",
            "width" -> 280
          )) map { wsResp =>
          wsResp.status match {
            case 200 =>
              val contentType   = wsResp.headers.find(t => t._1.trim.toLowerCase == "content-type").map(_._2.mkString("; ")).getOrElse("image/jpeg")
              // If there's a content length, send that, otherwise return the body chunked
              wsResp.headers.find(t => t._1.toLowerCase == "content-length").map(_._2) match {
                case Some(Seq(length)) =>
                  Some(HttpEntity.Streamed(wsResp.bodyAsSource, Some(length.toLong), Some(contentType)))
                case _ =>
                  None
              }
            case _ => None
          }
        }
      case None => Future.successful(None)
    }
  }

  /**
   * 通过授权码换取用户信息
   * @param code 授权码
   * @return (openid, unionid, session_key)
   */
  def getSessionByCode(code: String): Future[Option[(String, String, String)]] = {
    ws.url("https://api.weixin.qq.com/sns/jscode2session")
      .withHttpHeaders(headers: _*)
      .addQueryStringParameters(
        "grant_type" -> "authorization_code",
        "appid" -> appId,
        "secret" -> appKey,
        "js_code" -> code
      ).get() map { resp =>
      Logger.info("WeiXinService getSessionByCode: " + resp.body)
      resp.status match {
        case 200 =>
          val json = Json.parse(resp.body)
          val openid = (json \ "openid").as[String]
          val session_key = (json \ "session_key").as[String]
          val unionid = (json \ "unionid").asOpt[String].getOrElse("")
          Some((openid, unionid, session_key))
        case _ => None
      }
    }
  }



}
