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
    
  private val firstVisitId = "visit.first"
  private val lastVisitId =  "visit.last" 
  private val visitCountId =  "visit.count"
    
  val appIdId = "appId"    
    
  lazy val prefs = new ConfigurationScope().getNode(pluginId)
  private val _currentTime = String.valueOf(System.currentTimeMillis());

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
  def currentVisit = _currentTime
  def firstVisit : String = prefs.get(firstVisitId, null) match {
    case null => {
      prefs.put(Preferences.firstVisitId, _currentTime);
      _currentTime
    }
    case x => x
  }
  
  def lastVisit : String = prefs.get(lastVisitId, _currentTime)
  
  def visitCount = prefs.getLong(visitCountId, 1);
/*
  public void visit() {
    lastVisit = currentVisit;
    preferences.put(IUsageReportPreferenceConstants.LAST_VISIT, lastVisit);
    currentVisit = String.valueOf(System.currentTimeMillis());
    visitCount++;
    preferences.putLong(IUsageReportPreferenceConstants.VISIT_COUNT, visitCount);
    UsageReportPreferencesUtils.checkedSavePreferences(preferences, SubclipseToolsUsageActivator.getDefault(),
        GoogleAnalyticsEclipseMessages.EclipseEnvironment_Error_SavePreferences);
  }
*/
  /**
   * saves plugin preferences at the workspace level
   * @throws BackingStoreException
   */
  protected def save() {
    // prefs are automatically flushed during a plugin's "super.stop()".
    prefs.flush();
  }

}