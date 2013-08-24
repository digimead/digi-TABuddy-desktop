/**
 * This file is part of the TABuddy project.
 * Copyright (c) 2013 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.editor.view.editor

import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

import scala.Array.canBuildFrom
import scala.Option.option2Iterable
import scala.collection.immutable
import scala.collection.mutable
import scala.collection.parallel
import scala.language.reflectiveCalls
import scala.ref.WeakReference
import scala.reflect.runtime.universe

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Messages
import org.digimead.tabuddy.desktop.editor.Default
import org.digimead.tabuddy.desktop.logic.Data
import org.digimead.tabuddy.desktop.logic.payload.PropertyType
import org.digimead.tabuddy.desktop.logic.payload.api.TemplateProperty
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.TreeProxy
import org.digimead.tabuddy.desktop.support.WritableList
import org.digimead.tabuddy.model.element.Element
import org.eclipse.jface.action.IMenuListener
import org.eclipse.jface.action.IMenuManager
import org.eclipse.jface.action.MenuManager
import org.eclipse.jface.action.Separator
import org.eclipse.jface.databinding.viewers.ObservableListContentProvider
import org.eclipse.jface.viewers.CellLabelProvider
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport
import org.eclipse.jface.viewers.ILabelProvider
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.jface.viewers.TableViewer
import org.eclipse.jface.viewers.TableViewerColumn
import org.eclipse.jface.viewers.Viewer
import org.eclipse.jface.viewers.ViewerCell
import org.eclipse.jface.viewers.ViewerComparator
import org.eclipse.jface.viewers.ViewerFilter
import org.eclipse.swt.SWT
import org.eclipse.swt.events.DisposeEvent
import org.eclipse.swt.events.DisposeListener
import org.eclipse.swt.events.PaintEvent
import org.eclipse.swt.events.PaintListener
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.graphics.Font
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.graphics.Point

import com.ibm.icu.text.DateFormat

class Table(protected[editor] val view: View, style: Int)
  extends TableActions with TableFields with Loggable {
  /** The auto resize lock */
  protected val autoResizeLock = new ReentrantLock()
  /** The actual table content. */
  protected[editor] lazy val content = WritableList[TreeProxy.Item]
  /** The instance of filter that drops empty table rows */
  protected lazy val filterEmptyElements = new Table.FilterEmptyElements(view)
  /** On active listener flag */
  protected val onActiveCalled = new AtomicBoolean(false)
  /** On active listener */
  protected val onActiveListener = new Table.OnActiveListener(new WeakReference(this))
  /** The table viewer */
  val tableViewer = create()
  /** The table viewer update, scala bug SI-2991 */
  val structuredViewerUpdateF = {
    // scala bug SI-2991
    import scala.language.reflectiveCalls
    (tableViewer: { def update(a: Array[AnyRef], b: Array[String]): Unit }).update(_, _)
  }

  /** Adjust column width */
  protected def adjustColumnWidth(viewerColumn: TableViewerColumn, padding: Int) {
    val bounds = viewerColumn.getViewer.getControl.getBounds()
    val column = viewerColumn.getColumn()
    column.pack()
    column.setWidth(math.min(column.getWidth() + padding, bounds.width / 3))
  }
  /** Auto resize table viewer columns */
  protected def autoresize(immediately: Boolean) = if (onActiveCalled.get)
    if (immediately)
      autoresizeUpdateControls()
    else if (autoResizeLock.tryLock()) try {
      Thread.sleep(50)
      App.execNGet {
        if (!tableViewer.getTable.isDisposed())
          View.withRedrawDelayed(view) { autoresizeUpdateControls() }
      }
    } finally {
      autoResizeLock.unlock()
    }
  /** Auto resize control updater */
  protected def autoresizeUpdateControls() {
    if (view.ActionToggleIdentificators.isChecked())
      tableViewerColumns.dropRight(1).foreach(adjustColumnWidth(_, Default.columnPadding))
    else
      tableViewerColumns.tail.dropRight(1).foreach(adjustColumnWidth(_, Default.columnPadding))
    tableViewer.refresh()
  }
  /** Create contents of the table. */
  protected def create(): TableViewer = {
    log.debug("create table")
    val tableViewer = new TableViewer(view.getSashForm, style)
    tableViewer.getTable.setHeaderVisible(true)
    tableViewer.getTable.setLinesVisible(true)
    tableViewer.setContentProvider(new ObservableListContentProvider())
    val table = tableViewer.getTable()
    table.setData(view)
    // Activate the tooltip support for the viewer
    ColumnViewerToolTipSupport.enableFor(tableViewer)
    // Add the context menu
    val menuMgr = new MenuManager()
    val menu = menuMgr.createContextMenu(tableViewer.getControl)
    menuMgr.addMenuListener(new IMenuListener() {
      override def menuAboutToShow(manager: IMenuManager) {
        val selection = tableViewer.getSelection.asInstanceOf[IStructuredSelection]
        createMenu(manager, selection)
      }
    })
    menuMgr.setRemoveAllWhenShown(true)
    tableViewer.getControl.setMenu(menu)
    // Add selection listener
    tableViewer.addSelectionChangedListener(new ISelectionChangedListener() {
      override def selectionChanged(event: SelectionChangedEvent) = event.getSelection() match {
        case selection: IStructuredSelection if !selection.isEmpty() =>
          view.setSelectedElement(selection.getFirstElement().asInstanceOf[TreeProxy.Item].element)
        case selection =>
      }
    })
    table.addPaintListener(onActiveListener)
    // update id column width
    if (saveWidthColumnID == 0) {
      saveWidthColumnID = table.getColumn(0).getWidth()
      table.getColumn(0).setWidth(0)
      table.getColumn(0).setResizable(false)
    }
    tableViewer.setComparator(new Table.TableComparator(-1, Default.sortingDirection, new WeakReference(this)))
    tableViewer.setFilters(Array(new Table.TableFilter(view)))
    content.addChangeListener { (_) =>
      // reset sorting results on content change
      tableViewer.getComparator.asInstanceOf[Table.TableComparator].sorted = None
    }
    tableViewer.setInput(content.underlying)
    // Add the dispose listener
    table.addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) {
        table.setData(null)
      }
    })
    tableViewer
  }
  /** Create table viewer column */
  protected def createColumn(text: String, id: String, width: Option[Int], labelProvider: CellLabelProvider): TableViewerColumn = {
    val tableViewerColumn = new TableViewerColumn(tableViewer, SWT.LEFT)
    tableViewerColumn.setLabelProvider(labelProvider)
    val column = tableViewerColumn.getColumn
    column.setText(text)
    column.setData(id)
    width match {
      case Some(width) => column.setWidth(width)
      case None => column.pack()
    }
    tableViewerColumn
  }
  /** Create table viewer columns */
  protected[editor] def createColumns(columns: mutable.LinkedHashSet[String], columnsWidth: Map[String, Int]) {
    log.debug("create columns " + columns.mkString(","))
    var pos = 0
    tableViewerColumns = columns.map { columnId =>
      Table.columnLabelProvider.get(columnId) match {
        case Some(labelProvider: Table.TableLabelProviderID) =>
          val column = createColumn(Messages.identificator_text, View.COLUMN_ID, columnsWidth.get(columnId), labelProvider)
          column.getColumn.addSelectionListener(new Table.TableSelectionAdapter(pos))
          pos += 1
          Some(column)
        case Some(labelProvider) =>
          val column = if (columnId == View.COLUMN_NAME)
            createColumn(Messages.name_text, columnId, columnsWidth.get(columnId), labelProvider)
          else
            createColumn(columnId, columnId, columnsWidth.get(columnId), labelProvider)
          column.getColumn.addSelectionListener(new Table.TableSelectionAdapter(pos))
          pos += 1
          Some(column)
        case None =>
          log.fatal("unknown column " + columnId)
          None
      }
    }.flatten
  }
  /** Create context menu for table element */
  protected def createMenu(manager: IMenuManager, selection: IStructuredSelection) {
    val item = Option(selection.getFirstElement().asInstanceOf[TreeProxy.Item])
    // tree menu
    if (view.ActionHideTree.isChecked()) {
      manager.add(ActionShowTree)
    } else {
      val treeMenu = new MenuManager(Messages.tree_text, null)
      treeMenu.add({
        val action = new ActionSelectInTree(item.map(_.element).getOrElse(null))
        if (item.nonEmpty) {
          val treeSelection = view.tree.treeViewer.getSelection().asInstanceOf[IStructuredSelection]
          action.setEnabled(treeSelection.getFirstElement() != selection.getFirstElement())
        } else {
          action.setEnabled(false)
        }
        action
      })
      treeMenu.add(new Separator)
      treeMenu.add(view.tree.ActionHideTree)
      manager.add(treeMenu)
    }
    // main menu
    manager.add(ActionConfigureColumns)
    manager.add(ActionResetSorting)
    manager.add(new Separator)
    manager.add(ActionAutoResize)
  }
  /** Dispose table viewer columns */
  def disposeColumns(): Map[String, Int] = {
    val table = tableViewer.getTable
    val existsColumnsWidth = immutable.HashMap(table.getColumns.map { column => (column.getText(), column.getWidth()) }: _*)
    while (table.getColumnCount > 0)
      table.getColumns.head.dispose()
    existsColumnsWidth
  }
  /** onActive callback */
  protected def onActive() {}
  protected[editor] def onSortingChanged() {
    tableViewer.getComparator.asInstanceOf[Table.TableComparator].sorted = None
    tableViewer.refresh()
  }
}

