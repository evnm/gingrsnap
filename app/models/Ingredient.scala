package models

import java.util.Date
import play.db.anorm._
import play.db.anorm._
import play.db.anorm.defaults._
import play.db.anorm.SqlParser._

case class Ingredient(
  id: Pk[Long],
  name: String,
  createdAt: Date
)

object Ingredient extends Magic[Ingredient]
