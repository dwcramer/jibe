package org.scalawag.jibe

import org.scalawag.timber.api.Level.DEBUG
import org.scalawag.timber.api.Logger
import org.scalawag.timber.backend.DefaultDispatcher
import org.scalawag.timber.backend.dispatcher.Dispatcher
import org.scalawag.timber.backend.dispatcher.configuration.dsl._
import org.scalawag.timber.backend.receiver.formatter.ProgrammableEntryFormatter
import org.scalawag.timber.backend.receiver.formatter.ProgrammableEntryFormatter.entry
import org.scalawag.timber.backend.receiver.formatter.level.NameLevelFormatter
import org.scalawag.timber.backend.receiver.formatter.timestamp.HumanReadableTimestampFormatter

object Logging {

  implicit private val MyEntryFormatter = new ProgrammableEntryFormatter(Seq(
    entry.timestamp formattedWith HumanReadableTimestampFormatter,
    entry.level formattedWith NameLevelFormatter,
    entry.sourceLocation
  ))

  val config = org.scalawag.timber.backend.dispatcher.configuration.Configuration {
    ( level >= DEBUG ) ~> stdout
  }

  val disp = new Dispatcher(config)
  DefaultDispatcher.set(disp)

  val log = new Logger
}