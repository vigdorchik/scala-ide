package scala.tools.eclipse.codeanalysis

import org.eclipse.core.resources.IProject
import org.eclipse.core.runtime.{Platform, FileLocator}

import scala.tools.eclipse.buildmanager.CompilerSettingsExtension
import scala.tools.nsc.Settings

class CompilerPluginSettings extends CompilerSettingsExtension {

  def modifySettings(project: IProject, settings: Settings) {

    if(CodeAnalysisPreferences.isGenerallyEnabledForProject(project)) {
      val plugins = List("org.scala-refactoring.library", "org.scala-ide.sdt.codeanalysis")
      val bundles = plugins map Platform.getBundle map Option.apply flatten
      val jars    = bundles map (FileLocator.getBundleFile(_).getAbsolutePath) filter (_.endsWith("jar"))
      jars foreach settings.plugin.appendToValue
    }
  }
}
