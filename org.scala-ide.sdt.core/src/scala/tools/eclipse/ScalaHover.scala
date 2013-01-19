/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import ui.BrowserControlCreator
import org.eclipse.jdt.core.ICodeAssist
import org.eclipse.jface.text.{ ITextViewer, IRegion, ITextHover, ITextHoverExtension, ITextHoverExtension2 }
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Shell
import  org.eclipse.jface.text.{IInformationControlCreator, DefaultInformationControl}

import scala.tools.nsc.symtab.Flags
import scala.tools.eclipse.util.EclipseUtils._

class ScalaHover(val icu: InteractiveCompilationUnit) extends ITextHover with  ITextHoverExtension with ITextHoverExtension2 {

  private val NoHoverInfo = "" // could return null, but prefer to return empty (see API of ITextHover).

  def getHoverInfo(viewer: ITextViewer, region: IRegion) = null

  def getHoverInfo2(viewer: ITextViewer, region: IRegion): Object = {
    val start = region.getOffset
    val end = start + region.getLength
    icu.withSourceFile({ (src, compiler) =>
      import compiler._

      def hoverInfo(t: Tree): Option[Object] = 
        for ((sym, site, header) <- askOption { () =>
          def compose(ss: List[String]): String = ss.filter("" !=).mkString("", " ", "")
          def defString(sym: Symbol, tpe: Type): String = {
            // NoType is returned for defining occurrences, in this case we want to display symbol info itself.
            val tpeinfo = if (tpe ne NoType) tpe.widen else sym.info
            compose(List(sym.hasFlagsToString(Flags.ExplicitFlags), sym.keyString, sym.varianceString + sym.nameString +
              sym.infoString(tpeinfo)))
          }

          for (sym <- Option(t.symbol); tpe <- Option(t.tpe))
            yield {
              val header = if (sym.isClass || sym.isModule) sym.fullName else defString(sym, tpe)
              val site = t match {
                case apply: GenericApply => apply.fun.symbol.enclClass
                case Select(qual, _) => qual.tpe.typeSymbol
                case _ => sym.enclClass
              }
              (sym, site, header)
            }
      } flatten) yield
        documentation(sym, site, header) getOrElse {
          val html = "<html><body><b>" + header + "</b></body></html>"
          new BrowserInput(html, sym)
        }

      val resp = new Response[Tree]
      askTypeAt(region.toRangePos(src), resp)
      (for (
        t <- resp.get.left.toOption;
        hover <- hoverInfo(t)
      ) yield hover) getOrElse NoHoverInfo
    })(NoHoverInfo)
  }

  def getHoverRegion(viewer: ITextViewer, offset: Int) = {
    ScalaWordFinder.findWord(viewer.getDocument, offset)
  }

  def getHoverControlCreator() = new IInformationControlCreator {
    def createInformationControl(shell: Shell) = new DefaultInformationControl(shell, false)
  }
}
