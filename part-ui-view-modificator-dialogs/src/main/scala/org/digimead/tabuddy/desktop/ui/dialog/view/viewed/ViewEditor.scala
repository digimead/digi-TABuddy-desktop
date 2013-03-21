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

package org.digimead.tabuddy.desktop.ui.dialog.view.viewed

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import java.util.regex.Pattern

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future

import org.digimead.digi.lib.log.Loggable
import org.digimead.tabuddy.desktop.Data
import org.digimead.tabuddy.desktop.Main
import org.digimead.tabuddy.desktop.payload.view.Filter
import org.digimead.tabuddy.desktop.payload.view.Sorting
import org.digimead.tabuddy.desktop.payload.view.View
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
import org.eclipse.core.databinding.observable.ChangeEvent
import org.eclipse.jface.action.Action
import org.eclipse.jface.action.ActionContributionItem
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

class ViewEditor(val parentShell: Shell, val view: View, val viewList: List[View])
  extends org.digimead.tabuddy.desktop.res.dialog.view.ViewEditor(parentShell) with Dialog with Loggable {
  /** The actual fields */
  protected val actualFields = WritableList(view.fields.toList)
  /** The actual filters UUID */
  protected val actualFilters = WritableList(view.filters.toList)
  /** The actual sortings UUID */
  protected val actualSortings = WritableList(view.sortings.toList)
  /** All available filters */
  protected val allFilters = WritableList((Data.getAvailableViewFilters - Filter.default).toList.sortBy(_.name))
  /** All defined properties of the current model grouped by id */
  protected val allProperties: WritableList[Symbol] = WritableList(Data.elementTemplates.values.
    flatMap { template => template.properties.flatMap(_._2) }.map(property => property.id).toList.distinct.sortBy(_.name))
  /** All available sortings */
  protected val allSortings = WritableList((Data.getAvailableViewSortings - Sorting.default).toList.sortBy(_.name))
  /** The auto resize lock */
  protected val autoResizeLock = new ReentrantLock()
  /** The property representing current enumeration availability */
  protected val availabilityField = WritableValue[java.lang.Boolean]
  /** The property representing current view description */
  protected val descriptionField = WritableValue[String]
  /** The property representing fields filter content */
  protected val filterFields = WritableValue("")
  /** The property representing filters filter content */
  protected val filterFilters = WritableValue("")
  /** The property representing properties filter content */
  protected val filterProperties = WritableValue("")
  /** The property representing sortings filter content */
  protected val filterSortings = WritableValue("")
  /** The property representing current view name */
  protected val nameField = WritableValue[String]
  /** The property representing a selected field */
  protected val selectedField = WritableValue[Symbol]
  /** The property representing a selected property */
  protected val selectedProperty = WritableValue[Symbol]
  assert(ViewEditor.dialog.isEmpty, "ViewEditor dialog is already active")

  def getModifiedViews(): View = {
    val name = nameField.value.trim
    val description = descriptionField.value.trim
    View(view.id, name, description, availabilityField.value, actualFields.toSet, actualFilters.toSet, actualSortings.toSet)
  }

  /** Auto resize tableviewer columns */
  protected def autoresize() = if (autoResizeLock.tryLock()) try {
    Thread.sleep(50)
    Main.execNGet {
      if (!getTableViewerFields.getTable.isDisposed()) {
        adjustColumnWidth(getTableViewerColumnN(), Default.columnPadding)
        getTableViewerFields.refresh()
      }
    }
  } finally {
    autoResizeLock.unlock()
  }
  /** Create contents of the dialog. */
  override protected def createDialogArea(parent: Composite): Control = {
    val result = super.createDialogArea(parent)
    new ActionContributionItem(ViewEditor.ActionAdd).fill(getCompositeBody1())
    new ActionContributionItem(ViewEditor.ActionRemove).fill(getCompositeBody1())
    new ActionContributionItem(ViewEditor.ActionUp).fill(getCompositeBody2())
    new ActionContributionItem(ViewEditor.ActionDown).fill(getCompositeBody2())
    ViewEditor.ActionAdd.setEnabled(false)
    ViewEditor.ActionRemove.setEnabled(false)
    ViewEditor.ActionUp.setEnabled(false)
    ViewEditor.ActionDown.setEnabled(false)
    initTableViewerFields()
    initTableViewerFilters()
    initTableViewerProperties()
    initTableViewerSortings()
    // bind the view info: an availability
    Main.bindingContext.bindValue(WidgetProperties.selection().observe(getBtnCheckAvailability()), availabilityField)
    val availabilityFieldListener = availabilityField.addChangeListener { event => updateOK() }
    availabilityField.value = view.availability
    // bind the view info: a description
    Main.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextDescription()), descriptionField)
    val descriptionFieldListener = descriptionField.addChangeListener { event => updateOK() }
    descriptionField.value = view.description
    // bind the view info: a name
    Main.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextName()), nameField)
    val nameFieldValidator = Validator(getTextName(), true)((validator, event) =>
      if (event.keyCode != 0) validateName(validator, event.getSource.asInstanceOf[Text].getText, event.doit))
    val nameFieldListener = nameField.addChangeListener { event =>
      validateName(nameFieldValidator, nameField.value.trim(), true)
      updateOK()
    }
    nameField.value = view.name
    val actualFieldsListener = actualFields.addChangeListener { event => updateOK() }
    val actualFiltersListener = actualFilters.addChangeListener { event => updateOK() }
    val actualSortingListener = actualSortings.addChangeListener { event => updateOK() }
    // Add the dispose listener
    getShell().addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) {
        actualFields.removeChangeListener(actualFieldsListener)
        actualFilters.removeChangeListener(actualFiltersListener)
        actualSortings.removeChangeListener(actualSortingListener)
        availabilityField.removeChangeListener(availabilityFieldListener)
        descriptionField.removeChangeListener(descriptionFieldListener)
        nameField.removeChangeListener(nameFieldListener)
        ViewEditor.dialog = None
      }
    })
    // Set the dialog message
    setMessage(CustomMessages.viewEditorDescription_text.format(view.name))
    // Set the dialog window title
    getShell().setText(CustomMessages.viewEditorDialog_text.format(view.name))
    ViewEditor.dialog = Some(this)
    result
  }
  /** Allow external access for scala classes */
  override protected def getTableViewerFields() = super.getTableViewerFields
  /** Allow external access for scala classes */
  override protected def getTableViewerProperties = super.getTableViewerProperties
  /** Initialize table viewer 'Fields' */
  protected def initTableViewerFields() {
    val viewer = getTableViewerFields()
    viewer.setContentProvider(new ObservableListContentProvider())
    getTableViewerColumnN.setLabelProvider(new ColumnN.TLabelProvider(actualFields))
    getTableViewerColumnN.getColumn.addSelectionListener(new ViewEditor.ViewSelectionAdapter(0, viewer))
    getTableViewerColumnField.setLabelProvider(new ColumnField.TLabelProvider)
    getTableViewerColumnField.getColumn.addSelectionListener(new ViewEditor.ViewSelectionAdapter(1, viewer))
    // Activate the tooltip support for the viewer
    ColumnViewerToolTipSupport.enableFor(viewer)
    // Add the selection listener
    viewer.addSelectionChangedListener(new ISelectionChangedListener() {
      override def selectionChanged(event: SelectionChangedEvent) = event.getSelection() match {
        case selection: IStructuredSelection if !selection.isEmpty() =>
          val field = selection.getFirstElement().asInstanceOf[Symbol]
          ViewEditor.ActionUp.setEnabled(actualFields.headOption != Some(field))
          ViewEditor.ActionDown.setEnabled(actualFields.lastOption != Some(field))
          ViewEditor.ActionRemove.setEnabled(true)
        case selection =>
          ViewEditor.ActionRemove.setEnabled(false)
          ViewEditor.ActionUp.setEnabled(false)
          ViewEditor.ActionDown.setEnabled(false)
      }
    })
    // Add the filter
    Main.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextFieldFilter()), filterFields)
    val filter = new AtomicReference(".*".r.pattern)
    val filterListener = new BaseElement.RegexFilterListener(filter) {
      override def handleChange(event: ChangeEvent) {
        super.handleChange(event)
        getTableViewerFields.refresh()
      }
    }
    filterFields.underlying.addChangeListener(filterListener)
    viewer.setFilters(Array(new ViewEditor.SymbolFilter(filter)))
    // Set sorting
    viewer.setComparator(new ViewEditor.ViewComparator(0, Default.sortingDirection, true))
    viewer.setInput(actualFields.underlying)
    Main.bindingContext.bindValue(ViewersObservables.observeSingleSelection(viewer), selectedField)
  }
  /** Initialize table viewer 'Filters' */
  protected def initTableViewerFilters() {
    val viewer = getTableViewerFilters()
    viewer.setContentProvider(new ObservableListContentProvider())
    getTableViewerColumnFilter.setLabelProvider(new ColumnFilter.TLabelProvider(actualFilters))
    getTableViewerColumnFilter.getColumn.addSelectionListener(new ViewEditor.ViewSelectionAdapter(0, viewer))
    // Add a SWT.CHECK support
    viewer.getTable.addListener(SWT.Selection, new Listener() {
      def handleEvent(event: Event) = if (event.detail == SWT.CHECK)
        event.item match {
          case tableItem: TableItem =>
            val index = tableItem.getParent().indexOf(tableItem)
            viewer.getElementAt(index) match {
              case filter: Filter if tableItem.getChecked() =>
                actualFilters += filter.id
              case filter: Filter =>
                actualFilters -= filter.id
              case item =>
                log.fatal(s"unknown item $item")
            }
          case item =>
            log.fatal(s"unknown item $item")
        }
    })
    // Activate the tooltip support for the viewer
    ColumnViewerToolTipSupport.enableFor(viewer)
    // Add filters
    Main.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextFilterFilter()), filterFilters)
    val filter = new AtomicReference(".*".r.pattern)
    val filterListener = new BaseElement.RegexFilterListener(filter) {
      override def handleChange(event: ChangeEvent) {
        super.handleChange(event)
        getTableViewerFilters.refresh()
      }
    }
    filterFilters.underlying.addChangeListener(filterListener)
    viewer.setFilters(Array(new ViewEditor.FilterFilter(filter)))
    // Set sorting
    viewer.setComparator(new ViewEditor.ViewComparator(0))
    viewer.setInput(allFilters.underlying)
  }
  /** Initialize table viewer 'Properties' */
  protected def initTableViewerProperties() {
    val viewer = getTableViewerProperties()
    viewer.setContentProvider(new ObservableListContentProvider())
    getTableViewerColumnPropertyFrom.setLabelProvider(new ColumnPropertyFrom.TLabelProvider)
    getTableViewerColumnPropertyFrom.getColumn.addSelectionListener(new ViewEditor.ViewSelectionAdapter(0, viewer))
    // Activate the tooltip support for the viewer
    ColumnViewerToolTipSupport.enableFor(viewer)
    // Add the selection listener
    viewer.addSelectionChangedListener(new ISelectionChangedListener() {
      override def selectionChanged(event: SelectionChangedEvent) = event.getSelection() match {
        case selection: IStructuredSelection if !selection.isEmpty() =>
          ViewEditor.ActionAdd.setEnabled(true)
        case selection =>
          ViewEditor.ActionAdd.setEnabled(false)
      }
    })
    // Add filters
    val visibleFilter = new ViewerFilter {
      override def select(viewer: Viewer, parentElement: AnyRef, element: AnyRef): Boolean = !actualFields.contains(element)
    }
    Main.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextPropertyFilter()), filterProperties)
    val filter = new AtomicReference(".*".r.pattern)
    val filterListener = new BaseElement.RegexFilterListener(filter) {
      override def handleChange(event: ChangeEvent) {
        super.handleChange(event)
        getTableViewerProperties.refresh()
      }
    }
    filterProperties.underlying.addChangeListener(filterListener)
    viewer.setFilters(Array(visibleFilter, new ViewEditor.SymbolFilter(filter)))
    // Set sorting
    viewer.setComparator(new ViewEditor.ViewComparator(0))
    viewer.setInput(allProperties.underlying)
    Main.bindingContext.bindValue(ViewersObservables.observeSingleSelection(viewer), selectedProperty)
    viewer.getTable().pack
  }
  /** Initialize table viewer 'Sortings' */
  protected def initTableViewerSortings() {
    val viewer = getTableViewerSortings()
    viewer.setContentProvider(new ObservableListContentProvider())
    getTableViewerColumnSorting.setLabelProvider(new ColumnSorting.TLabelProvider(actualSortings))
    getTableViewerColumnSorting.getColumn.addSelectionListener(new ViewEditor.ViewSelectionAdapter(0, viewer))
    // Add a SWT.CHECK support
    viewer.getTable.addListener(SWT.Selection, new Listener() {
      def handleEvent(event: Event) = if (event.detail == SWT.CHECK)
        event.item match {
          case tableItem: TableItem =>
            val index = tableItem.getParent().indexOf(tableItem)
            viewer.getElementAt(index) match {
              case sorting: Sorting if tableItem.getChecked() =>
                actualSortings += sorting.id
              case sorting: Sorting =>
                actualSortings -= sorting.id
              case item =>
                log.fatal(s"unknown item $item")
            }
          case item =>
            log.fatal(s"unknown item $item")
        }
    })
    // Activate the tooltip support for the viewer
    ColumnViewerToolTipSupport.enableFor(viewer)
    // Add filters
    Main.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextSortingFilter()), filterSortings)
    val filter = new AtomicReference(".*".r.pattern)
    val filterListener = new BaseElement.RegexFilterListener(filter) {
      override def handleChange(event: ChangeEvent) {
        super.handleChange(event)
        getTableViewerSortings.refresh()
      }
    }
    filterSortings.underlying.addChangeListener(filterListener)
    viewer.setFilters(Array(new ViewEditor.SortingFilter(filter)))
    // Set sorting
    viewer.setComparator(new ViewEditor.ViewComparator(0))
    viewer.setInput(allSortings.underlying)
  }
  /** On dialog active */
  override protected def onActive = {
    updateOK()
    future { autoresize() }
    // prevent interference with the size calculation
    getTextPropertyFilter().setMessage(Messages.lookupFilter_text);
    getTextFieldFilter().setMessage(Messages.lookupFilter_text);
    getTextSortingFilter().setMessage(Messages.lookupFilter_text);
    getTextFilterFilter().setMessage(Messages.lookupFilter_text);
  }
  /** Updates an actual element template */
  protected[viewed] def updateActualView(before: Symbol, after: Symbol) {
    val index = actualFields.indexOf(before)
    actualFields.update(index, after)
    if (index == actualFields.size - 1)
      getTableViewerFields.refresh() // Workaround for the JFace bug. Force the last element modification.
    getTableViewerFields().setSelection(new StructuredSelection(after), true)
  }
  /** Update the dialog message */
  protected def updateDescription(error: Option[String]): String = {
    CustomMessages.viewEditorDescription_text.format(nameField.value.trim) +
      (error match {
        case Some(error) => "\n    * - " + error
        case None => "\n "
      })
  }
  /** Update OK button state */
  protected def updateOK() = if (ViewEditor.dialog.nonEmpty) {
    val error = validate()
    setMessage(updateDescription(error))
    Option(getButton(IDialogConstants.OK_ID)).foreach(_.setEnabled(actualFields.nonEmpty && error.isEmpty && {
      // new view
      !viewList.contains(view) ||
        // changed
        !{
          actualFields.sameElements(view.fields.toList) &&
            actualFilters.sameElements(view.filters.toList) &&
            actualSortings.sameElements(view.sortings.toList) &&
            availabilityField.value == view.availability &&
            nameField.value.trim == view.name &&
            descriptionField.value.trim == view.description
        }
    }))
  }
  /** Validate dialog for consistency */
  def validate(): Option[String] = {
    val name = nameField.value.trim
    if (name.isEmpty())
      return Some(Messages.nameIsNotDefined_text)
    if (viewList.exists(_.name == name) && name != view.name)
      return Some(Messages.nameIsAlreadyInUse_text.format(name))
    if (actualFields.isEmpty)
      return Some(Messages.thereAreNoSelectedProperties_text)
    None
  }
  /** Validates a text in the the name text field */
  def validateName(validator: Validator, text: String, valid: Boolean): Unit = if (!valid)
    validator.withDecoration(validator.showDecorationError(_))
  else if (text.isEmpty())
    validator.withDecoration(validator.showDecorationRequired(_))
  else
    validator.withDecoration(_.hide)
}

