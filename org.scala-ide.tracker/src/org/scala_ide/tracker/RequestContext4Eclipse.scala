package org.scala_ide.tracker

import org.eclipse.swt.graphics.Rectangle
import org.eclipse.swt.widgets.Display
import org.eclipse.ui.PlatformUI
import org.scala_ide.tracker.ga_eclipse.EclipseUserAgent
import org.scala_ide.tracker.ga_eclipse.LinuxSystem

class RequestContext4Eclipse(val accountName : String) extends RequestContext {
  
  private var _screenResolution = ""
  private var _screenColorDepth = 0

  private lazy val _eclipseUserAgent = new EclipseUserAgent()
  
  def referral : String = GoogleAnalyticsConstantes.VALUE_NO_REFERRAL
  def screenResolution : String = _screenResolution
  def screenColorDepth : Int = _screenColorDepth
  def browserLanguage : String = _eclipseUserAgent.getBrowserLanguage()
  def flashVersion : String = System.getProperty("java.version") // use the flash version to store java version
  def userId : String = Preferences.appId
  def userAgent : String = _eclipseUserAgent.toString()
 
  def firstVisit : String = Preferences.firstVisit
  def lastVisit : String = Preferences.lastVisit
  def currentVisit : String = Preferences.currentVisit
  def visitCount : Long = Preferences.visitCount
  
  def userDefined : String = LinuxSystem.INSTANCE.getDistroNameAndVersion()
  def keyword : String = "scala-ide"


  private def initScreenSettings() {
    def getDisplay() : Display = {
      if (PlatformUI.isWorkbenchRunning()) {
        return PlatformUI.getWorkbench().getDisplay();
      }

      var display = Display.getCurrent()
      if (display == null) {
        display = Display.getDefault()
      }
      display
    }
    val display = getDisplay()
    display.syncExec(new Runnable() {
      def run() {
        _screenColorDepth = display.getDepth()
        val bounds = display.getBounds()
        _screenResolution = bounds.width + GoogleAnalyticsConstantes.SCREERESOLUTION_DELIMITER + bounds.height;
      }
    })
  }

  // constructor
  try {
    initScreenSettings()
  } catch {
    case t => throw t
  }
}
