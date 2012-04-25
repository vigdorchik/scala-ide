package scala.tools.eclipse.codeanalysis

import org.eclipse.core.resources.IProject
import org.eclipse.core.runtime.{Platform, FileLocator}

import scala.Option.option2Iterable
import scala.tools.eclipse.buildmanager.CompilerSettingsExtension
import scala.tools.nsc.Settings

class CompilerPluginSettings extends CompilerSettingsExtension {

  def modifySettings(project: IProject, settings: Settings) {

    val setPlugin = settings.plugin.appendToValue _

    if(CodeAnalysisPreferences.isGenerallyEnabledForProject(project)) {

      getJarLocationForBundle("org.scala-refactoring.library") match {
        case Some(file) if file.endsWith("jar") =>
          setPlugin(file)
        case _ =>
          // XXX print an error?
          return
      }

      getJarLocationForBundle("org.scala-ide.sdt.codeanalysis") match {
        case Some(file) if file.endsWith("jar") =>
          setPlugin(file)
        case Some(dir) =>
          // TODO try to get into development-mode, LOG
          setPlugin(dir +"/target/code-analysis-development-plugin.jar")
        case None =>
          // XXX print an error?
      }
    }
  }

  private def getJarLocationForBundle(bundleName: String): Option[String] = {
    Option(Platform.getBundle(bundleName))
      .map(FileLocator.getBundleFile(_).getAbsolutePath)
  }
}
