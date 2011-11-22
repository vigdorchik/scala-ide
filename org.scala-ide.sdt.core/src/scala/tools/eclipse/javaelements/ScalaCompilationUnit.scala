/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse
package javaelements

import java.util.{ Map => JMap }
import scala.concurrent.SyncVar
import org.eclipse.core.internal.filebuffers.SynchronizableDocument
import org.eclipse.core.resources.{ IFile, IResource }
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.{
  BufferChangedEvent, CompletionRequestor, IBuffer, IBufferChangedListener, IJavaElement, IJavaModelStatusConstants,
  IProblemRequestor, ITypeRoot, JavaCore, JavaModelException, WorkingCopyOwner, IClassFile }
import org.eclipse.jdt.internal.compiler.env
import org.eclipse.jdt.internal.core.{
  BufferManager, CompilationUnitElementInfo, DefaultWorkingCopyOwner, JavaModelStatus, JavaProject, Openable,
  OpenableElementInfo, SearchableEnvironment }
import org.eclipse.jdt.internal.core.search.matching.{ MatchLocator, PossibleMatch }
import org.eclipse.jdt.internal.ui.javaeditor.DocumentAdapter
import org.eclipse.jface.text.{IRegion, ITextSelection}
import org.eclipse.ui.texteditor.ITextEditor
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.util.{ BatchSourceFile, SourceFile }
import scala.tools.eclipse.contribution.weaving.jdt.{ IScalaCompilationUnit, IScalaWordFinder }
import scala.tools.eclipse.{ ScalaImages, ScalaPlugin, ScalaPresentationCompiler, ScalaSourceIndexer, ScalaWordFinder }
import scala.tools.eclipse.util.ReflectionUtils
import org.eclipse.jdt.core.IField
import org.eclipse.jdt.core.IMethod
import org.eclipse.jdt.core.ISourceReference
import org.eclipse.jdt.core.IParent
import org.eclipse.jdt.internal.core.JavaElement
import org.eclipse.jdt.internal.core.SourceRefElement
import scala.tools.eclipse.util.HasLogger
import scala.tools.nsc.interactive.Response
import scala.tools.eclipse.scaladoc.ScaladocCommentsToEclipseHtmlTransformer

