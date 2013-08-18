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

package org.digimead.tabuddy.desktop.viewmod.dialog.filtered

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import java.util.regex.Pattern

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future
import scala.ref.WeakReference

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Messages
import org.digimead.tabuddy.desktop.definition.Dialog
import org.digimead.tabuddy.desktop.logic.Data
import org.digimead.tabuddy.desktop.logic.payload
import org.digimead.tabuddy.desktop.logic.payload.TemplateProperty
import org.digimead.tabuddy.desktop.logic.payload.view
import org.digimead.tabuddy.desktop.logic.payload.view.Filter
import org.digimead.tabuddy.desktop.logic.payload.view.AvailableFilters
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.Validator
import org.digimead.tabuddy.desktop.support.WritableList
import org.digimead.tabuddy.desktop.support.WritableList.wrapper2underlying
import org.digimead.tabuddy.desktop.support.WritableValue
import org.digimead.tabuddy.desktop.support.WritableValue.wrapper2underlying
import org.digimead.tabuddy.desktop.support.ui.RegexFilterListener
import org.digimead.tabuddy.desktop.viewmod.Default
import org.digimead.tabuddy.desktop.viewmod.dialog.CustomMessages
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

class FilterEditor(val parentShell: Shell, val filter: view.Filter, val filterList: List[view.Filter])
  extends FilterEditorSkel(parentShell) with Dialog with Loggable {
  /** The actual content */
  protected[filtered] val actual = WritableList(filter.rules.toList)
  /** The auto resize lock */
  protected val autoResizeLock = new ReentrantLock()
  /** The property representing current sorting availability */
  protected val availabilityField = WritableValue[java.lang.Boolean]
  /** The property representing current view description */
  protected val descriptionField = WritableValue[String]
  /** The property representing properties filter content */
  protected val filterProperties = WritableValue("")
  /** The property representing filters filter content */
  protected val filterFilters = WritableValue("")
  /** The property representing current view name */
  protected val nameField = WritableValue[String]
  /** The property representing a selected property */
  protected val selectedProperty = WritableValue[FilterEditor.PropertyItem[_ <: AnyRef with java.io.Serializable]]
  /** The property representing a selected sorting */
  protected val selectedRule = WritableValue[view.api.Filter.Rule]
  /** All defined properties of the current model grouped by id, type  */
  protected[filtered] lazy val total: WritableList[FilterEditor.PropertyItem[_ <: AnyRef with java.io.Serializable]] = WritableList(Data.elementTemplates.values.
    flatMap { template => template.properties.flatMap(_._2) }.map(property => FilterEditor.PropertyItem(property.id, property.ptype,
      !actual.exists(definition => definition.property == property.id && definition.propertyType == property.ptype.id))).
    toList.distinct.sortBy(_.ptype.typeSymbol.name).sortBy(_.id.name))
  assert(FilterEditor.dialog.isEmpty, "FilterEditor dialog is already active")

  def getModifiedFilter(): view.Filter = {
    val name = nameField.value.trim
    val description = descriptionField.value.trim
    view.Filter(filter.id, name, description, availabilityField.value, mutable.LinkedHashSet(actual: _*))
  }

  /** Auto resize tableviewer columns */
  protected def autoresize() = if (autoResizeLock.tryLock()) try {
    Thread.sleep(50)
    App.execNGet {
      if (!getTableViewerFilters.getTable.isDisposed() && !getTableViewerProperties.getTable.isDisposed()) {
        App.adjustTableViewerColumnWidth(getTableViewerColumnPropertyFrom(), Default.columnPadding)
        App.adjustTableViewerColumnWidth(getTableViewerColumnProperty(), Default.columnPadding)
        App.adjustTableViewerColumnWidth(getTableViewerColumnType(), Default.columnPadding)
        App.adjustTableViewerColumnWidth(getTableViewerColumnInversion(), Default.columnPadding)
        App.adjustTableViewerColumnWidth(getTableViewerColumnFilter(), Default.columnPadding)
        getTableViewerProperties.refresh()
        getTableViewerFilters.refresh()
      }
    }
  } finally {
    autoResizeLock.unlock()
  }
  /** Builds the dialog message */
  protected def updateDescription(error: Option[String]): String = {
    CustomMessages.viewFilterEditorDescription_text.format(nameField.value.trim) +
      (error match {
        case Some(error) => "\n    * - " + error
        case None => "\n "
      })
  }
  /** Create contents of the dialog. */
  override protected def createDialogArea(parent: Composite): Control = {
    val result = super.createDialogArea(parent)
    new ActionContributionItem(ActionAdd).fill(getCompositeBody())
    new ActionContributionItem(ActionRemove).fill(getCompositeBody())
    ActionAdd.setEnabled(false)
    ActionRemove.setEnabled(false)
    initTableViewerProperties()
    initTableViewerSortings()
    // bind the sorting info: an availability
    App.bindingContext.bindValue(WidgetProperties.selection().observe(getBtnCheckAvailability()), availabilityField)
    val availabilityFieldListener = availabilityField.addChangeListener { (availability, event) => updateOK() }
    availabilityField.value = filter.availability
    // bind the sorting info: a description
    App.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextDescription()), descriptionField)
    val descriptionFieldListener = descriptionField.addChangeListener { (description, event) => updateOK() }
    descriptionField.value = filter.description
    // bind the sorting info: a name
    App.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextName()), nameField)
    val nameFieldValidator = Validator(getTextName(), true)((validator, event) =>
      if (event.keyCode != 0) validateName(validator, event.getSource.asInstanceOf[Text].getText, event.doit))
    val nameFieldListener = nameField.addChangeListener { (name, event) =>
      validateName(nameFieldValidator, name.trim(), true)
      updateOK()
    }
    nameField.value = filter.name
    val actualListener = actual.addChangeListener { event => updateOK() }
    // Add the dispose listener
    getShell().addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) {
        actual.removeChangeListener(actualListener)
        availabilityField.removeChangeListener(availabilityFieldListener)
        descriptionField.removeChangeListener(descriptionFieldListener)
        nameField.removeChangeListener(nameFieldListener)
        FilterEditor.dialog = None
      }
    })
    // Set the dialog message
    setMessage(CustomMessages.viewFilterEditorDescription_text.format(filter.name))
    // Set the dialog window title
    getShell().setText(CustomMessages.viewFilterEditorDialog_text.format(filter.name))
    FilterEditor.dialog = Some(this)
    result
  }
  /** Allow external access for scala classes */
  override protected def getTableViewerFilters() = super.getTableViewerFilters()
  /** Allow external access for scala classes */
  override protected def getTableViewerProperties = super.getTableViewerProperties()
  /** Initialize tableTableViewerSortings */
  protected def initTableViewerProperties() {
    val viewer = getTableViewerProperties()
    viewer.setContentProvider(new ObservableListContentProvider())
    getTableViewerColumnPropertyFrom.setLabelProvider(new ColumnPropertyFrom.TLabelProvider)
    getTableViewerColumnPropertyFrom.getColumn.addSelectionListener(new FilterEditor.PropertySelectionAdapter(0))
    getTableViewerColumnTypeFrom.setLabelProvider(new ColumnTypeFrom.TLabelProvider)
    getTableViewerColumnTypeFrom.getColumn.addSelectionListener(new FilterEditor.PropertySelectionAdapter(1))
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
        element.asInstanceOf[FilterEditor.PropertyItem[AnyRef with java.io.Serializable]].visible
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
    viewer.setFilters(Array(visibleFilter, new FilterEditor.PropertyFilter(filter)))
    // Set sorting
    viewer.setComparator(new FilterEditor.FilterComparator(0))
    viewer.setInput(total.underlying)
    App.bindingContext.bindValue(ViewersObservables.observeSingleSelection(viewer), selectedProperty)
    viewer.getTable().pack
  }
  /** Initialize tableTableViewerSortings */
  protected def initTableViewerSortings() {
    val viewer = getTableViewerFilters()
    viewer.setContentProvider(new ObservableListContentProvider())
    getTableViewerColumnProperty.setLabelProvider(new ColumnProperty.TLabelProvider)
    getTableViewerColumnProperty.getColumn.addSelectionListener(new FilterEditor.FilterSelectionAdapter(0))
    getTableViewerColumnType.setLabelProvider(new ColumnType.TLabelProvider)
    getTableViewerColumnType.getColumn.addSelectionListener(new FilterEditor.FilterSelectionAdapter(1))
    getTableViewerColumnInversion.setLabelProvider(new ColumnInversion.TLabelProvider)
    getTableViewerColumnInversion.setEditingSupport(new ColumnInversion.TEditingSupport(viewer, this))
    getTableViewerColumnInversion.getColumn.addSelectionListener(new FilterEditor.FilterSelectionAdapter(2))
    getTableViewerColumnFilter.setLabelProvider(new ColumnSorting.TLabelProvider)
    getTableViewerColumnFilter.setEditingSupport(new ColumnSorting.TEditingSupport(viewer, this))
    getTableViewerColumnFilter.getColumn.addSelectionListener(new FilterEditor.FilterSelectionAdapter(3))
    getTableViewerColumnArgument.setLabelProvider(new ColumnArgument.TLabelProvider)
    getTableViewerColumnArgument.setEditingSupport(new ColumnArgument.TEditingSupport(viewer, this))
    getTableViewerColumnArgument.getColumn.addSelectionListener(new FilterEditor.FilterSelectionAdapter(4))
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
          ActionRemove.setEnabled(true)
        case selection =>
          ActionRemove.setEnabled(false)
      }
    })
    // Add the filter
    App.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextFilterFilters()), filterFilters)
    val filter = new AtomicReference(".*".r.pattern)
    val filterListener = new RegexFilterListener(filter) {
      override def handleChange(event: ChangeEvent) {
        super.handleChange(event)
        getTableViewerFilters.refresh()
      }
    }
    filterFilters.underlying.addChangeListener(filterListener)
    viewer.setFilters(Array(new FilterEditor.Filter(filter)))
    // Set sorting
    viewer.setComparator(new FilterEditor.FilterComparator(0))
    viewer.setInput(actual.underlying)
    App.bindingContext.bindValue(ViewersObservables.observeSingleSelection(viewer), selectedRule)
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
    getTextFilterFilters().setMessage(Messages.lookupFilter_text);
  }
  /** Updates an actual element template */
  protected[filtered] def updateActualRule(before: view.api.Filter.Rule, after: view.api.Filter.Rule) {
    val index = actual.indexOf(before)
    actual.update(index, after)
    if (index == actual.size - 1)
      getTableViewerFilters.refresh() // Workaround for the JFace bug. Force the last element modification.
    getTableViewerFilters.setSelection(new StructuredSelection(after), true)
    if (ActionAutoResize.isChecked())
      future { autoresize() } onFailure {
        case e: Exception => log.error(e.getMessage(), e)
        case e => log.error(e.toString())
      }
  }
  /** Update OK button state */
  protected def updateOK() = if (FilterEditor.dialog.nonEmpty) {
    val error = validate()
    setMessage(updateDescription(error))
    Option(getButton(IDialogConstants.OK_ID)).foreach(_.setEnabled(actual.nonEmpty && error.isEmpty && {
      // new filter
      !filterList.contains(filter) ||
        // changed
        !{
          actual.sameElements(filter.rules.toList) &&
            availabilityField.value == filter.availability &&
            nameField.value.trim == filter.name &&
            descriptionField.value.trim == filter.description
        }
    }))
  }
  /** Validate dialog for consistency */
  def validate(): Option[String] = {
    val name = nameField.value.trim
    if (name.isEmpty())
      return Some(Messages.nameIsNotDefined_text)
    if (filterList.exists(_.name == name) && name != filter.name)
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
    override def run = if (isChecked()) FilterEditor.dialog.foreach(dialog => future { dialog.autoresize })
  }
  object ActionAdd extends Action(">") with Loggable {
    override def run = FilterEditor.property { (dialog, property) =>
      property.visible = false
      dialog.actual += view.api.Filter.Rule(property.id, property.ptype.id, false, Filter.allowAllFilter.id, "")
      if (ActionAutoResize.isChecked())
        future { dialog.autoresize() } onFailure {
          case e: Exception => log.error(e.getMessage(), e)
          case e => log.error(e.toString())
        }
      else
        dialog.getTableViewerProperties.refresh()
    }
  }
  object ActionRemove extends Action("<") with Loggable {
    override def run = FilterEditor.rule { (dialog, rule) =>
      dialog.actual -= rule
      dialog.total.find(property => rule.property == property.id && rule.propertyType == property.ptype.id).foreach(_.visible = true)
      if (ActionAutoResize.isChecked())
        future { dialog.autoresize() } onFailure {
          case e: Exception => log.error(e.getMessage(), e)
          case e => log.error(e.toString())
        }
      else
        dialog.getTableViewerProperties.refresh()
    }
  }
}

