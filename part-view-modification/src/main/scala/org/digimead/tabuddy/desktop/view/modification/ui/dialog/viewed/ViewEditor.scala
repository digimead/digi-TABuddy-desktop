/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2012-2015 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.view.modification.ui.dialog.viewed

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import java.util.regex.Pattern
import javax.inject.Inject
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.support.{ App, WritableList, WritableValue }
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.core.ui.definition.Dialog
import org.digimead.tabuddy.desktop.core.ui.support.{ RegexFilterListener, TextValidator, Validator }
import org.digimead.tabuddy.desktop.logic.payload.Payload
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.desktop.logic.payload.view.{ Filter, Sorting, View }
import org.digimead.tabuddy.desktop.view.modification.{ Default, Messages }
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.graph.Graph
import org.eclipse.core.databinding.observable.ChangeEvent
import org.eclipse.e4.core.contexts.IEclipseContext
import org.eclipse.jface.action.{ Action, ActionContributionItem }
import org.eclipse.jface.databinding.swt.WidgetProperties
import org.eclipse.jface.databinding.viewers.{ ObservableListContentProvider, ViewersObservables }
import org.eclipse.jface.dialogs.IDialogConstants
import org.eclipse.jface.viewers.{ ColumnViewerToolTipSupport, ISelectionChangedListener, IStructuredSelection, SelectionChangedEvent, StructuredSelection, TableViewer, Viewer, ViewerComparator, ViewerFilter }
import org.eclipse.swt.SWT
import org.eclipse.swt.events.{ DisposeEvent, DisposeListener, FocusEvent, FocusListener, SelectionAdapter, SelectionEvent, ShellAdapter, ShellEvent, VerifyEvent }
import org.eclipse.swt.widgets.{ Composite, Control, Event, Listener, Shell, TableItem, Text }
import scala.collection.mutable
import scala.concurrent.Future
import scala.ref.WeakReference

