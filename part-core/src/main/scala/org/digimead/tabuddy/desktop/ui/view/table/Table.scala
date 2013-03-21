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

import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future
import scala.collection.mutable
import scala.collection.immutable
import com.ibm.icu.text.DateFormat
import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.tabuddy.desktop.Data
import org.digimead.tabuddy.desktop.Main
import org.digimead.tabuddy.desktop.job.view.JobModifyFilterList
import org.digimead.tabuddy.desktop.job.view.JobModifySortingList
import org.digimead.tabuddy.desktop.job.view.JobModifyViewList
import org.digimead.tabuddy.desktop.payload.ElementTemplate
import org.digimead.tabuddy.desktop.payload.PropertyType
import org.digimead.tabuddy.desktop.payload.TemplateProperty
import org.digimead.tabuddy.desktop.payload.view.Filter
import org.digimead.tabuddy.desktop.payload.view.Sorting
import org.digimead.tabuddy.desktop.res.Messages
import org.digimead.tabuddy.desktop.support.WritableValue
import org.digimead.tabuddy.desktop.support.WritableValue.wrapper2underlying
import org.digimead.tabuddy.desktop.ui.Default
import org.digimead.tabuddy.desktop.ui.Window
import org.digimead.tabuddy.desktop.ui.action.view.ActionModifyFilterList
import org.digimead.tabuddy.desktop.ui.action.view.ActionModifySortingList
import org.digimead.tabuddy.desktop.ui.action.view.ActionModifyViewList
import org.digimead.tabuddy.desktop.ui.dialog.Dialog
import org.digimead.tabuddy.desktop.ui.view.View
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.element.Stash
import org.eclipse.core.databinding.observable.Observables
import org.eclipse.core.databinding.observable.value.IValueChangeListener
import org.eclipse.core.databinding.observable.value.ValueChangeEvent
import org.eclipse.jface.action.Action
import org.eclipse.jface.action.CoolBarManager
import org.eclipse.jface.action.IAction
import org.eclipse.jface.action.IMenuListener
import org.eclipse.jface.action.IMenuManager
import org.eclipse.jface.action.MenuManager
import org.eclipse.jface.action.Separator
import org.eclipse.jface.viewers.AbstractTreeViewer
import org.eclipse.jface.viewers.CellLabelProvider
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport
import org.eclipse.jface.viewers.ITreeContentProvider
import org.eclipse.jface.viewers.TreeViewer
import org.eclipse.jface.viewers.TreeViewerColumn
import org.eclipse.jface.viewers.Viewer
import org.eclipse.jface.viewers.ViewerCell
import org.eclipse.jface.viewers.ViewerFilter
import org.eclipse.swt.SWT
import org.eclipse.swt.events.PaintEvent
import org.eclipse.swt.events.PaintListener
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.graphics.Font
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Event
import org.eclipse.swt.widgets.Listener
import swing2swt.layout.BorderLayout
import org.eclipse.swt.widgets.TreeItem
import org.digimead.digi.lib.DependencyInjection
import org.digimead.tabuddy.model.Record
import org.eclipse.jface.viewers.ViewerComparator

