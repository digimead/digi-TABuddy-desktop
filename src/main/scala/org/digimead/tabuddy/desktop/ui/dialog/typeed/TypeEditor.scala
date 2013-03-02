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

package org.digimead.tabuddy.desktop.ui.dialog.typeed

import java.util.concurrent.locks.ReentrantLock

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future
import scala.ref.WeakReference

import org.digimead.digi.lib.log.Loggable
import org.digimead.tabuddy.desktop.Main
import org.digimead.tabuddy.desktop.payload.PropertyType
import org.digimead.tabuddy.desktop.payload.TypeSchema
import org.digimead.tabuddy.desktop.res.Messages
import org.digimead.tabuddy.desktop.support.Validator
import org.digimead.tabuddy.desktop.support.WritableList
import org.digimead.tabuddy.desktop.support.WritableValue
import org.digimead.tabuddy.desktop.support.WritableValue.wrapper2underlying
import org.digimead.tabuddy.desktop.ui.dialog.Dialog
import org.digimead.tabuddy.desktop.ui.dialog.TranslationLookup
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Model.model2implementation
import org.eclipse.core.databinding.observable.ChangeEvent
import org.eclipse.core.databinding.observable.IChangeListener
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
import org.eclipse.jface.viewers.StructuredSelection
import org.eclipse.jface.viewers.TableViewer
import org.eclipse.jface.viewers.Viewer
import org.eclipse.jface.viewers.ViewerComparator
import org.eclipse.jface.window.Window
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

