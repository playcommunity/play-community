package domain.infrastructure.repository

import models.Resource
import scala.concurrent.Future

trait ResourceRepository {

  def findById(id: String): Future[Option[Resource]]

}