object FilterEditor extends Loggable {
  /** There is may be only one dialog instance at time */
  @volatile private var dialog: Option[FilterEditor] = None

  /** Apply a f(x) to the selected view if any */
  def property[T](f: (FilterEditor, PropertyItem[_ <: AnyRef with java.io.Serializable]) => T): Option[T] =
    dialog.flatMap(d => Option(d.selectedProperty.value).map(f(d, _)))
  /** Apply a f(x) to the selected view if any */
  def rule[T](f: (FilterEditor, view.api.Filter.Rule) => T): Option[T] =
    dialog.flatMap(d => Option(d.selectedRule.value).map(f(d, _)))

  class PropertyFilter(filter: AtomicReference[Pattern]) extends ViewerFilter {
    override def select(viewer: Viewer, parentElement: AnyRef, element: AnyRef): Boolean = {
      val pattern = filter.get
      val item = element.asInstanceOf[PropertyItem[AnyRef with java.io.Serializable]]
      pattern.matcher(item.id.name.toLowerCase()).matches() || pattern.matcher(item.ptype.name.toLowerCase()).matches()
    }
  }
  case class PropertyItem[T <: AnyRef with java.io.Serializable](val id: Symbol, ptype: payload.api.PropertyType[T], var visible: Boolean)
  class PropertySelectionAdapter(column: Int) extends SelectionAdapter {
    override def widgetSelected(e: SelectionEvent) = dialog.foreach { dialog =>
      val viewer = dialog.getTableViewerProperties()
      val comparator = viewer.getComparator().asInstanceOf[FilterComparator]
      if (comparator.column == column) {
        comparator.switchDirection()
        viewer.refresh()
      } else {
        comparator.column = column
        viewer.refresh()
      }
    }
  }

