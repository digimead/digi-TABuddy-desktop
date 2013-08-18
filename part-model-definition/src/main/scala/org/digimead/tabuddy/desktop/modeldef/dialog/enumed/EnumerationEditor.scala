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

package org.digimead.tabuddy.desktop.modeldef.dialog.enumed

import java.util.concurrent.locks.ReentrantLock

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future
import scala.ref.WeakReference

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Messages
import org.digimead.tabuddy.desktop.definition.Dialog
import org.digimead.tabuddy.desktop.logic.Data
import org.digimead.tabuddy.desktop.logic.payload
import org.digimead.tabuddy.desktop.modeldef.Default
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.SymbolValidator
import org.digimead.tabuddy.desktop.support.Validator
import org.digimead.tabuddy.desktop.support.WritableList
import org.digimead.tabuddy.desktop.support.WritableList.wrapper2underlying
import org.digimead.tabuddy.desktop.support.WritableValue
import org.digimead.tabuddy.desktop.support.WritableValue.wrapper2underlying
import org.digimead.tabuddy.model.dsl.DSLType
import org.digimead.tabuddy.model.dsl.DSLType.dsltype2implementation
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.element.Stash
import org.eclipse.jface.action.Action
import org.eclipse.jface.action.ActionContributionItem
import org.eclipse.jface.action.IAction
import org.eclipse.jface.action.IMenuListener
import org.eclipse.jface.action.IMenuManager
import org.eclipse.jface.action.MenuManager
import org.eclipse.jface.databinding.swt.WidgetProperties
import org.eclipse.jface.databinding.viewers.ObservableListContentProvider
import org.eclipse.jface.dialogs.IDialogConstants
import org.eclipse.jface.viewers.ArrayContentProvider
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.viewers.LabelProvider
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
import org.eclipse.swt.widgets.Shell
import org.eclipse.swt.widgets.Text