class ViewEditor @Inject() (
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
  /** Initial view definition. */
  val view: View,
  /** Initial view list. */
  val viewList: List[View])
    extends ViewEditorSkel(parentShell) with Dialog with XLoggable {
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** The actual fields */
  val actualFields = WritableList(view.fields.toList)
  /** The actual filters UUID */
  val actualFilters = WritableList(view.filters.toList)
  /** The actual sortings UUID */
  val actualSortings = WritableList(view.sortings.toList)
  /** All available filters */
  val allFilters = WritableList((payload.getAvailableViewFilters - Filter.allowAllFilter).toList.sortBy(_.name))
  /** All defined properties of the current model grouped by id */
  val allProperties: WritableList[Symbol] = WritableList(payload.elementTemplates.values.
    flatMap { template ⇒ template.properties.flatMap(_._2) }.map(property ⇒ property.id).toList.distinct.sortBy(_.name))
  /** All available sortings */
  val allSortings = WritableList((payload.getAvailableViewSortings - Sorting.simpleSorting).toList.sortBy(_.name))
  /** The auto resize lock */
  val autoResizeLock = new ReentrantLock()
  /** The property representing current enumeration availability */
  val availabilityField = WritableValue[java.lang.Boolean]
  /** The property representing current view description */
  val descriptionField = WritableValue[String]
  /** The property representing fields filter content */
  val filterFields = WritableValue("")
  /** The property representing filters filter content */
  val filterFilters = WritableValue("")
  /** The property representing properties filter content */
  val filterProperties = WritableValue("")
  /** The property representing sortings filter content */
  val filterSortings = WritableValue("")
  /** Activate context on focus. */
  val focusListener = new FocusListener() {
    def focusGained(e: FocusEvent) = context.activateBranch()
    def focusLost(e: FocusEvent) {}
  }
  /** The property representing current view name */
  val nameField = WritableValue[String]("")
  /** The property representing a selected field */
  val selectedField = WritableValue[Symbol]
  /** The property representing a selected property */
  val selectedProperty = WritableValue[Symbol]
  /** Activate context on shell events. */
  val shellListener = new ShellAdapter() {
    override def shellActivated(e: ShellEvent) = context.activateBranch()
  }
  /** Actual sortBy column index */
  @volatile protected var sortColumn = 0 // by an id
  /** Actual sort direction */
  @volatile protected var sortDirection = Default.sortingDirection

  def getModifiedViews(): View = {
    val name = nameField.value.trim
    val description = descriptionField.value.trim
    new View(view.id, name, description, availabilityField.value, mutable.LinkedHashSet(actualFields: _*),
      mutable.LinkedHashSet(actualFilters: _*), mutable.LinkedHashSet(actualSortings: _*))
  }

  /** Auto resize tableviewer columns */
  protected def autoresize() = if (autoResizeLock.tryLock()) try {
    Thread.sleep(50)
    App.execNGet {
      if (!getTableViewerFields.getTable.isDisposed()) {
        UI.adjustViewerColumnWidth(getTableViewerColumnN(), Default.columnPadding)
        getTableViewerFields.refresh()
      }
    }
  } finally {
    autoResizeLock.unlock()
  }
  /** Create contents of the dialog. */
  @log(result = false)
  override protected def createDialogArea(parent: Composite): Control = {
    val result = super.createDialogArea(parent)
    context.set(classOf[Composite], parent)
    context.set(classOf[org.eclipse.jface.dialogs.Dialog], this)
    new ActionContributionItem(ActionAdd).fill(getCompositeBody1())
    new ActionContributionItem(ActionRemove).fill(getCompositeBody1())
    new ActionContributionItem(ActionUp).fill(getCompositeBody2())
    new ActionContributionItem(ActionDown).fill(getCompositeBody2())
    ActionAdd.setEnabled(false)
    ActionRemove.setEnabled(false)
    ActionUp.setEnabled(false)
    ActionDown.setEnabled(false)
    initTableViewerFields()
    initTableViewerFilters()
    initTableViewerProperties()
    initTableViewerSortings()
    // bind the view info: an availability
    App.bindingContext.bindValue(WidgetProperties.selection().observe(getBtnCheckAvailability()), availabilityField)
    val availabilityFieldListener = availabilityField.addChangeListener { (_, _) ⇒ updateOK() }
    availabilityField.value = view.availability
    // bind the view info: a description
    App.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextDescription()), descriptionField)
    val descriptionFieldListener = descriptionField.addChangeListener { (_, _) ⇒ updateOK() }
    descriptionField.value = view.description
    // bind the view info: a name
    App.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextName()), nameField)
    val nameFieldValidator = TextValidator(getTextName(), true)((validator, event) ⇒
      if (event.keyCode != 0) validateName(validator, event.getSource.asInstanceOf[Text].getText, event.doit))
    val nameFieldListener = nameField.addChangeListener { (name, _) ⇒
      validateName(nameFieldValidator, name.trim(), true)
      updateOK()
    }
    nameField.value = view.name
    val actualFieldsListener = actualFields.addChangeListener { event ⇒ updateOK() }
    val actualFiltersListener = actualFilters.addChangeListener { event ⇒ updateOK() }
    val actualSortingListener = actualSortings.addChangeListener { event ⇒ updateOK() }
    getShell().addShellListener(shellListener)
    getShell().addFocusListener(focusListener)
    // Add the dispose listener
    getShell().addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) {
        getShell().removeFocusListener(focusListener)
        getShell().removeShellListener(shellListener)
        actualFields.removeChangeListener(actualFieldsListener)
        actualFilters.removeChangeListener(actualFiltersListener)
        actualSortings.removeChangeListener(actualSortingListener)
        availabilityField.removeChangeListener(availabilityFieldListener)
        descriptionField.removeChangeListener(descriptionFieldListener)
        nameField.removeChangeListener(nameFieldListener)
      }
    })
    // Set the dialog message
    setMessage(Messages.viewEditorDescription_text.format(view.name))
    // Set the dialog window title
    getShell().setText(Messages.viewEditorDialog_text.format(view.name))
    result
  }
  /** Initialize table viewer 'Fields' */
  protected def initTableViewerFields() {
    val viewer = getTableViewerFields()
    viewer.setContentProvider(new ObservableListContentProvider())
    getTableViewerColumnN.setLabelProvider(new ColumnN.TLabelProvider(actualFields))
    getTableViewerColumnN.getColumn.addSelectionListener(new ViewEditor.ViewSelectionAdapter(new WeakReference(this), 0, viewer))
    getTableViewerColumnField.setLabelProvider(new ColumnField.TLabelProvider)
    getTableViewerColumnField.getColumn.addSelectionListener(new ViewEditor.ViewSelectionAdapter(new WeakReference(this), 1, viewer))
    // Activate the tooltip support for the viewer
    ColumnViewerToolTipSupport.enableFor(viewer)
    // Add the selection listener
    viewer.addSelectionChangedListener(new ISelectionChangedListener() {
      override def selectionChanged(event: SelectionChangedEvent) = event.getSelection() match {
        case selection: IStructuredSelection if !selection.isEmpty() ⇒
          val field = selection.getFirstElement().asInstanceOf[Symbol]
          ActionUp.setEnabled(actualFields.headOption != Some(field))
          ActionDown.setEnabled(actualFields.lastOption != Some(field))
          ActionRemove.setEnabled(true)
        case selection ⇒
          ActionRemove.setEnabled(false)
          ActionUp.setEnabled(false)
          ActionDown.setEnabled(false)
      }
    })
    // Add the filter
    App.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextFieldFilter()), filterFields)
    val filter = new AtomicReference(".*".r.pattern)
    val filterListener = new RegexFilterListener(filter) {
      override def handleChange(event: ChangeEvent) {
        super.handleChange(event)
        getTableViewerFields.refresh()
      }
    }
    filterFields.underlying.addChangeListener(filterListener)
    viewer.setFilters(Array(new ViewEditor.SymbolFilter(filter)))
    // Set sorting
    viewer.setComparator(new ViewEditor.ViewComparator(new WeakReference(this), true))
    viewer.setInput(actualFields.underlying)
    App.bindingContext.bindValue(ViewersObservables.observeSingleSelection(viewer), selectedField)
  }
  /** Initialize table viewer 'Filters' */
  protected def initTableViewerFilters() {
    val viewer = getTableViewerFilters()
    viewer.setContentProvider(new ObservableListContentProvider())
    getTableViewerColumnFilter.setLabelProvider(new ColumnFilter.TLabelProvider(actualFilters))
    getTableViewerColumnFilter.getColumn.addSelectionListener(new ViewEditor.ViewSelectionAdapter(new WeakReference(this), 0, viewer))
    // Add a SWT.CHECK support
    viewer.getTable.addListener(SWT.Selection, new Listener() {
      def handleEvent(event: Event) = if (event.detail == SWT.CHECK)
        event.item match {
          case tableItem: TableItem ⇒
            val index = tableItem.getParent().indexOf(tableItem)
            viewer.getElementAt(index) match {
              case filter: Filter if tableItem.getChecked() ⇒
                actualFilters += filter.id
              case filter: Filter ⇒
                actualFilters -= filter.id
              case item ⇒
                log.fatal(s"unknown item $item")
            }
          case item ⇒
            log.fatal(s"unknown item $item")
        }
    })
    // Activate the tooltip support for the viewer
    ColumnViewerToolTipSupport.enableFor(viewer)
    // Add filters
    App.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextFilterFilter()), filterFilters)
    val filter = new AtomicReference(".*".r.pattern)
    val filterListener = new RegexFilterListener(filter) {
      override def handleChange(event: ChangeEvent) {
        super.handleChange(event)
        getTableViewerFilters.refresh()
      }
    }
    filterFilters.underlying.addChangeListener(filterListener)
    viewer.setFilters(Array(new ViewEditor.FilterFilter(filter)))
    // Set sorting
    viewer.setComparator(new ViewEditor.ViewComparator(new WeakReference(this)))
    viewer.setInput(allFilters.underlying)
  }
  /** Initialize table viewer 'Properties' */
  protected def initTableViewerProperties() {
    val viewer = getTableViewerProperties()
    viewer.setContentProvider(new ObservableListContentProvider())
    getTableViewerColumnPropertyFrom.setLabelProvider(new ColumnPropertyFrom.TLabelProvider)
    getTableViewerColumnPropertyFrom.getColumn.addSelectionListener(new ViewEditor.ViewSelectionAdapter(new WeakReference(this), 0, viewer))
    // Activate the tooltip support for the viewer
    ColumnViewerToolTipSupport.enableFor(viewer)
    // Add the selection listener
    viewer.addSelectionChangedListener(new ISelectionChangedListener() {
      override def selectionChanged(event: SelectionChangedEvent) = event.getSelection() match {
        case selection: IStructuredSelection if !selection.isEmpty() ⇒
          ActionAdd.setEnabled(true)
        case selection ⇒
          ActionAdd.setEnabled(false)
      }
    })
    // Add filters
    val visibleFilter = new ViewerFilter {
      override def select(viewer: Viewer, parentElement: AnyRef, element: AnyRef): Boolean = !actualFields.contains(element)
    }
    App.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextPropertyFilter()), filterProperties)
    val filter = new AtomicReference(".*".r.pattern)
    val filterListener = new RegexFilterListener(filter) {
      override def handleChange(event: ChangeEvent) {
        super.handleChange(event)
        getTableViewerProperties.refresh()
      }
    }
    filterProperties.underlying.addChangeListener(filterListener)
    viewer.setFilters(Array(visibleFilter, new ViewEditor.SymbolFilter(filter)))
    // Set sorting
    viewer.setComparator(new ViewEditor.ViewComparator(new WeakReference(this)))
    viewer.setInput(allProperties.underlying)
    App.bindingContext.bindValue(ViewersObservables.observeSingleSelection(viewer), selectedProperty)
    viewer.getTable().pack
  }
  /** Initialize table viewer 'Sortings' */
  protected def initTableViewerSortings() {
    val viewer = getTableViewerSortings()
    viewer.setContentProvider(new ObservableListContentProvider())
    getTableViewerColumnSorting.setLabelProvider(new ColumnSorting.TLabelProvider(actualSortings))
    getTableViewerColumnSorting.getColumn.addSelectionListener(new ViewEditor.ViewSelectionAdapter(new WeakReference(this), 0, viewer))
    // Add a SWT.CHECK support
    viewer.getTable.addListener(SWT.Selection, new Listener() {
      def handleEvent(event: Event) = if (event.detail == SWT.CHECK)
        event.item match {
          case tableItem: TableItem ⇒
            val index = tableItem.getParent().indexOf(tableItem)
            viewer.getElementAt(index) match {
              case sorting: Sorting if tableItem.getChecked() ⇒
                actualSortings += sorting.id
              case sorting: Sorting ⇒
                actualSortings -= sorting.id
              case item ⇒
                log.fatal(s"unknown item $item")
            }
          case item ⇒
            log.fatal(s"unknown item $item")
        }
    })
    // Activate the tooltip support for the viewer
    ColumnViewerToolTipSupport.enableFor(viewer)
    // Add filters
    App.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextSortingFilter()), filterSortings)
    val filter = new AtomicReference(".*".r.pattern)
    val filterListener = new RegexFilterListener(filter) {
      override def handleChange(event: ChangeEvent) {
        super.handleChange(event)
        getTableViewerSortings.refresh()
      }
    }
    filterSortings.underlying.addChangeListener(filterListener)
    viewer.setFilters(Array(new ViewEditor.SortingFilter(filter)))
    // Set sorting
    viewer.setComparator(new ViewEditor.ViewComparator(new WeakReference(this)))
    viewer.setInput(allSortings.underlying)
  }
  /** On dialog active */
  override protected def onActive = {
    updateOK()
    Future { autoresize() } onFailure {
      case e: Exception ⇒ log.error(e.getMessage(), e)
      case e ⇒ log.error(e.toString())
    }
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
    Messages.viewEditorDescription_text.format(nameField.value.trim) +
      (error match {
        case Some(error) ⇒ "\n    * - " + error
        case None ⇒ "\n "
      })
  }
  /** Update OK button state */
  protected def updateOK() = {
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
  def validateName(validator: Validator[VerifyEvent], text: String, valid: Boolean): Unit = if (!valid)
    validator.withDecoration(validator.showDecorationError(_))
  else if (text.isEmpty())
    validator.withDecoration(validator.showDecorationRequired(_))
  else
    validator.withDecoration(_.hide)

  object ActionAdd extends Action(">") with XLoggable {
    override def run = Option(selectedProperty.value) foreach { property ⇒
      actualFields += property
      Future { autoresize() } onFailure {
        case e: Exception ⇒ log.error(e.getMessage(), e)
        case e ⇒ log.error(e.toString())
      }
      getTableViewerProperties.refresh()
    }
  }
  object ActionRemove extends Action("<") with XLoggable {
    override def run = Option(selectedField.value) foreach { field ⇒
      actualFields -= field
      Future { autoresize() } onFailure {
        case e: Exception ⇒ log.error(e.getMessage(), e)
        case e ⇒ log.error(e.toString())
      }
      getTableViewerProperties.refresh()
    }
  }
  object ActionUp extends Action(Messages.up_text) with XLoggable {
    override def run = Option(selectedField.value) foreach { field ⇒
      val index = actualFields.indexOf(field)
      if (index > -1 && 0 <= (index - 1)) {
        actualFields.update(index, actualFields(index - 1))
        actualFields.update(index - 1, field)
        getTableViewerFields.refresh()
        getTableViewerFields.setSelection(new StructuredSelection(field), true)
      }
    }
  }
  object ActionDown extends Action(Messages.down_text) with XLoggable {
    override def run = Option(selectedField.value) foreach { field ⇒
      val index = actualFields.indexOf(field)
      if (index > -1 && actualFields.size > (index + 1)) {
        actualFields.update(index, actualFields(index + 1))
        actualFields.update(index + 1, field)
        getTableViewerFields.refresh()
        getTableViewerFields.setSelection(new StructuredSelection(field), true)
      }
    }
  }
}