class Table private (argParent: Composite, argStyle: Int)
  extends org.digimead.tabuddy.desktop.res.view.table.Table(argParent, argStyle) with View {
  lazy val title = Messages.tableView_text
  lazy val description = "Tree View"
  /** The auto resize lock */
  protected val autoResizeLock = new ReentrantLock()
  /** Current model column -> template map (id -> [ElementTemplate.id -> TemplateProperty]) */
  protected var columnTemplate = immutable.HashMap[String, immutable.HashMap[Symbol, TemplateProperty[_ <: AnyRef with java.io.Serializable]]]()
  /** Current model column -> label provider map */
  protected var columnLabelProvider = immutable.HashMap[String, Table.TableCellLabelProvider]()
  /** List of expanded elements */
  protected var expandedElements = Set[Element.Generic]()
  /** Tree viewer filters */
  protected var filters = Set[ViewerFilter]()
  /** On active listener flag */
  protected val onActiveFlag = new AtomicBoolean(true)
  /** On active listener */
  protected val onActiveListener = new Table.OnActiveListener()
  /** Pointer to StructuredViewer void update(Object[] elements, String[] properties), scala bug SI-2991 */
  protected var structuredViewerUpdateF: (Array[AnyRef], Array[String]) => Unit = null
  /** Tree viewer instance */
  protected var treeViewer: TreeViewer = null
  /** Tree viewer column's instances */
  protected var treeViewerColumns = Set[TreeViewerColumn]()
  /** The column special identifier */
  protected val COLUMN_ID = "id"
  /** The column special identifier */
  protected val COLUMN_NAME = "name"
  /** The table view menu */
  val tableViewMenu = {
    val menu = new MenuManager(Messages.tableView_text)
    menu.add(ActionModifyViewList)
    menu.add(ActionModifySortingList)
    menu.add(ActionModifyFilterList)
    menu.add(new Separator)
    menu.add(Table.ActionToggleSystem)
    menu.add(Table.ActionToggleExpand)
    menu
  }

  /** Adjust column width */
  protected def adjustColumnWidth(viewerColumn: TreeViewerColumn, padding: Int) {
    val bounds = viewerColumn.getViewer.getControl.getBounds()
    val column = viewerColumn.getColumn()
    column.pack()
    column.setWidth(math.min(column.getWidth() + padding, bounds.width / 3))
  }
  /** Auto resize tableviewer columns */
  protected def autoresize() = if (autoResizeLock.tryLock()) try {
    Thread.sleep(50)
    Main.execNGet {
      if (!treeViewer.getTree.isDisposed()) {
        if (Table.ActionToggleIdentificators.isChecked())
          treeViewerColumns.dropRight(1).foreach(adjustColumnWidth(_, Default.columnPadding))
        else
          treeViewerColumns.tail.dropRight(1).foreach(adjustColumnWidth(_, Default.columnPadding))
        treeViewer.refresh()
      }
    }
  } finally {
    autoResizeLock.unlock()
  }
  /** Get table coolbar manager */
  override protected[table] def getCoolBarManager(): CoolBarManager = super.getCoolBarManager()
  /** onActive callback */
  protected def onActive() {
    if (Table.ActionAutoResize.isChecked())
      future { autoresize() }
  }
  /** onHide callback */
  override protected def onHide() {
    Window.getMenuTopLevel().remove(tableViewMenu)
    Window.getMenuTopLevel().update(false)
  }
  /** onShow callback */
  override protected def onShow() {
    Window.getMenuTopLevel().add(tableViewMenu)
    Window.getMenuTopLevel().update(false)
  }
  /** Create contents of the view. */
  protected def createViewArea() {
    // initialize toolbars
    getCoolBarManager.add(TableToolbarView)
    getCoolBarManager.add(TableToolbarPrimary)
    getCoolBarManager.add(TableToolbarSecondary)
    getCoolBarManager.update(true)
    // initialize table
    treeViewer = new TreeViewer(this, SWT.BORDER)
    recreateTree()
  }
  /** Re/create contents of the table. */
  protected[table] def recreateTree() {
    log.debug("recreate tree")
    val parent = treeViewer.getTree.getParent()
    // dispose
    treeViewerColumns = Set()
    treeViewer.getControl().dispose()
    onActiveFlag.set(true)
    // create new
    treeViewer = new TreeViewer(parent, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL)
    structuredViewerUpdateF = {
      // scala bug SI-2991
      import scala.language.reflectiveCalls
      (treeViewer: { def update(a: Array[AnyRef], b: Array[String]): Unit }).update(_, _)
    }
    treeViewer.getTree.setHeaderVisible(true)
    treeViewer.getTree.setLinesVisible(true)
    treeViewer.setContentProvider(new Table.TContentProvider())
    val tree = treeViewer.getTree()
    tree.setLayoutData(BorderLayout.CENTER)
    recreateColumns(columnTemplate.keys.toSet)
    treeViewer.setInput(Model.inner)
    // Activate the tooltip support for the viewer
    ColumnViewerToolTipSupport.enableFor(treeViewer)
    // Add the context menu
    val menuMgr = new MenuManager()
    val menu = menuMgr.createContextMenu(treeViewer.getControl)
    menuMgr.addMenuListener(new IMenuListener() {
      override def menuAboutToShow(manager: IMenuManager) {
        manager.add(Table.ActionAutoResize)
      }
    })
    menuMgr.setRemoveAllWhenShown(true)
    treeViewer.getControl.setMenu(menu)
    // Add the expand/collapse listener
    tree.addListener(SWT.Collapse, new Listener() {
      override def handleEvent(e: Event) = {
        expandedElements -= e.item.getData.asInstanceOf[Element.Generic]
        if (Table.ActionAutoResize.isChecked())
          future { autoresize() }
      }
    })
    tree.addListener(SWT.Expand, new Listener() {
      override def handleEvent(e: Event) = {
        expandedElements += e.item.getData.asInstanceOf[Element.Generic]
        if (Table.ActionAutoResize.isChecked())
          future { autoresize() }
      }
    })
    tree.addPaintListener(onActiveListener)
    // update id column width
    if (Table.ActionToggleIdentificators.idColumnWidth != -1)
      tree.getColumn(0).setWidth(Table.ActionToggleIdentificators.idColumnWidth)
    // update of tree state
    Table.ActionToggleSystem()
    // force to recalculate layout of table in container
    parent.layout()
  }
  /** Recreate tree viewer columns */
  protected def recreateColumns(columns: Set[String]) {
    val tree = treeViewer.getTree
    tree.setRedraw(false)
    val exists = immutable.HashMap(tree.getColumns.map { column => (column.getText(), column.getWidth()) }: _*)
    while (tree.getColumnCount > 0)
      tree.getColumns.head.dispose()
    val columnIds = Option(TableToolbarView.view.value).map(_.fields.map(_.name)).getOrElse(Set(COLUMN_NAME))
    treeViewerColumns = (Set(COLUMN_ID) ++ columnIds).map { columnId =>
      columnTemplate.get(columnId) match {
        case Some(columnProperties) =>
          val treeViewerColumn = new TreeViewerColumn(treeViewer, SWT.LEFT)
          treeViewerColumn.setLabelProvider(columnLabelProvider(columnId))
          columnId match {
            case COLUMN_ID =>
              treeViewerColumn.getColumn().setText(Messages.identificator_text)
            case COLUMN_NAME =>
              treeViewerColumn.getColumn().setText(Messages.name_text)
            case _ =>
              treeViewerColumn.getColumn().setText(columnId)
          }
          treeViewerColumn.getColumn().setWidth(exists.get(columnId).getOrElse(100))
          Some(treeViewerColumn)
        case None =>
          log.fatal("unknown column " + columnId)
          None
      }
    }.flatten
    tree.setRedraw(true)
  }
  /** Recreate with preserve expanded and selected elements */
  protected def recreateSmart() {
    val selection = treeViewer.getSelection()
    recreateTree
    treeViewer.setExpandedElements(expandedElements.toArray.asInstanceOf[Array[AnyRef]])
    treeViewer.setSelection(selection)
  }
  /** Recreate tree viewer and reload data */
  protected def reload() {
    log.debug("update Tree view: major changes")
    val propertyCache = mutable.HashMap[TemplateProperty[_ <: AnyRef with java.io.Serializable], Seq[ElementTemplate.Interface]]()
    val properties = Data.elementTemplates.values.flatMap { template =>
      template.properties.foreach {
        case (group, properties) => properties.foreach(property =>
          propertyCache(property) = propertyCache.get(property).getOrElse(Seq()) :+ template)
      }
      template.properties.flatMap(_._2)
    }.toList.groupBy(_.id.name.toLowerCase())
    val columnIds = List(COLUMN_ID, COLUMN_NAME) ++ properties.keys.filterNot(_ == COLUMN_NAME).toSeq.sorted
    columnTemplate = immutable.HashMap((for (columnId <- columnIds) yield columnId match {
      case id if id == COLUMN_ID => (COLUMN_ID, immutable.HashMap[Symbol, TemplateProperty[_ <: AnyRef with java.io.Serializable]]())
      case id if id == COLUMN_NAME => (COLUMN_NAME, immutable.HashMap(properties.get(COLUMN_NAME).getOrElse(List()).map { property =>
        propertyCache(property).map(template => (template.id, property))
      }.flatten: _*))
      case _ => (columnId, immutable.HashMap(properties(columnId).map { property =>
        propertyCache(property).map(template => (template.id, property))
      }.flatten: _*))
    }): _*)
    columnLabelProvider = columnTemplate.map {
      case (columnId, columnProperties) =>
        if (columnId == COLUMN_ID)
          columnId -> new Table.LabelProviderID()
        else
          columnId -> new Table.LabelProvider(columnId, columnProperties)
    }
    recreateTree
    this.layout()
  }
  /**
   * Refreshes this viewer starting with the given element. Labels are updated
   * as described in <code>refresh(boolean updateLabels)</code>.
   * <p>
   * Unlike the <code>update</code> methods, this handles structural changes
   * to the given element (e.g. addition or removal of children). If only the
   * given element needs updating, it is more efficient to use the
   * <code>update</code> methods.
   */
  protected def refresh(elements: Array[Element.Generic]) {
    log.debug("refresh Tree view")
    elements.foreach { element => treeViewer.refresh(element, true) }
  }
  /** Refresh with preserve expanded and selected elements */
  protected def refreshSmart() {
    val selection = treeViewer.getSelection()
    treeViewer.refresh()
    treeViewer.setExpandedElements(expandedElements.toArray.asInstanceOf[Array[AnyRef]])
    treeViewer.setSelection(selection)
  }
  /**
   * Updates the given elements' presentation when one or more of their properties change.
   * Only the given elements are updated.
   *
   * This does not handle structural changes (e.g. addition or removal of elements),
   * and does not update any other related elements (e.g. child elements).
   * To handle structural changes, use the refresh methods instead.
   */
  protected def update(elements: Array[Element.Generic]) {
    log.debug("update Tree view")
    structuredViewerUpdateF(elements.asInstanceOf[Array[AnyRef]], null)
  }
}