trait ScalaCompilationUnit extends Openable 
	with env.ICompilationUnit 
	with ScalaElement 
	with IScalaCompilationUnit 
	with IBufferChangedListener 
	with JavadocUtils
	with HasLogger {
  val project = ScalaPlugin.plugin.getScalaProject(getJavaProject.getProject)

  val file : AbstractFile
  
  private var lastCrash: Throwable = null

  def doWithSourceFile(op : (SourceFile, ScalaPresentationCompiler) => Unit) {
    project.withSourceFile(this)(op)(())
  }
  
  def withSourceFile[T](op : (SourceFile, ScalaPresentationCompiler) => T)(orElse: => T = project.defaultOrElse) : T = {
    project.withSourceFile(this)(op)(orElse)
  }
  
  override def bufferChanged(e : BufferChangedEvent) {
    if (!e.getBuffer.isClosed)
      project.doWithPresentationCompiler(_.askReload(this, getContents))

    super.bufferChanged(e)
  }
  
  def createSourceFile : BatchSourceFile = {
    new BatchSourceFile(file, getContents())
  }

  def getProblemRequestor : IProblemRequestor = null

  override def buildStructure(info : OpenableElementInfo, pm : IProgressMonitor, newElements : JMap[_, _], underlyingResource : IResource) : Boolean =
    withSourceFile({ (sourceFile, compiler) =>
      val unsafeElements = newElements.asInstanceOf[JMap[AnyRef, AnyRef]]
      val tmpMap = new java.util.HashMap[AnyRef, AnyRef]
      val sourceLength = sourceFile.length
      
      try {
        logger.info("[%s] buildStructure for %s".format(project.underlying.getName(), this.getResource()))
        compiler.withStructure(sourceFile) { tree =>
          compiler.askOption { () =>
              new compiler.StructureBuilderTraverser(this, info, tmpMap, sourceLength).traverse(tree)
          }
        }
        info match {
          case cuei : CompilationUnitElementInfo => 
            cuei.setSourceLength(sourceLength)
          case _ =>
        }
    
        unsafeElements.putAll(tmpMap)
        info.setIsStructureKnown(true)
      } catch {
        case e: InterruptedException =>
          Thread.currentThread().interrupt()
          logger.info("ignored InterruptedException in build structure")
          info.setIsStructureKnown(false)
          
        case ex => 
          handleCrash("Compiler crash while building structure for %s".format(file), ex)
          info.setIsStructureKnown(false)
      }
      info.isStructureKnown
    }) (false)

    
  /** Schedule this unit for reconciliation. This implementation does nothing, subclasses
   *  implement custom behavior. @see ScalaSourceFile
   * 
   *  @return A response on which clients can synchronize. The response
   *          only notifies when the unit was added to the managed sources list, *not*
   *          that it was typechecked.
   */
  def scheduleReconcile(): Response[Unit] = {
    val r = (new Response[Unit])
    r.set()
    r
  }

  /** Log an error at most once for this source file. */
  private def handleCrash(msg: String, ex: Throwable) {
    if (lastCrash != ex) {
      lastCrash = ex
      logger.error(msg, ex)
    }
  }
  
  /** Index this source file, but only if the project has the Scala nature.
   * 
   *  This avoids crashes if the indexer kicks in on a project that has Scala sources
   *  but no Scala library on the classpath.
   */
  def addToIndexer(indexer : ScalaSourceIndexer) {
    if (project.hasScalaNature) {
      try doWithSourceFile { (source, compiler) =>
        compiler.withParseTree(source) { tree =>
          new compiler.IndexBuilderTraverser(indexer).traverse(tree)
        }
      } catch {
        case ex => handleCrash("Compiler crash during indexing of %s".format(getResource()), ex)
      }
    }
  }
  
  def newSearchableEnvironment(workingCopyOwner : WorkingCopyOwner) : SearchableEnvironment = {
    val javaProject = getJavaProject.asInstanceOf[JavaProject]
    javaProject.newSearchableNameEnvironment(workingCopyOwner)
  }

  def newSearchableEnvironment() : SearchableEnvironment =
    newSearchableEnvironment(DefaultWorkingCopyOwner.PRIMARY)
  
  override def getSourceElementAt(pos : Int) : IJavaElement = {
    getChildAt(this, pos) match {
      case smie : ScalaModuleInstanceElement => smie.getParent;
      case null => this
      case elem => elem
    }
  }
  
  def getChildAt(element: IJavaElement, pos: Int): IJavaElement = {
    /* companion-class can be selected instead of the object in the JDT-'super' 
       implementation and make the private method and fields unreachable.
       To avoid this, we look for deepest element containing the position
     */
    
    def depth(e: IJavaElement): Int = if (e == element) 0 else (depth(e.getParent()) + 1)
    
    element match {
      case parent: IParent => {
        var resultElement= element
        // look through the list of children from the end, because the constructor (at
        // the beginning) covers the whole source code
        for (child <- parent.getChildren().reverse) {
          child match {
            case sourceReference: ISourceReference => {
              // check if the range of the child contains the position
              val range= sourceReference.getSourceRange
              val rangeStart= range.getOffset
              if (rangeStart <= pos && pos <= rangeStart + range.getLength) {
                // look in the child itself
                val foundChild = getChildAt(child, pos)
                // check if the found element is more precise than the one previously found
                if (depth(foundChild) > depth(resultElement))
                  resultElement = foundChild
              }
            }
          }
        }
        resultElement
      }
      case elem => elem
    }
  }
    
  override def codeSelect(cu : env.ICompilationUnit, offset : Int, length : Int, workingCopyOwner : WorkingCopyOwner) : Array[IJavaElement] = {
    val scu = this
    withSourceFile({ (src, comp) =>
      new ScaladocCommentsToEclipseHtmlTransformer {
        val compiler = comp;
        import compiler._
      
        //@Iulian & co: this method is really badly placed here since we update the 
        //              JavadocView as a side-effect of the 'codeSelect' 
        def updateJavadocView(sym : Symbol, tpe : Type) {
          import org.eclipse.jdt.internal.ui._
    	  import org.eclipse.jdt.internal.ui.text.java.hover._
    	  import org.eclipse.jdt.internal.ui.infoviews._
          import org.eclipse.jdt.ui._
          import org.eclipse.jface.internal.text.html._
          import org.eclipse.ui._
          import scala.tools.eclipse.util._        
        
          SWTUtils.asyncExec {
    	    import org.eclipse.ui.internal._
	        val window = PlatformUI.getWorkbench.getActiveWorkbenchWindow().getActivePage().asInstanceOf[WorkbenchPage];
	        val perspective = window.getActivePerspective();
	        val viewRef = perspective.findView(JavaUI.ID_JAVADOC_VIEW, null);
	        if (viewRef == null)
	          return;
	        val view = viewRef.getView(true).asInstanceOf[JavadocView];	    
	    	val buffer= new StringBuffer();    
	        HTMLPrinter.insertPageProlog(buffer, 0, styleSheet);
	        val html = buildCommentAsHtml(scu, sym, tpe)    	
    	    buffer.append(html)
	        HTMLPrinter.addPageEpilog(buffer);
	        
	        val javadocViewClazz = Class.forName("org.eclipse.jdt.internal.ui.infoviews.JavadocView")
	        val objectClazz = Class.forName("java.lang.Object")
	        val doSetInputMethod = getDeclaredMethod(javadocViewClazz, "doSetInput", objectClazz); 	        
	        doSetInputMethod.invoke(view, buffer.toString);
      	  }
        }
      
        def computeJavaElement(t: Tree) : Option[IJavaElement] = { 
          askOption { () =>
            import scala.reflect.generic.Flags
            if (t.symbol != null && t.tpe != null && !t.symbol.hasFlag(Flags.JAVA)) { 
              //if the symbol comes from scala, then update the JavadocView
              updateJavadocView(t.symbol, t.tpe)
          	  None: Option[IJavaElement]
            } else { //for Java symbols, leave the JDT do the work of updating the JavadocView
              val res: Option[IJavaElement] = Option(t.symbol).flatMap(getJavaElement2)
              res
            }
          }.flatten.headOption
        }
      
        def performCodeSelect = { 
          val resp = new compiler.Response[Tree]
          val range = rangePos(src, offset, offset, offset + length)
          askTypeAt(range, resp)
          val r = resp.get.left.toOption.map(computeJavaElement(_)).flatten.headOption
          r.map(Array(_)).getOrElse(Array.empty[IJavaElement])
        }
      }.performCodeSelect
    })(Array.empty[IJavaElement])        
  }

  def codeComplete
    (cu : env.ICompilationUnit, unitToSkip : env.ICompilationUnit,
     position : Int,  requestor : CompletionRequestor, owner : WorkingCopyOwner, typeRoot : ITypeRoot) {
     codeComplete(cu, unitToSkip, position, requestor, owner, typeRoot, null) 
  }
    
  override def codeComplete
    (cu : env.ICompilationUnit, unitToSkip : env.ICompilationUnit,
     position : Int,  requestor : CompletionRequestor, owner : WorkingCopyOwner, typeRoot : ITypeRoot,
     monitor : IProgressMonitor) {
  }
  
  override def reportMatches(matchLocator : MatchLocator, possibleMatch : PossibleMatch) {
    doWithSourceFile { (sourceFile, compiler) =>
      val response = new Response[compiler.Tree]
      compiler.askLoadedTyped(sourceFile, response)
      response.get match {
        case Left(tree) => 
          compiler.askOption { () =>
            compiler.MatchLocator(this, matchLocator, possibleMatch).traverse(tree)
          }
        case _ => () // no op
      }
      
    }
  }
  
  override def createOverrideIndicators(annotationMap : JMap[_, _]) {
    if (project.hasScalaNature)
      doWithSourceFile { (sourceFile, compiler) =>
        try {
          compiler.withStructure(sourceFile, keepLoaded = true) { tree =>
            compiler.askOption { () =>
              new compiler.OverrideIndicatorBuilderTraverser(this, annotationMap.asInstanceOf[JMap[AnyRef, AnyRef]]).traverse(tree)
            }
          }
        } catch {
          case ex =>
            handleCrash("Exception thrown while creating override indicators for %s".format(sourceFile), ex)
        }
      }
  }
  
  override def getImageDescriptor = {
    Option(getCorrespondingResource) map { file =>
      import ScalaImages.{ SCALA_FILE, EXCLUDED_SCALA_FILE }
      val javaProject = JavaCore.create(project.underlying)
      if (javaProject.isOnClasspath(file)) SCALA_FILE else EXCLUDED_SCALA_FILE
    } orNull
  }
  
  override def getScalaWordFinder() : IScalaWordFinder = ScalaWordFinder
  
  def followReference(editor : ITextEditor, selection : ITextSelection) = {
    val region = new IRegion {
      def getOffset = selection.getOffset
      def getLength = selection.getLength
    }
    new ScalaHyperlinkDetector().detectHyperlinks(editor, region, false) match {
      case Array(hyp) => hyp.open
      case _ =>  
    }
  }
}

object OpenableUtils extends ReflectionUtils {
  private val oClazz = classOf[Openable]
  private val openBufferMethod = getDeclaredMethod(oClazz, "openBuffer", classOf[IProgressMonitor], classOf[AnyRef])
  private val getBufferManagerMethod = getDeclaredMethod(oClazz, "getBufferManager")

  def openBuffer(o : Openable, pm : IProgressMonitor, info : AnyRef) : IBuffer = openBufferMethod.invoke(o, pm, info).asInstanceOf[IBuffer]
  def getBufferManager(o : Openable) : BufferManager = getBufferManagerMethod.invoke(o).asInstanceOf[BufferManager]
}
