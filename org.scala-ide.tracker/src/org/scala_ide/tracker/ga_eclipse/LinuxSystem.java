/*******************************************************************************
 * Copyright (c) 2010 Subclipse project and others.
 * Copyright (c) 2010 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Subclipse project committers
 *     Red Hat, Inc. - initial API and implementation
 *     David Bernard (add LSB support)
 ******************************************************************************/
package org.scala_ide.tracker.ga_eclipse;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LinuxSystem {

	public static final LinuxSystem INSTANCE = new LinuxSystem();

	/**
	 * @see <a href="http://linuxmafia.com/faq/Admin/release-files.html"> an
	 *      extensive list of release file locations</a>
	 * 
	 * @see <a
	 *      href="http://superuser.com/questions/11008/how-do-i-find-out-what-version-of-linux-im-running">
	 *      release-file strings</a>
	 */
	
	public final LinuxDistro CENTOS = new CentOSDistro("CentOS", "/etc/redhat-release");
	public final LinuxDistro DEBIAN = new LinuxDistro("Debian", "/etc/debian_version");
	public final LinuxDistro FEDORA = new LinuxDistro("Fedora", "/etc/fedora-release");
	public final LinuxDistro GENTOO = new LinuxDistro("Gentoo", "/etc/gentoo-release");
	public final LinuxDistro YELLOWDOG = new LinuxDistro("YellowDog", "/etc/yellowdog-release");
	public final LinuxDistro KNOPPIX = new LinuxDistro("Knoppix", "knoppix_version");
	public final LinuxDistro MANDRAKE = new LinuxDistro("Mandrake", "/etc/mandrake-release");
	public final LinuxDistro MANDRIVA = new LinuxDistro("Mandriva", "/etc/mandriva-release");
	public final LinuxDistro PLD = new LinuxDistro("PLD", "/etc/pld-release");
	public final LinuxDistro REDHAT = new LinuxDistro("RedHat", "/etc/redhat-release");
	public final LinuxDistro SLACKWARE = new LinuxDistro("Slackware", "/etc/slackware-version");
	public final LinuxDistro SUSE = new LinuxDistro("SUSE", "/etc/SuSE-release");
	public final LinuxDistro LSB = new LinuxDistro("LSB", "/etc/lsb-release"); // Ubuntu, Arch Linux,...

	private final LinuxDistro[] ALL = new LinuxDistro[] {
			CENTOS,
			/**
			 * Attention: ubuntu has 2 release files
			 * <ul>
			 * <li>/etc/lsb-release</li>
			 * <li>/etc/debian_version</li>
			 * </ul>
			 * 
			 * It is not reliable to check Debian first and check there if no
			 * /etc/lsb-release exists. Debian may also have a /etc/lsb-release. We must
			 * check ubuntu prior to Debian.
			 * 
			 * @see http://bugs.debian.org/cgi-bin/bugreport.cgi?bug=444678
			 * 
			 */
			LSB,
			DEBIAN,
			FEDORA,
			GENTOO,
			KNOPPIX,
			MANDRAKE,
			MANDRIVA,
			PLD,
			REDHAT,
			SLACKWARE,
			SUSE,
			YELLOWDOG
		};

	public LinuxDistro getDistro() {
		for (int i = 0; i < ALL.length; i++) {
			if (ALL[i].isDistro()) {
				return ALL[i];
			}
		}
		return null;

	}

	public String getDistroNameAndVersion() {
		LinuxDistro distro = getDistro();
		if (distro != null) {
			return distro.getNameAndVersion();
		} else {
			return "";
		}
	}

	protected class CentOSDistro extends LinuxDistro {
		private static final String CENTOS_NAME = "CentOS";

		protected CentOSDistro(String name, String releaseFilePath) {
			super(name, releaseFilePath);
		}

		protected boolean isDistro() {
			boolean fileExists = super.isDistro();
			if (fileExists) {
				String content = getDistroFileContent(releaseFilePath);
				return content != null && content.indexOf(CENTOS_NAME) >= 0;
			}
			return false;
		}
	}

	public class LinuxDistro {

		/**
		 * The pattern to match the contents of the release-file -
		 * /etc/fedora-release etc. Attention: Ubuntu has multi-line release
		 * file
		 */
		private final Pattern VERSION_REGEX = Pattern.compile("([0-9.]+)"); // DISTRIB_RELEASE=\"([^\"]*)\"
    private final Pattern LSB_DESCRIPTION_REGEX = Pattern.compile("DISTRIB_DESCRIPTION=\"([^\"]*)\"");
    private final Pattern LSB_ID_REGEX = Pattern.compile("DISTRIB_ID=\"([^\"]*)\"");

		protected final String releaseFilePath;
		private String category;

		protected LinuxDistro(String category, String releaseFilePath) {
			this.category = category;
			this.releaseFilePath = releaseFilePath;
		}

		protected boolean isDistro() {
	    return new File(releaseFilePath).exists();
		}

		private String getName(String distroString) {
		  String name = category;
		  if (category == "LSB") {
        Matcher matcher = LSB_ID_REGEX.matcher(distroString);
        if (matcher.find()) {
          name = matcher.group(1);
        }
		  }
			return name;
		}

		private String getVersion(String distroString) {
		  String version = "";
			Matcher matcher = VERSION_REGEX.matcher(distroString);
			if (matcher.find()) {
				version = matcher.group(1);
			}
			return version;
		}

		public String getNameAndVersion() {
      String distroString = getDistroFileContent(releaseFilePath);
      String back = "";
      Matcher matcher = LSB_DESCRIPTION_REGEX.matcher(distroString);
      if (matcher.find()) {
        back = matcher.group(1);
      } else {
        back =  String.format("%s %s", getName(distroString), getVersion(distroString));
      }
      return back;
		}

		public String getReleaseFilePath() {
			return releaseFilePath;
		}
	}

	protected String getDistroFileContent(String filePath) {
		int charachtersToRead = 1024;
		StringBuffer builder = new StringBuffer(charachtersToRead);
		BufferedReader reader = null;
		try {
		  reader = new BufferedReader(new FileReader(filePath));
  		char[] buf = new char[charachtersToRead];
  		int charRead = 0;
  		while ((charRead = reader.read(buf)) != -1 && builder.length() < charachtersToRead) {
  			String readData = String.valueOf(buf, 0, charRead);
  			builder.append(readData);
  		}
		} catch (IOException exc){ 
		  // ignore
		} finally {
		  try {
		    if (reader != null) reader.close();
	    } catch (IOException exc){ 
	      // ignore
		  }
		}
		return builder.toString();
	}
}