object Table extends Loggable {
  /** Current model complete column/template map (id -> [ElementTemplate.id -> TemplateProperty]) */
  protected[editor] var columnTemplate = immutable.HashMap[String, immutable.HashMap[Symbol, TemplateProperty[_ <: AnyRef with java.io.Serializable]]]()
  /** Current model complete column/label provider map */
  protected[editor] var columnLabelProvider = immutable.HashMap[String, Table.TableLabelProvider]()

  /** Toggle the visibility of Id column */
  def toggleColumnId(show: Boolean, view: View) = {
    if (show)
      view.table.tableViewerColumns.headOption.foreach { column =>
        if (view.table.saveWidthColumnID <= 0)
          view.table.adjustColumnWidth(column, Default.columnPadding)
        else
          column.getColumn.setWidth(view.table.saveWidthColumnID)
        column.getColumn.setResizable(true)
      }
    else
      view.table.tableViewerColumns.headOption.foreach { column =>
        view.table.saveWidthColumnID = column.getColumn.getWidth()
        column.getColumn.setWidth(0)
        column.getColumn.setResizable(false)
      }
  }
  /** Toggle the visibility of empty rows */
  def toggleEmptyRows(hideEmpty: Boolean, view: View) = {
    val filters = view.table.tableViewer.getFilters()
    if (hideEmpty) {
      if (!filters.contains(view.table.filterEmptyElements))
        view.table.tableViewer.setFilters(filters :+ view.table.filterEmptyElements)
    } else {
      if (filters.contains(view.table.filterEmptyElements))
        view.table.tableViewer.setFilters(filters.filterNot(_ == view.table.filterEmptyElements))
    }
  }