object ViewEditor extends Loggable {
  /** There is may be only one dialog instance at time */
  @volatile private var dialog: Option[ViewEditor] = None

  /** Apply a f(x) to the selected property if any */
  def property[T](f: (ViewEditor, Symbol) => T): Option[T] =
    dialog.flatMap(d => Option(d.selectedProperty.value).map(f(d, _)))
  /** Apply a f(x) to the selected view if any */
  def field[T](f: (ViewEditor, Symbol) => T): Option[T] =
    dialog.flatMap(d => Option(d.selectedField.value).map(f(d, _)))

  class FilterFilter(filter: AtomicReference[Pattern]) extends ViewerFilter {
    override def select(viewer: Viewer, parentElement: AnyRef, element: AnyRef): Boolean = {
      val pattern = filter.get
      val item = element.asInstanceOf[Filter]
      pattern.matcher(item.name.toLowerCase()).matches()
    }
  }
  class SymbolFilter(filter: AtomicReference[Pattern]) extends ViewerFilter {
    override def select(viewer: Viewer, parentElement: AnyRef, element: AnyRef): Boolean = {
      val pattern = filter.get
      val item = element.asInstanceOf[Symbol]
      pattern.matcher(item.name.toLowerCase()).matches()
    }
  }
  class SortingFilter(filter: AtomicReference[Pattern]) extends ViewerFilter {
    override def select(viewer: Viewer, parentElement: AnyRef, element: AnyRef): Boolean = {
      val pattern = filter.get
      val item = element.asInstanceOf[Sorting]
      pattern.matcher(item.name.toLowerCase()).matches()
    }
  }
  class ViewComparator(initialColumn: Int, initialDirection: Boolean = Default.sortingDirection, dual: Boolean = false) extends ViewerComparator {
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
      val rc = e1 match {
        case entity1: Filter =>
          val entity2 = e2.asInstanceOf[Filter]
          entity1.name.compareTo(entity2.name)
        case entity1: Sorting =>
          val entity2 = e2.asInstanceOf[Sorting]
          entity1.name.compareTo(entity2.name)
        case entity1: Symbol =>
          val entity2 = e2.asInstanceOf[Symbol]
          if (dual) {
            // compare dual columns table values
            column match {
              case 0 =>
                val input = viewer.getInput().asInstanceOf[org.eclipse.core.databinding.observable.list.WritableList]
                input.indexOf(entity1).compareTo(input.indexOf(entity2))
              case 1 => entity1.name.compareTo(entity2.name)
            }
          } else {
            // compare single column table values
            entity1.name.compareTo(entity2.name)
          }
      }
      if (directionVar) -rc else rc
    }
    /** Switch comparator direction */
    def switchDirection() {
      directionVar = !directionVar
    }
  }
  class ViewSelectionAdapter(column: Int, viewer: TableViewer) extends SelectionAdapter {
    override def widgetSelected(e: SelectionEvent) = dialog.foreach { dialog =>
      val comparator = viewer.getComparator().asInstanceOf[ViewComparator]
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
  object ActionAdd extends Action(">") with Loggable {
    override def run = ViewEditor.property { (dialog, property) =>
      dialog.actualFields += property
      future { dialog.autoresize() }
      dialog.getTableViewerProperties.refresh()
    }
  }
  object ActionRemove extends Action("<") with Loggable {
    override def run = ViewEditor.field { (dialog, field) =>
      dialog.actualFields -= field
      future { dialog.autoresize() }
      dialog.getTableViewerProperties.refresh()
    }
  }
  object ActionUp extends Action(Messages.up_text) with Loggable {
    override def run = ViewEditor.field { (dialog, field) =>
      val index = dialog.actualFields.indexOf(field)
      if (index > -1 && 0 <= (index - 1)) {
        dialog.actualFields.update(index, dialog.actualFields(index - 1))
        dialog.actualFields.update(index - 1, field)
        dialog.getTableViewerFields.refresh()
        dialog.getTableViewerFields.setSelection(new StructuredSelection(field), true)
      }
    }
  }
  object ActionDown extends Action(Messages.down_text) with Loggable {
    override def run = ViewEditor.field { (dialog, field) =>
      val index = dialog.actualFields.indexOf(field)
      if (index > -1 && dialog.actualFields.size > (index + 1)) {
        dialog.actualFields.update(index, dialog.actualFields(index + 1))
        dialog.actualFields.update(index + 1, field)
        dialog.getTableViewerFields.refresh()
        dialog.getTableViewerFields.setSelection(new StructuredSelection(field), true)
      }
    }
  }
}
