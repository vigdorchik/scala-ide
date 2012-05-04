/*
 * Copyright 2011 LAMP/EPFL
 */

package scala.tools.eclipse
package codeanalysis.quickfixes

import org.eclipse.core.resources.IMarker
import org.eclipse.ui.IMarkerResolutionGenerator
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.refactoring.EditorHelpers
import scala.tools.refactoring.common.{TextChange, Selections, InteractiveScalaCompiler}
import org.eclipse.jface.text.TextSelection

class UnnamedBooleanArgumentMarkerResolutionGenerator extends IMarkerResolutionGenerator {

  def getResolutions(marker: IMarker) = {

    val resolution = new AbstractMarkerResolution(marker) {
      def run(marker: IMarker) {
        val scalaSourceFile = ScalaSourceFile.createFromPath(marker.getResource.getFullPath.toString)

        scalaSourceFile foreach { scalaSourceFile =>

          scalaSourceFile.doWithSourceFile { (sourceFile, compiler) =>

            // We use the Scala Refactoring's Selections class to query the AST for the selected
            // method call.
            val r = new Selections with InteractiveScalaCompiler { val global = compiler }

            val astSelection = MarkerUtil.getOffsetAndLengthFromMarker(marker) match {
              case Some(Pair(start, end)) =>
                new r.FileSelection(sourceFile.file, r.global.body(sourceFile), start, end)
              case _ =>
                throw new Exception("Could not get line number from marker, please file a bug report")
            }

            val parameterName = astSelection.findSelectedOfType[r.global.Apply] collect {
              case r.global.Apply(fun, args) =>
                fun.tpe.params zip args collect {
                  case (param, arg) if arg.pos == astSelection.pos => param.nameString
                } headOption
            } getOrElse {
              throw new Exception("Could not find the boolean argument's name, please file a bug report")
            }

            EditorHelpers.doWithCurrentEditor { editor =>

              import astSelection.pos
              
              val currentEditorSelection = EditorHelpers.selection(editor).getOrElse(TextSelection.emptySelection)
              val document = editor.getDocumentProvider.getDocument(editor.getEditorInput)
              val file = pos.source.file
              val change = new TextChange(pos.source, pos.start, pos.start,  parameterName +" = ")

              EditorHelpers.applyChangesToFileWhileKeepingSelection(document, currentEditorSelection, file, List(change))

              marker.delete()
            }
          }
        }
      }
    }

    Array(resolution)
  }
}
