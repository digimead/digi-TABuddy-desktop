/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2013-2014 Alexey Aksenov ezh@ezh.msk.ru
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

import com.ibm.icu.text.DateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.{ Messages ⇒ CMessages }
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.support.TreeProxy
import org.digimead.tabuddy.desktop.model.editor.{ Default, Messages }
import org.digimead.tabuddy.model.element.Element
import org.eclipse.jface.action.{ IMenuListener, IMenuManager, MenuManager, Separator }
import org.eclipse.jface.viewers.{ AbstractTreeViewer, CellLabelProvider, ColumnViewerToolTipSupport, ISelectionChangedListener, IStructuredSelection, ITreeContentProvider, SelectionChangedEvent, TreeViewer, TreeViewerColumn, Viewer, ViewerCell, ViewerFilter }
import org.eclipse.swt.SWT
import org.eclipse.swt.events.{ PaintEvent, PaintListener }
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.widgets.{ Event, Listener, Sash, TreeItem }
import scala.language.reflectiveCalls
import scala.ref.WeakReference

/**
 * Left part of the editor
 */
class Tree(protected[editor] val content: Content, style: Int)
  extends TreeActions with TreeFields with XLoggable {
  /** The auto resize lock */
  protected val autoResizeLock = new ReentrantLock()
  /** On active listener flag */
  protected val onActiveCalled = new AtomicBoolean(false)
  /** On active listener */
  protected val onActiveListener = new Tree.OnActiveListener(new WeakReference(this))
  /** Implicit value with current shell */
  implicit val shell = new java.lang.ref.WeakReference(content.getShell())
  /** The tree viewer update, scala bug SI-2991 */
  val structuredViewerUpdateF = {
    import scala.language.reflectiveCalls
    (treeViewer: { def update(a: Array[AnyRef], b: Array[String]): Unit }).update(_, _)
  }
  /** Internal table elements */
  protected lazy val tableElements = content.table.proxyContent.underlying
  /** The tree viewer */
  protected[editor] val treeViewer = create()
  /** Tree column width gap */
  protected lazy val treeColumnWidthGap = {
    val treeWidth = treeViewer.getTree.getBounds().width
    val columnWidth = treeViewer.getTree.getColumn(0).getWidth()
    treeWidth - columnWidth
  }

  /** Auto resize tree viewer */
  protected def autoresize(immediately: Boolean) = if (onActiveCalled.get)
    if (immediately)
      autoresizeUpdateControls()
    else if (autoResizeLock.tryLock()) try {
      Thread.sleep(50)
      App.execNGet { if (!treeViewer.getTree.isDisposed()) autoresizeUpdateControls() }
    } finally {
      autoResizeLock.unlock()
    }
  /** Auto resize control updater */
  protected def autoresizeUpdateControls() {
    val skipDither = 5
    val tree = treeViewer.getTree()
    val fromWeight = content.getSashForm.getWeights()
    val fromWidth = content.getSashForm.getChildren().map(_.getBounds.width)
    // pack
    val column = tree.getColumn(0)
    column.pack()
    // calculate shift
    val afterTreeWidth = column.getWidth() + Default.columnPadding + treeColumnWidthGap
    val shift = afterTreeWidth - fromWidth(0)
    if (math.abs(shift) <= skipDither)
      return
    // calculate weight
    val new0Weight = fromWidth(0).toDouble / fromWeight(0) * (fromWidth(0) + shift)
    val new1Weight = fromWidth(1).toDouble / fromWeight(1) * (fromWidth(1) - shift)
    val toWeight = if (new0Weight < 10000 && new1Weight < 10000)
      Array[Int]((new0Weight * 10000).toInt, (new1Weight * 10000).toInt) // reduce calculation error
    else
      Array[Int](new0Weight.toInt, new1Weight.toInt)
    content.getSashForm.setWeights(toWeight)
  }

  /** Create contents of the table. */
  protected def create(): TreeViewer = {
    log.debug("Create tree.")
    val treeViewer = new TreeViewer(content.getSashForm, style)
    val tree = treeViewer.getTree()
    tree.setData(Tree.widgetDataKey_WeakReferenceView, WeakReference(content))
    treeViewer.setUseHashlookup(true)
    treeViewer.getTree.setHeaderVisible(true)
    treeViewer.getTree.setLinesVisible(true)
    treeViewer.setContentProvider(new Tree.TreeContentProvider)
    // Create columns
    val treeViewerColumn = new TreeViewerColumn(treeViewer, SWT.LEFT)
    treeViewerColumn.setLabelProvider(Tree.TreeLabelProvider)
    treeViewerColumn.getColumn().setText(CMessages.identificator_text)
    treeViewerColumn.getColumn().pack
    // Activate the tooltip support for the viewer
    ColumnViewerToolTipSupport.enableFor(treeViewer)
    // Add the context menu
    val menuMgr = new MenuManager()
    val menu = menuMgr.createContextMenu(treeViewer.getControl)
    menuMgr.addMenuListener(new IMenuListener() {
      override def menuAboutToShow(manager: IMenuManager) {
        val selection = treeViewer.getSelection.asInstanceOf[IStructuredSelection]
        createMenu(manager, selection)
      }
    })
    menuMgr.setRemoveAllWhenShown(true)
    treeViewer.getControl.setMenu(menu)
    // Add selection listener
    treeViewer.addSelectionChangedListener(new ISelectionChangedListener() {
      override def selectionChanged(event: SelectionChangedEvent) = event.getSelection() match {
        case selection: IStructuredSelection if !selection.isEmpty() ⇒
          content.setSelectedElement(selection.getFirstElement().asInstanceOf[TreeProxy.Item].element)
        case selection ⇒
      }
    })
    // Add the expand/collapse listener
    tree.addListener(SWT.Collapse, new Listener() {
      override def handleEvent(e: Event) = Content.withRedrawDelayed(content) { onCollapse(e.item.asInstanceOf[TreeItem]) }
    })
    tree.addListener(SWT.Expand, new Listener() {
      override def handleEvent(e: Event) = Content.withRedrawDelayed(content) { onExpand(e.item.asInstanceOf[TreeItem]) }
    })
    tree.addPaintListener(onActiveListener)
    treeViewer
  }
  /** Create context menu for tree element */
  protected def createMenu(manager: IMenuManager, selection: IStructuredSelection) {
    Option(selection.getFirstElement().asInstanceOf[TreeProxy.Item]) match {
      case Some(item) ⇒
        // table menu
        val tableMenu = new MenuManager(Messages.table_text, null)
        tableMenu.add({
          val action = new ActionSelectInTable(item.element)
          val tableSelection = content.table.tableViewer.getSelection().asInstanceOf[IStructuredSelection]
          action.setEnabled(tableSelection.getFirstElement() != selection.getFirstElement())
          action
        })
        manager.add(tableMenu)
        // main menu
        manager.add(new ActionAsRoot(item.element))
        manager.add(new ActionExpand(item.element))
        manager.add(new ActionCollapse(item.element))
        manager.add(new Separator)
      case None ⇒
    }
    manager.add(ActionAutoResize)
    manager.add(new Separator)
    manager.add(ActionHideTree)
  }
  /** onActive callback */
  @log
  protected def onActive() {
    treeColumnWidthGap // initialize treeColumnWidthGap
    autoresize(true)
    // Add sash resize listener
    try {
      val privateSashesField = content.getSashForm.getClass.getDeclaredField("sashes")
      privateSashesField.setAccessible(true)
      privateSashesField.get(content.getSashForm).asInstanceOf[Array[Sash]].head.addListener(SWT.Selection, new Listener() {
        def handleEvent(e: Event) = ActionAutoResize.setChecked(false)
      })
    } catch {
      case e: Throwable ⇒
        log.warn("skip adding TableView sash resize listener: " + e.getMessage())
    }
  }
  /** onCollapse callback */
  @log
  protected def onCollapse(treeItem: TreeItem) {
    val item = treeItem.getData().asInstanceOf[TreeProxy.Item]
    if (expandedItems(item))
      content.proxy.onCollapse(item)
    content.getSelectedElement.foreach(content.updateActiveElement)
    content.ActionAutoResize(false)
  }
  /** onExpand callback */
  @log
  protected def onExpand(treeItem: TreeItem) {
    val item = treeItem.getData.asInstanceOf[TreeProxy.Item]
    if (!expandedItems(item))
      content.proxy.onExpand(item)
    // re expand children if needed
    val expand = treeItem.getItems().filter(child ⇒
      child.getData() != null && expandedItems(child.getData().asInstanceOf[TreeProxy.Item]))
    if (expand.nonEmpty) {
      App.exec {
        expand.foreach { item ⇒
          if (item.getItemCount() == 1 && item.getItems.head.getData() == null) {
            // expand invisible item after refresh
            treeViewer.setExpandedState(item.getData(), true)
          } else
            item.setExpanded(true)
          onExpand(item)
        }
      }
    }
    content.getSelectedElement.foreach(content.updateActiveElement)
    content.ActionAutoResize(false)
  }
  /** onInputChanged callback */
  @log
  protected def onInputChanged(item: TreeProxy.Item) {
    if (treeViewer.getTree().isDisposed())
      return
    content.proxy.onInputChanged(item)
    if (item == null) {
      content.clearRootElement()
      return
    }
    Tree.FilterSystemElement.updateSystemElement()
    Option(Tree.FilterSystemElement.systemElement).map(TreeProxy.Item(_)).foreach { systemItem ⇒
      if (content.ActionToggleSystem.isChecked())
        content.proxy.onUnfilter(systemItem)
      else
        content.proxy.onFilter(systemItem)
    }
    content.updateRootElement(item.element)
    content.getSelectedElement.foreach(content.updateActiveElement)
  }
}

