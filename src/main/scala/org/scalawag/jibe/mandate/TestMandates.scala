package org.scalawag.jibe.mandate

case object NoisyMandate extends CheckableMandate {
  override val description = Some("Make a lot of noise.")

  override def isActionCompleted(implicit context: MandateExecutionContext) = {
    import context._

    log.debug("This is a debug message...")
    log.info("followed by an info message.")
    log.warn("Then, there's a warning.")
    log.error("Finally, an ERROR!!!!!")

    false
  }

  override def takeAction(implicit context: MandateExecutionContext) = {
    try {
      throw new RuntimeException("BOOM")
    } catch {
      case ex: Exception =>
        throw new RuntimeException("message\nis\nlong", ex)
    }
  }
}

case class ExitWithArgument(exitCode: Int) extends CheckableMandate {
  override val description = Some(s"exit with $exitCode")

  override def isActionCompleted(implicit context: MandateExecutionContext) = {
    import context._
    val ec = runCommand("isActionCompleted", command.ExitWithArgument(exitCode))
    log.info(s"command exited with exit code: $ec")
    false
  }

  override def takeAction(implicit context: MandateExecutionContext) = {
    import context._
    val ec = runCommand("isActionCompleted", command.ExitWithArgument(-exitCode))
    log.warn(s"command exited with exit code: $ec")
  }
}