object Table extends Main.Interface with Loggable {
  @volatile private[table] var table: Table = null
  /** Aggregation listener delay */
  private val aggregatorDelay = 250 // msec
  /** Significant changes(schema modification, model reloading,...) aggregator */
  private val reloadEventsAggregator = WritableValue(Long.box(0L))
  /** Structural changes(e.g. addition or removal of elements) aggregator */
  private val refreshEventsAggregator = WritableValue(Set[Element[_ <: Stash]]())
  /** Structural changes(e.g. addition or removal of elements) aggregator */
  private val refreshEventsExpandAggregator = WritableValue(Set[Element[_ <: Stash]]())
  /** Actual sorting direction */
  @volatile private var sortingDirection = Default.sortingDirection
  /** Actual sortBy column index */
  @volatile private var sortingColumn = 0
  /** Minor changes(element modification) aggregator */
  private val updateEventsAggregator = WritableValue(Set[Element[_ <: Stash]]())
  /** Global element events subscriber */
  private val elementEventsSubscriber = new Element.Event.Sub {
    def notify(pub: Element.Event.Pub, event: Element.Event) = event match {
      case Element.Event.ChildInclude(element, newElement, _) =>
        if (element.eStash.model.forall(_ eq Model.inner))
          Main.exec {
            refreshEventsAggregator.value = refreshEventsAggregator.value + element
            if (ActionToggleExpand.isChecked()) {
              if (element.eChildren.size == 1) // if 1st child
                table.treeViewer.setExpandedState(element, true)
              table.treeViewer.setExpandedState(newElement, table.treeViewer.getExpandedState(element))
            }
          }
      case Element.Event.ChildRemove(element, _, _) =>
        if (element.eStash.model.forall(_ eq Model.inner))
          Main.exec { refreshEventsAggregator.value = refreshEventsAggregator.value + element }
      case Element.Event.ChildrenReset(element, _) =>
        if (element.eStash.model.forall(_ eq Model.inner))
          Main.exec { refreshEventsAggregator.value = refreshEventsAggregator.value + element }
      case Element.Event.ChildReplace(element, _, _, _) =>
        if (element.eStash.model.forall(_ eq Model.inner))
          Main.exec { refreshEventsAggregator.value = refreshEventsAggregator.value + element }
      case Element.Event.StashReplace(element, _, _, _) =>
        if (element.eStash.model.forall(_ eq Model.inner))
          Main.exec { updateEventsAggregator.value = updateEventsAggregator.value + element }
      case Element.Event.ValueInclude(element, _, _) =>
        if (element.eStash.model.forall(_ eq Model.inner))
          Main.exec { updateEventsAggregator.value = updateEventsAggregator.value + element }
      case Element.Event.ValueRemove(element, _, _) =>
        if (element.eStash.model.forall(_ eq Model.inner))
          Main.exec { updateEventsAggregator.value = updateEventsAggregator.value + element }
      case Element.Event.ValueUpdate(element, _, _, _) =>
        if (element.eStash.model.forall(_ eq Model.inner))
          Main.exec { updateEventsAggregator.value = updateEventsAggregator.value + element }
      case Element.Event.ModelReplace(_, _, _) =>
        TFilterSystemElement.updateSystemElement
      case _ =>
    }
  }

