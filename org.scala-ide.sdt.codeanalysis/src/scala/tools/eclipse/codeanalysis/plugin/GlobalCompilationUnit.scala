package scala.tools.eclipse.codeanalysis.plugin

import scala.tools.nsc.Global

trait GlobalCompilationUnit {
  val global: Global
  val unit: global.CompilationUnit
}