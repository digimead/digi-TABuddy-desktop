/**
 * This file is part of the TA Buddy project.
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

package org.digimead.tabuddy.desktop.view.modification.dialog.filtered

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import java.util.regex.Pattern
import javax.inject.Inject
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.WritableList
import org.digimead.tabuddy.desktop.core.support.WritableValue
import org.digimead.tabuddy.desktop.logic.filter.AvailableFilters
import org.digimead.tabuddy.desktop.logic.payload.{ Payload, PropertyType, api ⇒ papi, view }
import org.digimead.tabuddy.desktop.logic.payload.maker.GraphMarker
import org.digimead.tabuddy.desktop.ui.UI
import org.digimead.tabuddy.desktop.ui.definition.Dialog
import org.digimead.tabuddy.desktop.ui.support.RegexFilterListener
import org.digimead.tabuddy.desktop.ui.support.Validator
import org.digimead.tabuddy.desktop.view.modification.{ Default, Messages }
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.graph.Graph
import org.eclipse.core.databinding.observable.ChangeEvent
import org.eclipse.e4.core.contexts.IEclipseContext
import org.eclipse.jface.action.{ Action, ActionContributionItem, IAction, IMenuListener, IMenuManager, MenuManager }
import org.eclipse.jface.databinding.swt.WidgetProperties
import org.eclipse.jface.databinding.viewers.{ ObservableListContentProvider, ViewersObservables }
import org.eclipse.jface.dialogs.IDialogConstants
import org.eclipse.jface.viewers.{ ColumnViewerToolTipSupport, ISelectionChangedListener, IStructuredSelection, SelectionChangedEvent, StructuredSelection, TableViewer, Viewer, ViewerComparator, ViewerFilter }
import org.eclipse.swt.SWT
import org.eclipse.swt.events.{ DisposeEvent, DisposeListener, FocusEvent, FocusListener, SelectionAdapter, SelectionEvent, ShellAdapter, ShellEvent }
import org.eclipse.swt.widgets.Text
import org.eclipse.swt.widgets.{ Composite, Control, Event, Listener, Shell, TableItem }
import scala.collection.immutable
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future
import scala.ref.WeakReference

class FilterEditor @Inject() (
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
  /** Initial filter definition. */
  val filter: view.api.Filter,
  /** Initial filter list. */
  val filterList: List[view.api.Filter])
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
  /** Activate context on focus. */
  protected val focusListener = new FocusListener() {
    def focusGained(e: FocusEvent) = context.activateBranch()
    def focusLost(e: FocusEvent) {}
  }
  /** The property representing current view name */
  protected val nameField = WritableValue[String]("")
  /** The property representing a selected property */
  protected val selectedProperty = WritableValue[FilterEditor.PropertyItem[_ <: AnySRef]]
  /** The property representing a selected sorting */
  protected val selectedRule = WritableValue[view.api.Filter.Rule]
  /** Activate context on shell events. */
  protected val shellListener = new ShellAdapter() {
    override def shellActivated(e: ShellEvent) = context.activateBranch()
  }
  /** Actual sortBy column index */
  @volatile protected var sortColumn = 0 // by an id
  /** Actual sort direction */
  @volatile protected var sortDirection = Default.sortingDirection
  /** All defined properties of the current model grouped by id, type  */
  protected[filtered] lazy val total: WritableList[FilterEditor.PropertyItem[_ <: AnySRef]] = WritableList(payload.elementTemplates.values.
    flatMap { template ⇒ template.properties.flatMap(_._2) }.map(property ⇒ FilterEditor.PropertyItem(property.id, property.ptype,
      !actual.exists(definition ⇒ definition.property == property.id && definition.propertyType == property.ptype.id))).
    toList.distinct.sortBy(_.ptype.typeSymbol.name).sortBy(_.id.name))

  def getModifiedFilter(): view.api.Filter = {
    val name = nameField.value.trim
    val description = descriptionField.value.trim
    new view.Filter(filter.id, name, description, availabilityField.value, mutable.LinkedHashSet(actual: _*))
  }

  /** Auto resize tableviewer columns */
  protected def autoresize() = if (autoResizeLock.tryLock()) try {
    Thread.sleep(50)
    App.execNGet {
      if (!getTableViewerFilters.getTable.isDisposed() && !getTableViewerProperties.getTable.isDisposed()) {
        UI.adjustTableViewerColumnWidth(getTableViewerColumnPropertyFrom(), Default.columnPadding)
        UI.adjustTableViewerColumnWidth(getTableViewerColumnProperty(), Default.columnPadding)
        UI.adjustTableViewerColumnWidth(getTableViewerColumnType(), Default.columnPadding)
        UI.adjustTableViewerColumnWidth(getTableViewerColumnInversion(), Default.columnPadding)
        UI.adjustTableViewerColumnWidth(getTableViewerColumnFilter(), Default.columnPadding)
        getTableViewerProperties.refresh()
        getTableViewerFilters.refresh()
      }
    }
  } finally {
    autoResizeLock.unlock()
  }
  /** Builds the dialog message */
  protected def updateDescription(error: Option[String]): String = {
    Messages.viewFilterEditorDescription_text.format(nameField.value.trim) +
      (error match {
        case Some(error) ⇒ "\n    * - " + error
        case None ⇒ "\n "
      })
  }
  /** Create contents of the dialog. */
  override protected def createDialogArea(parent: Composite): Control = {
    val result = super.createDialogArea(parent)
    context.set(classOf[Composite], parent)
    new ActionContributionItem(ActionAdd).fill(getCompositeBody())
    new ActionContributionItem(ActionRemove).fill(getCompositeBody())
    ActionAdd.setEnabled(false)
    ActionRemove.setEnabled(false)
    initTableViewerProperties()
    initTableViewerSortings()
    // bind the sorting info: an availability
    App.bindingContext.bindValue(WidgetProperties.selection().observe(getBtnCheckAvailability()), availabilityField)
    val availabilityFieldListener = availabilityField.addChangeListener { (availability, event) ⇒ updateOK() }
    availabilityField.value = filter.availability
    // bind the sorting info: a description
    App.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextDescription()), descriptionField)
    val descriptionFieldListener = descriptionField.addChangeListener { (description, event) ⇒ updateOK() }
    descriptionField.value = filter.description
    // bind the sorting info: a name
    App.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTextName()), nameField)
    val nameFieldValidator = Validator(getTextName(), true)((validator, event) ⇒
      if (event.keyCode != 0) validateName(validator, event.getSource.asInstanceOf[Text].getText, event.doit))
    val nameFieldListener = nameField.addChangeListener { (name, event) ⇒
      validateName(nameFieldValidator, name.trim(), true)
      updateOK()
    }
    nameField.value = filter.name
    val actualListener = actual.addChangeListener { event ⇒ updateOK() }
    getShell().addShellListener(shellListener)
    getShell().addFocusListener(focusListener)
    // Add the dispose listener
    getShell().addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) {
        getShell().removeFocusListener(focusListener)
        getShell().removeShellListener(shellListener)
        actual.removeChangeListener(actualListener)
        availabilityField.removeChangeListener(availabilityFieldListener)
        descriptionField.removeChangeListener(descriptionFieldListener)
        nameField.removeChangeListener(nameFieldListener)
      }
    })
    // Set the dialog message
    setMessage(Messages.viewFilterEditorDescription_text.format(filter.name))
    // Set the dialog window title
    getShell().setText(Messages.viewFilterEditorDialog_text.format(filter.name))
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
    getTableViewerColumnPropertyFrom.getColumn.addSelectionListener(new FilterEditor.PropertySelectionAdapter(new WeakReference(this), 0))
    getTableViewerColumnTypeFrom.setLabelProvider(new ColumnTypeFrom.TLabelProvider(graph))
    getTableViewerColumnTypeFrom.getColumn.addSelectionListener(new FilterEditor.PropertySelectionAdapter(new WeakReference(this), 1))
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
        case selection: IStructuredSelection if !selection.isEmpty() ⇒
          ActionAdd.setEnabled(true)
        case selection ⇒
          ActionAdd.setEnabled(false)
      }
    })
    // Add filters
    val visibleFilter = new ViewerFilter {
      override def select(viewer: Viewer, parentElement: AnyRef, element: AnyRef): Boolean =
        element.asInstanceOf[FilterEditor.PropertyItem[AnySRef]].visible
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
    viewer.setFilters(Array(visibleFilter, new FilterEditor.PropertyFilter(graph, filter)))
    // Set sorting
    viewer.setComparator(new FilterEditor.FilterComparator(graph, new WeakReference(this)))
    viewer.setInput(total.underlying)
    App.bindingContext.bindValue(ViewersObservables.observeSingleSelection(viewer), selectedProperty)
    viewer.getTable().pack
  }
  /** Initialize tableTableViewerSortings */
  protected def initTableViewerSortings() {
    val viewer = getTableViewerFilters()
    viewer.setContentProvider(new ObservableListContentProvider())
    getTableViewerColumnProperty.setLabelProvider(new ColumnProperty.TLabelProvider)
    getTableViewerColumnProperty.getColumn.addSelectionListener(new FilterEditor.FilterSelectionAdapter(new WeakReference(this), 0))
    getTableViewerColumnType.setLabelProvider(new ColumnType.TLabelProvider)
    getTableViewerColumnType.getColumn.addSelectionListener(new FilterEditor.FilterSelectionAdapter(new WeakReference(this), 1))
    getTableViewerColumnInversion.setLabelProvider(new ColumnInversion.TLabelProvider)
    getTableViewerColumnInversion.setEditingSupport(new ColumnInversion.TEditingSupport(viewer, this))
    getTableViewerColumnInversion.getColumn.addSelectionListener(new FilterEditor.FilterSelectionAdapter(new WeakReference(this), 2))
    getTableViewerColumnFilter.setLabelProvider(new ColumnSorting.TLabelProvider)
    getTableViewerColumnFilter.setEditingSupport(new ColumnSorting.TEditingSupport(viewer, this))
    getTableViewerColumnFilter.getColumn.addSelectionListener(new FilterEditor.FilterSelectionAdapter(new WeakReference(this), 3))
    getTableViewerColumnArgument.setLabelProvider(new ColumnArgument.TLabelProvider)
    getTableViewerColumnArgument.setEditingSupport(new ColumnArgument.TEditingSupport(viewer, this))
    getTableViewerColumnArgument.getColumn.addSelectionListener(new FilterEditor.FilterSelectionAdapter(new WeakReference(this), 4))
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
        case selection: IStructuredSelection if !selection.isEmpty() ⇒
          ActionRemove.setEnabled(true)
        case selection ⇒
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
    viewer.setFilters(Array(new FilterEditor.Filter(graph, filter)))
    // Set sorting
    viewer.setComparator(new FilterEditor.FilterComparator(graph, new WeakReference(this)))
    viewer.setInput(actual.underlying)
    App.bindingContext.bindValue(ViewersObservables.observeSingleSelection(viewer), selectedRule)
  }
  /** On dialog active */
  override protected def onActive = {
    updateOK()
    if (ActionAutoResize.isChecked())
      future { autoresize() } onFailure {
        case e: Exception ⇒ log.error(e.getMessage(), e)
        case e ⇒ log.error(e.toString())
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
        case e: Exception ⇒ log.error(e.getMessage(), e)
        case e ⇒ log.error(e.toString())
      }
  }
  /** Update OK button state */
  protected def updateOK() {
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
    override def run = if (isChecked()) future { autoresize }
  }
  object ActionAdd extends Action(">") with Loggable {
    override def run = Option(selectedProperty.value) foreach { property ⇒
      property.visible = false
      actual += view.api.Filter.Rule(property.id, property.ptype.id, false, view.Filter.allowAllFilter.id, "")
      if (ActionAutoResize.isChecked())
        future { autoresize() } onFailure {
          case e: Exception ⇒ log.error(e.getMessage(), e)
          case e ⇒ log.error(e.toString())
        }
      else
        getTableViewerProperties.refresh()
    }
  }
  object ActionRemove extends Action("<") with Loggable {
    override def run = Option(selectedRule.value) foreach { rule ⇒
      actual -= rule
      total.find(property ⇒ rule.property == property.id && rule.propertyType == property.ptype.id).foreach(_.visible = true)
      if (ActionAutoResize.isChecked())
        future { autoresize() } onFailure {
          case e: Exception ⇒ log.error(e.getMessage(), e)
          case e ⇒ log.error(e.toString())
        }
      else
        getTableViewerProperties.refresh()
    }
  }
}

