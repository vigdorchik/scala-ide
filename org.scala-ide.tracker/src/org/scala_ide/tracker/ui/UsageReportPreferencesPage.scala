package org.scala_ide.tracker.ui

import org.eclipse.jface.preference.BooleanFieldEditor
import org.eclipse.jface.preference.DirectoryFieldEditor
import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.eclipse.jface.preference.RadioGroupFieldEditor
import org.eclipse.jface.preference.StringFieldEditor
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage
import org.scala_ide.tracker.Preferences
import org.eclipse.ui.preferences.ScopedPreferenceStore
import org.eclipse.core.runtime.preferences.ConfigurationScope

class UsageReportPreferencesPage extends FieldEditorPreferencePage(FieldEditorPreferencePage.GRID) with IWorkbenchPreferencePage {

  def createFieldEditors() {
    addField(new BooleanFieldEditor(Preferences.usageReportEnabledId, "&Report usage of ScalaIDE to ScalaIDE team", getFieldEditorParent()))
  }

  override def  init( workbench : IWorkbench) {
    val store = new ScopedPreferenceStore(new ConfigurationScope(), Preferences.pluginId)
    setPreferenceStore(store)
    setDescription("Allow the ScalaIDE team to receive anonymous usage\n statistics for this Eclipse installation (versions of jdt, scala, sdt, m2e).")
  }
}