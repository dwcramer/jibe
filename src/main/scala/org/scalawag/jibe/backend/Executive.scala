package org.scalawag.jibe.backend

import java.io.File

import org.scalawag.jibe.FileUtils._
import org.scalawag.jibe.backend.JsonFormat.ShallowMandateResults
import org.scalawag.jibe.mandate.{CompositeMandate, Mandate, MandateResults}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object Executive {
  def execute(targets: Map[Target, Mandate], reportDir: File) = {
    val futures = targets map { case (t, m) =>
      Future {
        val dir = reportDir / s"${t.username}@${t.hostname}:${t.port}"
        this.apply(m, t, dir)
        writeFileWithPrintWriter(dir / "target.js") { pw =>
          import spray.json._
          import JsonFormat._
          pw.write(PersistentTarget(t).toJson.prettyPrint)
        }
      }
    }

    Await.ready(Future.sequence(futures), Duration.Inf)
  }

  def apply(rootMandate: Mandate, target: Target, reportDir: File) = {

    def execute(mandate: Mandate, reportDir: File): MandateResults = {
      val (results, shallowResults) =
        mandate match {

          case CompositeMandate(desc, innards, _) =>
            val width = math.log10(innards.length).toInt + 1

            val startTime = System.currentTimeMillis

            val results = innards.zipWithIndex map { case (m, n) =>
              val subdir = s"%0${width}d".format(n + 1) + m.description.map(s => "_" + s.replaceAll("\\W+", "_")).getOrElse("")

              val childDir = mkdir(reportDir / subdir)
              execute(m, childDir)
            }

            val endTime = System.currentTimeMillis

            val outcome =
              if (results.exists(_.outcome == MandateResults.Outcome.FAILURE))
                MandateResults.Outcome.FAILURE
              else if (results.forall(_.outcome == MandateResults.Outcome.USELESS))
                MandateResults.Outcome.USELESS
              else
                MandateResults.Outcome.SUCCESS

            (
              MandateResults(mandate, outcome, startTime, endTime, results),
              ShallowMandateResults(mandate.description, true, outcome, startTime, endTime)
            )

          case m =>

            val command = target.commander.getCommand(m)

            val startTime = System.currentTimeMillis

            val testExitCode = command.test(target, reportDir / "test")
            if (testExitCode == 0) {
              val endTime = System.currentTimeMillis

              (
                MandateResults(mandate, MandateResults.Outcome.USELESS, startTime, endTime),
                ShallowMandateResults(mandate.description, false, MandateResults.Outcome.USELESS, startTime, endTime)
              )
            } else {
              val performExitCode = command.perform(target, reportDir / "perform")

              val outcome =
                if (performExitCode == 0)
                  MandateResults.Outcome.SUCCESS
                else
                  MandateResults.Outcome.FAILURE

              val endTime = System.currentTimeMillis

              (
                MandateResults(mandate, outcome, startTime, endTime),
                ShallowMandateResults(mandate.description, false, outcome, startTime, endTime)
              )
            }

        }

      writeFileWithPrintWriter(reportDir / "results.js") { pw =>
        import spray.json._
        import JsonFormat._
        pw.write(shallowResults.toJson.prettyPrint)
      }

      results
    }

    execute(rootMandate, reportDir)
  }
}
