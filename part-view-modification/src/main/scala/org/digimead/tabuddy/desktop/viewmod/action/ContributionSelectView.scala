/**
 * This file is part of the TABuddy project.
 * Copyright (c) 2013 Alexey Aksenov ezh@ezh.msk.ru
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Global License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED
 * BY Limited Liability Company «MEZHGALAKTICHESKIJ TORGOVYJ ALIANS»,
 * Limited Liability Company «MEZHGALAKTICHESKIJ TORGOVYJ ALIANS» DISCLAIMS
 * THE WARRANTY OF NON INFRINGEMENT OF THIRD PARTY RIGHTS.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Global License for more details.
 * You should have received a copy of the GNU Affero General Global License
 * along with this program; if not, see http://www.gnu.org/licenses or write to
 * the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA, or download the license from the following URL:
 * http://www.gnu.org/licenses/agpl.html
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Global License.
 *
 * In accordance with Section 7(b) of the GNU Affero General Global License,
 * you must retain the producer line in every report, form or document
 * that is created or manipulated using TABuddy.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the TABuddy software without
 * disclosing the source code of your own applications.
 * These activities include: offering paid services to customers,
 * serving files in a web or/and network application,
 * shipping TABuddy with a closed source product.
 *
 * For more information, please contact Digimead Team at this
 * address: ezh@ezh.msk.ru
 */

package org.digimead.tabuddy.desktop.viewmod.action

import java.util.UUID

import scala.collection.mutable
import scala.ref.WeakReference

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Core
import org.digimead.tabuddy.desktop.Messages
import org.digimead.tabuddy.desktop.definition.Context.rich2appContext
import org.digimead.tabuddy.desktop.gui.GUI
import org.digimead.tabuddy.desktop.gui.widget.VComposite
import org.digimead.tabuddy.desktop.logic.Data
import org.digimead.tabuddy.desktop.logic.Default
import org.digimead.tabuddy.desktop.logic.payload
import org.digimead.tabuddy.desktop.logic.payload.view.api.View
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.WritableValue
import org.digimead.tabuddy.desktop.support.WritableValue.wrapper2underlying
import org.eclipse.core.databinding.observable.ChangeEvent
import org.eclipse.core.databinding.observable.IChangeListener
import org.eclipse.core.databinding.observable.Observables
import org.eclipse.core.databinding.observable.value.IObservableValue
import org.eclipse.core.databinding.observable.value.IValueChangeListener
import org.eclipse.core.databinding.observable.value.ValueChangeEvent
import org.eclipse.e4.core.contexts.Active
import org.eclipse.e4.core.contexts.ContextInjectionFactory
import org.eclipse.e4.core.di.annotations.Optional
import org.eclipse.jface.action.ControlContribution
import org.eclipse.jface.action.IContributionItem
import org.eclipse.jface.action.ToolBarContributionItem
import org.eclipse.jface.databinding.viewers.IViewerObservableValue
import org.eclipse.jface.databinding.viewers.ViewersObservables
import org.eclipse.jface.viewers.ArrayContentProvider
import org.eclipse.jface.viewers.ComboViewer
import org.eclipse.jface.viewers.LabelProvider
import org.eclipse.jface.viewers.StructuredSelection
import org.eclipse.swt.SWT
import org.eclipse.swt.events.DisposeEvent
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control

import javax.inject.Inject
import javax.inject.Named

class ContributionSelectView extends ControlContribution(ContributionSelectView.id) with Loggable {
  /** The combo box with list of view definitions. */
  @volatile protected var comboViewer = WeakReference[ComboViewer](null)
  /** Combo viewer observable. */
  @volatile protected var comboViewerObservable: Option[IViewerObservableValue] = None
  /** ToolBarContributionItem inside CoolBar. */
  @volatile protected var coolBarContributionItem = WeakReference[ToolBarContributionItem](null)
  /** IContributionItem inside ToolBar. */
  @volatile protected var toolBarContributionItem = WeakReference[IContributionItem](null)
  /** An active view definition. */
  protected val view = WritableValue[Option[View]](None)
  /** View entities changes aggregator. */
  protected val viewEventsAggregator = WritableValue(Long.box(0L))

  initialize()