class TypeEditor(val parentShell: Shell, val initial: TypeSchema.Interface, val typeSchemas: List[TypeSchema.Interface], isSchemaActive: Boolean)
  extends org.digimead.tabuddy.desktop.res.dialog.TypeEditor(parentShell) with Dialog with Loggable {
  /** Actual schema entities */
  protected[typeed] lazy val actualEntities = WritableList(initialEntities)
  /** The property representing the current schema 'active' flag state */
  protected val activeField = WritableValue(Boolean.box(isSchemaActive))
  /** The auto resize lock */
  protected val autoResizeLock = new ReentrantLock()
  /** The property representing the current schema label */
  protected val labelField = WritableValue("")
  /** The property representing a selected entity */
  protected val entityField = WritableValue[TypeSchema.Entity[_ <: AnyRef with java.io.Serializable]]
  /** Initial schema entities */
  protected lazy val initialEntities = initial.entity.values.toList.sortBy(_.ptypeId.name)
  /** The property representing the current schema name */
  protected val nameField = WritableValue("")
  /** The property contain nameField validation, it is modified only from UI thread */
  protected var nameFieldValid = true
  assert(TypeEditor.dialog.isEmpty, "TypeEditor dialog is already active")

  /** Get the modified type schema */
  def getModifiedSchema(): TypeSchema.Interface = initial.copy(
    name = nameField.value.trim,
    label = labelField.value.trim,
    entity = immutable.HashMap(actualEntities.map(entity => (entity.ptypeId,
      entity.copy(alias = entity.alias.trim,
        label = entity.label.trim))): _*))
  /** Get the modified type schema */
  def getModifiedSchemaActiveFlag(): Boolean = activeField.value

  /** Auto resize tableviewer columns */
  protected def autoresize() = if (autoResizeLock.tryLock()) try {
    Thread.sleep(50)
    Main.execNGet {
      if (!getTableViewer.getTable.isDisposed()) {
        adjustColumnWidth(getTableViewerColumnAvailability(), Dialog.columnPadding)
        adjustColumnWidth(getTableViewerColumnType(), Dialog.columnPadding)
        adjustColumnWidth(getTableViewerColumnAlias(), Dialog.columnPadding)
        adjustColumnWidth(getTableViewerColumnView(), Dialog.columnPadding)
        adjustColumnWidth(getTableViewerColumnDescription(), Dialog.columnPadding)
        getTableViewer().refresh()
      }
    }
  } finally {
    autoResizeLock.unlock()
  }
  /** Create contents of the dialog. */
  override protected def createDialogArea(parent: Composite): Control = {
    val result = super.createDialogArea(parent)
    new ActionContributionItem(TypeEditor.ActionAliasLookup).fill(getCompositeSchemaActions())
    TypeEditor.ActionAliasLookup.setEnabled(false)
    // Bind the schema info: a name
    nameField.value = initial.name
    Main.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextSchemaName()), nameField)
    val validator = Validator(getTextSchemaName, true) { (validator, event) => }
    WidgetProperties.text(SWT.Modify).observe(getTextSchemaName).addChangeListener(new IChangeListener() {
      override def handleChange(event: ChangeEvent) = {
        val newName = getTextSchemaName.getText().trim
        if (newName.isEmpty()) {
          validator.withDecoration(validator.showDecorationRequired(_))
          nameFieldValid = false
        } else if (typeSchemas.exists(_.name == newName) && newName != initial.name) {
          validator.withDecoration(validator.showDecorationError(_, Messages.nameIsAlreadyInUse_text.format(newName)))
          nameFieldValid = false
        } else {
          validator.withDecoration(_.hide)
          nameFieldValid = true
        }
      }
    })
    // Bind the schema info: a label
    labelField.value = initial.label
    Main.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextSchemaDescription()), labelField)
    // Bind the schema info: an active flag
    Main.bindingContext.bindValue(WidgetProperties.selection().observe(getBtnCheckActive()), activeField)
    // Initialize the table
    initTableTypeSchemas()
    // Handle modifications
    val actualEntitiesListener = actualEntities.addChangeListener { event =>
      if (TypeEditor.ActionAutoResize.isChecked())
        future { autoresize() }
      updateOK()
    }
    val entityListener = entityField.addChangeListener { event => TypeEditor.ActionAliasLookup.setEnabled(entityField.value != null) }
    val nameListener = nameField.addChangeListener { event => updateOK }
    val labelListener = labelField.addChangeListener { event => updateOK }
    val activeFlagListener = activeField.addChangeListener { event => updateOK() }
    // Add the dispose listener
    getShell().addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) {
        actualEntities.removeChangeListener(actualEntitiesListener)
        entityField.removeChangeListener(entityListener)
        nameField.removeChangeListener(nameListener)
        labelField.removeChangeListener(labelListener)
        activeField.removeChangeListener(activeFlagListener)
        TypeEditor.dialog = None
      }
    })
    // Set the dialog message
    setMessage(Messages.typeEditorDescription_text.format(Model.eId.name))
    // Set the dialog window title
    getShell().setText(Messages.typeEditorDialog_text.format(initial.name))
    TypeEditor.dialog = Some(this)
    result
  }
  /** Initialize tableTypeEditor */
  protected def initTableTypeSchemas() {
    val viewer = getTableViewer()
    viewer.setContentProvider(new ObservableListContentProvider())
    getTableViewerColumnAvailability.setLabelProvider(new ColumnAvailability.TLabelProvider)
    getTableViewerColumnAvailability.setEditingSupport(new ColumnAvailability.TEditingSupport(viewer, this))
    getTableViewerColumnAvailability.getColumn.addSelectionListener(new TypeEditor.SchemaSelectionAdapter(WeakReference(viewer), 0))
    getTableViewerColumnType.setLabelProvider(new ColumnType.TLabelProvider)
    getTableViewerColumnType.getColumn.addSelectionListener(new TypeEditor.SchemaSelectionAdapter(WeakReference(viewer), 1))
    getTableViewerColumnAlias.setLabelProvider(new ColumnAlias.TLabelProvider)
    getTableViewerColumnAlias.setEditingSupport(new ColumnAlias.TEditingSupport(viewer, this))
    getTableViewerColumnAlias.getColumn.addSelectionListener(new TypeEditor.SchemaSelectionAdapter(WeakReference(viewer), 2))
    getTableViewerColumnView.setLabelProvider(new ColumnView.TLabelProvider)
    getTableViewerColumnView.getColumn.addSelectionListener(new TypeEditor.SchemaSelectionAdapter(WeakReference(viewer), 3))
    getTableViewerColumnDescription.setLabelProvider(new ColumnLabel.TLabelProvider)
    getTableViewerColumnDescription.setEditingSupport(new ColumnLabel.TEditingSupport(viewer, this))
    getTableViewerColumnDescription.getColumn.addSelectionListener(new TypeEditor.SchemaSelectionAdapter(WeakReference(viewer), 4))
    // Add SWT.CHECK support
    viewer.getTable.addListener(SWT.Selection, new Listener() {
      def handleEvent(event: Event) = if (event.detail == SWT.CHECK)
        event.item match {
          case tableItem: TableItem =>
            val index = tableItem.getParent().indexOf(tableItem)
            viewer.getElementAt(index) match {
              case before: TypeSchema.Entity[_] if PropertyType.container.contains(before.ptypeId) =>
                val availability = tableItem.getChecked()
                if (before.availability != availability)
                  updateActualEntity(before, before.copy(availability = availability))
              case before: TypeSchema.Entity[_] => // type is unknown
                tableItem.setChecked(false)
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
        manager.add(TypeEditor.ActionAutoResize)
      }
    })
    menuMgr.setRemoveAllWhenShown(true)
    viewer.getControl.setMenu(menu)
    // Add the comparator
    viewer.setComparator(new TypeEditor.SchemaComparator)
    viewer.setInput(actualEntities.underlying)
    Main.bindingContext.bindValue(ViewersObservables.observeSingleSelection(viewer), entityField)
    autoresize()
  }
  /** On dialog active */
  override protected def onActive = {
    updateOK()
    future { autoresize() }
  }
  /** Updates an actual entity */
  protected[typeed] def updateActualEntity(before: TypeSchema.Entity[_ <: AnyRef with java.io.Serializable], after: TypeSchema.Entity[_ <: AnyRef with java.io.Serializable]) {
    val index = actualEntities.indexOf(before)
    actualEntities.update(index, after)
    if (index == actualEntities.size - 1)
      getTableViewer.refresh() // Workaround for the JFace bug. Force the last element modification.
    getTableViewer.setSelection(new StructuredSelection(after), true)
  }
  /** Update OK button state */
  protected def updateOK() = Option(getButton(IDialogConstants.OK_ID)).foreach(_.setEnabled(nameFieldValid && !{
    typeSchemas.contains(initial) &&
      activeField.value == isSchemaActive &&
      initial.name == nameField.value.trim &&
      initial.label == labelField.value.trim &&
      initialEntities.size == actualEntities.size &&
      (initialEntities, actualEntities).zipped.forall(TypeSchema.compareDeep(_, _))
  } && actualEntities.filter(e => PropertyType.container.contains(e.ptypeId)).exists(_.availability)))
}

