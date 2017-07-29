package utils

import models.{Author, Role}
import play.api.mvc.RequestHeader
import reactivemongo.bson.BSONObjectID

/**
  * Created by joymufeng on 2017/7/17.
  */
object UserHelper {

  def isLogin(implicit request: RequestHeader): Boolean = {
    request.session.get("login").nonEmpty
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
    request.session.get("role").getOrElse("") == Role.ADMIN_USER
  }

  def isOwnerOf(_id: String)(implicit request: RequestHeader): Boolean = {
    getUidOpt.map(uid => _id.startsWith(uid)).getOrElse(false)
  }

  def getHeadImg(implicit request: RequestHeader) : String = {
    request.session.get("headImg").getOrElse("/assets/images/head.png")
  }

  def getAuthorOpt(implicit request: RequestHeader): Option[Author] = {
    request.session.get("uid").map{ uid =>
      Author(uid, getLoginOpt.get, getNameOpt.get, getHeadImg)
    }
  }

  def generateId(implicit request: RequestHeader): String = {
    getUid + "-" + BSONObjectID.generate().stringify
  }

}