  /** Create table view */
  def apply(parent: Composite, style: Int): Table = {
    assert(table == null, "Tree already initialized")
    table = new Table(parent, style)
    table.createViewArea
    // handle data modification changes
    Data.modelName.addChangeListener { event => reloadEventsAggregator.value = System.currentTimeMillis() }
    Data.elementTemplates.addChangeListener { event => reloadEventsAggregator.value = System.currentTimeMillis() }
    Observables.observeDelayedValue(aggregatorDelay, reloadEventsAggregator).addValueChangeListener(new IValueChangeListener {
      def handleValueChange(event: ValueChangeEvent) = {
        table.reload()
        if (Table.ActionAutoResize.isChecked())
          future { table.autoresize() }
      }
    })
    // handle structural changes
    Observables.observeDelayedValue(aggregatorDelay, refreshEventsAggregator).addValueChangeListener(new IValueChangeListener {
      def handleValueChange(event: ValueChangeEvent) {
        val set = refreshEventsAggregator.value
        refreshEventsAggregator.value = Set()
        if (set.nonEmpty)
          table.refresh(set.toArray)
        if (Table.ActionAutoResize.isChecked())
          future { table.autoresize() }
      }
    })
    // handle presentation changes
    Observables.observeDelayedValue(aggregatorDelay, updateEventsAggregator).addValueChangeListener(new IValueChangeListener {
      def handleValueChange(event: ValueChangeEvent) {
        val set = updateEventsAggregator.value
        updateEventsAggregator.value = Set()
        if (set.nonEmpty)
          table.update(set.toArray)
        if (Table.ActionAutoResize.isChecked())
          future { table.autoresize() }
      }
    })
    // handle view changes
    TableToolbarView.view.addChangeListener { event =>
      table.recreateSmart
      if (Table.ActionAutoResize.isChecked())
        future { table.autoresize() }
    }
    // initial update of tree state
    if (Table.ActionToggleExpand.isChecked()) Table.ActionExpandAll() else Table.ActionCollapseAll()
    Table.ActionToggleEmpty()
    Table.ActionToggleIdentificators()
    table
  }
  /** Get table instance */
  def getTable() = table
  /**
   * This function is invoked at application start
   */
  def start() = Element.Event.subscribe(elementEventsSubscriber)
  /**
   * This function is invoked at application stop
   */
  def stop() = Element.Event.removeSubscription(elementEventsSubscriber)

