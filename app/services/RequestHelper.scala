package services

import javax.inject.Inject

import models.Author
import play.api.mvc.RequestHeader
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.ExecutionContext

/**
  * Created by joymufeng on 2017/7/17.
  */
class RequestHelper @Inject()()(implicit ec: ExecutionContext) {

  def isLogin(implicit request: RequestHeader) : Boolean = {
    request.session.get("login").nonEmpty
  }

  def getUidOpt(implicit request: RequestHeader) : Option[String] = {
    request.session.get("uid")
  }

  def getLoginOpt(implicit request: RequestHeader) : Option[String] = {
    request.session.get("login")
  }

  def getNameOpt(implicit request: RequestHeader) : Option[String] = {
    request.session.get("name")
  }

  def getHeadImgOpt(implicit request: RequestHeader) : Option[String] = {
    request.session.get("headImg")
  }

  def getAuthorOpt(implicit request: RequestHeader) : Option[Author] = {
    request.session.get("uid").map{ uid =>
      Author(uid, getLoginOpt.get, getNameOpt.get, getHeadImgOpt.get)
    }
  }
}
