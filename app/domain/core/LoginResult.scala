package domain.core

import models.User

case class LoginResult(code: Int, message: String, user: Option[User], session: List[(String, String)]) extends Result(code, message)
