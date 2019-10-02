package controllers

import java.io.{ ByteArrayOutputStream, File }

import javax.inject._
import akka.stream.Materializer
import akka.util.ByteString
import cn.playscala.mongo.Mongo
import models._
import org.apache.pdfbox.pdmodel.PDDocument
import org.joda.time.DateTime
import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms.{ tuple, _ }
import play.api.libs.json.Json
import play.api.mvc._
import services.{ CommonService, ElasticService, MailerService }
import utils.PDFUtil.{ getCatalogs, getText }
import utils._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Random
import scala.collection.JavaConverters._
import play.api.libs.json.Json._
import security.PasswordEncoder

@Singleton
class Application @Inject()(cc: ControllerComponents, mongo: Mongo, counterService: CommonService,
                            elasticService: ElasticService, mailer: MailerService, userAction: UserAction,
                            config: Configuration, passwordEncoder: PasswordEncoder)(implicit ec: ExecutionContext, mat: Materializer,
                                                   parser: BodyParsers.Default) extends AbstractController(cc) {

  def icons = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.icons())
  }

  def index(status: String, category: String, page: Int) = Action.async { implicit request: Request[AnyContent] =>
    val cPage = if(page < 1){1}else{page}
    var q = obj("categoryPath" -> obj("$regex" -> s"^${category}"), "visible" -> true)
    status match {
      case "0" =>
      case "1" => q ++= obj("closed" -> false)
      case "2" => q ++= obj("closed" -> true)
      case "3" => q ++= obj("recommended" -> true)
    }
    for {
      topNews <- mongo.find[Resource](Json.obj("top" -> true, "visible" -> true)).sort(Json.obj("createTime" -> -1)).limit(5).list()
      news <- mongo.find[Resource](q).sort(Json.obj("createTime" -> -1)).skip((cPage-1) * 15).limit(15).list()
      total <- mongo.count[Resource](q)
      activeUsers <- mongo.find[User]().sort(Json.obj("stat.resCount" -> -1)).limit(12).list()
      topViewDocs <- mongo.find[Resource](obj("resType" -> Resource.Doc, "visible" -> true)).sort(Json.obj("viewStat.count" -> -1)).limit(10).list()
    } yield {
      Ok(views.html.index(status, category, topNews, news, activeUsers, topViewDocs, cPage, total.toInt))
    }
  }

  def login(login: Option[String]) = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.login())
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
          for{
              userOpt <- passwordEncoder.findUserAndUpgrade(login, password)
          } yield {
            userOpt match {
              case Some(u) =>
                if (u.activeCode.nonEmpty) {
                  Redirect("/user/activate")
                    .withSession("uid" -> u._id, "login" -> u.login, "name" -> u.setting.name, "headImg" -> u.setting.headImg, "role" -> u.role, "active" -> "0", "loginType" -> LoginType.PASSWORD)
                } else {
                  Redirect(s"/user/home?uid=${u._id}")
                    .withSession("uid" -> u._id, "login" -> u.login, "name" -> u.setting.name, "headImg" -> u.setting.headImg, "role" -> u.role, "active" -> "1", "loginType" -> LoginType.PASSWORD)
                }
              case None =>
                Redirect(routes.Application.message("操作出错了！", "用户名或密码错误！"))
            }
          }
        } else {
          Future.successful(Redirect(routes.Application.message("操作出错了！", "验证码输入错误！")))
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

  // FIXME 临时索引电子书
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
      errForm => Future.successful(Redirect(routes.Application.message("注册出错了", "您的填写有误！"))),
      tuple => {
        val (login, name, password, repassword, verifyCode) = tuple
        if (HashUtil.sha256(verifyCode.toLowerCase) == request.session.get("verifyCode").getOrElse("")) {
          (for {
            userOpt <- mongo.find[User](obj("login" -> login)).first
          } yield {
            userOpt match {
              case Some(u) =>
                Future.successful(Redirect(routes.Application.message("注册出错了", "您已经注册过了！")))
              case None =>
                if (password == repassword) {
                  val activeCode = (0 to 7).map(i => Random.nextInt(10).toString).mkString
                  val newSalt = passwordEncoder.createSalt()
                  for {
                    uid <- counterService.getNextSequence("user-sequence")
                    wr <- mongo.insertOne(User(uid.toString, Role.USER, login, HashUtil.sha256(password), UserSetting(name, "", "", "/assets/images/head.png", ""),
                      UserStat.DEFAULT, 0, true, "register", request.remoteAddress, None, Nil, Some(activeCode),
                      salt = Option(new String(newSalt)), argon2Hash = Option(passwordEncoder.hash(password, newSalt))))
                  } yield {
                    val subject = s"请激活您的${app.Global.siteSetting.name}账户！"
                    // 发送激活码
                    mailer.sendEmail(name, login, subject, views.html.mail.activeMail(name, activeCode).body)
                    Redirect(routes.UserController.activate())
                      .withSession("login" -> login, "uid" -> uid.toString, "name" -> name, "headImg" -> "/assets/images/head.png")
                  }
                } else {
                  Future.successful(Redirect(routes.Application.message("注册出错了", "您两次输入的密码不一致！")))
                }
            }
          }).flatMap(f1 => f1)
        } else {
          Future.successful(Redirect(routes.Application.message("操作出错了！", "验证码输入错误！")))
        }
      }
    )
  }

  def resetPassword = Action { implicit request: Request[AnyContent] =>
      Ok(views.html.resetPassword())
    }

  def doResetPassword= Action.async { implicit request: Request[AnyContent] =>  Form(tuple("login" -> nonEmptyText,  "password" -> nonEmptyText, "repassword" -> nonEmptyText, "verifyCode" -> nonEmptyText)).bindFromRequest().fold(
        errForm => Future.successful(Redirect(routes.Application.message("重置密码出错了", "您的填写有误！"))),
        tuple => {
          val (login, password, repassword, verifyCode) = tuple
          if (HashUtil.sha256(verifyCode.toLowerCase) == request.session.get("verifyCode").getOrElse("")) {
            (for {
              userOpt <- mongo.find[User](obj("login" -> login)).first
            } yield {
              userOpt match {
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
                  }else{
                    Future.successful(Redirect(routes.Application.message("注册出错了", "您两次输入的密码不一致！")))
                  }
                case None =>
                  Future.successful(Redirect(routes.Application.message("重置密码出错了", "未注册的邮箱！")))
              }
            }).flatMap(f1 => f1)
          } else {
            Future.successful(Redirect(routes.Application.message("操作出错了！", "验证码输入错误！")))
          }
        }
      )}

  def verifyResetPassword(p:String) = Action.async { implicit request: Request[AnyContent] =>{
    val key = config.getOptional[String]("resetPassword.key").getOrElse("https://www.playscala.cn")
    //参数传递过程中,会把+转化成空格，所以替换回来,或者去掉playframework的安全相关的过滤器
    val decrptP = CryptoUtil.decrypt(key,p.replaceAll(" ","+"))
    val params = decrptP.split(",")
    val (sendTime,login,enPassword) =(params(0),params(1),params(2))
    val nowTime =  DateTime.now().getMillis
      if((nowTime - sendTime.toLong) > config.getOptional[Int]("resetPassword.timeout").getOrElse(300000)){
        Future.successful(Redirect(routes.Application.message("系统提示", "操作失败,邮件超过有效期,请重新操作。")))
      }else{
        (for {
            userOpt <- mongo.find[User](obj("login" -> login)).first
          } yield {
            userOpt match {
              case Some(u) =>
                //encode password
                passwordEncoder.updateUserPassword(u._id, enPassword)
                Future.successful(Redirect(routes.Application.message("系统提示", "密码修改成功！")))
              case None =>
                Future.successful(Redirect(routes.Application.message("系统提示", "用户不存在！")))
            }
          }).flatMap(f1 => f1)
      }
    }}



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
          mongo.updateOne[User](obj("_id" -> request.user._id), obj("$unset" -> Json.obj("activeCode" -> 1)))
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
          _ <- mongo.insertOne[User](User(uid.toString, Role.USER, RequestHelper.getLogin, "", UserSetting(RequestHelper.getName, "", "", RequestHelper.getHeadImg, ""), UserStat.DEFAULT, 0, true, request.session.get("from").getOrElse(""), request.remoteAddress, None, Nil, None))
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
    val verifyCode = VerifyCodeUtils.generateVerifyCode(4)
    val out: ByteArrayOutputStream = new ByteArrayOutputStream
    VerifyCodeUtils.outputImage(200, 80, out, verifyCode)
    Ok(ByteString(out.toByteArray))
      .as("image/jpeg")
      .addingToSession("verifyCode" -> HashUtil.sha256(verifyCode.toLowerCase()))
  }

}
