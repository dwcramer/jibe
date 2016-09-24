package org.scalawag.jibe

import java.io.{File, FileFilter}
import java.nio.file.Files

import spray.json._
import FileUtils._
import org.scalawag.jibe.mandate.MandateResults
import org.scalawag.jibe.backend.JsonFormat._
import scala.io.Source
import scala.util.Try
import scala.xml.NodeSeq

object Reporter {
  private val dirFilter = new FileFilter {
    override def accept(f: File) = f.isDirectory
  }

  private def spacers(n: Int): NodeSeq = Seq.fill(n)(<div class="box spacer">&nbsp;</div>)

  // parses the raw log into structured data that's easier to format
  private trait TopLevelElement
  private case class LogLine(tag: String, level: String, timestamp: String, text: String) extends TopLevelElement
  private case class Command(name: LogLine, content: Seq[LogLine] = Seq.empty, output: Seq[LogLine] = Seq.empty, exitCode: Option[LogLine] = None) extends TopLevelElement
  private case class StackTrace(message: Seq[LogLine], location: Seq[LogLine] = Seq.empty) extends TopLevelElement
  private case class ThrownException(traces: Seq[StackTrace]) extends TopLevelElement

  private def parseLogLine(s: String) = {
    val Array(tag, level, timestamp, text) = s.split("\\|", 4)
    LogLine(tag, level, timestamp, text)
  }

  private def parseLog(log: Iterator[String]): Iterator[TopLevelElement] = {
    val lines = log.map(parseLogLine)

    def helper(current: Option[TopLevelElement]): Stream[TopLevelElement] = {
      if (lines.hasNext) {
        val line = lines.next()

        line.tag match {

          // What to do when it's a plain log line, flush anything currently buffered and set up the line

          case "" =>

            current match {
              case Some(x) =>
                x #:: helper(Some(line))
              case None =>
                helper(Some(line))
            }

          // What to do when it's an exception stack trace line

          case "EE" =>

            current match {
              case None =>
                // Start a new StackTrace
                helper(Some(StackTrace(Seq(line),Seq.empty)))

              case Some(st: StackTrace) =>
                // Append to the existing StackTrace - determine what kind of line it is based on the prefix
                if ( line.text.startsWith("\tat ") || line.text.startsWith("\t...") )
                  helper(Some(st.copy(location = st.location :+ line)))
                else {
                  if ( st.location.isEmpty )
                    helper(Some(st.copy(message = st.message :+ line)))
                  else
                    st #:: helper(Some(StackTrace(Seq(line))))
                }

              case Some(x) =>
                // Something's on deck that's not a StackTrace, emit that and start a StackTrace
                x #:: helper(Some(StackTrace(Seq(line))))
            }

          // What to do when it's anything else (which means it's part of a Command structure, for now)...

          case "CS" =>
            current match {
              case Some(x) =>
                x #:: helper(Some(Command(line)))
              case None =>
                helper(Some(Command(line)))
            }

          case "CC" =>

            current match {
              case Some(c: Command)  =>
                helper(Some(c.copy(content = c.content :+ line)))
            }

          case "CO" =>

            current match {
              case Some(c: Command)  =>
                helper(Some(c.copy(output = c.output :+ line)))
            }

          case "CE" =>

            current match {
              case Some(c: Command)  =>
                c.copy(exitCode = Some(line)) #:: helper(None)
            }

        }
      } else {
        current.toStream
      }
    }

    helper(None).iterator
  }

