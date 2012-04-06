/*
 * Copyright 2011 LAMP/EPFL
 */

package scala.tools.eclipse
package codeanalysis.analyzers

import scala.tools.eclipse.codeanalysis.plugin.CodeAnalyzer
import scala.tools.eclipse.codeanalysis.plugin.GlobalCompilationUnit
import org.eclipse.core.internal.resources.Marker
import scala.tools.eclipse.codeanalysis.plugin.CodeAnalyzer

class PrintlnStatement extends CodeAnalyzer {

  def analyze(param: GlobalCompilationUnit, msg: String) = {

    import param._
    import global._

    object Tm {
      val scala = newTermName("scala")
      val lang = newTermName("lang")
      val System = newTermName("System")
      val Console = newTermName("Console")
      val Predef = newTermName("Predef")
      val out = newTermName("out")
      val println = newTermName("println")
    }

    object Ty {
      val java = newTypeName("java")
      val scala = newTypeName("scala")
    }

    unit.body filter {
      case Apply(Select(Select(Select(Select(This(Ty.java), Tm.lang), Tm.System), Tm.out), Tm.println), _) =>
        true
      case Apply(Select(Select(This(Ty.scala), Tm.Predef), Tm.println), _) =>
        true
      case Apply(Select(Select(Ident(Tm.scala), Tm.Console), Tm.println), _) =>
        true
      case _ =>
        false
    } map { t =>
      Marker(msg, t.pos)
    }
  }
}
