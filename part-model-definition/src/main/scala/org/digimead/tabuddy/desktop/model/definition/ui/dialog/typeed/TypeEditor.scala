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

package org.digimead.tabuddy.desktop.model.definition.ui.dialog.typeed

import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.Messages
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.core.operation.OperationCustomTranslations
import org.digimead.tabuddy.desktop.core.support.{ App, WritableList, WritableValue }
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.desktop.logic.payload.{ Enumeration, Payload, PropertyType, TypeSchema }
import org.digimead.tabuddy.desktop.model.definition.Default
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.core.ui.definition.Dialog
import org.digimead.tabuddy.desktop.core.ui.support.{ SymbolValidator, TextValidator }
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.dsl.DSLType
import org.digimead.tabuddy.model.graph.Graph
import org.eclipse.core.databinding.observable.ChangeEvent
import org.eclipse.core.databinding.observable.IChangeListener
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

class TypeEditor @Inject() (
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
  /** The initial type schema. */
  val initial: TypeSchema,
  /** The list of type schemas. */
  val typeSchemas: Set[TypeSchema],
  /** Flag indicating whether the initial schema is active. */
  isSchemaActive: Boolean)
  extends TypeEditorSkel(parentShell) with Dialog with XLoggable {
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** Actual schema entities */
  protected[typeed] lazy val actualEntities = WritableList(initialEntities)
  /** The property representing the current schema 'active' flag state */
  protected val activeField = WritableValue(Boolean.box(isSchemaActive))
  /** The auto resize lock */
  protected val autoResizeLock = new ReentrantLock()
  /** The property representing the current schema description */
  protected val descriptionField = WritableValue("")
  /** The property representing a selected entity */
  protected val entityField = WritableValue[TypeSchema.Entity[_ <: AnySRef]]
  /** Activate context on focus. */
  protected val focusListener = new FocusListener() {
    def focusGained(e: FocusEvent) = context.activateBranch()
    def focusLost(e: FocusEvent) {}
  }
  /** Initial schema entities */
  protected lazy val initialEntities = initial.entity.values.toList.sortBy(_.ptypeId.name)
  /** The property representing the current schema name */
  protected val nameField = WritableValue("")
  /** The property contain nameField validation, it is modified only from UI thread */
  protected var nameFieldValid = true
  /** Activate context on shell events. */
  protected val shellListener = new ShellAdapter() {
    override def shellActivated(e: ShellEvent) = context.activateBranch()
  }
  /** Actual sortBy column index */
  @volatile private var sortColumn = 1
  /** Actual sort direction */
  @volatile private var sortDirection = Default.sortingDirection

  /** Get the modified type schema */
  def getModifiedSchema(): TypeSchema = initial.copy(
    name = nameField.value.trim,
    description = descriptionField.value.trim,
    entity = immutable.HashMap(actualEntities.map(entity ⇒ (entity.ptypeId,
      entity.copy(alias = entity.alias.trim,
        description = entity.description.trim))): _*))
  /** Get the modified type schema */
  def getModifiedSchemaActiveFlag(): Boolean = activeField.value

  /** Auto resize tableviewer columns */
  protected def autoresize() = if (autoResizeLock.tryLock()) try {
    Thread.sleep(50)
    App.execNGet {
      if (!getTableViewer.getTable.isDisposed()) {
        UI.adjustViewerColumnWidth(getTableViewerColumnAvailability(), Default.columnPadding)
        UI.adjustViewerColumnWidth(getTableViewerColumnType(), Default.columnPadding)
        UI.adjustViewerColumnWidth(getTableViewerColumnAlias(), Default.columnPadding)
        UI.adjustViewerColumnWidth(getTableViewerColumnView(), Default.columnPadding)
        UI.adjustViewerColumnWidth(getTableViewerColumnLabel(), Default.columnPadding)
        getTableViewer().refresh()
      }
    }
  } finally {
    autoResizeLock.unlock()
  }
  /** Create contents of the dialog. */
  override protected def createDialogArea(parent: Composite): Control = {
    val result = super.createDialogArea(parent)
    context.set(classOf[Composite], parent)
    new ActionContributionItem(ActionAliasLookup).fill(getCompositeFooter())
    ActionAliasLookup.setEnabled(false)
    // Bind the schema info: a name
    nameField.value = initial.name
    App.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextSchemaName()), nameField)
    val validator = TextValidator(getTextSchemaName, true) { (validator, event) ⇒ }
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
    descriptionField.value = initial.description
    App.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextSchemaDescription()), descriptionField)
    // Bind the schema info: an active flag
    App.bindingContext.bindValue(WidgetProperties.selection().observe(getBtnCheckActive()), activeField)
    // Initialize the table
    initTableTypeSchemas()
    // Handle modifications
    val actualEntitiesListener = actualEntities.addChangeListener { event ⇒
      if (ActionAutoResize.isChecked())
        Future { autoresize() } onFailure {
          case e: Exception ⇒ log.error(e.getMessage(), e)
          case e ⇒ log.error(e.toString())
        }
      updateOK()
    }
    val entityListener = entityField.addChangeListener { (entity, _) ⇒ ActionAliasLookup.setEnabled(entity != null) }
    val nameListener = nameField.addChangeListener { (_, _) ⇒ updateOK }
    val descriptionListener = descriptionField.addChangeListener { (_, _) ⇒ updateOK }
    val activeFlagListener = activeField.addChangeListener { (_, _) ⇒ updateOK() }
    getShell().addShellListener(shellListener)
    getShell().addFocusListener(focusListener)
    // Add the dispose listener
    getShell().addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) {
        getShell().removeFocusListener(focusListener)
        getShell().removeShellListener(shellListener)
        actualEntities.removeChangeListener(actualEntitiesListener)
        entityField.removeChangeListener(entityListener)
        nameField.removeChangeListener(nameListener)
        descriptionField.removeChangeListener(descriptionListener)
        activeField.removeChangeListener(activeFlagListener)
      }
    })
    // Set the dialog message
    setMessage(Messages.typeEditorDescription_text.format(graph.model.eId.name))
    // Set the dialog window title
    getShell().setText(Messages.typeEditorDialog_text.format(initial.name))
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
    getTableViewerColumnLabel.setLabelProvider(new ColumnDescription.TLabelProvider)
    getTableViewerColumnLabel.setEditingSupport(new ColumnDescription.TEditingSupport(viewer, this))
    getTableViewerColumnLabel.getColumn.addSelectionListener(new TypeEditor.SchemaSelectionAdapter(WeakReference(viewer), 4))
    // Add SWT.CHECK support
    viewer.getTable.addListener(SWT.Selection, new Listener() {
      def handleEvent(event: Event) = if (event.detail == SWT.CHECK)
        event.item match {
          case tableItem: TableItem ⇒
            val index = tableItem.getParent().indexOf(tableItem)
            viewer.getElementAt(index) match {
              case before: TypeSchema.Entity[_] if PropertyType.container.contains(before.ptypeId) ⇒
                val availability = tableItem.getChecked()
                if (before.availability != availability)
                  updateActualEntity(before, before.copy(availability = availability))
              case before: TypeSchema.Entity[_] ⇒ // type is unknown
                tableItem.setChecked(false)
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
    // Add the comparator
    viewer.setComparator(new TypeEditor.SchemaComparator(new WeakReference(this)))
    viewer.setInput(actualEntities.underlying)
    App.bindingContext.bindValue(ViewersObservables.observeSingleSelection(viewer), entityField)
    autoresize()
  }
  /** On dialog active */
  override protected def onActive() = {
    updateOK()
    Future { autoresize() } onFailure {
      case e: Exception ⇒ log.error(e.getMessage(), e)
      case e ⇒ log.error(e.toString())
    }
  }
  /** Updates an actual entity */
  protected[typeed] def updateActualEntity(before: TypeSchema.Entity[_ <: AnySRef], after: TypeSchema.Entity[_ <: AnySRef]) {
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
      initial.description == descriptionField.value.trim &&
      initialEntities.size == actualEntities.size &&
      (initialEntities, actualEntities).zipped.forall(TypeSchema.compareDeep(_, _))
  } && actualEntities.filter(e ⇒ PropertyType.container.contains(e.ptypeId)).exists(_.availability)))

  object ActionAutoResize extends Action(Messages.autoresize_key, IAction.AS_CHECK_BOX) {
    setChecked(true)
    override def run = if (isChecked())
      Future { autoresize } onFailure {
        case e: Exception ⇒ log.error(e.getMessage(), e)
        case e ⇒ log.error(e.toString())
      }
  }
  object ActionAliasLookup extends Action(Messages.lookupAliasInTranslations_text) {
    override def run = Option(entityField.value) foreach { before ⇒
      OperationCustomTranslations().foreach { operation ⇒
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
                case (key, value, singleton) ⇒ App.exec {
                  val alias = "*" + key.trim
                  if (before.alias != alias) {
                    val after = before.copy(alias = alias)
                    val index = actualEntities.indexOf(before)
                    actualEntities.update(index, after)
                    if (index == actualEntities.size - 1)
                      getTableViewer().refresh() // Workaround for the JFace bug. Force the last element modification.
                  }
                }
              }
            case Operation.Result.Cancel(message) ⇒
              log.warn(s"Operation canceled, reason: ${message}.")
            case other ⇒
              log.error(s"Unable to complete operation: ${other}.")
          }).schedule()
        }
      }
      if (ActionAutoResize.isChecked())
        autoresize()
    }
  }
}

