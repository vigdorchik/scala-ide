package scala.tools.eclipse
package codeanalysis

import org.eclipse.core.resources.{IncrementalProjectBuilder, IResource, IMarker}
import org.eclipse.core.runtime.{Platform, NullProgressMonitor, FileLocator}
import org.junit.{Assert, Test, Before}

import scala.tools.eclipse.testsetup.{TestProjectSetup, SDTTestUtils}
import scala.tools.eclipse.ScalaPlugin

object CodeAnalysisTest extends TestProjectSetup(projectName = "codeanalysis", containingBundleName = "org.scala-ide.sdt.codeanalysis.tests")

class CodeAnalysisTest {

  import CodeAnalysisTest._

  @Before
  def setupWorkspace {
    SDTTestUtils.enableAutoBuild(false)

    ScalaPlugin.plugin.getPreferenceStore.setValue(CodeAnalysisPreferences.generallyEnabledKey, true)

    List("org.scala-ide.sdt.codeanalysis.println", "org.scala-ide.sdt.codeanalysis.classfilenamemismatch") foreach { key =>
      ScalaPlugin.plugin.getPreferenceStore.setValue(CodeAnalysisPreferences.enabledKey(key), true)
      setAnalyzerSeverity(key, 1)
    }
  }

  def buildWithCodeAnalysis() {
    project.clean(new NullProgressMonitor())
    project.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)
  }

  @Test def detectsPrintln() {
    // we can only use the codeanalysis plug-ins when the refactoring and codeanalysis
    // plugins are available as jars. That's not the case when we run the tests in the
    // IDE (without additional setup), so we simply skip the tests.
    runWhenPluginsAreJars {
      buildWithCodeAnalysis()
      assertMarkers("test/foo/ClassA.scala", "println called:1")
    }
  }

  @Test def fileClassNameMismatch() {
    runWhenPluginsAreJars {
      buildWithCodeAnalysis()
      assertMarkers("test/foo/ClassB.scala", "Class- and filename mismatch:1")
    }
  }

  @Test def canDisableAnalyzers() {
    runWhenPluginsAreJars {
      ScalaPlugin.plugin.getPreferenceStore.setValue(CodeAnalysisPreferences.enabledKey("org.scala-ide.sdt.codeanalysis.println"), false)
      buildWithCodeAnalysis()
      assertMarkers("test/foo/ClassC.scala", "Class- and filename mismatch:1")
    }
  }

  @Test def canChangePriorities() {
    runWhenPluginsAreJars {
      setAnalyzerSeverity("org.scala-ide.sdt.codeanalysis.println", 0)
      setAnalyzerSeverity("org.scala-ide.sdt.codeanalysis.classfilenamemismatch", 3)
      buildWithCodeAnalysis()
      assertMarkers("test/foo/ClassC.scala", "Class- and filename mismatch:3, println called:0")
    }
  }

  private def runWhenPluginsAreJars(t: => Unit) {
    val plugins = List("org.scala-refactoring.library", "org.scala-ide.sdt.codeanalysis")
    val pluginsAreJars = plugins map Platform.getBundle forall { bundle =>
      val bundlePath = FileLocator.getBundleFile(bundle).getAbsolutePath
      bundle != null && bundlePath.endsWith("jar")
    }
    if (pluginsAreJars) {
      t
    }
  }

  private def assertMarkers(file: String, expected: String) {
    val msgs = getMarkerMessagesFromFile(file)
    Assert.assertEquals(expected, msgs mkString ", ")
  }

  private def getMarkerMessagesFromFile(file: String) = {
    val units = compilationUnits(file)
    val resource = units(0).getUnderlyingResource
    val markers = resource.findMarkers(CodeAnalysisExtensionPoint.MARKER_TYPE, true, IResource.DEPTH_INFINITE)
    markers.toList map { m =>
      m.getAttribute(IMarker.MESSAGE).toString + ":" + m.getAttribute(IMarker.SEVERITY).toString
    } sorted
  }

  private def setAnalyzerSeverity(id: String, severity: Int) {
    ScalaPlugin.plugin.getPreferenceStore.setValue(CodeAnalysisPreferences.severityKey(id), severity)
  }
}
