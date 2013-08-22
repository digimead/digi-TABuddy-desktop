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
import org.digimead.tabuddy.desktop.gui.widget.VComposite
import org.digimead.tabuddy.desktop.logic.Data
import org.digimead.tabuddy.desktop.logic.Default
import org.digimead.tabuddy.desktop.logic.payload
import org.digimead.tabuddy.desktop.logic.payload.view.api.Sorting
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
import org.eclipse.jface.databinding.viewers.ViewersObservables
import org.eclipse.jface.viewers.ArrayContentProvider
import org.eclipse.jface.viewers.ComboViewer
import org.eclipse.jface.viewers.LabelProvider
import org.eclipse.jface.viewers.StructuredSelection
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control

import javax.inject.Inject
import javax.inject.Named

class ContributionSelectSorting extends ControlContribution(ContributionSelectSorting.id) with Loggable {
  /** ToolBarContributionItem inside CoolBar. */
  @volatile protected var coolBarContributionItem = WeakReference[ToolBarContributionItem](null)
  /** IContributionItem inside ToolBar. */
  @volatile protected var toolBarContributionItem = WeakReference[IContributionItem](null)
  /** An active view sorting. */
  protected val sorting = WritableValue[Option[Sorting]](None)
  /** The combo box with list of view sortings. */
  @volatile protected var sortingCombo = WeakReference[ComboViewer](null)
  /** Sorting entities changes aggregator. */
  private val sortingEventsAggregator = WritableValue(Long.box(0L))

  ContributionSelectSorting.instance += (ContributionSelectSorting.this) -> {}
  ContextInjectionFactory.inject(ContributionSelectSorting.this, Core.context)

  /** Create contribution control. */
  override protected def createControl(parent: Composite): Control = {
    val viewer = new ComboViewer(parent, SWT.BORDER | SWT.H_SCROLL | SWT.READ_ONLY)
    viewer.getCombo.setToolTipText(Messages.sortings_text)
    viewer.setContentProvider(ArrayContentProvider.getInstance())
    viewer.setLabelProvider(new LabelProvider() {
      override def getText(element: Object): String = element match {
        case sorting: Sorting =>
          sorting.name
        case unknown =>
          log.fatal("Unknown item " + unknown.getClass())
          unknown.toString
      }
    })
    ViewersObservables.observeDelayedValue(50, ViewersObservables.observeSingleSelection(viewer)).addChangeListener(new IChangeListener {
      override def handleChange(event: ChangeEvent) = {
        val newValue = Option(event.getObservable.asInstanceOf[IObservableValue].getValue().asInstanceOf[Sorting])
        if (ContributionSelectSorting.this.sorting.value != newValue)
          ContributionSelectSorting.this.sorting.value = newValue
      }
    })
    ContributionSelectSorting.this.sorting.addChangeListener { (value, _) =>
      value match {
        case Some(selected) => viewer.setSelection(new StructuredSelection(selected))
        case None => viewer.setSelection(new StructuredSelection(payload.view.Sorting.simpleSorting))
      }
      updateContextValue(value)
    }
    Data.viewSortings.addChangeListener { event => sortingEventsAggregator.value = System.currentTimeMillis() }
    Observables.observeDelayedValue(Default.aggregatorDelay, sortingEventsAggregator).addValueChangeListener(new IValueChangeListener {
      def handleValueChange(event: ValueChangeEvent) {
        reloadItems()
        for {
          selectedSortingValue <- ContributionSelectSorting.this.sorting.value
          actualSortingValue <- Data.viewSortings.get(selectedSortingValue.id)
        } if (!payload.view.Sorting.compareDeep(selectedSortingValue, actualSortingValue))
          // user modified the current sorting: id is persisted, but object is changed
          ContributionSelectSorting.this.sorting.value = Some(actualSortingValue)
      }
    })
    sortingCombo = WeakReference(viewer)
    sorting.value = None
    reloadItems()
    viewer.getControl()
  }
  /** Get IContributionItem for this ControlContribution. */
  protected def getComboContribution(): Option[IContributionItem] = toolBarContributionItem.get orElse
    getCoolBarContribution.flatMap { cc =>
      val result = Option(cc.getToolBarManager().find(ContributionSelectSorting.id))
      result.map(item => toolBarContributionItem = WeakReference(item))
      result
    }
  /** Returns CoolBar contribution item. */
  protected def getCoolBarContribution(): Option[ToolBarContributionItem] = coolBarContributionItem.get orElse sortingCombo.get.flatMap { combo =>
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
  /** Invoked at every modification of Data.Id.selectedSorting. */
  @Inject @Optional // @log
  def onSelectedSortingChanged(@Active @Named(Data.Id.selectedSorting) id: UUID): Unit = App.exec {
    val newValue = if (id != null)
      Data.getAvailableViewSortings.find(_.id == id) getOrElse { payload.view.Sorting.simpleSorting }
    else
      payload.view.Sorting.simpleSorting
    if (ContributionSelectSorting.this.sorting.value != Some(newValue)) {
      log.debug(s"Update selected sorting value to ${newValue}.")
      ContributionSelectSorting.this.sorting.value = Some(newValue)
    } else if (id != newValue.id)
      updateContextValue(Option(newValue))
  }
  /** Invoked at every modification of Data.Id.selectedView. */
  @Inject @Optional // @log
  def onSelectedViewChanged(@Active @Named(Data.Id.selectedView) id: UUID): Unit = App.exec {
    val newValue = if (id != null)
      Data.getAvailableViewDefinitions.find(_.id == id) getOrElse { payload.view.View.displayName }
    else
      payload.view.View.displayName
    reloadItems(newValue)
  }
  /** Reload view definitions combo box */
  protected def reloadItems(view: payload.view.api.View = payload.view.View.displayName) = for {
    combo <- sortingCombo.get if !combo.getControl().isDisposed()
    toolBarContribution <- getCoolBarContribution()
    comboContribution <- getComboContribution()
  } {
    log.debug("Reload sorting combo.")
    App.assertUIThread()
    if (view != payload.view.View.displayName) {
      val available = Data.getAvailableViewSortings
      val sortings = payload.view.Sorting.simpleSorting +: view.sortings.flatMap(id => available.find(_.id == id)).toArray
      combo.setInput(sortings)
      sorting.value match {
        case Some(selected) if sortings.contains(selected) => combo.setSelection(new StructuredSelection(selected))
        case _ => combo.setSelection(new StructuredSelection(payload.view.Sorting.simpleSorting))
      }
    } else {
      combo.setInput(Data.getAvailableViewSortings.toArray)
      sorting.value match {
        case Some(selected) => combo.setSelection(new StructuredSelection(selected))
        case None => combo.setSelection(new StructuredSelection(payload.view.Sorting.simpleSorting))
      }
    }
  }
  /** Update current view context Data.Id.selectedView value. */
  protected def updateContextValue(newValue: Option[Sorting]) =
    App.findBranchContextByName(Core.context.getActiveLeaf, VComposite.contextName).foreach(context =>
      newValue match {
        case Some(value) => context.set(Data.Id.selectedSorting, value.id)
        case None => context.remove(Data.Id.selectedSorting)
      })
}

object ContributionSelectSorting {
  /** All SelectSorting instances. */
  private val instance = new mutable.WeakHashMap[ContributionSelectSorting, Unit] with mutable.SynchronizedMap[ContributionSelectSorting, Unit]
  /** Singleton identificator. */
  val id = getClass.getName().dropRight(1)
}
