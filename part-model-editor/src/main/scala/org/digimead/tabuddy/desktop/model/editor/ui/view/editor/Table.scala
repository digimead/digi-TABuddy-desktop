/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2013-2015 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.model.editor.ui.view.editor

import com.google.common.collect.Lists
import com.ibm.icu.text.DateFormat
import java.util.{ Date, UUID }
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.{ Messages ⇒ CMessages }
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.WritableList
import org.digimead.tabuddy.desktop.core.support.WritableValue
import org.digimead.tabuddy.desktop.core.ui.support.TreeProxy
import org.digimead.tabuddy.desktop.logic.comparator.AvailableComparators
import org.digimead.tabuddy.desktop.logic.comparator.api.XComparator
import org.digimead.tabuddy.desktop.logic.filter.AvailableFilters
import org.digimead.tabuddy.desktop.logic.filter.api.XFilter
import org.digimead.tabuddy.desktop.logic.payload.{ ElementTemplate, PropertyType, TemplateProperty }
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.desktop.logic.payload.view.{ Filter, Sorting, View ⇒ ModelView }
import org.digimead.tabuddy.desktop.logic.payload.view.api.{ XFilter ⇒ XViewFilter, XSorting }
import org.digimead.tabuddy.desktop.model.editor.{ AnySRef, Default, Messages }
import org.digimead.tabuddy.model.element.Element
import org.eclipse.core.databinding.observable.Observables
import org.eclipse.core.databinding.observable.value.{ IValueChangeListener, ValueChangeEvent }
import org.eclipse.jface.action.{ IMenuListener, IMenuManager, MenuManager, Separator }
import org.eclipse.jface.databinding.viewers.ObservableListContentProvider
import org.eclipse.jface.viewers.{ CellLabelProvider, ColumnViewerToolTipSupport, ILabelProvider, ISelectionChangedListener, IStructuredSelection, SelectionChangedEvent, TableViewer, TableViewerColumn, Viewer, ViewerCell, ViewerComparator, ViewerFilter }
import org.eclipse.swt.SWT
import org.eclipse.swt.events.{ DisposeEvent, DisposeListener, PaintEvent, PaintListener, SelectionAdapter, SelectionEvent }
import org.eclipse.swt.graphics.{ Color, Font, Image, Point }
import scala.annotation.migration
import scala.collection.{ immutable, mutable, parallel }
import scala.collection.JavaConverters.{ asJavaIteratorConverter, seqAsJavaListConverter }
import scala.concurrent.Future
import scala.language.reflectiveCalls
import scala.ref.WeakReference

