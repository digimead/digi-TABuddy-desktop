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

package org.digimead.tabuddy.desktop.ui.dialog.view.sorted

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import java.util.regex.Pattern

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future
import scala.ref.WeakReference

import org.digimead.digi.lib.log.Loggable
import org.digimead.tabuddy.desktop.Data
import org.digimead.tabuddy.desktop.Main
import org.digimead.tabuddy.desktop.payload.PropertyType
import org.digimead.tabuddy.desktop.payload.TemplateProperty
import org.digimead.tabuddy.desktop.payload.view.Sorting
import org.digimead.tabuddy.desktop.payload.view.comparator.Comparator
import org.digimead.tabuddy.desktop.res.Messages
import org.digimead.tabuddy.desktop.res.dialog.view.CustomMessages
import org.digimead.tabuddy.desktop.support.Validator
import org.digimead.tabuddy.desktop.support.WritableList
import org.digimead.tabuddy.desktop.support.WritableList.wrapper2underlying
import org.digimead.tabuddy.desktop.support.WritableValue
import org.digimead.tabuddy.desktop.support.WritableValue.wrapper2underlying
import org.digimead.tabuddy.desktop.ui.BaseElement
import org.digimead.tabuddy.desktop.ui.Default
import org.digimead.tabuddy.desktop.ui.dialog.Dialog
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Model.model2implementation
import org.eclipse.core.databinding.observable.ChangeEvent
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
import org.eclipse.jface.viewers.ViewerFilter
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
import org.eclipse.swt.widgets.Text

