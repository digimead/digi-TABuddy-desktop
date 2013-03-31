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

package org.digimead.tabuddy.desktop.ui.view.table

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import java.util.Date

import scala.Array.canBuildFrom
import scala.Option.option2Iterable
import scala.collection.immutable
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future
import scala.ref.WeakReference

import com.ibm.icu.text.DateFormat

import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.tabuddy.desktop.Data
import org.digimead.tabuddy.desktop.Main
import org.digimead.tabuddy.desktop.payload.PropertyType
import org.digimead.tabuddy.desktop.payload.TemplateProperty
import org.digimead.tabuddy.desktop.payload.view.Sorting
import org.digimead.tabuddy.desktop.res.Messages
import org.digimead.tabuddy.desktop.support.TreeProxy
import org.digimead.tabuddy.desktop.support.WritableList
import org.digimead.tabuddy.desktop.ui.Default
import org.digimead.tabuddy.desktop.ui.ShellContext
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.element.Stash
import org.eclipse.core.databinding.observable.list.{ WritableList => OriginalWritableList }
import org.eclipse.jface.action.Action
import org.eclipse.jface.action.IAction
import org.eclipse.jface.action.IMenuListener
import org.eclipse.jface.action.IMenuManager
import org.eclipse.jface.action.MenuManager
import org.eclipse.jface.action.Separator
import org.eclipse.jface.databinding.viewers.ObservableListContentProvider
import org.eclipse.jface.viewers.CellLabelProvider
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.jface.viewers.StructuredSelection
import org.eclipse.jface.viewers.TableViewer
import org.eclipse.jface.viewers.TableViewerColumn
import org.eclipse.jface.viewers.Viewer
import org.eclipse.jface.viewers.ViewerCell
import org.eclipse.jface.viewers.ViewerComparator
import org.eclipse.jface.viewers.ViewerFilter
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.SashForm
import org.eclipse.swt.events.PaintEvent
import org.eclipse.swt.events.PaintListener
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.graphics.Font
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Shell

class Table(view: TableView, style: Int) extends Loggable {
  /** The auto resize lock */
  protected val autoResizeLock = new ReentrantLock()
  /** Per shell context */
  protected[table] val context = Table.perShellContextInitialize(view)
  /** The instance of filter that drops empty table rows */
  protected lazy val filterEmptyElements = new Table.FilterEmptyElements(view)
  /** On active listener flag */
  protected val onActiveCalled = new AtomicBoolean(false)
  /** On active listener */
  protected val onActiveListener = new Table.OnActiveListener(new WeakReference(this))
  /** The actual table content. */
  protected[table] lazy val content = WritableList[TreeProxy.Item]
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
      Main.execNGet {
        if (!tableViewer.getTable.isDisposed())
          TableView.withRedrawDelayed(view) { autoresizeUpdateControls() }
      }
    } finally {
      autoResizeLock.unlock()
    }
  /** Auto resize control updater */
  protected def autoresizeUpdateControls() {
    if (TableView.ActionToggleIdentificators.isChecked())
      context.tableViewerColumns.dropRight(1).foreach(adjustColumnWidth(_, Default.columnPadding))
    else
      context.tableViewerColumns.tail.dropRight(1).foreach(adjustColumnWidth(_, Default.columnPadding))
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
          Data.fieldElement.value = selection.getFirstElement().asInstanceOf[TreeProxy.Item].element
        case selection =>
      }
    })
    table.addPaintListener(onActiveListener)
    // update id column width
    if (context.saveWidthColumnID == 0) {
      context.saveWidthColumnID = table.getColumn(0).getWidth()
      table.getColumn(0).setWidth(0)
      table.getColumn(0).setResizable(false)
    }
    tableViewer.setInput(content.underlying)
    tableViewer
  }
  /** Create table viewer column */
  protected def createColumn(text: String, id: String, width: Option[Int], labelProvider: CellLabelProvider): TableViewerColumn = {
    val tableViewerColumn = new TableViewerColumn(tableViewer, SWT.LEFT)
    tableViewerColumn.setLabelProvider(labelProvider)
    val column = tableViewerColumn.getColumn
    column.setText(text)
    width match {
      case Some(width) => column.setWidth(width)
      case None => column.pack()
    }
    tableViewerColumn
  }
  /** Create table viewer columns */
  protected[table] def createColumns(columns: mutable.LinkedHashSet[String], columnsWidth: Map[String, Int]) {
    log.debug("create columns " + columns.mkString(","))
    context.tableViewerColumns = columns.map { columnId =>
      Table.columnLabelProvider.get(columnId) match {
        case Some(labelProvider: Table.TableLabelProviderID) =>
          Some(createColumn(Messages.identificator_text, TableView.COLUMN_ID, columnsWidth.get(columnId), labelProvider))
        case Some(labelProvider) =>
          if (columnId == TableView.COLUMN_NAME)
            Some(createColumn(Messages.name_text, columnId, columnsWidth.get(columnId), labelProvider))
          else
            Some(createColumn(columnId, columnId, columnsWidth.get(columnId), labelProvider))
        case None =>
          log.fatal("unknown column " + columnId)
          None
      }
    }.flatten
  }
  /** Create context menu for table element */
  protected def createMenu(manager: IMenuManager, selection: IStructuredSelection) {
    Option(selection.getFirstElement().asInstanceOf[TreeProxy.Item]) match {
      case Some(item) =>
        // tree menu
        if (view.context.ActionHideTree.isChecked()) {
          manager.add(context.ActionShowTree)
        } else {
          val treeMenu = new MenuManager(Messages.tree_text, null)
          treeMenu.add({
            val action = new context.ActionSelectInTree(item.element)
            val treeSelection = view.tree.treeViewer.getSelection().asInstanceOf[IStructuredSelection]
            action.setEnabled(treeSelection.getFirstElement() != selection.getFirstElement())
            action
          })
          treeMenu.add(new Separator)
          treeMenu.add(view.tree.context.ActionHideTree)
          manager.add(treeMenu)
        }
        // main menu
        manager.add(new Separator)
      case None =>
    }
    manager.add(context.ActionAutoResize)
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
}

