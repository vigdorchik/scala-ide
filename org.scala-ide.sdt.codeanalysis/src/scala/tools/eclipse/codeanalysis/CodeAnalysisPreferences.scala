/*
 * Copyright 2005-2011 LAMP/EPFL
 * @author Mirko Stocker
 * @author Sean McDirmid
 */

package scala.tools.eclipse
package codeanalysis

import org.eclipse.core.resources.{ResourcesPlugin, IResource, IProject}
import org.eclipse.core.runtime.CoreException
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.swt.layout.{GridLayout, GridData}
import org.eclipse.swt.widgets.{Group, Control, Composite}
import org.eclipse.swt.SWT
import org.eclipse.ui.dialogs.PropertyPage
import org.eclipse.ui.{IWorkbenchPreferencePage, IWorkbench}

import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.properties.IDESettings.Box
import scala.tools.eclipse.properties.{ScalaPluginPreferencePage, PropertyStore, EclipseSettings}
import scala.tools.eclipse.ScalaPlugin

object Settings extends scala.tools.nsc.Settings {
  val codeAnalysis = BooleanSetting("-codeanalysis", "Enable additional code analysis during compilation.")
  def codeAnalysisSettings: List[Box] = List(Box("Scala Code Analysis", List(codeAnalysis)))
}

class CodeAnalysisPreferences extends PropertyPage with IWorkbenchPreferencePage with EclipseSettings
  with ScalaPluginPreferencePage with HasLogger {

	/** Pulls the preference store associated with this plugin */
  override def doGetPreferenceStore() : IPreferenceStore = {
	    ScalaPlugin.plugin.getPreferenceStore
  }

  override def init(wb : IWorkbench) { }

  /** Returns the id of what preference page we use */
  import EclipseSetting.toEclipseBox
  lazy val scalaBoxes   = Settings.codeAnalysisSettings
  lazy val eclipseBoxes = scalaBoxes.map { s => toEclipseBox(s, getPreferenceStore) }

  def createContents(parent : Composite) : Control = {
    val composite = {
        //No Outer Composite
        val tmp = new Composite(parent, SWT.NONE)
	      val layout = new GridLayout(1, false)
        tmp.setLayout(layout)
        val data = new GridData(GridData.FILL)
        data.grabExcessHorizontalSpace = true
        data.horizontalAlignment = GridData.FILL
        tmp.setLayoutData(data)
        tmp
    }

    eclipseBoxes.foreach(eBox => {
      val group = new Group(composite, SWT.SHADOW_ETCHED_IN)
      group.setText(eBox.name)
      val layout = new GridLayout(3, false)
      group.setLayout(layout)
      val data = new GridData(GridData.FILL)
      data.grabExcessHorizontalSpace = true
      data.horizontalAlignment = GridData.FILL
      group.setLayoutData(data)
      eBox.eSettings.foreach(_.addTo(group))
    })
    composite
  }

  override def performOk = try {
    eclipseBoxes.foreach(_.eSettings.foreach(_.apply()))

    if(!Settings.codeAnalysis.value) {
      deleteAllMarkers
    }

    save()
    true
  } catch {
    case ex => logger.error(ex)
    false
  }

  def updateApply = updateApplyButton

  /** Updates the apply button with the appropriate enablement. */
  protected override def updateApplyButton() : Unit = {
    if(getApplyButton != null) {
      if(isValid) {
          getApplyButton.setEnabled(isChanged)
      } else {
        getApplyButton.setEnabled(false)
      }
    }
  }

  def save(): Unit = {
	save(scalaBoxes, getPreferenceStore)

    //Don't let user click "apply" again until a change
    updateApplyButton
  }

  private def deleteAllMarkers {
    ResourcesPlugin.getWorkspace.getRoot.getProjects foreach { project =>
      try {
        project.deleteMarkers(codeanalysis.CodeAnalysisExtensionPoint.MARKER_TYPE, true, IResource.DEPTH_INFINITE)
      } catch {
        case _: CoreException => // ignore, happens for example when the project is closed
      }
    }
  }
}

object CodeAnalysisPreferences {
  val PREFIX = "codeanalysis"
  val USE_PROJECT_SPECIFIC_SETTINGS_KEY = PREFIX + ".useProjectSpecificSettings"
  val PAGE_ID = "scala.tools.eclipse.codeanalysis.CodeAnalysisPreferencePage"
  val SEVERITY = "severity"
  val ENABLED = "enabled"

  def enabledKey (id: String) = (PREFIX :: id :: ENABLED  :: Nil) mkString "."
  def severityKey(id: String) = (PREFIX :: id :: SEVERITY :: Nil) mkString "."
  def generallyEnabledKey = (PREFIX :: ENABLED  :: Nil) mkString "."

  def isEnabledForProject(project: IProject, analyzerId: String) = {
    getPreferenceStore(project).getBoolean(enabledKey(analyzerId))
  }

  def getSeverityForProject(project: IProject, analyzerId: String) = {
    getPreferenceStore(project).getInt(severityKey(analyzerId))
  }

  def isGenerallyEnabledForProject(project: IProject) = {
    getPreferenceStore(project).getBoolean(generallyEnabledKey)
  }

  private def getPreferenceStore(project: IProject): IPreferenceStore = {
    val workspaceStore = ScalaPlugin.plugin.getPreferenceStore
    val projectStore = new PropertyStore(project, workspaceStore, ScalaPlugin.plugin.pluginId)
    val useProjectSettings = projectStore.getBoolean(USE_PROJECT_SPECIFIC_SETTINGS_KEY)
    if (useProjectSettings) projectStore else ScalaPlugin.plugin.getPreferenceStore
  }
}
