package controllers.api

import akka.stream.Materializer
import cn.playscala.mongo.Mongo
import config.QQConfig._
import enums.LoginType._
import javax.inject._
import models._
import models.qq.api.QQAccessTokenRequest
import play.api.Configuration
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc._
import services.CommonService
import utils._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

@Singleton
class InternalApiController @Inject()(cc: ControllerComponents, mongo: Mongo, config: Configuration, ws: WSClient, counterService: CommonService)(implicit ec: ExecutionContext, mat: Materializer, parser: BodyParsers.Default) extends AbstractController(cc) {

  private val githubClientId = config.getOptional[String]("oauth.github.clientId").getOrElse("")
  private val githubClientSecret = config.getOptional[String]("oauth.github.clientSecret").getOrElse("")

  /**
   * 如果GitHub用户未公开邮箱，则无法获取邮箱信息
   */
  def githubOauthCallback(code: String, state: String) = Action.async { implicit request =>
    if (githubClientId != "" && githubClientSecret != "") {
      ws.url("https://github.com/login/oauth/access_token")
        .withHttpHeaders("Accept" -> "application/json")
        .post(Map("client_id" -> githubClientId, "client_secret" -> githubClientSecret, "code" -> code, "state" -> "play"))
        .map(_.json("access_token").as[String])
        .flatMap { access_token =>
          ws.url(s"https://api.github.com/user?access_token=${access_token}")
            .withHttpHeaders("Accept" -> "application/json")
            .get().map(_.json)
        }.flatMap { js =>
        val login = js("login").as[String]
        val email = js("email").asOpt[String].getOrElse(login)
        val name = js("name").asOpt[String].getOrElse(login)
        val headImg = js("avatar_url").as[String]
        val location = js("location").asOpt[String].getOrElse("")
        val bio = js("bio").asOpt[String].getOrElse("")
        val githubUrl = js("html_url").as[String]

        mongo.find[User](Json.obj("$or" -> Json.arr(Json.obj("login" -> email), Json.obj("login" -> login)))).first.flatMap {
          case Some(u) => Future.successful(u)
          case None => for {
            uid <- counterService.getNextSequence("user-sequence")
            u = User(uid.toString, Role.USER, email, "", UserSetting(name, "", bio, headImg, location), UserStat.DEFAULT, 0, true, "register", request.remoteAddress, None, List(Channel("github", "GitHub", githubUrl)), None)
            wr <- mongo.insertOne[User](u)
          } yield u
        }.map { u =>
          if (u.password == "" && email.contains("@")) {
            Redirect(controllers.routes.UserController.setting("pass"))
              .addingToSession("uid" -> u._id, "login" -> u.login, "name" -> u.setting.name, "headImg" -> u.setting.headImg, "role" -> u.role, "active" -> "1",
                "loginType" -> GITHUB)
          } else {
            Redirect(controllers.routes.Application.index("0", "/", 1))
              .addingToSession("uid" -> u._id, "login" -> u.login, "name" -> u.setting.name, "headImg" -> u.setting.headImg, "role" -> u.role, "active" -> "1",
                "loginType" -> GITHUB)
          }
        }
      }
    } else {
      Future.successful(Ok(views.html.message("系统提示", "系统暂不支持GitHub登录！")))
    }
  }

  /**
   * QQ登录的不需要重定向到绑定页面，目前没有存accessToken
   *
   * @param code
   * @param state
   * @return
   */
  def qqOauthCallback(code: String, state: String) = Action.async { implicit request =>
    //通用请求头
    val headers = Seq("Accept" -> "application/json", "Content-Type" -> "application/json")
    //没有设置这个就抛异常。
    IsCondition.conditionException(QQ_APPID == null || "".equals(QQ_APPID)
      || QQ_APPKEY == null || "".equals(QQ_APPKEY) || !QQ_OPEN_ID.startsWith("https://") || !QQ_ACCESS_TOKEN.startsWith("https://")
      || !QQ_USER_INFO.startsWith("https://"))
    //匹配目前随意 QQ_CALL_BACK
    val accessTokenRequest = new QQAccessTokenRequest("authorization_code", QQ_APPID, QQ_APPKEY, code, QQ_CALL_BACK)
    //通过Authorization Code获取Access Token，测试时注释第一个ws调用，传入一个自己的Access Token
    //浏览器 http://localhost/api/internal/oauth/qq/callback?code=E3342282FC5C19091693C6FFE45B20FB&state=playscala
    //    val access_token = "************"
    val user = for {
      access_token <- ws.url(QQ_ACCESS_TOKEN + accessTokenRequest.toString).withHttpHeaders(headers: _*).get()
        .map(x => StringUtil.getString(x.body))
      //测试从以下开始，跳过上面的接口
      openid <- ws.url(QQ_OPEN_ID + s"access_token=$access_token").withHttpHeaders(headers: _*)
        .get().map(x => StringUtil.getJsonBody(x.body)("openid").asOpt[String].getOrElse(""))
      js <- ws.url(QQ_USER_INFO + s"access_token=$access_token&oauth_consumer_key=$QQ_APPID&openid=$openid")
        .withHttpHeaders(headers: _*).get().map(_.json)
    } yield {
      val qqUrl = "qq.com"
      val ret = js("ret").as[Int]
      val msg = js("msg").asOpt[String].getOrElse("")
      if (ret < 0) {
        Future.successful(Ok(views.html.message("系统提示", msg))) //错误
      }

      //40*40的头像一定存在
      //先取QQ 100大头像，若存在则取QQ空间 100大头像，若取不到，则使用40小头像。
      val headImg = js("figureurl_qq_2").asOpt[String].getOrElse(js("figureurl_2")
        .asOpt[String].getOrElse(js("figureurl_qq_1").as[String]))
      val name = js("nickname").as[String]
      val province = js("province").asOpt[String].getOrElse("")
      val city = js("city").asOpt[String].getOrElse("")
      //默认是男
      val gender = js("gender").as[String]
      var sex = ""
      if ("男".equals(gender)) {
        sex = "male"
      } else {
        sex = "female"
      }
      //TODO 注释掉
      // val login = IntHash.hashOpenId(openId).asInstanceOf[String]
      //通过open作为唯一用户标识，hash成Int后的值作为用户的账号，待考虑
      // mongo.find[User](Json.obj("login" -> Json.toJsFieldJsValueWrapper(login))).first.flatMap {
      Await.result(mongo.find[User](Json.obj("login" -> Json.toJsFieldJsValueWrapper(openid))).first.flatMap {
        case Some(u) => Future.successful(u)
        case None => for {
          uid <- counterService.getNextSequence("user-sequence")
          u = User(uid.toString, Role.USER, openid, "", UserSetting(name, sex, "", headImg, province + " " + city),
            UserStat.DEFAULT, 0, true, "", request.remoteAddress, None, List(Channel("qq", "QQ", qqUrl)), None)
          _ <- mongo.insertOne[User](u) //创建用户并重定向到
        } yield u
      }, Duration.create("3s"))
    }

    //必须得到用户才能继续，否则这里无法重定向
    user.map { u =>
      Redirect(controllers.routes.Application.index("0", "/", 1))
        .withSession("uid" -> u._id, "login" -> u.login, "name" -> u.setting.name, "headImg" -> u.setting.headImg, "role" -> u.role, "active" -> "1",
          "loginType" -> enumToString(QQ))
    }

    Future.successful(Ok(views.html.message("系统提示", "系统暂不支持QQ登录！")).withNewSession)
  }
}