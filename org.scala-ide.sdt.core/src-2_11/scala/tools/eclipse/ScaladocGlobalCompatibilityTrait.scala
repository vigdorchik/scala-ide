package scala.tools.eclipse

import scala.tools.nsc.interactive.Global
import scala.tools.nsc.interactive
import scala.tools.nsc.doc.{ScaladocGlobalTrait => _, _}
import scala.tools.nsc.symtab.BrowsingLoaders
import scala.tools.nsc.interactive.InteractiveReporter
import scala.tools.nsc.typechecker.Analyzer
import scala.tools.nsc.interactive.CommentPreservingTypers
import scala.collection.mutable

trait InteractiveScaladocAnalyzer extends interactive.InteractiveAnalyzer with ScaladocAnalyzer {
    val global : Global
    override def newTyper(context: Context) = new Typer(context) with InteractiveTyper with ScaladocTyper {
      override def canAdaptConstantTypeToLiteral = false
    }
  }

protected class ScaladocEnabledGlobal(settings:scala.tools.nsc.Settings, compilerReporter:InteractiveReporter, name:String) extends Global(settings, compilerReporter, name) {
  override lazy val analyzer = new {
    val global: ScaladocEnabledGlobal.this.type = ScaladocEnabledGlobal.this
  } with InteractiveScaladocAnalyzer with CommentPreservingTypers
}

trait ScaladocGlobalCompatibilityTrait extends Global
   with scala.tools.nsc.doc.ScaladocGlobalTrait { outer =>


    // @see analogous member in scala.tools.nsc.interactive.Global
    override lazy val loaders = new {
    val global: outer.type = outer
    val platform: outer.platform.type = outer.platform } with BrowsingLoaders {

    // SI-5593 Scaladoc's current strategy is to visit all packages in search of user code that can be documented
    // therefore, it will rummage through the classpath triggering errors whenever it encounters package objects
    // that are not in their correct place (see bug for details)
    // (see also the symmetric comment in s.t.nsc.doc.ScaladocGlobalTrait)
    override protected def signalError(root: Symbol, ex: Throwable) {
      log(s"Suppressing error involving $root: $ex")
    }
  }
}