object TypeEditor extends XLoggable {
  class SchemaComparator(dialog: WeakReference[TypeEditor]) extends ViewerComparator {
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
      val entity1 = e1.asInstanceOf[TypeSchema.Entity[_ <: AnySRef]]
      val entity2 = e2.asInstanceOf[TypeSchema.Entity[_ <: AnySRef]]
      val rc = column match {
        case 0 ⇒ entity1.availability.compareTo(entity2.availability)
        case 1 ⇒ entity1.ptypeId.name.compareTo(entity2.ptypeId.name)
        case 2 ⇒ entity1.alias.compareTo(entity2.alias)
        case 3 ⇒ entity1.view.compareTo(entity2.view)
        case 4 ⇒ entity1.description.compareTo(entity2.description)
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
  class SchemaSelectionAdapter(tableViewer: WeakReference[TableViewer], column: Int) extends SelectionAdapter {
    override def widgetSelected(e: SelectionEvent) = {
      tableViewer.get.foreach(viewer ⇒ viewer.getComparator() match {
        case comparator: SchemaComparator if comparator.column == column ⇒
          comparator.switchDirection()
          viewer.refresh()
        case comparator: SchemaComparator ⇒
          comparator.column = column
          viewer.refresh()
        case _ ⇒
      })
    }
  }
}
