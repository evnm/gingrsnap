package models

import java.util.Date
import play.db.anorm._
import play.db.anorm._
import play.db.anorm.defaults._
import play.db.anorm.SqlParser._

case class Recipe(
  id: Pk[Long],
  title: String,
  authorId: Long,
  createdAt: Date,
  body: String
)

object Recipe extends Magic[Recipe]
