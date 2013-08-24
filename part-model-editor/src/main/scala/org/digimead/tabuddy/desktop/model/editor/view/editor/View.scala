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

package org.digimead.tabuddy.desktop.model.editor.view.editor

import scala.Array.canBuildFrom
import scala.Array.fallbackCanBuildFrom
import scala.Option.option2Iterable
import scala.collection.immutable
import scala.collection.mutable
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Messages
import org.digimead.tabuddy.desktop.ResourceManager
import org.digimead.tabuddy.desktop.gui.widget.VComposite
import org.digimead.tabuddy.desktop.logic.Data
import org.digimead.tabuddy.desktop.logic.payload.Payload
import org.digimead.tabuddy.desktop.logic.payload.api.ElementTemplate
import org.digimead.tabuddy.desktop.logic.payload.api.TemplateProperty
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.TreeProxy
import org.digimead.tabuddy.desktop.support.WritableList.wrapper2underlying
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Model.model2implementation
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.element.Stash
import org.eclipse.e4.core.contexts.Active
import org.eclipse.e4.core.contexts.ContextInjectionFactory
import org.eclipse.e4.core.di.annotations.Optional
import org.eclipse.jface.action.CoolBarManager
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.StyleRange
import org.eclipse.swt.events.DisposeEvent
import org.eclipse.swt.events.DisposeListener
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.widgets.Widget
import javax.inject.Inject
import javax.inject.Named
import org.eclipse.swt.widgets.Listener
import org.eclipse.swt.widgets.Event
import org.eclipse.swt.graphics.Point

