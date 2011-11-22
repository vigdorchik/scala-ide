package scala.tools.eclipse.completion
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.ScalaPresentationCompiler
import scala.tools.nsc.util.SourceFile
import org.eclipse.jdt.core.search.{SearchEngine, IJavaSearchConstants, SearchPattern, TypeNameRequestor}
import org.eclipse.jdt.core.IJavaElement
import scala.collection.mutable
import org.eclipse.core.runtime.NullProgressMonitor
import scala.tools.eclipse.util.HasLogger
import scala.tools.eclipse.scaladoc.ScaladocCommentsToEclipseHtmlTransformer
import org.eclipse.jdt.internal.ui.text.javadoc.JavadocContentAccess2
import org.eclipse.jdt.internal.core.JavaElement
import org.eclipse.jdt.core.IMember

/** Base class for Scala completions. No UI dependency, can be safely used in a
 *  headless testing environment.
 *  
 *  @see scala.tools.eclipse.ui.ScalaCompletinProposalComputer
 */
class ScalaCompletions extends HasLogger {
  import org.eclipse.jface.text.IRegion
  
  def findCompletions(region: IRegion)(position: Int, scu: ScalaCompilationUnit)
                             (sourceFile: SourceFile, comp: ScalaPresentationCompiler): List[CompletionProposal] = {
    val converter = new ScaladocCommentsToEclipseHtmlTransformer { val compiler: comp.type = comp }

    /** Return the scaladoc/javadoc string for the given symbol and type, if found. 
     *  Since this may be a long-running operation, we return a thunk.
     */
    def docString(sym: comp.Symbol, tpe: comp.Type)(): Option[String] = {
      if (sym.isJavaDefined) {
        comp.getJavaElement2(sym) match {
          case Some(element) =>
            javaDocString(element)().orElse(Some(converter.buildCommentAsHtml(scu, sym, tpe).toString))
          case _ =>
            logger.info("getJavaElement2 did not find the corresponding Java element for symbol " + sym.fullName)
            None
        }
      } else Some(converter.buildCommentAsHtml(scu, sym, tpe).toString)
    }

    /** Return the Javadoc for the given Java element.
     *  Since this is a long-running operation, we return a thunk.
     */
    def javaDocString(element: IMember)() = {
      val info = JavadocContentAccess2.getHTMLContent(element, true) //element.getAttachedJavadoc(null)         
      if (info != null && info.length() > 0)
        Some(info)
      else
        None
    }
    
    val pos = comp.rangePos(sourceFile, position, position, position)
    
    val start = if (region == null) position else region.getOffset
    
    val typed = new comp.Response[comp.Tree]
    comp.askTypeAt(pos, typed)
    val t1 = typed.get.left.toOption

    val completed = new comp.Response[List[comp.Member]]
    // completion depends on the typed tree
    t1 match {
      // completion on select
      case Some(s@comp.Select(qualifier, name)) if qualifier.pos.isDefined && qualifier.pos.isRange =>
        val cpos0 = qualifier.pos.end 
        val cpos = comp.rangePos(sourceFile, cpos0, cpos0, cpos0)
        comp.askTypeCompletion(cpos, completed)
      case Some(comp.Import(expr, _)) =>
        // completion on `imports`
        val cpos0 = expr.pos.endOrPoint
        val cpos = comp.rangePos(sourceFile, cpos0, cpos0, cpos0)
        comp.askTypeCompletion(cpos, completed)
      case _ =>
        // this covers completion on `types`
        val cpos = comp.rangePos(sourceFile, start, start, start)
        comp.askScopeCompletion(cpos, completed)
    }
    
    val prefix = (if (position <= start) "" else scu.getBuffer.getText(start, position-start).trim).toArray
    
    def nameMatches(sym : comp.Symbol) = prefixMatches(sym.decodedName.toString.toArray, prefix)
    
    val buff = new mutable.ListBuffer[CompletionProposal]
    
    def alreadyListed(fullyQualifiedName: String) = buff.exists((completion) => fullyQualifiedName.equals(completion.fullyQualifiedName))

    for (completions <- completed.get.left.toOption) {
      comp.askOption { () =>
        for (completion <- completions) {
          completion match {
            case comp.TypeMember(sym, tpe, accessible, inherited, viaView) if !sym.isConstructor && nameMatches(sym) =>
              val completionProposal= comp.mkCompletionProposal(start, sym, tpe, inherited, viaView, docString(sym, tpe))
              if (!alreadyListed(completionProposal.fullyQualifiedName))
                buff += completionProposal
            case comp.ScopeMember(sym, tpe, accessible, _) if !sym.isConstructor && nameMatches(sym) =>
              val completionProposal= comp.mkCompletionProposal(start, sym, tpe, false, comp.NoSymbol, docString(sym, tpe))
              if (!alreadyListed(completionProposal.fullyQualifiedName))
              	buff += completionProposal
            case _ =>
          }
        }
      }
    }
    
    // try to find a package name prefixing the word being completed
    val packageName= t1 match {
      case Some(e) if e.pos.isDefined && position > e.pos.startOrPoint =>
        // some tree, not empty
        val length= position - e.pos.startOrPoint
        // get the text of the tree element, with all white spaces removed
        var content= sourceFile.content.slice(e.pos.startOrPoint, position).filterNot((c) => c.isWhitespace)
        // check if it may look like a qualified type reference
        if (length > prefix.length + 1 && content.find((c) => !c.isUnicodeIdentifierPart && c != '.') == None)
          // extract the package qualifier
          content.slice(0, content.length - prefix.length - 1)
        else
          null
      case _ => null
    }
    
    logger.info("Search for: " + (if (packageName == null) "null" else new String(packageName)) + " . " + new String(prefix))
    
    if (prefix.length > 0 || packageName != null) {
      // if there is data to work with, look for a type in the classpath

      // the requestor will receive the search results
      val requestor= new TypeNameRequestor() {
        override def acceptType(modifiers: Int, packageNameArray: Array[Char], simpleTypeName: Array[Char], enclosingTypeName: Array[Array[Char]], path: String) {
	      val packageName= new String(packageNameArray)
	      val simpleName= new String(simpleTypeName)
	      val fullyQualifiedName= (if (packageName.length > 0) packageName + '.' else "") + simpleName
	      
	      logger.info("Found type: " + fullyQualifiedName)

	      if (!alreadyListed(fullyQualifiedName)) {
	        logger.info("Adding type: " + fullyQualifiedName)
            // if the type is not already in the completion list, add it
	        buff+= CompletionProposal(
	            MemberKind.Object,
	            start,
	            simpleName,
	            simpleName,
	            "",
	            packageName,
	            50,
	            HasArgs.NoArgs,
	            true,
	            javaDocString(scu.getJavaProject().findType(fullyQualifiedName)),
	            fullyQualifiedName,
	            true)
	      }
        }
      }
      
      // launch the JDT search, for a type in the package, starting with the given prefix
      new SearchEngine().searchAllTypeNames(
          packageName,
          SearchPattern.R_EXACT_MATCH,
          prefix,
          SearchPattern.R_PREFIX_MATCH,
          IJavaSearchConstants.TYPE,
          SearchEngine.createJavaSearchScope(Array[IJavaElement](scu.getJavaProject()), true),
          requestor,
          IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH, // wait until all types are indexed by the JDT
          null)
          
    }
    
    buff.toList
  }
}