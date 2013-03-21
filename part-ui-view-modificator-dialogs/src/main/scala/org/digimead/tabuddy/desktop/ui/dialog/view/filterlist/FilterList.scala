/**
 * This file is part of the TABuddy project.
 * Copyright (c) 2012-2013 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.ui.dialog.view.filterlist

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import java.util.regex.Pattern

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future
import scala.ref.WeakReference

import org.digimead.digi.lib.log.Loggable
import org.digimead.tabuddy.desktop.Main
import org.digimead.tabuddy.desktop.job.view.JobModifyFilter
import org.digimead.tabuddy.desktop.payload.Payload
import org.digimead.tabuddy.desktop.payload.Payload.payload2implementation
import org.digimead.tabuddy.desktop.payload.view.Filter
import org.digimead.tabuddy.desktop.res.Messages
import org.digimead.tabuddy.desktop.res.dialog.view.CustomMessages
import org.digimead.tabuddy.desktop.support.WritableList
import org.digimead.tabuddy.desktop.support.WritableList.wrapper2underlying
import org.digimead.tabuddy.desktop.support.WritableValue
import org.digimead.tabuddy.desktop.support.WritableValue.wrapper2underlying
import org.digimead.tabuddy.desktop.ui.BaseElement
import org.digimead.tabuddy.desktop.ui.Default
import org.digimead.tabuddy.desktop.ui.dialog.Dialog
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Model.model2implementation
import org.eclipse.core.databinding.observable.ChangeEvent
import org.eclipse.jface.action.Action
import org.eclipse.jface.action.ActionContributionItem
import org.eclipse.jface.action.IAction
import org.eclipse.jface.action.IMenuListener
import org.eclipse.jface.action.IMenuManager
import org.eclipse.jface.action.MenuManager
import org.eclipse.jface.databinding.swt.WidgetProperties
import org.eclipse.jface.databinding.viewers.ObservableListContentProvider
import org.eclipse.jface.databinding.viewers.ViewersObservables
import org.eclipse.jface.dialogs.IDialogConstants
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.jface.viewers.StructuredSelection
import org.eclipse.jface.viewers.TableViewer
import org.eclipse.jface.viewers.Viewer
import org.eclipse.jface.viewers.ViewerComparator
import org.eclipse.jface.viewers.ViewerFilter
import org.eclipse.swt.SWT
import org.eclipse.swt.events.DisposeEvent
import org.eclipse.swt.events.DisposeListener
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Event
import org.eclipse.swt.widgets.Listener
import org.eclipse.swt.widgets.Shell
import org.eclipse.swt.widgets.TableItem

class FilterList(val parentShell: Shell, val initial: List[Filter])
  extends org.digimead.tabuddy.desktop.res.dialog.view.FilterList(parentShell) with Dialog with Loggable {
  /** The actual content */
  protected[filterlist] val actual = WritableList(initial)
  /** The auto resize lock */
  protected val autoResizeLock = new ReentrantLock()
  /** The property representing filter field content */
  protected val filterField = WritableValue("")
  /** The property representing a selected view */
  protected val selected = WritableValue[Filter]
  assert(FilterList.dialog.isEmpty, "FilterList dialog is already active")

  def getModifiedFilters(): Set[Filter] = actual.sortBy(_.name).toSet

  /** Auto resize table viewer columns */
  protected def autoresize() = if (autoResizeLock.tryLock()) try {
    Thread.sleep(50)
    Main.execNGet {
      if (!getTableViewer.getTable.isDisposed()) {
        adjustColumnWidth(getTableViewerColumnName(), Default.columnPadding)
        getTableViewer.refresh()
      }
    }
  } finally {
    autoResizeLock.unlock()
  }
  /** Create contents of the dialog. */
  override protected def createDialogArea(parent: Composite): Control = {
    val result = super.createDialogArea(parent)
    new ActionContributionItem(FilterList.ActionCreate).fill(getCompositeFooter())
    new ActionContributionItem(FilterList.ActionCreateFrom).fill(getCompositeFooter())
    new ActionContributionItem(FilterList.ActionEdit).fill(getCompositeFooter())
    new ActionContributionItem(FilterList.ActionRemove).fill(getCompositeFooter())
    FilterList.ActionCreateFrom.setEnabled(false)
    FilterList.ActionEdit.setEnabled(false)
    FilterList.ActionRemove.setEnabled(false)
    initTableViews()
    val actualListener = actual.addChangeListener { event =>
      if (FilterList.ActionAutoResize.isChecked())
        future { autoresize() }
      updateOK()
    }
    // Add the dispose listener
    getShell().addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) {
        actual.removeChangeListener(actualListener)
        FilterList.dialog = None
      }
    })
    // Set the dialog message
    setMessage(CustomMessages.viewFilterListDescription_text.format(Model.eId.name))
    // Set the dialog window title
    getShell().setText(CustomMessages.viewFilterListDialog_text.format(Model.eId.name))
    FilterList.dialog = Some(this)
    result
  }
  /** Initialize tableViewer */
  protected def initTableViews() {
    val viewer = getTableViewer()
    viewer.setContentProvider(new ObservableListContentProvider())
    getTableViewerColumnName.setLabelProvider(new ColumnName.TLabelProvider)
    getTableViewerColumnName.setEditingSupport(new ColumnName.TEditingSupport(viewer, this))
    getTableViewerColumnName.getColumn.addSelectionListener(new FilterList.FilterSelectionAdapter(WeakReference(viewer), 1))
    getTableViewerColumnDescription.setLabelProvider(new ColumnDescription.TLabelProvider)
    getTableViewerColumnDescription.setEditingSupport(new ColumnDescription.TEditingSupport(viewer, this))
    getTableViewerColumnDescription.getColumn.addSelectionListener(new FilterList.FilterSelectionAdapter(WeakReference(viewer), 2))
    // Add a SWT.CHECK support
    viewer.getTable.addListener(SWT.Selection, new Listener() {
      def handleEvent(event: Event) = if (event.detail == SWT.CHECK)
        event.item match {
          case tableItem: TableItem =>
            val index = tableItem.getParent().indexOf(tableItem)
            viewer.getElementAt(index) match {
              case before: Filter =>
                if (before.availability != tableItem.getChecked()) {
                  val after = before.copy(availability = tableItem.getChecked())
                  updateActualFilter(before, after)
                }
              case item =>
                log.fatal(s"unknown item $item")
            }
          case item =>
            log.fatal(s"unknown item $item")
        }
    })
    // Activate the tooltip support for the viewer
    ColumnViewerToolTipSupport.enableFor(viewer)
    // Add the context menu
    val menuMgr = new MenuManager()
    val menu = menuMgr.createContextMenu(viewer.getControl)
    menuMgr.addMenuListener(new IMenuListener() {
      override def menuAboutToShow(manager: IMenuManager) {
        manager.add(FilterList.ActionAutoResize)
      }
    })
    menuMgr.setRemoveAllWhenShown(true)
    viewer.getControl.setMenu(menu)
    // Add the selection listener
    viewer.addSelectionChangedListener(new ISelectionChangedListener() {
      override def selectionChanged(event: SelectionChangedEvent) = event.getSelection() match {
        case selection: IStructuredSelection if !selection.isEmpty() =>
          val filter = selection.getFirstElement().asInstanceOf[Filter]
          FilterList.ActionCreateFrom.setEnabled(true)
          FilterList.ActionEdit.setEnabled(true)
          FilterList.ActionRemove.setEnabled(Filter.default != filter) // exclude predefined
        case selection =>
          FilterList.ActionCreateFrom.setEnabled(false)
          FilterList.ActionEdit.setEnabled(false)
          FilterList.ActionRemove.setEnabled(false)
      }
    })
    // Add the filter
    Main.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextFilter()), filterField)
    val filter = new AtomicReference(".*".r.pattern)
    val filterListener = new BaseElement.RegexFilterListener(filter) {
      override def handleChange(event: ChangeEvent) {
        super.handleChange(event)
        getTableViewer.refresh()
      }
    }
    filterField.underlying.addChangeListener(filterListener)
    viewer.setFilters(Array(new FilterList.TableFilter(filter)))
    // Set sorting
    viewer.setComparator(new FilterList.FilterComparator)
    viewer.setInput(actual.underlying)
    Main.bindingContext.bindValue(ViewersObservables.observeSingleSelection(viewer), selected)
  }
  /** On dialog active */
  override protected def onActive = {
    updateOK()
    if (FilterList.ActionAutoResize.isChecked())
      future { autoresize() }
    // prevent interference with the size calculation
    getTextFilter().setMessage(Messages.lookupFilter_text);
  }
  /** Updates an actual element template */
  protected[filterlist] def updateActualFilter(before: Filter, after: Filter) {
    val index = actual.indexOf(before)
    actual.update(index, after)
    if (index == actual.size - 1)
      getTableViewer.refresh() // Workaround for the JFace bug. Force the last element modification.
    getTableViewer.setSelection(new StructuredSelection(after), true)
    if (FilterList.ActionAutoResize.isChecked())
      future { autoresize() }
  }
  /** Update OK button state */
  protected def updateOK() = Option(getButton(IDialogConstants.OK_ID)).
    foreach(_.setEnabled(!{ initial.sameElements(actual) && (initial, actual).zipped.forall(Filter.compareDeep(_, _)) }))
}

