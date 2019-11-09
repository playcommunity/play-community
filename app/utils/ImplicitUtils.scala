package utils

import play.api.libs.json.Json

object ImplicitUtils {

  implicit class ConvertJsValue(any: Any) {
    def toJsValue = any match {
      case a: Int => Json.toJsFieldJsValueWrapper(a)
      case a: String => Json.toJsFieldJsValueWrapper(a)
      case a: Boolean => Json.toJsFieldJsValueWrapper(a)
      case a: Long => Json.toJsFieldJsValueWrapper(a)
      case _ => throw new Exception("unsupport convert to JsValue")
    }
  }

}