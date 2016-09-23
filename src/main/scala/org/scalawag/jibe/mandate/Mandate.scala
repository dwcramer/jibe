package org.scalawag.jibe.mandate

import java.io.File
import org.scalawag.jibe.backend.{Commander, Resource}

import scala.util.{Success, Try}

/** A Mandate is an operation that can be executed against a system.  It may be realized as a series of system-specific
  * commands or it can be an aggregation of several other Mandates.
  *
  * Mandates should be idempotent.  In addition to being a convenience to the user that partial/failed runs can be
  * restarted without negative consequences, jibe also will optimize the run by avoiding running mandates that have
  * already been run (assuming that the outcome would be the same, anyway).
  */

trait Mandate {
  val description: Option[String] = None
  def prerequisites: Iterable[Resource] = Iterable.empty
  def consequences: Iterable[Resource] = Iterable.empty

  // Unit -> action was successfully taken
  // throw -> error
  def takeAction(commander: Commander, resultsDir: File): Unit

  override def toString = description.getOrElse(super.toString)
}

trait CheckableMandate extends Mandate {
  // true -> action is not needed
  // false -> action is needed
  def isActionCompleted(commander: Commander, resultsDir: File): Boolean

  // false -> action was unneeded
  // true -> action was needed and completed
  def takeActionIfNeeded(commander: Commander, resultsDir: File): Boolean =
    if ( isActionCompleted(commander, resultsDir) ) {
      false
    } else {
      takeAction(commander, resultsDir)
      true
    }
}

object Mandate {
  implicit class MandatePimper(mandate: Mandate) {
    def before(after: Mandate) = mandate match {
      // If the mandate we're adding to is already a fixed-order CompositeMandate, just add it.
      case CompositeMandate(desc, innards, true) => new CompositeMandate(desc, innards :+ after, true)
      case m => new CompositeMandate(None, Seq(m, after), true)
    }
  }
}
