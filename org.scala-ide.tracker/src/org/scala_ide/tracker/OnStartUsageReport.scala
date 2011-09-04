/*******************************************************************************
 * Copyright (c) 2011 scala-ide project and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *     
 ******************************************************************************/
package org.scala_ide.tracker

import org.osgi.framework.{BundleContext, Version}
import scala.tools.eclipse.ext.OnStart

/**
 * @author David Bernard
 */
object OnStartUsageReport {

  /**
   * Tracking with JGoogleAnalyticsTracker
   * @see http://code.google.com/p/jgoogleanalyticstracker/
   */
  def sendTrackingInfo(gaTrackerId : String, appId : String, jdtVersion : Option[Version], scalaVersion : Option[Version], sdtVersion : Option[Version], m2eVersion : Option[Version]) {
    def toMajorMinorMicro(v : Option[Version]) = v.map(x => x.getMajor + "." + x.getMinor + "." + x.getMicro).getOrElse("undef")
    
    //JGoogleAnalyticsTracker.setProxy(System.getenv("http_proxy"))
    //val config = new AnalyticsConfigData(gaTrackerId)
    //val tracker = new JGoogleAnalyticsTracker(config, GoogleAnalyticsVersion.V_4_7_2, JGoogleAnalyticsTracker.DispatchMode.SYNCHRONOUS)
    // HACK to be able to use My custom UrlBuilder that take care of appId
    //val builder2 = new GoogleAnalyticsURLBuilder4App(config, appId)
    //val privateBuilderField = classOf[JGoogleAnalyticsTracker].getDeclaredField("builder")
    //privateBuilderField.setAccessible(true)
    //privateBuilderField.set(tracker, builder2)
    
    val tracker = new GoogleAnalyticsTracker()
    val ctx = new RequestContext4Eclipse(gaTrackerId)
    val pageURL = String.format("/jdt/%s/scala/%s/sdt/%s", toMajorMinorMicro(jdtVersion), toMajorMinorMicro(scalaVersion), toMajorMinorMicro(sdtVersion))
    val pageTitle = "scala-ide config"
    val hostName = "tracker.scala-ide.org"
    tracker.trackPageView(ctx, hostName, pageURL, pageTitle)
/*    
    tracker.trackPageViewFromReferrer(pageURL, pageTitle, hostName, null, null)
    //println("tracker.trackPageView", pageURL, pageTitle, hostName)
    jdtVersion.foreach{ v => tracker.trackEvent("version", "jdt", v.toString) }
    scalaVersion.foreach{ v => tracker.trackEvent("version", "scala", v.toString) }
    sdtVersion.foreach{ v => tracker.trackEvent("version", "sdt", v.toString) }
    m2eVersion.foreach{ v => tracker.trackEvent("version", "m2e", v.toString) }
*/    
  }
  
  def main(args: Array[String]) {
	 sendTrackingInfo("UA-24487935-1", "test", Option(new Version("0.0.0.jdtVersion")), Option(new Version("0.0.0.scalaVersion")), Option(new Version("0.0.0.sdtVersion")), Option(new Version("0.0.0.m2eVersion")))
	 println("DONE")
  }
}

class OnStartUsageReport extends OnStart {
  final val HEADLESS_TEST  = "sdtcore.headless"
    
  def started(bc : BundleContext) {
    //org.slf4j.impl.OSGILogFactory.initOSGI(bc);
    //retrieve preferences about send info
    // ask user if no configuration
    // if ok retrieve info + send info
    if (!Preferences.usageReportRequested && (System.getProperty(HEADLESS_TEST) eq null)) {
      //TODO popup a dialog with PreferencesPage
    }
    if (Preferences.usageReportEnabled) {
      sendTrackingInfo("UA-24487935-1", Preferences.appId, bc)
    }
  }
  
  
  /**
   * Tracking with JGoogleAnalyticsTracker
   * @see http://code.google.com/p/jgoogleanalyticstracker/
   * @return
   */
  private def sendTrackingInfo(gaTrackerId : String, appId : String, bc : BundleContext) {
    def findVersion(symbolicName : String) : Option[Version] = {
      bc.getBundles.find(_.getSymbolicName == symbolicName).map(_.getVersion)
    }
    val jdtVersion = findVersion("org.eclipse.jdt")
    val scalaVersion = findVersion("org.scala-ide.scala.compiler")
    val sdtVersion = findVersion("org.scala-ide.sdt.core")
    val m2eVersion = findVersion("org.eclipse.m2e.core")
    OnStartUsageReport.sendTrackingInfo(gaTrackerId, appId, jdtVersion, scalaVersion, sdtVersion, m2eVersion)
  }
  
}