class TablePerShellContext(val view: WeakReference[TableView]) extends Table.PerShellContext

object Table extends ShellContext[TableView, TablePerShellContext] with Loggable {
  /** Current model complete column/template map (id -> [ElementTemplate.id -> TemplateProperty]) */
  protected[table] var columnTemplate = immutable.HashMap[String, immutable.HashMap[Symbol, TemplateProperty[_ <: AnyRef with java.io.Serializable]]]()
  /** Current model complete column/label provider map */
  protected[table] var columnLabelProvider = immutable.HashMap[String, Table.TableLabelProvider]()

  /** Toggle the visibility of Id column */
  def toggleColumnId(show: Boolean, shell: Shell) = withContext(shell) { (context, view) =>
    if (show)
      context.tableViewerColumns.headOption.foreach { column =>
        if (context.saveWidthColumnID <= 0)
          view.table.adjustColumnWidth(column, Default.columnPadding)
        else
          column.getColumn.setWidth(context.saveWidthColumnID)
        column.getColumn.setResizable(true)
      }
    else
      context.tableViewerColumns.headOption.foreach { column =>
        context.saveWidthColumnID = column.getColumn.getWidth()
        column.getColumn.setWidth(0)
        column.getColumn.setResizable(false)
      }
  }
  /** Toggle the visibility of empty rows */
  def toggleEmptyRows(hideEmpty: Boolean, shell: Shell) = withContext(shell) { (context, view) =>
    val filters = view.table.tableViewer.getFilters()
    if (hideEmpty) {
      if (!filters.contains(view.table.filterEmptyElements))
        view.table.tableViewer.setFilters(filters :+ view.table.filterEmptyElements)
    } else {
      if (filters.contains(view.table.filterEmptyElements))
        view.table.tableViewer.setFilters(filters.filterNot(_ == view.table.filterEmptyElements))
    }
  }

  /** Create new instance of PerShellContext */
  protected def perShellContextNewInstance(dialogOrViewOrOtherControl: WeakReference[TableView]): TablePerShellContext =
    new TablePerShellContext(dialogOrViewOrOtherControl)