class Table(protected[editor] val content: Content, style: Int)
    extends TableActions with TableFields with XLoggable {
  /** The auto resize lock */
  protected val autoResizeLock = new ReentrantLock()
  /** The actual table content. */
  protected[editor] lazy val proxyContent = WritableList[TreeProxy.Item]
  /** The sorted table content. */
  protected[editor] lazy val proxyContentSorted = WritableList[TreeProxy.Item]
  /** The table content sorter. */
  protected[editor] lazy val proxyContentSorter = new Table.ContentSorter(proxyContent, proxyContentSorted, 100)
  /** The instance of a filter that drops empty table rows. */
  protected lazy val filterEmptyElements = new Table.FilterEmptyElements(WeakReference(content))
  /** The instance of a filter that apply user rules to data. */
  protected lazy val filter = new Table.FilterWithUserDefinedRules(WeakReference(content))
  /** On active listener flag */
  protected val onActiveCalled = new AtomicBoolean(false)
  /** On active listener */
  protected val onActiveListener = new Table.OnActiveListener(new WeakReference(this))
  /** The instance of a comparator that apply user defined sortings. */
  protected lazy val sorting = new Table.TableComparator(-1, Default.sortingDirection, new WeakReference(this))
  /** The table viewer */
  val tableViewer = create()
  /** The table viewer update, scala bug SI-2991 */
  val structuredViewerUpdateF = {
    // scala bug SI-2991
    import scala.language.reflectiveCalls
    (tableViewer: { def update(a: Array[AnyRef], b: Array[String]): Unit }).update(_, _)
  }
  /** Current model complete column/label provider map */
  protected var columnLabelProvider = immutable.HashMap[String, Table.TableLabelProvider]()
  /** Current model complete column/template map (id -> [ElementTemplate.id -> TemplateProperty]) */
  protected var columnTemplate = immutable.HashMap[String, immutable.HashMap[Symbol, TemplateProperty[_ <: AnyRef with java.io.Serializable]]]()

  /** Dispose table viewer columns. */
  def disposeColumns(): Map[String, Int] = {
    val table = tableViewer.getTable
    val existsColumnsWidth = immutable.HashMap(table.getColumns.map { column ⇒ (column.getText(), column.getWidth()) }: _*)
    while (table.getColumnCount > 0)
      table.getColumns.head.dispose()
    existsColumnsWidth
  }
  /** Create enumeration of column templates and column label providers. */
  def enumerateColumns(marker: GraphMarker) {
    log.debug("Enumerate table columns for " + marker)
    val (properties, propertyCache) = marker.safeRead { state ⇒
      val propertyCache = mutable.HashMap[TemplateProperty[_ <: AnyRef with java.io.Serializable], Seq[ElementTemplate]]()
      val properties = state.payload.elementTemplates.values.flatMap { template ⇒
        template.properties.foreach {
          case (group, properties) ⇒ properties.foreach(property ⇒
            propertyCache(property) = propertyCache.get(property).getOrElse(Seq()) :+ template)
        }
        template.properties.flatMap(_._2)
      }.toList.groupBy(_.id.name.toLowerCase())
      (properties, propertyCache)
    }
    val columnIds = List(Content.COLUMN_ID, Content.COLUMN_NAME) ++ properties.keys.filterNot(_ == Content.COLUMN_NAME).toSeq.sorted
    val columnTemplate = immutable.HashMap((for (columnId ← columnIds) yield columnId match {
      case id if id == Content.COLUMN_ID ⇒
        (Content.COLUMN_ID, immutable.HashMap[Symbol, TemplateProperty[_ <: AnyRef with java.io.Serializable]]())
      case id if id == Content.COLUMN_NAME ⇒
        (Content.COLUMN_NAME, immutable.HashMap(properties.get(Content.COLUMN_NAME).getOrElse(List()).map { property ⇒
          propertyCache(property).map(template ⇒ (template.id, property))
        }.flatten: _*))
      case _ ⇒
        (columnId, immutable.HashMap(properties(columnId).map { property ⇒
          propertyCache(property).map(template ⇒ (template.id, property))
        }.flatten: _*))
    }): _*)
    val columnLabelProvider = columnTemplate.map {
      case (columnId, columnProperties) ⇒
        if (columnId == Content.COLUMN_ID)
          columnId -> new Table.TableLabelProviderID()
        else
          columnId -> new Table.TableLabelProvider(columnId, columnProperties)
    }
    App.execNGet {
      this.columnTemplate = columnTemplate
      this.columnLabelProvider = columnLabelProvider
    }
  }
  /** Get column label provider map. */
  def getColumnLabelProvider() = columnLabelProvider
  /** Get column template map. */
  def getColumnTemplate() = columnTemplate
  /** Set user defined filter. */
  def setFilter(userDefinedFilter: Filter) {
    filter.set(userDefinedFilter)
    tableViewer.refresh()
  }
  /** Set user defined sorting. */
  def setSorting(userDefinedSorting: Sorting) {
    proxyContentSorter.set(userDefinedSorting)
    tableViewer.refresh()
  }
  /** Set user defined view. */
  def setViewDefinition(userDefinedView: ModelView) = App.execAsync { updateColumns(Some(userDefinedView)) }
  /** Remove user defined filter. */
  def removeFilter() {
    filter.clear()
    tableViewer.refresh()
  }
  /** Remove user defined sorting. */
  def removeSorting() {
    proxyContentSorter.clear()
    tableViewer.refresh()
  }
  /** Remove user defined view. */
  def removeViewDefinition() = App.execAsync { updateColumns(None) }
  /** Recreate table columns */
  def updateColumns(marker: GraphMarker): Unit =
    updateColumns(marker.safeRead { state ⇒
      content.getParent.getContext.flatMap(state.payload.getSelectedViewDefinition(_))
    })
  /** Recreate table columns */
  def updateColumns(view: Option[ModelView]) {
    val disposedColumnsWidth = disposeColumns()
    val columnList = view.map(selected ⇒ mutable.LinkedHashSet(Content.COLUMN_ID) ++
      (selected.fields.map(_.name) - Content.COLUMN_ID)).getOrElse(mutable.LinkedHashSet(Content.COLUMN_ID, Content.COLUMN_NAME))
    if (columnLabelProvider.nonEmpty)
      createColumns(columnList, disposedColumnsWidth)
    filterEmptyElements.set(tableViewerColumns)
    tableViewer.refresh()
  }

  /** Adjust column width */
  protected def adjustColumnWidth(viewerColumn: TableViewerColumn, padding: Int) {
    val bounds = viewerColumn.getViewer.getControl.getBounds()
    val column = viewerColumn.getColumn()
    column.pack()
    column.setWidth(math.max(math.min(column.getWidth() + padding, bounds.width / 3), Default.columnMinimumWidth + padding))
  }
  /** Auto resize table viewer columns */
  protected def autoresize(immediately: Boolean) = if (onActiveCalled.get)
    if (immediately)
      autoresizeUpdateControls()
    else if (autoResizeLock.tryLock()) try {
      Thread.sleep(50)
      App.execNGet {
        if (!tableViewer.getTable.isDisposed())
          Content.withRedrawDelayed(content) { autoresizeUpdateControls() }
      }
    } finally {
      autoResizeLock.unlock()
    }
  /** Auto resize control updater */
  protected def autoresizeUpdateControls() {
    if (content.ActionToggleIdentificators.isChecked())
      tableViewerColumns.dropRight(1).foreach(adjustColumnWidth(_, Default.columnPadding))
    else
      tableViewerColumns.tail.dropRight(1).foreach(adjustColumnWidth(_, Default.columnPadding))
    tableViewer.refresh()
  }
  /** Create contents of the table. */
  protected def create(): TableViewer = {
    log.debug("Create table.")
    val tableViewer = new TableViewer(content.getSashForm, style)
    tableViewer.getTable.setHeaderVisible(true)
    tableViewer.getTable.setLinesVisible(true)
    tableViewer.setContentProvider(new ObservableListContentProvider())
    val table = tableViewer.getTable()
    table.setData(content)
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
        case selection: IStructuredSelection if !selection.isEmpty() ⇒
          content.setSelectedElement(selection.getFirstElement().asInstanceOf[TreeProxy.Item].element)
        case selection ⇒
      }
    })
    table.addPaintListener(onActiveListener)
    tableViewer.setComparator(sorting)
    tableViewer.setFilters(Array(filter))
    proxyContentSorted.addChangeListener { _ ⇒
      // reset cached sorting results on sorted content change
      tableViewer.getComparator.asInstanceOf[Table.TableComparator].sorted = None
    }
    // Add the dispose listener
    table.addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) {
        proxyContentSorter.dispose()
        proxyContentSorted.dispose()
        proxyContent.dispose()
        table.setData(null)
      }
    })
    proxyContentSorter.init()
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
      case Some(width) ⇒ column.setWidth(width)
      case None ⇒ column.pack()
    }
    tableViewerColumn
  }
  /** Create table viewer columns */
  protected[editor] def createColumns(columns: mutable.LinkedHashSet[String], columnsWidth: Map[String, Int]) {
    log.debug("Create columns " + columns.mkString(","))
    var pos = 0
    tableViewerColumns = columns.map { columnId ⇒
      columnLabelProvider.get(columnId) match {
        case Some(labelProvider: Table.TableLabelProviderID) ⇒
          val column = createColumn(CMessages.identificator_text, Content.COLUMN_ID, columnsWidth.get(columnId), labelProvider)
          column.getColumn.addSelectionListener(new Table.TableSelectionAdapter(WeakReference(this), pos))
          pos += 1
          // update id column width
          saveWidthColumnID.foreach { width ⇒
            column.getColumn.setWidth(0)
            column.getColumn.setResizable(false)
          }
          Some(column)
        case Some(labelProvider) ⇒
          val column = if (columnId == Content.COLUMN_NAME)
            createColumn(Messages.name_text, columnId, columnsWidth.get(columnId), labelProvider)
          else
            createColumn(columnId, columnId, columnsWidth.get(columnId), labelProvider)
          column.getColumn.addSelectionListener(new Table.TableSelectionAdapter(WeakReference(this), pos))
          pos += 1
          Some(column)
        case None ⇒
          log.warn("Unable to create an unknown column " + columnId)
          None
      }
    }.flatten
  }
  /** Create context menu for table element */
  protected def createMenu(manager: IMenuManager, selection: IStructuredSelection) {
    val item = Option(selection.getFirstElement().asInstanceOf[TreeProxy.Item])
    // tree menu
    if (content.ActionHideTree.isChecked()) {
      manager.add(ActionShowTree)
    } else {
      val treeMenu = new MenuManager(Messages.tree_text, null)
      treeMenu.add({
        val action = new ActionSelectInTree(item.map(_.element).getOrElse(null))
        if (item.nonEmpty) {
          val treeSelection = content.tree.treeViewer.getSelection().asInstanceOf[IStructuredSelection]
          action.setEnabled(treeSelection.getFirstElement() != selection.getFirstElement())
        } else {
          action.setEnabled(false)
        }
        action
      })
      treeMenu.add(new Separator)
      treeMenu.add(content.tree.ActionHideTree)
      manager.add(treeMenu)
    }
    // main menu
    manager.add(ActionConfigureColumns)
    manager.add(ActionResetSorting)
    manager.add(new Separator)
    manager.add(ActionAutoResize)
  }
  /** onActive callback */
  protected def onActive() {}
  protected[editor] def onSortingChanged() {
    tableViewer.getComparator.asInstanceOf[Table.TableComparator].sorted = None
    tableViewer.refresh()
  }
}

