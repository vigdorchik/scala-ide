/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import org.eclipse.ui.IPartService
import org.eclipse.core.runtime.jobs.Job
import scala.tools.eclipse.util.JobUtils
import org.eclipse.jdt.core.IJavaProject
import scala.collection.mutable.HashMap
import scala.util.control.ControlThrowable
import org.eclipse.core.resources.{ IFile, IProject, IResourceChangeEvent, IResourceChangeListener, ResourcesPlugin }
import org.eclipse.core.runtime.{ CoreException, FileLocator, IStatus, Platform, Status }
import org.eclipse.core.runtime.content.IContentTypeSettings
import org.eclipse.jdt.core.{ ElementChangedEvent, IElementChangedListener, JavaCore, IJavaElement, IJavaElementDelta, IPackageFragmentRoot }
import org.eclipse.jdt.internal.core.{ JavaModel, JavaProject, PackageFragment, PackageFragmentRoot }
import org.eclipse.jdt.internal.core.util.Util
import org.eclipse.jdt.internal.ui.javaeditor.IClassFileEditorInput
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.swt.widgets.Shell
import org.eclipse.swt.graphics.Color
import org.eclipse.ui.{ IEditorInput, IFileEditorInput, PlatformUI, IPartListener, IWorkbenchPart, IWorkbenchPage, IPageListener, IEditorPart }
import org.eclipse.ui.part.FileEditorInput
import org.eclipse.ui.plugin.AbstractUIPlugin
import util.SWTUtils.asyncExec
import org.osgi.framework.BundleContext
import scala.tools.eclipse.javaelements.{ ScalaElement, ScalaSourceFile }
import scala.tools.eclipse.util.OSGiUtils.pathInBundle
import scala.tools.eclipse.templates.ScalaTemplateManager
import scala.tools.eclipse.util.Tracer
import scala.tools.eclipse.util.Defensive
import scala.tools.eclipse.markoccurrences.UpdateOccurrenceAnnotationsService
import org.eclipse.core.runtime.NullProgressMonitor
import org.osgi.util.tracker.ServiceTracker

object ScalaPlugin {
  var plugin: ScalaPlugin = _
  
  def getWorkbenchWindow = {
    val workbench = PlatformUI.getWorkbench
    Option(workbench.getActiveWorkbenchWindow) orElse workbench.getWorkbenchWindows.headOption
  }
  
  def getShell: Shell = getWorkbenchWindow map (_.getShell) orNull
}

class ScalaPlugin extends AbstractUIPlugin with IResourceChangeListener with IElementChangedListener with IPartListener {
  ScalaPlugin.plugin = this

  final val HEADLESS_TEST  = "sdtcore.headless"
  
  def pluginId = "org.scala-ide.sdt.core"
  def compilerPluginId = "org.scala-ide.scala.compiler"
  def libraryPluginId = "org.scala-ide.scala.library"

  def wizardPath = pluginId + ".wizards"
  def wizardId(name: String) = wizardPath + ".new" + name
  def classWizId = wizardId("Class")
  def traitWizId = wizardId("Trait")
  def objectWizId = wizardId("Object")
  def applicationWizId = wizardId("Application")
  def projectWizId = wizardId("Project")
  def netProjectWizId = wizardId("NetProject")

  def editorId = "scala.tools.eclipse.ScalaSourceFileEditor"
  def builderId = pluginId + ".scalabuilder"
  def natureId = pluginId + ".scalanature"
  def launchId = "org.scala-ide.sdt.launching"
  val scalaCompiler = "SCALA_COMPILER_CONTAINER"
  val scalaLib = "SCALA_CONTAINER"
  def scalaCompilerId = launchId + "." + scalaCompiler
  def scalaLibId = launchId + "." + scalaLib
  def launchTypeId = "scala.application"
  def problemMarkerId = pluginId + ".problem"

  // Retained for backwards compatibility
  val oldPluginId = "ch.epfl.lamp.sdt.core"
  val oldLibraryPluginId = "scala.library"
  val oldNatureId = oldPluginId + ".scalanature"
  val oldBuilderId = oldPluginId + ".scalabuilder"
  val oldLaunchId = "ch.epfl.lamp.sdt.launching"
  val oldScalaLibId = oldLaunchId + "." + scalaLib

  val scalaFileExtn = ".scala"
  val javaFileExtn = ".java"
  val jarFileExtn = ".jar"

  val scalaCompilerBundle = Platform.getBundle(ScalaPlugin.plugin.compilerPluginId)
  val compilerClasses = pathInBundle(scalaCompilerBundle, "/lib/scala-compiler.jar")
  val continuationsClasses = pathInBundle(scalaCompilerBundle, "/lib/continuations.jar")
  val compilerSources = pathInBundle(scalaCompilerBundle, "/lib/scala-compiler-src.jar")

