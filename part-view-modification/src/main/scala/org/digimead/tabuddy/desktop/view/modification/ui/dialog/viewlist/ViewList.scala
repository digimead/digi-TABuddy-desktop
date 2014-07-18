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

package org.digimead.tabuddy.desktop.view.modification.ui.dialog.viewlist

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import java.util.regex.Pattern
import javax.inject.Inject
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.core.support.{ App, WritableList, WritableValue }
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.core.ui.definition.Dialog
import org.digimead.tabuddy.desktop.core.ui.support.RegexFilterListener
import org.digimead.tabuddy.desktop.logic.operation.view.OperationModifyView
import org.digimead.tabuddy.desktop.logic.payload.Payload
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.desktop.logic.payload.view.View
import org.digimead.tabuddy.desktop.view.modification.{ Default, Messages }
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.graph.Graph
import org.eclipse.core.databinding.observable.ChangeEvent
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.e4.core.contexts.IEclipseContext
import org.eclipse.jface.action.{ Action, ActionContributionItem, IAction, IMenuListener, IMenuManager, MenuManager }
import org.eclipse.jface.databinding.swt.WidgetProperties
import org.eclipse.jface.databinding.viewers.{ ObservableListContentProvider, ViewersObservables }
import org.eclipse.jface.dialogs.IDialogConstants
import org.eclipse.jface.viewers.{ ColumnViewerToolTipSupport, ISelectionChangedListener, IStructuredSelection, SelectionChangedEvent, StructuredSelection, TableViewer, Viewer, ViewerComparator, ViewerFilter }
import org.eclipse.swt.SWT
import org.eclipse.swt.events.{ DisposeEvent, DisposeListener, FocusEvent, FocusListener, SelectionAdapter, SelectionEvent, ShellAdapter, ShellEvent }
import org.eclipse.swt.widgets.{ Composite, Control, Event, Listener, Shell, TableItem }
import scala.collection.{ immutable, mutable }
import scala.concurrent.Future
import scala.ref.WeakReference

