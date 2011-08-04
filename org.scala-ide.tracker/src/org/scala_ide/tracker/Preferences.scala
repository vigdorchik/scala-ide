package org.scala_ide.tracker

/**
 * prefs.get(KEY1)
 * prefs.getBoolean(KEY2)
 * 
 * @author David Bernard
 * @see http://wiki.eclipse.org/FAQ_How_do_I_load_and_save_plug-in_preferences%3F
 * @see http://www.vogella.de/articles/EclipsePreferences/article.html
 */
object Preferences {
  import org.eclipse.core.runtime.preferences.ConfigurationScope
  
  val pluginId = "org.scala-ide.tracker"
  val usageReportEnabledId = "usageReportEnabled"
  val appIdId = "appId"    
    
  lazy val prefs = new ConfigurationScope().getNode(pluginId)

  def usageReportEnabled : Boolean = prefs.getBoolean(usageReportEnabledId, false)
  def usageReportRequested : Boolean = prefs.get(usageReportEnabledId, null) ne null
  def appId : String = prefs.get(appIdId, null) match {
    case null => {
      import java.util.UUID
      val uuid = UUID.randomUUID()
      val b = uuid.getMostSignificantBits + "." + uuid.getLeastSignificantBits
      prefs.put(appIdId, b)
      save()
      b
    }
    case b => b
  }
  
  /**
   * saves plugin preferences at the workspace level
   * @throws BackingStoreException
   */
  protected def save() {
    // prefs are automatically flushed during a plugin's "super.stop()".
    prefs.flush();
  }

}