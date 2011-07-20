/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse 

import org.eclipse.jdt.core.{IJavaElement, ICodeAssist}
import org.eclipse.jdt.core._
import org.eclipse.jdt.internal.ui.text.java.hover.JavadocHover
import org.eclipse.jface.text.{ITextViewer, IRegion}
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import org.eclipse.jdt.internal.core.JavaModelManager
import scala.tools.eclipse.javaelements.ScalaSourceTypeElement
import org.eclipse.jface.internal.text.html.HTMLPrinter
import org.eclipse.jdt.internal.ui.text.java.hover.JavadocBrowserInformationControlInput
import util.ReflectionUtils
import org.eclipse.jdt.internal.core.SourceType
import scala.tools.nsc.doc.model.comment.CommentFactory
import scala.tools.nsc.doc.model.ModelFactory
import scala.tools.nsc.doc.model.TreeFactory
import scala.tools.nsc.doc.html.HtmlPage
import scala.xml.NodeSeq
import scala.tools.eclipse.javaelements.ScalaClassFile
import scala.tools.eclipse.javaelements.ScalaElement
import scala.tools.nsc.doc.model.comment.Comment
import scala.util.matching.Regex
import scala.tools.nsc.doc.model.comment.Body
import scala.tools.eclipse.scaladoc.ScaladocCommentsToEclipseHtmlTransformer

class ScalaHover(codeAssist: () => Option[ICodeAssist]) extends JavadocHover with JavadocUtils {
  
  override def getJavaElementsAt(textViewer: ITextViewer, region: IRegion) = {
    codeAssist() match {
      case Some(scu: ScalaCompilationUnit) => {
        val start = region.getOffset
        val end = start + region.getLength
        scu.withSourceFile({ (src, compiler) =>
          import compiler._        
          val resp = new Response[Tree]
          val range = compiler.rangePos(src, start, start, end)
          askTypeAt(range, resp)
          resp.get match {
            case Left(tree) if tree.symbol ne null => 
              askOption { () => 
                compiler.getJavaElement2(tree.symbol).getOrElse(null) 
              } match {
                case Some(el) => Array[IJavaElement](el)                  	
                case _ => Array.empty[IJavaElement]
              }
            case _ => 
              Array.empty[IJavaElement]
          }          
        })(Array.empty[IJavaElement])
      }
      case _ => 
        Array.empty[IJavaElement]
    }
  }

  override def getHoverInfo2(textViewer: ITextViewer, hoverRegion: IRegion): Object = {
    val javaElements = getJavaElementsAt(textViewer, hoverRegion);
    if (javaElements.length > 0 && !javaElements(0).isInstanceOf[ScalaElement]
    	 && !javaElements(0).isInstanceOf[ScalaClassFile#ScalaBinaryType]) {
      try {
        super.getHoverInfo2(textViewer, hoverRegion)
      } catch { 
        case e => buildScalaHover(textViewer, hoverRegion)
      }
    } else {
      buildScalaHover(textViewer, hoverRegion)
    }
  }
  
    
  def buildScalaHover(viewer: ITextViewer, region: IRegion) : JavadocBrowserInformationControlInput = {
    val buffer= new StringBuffer();    
    HTMLPrinter.insertPageProlog(buffer, 0, styleSheet);
    
    codeAssist() match {
      case Some(scu: ScalaCompilationUnit) => {
        val start = region.getOffset
        val end = start + region.getLength
        scu.withSourceFile({ (src, comp) =>
          new ScaladocCommentsToEclipseHtmlTransformer {
            val compiler = comp;
        	import compiler._
          
            def doBuildHover(t: Tree) { 
              if (t.symbol != null && t.tpe != null) {                 
                buffer.append(buildCommentAsHtml(scu, t.symbol, t.tpe))
              }
            }  
        	
            val resp = new Response[Tree]
            val range = rangePos(src, start, start, end)
            askTypeAt(range, resp)
            resp.get.left.foreach(doBuildHover(_))          
          }
          
        })()
      }
      case _ => 
    }
     
	HTMLPrinter.addPageEpilog(buffer);
	new JavadocBrowserInformationControlInput(null, null, buffer.toString, 20)
  }

  override def getHoverRegion(viewer: ITextViewer, offset: Int) = {
    ScalaWordFinder.findWord(viewer.getDocument, offset)
  }
}

trait JavadocUtils extends ReflectionUtils {
  
  val styleSheet = {
    val javadocHoverClazz = Class.forName("org.eclipse.jdt.internal.ui.text.java.hover.JavadocHover")
    val getStyleSheetMethod = getDeclaredMethod(javadocHoverClazz, "getStyleSheet") 
    getStyleSheetMethod.invoke(null).asInstanceOf[String];
  }
}

