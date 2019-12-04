package utils

import models.{Author, LoginType, Role}
import org.bson.types.ObjectId
import play.api.mvc.RequestHeader

/**
  * Created by joymufeng on 2017/7/17.
  */
object RequestHelper {

  def isLogin(implicit request: RequestHeader): Boolean = {
    request.session.get("login").nonEmpty
  }

  def isActive(implicit request: RequestHeader): Boolean = {
    isLogin && request.session.get("active").getOrElse("0") == "1"
  }

  def getUidOpt(implicit request: RequestHeader): Option[String] = {
    request.session.get("uid")
  }

  def getUid(implicit request: RequestHeader): String = {
    request.session.get("uid").getOrElse("")
  }

  def getLoginOpt(implicit request: RequestHeader): Option[String] = {
    request.session.get("login")
  }

  def getLogin(implicit request: RequestHeader): String = {
    request.session("login")
  }

  def getNameOpt(implicit request: RequestHeader): Option[String] = {
    request.session.get("name")
  }

  def getName(implicit request: RequestHeader): String = {
    request.session.get("name").getOrElse("游客")
  }

  def getRole(implicit request: RequestHeader): String = {
    request.session.get("role").getOrElse("")
  }

  def isAdmin(implicit request: RequestHeader): Boolean = {
    request.session.get("role").getOrElse("") == Role.ADMIN
  }

  def isOwnerOf(_id: String)(implicit request: RequestHeader): Boolean = {
    getUidOpt.map(uid => _id.startsWith(uid)).getOrElse(false)
  }

  def getHeadImg(implicit request: RequestHeader) : String = {
    request.session.get("headImg").getOrElse("/assets/images/head.png")
  }

  def getAuthor(implicit request: RequestHeader): Author = {
    Author(getUid, getLoginOpt.get, getNameOpt.get, getHeadImg)
  }

  def getAuthorOpt(implicit request: RequestHeader): Option[Author] = {
    request.session.get("uid").map{ uid =>
      Author(uid, getLoginOpt.get, getNameOpt.get, getHeadImg)
    }
  }

  def getBoards(implicit request: RequestHeader): List[String] = {
    request.session("boards").split(",").toList
  }

  def generateId(implicit request: RequestHeader): String = {
    getUid + "-" + ObjectId.get().toHexString
  }

  def isPasswordLogin(implicit request: RequestHeader): Boolean = {
    request.session.get("loginType").getOrElse("") == LoginType.PASSWORD.toString
  }

}
