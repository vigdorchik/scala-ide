package scala.tools.eclipse
package sbt


import java.io._
import java.util.regex.Pattern
import org.eclipse.core.resources._
import org.eclipse.core.runtime.Path
import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.swt.widgets.Display
import org.eclipse.ui.console._
import org.eclipse.ui.ide.IDE
import org.eclipse.ui.PlatformUI
import scala.concurrent.ops
import scala.sys.process._
import scala.tools.eclipse.util.FileUtils
import scala.tools.eclipse.{ScalaPlugin, ScalaProject}
import scala.util.matching.Regex._
import scala.util.matching.Regex

/** Hold together the SBT process, the associated project and the
 *  corresponding Console instance
 */
class SbtBuilder(project: ScalaProject) {
  def console: SbtConsole = getConsole()
  
  @volatile private var shuttingDown = false
  
  private var sbtProcess: Process = _
  
  private val consoleName = "Sbt - %s".format(project.underlying.getName)
  
  private val consoleManager = ConsolePlugin.getDefault().getConsoleManager()
  
  /** Return the corresponding Sbt console for this builder.
   *  If the current Console manager has a console registered with this name,
   *  return it. Otherwise create and add a new console to the Console manager.
   */
  def getConsole(): SbtConsole =  {
    consoleManager.getConsoles().find(_.getName == consoleName) match {
      case Some(c: SbtConsole) => c
      case None    => createConsole()
    }
  }
  
  /** Create a new SbtConsole. Install the pattern matcher that adds hyperlinks
   *  for error messages.
   */
  private def createConsole(): SbtConsole = {
    val console = new SbtConsole(consoleName, null)
    console.setConsoleWidth(140)
    
    val sourceLocationPattern = """^\[error\]\w*(.*):(\d+):(.*)$"""

    console.addPatternMatchListener(PatMatchListener(sourceLocationPattern, (text, offset) =>
      sourceLocationPattern.r.findFirstMatchIn(text) match {
        case Some(m @ Groups(path, lineNr, msg)) =>
          println("error found at %s:%d:%s".format(path, lineNr.toInt, msg))
          for (file <- ResourcesPlugin.getWorkspace.getRoot().findFilesForLocation(new Path(path.trim))) {
            println("added hyperlink for %s".format(file))
            console.addHyperlink(ErrorHyperlink(file, lineNr.toInt, msg), offset + m.start(1), path.length)
          }
            
        case _ => println("something went wrong")
      }))
      
    
    consoleManager.addConsoles(Array[IConsole](console))
    console
  }

  var consoleOutputStream: OutputStream = _
  
  /** Launch the sbt process and route input and output through
   *  the Console object. Escape sequences are stripped form the output
   *  of Sbt.
   */
  def launchSbt() {
    val pio = new ProcessIO(in => BasicIO.transferFully(console.getInputStream, in),
                            os => BasicIO.transferFully(os, consoleOutputStream),
                            es => BasicIO.transferFully(es, consoleOutputStream))
    
    try {
      import util.ScalaPluginSettings._
      loadSbtSettings()

      val wkDir = project.underlying.getLocation()
      consoleOutputStream = console.newOutputStream()
      
      val javaCmd = "java" :: sbtJavaArgs.value.split(' ').map(_.trim).toList ::: List("-jar", "-Dsbt.log.noformat=true", pathToSbt.value)
      println("starting sbt in %s (%s)".format(wkDir.toString, javaCmd))
      shuttingDown = false
      val builder = Process(javaCmd.toArray, Some(project.underlying.getLocation().toFile))
      sbtProcess = builder.run(pio)  
      
      ops.spawn {
        val exitCode = sbtProcess.exitValue()
        // wait until the process terminates, and close this console
        println("Sbt finished with exit code: %d".format(exitCode))
        if (exitCode != 0 && !shuttingDown) Display.getDefault asyncExec new Runnable {
          def run() {
            MessageDialog.openInformation(ScalaPlugin.getShell, "Sbt launch error", """Could not start Sbt.

Please check the path to sbt-launch.jar (currently %s)""".format(pathToSbt.value))
            }
          }
        dispose()
      }
    } catch {
      case e => 
        ScalaPlugin.plugin.logError("Error launching sbt", e)
    }
  }
  
  private def loadSbtSettings() {
    import util.ScalaPluginSettings._

    val store = ScalaPlugin.plugin.getPreferenceStore
    pathToSbt.value = store.getString(SettingConverterUtil.convertNameToProperty(pathToSbt.name))
    sbtJavaArgs.value = store.getString(SettingConverterUtil.convertNameToProperty(sbtJavaArgs.name))
  }
  
  def visible: Boolean =
    sbtProcess ne null
  
  def showConsole() {
    if (sbtProcess == null) launchSbt()
    consoleManager.showConsoleView(console)
  }
  
  def dispose() {
    if (sbtProcess ne null) {
      shuttingDown = true
      sbtProcess.destroy
      sbtProcess = null
      console.dispose();
      consoleManager.removeConsoles(Array(console))
    }
  }
}

/** A Pattern match listener that calls `fun' when the given regular expression matches. */
case class PatMatchListener(regex: String, fun: (String, Int) => Unit) extends IPatternMatchListener with IPatternMatchListenerDelegate {
  var console: Option[TextConsole] = None
  
  def getLineQualifier() = null
  
  def getCompilerFlags() = Pattern.MULTILINE
  
  def getPattern() = regex
  
  def connect(console: TextConsole) {
    this.console = Some(console)
  }
  
  def disconnect() {
    console = None
  }
  
  def matchFound(event: PatternMatchEvent) {
    console match {
      case Some(c) => fun(c.getDocument().get(event.getOffset(), event.getLength()), event.getOffset)
      case None => println("invalid match, no text console found.")
    }
  }
}

/** A hyperlink for errors in sbt output. When clicked, it opens the corresponding 
 *  editor and selects the affected line.
 *  
 *  Error markers are not persisted (the hyperlink deletes it after the editor is open
 */
case class ErrorHyperlink(file: IFile, lineNr: Int, msg: String) extends IHyperlink {
  def linkActivated() {
    val marker = FileUtils.createMarker(file, IMarker.SEVERITY_ERROR, msg, lineNr)
    try {
      val page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
      IDE.openEditor(page, marker, true) 
    } catch {
      case e: Exception => 
        println("exception while opening editor")
    } finally marker.delete()
  }
  
  def linkExited() {}
  def linkEntered() {}
}