object Table extends XLoggable {
  /** Toggle the visibility of Id column */
  def toggleColumnId(show: Boolean, content: Content) = {
    if (show) {
      content.table.tableViewerColumns.headOption.foreach { column ⇒
        val width = content.table.saveWidthColumnID.getOrElse(-1)
        if (width <= 0)
          content.table.adjustColumnWidth(column, Default.columnPadding)
        else
          column.getColumn.setWidth(width)
        column.getColumn.setResizable(true)
      }
      content.table.saveWidthColumnID = None
    } else {
      content.table.tableViewerColumns.headOption match {
        case Some(column) ⇒
          content.table.saveWidthColumnID = Option(column.getColumn.getWidth())
          column.getColumn.setWidth(0)
          column.getColumn.setResizable(false)
        case None ⇒
          content.table.saveWidthColumnID = Option(-1)
      }
    }
  }
  /** Toggle the visibility of empty rows */
  def toggleEmptyRows(hideEmpty: Boolean, content: Content) = {
    val filters = content.table.tableViewer.getFilters()
    if (hideEmpty) {
      if (!filters.contains(content.table.filterEmptyElements))
        content.table.tableViewer.setFilters(filters :+ content.table.filterEmptyElements)
    } else {
      if (filters.contains(content.table.filterEmptyElements))
        content.table.tableViewer.setFilters(filters.filterNot(_ == content.table.filterEmptyElements))
    }
  }

