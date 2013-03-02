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

package org.digimead.tabuddy.desktop.ui.view.tree

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
import org.digimead.tabuddy.desktop.payload.ElementTemplate
import org.digimead.tabuddy.desktop.payload.PropertyType
import org.digimead.tabuddy.desktop.payload.TemplateProperty
import org.digimead.tabuddy.desktop.res.Messages
import org.digimead.tabuddy.desktop.support.WritableValue
import org.digimead.tabuddy.desktop.support.WritableValue.wrapper2underlying
import org.digimead.tabuddy.desktop.ui.dialog.Dialog
import org.digimead.tabuddy.desktop.ui.toolbar.TreePrimary
import org.digimead.tabuddy.desktop.ui.view.View
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.element.Stash
import org.eclipse.core.databinding.observable.Observables
import org.eclipse.core.databinding.observable.value.IValueChangeListener
import org.eclipse.core.databinding.observable.value.ValueChangeEvent
import org.eclipse.jface.action.Action
import org.eclipse.jface.action.IAction
import org.eclipse.jface.action.IMenuListener
import org.eclipse.jface.action.IMenuManager
import org.eclipse.jface.action.MenuManager
import org.eclipse.jface.viewers.CellLabelProvider
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport
import org.eclipse.jface.viewers.ITreeContentProvider
import org.eclipse.jface.viewers.TreeViewer
import org.eclipse.jface.viewers.TreeViewerColumn
import org.eclipse.jface.viewers.Viewer
import org.eclipse.jface.viewers.ViewerCell
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

class Tree private (argParent: Composite, argStyle: Int)
  extends org.digimead.tabuddy.desktop.res.view.Tree(argParent, argStyle) with View {
  lazy val title = "Tree"
  lazy val description = "Tree View"
  /** The auto resize lock */
  protected val autoResizeLock = new ReentrantLock()
  /** On active listener flag */
  protected val onActiveFlag = new AtomicBoolean(true)
  /** On active listener */
  protected val onActiveListener = new Tree.OnActiveListener()
  /** Tree viewer instance */
  protected var treeViewer: TreeViewer = null
  /** Tree viewer instance columns */
  protected var treeViewerColumns: Seq[TreeViewerColumn] = Seq()
  /** Pointer to StructuredViewer void update(Object[] elements, String[] properties), scala bug SI-2991 */
  protected var structuredViewerUpdateF: (Array[AnyRef], Array[String]) => Unit = null
  /** A flag that indicates if new elements should be expanded */
  protected var fExpandNew = true
  createViewArea(this)

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
        adjustColumnWidth(treeViewerColumns.head, Dialog.columnPadding)
        treeViewer.refresh()
      }
    }
  } finally {
    autoResizeLock.unlock()
  }
  /** Create contents of the view. */
  protected def createViewArea(parent: Composite) {
    // initialize toolbars
    getCoolBarManager.add(TreePrimary)
    getCoolBarManager.update(true)
    // initialize table
    treeViewer = new TreeViewer(parent, SWT.BORDER)
    recreateTree()
  }
  /** Re/create contents of the tree. */
  protected def recreateTree() {
    log.debug("recreate tree")
    val parent = treeViewer.getTree.getParent()
    // dispose
    treeViewerColumns = Seq()
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
    treeViewer.setContentProvider(new Tree.TContentProvider())
    val tree = treeViewer.getTree()
    tree.setLayoutData(BorderLayout.CENTER)
    val propertyCache = mutable.HashMap[TemplateProperty[_ <: AnyRef with java.io.Serializable], Seq[ElementTemplate.Interface]]()
    val properties = Data.elementTemplates.flatMap { template =>
      template.properties.foreach {
        case (group, properties) => properties.foreach(property =>
          propertyCache(property) = propertyCache.get(property).getOrElse(Seq()) :+ template)
      }
      template.properties.flatMap(_._2)
    }.toList.groupBy(_.id.name.toLowerCase())
    val labelId = "label"
    val columns = List("id", labelId) ++ properties.keys.filterNot(_ == labelId).toList.sorted
    treeViewerColumns = for (column <- columns) yield {
      val treeViewerColumn = new TreeViewerColumn(treeViewer, SWT.LEFT)
      column match {
        case "id" =>
          treeViewerColumn.setLabelProvider(new Tree.LabelProviderID())
        case "label" =>
          treeViewerColumn.setLabelProvider(new Tree.LabelProvider(labelId,
            immutable.HashMap(properties.get(labelId).getOrElse(List()).map { property =>
              propertyCache(property).map(template => (template.id, property))
            }.flatten: _*)))
        case _ =>
          treeViewerColumn.setLabelProvider(new Tree.LabelProvider(column,
            immutable.HashMap(properties(column).map { property =>
              propertyCache(property).map(template => (template.id, property))
            }.flatten: _*)))
      }
      treeViewerColumn.getColumn().setText(column)
      treeViewerColumn.getColumn().setWidth(100)
      treeViewerColumn
    }
    treeViewer.setInput(Model.inner)
    // Activate the tooltip support for the viewer
    ColumnViewerToolTipSupport.enableFor(treeViewer)
    // Add the context menu
    val menuMgr = new MenuManager()
    val menu = menuMgr.createContextMenu(treeViewer.getControl)
    menuMgr.addMenuListener(new IMenuListener() {
      override def menuAboutToShow(manager: IMenuManager) {
        manager.add(Tree.ActionAutoResize)
      }
    })
    menuMgr.setRemoveAllWhenShown(true)
    treeViewer.getControl.setMenu(menu)
    // Add the expand/collapse listener
    val listener = new Listener() {
      override def handleEvent(e: Event) {
        /*
         * expand/collapse example
         * final TreeItem treeItem = (TreeItem)e.item;
         * for ( TreeColumn tc : treeItem.getParent().getColumns() ) tc.pack();
         */
        if (Tree.ActionAutoResize.isChecked())
          future { autoresize() }
      }
    }
    tree.addListener(SWT.Collapse, listener);
    tree.addListener(SWT.Expand, listener);
    // add paint listener
    tree.addPaintListener(onActiveListener)
    treeViewer.expandAll()
    parent.layout()
    tree.layout()
  }
  /** Recreate tree viewer and reload data */
  protected def reload() {
    log.debug("update Tree view: major changes")
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
  /** onActive callback */
  protected def onActive() {
    if (Tree.ActionAutoResize.isChecked())
      future { autoresize() }
  }
}

