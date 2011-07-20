package scala.tools.eclipse.scaladoc

import scala.tools.eclipse.ScalaPresentationCompiler
import scala.tools.nsc.symtab.Flags
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.javaelements.ScalaClassFile

trait ScalaCommentsExtractor { self : ScalaPresentationCompiler =>
  
  def defString(sym: Symbol, tpe: Type): String = {      
    def compose(ss: List[String]): String = ss.filter("" !=).mkString("", " ", "")

    // NoType is returned for defining occurrences, in this case we want to display symbol info itself. 
    val tpeinfo = if (tpe ne NoType) tpe.widen else sym.info
    compose(List(sym.hasFlagsToString(Flags.ExplicitFlags), sym.keyString,
      sym.varianceString + sym.nameString + sym.infoString(tpeinfo)))
  }

  def extractComment(sym : Symbol, scu : ScalaCompilationUnit) = {
    val loc = locate(sym, scu)
    loc match {
      case Some((scf: ScalaClassFile, pos)) =>
        //if the symbol is defined in a scala class file (scf), parse the source   
        //corresponding to that file to harvest the ScalaDoc                      
        scf.withSourceFile({ (src, compiler) =>
          val resp = new Response[Tree]
          val range = compiler.rangePos(src, pos, pos, pos + sym.name.length)
          askTypeAt(range, resp)
          resp.get
        })();
      case _ =>
    }
    
    (askOption(() => expandedDocComment(sym)).getOrElse(""), 
        rawDocComment(sym))
  }
}