object FilterEditor extends Loggable {
  class FilterComparator(graph: Graph[_ <: Model.Like], dialog: WeakReference[FilterEditor]) extends ViewerComparator {
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
      e1 match {
        case entity1: view.api.Filter.Rule ⇒
          val entity2 = e2.asInstanceOf[view.api.Filter.Rule]
          val rc = column match {
            case 0 ⇒ entity1.property.name.compareTo(entity2.property.name)
            case 1 ⇒ entity1.propertyType.name.compareTo(entity2.propertyType.name)
            case 2 ⇒ entity1.not.compareTo(entity2.not)
            case 3 ⇒ AvailableFilters.map.get(entity1.filter).map(_.name).getOrElse("").
              compareTo(AvailableFilters.map.get(entity2.filter).map(_.name).getOrElse(""))
            case 4 ⇒
              val argument1 = AvailableFilters.map.get(entity1.filter).flatMap(filter ⇒
                filter.stringToArgument(entity1.argument).map(filter.generic.argumentToText)).getOrElse(entity1.argument)
              val argument2 = AvailableFilters.map.get(entity2.filter).flatMap(filter ⇒
                filter.stringToArgument(entity2.argument).map(filter.generic.argumentToText)).getOrElse(entity2.argument)
              argument1.compareTo(argument2)
            case index ⇒
              log.fatal(s"unknown column with index $index"); 0
          }
          if (_direction) -rc else rc
        case entity1: PropertyItem[_] ⇒
          val entity2 = e2.asInstanceOf[PropertyItem[_]]
          val rc = column match {
            case 0 ⇒ entity1.id.name.compareTo(entity2.id.name)
            case 1 ⇒ entity1.ptype.name(graph).compareTo(entity2.ptype.name(graph))
            case index ⇒
              log.fatal(s"unknown column with index $index"); 0
          }
          if (_direction) -rc else rc
      }
    }
    /** Switch comparator direction */
    def switchDirection() {
      _direction = !_direction
      dialog.get.foreach(_.sortDirection = _direction)
    }
  }
  class Filter(graph: Graph[_ <: Model.Like], filter: AtomicReference[Pattern]) extends ViewerFilter {
    override def select(viewer: Viewer, parentElement: AnyRef, element: AnyRef): Boolean = {
      val pattern = filter.get
      val item = element.asInstanceOf[view.api.Filter.Rule]
      pattern.matcher(item.property.name.toLowerCase()).matches() ||
        pattern.matcher(PropertyType.get(item.propertyType).name(graph).toLowerCase()).matches() ||
        pattern.matcher(AvailableFilters.map(item.filter).name.toLowerCase()).matches() ||
        pattern.matcher(AvailableFilters.map.get(item.filter).flatMap(filter ⇒
          filter.stringToArgument(item.argument).map(filter.generic.argumentToText)).getOrElse(item.argument).toLowerCase()).matches()
    }
  }
  class FilterSelectionAdapter(dialog: WeakReference[FilterEditor], column: Int) extends SelectionAdapter {
    override def widgetSelected(e: SelectionEvent) = dialog.get foreach { dialog ⇒
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
  class PropertyFilter(graph: Graph[_ <: Model.Like], filter: AtomicReference[Pattern]) extends ViewerFilter {
    override def select(viewer: Viewer, parentElement: AnyRef, element: AnyRef): Boolean = {
      val pattern = filter.get
      val item = element.asInstanceOf[PropertyItem[AnySRef]]
      pattern.matcher(item.id.name.toLowerCase()).matches() || pattern.matcher(item.ptype.name(graph).toLowerCase()).matches()
    }
  }
  case class PropertyItem[T <: AnySRef](val id: Symbol, ptype: papi.PropertyType[T], var visible: Boolean)
  class PropertySelectionAdapter(dialog: WeakReference[FilterEditor], column: Int) extends SelectionAdapter {
    override def widgetSelected(e: SelectionEvent) = dialog.get foreach { dialog ⇒
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
}