  /** Filter empty rows from table (id row is not takes into consideration) */
  class FilterEmptyElements(view: View) extends ViewerFilter {
    override def select(viewer: Viewer, parentElement: Object, element: Object): Boolean =
      !isEmpty(element.asInstanceOf[TreeProxy.Item].element)
    protected def isEmpty(element: Element.Generic) = {
      view.table.tableViewerColumns.tail.forall { column =>
        column.getColumn.getText() match {
          case id if id == Messages.name_text =>
            Table.columnLabelProvider(View.COLUMN_NAME).isEmpty(element)
          case columnId =>
            Table.columnLabelProvider(columnId).isEmpty(element)
        }
      }
    }
  }
  class OnActiveListener(table: WeakReference[Table]) extends PaintListener {
    /** Sent when a paint event occurs for the control. */
    def paintControl(e: PaintEvent) = table.get.foreach { table =>
      if (table.onActiveCalled.compareAndSet(false, true)) {
        table.tableViewer.getControl.removePaintListener(table.onActiveListener)
        table.onActive()
      }
    }
  }
  class TableComparator(initialColumn: Int, initialDirection: Boolean, table: WeakReference[Table]) extends ViewerComparator {
    /** The sort column */
    var columnVar = initialColumn
    /** The sort direction */
    var directionVar = initialDirection
    /** Sorted content by user defined sorter */
    protected[editor] var sorted: Option[parallel.immutable.ParVector[TreeProxy.Item]] = None
    updateActionResetSorting

