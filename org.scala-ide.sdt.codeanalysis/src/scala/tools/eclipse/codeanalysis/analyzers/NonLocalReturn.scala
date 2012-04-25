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

class NonLocalReturn extends CodeAnalyzer {

  def analyze(param: GlobalCompilationUnit, msg: String) = {

    import param.global

    val hits = new collection.mutable.ListBuffer[Position]

    val traverser = new global.Traverser {

      def isNonLocalReturn(ret: global.Return) = {
        val a = (ret.symbol.toString)
        val b = (ret.symbol.owner.toString)
        val c = (currentOwner.toString)
        val d = (currentOwner.enclMethod.toString)
        ret.symbol != currentOwner || currentOwner.isLazy
      }

      override def traverse(t: global.Tree) = {
        t match {
          case t: global.Return =>
            if(isNonLocalReturn(t)) {
              hits += t.pos
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