  /** Initialize this class */
  protected def initialize() {
    App.assertUIThread()
    ContributionSelectView.instance += (ContributionSelectView.this) -> {}
    ContextInjectionFactory.inject(ContributionSelectView.this, Core.context)
    Data.viewDefinitions.addChangeListener { event => viewEventsAggregator.value = System.currentTimeMillis() }
    Observables.observeDelayedValue(Default.aggregatorDelay, viewEventsAggregator).addValueChangeListener(new IValueChangeListener {
      def handleValueChange(event: ValueChangeEvent) {
        reloadItems()
        for {
          selectedViewValue <- ContributionSelectView.this.view.value
          actualViewValue <- Data.viewDefinitions.get(selectedViewValue.id)
        } if (!payload.view.View.compareDeep(selectedViewValue, actualViewValue))
          // user modified the current view: id is persisted, but object is changed
          ContributionSelectView.this.view.value = Some(actualViewValue)
      }
    })
    ContributionSelectView.this.view.addChangeListener { (value, _) =>
      value match {
        case Some(selected) => comboViewer.get.foreach(_.setSelection(new StructuredSelection(selected)))
        case None => comboViewer.get.foreach(_.setSelection(new StructuredSelection(payload.view.Sorting.simpleSorting)))
      }
      updateContextValue(value)
    }
  }
  /** Create contribution control. */
  override protected def createControl(parent: Composite): Control = {
    log.debug("Create ContributionSelectView contribution")
    val comboViewer = new ComboViewer(parent, SWT.BORDER | SWT.H_SCROLL | SWT.READ_ONLY)
    comboViewer.getCombo.setToolTipText(Messages.views_text)
    comboViewer.setContentProvider(ArrayContentProvider.getInstance())
    comboViewer.setLabelProvider(new LabelProvider() {
      override def getText(element: Object): String = element match {
        case view: View =>
          view.name
        case unknown =>
          log.fatal("Unknown item " + unknown.getClass())
          unknown.toString
      }
    })
    val comboViewerObservable = ViewersObservables.observeDelayedValue(50, ViewersObservables.observeSingleSelection(comboViewer))
    comboViewerObservable.addChangeListener(ViewerChangeListener)
    comboViewer.getControl().addDisposeListener(DisposeListener)
    this.comboViewerObservable = Some(comboViewerObservable)
    this.comboViewer = WeakReference(comboViewer)
    reloadItems()
    comboViewer.getControl()
  }
  /** Get IContributionItem for this ControlContribution. */
  protected def getComboContribution(): Option[IContributionItem] = toolBarContributionItem.get orElse
    getCoolBarContribution.flatMap { cc =>
      val result = Option(cc.getToolBarManager().find(ContributionSelectView.id))
      result.map(item => toolBarContributionItem = WeakReference(item))
      result
    }
  /** Returns CoolBar contribution item. */
  protected def getCoolBarContribution(): Option[ToolBarContributionItem] = coolBarContributionItem.get orElse comboViewer.get.flatMap { combo =>
    App.findWindowComposite(combo.getControl().getShell).flatMap(_.getAppWindow).map(_.getCoolBarManager2()).flatMap { coolbarManager =>
      coolbarManager.getItems().find {
        case item: ToolBarContributionItem =>
          item.getToolBarManager() == this.getParent()
        case item =>
          false
      } map { item =>
        coolBarContributionItem = WeakReference(item.asInstanceOf[ToolBarContributionItem])
        item.asInstanceOf[ToolBarContributionItem]
      }
    }
  }
  /** Invoked on view activation. */
  @Inject @Optional
  def onViewChanged(@Named(GUI.viewContextKey) vcomposite: VComposite) = Option(vcomposite) foreach (vcomposite => App.exec {
    Option(vcomposite.getContext.getLocal(Data.Id.selectedView)) match {
      case Some(viewId: UUID) if Some(viewId) != view.value.map(_.id) =>
        // Take previous value.
        updateContributionValue(Some(viewId))
      case None =>
        // There is uninitialized context.
        log.debug(s"Initialize ${vcomposite} context.")
        view.value = Option(payload.view.View.displayName)
        updateContextValue(Some(payload.view.View.displayName))
      case _ =>
    }
  })
  /** Invoked at every modification of Data.Id.selectedView. */
  @Inject @Optional // @log
  def onSelectedViewChanged(@Active @Named(Data.Id.selectedView) id: UUID): Unit =
    App.exec { updateContributionValue(Option(id)) }
  /** Reload view definitions combo box */
  protected def reloadItems() = for {
    combo <- comboViewer.get
    toolBarContribution <- getCoolBarContribution()
    comboContribution <- getComboContribution()
  } {
    log.debug("Reload view combo.")
    App.assertUIThread()
    combo.setInput(Data.getAvailableViewDefinitions.toArray)
    view.value match {
      case Some(selected) => combo.setSelection(new StructuredSelection(selected))
      case None => combo.setSelection(new StructuredSelection(payload.view.View.displayName))
    }
  }
  /** Update combo box value by ID. */
  protected def updateContributionValue(newValueId: Option[UUID]) = newValueId match {
    case Some(valueId) if view.value.map(_.id) != newValueId && Data.getAvailableViewDefinitions.exists(_.id == newValueId) =>
      view.value = Option(Data.getAvailableViewDefinitions.find(_.id == valueId) getOrElse { payload.view.View.displayName })
    case _ if Option(payload.view.View.displayName.id) != newValueId =>
      view.value = Option(payload.view.View.displayName)
    case _ =>
  }
  /** Update context Data.Id.selectedView value. */
  protected def updateContextValue(newValue: Option[View]) =
    App.findBranchContextByName(Core.context.getActiveLeaf, VComposite.contextName).foreach(context =>
      newValue match {
        case Some(value) => context.set(Data.Id.selectedView, value.id)
        case None => context.remove(Data.Id.selectedView)
      })
  /** Combo viewer change listener. */
  object ViewerChangeListener extends IChangeListener {
    override def handleChange(event: ChangeEvent) = {
      val newValue = Option(event.getObservable.asInstanceOf[IObservableValue].getValue().asInstanceOf[View])
      if (ContributionSelectView.this.view.value != newValue)
        ContributionSelectView.this.view.value = newValue
    }
  }
  /** Combo control dispose listener. */
  object DisposeListener extends org.eclipse.swt.events.DisposeListener {
    def widgetDisposed(e: DisposeEvent) {
      comboViewer = WeakReference(null)
      comboViewerObservable.foreach(_.dispose())
      comboViewerObservable = None
    }
  }
}

object ContributionSelectView {
  /** All SelectView instances. */
  private val instance = new mutable.WeakHashMap[ContributionSelectView, Unit] with mutable.SynchronizedMap[ContributionSelectView, Unit]
  /** Singleton identificator. */
  val id = getClass.getName().dropRight(1)
}
