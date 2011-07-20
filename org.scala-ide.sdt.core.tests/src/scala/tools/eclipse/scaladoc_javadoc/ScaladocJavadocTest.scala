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
            val tree =  new Response[Tree]
            val range = rangePos(src, pos - 2, pos - 2, pos - 1)
            askTypeAt(range, tree)          
            tree.get.left.foreach(t => {
              return buildCommentAsHtml(unit, t.symbol, t.tpe).toString
            })
            ""
          }
	    }.computeComment
	  }() 
  }
  
  val unit = compilationUnit("scaladoc/ScaladocTestData.scala").asInstanceOf[ScalaCompilationUnit];
  
  @Test def scaladocFromLibraryTest() {        
    assertTrue(computeScaladocComment(unit, "/*<1*/").contains(arrayBufferDoc));     
    assertEquals(arrayBufferSizeDoc, computeScaladocComment(unit, "/*<2*/"));
    assertTrue(computeScaladocComment(unit, "/*<3*/").contains(arrayBufferRemoveDoc));
  }
  
  @Test def scaladocHarvestingLimitationsTest() {    
    //scaladoc is not propagated if the method is overriden
    assertEquals(arrayBufferSizeHintDoc, computeScaladocComment(unit, "/*<4*/"));    
  }
  
  @Test def scaladocFromProjectFilesTest() {        
    val c = computeScaladocComment(unit, "/*<5*/");
    assertTrue(computeScaladocComment(unit, "/*<5*/").contains(aDocumentedClassDoc));
        
    val cc = computeScaladocComment(unit, "/*<6*/");
    assertTrue(computeScaladocComment(unit, "/*<6*/").contains(aDocumentedMethodDoc));
  }
  
  val arrayBufferDoc = 
"""scala.collection.mutable.ArrayBuffer</div><p>An implementation of the <code>Buffer</code> class using an array to
 represent the assembled sequence internally. Append, update and random
 access take constant time (amortized time). Prepends and removes are
 linear in the buffer size.
</p><h5>Type Parameters:</h5><ul><li>A -  the type of this arraybuffer's elements .</li></ul>"""
    
  val arrayBufferSizeDoc = """<h5>override def size: Int</h5><p>The size of this sequence, equivalent to <code>length</code>.</p><p> $willNotTerminateInf
</p>"""  

  val arrayBufferRemoveDoc = """Removes the element at a given index position"""
    
  val arrayBufferSizeHintDoc = """Gives a hint how many elements are expected to be added"""
    
  val aDocumentedClassDoc = """Documentation of a class."""
    
  val aDocumentedMethodDoc = """Documentation of a method."""  
}