  class CustomComparator extends ViewerComparator {
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
          Option(TableToolbarView.sorting) match {
            case Some(sorting) if sorting != Sorting.default =>
              log.___glance("skip")
              0
            case _ =>
              val iterator = table.treeViewerColumns.iterator
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
  }
  class TContentProvider extends ITreeContentProvider {
    /**
     * Disposes of this content provider.
     * This is called by the viewer when it is disposed.
     */
    def dispose() {}
    /** Returns whether the given element has children. */
    def hasChildren(element: AnyRef): Boolean = element match {
      case element: Element.Generic =>
        element.eChildren.nonEmpty
      case unknown =>
        log.fatal("Unknown element '%s' with type '%s'".format(unknown, unknown.getClass()))
        false
    }
    /**
     * Notifies this content provider that the given viewer's input
     * has been switched to a different element.
     */
    def inputChanged(v: Viewer, oldInput: Object, newInput: Object) {}
    /** Returns the child elements of the given parent element. */
    def getChildren(parent: AnyRef): Array[AnyRef] = parent match {
      case element: Element.Generic =>
        filterEmpty(element).asInstanceOf[Array[AnyRef]]
      case unknown =>
        log.fatal("Unknown element '%s' with type '%s'".format(unknown, unknown.getClass()))
        Array()
    }
    /**
     * Returns the elements to display in the viewer
     * when its input is set to the given element.
     * These elements can be presented as rows in a table, items in a list, etc.
     * The result is not modified by the viewer.
     *
     * @param inputElement the input element
     * @return the array of elements to display in the viewer
     */
    def getElements(parent: AnyRef): Array[AnyRef] = parent match {
      case element: Element.Generic =>
        filterEmpty(element).asInstanceOf[Array[AnyRef]]
      case unknown =>
        log.fatal("Unknown element '%s' with type '%s'".format(unknown, unknown.getClass()))
        Array()
    }
    /**
     * Returns the parent for the given element, or <code>null</code>
     * indicating that the parent can't be computed.
     * In this case the tree-structured viewer can't expand
     * a given node correctly if requested.
     */
    def getParent(element: AnyRef): AnyRef = element match {
      case element: Element.Generic =>
        element.eParent.getOrElse(null)
      case item: TreeItem =>
        item.getData().asInstanceOf[Element.Generic].eParent.getOrElse(null)
      case unknown =>
        log.fatal("Unknown element '%s' with type '%s'".format(unknown, unknown.getClass()))
        null
    }

