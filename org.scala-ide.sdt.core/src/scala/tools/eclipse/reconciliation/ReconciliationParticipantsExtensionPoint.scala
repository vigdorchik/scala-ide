/*
 * Copyright 2005-2011 LAMP/EPFL
 */
package scala.tools.eclipse
package reconciliation

import org.eclipse.core.runtime.CoreException
import org.eclipse.core.runtime.{Platform, IProgressMonitor}
import org.eclipse.jdt.core.WorkingCopyOwner
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.util.Utils
import scala.tools.eclipse.util.ExtensionPointUtils

/**
 * The implementation for the org.scala-ide.sdt.core.reconciliationParticipants
 * extension points which gets the registered extensions and invokes them.
 * 
 * The runBefore and runAfter methods are themselves invoked by the 
 * ScalaSourceFile.reconcile method.
 * 
 * @author Mirko Stocker
 */
object ReconciliationParticipantsExtensionPoint extends ExtensionPointUtils with HasLogger {
  
  final val PARTICIPANTS_ID = "org.scala-ide.sdt.core.reconciliationParticipants"
    
  lazy val extensions = discoverExtensions[ReconciliationParticipant](PARTICIPANTS_ID)
  
  def runBefore(scu: ScalaCompilationUnit, monitor: IProgressMonitor, workingCopyOwner: WorkingCopyOwner) {
    extensions foreach { extension =>
      Utils.tryExecute {
        extension.beforeReconciliation(scu, monitor, workingCopyOwner)
      }
    }
  }
  
  def runAfter(scu: ScalaCompilationUnit, monitor: IProgressMonitor, workingCopyOwner: WorkingCopyOwner) {
    extensions foreach { extension =>
      Utils.tryExecute {
        extension.afterReconciliation(scu, monitor, workingCopyOwner)
      }
    }
  }
}