    /** Active column getter */
    def column = columnVar
    /** Active column setter */
    def column_=(arg: Int) {
      columnVar = arg
      directionVar = initialDirection
      updateActionResetSorting
    }
    /** Sorting direction */
    def direction = directionVar
    /**
     * Returns a negative, zero, or positive number depending on whether
     * the first element is less than, equal to, or greater than
     * the second element.
     */
    override def compare(viewer: Viewer, e1: Object, e2: Object): Int = {
      val item1 = e1.asInstanceOf[TreeProxy.Item]
      val item2 = e2.asInstanceOf[TreeProxy.Item]
      val view = viewer.asInstanceOf[TableViewer].getTable().getData().asInstanceOf[View]
      val columnCount = viewer.asInstanceOf[TableViewer].getTable.getColumnCount()
      val rc: Int = if (column < 0) {
        /*sorted orElse {
          // sorted is empty, update sorted
          view.context.toolbarView.sorting.value match {
            case Some(sorting) =>
              val sortBy = sorting.definitions.toSeq.flatMap { definition =>
                val comparator = Comparator.map.get(definition.comparator): Option[Comparator.Interface[_ <: Comparator.Argument]]
                val propertyType = PropertyType.container.get(definition.propertyType): Option[PropertyType[_ <: AnyRef with java.io.Serializable]]
                val argument = comparator.flatMap(c => c.stringToArgument(definition.argument))
                comparator.flatMap(c => propertyType.map(ptype => (definition, ptype, c, argument)))
              }
              if (sortBy.nonEmpty) {
                var iteration = view.proxy.getContent.seq
                sortBy.foreach {
                  case ((definition, propertyType, comparator, argument)) =>
                    // iterate over sorting definitions
                    if (definition.direction)
                      iteration = iteration.sortWith { (a, b) =>
                        comparator.compare[AnyRef with java.io.Serializable](
                          definition.property,
                          propertyType.asInstanceOf[org.digimead.tabuddy.desktop.payload.PropertyType[AnyRef with java.io.Serializable]],
                          a.element, b.element,
                          argument.asInstanceOf[Option[Comparator.Argument]]) < 0
                      }
                    else
                      iteration = iteration.sortWith { (a, b) =>
                        comparator.compare[AnyRef with java.io.Serializable](
                          definition.property,
                          propertyType.asInstanceOf[org.digimead.tabuddy.desktop.payload.PropertyType[AnyRef with java.io.Serializable]],
                          a.element, b.element,
                          argument.asInstanceOf[Option[Comparator.Argument]]) >= 0
                      }
                }
                sorted = Some(iteration.par)
              } else {
                // if there are no sortBy elements (default sorting) return empty vector
                // so comparation will always -1 vs -1
                sorted = Some(parallel.immutable.ParVector[TreeProxy.Item]())
              }
              sorted
            case None =>
              None
          }
        } map { sorted =>
          sorted.indexOf(item1).compareTo(sorted.indexOf(item2))
        } getOrElse 0*/
        0
      } else if (column < columnCount) {
        val columnId = viewer.asInstanceOf[TableViewer].getTable.getColumn(column).getData().asInstanceOf[String]
        if (columnId == View.COLUMN_ID) {
          item1.element.eId.name.compareTo(item2.element.eId.name)
        } else {
          val lprovider = viewer.asInstanceOf[TableViewer].getLabelProvider(column).asInstanceOf[ILabelProvider]
          lprovider.getText(item1).compareTo(lprovider.getText(item2))
        }
      } else {
        log.fatal(s"unknown column with index $column"); 0
      }
      if (directionVar) -rc else rc
    }
    /** get element label */
    protected def getLabel(viewer: Viewer, e1: AnyRef, lprovider: ILabelProvider): String =
      Option(lprovider.getText(e1)).getOrElse("")
    /** Switch comparator direction */
    def switchDirection() {
      directionVar = !directionVar
    }
    /** Update ActionResetSorting state */
    def updateActionResetSorting() = {} /*table.get.foreach(table =>
      table.context.ActionResetSorting.setEnabled(columnVar != -1 || directionVar != initialDirection))*/
  }
  /** Filter that apply user rules */
  class TableFilter(view: View) extends ViewerFilter {
    override def select(viewer: Viewer, parentElement: Object, element: Object): Boolean = element match {
      case item: TreeProxy.Item =>
        val view = viewer.asInstanceOf[TableViewer].getTable.getData.asInstanceOf[View]
        val selectedFilter = Data.getSelectedViewFilter(view.getParent.getContext)
        selectedFilter match {
          case Some(filter) =>
            /*val filterBy = filter.rules.toSeq.flatMap { rule =>
              val filterInstance = Filter.map.get(rule.filter): Option[Filter.Interface[_ <: Filter.Argument]]
              val propertyType = PropertyType.container.get(rule.propertyType): Option[PropertyType[_ <: AnyRef with java.io.Serializable]]
              val argument = filterInstance.flatMap(f => f.stringToArgument(rule.argument))
              filterInstance.flatMap(f => propertyType.map(ptype => (rule, ptype, f, argument)))
            }
            filterBy.isEmpty || filterBy.forall {
              case ((rule, propertyType, filter, argument)) =>
                filter.generic.filter(rule.property, propertyType, item.element, argument)
            }*/
            true
          case None =>
            true
        }
      case unknown =>
        log.fatal("Unknown item '%s' with type '%s'".format(unknown, unknown.getClass()))
        true
    }
  }
  class TableLabelProvider(val propertyId: String, val propertyMap: immutable.HashMap[Symbol, TemplateProperty[_ <: AnyRef with java.io.Serializable]])
    extends CellLabelProvider with ILabelProvider {
    override def update(cell: ViewerCell) = cell.getElement() match {
      case item: TreeProxy.Item =>
        propertyMap.get(item.element.eScope.modificator).foreach { property =>
          val value = item.element.eGet(property.id, property.ptype.typeSymbol).map(_.get)
          // as common unknown type
          property.ptype.adapter.as[PropertyType.genericAdapter].cellLabelProvider.update(cell, value)
        }
      case unknown =>
        log.fatal("Unknown item '%s' with type '%s'".format(unknown, unknown.getClass()))
    }
    /**
     * Returns the image for the label of the given element.  The image
     * is owned by the label provider and must not be disposed directly.
     * Instead, dispose the label provider when no longer needed.
     *
     * @param element the element for which to provide the label image
     * @return the image used to label the element, or <code>null</code>
     *   if there is no image for the given object
     */
    def getImage(element: AnyRef): Image = element match {
      case item: TreeProxy.Item =>
        propertyMap.get(item.element.eScope.modificator).map { property =>
          val value = item.element.eGet(property.id, property.ptype.typeSymbol).map(_.get)
          // as common unknown type
          property.ptype.adapter.as[PropertyType.genericAdapter].labelProvider.getImage(value)
        } getOrElse null
      case unknown =>
        log.fatal("Unknown item '%s' with type '%s'".format(unknown, unknown.getClass()))
        null
    }
    /**
     * Returns the text for the label of the given element.
     *
     * @param element the element for which to provide the label text
     * @return the text string used to label the element, or <code>null</code>
     *   if there is no text label for the given object
     */
    def getText(element: AnyRef): String = element match {
      case item: TreeProxy.Item =>
        propertyMap.get(item.element.eScope.modificator).map { property =>
          val value = item.element.eGet(property.id, property.ptype.typeSymbol).map(_.get)
          // as common unknown type
          property.ptype.adapter.as[PropertyType.genericAdapter].labelProvider.getText(value)
        } getOrElse ""
      case unknown =>
        log.fatal("Unknown item '%s' with type '%s'".format(unknown, unknown.getClass()))
        ""
    }
    override def getToolTipBackgroundColor(element: AnyRef): Color =
      withElement(element)((adapter, element) => adapter.getToolTipBackgroundColor(element)).getOrElse(super.getToolTipBackgroundColor(element))
    override def getToolTipDisplayDelayTime(element: AnyRef): Int =
      withElement(element)((adapter, element) => adapter.getToolTipDisplayDelayTime(element)).getOrElse(super.getToolTipDisplayDelayTime(element))
    override def getToolTipFont(element: AnyRef): Font =
      withElement(element)((adapter, element) => adapter.getToolTipFont(element)).getOrElse(super.getToolTipFont(element))
    override def getToolTipForegroundColor(element: AnyRef): Color =
      withElement(element)((adapter, element) => adapter.getToolTipForegroundColor(element)).getOrElse(super.getToolTipForegroundColor(element))
    override def getToolTipImage(element: AnyRef): Image =
      withElement(element)((adapter, element) => adapter.getToolTipImage(element)).getOrElse(super.getToolTipImage(element))
    override def getToolTipShift(element: AnyRef): Point =
      withElement(element)((adapter, element) => adapter.getToolTipShift(element)).getOrElse(super.getToolTipShift(element))
    override def getToolTipText(element: AnyRef): String =
      withElement(element)((adapter, element) => adapter.getToolTipText(element)).getOrElse(super.getToolTipText(element))
    override def getToolTipTimeDisplayed(element: AnyRef): Int =
      withElement(element)((adapter, element) => adapter.getToolTipTimeDisplayed(element)).getOrElse(super.getToolTipTimeDisplayed(element))
    override def getToolTipStyle(element: AnyRef): Int =
      withElement(element)((adapter, element) => adapter.getToolTipStyle(element)).getOrElse(super.getToolTipStyle(element))

    /** Returns whether property text is empty */
    def isEmpty(element: Element.Generic) = {
      propertyMap.get(element.eScope.modificator).map { property =>
        val value = element.eGet(property.id, property.ptype.typeSymbol).map(_.get)
        // as common unknown type
        property.ptype.adapter.as[PropertyType.genericAdapter].labelProvider.getText(value).isEmpty()
      } getOrElse (true)
      true
    }

    /** Call the specific CellLabelProviderAdapter Fn with element argument */
    protected def withElement[T](element: AnyRef)(f: (PropertyType.CellLabelProviderAdapter[_], AnyRef) => T): Option[T] = element match {
      case item: TreeProxy.Item =>
        propertyMap.get(item.element.eScope.modificator) match {
          case Some(property) =>
            item.element.eGet(property.id, property.ptype.typeSymbol).map(_.get) match {
              case Some(value) if value.getClass() == property.ptype.typeClass =>
                Some(f(property.ptype.adapter.as[PropertyType.genericAdapter].cellLabelProvider, value))
              case _ =>
                Some(f(property.ptype.adapter.as[PropertyType.genericAdapter].cellLabelProvider, null))
            }
          case None =>
            None
        }
      case unknown =>
        log.fatal("Unknown item '%s' with type '%s'".format(unknown, unknown.getClass()))
        None
    }
  }
  class TableLabelProviderID() extends TableLabelProvider(View.COLUMN_ID, immutable.HashMap()) {
    val dfg = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL)

