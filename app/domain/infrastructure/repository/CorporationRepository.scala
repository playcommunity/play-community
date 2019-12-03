package domain.infrastructure.repository

import models.Corporation

import scala.concurrent.Future

trait CorporationRepository {

  def findById(id: String): Future[Option[Corporation]]

}
