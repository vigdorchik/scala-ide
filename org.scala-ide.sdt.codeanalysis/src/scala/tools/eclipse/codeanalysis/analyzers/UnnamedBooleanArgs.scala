/*
 * Copyright 2011 LAMP/EPFL
 */

package scala.tools.eclipse
package codeanalysis.analyzers

import tools.nsc.io.AbstractFile
import tools.refactoring.implementations.EliminateMatch
import scala.tools.eclipse.codeanalysis.plugin.GlobalCompilationUnit
import scala.tools.eclipse.codeanalysis.plugin.CodeAnalyzer
import scala.tools.nsc.util.Position
import scala.tools.eclipse.codeanalysis.plugin.CodeAnalyzer

class UnamedBooleanArgs extends CodeAnalyzer {

  def analyze(param: GlobalCompilationUnit, msg: String) = {

    val hits = new collection.mutable.ListBuffer[Position]

    import param.global._

    def hasBoolTpe(t: Tree) = t match {
      case Literal(Constant(true | false)) => true
      case _ => false
    }
    
    val traverser = new Traverser {

      override def traverse(t: Tree) = {
        t match {
          case Apply(fun: RefTree, args) 
              if !fun.symbol.isJavaDefined && args.exists(hasBoolTpe) =>
            (t :: args).sliding(2) foreach {
              case Seq(pred, arg) if hasBoolTpe(arg) =>
                val src = t.pos.source.content.slice(pred.pos.point, arg.pos.point)
                if(!src.contains('=')) {
                  hits += arg.pos
                }
              case _ => ()
            }
          case _ => ()
        }
        super.traverse(t)
      }
    }

    traverser.traverse(param.unit.body)

    hits.toList map (Marker(msg, _))
  }
}