  /** Sorter that transform source list to ordered target. */
  class ContentSorter(source: WritableList[TreeProxy.Item], target: WritableList[TreeProxy.Item], updateDelay: Int) {
    /** Akka execution context. */
    implicit lazy val ec = App.system.dispatcher
    /** Source events aggregator. */
    protected lazy val eventsAggregator = WritableValue(Long.box(0L))
    /** Aggregator change listener. */
    protected lazy val eventsAggregatorListener = source.addChangeListener { event ⇒ eventsAggregator.value = System.currentTimeMillis() }
    /** Delayed observable. */
    protected lazy val delayedObservable = Observables.observeDelayedValue(100, eventsAggregator)
    /** Delayed observable listener. */
    protected lazy val delayedObservableListener = new IValueChangeListener {
      def handleValueChange(event: ValueChangeEvent) = updateRequest()
    }
    /** Comparator lock. */
    protected val lock = new Object
    /** Boolean state whether the current request in progress. */
    protected var requestInProgress = false
    /** Boolean state whether the next request is required. */
    protected var requestInQueue = false
    /** Source listener. */
    protected lazy val sourceListener = source.addChangeListener { _ ⇒ eventsAggregator.value = System.currentTimeMillis() }
    /** User defined sorting. */
    @volatile protected var userDefinedSorting = Seq.empty[(XSorting.Definition, PropertyType[AnyRef with java.io.Serializable], XComparator[XComparator.Argument], Option[XComparator.Argument])]
    /** User defined sorting id. */
    @volatile protected var userDefinedSortingId = Option.empty[UUID]