class View(parent: VComposite, style: Int)
  extends TableViewSkel(parent, style) with ViewActions with Loggable {
  lazy val proxy = new TreeProxy(tree.treeViewer, Seq(table.content), tree.expandedItems)
  /** Table subview. */
  lazy val table = new Table(this, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL)
  /** Tree subview. */
  lazy val tree = new Tree(this, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL)
  /** Limit of refresh elements quantity */
  val maximumElementsPerRefresh = 1000
  /** Limit of refresh elements quantity that updated */
  val maximumFilteredElementsPerRefresh = 100
  /** Root navigation ranges */
  var rootElementRanges = Seq[View.RootPathLinkRange[_ <: Element.Generic]]()
  /** The table view menu */
  /*val tableViewMenu = {
    val menu = new MenuManager(Messages.tableView_text)
    menu.add(ActionModifyViewList)
    menu.add(ActionModifySortingList)
    menu.add(ActionModifyFilterList)
    menu.add(new Separator)
    menu.add(context.ActionToggleSystem)
    menu.add(context.ActionToggleExpand)
    menu
  }*/
  // Initialize editor view
  initialize()

  /** Returns the view's parent, which must be a VComposite. */
  override def getParent(): VComposite = parent
  /** Get selected element for current context. */
  def getSelectedElement() = Option(parent.getContext.get(Data.Id.selectedElement).asInstanceOf[Element[Stash]])
  /** Invoked at every modification of Data.Id.selectedElement. */
  @Inject @Optional // @log
  def onSelectedElementChanged(@Named(Data.Id.selectedElement) element: Element.Generic) =
    App.exec { updateActiveElement(element) }
  /**
   * Invoked at every modification of Data.Id.selectedElement on active context.
   * This is allow to capture active element from neighbors.
   */
  @Inject @Optional // @log
  def onActiveSelectedElementChanged(@Active @Named(Data.Id.selectedElement) element: Element.Generic) =
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
  def refresh(elementsForRefresh: Array[Element.Generic]) {
    log.debug("Refresh editor view: structural changes.")
    if (tree.treeViewer.getTree.getItemCount() == 0 || tree.treeViewer.getInput() == null) {
      log.debug("Skip refresh for empty editor view.")
      return
    }
    if (Model.eId == Payload.defaultModel.eId) {
      log.debug("Skip refresh for default model.")
      return
    }
    val elements = elementsForRefresh.distinct
    if (elements.size >= maximumElementsPerRefresh)
      return reload()
    /*
     * search for top elements
     */
    class SearchMap extends mutable.HashMap[Element.Generic, SearchMap]
    val treeRoot = tree.treeViewer.getInput().asInstanceOf[TreeProxy.Item]
    val searchMap = new SearchMap
    elements.foreach { element =>
      var pointer = searchMap
      util.control.Breaks.breakable {
        (element.eAncestors.reverse :+ element).foreach { ancestorOrElement =>
          pointer.get(ancestorOrElement) match {
            case Some(ancestorOrElementMap) =>
              if (ancestorOrElementMap.nonEmpty) {
                if (ancestorOrElement == element)
                  pointer = new SearchMap // drop the longer sequence
                else
                  pointer = searchMap
              } else
                util.control.Breaks.break // ancestor is an element that is waiting for refresh
            case None =>
              val map = new SearchMap
              pointer(ancestorOrElement) = map
              pointer = map
          }
        }
      }
    }
    // sequence of top elements
    def topElements(searchMap: SearchMap): Iterable[Element.Generic] = searchMap.map {
      case (key, value) =>
        if (value.isEmpty)
          Seq(key)
        else
          topElements(value)
    }.flatten
    // The element new name may affect to tree item position, so handle it as structural change
    // updating parent, not the element itself
    var topFiltered = topElements(searchMap).flatMap(el =>
      if (treeRoot.element == el) Option[Element.Generic](el) else el.eParent).toSeq.distinct
    if (topFiltered.size >= maximumFilteredElementsPerRefresh)
      return reload()
    // refresh
    log.debug("Refresh %d elements vs %d total: %s.".format(topFiltered.size, elements.size, topFiltered.mkString(",")))
    /*
     * search for middle and bottom elements
     */
    // unmodified ancestors of modified elements
    val middleItems = mutable.HashMap[Element.Generic, Seq[TreeProxy.Item]](topFiltered.map(el => (el, Seq())): _*)
    // modified elements itself
    val bottomElements = immutable.HashMap[Element.Generic, Seq[TreeProxy.Item]](topFiltered.map { topElement =>
      (topElement, elements.filter(el => el.ne(topElement) && {
        val ancestors = el.eAncestors
        if (ancestors.contains(topElement)) {
          middleItems(topElement) = middleItems(topElement) ++ ancestors.takeWhile(_ != topElement).map(TreeProxy.Item(_))
          true
        } else false
      }).toSeq.map(TreeProxy.Item(_)))
    }: _*)
    /*
     * refresh
     */
    var refreshVisible = false
    View.withRedrawDelayed(this) {
      topFiltered.foreach { topElement =>
        val topItem = TreeProxy.Item(topElement)
        val expanded = tree.expandedItems(topItem)
        if (topItem != treeRoot && (topElement.eChildren.nonEmpty || expanded)) {
          val ancestors = topElement.eAncestors
          val visibleAncestors = ancestors.takeWhile(_ == treeRoot.element)
          if (ancestors.size != visibleAncestors.size) {
            // treeRoot discovered
            if (ancestors.dropRight(1).forall(el => tree.expandedItems(TreeProxy.Item(el)))) {
              proxy.onRefresh(topItem, middleItems(topElement).distinct, bottomElements(topElement))
            } else {
              log.debug(s"Refresh invisible ${topElement}.")
              tree.treeViewer.refresh(topItem, true)
              bottomElements(topElement).foreach(tree.treeViewer.refresh(_, true))
            }
          } else
            log.debug(s"Skip $topElement - the element is not belong to the current tree.")
        } else {
          proxy.onRefresh(topItem, middleItems(topElement).distinct, bottomElements(topElement))
        }
      }
      ActionAutoResize(false)
    }
  }
  /** Reload data. */
  def reload() {
    log.debug("Update editor view: major changes.")
    proxy.clearContent
    if (Model.eId == Payload.defaultModel.eId) {
      log.debug("Update for defalt model.")
      tree.treeViewer.setInput(null)
      tree.treeViewer.getTree.clearAll(true)
      table.tableViewer.setInput(null)
      table.tableViewer.getTable.clearAll()
    } else {
      log.debug(s"Update for ${Model.eId} model.")
      /*
       * reload:
       *  Table.columnTemplate
       *  Table.columnLabelProvider
       */
      val propertyCache = mutable.HashMap[TemplateProperty[_ <: AnyRef with java.io.Serializable], Seq[ElementTemplate]]()
      val properties = Data.elementTemplates.values.flatMap { template =>
        template.properties.foreach {
          case (group, properties) => properties.foreach(property =>
            propertyCache(property) = propertyCache.get(property).getOrElse(Seq()) :+ template)
        }
        template.properties.flatMap(_._2)
      }.toList.groupBy(_.id.name.toLowerCase())
      val columnIds = List(View.COLUMN_ID, View.COLUMN_NAME) ++ properties.keys.filterNot(_ == View.COLUMN_NAME).toSeq.sorted
      Table.columnTemplate = immutable.HashMap((for (columnId <- columnIds) yield columnId match {
        case id if id == View.COLUMN_ID =>
          (View.COLUMN_ID, immutable.HashMap[Symbol, TemplateProperty[_ <: AnyRef with java.io.Serializable]]())
        case id if id == View.COLUMN_NAME =>
          (View.COLUMN_NAME, immutable.HashMap(properties.get(View.COLUMN_NAME).getOrElse(List()).map { property =>
            propertyCache(property).map(template => (template.id, property))
          }.flatten: _*))
        case _ =>
          (columnId, immutable.HashMap(properties(columnId).map { property =>
            propertyCache(property).map(template => (template.id, property))
          }.flatten: _*))
      }): _*)
      Table.columnLabelProvider = Table.columnTemplate.map {
        case (columnId, columnProperties) =>
          if (columnId == View.COLUMN_ID)
            columnId -> new Table.TableLabelProviderID()
          else
            columnId -> new Table.TableLabelProvider(columnId, columnProperties)
      }
      updateColumns()
      // update content
      table.tableViewer.setInput(table.content.underlying)
      tree.treeViewer.setInput(TreeProxy.Item(Model.inner))
      tree.treeViewer.setExpandedElements(tree.expandedItems.toArray)
      table.tableViewer.refresh()
    }
  }
  /** Set selected element for current context. */
  def setSelectedElement(element: Element.Generic) = parent.getContext.set(Data.Id.selectedElement, element)
  /**
   * Updates the given elements' presentation when one or more of their properties change.
   * Only the given elements are updated.
   *
   * This does not handle structural changes (e.g. addition or removal of elements),
   * and does not update any other related elements (e.g. child elements).
   * To handle structural changes, use the refresh methods instead.
   */
  def update(elements: Array[Element.Generic]) {
    log.debug("Update editor view.")
    // The element new name may affect to tree item position, so handle it as structural change
    // tree.structuredViewerUpdateF(elements.map(TreeProxy.Item(_).asInstanceOf[AnyRef]).toArray, null) is inapplicable
    val elementsPerParent = (elements.flatMap(el => el.eParent.map(p => (p, el))): Array[(Element.Generic, Element.Generic)]).
      groupBy(_._1): immutable.Map[Element.Generic, Array[(Element.Generic, Element.Generic)]]
    elementsPerParent.foreach {
      case (parent, parentAndElement) =>
        val parentItem = TreeProxy.Item(parent)
        proxy.onRefresh(parentItem, Seq(), parentAndElement.map(el => TreeProxy.Item(el._2)))
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
  protected def initialize() {
    // Initialize injection from view context after everything is ready.
    App.execAsync { ContextInjectionFactory.inject(View.this, getParent.getContext) }
    setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1))
    // initialize tree
    tree
    // initialize table
    table
    // initialize miscellaneous elements
    getBtnResetActiveElement.addListener(SWT.Selection, new Listener() {
      def handleEvent(event: Event) = Option(tree.treeViewer.getInput().asInstanceOf[TreeProxy.Item]) match {
        case Some(item) if item.hash != 0 => setSelectedElement(item.element)
        case _ => setSelectedElement(Model)
      }
    })
    // Jump for root links.
    getTextRootElement.addListener(SWT.MouseDown, new Listener() {
      def handleEvent(event: Event) = try {
        val offset = getTextRootElement.getOffsetAtLocation(new Point(event.x, event.y))
        val style = getTextRootElement.getStyleRangeAtOffset(offset)
        if (style != null && style.underline && style.underlineStyle == SWT.UNDERLINE_LINK)
          rootElementRanges.find(range => offset >= range.index && offset <= range.index + range.length).foreach { range =>
            setSelectedElement(range.element)
            val item = TreeProxy.Item(range.element)
            if (tree.treeViewer.getInput() != item) View.withRedrawDelayed(View.this) {
              tree.treeViewer.setInput(item)
              ActionAutoResize(false)
            }
          }
      } catch {
        case e: IllegalArgumentException => // no character under event.x, event.y
      }
    })
    View.views += this -> {}
    // Update newly created view
    View.withRedrawDelayed(this) {
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
  protected def updateColumns() {
    val disposedColumnsWidth = table.disposeColumns()
    val columnList = Data.getSelectedViewDefinition(getParent.getContext).map(selected => mutable.LinkedHashSet(View.COLUMN_ID) ++
      (selected.fields.map(_.name) - View.COLUMN_ID)).getOrElse(mutable.LinkedHashSet(View.COLUMN_ID, View.COLUMN_NAME))
    table.createColumns(columnList, disposedColumnsWidth)
  }
  /** Update active element status */
  protected[editor] def updateActiveElement(element: Element.Generic) {
    val item = TreeProxy.Item(element)
    val ancestorsN = 8
    val ancestors = element.eAncestors
    var n = 0
    val tooltipPrefix = if (ancestors.size > ancestorsN) {
      n += 1
      ": \n > ..."
    } else
      ":"
    val tooltip = (ancestors.take(ancestorsN).reverse :+ element).foldLeft(Messages.path_text + tooltipPrefix) { (acc, element) =>
      val separator = "\n " + " " * n + "> "
      n += 1
      acc + separator + element.eId.name + ", " + element.eGet[String]('name).map(value => "\"%s\"".format(value.get)).getOrElse(Messages.untitled_text)
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
  protected[editor] def updateRootElement(element: Element.Generic) {
    log.debug(s"Update root element to ${element}.")
    val ancestors = element.eAncestors
    val path = ancestors.reverse :+ element
    val style = new StyleRange()
    style.underline = true
    style.underlineStyle = SWT.UNDERLINE_LINK
    val separator = " > "
    val shift = separator.length
    var rangeIndex = 0
    var rangeShift = 0
    var rootElementLinks = Seq[View.RootPathLinkRange[_ <: Element.Generic]]()
    val ranges = path.map { element =>
      val name = element.eId.name
      val index = rangeIndex
      rangeIndex = rangeIndex + name.length + shift
      rootElementLinks = rootElementLinks :+ View.RootPathLinkRange(element, index, name.length)
      Array(index, name.length)
    }.flatten.toArray
    val styles = path.map(_ => style).toArray
    rootElementRanges = rootElementLinks
    getTextRootElement.setText(path.map(_.eId.name).mkString(separator))
    getTextRootElement.setStyleRanges(ranges, styles)
  }
}

object View extends Loggable {
  /** The column special identifier */
  protected[editor] val COLUMN_ID = "id"
  /** The column special identifier */
  protected[editor] val COLUMN_NAME = "name"
  /** Structural changes(e.g. addition or removal of elements) aggregator */
  //private val refreshEventsExpandAggregator = WritableValue(Set[Element[_ <: Stash]]())
  /** All views. */
  val views = new ViewWeakHashMap with mutable.SynchronizedMap[View, Unit]
  /** withRedraw counter that allows nested usage. It is valid only within UI thread. */
  protected var withRedrawCounter = 0
  /** Create editor view */
  /*def apply(parent: VComposite, style: Int): View = {
    // handle view changes
    /*view.context.toolbarView.view.addChangeListener { (_, _) =>
      withRedrawDelayed(view) {
        view.recreateSmart
        view.context.ActionAutoResize(true)
      }
    }
    // handle sorting changes
    view.context.toolbarView.sorting.addChangeListener { (_, _) =>
      withRedrawDelayed(view) {
        view.table.onSortingChanged
      }
    }
    // handle filter changes
    view.context.toolbarView.filter.addChangeListener { (f, _) =>
      withRedrawDelayed(view) {
        view.table.tableViewer.refresh()
      }
    }*/
  }*/
  /** Disable the redraw while updating */
  def withRedrawDelayed[T](view: View)(f: => T): T = {
    App.assertUIThread()
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
  class ViewWeakHashMap extends mutable.WeakHashMap[View, Unit] {
    override def +=(kv: (View, Unit)): this.type = {
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
  case class RootPathLinkRange[T <: Element.Generic](val element: T, val index: Int, val length: Int)
}
