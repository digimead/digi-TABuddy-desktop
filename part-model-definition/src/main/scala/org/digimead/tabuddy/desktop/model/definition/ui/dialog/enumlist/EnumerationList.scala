/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2013-2015 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.model.definition.ui.dialog.enumlist

import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.core.support.{ App, WritableList, WritableValue }
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.core.ui.definition.Dialog
import org.digimead.tabuddy.desktop.core.ui.support.{ SymbolValidator, Validator }
import org.digimead.tabuddy.desktop.core.{ Messages ⇒ CoreMessages }
import org.digimead.tabuddy.desktop.logic.operation.OperationModifyEnumeration
import org.digimead.tabuddy.desktop.logic.payload.api.XEnumeration
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.desktop.logic.payload.{ Enumeration, Payload, PropertyType }
import org.digimead.tabuddy.desktop.model.definition.{ Default, Messages }
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.dsl.DSLType
import org.digimead.tabuddy.model.graph.Graph
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.e4.core.contexts.IEclipseContext
import org.eclipse.jface.action.{ Action, ActionContributionItem, IAction, IMenuListener, IMenuManager, MenuManager }
import org.eclipse.jface.databinding.swt.WidgetProperties
import org.eclipse.jface.databinding.viewers.{ ObservableListContentProvider, ViewersObservables }
import org.eclipse.jface.dialogs.IDialogConstants
import org.eclipse.jface.viewers.{ ArrayContentProvider, ColumnViewerToolTipSupport, ISelectionChangedListener, IStructuredSelection, LabelProvider, SelectionChangedEvent, StructuredSelection, TableViewer, Viewer, ViewerComparator }
import org.eclipse.swt.SWT
import org.eclipse.swt.events.{ DisposeEvent, DisposeListener, FocusEvent, FocusListener, SelectionAdapter, SelectionEvent, ShellAdapter, ShellEvent }
import org.eclipse.swt.widgets.{ Composite, Control, Event, Listener, Shell, TableItem, Text }
import scala.collection.immutable
import scala.concurrent.Future
import scala.ref.WeakReference

