package controllers.api

import java.net.URLEncoder

import akka.stream.Materializer
import cn.playscala.mongo.Mongo
import javax.inject._
import models._
import models.qq.api.QQAccessTokenRequest
import play.api.Configuration
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc._
import services.CommonService
import utils.QQConfig._
import utils._

import scala.concurrent.{ExecutionContext, Future}

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
              .addingToSession("uid" -> u._id, "login" -> u.login, "name" -> u.setting.name, "headImg" -> u.setting.headImg, "role" -> u.role, "active" -> "1")
          } else {
            Redirect(controllers.routes.Application.index("0", "/", 1))
              .addingToSession("uid" -> u._id, "login" -> u.login, "name" -> u.setting.name, "headImg" -> u.setting.headImg, "role" -> u.role, "active" -> "1")
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
    if (state.equals("playscala")) {
      Future.successful(Ok(views.html.message("系统提示", "非法登录请求")))
    }
    //保存openId
    var openId = ""
    //通用请求头
    val headers: (String, String) = "Accept" -> "application/json"
    //没有设置这个就抛异常。
    IsConditionException.conditionException(QQ_APPID == null || "".equals(QQ_APPID)
      || QQ_APPKEY == null || "".equals(QQ_APPKEY) || !QQ_OPEN_ID.startsWith("https://") ||
      !QQ_ACCESS_TOKEN.startsWith("https://") || !QQ_USER_INFO.startsWith("https://"))
    //匹配目前随意
    val accessTokenRequest = new QQAccessTokenRequest("authorization_code", QQ_APPID, QQ_APPKEY, code, URLEncoder.encode("https://www.playscala.cn"))
    //通过Authorization Code获取Access Token
    ws.url(QQ_ACCESS_TOKEN + accessTokenRequest.toString)
      //需要存储，Access_Token的有效期默认是3个月，最后持久化，定时刷新
      .withHttpHeaders(headers).get().map(_.json("access_token").as[String]).flatMap { access_token =>
      //使用Access Token来获取用户的OpenID
      ws.url(QQ_OPEN_ID + s"access_token=$access_token")
        .withHttpHeaders(headers).get().map(_.json("openid").as[String]).flatMap { openid =>
        openId = openid
        //使用Access Token以及OpenID来访问
        ws.url(QQ_USER_INFO + s"access_token=$access_token&oauth_consumer_key=$QQ_APPID&openid=$openid")
          .withHttpHeaders(headers).get().map(_.json)
      }
    }.flatMap {
      js =>
        //用户账号就是openId
        val qqUrl = "qq.com"
        val ret = js("ret").as[Int]
        val msg = js("msg").asOpt[String].getOrElse("")
        if (ret < 0) {
          Future.successful(Ok(views.html.message("系统提示", msg))) //错误
        }
        //40*40的头像一定存在
        val headImg = js("figureurl_qq_1").as[String]
        val name = js("nickname").as[String]
        //默认男
        val gender = js("gender").as[String]
        var sex = ""
        if ("男".equals(gender)) {
          sex = "male"
        } else {
          sex = "female"
        }
        val login = IntHash.hashOpenId(openId).asInstanceOf[String]
        //通过open作为唯一用户标识，hash成Int后的值作为用户的账号，待考虑
        mongo.find[User](Json.obj("login" -> Json.toJsFieldJsValueWrapper(login))).first.flatMap {
          case Some(u) => Future.successful(u)
          case None => for {
            uid <- counterService.getNextSequence("user-sequence")
            u = User(uid.toString, Role.USER, "", "", UserSetting(name, sex, "", headImg, "中国"),
              UserStat.DEFAULT, 0, true, "", request.remoteAddress, None, List(Channel("qq", "QQ", qqUrl)), None)
            _ <- mongo.insertOne[User](u) //创建用户并重定向到
          } yield u
        }.map { u =>
          //来自QQ的
          if (u.channels != null && u.channels.head.name.equals("QQ") && u.password == "") {
            //QQ用户的密码没有设置过
            Redirect(controllers.routes.UserController.setting("pass"))
              .addingToSession("uid" -> u._id, "login" -> u.login, "name" -> u.setting.name, "headImg" -> u.setting.headImg, "role" -> u.role, "active" -> "1")
          } else {
            //密码设置过
            Redirect(controllers.routes.Application.index("0", "/", 1))
              .addingToSession("uid" -> u._id, "login" -> u.login, "name" -> u.setting.name, "headImg" -> u.setting.headImg, "role" -> u.role, "active" -> "1")
          }
        }
    }
    Future.successful(Ok(views.html.message("系统提示", "系统暂不支持QQ登录！")))
  }

}
