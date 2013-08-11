package org.digimead.tabuddy.desktop.editor.action

import org.eclipse.jface.action.ControlContribution
import org.digimead.digi.lib.log.api.Loggable
import org.eclipse.jface.viewers.LabelProvider
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future
import org.digimead.tabuddy.desktop.logic.payload.Payload
import org.digimead.tabuddy.desktop.Messages
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.jface.viewers.ComboViewer
import org.eclipse.swt.SWT
import org.eclipse.jface.viewers.ArrayContentProvider
import org.eclipse.jface.databinding.viewers.ViewersObservables

class SelectViewDefinition extends ControlContribution(SelectViewDefinition.id) with Loggable {

  SelectViewDefinition.instance += this -> {}

  /** Create toolbar control. */
  override protected def createControl(parent: Composite): Control = {
    val viewer = new ComboViewer(parent, SWT.BORDER | SWT.H_SCROLL | SWT.READ_ONLY)
    viewer.getCombo.setToolTipText(Messages.views_text)
    viewer.setContentProvider(ArrayContentProvider.getInstance())
    /*viewer.setLabelProvider(new LabelProvider() {
      override def getText(element: Object): String = element match {
        case view: View =>
          view.name
        case unknown =>
          log.fatal("Unknown item " + unknown.getClass())
          unknown.toString
      }
    })
    ViewersObservables.observeDelayedValue(50, ViewersObservables.observeSingleSelection(viewer)).addChangeListener(new IChangeListener {
      override def handleChange(event: ChangeEvent) = {
        ToolbarView.this.view.value = Some(event.getObservable.asInstanceOf[IObservableValue].getValue().asInstanceOf[View])
      }
    })
    ToolbarView.this.view.addChangeListener { (_, _) =>
      reloadSortingItems
      reloadFilterItems
      setSelected
    }
    viewCombo = Some(viewer)
    reloadViewItems()*/
    viewer.getControl()
  }
}

object SelectViewDefinition {
  /** All SelectViewDefinition instances. */
  private val instance = new mutable.WeakHashMap[SelectViewDefinition, Unit] with mutable.SynchronizedMap[SelectViewDefinition, Unit]
  /** Singleton identificator. */
  val id = getClass.getName().dropRight(1)

  class ComboLabelProvider extends LabelProvider {
    override def getText(element: AnyRef): String = element match {
      case value: String if value == Payload.defaultModel.eId.name => Messages.default_text
      case value => super.getText(element)
    }
  }
}