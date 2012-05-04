/*
 * Copyright 2011 LAMP/EPFL
 */

package scala.tools.eclipse
package codeanalysis.quickfixes

import org.eclipse.core.resources.IMarker
import org.eclipse.ui.texteditor.AbstractMarkerAnnotationModel

import scala.tools.eclipse.refactoring.EditorHelpers
import scala.tools.eclipse.ScalaSourceFileEditor
import org.eclipse.ui.texteditor.IDocumentProvider

object MarkerUtil {

  def getOffsetAndLengthFromMarker(marker: IMarker): Option[(Int, Int)] = {
    
    getDocumentAndPosFromMarker(marker) map {
      case (_, pos) =>
        (pos.getOffset -> (pos.getOffset + pos.getLength))
    } orElse {
      marker.getAttribute(IMarker.CHAR_START) -> marker.getAttribute(IMarker.CHAR_END) match {
        case (start: java.lang.Integer, end: java.lang.Integer) => Some(start, end)
        case _ => None
      }
    }  
  }
  
  def getLineNumberFromMarker(marker: IMarker): Option[Int] = {
    
    getDocumentAndPosFromMarker(marker) map {
      case (doc, pos) => doc.getLineOfOffset(pos.getOffset) + 1
    } orElse {
      marker.getAttribute(IMarker.LINE_NUMBER) match {
        case i: java.lang.Integer => Some(i)
        case _ => None
      }
    }
  }
  
  private def getDocumentAndPosFromMarker(marker: IMarker) = {
    EditorHelpers.withCurrentEditor { editor =>
      Option(editor.getDocumentProvider) flatMap { documentProvider =>
        documentProvider.getAnnotationModel(editor.getEditorInput) match {
          case model: AbstractMarkerAnnotationModel =>
            Option(model.getMarkerPosition(marker)) filterNot (_.isDeleted) map { pos =>
              documentProvider.getDocument(editor.getEditorInput) -> pos
            }
          case _ => None
        }
      }
    }
  }
}