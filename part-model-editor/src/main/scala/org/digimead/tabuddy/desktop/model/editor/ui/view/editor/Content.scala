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

import javax.inject.{ Inject, Named }
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.{ Messages ⇒ CMessages }
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.ResourceManager
import org.digimead.tabuddy.desktop.core.ui.definition.widget.VComposite
import org.digimead.tabuddy.desktop.core.ui.support.TreeProxy
import org.digimead.tabuddy.desktop.logic.Logic
import org.digimead.tabuddy.desktop.logic.payload.{ ElementTemplate, TemplateProperty }
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.desktop.model.editor.Messages
import org.digimead.tabuddy.model.element.Element
import org.eclipse.e4.core.contexts.Active
import org.eclipse.e4.core.di.annotations.Optional
import org.eclipse.jface.action.CoolBarManager
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.StyleRange
import org.eclipse.swt.events.{ DisposeEvent, DisposeListener }
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.widgets.{ Event, Listener, Widget }
import scala.collection.{ immutable, mutable }
import org.eclipse.jface.action.MenuManager

class Content @Inject() (val context: Context, parent: VComposite, @Named("style") style: Integer)
  extends ContentSkel(parent, style) with ContentActions with XLoggable {
  lazy val proxy = new TreeProxy(tree.treeViewer, Seq(table.proxyContent), tree.expandedItems)
  /** Table subview. */
  lazy val table = new Table(this, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL)
  /** Tree subview. */
  lazy val tree = new Tree(this, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL)
  /** View graph marker. */
  var graphMarker = Option.empty[GraphMarker]
  /** Limit of refresh elements quantity */
  val maximumElementsPerRefresh = 1000
  /** Limit of refresh elements quantity that updated */
  val maximumFilteredElementsPerRefresh = 100
  /** Root navigation ranges */
  var rootElementRanges = Seq[Content.RootPathLinkRange[_ <: Element]]()
  /** The table view menu */
  //  val tableViewMenu = {
  //    val menu = new MenuManager(Messages.tableView_text)
  //    menu.add(ActionModifyViewList)
  //    menu.add(ActionModifySortingList)
  //    menu.add(ActionModifyFilterList)
  //    menu.add(new Separator)
  //    menu.add(context.ActionToggleSystem)
  //    menu.add(context.ActionToggleExpand)
  //    menu
  //  }
  // Initialize editor view
  initializeUI()
  //initializeBindings()
  //initializeDefaults()

  /** Returns the view's parent, which must be a VComposite. */
  override def getParent(): VComposite = parent
  /** Get selected element for current context. */
  def getSelectedElement() = parent.getContext.flatMap(ctx ⇒ Option(ctx.get(Logic.Id.selectedElement).asInstanceOf[Element]))
  /** Invoked after App.Message.Set(_, marker: GraphMarker). */
  @Inject @Optional
  def onGraphMarkerAssigned(marker: GraphMarker) = App.exec {
    if (graphMarker != Some(marker)) {
      graphMarker = Some(marker)
      reload()
    }
  }
  /** Invoked at every modification of Payload.Id.selectedElement. */
  @Inject @Optional // @log
  def onSelectedElementChanged(@Named(Logic.Id.selectedElement) element: Element) =
    App.exec { updateActiveElement(element) }
  /**
   * Invoked at every modification of Payload.Id.selectedElement on active context.
   * This is allow to capture active element from neighbors.
   */
  @Inject @Optional // @log
  def onActiveSelectedElementChanged(@Active @Named(Logic.Id.selectedElement) element: Element) =
    if (false) App.exec { updateActiveElement(element) }
  /** onStart callback */
  def onStart(widget: Widget) = {}
  /** onStop callback */
  def onStop(widget: Widget) = {}
  /**
   * Refreshes this viewer starting with the given element. Labels are updated
   * as described in <code>refresh(boolean updateLabels)</code>.
   * <p>
   * Unlike the <code>update</code> methods, this handles structural changes
   * to the given element (e.g. addition or removal of children). If only the
   * given element needs updating, it is more efficient to use the
   * <code>update</code> methods.
   */
  def refresh(elementsForRefresh: Array[Element]) {
    log.debug("Refresh editor view: structural changes.")
    if (tree.treeViewer.getTree.getItemCount() == 0 || tree.treeViewer.getInput() == null) {
      log.debug("Skip refresh for the empty editor view.")
      return
    }
    if (graphMarker.isEmpty) {
      log.debug("Skip refresh for the empty model.")
      return
    }
    val elements = elementsForRefresh.distinct
    if (elements.size >= maximumElementsPerRefresh)
      return reload()
    /*
     * Search for top elements.
     */
    class SearchMap extends mutable.HashMap[Element, SearchMap]
    val treeRoot = tree.treeViewer.getInput().asInstanceOf[TreeProxy.Item]
    val searchMap = new SearchMap
    elements.foreach { element ⇒
      var pointer = searchMap
      util.control.Breaks.breakable {
        (element.eAncestors.reverse :+ element.eNode).foreach { ancestorOrElement ⇒
          pointer.get(ancestorOrElement.rootBox.e) match {
            case Some(ancestorOrElementMap) ⇒
              if (ancestorOrElementMap.nonEmpty) {
                if (ancestorOrElement == element)
                  pointer = new SearchMap // drop the longer sequence
                else
                  pointer = searchMap
              } else
                util.control.Breaks.break // ancestor is an element that is waiting for refresh
            case None ⇒
              val map = new SearchMap
              pointer(ancestorOrElement.rootBox.e) = map
              pointer = map
          }
        }
      }
    }
    // sequence of top elements
    def topElements(searchMap: SearchMap): Iterable[Element] = searchMap.map {
      case (key, value) ⇒
        if (value.isEmpty)
          Seq(key)
        else
          topElements(value)
    }.flatten
    // The element new name may affect to tree item position, so handle it as structural change
    // updating parent, not the element itself
    var topFiltered = topElements(searchMap).flatMap(el ⇒
      if (treeRoot.element == el) Option[Element](el) else el.eParent.map(_.rootBox.e)).toSeq.distinct
    if (topFiltered.size >= maximumFilteredElementsPerRefresh)
      return reload()
    // refresh
    log.debug("Refresh %d elements vs %d total: %s.".format(topFiltered.size, elements.size, topFiltered.mkString(",")))
    /*
     * Search for middle and bottom elements.
     */
    // unmodified ancestors of modified elements
    val middleItems = mutable.HashMap[Element, Seq[TreeProxy.Item]](topFiltered.map(el ⇒ (el, Seq())): _*)
    // modified elements itself
    val bottomElements = immutable.HashMap[Element, Seq[TreeProxy.Item]](topFiltered.map { topElement ⇒
      (topElement, elements.filter(el ⇒ el.ne(topElement) && {
        val ancestors = el.eAncestors
        if (ancestors.contains(topElement)) {
          middleItems(topElement) = middleItems(topElement) ++ ancestors.takeWhile(_ != topElement).map(node ⇒ TreeProxy.Item(node.rootBox.e))
          true
        } else false
      }).toSeq.map(TreeProxy.Item(_)))
    }: _*)
    /*
     * refresh
     */
    var refreshVisible = false
    Content.withRedrawDelayed(this) {
      topFiltered.foreach { topElement ⇒
        val topItem = TreeProxy.Item(topElement)
        val expanded = tree.expandedItems(topItem)
        if (topItem != treeRoot && (topElement.eNode.safeRead(_.children).nonEmpty || expanded)) {
          val ancestors = topElement.eAncestors
          val visibleAncestors = ancestors.takeWhile(_ == treeRoot.element)
          if (ancestors.size != visibleAncestors.size) {
            // treeRoot discovered
            if (ancestors.dropRight(1).forall(node ⇒ tree.expandedItems(TreeProxy.Item(node.rootBox.e)))) {
              proxy.onRefresh(topItem, middleItems(topElement).distinct, bottomElements(topElement), true)
            } else {
              log.debug(s"Refresh invisible ${topElement}.")
              tree.treeViewer.refresh(topItem, true)
              bottomElements(topElement).foreach(tree.treeViewer.refresh(_, true))
            }
          } else
            log.debug(s"Skip $topElement - the element is not belong to the current tree.")
        } else {
          proxy.onRefresh(topItem, middleItems(topElement).distinct, bottomElements(topElement), true)
        }
      }
      ActionAutoResize(false)
    }
  }
  /** Reload data. */
  def reload() {
    log.debug("Update editor content: major changes.")
    proxy.clearContent
    graphMarker match {
      case None ⇒
        log.debug("Update for empty graph.")
        tree.treeViewer.setInput(null)
        tree.treeViewer.getTree.clearAll(true)
        table.tableViewer.setInput(null)
        table.tableViewer.getTable.clearAll()
      case Some(marker) ⇒
        log.debug(s"Update for '${marker.graphModelId.name}' graph.")
        marker.safeRead { state ⇒
          /*
           * reload:
           *  Table.columnTemplate
           *  Table.columnLabelProvider
           */
          val propertyCache = mutable.HashMap[TemplateProperty[_ <: AnyRef with java.io.Serializable], Seq[ElementTemplate]]()
          val properties = state.payload.elementTemplates.values.flatMap { template ⇒
            template.properties.foreach {
              case (group, properties) ⇒ properties.foreach(property ⇒
                propertyCache(property) = propertyCache.get(property).getOrElse(Seq()) :+ template)
            }
            template.properties.flatMap(_._2)
          }.toList.groupBy(_.id.name.toLowerCase())
          val columnIds = List(Content.COLUMN_ID, Content.COLUMN_NAME) ++ properties.keys.filterNot(_ == Content.COLUMN_NAME).toSeq.sorted
          Table.columnTemplate = immutable.HashMap((for (columnId ← columnIds) yield columnId match {
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
          Table.columnLabelProvider = Table.columnTemplate.map {
            case (columnId, columnProperties) ⇒
              if (columnId == Content.COLUMN_ID)
                columnId -> new Table.TableLabelProviderID()
              else
                columnId -> new Table.TableLabelProvider(columnId, columnProperties)
          }
          updateColumns()
          // update content
          table.tableViewer.setInput(table.proxyContent.underlying)
          tree.treeViewer.setInput(TreeProxy.Item(state.graph.model))
          tree.treeViewer.setExpandedElements(tree.expandedItems.toArray)
          table.tableViewer.refresh()
        }
    }
  }
  /** Set selected element for current context. */
  def setSelectedElement(element: Element) = parent.getContext.flatMap(ctx ⇒ Option(ctx.set(Logic.Id.selectedElement, element)))
  /**
   * Updates the given elements' presentation when one or more of their properties change.
   * Only the given elements are updated.
   *
   * This does not handle structural changes (e.g. addition or removal of elements),
   * and does not update any other related elements (e.g. child elements).
   * To handle structural changes, use the refresh methods instead.
   */
  def update(elements: Array[Element]) {
    log.debug("Update editor view.")
    // The element new name may affect to tree item position, so handle it as structural change
    // tree.structuredViewerUpdateF(elements.map(TreeProxy.Item(_).asInstanceOf[AnyRef]).toArray, null) is inapplicable
    val elementsPerParent = (elements.flatMap(el ⇒ el.eParent.map(p ⇒ (p.rootBox.e, el))): Array[(Element, Element)]).
      groupBy(_._1): immutable.Map[Element, Array[(Element, Element)]]
    elementsPerParent.foreach {
      case (parent, parentAndElement) ⇒
        val parentItem = TreeProxy.Item(parent)
        proxy.onRefresh(parentItem, Seq(), parentAndElement.map(el ⇒ TreeProxy.Item(el._2)), true)
    }
    table.structuredViewerUpdateF(elements.map(TreeProxy.Item(_).asInstanceOf[AnyRef]).toArray, null)
  }

  /** Clear root path */
  protected[editor] def clearRootElement() {
    getTextRootElement.setText("")
    getTextRootElement.setToolTipText(null)
    rootElementRanges = Seq()
  }
  /** Allow external access for scala classes */
  override protected[editor] def getCoolBarManager(): CoolBarManager = super.getCoolBarManager()
  /** Allow external access for scala classes */
  override protected[editor] def getSashForm() = super.getSashForm
  /** Initialize editor view */
  protected def initializeUI() {
    // Initialize coolbar
    val coolBar = getCoolBarManager()
    bar.ElementBar.create(coolBar, context)
    coolBar.refresh()
    coolBar.update(true)
    // Initialize tree
    tree
    // Initialize table
    table
    // Initialize miscellaneous elements
    getBtnResetActiveElement.addListener(SWT.Selection, new Listener() {
      def handleEvent(event: Event) = Option(tree.treeViewer.getInput().asInstanceOf[TreeProxy.Item]) match {
        case Some(item) if item.hash != 0 ⇒ setSelectedElement(item.element)
        case _ ⇒ setSelectedElement(graphMarker.map(_.safeRead(_.graph.model)).getOrElse(null))
      }
    })
    // Jump for root links.
    getTextRootElement.addListener(SWT.MouseDown, new Listener() {
      def handleEvent(event: Event) = try {
        val offset = getTextRootElement.getOffsetAtLocation(new Point(event.x, event.y))
        val style = getTextRootElement.getStyleRangeAtOffset(offset)
        if (style != null && style.underline && style.underlineStyle == SWT.UNDERLINE_LINK)
          rootElementRanges.find(range ⇒ offset >= range.index && offset <= range.index + range.length).foreach { range ⇒
            setSelectedElement(range.element)
            val item = TreeProxy.Item(range.element)
            if (tree.treeViewer.getInput() != item) Content.withRedrawDelayed(Content.this) {
              tree.treeViewer.setInput(item)
              ActionAutoResize(false)
            }
          }
      } catch {
        case e: IllegalArgumentException ⇒ // no character under event.x, event.y
      }
    })
    // Update newly created view
    Content.withRedrawDelayed(this) {
      reload()
      ActionAutoResize(false)
      if (ActionToggleExpand.isChecked())
        Tree.expandAll(this)
      ActionToggleEmpty()
      ActionToggleSystem()
      ActionToggleIdentificators()
      ActionAutoResize(true)
    }
  }
  /** Recreate table columns with preserve table selected elements */
  protected def recreateSmart() {
    if (Table.columnLabelProvider.isEmpty)
      return // there are not column definitions yet
    val selection = table.tableViewer.getSelection()
    updateColumns
    table.tableViewer.refresh()
    table.tableViewer.setSelection(selection)
  }
  /** Recreate table columns */
  protected def updateColumns() = graphMarker.foreach { marker ⇒
    val disposedColumnsWidth = table.disposeColumns()
    val columnList = marker.safeRead { state ⇒
      getParent.getContext.flatMap(state.payload.getSelectedViewDefinition(_))
    }.map(selected ⇒ mutable.LinkedHashSet(Content.COLUMN_ID) ++
      (selected.fields.map(_.name) - Content.COLUMN_ID)).getOrElse(mutable.LinkedHashSet(Content.COLUMN_ID, Content.COLUMN_NAME))
    table.createColumns(columnList, disposedColumnsWidth)
  }
  /** Update active element status */
  protected[editor] def updateActiveElement(element: Element) {
    val item = TreeProxy.Item(element)
    val ancestorsN = 8
    val ancestors = element.eAncestors
    var n = 0
    val tooltipPrefix = if (ancestors.size > ancestorsN) {
      n += 1
      ": \n > ..."
    } else
      ":"
    val tooltip = (ancestors.take(ancestorsN).reverse :+ element.eNode).foldLeft(Messages.path_text + tooltipPrefix) { (acc, node) ⇒
      val separator = "\n " + " " * n + "> "
      n += 1
      acc + separator + node.id.name + ", " + node.rootBox.e.eGet[String]('name).map(value ⇒ "\"%s\"".format(value.get)).getOrElse(CMessages.untitled_text)
    }
    if (tree.treeViewer.getInput() == item) {
      // root
      val prefix = element.toString + " "
      val suffix = "[root]"
      val style = new StyleRange()
      style.fontStyle ^= SWT.ITALIC | SWT.BOLD
      style.foreground = ResourceManager.getColor(SWT.COLOR_DARK_YELLOW)
      getTextActiveElement.setText(prefix + suffix)
      getTextActiveElement.setStyleRanges(Array(prefix.length(), suffix.length()), Array(style))
      ActionElementNew.setEnabled(true)
      ActionElementEdit.setEnabled(false)
      ActionElementDelete.setEnabled(false)
    } else if (proxy.getContent.exists(_ == item)) {
      getTextActiveElement.setText(element.toString)
      ActionElementNew.setEnabled(true)
      ActionElementEdit.setEnabled(true)
      ActionElementDelete.setEnabled(true)
    } else {
      // hidden
      val prefix = element.toString + " "
      val suffix = "[hidden]"
      val style = new StyleRange()
      style.fontStyle ^= SWT.ITALIC | SWT.BOLD
      style.foreground = ResourceManager.getColor(SWT.COLOR_DARK_RED)
      getTextActiveElement.setText(prefix + suffix)
      getTextActiveElement.setStyleRanges(Array(prefix.length(), suffix.length()), Array(style))
      ActionElementNew.setEnabled(false)
      ActionElementEdit.setEnabled(false)
      ActionElementDelete.setEnabled(false)
    }
    getTextActiveElement.setToolTipText(tooltip)
    getBtnResetActiveElement.setEnabled(TreeProxy.Item(element) != tree.treeViewer.getInput())
  }
  /** Update root path */
  protected[editor] def updateRootElement(element: Element) {
    log.debug(s"Update root element to ${element}.")
    val ancestors = element.eAncestors
    val path = ancestors.reverse :+ element.eNode
    val style = new StyleRange()
    style.underline = true
    style.underlineStyle = SWT.UNDERLINE_LINK
    val separator = " > "
    val shift = separator.length
    var rangeIndex = 0
    var rangeShift = 0
    var rootElementLinks = Seq[Content.RootPathLinkRange[_ <: Element]]()
    val ranges = path.map { node ⇒
      val name = node.id.name
      val index = rangeIndex
      rangeIndex = rangeIndex + name.length + shift
      rootElementLinks = rootElementLinks :+ Content.RootPathLinkRange(node.rootBox.e, index, name.length)
      Array(index, name.length)
    }.flatten.toArray
    val styles = path.map(_ ⇒ style).toArray
    rootElementRanges = rootElementLinks
    getTextRootElement.setText(path.map(_.id.name).mkString(separator))
    getTextRootElement.setStyleRanges(ranges, styles)
  }
}

object Content extends XLoggable {
  /** The column special identifier */
  protected[editor] val COLUMN_ID = "id"
  /** The column special identifier */
  protected[editor] val COLUMN_NAME = "name"
  /** Structural changes(e.g. addition or removal of elements) aggregator */
  //private val refreshEventsExpandAggregator = WritableValue(Set[Element[_ <: Stash]]())
  /** All views. */
  val views = new ViewWeakHashMap // with mutable.SynchronizedMap[View, Unit]
  /** withRedraw counter that allows nested usage. It is valid only within UI thread. */
  protected var withRedrawCounter = 0

  /** Disable the redraw while updating */
  def withRedrawDelayed[T](view: Content)(f: ⇒ T): T = {
    App.assertEventThread()
    withRedrawCounter += 1
    if (withRedrawCounter == 1) {
      view.getSashForm.setRedraw(false)
      view.table.tableViewer.getTable.setRedraw(false)
      view.tree.treeViewer.getTree.setRedraw(false)
    }
    try {
      f
    } finally {
      if (withRedrawCounter == 1) {
        view.tree.treeViewer.getTree.setRedraw(true)
        view.table.tableViewer.getTable.setRedraw(true)
        view.getSashForm.setRedraw(true)
      }
      withRedrawCounter -= 1
    }
  }
  class ViewWeakHashMap extends mutable.WeakHashMap[Content, Unit] {
    override def +=(kv: (Content, Unit)): this.type = {
      val (key, value) = kv
      App.exec {
        key.addDisposeListener(new DisposeListener {
          def widgetDisposed(e: DisposeEvent) = ViewWeakHashMap.this -= key
        })
      }
      super.+=(kv)
    }
  }
  /** Range information about a link in the StyledTextRootElement */
  case class RootPathLinkRange[T <: Element](val element: T, val index: Int, val length: Int)
}