class SortingEditor(val parentShell: Shell, val sorting: Sorting, val sortingList: List[Sorting])
  extends org.digimead.tabuddy.desktop.res.dialog.view.SortingEditor(parentShell) with Dialog with Loggable {
  /** The actual content */
  protected[sorted] val actual = WritableList(sorting.definitions.toList)
  /** The auto resize lock */
  protected val autoResizeLock = new ReentrantLock()
  /** The property representing current sorting availability */
  protected val availabilityField = WritableValue[java.lang.Boolean]
  /** The property representing current view description */
  protected val descriptionField = WritableValue[String]
  /** The property representing properties filter content */
  protected val filterProperties = WritableValue("")
  /** The property representing sorting filter content */
  protected val filterSortings = WritableValue("")
  /** The property representing current view name */
  protected val nameField = WritableValue[String]
  /** The property representing a selected property */
  protected val selectedProperty = WritableValue[SortingEditor.PropertyItem[_ <: AnyRef with java.io.Serializable]]
  /** The property representing a selected sorting */
  protected val selectedSorting = WritableValue[Sorting.Definition]
  /** All defined properties of the current model grouped by id, type  */
  protected[sorted] lazy val total: WritableList[SortingEditor.PropertyItem[_ <: AnyRef with java.io.Serializable]] = WritableList(Data.elementTemplates.values.
    flatMap { template => template.properties.flatMap(_._2) }.map(property => SortingEditor.PropertyItem(property.id, property.ptype,
      !actual.exists(definition => definition.property == property.id && definition.propertyType == property.ptype.id))).
    toList.distinct.sortBy(_.ptype.typeSymbol.name).sortBy(_.id.name))
  assert(SortingEditor.dialog.isEmpty, "SortingEditor dialog is already active")

  def getModifiedSorting(): Sorting = {
    val name = nameField.value.trim
    val description = descriptionField.value.trim
    Sorting(sorting.id, name, description, availabilityField.value, mutable.LinkedHashSet(actual: _*))
  }

  /** Auto resize tableviewer columns */
  protected def autoresize() = if (autoResizeLock.tryLock()) try {
    Thread.sleep(50)
    Main.execNGet {
      if (!getTableViewerSortings.getTable.isDisposed() && !getTableViewerProperties.getTable.isDisposed()) {
        adjustColumnWidth(getTableViewerColumnPropertyFrom(), Default.columnPadding)
        adjustColumnWidth(getTableViewerColumnN(), Default.columnPadding)
        adjustColumnWidth(getTableViewerColumnProperty(), Default.columnPadding)
        adjustColumnWidth(getTableViewerColumnType(), Default.columnPadding)
        adjustColumnWidth(getTableViewerColumnDirection(), Default.columnPadding)
        adjustColumnWidth(getTableViewerColumnSorting(), Default.columnPadding)
        getTableViewerProperties.refresh()
        getTableViewerSortings.refresh()
      }
    }
  } finally {
    autoResizeLock.unlock()
  }
  /** Create contents of the dialog. */
  override protected def createDialogArea(parent: Composite): Control = {
    val result = super.createDialogArea(parent)
    new ActionContributionItem(SortingEditor.ActionAdd).fill(getCompositeBody1())
    new ActionContributionItem(SortingEditor.ActionRemove).fill(getCompositeBody1())
    new ActionContributionItem(SortingEditor.ActionUp).fill(getCompositeBody2())
    new ActionContributionItem(SortingEditor.ActionDown).fill(getCompositeBody2())
    SortingEditor.ActionAdd.setEnabled(false)
    SortingEditor.ActionRemove.setEnabled(false)
    SortingEditor.ActionUp.setEnabled(false)
    SortingEditor.ActionDown.setEnabled(false)
    initTableViewerProperties()
    initTableViewerSortings()
    // bind the sorting info: an availability
    Main.bindingContext.bindValue(WidgetProperties.selection().observe(getBtnCheckAvailability()), availabilityField)
    val availabilityFieldListener = availabilityField.addChangeListener { (_, _) => updateOK() }
    availabilityField.value = sorting.availability
    // bind the sorting info: a description
    Main.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextDescription()), descriptionField)
    val descriptionFieldListener = descriptionField.addChangeListener { (_, _) => updateOK() }
    descriptionField.value = sorting.description
    // bind the sorting info: a name
    Main.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextName()), nameField)
    val nameFieldValidator = Validator(getTextName(), true)((validator, event) =>
      if (event.keyCode != 0) validateName(validator, event.getSource.asInstanceOf[Text].getText, event.doit))
    val nameFieldListener = nameField.addChangeListener { (name, event) =>
      validateName(nameFieldValidator, name.trim(), true)
      updateOK()
    }
    nameField.value = sorting.name
    val actualListener = actual.addChangeListener { event => updateOK() }
    // Add the dispose listener
    getShell().addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) {
        actual.removeChangeListener(actualListener)
        availabilityField.removeChangeListener(availabilityFieldListener)
        descriptionField.removeChangeListener(descriptionFieldListener)
        nameField.removeChangeListener(nameFieldListener)
        SortingEditor.dialog = None
      }
    })
    // Set the dialog message
    setMessage(CustomMessages.viewSortingEditorDescription_text.format(sorting.name))
    // Set the dialog window title
    getShell().setText(CustomMessages.viewSortingEditorDialog_text.format(sorting.name))
    SortingEditor.dialog = Some(this)
    result
  }
  /** Allow external access for scala classes */
  override protected def getTableViewerSortings() = super.getTableViewerSortings
  /** Allow external access for scala classes */
  override protected def getTableViewerProperties = super.getTableViewerProperties()
  /** Initialize  table viewer 'Properties' */
  protected def initTableViewerProperties() {
    val viewer = getTableViewerProperties()
    viewer.setContentProvider(new ObservableListContentProvider())
    getTableViewerColumnPropertyFrom.setLabelProvider(new ColumnPropertyFrom.TLabelProvider)
    getTableViewerColumnPropertyFrom.getColumn.addSelectionListener(new SortingEditor.PropertySelectionAdapter(0))
    getTableViewerColumnTypeFrom.setLabelProvider(new ColumnTypeFrom.TLabelProvider)
    getTableViewerColumnTypeFrom.getColumn.addSelectionListener(new SortingEditor.PropertySelectionAdapter(1))
    // Activate the tooltip support for the viewer
    ColumnViewerToolTipSupport.enableFor(viewer)
    // Add the context menu
    val menuMgr = new MenuManager()
    val menu = menuMgr.createContextMenu(viewer.getControl)
    menuMgr.addMenuListener(new IMenuListener() {
      override def menuAboutToShow(manager: IMenuManager) {
        manager.add(SortingEditor.ActionAutoResize)
      }
    })
    menuMgr.setRemoveAllWhenShown(true)
    viewer.getControl.setMenu(menu)
    // Add the selection listener
    viewer.addSelectionChangedListener(new ISelectionChangedListener() {
      override def selectionChanged(event: SelectionChangedEvent) = event.getSelection() match {
        case selection: IStructuredSelection if !selection.isEmpty() =>
          SortingEditor.ActionAdd.setEnabled(true)
        case selection =>
          SortingEditor.ActionAdd.setEnabled(false)
      }
    })
    // Add filters
    val visibleFilter = new ViewerFilter {
      override def select(viewer: Viewer, parentElement: AnyRef, element: AnyRef): Boolean =
        element.asInstanceOf[SortingEditor.PropertyItem[AnyRef with java.io.Serializable]].visible
    }
    Main.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextFilterProperties()), filterProperties)
    val filter = new AtomicReference(".*".r.pattern)
    val filterListener = new BaseElement.RegexFilterListener(filter) {
      override def handleChange(event: ChangeEvent) {
        super.handleChange(event)
        getTableViewerProperties.refresh()
      }
    }
    filterProperties.underlying.addChangeListener(filterListener)
    viewer.setFilters(Array(visibleFilter, new SortingEditor.PropertyFilter(filter)))
    // Set sorting
    viewer.setComparator(new SortingEditor.SortingComparator(0))
    viewer.setInput(total.underlying)
    Main.bindingContext.bindValue(ViewersObservables.observeSingleSelection(viewer), selectedProperty)
    viewer.getTable().pack
  }
  /** Initialize table viewer 'Sortings' */
  protected def initTableViewerSortings() {
    val viewer = getTableViewerSortings()
    viewer.setContentProvider(new ObservableListContentProvider())
    getTableViewerColumnN.setLabelProvider(new ColumnN.TLabelProvider(actual))
    getTableViewerColumnN.getColumn.addSelectionListener(new SortingEditor.SortingSelectionAdapter(0))
    getTableViewerColumnProperty.setLabelProvider(new ColumnProperty.TLabelProvider)
    getTableViewerColumnProperty.getColumn.addSelectionListener(new SortingEditor.SortingSelectionAdapter(1))
    getTableViewerColumnType.setLabelProvider(new ColumnType.TLabelProvider)
    getTableViewerColumnType.getColumn.addSelectionListener(new SortingEditor.SortingSelectionAdapter(2))
    getTableViewerColumnDirection.setLabelProvider(new ColumnDirection.TLabelProvider)
    getTableViewerColumnDirection.setEditingSupport(new ColumnDirection.TEditingSupport(viewer, this))
    getTableViewerColumnDirection.getColumn.addSelectionListener(new SortingEditor.SortingSelectionAdapter(3))
    getTableViewerColumnSorting.setLabelProvider(new ColumnSorting.TLabelProvider)
    getTableViewerColumnSorting.setEditingSupport(new ColumnSorting.TEditingSupport(viewer, this))
    getTableViewerColumnSorting.getColumn.addSelectionListener(new SortingEditor.SortingSelectionAdapter(4))
    getTableViewerColumnArgument.setLabelProvider(new ColumnArgument.TLabelProvider)
    getTableViewerColumnArgument.setEditingSupport(new ColumnArgument.TEditingSupport(viewer, this))
    getTableViewerColumnArgument.getColumn.addSelectionListener(new SortingEditor.SortingSelectionAdapter(5))
    // Activate the tooltip support for the viewer
    ColumnViewerToolTipSupport.enableFor(viewer)
    // Add the context menu
    val menuMgr = new MenuManager()
    val menu = menuMgr.createContextMenu(viewer.getControl)
    menuMgr.addMenuListener(new IMenuListener() {
      override def menuAboutToShow(manager: IMenuManager) {
        manager.add(SortingEditor.ActionAutoResize)
      }
    })
    menuMgr.setRemoveAllWhenShown(true)
    viewer.getControl.setMenu(menu)
    // Add the selection listener
    viewer.addSelectionChangedListener(new ISelectionChangedListener() {
      override def selectionChanged(event: SelectionChangedEvent) = event.getSelection() match {
        case selection: IStructuredSelection if !selection.isEmpty() =>
          val definition = selection.getFirstElement().asInstanceOf[Sorting.Definition]
          SortingEditor.ActionUp.setEnabled(actual.headOption != Some(definition))
          SortingEditor.ActionDown.setEnabled(actual.lastOption != Some(definition))
          SortingEditor.ActionRemove.setEnabled(true)
        case selection =>
          SortingEditor.ActionRemove.setEnabled(false)
          SortingEditor.ActionUp.setEnabled(false)
          SortingEditor.ActionDown.setEnabled(false)
      }
    })
    // Add the filter
    Main.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextFilterSortings()), filterSortings)
    val filter = new AtomicReference(".*".r.pattern)
    val filterListener = new BaseElement.RegexFilterListener(filter) {
      override def handleChange(event: ChangeEvent) {
        super.handleChange(event)
        getTableViewerSortings.refresh()
      }
    }
    filterSortings.underlying.addChangeListener(filterListener)
    viewer.setFilters(Array(new SortingEditor.SortingFilter(filter)))
    // Set sorting
    viewer.setComparator(new SortingEditor.SortingComparator(0))
    viewer.setInput(actual.underlying)
    Main.bindingContext.bindValue(ViewersObservables.observeSingleSelection(viewer), selectedSorting)
  }
  /** On dialog active */
  override protected def onActive = {
    updateOK()
    if (SortingEditor.ActionAutoResize.isChecked())
      future { autoresize() }
    // prevent interference with the size calculation
    getTextFilterProperties().setMessage(Messages.lookupFilter_text);
    getTextFilterSortings().setMessage(Messages.lookupFilter_text);
  }
  /** Updates an actual element template */
  protected[sorted] def updateActualDefinition(before: Sorting.Definition, after: Sorting.Definition) {
    val index = actual.indexOf(before)
    actual.update(index, after)
    if (index == actual.size - 1)
      getTableViewerSortings.refresh() // Workaround for the JFace bug. Force the last element modification.
    getTableViewerSortings.setSelection(new StructuredSelection(after), true)
    if (SortingEditor.ActionAutoResize.isChecked())
      future { autoresize() }
  }
  /** Update the dialog message */
  protected def updateDescription(error: Option[String]): String = {
    CustomMessages.viewSortingEditorDescription_text.format(nameField.value.trim) +
      (error match {
        case Some(error) => "\n    * - " + error
        case None => "\n "
      })
  }
  /** Update OK button state */
  protected def updateOK() = if (SortingEditor.dialog.nonEmpty) {
    val error = validate()
    setMessage(updateDescription(error))
    Option(getButton(IDialogConstants.OK_ID)).foreach(_.setEnabled(actual.nonEmpty && error.isEmpty && {
      // new sorting
      !sortingList.contains(sorting) ||
        // changed
        !{
          actual.sameElements(sorting.definitions.toList) &&
            availabilityField.value == sorting.availability &&
            nameField.value.trim == sorting.name &&
            descriptionField.value.trim == sorting.description
        }
    }))
  }
  /** Validate dialog for consistency */
  def validate(): Option[String] = {
    val name = nameField.value.trim
    if (name.isEmpty())
      return Some(Messages.nameIsNotDefined_text)
    if (sortingList.exists(_.name == name) && name != sorting.name)
      return Some(Messages.nameIsAlreadyInUse_text.format(name))
    if (actual.isEmpty)
      return Some(Messages.thereAreNoSelectedProperties_text)
    None
  }
  /** Validates a text in the the name text field */
  def validateName(validator: Validator, text: String, valid: Boolean): Unit =
    if (!valid)
      validator.withDecoration(validator.showDecorationError(_))
    else if (text.isEmpty())
      validator.withDecoration(validator.showDecorationRequired(_, Messages.nameIsNotDefined_text))
    else
      validator.withDecoration(_.hide)
}

