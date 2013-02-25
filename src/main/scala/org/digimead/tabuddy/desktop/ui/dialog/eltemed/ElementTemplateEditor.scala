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

package org.digimead.tabuddy.desktop.ui.dialog.eltemed

import java.util.concurrent.locks.ReentrantLock

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future
import scala.ref.WeakReference

import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.tabuddy.desktop.Data
import org.digimead.tabuddy.desktop.Main
import org.digimead.tabuddy.desktop.payload.ElementTemplate
import org.digimead.tabuddy.desktop.payload.Enumeration
import org.digimead.tabuddy.desktop.payload.PropertyType
import org.digimead.tabuddy.desktop.payload.TemplateProperty
import org.digimead.tabuddy.desktop.payload.TemplatePropertyGroup
import org.digimead.tabuddy.desktop.payload.TypeSchema
import org.digimead.tabuddy.desktop.res.Messages
import org.digimead.tabuddy.desktop.support.WritableList
import org.digimead.tabuddy.desktop.support.WritableValue
import org.digimead.tabuddy.desktop.support.WritableValue.wrapper2underlying
import org.digimead.tabuddy.desktop.ui.dialog.Dialog
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
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Shell

class ElementTemplateEditor(val parentShell: Shell,
  /** The initial element template */
  val initial: ElementTemplate.Interface,
  /** The list of element template identifier */
  val templateList: Set[ElementTemplate.Interface])
  extends org.digimead.tabuddy.desktop.res.dialog.ElementTemplateEditor(parentShell) with Dialog with Loggable {
  /** The auto resize lock */
  protected val autoResizeLock = new ReentrantLock()
  /** The actual template properties */
  protected[eltemed] val actualProperties = WritableList(getTemplateProperties(initial).sortBy(_.id))
  /** The property representing current template availability */
  protected val availabilityField = WritableValue[java.lang.Boolean]
  /** The property representing current template description */
  protected val descriptionField = WritableValue[String]
  /** List of available enumerations */
  protected[eltemed] val enumerations: Array[Enumeration.Interface[_ <: AnyRef with java.io.Serializable]] =
    Data.getAvailableEnumerations.sortBy(_.id.name).toArray
  /** The property representing current template id */
  protected val idField = WritableValue[String]
  /** Used to create new propery */
  protected val newPropertySample = newProperty()
  /** The property representing a selected template property */
  protected val selected = WritableValue[ElementTemplateEditor.Item]
  /** List of available types */
  protected[eltemed] val types: Array[PropertyType[_ <: AnyRef with java.io.Serializable]] =
    Data.getAvailableTypes().sortBy(_.id.name).toArray
  assert(ElementTemplateEditor.dialog.isEmpty, "ElementTemplateEditor dialog is already active")

  /** Auto resize tableviewer columns */
  def autoresize() = if (autoResizeLock.tryLock()) try {
    Thread.sleep(50)
    Main.execNGet {
      if (!getTableViewerTemplateProperties.getTable.isDisposed()) {
        adjustColumnWidth(getTableViewerColumnId, Dialog.columnPadding)
        adjustColumnWidth(getTableViewerColumnRequired, Dialog.columnPadding)
        adjustColumnWidth(getTableViewerColumnType, Dialog.columnPadding)
        adjustColumnWidth(getTableViewerColumnDefault, Dialog.columnPadding)
        getTableViewerTemplateProperties.refresh()
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
    new ActionContributionItem(ElementTemplateEditor.ActionAdd).fill(getCompositeActions())
    new ActionContributionItem(ElementTemplateEditor.ActionDelete).fill(getCompositeActions())
    // Bind the template info: an id
    Main.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observe(getTextTemplateID()), idField)
    val idFieldListener = idField.addChangeListener { event =>
      setMessage(Messages.elementTemplateEditorDescription_text.format(idField.value))
      updateOK()
    }
    idField.value = initial.id.name
    // Bind the template info: a description
    Main.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observe(getTextTemplateDescription()), descriptionField)
    val descriptionFieldListener = descriptionField.addChangeListener { event => updateOK() }
    descriptionField.value = initial.description
    // Bind the template info: an availability
    Main.bindingContext.bindValue(WidgetProperties.selection().observe(getBtnCheckAvailability()), availabilityField)
    val availabilityFieldListener = availabilityField.addChangeListener { event => updateOK() }
    availabilityField.value = initial.availability
    // The complex content listener
    val actualPropertiesListener = actualProperties.addChangeListener { event =>
      if (ElementTemplateEditor.ActionAutoResize.isChecked())
        future { autoresize() }
    }
    // Add the dispose listener
    getShell().addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) {
        idField.removeChangeListener(idFieldListener)
        descriptionField.removeChangeListener(descriptionFieldListener)
        availabilityField.removeChangeListener(availabilityFieldListener)
        actualProperties.removeChangeListener(actualPropertiesListener)
        ElementTemplateEditor.dialog = None
      }
    })
    // Disable getTextTemplateID if the template is a predefined element
    getTextTemplateID().setEditable(!ElementTemplate.predefined.exists(_.id == initial.id))
    // Set the dialog window title
    getShell().setText(Messages.elementTemplateEditorDialog_text.format(initial.id))
    ElementTemplateEditor.dialog = Some(this)
    result
  }
  /** Get the template property list for the ElementTemplateEditor.Template item */
  def getTemplateProperties(template: ElementTemplate.Interface): List[ElementTemplateEditor.Item] = {
    val nested = template.properties.map {
      case (group, properties) =>
        properties.map(property =>
          ElementTemplateEditor.Item(property.id.name,
            property.required,
            property.enumeration.asInstanceOf[Option[Enumeration.Interface[AnyRef with java.io.Serializable]]],
            property.ptype.asInstanceOf[PropertyType[AnyRef with java.io.Serializable]],
            property.defaultValue.map(_.toString).getOrElse(""), group.id.name))
    }
    nested.flatten.toList.sortBy(_.id)
  }
  /** Initialize table */
  protected def initTableTemplateProperties() {
    val viewer = getTableViewerTemplateProperties()
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
        manager.add(ElementTemplateEditor.ActionAutoResize)
      }
    })
    menuMgr.setRemoveAllWhenShown(true)
    viewer.getControl.setMenu(menu)
    // Add the selection listener
    viewer.addSelectionChangedListener(new ISelectionChangedListener() {
      override def selectionChanged(event: SelectionChangedEvent) = event.getSelection() match {
        case selection: IStructuredSelection if !selection.isEmpty() =>
          ElementTemplateEditor.ActionDelete.setEnabled(true)
        case selection =>
          ElementTemplateEditor.ActionDelete.setEnabled(false)
      }
    })
    viewer.setComparator(new ElementTemplateEditor.TemplateComparator)
    viewer.setInput(actualProperties.underlying)
    Main.bindingContext.bindValue(ViewersObservables.observeSingleSelection(viewer), selected)
  }
  /** Return the stub for the new property */
  // TemplateProperty.defaultType contains value that exists for sure in DSLType.types
  protected def newProperty() =
    ElementTemplateEditor.Item("new", false, None, PropertyType.defaultType.asInstanceOf[PropertyType[AnyRef with java.io.Serializable]], "", TemplatePropertyGroup.default.id.name)
  /** Update an Add button */
  protected def updateAdd() = ElementTemplateEditor.ActionAdd.setEnabled(!actualProperties.contains(newPropertySample))
  /** On dialog active */
  override protected def onActive = {
    updateAdd()
    updateOK()
    autoresize()
  }
  /** Updates an actual constant */
  protected[eltemed] def updateActualProperty(before: ElementTemplateEditor.Item, after: ElementTemplateEditor.Item) {
    val index = actualProperties.indexOf(before)
    actualProperties.update(index, after)
    if (index == actualProperties.size - 1)
      getTableViewerTemplateProperties.refresh() // Workaround for the JFace bug. Force the last element modification.
    getTableViewerTemplateProperties.setSelection(new StructuredSelection(after), true)
  }
  /**
   * Update an OK button
   * Disable button if there are duplicate names
   * Disable button if initialContent == actualContent
   */
  protected def updateOK() =
    Option(getButton(IDialogConstants.OK_ID)).foreach(_.setEnabled(
      // and there are no duplicates
      actualProperties.size == actualProperties.map(_.id).distinct.size &&
        // newly created
        (false ||
          // actualContent changed
          //!actual.value.properties.sameElements(actualProperties.sortBy(_.id)) ||
          // id changed
          (idField.value.trim.nonEmpty && // prevent empty
            !ElementTemplate.container.eChildren.exists(_.eId.name == idField.value.trim) && // prevent exists
            idField.value != initial.id) ||
            // description changed
            descriptionField.value != initial.description ||
            // availability changed
            availabilityField.value != initial.availability)))
}

