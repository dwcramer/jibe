package org.scalawag.jibe.mandate

import scala.concurrent.duration._
import org.scalawag.jibe.mandate.command.CommandArgument
import org.scalawag.jibe.multitree._

@CommandArgument
case class Service(name: String,
                   version: Option[String] = None)

object Service {
  implicit def fromString(name: String) = Service(name)
  def apply(name: String, version: String): Service = new Service(name, Some(version))
}

object StartService {
  case class StartService(service: Service) extends StatelessMandate with MandateHelpers {
    override def isActionCompleted(implicit context: MandateExecutionContext) = {
      runCommand(command.IsServiceRunning(service))
    }

    override def takeAction(implicit context: MandateExecutionContext) = {
      runCommand(command.StartService(service))
    }
  }

  def apply(service: Service) = MultiTreeLeaf(
    mandate = new StartService(service),
    name = Some(s"start service: ${service.name}" ),
    decorations = Set[MultiTreeDecoration](Consequences(PackageResource(service.name)))
  )
}


object StopService {
  case class StopService(service: Service) extends StatelessMandate with MandateHelpers {
    override def isActionCompleted(implicit context: MandateExecutionContext) = {
      ! runCommand(command.IsServiceRunning(service))
    }

    override def takeAction(implicit context: MandateExecutionContext) = {
      runCommand(command.StartService(service))
    }
  }

  def apply(service: Service) = MultiTreeLeaf(
    mandate = new StopService(service),
    name = Some(s"stop service: ${service.name}" ),
    decorations = Set[MultiTreeDecoration](Consequences(PackageResource(service.name)))
  )
}