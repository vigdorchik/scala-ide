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

    val tracker = new GoogleAnalyticsTracker()
    val ctx = new RequestContext4Eclipse(gaTrackerId)

    val pageURL = String.format("/jdt/%s/scala/%s/sdt/%s", toMajorMinorMicro(jdtVersion), toMajorMinorMicro(scalaVersion), toMajorMinorMicro(sdtVersion))
    val pageTitle = "scala-ide config"
    val hostName = "tracker.scala-ide.org"
    tracker.trackPageView(ctx, PageInfo(hostName, pageURL, pageTitle))

    def trackVersion(label : String, version : Option[Version]) =  version.foreach{ v => tracker.trackEvent(ctx, EventInfo("version", label, Option(v.toString))) }
    trackVersion("jdt", jdtVersion)
    trackVersion("scala", scalaVersion)
    trackVersion("sdt", sdtVersion)
    trackVersion("m2e", m2eVersion)
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
