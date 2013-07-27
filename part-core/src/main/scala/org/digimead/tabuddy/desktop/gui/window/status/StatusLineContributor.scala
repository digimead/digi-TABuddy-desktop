package org.digimead.tabuddy.desktop.gui.window.status

import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.eclipse.jface.action.ContributionItem
import org.eclipse.jface.action.{ StatusLineManager => JStatusLineManager }
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Combo
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.widgets.Text
import org.eclipse.ui.internal.HeapStatus

import language.implicitConversions

class StatusLineContributor {
  def apply(statusLineManager: StatusLineManager) {
    val beginGroup = statusLineManager.find(JStatusLineManager.BEGIN_GROUP)
    statusLineManager.prependToGroup(JStatusLineManager.BEGIN_GROUP, new Item("1"))
    statusLineManager.appendToGroup(JStatusLineManager.END_GROUP, new HeapStatusContribution)
  }

  class HeapStatusContribution extends ContributionItem("HeapStatus") {
    override def fill(parent: Composite) {
      new HeapStatus(parent, App.getPreferenceStore)
    }
  }
  class Item(val id: String) extends ContributionItem(id) {
    override def fill(parent: Composite) {

      val label = new Label(parent, SWT.NONE);
      label.setText("LABEL");
      val text = new Text(parent, SWT.BORDER);
      text.setText("TEXT");
      val combo = new Combo(parent, SWT.NONE);
      combo.setText("COMBO");
    }
  }
}

object StatusLineContributor {
  implicit def sbar2implementation(c: StatusLineContributor.type): StatusLineContributor = c.inner

  /** StatusLineContributor implementation. */
  def inner = DI.implementation

  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** StatusLineContributor implementation. */
    lazy val implementation = injectOptional[StatusLineContributor] getOrElse new StatusLineContributor
  }
}
