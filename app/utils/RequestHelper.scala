package utils

import models.Author
import play.api.mvc.RequestHeader

/**
  * Created by joymufeng on 2017/7/17.
  */
object RequestHelper {

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

  def getName(implicit request: RequestHeader) : String = {
    request.session.get("name").getOrElse("游客")
  }

  def getRole(implicit request: RequestHeader) : String = {
    request.session.get("role").getOrElse("")
  }

  def getHeadImg(implicit request: RequestHeader) : String = {
    request.session.get("headImg").getOrElse("/assets/images/head.png")
  }

  def getAuthorOpt(implicit request: RequestHeader) : Option[Author] = {
    request.session.get("uid").map{ uid =>
      Author(uid, getLoginOpt.get, getNameOpt.get, getHeadImg)
    }
  }
}