  val scalaLibBundle = Platform.getBundle(ScalaPlugin.plugin.libraryPluginId)
  val libClasses = pathInBundle(scalaLibBundle, "/lib/scala-library.jar")
  val libSources = pathInBundle(scalaLibBundle, "/lib/scala-library-src.jar")
  val dbcClasses = pathInBundle(scalaLibBundle, "/lib/scala-dbc.jar")
  val dbcSources = pathInBundle(scalaLibBundle, "/lib/scala-dbc-src.jar")
  val swingClasses = pathInBundle(scalaLibBundle, "/lib/scala-swing.jar")
  val swingSources = pathInBundle(scalaLibBundle, "/lib/scala-swing-src.jar")

  lazy val templateManager = new ScalaTemplateManager()
  lazy val updateOccurrenceAnnotationsService = new UpdateOccurrenceAnnotationsService()
  private var _serviceTracker : ServiceTracker = null

  private val projects = new HashMap[IProject, ScalaProject]

  override def start(context: BundleContext) = {
    println("starting org.scala-ide.sdt.core")
    super.start(context)
    import scala.tools.eclipse.ext.{OnStart, ServiceTrackerCustomizer4OnStart}
    _serviceTracker = new ServiceTracker(context, classOf[OnStart].getName, new ServiceTrackerCustomizer4OnStart(context))
    _serviceTracker.open();
    println("starting tracker : " + _serviceTracker)
    if (System.getProperty(HEADLESS_TEST) eq null) {
      ResourcesPlugin.getWorkspace.addResourceChangeListener(this, IResourceChangeEvent.PRE_CLOSE | IResourceChangeEvent.POST_CHANGE)
      JavaCore.addElementChangedListener(this)
      PlatformUI.getWorkbench.getEditorRegistry.setDefaultEditor("*.scala", editorId)
      ScalaPlugin.getWorkbenchWindow map (_.getPartService().addPartListener(ScalaPlugin.this))

      PerspectiveFactory.updatePerspective
      diagnostic.StartupDiagnostics.run
    }
    Tracer.println("Scala compiler bundle z: " + scalaCompilerBundle.getLocation)
  }

  override def stop(context: BundleContext) = {
    _serviceTracker.close()
    ResourcesPlugin.getWorkspace.removeResourceChangeListener(this)
    super.stop(context)
  }

  def workspaceRoot = ResourcesPlugin.getWorkspace.getRoot

  def getJavaProject(project: IProject) = JavaCore.create(project)

  def getScalaProject(project: IProject): ScalaProject = projects.synchronized {
    projects.get(project) match {
      case Some(scalaProject) => scalaProject
      case None =>
        val scalaProject = new ScalaProject(project)
        projects(project) = scalaProject
        scalaProject
    }
  }

  def getScalaProject(input: IEditorInput): ScalaProject = input match {
    case fei: IFileEditorInput => getScalaProject(fei.getFile.getProject)
    case cfei: IClassFileEditorInput => getScalaProject(cfei.getClassFile.getJavaProject.getProject)
    case _ => null
  }

  def isScalaProject(project: IJavaProject): Boolean = isScalaProject(project.getProject)

  def isScalaProject(project: IProject): Boolean =
    try {
      project != null && project.isOpen && (project.hasNature(natureId) || project.hasNature(oldNatureId))
    } catch {
      case _: CoreException => false
    }

  //TODO merge behavior with/into elementChanged ?
  override def resourceChanged(event: IResourceChangeEvent) {
    if ((event.getType & IResourceChangeEvent.PRE_CLOSE) != 0) {
      event.getResource match {
        case project : IProject =>  projects.synchronized{
          projects.get(project) match {
            case Some(scalaProject) =>
              Defensive.tryOrLog {
                projects.remove(project)
                scalaProject.resetCompilers(null)
              }
            case None => 
          }
        }
        case _ => ()
      }

    }
  }


  //TODO invalidate (set dirty) cache about classpath, compilers,... when sourcefolders, classpath change
  override def elementChanged(event: ElementChangedEvent) {
    if ((event.getType & ElementChangedEvent.POST_CHANGE) == 0) {
      return
    }
    processDelta(event.getDelta)
  }
  