    protected def filterEmpty(element: Element.Generic): Array[Element.Generic] = if (ActionToggleEmpty.isChecked()) {
      element.eChildren.toArray
    } else {
      element.eChildren.toSeq.map { element =>
        if (isEmpty(element)) {
          filterEmpty(element)
        } else {
          Array[Element.Generic](element)
        }
      }.flatten.toArray
    }
    protected def isEmpty(element: Element.Generic) = {
      table.treeViewerColumns.tail.forall { column =>
        column.getColumn.getText() match {
          case id if id == Messages.name_text =>
            table.columnLabelProvider(table.COLUMN_NAME).isEmpty(element)
          case columnId =>
            table.columnLabelProvider(columnId).isEmpty(element)
        }
      }
    }
  }
  class LabelProvider(propertyId: String, propertyMap: immutable.HashMap[Symbol, TemplateProperty[_ <: AnyRef with java.io.Serializable]]) extends TableCellLabelProvider {
    override def update(cell: ViewerCell) = cell.getElement() match {
      case element: Element.Generic =>
        propertyMap.get(element.eScope.modificator).foreach { property =>
          val value = element.eGet(property.id, property.ptype.typeSymbol).map(_.get)
          // as common unknown type
          property.ptype.adapter.cellLabelProvider.asInstanceOf[PropertyType.CellLabelProviderAdapter[AnyRef with java.io.Serializable]].
            update(cell, value)
        }
      case unknown =>
        log.fatal("Unknown element '%s' with type '%s'".format(unknown, unknown.getClass()))
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
      case element: Element.Generic =>
        propertyMap.get(element.eScope.modificator) match {
          case Some(property) =>
            element.eGet(property.id, property.ptype.typeSymbol).map(_.get) match {
              case Some(value) if value.getClass() == property.ptype.typeClass =>
                Some(f(property.ptype.adapter.cellLabelProvider, value))
              case _ =>
                Some(f(property.ptype.adapter.cellLabelProvider, null))
            }
          case None =>
            None
        }
      case unknown =>
        log.fatal("Unknown element '%s' with type '%s'".format(unknown, unknown.getClass()))
        None
    }
  }
  class LabelProviderID() extends TableCellLabelProvider {
    val dfg = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL)

    /** Update the label for cell. */
    override def update(cell: ViewerCell) = cell.getElement() match {
      case element: Element.Generic =>
        cell.setText(element.eId.name)
      case unknown =>
        log.fatal("Unknown element '%s' with type '%s'".format(unknown, unknown.getClass()))
    }
    /** Get the text displayed in the tool tip for object. */
    override def getToolTipText(element: Object): String = element match {
      case element: Element.Generic =>
        Messages.lastModification_text.format(dfg.format(new Date(element.eModified.milliseconds)))
      case unknown =>
        log.fatal("Unknown element '%s' with type '%s'".format(unknown, unknown.getClass()))
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
    def isEmpty(element: Element.Generic) = false
  }
  class OnActiveListener() extends PaintListener {
    /** Sent when a paint event occurs for the control. */
    def paintControl(e: PaintEvent) {
      if (table.onActiveFlag.compareAndSet(true, false)) {
        table.treeViewer.getControl.removePaintListener(table.onActiveListener)
        table.onActive()
      }
    }
  }
  abstract class TableCellLabelProvider extends CellLabelProvider {
    def isEmpty(element: Element.Generic): Boolean
  }
  object TFilterSystemElement extends ViewerFilter {
    var systemElement: Element.Generic = null