class ViewList @Inject() (
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
  /** Initial view list. */
  val initial: Set[View])
  extends ViewListSkel(parentShell) with Dialog with XLoggable {
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** The actual content */
  protected[viewlist] val actual = WritableList(initial.toList)
  /** The auto resize lock */
  protected val autoResizeLock = new ReentrantLock()
  /** The property representing view filter content */
  protected val filterViews = WritableValue("")
  /** Activate context on focus. */
  protected val focusListener = new FocusListener() {
    def focusGained(e: FocusEvent) = context.activateBranch()
    def focusLost(e: FocusEvent) {}
  }
  /** The property representing a selected view */
  protected val selected = WritableValue[View]
  /** Activate context on shell events. */
  protected val shellListener = new ShellAdapter() {
    override def shellActivated(e: ShellEvent) = context.activateBranch()
  }
  /** Actual sort direction */
  @volatile private var sortDirection = Default.sortingDirection
  /** Actual sortBy column index */
  @volatile private var sortColumn = 1

  def getModifiedViews(): Set[View] = actual.sortBy(_.name).toSet

  /** Auto resize tableviewer columns */
  protected def autoresize() = if (autoResizeLock.tryLock()) try {
    Thread.sleep(50)
    App.execNGet {
      if (!getTableViewer.getTable.isDisposed()) {
        UI.adjustViewerColumnWidth(getTableViewerColumnName(), Default.columnPadding)
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
        Future { autoresize() } onFailure {
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
    setMessage(Messages.viewListDescription_text.format(graph.model.eId.name))
    // Set the dialog window title
    getShell().setText(Messages.viewListDialog_text.format(graph.model.eId.name))
    result
  }
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
          case tableItem: TableItem ⇒
            val index = tableItem.getParent().indexOf(tableItem)
            viewer.getElementAt(index) match {
              case before: View ⇒
                if (before.availability != tableItem.getChecked()) {
                  val after = before.copy(availability = tableItem.getChecked())
                  updateActualView(before, after)
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
          val view = selection.getFirstElement().asInstanceOf[View]
          ActionCreateFrom.setEnabled(true)
          ActionEdit.setEnabled(true)
          ActionRemove.setEnabled(org.digimead.tabuddy.desktop.logic.payload.view.View.displayName != view) // exclude predefined
        case selection ⇒
          ActionCreateFrom.setEnabled(false)
          ActionEdit.setEnabled(false)
          ActionRemove.setEnabled(false)
      }
    })
    // Add the filter
    App.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextFilter()), filterViews)
    val filter = new AtomicReference(".*".r.pattern)
    val filterListener = new RegexFilterListener(filter) {
      override def handleChange(event: ChangeEvent) {
        super.handleChange(event)
        getTableViewer.refresh()
      }
    }
    filterViews.underlying.addChangeListener(filterListener)
    viewer.setFilters(Array(new ViewList.ViewFilter(filter)))
    // Set sorting
    viewer.setComparator(new ViewList.ViewComparator(new WeakReference(this)))
    viewer.setInput(actual.underlying)
    App.bindingContext.bindValue(ViewersObservables.observeSingleSelection(viewer), selected)
  }
  /** On dialog active */
  override protected def onActive = {
    updateOK()
    if (ActionAutoResize.isChecked())
      Future { autoresize() } onFailure {
        case e: Exception ⇒ log.error(e.getMessage(), e)
        case e ⇒ log.error(e.toString())
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

  object ActionAutoResize extends Action(Messages.autoresize_key, IAction.AS_CHECK_BOX) {
    setChecked(true)
    override def run = if (isChecked()) autoresize
  }
  object ActionCreate extends Action(Messages.create_text) with XLoggable {
    override def run = {
      val newViewName = payload.generateNew(Messages.newViewName_text, " ", newName ⇒ actual.exists(_.name == newName))
      val newView = new View(UUID.randomUUID(), newViewName, "", true, mutable.LinkedHashSet(), mutable.LinkedHashSet(), mutable.LinkedHashSet())
      OperationModifyView(graph, newView, actual.toSet).foreach { operation ⇒
        operation.getExecuteJob() match {
          case Some(job) ⇒
            job.setPriority(Job.SHORT)
            job.onComplete(_ match {
              case Operation.Result.OK(result, message) ⇒
                log.info(s"Operation completed successfully: ${result}")
                result.foreach {
                  case (view) ⇒ App.exec {
                    actual += view
                    getTableViewer.setSelection(new StructuredSelection(view), true)
                  }
                }
              case Operation.Result.Cancel(message) ⇒
                log.warn(s"Operation canceled, reason: ${message}.")
              case other ⇒
                log.error(s"Unable to complete operation: ${other}.")
            }).schedule()
          case None ⇒
            log.fatal(s"Unable to create job for ${operation}.")
        }
      }
    }
  }
  object ActionCreateFrom extends Action(Messages.createFrom_text) with XLoggable {
    override def run = Option(selected.value) foreach { selected ⇒
      val name = getNewViewCopyName(selected.name, actual.toList)
      val newView = selected.copy(id = UUID.randomUUID(), name = name)
      OperationModifyView(graph, newView, actual.toSet).foreach { operation ⇒
        operation.getExecuteJob() match {
          case Some(job) ⇒
            job.setPriority(Job.SHORT)
            job.onComplete(_ match {
              case Operation.Result.OK(result, message) ⇒
                log.info(s"Operation completed successfully: ${result}")
                result.foreach {
                  case (view) ⇒ App.exec {
                    assert(!actual.exists(_.id == view.id), "View %s already exists".format(view))
                    actual += view
                    getTableViewer.setSelection(new StructuredSelection(view), true)
                  }
                }
              case Operation.Result.Cancel(message) ⇒
                log.warn(s"Operation canceled, reason: ${message}.")
              case other ⇒
                log.error(s"Unable to complete operation: ${other}.")
            }).schedule()
          case None ⇒
            log.fatal(s"Unable to create job for ${operation}.")
        }
      }
    }
  }
  object ActionEdit extends Action(Messages.edit_text) {
    override def run = Option(selected.value) foreach { before ⇒
      OperationModifyView(graph, before, actual.toSet).foreach { operation ⇒
        operation.getExecuteJob() match {
          case Some(job) ⇒
            job.setPriority(Job.SHORT)
            job.onComplete(_ match {
              case Operation.Result.OK(result, message) ⇒
                log.info(s"Operation completed successfully: ${result}")
                result.foreach { case (after) ⇒ App.exec { updateActualView(before, after) } }
              case Operation.Result.Cancel(message) ⇒
                log.warn(s"Operation canceled, reason: ${message}.")
              case other ⇒
                log.error(s"Unable to complete operation: ${other}.")
            }).schedule()
          case None ⇒
            log.fatal(s"Unable to create job for ${operation}.")
        }
      }
    }
  }
  object ActionRemove extends Action(Messages.remove_text) {
    override def run = Option(selected.value) foreach { selected ⇒
      actual -= selected
    }
  }
}

object ViewList extends XLoggable {
  class ViewComparator(dialog: WeakReference[ViewList]) extends ViewerComparator {
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
      val entity1 = e1.asInstanceOf[View]
      val entity2 = e2.asInstanceOf[View]
      val rc = column match {
        case 0 ⇒ entity1.availability.compareTo(entity2.availability)
        case 1 ⇒ entity1.name.compareTo(entity2.name)
        case 2 ⇒ entity1.description.compareTo(entity2.description)
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
      tableViewer.get.foreach(viewer ⇒ viewer.getComparator() match {
        case comparator: ViewComparator if comparator.column == column ⇒
          comparator.switchDirection()
          viewer.refresh()
        case comparator: ViewComparator ⇒
          comparator.column = column
          viewer.refresh()
        case _ ⇒
      })
    }
  }
}
