package org.scala_ide.tracker

import java.net.URL
import java.net.HttpURLConnection
import java.net.URLEncoder

object GoogleAnalyticsConstantes {
  val URL_BASE = "http://www.google-analytics.com/__utm.gif?"
    
  val PARAM_HID = "utmhid"
	val PARAM_PAGE_REQUEST = "utmp"
	val PARAM_ACCOUNT_NAME = "utmac"
	val PARAM_HOST_NAME = "utmhn"
	val PARAM_COOKIES = "utmcc"
	val PARAM_COOKIES_UNIQUE_VISITOR_ID = "__utma"
	val PARAM_COOKIES_SESSION = "__utmb"
	val PARAM_COOKIES_BROWSERSESSION = "__utmc"
	val PARAM_COOKIES_REFERRAL_TYPE = "__utmz"
	val PARAM_COOKIES_UTMCSR = "utmcsr"
	val PARAM_COOKIES_UTMCCN = "utmccn"
	val PARAM_COOKIES_UTMCMD = "utmcmd"
	val PARAM_COOKIES_KEYWORD = "utmctr"
	val PARAM_COOKIES_USERDEFINED = "__utmv"

	val PARAM_REFERRAL = "utmr"
	val PARAM_TRACKING_CODE_VERSION = "utmwv"
	val PARAM_UNIQUE_TRACKING_NUMBER = "utmn"
	val PARAM_LANGUAGE_ENCODING = "utmcs"
	val PARAM_SCREEN_RESOLUTION = "utmsr"
	val PARAM_SCREEN_COLOR_DEPTH = "utmsc"
	val PARAM_PRODUCT_NAME = "utmipn"
	val PARAM_PRODUCT_CODE = "utmipc"
	val PARAM_FLASH_VERSION = "utmfl"
	val PARAM_BROWSER_LANGUAGE = "utmul"
	val PARAM_REPEAT_CAMPAIGN_VISIT = "utmcr"
	val PARAM_PAGE_TITLE = "utmdt"
	val PARAM_GAQ = "gaq"
	val PARAM_AD_CONTENT = "utm_content"
	val PARAM_JAVA_ENABLED = "utmje"

	val VALUE_TRACKING_CODE_VERSION = "4.7.2";
	val VALUE_NO_REFERRAL = "0";
	val VALUE_ENCODING_UTF8 = "UTF-8";

	val SCREERESOLUTION_DELIMITER = "x";
	val SCREENCOLORDEPTH_POSTFIX = "-bit";

}

trait RequestContext {
	def accountName : String
	def referral : String
	def screenResolution : String
	def screenColorDepth : Int
	def browserLanguage : String
	def flashVersion : String
	def userId : String
	def userAgent : String
	def firstVisit : String
	def lastVisit : String
	def currentVisit : String
	def visitCount : Long
	def userDefined : String
	def keyword : String
}

/**
 * Similar to the original GoogleAnalyticsV4_7_2 but allow to define appId (__utma)
 * to track distinct ide/app like google analytics track distinct browser.
 *  
 * @author David Bernard
 * @based on http://code.google.com/p/jgoogleanalyticstracker/source/browse/trunk/src/main/java/com/dmurph/tracking/GoogleAnalyticsV4_7_2.java
 * @see http://code.google.com/apis/analytics/docs/tracking/gaTrackingTroubleshooting.html#gifParameters
 * @see http://code.google.com/apis/analytics/docs/concepts/gaConceptsCookies.html
 */
class GoogleAnalyticsTracker() {
  import GoogleAnalyticsConstantes._

  private val _random = new scala.util.Random()

  def trackPageView(ctx : RequestContext, hostname : String = "", pagePath :String = "", pageTitle : String = "") : Int = {
    val gaUrl =newGAUrlRequest(ctx, hostname, pagePath, pageTitle)
    println(gaUrl)
    httpNotify(gaUrl, ctx.userAgent)
  }
  