object TypeEditor extends Loggable {
  /** There is may be only one dialog instance at time */
  @volatile private var dialog: Option[TypeEditor] = None
  /** Default sort direction */
  private val defaultDirection = Dialog.ASCENDING
  /** Actual sortBy column index */
  @volatile private var sortColumn = 1
  /** Actual sort direction */
  @volatile private var sortDirection = defaultDirection

  /** Apply a f(x) to the selected type entity if any */
  def entity[T](f: TypeSchema.Entity[_ <: AnyRef with java.io.Serializable] => T): Option[T] =
    dialog.flatMap(_.entityField.value match {
      case selected if selected != null => Option(f(selected))
      case _ => None
    })

  class SchemaComparator extends ViewerComparator {
    private var _column = TypeEditor.sortColumn
    private var _direction = TypeEditor.sortDirection

    /** Active column getter */
    def column = _column
    /** Active column setter */
    def column_=(arg: Int) {
      _column = arg
      TypeEditor.sortColumn = _column
      _direction = TypeEditor.defaultDirection
      TypeEditor.sortDirection = _direction
    }
    /** Sorting direction */
    def direction = _direction
    /**
     * Returns a negative, zero, or positive number depending on whether
     * the first element is less than, equal to, or greater than
     * the second element.
     */
    override def compare(viewer: Viewer, e1: Object, e2: Object): Int = {
      val entity1 = e1.asInstanceOf[TypeSchema.Entity[_ <: AnyRef with java.io.Serializable]]
      val entity2 = e2.asInstanceOf[TypeSchema.Entity[_ <: AnyRef with java.io.Serializable]]
      val rc = column match {
        case 0 => entity1.availability.compareTo(entity2.availability)
        case 1 => entity1.ptypeId.name.compareTo(entity2.ptypeId.name)
        case 2 => entity1.alias.compareTo(entity2.alias)
        case 3 => entity1.view.compareTo(entity2.view)
        case 4 => entity1.label.compareTo(entity2.label)
        case index =>
          log.fatal(s"unknown column with index $index"); 0
      }
      if (_direction == Dialog.DESCENDING) -rc else rc
    }
    /** Switch comparator direction */
    def switchDirection() {
      _direction = 1 - _direction
      TypeEditor.sortDirection = _direction
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
  /*
   * Actions
   */
  object ActionAutoResize extends Action(Messages.autoresize_key, IAction.AS_CHECK_BOX) {
    setChecked(true)
    override def run = if (isChecked()) TypeEditor.dialog.foreach(dialog => future { dialog.autoresize })
  }
  object ActionAliasLookup extends Action(Messages.lookupAliasInTranslations_text) {
    override def run = TypeEditor.entity { before =>
      dialog.foreach { dialog =>
        val translationDialog = new TranslationLookup(dialog.getShell())
        if (translationDialog.open() == Window.OK) {
          translationDialog.getSelected.foreach(translation => {
            val alias = "*" + translation.getKey.trim
            if (before.alias != alias) {
              val after = before.copy(alias = alias)
              val index = dialog.actualEntities.indexOf(before)
              dialog.actualEntities.update(index, after)
              if (index == dialog.actualEntities.size - 1)
                dialog.getTableViewer().refresh() // Workaround for the JFace bug. Force the last element modification.
            }
          })
          if (ActionAutoResize.isChecked())
            dialog.autoresize()
        }
      }
    }
  }
}