  /** Filter empty rows from table (id row is not takes into consideration) */
  class FilterEmptyElements(view: TableView) extends ViewerFilter {
    override def select(viewer: Viewer, parentElement: Object, element: Object): Boolean =
      !isEmpty(element.asInstanceOf[TreeProxy.Item].element)
    protected def isEmpty(element: Element.Generic) = {
      view.table.context.tableViewerColumns.tail.forall { column =>
        column.getColumn.getText() match {
          case id if id == Messages.name_text =>
            Table.columnLabelProvider(TableView.COLUMN_NAME).isEmpty(element)
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
  /*class TableComparator extends ViewerComparator {
    private var _column = Table.sortingColumn
    private var _direction = Table.sortingDirection

    /** Active column getter */
    def column = _column
    /** Active column setter */
    def column_=(arg: Int) {
      _column = arg
      Table.sortingColumn = _column
      _direction = Default.sortingDirection
      Table.sortingDirection = _direction
    }
    /** Sorting direction */
    def direction = _direction
    /**
     * Returns a negative, zero, or positive number depending on whether
     * the first element is less than, equal to, or greater than
     * the second element.
     */
    override def compare(viewer: Viewer, e1: Object, e2: Object): Int = {
      val element1 = e1.asInstanceOf[Element.Generic]
      val element2 = e2.asInstanceOf[Element.Generic]
      val rc = column match {
        case 0 =>
          /*
           * 0 is always id column
           * if there is user sorting - return unmodified order
           * if there is default sorting - sort by column 1
           */
          Option(ToolbarView.sorting) match {
            case Some(sorting) if sorting != Sorting.default =>
              log.___glance("skip")
              0
            case _ =>
              val iterator = Table.tableViewerColumns.iterator
              iterator.next // skip id column
              val column = iterator.next
              val data = column.getColumn().getData()
              log.___glance("!!!DATA " + data)

              /*
                    .forall { column =>
        column.getColumn.getText() match {
          case id if id == Messages.name_text =>
            table.columnLabelProvider(table.COLUMN_NAME).isEmpty(element)
          case columnId =>
            table.columnLabelProvider(columnId).isEmpty(element)
        }*/
              1
          }

        case 1 => 0
        //translation1.getValue().compareTo(translation2.getValue())
        case index =>
          log.fatal(s"unknown column with index $index"); 0
      }
      if (_direction) -rc else rc
    }
    /** Switch comparator direction */
    def switchDirection() {
      _direction = !_direction
      Table.sortingDirection = _direction
    }
  }*/
  class TableLabelProvider(propertyId: String, propertyMap: immutable.HashMap[Symbol, TemplateProperty[_ <: AnyRef with java.io.Serializable]]) extends CellLabelProvider {
    override def update(cell: ViewerCell) = cell.getElement() match {
      case item: TreeProxy.Item =>
        propertyMap.get(item.element.eScope.modificator).foreach { property =>
          val value = item.element.eGet(property.id, property.ptype.typeSymbol).map(_.get)
          // as common unknown type
          property.ptype.adapter.cellLabelProvider.asInstanceOf[PropertyType.CellLabelProviderAdapter[AnyRef with java.io.Serializable]].
            update(cell, value)
        }
      case unknown =>
        log.fatal("Unknown item '%s' with type '%s'".format(unknown, unknown.getClass()))
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
        property.ptype.adapter.labelProvider.asInstanceOf[PropertyType.LabelProviderAdapter[AnyRef with java.io.Serializable]].
          getText(value).isEmpty()
      } getOrElse (true)
    }

    /** Call the specific CellLabelProviderAdapter Fn with element argument */
    protected def withElement[T](element: AnyRef)(f: (PropertyType.CellLabelProviderAdapter[_], AnyRef) => T): Option[T] = element match {
      case item: TreeProxy.Item =>
        propertyMap.get(item.element.eScope.modificator) match {
          case Some(property) =>
            item.element.eGet(property.id, property.ptype.typeSymbol).map(_.get) match {
              case Some(value) if value.getClass() == property.ptype.typeClass =>
                Some(f(property.ptype.adapter.cellLabelProvider, value))
              case _ =>
                Some(f(property.ptype.adapter.cellLabelProvider, null))
            }
          case None =>
            None
        }
      case unknown =>
        log.fatal("Unknown item '%s' with type '%s'".format(unknown, unknown.getClass()))
        None
    }
  }
  class TableLabelProviderID() extends TableLabelProvider(TableView.COLUMN_ID, immutable.HashMap()) {
    val dfg = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL)

    /** Update the label for cell. */
    override def update(cell: ViewerCell) = cell.getElement() match {
      case item: TreeProxy.Item =>
        cell.setText(item.element.eId.name)
      case unknown =>
        log.fatal("Unknown item '%s' with type '%s'".format(unknown, unknown.getClass()))
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
  /** Per shell context for with Table routines */
  sealed trait PerShellContext extends ShellContext.PerShellContext[TableView] {
    /** Actual sorting direction. */
    @volatile private var sortingDirection = Default.sortingDirection
    /** Actual sortBy column index. */
    @volatile private var sortingColumn = 0
    /** Table viewer column's instances. */
    protected[Table] var tableViewerColumns = mutable.LinkedHashSet[TableViewerColumn]()
    /** The property that contains id column width. */
    protected[Table] var saveWidthColumnID = -1

    /*
     * Actions
     */
    object ActionAutoResize extends Action(Messages.autoresize_key, IAction.AS_CHECK_BOX) {
      setChecked(true)
      def apply(immediately: Boolean = false) = view.get.foreach(view => if (immediately)
        view.table.autoresize(true)
      else
        future { try { view.table.autoresize(false) } catch { case e: Throwable => log.error(e.getMessage, e) } })
      override def run = if (isChecked()) apply()
    }
    class ActionSelectInTree(val element: Element.Generic) extends Action(Messages.select_text) {
      def apply() = view.get.foreach(v => v.tree.treeViewer.setSelection(new StructuredSelection(TreeProxy.Item(element)), true))
      override def run() = apply()
    }
    object ActionShowTree extends Action(Messages.tree_text) {
      def apply() = view.get.foreach { v =>
        v.context.ActionHideTree.setChecked(false)
        v.context.ActionHideTree()
      }
      override def run() = apply()
    }
  }
}