  class FilterComparator(initialColumn: Int, initialDirection: Boolean = Default.sortingDirection) extends ViewerComparator {
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
        case entity1: view.api.Filter.Rule =>
          val entity2 = e2.asInstanceOf[view.api.Filter.Rule]
          val rc = column match {
            case 0 => entity1.property.name.compareTo(entity2.property.name)
            case 1 => entity1.propertyType.name.compareTo(entity2.propertyType.name)
            case 2 => entity1.not.compareTo(entity2.not)
            case 3 => AvailableFilters.map.get(entity1.filter).map(_.name).getOrElse("").
              compareTo(AvailableFilters.map.get(entity2.filter).map(_.name).getOrElse(""))
            case 4 =>
              val argument1 = AvailableFilters.map.get(entity1.filter).flatMap(filter =>
                filter.stringToArgument(entity1.argument).map(filter.generic.argumentToText)).getOrElse(entity1.argument)
              val argument2 = AvailableFilters.map.get(entity2.filter).flatMap(filter =>
                filter.stringToArgument(entity2.argument).map(filter.generic.argumentToText)).getOrElse(entity2.argument)
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
  class Filter(filter: AtomicReference[Pattern]) extends ViewerFilter {
    override def select(viewer: Viewer, parentElement: AnyRef, element: AnyRef): Boolean = {
      val pattern = filter.get
      val item = element.asInstanceOf[view.api.Filter.Rule]
      pattern.matcher(item.property.name.toLowerCase()).matches() ||
        pattern.matcher(payload.PropertyType.get(item.propertyType).name.toLowerCase()).matches() ||
        pattern.matcher(AvailableFilters.map(item.filter).name.toLowerCase()).matches() ||
        pattern.matcher(AvailableFilters.map.get(item.filter).flatMap(filter =>
          filter.stringToArgument(item.argument).map(filter.generic.argumentToText)).getOrElse(item.argument).toLowerCase()).matches()
    }
  }
  class FilterSelectionAdapter(column: Int) extends SelectionAdapter {
    override def widgetSelected(e: SelectionEvent) = dialog.foreach { dialog =>
      val viewer = dialog.getTableViewerFilters()
      val comparator = viewer.getComparator().asInstanceOf[FilterComparator]
      if (comparator.column == column) {
        comparator.switchDirection()
        viewer.refresh()
      } else {
        comparator.column = column
        viewer.refresh()
      }
    }
  }
}
