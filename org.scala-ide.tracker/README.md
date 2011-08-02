The goal Of the plugin is to report *anonymous* info about usage of ScalaIDE.

The data reported are (group by version of the plugin) :
* v0.1.0: jdt version, scala version, scala-ide version, m2e version

All data are send to Google Analytics.
 
After I finish the first runnable implementation 0.1.0. By hazard, I found a similar (and more advanced)  plugin used by subclipse to report usage into GA :http://subclipse.tigris.org/source/browse/subclipse/trunk/subclipse/org.tigris.subversion.subclipse.tools.usage/#dirlist

TODO :
* use a same "visitor ID" for one installation 
* store the time on IDE as time on page
* use http proxy configuration from eclispe (Preferences > General > Network)