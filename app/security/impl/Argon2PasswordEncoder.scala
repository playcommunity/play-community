package security.impl

import java.nio.charset.Charset
import java.security.SecureRandom

import cn.playscala.mongo.Mongo
import de.mkammerer.argon2.{ Argon2Advanced, Argon2Factory }
import javax.inject.Inject
import models.User
import play.api.libs.json.Json
import play.api.libs.json.Json.obj
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
class Argon2PasswordEncoder @Inject()(mongo: Mongo) extends PasswordEncoder {

  private final lazy val ARGON2: Argon2Advanced = Argon2Factory.createAdvanced(Argon2Factory.Argon2Types.ARGON2i)

  private val ITERATIONS = 2
  private val MEMORY = 65536
  private val PARALLELISM = 1

  /**
   * hash returns already the encoded String
   *
   * update password/rest password/login/register
   *
   * @param rawPassword 待检验原始密码
   * @param salt        用户的盐
   * @return hash密码
   */
  override def hash(rawPassword: CharSequence, salt: Array[Byte]) = {
    ARGON2.hash(ITERATIONS, MEMORY, PARALLELISM, rawPassword.toString.toCharArray, UTF8, salt)
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
  def matches(rawPassword: CharSequence, encodedPassword: String, salt: Array[Byte]) = hash(rawPassword, salt) == encodedPassword

  override def createSalt = {
    //default 16
    val salt = new Array[Byte](16)
    val r = new SecureRandom
    r.nextBytes(salt)
    salt
  }

  override def findUserAndUpgrade(login: String, password: String) = {
    mongo.find[User](obj("login" -> login)).first.map {
      //1.已经升级到新校验
      case Some(u) if u.argon2Hash.isDefined && u.salt.isDefined =>
        if (u.argon2Hash.get == hash(password, u.salt.get.getBytes)) Some(u) else None
      //2.没有升级的。（这种情况表明：用户从未在本版本上线后登陆，修改密码，重置密码，注册），同步更新新校验hash值
      case Some(u) if u.argon2Hash.isEmpty && u.salt.isEmpty && u.password == HashUtil.sha256(password) =>
        val salt = createSalt()
        val passwordHash = hash(u.password, salt)
        u.copy(salt = Option(new String(salt, UTF8)), argon2Hash = Option(passwordHash))
        mongo.updateOne[User](
          Json.obj("_id" -> u._id),
          Json.obj(
            //这里new String得到的是byte转字符后的乱码，存储后刚好不可见，如：�i$|\u0013\u0012\u0011^�Y��\u001d��
            //为了保持平台一致，显示指定可能更好，已改为UTF-8
            "$set" -> Json.obj("salt" -> new String(salt, UTF8), "argon2Hash" -> passwordHash)
          ))
        Some(u)
      case _ => None
    }
  }

  override def updateUserPassword(_id: String, password: String): Unit = {
    val newSalt = createSalt()
    //为了方便，无论是否已经升级，均重新生成盐。
    mongo.updateOne[User](Json.obj("_id" -> _id), Json.obj(
      "$set" -> Json.obj("password" -> password,
        "argon2Hash" -> hash(password, newSalt), "salt" -> new String(newSalt, UTF8))
    ))
  }

}