object Tree extends XLoggable {
  /** Tree viewer data key with weak reference to view. */
  val widgetDataKey_WeakReferenceView = getClass.getName() + "#WeakReferenceView"

  /** Collapse the element. */
  def collapse(element: Element, recursively: Boolean, content: Content): Unit = {
    log.debug("collapse " + element)
    val item = TreeProxy.Item(element)
    if (recursively) {
      content.tree.treeViewer.collapseToLevel(item, AbstractTreeViewer.ALL_LEVELS)
      content.proxy.onCollapseRecursively(item)
    } else {
      content.tree.treeViewer.collapseToLevel(item, 1)
      content.proxy.onCollapse(item)
    }
    content.getSelectedElement.foreach(content.updateActiveElement)
  }
  /** Collapse all elements */
  def collapseAll(content: Content) = {
    log.debug("collapse all elements")
    content.tree.treeViewer.collapseAll()
    content.proxy.onCollapseAll()
    content.getSelectedElement.foreach(content.updateActiveElement)
  }
  /** Expand the element. */
  def expand(element: Element, recursively: Boolean, content: Content): Unit = {
    log.debug("expand " + element)
    val item = TreeProxy.Item(element)
    if (recursively)
      content.tree.treeViewer.expandToLevel(item, AbstractTreeViewer.ALL_LEVELS)
    else
      content.tree.treeViewer.expandToLevel(item, 1)
    def expandItem(item: TreeProxy.Item): Unit = {
      if (!content.tree.expandedItems(item)) {
        if (recursively)
          content.proxy.onExpandRecursively(item)
        else
          content.proxy.onExpand(item)
      } else {
        if (recursively)
          for (childNode ← item.element.eNode.safeRead(_.children))
            expandItem(TreeProxy.Item(childNode.rootBox.e))
      }
    }
    expandItem(item)
    content.getSelectedElement.foreach(content.updateActiveElement)
  }
  /** Expand all elements */
  def expandAll(content: Content): Unit = {
    log.debug("expand all elements")
    val tableSelection = content.table.tableViewer.getSelection()
    content.tree.treeViewer.expandAll()
    content.proxy.onExpandAll()
    content.table.tableViewer.setSelection(tableSelection)
    content.getSelectedElement.foreach(content.updateActiveElement)
  }
  /** Toggle system elements filter */
  def toggleSystemElementsFilter(enableFilter: Boolean, content: Content) = {
    val filters = content.tree.treeViewer.getFilters()
    Tree.FilterSystemElement.updateSystemElement()
    if (enableFilter) {
      if (!filters.contains(FilterSystemElement)) {
        content.tree.treeViewer.setFilters(filters :+ FilterSystemElement)
        Option(FilterSystemElement.systemElement).map(TreeProxy.Item(_)).foreach { systemItem ⇒
          content.proxy.onFilter(systemItem)
        }
      }
    } else {
      if (filters.contains(FilterSystemElement)) {
        content.tree.treeViewer.setFilters(filters.filterNot(_ == FilterSystemElement))
        Option(FilterSystemElement.systemElement).map(TreeProxy.Item(_)).foreach { systemItem ⇒
          content.proxy.onUnfilter(systemItem)
        }
      }
    }
    content.getSelectedElement.foreach(content.updateActiveElement)
  }
  /** Toggle auto expand */
  def toggleAutoExpand(enable: Boolean, content: Content) = {
    if (enable)
      content.tree.treeViewer.setAutoExpandLevel(AbstractTreeViewer.ALL_LEVELS)
    else
      content.tree.treeViewer.setAutoExpandLevel(0)
  }

