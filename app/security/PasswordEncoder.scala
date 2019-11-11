package security

import java.nio.charset.Charset

/**
 *
 * @author 梦境迷离
 * @since 2019-10-02
 * @version v1.0
 */
trait PasswordEncoder extends ExtraUserEncoder {

  final lazy val UTF8 = Charset.forName("UTF-8")

  def hash(rawPassword: CharSequence, salt: String): String

  def hash(rawPassword: CharSequence, salt: CharSequence, charset: Charset = UTF8): String

  def createSalt(): String

}