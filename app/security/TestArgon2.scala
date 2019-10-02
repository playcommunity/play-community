package security

import security.impl.Argon2PasswordEncoder

/**
 *
 * @author 梦境迷离
 * @since 2019-10-02
 * @version v1.0
 */
object TestArgon2 extends App {

  def getArgon2 = new Argon2PasswordEncoder

  val rawMessage = "123456"
  //  val salt = Argon2PasswordEncoder.createSalt
  val salt = "127-1238027-1057152-11945-1512-1096727348".getBytes
  val saltHash = getArgon2.hash(rawMessage, salt)
  Console print "salt: "
  salt.foreach(print)
  println

  Console print "with salt: " + saltHash
  Console println ", result: " + getArgon2.matches(rawMessage, saltHash, salt)

}