  private def processDelta(delta: IJavaElementDelta) {    
    import IJavaElement._    
    import IJavaElementDelta._
    import scala.collection.mutable.ListBuffer
    
    val isChanged = delta.getKind == CHANGED
    val isRemoved = delta.getKind == REMOVED
    def hasFlag(flag: Int) = (delta.getFlags & flag) != 0

    val elem = delta.getElement
    val toDelete = new ListBuffer[ScalaSourceFile]
    val processChildren: Boolean = elem.getElementType match {
      case JAVA_MODEL => true
      case JAVA_PROJECT if !isRemoved && !hasFlag(F_CLOSED) => true

      case PACKAGE_FRAGMENT_ROOT =>
        if (isRemoved || (isChanged && (hasFlag(F_REMOVED_FROM_CLASSPATH | F_ADDED_TO_CLASSPATH | F_ARCHIVE_CONTENT_CHANGED)))) {
          //println("package fragment root changed (resetting pres compiler): " + elem)
          val p = getScalaProject(elem.getJavaProject.getProject)
          JobUtils.askRunInJob2("classpath change") { m => 
            p.clean(m) //.resetPresentationCompiler
          }
          false
        } else true

        case PACKAGE_FRAGMENT => true

      case COMPILATION_UNIT if elem.isInstanceOf[ScalaSourceFile] && isRemoved =>
        toDelete += elem.asInstanceOf[ScalaSourceFile]
        false
      case _ => false
    }
    if (processChildren)
      delta.getAffectedChildren foreach { processDelta(_) }
    if(!toDelete.isEmpty) {
      for (
        (project, srcs) <- toDelete.toList.groupBy( _.getJavaProject.getProject ) ;
        if (project.isOpen)
      ) {
        getScalaProject(project).withPresentationCompilerIfExists { _.filesDeleted(srcs.map(_.file)) }
      }
    }
  }

  def logInfo(msg : String, t : Option[Throwable] = None) : Unit = log(IStatus.INFO, msg, t)

  def logWarning(msg : String, t : Option[Throwable] = None) : Unit = log(IStatus.WARNING, msg, t)


  def logError(t: Throwable): Unit = logError(t.getClass + ":" + t.getMessage, t)

  def logError(msg: String, t: Throwable): Unit = {
    val t1 = if (t != null) t else { val ex = new Exception ; ex.fillInStackTrace ; ex }
    log(IStatus.ERROR, msg, Some(t1))
  }
  
  private def log(level : Int, msg : String, t : Option[Throwable]) : Unit = {
    val status1 = new Status(level, pluginId, level, msg, t.getOrElse(null))
    getLog.log(status1)

    t match {
      case ce: ControlThrowable =>
        val t2 = { val ex = new Exception; ex.fillInStackTrace; ex }
        val status2 = new Status(
          IStatus.ERROR, pluginId, IStatus.ERROR,
          "Incorrectly logged ControlThrowable: " + ce.getClass.getSimpleName + "(" + ce.getMessage + ")", t2)
        getLog.log(status2)
      case _ =>
    }
  }

  def bundlePath = check {
    val bundle = getBundle
    val bpath = bundle.getEntry("/")
    val rpath = FileLocator.resolve(bpath)
    rpath.getPath
  }.getOrElse("unresolved")

  final def check[T](f: => T) =
    try {
      Some(f)
    } catch {
      case e: Throwable =>
        logError(e)
        None
    }

  final def checkOrElse[T](f: => T, msgIfError: String): Option[T] = {
    try {
      Some(f)
    } catch {
      case e: Throwable =>
        logError(msgIfError, e)
        None
    }
  }

  def isBuildable(file: IFile) = (file.getName.endsWith(scalaFileExtn) || file.getName.endsWith(javaFileExtn))

  // IPartListener
  def partActivated(part: IWorkbenchPart) {}
  def partDeactivated(part: IWorkbenchPart) {}
  def partBroughtToTop(part: IWorkbenchPart) {}
  def partOpened(part: IWorkbenchPart) {
    Tracer.println("open " + part.getTitle)
    doWithCompilerAndFile(part) { (compiler, ssf) =>
      compiler.askReload(ssf.file, ssf.getContents)
    }
  }
  def partClosed(part: IWorkbenchPart) {
    Tracer.println("close " + part.getTitle)
    doWithCompilerAndFile(part) { (compiler, ssf) =>
      compiler.discardSourceFile(ssf.file)
    }
  }


  private def doWithCompilerAndFile(part : IWorkbenchPart)(op: (ScalaPresentationCompiler, ScalaSourceFile) => Unit) {
	  part match {
      case editor: IEditorPart =>
        editor.getEditorInput match {
          case fei: FileEditorInput =>
            val f = fei.getFile
            if (f.getName.endsWith(scalaFileExtn)) {
              for (ssf <- ScalaSourceFile.createFromPath(f.getFullPath.toString)) {
            	  val proj = getScalaProject(f.getProject)
                if (proj.underlying.isOpen) {
                  proj.withPresentationCompiler(op(_, ssf)) ()
                  //proj.doWithPresentationCompiler(op(_, ssf)) // so that an exception is not thrown
                }
              }
            }
          case _ =>
        }
      case _ =>
    }  
  }
}
