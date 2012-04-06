/*
 * Copyright 2012 LAMP/EPFL
 */

package scala.tools.eclipse
package util

import org.eclipse.core.runtime.Platform

trait ExtensionPointUtils {

  def discoverExtensions[T](id: String): List[T] = {
    val configs = Platform.getExtensionRegistry.getConfigurationElementsFor(id).toList

    configs map { e =>
      Utils.tryExecute {
        e.createExecutableExtension("class")
      }
    } collect {
      case Some(p: T) => p
    }
  }
}
