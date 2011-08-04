package scala.tools.nsc
package interactive.compat

import _root_.scala.tools.nsc.{Settings => MainSettings}

class Settings(errorFn: String => Unit) extends MainSettings(errorFn) {

    
}

object Info {
  val scalaVersion = "2.8.2"
}
