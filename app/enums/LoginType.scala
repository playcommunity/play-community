package enums

/**
 * 第三方登录类型
 *
 * @author 梦境迷离
 * @version 1.0, 2019-06-29
 */
object LoginType extends Enumeration {

  type LoginType = Value

  val GITHUB = Value(0, "github")
  val QQ = Value(1, "qq")
  val WECHAT = Value(2, "wechat")


  /**
   * 找到返回
   * 找不到默认github
   *
   * @param loginType
   * @return
   */
  def exists(loginType: String) = {
    if (LoginType.values.contains(withName(loginType))) true
    else false
  }


  implicit def enumToString = (loginType: LoginType.Value) => loginType.toString
}
