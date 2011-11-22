package scala.tools.eclipse
package ui

import completion._
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.eclipse.jface.text.contentassist.{ ICompletionProposalExtension, ICompletionProposalExtension3, ICompletionProposalExtension5, ICompletionProposalExtension6, IContextInformation }
import org.eclipse.swt.graphics.Image
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.viewers.{ISelectionProvider, StyledString}
import org.eclipse.jface.text.TextSelection
import org.eclipse.jface.text.ITextViewer
import org.eclipse.jdt.internal.ui.JavaPluginImages
import refactoring.EditorHelpers
import refactoring.EditorHelpers._
import scala.tools.refactoring.implementations.AddImportStatement
import org.eclipse.jdt.internal.ui.text.java.hover.JavadocBrowserInformationControlInput
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jface.text.IInformationControlCreator
import org.eclipse.jface.internal.text.html.BrowserInformationControl
import org.eclipse.jdt.internal.ui.text.java.hover.JavadocHover
import scala.tools.eclipse.util.EclipseUtils
import org.eclipse.jface.internal.text.html.HTMLPrinter


/** A UI class for displaying completion proposals.
 * 
 *  It adds parenthesis at the end of a proposal if it has parameters, and places the caret
 *  between them.
 */
class ScalaCompletionProposal(proposal: CompletionProposal, 
      selectionProvider: ISelectionProvider) 
    extends IJavaCompletionProposal 
    with ICompletionProposalExtension 
    with ICompletionProposalExtension3
    with ICompletionProposalExtension5
    with ICompletionProposalExtension6 with JavadocUtils {
  
  import proposal._
  import ScalaCompletionProposal._
  
  def getRelevance = relevance
  
  private lazy val image = {
    import MemberKind._
    
    kind match {
      case Def           => defImage
      case Class         => classImage
      case Trait         => traitImage
      case Package       => packageImage
      case PackageObject => packageObjectImage
      case Object        =>
        if (isJava) javaClassImage
        else objectImage
      case Type => typeImage
      case _    => valImage
    }
  }
  
  def getImage = image
  
  val completionString = if (hasArgs == HasArgs.NoArgs) completion else completion + "()"
  
  def getContextInformation(): IContextInformation =
    if (tooltip.size > 0)
      new ScalaContextInformation(display, tooltip, image)
    else null
 
  /**
   * A simple display string
   */
  def getDisplayString() = display
  
  /**
   * A display string with grayed out extra details
   */
  def getStyledDisplayString() : StyledString = {
       val styledString= new StyledString(display)
       if (displayDetail != null && displayDetail.size > 0)
         styledString.append(" - ", StyledString.QUALIFIER_STYLER).append(displayDetail, StyledString.QUALIFIER_STYLER)
      styledString
    }

  /** Some additional info (like javadoc ...)
   */
  def getAdditionalProposalInfo(monitor: IProgressMonitor): Object = additionalInfoBuilder();

  def additionalInfoBuilder(): JavadocBrowserInformationControlInput = {
    import org.eclipse.jdt.internal.ui.text.javadoc.JavadocContentAccess2
    val buffer = new StringBuffer();
    HTMLPrinter.insertPageProlog(buffer, 0, styleSheet);

    buffer.append(docString().getOrElse(""))

    HTMLPrinter.addPageEpilog(buffer);
    return new JavadocBrowserInformationControlInput(null, null, buffer.toString, 0);
  }
  
  def getAdditionalProposalInfo() = {
    val res = additionalInfoBuilder()
    if (res != null) res.toString else "No Proposal Info"
  }

  def getInformationControlCreator(): IInformationControlCreator =
    informationControlCreator

  lazy val informationControlCreator = {
    import org.eclipse.jdt.internal.ui.JavaPlugin;
    val shell = JavaPlugin.getActiveWorkbenchShell();
    if (!BrowserInformationControl.isAvailable(shell))
      null
    else {
      val presenterControlCreator = new JavadocHover.PresenterControlCreator(EclipseUtils.getWorkbenchSite);
      new JavadocHover.HoverControlCreator(presenterControlCreator, true);
    }
  }
  
  def getPrefixCompletionText(document : IDocument, completionOffset : Int) : CharSequence = ""
      
  def getPrefixCompletionStart(document : IDocument, completionOffset : Int) : Int = 
    startPos;
      
  
  def getSelection(d: IDocument) = null
  def apply(d: IDocument) { throw new IllegalStateException("Shouldn't be called") }

  def apply(d: IDocument, trigger: Char, offset: Int) {
    d.replace(startPos, offset - startPos, completionString)
    selectionProvider.setSelection(new TextSelection(startPos + completionString.length, 0))
    selectionProvider match {
      case viewer: ITextViewer if hasArgs == HasArgs.NonEmptyArgs =>
        // obtain the relative offset in the screen (this is needed to correctly 
        // update the caret position when folded comments/imports/classes are
        // present in the source file.
        val viewCaretOffset = viewer.getTextWidget().getCaretOffset()
        viewer.getTextWidget().setCaretOffset(viewCaretOffset -1 )
      case _ => () 
    }
    if (needImport) { // add an import statement if required
      // [luc] code copied from scala.tools.eclipse.quickfix.ImportCompletionProposal
      withScalaFileAndSelection { (scalaSourceFile, textSelection) =>
        val changes = scalaSourceFile.withSourceFile { (sourceFile, compiler) =>
          val refactoring = new AddImportStatement { val global = compiler }
          refactoring.addImport(scalaSourceFile.file, fullyQualifiedName)
        }(Nil)
        EditorHelpers.applyChangesToFileWhileKeepingSelection(d, textSelection, scalaSourceFile.file, changes)
        None
      }
    }
  }
  def getTriggerCharacters = null
  def getContextInformationPosition = 0
  def isValidFor(d: IDocument, pos: Int) = 
    prefixMatches(completion.toArray, d.get.substring(startPos, pos).toArray)
}

object ScalaCompletionProposal {
  import ScalaImages._
  val defImage = PUBLIC_DEF.createImage()
  val classImage = SCALA_CLASS.createImage()
  val traitImage = SCALA_TRAIT.createImage()
  val objectImage = SCALA_OBJECT.createImage()
  val packageObjectImage = SCALA_PACKAGE_OBJECT.createImage()
  val typeImage = SCALA_TYPE.createImage()
  val valImage = PUBLIC_VAL.createImage()

  val javaInterfaceImage = JavaPluginImages.get(JavaPluginImages.IMG_OBJS_INTERFACE)
  val javaClassImage = JavaPluginImages.get(JavaPluginImages.IMG_OBJS_CLASS)
  val packageImage = JavaPluginImages.get(JavaPluginImages.IMG_OBJS_PACKAGE)
  
  def apply(selectionProvider: ISelectionProvider)(proposal: CompletionProposal) = new ScalaCompletionProposal(proposal, selectionProvider)
}