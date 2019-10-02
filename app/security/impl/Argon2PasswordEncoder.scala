package security.impl

import java.nio.charset.Charset
import java.security.SecureRandom

import de.mkammerer.argon2.{ Argon2Advanced, Argon2Factory }
import security.PasswordEncoder

/**
 * 基于Argon2的加密
 *
 * @author 梦境迷离
 * @since 2019-10-02
 * @version v1.0
 */
class Argon2PasswordEncoder extends PasswordEncoder {

  private final lazy val ARGON2: Argon2Advanced = Argon2Factory.createAdvanced(Argon2Factory.Argon2Types.ARGON2i)

  private val ITERATIONS = 2
  private val MEMORY = 65536
  private val PARALLELISM = 1

  /**
   * hash returns already the encoded String
   *
   * @param rawPassword 待检验原始密码
   * @param salt        用户的盐
   * @return hash密码
   */
  def hash(rawPassword: CharSequence, salt: Array[Byte]) = {
    ARGON2.hash(ITERATIONS, MEMORY, PARALLELISM, rawPassword.toString.toCharArray, Charset.forName("UTF-8"), salt)
  }

  /**
   * match with password and encodedPassword
   *
   * @param rawPassword     待检验原始密码
   * @param encodedPassword hash密码
   * @return
   */
  def matches(rawPassword: CharSequence, encodedPassword: String, salt: Array[Byte]) =
    hash(rawPassword, salt) == encodedPassword
}

object Argon2PasswordEncoder {

  def createSalt = {
    //default 16
    val salt = new Array[Byte](16)
    val r = new SecureRandom
    r.nextBytes(salt)
    salt
  }
}