class EnumerationList @Inject() (
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
  /** Initial enumeration list. */
  val initial: Set[Enumeration[_ <: AnySRef]])
    extends EnumerationListSkel(parentShell) with Dialog with XLoggable {
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** The actual enumeration list */
  val actual = WritableList[Enumeration[_ <: AnySRef]](
    // replace initial elements with copies that will be modified in the progress
    initial.toList.map { initialEnumeration ⇒
      initialEnumeration.element.eNode.parent.get.freezeWrite { target ⇒
        val copyOfNode = initialEnumeration.element.eNode.**.copy(attach = false)
        initialEnumeration.**.copy(element = copyOfNode.rootBox.e.eRelative)
      }
    }.sortBy(_.id.name))
  /** The auto resize lock */
  val autoResizeLock = new ReentrantLock()
  /** The property representing enumeration in current UI field(s) that available for user */
  val enumerationField = WritableValue[Enumeration[_ <: AnySRef]]
  /** Activate context on focus. */
  val focusListener = new FocusListener() {
    def focusGained(e: FocusEvent) = context.activateBranch()
    def focusLost(e: FocusEvent) {}
  }
  /** Activate context on shell events. */
  val shellListener = new ShellAdapter() {
    override def shellActivated(e: ShellEvent) = context.activateBranch()
  }
  /** Actual sortBy column index */
  @volatile protected var sortColumn = 1
  /** Actual sort direction */
  @volatile protected var sortDirection = Default.sortingDirection

  /** Get actual enumerations */
  def getModifiedEnumerations(): Set[Enumeration[_ <: AnySRef]] = actual.toSet

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
    new ActionContributionItem(ActionCreate).fill(getCompositeFooter())
    new ActionContributionItem(ActionCreateFrom).fill(getCompositeFooter())
    new ActionContributionItem(ActionEdit).fill(getCompositeFooter())
    new ActionContributionItem(ActionRemove).fill(getCompositeFooter())
    ActionCreate.setEnabled(true)
    ActionCreateFrom.setEnabled(false)
    ActionEdit.setEnabled(false)
    ActionRemove.setEnabled(false)
    initTableEnumerations()
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
    // add dispose listener
    getShell().addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) {
        getShell().removeFocusListener(focusListener)
        getShell().removeShellListener(shellListener)
        actual.removeChangeListener(actualListener)
      }
    })
    // set dialog message
    setMessage(Messages.enumerationListDescription_text.format(graph.model.eId.name))
    // set dialog window title
    getShell().setText(Messages.enumerationListDialog_text.format(graph.model.eId.name))
    result
  }
  /** Generate new ID: old ID + 'Copy' + N */
  protected def getNewEnumerationCopyID(id: Symbol, enumerations: List[Enumeration[_ <: AnySRef]]): Symbol = {
    val sameIds = immutable.HashSet(enumerations.filter(_.id.name.startsWith(id.name)).map(_.id.name).toSeq: _*)
    var n = 0
    var newId = id.name + CoreMessages.copy_item_text
    while (sameIds(newId)) {
      n += 1
      newId = id.name + CoreMessages.copy_item_text + n
    }
    Symbol(newId)
  }
  /** Initialize tableEnumerationList */
  protected def initTableEnumerations() {
    val viewer = getTableViewer()
    viewer.setContentProvider(new ObservableListContentProvider())
    getTableViewerColumnAvailability.setLabelProvider(new ColumnAvailable.TLabelProvider)
    getTableViewerColumnAvailability.setEditingSupport(new ColumnAvailable.TEditingSupport(viewer, this))
    getTableViewerColumnAvailability.getColumn.addSelectionListener(new EnumerationList.EnumerationSelectionAdapter(WeakReference(viewer), 0))
    getTableViewerColumnId.setLabelProvider(new ColumnIdentificator.TLabelProvider)
    getTableViewerColumnId.setEditingSupport(new ColumnIdentificator.TEditingSupport(viewer, this))
    getTableViewerColumnId.getColumn.addSelectionListener(new EnumerationList.EnumerationSelectionAdapter(WeakReference(viewer), 1))
    getTableViewerColumnName.setLabelProvider(new ColumnName.TLabelProvider)
    getTableViewerColumnName.setEditingSupport(new ColumnName.TEditingSupport(viewer, this))
    getTableViewerColumnName.getColumn.addSelectionListener(new EnumerationList.EnumerationSelectionAdapter(WeakReference(viewer), 2))
    // Add a SWT.CHECK support
    viewer.getTable.addListener(SWT.Selection, new Listener() {
      def handleEvent(event: Event) = if (event.detail == SWT.CHECK)
        event.item match {
          case tableItem: TableItem ⇒
            val index = tableItem.getParent().indexOf(tableItem)
            viewer.getElementAt(index) match {
              case before: Enumeration[_] ⇒
                updateActualEnumeration(before, before.copy(availability = tableItem.getChecked()))
              case element ⇒
                log.fatal(s"unknown element $element")
            }
          case item ⇒
            log.fatal(s"unknown item $item")
        }
    })
    // Activate the tooltip support for the viewer
    ColumnViewerToolTipSupport.enableFor(viewer)
    // Add context menu
    val menuMgr = new MenuManager()
    val menu = menuMgr.createContextMenu(viewer.getControl)
    menuMgr.addMenuListener(new IMenuListener() {
      override def menuAboutToShow(manager: IMenuManager) {
        manager.add(ActionAutoResize)
      }
    })
    menuMgr.setRemoveAllWhenShown(true)
    viewer.getControl.setMenu(menu)
    // Add selection listener
    viewer.addSelectionChangedListener(new ISelectionChangedListener() {
      override def selectionChanged(event: SelectionChangedEvent) = event.getSelection() match {
        case selection: IStructuredSelection if !selection.isEmpty() ⇒
          ActionCreateFrom.setEnabled(true)
          ActionEdit.setEnabled(true)
          ActionRemove.setEnabled(true)
        case selection ⇒
          ActionCreateFrom.setEnabled(false)
          ActionEdit.setEnabled(false)
          ActionRemove.setEnabled(false)
      }
    })
    actual.addChangeListener(event ⇒ Future {
      if (ActionAutoResize.isChecked())
        App.exec { if (!viewer.getTable.isDisposed()) autoresize() }
    } onFailure {
      case e: Exception ⇒ log.error(e.getMessage(), e)
      case e ⇒ log.error(e.toString())
    })
    val comparator = new EnumerationList.EnumerationComparator(new WeakReference(this))
    viewer.setComparator(comparator)
    viewer.setInput(actual.underlying)
    App.bindingContext.bindValue(ViewersObservables.observeSingleSelection(viewer), enumerationField)
  }
  /** On dialog active */
  override protected def onActive = {
    updateOK()
    autoresize()
  }
  /** Updates an actual enumeration */
  protected[enumlist] def updateActualEnumeration(before: Enumeration[_ <: AnySRef],
    after: Enumeration[_ <: AnySRef]) {
    val index = actual.indexOf(before)
    actual.update(index, after)
    if (index == actual.size - 1)
      getTableViewer.refresh() // Workaround for the JFace bug. Force the last element modification.
    getTableViewer.setSelection(new StructuredSelection(after), true)
  }
  /** Update OK button state */
  protected def updateOK() = Option(getButton(IDialogConstants.OK_ID)).foreach(_.setEnabled(
    initial.size != actual.size ||
      !(initial, actual).zipped.forall { (initial, actual) ⇒
        (initial eq actual) || (
          initial.availability == actual.availability &&
          initial.name == actual.name &&
          initial.element.eq(actual.element) &&
          initial.id == actual.id &&
          initial.ptype == actual.ptype &&
          initial.constants.size == actual.constants.size &&
          (initial.constants, actual.constants).zipped.forall(Enumeration.compareDeep(_, _)))
      }))

  object ActionAutoResize extends Action(CoreMessages.autoresize_key) {
    setChecked(true)
    override def run = autoresize
  }
  object ActionCreate extends Action(CoreMessages.create_text) with XLoggable {
    override def run = {
      val newEnumerationID = payload.generateNew("NewEnumeration", "", newId ⇒ actual.exists(_.id.name == newId))
      val newEnumerationElement = Enumeration.factory(graph, Symbol(newEnumerationID), false)
      val newEnumeration = new Enumeration(newEnumerationElement.eRelative, PropertyType.defaultType)(Manifest.classType(PropertyType.defaultType.typeClass))
      OperationModifyEnumeration(graph, newEnumeration, actual.toSet).foreach { operation ⇒
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
              result.foreach { case (enumeration) ⇒ App.exec { actual += enumeration } }
            case Operation.Result.Cancel(message) ⇒
              log.warn(s"Operation canceled, reason: ${message}.")
            case other ⇒
              log.error(s"Unable to complete operation: ${other}.")
          }).schedule()
        }
      }
    }
  }
  object ActionCreateFrom extends Action(CoreMessages.createFrom_text) with XLoggable {
    override def run = Option(enumerationField.value) foreach { (selected) ⇒
      val from = selected.element
      // create new ID
      val toId = getNewEnumerationCopyID(from.eId, actual.toList)
      assert(!actual.exists(_.id == toId),
        s"Unable to create the enumeration copy. The element $toId is already exists")
      // create an element for the new template
      val to = from.eNode.copy(id = toId, unique = UUID.randomUUID).**
      // create an enumeration for the 'to' element
      val newEnumeration = new Enumeration(to.rootBox.e.eRelative, selected.ptype)(Manifest.classType(selected.ptype.typeClass)).
        asInstanceOf[Enumeration[_ <: AnySRef]]
      // start job
      OperationModifyEnumeration(graph, newEnumeration, actual.toSet).foreach { operation ⇒
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
              result.foreach { case (enumeration) ⇒ App.exec { actual += enumeration } }
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
    override def run = Option(enumerationField.value) foreach { (before) ⇒
      OperationModifyEnumeration(graph, before, actual.toSet).foreach { operation ⇒
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
              result.foreach { case (after) ⇒ App.exec { updateActualEnumeration(before, after) } }
            case Operation.Result.Cancel(message) ⇒
              log.warn(s"Operation canceled, reason: ${message}.")
            case other ⇒
              log.error(s"Unable to complete operation: ${other}.")
          }).schedule()
        }
      }
    }
  }
  object ActionRemove extends Action(CoreMessages.remove_text) {
    override def run = Option(enumerationField.value) foreach { (selected) ⇒
      App.exec { actual -= selected }
    }
  }
}

object EnumerationList extends XLoggable {
  class EnumerationComparator(dialog: WeakReference[EnumerationList]) extends ViewerComparator {
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
      val enum1 = e1.asInstanceOf[Enumeration[_]]
      val enum2 = e2.asInstanceOf[Enumeration[_]]
      val rc = column match {
        case 0 ⇒ enum1.availability.compareTo(enum2.availability)
        case 1 ⇒ enum1.id.name.compareTo(enum2.id.name)
        case 2 ⇒ enum1.name.compareTo(enum2.name)
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
  class EnumerationSelectionAdapter(tableViewer: WeakReference[TableViewer], column: Int) extends SelectionAdapter {
    override def widgetSelected(e: SelectionEvent) = {
      tableViewer.get.foreach(viewer ⇒ viewer.getComparator() match {
        case comparator: EnumerationComparator if comparator.column == column ⇒
          comparator.switchDirection()
          viewer.refresh()
        case comparator: EnumerationComparator ⇒
          comparator.column = column
          viewer.refresh()
        case _ ⇒
      })
    }
  }
}