    /** Update the label for cell. */
    override def update(cell: ViewerCell) = cell.getElement() match {
      case item: TreeProxy.Item =>
        cell.setText(item.element.eId.name)
      case unknown =>
        log.fatal("Unknown item '%s' with type '%s'".format(unknown, unknown.getClass()))
    }
    /**
     * Returns the image for the label of the given element.  The image
     * is owned by the label provider and must not be disposed directly.
     * Instead, dispose the label provider when no longer needed.
     *
     * @param element the element for which to provide the label image
     * @return the image used to label the element, or <code>null</code>
     *   if there is no image for the given object
     */
    override def getImage(element: AnyRef): Image = null
    /**
     * Returns the text for the label of the given element.
     *
     * @param element the element for which to provide the label text
     * @return the text string used to label the element, or <code>null</code>
     *   if there is no text label for the given object
     */
    override def getText(element: AnyRef): String = element match {
      case item: TreeProxy.Item =>
        item.element.eId.name
      case unknown =>
        log.fatal("Unknown item '%s' with type '%s'".format(unknown, unknown.getClass()))
        null
    }
    /** Get the text displayed in the tool tip for object. */
    override def getToolTipText(obj: Object): String = obj match {
      case item: TreeProxy.Item =>
        Messages.lastModification_text.format(dfg.format(new Date(item.element.eModified.milliseconds)))
      case unknown =>
        log.fatal("Unknown item '%s' with type '%s'".format(unknown, unknown.getClass()))
        null
    }
    /** The time in milliseconds until the tool tip is displayed. */
    override def getToolTipDisplayDelayTime(obj: Object): Int = Default.toolTipDisplayDelayTime
    /**
     * Return the amount of pixels in x and y direction that the tool tip to
     * pop up from the mouse pointer.
     */
    override def getToolTipShift(obj: Object): Point = Default.toolTipShift
    /** The time in milliseconds the tool tip is shown for. */
    override def getToolTipTimeDisplayed(obj: Object): Int = Default.toolTipTimeDisplayed
    /** Returns whether property text is empty */
    override def isEmpty(element: Element.Generic) = false
  }
  class TableSelectionAdapter(column: Int) extends SelectionAdapter {
    override def widgetSelected(e: SelectionEvent) = {} /*App.findShell(e.widget).foreach(withContext(_) { (context, view) =>
      val comparator = view.table.tableViewer.getComparator().asInstanceOf[TableComparator]
      if (comparator.column == column) {
        comparator.switchDirection()
        view.table.tableViewer.refresh()
      } else {
        comparator.column = column
        view.table.tableViewer.refresh()
      }
    })*/
  }
}
