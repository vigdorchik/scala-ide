package scala.tools.eclipse.scaladoc_javadoc

import org.eclipse.jdt.core.IPackageFragmentRoot
import org.eclipse.jdt.core.JavaCore
import scala.tools.eclipse.testsetup.SDTTestUtils
import scala.tools.eclipse.javaelements._
import scala.tools.eclipse.scaladoc._
import org.junit.Assert._
import org.eclipse.core.runtime.Path
import org.junit.Test
import org.eclipse.jface.text.Region
import scala.tools.eclipse.markoccurrences.ScalaOccurrencesFinder
import scala.tools.eclipse.ScalaWordFinder
import scala.tools.eclipse.testsetup.TestProjectSetup
import scala.tools.eclipse._

object ScaladocJavadocTest extends TestProjectSetup("scaladoc-javadoc")

class ScaladocJavadocTest {
  import ScaladocJavadocTest._
  
  def computeScaladocComment(unit : ScalaCompilationUnit, marker : String) : String = {
	  project.withSourceFile(unit) { (src, comp) =>
	    new ScaladocCommentsToEclipseHtmlTransformer {
          val compiler = comp;
          import compiler._
          
          def computeComment : String = {
            val pos = SDTTestUtils.positionsOf(src.content, marker).head;
            val resp =  new Response[Tree]
            val range = rangePos(src, pos - 2, pos - 2, pos - 1)
            askTypeAt(range, resp)          
            val r = resp.get
            if (r.isRight) {
              //if we are here, then an exception was thrown during parsing
              println(r.right.get.printStackTrace)    
              fail(r.right.get.toString)
            } else
              r.left.foreach(t => {
                return buildCommentAsHtml(unit, t.symbol, t.tpe).toString
              })
            "UNEXPECTED_ERROR"
          }
	    }.computeComment
	  }() 
  }
  
  val unit = compilationUnit("scaladoc/ScaladocTestData.scala").asInstanceOf[ScalaCompilationUnit];
  
  @Test def scaladocFromLibraryTest() {        
    assertEquals(arrayBufferSizeDoc, computeScaladocComment(unit, "/*<2*/"));
    assertTrue(computeScaladocComment(unit, "/*<3*/").contains(arrayBufferRemoveDoc));
  }
  
  @Test def scaladocFromProjectFilesTest() {        
    assertTrue(computeScaladocComment(unit, "/*<5*/").contains(aDocumentedClassDoc));
  }
  
  @Test def unexpectedFailingTests() {     
    // the following tests fail even if they shouldn't. They might reveal the cause for 
    // many times delayed display of scaladoc comments in the IDE
    
    val arrayBufferClassDoc = computeScaladocComment(unit, "/*<1*/");
    assertTrue(arrayBufferClassDoc.contains(arrayBufferDocFragment));     
    
    assertTrue(computeScaladocComment(unit, "/*<6*/").contains(aDocumentedMethodDoc));
  }
  
  @Test def scaladocHarvestingLimitationsTest() {    
    //scaladoc is not propagated if the method is overriden
    assertEquals(arrayBufferSizeHintDoc, computeScaladocComment(unit, "/*<4*/"));    
  }
  
  val arrayBufferDocFragment = """An implementation of the"""
    
  val arrayBufferSizeDoc = """<h5>override def size: Int</h5><p>The size of this sequence, equivalent to <code>length</code>.</p><p> $willNotTerminateInf
</p>"""  

  val arrayBufferRemoveDoc = """Removes the element at a given index position"""
    
  val arrayBufferSizeHintDoc = """Gives a hint how many elements are expected to be added"""
    
  val aDocumentedClassDoc = """Documentation of a class."""
    
  val aDocumentedMethodDoc = """Documentation of a method."""  
}