object SortingEditor extends Loggable {
  /** There is may be only one dialog instance at time */
  @volatile private var dialog: Option[SortingEditor] = None

  /** Apply a f(x) to the selected property if any */
  def property[T](f: (SortingEditor, PropertyItem[_ <: AnyRef with java.io.Serializable]) => T): Option[T] =
    dialog.flatMap(d => Option(d.selectedProperty.value).map(f(d, _)))
  /** Apply a f(x) to the selected view if any */
  def sorting[T](f: (SortingEditor, Sorting.Definition) => T): Option[T] =
    dialog.flatMap(d => Option(d.selectedSorting.value).map(f(d, _)))

  class PropertyFilter(filter: AtomicReference[Pattern]) extends ViewerFilter {
    override def select(viewer: Viewer, parentElement: AnyRef, element: AnyRef): Boolean = {
      val pattern = filter.get
      val item = element.asInstanceOf[PropertyItem[AnyRef with java.io.Serializable]]
      pattern.matcher(item.id.name.toLowerCase()).matches() || pattern.matcher(item.ptype.name.toLowerCase()).matches()
    }
  }
  case class PropertyItem[T <: AnyRef with java.io.Serializable](val id: Symbol, ptype: PropertyType[T], var visible: Boolean)
  class PropertySelectionAdapter(column: Int) extends SelectionAdapter {
    override def widgetSelected(e: SelectionEvent) = dialog.foreach { dialog =>
      val viewer = dialog.getTableViewerProperties()
      val comparator = viewer.getComparator().asInstanceOf[SortingComparator]
      if (comparator.column == column) {
        comparator.switchDirection()
        viewer.refresh()
      } else {
        comparator.column = column
        viewer.refresh()
      }
    }
  }
  class SortingComparator(initialColumn: Int, initialDirection: Boolean = Default.sortingDirection) extends ViewerComparator {
    /** The sort column */
    var columnVar = initialColumn
    /** The sort direction */
    var directionVar = initialDirection