  object FilterSystemElement extends ViewerFilter {
    var systemElement: Element = null // updated only from UI thread

    override def select(viewer: Viewer, parentElement: Object, element: Object): Boolean =
      element.asInstanceOf[TreeProxy.Item].element.ne(systemElement)
    def updateSystemElement(): Unit = {} // DependencyInjection.get.map(di =>)
    //      App.exec { systemElement = di.inject[Record.Interface[_ <: Record.Stash]](Some("eTABuddy")).eParent.getOrElse(null) }
  }
  class OnActiveListener(tree: WeakReference[Tree]) extends PaintListener {
    /** Sent when a paint event occurs for the control. */
    def paintControl(e: PaintEvent) = tree.get.foreach { tree ⇒
      if (tree.onActiveCalled.compareAndSet(false, true)) {
        tree.treeViewer.getControl.removePaintListener(tree.onActiveListener)
        tree.onActive()
      }
    }
  }
  class TreeContentProvider extends ITreeContentProvider {
    /**
     * Disposes of this content provider.
     * This is called by the viewer when it is disposed.
     */
    def dispose() {}
    /** Returns whether the given element has children. */
    def hasChildren(element: AnyRef): Boolean = element match {
      case element: Element ⇒
        element.eNode.safeRead(_.children).nonEmpty
      case item: TreeProxy.Item ⇒
        item.element.eNode.safeRead(_.children).nonEmpty
      case unknown ⇒
        log.fatal("Unknown item '%s' with type '%s'".format(unknown, unknown.getClass()))
        false
    }
    /**
     * Notifies this content provider that the given viewer's input
     * has been switched to a different element.
     */
    def inputChanged(v: Viewer, oldInput: Object, newInput: Object) = App.exec {
      Option(v.getControl().getData(Tree.widgetDataKey_WeakReferenceView)) match {
        case Some(viewRef: WeakReference[_]) ⇒
          viewRef.get.asInstanceOf[Option[Content]].foreach { view ⇒
          println("!!!!!!!!!!+++ " + newInput)
            if (view.tree.treeViewer.getInput() == newInput)
              log.warn("JFace bug, WTF? event fired, but input is not changed")
            view.tree.onInputChanged(newInput.asInstanceOf[TreeProxy.Item])
          }
        case _ ⇒
          log.fatal("Unable to find view reference.")
      }
    }
    /** Returns the child elements of the given parent element. */
    def getChildren(parent: AnyRef): Array[AnyRef] = parent match {
      case element: Element ⇒
        element.eNode.safeRead(_.children).toArray.sortBy(_.id.name).map(node ⇒ TreeProxy.Item(node.rootBox.e)).asInstanceOf[Array[AnyRef]]
      case item: TreeProxy.Item ⇒
        item.element.eNode.safeRead(_.children).toArray.sortBy(_.id.name).map(node ⇒ TreeProxy.Item(node.rootBox.e)).asInstanceOf[Array[AnyRef]]
      case unknown ⇒
        log.fatal("Unknown item '%s' with type '%s'".format(unknown, unknown.getClass()))
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
      case element: Element ⇒
        element.eNode.safeRead(_.children).toArray.sortBy(_.id.name).map(node ⇒ TreeProxy.Item(node.rootBox.e)).asInstanceOf[Array[AnyRef]]
      case item: TreeProxy.Item ⇒
        item.element.eNode.safeRead(_.children).toArray.sortBy(_.id.name).map(node ⇒ TreeProxy.Item(node.rootBox.e)).asInstanceOf[Array[AnyRef]]
      case unknown ⇒
        log.fatal("Unknown item '%s' with type '%s'".format(unknown, unknown.getClass()))
        Array()
    }
    /**
     * Returns the parent for the given element, or <code>null</code>
     * indicating that the parent can't be computed.
     * In this case the tree-structured viewer can't expand
     * a given node correctly if requested.
     */
    def getParent(element: AnyRef): AnyRef = element match {
      case element: Element ⇒
        element.eParent.map(node ⇒ TreeProxy.Item(node.rootBox.e)).getOrElse(null)
      case item: TreeProxy.Item ⇒
        item.element.eParent.map(node ⇒ TreeProxy.Item(node.rootBox.e)).getOrElse(null)
      case item: TreeItem ⇒
        item.getData().asInstanceOf[Element].eParent.map(node ⇒ TreeProxy.Item(node.rootBox.e)).getOrElse(null)
      case unknown ⇒
        log.fatal("Unknown item '%s' with type '%s'".format(unknown, unknown.getClass()))
        null
    }
  }
  object TreeLabelProvider extends CellLabelProvider {
    val dfg = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL)

    /** Update the label for cell. */
    override def update(cell: ViewerCell) = cell.getElement() match {
      case item: TreeProxy.Item ⇒
        cell.setText(item.element.eId.name)
      case unknown ⇒
        log.fatal("Unknown element '%s' with type '%s'".format(unknown, unknown.getClass()))
    }
    /** Get the text displayed in the tool tip for object. */
    override def getToolTipText(obj: Object): String = obj match {
      case item: TreeProxy.Item ⇒
        Messages.lastModification_text.format(dfg.format(new Date(item.element.eStash.modified.milliseconds)))
      case unknown ⇒
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
  }
}
