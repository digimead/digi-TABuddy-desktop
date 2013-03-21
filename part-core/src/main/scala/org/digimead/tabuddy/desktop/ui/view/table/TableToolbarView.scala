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

package org.digimead.tabuddy.desktop.ui.view.table

import org.digimead.configgy.Configgy
import org.digimead.configgy.Configgy.getImplementation
import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.tabuddy.desktop.Config
import org.digimead.tabuddy.desktop.Config.config2implementation
import org.digimead.tabuddy.desktop.Data
import org.digimead.tabuddy.desktop.Main
import org.digimead.tabuddy.desktop.payload.view.View
import org.digimead.tabuddy.desktop.support.WritableValue
import org.digimead.tabuddy.desktop.support.WritableValue.wrapper2underlying
import org.eclipse.jface.action.ControlContribution
import org.eclipse.jface.action.ICoolBarManager
import org.eclipse.jface.action.ToolBarContributionItem
import org.eclipse.jface.action.ToolBarManager
import org.eclipse.jface.databinding.viewers.ViewersObservables
import org.eclipse.jface.viewers.ArrayContentProvider
import org.eclipse.jface.viewers.ComboViewer
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.viewers.LabelProvider
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.jface.viewers.StructuredSelection
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.digimead.digi.lib.aop.log
import org.digimead.tabuddy.desktop.payload.view.Sorting
import org.digimead.tabuddy.desktop.payload.view.Filter
import org.digimead.tabuddy.desktop.res.Messages
import org.eclipse.core.databinding.observable.Observables
import org.eclipse.core.databinding.observable.value.IValueChangeListener
import org.eclipse.core.databinding.observable.value.ValueChangeEvent

object TableToolbarView extends ToolBarManager with Loggable {
  log.debug("alive")
  /** Aggregation listener delay */
  private val aggregatorDelay = 250 // msec
  /** View entities changes aggregator */
  private val viewEventsAggregator = WritableValue(Long.box(0L))
  /** An active view filter */
  protected[table] val filter = WritableValue[Filter]
  /** The combo box with list of view filters */
  @volatile protected var filterCombo: Option[ComboViewer] = None
  /** The view filters configuration key */
  protected lazy val filterPersistenceKey = Config.persistenceKey(TableToolbarView.getClass(), "filter")
  /** An active view sorting */
  protected[table] val sorting = WritableValue[Sorting]
  /** The combo box with list of view sorting */
  @volatile protected var sortingCombo: Option[ComboViewer] = None
  /** The view sorting configuration key */
  protected lazy val sortingPersistenceKey = Config.persistenceKey(TableToolbarView.getClass(), "sorting")
  /** ToolBarContributionItem of the current ToolBar */
  @volatile protected var toolBarContributionItem: Option[ToolBarContributionItem] = None
  /** An active view definition */
  protected[table] val view = WritableValue[View]
  /** The combo box with list of view definitions */
  @volatile protected var viewCombo: Option[ComboViewer] = None
  /** The view definition configuration key */
  protected lazy val viewPersistenceKey = Config.persistenceKey(TableToolbarView.getClass(), "view")

  // view definitions
  add(new ControlContribution(null) {
    protected def createControl(parent: Composite): Control = {
      val viewer = new ComboViewer(parent, SWT.BORDER | SWT.H_SCROLL | SWT.READ_ONLY)
      viewer.getCombo.setToolTipText(Messages.views_text)
      viewer.setContentProvider(ArrayContentProvider.getInstance())
      viewer.setLabelProvider(new LabelProvider() {
        override def getText(element: Object): String = element match {
          case view: View =>
            view.name
          case unknown =>
            log.fatal("Unknown item " + unknown.getClass())
            unknown.toString
        }
      })
      Main.bindingContext.bindValue(ViewersObservables.observeDelayedValue(50,
        ViewersObservables.observeSingleSelection(viewer)), TableToolbarView.this.view)
      TableToolbarView.this.view.addChangeListener { event => Configgy.setString(viewPersistenceKey, view.value.id.toString) }
      viewCombo = Some(viewer)
      val selectedView = {
        val views = Data.getAvailableViewDefinitions
        (Configgy.getString(viewPersistenceKey) match {
          case Some(id) => views.find(_.id.toString == id)
          case None => views.headOption
        }).getOrElse(views.head)
      }
      reloadViewItems(selectedView)
      viewer.getControl()
    }
  })
  // view sortings
  add(new ControlContribution(null) {
    protected def createControl(parent: Composite): Control = {
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
      Main.bindingContext.bindValue(ViewersObservables.observeDelayedValue(50,
        ViewersObservables.observeSingleSelection(viewer)), TableToolbarView.this.sorting)
      TableToolbarView.this.sorting.addChangeListener { event => Configgy.setString(sortingPersistenceKey, sorting.value.id.toString) }
      sortingCombo = Some(viewer)
      val selectedSorting = {
        val sortings = Data.getAvailableViewSortings
        (Configgy.getString(sortingPersistenceKey) match {
          case Some(id) => sortings.find(_.id.toString == id)
          case None => sortings.headOption
        }).getOrElse(sortings.head)
      }
      reloadSortingItems(selectedSorting)
      viewer.getControl()
    }
  })
  // view filters
  add(new ControlContribution(null) {
    protected def createControl(parent: Composite): Control = {
      val viewer = new ComboViewer(parent, SWT.BORDER | SWT.H_SCROLL | SWT.READ_ONLY)
      viewer.getCombo.setToolTipText(Messages.filters_text)
      viewer.setContentProvider(ArrayContentProvider.getInstance())
      viewer.setLabelProvider(new LabelProvider() {
        override def getText(element: Object): String = element match {
          case filter: Filter =>
            filter.name
          case unknown =>
            log.fatal("Unknown item " + unknown.getClass())
            unknown.toString
        }
      })
      Main.bindingContext.bindValue(ViewersObservables.observeDelayedValue(50,
        ViewersObservables.observeSingleSelection(viewer)), TableToolbarView.this.filter)
      TableToolbarView.this.sorting.addChangeListener { event => Configgy.setString(filterPersistenceKey, filter.value.id.toString) }
      filterCombo = Some(viewer)
      val selectedFilter = {
        val filters = Data.getAvailableViewFilters
        (Configgy.getString(filterPersistenceKey) match {
          case Some(id) => filters.find(_.id.toString == id)
          case None => filters.headOption
        }).getOrElse(filters.head)
      }
      reloadFilterItems(selectedFilter)
      viewer.getControl()
    }
  })
  Data.viewDefinitions.addChangeListener { event => viewEventsAggregator.value = System.currentTimeMillis() }
  Data.viewSortings.addChangeListener { event => viewEventsAggregator.value = System.currentTimeMillis() }
  Data.viewFilters.addChangeListener { event => viewEventsAggregator.value = System.currentTimeMillis() }
  Observables.observeDelayedValue(aggregatorDelay, viewEventsAggregator).addValueChangeListener(new IValueChangeListener {
    def handleValueChange(event: ValueChangeEvent) {
      reloadViewItems(Option(view.value) getOrElse Data.getAvailableViewDefinitions.head)
      for {
        comboView <- Option(view.value)
        actualView <- Data.viewDefinitions.get(comboView.id)
      } if (!View.compareDeep(comboView, actualView))
        view.value = actualView // user modify current view
      reloadSortingItems(Option(sorting.value) getOrElse Data.getAvailableViewSortings.head)
      for {
        comboSorting <- Option(sorting.value)
        actualSorting <- Data.viewSortings.get(comboSorting.id)
      } if (!Sorting.compareDeep(comboSorting, actualSorting))
        sorting.value = actualSorting // user modify current sorting
      reloadFilterItems(Option(filter.value) getOrElse Data.getAvailableViewFilters.head)
      for {
        comboFilter <- Option(filter.value)
        actualFilter <- Data.viewFilters.get(comboFilter.id)
      } if (!Filter.compareDeep(comboFilter, actualFilter))
        filter.value = actualFilter // user modify current filter
    }
  })

