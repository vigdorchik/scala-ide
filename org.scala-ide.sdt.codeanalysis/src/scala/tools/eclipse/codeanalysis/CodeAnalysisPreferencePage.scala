package scala.tools.eclipse
package codeanalysis

import CodeAnalysisPreferences.PAGE_ID
import CodeAnalysisPreferences.USE_PROJECT_SPECIFIC_SETTINGS_KEY
import CodeAnalysisPreferences.enabledKey
import CodeAnalysisPreferences.generallyEnabledKey
import CodeAnalysisPreferences.severityKey
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swt.MigLayout
import org.eclipse.core.resources.IProject
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.internal.ui.preferences.PreferencesMessages
import org.eclipse.swt.custom.CCombo
import org.eclipse.swt.custom.TableEditor
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Event
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.widgets.Link
import org.eclipse.swt.widgets.Listener
import org.eclipse.swt.widgets.Table
import org.eclipse.swt.widgets.TableColumn
import org.eclipse.swt.widgets.TableItem
import org.eclipse.swt.SWT
import org.eclipse.ui.dialogs.PreferencesUtil
import org.eclipse.ui.dialogs.PropertyPage
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage
import scala.tools.eclipse.properties.PropertyStore
import scala.tools.eclipse.util.SWTUtils.fnToSelectionAdapter
import scala.tools.eclipse.util.SWTUtils.noArgFnToSelectionAdapter
import scala.tools.eclipse.ScalaPlugin

class CodeAnalysisPreferencePage extends PropertyPage with IWorkbenchPreferencePage {
  import CodeAnalysisPreferences._

  private var isWorkbenchPage = false

  private var allEnableDisableControls: Set[Control] = Set()

  case class AnalyzerSetting(enabled: Boolean, severity: Int)

  private val settings = new collection.mutable.HashMap[String, AnalyzerSetting]

  override def init(workbench: IWorkbench) {
    isWorkbenchPage = true
  }

  private def initUnderlyingPreferenceStore() {
    val pluginId = ScalaPlugin.plugin.pluginId
    val scalaPrefStore = ScalaPlugin.plugin.getPreferenceStore
    setPreferenceStore(getElement match {
      case project: IProject => new PropertyStore(project, scalaPrefStore, pluginId)
      case project: IJavaProject => new PropertyStore(project.getProject, scalaPrefStore, pluginId)
      case _ => scalaPrefStore
    })
  }

  def createContents(parent: Composite): Control = {

    initUnderlyingPreferenceStore() // done here to ensure that getElement will have been set

    val control = new Composite(parent, SWT.NONE)
    val rowConstraints = if (isWorkbenchPage)
      new AC().index(0).grow(0).index(1).grow
    else
      new AC().index(0).grow(0).index(1).grow(0).index(2).grow(0).index(3).grow
    control.setLayout(new MigLayout(new LC().insetsAll("0").fill, new AC(), rowConstraints))

    if (!isWorkbenchPage) {

      val projectSpecificButton = new Button(control, SWT.CHECK | SWT.WRAP)
      projectSpecificButton.setText("Enable project specific settings")
      projectSpecificButton.setSelection(getPreferenceStore.getBoolean(USE_PROJECT_SPECIFIC_SETTINGS_KEY))
      projectSpecificButton.addSelectionListener { e: SelectionEvent =>
        val enabled = projectSpecificButton.getSelection
        getPreferenceStore.setValue(USE_PROJECT_SPECIFIC_SETTINGS_KEY, enabled)
        allEnableDisableControls foreach { _.setEnabled(enabled) }
      }
      projectSpecificButton.setLayoutData(new CC)

      val link = new Link(control, SWT.NONE)
      link.setText("<a>" + PreferencesMessages.PropertyAndPreferencePage_useworkspacesettings_change + "</a>")
      link.addSelectionListener { () =>
        PreferencesUtil.createPreferenceDialogOn(getShell, PAGE_ID, Array(PAGE_ID), null).open()
      }
      link.setLayoutData(new CC().alignX("right").wrap)

      val horizontalLine = new Label(control, SWT.SEPARATOR | SWT.HORIZONTAL)
      horizontalLine.setLayoutData(new CC().spanX(2).grow.wrap)
    }

    val button = new Button(control, SWT.CHECK)
    button.setText("Enable code analysis during compilation.")
    button.setSelection(getPreferenceStore.getBoolean(generallyEnabledKey))
    button.addSelectionListener { _: SelectionEvent =>
      getPreferenceStore.setValue(generallyEnabledKey, button.getSelection)
    }
    button.setLayoutData(new CC().spanX(2).grow.wrap)

    val table = new Table(control, SWT.CHECK | SWT.BORDER)

    if(isWorkbenchPage) {
      table.setLayoutData(new CC().grow)
    } else {
      table.setLayoutData(new CC().spanX(2).grow.wrap)
    }

    table.setHeaderVisible(true)
    table.setLinesVisible(true)

    for(name <- "Analyzer" ::  "Severity" :: Nil) {
      val column1 = new TableColumn(table, SWT.NONE)
      column1.setText(name)
      column1.setWidth(200)
    }

    val combos = CodeAnalysisExtensionPoint.extensions map {
      case CodeAnalysisExtensionPoint.ExtensionPointDescription(id, name, _, defaultSeverity, _) =>

        val item = new TableItem(table, SWT.NONE)

        val prefs = getPreferenceStore

        val isCurrentlyEnabled = prefs.getBoolean(enabledKey(id))
        val currentSeverity = prefs.getInt(severityKey(id))

        item.setText(name)
        item.setChecked(isCurrentlyEnabled)

        table.addListener(SWT.Selection, new Listener {
          def handleEvent(event: Event) {
            if(event.detail == SWT.CHECK && event.item == item) {
              val currentSettings = settings.getOrElse(id, AnalyzerSetting(isCurrentlyEnabled, currentSeverity))
              settings(id) = currentSettings copy (enabled = item.getChecked)
            }
          }
        })

        val editor= new TableEditor(table)
        val combo = new CCombo(table, SWT.NONE)
        combo.setText("Info")
        combo.add("Info")
        combo.add("Warning")
        combo.add("Error")
        combo.select(currentSeverity)
        editor.grabHorizontal = true
        editor.setEditor(combo, item, 1)
        combo.setEditable(false)

        combo.addSelectionListener(new SelectionAdapter {
          override def widgetSelected(e: SelectionEvent) {
            val currentSettings = settings.getOrElse(id, AnalyzerSetting(isCurrentlyEnabled, currentSeverity))
            settings(id) = currentSettings copy (severity = combo.getSelectionIndex)
          }
        })

        combo
    }

    allEnableDisableControls ++= (button :: table :: combos)

    if (!isWorkbenchPage) {
      val enabled = getPreferenceStore.getBoolean(USE_PROJECT_SPECIFIC_SETTINGS_KEY)
      allEnableDisableControls foreach { _.setEnabled(enabled) }
    }
    control
  }

  override def performOk() = {
    super.performOk()

    settings foreach {
      case x @ (id, AnalyzerSetting(enabled, severity)) =>
        getPreferenceStore.setValue(enabledKey(id), enabled)
        getPreferenceStore.setValue(severityKey(id), severity)
      }

    ScalaPlugin.plugin.savePluginPreferences()
    true
  }
}
