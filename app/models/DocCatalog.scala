package models

import java.time.Instant
import cn.playscala.mongo.annotations.Entity
import play.api.libs.json.JsArray

// 已整理文档目录
@Entity("doc-catalog")
case class DocCatalog(
  _id: String,
  nodes: JsArray,
  isDefault: Boolean,
  createTime: Instant,
  updateTime: Instant
)