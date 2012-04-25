package scala.tools.eclipse.codeanalysis.plugin

import java.io.File
import java.net.{URLClassLoader, URL}

import scala.tools.nsc.plugins.{PluginComponent, Plugin}
import scala.tools.nsc.{Phase, Global}

class CodeAnalysisPlugin(val global: Global) extends Plugin {

  val name = "codeanalysis"

  val description = "code analysis"

  val components = List[PluginComponent](component)

  private object component extends PluginComponent {

    val global: CodeAnalysisPlugin.this.global.type = CodeAnalysisPlugin.this.global

    val phaseName = "codeanalysis"

    val runsAfter = List("typer")

    def newPhase(prev: Phase) = new AnalysisPhase(prev)

    class AnalysisPhase(prev: Phase) extends StdPhase(prev) {

      def apply(compilationUnit: global.CompilationUnit) {

        val classLoader = getClassLoader()

        val pluginXml = classLoader.getResource("plugin.xml")

        val xmlRoot = xml.XML.load(pluginXml)
        val (analyzerClassNames, analyzerMessage) = {
          val analyzer = xmlRoot \\ "analyzer"
          (analyzer \\ "@class" map (_.text), analyzer \\ "@msgPattern" map (_.text))
        }

        val analyzers = analyzerClassNames map { name =>
          val clazz = classLoader.loadClass(name)
          clazz.newInstance.asInstanceOf[CodeAnalyzer]
        }

        val cu = new GlobalCompilationUnit {
          val global: CodeAnalysisPlugin.this.global.type = CodeAnalysisPlugin.this.global
          val unit = compilationUnit
        }

        (analyzers zip analyzerMessage) foreach {
          case (analyzer, msgPattern) =>
            analyzer.analyze(cu, msgPattern) foreach {
              case analyzer.Marker(message, pos) =>
                global.reporter.warning(pos, message)
            }
        }
      }

      private def getClassLoader() = {
        val thisClassLoader = getClass.getClassLoader
        val jarHasPluginXml = thisClassLoader.getResource("plugin.xml") != null
        if (jarHasPluginXml) {
          // This means we're in "production" mode where all the required classes
          // and the plugin.xml are bundled with the compiler plug-in jar.
          thisClassLoader
        } else {

          // We're running in development mode, so we need to get the directory where
          // the codeanalysis project is located.
          val baseDirectoryUrl = {
            // The `code-analysis-development-plugin.jar` is in the `target` directory
            val codeSource = getClass.getProtectionDomain.getCodeSource
            new File(codeSource.getLocation.toURI.getPath).getParentFile.getParentFile.toURI.toURL
          }

          // For the `plugin.xml`
          val pluginXmlUrl = new URL(baseDirectoryUrl.toString)
          // For the compiled classes of the codeanalysis project
          val classesUrl   = new URL(baseDirectoryUrl.toString + "target/classes/")

          new URLClassLoader(Array(classesUrl, pluginXmlUrl), thisClassLoader)
        }
      }
    }
  }
}
