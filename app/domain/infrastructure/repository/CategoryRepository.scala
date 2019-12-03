package domain.infrastructure.repository

import models.Category

import scala.concurrent.Future

trait CategoryRepository {

  def findChildren(parentPath: String): Future[List[Category]]

}