  /**
   * @see com.dmurph.tracking.IGoogleAnalyticsURLBuilder#buildURL(com.dmurph.tracking.AnalyticsRequestData)
   */
  def newGAUrlRequest(ctx : RequestContext, hostname : String = "", pagePath :String = "", pageTitle : String = "") : String = {

    val cookies = buildCookiesString(ctx)
    var l = List(
      (PARAM_TRACKING_CODE_VERSION,  VALUE_TRACKING_CODE_VERSION)
      , (PARAM_UNIQUE_TRACKING_NUMBER, _random.nextInt.toString) //(Math.random() * 0x7fffffff)
      , (PARAM_HOST_NAME, hostname)
      , (PARAM_LANGUAGE_ENCODING, VALUE_ENCODING_UTF8)
      , (PARAM_SCREEN_RESOLUTION, ctx.screenResolution)
      , (PARAM_SCREEN_COLOR_DEPTH, ctx.screenColorDepth + SCREENCOLORDEPTH_POSTFIX)
      , (PARAM_BROWSER_LANGUAGE, ctx.browserLanguage)
      , (PARAM_FLASH_VERSION, ctx.flashVersion)        
      , (PARAM_JAVA_ENABLED, "1")        
      , (PARAM_REFERRAL, ctx.referral)
      , (PARAM_ACCOUNT_NAME, ctx.accountName)
      , (PARAM_COOKIES, cookies)
      , (PARAM_GAQ, "1")
    )
//    if (argData.getEventAction() != null && argData.getEventCategory() != null) {
//      (sb.append("&utmt=event")
//        .append("&utme=5(")
//        .append(getURIString(argData.getEventCategory()))
//        .append('*')
//        .append(getURIString(argData.getEventAction()))
//      )
//      if (argData.getEventLabel() != null){
//        sb.append('*').append(getURIString(argData.getEventLabel()))
//      }
//      sb.append(')')
//                
//      if (argData.getEventValue() != null){
//        sb.append('(').append(argData.getEventValue()).append(')')
//      }
//    } else if(argData.getEventAction() != null || argData.getEventCategory() != null) {
//      throw new IllegalArgumentException("Event tracking must have both a category and an action");
//    }

    l = (PARAM_PAGE_TITLE, pageTitle) :: (PARAM_PAGE_REQUEST, pagePath) :: l 


    l.collect{ case (k,v) => k + "=" + encodeURIString(v) }.mkString(URL_BASE, "&", "")
  }    

  private def buildCookiesString(ctx : RequestContext) = {
    val l = List(
      (PARAM_COOKIES_UNIQUE_VISITOR_ID, "9999.%s.%s.%s.%s;+".format(ctx.userId, ctx.firstVisit, ctx.lastVisit, ctx.currentVisit, ctx.visitCount))
      , (PARAM_COOKIES_REFERRAL_TYPE, "999.%s.1.1.".format(ctx.firstVisit))
      , (PARAM_COOKIES_UTMCSR, "(direct)|")
      , (PARAM_COOKIES_UTMCCN, "(direct)|")
      , (PARAM_COOKIES_UTMCMD, "(none)|")
      , (PARAM_COOKIES_KEYWORD, ctx.keyword + "|")
      , (PARAM_COOKIES_USERDEFINED, "%d.%s ;".format(_random.nextInt, ctx.userDefined))
    )
    l.collect{ case (k,v) => k + "=" + v }.mkString("")
  }

  private def encodeURIString(v : String) : String = v match { 
    case null => null
    case str => URLEncoder.encode(v, VALUE_ENCODING_UTF8) 
  }
  
  def httpNotify(urlString : String, userAgent : String) : Int = {
    def createURLConnection(urlString : String, userAgent : String) : HttpURLConnection = {
      val url = new URL(urlString)
      val urlConnection = url.openConnection().asInstanceOf[HttpURLConnection]
      urlConnection.setInstanceFollowRedirects(true);
      urlConnection.setRequestMethod("GET")
      urlConnection.setRequestProperty("User-Agent", userAgent)
      urlConnection
    }
    val urlConnection = createURLConnection(urlString, userAgent)
    urlConnection.connect()
    urlConnection.getResponseCode
  }
}
