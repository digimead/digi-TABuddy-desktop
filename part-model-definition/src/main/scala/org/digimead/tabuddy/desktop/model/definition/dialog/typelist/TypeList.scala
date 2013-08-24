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

package org.digimead.tabuddy.desktop.model.definition.dialog.typelist

import java.util.UUID
import java.util.concurrent.locks.ReentrantLock

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future
import scala.ref.WeakReference

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Messages
import org.digimead.tabuddy.desktop.definition.Dialog
import org.digimead.tabuddy.desktop.definition.Operation
import org.digimead.tabuddy.desktop.logic.operation.OperationModifyTypeSchema
import org.digimead.tabuddy.desktop.logic.payload
import org.digimead.tabuddy.desktop.logic.payload.Payload.payload2implementation
import org.digimead.tabuddy.desktop.logic.payload.PropertyType
import org.digimead.tabuddy.desktop.model.definition.Default
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.WritableList
import org.digimead.tabuddy.desktop.support.WritableList.wrapper2underlying
import org.digimead.tabuddy.desktop.support.WritableValue
import org.digimead.tabuddy.desktop.support.WritableValue.wrapper2underlying
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Model.model2implementation
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.jface.action.Action
import org.eclipse.jface.action.ActionContributionItem
import org.eclipse.jface.action.IAction
import org.eclipse.jface.action.IMenuListener
import org.eclipse.jface.action.IMenuManager
import org.eclipse.jface.action.MenuManager
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