object ViewEditor extends XLoggable {
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
  class ViewComparator(dialog: WeakReference[ViewEditor], dual: Boolean = false) extends ViewerComparator {
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
      val rc = e1 match {
        case entity1: Filter ⇒
          val entity2 = e2.asInstanceOf[Filter]
          entity1.name.compareTo(entity2.name)
        case entity1: Sorting ⇒
          val entity2 = e2.asInstanceOf[Sorting]
          entity1.name.compareTo(entity2.name)
        case entity1: Symbol ⇒
          val entity2 = e2.asInstanceOf[Symbol]
          if (dual) {
            // compare dual columns table values
            column match {
              case 0 ⇒
                val input = viewer.getInput().asInstanceOf[org.eclipse.core.databinding.observable.list.WritableList]
                input.indexOf(entity1).compareTo(input.indexOf(entity2))
              case 1 ⇒ entity1.name.compareTo(entity2.name)
            }
          } else {
            // compare single column table values
            entity1.name.compareTo(entity2.name)
          }
      }
      if (_direction) -rc else rc
    }
    /** Switch comparator direction */
    def switchDirection() {
      _direction = !_direction
      dialog.get.foreach(_.sortDirection = _direction)
    }
  }
  class ViewSelectionAdapter(dialog: WeakReference[ViewEditor], column: Int, viewer: TableViewer) extends SelectionAdapter {
    override def widgetSelected(e: SelectionEvent) = dialog.get foreach { dialog ⇒
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
}