class EnumerationEditor(val parentShell: Shell, val initial: payload.api.Enumeration[_ <: AnyRef with java.io.Serializable],
  val enumerations: List[payload.api.Enumeration[_ <: AnyRef with java.io.Serializable]])
  extends EnumerationEditorSkel(parentShell) with Dialog with Loggable {
  /** Actual enumeration constants */
  protected[enumed] lazy val actualConstants = WritableList(initialConstants)
  /** The auto resize lock */
  protected val autoResizeLock = new ReentrantLock()
  /** The property representing current enumeration availability */
  protected val availabilityField = WritableValue[java.lang.Boolean]
  /** The property representing current enumeration name */
  protected val nameField = WritableValue[String]
  /** The property representing current enumeration id */
  protected val idField = WritableValue[String]
  /** Actual enumeration constants */
  protected val initialConstants = getInitialContent(initial)
  /** Actual sortBy column index */
  @volatile protected var sortColumn = 0
  /** Actual sort direction */
  @volatile protected var sortDirection = Default.sortingDirection
  /** The property representing current enumeration type */
  protected val typeField = WritableValue[java.lang.Integer]
  /** List of available types */
  protected val types: Array[payload.api.PropertyType[_ <: AnyRef with java.io.Serializable]] = {
    val userTypes = Data.getAvailableTypes().filter(_.enumerationSupported)
    // add an initial enumeration if absent
    (if (userTypes.contains(initial.ptype)) userTypes else (userTypes :+ initial.ptype)).sortBy(_.id.name).toArray
  }

  /** Get an actual enumeration */
  def getModifiedEnumeration(): payload.api.Enumeration[_ <: AnyRef with java.io.Serializable] = {
    val newId = Symbol(idField.value.trim)
    val newElement: Element.Generic = if (initial.id == newId)
      initial.element
    else
      initial.element.asInstanceOf[Element[Stash]].eCopy(initial.element.eStash.copy(id = newId)).asInstanceOf[Element.Generic]
    val newType = types(typeField.value).asInstanceOf[payload.api.PropertyType[AnyRef with java.io.Serializable]]
    val newConstants = actualConstants.map {
      case EnumerationEditor.Item(value, alias, description) =>
        payload.Enumeration.Constant(newType.valueFromString(value), alias, description)(newType, Manifest.classType(newType.typeClass))
    }.toSet: Set[payload.api.Enumeration.Constant[AnyRef with java.io.Serializable]]
    val name = nameField.value.trim
    new payload.Enumeration(newElement, newType, availabilityField.value, if (name.isEmpty()) newId.name else name, newConstants)(Manifest.classType(newType.typeClass))
  }

  /** Auto resize tableviewer columns */
  protected def autoresize() = if (autoResizeLock.tryLock()) try {
    Thread.sleep(50)
    App.execNGet {
      if (!getTableViewer.getTable.isDisposed()) {
        App.adjustTableViewerColumnWidth(getTableViewerColumnValue(), Default.columnPadding)
        App.adjustTableViewerColumnWidth(getTableViewerColumnAlias(), Default.columnPadding)
        getTableViewer.refresh()
      }
    }
  } finally {
    autoResizeLock.unlock()
  }
  /** Builds the dialog message */
  protected def updateDescription(error: Option[String]): String = {
    Messages.enumerationEditorDescription_text.format(idField.value) +
      (error match {
        case Some(error) => "\n    * - " + error
        case None => "\n "
      })
  }
  /** Create contents of the dialog. */
  override protected def createDialogArea(parent: Composite): Control = {
    // create dialog elements
    val result = super.createDialogArea(parent)
    new ActionContributionItem(ActionAdd).fill(getCompositeFooter())
    new ActionContributionItem(ActionDelete).fill(getCompositeFooter())
    ActionAdd.setEnabled(true)
    ActionDelete.setEnabled(false)
    initTableEnumerations
    // bind the enumeration info: an id
    App.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextEnumerationId()), idField)
    val idFieldValidator = SymbolValidator(getTextEnumerationId, true)((validator, event) => validateID(validator, event.getSource.asInstanceOf[Text].getText, event.doit))
    val idFieldListener = idField.addChangeListener { (id, event) =>
      val newId = id.trim
      setMessage(Messages.elementTemplateEditorDescription_text.format(newId))
      validateID(idFieldValidator, newId, true)
      getTextEnumerationName.setMessage(newId)
      updateOK()
    }
    idField.value = initial.id.name
    // bind the enumeration info: a name
    App.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextEnumerationName()), nameField)
    val nameFieldListener = nameField.addChangeListener { (name, event) => updateOK() }
    nameField.value = initial.name
    // bind the enumeration info: an availability
    App.bindingContext.bindValue(WidgetProperties.selection().observe(getBtnCheckAvailability()), availabilityField)
    val availabilityFieldListener = availabilityField.addChangeListener { (availability, event) => updateOK() }
    availabilityField.value = initial.availability
    // bind the enumeration info: a type
    App.bindingContext.bindValue(WidgetProperties.singleSelectionIndex().observeDelayed(50, getComboType.getCombo), typeField)
    val typeFieldListener = typeField.addChangeListener { (_, _) => updateOK() }
    getComboType.setContentProvider(ArrayContentProvider.getInstance())
    getComboType.setLabelProvider(new EnumerationEditor.TypeLabelProvider())
    getComboType.setInput(types)
    typeField.value = math.max(types.indexWhere(_ == payload.PropertyType.defaultType), 0)
    // complex content listener
    val actualConstantsListener = actualConstants.addChangeListener { event =>
      if (ActionAutoResize.isChecked())
        future { autoresize() } onFailure {
          case e: Exception => log.error(e.getMessage(), e)
          case e => log.error(e.toString())
        }
      updateOK()
    }
    // add dispose listener
    getShell().addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) {
        actualConstants.removeChangeListener(actualConstantsListener)
        availabilityField.removeChangeListener(availabilityFieldListener)
        nameField.removeChangeListener(nameFieldListener)
        idField.removeChangeListener(idFieldListener)
        typeField.removeChangeListener(typeFieldListener)
      }
    })
    // set dialog window title
    getShell().setText(Messages.elementTemplateEditorDialog_text.format(initial.id.name))
    validateID(idFieldValidator, idField.value, true)
    result
  }
  /** Get table content */
  protected def getInitialContent(enumeration: payload.api.Enumeration[_ <: AnyRef with java.io.Serializable]): List[EnumerationEditor.Item] =
    enumeration.constants.map(constant => EnumerationEditor.Item(constant.ptype.valueToString(constant.value), constant.alias, constant.description)).toList
  /** Allow external access for scala classes */
  override protected def getTableViewer() = super.getTableViewer
  /** Initialize table */
  protected def initTableEnumerations() {
    val viewer = getTableViewer()
    viewer.setContentProvider(new ObservableListContentProvider())
    getTableViewerColumnValue.setLabelProvider(new ColumnValue.TLabelProvider)
    getTableViewerColumnValue.setEditingSupport(new ColumnValue.TEditingSupport(viewer, this))
    getTableViewerColumnValue.getColumn.addSelectionListener(new EnumerationEditor.EnumerationSelectionAdapter(WeakReference(viewer), 0))
    getTableViewerColumnAlias.setLabelProvider(new ColumnAlias.TLabelProvider)
    getTableViewerColumnAlias.setEditingSupport(new ColumnAlias.TEditingSupport(viewer, this))
    getTableViewerColumnAlias.getColumn.addSelectionListener(new EnumerationEditor.EnumerationSelectionAdapter(WeakReference(viewer), 1))
    getTableViewerColumnDescription.setLabelProvider(new ColumnDescription.TLabelProvider)
    getTableViewerColumnDescription.setEditingSupport(new ColumnDescription.TEditingSupport(viewer, this))
    getTableViewerColumnDescription.getColumn.addSelectionListener(new EnumerationEditor.EnumerationSelectionAdapter(WeakReference(viewer), 2))
    viewer.getTable.setLinesVisible(true)
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
    // Add selection listener
    viewer.addSelectionChangedListener(new ISelectionChangedListener() {
      override def selectionChanged(event: SelectionChangedEvent) = event.getSelection() match {
        case selection: IStructuredSelection if !selection.isEmpty() =>
          ActionDelete.setEnabled(true)
        case selection =>
          ActionDelete.setEnabled(false)
      }
    })
    // Add the comparator
    viewer.setComparator(new EnumerationEditor.EnumerationComparator(new WeakReference(this)))
    viewer.setInput(actualConstants.underlying)
  }
  /** Return the stub for the new enumeration */
  protected def newEnumeration() = {
    val enumerationType = types(typeField.value)
    val iterator = enumerationType.createValues
    var newValue = DSLType.convertToString(enumerationType.typeSymbol, iterator.next)
    while (actualConstants.exists(i => Some(i.value) == newValue))
      newValue = DSLType.convertToString(enumerationType.typeSymbol, iterator.next)
    newValue match {
      case Some(value) =>
        EnumerationEditor.Item(value, "", "")
      case None =>
        log.fatal("unable to get new value from PropertyType for " + enumerationType.id)
        EnumerationEditor.Item("", "", "")
    }
  }
  /** On dialog active */
  override protected def onActive = {
    updateOK()
    future { autoresize() } onFailure {
      case e: Exception => log.error(e.getMessage(), e)
      case e => log.error(e.toString())
    }
  }
  /** Updates an actual constant */
  protected[enumed] def updateActualConstant(before: EnumerationEditor.Item, after: EnumerationEditor.Item) {
    val index = actualConstants.indexOf(before)
    actualConstants.update(index, after)
    if (index == actualConstants.size - 1)
      getTableViewer.refresh() // Workaround for the JFace bug. Force the last element modification.
    getTableViewer.setSelection(new StructuredSelection(after), true)
  }
  /**
   * Update an OK button
   * Disable button if there are duplicate names
   * Disable button if initialContent == actualContent
   */
  protected def updateOK() {
    val error = validate()
    setMessage(updateDescription(error))
    Option(getButton(IDialogConstants.OK_ID)).foreach(_.setEnabled(actualConstants.nonEmpty && error.isEmpty && {
      // new enumeration
      !enumerations.contains(initial) ||
        // changed
        !{
          initialConstants.sameElements(actualConstants) &&
            availabilityField.value == initial.availability &&
            nameField.value.trim == initial.name &&
            idField.value.trim == initial.id.name &&
            types(typeField.value) == initial.ptype
        }
    }))
  }
  /** Validate dialog for consistency */
  def validate(): Option[String] = {
    if (idField.value.trim.isEmpty())
      return Some(Messages.identificatorIsNotDefined_text)
    if (enumerations.exists(_.id.name == idField.value.trim) &&
      idField.value.trim != initial.id.name)
      return Some(Messages.identificatorIsAlreadyInUse_text.format(idField.value.trim))
    if (actualConstants.isEmpty)
      return Some(Messages.thereIsNoData_text)
    if (actualConstants.size != actualConstants.map(_.value.trim).distinct.size)
      return Some(Messages.thereAreDuplicatedValuesInField_text.format(Messages.value_text))
    if (actualConstants.size != (actualConstants.map(_.alias.trim).filter(_.nonEmpty).distinct.size + actualConstants.filter(_.alias.trim.isEmpty).size))
      return Some(Messages.thereAreDuplicatedValuesInField_text.format(Messages.alias_text))
    None
  }
  /** Validates a text in the the ID text field */
  def validateID(validator: Validator, text: String, valid: Boolean): Unit = if (!valid)
    validator.withDecoration(validator.showDecorationError(_))
  else if (text.isEmpty())
    validator.withDecoration(validator.showDecorationRequired(_))
  else
    validator.withDecoration(_.hide)

  object ActionAdd extends Action(Messages.add_text) {
    override def run = actualConstants += newEnumeration
  }
  object ActionAutoResize extends Action(Messages.autoresize_key, IAction.AS_CHECK_BOX) {
    setChecked(true)
    override def run = if (isChecked()) future { autoresize } onFailure {
      case e: Exception => log.error(e.getMessage(), e)
      case e => log.error(e.toString())
    }
  }
  object ActionDelete extends Action(Messages.delete_text) {
    override def run = getTableViewer.getSelection() match {
      case selection: IStructuredSelection if !selection.isEmpty() =>
        actualConstants -= selection.getFirstElement().asInstanceOf[EnumerationEditor.Item]
      case selection =>
    }
  }
}

