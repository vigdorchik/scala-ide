package scala.tools.eclipse.codeanalysis

import org.eclipse.core.resources.IProject
import scala.tools.eclipse.logging.HasLogger
import scala.tools.nsc.reporters.Reporter
import scala.tools.nsc.util.Position
import scala.tools.eclipse.buildmanager.BuildReportingExtension

class CodeAnalysisProblemsReporter extends BuildReportingExtension {

  import CodeAnalysisPreferences.isGenerallyEnabledForProject
  import CodeAnalysisExtensionPoint.handleMessage

  def handle(project: IProject, pos: Position, msg: String, severity: Reporter#Severity) = {
    isGenerallyEnabledForProject(project) && handleMessage(pos, msg)
  }
}
