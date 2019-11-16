import java.time.Instant

import cn.playscala.mongo.annotations.Entity
import cn.playscala.mongo.codecs.macrocodecs.JsonFormat

package object models {

  @JsonFormat("models")
  implicit val formats = ???

}


