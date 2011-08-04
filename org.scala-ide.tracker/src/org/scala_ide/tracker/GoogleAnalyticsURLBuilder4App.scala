package org.scala_ide.tracker

import com.dmurph.tracking.{IGoogleAnalyticsURLBuilder, AnalyticsConfigData, AnalyticsRequestData, URIEncoder}
/**
 * Similar to the original GoogleAnalyticsV4_7_2 but allow to define appId (__utma)
 * to track distinct ide/app like google analytics track distinct browser.
 *  
 * @author David Bernard
 * @based on http://code.google.com/p/jgoogleanalyticstracker/source/browse/trunk/src/main/java/com/dmurph/tracking/GoogleAnalyticsV4_7_2.java
 * @see http://code.google.com/apis/analytics/docs/tracking/gaTrackingTroubleshooting.html#gifParameters
 * @see http://code.google.com/apis/analytics/docs/concepts/gaConceptsCookies.html
 */
class GoogleAnalyticsURLBuilder4App(config : AnalyticsConfigData, appId : String) extends IGoogleAnalyticsURLBuilder {
  private val _url_prefix = "http://www.google-analytics.com/__utm.gif"
  private val _random = new scala.util.Random()

  /**
   * @see com.dmurph.tracking.IGoogleAnalyticsURLBuilder#getGoogleAnalyticsVersion()
   */
  def getGoogleAnalyticsVersion() = "4.7.2"
        
  /**
   * @see com.dmurph.tracking.IGoogleAnalyticsURLBuilder#buildURL(com.dmurph.tracking.AnalyticsRequestData)
   */
  def buildURL(argData : AnalyticsRequestData) : String = {
    val sb = new StringBuilder(_url_prefix)
                
    sb.append("?utmwv=").append(getGoogleAnalyticsVersion()) // version
    sb.append("&utmn=").append(_random.nextInt()) // random int so no caching
           
    if (argData.getHostName() != null) {
      sb.append("&utmhn=").append(getURIString(argData.getHostName())); // hostname
    }
            
    if (argData.getEventAction() != null && argData.getEventCategory() != null) {
      (sb.append("&utmt=event")
        .append("&utme=5(")
        .append(getURIString(argData.getEventCategory()))
        .append('*')
        .append(getURIString(argData.getEventAction()))
      )
      if (argData.getEventLabel() != null){
        sb.append('*').append(getURIString(argData.getEventLabel()))
      }
      sb.append(')')
                
      if (argData.getEventValue() != null){
        sb.append('(').append(argData.getEventValue()).append(')')
      }
    } else if(argData.getEventAction() != null || argData.getEventCategory() != null) {
      throw new IllegalArgumentException("Event tracking must have both a category and an action");
    }
            
    if (config.getEncoding() != null){
      sb.append("&utmcs=").append(getURIString(config.getEncoding())) // encoding
    } else {
      sb.append("&utmcs=-");
    }
    if (config.getScreenResolution() != null){
      sb.append("&utmsr=").append(getURIString(config.getScreenResolution())) // screen resolution
    }
    if (config.getColorDepth() != null){
      sb.append("&utmsc=").append(getURIString(config.getColorDepth())) // color depth
    }
    if (config.getUserLanguage() != null){
      sb.append("&utmul=").append(getURIString(config.getUserLanguage())) // language
    }
    sb.append("&utmje=1"); // java enabled (probably)
            
    if (config.getFlashVersion() != null) {
      sb.append("&utmfl=").append(getURIString(config.getFlashVersion())) // flash version
    }
            
    if(argData.getPageTitle() != null) {
      sb.append("&utmdt=").append(getURIString(argData.getPageTitle())) // page title
    }
            
    sb.append("&utmhid=").append(_random.nextInt())
            
    if (argData.getPageURL() != null) {
      sb.append("&utmp=").append(getURIString(argData.getPageURL())) // page url
    }
            
    sb.append("&utmac=").append(config.getTrackingCode()) // tracking code
            
    // cookie data
    // utmccn=(organic)|utmcsr=google|utmctr=snotwuh |utmcmd=organic
    val utmcsr = getURIString(argData.getUtmcsr())
    val utmccn = getURIString(argData.getUtmccn())
    val utmcmd = getURIString(argData.getUtmcmd())
                
    // yes, this did take a while to figure out
    val now = System.currentTimeMillis()
    (sb.append("&utmcc=__utma%3D").append(getURIString(appId))
      .append("%3B%2B__utmz%3D").append(now)
      .append(".1.1.utmcsr%3D").append(utmcsr)
      .append("%7Cutmccn%3D").append(utmccn)
      .append("%7Cutmcmd%3D").append(utmcmd)
    )
    if (argData.getUtmctr() != null) {
      sb.append("%7Cutmctr%3D").append(getURIString(argData.getUtmctr()))
    }
    if (argData.getUtmcct() != null) {
      sb.append("%7Cutmcct%3D").append(getURIString(argData.getUtmcct()))
    }
    
    sb.append("%3B&gaq=1");
    sb.toString
  }
        
  private def getURIString(v : String) : String = v match { 
    case null => null
    case str => URIEncoder.encodeURI(str)
  }
  
  /**
   * Do Nothing
   * @see com.dmurph.tracking.IGoogleAnalyticsURLBuilder#resetSession()
   */
  def resetSession() {}
}