class TypeList(val parentShell: Shell, val initial: List[payload.api.TypeSchema], val initialActiveSchema: payload.api.TypeSchema)
  extends TypeListSkel(parentShell) with Dialog with Loggable {
  /** The actual schemas value */
  protected[typelist] val actual = WritableList(initial)
  /** The property representing active schema */
  protected val actualActiveSchema = WritableValue[UUID]
  /** The auto resize lock */
  protected val autoResizeLock = new ReentrantLock()
  /** The property representing selected schema */
  protected val schemaField = WritableValue[payload.api.TypeSchema]
  /** Actual sortBy column index */
  @volatile protected var sortColumn = 0
  /** Actual sort direction */
  @volatile protected var sortDirection = Default.sortingDirection

  /** Auto resize tableviewer columns */
  def autoresize() = if (autoResizeLock.tryLock()) try {
    Thread.sleep(50)
    App.execNGet {
      if (!getTableViewer.getTable.isDisposed()) {
        App.adjustTableViewerColumnWidth(getTableViewerColumnName, Default.columnPadding)
        getTableViewer.refresh()
      }
    }
  } finally {
    autoResizeLock.unlock()
  }
  /** Get active type schema */
  def getActiveSchema(): payload.api.TypeSchema = actual.find(_.id == actualActiveSchema.value).getOrElse(initialActiveSchema)
  /** Get modified type schemas */
  def getSchemaSet(): Set[payload.api.TypeSchema] = actual.toSet
  /** Create contents of the dialog. */
  override protected def createDialogArea(parent: Composite): Control = {
    val result = super.createDialogArea(parent)
    new ActionContributionItem(ActionActivate).fill(getCompositeActivator())
    new ActionContributionItem(ActionCreate).fill(getCompositeFooter())
    new ActionContributionItem(ActionCreateFrom).fill(getCompositeFooter())
    new ActionContributionItem(ActionEdit).fill(getCompositeFooter())
    new ActionContributionItem(ActionRemove).fill(getCompositeFooter())
    new ActionContributionItem(ActionReset).fill(getCompositeFooter())
    ActionActivate.setEnabled(false)
    ActionCreateFrom.setEnabled(false)
    ActionEdit.setEnabled(false)
    ActionRemove.setEnabled(false)
    ActionReset.setEnabled(false)
    getbtnResetSchema.addListener(SWT.Selection, new Listener() { def handleEvent(event: Event) = actualActiveSchema.value = initialActiveSchema.id })
    initTableTypeSchemas()
    val actualListener = actual.addChangeListener { event =>
      if (ActionAutoResize.isChecked())
        future { autoresize() } onFailure {
          case e: Exception => log.error(e.getMessage(), e)
          case e => log.error(e.toString())
        }
      updateOK()
    }
    val actualActiveSchemaListener = actualActiveSchema.addChangeListener { (activeSchema, event) =>
      getTextActiveSchema.setText(actual.find(_.id == activeSchema).getOrElse(initialActiveSchema).name)
      getTextActiveSchema.setToolTipText("id: " + activeSchema.toString)
      getbtnResetSchema().setEnabled(actualActiveSchema.value != initialActiveSchema.id)
      getTableViewer.getSelection match {
        case selection: IStructuredSelection if !selection.isEmpty() =>
          ActionActivate.setEnabled(!selection.getFirstElement().eq(activeSchema))
        case selection =>
      }
      updateOK()
    }
    actualActiveSchema.value = initialActiveSchema.id
    // add dispose listener
    getShell().addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) {
        actual.removeChangeListener(actualListener)
        actualActiveSchema.removeChangeListener(actualActiveSchemaListener)
      }
    })
    // set dialog message
    setMessage(Messages.typeListDescription_text.format(Model.eId.name))
    // set dialog window title
    getShell().setText(Messages.typeListDialog_text.format(Model.eId.name))
    result
  }
  /** Generate the new name: old name + ' Copy' + N */
  def getNewTypeSchemaCopyName(name: String): String = {
    val sameNames = immutable.HashSet(actual.filter(_.name.startsWith(name)).map(_.name).toSeq: _*)
    var n = 0
    var newName = name + " " + Messages.copy_item_text
    while (sameNames(newName)) {
      n += 1
      newName = name + " " + Messages.copy_item_text + n
    }
    newName
  }
  /** Initialize tableTypeList */
  protected def initTableTypeSchemas() {
    val viewer = getTableViewer()
    viewer.setContentProvider(new ObservableListContentProvider())
    getTableViewerColumnName.setLabelProvider(new ColumnName.TLabelProvider)
    getTableViewerColumnName.setEditingSupport(new ColumnName.TEditingSupport(viewer, this))
    getTableViewerColumnName.getColumn.addSelectionListener(new TypeList.SchemaSelectionAdapter(WeakReference(viewer), 0))
    getTableViewerColumnDescription.setLabelProvider(new ColumnDescription.TLabelProvider)
    getTableViewerColumnDescription.setEditingSupport(new ColumnDescription.TEditingSupport(viewer, this))
    getTableViewerColumnDescription.getColumn.addSelectionListener(new TypeList.SchemaSelectionAdapter(WeakReference(viewer), 1))
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
        case selection: IStructuredSelection if !selection.isEmpty() =>
          val selected = selection.getFirstElement().asInstanceOf[payload.api.TypeSchema]
          ActionActivate.setEnabled(selected.id ne actualActiveSchema.value)
          ActionCreateFrom.setEnabled(true)
          ActionEdit.setEnabled(true)
          ActionRemove.setEnabled(!payload.TypeSchema.predefined.exists(_.id == selected.id)) // exclude predefined
          ActionReset.setEnabled(payload.TypeSchema.predefined.find(_.id == selected.id) match {
            case Some(predefined) =>
              ActionReset.setToolTipText(Messages.typeSchemaPredefinedResetToDefault_text)
              selected.id ne predefined.id
            case None =>
              ActionReset.setToolTipText(Messages.typeSchemaResetUnknownTypes_text)
              selected.entity.keys.exists(ptypeId => !PropertyType.container.contains(ptypeId))
          })
        case selection =>
          ActionActivate.setEnabled(false)
          ActionCreateFrom.setEnabled(false)
          ActionEdit.setEnabled(false)
          ActionRemove.setEnabled(false)
          ActionReset.setEnabled(false)
          ActionReset.setToolTipText(null)
      }
    })
    viewer.setComparator(new TypeList.SchemaComparator(new WeakReference(this)))
    viewer.setInput(actual.underlying)
    App.bindingContext.bindValue(ViewersObservables.observeSingleSelection(viewer), schemaField)
  }
  /** On dialog active */
  override protected def onActive = {
    updateOK()
    future { autoresize() } onFailure {
      case e: Exception => log.error(e.getMessage(), e)
      case e => log.error(e.toString())
    }
  }
  /** Updates an actual schema */
  protected[typelist] def updateActualSchema(before: payload.api.TypeSchema, after: payload.api.TypeSchema) {
    val index = actual.indexOf(before)
    actual.update(index, after)
    if (index == actual.size - 1)
      getTableViewer.refresh() // Workaround for the JFace bug. Force the last element modification.
    getTableViewer.setSelection(new StructuredSelection(after), true)
  }
  /** Update OK button state */
  protected def updateOK() = Option(getButton(IDialogConstants.OK_ID)).foreach(_.setEnabled(
    actualActiveSchema.value != initialActiveSchema || initial.size != actual.size ||
      !(initial, actual).zipped.forall { (initial, actual) =>
        (initial eq actual) || (
          initial.id == actual.id &&
          initial.name == actual.name &&
          initial.description == actual.description &&
          initial.entity.size == actual.entity.size &&
          (initial.entity, actual.entity).zipped.forall((a, b) => payload.TypeSchema.compareDeep(a._2, b._2)))
      }))

  object ActionAutoResize extends Action(Messages.autoresize_key, IAction.AS_CHECK_BOX) {
    setChecked(true)
    override def run = if (isChecked())
      future { autoresize } onFailure {
        case e: Exception => log.error(e.getMessage(), e)
        case e => log.error(e.toString())
      }
  }
  object ActionActivate extends Action(Messages.activate_text) with Loggable {
    override def run = Option(schemaField.value) foreach { (selected) =>
      if (actualActiveSchema.value != selected.id)
        actualActiveSchema.value = selected.id
    }
  }
  object ActionCreate extends Action(Messages.create_text) with Loggable {
    override def run = {
      val newSchemaName = payload.Payload.generateNew(Messages.newTypeSchema_text, " ", newName => actual.exists(_.name == newName))
      val newSchema = payload.TypeSchema(UUID.randomUUID(), newSchemaName, "", immutable.HashMap(payload.TypeSchema.entities.map(e => (e.ptypeId, e)).toSeq: _*))
      OperationModifyTypeSchema(newSchema, actual.toSet, newSchema.id == actualActiveSchema.value).foreach { operation =>
        val job = if (operation.canRedo())
          Some(operation.redoJob())
        else if (operation.canExecute())
          Some(operation.executeJob())
        else
          None
        job foreach { job =>
          job.setPriority(Job.SHORT)
          job.onComplete(_ match {
            case Operation.Result.OK(result, message) =>
              log.info(s"Operation completed successfully: ${result}")
              result.foreach {
                case (schema, active) => App.exec {
                  actual += schema
                  if (active)
                    actualActiveSchema.value = schema.id
                }
              }
            case Operation.Result.Cancel(message) =>
              log.warn(s"Operation canceled, reason: ${message}.")
            case other =>
              log.error(s"Unable to complete operation: ${other}.")
          }).schedule()
        }
      }
    }
  }
  object ActionCreateFrom extends Action(Messages.createFrom_text) with Loggable {
    override def run = Option(schemaField.value) foreach { (selected) =>
      val from = selected
      // create a new ID
      val toName = getNewTypeSchemaCopyName(from.name)
      assert(!actual.exists(_.name == toName),
        s"Unable to create the type schema copy. The schema $toName is already exists")
      val to = payload.TypeSchema(UUID.randomUUID(), toName, from.description, from.entity.map(e => (e._1, e._2.copy())))
      OperationModifyTypeSchema(to, actual.toSet, to.id == actualActiveSchema.value).foreach { operation =>
        val job = if (operation.canRedo())
          Some(operation.redoJob())
        else if (operation.canExecute())
          Some(operation.executeJob())
        else
          None
        job foreach { job =>
          job.setPriority(Job.SHORT)
          job.onComplete(_ match {
            case Operation.Result.OK(result, message) =>
              log.info(s"Operation completed successfully: ${result}")
              result.foreach {
                case (schema, active) => App.exec {
                  actual += schema
                  if (active)
                    actualActiveSchema.value = schema.id
                }
              }
            case Operation.Result.Cancel(message) =>
              log.warn(s"Operation canceled, reason: ${message}.")
            case other =>
              log.error(s"Unable to complete operation: ${other}.")
          }).schedule()
        }
      }
    }
  }
  object ActionEdit extends Action(Messages.edit_text) {
    override def run = Option(schemaField.value) foreach { (before) =>
      OperationModifyTypeSchema(before, actual.toSet, before.id == actualActiveSchema.value).foreach { operation =>
        val job = if (operation.canRedo())
          Some(operation.redoJob())
        else if (operation.canExecute())
          Some(operation.executeJob())
        else
          None
        job foreach { job =>
          job.setPriority(Job.SHORT)
          job.onComplete(_ match {
            case Operation.Result.OK(result, message) =>
              log.info(s"Operation completed successfully: ${result}")
              result.foreach {
                case (after, active) => App.exec {
                  if (!payload.TypeSchema.compareDeep(before, after))
                    updateActualSchema(before, after)
                  if (active)
                    actualActiveSchema.value = after.id
                  else if (actualActiveSchema.value == before)
                    actualActiveSchema.value = initialActiveSchema.id
                }
              }
            case Operation.Result.Cancel(message) =>
              log.warn(s"Operation canceled, reason: ${message}.")
            case other =>
              log.error(s"Unable to complete operation: ${other}.")
          }).schedule()
        }
      }
    }
  }
  object ActionRemove extends Action(Messages.remove_text) {
    override def run = Option(schemaField.value) foreach { (selected) =>
      if (!payload.TypeSchema.predefined.exists(_.id == selected.id))
        actual -= selected
    }
  }
  object ActionReset extends Action(Messages.reset_text) {
    override def run = Option(schemaField.value) foreach { (selected) =>
      payload.TypeSchema.predefined.find(_.id == selected.id) match {
        case Some(predefined) =>
          updateActualSchema(selected, predefined)
        case None => // remove unknown or invalid entities
          val fixed = selected.entity.filter(entity => PropertyType.container.contains(entity._2.ptypeId))
          if (fixed.size != selected.entity.size)
            updateActualSchema(selected, selected.copy(entity = fixed))
      }
    }
  }
}

object TypeList extends Loggable {
  class SchemaComparator(dialog: WeakReference[TypeList]) extends ViewerComparator {
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
      val schema1 = e1.asInstanceOf[payload.api.TypeSchema]
      val schema2 = e2.asInstanceOf[payload.api.TypeSchema]
      val rc = column match {
        case 0 => schema1.name.compareTo(schema2.name)
        case 1 => schema1.description.compareTo(schema2.description)
        case index =>
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
  class SchemaSelectionAdapter(tableViewer: WeakReference[TableViewer], column: Int) extends SelectionAdapter {
    override def widgetSelected(e: SelectionEvent) = {
      tableViewer.get.foreach(viewer => viewer.getComparator() match {
        case comparator: SchemaComparator if comparator.column == column =>
          comparator.switchDirection()
          viewer.refresh()
        case comparator: SchemaComparator =>
          comparator.column = column
          viewer.refresh()
        case _ =>
      })
    }
  }
}
