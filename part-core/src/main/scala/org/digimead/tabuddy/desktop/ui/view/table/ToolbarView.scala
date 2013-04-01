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
import org.digimead.tabuddy.desktop.payload.view.Filter
import org.digimead.tabuddy.desktop.payload.view.Sorting
import org.digimead.tabuddy.desktop.payload.view.View
import org.digimead.tabuddy.desktop.res.Messages
import org.digimead.tabuddy.desktop.support.WritableValue
import org.digimead.tabuddy.desktop.support.WritableValue.wrapper2underlying
import org.eclipse.core.databinding.observable.ChangeEvent
import org.eclipse.core.databinding.observable.IChangeListener
import org.eclipse.core.databinding.observable.Observables
import org.eclipse.core.databinding.observable.value.IValueChangeListener
import org.eclipse.core.databinding.observable.value.ValueChangeEvent
import org.eclipse.core.databinding.observable.value.{ WritableValue => OriginalWritableValue }
import org.eclipse.jface.action.ControlContribution
import org.eclipse.jface.action.ICoolBarManager
import org.eclipse.jface.action.ToolBarContributionItem
import org.eclipse.jface.action.ToolBarManager
import org.eclipse.jface.databinding.viewers.ViewersObservables
import org.eclipse.jface.viewers.ArrayContentProvider
import org.eclipse.jface.viewers.ComboViewer
import org.eclipse.jface.viewers.LabelProvider
import org.eclipse.jface.viewers.StructuredSelection
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.core.databinding.observable.value.IObservableValue

