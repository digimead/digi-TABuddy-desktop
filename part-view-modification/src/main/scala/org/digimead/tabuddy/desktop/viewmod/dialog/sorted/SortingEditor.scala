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

package org.digimead.tabuddy.desktop.viewmod.dialog.sorted

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import java.util.regex.Pattern
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future
import scala.ref.WeakReference
import org.digimead.tabuddy.desktop.logic.payload
import org.digimead.tabuddy.desktop.logic.payload.TemplateProperty
import org.digimead.tabuddy.desktop.logic.payload.view
import org.digimead.tabuddy.desktop.support.Validator
import org.digimead.tabuddy.desktop.support.WritableList
import org.digimead.tabuddy.desktop.support.WritableList.wrapper2underlying
import org.digimead.tabuddy.desktop.support.WritableValue
import org.digimead.tabuddy.desktop.support.WritableValue.wrapper2underlying
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
import org.digimead.tabuddy.desktop.definition.Dialog
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.logic.Data
import org.digimead.tabuddy.desktop.viewmod.Default
import org.digimead.tabuddy.desktop.viewmod.dialog.CustomMessages
import org.digimead.tabuddy.desktop.support.ui.RegexFilterListener
import org.digimead.tabuddy.desktop.Messages
import org.digimead.tabuddy.desktop.logic.payload.view.AvailableComparators

