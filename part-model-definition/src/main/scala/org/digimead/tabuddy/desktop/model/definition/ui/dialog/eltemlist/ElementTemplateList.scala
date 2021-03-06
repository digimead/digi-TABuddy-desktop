/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2012-2015 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.model.definition.ui.dialog.eltemlist

import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.core.support.{ App, WritableList, WritableValue }
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.core.ui.definition.Dialog
import org.digimead.tabuddy.desktop.core.{ Messages ⇒ CoreMessages }
import org.digimead.tabuddy.desktop.logic.operation.OperationModifyElementTemplate
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.desktop.logic.payload.{ ElementTemplate, Payload }
import org.digimead.tabuddy.desktop.model.definition.Default
import org.digimead.tabuddy.desktop.model.definition.Messages
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.graph.Graph
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.e4.core.contexts.IEclipseContext
import org.eclipse.jface.action.{ Action, ActionContributionItem, IAction, IMenuListener, IMenuManager, MenuManager }
import org.eclipse.jface.databinding.viewers.{ ObservableListContentProvider, ViewersObservables }
import org.eclipse.jface.dialogs.IDialogConstants
import org.eclipse.jface.viewers.{ ColumnViewerToolTipSupport, ISelectionChangedListener, IStructuredSelection, SelectionChangedEvent, StructuredSelection, TableViewer, Viewer, ViewerComparator }
import org.eclipse.swt.SWT
import org.eclipse.swt.events.{ DisposeEvent, DisposeListener, FocusEvent, FocusListener, SelectionAdapter, SelectionEvent, ShellAdapter, ShellEvent }
import org.eclipse.swt.widgets.{ Composite, Control, Event, Listener, Shell, TableItem }
import scala.collection.immutable
import scala.concurrent.Future
import scala.ref.WeakReference

