/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.tools.eclipse.util.Tracer
import scala.collection.mutable
import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jdt.internal.compiler.problem.{ DefaultProblem, ProblemSeverities }
import scala.tools.nsc.interactive.{Global, InteractiveReporter, Problem, FreshRunReq}
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.reporters.Reporter
import scala.tools.nsc.util.{ BatchSourceFile, Position, SourceFile }
import scala.tools.eclipse.javaelements.{
  ScalaIndexBuilder, ScalaJavaMapper, ScalaMatchLocator, ScalaStructureBuilder,
  ScalaOverrideIndicatorBuilder }
import scala.tools.eclipse.util.{ Cached, EclipseFile, EclipseResource, IDESettings }
import scala.tools.nsc.interactive.compat.Settings
import scala.tools.nsc.interactive.compat.conversions._

class ScalaPresentationCompiler(project : ScalaProject, settings : Settings)
  extends Global(settings, new ScalaPresentationCompiler.PresentationReporter, project.underlying.getName)
  with ScalaStructureBuilder 
  with ScalaIndexBuilder 
  with ScalaMatchLocator
  with ScalaOverrideIndicatorBuilder 
  with ScalaJavaMapper 
  with JVMUtils 
  with LocateSymbol { self =>
  
  def presentationReporter = reporter.asInstanceOf[ScalaPresentationCompiler.PresentationReporter]
  presentationReporter.compiler = this
  
  private val sourceFiles = new mutable.HashMap[AbstractFile, BatchSourceFile]{
    override def default(k : AbstractFile) = { 
      val v = new BatchSourceFile(k)
      ScalaPresentationCompiler.this.synchronized {
        get(k) match {
          case Some(v) => v
          case None => put(k, v); v
        }
      }
    }
  }
  
  def askRunLoadedTyped(file : AbstractFile) = {
    for (source <- (sourceFiles get file)) {
      val response = new Response[Tree]
      askLoadedTyped(source, response)
      val timeout = IDESettings.timeOutBodyReq.value //Defensive use a timeout
      response.get(timeout) orElse { throw new AsyncGetTimeoutException(timeout, "askRunLoadedTyped(" + file + ")") }
    }
  }
        
  def askProblemsOf(file : AbstractFile) : List[IProblem] = ask{() =>
    val b = unitOfFile get file match {
      case Some(unit) => 
        val result = unit.problems.toList flatMap presentationReporter.eclipseProblem
        //unit.problems.clear()
        result
      case None => 
        Tracer.println("no unit for " + file)
        Nil
    }
    Tracer.println("problems of " + file + " : " + b.size)
    b.toList
  }
  
  //def problemsOf(scu : IFile) : List[IProblem] = problemsOf(FileUtils.toAbstractFile(scu))
  
  def withSourceFile[T](scu : AbstractFile)(op : (SourceFile, ScalaPresentationCompiler) => T) : T =
    op(sourceFiles(scu), this)

    
  def body(sourceFile : SourceFile) = {
    val response = new Response[Tree]
    askType(sourceFile, false, response)
    val timeout = IDESettings.timeOutBodyReq.value //Defensive use a timeout see issue_0003 issue_0004
    response.get(timeout) match {
      case None => throw new AsyncGetTimeoutException(timeout, "body(" + sourceFile + ")")
      case Some(x) => x match {
        case Left(tree) => tree
        case Right(exc) => throw new AsyncGetException(exc, "body(" + sourceFile + ")")
      }
    }
  }
  
  def withParseTree[T](sourceFile : SourceFile)(op : Tree => T) : T = {
    op(parseTree(sourceFile))
  }

  def withStructure[T](sourceFile : SourceFile)(op : Tree => T) : T = {
    val tree = {
      val response = new Response[Tree]
      askStructure(sourceFile, response)
      val timeout = math.max(15000, IDESettings.timeOutBodyReq.value)
      response.get(timeout) match {
        case None => throw new AsyncGetTimeoutException(timeout, "withStructure(" + sourceFile + ")")
        case Some(x) => x match {
          case Left(tree) => tree
          case Right(exc) => throw new AsyncGetException(exc, "withStructure(" + sourceFile + ")")
        }
      }      
    }
    op(tree)
  }
  /** Perform `op' on the compiler thread. Catch all exceptions, and return 
   *  None if an exception occured. TypeError and FreshRunReq are printed to
   *  stdout, all the others are logged in the platform error log.
   */
  def askOption[A](op: () => A): Option[A] =
    try Some(ask(op))
    catch {
      case e: TypeError =>
        println("TypeError in ask:\n" + e)
        None
      case f: FreshRunReq =>
        println("FreshRunReq in ask:\n" + f)
         None
// BACK-2.8.1         
//      case e @ InvalidCompanions(c1, c2) =>
//        reporter.warning(c1.pos, e.getMessage)
//        None
      case e =>
        ScalaPlugin.plugin.logError("Error during askOption", e)
        None
    }
  
  def askReload(scu : AbstractFile, content : Array[Char]) {
    Tracer.println("askReload 3: " + scu)
//    sourceFiles.get(scu) match {
//      case None =>
//      case Some(f) => {
        val newF = new BatchSourceFile(scu, content) 
        Tracer.println("content length :" + content.length)
        synchronized { sourceFiles(scu) = newF } 
        askReload(List(newF), new Response[Unit])
//      }
//    }
  }
  
  def filesDeleted(files : List[AbstractFile]) {
    Tracer.println("files deleted:\n" + (files map (_.path) mkString "\n"))
    synchronized {
      val srcs = files.map(sourceFiles remove _).foldLeft(List[SourceFile]()) {
        case (acc, None) => acc
        case (acc, Some(f)) => f::acc
      }
      if (!srcs.isEmpty)
        askFilesDeleted(srcs, new Response[Unit])
    }
  }

  def discardSourceFile(scu : AbstractFile) {
    Tracer.println("discarding " + scu)
    synchronized {
      for (source <- sourceFiles.get(scu)) {
        removeUnitOf(source)
        sourceFiles.remove(scu)
      }
    }
  }

  override def logError(msg : String, t : Throwable) =
    ScalaPlugin.plugin.logError(msg, t)
    
  def destroy() {
    askShutdown
  }
}