object EnumerationEditor extends Loggable {
  class EnumerationComparator(dialog: WeakReference[EnumerationEditor]) extends ViewerComparator {
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
      val entity1 = e1.asInstanceOf[EnumerationEditor.Item]
      val entity2 = e2.asInstanceOf[EnumerationEditor.Item]
      val rc = column match {
        case 0 => entity1.value.compareTo(entity2.value)
        case 1 => entity1.alias.compareTo(entity2.alias)
        case 2 => entity1.description.compareTo(entity2.description)
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
  class EnumerationSelectionAdapter(tableViewer: WeakReference[TableViewer], column: Int) extends SelectionAdapter {
    override def widgetSelected(e: SelectionEvent) = {
      tableViewer.get.foreach(viewer => viewer.getComparator() match {
        case comparator: EnumerationComparator if comparator.column == column =>
          comparator.switchDirection()
          viewer.refresh()
        case comparator: EnumerationComparator =>
          comparator.column = column
          viewer.refresh()
        case _ =>
      })
    }
  }
  case class Item(val value: String, val alias: String, val description: String)
  class TypeLabelProvider extends LabelProvider {
    /** Returns the type name */
    override def getText(element: AnyRef): String = element match {
      case item: payload.api.PropertyType[_] =>
        item.name
      case unknown =>
        log.fatal("Unknown item " + unknown.getClass())
        ""
    }
  }
}
