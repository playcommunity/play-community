package domain.core

import domain.infrastructure.repository.mongo.{MongoBoardRepository, MongoResourceRepository, MongoUserRepository}
import javax.inject.{Inject, Singleton}
import models.{LoginType, Role, User}
import security.PasswordEncoder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * 认证管理器
 */
@Singleton
class AuthenticateManager @Inject()(passwordEncoder: PasswordEncoder, resourceRepo: MongoResourceRepository, userRepo: MongoUserRepository, boardRepo: MongoBoardRepository) {
  def login(username: String, password: String): Future[LoginResult] = {
    passwordEncoder.findUserAndUpgrade(username, password) flatMap {
      case Some(u) =>
        generateSession(u).map(new LoginResult(0, "success", Some(u), _))
      case None =>
        Future.successful(new LoginResult(1, "username or password error.", None, Nil))
    }
  }

  def generateSession(user: User): Future[List[(String, String)]] = {
    user.getOwnedBoards() map { boards =>
      var roles = List(user.role)
      if(boards.nonEmpty) {
        roles = roles ::: List(Role.BOARDER)
      }
      val session: List[(String, String)] = List(
        "uid" -> user._id,
        "login" -> user.login,
        "name" -> user.setting.name,
        "headImg" -> user.setting.headImg,
        "role" -> user.role, // 保持向前兼容
        "roles" -> roles.mkString(","), // 1.3.0新增属性
        "boards" -> boards.mkString(",")
      )
      user.activeCode match {
        case Some(_) => session ::: List("active" -> "0")
        case None => session ::: List("active" -> "1")
      }
    }
  }
}
