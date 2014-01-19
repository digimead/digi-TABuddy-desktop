/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2012-2014 Alexey Aksenov ezh@ezh.msk.ru
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
 * that is created or manipulated using TA Buddy.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the TA Buddy software without
 * disclosing the source code of your own applications.
 * These activities include: offering paid services to customers,
 * serving files in a web or/and network application,
 * shipping TA Buddy with a closed source product.
 *
 * For more information, please contact Digimead Team at this
 * address: ezh@ezh.msk.ru
 */

package org.digimead.tabuddy.desktop.view.modification.ui.dialog.sortlist

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import java.util.regex.Pattern
import javax.inject.Inject
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.WritableList
import org.digimead.tabuddy.desktop.core.support.WritableValue
import org.digimead.tabuddy.desktop.logic.payload.Payload
import org.digimead.tabuddy.desktop.logic.payload.maker.GraphMarker
import org.digimead.tabuddy.desktop.logic.payload.view
import org.digimead.tabuddy.desktop.ui.UI
import org.digimead.tabuddy.desktop.ui.definition.Dialog
import org.digimead.tabuddy.desktop.ui.support.RegexFilterListener
import org.digimead.tabuddy.desktop.view.modification.{ Default, Messages }
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.graph.Graph
import org.eclipse.core.databinding.observable.ChangeEvent
import org.eclipse.e4.core.contexts.IEclipseContext
import org.eclipse.jface.action.{ Action, ActionContributionItem, IAction, IMenuListener, IMenuManager, MenuManager }
import org.eclipse.jface.databinding.swt.WidgetProperties
import org.eclipse.jface.databinding.viewers.{ ObservableListContentProvider, ViewersObservables }
import org.eclipse.jface.dialogs.IDialogConstants
import org.eclipse.jface.viewers.{ ColumnViewerToolTipSupport, ISelectionChangedListener, IStructuredSelection, SelectionChangedEvent, StructuredSelection, TableViewer, Viewer, ViewerComparator, ViewerFilter }
import org.eclipse.swt.SWT
import org.eclipse.swt.events.{ DisposeEvent, DisposeListener, FocusEvent, FocusListener, SelectionAdapter, SelectionEvent, ShellAdapter, ShellEvent }
import org.eclipse.swt.widgets.{ Composite, Control, Event, Listener, Shell, TableItem }
import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future
import scala.ref.WeakReference