    def updateSystemElement = DependencyInjection.get.map { di =>
      Main.exec { systemElement = di.inject[Record.Interface[_ <: Record.Stash]](Some("eTABuddy")).eParent.getOrElse(null) }
    }
    override def select(viewer: Viewer, parentElement: Object, element: Object): Boolean =
      element.ne(systemElement)
  }
  /*
   * Actions
   */
  object ActionAutoResize extends Action(Messages.autoresize_key, IAction.AS_CHECK_BOX) {
    setChecked(true)
    override def run = if (isChecked()) Table.table.autoresize
  }
  object ActionElementEdit extends Action(Messages.edit_text) {
    override def run = {}
  }
  object ActionElementDelete extends Action(Messages.delete_text) {
    override def run = {}
  }
  object ActionElementLink extends Action(Messages.link_text) {
    override def run = {}
  }
  object ActionElementLeft extends Action(Messages.delete_text) {
    override def run = {}
  }
  object ActionElementRight extends Action(Messages.delete_text) {
    override def run = {}
  }
  object ActionElementUp extends Action(Messages.delete_text) {
    override def run = {}
  }
  object ActionElementDown extends Action(Messages.delete_text) {
    override def run = {}
  }
  object ActionToggleIdentificators extends Action(Messages.identificators_text, IAction.AS_CHECK_BOX) {
    protected[table] var idColumnWidth = -1
    setChecked(true)

    def apply() = if (isChecked())
      table.treeViewerColumns.headOption.foreach { column =>
        if (idColumnWidth <= 1)
          table.adjustColumnWidth(column, Default.columnPadding)
        else
          column.getColumn.setWidth(idColumnWidth)
        column.getColumn.setResizable(true)
      }
    else
      table.treeViewerColumns.headOption.foreach { column =>
        idColumnWidth = column.getColumn.getWidth()
        column.getColumn.setWidth(1)
        column.getColumn.setResizable(false)
      }
    override def run = apply()
  }
  object ActionToggleEmpty extends Action(Messages.emptyRows_text, IAction.AS_CHECK_BOX) {
    setChecked(true)

    def apply() = if (isChecked())
      table.refreshSmart()
    else
      table.refreshSmart()
    override def run = apply()
  }
  object ActionToggleExpand extends Action(Messages.expandNew_text, IAction.AS_CHECK_BOX) {
    override def run = if (isChecked())
      table.treeViewer.setAutoExpandLevel(AbstractTreeViewer.ALL_LEVELS)
    else
      table.treeViewer.setAutoExpandLevel(0)
  }
  object ActionToggleSystem extends Action(Messages.systemElements_text, IAction.AS_CHECK_BOX) {
    def apply() = if (isChecked()) {
      table.treeViewer.setFilters(table.treeViewer.getFilters().filterNot(_ == TFilterSystemElement))
      // expand system elements if needed
      table.treeViewer.setExpandedElements(table.expandedElements.toArray.asInstanceOf[Array[AnyRef]])
    } else
      table.treeViewer.setFilters(table.treeViewer.getFilters().filterNot(_ == TFilterSystemElement) :+ TFilterSystemElement)
    override def run = apply()
  }
  object ActionExpandAll extends Action(Messages.expandAll_text) {
    def apply() {
      table.treeViewer.expandAll()
      table.treeViewer.getExpandedElements().foreach(_ match {
        case element: Element.Generic =>
          table.expandedElements += element
        case unknown =>
          log.fatal("Unknown element '%s' with type '%s'".format(unknown, unknown.getClass()))
      })
      future { table.autoresize }
    }
    override def run = apply()
  }
  object ActionCollapseAll extends Action(Messages.collapseAll_text) {
    def apply() {
      table.treeViewer.collapseAll()
      table.expandedElements = Set()
      future { table.autoresize }
    }
    override def run = apply()
  }
}
