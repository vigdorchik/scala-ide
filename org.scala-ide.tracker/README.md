The goal Of the plugin is to report *anonymous* info about usage of ScalaIDE.

# Data

The data reported are (group by version of the plugin) :

* 0.2.0:
  * jdt version, scala version, scala-ide version, m2e version,
  * java version, os name, os version, screen resolution
* 0.1.0:
  * jdt version, scala version, scala-ide version, m2e version

All data are send to Google Analytics.

# Todo :

* --use a same "visitor ID" for one installation--
* send event for each version (like in 0.1.0)
* store the time on IDE as time on page
* use http proxy configuration from eclispe (Preferences > General > Network)
* ask user to enable "Usage Data" the first time

# Changelog :

* 0.2.0 :
  * no longer use ... instead used "home made" connector based on
    * [org.tigris.subversion.subclipse.tools.usage]
    * [JGoogleAnalyticsTracker]
  * reuse visitor ID (appId) per eclipse installation (preferences)
  * use user-agent and flash version to send os information and java version
  * event with individual versions not send (temporary)
* 0.1.0 :
  * initial code (use [JGoogleAnalyticsTracker])

# Comments :

After I finish the first runnable implementation 0.1.0. By hazard, I found a similar (and more advanced)  plugin used by subclipse to report usage into GA : [org.tigris.subversion.subclipse.tools.usage]

   [org.tigris.subversion.subclipse.tools.usage]: http://subclipse.tigris.org/source/browse/subclipse/trunk/subclipse/org.tigris.subversion.subclipse.tools.usage/#dirlist
   [JGoogleAnalyticsTracker]: https://code.google.com/p/jgoogleanalyticstracker/
   [JGoogleAnalytics]: http://code.google.com/p/jgoogleanalytics/