class SortingList @Inject() (
  /** This dialog context. */
  val context: IEclipseContext,
  /** Parent shell. */
  val parentShell: Shell,
  /** Graph container. */
  val graph: Graph[_ <: Model.Like],
  /** Graph marker. */
  val marker: GraphMarker,
  /** Graph payload. */
  val payload: Payload,
  /** Initial sortirg list. */
  val initial: Set[view.api.Sorting])
  extends SortingListSkel(parentShell) with Dialog with Loggable {
  /** The actual content */
  protected[sortlist] val actual = WritableList(initial.toList)
  /** The auto resize lock */
  protected val autoResizeLock = new ReentrantLock()
  /** The property representing sorting filter content */
  protected val filterSortings = WritableValue("")
  /** Activate context on focus. */
  protected val focusListener = new FocusListener() {
    def focusGained(e: FocusEvent) = context.activateBranch()
    def focusLost(e: FocusEvent) {}
  }
  /** The property representing a selected view */
  protected val selected = WritableValue[view.api.Sorting]
  /** Activate context on shell events. */
  protected val shellListener = new ShellAdapter() {
    override def shellActivated(e: ShellEvent) = context.activateBranch()
  }
  /** Actual sortBy column index */
  @volatile protected var sortColumn = 0 // by an id
  /** Actual sort direction */
  @volatile protected var sortDirection = Default.sortingDirection

  def getModifiedSortings(): Set[view.api.Sorting] = actual.sortBy(_.name).toSet

  /** Auto resize table viewer columns */
  protected def autoresize() = if (autoResizeLock.tryLock()) try {
    Thread.sleep(50)
    App.execNGet {
      if (!getTableViewer.getTable.isDisposed()) {
        UI.adjustTableViewerColumnWidth(getTableViewerColumnName(), Default.columnPadding)
        getTableViewer.refresh()
      }
    }
  } finally {
    autoResizeLock.unlock()
  }
  /** Create contents of the dialog. */
  override protected def createDialogArea(parent: Composite): Control = {
    val result = super.createDialogArea(parent)
    context.set(classOf[Composite], parent)
    new ActionContributionItem(ActionCreate).fill(getCompositeFooter())
    new ActionContributionItem(ActionCreateFrom).fill(getCompositeFooter())
    new ActionContributionItem(ActionEdit).fill(getCompositeFooter())
    new ActionContributionItem(ActionRemove).fill(getCompositeFooter())
    ActionCreateFrom.setEnabled(false)
    ActionEdit.setEnabled(false)
    ActionRemove.setEnabled(false)
    initTableViews()
    val actualListener = actual.addChangeListener { event ⇒
      if (ActionAutoResize.isChecked())
        future { autoresize() } onFailure {
          case e: Exception ⇒ log.error(e.getMessage(), e)
          case e ⇒ log.error(e.toString())
        }
      updateOK()
    }
    getShell().addShellListener(shellListener)
    getShell().addFocusListener(focusListener)
    // Add the dispose listener
    getShell().addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) {
        getShell().removeFocusListener(focusListener)
        getShell().removeShellListener(shellListener)
        actual.removeChangeListener(actualListener)
      }
    })
    // Set the dialog message
    setMessage(Messages.viewSortingListDescription_text.format(graph.model.eId.name))
    // Set the dialog window title
    getShell().setText(Messages.viewSortingListDialog_text.format(graph.model.eId.name))
    result
  }
  /** Generate new name: old name + ' Copy' + N */
  protected def getNewSortingCopyName(name: String, sortingList: List[view.api.Sorting]): String = {
    val sameIds = immutable.HashSet(sortingList.filter(_.name.startsWith(name)).map(_.name).toSeq: _*)
    var n = 0
    var newName = name + " " + Messages.copy_item_text
    while (sameIds(newName)) {
      n += 1
      newName = name + " " + Messages.copy_item_text + n
    }
    newName
  }
  /** Initialize tableViewer */
  protected def initTableViews() {
    val viewer = getTableViewer()
    viewer.setContentProvider(new ObservableListContentProvider())
    getTableViewerColumnName.setLabelProvider(new ColumnName.TLabelProvider)
    getTableViewerColumnName.setEditingSupport(new ColumnName.TEditingSupport(viewer, this))
    getTableViewerColumnName.getColumn.addSelectionListener(new SortingList.SortingSelectionAdapter(WeakReference(viewer), 0))
    getTableViewerColumnDescription.setLabelProvider(new ColumnDescription.TLabelProvider)
    getTableViewerColumnDescription.setEditingSupport(new ColumnDescription.TEditingSupport(viewer, this))
    getTableViewerColumnDescription.getColumn.addSelectionListener(new SortingList.SortingSelectionAdapter(WeakReference(viewer), 1))
    // Add a SWT.CHECK support
    viewer.getTable.addListener(SWT.Selection, new Listener() {
      def handleEvent(event: Event) = if (event.detail == SWT.CHECK)
        event.item match {
          case tableItem: TableItem ⇒
            val index = tableItem.getParent().indexOf(tableItem)
            viewer.getElementAt(index) match {
              case before: view.api.Sorting ⇒
                if (before.availability != tableItem.getChecked()) {
                  val after = before.copy(availability = tableItem.getChecked())
                  updateActualSorting(before, after)
                }
              case item ⇒
                log.fatal(s"unknown item $item")
            }
          case item ⇒
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
        manager.add(ActionAutoResize)
      }
    })
    menuMgr.setRemoveAllWhenShown(true)
    viewer.getControl.setMenu(menu)
    // Add the selection listener
    viewer.addSelectionChangedListener(new ISelectionChangedListener() {
      override def selectionChanged(event: SelectionChangedEvent) = event.getSelection() match {
        case selection: IStructuredSelection if !selection.isEmpty() ⇒
          val sorting = selection.getFirstElement().asInstanceOf[view.api.Sorting]
          ActionCreateFrom.setEnabled(true)
          ActionEdit.setEnabled(true)
          ActionRemove.setEnabled(view.Sorting.simpleSorting != sorting) // exclude predefined
        case selection ⇒
          ActionCreateFrom.setEnabled(false)
          ActionEdit.setEnabled(false)
          ActionRemove.setEnabled(false)
      }
    })
    // Add the filter
    App.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextFilter()), filterSortings)
    val filter = new AtomicReference(".*".r.pattern)
    val filterListener = new RegexFilterListener(filter) {
      override def handleChange(event: ChangeEvent) {
        super.handleChange(event)
        getTableViewer.refresh()
      }
    }
    filterSortings.underlying.addChangeListener(filterListener)
    viewer.setFilters(Array(new SortingList.SortingFilter(filter)))
    // Set sorting
    viewer.setComparator(new SortingList.SortingComparator(new WeakReference(this)))
    viewer.setInput(actual.underlying)
    App.bindingContext.bindValue(ViewersObservables.observeSingleSelection(viewer), selected)
  }
  /** On dialog active */
  override protected def onActive = {
    updateOK()
    if (ActionAutoResize.isChecked())
      future { autoresize() } onFailure {
        case e: Exception ⇒ log.error(e.getMessage(), e)
        case e ⇒ log.error(e.toString())
      }
  }
  /** Updates an actual element template */
  protected[sortlist] def updateActualSorting(before: view.api.Sorting, after: view.api.Sorting) {
    val index = actual.indexOf(before)
    actual.update(index, after)
    if (index == actual.size - 1)
      getTableViewer.refresh() // Workaround for the JFace bug. Force the last element modification.
    getTableViewer.setSelection(new StructuredSelection(after), true)
    if (ActionAutoResize.isChecked())
      future { autoresize() } onFailure {
        case e: Exception ⇒ log.error(e.getMessage(), e)
        case e ⇒ log.error(e.toString())
      }
  }
  /** Update OK button state */
  protected def updateOK() = Option(getButton(IDialogConstants.OK_ID)).
    foreach(_.setEnabled(!{ initial.sameElements(actual) && (initial, actual).zipped.forall(view.Sorting.compareDeep(_, _)) }))

  object ActionAutoResize extends Action(Messages.autoresize_key, IAction.AS_CHECK_BOX) {
    setChecked(true)
    override def run = if (isChecked())
      future { autoresize } onFailure {
        case e: Exception ⇒ log.error(e.getMessage(), e)
        case e ⇒ log.error(e.toString())
      }
  }
  object ActionCreate extends Action(Messages.create_text) with Loggable {
    override def run = {
      //      val newSortingName = payload.generateNew(Messages.newSortingName_text, " ", newName ⇒ actual.exists(_.name == newName))
      //      val newSorting = new view.Sorting(UUID.randomUUID(), newSortingName, "", true, mutable.LinkedHashSet())
      //      OperationModifySorting(newSorting, actual.toSet).foreach { operation ⇒
      //        operation.getExecuteJob() match {
      //          case Some(job) ⇒
      //            job.setPriority(Job.SHORT)
      //            job.onComplete(_ match {
      //              case Operation.Result.OK(result, message) ⇒
      //                log.info(s"Operation completed successfully: ${result}")
      //                result.foreach { case (sorting) ⇒ App.exec { actual += sorting } }
      //              case Operation.Result.Cancel(message) ⇒
      //                log.warn(s"Operation canceled, reason: ${message}.")
      //              case other ⇒
      //                log.error(s"Unable to complete operation: ${other}.")
      //            }).schedule()
      //          case None ⇒
      //            log.fatal(s"Unable to create job for ${operation}.")
      //        }
      //      }
    }
  }
  object ActionCreateFrom extends Action(Messages.createFrom_text) with Loggable {
    override def run = Option(selected.value) foreach { selected ⇒
      //      val name = getNewSortingCopyName(selected.name, actual.toList)
      //      val newSorting = selected.copy(id = UUID.randomUUID(), name = name)
      //      OperationModifySorting(newSorting, actual.toSet).foreach { operation ⇒
      //        operation.getExecuteJob() match {
      //          case Some(job) ⇒
      //            job.setPriority(Job.SHORT)
      //            job.onComplete(_ match {
      //              case Operation.Result.OK(result, message) ⇒
      //                log.info(s"Operation completed successfully: ${result}")
      //                result.foreach {
      //                  case (sorting) ⇒ App.exec {
      //                    assert(!actual.exists(_.id == sorting.id), "Sorting %s already exists".format(sorting))
      //                    actual += sorting
      //                  }
      //                }
      //              case Operation.Result.Cancel(message) ⇒
      //                log.warn(s"Operation canceled, reason: ${message}.")
      //              case other ⇒
      //                log.error(s"Unable to complete operation: ${other}.")
      //            }).schedule()
      //          case None ⇒
      //            log.fatal(s"Unable to create job for ${operation}.")
      //        }
      //      }
    }
  }
  object ActionEdit extends Action(Messages.edit_text) {
    override def run = Option(selected.value) foreach { before ⇒
      //      OperationModifySorting(before, actual.toSet).foreach { operation ⇒
      //        operation.getExecuteJob() match {
      //          case Some(job) ⇒
      //            job.setPriority(Job.SHORT)
      //            job.onComplete(_ match {
      //              case Operation.Result.OK(result, message) ⇒
      //                log.info(s"Operation completed successfully: ${result}")
      //                result.foreach { case (after) ⇒ App.exec { updateActualSorting(before, after) } }
      //              case Operation.Result.Cancel(message) ⇒
      //                log.warn(s"Operation canceled, reason: ${message}.")
      //              case other ⇒
      //                log.error(s"Unable to complete operation: ${other}.")
      //            }).schedule()
      //          case None ⇒
      //            log.fatal(s"Unable to create job for ${operation}.")
      //        }
      //      }
    }
  }
  object ActionRemove extends Action(Messages.remove_text) {
    override def run = Option(selected.value) foreach { selected ⇒
      actual -= selected
    }
  }
}

