package domain.infrastructure.repository

import models.{Corporation, Leader}

import scala.concurrent.Future

trait LeaderRepository {

  def findById(id: String): Future[Option[Leader]]

}