object Tree extends Main.Interface with Loggable {
  @volatile private var tree: Tree = null
  /** Aggregation listener delay */
  private val aggregatorDelay = 250 // msec
  /** Significant changes(schema modification, model reloading,...) aggregator */
  private val reloadEventsAggregator = WritableValue(Long.box(0L))
  /** Structural changes(e.g. addition or removal of elements) aggregator */
  private val refreshEventsAggregator = WritableValue(Set[Element[_ <: Stash]]())
  /** Structural changes(e.g. addition or removal of elements) aggregator */
  private val refreshEventsExpandAggregator = WritableValue(Set[Element[_ <: Stash]]())
  /** Minor changes(element modification) aggregator */
  private val updateEventsAggregator = WritableValue(Set[Element[_ <: Stash]]())
  /** Global element events subscriber */
  private val elementEventsSubscriber = new Element.Event.Sub {
    def notify(pub: Element.Event.Pub, event: Element.Event) = event match {
      case Element.Event.ChildInclude(element, newElement, _) =>
        if (element.eStash.model.forall(_ eq Model.inner))
          Main.exec {
            refreshEventsAggregator.value = refreshEventsAggregator.value + element
            Option(tree).foreach { tree =>
              if (tree.fExpandNew) {
                if (element.eChildren.size == 1) // if 1st child
                  tree.treeViewer.setExpandedState(element, true)
                tree.treeViewer.setExpandedState(newElement, tree.treeViewer.getExpandedState(element))
              }
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
      case _ =>
    }
  }
  // handle data modification changes
  Data.modelName.addChangeListener { event => reloadEventsAggregator.value = System.currentTimeMillis() }
  Data.elementTemplates.addChangeListener { event => reloadEventsAggregator.value = System.currentTimeMillis() }
  Observables.observeDelayedValue(aggregatorDelay, reloadEventsAggregator).addValueChangeListener(new IValueChangeListener {
    def handleValueChange(event: ValueChangeEvent) = Option(tree).foreach(_.reload())
  })
  // handle structural changes
  Observables.observeDelayedValue(aggregatorDelay, refreshEventsAggregator).addValueChangeListener(new IValueChangeListener {
    def handleValueChange(event: ValueChangeEvent) = Option(tree).foreach { tree =>
      val set = refreshEventsAggregator.value
      refreshEventsAggregator.value = Set()
      if (set.nonEmpty)
        tree.refresh(set.toArray)
    }
  })
  // handle presentation changes
  Observables.observeDelayedValue(aggregatorDelay, updateEventsAggregator).addValueChangeListener(new IValueChangeListener {
    def handleValueChange(event: ValueChangeEvent) = Option(tree).foreach { tree =>
      val set = updateEventsAggregator.value
      updateEventsAggregator.value = Set()
      if (set.nonEmpty)
        tree.update(set.toArray)
    }
  })

  def apply(parent: Composite, style: Int): Tree = {
    assert(tree == null, "Tree already initialized")
    tree = new Tree(parent, style)
    tree
  }
  /**
   * This function is invoked at application start
   */
  def start() = Element.Event.subscribe(elementEventsSubscriber)
  /**
   * This function is invoked at application stop
   */
  def stop() = Element.Event.removeSubscription(elementEventsSubscriber)

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
        element.eChildren.toArray
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
        element.eChildren.toArray
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
      case unknown =>
        log.fatal("Unknown element '%s' with type '%s'".format(unknown, unknown.getClass()))
        null
    }
  }
  class LabelProvider(propertyId: String, propertyMap: immutable.HashMap[Symbol, TemplateProperty[_ <: AnyRef with java.io.Serializable]]) extends CellLabelProvider {
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
  class LabelProviderID() extends CellLabelProvider {
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
    override def getToolTipDisplayDelayTime(obj: Object): Int = Dialog.ToolTipDisplayDelayTime
    /**
     * Return the amount of pixels in x and y direction that the tool tip to
     * pop up from the mouse pointer.
     */
    override def getToolTipShift(obj: Object): Point = Dialog.ToolTipShift
    /** The time in milliseconds the tool tip is shown for. */
    override def getToolTipTimeDisplayed(obj: Object): Int = Dialog.ToolTipTimeDisplayed
  }
  class OnActiveListener() extends PaintListener {
    /** Sent when a paint event occurs for the control. */
    def paintControl(e: PaintEvent) {
      if (tree.onActiveFlag.compareAndSet(true, false)) {
        tree.treeViewer.getControl.removePaintListener(tree.onActiveListener)
        tree.onActive()
      }
    }
  }
  /*
   * Actions
   */
  object ActionAutoResize extends Action(Messages.autoresize_key, IAction.AS_CHECK_BOX) {
    setChecked(true)
    override def run = if (isChecked()) Tree.tree.autoresize
  }
}