object ToolbarView extends ToolBarManager with Loggable {
  log.debug("alive")
  /** Aggregation listener delay */
  private val aggregatorDelay = 250 // msec
  /** View entities changes aggregator */
  private val viewEventsAggregator = WritableValue(Long.box(0L))
  /** An active view filter */
  protected[table] val filter = WritableValue[Option[Filter]](None)
  /** The combo box with list of view filters */
  @volatile protected var filterCombo: Option[ComboViewer] = None
  /** An active view sorting */
  protected[table] val sorting = WritableValue[Option[Sorting]](None)
  /** The combo box with list of view sorting */
  @volatile protected var sortingCombo: Option[ComboViewer] = None
  /** ToolBarContributionItem of the current ToolBar */
  @volatile protected var toolBarContributionItem: Option[ToolBarContributionItem] = None
  /** An active view definition */
  protected[table] val view = WritableValue[Option[View]](None)
  /** The combo box with list of view definitions */
  @volatile protected var viewCombo: Option[ComboViewer] = None
  /** The view definition configuration key */
  protected lazy val viewPersistenceKey = Config.persistenceKey(ToolbarView.getClass(), "view")
  /** The view configuration key prefix */
  protected lazy val viewPersistenceKeyPrefix = Config.persistenceKey(ToolbarView.getClass(), "view_")
  /** The filter configuration key prefix */
  protected lazy val viewPersistenceKeyPrefixFilter = "filter_"
  /** The sorting configuration key prefix */
  protected lazy val viewPersistenceKeyPrefixSorting = "sorting_"

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
      reloadViewItems()
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
      ViewersObservables.observeDelayedValue(50, ViewersObservables.observeSingleSelection(viewer)).addChangeListener(new IChangeListener {
        override def handleChange(event: ChangeEvent) =
          ToolbarView.this.sorting.value = Some(event.getObservable.asInstanceOf[IObservableValue].getValue().asInstanceOf[Sorting])
      })
      ToolbarView.this.sorting.addChangeListener { (_, _) => setSelected }
      sortingCombo = Some(viewer)
      reloadSortingItems()
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
      ViewersObservables.observeDelayedValue(50, ViewersObservables.observeSingleSelection(viewer)).addChangeListener(new IChangeListener {
        override def handleChange(event: ChangeEvent) =
          ToolbarView.this.filter.value = Some(event.getObservable.asInstanceOf[IObservableValue].getValue().asInstanceOf[Filter])
      })
      ToolbarView.this.filter.addChangeListener { (_, _) => setSelected }
      filterCombo = Some(viewer)
      reloadFilterItems()
      viewer.getControl()
    }
  })
  Data.viewDefinitions.addChangeListener { event => viewEventsAggregator.value = System.currentTimeMillis() }
  Data.viewSortings.addChangeListener { event => viewEventsAggregator.value = System.currentTimeMillis() }
  Data.viewFilters.addChangeListener { event => viewEventsAggregator.value = System.currentTimeMillis() }
  Observables.observeDelayedValue(aggregatorDelay, viewEventsAggregator).addValueChangeListener(new IValueChangeListener {
    def handleValueChange(event: ValueChangeEvent) {
      reloadViewItems()
      for {
        comboView <- view.value
        actualView <- Data.viewDefinitions.get(comboView.id)
      } if (!View.compareDeep(comboView, actualView))
        view.value = Some(actualView) // user modify current view
      reloadSortingItems()
      for {
        comboSorting <- sorting.value
        actualSorting <- Data.viewSortings.get(comboSorting.id)
      } if (!Sorting.compareDeep(comboSorting, actualSorting))
        sorting.value = Some(actualSorting) // user modify current sorting
      reloadFilterItems()
      for {
        comboFilter <- filter.value
        actualFilter <- Data.viewFilters.get(comboFilter.id)
      } if (!Filter.compareDeep(comboFilter, actualFilter))
        filter.value = Some(actualFilter) // user modify current filter
    }
  })

  def getSelectedFilter(view: View, filters: Seq[Filter]): Filter = {
    val key = viewPersistenceKeyPrefix + view.id
    Configgy.getList(key) match {
      case Seq(sorting, filter) =>
        try {
          val id = filter.substring(viewPersistenceKeyPrefixFilter.length())
          filters.find(_.id.toString == id)
        } catch {
          case e: Throwable =>
            log.warn("unable to load filter id from configuration: " + e.getMessage())
            None
        }
      case _ => filters.headOption
    }
  } getOrElse Filter.default
  def getSelectedSorting(view: View, sortings: Seq[Sorting]): Sorting = {
    val key = viewPersistenceKeyPrefix + view.id
    Configgy.getList(key) match {
      case Seq(sorting, filter) =>
        try {
          val id = sorting.substring(viewPersistenceKeyPrefixSorting.length())
          sortings.find(_.id.toString == id)
        } catch {
          case e: Throwable =>
            log.warn("unable to load filter id from configuration: " + e.getMessage())
            None
        }
      case _ => sortings.headOption
    }
  } getOrElse Sorting.default
  /** Returns last selected view from configuration */
  def getSelectedView(views: Seq[View]): View = {
    Configgy.getString(viewPersistenceKey) match {
      case Some(id) => views.find(_.id.toString == id)
      case None => views.headOption
    }
  } getOrElse View.default
  /** Returns CoolBar contribution item */
  protected def getToolBarContributionItem(): Option[ToolBarContributionItem] = toolBarContributionItem orElse {
    TableView.withContext(getControl().getShell()) { (context, view) =>
      view.getCoolBarManager.getItems().find {
        case item: ToolBarContributionItem =>
          item.getToolBarManager() == this
        case item =>
          false
      } map { item =>
        toolBarContributionItem = Some(item.asInstanceOf[ToolBarContributionItem])
        item.asInstanceOf[ToolBarContributionItem]
      }
    }.flatten
  }
  /** Reload view filters combo box */
  protected def reloadFilterItems() = for {
    combo <- filterCombo if getControl().getItemCount() == 3 // update only if toolbar have all suitable children
    toolBarItem <- getToolBarContributionItem()
    comboToolBarItem = getControl().getItem(2)
    view <- view.value
  } {
    log.debug("reload filter combo")
    // dirty hack for combo resize
    // sometimes combo.getCombo.computeSize returns cached value under GTK
    filter.value = None
    combo.getCombo.removeAll()
    combo.getCombo.add(" ")
    combo.getCombo.update()
    Main.exec {
      val previousWidth = combo.getCombo().getBounds().width
      val available = Data.getAvailableViewFilters
      val filters = Filter.default +: view.filters.flatMap(id => available.find(_.id == id)).toArray
      combo.setInput(filters)
      // set new width
      val newWidth = combo.getCombo().computeSize(SWT.DEFAULT, SWT.DEFAULT, true).x
      comboToolBarItem.setWidth(comboToolBarItem.getWidth() + newWidth - previousWidth)
      toolBarItem.update(ICoolBarManager.SIZE)
      val selected = getSelectedFilter(view, filters)
      filter.value = Some(selected)
      combo.setSelection(new StructuredSelection(selected))
    }
  }
  /** Reload view sortings combo box */
  protected def reloadSortingItems() = for {
    combo <- sortingCombo if getControl().getItemCount() == 3 // update only if toolbar have all suitable children
    toolBarItem <- getToolBarContributionItem()
    comboToolBarItem = getControl().getItem(1)
    view <- view.value
  } {
    log.debug("reload sorting combo")
    // dirty hack for combo resize
    // sometimes combo.getCombo.computeSize returns cached value under GTK
    sorting.value = None
    combo.getCombo.removeAll()
    combo.getCombo.add(" ")
    combo.getCombo.update()
    Main.exec {
      val previousWidth = combo.getCombo().getBounds().width
      val available = Data.getAvailableViewSortings
      val sortings = Sorting.default +: view.sortings.flatMap(id => available.find(_.id == id)).toArray
      combo.setInput(sortings)
      // set new width
      val newWidth = combo.getCombo().computeSize(SWT.DEFAULT, SWT.DEFAULT, true).x
      comboToolBarItem.setWidth(comboToolBarItem.getWidth() + newWidth - previousWidth)
      toolBarItem.update(ICoolBarManager.SIZE)
      val selected = getSelectedSorting(view, sortings)
      sorting.value = Some(selected)
      combo.setSelection(new StructuredSelection(selected))
    }
  }
  /** Reload view definitions combo box */
  protected def reloadViewItems() = for {
    combo <- viewCombo if getControl().getItemCount() == 3 // update only if toolbar have all suitable children
    toolBarItem <- getToolBarContributionItem()
    comboToolBarItem = getControl().getItem(0)
  } {
    log.debug("reload view combo")
    // dirty hack for combo resize
    // sometimes combo.getCombo.computeSize returns cached value under GTK
    view.value = None
    combo.getCombo.removeAll()
    combo.getCombo.add(" ")
    combo.getCombo.update()
    Main.exec {
      val previousWidth = combo.getCombo().getBounds().width
      val views = Data.getAvailableViewDefinitions.toArray
      combo.setInput(views)
      // set new width
      val newWidth = combo.getCombo().computeSize(SWT.DEFAULT, SWT.DEFAULT, true).x
      comboToolBarItem.setWidth(comboToolBarItem.getWidth() + newWidth - previousWidth)
      toolBarItem.update(ICoolBarManager.SIZE)
      val selected = getSelectedView(views)
      view.value = Some(selected)
      combo.setSelection(new StructuredSelection(selected))
    }
  }
  /** Save selected value to configuration */
  def setSelected(): Unit = for {
    view <- view.value
    sorting <- sorting.value
    filter <- filter.value
  } {
    val key = viewPersistenceKeyPrefix + view.id
    Configgy.setString(viewPersistenceKey, view.id.toString())
    Configgy.setList(key, Seq(viewPersistenceKeyPrefixSorting + sorting.id,
      viewPersistenceKeyPrefixFilter + filter.id))
  }
}
