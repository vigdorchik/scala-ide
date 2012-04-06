package scala.tools.eclipse.buildmanager

import scala.tools.nsc.Settings
import org.eclipse.core.resources.IProject
import scala.tools.nsc.util.Position
import scala.tools.nsc.reporters.Reporter

/**
 * An extension that allows contributions to the compiler settings. For example,
 * an extension can add additional compiler plug-ins.
 */
trait CompilerSettingsExtension {

  def modifySettings(project: IProject, settings: Settings): Unit
}

/**
 * An extension that makes it possible to intercept messages reported by the
 * compiler. This is useful to handle messages emitted by a  compiler plug-in.
 */
trait BuildReportingExtension {

  /**
   * Gives the extension a chance to handle the given message, for example by
   * converting it to a specific marker type.
   *
   * @return Returns true if the messages was handled and should not be further
   * processed by the build manager. When false is returned, the message will
   * be reporting by the original build reporter.
   */
  def handle(project: IProject, pos: Position, msg: String, severity: Reporter#Severity): Boolean
}
