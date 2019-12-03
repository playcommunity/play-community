package security.impl

import java.nio.charset.Charset
import java.security.SecureRandom
import java.util.Base64

import cn.playscala.mongo.Mongo
import de.mkammerer.argon2.{Argon2Advanced, Argon2Factory}
import domain.infrastructure.repository.mongo.MongoUserRepository
import javax.inject.Inject
import play.api.libs.json.Json
import security.PasswordEncoder
import utils.HashUtil

import scala.concurrent.ExecutionContext.Implicits.global


/**
 * 基于Argon2的加密
 *
 * @author 梦境迷离
 * @since 2019-10-02
 * @version v1.0
 */
class Argon2PasswordEncoder @Inject()(mongo: Mongo, mongoUserRepository: MongoUserRepository) extends PasswordEncoder {

  private final lazy val ARGON2: Argon2Advanced = Argon2Factory.createAdvanced(Argon2Factory.Argon2Types.ARGON2i)

  private val ITERATIONS = 2
  private val MEMORY = 1024
  private val PARALLELISM = 2

  /**
   * hash returns already the encoded String
   *
   * update password/rest password/login/register
   *
   * @param rawPassword 待检验原始密码
   * @param salt        用户的盐
   * @return hash密码
   */
  override def hash(rawPassword: CharSequence, salt: String) = {
    ARGON2.hash(ITERATIONS, MEMORY, PARALLELISM, rawPassword.toString.toCharArray(), UTF8, salt.toString.getBytes("utf-8"))
  }

  override def hash(rawPassword: CharSequence, salt: CharSequence, charset: Charset = UTF8): String = {
    ARGON2.hash(ITERATIONS, MEMORY, PARALLELISM, rawPassword.toString.toCharArray, UTF8, salt.toString.getBytes(charset))
  }


  /**
   * match with password and encodedPassword
   * not use
   *
   * @param rawPassword     待检验原始密码
   * @param encodedPassword hash密码
   * @return
   */
  def matches(rawPassword: CharSequence, encodedPassword: String, salt: String) = hash(rawPassword, salt) == encodedPassword

  override def createSalt: String = {
    //default 16
    val salt = new Array[Byte](16)
    val r = new SecureRandom
    r.nextBytes(salt)

    Base64.getEncoder().encodeToString(salt)
  }

  override def findUserAndUpgrade(login: String, password: String) = {
    mongoUserRepository.findByLogin(login).map {
      //1.已经升级到新校验
      case Some(u) if u.argon2Hash.isDefined && u.salt.isDefined =>
        if (u.argon2Hash.get == hash(password, u.salt.get)) Some(u) else None
      //2.没有升级的。（这种情况表明：用户从未在本版本上线后登陆，修改密码，重置密码，注册），同步更新新校验hash值
      case Some(u) if u.argon2Hash.isEmpty && u.salt.isEmpty && u.password == HashUtil.sha256(password) =>
        val salt = createSalt()
        val passwordHash = hash(password, salt)
        mongoUserRepository.updateUser(u._id, Json.obj("salt" -> salt, "argon2Hash" -> passwordHash))
        Some(u.copy(salt = Some(salt), argon2Hash = Some(passwordHash)))
      case _ => None
    }
  }

  override def updateUserPassword(uid: String, password: String): Unit = {
    val newSalt = createSalt()
    //为了方便，无论是否已经升级，均重新生成盐。
    mongoUserRepository.updateUser(uid, Json.obj("password" -> password, "argon2Hash" -> hash(password, newSalt), "salt" -> newSalt))
  }

}