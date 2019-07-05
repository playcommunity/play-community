import cn.playscala.mongo.codecs.macrocodecs.JsonFormat

package object models {
  @JsonFormat("models")
  implicit val formats = ???

  // 登录类型
  object LoginType extends Enumeration {
    type LoginType = Value

    val PASSWORD  = Value(0, "password") //用户密码登录
    val GITHUB = Value(1, "github")
    val QQ = Value(2, "qq")
    val WECHAT = Value(3, "wechat")

    implicit def enumToString = (loginType: LoginType.Value) => loginType.toString
  }
}


