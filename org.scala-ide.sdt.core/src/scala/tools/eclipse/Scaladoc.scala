/*
 * Copyright 2005-2013 LAMP/EPFL
 * @author Eugene Vigdorchik
 */
// $Id$

package scala.tools.eclipse

import scala.tools.nsc.doc.{Settings => DocSettings}
import scala.tools.nsc.doc.html.HtmlPage
import scala.tools.nsc.doc.base._
import scala.tools.nsc.doc.base.comment.Comment
import scala.tools.nsc.util.SourceFile
import org.eclipse.jface.internal.text.html.BrowserInformationControlInput
import scala.xml.{NodeSeq, Text}
import scala.xml.dtd.{DocType, PublicID}
import scala.beans.BeanProperty

trait Scaladoc extends MemberLookupBase with CommentFactoryBase { this: ScalaPresentationCompiler =>
  val global: this.type = this
  import global._

  def chooseLink(links: List[LinkTo]) = links.head

  override def internalLink(sym: Symbol, site: Symbol): Option[LinkTo] = {
    assert(onCompilerThread, "!onCompilerThread")
    if (sym.isClass || sym.isModule)
      Some(LinkToTpl(sym))
    else
      if ((site.isClass || site.isModule) && site.info.members.toList.contains(sym))
        Some(LinkToMember(sym, site))
      else None
  }

  override def toString(link: LinkTo) = {
    assert(onCompilerThread, "!onCompilerThread")
    link match {
      case LinkToMember(mbr: Symbol, site: Symbol) =>
        mbr.signatureString + " in " + site.toString
      case LinkToTpl(sym: Symbol) => sym.toString
      case _ => link.toString
    }
  }

  override def warnNoLink = false
  override def findExternalLink(sym: Symbol, name: String) = None

  def documentation(sym: Symbol, site: =>Symbol, header: String = ""): Option[BrowserInput] = {
    logger.info("Computing documentation for: " + sym)

    val res =
      for (u <- findCompilationUnit(sym)) yield withSourceFile(u) { (source, _) =>
        def withFragments(syms: List[Symbol], fragments: List[(Symbol, SourceFile)]): Option[(String, String, Position)] =
          syms match {
            case Nil =>
              val response = new Response[(String, String, Position)]
              askDocComment(sym, source, site, fragments, response)
              response.get.left.toOption
            case s :: rest =>
              findCompilationUnit(s) match {
                case None =>
                  withFragments(rest, fragments)
                case Some(u) =>
                  withSourceFile(u) { (source, _) =>
                    withFragments(rest, (s,source)::fragments)
                  }
              }
          }

        askOption {
          () => sym::site::sym.allOverriddenSymbols:::site.baseClasses
        } flatMap { fragments =>
          withFragments(fragments, Nil) flatMap {
            case (expanded, raw, pos) if !expanded.isEmpty =>
              askOption { () => parseAtSymbol(expanded, raw, pos, Some(site)) }
            case _ =>
              None
          }
        }
      }

    logger.info("retrieve documentation result: " + res)

    askOption { () =>
      res.flatten map (HtmlProducer(_, sym, header))
    } flatten
  }

  object HtmlProducer extends HtmlPage {
    // Abstract methods from base not used.
    def title = ""
    def headers = NodeSeq.Empty
    def body = NodeSeq.Empty
    def path = List()

    def apply(comment: Comment, sym: Symbol, header: String) = {
      val doctype =
        DocType("html", PublicID("-//W3C//DTD XHTML 1.1//EN", "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd"), Nil)
      val html =
        <HTML>
           { htmlContents(header, comment) }
        </HTML>
      new BrowserInput(doctype.toString + "\n" + html.toString, sym)
    }

    def htmlContents(header: String, comment: Comment) = {
      val headerHtml = 
        if (header.isEmpty) NodeSeq.Empty
        else
          <div class="block"><ol><b>{ header }</b></ol></div>

      def orEmpty[T](it: Iterable[T])(gen:  =>NodeSeq): NodeSeq =
        if (it.isEmpty) NodeSeq.Empty else gen

       val example =
        orEmpty(comment.example) {
          <div class="block">Example{ if (comment.example.length > 1) "s" else ""}:
             <ol>{
               val exampleXml: List[NodeSeq] = for (ex <- comment.example) yield
                 <li class="cmt">{ bodyToHtml(ex) }</li>
               exampleXml.reduceLeft(_ ++ Text(", ") ++ _)
            }</ol>
          </div>
        }

      val version: NodeSeq =
        orEmpty(comment.version) {
          <dt>Version</dt>
          <dd>{ for(body <- comment.version.toList) yield bodyToHtml(body) }</dd>
        }

      val sinceVersion: NodeSeq =
        orEmpty(comment.since) {
          <dt>Since</dt>
          <dd>{ for(body <- comment.since.toList) yield bodyToHtml(body) }</dd>
        }

      val note: NodeSeq =
        orEmpty(comment.note) {
          <dt>Note</dt>
          <dd>{
            val noteXml: List[NodeSeq] =  for(note <- comment.note ) yield <span class="cmt">{bodyToHtml(note)}</span>
            noteXml.reduceLeft(_ ++ Text(", ") ++ _)
          }</dd>
        }

      val seeAlso: NodeSeq =
        orEmpty(comment.see) {
          <dt>See also</dt>
          <dd>{
            val seeXml: List[NodeSeq] = for(see <- comment.see ) yield <span class="cmt">{bodyToHtml(see)}</span>
            seeXml.reduceLeft(_ ++ _)
          }</dd>
        }

      val exceptions: NodeSeq =
        orEmpty(comment.throws) {
          <dt>Exceptions thrown</dt>
          <dd>{
            val exceptionsXml: List[NodeSeq] =
              for((name, body) <- comment.throws.toList.sortBy(_._1) ) yield
                <span class="cmt">{Text(name) ++ bodyToHtml(body)}</span>
            exceptionsXml.reduceLeft(_ ++ Text("") ++ _)
          }</dd>
        }

      val todo: NodeSeq =
        orEmpty(comment.todo) {
          <dt>To do</dt>
          <dd>{
            val todoXml: List[NodeSeq] = (for(todo <- comment.todo ) yield <span class="cmt">{bodyToHtml(todo)}</span> )
            todoXml.reduceLeft(_ ++ Text(", ") ++ _)
          }</dd>
        }

      <BODY> { headerHtml ++ (commentToHtml(comment) +: <br/>) ++ example ++ version ++ sinceVersion ++ exceptions ++ todo ++ note ++ seeAlso } </BODY>
    }
  }

  class BrowserInput(@BeanProperty val html: String,
                     @BeanProperty val inputElement: Object,
                     @BeanProperty val inputName: String) extends BrowserInformationControlInput(null) {
    def this(html: String, sym: Symbol) = this(html, getJavaElement(sym).orNull, sym.toString)
  }
}
