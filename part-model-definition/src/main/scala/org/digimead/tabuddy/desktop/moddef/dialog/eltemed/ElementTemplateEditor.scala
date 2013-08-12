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

package org.digimead.tabuddy.desktop.moddef.dialog.eltemed

import java.util.concurrent.locks.ReentrantLock

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future
import scala.ref.WeakReference
import scala.collection.immutable

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Messages
import org.digimead.tabuddy.desktop.Resources
import org.digimead.tabuddy.desktop.definition.Dialog
import org.digimead.tabuddy.desktop.logic.Data
import org.digimead.tabuddy.desktop.logic.payload
import org.digimead.tabuddy.desktop.logic.payload.Payload
import org.digimead.tabuddy.desktop.logic.payload.TypeSchema
import org.digimead.tabuddy.desktop.moddef.Default
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.WritableList
import org.digimead.tabuddy.desktop.support.WritableValue
import org.digimead.tabuddy.desktop.support.WritableValue.wrapper2underlying
import org.digimead.tabuddy.model.dsl.DSLType
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
import org.eclipse.swt.SWT
import org.eclipse.swt.events.DisposeEvent
import org.eclipse.swt.events.DisposeListener
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Shell

class ElementTemplateEditor(val parentShell: Shell,
  /** The initial element template */
  val initial: payload.api.ElementTemplate,
  /** The list of element template identifier */
  val templateList: Set[payload.api.ElementTemplate])
  extends ElementTemplateEditorSkel(parentShell) with Dialog with Loggable {
  /** The actual template properties */
  protected[eltemed] val actualProperties = WritableList(initialProperties)
  /** The auto resize lock */
  protected val autoResizeLock = new ReentrantLock()
  /** The property representing current template availability */
  protected val availabilityField = WritableValue[java.lang.Boolean]
  /** The property representing current template label */
  protected val nameField = WritableValue[String]
  /** List of available enumerations */
  protected[eltemed] val enumerations: Array[payload.api.Enumeration[_ <: AnyRef with java.io.Serializable]] =
    Data.getAvailableEnumerations.sortBy(_.id.name).toArray
  /** The property representing current template id */
  protected val idField = WritableValue[String]
  /** The initial template properties */
  protected lazy val initialProperties = getTemplateProperties(initial).sortBy(_.id)
  /** Used to create new propery */
  protected val newPropertySample = newProperty()
  /** The property that indicates whether the template is predefined */
  protected val predefined = payload.ElementTemplate.original.find(_.id == initial.id)
  /** The property that contains predefined properties if any */
  protected val predefinedProperties = predefined.map(getTemplateProperties(_).sortBy(_.id))
  /** The property representing a selected template property */
  protected val selected = WritableValue[ElementTemplateEditor.Item]
  /** List of available types */
  protected[eltemed] val types: Array[payload.api.PropertyType[_ <: AnyRef with java.io.Serializable]] =
    Data.getAvailableTypes().sortBy(_.id.name).toArray
  /** Actual sortBy column index */
  @volatile protected var sortColumn = 0 // by an id
  /** Actual sort direction */
  @volatile protected var sortDirection = Default.sortingDirection

  /** Get the modified type schema */
  def getModifiedTemplate(): payload.api.ElementTemplate = {
    val id = idField.value.trim
    val name = nameField.value.trim
    initial.copy(
      availability = availabilityField.value,
      name = if (name.isEmpty()) id else name,
      id = Symbol(id),
      properties = getTemplateProperties(actualProperties.toList))
  }

  /** Auto resize tableviewer columns */
  protected def autoresize() = if (autoResizeLock.tryLock()) try {
    Thread.sleep(50)
    App.execNGet {
      if (!getTableViewer.getTable.isDisposed()) {
        App.adjustTableViewerColumnWidth(getTableViewerColumnId, Default.columnPadding)
        App.adjustTableViewerColumnWidth(getTableViewerColumnRequired, Default.columnPadding)
        App.adjustTableViewerColumnWidth(getTableViewerColumnType, Default.columnPadding)
        App.adjustTableViewerColumnWidth(getTableViewerColumnDefault, Default.columnPadding)
        getTableViewer.refresh()
      }
    }
  } finally {
    autoResizeLock.unlock()
  }
  /** Create contents of the dialog. */
  override protected def createDialogArea(parent: Composite): Control = {
    // Create dialog elements
    val result = super.createDialogArea(parent)
    initTableTemplateProperties
    new ActionContributionItem(ActionAdd).fill(getCompositeFooter())
    new ActionContributionItem(ActionDelete).fill(getCompositeFooter())
    // Add reset action for predefined template
    if (predefined.nonEmpty) {
      new Composite(getCompositeFooter(), SWT.NONE) {
        override def computeSize(wHint: Int, hHint: Int, changed: Boolean) = new Point(10, 1)
        // Disable the check that prevents subclassing of SWT components
        override protected def checkSubclass() {}
      }
      new ActionContributionItem(ActionReset).fill(getCompositeFooter())
    }
    ActionAdd.setEnabled(true)
    ActionDelete.setEnabled(false)
    ActionReset.setEnabled(false)
    // Bind the template info: an id
    App.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observe(getTextTemplateId()), idField)
    val idFieldListener = idField.addChangeListener { (id, event) =>
      val newId = id.trim
      setMessage(Messages.elementTemplateEditorDescription_text.format(newId))
      getTextTemplateName.setMessage(newId)
      updateButtons()
    }
    idField.value = initial.id.name
    // Bind the template info: a name
    App.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observe(getTextTemplateName()), nameField)
    val nameFieldListener = nameField.addChangeListener { (name, event) => updateButtons() }
    nameField.value = initial.name
    // Bind the template info: an availability
    App.bindingContext.bindValue(WidgetProperties.selection().observe(getBtnCheckAvailability()), availabilityField)
    val availabilityFieldListener = availabilityField.addChangeListener { (availability, event) => updateButtons() }
    availabilityField.value = initial.availability
    // The complex content listener
    val actualPropertiesListener = actualProperties.addChangeListener { event =>
      if (ActionAutoResize.isChecked())
        future { autoresize() } onFailure {
          case e: Exception => log.error(e.getMessage(), e)
          case e => log.error(e.toString())
        }
      updateButtons()
    }
    // Add the dispose listener
    getShell().addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) {
        idField.removeChangeListener(idFieldListener)
        nameField.removeChangeListener(nameFieldListener)
        availabilityField.removeChangeListener(availabilityFieldListener)
        actualProperties.removeChangeListener(actualPropertiesListener)
      }
    })
    // Disable getTextTemplateID if the template is a predefined element
    getTextTemplateId().setEditable(predefined.isEmpty)
    // Set the dialog window title
    getShell().setText(Messages.elementTemplateEditorDialog_text.format(initial.id))
    result
  }
  /** Convert ElementTemplate.Interface.property map to ElementTemplateEditor.Item list */
  protected def getTemplateProperties(template: payload.api.ElementTemplate): List[ElementTemplateEditor.Item] = {
    val nested = template.properties.map {
      case (group, properties) =>
        properties.map(property =>
          ElementTemplateEditor.Item(property.id.name, None, // id column
            property.required, None, // required column
            property.enumeration.flatMap(Data.enumerations.get).asInstanceOf[Option[payload.api.Enumeration[AnyRef with java.io.Serializable]]],
            property.ptype.asInstanceOf[payload.api.PropertyType[AnyRef with java.io.Serializable]], None, // type column
            property.defaultValue, None, // default column
            group.id.name, None)) // group column
    }
    nested.flatten.toList
  }
  /** Convert ElementTemplate.Interface.property map to ElementTemplateEditor.Item list */
  protected def getTemplateProperties(items: List[ElementTemplateEditor.Item]): payload.api.ElementTemplate.propertyMap = {
    var properties = immutable.HashMap[payload.api.TemplatePropertyGroup, Seq[payload.api.TemplateProperty[_ <: AnyRef with java.io.Serializable]]]()
    actualProperties.foreach { item =>
      val group = payload.TemplatePropertyGroup.default // TODO
      if (!properties.isDefinedAt(group))
        properties = properties.updated(group, Seq())
      properties = properties.updated(group, properties(group) :+
        new payload.TemplateProperty(Symbol(item.id.trim), item.required, item.enumeration.map(_.id), item.ptype,
          item.default)(Manifest.classType(item.ptype.typeClass)))
    }
    properties
  }
  /** Initialize table */
  protected def initTableTemplateProperties() {
    val viewer = getTableViewer()
    val table = viewer.getTable()
    viewer.setContentProvider(new ObservableListContentProvider())
    getTableViewerColumnId.setLabelProvider(new ColumnId.TLabelProvider)
    getTableViewerColumnId.setEditingSupport(new ColumnId.TEditingSupport(viewer, this))
    getTableViewerColumnId.getColumn.addSelectionListener(new ElementTemplateEditor.TemplateSelectionAdapter(WeakReference(viewer), 0))
    getTableViewerColumnRequired.setLabelProvider(new ColumnRequired.TLabelProvider)
    getTableViewerColumnRequired.setEditingSupport(new ColumnRequired.TEditingSupport(viewer, this))
    getTableViewerColumnRequired.getColumn.addSelectionListener(new ElementTemplateEditor.TemplateSelectionAdapter(WeakReference(viewer), 1))
    getTableViewerColumnType.setLabelProvider(new ColumnType.TLabelProvider)
    getTableViewerColumnType.setEditingSupport(new ColumnType.TEditingSupport(viewer, this))
    getTableViewerColumnType.getColumn.addSelectionListener(new ElementTemplateEditor.TemplateSelectionAdapter(WeakReference(viewer), 2))
    getTableViewerColumnDefault.setLabelProvider(new ColumnDefault.TLabelProvider)
    getTableViewerColumnDefault.setEditingSupport(new ColumnDefault.TEditingSupport(viewer, this))
    getTableViewerColumnDefault.getColumn.addSelectionListener(new ElementTemplateEditor.TemplateSelectionAdapter(WeakReference(viewer), 3))
    getTableViewerColumnGroup.setLabelProvider(new ColumnGroup.TLabelProvider)
    getTableViewerColumnGroup.setEditingSupport(new ColumnGroup.TEditingSupport(viewer, this))
    getTableViewerColumnGroup.getColumn.addSelectionListener(new ElementTemplateEditor.TemplateSelectionAdapter(WeakReference(viewer), 4))
    getTableViewerColumnRequired.getColumn().setAlignment(SWT.CENTER)
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
    // Add the selection listener
    viewer.addSelectionChangedListener(new ISelectionChangedListener() {
      override def selectionChanged(event: SelectionChangedEvent) = event.getSelection() match {
        case selection: IStructuredSelection if !selection.isEmpty() =>
          ActionDelete.setEnabled(true)
        case selection =>
          ActionDelete.setEnabled(false)
      }
    })
    viewer.setComparator(new ElementTemplateEditor.TemplateComparator(new WeakReference(this)))
    viewer.setInput(actualProperties.underlying)
    App.bindingContext.bindValue(ViewersObservables.observeSingleSelection(viewer), selected)
  }
  /** Return the stub for the new property */
  protected def newProperty() = {
    val newPropertyID = Payload.generateNew("property", "", newId => actualProperties.exists(_.id == newId))
    ElementTemplateEditor.Item(newPropertyID, None, // id column
      false, None, // required column
      None, payload.PropertyType.defaultType.asInstanceOf[payload.api.PropertyType[AnyRef with java.io.Serializable]], None, // type column
      None, None, // default column
      payload.TemplatePropertyGroup.default.id.name, None) // group column
  }
  /** On dialog active */
  override protected def onActive = {
    updateButtons()
    autoresize()
  }
  /** Updates an actual constant */
  protected[eltemed] def updateActualProperty(before: ElementTemplateEditor.Item, after: ElementTemplateEditor.Item) {
    val index = actualProperties.indexOf(before)
    actualProperties.update(index, after)
    if (index == actualProperties.size - 1)
      getTableViewer.refresh() // Workaround for the JFace bug. Force the last element modification.
    getTableViewer.setSelection(new StructuredSelection(after), true)
  }
  /**
   * Update OK button
   * Disable button if there are duplicate names
   * Disable button if initialContent == actualContent
   */
  protected def updateButtons() = {
    val newId = idField.value.trim
    // update ActionReset
    for {
      predefined <- predefined if (nameField.value != null)
      predefinedProperties <- predefinedProperties
    } ActionReset.setEnabled(predefined.name != nameField.value.trim || predefinedProperties != actualProperties)
    // update Ok
    Option(getButton(IDialogConstants.OK_ID)).foreach(_.setEnabled(
      // prevent the empty id
      newId.nonEmpty &&
        // and there are no duplicate ids
        actualProperties.size == actualProperties.map(_.id).distinct.size &&
        // and there are no errors
        !actualProperties.exists(item =>
          item.idError.nonEmpty ||
            item.requiredError.nonEmpty ||
            item.typeError.nonEmpty ||
            item.defaultError.nonEmpty ||
            item.groupError.nonEmpty) &&
        // the id not exists or template modified
        (!templateList.exists(_.id.name == newId) ||
          (newId == initial.id.name && !{
            initialProperties.sameElements(actualProperties.sortBy(_.id)) &&
              availabilityField.value == initial.availability &&
              nameField.value.trim == initial.name
          }))))
  }
  /** Validate ElementTemplateEditor item */
  protected[eltemed] def validateItem(item: ElementTemplateEditor.Item): ElementTemplateEditor.Item = {
    var idError: Option[(String, Image)] = None
    var requiredError: Option[(String, Image)] = None
    var typeError: Option[(String, Image)] = None
    var defaultError: Option[(String, Image)] = None
    var groupError: Option[(String, Image)] = None
    item.enumeration.foreach { enumeration =>
      item.default match {
        case Some(default) =>
          if (!enumeration.constants.exists(_.value == default))
            defaultError = Some(("The incorrect value", Resources.Image.error))
        case None =>
          defaultError = Some(("The value is required", Resources.Image.required))
      }
    }
    item.default.foreach { default =>
      if (default.getClass != item.ptype.typeClass)
        if (defaultError.nonEmpty)
          defaultError = Some(("Incorrect the default value", Resources.Image.error))
    }
    item.copy(idError = idError, requiredError = requiredError, typeError = typeError, defaultError = defaultError, groupError = groupError)
  }

  object ActionAutoResize extends Action(Messages.autoresize_key, IAction.AS_CHECK_BOX) {
    setChecked(true)
    override def run = if (isChecked()) autoresize
  }
  object ActionAdd extends Action(Messages.add_text) {
    override def run = actualProperties += newProperty
  }
  object ActionDelete extends Action(Messages.delete_text) {
    override def run = Option(selected.value) foreach { (selected) => actualProperties -= selected }
  }
  object ActionReset extends Action(Messages.reset_text) {
    setToolTipText(Messages.resetPredefinedTemplate_text)
    override def run = {
      for {
        predefined <- predefined
        predefinedProperties <- predefinedProperties
      } {
        nameField.value = predefined.name
        actualProperties.clear
        actualProperties ++= predefinedProperties
      }
    }
  }
}

