package security

/**
 *
 * @author 梦境迷离
 * @since 2019-10-02
 * @version v1.0
 */
trait PasswordEncoder extends ExtraUserEncoder {

  def hash(rawPassword: CharSequence, salt: Array[Byte]): String

  def createSalt(): Array[Byte]

}