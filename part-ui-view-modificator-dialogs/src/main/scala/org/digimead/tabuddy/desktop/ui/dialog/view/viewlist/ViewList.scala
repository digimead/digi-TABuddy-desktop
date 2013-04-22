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

package org.digimead.tabuddy.desktop.ui.dialog.view.viewlist

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import java.util.regex.Pattern

import scala.collection.immutable
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future
import scala.ref.WeakReference

import org.digimead.digi.lib.log.Loggable
import org.digimead.tabuddy.desktop.Main
import org.digimead.tabuddy.desktop.job.view.JobModifyView
import org.digimead.tabuddy.desktop.payload.Payload
import org.digimead.tabuddy.desktop.payload.Payload.payload2implementation
import org.digimead.tabuddy.desktop.payload.view.View
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

class ViewList(val parentShell: Shell, val initial: List[View])
  extends org.digimead.tabuddy.desktop.res.dialog.view.ViewList(parentShell) with Dialog with Loggable {
  /** The actual content */
  protected[viewlist] val actual = WritableList(initial)
  /** The auto resize lock */
  protected val autoResizeLock = new ReentrantLock()
  /** The property representing view filter content */
  protected val filterViews = WritableValue("")
  /** The property representing a selected view */
  protected val selected = WritableValue[View]
  assert(ViewList.dialog.isEmpty, "ViewList dialog is already active")

  def getModifiedViews(): Set[View] = actual.sortBy(_.name).toSet

  /** Auto resize tableviewer columns */
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
    new ActionContributionItem(ViewList.ActionCreate).fill(getCompositeFooter())
    new ActionContributionItem(ViewList.ActionCreateFrom).fill(getCompositeFooter())
    new ActionContributionItem(ViewList.ActionEdit).fill(getCompositeFooter())
    new ActionContributionItem(ViewList.ActionRemove).fill(getCompositeFooter())
    ViewList.ActionCreateFrom.setEnabled(false)
    ViewList.ActionEdit.setEnabled(false)
    ViewList.ActionRemove.setEnabled(false)
    initTableViews()
    val actualListener = actual.addChangeListener { event =>
      if (ViewList.ActionAutoResize.isChecked())
        future { autoresize() } onFailure {
          case e: Exception => log.error(e.getMessage(), e)
          case e => log.error(e.toString())
        }
      updateOK()
    }
    // Add the dispose listener
    getShell().addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) {
        actual.removeChangeListener(actualListener)
        ViewList.dialog = None
      }
    })
    // Set the dialog message
    setMessage(CustomMessages.viewListDescription_text.format(Model.eId.name))
    // Set the dialog window title
    getShell().setText(CustomMessages.viewListDialog_text.format(Model.eId.name))
    ViewList.dialog = Some(this)
    result
  }
  /** Allow external access for scala classes */
  override protected def getTableViewer() = super.getTableViewer
  /** Initialize tableTypeList */
  protected def initTableViews() {
    val viewer = getTableViewer()
    viewer.setContentProvider(new ObservableListContentProvider())
    getTableViewerColumnName.setLabelProvider(new ColumnName.TLabelProvider)
    getTableViewerColumnName.setEditingSupport(new ColumnName.TEditingSupport(viewer, this))
    getTableViewerColumnName.getColumn.addSelectionListener(new ViewList.ViewSelectionAdapter(WeakReference(viewer), 1))
    getTableViewerColumnDescription.setLabelProvider(new ColumnDescription.TLabelProvider)
    getTableViewerColumnDescription.setEditingSupport(new ColumnDescription.TEditingSupport(viewer, this))
    getTableViewerColumnDescription.getColumn.addSelectionListener(new ViewList.ViewSelectionAdapter(WeakReference(viewer), 2))
    // Add a SWT.CHECK support
    viewer.getTable.addListener(SWT.Selection, new Listener() {
      def handleEvent(event: Event) = if (event.detail == SWT.CHECK)
        event.item match {
          case tableItem: TableItem =>
            val index = tableItem.getParent().indexOf(tableItem)
            viewer.getElementAt(index) match {
              case before: View =>
                if (before.availability != tableItem.getChecked()) {
                  val after = before.copy(availability = tableItem.getChecked())
                  updateActualView(before, after)
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
        manager.add(ViewList.ActionAutoResize)
      }
    })
    menuMgr.setRemoveAllWhenShown(true)
    viewer.getControl.setMenu(menu)
    // Add the selection listener
    viewer.addSelectionChangedListener(new ISelectionChangedListener() {
      override def selectionChanged(event: SelectionChangedEvent) = event.getSelection() match {
        case selection: IStructuredSelection if !selection.isEmpty() =>
          val view = selection.getFirstElement().asInstanceOf[View]
          ViewList.ActionCreateFrom.setEnabled(true)
          ViewList.ActionEdit.setEnabled(true)
          ViewList.ActionRemove.setEnabled(View.default != view) // exclude predefined
        case selection =>
          ViewList.ActionCreateFrom.setEnabled(false)
          ViewList.ActionEdit.setEnabled(false)
          ViewList.ActionRemove.setEnabled(false)
      }
    })
    // Add the filter
    Main.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextFilter()), filterViews)
    val filter = new AtomicReference(".*".r.pattern)
    val filterListener = new BaseElement.RegexFilterListener(filter) {
      override def handleChange(event: ChangeEvent) {
        super.handleChange(event)
        getTableViewer.refresh()
      }
    }
    filterViews.underlying.addChangeListener(filterListener)
    viewer.setFilters(Array(new ViewList.ViewFilter(filter)))
    // Set sorting
    viewer.setComparator(new ViewList.ViewComparator)
    viewer.setInput(actual.underlying)
    Main.bindingContext.bindValue(ViewersObservables.observeSingleSelection(viewer), selected)
  }
  /** On dialog active */
  override protected def onActive = {
    updateOK()
    if (ViewList.ActionAutoResize.isChecked())
      future { autoresize() } onFailure {
        case e: Exception => log.error(e.getMessage(), e)
        case e => log.error(e.toString())
      }
  }
  /** Updates an actual element template */
  protected[viewlist] def updateActualView(before: View, after: View) {
    val index = actual.indexOf(before)
    actual.update(index, after)
    if (index == actual.size - 1)
      getTableViewer.refresh() // Workaround for the JFace bug. Force the last element modification.
    getTableViewer.setSelection(new StructuredSelection(after), true)
  }
  /** Update OK button state */
  protected def updateOK() = Option(getButton(IDialogConstants.OK_ID)).
    foreach(_.setEnabled(!{ initial.sameElements(actual) && (initial, actual).zipped.forall(View.compareDeep(_, _)) }))
}