class ElementTemplateList @Inject() (
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
  /** Initial element template list. */
  val initial: Set[ElementTemplate])
    extends ElementTemplateListSkel(parentShell) with Dialog with XLoggable {
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** The actual content */
  val actual = WritableList[ElementTemplate](
    // replace initial elements with copies that will be modified in the progress
    initial.toList.map { initialTemplate ⇒
      initialTemplate.element.eNode.parent.get.freezeWrite { target ⇒
        val copyOfNode = initialTemplate.element.eNode.**.copy(attach = false)
        initialTemplate.copy(element = copyOfNode.rootBox.e.eRelative)
      }
    }.sortBy(_.id.name))
  /** The auto resize lock. */
  val autoResizeLock = new ReentrantLock()
  /** Activate context on focus. */
  val focusListener = new FocusListener() {
    def focusGained(e: FocusEvent) = context.activateBranch()
    def focusLost(e: FocusEvent) {}
  }
  /** The property representing a selected element template */
  val selected = WritableValue[ElementTemplate]
  /** Activate context on shell events. */
  val shellListener = new ShellAdapter() {
    override def shellActivated(e: ShellEvent) = context.activateBranch()
  }
  /** Actual sortBy column index */
  @volatile protected var sortColumn = 1 // by an id
  /** Actual sort direction */
  @volatile protected var sortDirection = Default.sortingDirection

  /** Get modified type templates */
  def getModifiedTemplates(): Set[ElementTemplate] = actual.toSet

  /** Auto resize tableviewer columns */
  protected def autoresize() = if (autoResizeLock.tryLock()) try {
    Thread.sleep(50)
    App.execNGet {
      if (!getTableViewer.getTable.isDisposed()) {
        UI.adjustViewerColumnWidth(getTableViewerColumnAvailability, Default.columnPadding)
        UI.adjustViewerColumnWidth(getTableViewerColumnId, Default.columnPadding)
        getTableViewer.refresh()
      }
    }
  } finally {
    autoResizeLock.unlock()
  }
  /** Create contents of the dialog. */
  @log(result = false)
  override protected def createDialogArea(parent: Composite): Control = {
    val result = super.createDialogArea(parent)
    context.set(classOf[Composite], parent)
    context.set(classOf[org.eclipse.jface.dialogs.Dialog], this)
    new ActionContributionItem(ActionCreateFrom).fill(getCompositeFooter())
    new ActionContributionItem(ActionEdit).fill(getCompositeFooter())
    new ActionContributionItem(ActionRemove).fill(getCompositeFooter())
    ActionCreateFrom.setEnabled(false)
    ActionEdit.setEnabled(false)
    ActionRemove.setEnabled(false)
    initTableElementTemplates()
    val actualListener = actual.addChangeListener { event ⇒
      if (ActionAutoResize.isChecked())
        Future { autoresize() }
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
    setMessage(Messages.elementTemplateListDescription_text.format(graph.model.eId.name))
    // Set the dialog window title
    getShell().setText(Messages.elementTemplateListDialog_text.format(graph.model.eId.name))
    result
  }
  /** Generate new ID: old ID + 'Copy' + N */
  protected def getNewTemplateCopyID(id: Symbol): Symbol = {
    val sameIds = immutable.HashSet(actual.filter(_.id.name.startsWith(id.name)).map(_.id.name).toSeq: _*)
    var n = 0
    var newId = id.name + CoreMessages.copy_item_text
    while (sameIds(newId)) {
      n += 1
      newId = id.name + CoreMessages.copy_item_text + n
    }
    Symbol(newId)
  }
  /** Initialize tableTypeList */
  protected def initTableElementTemplates() {
    val viewer = getTableViewer()
    viewer.setContentProvider(new ObservableListContentProvider())
    getTableViewerColumnAvailability.setLabelProvider(new ColumnAvailability.TLabelProvider)
    getTableViewerColumnAvailability.setEditingSupport(new ColumnAvailability.TEditingSupport(viewer, this))
    getTableViewerColumnAvailability.getColumn.addSelectionListener(new ElementTemplateList.TemplateSelectionAdapter(WeakReference(viewer), 0))
    getTableViewerColumnId.setLabelProvider(new ColumnId.TLabelProvider)
    getTableViewerColumnId.setEditingSupport(new ColumnId.TEditingSupport(payload, viewer, this))
    getTableViewerColumnId.getColumn.addSelectionListener(new ElementTemplateList.TemplateSelectionAdapter(WeakReference(viewer), 1))
    getTableViewerColumnName.setLabelProvider(new ColumnName.TLabelProvider)
    getTableViewerColumnName.setEditingSupport(new ColumnName.TEditingSupport(viewer, this))
    getTableViewerColumnName.getColumn.addSelectionListener(new ElementTemplateList.TemplateSelectionAdapter(WeakReference(viewer), 2))
    // Add a SWT.CHECK support
    viewer.getTable.addListener(SWT.Selection, new Listener() {
      def handleEvent(event: Event) = if (event.detail == SWT.CHECK)
        event.item match {
          case tableItem: TableItem ⇒
            val index = tableItem.getParent().indexOf(tableItem)
            viewer.getElementAt(index) match {
              case before: ElementTemplate ⇒
                if (before.availability != tableItem.getChecked()) {
                  val after = before.updated(tableItem.getChecked())
                  updateActualTemplate(before, after)
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
          val template = selection.getFirstElement().asInstanceOf[ElementTemplate]
          ActionCreateFrom.setEnabled(true)
          ActionEdit.setEnabled(true)
          ActionRemove.setEnabled(!payload.originalElementTemplates.exists(_.id == template.id)) // exclude predefined
        case selection ⇒
          ActionCreateFrom.setEnabled(false)
          ActionEdit.setEnabled(false)
          ActionRemove.setEnabled(false)
      }
    })
    viewer.setComparator(new ElementTemplateList.TemplateComparator(new WeakReference(this)))
    viewer.setInput(actual.underlying)
    App.bindingContext.bindValue(ViewersObservables.observeSingleSelection(viewer), selected)
  }
  /** On dialog active */
  override protected def onActive() = {
    updateOK()
    if (ActionAutoResize.isChecked())
      Future { autoresize() } onFailure {
        case e: Exception ⇒ log.error(e.getMessage(), e)
        case e ⇒ log.error(e.toString())
      }
  }
  /** Updates an actual element template */
  protected[eltemlist] def updateActualTemplate(before: ElementTemplate, after: ElementTemplate) {
    val index = actual.indexOf(before)
    actual.update(index, after)
    if (index == actual.size - 1)
      getTableViewer.refresh() // Workaround for the JFace bug. Force the last element modification.
    getTableViewer.setSelection(new StructuredSelection(after), true)
  }
  /** Update OK button state */
  protected def updateOK() = Option(getButton(IDialogConstants.OK_ID)).
    foreach(_.setEnabled(initial.map(_.element.modified) != actual.map(_.element.modified).toSet))

  object ActionAutoResize extends Action(CoreMessages.autoresize_key, IAction.AS_CHECK_BOX) {
    setChecked(true)
    override def run = if (isChecked()) autoresize
  }
  object ActionCreateFrom extends Action(CoreMessages.createFrom_text) with XLoggable {
    override def run = Option(selected.value) foreach { (before) ⇒
      val from = before.element
      // create new ID
      val toId = getNewTemplateCopyID(from.eId)
      // create an element for a new template
      val to = from.eNode.copy(id = toId, unique = UUID.randomUUID).**
      // create a template for a 'to' element
      val newTemplate = new ElementTemplate(to.rootBox.e.eRelative, before.factory).copy(name = before.name + " " + CoreMessages.copy_item_text)
      // start job
      OperationModifyElementTemplate(graph, newTemplate, actual.toSet).foreach { operation ⇒
        val job = if (operation.canRedo())
          Some(operation.redoJob())
        else if (operation.canExecute())
          Some(operation.executeJob())
        else
          None
        job foreach { job ⇒
          job.setPriority(Job.SHORT)
          job.onComplete(_ match {
            case Operation.Result.OK(result, message) ⇒
              log.info(s"Operation completed successfully: ${result}")
              result.foreach {
                case (after) ⇒ App.exec {
                  assert(!actual.exists(_.id == after.id), "Element template %s already exists".format(after))
                  actual += after
                }
              }
            case Operation.Result.Cancel(message) ⇒
              log.warn(s"Operation canceled, reason: ${message}.")
            case other ⇒
              log.error(s"Unable to complete operation: ${other}.")
          }).schedule()
        }
      }
    }
  }
  object ActionEdit extends Action(CoreMessages.edit_text) {
    override def run = Option(selected.value) foreach { (before) ⇒
      OperationModifyElementTemplate(graph, before, actual.toSet).foreach { operation ⇒
        operation.getExecuteJob() match {
          case Some(job) ⇒
            job.setPriority(Job.SHORT)
            job.onComplete(_ match {
              case Operation.Result.OK(result, message) ⇒
                log.info(s"Operation completed successfully: ${result}")
                result.foreach { case (after) ⇒ App.exec { updateActualTemplate(before, after) } }
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
  object ActionRemove extends Action(CoreMessages.remove_text) {
    override def run = Option(selected.value) foreach { (selected) ⇒
      if (!payload.originalElementTemplates.exists(_.id == selected.id))
        actual -= selected
    }
  }
}

object ElementTemplateList extends XLoggable {
  class TemplateComparator(dialog: WeakReference[ElementTemplateList]) extends ViewerComparator {
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
      val entity1 = e1.asInstanceOf[ElementTemplate]
      val entity2 = e2.asInstanceOf[ElementTemplate]
      val rc = column match {
        case 0 ⇒ entity1.availability.compareTo(entity2.availability)
        case 1 ⇒ entity1.id.name.compareTo(entity2.id.name)
        case 2 ⇒ entity1.name.compareTo(entity2.name)
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
  class TemplateSelectionAdapter(tableViewer: WeakReference[TableViewer], column: Int) extends SelectionAdapter {
    override def widgetSelected(e: SelectionEvent) = {
      tableViewer.get.foreach(viewer ⇒ viewer.getComparator() match {
        case comparator: TemplateComparator if comparator.column == column ⇒
          comparator.switchDirection()
          viewer.refresh()
        case comparator: TemplateComparator ⇒
          comparator.column = column
          viewer.refresh()
        case _ ⇒
      })
    }
  }
}
