/*
 * Copyright 2011 LAMP/EPFL
 */

package scala.tools.eclipse
package codeanalysis.quickfixes

import org.eclipse.core.resources.{IMarker, IFile}
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.internal.ui.JavaPluginImages
import org.eclipse.ui.{IMarkerResolutionGenerator, IMarkerResolution2}

import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.refactoring.EditorHelpers
import scala.tools.refactoring.common.InteractiveScalaCompiler
import scala.tools.refactoring.implementations.EliminateMatch

abstract class AbstractMarkerResolution(val marker: IMarker) extends IMarkerResolution2 {
  
  def getImage = JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE)
  
  def getDescription = null
  
  def getLabel = marker.getAttribute(IMarker.MESSAGE, "<could not find marker message>")
}