object SortingList extends Loggable {
  class SortingComparator(dialog: WeakReference[SortingList]) extends ViewerComparator {
    private var _column = dialog.get.map(_.sortColumn) getOrElse
      { throw new IllegalStateException("Dialog not found.") }
    private var _direction = dialog.get.map(_.sortDirection) getOrElse
      { throw new IllegalStateException("Dialog not found.") }

    /** Active column getter */
    def column = _column
    /** Active column setter */
    def column_=(arg: Int) {
      _column = arg
      dialog.get.foreach(_.sortColumn = _column)
      _direction = Default.sortingDirection
      dialog.get.foreach(_.sortDirection = _direction)
    }
    /** Sorting direction */
    def direction = _direction
    /**
     * Returns a negative, zero, or positive number depending on whether
     * the first element is less than, equal to, or greater than
     * the second element.
     */
    override def compare(viewer: Viewer, e1: Object, e2: Object): Int = {
      val entity1 = e1.asInstanceOf[view.api.Sorting]
      val entity2 = e2.asInstanceOf[view.api.Sorting]
      val rc = column match {
        case 0 ⇒ entity1.name.compareTo(entity2.name)
        case 1 ⇒ entity1.description.compareTo(entity2.description)
        case index ⇒
          log.fatal(s"unknown column with index $index"); 0
      }
      if (_direction) -rc else rc
    }
    /** Switch comparator direction */
    def switchDirection() {
      _direction = !_direction
      dialog.get.foreach(_.sortDirection = _direction)
    }
  }
  class SortingFilter(filter: AtomicReference[Pattern]) extends ViewerFilter {
    override def select(viewer: Viewer, parentElement: AnyRef, element: AnyRef): Boolean = {
      val pattern = filter.get
      val item = element.asInstanceOf[view.api.Sorting]
      pattern.matcher(item.name.toLowerCase()).matches() ||
        pattern.matcher(item.description.toLowerCase()).matches()
    }
  }
  class SortingSelectionAdapter(tableViewer: WeakReference[TableViewer], column: Int) extends SelectionAdapter {
    override def widgetSelected(e: SelectionEvent) = {
      tableViewer.get.foreach(viewer ⇒ viewer.getComparator() match {
        case comparator: SortingComparator if comparator.column == column ⇒
          comparator.switchDirection()
          viewer.refresh()
        case comparator: SortingComparator ⇒
          comparator.column = column
          viewer.refresh()
        case _ ⇒
      })
    }
  }
}