  protected def getToolBarContributionItem(): Option[ToolBarContributionItem] = toolBarContributionItem orElse {
    Table.getTable.getCoolBarManager.getItems().find {
      case item: ToolBarContributionItem =>
        item.getToolBarManager() == this
      case item =>
        false
    } map { item =>
      toolBarContributionItem = Some(item.asInstanceOf[ToolBarContributionItem])
      item.asInstanceOf[ToolBarContributionItem]
    }
  }
  /** Reload view filters combo box */
  @log
  protected def reloadFilterItems(selected: Filter) = for {
    combo <- filterCombo if getControl().getItemCount() == 3 // update only if toolbar have all suitable children
    toolBarItem <- getToolBarContributionItem()
    comboToolBarItem = getControl().getItem(2)
  } {
    val previousWidth = combo.getCombo().getBounds().width
    val filters = Data.getAvailableViewFilters.toArray
    combo.setInput(filters)
    // set new width
    val newWidth = combo.getCombo().computeSize(SWT.DEFAULT, SWT.DEFAULT, true).x
    comboToolBarItem.setWidth(comboToolBarItem.getWidth() + newWidth - previousWidth)
    toolBarItem.update(ICoolBarManager.SIZE)
    // set current element
    filters.find(_.id == selected.id) match {
      case Some(selected) =>
        combo.setSelection(new StructuredSelection(selected))
      case None =>
        combo.setSelection(new StructuredSelection(filters.head))
    }
  }
  /** Reload view sortings combo box */
  @log
  protected def reloadSortingItems(selected: Sorting) = for {
    combo <- sortingCombo if getControl().getItemCount() == 3 // update only if toolbar have all suitable children
    toolBarItem <- getToolBarContributionItem()
    comboToolBarItem = getControl().getItem(1)
  } {
    val previousWidth = combo.getCombo().getBounds().width
    val sortings = Data.getAvailableViewSortings.toArray
    combo.setInput(sortings)
    // set new width
    val newWidth = combo.getCombo().computeSize(SWT.DEFAULT, SWT.DEFAULT, true).x
    comboToolBarItem.setWidth(comboToolBarItem.getWidth() + newWidth - previousWidth)
    toolBarItem.update(ICoolBarManager.SIZE)
    // set current element
    sortings.find(_.id == selected.id) match {
      case Some(selected) =>
        combo.setSelection(new StructuredSelection(selected))
      case None =>
        combo.setSelection(new StructuredSelection(sortings.head))
    }
  }
  /** Reload view definitions combo box */
  @log
  protected def reloadViewItems(selected: View) = for {
    combo <- viewCombo if getControl().getItemCount() == 3 // update only if toolbar have all suitable children
    toolBarItem <- getToolBarContributionItem()
    comboToolBarItem = getControl().getItem(0)
  } {
    val previousWidth = combo.getCombo().getBounds().width
    val views = Data.getAvailableViewDefinitions.toArray
    combo.setInput(views)
    // set new width
    val newWidth = combo.getCombo().computeSize(SWT.DEFAULT, SWT.DEFAULT, true).x
    comboToolBarItem.setWidth(comboToolBarItem.getWidth() + newWidth - previousWidth)
    toolBarItem.update(ICoolBarManager.SIZE)
    // set current element
    views.find(_.id == selected.id) match {
      case Some(selected) =>
        combo.setSelection(new StructuredSelection(selected))
      case None =>
        combo.setSelection(new StructuredSelection(views.head))
    }
  }
}
