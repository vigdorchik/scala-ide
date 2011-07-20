package scala.tools.eclipse.scaladoc

import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jface.internal.text.html.HTMLPrinter
import scala.tools.eclipse.ScalaPresentationCompiler
import scala.tools.eclipse.ScalaImages
import scala.tools.nsc.doc.html.HtmlPage
import scala.xml.NodeSeq
import scala.tools.nsc.doc.model.TreeFactory
import scala.tools.nsc.doc.model.comment.Body
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import org.eclipse.jdt.internal.ui.text.java.hover.JavadocHover
import scala.tools.nsc.doc.model.ModelFactory
import scala.tools.nsc.doc.model.comment.CommentFactory

trait ScaladocCommentsToEclipseHtmlTransformer { 
  import scala.tools.nsc.doc.Settings
  
  val compiler : ScalaPresentationCompiler;
  
  import compiler._
  
  lazy val clsImage = JavaPlugin.getDefault().getImagesOnFSRegistry().getImageURL(ScalaImages.SCALA_CLASS)
  lazy val objImage = JavaPlugin.getDefault().getImagesOnFSRegistry().getImageURL(ScalaImages.SCALA_OBJECT)

  val comment2HTMLBuilder = new HtmlPage {
    val path = List("")
    val title = ""  
	val headers = NodeSeq.Empty
	val body = NodeSeq.Empty			        	
  }
  
  val commentFactory = new ModelFactory(compiler, new Settings({ e: String => })) with CommentFactory with TreeFactory {

    def scalaDocComment2Html(expanded: String, raw: String, pos: Position, symbol: Symbol): String = {
      val sb = new StringBuffer()
      // transforms "<p>something</p>" into "something"
      def removeParagraph(html: NodeSeq) =
        html.head.child.foldLeft("")((x, y) => x + " " + y.toString)

      def addParList(title: String, parNames2Explanation: scala.collection.Map[String, Body]) {
        HTMLPrinter.addSmallHeader(sb, title)
        HTMLPrinter.startBulletList(sb);
        parNames2Explanation.foreach(entry =>
          HTMLPrinter.addBullet(sb, entry._1 + " - " +
            removeParagraph(comment2HTMLBuilder.bodyToHtml(entry._2))))
        HTMLPrinter.endBulletList(sb);
      }

      val comment = parse(expanded, raw, pos)
      sb.append(comment2HTMLBuilder.commentToHtml(comment).toString)
      if (comment.valueParams.size > 0)
        addParList("Parameters:", comment.valueParams)
      if (comment.typeParams.size > 0)
        addParList("Type Parameters:", comment.typeParams)
      comment.result match {
        case Some(body) =>
          HTMLPrinter.addSmallHeader(sb, "Returns:")
          HTMLPrinter.startBulletList(sb);
          HTMLPrinter.addBullet(sb,
            removeParagraph(comment2HTMLBuilder.bodyToHtml(body)))
          HTMLPrinter.endBulletList(sb);
        case _ =>
      }
      if (comment.throws.size > 0)
        addParList("Throws:", comment.throws)
      sb.toString
    }
  }
  
  def buildCommentAsHtml(scu : ScalaCompilationUnit, sym : Symbol, tpe : compiler.Type): StringBuffer = {    
    val buffer= new StringBuffer();    
    if (sym.isClass || sym.isModule) {
      val image = if (sym.isClass) clsImage.toExternalForm
      			  else objImage.toExternalForm
      JavadocHover.addImageAndLabel(buffer, image,
        16, 16, sym.fullName, 20, 2);
    } else
      HTMLPrinter.addSmallHeader(buffer, askOption(() => compiler.defString(sym, tpe)).getOrElse(""))

    val comment = compiler.extractComment(sym, scu)
    buffer.append(commentFactory.scalaDocComment2Html(
        comment._1, 
        comment._2, sym.pos, sym))
    buffer
  }
}