    /** Remove user defined sorting. */
    def clear() = lock.synchronized {
      if (userDefinedSortingId.nonEmpty) {
        this.userDefinedSorting = Seq.empty
        userDefinedSortingId = None
        App.exec { eventsAggregator.value = System.currentTimeMillis() }
      }
    }
    /** Dispose sorter. */
    def dispose() {
      App.assertEventThread()
      delayedObservable.removeValueChangeListener(delayedObservableListener)
      delayedObservable.dispose()
      source.removeChangeListener(eventsAggregatorListener)
    }
    /** Init sorter. */
    def init() {
      App.assertEventThread()
      delayedObservable.addValueChangeListener(delayedObservableListener)
      // Initialize lazy value.
      eventsAggregatorListener
    }
    /** Set user defined sorting. */
    def set(userDefinedSorting: Sorting) = lock.synchronized {
      if (userDefinedSortingId != Some(userDefinedSorting.id)) {
        log.debug("Set user defined sorting to " + userDefinedSorting)
        this.userDefinedSorting = userDefinedSorting.definitions.toSeq.flatMap { definition ⇒
          val comparator = AvailableComparators.map.get(definition.comparator)
          if (comparator.isEmpty)
            log.error(s"Comparator for id ${definition.comparator} is not found")
          val propertyType = PropertyType.container.get(definition.propertyType)
          if (propertyType.isEmpty)
            log.error(s"PropertyType for id ${definition.propertyType} is not found")
          val argument = comparator.flatMap(c ⇒ c.stringToArgument(definition.argument))
          comparator.flatMap(c ⇒ propertyType.map(ptype ⇒ (definition,
            ptype.asInstanceOf[PropertyType[AnyRef with java.io.Serializable]],
            c.asInstanceOf[XComparator[XComparator.Argument]], argument)))
        }.reverse
        userDefinedSortingId = Some(userDefinedSorting.id)
        App.exec {
          val ts = System.currentTimeMillis()
          if (eventsAggregator.value < ts)
            eventsAggregator.value = ts
        }
      } else
        log.debug(s"Skip user defined sorting ${userDefinedSorting}. Already exists.")
    }

    /** Start update job if needed. */
    protected def processRequest() = lock.synchronized {
      (requestInProgress, requestInQueue) match {
        case (true, _) ⇒
        case (false, true) ⇒
          requestInProgress = true
          requestInQueue = false
          Future { sort() } onFailure { case e: Throwable ⇒ log.error(e.getMessage(), e) }
        case (false, false) ⇒
      }
    }
    /** Fill target with sorted content. */
    protected def sort() {
      lock.synchronized { (userDefinedSortingId, userDefinedSorting) } match {
        case (Some(uuid), sortBy) ⇒
          var iteration = App.execNGet { source.toIndexedSeq }
          sortBy.foreach {
            case ((definition, propertyType, comparator, argument)) ⇒
              // iterate over sorting definitions
              if (definition.direction)
                iteration = iteration.sortWith { (a, b) ⇒
                  comparator.compare[AnyRef with java.io.Serializable](
                    definition.property,
                    propertyType.asInstanceOf[comparator.ComparatorPropertyType[AnyRef with java.io.Serializable]],
                    a.element, b.element,
                    argument) < 0
                }
              else
                iteration = iteration.sortWith { (a, b) ⇒
                  comparator.compare[AnyRef with java.io.Serializable](
                    definition.property,
                    propertyType.asInstanceOf[comparator.ComparatorPropertyType[AnyRef with java.io.Serializable]],
                    a.element, b.element,
                    argument) >= 0
                }
          }
          App.execNGet {
            target.clear()
            target.addAll(iteration.asJava)
          }
          log.debug("Fill target with sorted (%s) content".format(uuid))
        case (None, _) ⇒
          App.execNGet {
            target.clear()
            target.addAll(Lists.newArrayList[TreeProxy.Item](source.iterator.asJava: java.util.Iterator[TreeProxy.Item]))
          }
          log.debug("Fill target with unsorted content")
      }
      updateComplete()
    }
    /** Mark update as completed. */
    protected def updateComplete() = lock.synchronized {
      requestInProgress = false
      processRequest()
    }
    /** Make update request. */
    protected def updateRequest() = lock.synchronized {
      requestInQueue = true
      processRequest()
    }
  }
  /** Filter that apply user rules */
  class FilterWithUserDefinedRules(content: WeakReference[Content]) extends ViewerFilter {
    /** User defined rules. */
    var rules = Seq.empty[(XViewFilter.Rule, PropertyType[_ <: AnySRef], XFilter[_ <: XFilter.Argument], Option[_ <: XFilter.Argument])]

