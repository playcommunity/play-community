package controllers.api

import akka.stream.Materializer
import cn.playscala.mongo.Mongo
import infrastructure.repository.mongo.{MongoResourceRepository, MongoUserRepository}
import javax.inject._
import models._
import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist
import play.api.cache.AsyncCacheApi
import play.api.{Configuration, Logger}
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc._
import services.{CommonService, QQService, WeiXinService}
import utils._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, Promise}

@Singleton
class InternalApiController @Inject()(cc: ControllerComponents, mongo: Mongo, config: Configuration, ws: WSClient, counterService: CommonService, qqService: QQService,
  weixinService: WeiXinService, userRepository: MongoUserRepository, resourceRepo: MongoResourceRepository, cache: AsyncCacheApi)(implicit ec: ExecutionContext, mat: Materializer, parser: BodyParsers.Default) extends AbstractController(cc) {

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
                "loginType" -> LoginType.GITHUB)
          } else {
            Redirect(controllers.routes.Application.index("0", "/", 1))
              .addingToSession("uid" -> u._id, "login" -> u.login, "name" -> u.setting.name, "headImg" -> u.setting.headImg, "role" -> u.role, "active" -> "1",
                "loginType" -> LoginType.GITHUB)
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
    (for {
      accessToken <- qqService.getAccessToken(code)
      openId <- qqService.getOpenId(accessToken)
      userOpt <- qqService.getUserInfo(accessToken, openId)
    } yield {
      userOpt match {
        case Some(u) =>
          Redirect(controllers.routes.Application.index("0", "/", 1))
            .withSession("uid" -> u._id, "login" -> u.login, "name" -> u.setting.name, "headImg" -> u.setting.headImg, "role" -> u.role, "active" -> "1", "loginType" -> LoginType.QQ)

        case None =>
          Ok(views.html.message("系统提示", "很抱歉，QQ登录失败！"))
      }
    }).recover{
      case t: Throwable =>
        Logger.error("QQ登录异常：" + t.getMessage, t)
        Ok(views.html.message("系统提示", "很抱歉，QQ登录异常！"))
    }
  }

  /**
    * 扫码微信小程序码，成功获取授权码后回调接口
    */
  def weixinOauthCallback = Action.async { implicit request =>
    Logger.info("recive weixinOauthCallback request: " + request.body.asJson.toString)

    request.body.asJson match {
      case Some(json) =>
        val code = json("code").as[String]
        val uuid = json("uuid").as[String]
        val nickName = json("nickName").as[String]
        val city = json("city").as[String]
        val gender = json("gender").as[Int]
        val country = json("country").as[String]
        val province = json("province").as[String]
        val avatarUrl = json("avatarUrl").as[String]

        Logger.info(s"weixinOauthCallback - code: ${code}, uuid: ${uuid}, nickName:${nickName}")

        // FIXME: 使用下面注释代码
        weixinService.getSessionByCode(code).flatMap{
          case Some((openid, unionid, session_key)) =>
            userRepository.findByChannelId(openid) flatMap {
              // 用户已存在
              case Some(user) =>
                Logger.info("weixinOauthCallback: find existing user for " + openid)
                //promise.success(user)

                // FIXME
                ("undefined" :: "123" :: uuid :: app.Global.appCodes).distinct.foreach{ id =>
                  cache.get[Promise[User]](s"app_code_${id}") foreach {
                    case Some(p) => p.success(user)
                    case None => Logger.error("Expired uuid: " + id)
                  }
                }
                app.Global.appCodes = List.empty[String]

                Future.successful(Ok(Json.obj("code" -> 0, "openid" -> openid)))

              // 创建用户
              case None =>
                Logger.info("weixinOauthCallback: create user for " + openid)
                for {
                  uid <- counterService.getNextSequence("user-sequence")
                  user = User(uid.toString, Role.USER, openid, "", UserSetting(nickName, gender.toString, "", avatarUrl, city), UserStat.DEFAULT, 0, true, "register", request.remoteAddress, None, List(Channel(openid, "WeiXin", "")), None)
                  wr <- mongo.insertOne[User](user)
                } yield {
                  //promise.success(user)

                  // FIXME
                  ("undefined" :: "123" :: uuid :: app.Global.appCodes).distinct.foreach{ id =>
                    cache.get[Promise[User]](s"app_code_${id}") foreach {
                      case Some(p) => p.success(user)
                      case None => Logger.error("Expired uuid: " + id)
                    }
                  }
                  app.Global.appCodes = List.empty[String]


                  Ok(Json.obj("code" -> 0, "openid" -> openid, "unionid" -> unionid))
                }
            }
          case None =>
            Future.successful(Ok(Json.obj("code" -> 1, "message" -> "Get session failed.")))
        }.recover{
          case t: Throwable =>
            Logger.error("WeiXin登录异常：" + t.getMessage, t)
            Ok(Json.obj("code" -> 1, "message" -> "Please retry later."))
        }

        /*cache.get[Promise[User]](s"app_code_${uuid}").flatMap{
          case Some(promise) =>
            weixinService.getSessionByCode(code).flatMap{
              case Some((openid, unionid, session_key)) =>
                userRepository.findByChannelId(openid) flatMap {
                  // 用户已存在
                  case Some(user) =>
                    Logger.info("weixinOauthCallback: find existing user for " + openid)
                    promise.success(user)
                    Future.successful(Ok(Json.obj("code" -> 0, "openid" -> openid)))

                  // 创建用户
                  case None =>
                    Logger.info("weixinOauthCallback: create user for " + openid)
                    for {
                      uid <- counterService.getNextSequence("user-sequence")
                      user = User(uid.toString, Role.USER, openid, "", UserSetting(nickName, gender.toString, "", avatarUrl, city), UserStat.DEFAULT, 0, true, "register", request.remoteAddress, None, List(Channel(openid, "WeiXin", "")), None)
                      wr <- mongo.insertOne[User](user)
                    } yield {
                      promise.success(user)
                      Ok(Json.obj("code" -> 0, "openid" -> openid, "unionid" -> unionid))
                    }
                }
              case None =>
                Future.successful(Ok(Json.obj("code" -> 1, "message" -> "Get session failed.")))
            }

          // 未扫码导致过期
          case None =>
            Logger.error("weixinOauthCallback: Session expired.")
            Future.successful(Ok(Json.obj("code" -> 1, "message" -> "Session expired.")))
        }.recover{
          case t: Throwable =>
            Logger.error("WeiXin登录异常：" + t.getMessage, t)
            Ok(Json.obj("code" -> 1, "message" -> "Please retry later."))
        }*/

      case None =>
        Logger.error("weixinOauthCallback: Invalid request data.")
        Future.successful(Ok(Json.obj("code" -> 1, "message" -> "Invalid request data.")))
    }
  }

  def getArticles(page: Int, resType: String) = Action.async { implicit request =>
    val PAGE_SIZE = 15
    val cPage = AppUtil.parsePage(page)
    var q = Json.obj()
    if(resType != ""){
      q ++= Json.obj("resType" -> resType)
    }
    resourceRepo.findList(q, Json.obj("createTime" -> -1), (cPage-1) * PAGE_SIZE, PAGE_SIZE) map { list =>
      Ok(Json.toJson(list))
    }
  }

  def getArticle(id: String) = Action.async { implicit request =>
    resourceRepo.findById(id) map {
      case Some(res) =>
        val content = res.content.getOrElse("")
        val cleanedHTML = Jsoup.clean(content, Whitelist.basic());
        Ok(Json.toJson(res.copy(content = Some(cleanedHTML))))
      case None => Ok(Json.obj("code" -> -1, "message" -> "not_found"))
    }
  }

}