package models

import cn.playscala.mongo.annotations.Entity

@Entity("common-category")
case class Category(_id: String, name: String, path: String, parentPath: String, index: Int, disabled: Boolean)