    /** Clear user defined filter. */
    def clear() = rules = Nil
    override def select(viewer: Viewer, parentElement: Object, element: Object): Boolean = element match {
      case item: TreeProxy.Item ⇒
        val view = viewer.asInstanceOf[TableViewer].getTable.getData.asInstanceOf[Content]
        rules.isEmpty || rules.forall {
          case ((rule, propertyType, filter, argument)) ⇒
            val generic = filter.**
            generic.filter(rule.property, propertyType.asInstanceOf[generic.FilterPropertyType[AnySRef]], item.element, argument)
        }
      case unknown ⇒
        log.fatal("Unknown item '%s' with type '%s'".format(unknown, unknown.getClass()))
        true
    }
    /** Set user defined filter. */
    def set(userDefinedFilter: Filter) {
      rules = userDefinedFilter.rules.toSeq.flatMap { rule ⇒
        val filterInstance = AvailableFilters.map.get(rule.filter): Option[XFilter[_ <: XFilter.Argument]]
        val propertyType = PropertyType.container.get(rule.propertyType): Option[PropertyType[_ <: AnyRef with java.io.Serializable]]
        val argument = filterInstance.flatMap(f ⇒ f.stringToArgument(rule.argument))
        filterInstance.flatMap(f ⇒ propertyType.map(ptype ⇒ (rule, ptype, f, argument)))
      }
    }
  }
  /** Filter empty rows from table (id row is not takes into consideration) */
  class FilterEmptyElements(content: WeakReference[Content]) extends ViewerFilter {
    /** Actual columns. */
    protected var tableViewerColumnsTail = Seq.empty[TableViewerColumn]

    /** Returns whether the given element makes it through this filter. */
    override def select(viewer: Viewer, parentElement: Object, element: Object): Boolean =
      !isEmpty(element.asInstanceOf[TreeProxy.Item].element)
    /** Set list of columns. */
    def set(tableViewerColumns: mutable.LinkedHashSet[TableViewerColumn]) =
      tableViewerColumnsTail = if (tableViewerColumns.isEmpty) Nil else tableViewerColumns.toSeq.tail

    /** Check if column is empty. */
    protected def isEmpty(element: Element) = content.get.map { content ⇒
      tableViewerColumnsTail.forall { column ⇒
        column.getColumn.getText() match {
          case id if id == Messages.name_text ⇒
            content.table.columnLabelProvider(Content.COLUMN_NAME).isEmpty(element)
          case columnId ⇒
            content.table.columnLabelProvider(columnId).isEmpty(element)
        }
      }
    } getOrElse true
  }
  class OnActiveListener(table: WeakReference[Table]) extends PaintListener {
    /** Sent when a paint event occurs for the control. */
    def paintControl(e: PaintEvent) = table.get.foreach { table ⇒
      if (table.onActiveCalled.compareAndSet(false, true)) {
        table.tableViewer.getControl.removePaintListener(table.onActiveListener)
        table.onActive()
      }
    }
  }
  class TableComparator(initialColumn: Int, initialDirection: Boolean, table: WeakReference[Table]) extends ViewerComparator {
    /** The sort column. */
    protected var columnVar = initialColumn
    /** The sort direction. */
    protected var directionVar = initialDirection
    /** Sorted content by user defined sorter. */
    protected[editor] var sorted: Option[parallel.immutable.ParVector[TreeProxy.Item]] = None
    updateActionResetSorting