object ScalaPresentationCompiler {
  class PresentationReporter extends InteractiveReporter {
    var compiler : ScalaPresentationCompiler = null
      
    def nscSeverityToEclipse(severityLevel: Int) = 
      severityLevel match {
        case v if v == ERROR.id => ProblemSeverities.Error
        case v if v == WARNING.id => ProblemSeverities.Warning
        case v if v == INFO.id => ProblemSeverities.Ignore
      }
    
    def eclipseProblem(prob: Problem): Option[IProblem] = {
      import prob._
      if (pos.isDefined) {
          val source = pos.source
          val pos1 = pos.toSingleLine
          source.file match {
            case ef@EclipseFile(file) =>
              Some(
                new DefaultProblem(
                  file.getFullPath.toString.toCharArray,
                  formatMessage(msg),
                  0,
                  new Array[String](0),
                  nscSeverityToEclipse(severityLevel),
                  pos1.startOrPoint,
                  pos1.endOrPoint,
                  pos1.line,
                  pos1.column
                ))
            case af : AbstractFile =>
              Some(
                new DefaultProblem(
                  af.path.toCharArray,
                  formatMessage(msg),
                  0,
                  new Array[String](0),
                  nscSeverityToEclipse(severityLevel),
                  pos1.startOrPoint,
                  pos1.endOrPoint,
                  pos1.line,
                  pos1.column
                ))
            case _ => None
          }
        } else None
      }   

      def formatMessage(msg : String) = msg.map{
        case '\n' => ' '
        case '\r' => ' '
        case c => c
      }
  }
}

/**
 * Wrapping exception for Exception raise in an other thread (from async call).
 * Help to find where is the cause on caller/context (ask+get).
 * Message of the exception include the hashCode of the cause, because a cause Exception can be wrapped several time.
 */
class AsyncGetException(cause : Throwable, contextInfo : String = "") extends Exception("origin (" + cause.hashCode + ") : " + cause.getMessage + " [" + contextInfo + "]", cause)
class AsyncGetTimeoutException(timeout : Int, contextInfo : String = "") extends Exception("timeout (" + timeout + " ms) expired [" + contextInfo + "]")