  private def mandate(dir: File, depth: Int, rowId: String, description: Option[String] = None, icon: Option[String] = None): NodeSeq = {
    val mr = Source.fromFile(dir / "mandate.js").mkString.parseJson.convertTo[MandateResults]

    val (outcomeClass, outcomeIcon) = mr.outcome match {
      case MandateResults.Outcome.SUCCESS => ("success", "fa fa-check")
      case MandateResults.Outcome.FAILURE => ("failure", "fa fa-exclamation")
      case MandateResults.Outcome.USELESS => ("skipped", "fa fa-times")
    }

    val logFile = dir / "log"
    val logNodes =
      if ( logFile.exists ) {
        <div class="row mono">
          {spacers(depth + 1)}
          <div class="log">{
            var nextShutterId = -1
            def allocateShutterId: String = {
              nextShutterId += 1
              s"${rowId}_$nextShutterId"
            }

            parseLog(Source.fromFile(logFile).getLines) map {
              case line: LogLine =>
                import line._
                <div class={level + " line " + tag} title={timestamp}>{text}</div>

              case cmd: Command =>
                val sid = allocateShutterId

                <div class="command">
                  <div class="collapser" shutter-control={sid} shutter-indicator={sid}><span class="fa fa-caret-right"></span></div>
                  <div class="collapser-insert">
                    <div class="section start">
                      <div class="line" shutter-control={sid}>Command: {cmd.name.text}</div> <!-- TODO: timestamp -->
                    </div>
                    <div class="section content" shutter={sid} shuttered="true">
                      {
                        cmd.content map { cc =>
                          import cc._
                          <div class="line" title={timestamp}>{text}</div>
                        }
                      }
                    </div>
                    <div class="section output">
                      {
                        cmd.output map { co =>
                          import co._
                          <div class={"line " + level} title={timestamp}>{text}</div>
                        }
                      }
                    </div>
                    <div class="section exit">
                      {
                        cmd.exitCode.toSeq map { ec =>
                          <div class="line" shutter-control={sid}>Exit Code = {ec.text}</div> <!-- TODO: timestamp -->
                        }
                      }
                    </div>
                  </div>
                </div>

              case st: StackTrace =>
                val sid = allocateShutterId

                <div class="stack-trace">
                  <div class="collapser" shutter-control={sid} shutter-indicator={sid}><span class="fa fa-caret-right"></span></div>
                  <div class="collapser-insert">
                    <div class="message" shutter-control={sid}>
                      {
                        st.message map { line =>
                          import line._
                          <div class={"line message " + level} title={timestamp}>{text}</div>
                        }
                      }
                    </div>
                    <div class="location" shutter={sid} shuttered="true">
                      {
                        st.location map { line =>
                          import line._
                          <div class={"line location " + level} title={timestamp}>{text}</div>
                        }
                      }
                    </div>
                  </div>
                </div>

            }
          }</div>
        </div>
      } else {
        NodeSeq.Empty
      }

    val innards =
      if ( mr.composite ) {
        dir.listFiles(dirFilter).zipWithIndex.flatMap { case (m, n) =>
          mandate(m, depth + 1, s"${rowId}_${n}")
        }.toSeq
      } else {
        NodeSeq.Empty
      }

    val iconClass = icon.map( i => s"fa $i" ).getOrElse(outcomeIcon)

    <div class={s"mandate $outcomeClass"}>
      <div class="row summary">
        {spacers(depth)}
        <div class="box collapser" shutter-control={rowId} shutter-indicator={rowId}><i class="fa fa-caret-right"></i></div>
        <div class="box icon" shutter-control={rowId}><i class={iconClass}></i></div>
<!--      <div class="box outcome"><div style="height: 1em; width: 20em; background: linear-gradient(to right, green 60%, yellow 60%);"></div></div> -->
        <div class="box time" shutter-control={rowId}>{mr.endTime - mr.startTime} ms</div>
        <div class="box description" shutter-control={rowId}>{description.getOrElse(mr.description.getOrElse(""))}&nbsp;</div>
      </div>
      <div shutter={rowId} shuttered="true">
        {logNodes}
        {innards}
      </div>
    </div>
  }

  private def targets(dir: File): NodeSeq =
    dir.listFiles(dirFilter).zipWithIndex flatMap { case (d, n)  =>
      mandate(d, 0, s"r${n}_0", Some(d.getName), Some("fa-dot-circle-o"))
    } toSeq

  def generate(resultsDir: File): Unit = {

    FileUtils.writeFileWithPrintWriter(resultsDir / "html" / "index.html") { pw =>
      pw.println(
        <html>
          <head>
            <meta charset="utf-8" />
            <link rel="stylesheet" href="http://cdnjs.cloudflare.com/ajax/libs/font-awesome/4.6.3/css/font-awesome.min.css"/>
            <link rel="stylesheet" type="text/css" href="../../../style.css"/>
            <script src="http://ajax.googleapis.com/ajax/libs/jquery/1.8.1/jquery.min.js"></script>
            <script type="text/javascript" src="../../../code.js"></script>
          </head>
          <body>
            <div class="row">
              <div class="box actions">
                <i class="fa fa-angle-double-down" onclick="shutterOpenAll()" title="Expand All"></i>
              </div>
              <div class="box actions">
                <i class="fa fa-angle-down" onclick="shutterOpenMandates()" title="Expand Mandates"></i>
              </div>
              <div class="box actions">
                <i class="fa fa-angle-double-up" onclick="shutterCloseAll()" title="Collapse All"></i>
              </div>
              <div class="box actions">
                <i class="fa fa-exclamation toggle-on" outcome="failure" onclick="toggleHide(this)" title="Hide Failed"></i>
              </div>
              <div class="box actions">
                <i class="fa fa-times toggle-on" outcome="skipped" onclick="toggleHide(this)" title="Hide Skipped"></i>
              </div>
              <div class="box actions">
                <i class="fa fa-check toggle-on" outcome="success" onclick="toggleHide(this)" title="Hide Successful"></i>
              </div>
              <div class="box description">{ resultsDir.getName }</div>
            </div>

            { targets(resultsDir / "raw") }
          </body>
        </html>
      )
    }

    try {
      val symlinkPath = ( resultsDir.getParentFile / "latest" ).toPath
      Files.deleteIfExists(symlinkPath)
      Files.createSymbolicLink(symlinkPath, symlinkPath.getParent.relativize( resultsDir.toPath ))
    } catch {
      case uoe: UnsupportedOperationException => println("Your OS sucks. Got symlinks?")
      case unknown: Exception => println("Failed to Create symlink: " + unknown)
    }

  }
}