object FilterList extends Loggable {
  /** There is may be only one dialog instance at time */
  @volatile private var dialog: Option[FilterList] = None
  /** Default sort direction */
  private val defaultDirection = Default.ASCENDING
  /** Actual sort direction */
  @volatile private var sortDirection = defaultDirection
  /** Actual sortBy column index */
  @volatile private var sortColumn = 0

  /** Generate new name: old name + ' Copy' + N */
  protected def getNewFilterCopyName(name: String, filterList: List[Filter]): String = {
    val sameIds = immutable.HashSet(filterList.filter(_.name.startsWith(name)).map(_.name).toSeq: _*)
    var n = 0
    var newName = name + " " + Messages.copy_item_text
    while (sameIds(newName)) {
      n += 1
      newName = name + " " + Messages.copy_item_text + n
    }
    newName
  }
  /** Apply a f(x) to the selected filter if any */
  def filter[T](f: (FilterList, Filter) => T): Option[T] =
    dialog.flatMap(d => Option(d.selected.value).map(f(d, _)))

  class FilterComparator extends ViewerComparator {
    private var _column = FilterList.sortColumn
    private var _direction = FilterList.sortDirection

    /** Active column getter */
    def column = _column
    /** Active column setter */
    def column_=(arg: Int) {
      _column = arg
      FilterList.sortColumn = _column
      _direction = FilterList.defaultDirection
      FilterList.sortDirection = _direction
    }
    /** Sorting direction */
    def direction = _direction
    /**
     * Returns a negative, zero, or positive number depending on whether
     * the first element is less than, equal to, or greater than
     * the second element.
     */
    override def compare(viewer: Viewer, e1: Object, e2: Object): Int = {
      val entity1 = e1.asInstanceOf[Filter]
      val entity2 = e2.asInstanceOf[Filter]
      val rc = column match {
        case 0 => entity1.name.compareTo(entity2.name)
        case 1 => entity1.description.compareTo(entity2.description)
        case index =>
          log.fatal(s"unknown column with index $index"); 0
      }
      if (_direction) -rc else rc
    }
    /** Switch comparator direction */
    def switchDirection() {
      _direction = !_direction
      FilterList.sortDirection = _direction
    }
  }
  class TableFilter(filter: AtomicReference[Pattern]) extends ViewerFilter {
    override def select(viewer: Viewer, parentElement: AnyRef, element: AnyRef): Boolean = {
      val pattern = filter.get
      val item = element.asInstanceOf[Filter]
      pattern.matcher(item.name.toLowerCase()).matches() ||
        pattern.matcher(item.description.toLowerCase()).matches()
    }
  }
  class FilterSelectionAdapter(tableViewer: WeakReference[TableViewer], column: Int) extends SelectionAdapter {
    override def widgetSelected(e: SelectionEvent) = {
      tableViewer.get.foreach(viewer => viewer.getComparator() match {
        case comparator: FilterComparator if comparator.column == column =>
          comparator.switchDirection()
          viewer.refresh()
        case comparator: FilterComparator =>
          comparator.column = column
          viewer.refresh()
        case _ =>
      })
    }
  }
  /*
   * Actions
   */
  object ActionAutoResize extends Action(Messages.autoresize_key, IAction.AS_CHECK_BOX) {
    setChecked(true)
    override def run = if (isChecked()) FilterList.dialog.foreach(_.autoresize)
  }
  object ActionCreate extends Action(Messages.create_text) with Loggable {
    override def run = FilterList.dialog.foreach { dialog =>
      val newFilterName = Payload.generateNew(Messages.newFilterName_text, " ", newName => dialog.actual.exists(_.name == newName))
      val newFilter = Filter(UUID.randomUUID(), newFilterName, "", true, Set())
      JobModifyFilter(newFilter, dialog.actual.toSet).foreach(_.setOnSucceeded { job =>
        job.getValue.foreach { case (filter) => Main.exec { dialog.actual += filter } }
      }.execute)
    }
  }
  object ActionCreateFrom extends Action(Messages.createFrom_text) with Loggable {
    override def run = FilterList.filter { (dialog, selected) =>
      val name = getNewFilterCopyName(selected.name, dialog.actual.toList)
      val newFilter = selected.copy(id = UUID.randomUUID(), name = name)
      JobModifyFilter(newFilter, dialog.actual.toSet).foreach(_.setOnSucceeded { job =>
        job.getValue.foreach {
          case (filter) => Main.exec {
            assert(!dialog.actual.exists(_.id == filter.id), "Filter %s already exists".format(filter))
            dialog.actual += filter
          }
        }
      }.execute)
    }
  }
  object ActionEdit extends Action(Messages.edit_text) {
    override def run = FilterList.filter { (dialog, before) =>
      JobModifyFilter(before, dialog.actual.toSet).
        foreach(_.setOnSucceeded { job =>
          job.getValue.foreach { case (after) => Main.exec { dialog.updateActualFilter(before, after) } }
        }.execute)
    }
  }
  object ActionRemove extends Action(Messages.remove_text) {
    override def run = FilterList.filter { (dialog, selected) =>
      dialog.actual -= selected
    }
  }
}
