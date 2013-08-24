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

package org.digimead.tabuddy.desktop.view.modification.action

import java.util.UUID

import scala.ref.WeakReference

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Core
import org.digimead.tabuddy.desktop.definition.Context.rich2appContext
import org.digimead.tabuddy.desktop.gui.widget.AppWindow
import org.digimead.tabuddy.desktop.gui.widget.VComposite
import org.digimead.tabuddy.desktop.logic.Data
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.WritableValue
import org.digimead.tabuddy.desktop.support.WritableValue.wrapper2underlying
import org.digimead.tabuddy.desktop.view.modification.Default
import org.eclipse.core.databinding.observable.ChangeEvent
import org.eclipse.core.databinding.observable.IChangeListener
import org.eclipse.core.databinding.observable.Observables
import org.eclipse.core.databinding.observable.value.IObservableValue
import org.eclipse.core.databinding.observable.value.IValueChangeListener
import org.eclipse.core.databinding.observable.value.ValueChangeEvent
import org.eclipse.e4.core.contexts.ContextInjectionFactory
import org.eclipse.jface.action.ControlContribution
import org.eclipse.jface.action.IContributionItem
import org.eclipse.jface.action.ToolBarContributionItem
import org.eclipse.jface.databinding.viewers.IViewerObservableValue
import org.eclipse.jface.databinding.viewers.ViewersObservables
import org.eclipse.jface.viewers.ArrayContentProvider
import org.eclipse.jface.viewers.ComboViewer
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.swt.SWT
import org.eclipse.swt.events.DisposeEvent
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control

import language.reflectiveCalls

trait ContributionSelectBase[T <: { val id: UUID }] {
  this: ControlContribution with Loggable =>
  /** The combo box with list of values. */
  @volatile protected var comboViewer = WeakReference[ComboViewer](null)
  /** Combo viewer observable. */
  @volatile protected var comboViewerObservable: Option[IViewerObservableValue] = None
  /** Context value key. */
  val contextValueKey: String
  /** ToolBarContributionItem inside CoolBar. */
  @volatile protected var coolBarContributionItem = WeakReference[ToolBarContributionItem](null)
  /** View entities changes aggregator. */
  protected val eventsAggregator = WritableValue(Long.box(0L))
  /** IContributionItem inside ToolBar. */
  @volatile protected var toolBarContributionItem = WeakReference[IContributionItem](null)
  @volatile protected var window = WeakReference[AppWindow](null)

  /** Create contribution control. */
  override protected def createControl(parent: Composite): Control = {
    val comboViewer = new ComboViewer(parent, SWT.BORDER | SWT.H_SCROLL | SWT.READ_ONLY)
    comboViewer.setContentProvider(ArrayContentProvider.getInstance())
    val comboViewerObservable = ViewersObservables.observeDelayedValue(50, ViewersObservables.observeSingleSelection(comboViewer))
    comboViewerObservable.addChangeListener(new IChangeListener {
      override def handleChange(event: ChangeEvent) =
        updateContextValue(Option(event.getObservable.asInstanceOf[IObservableValue].getValue().asInstanceOf[T]))
    })
    comboViewer.getControl().addDisposeListener(DisposeListener)
    this.comboViewerObservable = Some(comboViewerObservable)
    this.comboViewer = WeakReference(comboViewer)
    reloadItems()
    comboViewer.getControl()
  }
  /** Get IContributionItem for this ControlContribution. */
  protected def getComboContribution(): Option[IContributionItem] = toolBarContributionItem.get orElse
    getCoolBarContribution.flatMap { cc =>
      val result = Option(cc.getToolBarManager().find(getId()))
      result.map(item => toolBarContributionItem = WeakReference(item))
      result
    }
  /** Get app window. */
  protected def getAppWindow() = window.get orElse comboViewer.get.flatMap(combo =>
    App.findWindowComposite(combo.getControl().getShell).flatMap(_.getAppWindow))
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
  /** Get selection from combo box. */
  def getSelection(): Option[T] = comboViewer.get.flatMap(_.getSelection() match {
    case selection: IStructuredSelection if !selection.isEmpty() => Option(selection.getFirstElement().asInstanceOf[T])
    case selection => None
  })
  /** Initialize this class */
  protected def initialize() {
    App.assertUIThread()
    ContextInjectionFactory.inject(this, Core.context)
    Data.viewDefinitions.addChangeListener { event => eventsAggregator.value = System.currentTimeMillis() }
    Observables.observeDelayedValue(Default.aggregatorDelay, eventsAggregator).addValueChangeListener(new IValueChangeListener {
      def handleValueChange(event: ValueChangeEvent) = reloadItems()
    })
  }
  /** Reload view definitions combo box */
  protected def reloadItems()
  /** Update combo box value by ID. */
  protected def updateComboBoxValue(newValueId: UUID)
  /** Update combo box value. */
  protected def updateComboBoxValue(value: Option[T])
  /** Update context 'contextValueKey' value. */
  protected def updateContextValue(newValue: Option[T]): Unit =
    App.findBranchContextByName(Core.context.getActiveLeaf, VComposite.contextName).foreach(context =>
      // Update only contexts with key Data.Id.usingViewDefinition = TRUE
      if (context.getLocal(Data.Id.usingViewDefinition) == java.lang.Boolean.TRUE) newValue match {
        case Some(value) =>
          log.debug(s"Set context value to ${value.id}.")
          context.set(contextValueKey, value.id)
        case None =>
          log.debug("Remove context value.")
          context.remove(contextValueKey)
      })
  /** Combo control dispose listener. */
  object DisposeListener extends org.eclipse.swt.events.DisposeListener {
    def widgetDisposed(e: DisposeEvent) {
      comboViewer = WeakReference(null)
      comboViewerObservable.foreach(_.dispose())
      comboViewerObservable = None
    }
  }
}