    /** Active column getter. */
    def column = columnVar
    /** Active column setter. */
    def column_=(arg: Int) {
      columnVar = arg
      directionVar = initialDirection
      updateActionResetSorting
    }
    /** Sorting direction. */
    def direction = directionVar
    /**
     * Returns a negative, zero, or positive number depending on whether
     * the first element is less than, equal to, or greater than
     * the second element.
     */
    override def compare(viewer: Viewer, e1: Object, e2: Object): Int = {
      val item1 = e1.asInstanceOf[TreeProxy.Item]
      val item2 = e2.asInstanceOf[TreeProxy.Item]
      val view = viewer.asInstanceOf[TableViewer].getTable().getData().asInstanceOf[Content]
      val columnCount = viewer.asInstanceOf[TableViewer].getTable.getColumnCount()

      val rc: Int = if (column < 0) {
        val items = sorted getOrElse {
          sorted = table.get.map(_.proxyContentSorted.toVector.par)
          sorted getOrElse parallel.immutable.ParVector.empty
        }
        items.indexOf(item1).compareTo(items.indexOf(item2))
      } else if (column < columnCount) {
        val columnId = viewer.asInstanceOf[TableViewer].getTable.getColumn(column).getData().asInstanceOf[String]
        if (columnId == Content.COLUMN_ID) {
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
    /** Reset cached sorted data. */
    def reset() = sorted = None
    /** Switch comparator direction */
    def switchDirection() {
      directionVar = !directionVar
    }

    /** get element label */
    protected def getLabel(viewer: Viewer, e1: AnyRef, lprovider: ILabelProvider): String =
      Option(lprovider.getText(e1)).getOrElse("")
    /** Update ActionResetSorting state */
    protected def updateActionResetSorting() = table.get.foreach(table ⇒
      table.ActionResetSorting.setEnabled(columnVar != -1 || directionVar != initialDirection))
  }
  class TableLabelProvider(val propertyId: String, val propertyMap: immutable.HashMap[Symbol, TemplateProperty[_ <: AnyRef with java.io.Serializable]])
      extends CellLabelProvider with ILabelProvider {
    override def update(cell: ViewerCell) = cell.getElement() match {
      case item: TreeProxy.Item ⇒
        propertyMap.get(item.element.eScope.modificator).foreach { property ⇒
          val value = item.element.eGet(property.id, property.ptype.typeSymbol).map(_.get)
          // as common unknown type
          property.ptype.adapter.asAdapter[PropertyType.genericAdapter].cellLabelProvider.update(cell, value)
        }
      case unknown ⇒
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
      case item: TreeProxy.Item ⇒
        propertyMap.get(item.element.eScope.modificator).map { property ⇒
          val value = item.element.eGet(property.id, property.ptype.typeSymbol).map(_.get)
          // as common unknown type
          property.ptype.adapter.asAdapter[PropertyType.genericAdapter].labelProvider.getImage(value)
        } getOrElse null
      case unknown ⇒
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
      case item: TreeProxy.Item ⇒
        propertyMap.get(item.element.eScope.modificator).map { property ⇒
          val value = item.element.eGet(property.id, property.ptype.typeSymbol).map(_.get)
          // as common unknown type
          property.ptype.adapter.asAdapter[PropertyType.genericAdapter].labelProvider.getText(value)
        } getOrElse ""
      case unknown ⇒
        log.fatal("Unknown item '%s' with type '%s'".format(unknown, unknown.getClass()))
        ""
    }
    override def getToolTipBackgroundColor(element: AnyRef): Color =
      withElement(element)((adapter, element) ⇒ adapter.getToolTipBackgroundColor(element)).getOrElse(super.getToolTipBackgroundColor(element))
    override def getToolTipDisplayDelayTime(element: AnyRef): Int =
      withElement(element)((adapter, element) ⇒ adapter.getToolTipDisplayDelayTime(element)).getOrElse(super.getToolTipDisplayDelayTime(element))
    override def getToolTipFont(element: AnyRef): Font =
      withElement(element)((adapter, element) ⇒ adapter.getToolTipFont(element)).getOrElse(super.getToolTipFont(element))
    override def getToolTipForegroundColor(element: AnyRef): Color =
      withElement(element)((adapter, element) ⇒ adapter.getToolTipForegroundColor(element)).getOrElse(super.getToolTipForegroundColor(element))
    override def getToolTipImage(element: AnyRef): Image =
      withElement(element)((adapter, element) ⇒ adapter.getToolTipImage(element)).getOrElse(super.getToolTipImage(element))
    override def getToolTipShift(element: AnyRef): Point =
      withElement(element)((adapter, element) ⇒ adapter.getToolTipShift(element)).getOrElse(super.getToolTipShift(element))
    override def getToolTipText(element: AnyRef): String =
      withElement(element)((adapter, element) ⇒ adapter.getToolTipText(element)).getOrElse(super.getToolTipText(element))
    override def getToolTipTimeDisplayed(element: AnyRef): Int =
      withElement(element)((adapter, element) ⇒ adapter.getToolTipTimeDisplayed(element)).getOrElse(super.getToolTipTimeDisplayed(element))
    override def getToolTipStyle(element: AnyRef): Int =
      withElement(element)((adapter, element) ⇒ adapter.getToolTipStyle(element)).getOrElse(super.getToolTipStyle(element))

    /** Returns whether property text is empty */
    def isEmpty(element: Element) =
      propertyMap.get(element.eScope.modificator).map { property ⇒
        val value = element.eGet(property.id, property.ptype.typeSymbol).map(_.get)
        // as common unknown type
        property.ptype.adapter.asAdapter[PropertyType.genericAdapter].labelProvider.getText(value).isEmpty()
      } getOrElse (true)

    /** Call the specific CellLabelProviderAdapter Fn with element argument */
    protected def withElement[T](element: AnyRef)(f: (PropertyType.CellLabelProviderAdapter[_], AnyRef) ⇒ T): Option[T] = element match {
      case item: TreeProxy.Item ⇒
        propertyMap.get(item.element.eScope.modificator) match {
          case Some(property) ⇒
            item.element.eGet(property.id, property.ptype.typeSymbol).map(_.get) match {
              case Some(value) if value.getClass() == property.ptype.typeClass ⇒
                Some(f(property.ptype.adapter.asAdapter[PropertyType.genericAdapter].cellLabelProvider, value))
              case _ ⇒
                Some(f(property.ptype.adapter.asAdapter[PropertyType.genericAdapter].cellLabelProvider, null))
            }
          case None ⇒
            None
        }
      case unknown ⇒
        log.fatal("Unknown item '%s' with type '%s'".format(unknown, unknown.getClass()))
        None
    }
  }
  class TableLabelProviderID() extends TableLabelProvider(Content.COLUMN_ID, immutable.HashMap()) {
    val dfg = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL)

    /** Update the label for cell. */
    override def update(cell: ViewerCell) = cell.getElement() match {
      case item: TreeProxy.Item ⇒
        cell.setText(item.element.eId.name)
      case unknown ⇒
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
      case item: TreeProxy.Item ⇒
        item.element.eId.name
      case unknown ⇒
        log.fatal("Unknown item '%s' with type '%s'".format(unknown, unknown.getClass()))
        null
    }
    /** Get the text displayed in the tool tip for object. */
    override def getToolTipText(obj: Object): String = obj match {
      case item: TreeProxy.Item ⇒
        Messages.lastModification_text.format(dfg.format(new Date(item.element.eStash.modified.milliseconds)))
      case unknown ⇒
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
    override def isEmpty(element: Element) = false
  }
  class TableSelectionAdapter(table: WeakReference[Table], column: Int) extends SelectionAdapter {
    override def widgetSelected(e: SelectionEvent) = table.get.foreach { table ⇒
      val comparator = table.tableViewer.getComparator().asInstanceOf[TableComparator]
      if (comparator.column == column) {
        comparator.switchDirection()
        table.tableViewer.refresh()
      } else {
        comparator.column = column
        table.tableViewer.refresh()
      }
    }
  }
}