    /** Active column getter */
    def column = columnVar
    /** Active column setter */
    def column_=(arg: Int) {
      columnVar = arg
      directionVar = initialDirection
    }
    /** Sorting direction */
    def direction = directionVar
    /**
     * Returns a negative, zero, or positive number depending on whether
     * the first element is less than, equal to, or greater than
     * the second element.
     */
    override def compare(viewer: Viewer, e1: Object, e2: Object): Int = {
      e1 match {
        case entity1: Sorting.Definition =>
          val entity2 = e2.asInstanceOf[Sorting.Definition]
          val rc = column match {
            case 0 =>
              val input = viewer.getInput().asInstanceOf[org.eclipse.core.databinding.observable.list.WritableList]
              input.indexOf(entity1).compareTo(input.indexOf(entity2))
            case 1 => entity1.property.name.compareTo(entity2.property.name)
            case 2 => entity1.propertyType.name.compareTo(entity2.propertyType.name)
            case 3 => entity1.direction.compareTo(entity2.direction)
            case 4 => Comparator.map.get(entity1.comparator).map(_.name).getOrElse("").
              compareTo(Comparator.map.get(entity2.comparator).map(_.name).getOrElse(""))
            case 5 =>
              val argument1 = Comparator.map.get(entity1.comparator).flatMap(c => c.stringToArgument(entity1.argument).map(c.argumentToText)).getOrElse(entity1.argument)
              val argument2 = Comparator.map.get(entity2.comparator).flatMap(c => c.stringToArgument(entity2.argument).map(c.argumentToText)).getOrElse(entity2.argument)
              argument1.compareTo(argument2)
            case index =>
              log.fatal(s"unknown column with index $index"); 0
          }
          if (directionVar) -rc else rc
        case entity1: PropertyItem[_] =>
          val entity2 = e2.asInstanceOf[PropertyItem[_]]
          val rc = column match {
            case 0 => entity1.id.name.compareTo(entity2.id.name)
            case 1 => entity1.ptype.name.compareTo(entity2.ptype.name)
            case index =>
              log.fatal(s"unknown column with index $index"); 0
          }
          if (directionVar) -rc else rc
      }
    }
    /** Switch comparator direction */
    def switchDirection() {
      directionVar = !directionVar
    }
  }
  class SortingFilter(filter: AtomicReference[Pattern]) extends ViewerFilter {
    override def select(viewer: Viewer, parentElement: AnyRef, element: AnyRef): Boolean = {
      val pattern = filter.get
      val item = element.asInstanceOf[Sorting.Definition]
      pattern.matcher(item.property.name.toLowerCase()).matches() ||
        pattern.matcher(PropertyType.get(item.propertyType).name.toLowerCase()).matches() ||
        pattern.matcher(Comparator.map(item.comparator).name.toLowerCase()).matches() ||
        pattern.matcher(Comparator.map.get(item.comparator).flatMap(c =>
          c.stringToArgument(item.argument).map(c.argumentToText)).getOrElse(item.argument).toLowerCase()).matches()
    }
  }
  class SortingSelectionAdapter(column: Int) extends SelectionAdapter {
    override def widgetSelected(e: SelectionEvent) = dialog.foreach { dialog =>
      val viewer = dialog.getTableViewerSortings()
      val comparator = viewer.getComparator().asInstanceOf[SortingComparator]
      if (comparator.column == column) {
        comparator.switchDirection()
        viewer.refresh()
      } else {
        comparator.column = column
        viewer.refresh()
      }
    }
  }
  /*
   * Actions
   */
  object ActionAutoResize extends Action(Messages.autoresize_key, IAction.AS_CHECK_BOX) {
    setChecked(true)
    override def run = if (isChecked()) SortingEditor.dialog.foreach(dialog => future { dialog.autoresize })
  }
  object ActionAdd extends Action(">") with Loggable {
    override def run = SortingEditor.property { (dialog, property) =>
      property.visible = false
      dialog.actual += Sorting.Definition(property.id, property.ptype.id, Default.sortingDirection, Comparator.default.id, "")
      if (SortingEditor.ActionAutoResize.isChecked())
        future { dialog.autoresize() }
      else
        dialog.getTableViewerProperties.refresh()
    }
  }
  object ActionRemove extends Action("<") with Loggable {
    override def run = SortingEditor.sorting { (dialog, definition) =>
      dialog.actual -= definition
      dialog.total.find(property => definition.property == property.id && definition.propertyType == property.ptype.id).foreach(_.visible = true)
      if (SortingEditor.ActionAutoResize.isChecked())
        future { dialog.autoresize() }
      else
        dialog.getTableViewerProperties.refresh()
    }
  }
  object ActionUp extends Action(Messages.up_text) with Loggable {
    override def run = SortingEditor.sorting { (dialog, definition) =>
      val index = dialog.actual.indexOf(definition)
      if (index > -1 && 0 <= (index - 1)) {
        dialog.actual.update(index, dialog.actual(index - 1))
        dialog.actual.update(index - 1, definition)
        dialog.getTableViewerSortings.refresh()
        dialog.getTableViewerSortings.setSelection(new StructuredSelection(definition), true)
      }
    }
  }
  object ActionDown extends Action(Messages.down_text) with Loggable {
    override def run = SortingEditor.sorting { (dialog, definition) =>
      val index = dialog.actual.indexOf(definition)
      if (index > -1 && dialog.actual.size > (index + 1)) {
        dialog.actual.update(index, dialog.actual(index + 1))
        dialog.actual.update(index + 1, definition)
        dialog.getTableViewerSortings.refresh()
        dialog.getTableViewerSortings.setSelection(new StructuredSelection(definition), true)
      }
    }
  }
}