object ViewList extends Loggable {
  /** There is may be only one dialog instance at time */
  @volatile private var dialog: Option[ViewList] = None
  /** Default sort direction */
  private val defaultDirection = Default.ASCENDING
  /** Actual sort direction */
  @volatile private var sortDirection = defaultDirection
  /** Actual sortBy column index */
  @volatile private var sortColumn = 1

  /** Generate new name: old name + ' Copy ' + N */
  protected def getNewViewCopyName(name: String, viewList: List[View]): String = {
    val sameIds = immutable.HashSet(viewList.filter(_.name.startsWith(name)).map(_.name).toSeq: _*)
    var n = 0
    var newName = name + " " + Messages.copy_item_text
    while (sameIds(newName)) {
      n += 1
      newName = name + " " + Messages.copy_item_text + " " + n
    }
    newName
  }
  /** Apply a f(x) to the selected view if any */
  def view[T](f: (ViewList, View) => T): Option[T] =
    dialog.flatMap(d => Option(d.selected.value).map(f(d, _)))

  class ViewComparator extends ViewerComparator {
    private var _column = ViewList.sortColumn
    private var _direction = ViewList.sortDirection

    /** Active column getter */
    def column = _column
    /** Active column setter */
    def column_=(arg: Int) {
      _column = arg
      ViewList.sortColumn = _column
      _direction = ViewList.defaultDirection
      ViewList.sortDirection = _direction
    }
    /** Sorting direction */
    def direction = _direction
    /**
     * Returns a negative, zero, or positive number depending on whether
     * the first element is less than, equal to, or greater than
     * the second element.
     */
    override def compare(viewer: Viewer, e1: Object, e2: Object): Int = {
      val entity1 = e1.asInstanceOf[View]
      val entity2 = e2.asInstanceOf[View]
      val rc = column match {
        case 0 => entity1.availability.compareTo(entity2.availability)
        case 1 => entity1.name.compareTo(entity2.name)
        case 2 => entity1.description.compareTo(entity2.description)
        case index =>
          log.fatal(s"unknown column with index $index"); 0
      }
      if (_direction) -rc else rc
    }
    /** Switch comparator direction */
    def switchDirection() {
      _direction = !_direction
      ViewList.sortDirection = _direction
    }
  }
  class ViewFilter(filter: AtomicReference[Pattern]) extends ViewerFilter {
    override def select(viewer: Viewer, parentElement: AnyRef, element: AnyRef): Boolean = {
      val pattern = filter.get
      val item = element.asInstanceOf[View]
      pattern.matcher(item.name.toLowerCase()).matches() ||
        pattern.matcher(item.description.toLowerCase()).matches()
    }
  }
  class ViewSelectionAdapter(tableViewer: WeakReference[TableViewer], column: Int) extends SelectionAdapter {
    override def widgetSelected(e: SelectionEvent) = {
      tableViewer.get.foreach(viewer => viewer.getComparator() match {
        case comparator: ViewComparator if comparator.column == column =>
          comparator.switchDirection()
          viewer.refresh()
        case comparator: ViewComparator =>
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
    override def run = if (isChecked()) ViewList.dialog.foreach(_.autoresize)
  }
  object ActionCreate extends Action(Messages.create_text) with Loggable {
    override def run = dialog.foreach { dialog =>
      val newViewName = Payload.generateNew(Messages.newViewName_text, " ", newName => dialog.actual.exists(_.name == newName))
      val newView = View(UUID.randomUUID(), newViewName, "", true, mutable.LinkedHashSet(), mutable.LinkedHashSet(), mutable.LinkedHashSet())
      JobModifyView(newView, dialog.actual.toSet).foreach(_.setOnSucceeded { job =>
        job.getValue.foreach {
          case (view) => Main.exec {
            dialog.actual += view
            dialog.getTableViewer.setSelection(new StructuredSelection(view), true)
          }
        }
      }.execute)
    }
  }
  object ActionCreateFrom extends Action(Messages.createFrom_text) with Loggable {
    override def run = ViewList.view { (dialog, selected) =>
      val name = getNewViewCopyName(selected.name, dialog.actual.toList)
      val newView = selected.copy(id = UUID.randomUUID(), name = name)
      JobModifyView(newView, dialog.actual.toSet).foreach(_.setOnSucceeded { job =>
        job.getValue.foreach {
          case (view) => Main.exec {
            assert(!dialog.actual.exists(_.id == view.id), "View %s already exists".format(view))
            dialog.actual += view
            dialog.getTableViewer.setSelection(new StructuredSelection(view), true)
          }
        }
      }.execute)
    }
  }
  object ActionEdit extends Action(Messages.edit_text) {
    override def run = ViewList.view { (dialog, before) =>
      JobModifyView(before, dialog.actual.toSet).
        foreach(_.setOnSucceeded { job =>
          job.getValue.foreach { case (after) => Main.exec { dialog.updateActualView(before, after) } }
        }.execute)
    }
  }
  object ActionRemove extends Action(Messages.remove_text) {
    override def run = ViewList.view { (dialog, selected) =>
      dialog.actual -= selected
    }
  }
}
