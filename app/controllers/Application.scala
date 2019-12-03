package controllers

import java.io.{ByteArrayOutputStream, File}

import javax.inject._
import akka.stream.Materializer
import akka.util.ByteString
import cn.playscala.mongo.Mongo
import domain.core.AuthenticateManager
import domain.infrastructure.repository.mongo.{MongoBoardRepository, MongoResourceRepository, MongoUserRepository}
import models._
import org.apache.pdfbox.pdmodel.PDDocument
import org.bson.BsonObjectId
import org.bson.types.ObjectId
import org.joda.time.DateTime
import play.api.{Configuration, Logger}
import play.api.cache.AsyncCacheApi
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import play.api.libs.concurrent.Futures
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import services.{CommonService, ElasticService, MailerService, WeiXinService}
import utils.PDFUtil.{getCatalogs, getText}
import utils._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.Random
import scala.collection.JavaConverters._
import play.api.libs.json.Json._
import security.PasswordEncoder
import play.api.libs.concurrent.Futures._
import vcode.GIFCaptcha
import domain.core.LoginResult

@Singleton
class Application @Inject()(cc: ControllerComponents, passwordEncoder: PasswordEncoder, mongo: Mongo, counterService: CommonService, elasticService: ElasticService, mailer: MailerService, weiXinService: WeiXinService, cache: AsyncCacheApi,
  userAction: UserAction, config: Configuration, authenticateManager: AuthenticateManager, resourceRepo: MongoResourceRepository, userRepo: MongoUserRepository, boardRepo: MongoBoardRepository)(implicit ec: ExecutionContext, mat: Materializer, parser: BodyParsers.Default, futures: Futures) extends AbstractController(cc) {

  // 分页大小
  val PAGE_SIZE = 15

  def icons = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.icons())
  }

  def index(resType: String, status: String, page: Int) = Action.async { implicit request: Request[AnyContent] =>
    val cPage = AppUtil.parsePage(page)
    var q = obj("visible" -> true)
    if(resType != ""){
      q ++= obj("resType" -> resType)
    }
    status match {
      case "0" =>
      case "1" => q ++= obj("closed" -> false)
      case "2" => q ++= obj("closed" -> true)
      case "3" => q ++= obj("recommended" -> true)
    }
    for {
      topNews <- resourceRepo.findTopList(5)
      news <- resourceRepo.findList(q, Json.obj("createTime" -> -1), (cPage-1) * PAGE_SIZE, PAGE_SIZE)
      total <- resourceRepo.count(q)
      activeUsers <- userRepo.findActiveList(12)
      topViewDocs <- resourceRepo.findTopViewList(10)
      boards <- boardRepo.findTop(11)
    } yield {
      Ok(views.html.index(resType, status, topNews, news, activeUsers, topViewDocs, boards, cPage, total.toInt))
    }
  }

  /**
    * 用户名密码登录
    */
  def login(login: Option[String]) = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.login())
  }

  /**
    * 微信小程序扫码登录
    */
  def weixinLogin = Action { implicit request =>
    val uuid = "scan-" + ObjectId.get.toHexString
    Ok(views.html.weixinLogin(uuid))
  }

  /**
    * 生成微信小程序码
    */
  def renderAppCodeImage(uuid: String) = Action.async { implicit request: Request[AnyContent] =>
    weiXinService.getAppCodeImage(uuid) map {
      case Some(entity) => Ok.sendEntity(entity)
      case None => NoContent
    }
  }

  /**
    * 等待微信扫码并处理
    */
  def waitWeiXinScanResult(uuid: String) = Action.async { implicit request: Request[AnyContent] =>

    val promise = Promise[User]()
    cache.set(s"app_code_${uuid}", promise, 180.seconds)

    // 等待扫码结果
    promise.future.withTimeout(180.seconds).flatMap{ u =>
      authenticateManager.generateSession(u).map{ session =>
        Ok(Json.obj("code" -> 0))
          .withSession((session ::: List("loginType" -> LoginType.WEIXIN.toString)): _*)
      }
    } recover {
      case e: scala.concurrent.TimeoutException =>
        Logger.error("Scan app code timeout for " + uuid)
        Ok(Json.obj("code" -> 1, "message" -> "扫码超时！"))
      case t: Throwable =>
        Logger.error("Application.waitAppCodeScanResult error: " + t.getLocalizedMessage, t)
        Ok(Json.obj("code" -> 1, "message" -> "系统繁忙，请稍后再试！"))
    }
  }

  def logout = checkLogin(parser, ec) { implicit request: Request[AnyContent] =>
    Redirect("/login").withNewSession
  }

  def doLogin = Action.async { implicit request: Request[AnyContent] =>
    Form(tuple("login" -> nonEmptyText, "password" -> nonEmptyText, "verifyCode" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Redirect(routes.Application.message("系统提示", "用户名或密码错误！"))),
      tuple => {
        val (login, password, verifyCode) = tuple
        if (HashUtil.sha256(verifyCode.toLowerCase) == request.session.get("verifyCode").getOrElse("")) {
          authenticateManager.login(login, password) map {
            case LoginResult(0, _, Some(u), session) =>
              u.activeCode match {
                case Some(_) => Redirect("/user/activate").withSession(session: _*)
                case None => Redirect("/").withSession(session: _*)
              }
            case _ =>
              Redirect(routes.Application.message("系统提示", "用户名或密码错误！"))
          }
        } else {
          Future.successful(Redirect(routes.Application.message("系统提示", "验证码输入错误！")))
        }
      }
    )
  }

  def message(title: String, message: String) = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.message(title, message))
  }

  def search(q: String, plate: String, page: Int) = Action.async { implicit request: Request[AnyContent] =>
    val cPage = if(page < 1){1}else{page}

    if (app.Global.esEnabled && app.Global.isElasticReady) {
      elasticService.search(q, cPage).map { t =>
        //t._2.foreach(println _)
        Ok(views.html.search(q, plate, t._2, cPage, t._1))
      }
    } else {
      Future.successful(Ok(views.html.search(q, "", Nil, 0, 0)))
    }
  }

  // FIXME: 临时索引电子书
  def indexBook = Action { implicit request: Request[AnyContent] =>
    val doc = PDDocument.load(new File("f:/Scala in Depth.pdf"))
    /*val bookId = "p-i-s-3"
    val bookName = "Programming in Scala - Third Edition"
    val author = "Martin Odersky"*/
    /*val bookId = "scala-cookbook"
    val bookName = "Scala Cookbook"
    val author = "Alvin Alexander"*/
    val bookId = "scala-in-depth"
    val bookName = "Scala In Depth"
    val author = "Joshua D. Suereth"

    val totalPages = doc.getNumberOfPages
    val items = doc.getDocumentCatalog.getDocumentOutline.children().asScala.flatMap( i => getCatalogs("/", i))
    items.foreach(println _)
    val scannedItems =
      items.filter(_._3 >= 0).sliding(2).toList.map(_.toList).map{ t =>
        val endPage = if (t(0)._3 == t(1)._3) { t(0)._3 } else { t(1)._3 - 1 }
        t(0).copy(_4 = endPage)
      }
    (scannedItems ::: List(items.last.copy(_4 = totalPages))).map(i => i.copy(_5 = PDFUtil.getText(doc, i._3, i._4))).foreach{ t =>
      val doc = IndexedDocument(HashUtil.md5(bookName + t._2), "book", t._2, t._5, "沐风", "1", System.currentTimeMillis, None, None, Some(BookInfo(bookId, bookName, author, t._1, t._2, t._3, t._4)))
      elasticService.insert(doc)
      Thread.sleep(50)
      println("Index " + t._2)
    }
    Ok("Finish.")
  }

  def register = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.register())
  }

  def doRegister = Action.async { implicit request: Request[AnyContent] =>
    Form(tuple("login" -> nonEmptyText, "name" -> nonEmptyText, "password" -> nonEmptyText, "repassword" -> nonEmptyText, "verifyCode" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Redirect(routes.Application.message("系统提示", "您的填写有误！"))),
      tuple => {
        val (login, name, password, repassword, verifyCode) = tuple
        if (HashUtil.sha256(verifyCode.toLowerCase) == request.session.get("verifyCode").getOrElse("")) {
          userRepo.findById(login) flatMap {
            case Some(u) =>
              Future.successful(Redirect(routes.Application.message("系统提示", "您已经注册过了！")))
            case None =>
              if (password == repassword) {
                val activeCode = (0 to 7).map(i => Random.nextInt(10).toString).mkString
                val newSalt = passwordEncoder.createSalt()
                for {
                  uid <- counterService.getNextSequence("user-sequence")
                  _ <- userRepo.add(User(uid.toString, Role.USER, login, HashUtil.sha256(password), UserSetting(name, HanLPUtil.convertToPinyin(name), "", "", "/assets/images/head.png", ""),
                    UserStat.DEFAULT, 0, true, "register", request.remoteAddress, None, Nil, Some(activeCode),
                    salt = Option(newSalt), argon2Hash = Option(passwordEncoder.hash(password, newSalt))))
                } yield {
                  val subject = s"请激活您的${app.Global.siteSetting.name}账户！"
                  // 发送激活码
                  mailer.sendEmail(name, login, subject, views.html.mail.activeMail(name, activeCode).body)
                  Redirect(routes.UserController.activate())
                    .withSession("login" -> login, "uid" -> uid.toString, "name" -> name, "headImg" -> "/assets/images/head.png")
                }
              } else {
                Future.successful(Redirect(routes.Application.message("系统提示", "您两次输入的密码不一致！")))
              }
          }
        } else {
          Future.successful(Redirect(routes.Application.message("系统提示", "验证码输入错误！")))
        }
      }
    )
  }

  def resetPassword = Action { implicit request: Request[AnyContent] =>
      Ok(views.html.resetPassword())
    }

  def doResetPassword= Action.async { implicit request: Request[AnyContent] =>
    Form(tuple("login" -> nonEmptyText,  "password" -> nonEmptyText, "repassword" -> nonEmptyText, "verifyCode" -> nonEmptyText)).bindFromRequest().fold(
        errForm => Future.successful(Redirect(routes.Application.message("重置密码出错了", "您的填写有误！"))),
        tuple => {
          val (login, password, repassword, verifyCode) = tuple
          if (HashUtil.sha256(verifyCode.toLowerCase) == request.session.get("verifyCode").getOrElse("")) {
            userRepo.findById(login) flatMap {
              case Some(u) =>
                if (password == repassword) {
                  val name = u.setting.name
                  val sendTime = DateTime.now().getMillis
                  val subject = s"请确认重置您的${app.Global.siteSetting.name}账户密码！"
                  val p = s"${sendTime},${login},${HashUtil.sha256(password)}"

                  //对发送的参数进行加密传输，没有配置key则默认区官网地址作为key
                  val cryptoP = CryptoUtil.encrypt(config.getOptional[String]("resetPassword.key").getOrElse("https://www.playscala.cn"),p)
                  mailer.sendEmail(name, login, subject, views.html.mail.resetPassword(name, cryptoP).body)

                  Future.successful(Redirect(routes.Application.message("系统提示", "操作成功,请检查邮箱确认密码重置。")))
                } else {
                  Future.successful(Redirect(routes.Application.message("注册出错了", "您两次输入的密码不一致！")))
                }
              case None =>
                Future.successful(Redirect(routes.Application.message("重置密码出错了", "未注册的邮箱！")))
            }
          } else {
            Future.successful(Redirect(routes.Application.message("操作出错了！", "验证码输入错误！")))
          }
        }
      )}

  def verifyResetPassword(p:String) = Action.async { implicit request: Request[AnyContent] =>
    val key = config.getOptional[String]("resetPassword.key").getOrElse("https://www.playscala.cn")

    //参数传递过程中,会把+转化成空格，所以替换回来,或者去掉playframework的安全相关的过滤器
    val decrptP = CryptoUtil.decrypt(key,p.replaceAll(" ","+"))
    val params = decrptP.split(",")
    val (sendTime,login,enPassword) =(params(0),params(1),params(2))
    val nowTime =  DateTime.now().getMillis

    if((nowTime - sendTime.toLong) > config.getOptional[Int]("resetPassword.timeout").getOrElse(300000)){
      Future.successful(Redirect(routes.Application.message("系统提示", "操作失败,邮件超过有效期,请重新操作。")))
    } else {
      userRepo.findById(login) flatMap {
        case Some(u) =>
          //update encode password by user id
          passwordEncoder.updateUserPassword(u._id, enPassword)
          Future.successful(Redirect(routes.Application.message("系统提示", "密码修改成功！")))
        case None =>
          Future.successful(Redirect(routes.Application.message("系统提示", "用户不存在！")))
      }
    }
  }



  def doSendActiveMail = (checkLogin andThen userAction) { implicit request =>
    val subject = s"请激活您的${app.Global.siteSetting.name}账户！"
    // 发送激活码
    mailer.sendEmail(RequestHelper.getName, RequestHelper.getLogin, subject, views.html.mail.activeMail(RequestHelper.getName, request.user.activeCode.getOrElse("您的账号已经激活了！")).body)
    Ok(Json.obj("status" -> 0))
  }

  def doActive = (checkLogin andThen userAction) { implicit request =>
    Form(single("activeCode" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Redirect(routes.Application.message("系统提示", "您的填写有误！")),
      activeCode => {
        if (activeCode.trim == request.user.activeCode.getOrElse("")) {
          request.user.setToActive()
          Redirect(routes.UserController.activate())
            .addingToSession("active" -> "1")
        } else {
          Redirect(routes.Application.message("系统提示", "您输入的激活码不正确！"))
        }
      }
    )
  }

  /**
    * 自动注册单点登录用户
    */
  def autoRegister = Action.async { implicit request: Request[AnyContent] =>
    mongo.find[User](Json.obj("login" -> RequestHelper.getLogin)).first.flatMap {
      case Some(u) =>
        Future.successful {
          Redirect(routes.Application.index("0", "/", 1))
            .addingToSession("uid" -> u._id, "login" -> u.login, "name" -> u.setting.name, "headImg" -> u.setting.headImg, "role" -> u.role, "active" -> "1")
        }
      case None =>
        for{
          uid <- counterService.getNextSequence("user-sequence")
          _ <- userRepo.add(User(uid.toString, Role.USER, RequestHelper.getLogin, "", UserSetting(RequestHelper.getName, HanLPUtil.convertToPinyin(RequestHelper.getName), "", "", RequestHelper.getHeadImg, ""), UserStat.DEFAULT, 0, true, request.session.get("from").getOrElse(""), request.remoteAddress, None, Nil, None))
        } yield {
          Redirect(routes.Application.index("0", "/", 1))
            .addingToSession("uid" -> uid.toString, "role" -> Role.USER, "active" -> "1")
        }
    }
  }

  def notFound = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.notFound())
  }

  def verifyCode() = Action { implicit request: Request[AnyContent] =>
    val captcha = new GIFCaptcha(200, 80, 6)
    val out: ByteArrayOutputStream = new ByteArrayOutputStream
    captcha.out(out)
    Ok(ByteString(out.toByteArray))
      .as("image/jpeg")
      .addingToSession("verifyCode" -> HashUtil.sha256(captcha.text().toLowerCase))
  }

}
