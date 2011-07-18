package scaladoc

import scala.collection.mutable.ArrayBuffer;

object AClass {
  
  val anArrayBuffer = new ArrayBuffer/*<1*/[String]
  val size = anArrayBuffer.size/*<2*/
  anArrayBuffer.remove/*<3*/(0)
  anArrayBuffer.sizeHint/*<4*/(2)
} 

/**
 * Documentation of a class.
 */
class ADocumentedClass {
  
  var aField : ADocumentedClass/*<5*/ = null;
  
  /** Documentation of a method.
   *  
   *  @param i      the index.
   *  @return   	a return.
   *  @throws AnException.
   */
  def aDocumentedMethod(i : Int) : String = ""
    
  aDocumentedMethod/*<6*/(0)  
}