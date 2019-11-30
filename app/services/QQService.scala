package services

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Query
import cn.playscala.mongo.Mongo
import javax.inject.{Inject, Singleton}
import models.{Channel, IndexedDocument, Role, User, UserSetting, UserStat}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsObject, JsString, JsValue, Json}
import play.api.libs.ws.WSClient
import play.api.{Configuration, Environment, Logger}
import utils.HanLPUtil

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

@Singleton
class QQService @Inject()(env: Environment, config: Configuration, mongo: Mongo, ws: WSClient, counterService: CommonService) {
  private val appId = config.getOptional[String]("oauth.qq.appId").getOrElse("")
  private val appKey = config.getOptional[String]("oauth.qq.appKey").getOrElse("")
  private val callbackUrl = app.Global.homeUrl + "/api/internal/oauth/qq/callback"

  val headers = Seq("Accept" -> "application/json", "Content-Type" -> "application/json")

  def getAccessToken(code: String): Future[String] = {
    ws.url("https://graph.qq.com/oauth2.0/token")
      .withHttpHeaders(headers: _*)
      .addQueryStringParameters(
        "grant_type" -> "authorization_code",
        "client_id" -> appId,
        "client_secret" -> appKey,
        "code" -> code,
        "redirect_uri" -> callbackUrl
      ).get().map{ resp =>
      Logger.info("QQLogin getAccessToken: " + resp.body)
      val query = Query(resp.body, mode = Uri.ParsingMode.Strict)
      query.get("access_token").getOrElse("")
    }
  }

  def getOpenId(accessToken: String): Future[String] = {
    ws.url("https://graph.qq.com/oauth2.0/me")
      .withHttpHeaders(headers: _*)
      .addQueryStringParameters(
        "access_token" -> accessToken,
        "format"  -> "json"
      ).get().map{ resp =>
      val bodyStr = resp.body
      Logger.info("QQLogin getOpenId: " + bodyStr)
      val js = Json.parse(bodyStr.substring(10, bodyStr.length() - 3))
      js("openid").asOpt[String].getOrElse("")
    }
  }

  /**
    * 获取用户信息。首先判断是否在数据库中已存在，否则调用腾讯接口读取。
    * @param accessToken
    * @param openId
    * @return
    */
  def getUserInfo(accessToken: String, openId: String): Future[Option[User]] = {
    mongo.find[User](Json.obj("login" -> openId)).first.flatMap{
      case Some(u) => Future.successful(Some(u))
      case None =>
        ws.url("https://graph.qq.com/user/get_user_info")
          .withHttpHeaders(headers: _*)
          .addQueryStringParameters(
            "access_token" -> accessToken,
            "oauth_consumer_key" -> appId,
            "openid" -> openId,
            "format"  -> "json"
          ).get().flatMap{ resp =>
            Logger.info("QQLogin getUserInfo: " + resp.body)

            val js = resp.json
            val ret = js("ret").as[Int]
            val msg = js("msg").asOpt[String].getOrElse("")

            if (ret < 0) {
              Future.successful(None)
            } else {
              // 取头像策略：QQ100大头像 > QQ空间100大头像 > 40小头像 > 系统默认头像
              val headImg =
                js("figureurl_qq_2").asOpt[String] orElse
                js("figureurl_2").asOpt[String] orElse
                js("figureurl_qq_1").asOpt[String] getOrElse
                "/assets/images/head.png"

              val name = js("nickname").as[String]
              val province = js("province").asOpt[String].getOrElse("")
              val city = js("city").asOpt[String].getOrElse("")
              //默认是男
              var sex = if (js("gender").asOpt[String].getOrElse("") == "女") "female" else "male"

              for {
                uid <- counterService.getNextSequence("user-sequence")
                u = User(uid.toString, Role.USER, openId, "", UserSetting(name, HanLPUtil.convertToPinyin(name), sex, "", headImg, province + " " + city),
                         UserStat.DEFAULT, 0, true, "", "", None, List(Channel("qq", "QQ", "qq.com")), None)
                _ <- mongo.insertOne[User](u) //创建用户并重定向到
              } yield Some(u)
            }
        }
    }
  }
}