object ElementTemplateEditor extends Loggable {
  /** There is may be only one dialog instance at time */
  @volatile private var dialog: Option[ElementTemplateEditor] = None
  /** Default sort direction */
  private val defaultDirection = Dialog.ASCENDING
  /** Actual sortBy column index */
  @volatile private var sortColumn = 0 // by an id
  /** Actual sort direction */
  @volatile private var sortDirection = defaultDirection

  /** Apply a f(x) to the selected property if any */
  def property[T](f: (ElementTemplateEditor, Item) => T): Option[T] =
    dialog.flatMap(d => Option(d.selected.value).map(f(d, _)))

  case class Item(val id: String,
    val required: Boolean,
    val enumeration: Option[Enumeration.Interface[AnyRef with java.io.Serializable]],
    val ptype: PropertyType[AnyRef with java.io.Serializable],
    val default: String,
    val group: String)
  class TemplateComparator extends ViewerComparator {
    private var _column = ElementTemplateEditor.sortColumn
    private var _direction = ElementTemplateEditor.sortDirection

    /** Active column getter */
    def column = _column
    /** Active column setter */
    def column_=(arg: Int) {
      _column = arg
      ElementTemplateEditor.sortColumn = _column
      _direction = ElementTemplateEditor.defaultDirection
      ElementTemplateEditor.sortDirection = _direction
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
        case 3 => entity1.default.compareTo(entity2.default)
        case 4 => entity1.group.compareTo(entity2.group)
        case index =>
          log.fatal(s"unknown column with index $index"); 0
      }
      if (_direction == Dialog.DESCENDING) -rc else rc
    }
    /** Switch comparator direction */
    def switchDirection() {
      _direction = 1 - _direction
      ElementTemplateEditor.sortDirection = _direction
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
  /*
   * Actions
   */
  object ActionAutoResize extends Action(Messages.autoresize_key, IAction.AS_CHECK_BOX) {
    setChecked(true)
    override def run = if (isChecked()) ElementTemplateEditor.dialog.foreach(_.autoresize)
  }
  object ActionAdd extends Action(Messages.add_text) {
    override def run = dialog.foreach { dialog =>
      dialog.actualProperties += dialog.newProperty
    }
  }
  object ActionDelete extends Action(Messages.delete_text) {
    override def run = property { (dialog, selected) =>
      dialog.actualProperties -= selected
    }
  }
}