class SortingEditor(val parentShell: Shell, val sorting: view.Sorting, val sortingList: List[view.Sorting])
  extends SortingEditorSkel(parentShell) with Dialog with Loggable {
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
  protected val selectedSorting = WritableValue[view.Sorting.Definition]
  /** All defined properties of the current model grouped by id, type  */
  protected[sorted] lazy val total: WritableList[SortingEditor.PropertyItem[_ <: AnyRef with java.io.Serializable]] = WritableList(Data.elementTemplates.values.
    flatMap { template => template.properties.flatMap(_._2) }.map(property => SortingEditor.PropertyItem(property.id, property.ptype,
      !actual.exists(definition => definition.property == property.id && definition.propertyType == property.ptype.id))).
    toList.distinct.sortBy(_.ptype.typeSymbol.name).sortBy(_.id.name))

  def getModifiedSorting(): view.Sorting = {
    val name = nameField.value.trim
    val description = descriptionField.value.trim
    view.Sorting(sorting.id, name, description, availabilityField.value, mutable.LinkedHashSet(actual: _*))
  }

  /** Auto resize tableviewer columns */
  protected def autoresize() = if (autoResizeLock.tryLock()) try {
    Thread.sleep(50)
    App.execNGet {
      if (!getTableViewerSortings.getTable.isDisposed() && !getTableViewerProperties.getTable.isDisposed()) {
        App.adjustTableViewerColumnWidth(getTableViewerColumnPropertyFrom(), Default.columnPadding)
        App.adjustTableViewerColumnWidth(getTableViewerColumnN(), Default.columnPadding)
        App.adjustTableViewerColumnWidth(getTableViewerColumnProperty(), Default.columnPadding)
        App.adjustTableViewerColumnWidth(getTableViewerColumnType(), Default.columnPadding)
        App.adjustTableViewerColumnWidth(getTableViewerColumnDirection(), Default.columnPadding)
        App.adjustTableViewerColumnWidth(getTableViewerColumnSorting(), Default.columnPadding)
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
    new ActionContributionItem(ActionAdd).fill(getCompositeBody1())
    new ActionContributionItem(ActionRemove).fill(getCompositeBody1())
    new ActionContributionItem(ActionUp).fill(getCompositeBody2())
    new ActionContributionItem(ActionDown).fill(getCompositeBody2())
    ActionAdd.setEnabled(false)
    ActionRemove.setEnabled(false)
    ActionUp.setEnabled(false)
    ActionDown.setEnabled(false)
    initTableViewerProperties()
    initTableViewerSortings()
    // bind the sorting info: an availability
    App.bindingContext.bindValue(WidgetProperties.selection().observe(getBtnCheckAvailability()), availabilityField)
    val availabilityFieldListener = availabilityField.addChangeListener { (_, _) => updateOK() }
    availabilityField.value = sorting.availability
    // bind the sorting info: a description
    App.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextDescription()), descriptionField)
    val descriptionFieldListener = descriptionField.addChangeListener { (_, _) => updateOK() }
    descriptionField.value = sorting.description
    // bind the sorting info: a name
    App.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextName()), nameField)
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
      }
    })
    // Set the dialog message
    setMessage(CustomMessages.viewSortingEditorDescription_text.format(sorting.name))
    // Set the dialog window title
    getShell().setText(CustomMessages.viewSortingEditorDialog_text.format(sorting.name))
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
        manager.add(ActionAutoResize)
      }
    })
    menuMgr.setRemoveAllWhenShown(true)
    viewer.getControl.setMenu(menu)
    // Add the selection listener
    viewer.addSelectionChangedListener(new ISelectionChangedListener() {
      override def selectionChanged(event: SelectionChangedEvent) = event.getSelection() match {
        case selection: IStructuredSelection if !selection.isEmpty() =>
          ActionAdd.setEnabled(true)
        case selection =>
          ActionAdd.setEnabled(false)
      }
    })
    // Add filters
    val visibleFilter = new ViewerFilter {
      override def select(viewer: Viewer, parentElement: AnyRef, element: AnyRef): Boolean =
        element.asInstanceOf[SortingEditor.PropertyItem[AnyRef with java.io.Serializable]].visible
    }
    App.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextFilterProperties()), filterProperties)
    val filter = new AtomicReference(".*".r.pattern)
    val filterListener = new RegexFilterListener(filter) {
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
    App.bindingContext.bindValue(ViewersObservables.observeSingleSelection(viewer), selectedProperty)
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
        manager.add(ActionAutoResize)
      }
    })
    menuMgr.setRemoveAllWhenShown(true)
    viewer.getControl.setMenu(menu)
    // Add the selection listener
    viewer.addSelectionChangedListener(new ISelectionChangedListener() {
      override def selectionChanged(event: SelectionChangedEvent) = event.getSelection() match {
        case selection: IStructuredSelection if !selection.isEmpty() =>
          val definition = selection.getFirstElement().asInstanceOf[view.Sorting.Definition]
          ActionUp.setEnabled(actual.headOption != Some(definition))
          ActionDown.setEnabled(actual.lastOption != Some(definition))
          ActionRemove.setEnabled(true)
        case selection =>
          ActionRemove.setEnabled(false)
          ActionUp.setEnabled(false)
          ActionDown.setEnabled(false)
      }
    })
    // Add the filter
    App.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextFilterSortings()), filterSortings)
    val filter = new AtomicReference(".*".r.pattern)
    val filterListener = new RegexFilterListener(filter) {
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
    App.bindingContext.bindValue(ViewersObservables.observeSingleSelection(viewer), selectedSorting)
  }
  /** On dialog active */
  override protected def onActive = {
    updateOK()
    if (ActionAutoResize.isChecked())
      future { autoresize() } onFailure {
        case e: Exception => log.error(e.getMessage(), e)
        case e => log.error(e.toString())
      }
    // prevent interference with the size calculation
    getTextFilterProperties().setMessage(Messages.lookupFilter_text);
    getTextFilterSortings().setMessage(Messages.lookupFilter_text);
  }
  /** Updates an actual element template */
  protected[sorted] def updateActualDefinition(before: view.Sorting.Definition, after: view.Sorting.Definition) {
    val index = actual.indexOf(before)
    actual.update(index, after)
    if (index == actual.size - 1)
      getTableViewerSortings.refresh() // Workaround for the JFace bug. Force the last element modification.
    getTableViewerSortings.setSelection(new StructuredSelection(after), true)
    if (ActionAutoResize.isChecked())
      future { autoresize() } onFailure {
        case e: Exception => log.error(e.getMessage(), e)
        case e => log.error(e.toString())
      }
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
  protected def updateOK() = {
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
  object ActionAutoResize extends Action(Messages.autoresize_key, IAction.AS_CHECK_BOX) {
    setChecked(true)
    override def run = if (isChecked()) {
      future { autoresize } onFailure {
        case e: Exception => log.error(e.getMessage(), e)
        case e => log.error(e.toString())
      }
    }
  }
  object ActionAdd extends Action(">") with Loggable {
    override def run = {} /*SortingEditor.property { (dialog, property) =>
      property.visible = false
      dialog.actual += Sorting.Definition(property.id, property.ptype.id, Default.sortingDirection, Comparator.default.id, "")
      if (SortingEditor.ActionAutoResize.isChecked())
        future { dialog.autoresize() } onFailure {
          case e: Exception => log.error(e.getMessage(), e)
          case e => log.error(e.toString())
        }
      else
        dialog.getTableViewerProperties.refresh()
    }*/
  }
  object ActionRemove extends Action("<") with Loggable {
    override def run = {} /*SortingEditor.sorting { (dialog, definition) =>
      dialog.actual -= definition
      dialog.total.find(property => definition.property == property.id && definition.propertyType == property.ptype.id).foreach(_.visible = true)
      if (SortingEditor.ActionAutoResize.isChecked())
        future { dialog.autoresize() } onFailure {
          case e: Exception => log.error(e.getMessage(), e)
          case e => log.error(e.toString())
        }
      else
        dialog.getTableViewerProperties.refresh()
    }*/
  }
  object ActionUp extends Action(Messages.up_text) with Loggable {
    override def run = {} /*SortingEditor.sorting { (dialog, definition) =>
      val index = dialog.actual.indexOf(definition)
      if (index > -1 && 0 <= (index - 1)) {
        dialog.actual.update(index, dialog.actual(index - 1))
        dialog.actual.update(index - 1, definition)
        dialog.getTableViewerSortings.refresh()
        dialog.getTableViewerSortings.setSelection(new StructuredSelection(definition), true)
      }
    }*/
  }
  object ActionDown extends Action(Messages.down_text) with Loggable {
    override def run = {} /*SortingEditor.sorting { (dialog, definition) =>
      val index = dialog.actual.indexOf(definition)
      if (index > -1 && dialog.actual.size > (index + 1)) {
        dialog.actual.update(index, dialog.actual(index + 1))
        dialog.actual.update(index + 1, definition)
        dialog.getTableViewerSortings.refresh()
        dialog.getTableViewerSortings.setSelection(new StructuredSelection(definition), true)
      }
    }*/
  }
}

object SortingEditor extends Loggable {
  /** Apply a f(x) to the selected property if any */
  //  def property[T](f: (SortingEditor, PropertyItem[_ <: AnyRef with java.io.Serializable]) => T): Option[T] =
  //    dialog.flatMap(d => Option(d.selectedProperty.value).map(f(d, _)))
  /** Apply a f(x) to the selected view if any */
  //  def sorting[T](f: (SortingEditor, view.Sorting.Definition) => T): Option[T] =
  //    dialog.flatMap(d => Option(d.selectedSorting.value).map(f(d, _)))

  class PropertyFilter(filter: AtomicReference[Pattern]) extends ViewerFilter {
    override def select(viewer: Viewer, parentElement: AnyRef, element: AnyRef): Boolean = {
      val pattern = filter.get
      val item = element.asInstanceOf[PropertyItem[AnyRef with java.io.Serializable]]
      pattern.matcher(item.id.name.toLowerCase()).matches() || pattern.matcher(item.ptype.name.toLowerCase()).matches()
    }
  }
  case class PropertyItem[T <: AnyRef with java.io.Serializable](val id: Symbol, ptype: payload.api.PropertyType[T], var visible: Boolean)
  class PropertySelectionAdapter(column: Int) extends SelectionAdapter {
    /*    override def widgetSelected(e: SelectionEvent) = dialog.foreach { dialog =>
      val viewer = dialog.getTableViewerProperties()
      val comparator = viewer.getComparator().asInstanceOf[SortingComparator]
      if (comparator.column == column) {
        comparator.switchDirection()
        viewer.refresh()
      } else {
        comparator.column = column
        viewer.refresh()
      }
    }*/
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
        case entity1: view.Sorting.Definition =>
          val entity2 = e2.asInstanceOf[view.Sorting.Definition]
          val rc = column match {
            case 0 =>
              val input = viewer.getInput().asInstanceOf[org.eclipse.core.databinding.observable.list.WritableList]
              input.indexOf(entity1).compareTo(input.indexOf(entity2))
            case 1 => entity1.property.name.compareTo(entity2.property.name)
            case 2 => entity1.propertyType.name.compareTo(entity2.propertyType.name)
            case 3 => entity1.direction.compareTo(entity2.direction)
            case 4 => AvailableComparators.map.get(entity1.comparator).map(_.name).getOrElse("").
              compareTo(AvailableComparators.map.get(entity2.comparator).map(_.name).getOrElse(""))
            case 5 =>
              //              val argument1 = AvailableComparators.map.get(entity1.comparator).flatMap(c => c.stringToArgument(entity1.argument).map(c.argumentToText)).getOrElse(entity1.argument)
              //              val argument2 = AvailableComparators.map.get(entity2.comparator).flatMap(c => c.stringToArgument(entity2.argument).map(c.argumentToText)).getOrElse(entity2.argument)
              //              argument1.compareTo(argument2)
              0
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
      val item = element.asInstanceOf[view.Sorting.Definition]
      pattern.matcher(item.property.name.toLowerCase()).matches() ||
        pattern.matcher(payload.PropertyType.get(item.propertyType).name.toLowerCase()).matches() ||
        pattern.matcher(AvailableComparators.map(item.comparator).name.toLowerCase()).matches() ||
        pattern.matcher(AvailableComparators.map.get(item.comparator).
          flatMap(_.stringToText(item.argument)).getOrElse(item.argument).toLowerCase()).matches()
    }
  }
  class SortingSelectionAdapter(column: Int) extends SelectionAdapter {
    /*    override def widgetSelected(e: SelectionEvent) = dialog.foreach { dialog =>
      val viewer = dialog.getTableViewerSortings()
      val comparator = viewer.getComparator().asInstanceOf[SortingComparator]
      if (comparator.column == column) {
        comparator.switchDirection()
        viewer.refresh()
      } else {
        comparator.column = column
        viewer.refresh()
      }
    }*/
  }
}