object ElementTemplateEditor extends Loggable {
  case class Item(val id: String,
    val idError: Option[(String, Image)],
    val required: Boolean,
    val requiredError: Option[(String, Image)],
    val enumeration: Option[payload.api.Enumeration[AnyRef with java.io.Serializable]],
    val ptype: payload.api.PropertyType[AnyRef with java.io.Serializable],
    val typeError: Option[(String, Image)],
    val default: Option[AnyRef with java.io.Serializable],
    val defaultError: Option[(String, Image)],
    val group: String,
    val groupError: Option[(String, Image)])
  class TemplateComparator(dialog: WeakReference[ElementTemplateEditor]) extends ViewerComparator {
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
      val entity1 = e1.asInstanceOf[ElementTemplateEditor.Item]
      val entity2 = e2.asInstanceOf[ElementTemplateEditor.Item]
      val rc = column match {
        case 0 => entity1.id.compareTo(entity2.id)
        case 1 => entity1.required.compareTo(entity2.required)
        case 2 => entity1.ptype.id.name.compareTo(entity2.ptype.id.name)
        case 3 => (entity1.default, entity2.default) match {
          case (Some(value1), Some(value2)) => entity1.ptype.valueToString(value1).compareTo(entity2.ptype.valueToString(value2))
          case (Some(value1), None) => 1
          case (None, Some(value2)) => -1
          case _ => 0
        }
        case 4 => entity1.group.compareTo(entity2.group)
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
  class TemplateSelectionAdapter(tableViewer: WeakReference[TableViewer], column: Int) extends SelectionAdapter {
    override def widgetSelected(e: SelectionEvent) = {
      tableViewer.get.foreach(viewer => viewer.getComparator() match {
        case comparator: TemplateComparator if comparator.column == column =>
          comparator.switchDirection()
          viewer.refresh()
        case comparator: TemplateComparator =>
          comparator.column = column
          viewer.refresh()
        case _ =>
      })
    }
  }
}
