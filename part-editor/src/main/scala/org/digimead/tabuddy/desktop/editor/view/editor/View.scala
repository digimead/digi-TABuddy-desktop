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
import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.collection.immutable
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future
import scala.ref.WeakReference
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Messages
import org.digimead.tabuddy.desktop.logic.Data
import org.digimead.tabuddy.desktop.logic.payload.Payload
import org.digimead.tabuddy.desktop.logic.payload.api.ElementTemplate
import org.digimead.tabuddy.desktop.logic.payload.api.TemplateProperty
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.TreeProxy
import org.digimead.tabuddy.desktop.support.WritableValue
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.element.Stash
import org.eclipse.core.databinding.observable.ChangeEvent
import org.eclipse.core.databinding.observable.IChangeListener
import org.eclipse.core.databinding.observable.Observables
import org.eclipse.core.databinding.observable.value.IValueChangeListener
import org.eclipse.core.databinding.observable.value.ValueChangeEvent
import org.eclipse.jface.action.Action
import org.eclipse.jface.action.CoolBarManager
import org.eclipse.jface.action.IAction
import org.eclipse.jface.action.MenuManager
import org.eclipse.jface.action.Separator
import org.eclipse.jface.databinding.swt.WidgetProperties
import org.eclipse.jface.util.ConfigureColumns
import org.eclipse.jface.viewers.CellLabelProvider
import org.eclipse.jface.viewers.TreeViewer
import org.eclipse.jface.viewers.ViewerCell
import org.eclipse.jface.viewers.ViewerFilter
import org.eclipse.jface.window.SameShellProvider
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.StyleRange
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Event
import org.eclipse.swt.widgets.Listener
import org.eclipse.swt.widgets.Shell
import org.eclipse.swt.widgets.Widget
import org.digimead.tabuddy.desktop.gui.widget.VComposite
import com.escalatesoft.subcut.inject.Inject
import org.eclipse.e4.core.di.annotations.Optional
import org.eclipse.e4.core.contexts.Active
import javax.inject.Named
import org.digimead.tabuddy.desktop.ResourceManager

class View private (parent: VComposite, style: Int)
  extends TableViewSkel(parent, style) with ViewActions with Loggable {
  lazy val title = Messages.tableView_text
  lazy val description = "Tree View"
  protected[editor] lazy val table = new Table(this, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL)
  protected[editor] lazy val tree = new Tree(this, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL)
  protected[editor] lazy val proxy = new TreeProxy(tree.treeViewer, Seq(table.content), tree.expandedItems)
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
  def getSelectedElementUserInput() = Option(parent.getContext.get(Data.Id.selectedElementUserInput).asInstanceOf[Element[Stash]])
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
    setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1))
    // initialize tree
    tree
    // initialize table
    table
    // initialize toolbars
    //getCoolBarManager.add(context.toolbarView)
    //getCoolBarManager.add(context.toolbarPrimary)
    //getCoolBarManager.add(context.toolbarSecondary)
    //getCoolBarManager.update(true)
    // initialize miscellaneous elements
    /*Data.fieldElement.addChangeListener { (element, event) => updateActiveElement(element) }*/
    getBtnResetActiveElement.addListener(SWT.Selection, new Listener() {
      def handleEvent(event: Event) = Option(tree.treeViewer.getInput().asInstanceOf[TreeProxy.Item]) match {
        case Some(item) if item.hash != 0 => setSelectedElementUserInput(item.element)
        case _ => setSelectedElementUserInput(Model)
      }
    })
    getTextRootElement.addListener(SWT.MouseDown, new Listener() {
      def handleEvent(event: Event) = try {
        val offset = getTextRootElement.getOffsetAtLocation(new Point(event.x, event.y))
        val style = getTextRootElement.getStyleRangeAtOffset(offset)
        if (style != null && style.underline && style.underlineStyle == SWT.UNDERLINE_LINK)
          rootElementRanges.find(range => offset >= range.index && offset <= range.index + range.length).foreach { range =>
            setSelectedElementUserInput(range.element)
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
    // reload data
    View.withRedrawDelayed(this) {
      reload
      ActionAutoResize(false)
    }
    View.views += this -> {}
  }
  /** Invoked at every modification of Data.Id.modelIdUserInput. */
  @Inject @Optional // @log
  def onModelIdUserInputChanged(@Named(Data.Id.selectedElementUserInput) element: Element[_ <: Stash]) =
    App.exec { updateActiveElement(element) }
  /** onStart callback */
  def onStart() {
    log.___gaze("ONSTART")
    //    Window.getMenuTopLevel().add(tableViewMenu)
    //    Window.getMenuTopLevel().update(false)
    this.layout()
  }
  /** onStop callback */
  def onStop() {
    log.___gaze("ONSTOP")
    //    Window.getMenuTopLevel().remove(tableViewMenu)
    //    Window.getMenuTopLevel().update(false)
  }
  /** Set selected element for current context. */
  def setSelectedElementUserInput(element: Element[_ <: Stash]) = parent.getContext.set(Data.Id.selectedElementUserInput, element)

  /**
   * Refreshes this viewer starting with the given element. Labels are updated
   * as described in <code>refresh(boolean updateLabels)</code>.
   * <p>
   * Unlike the <code>update</code> methods, this handles structural changes
   * to the given element (e.g. addition or removal of children). If only the
   * given element needs updating, it is more efficient to use the
   * <code>update</code> methods.
   */
  protected def refresh(elementsForRefresh: Array[Element.Generic]) {
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
  /** Reload data */
  protected def reload() {
    log.debug("Update editor view: major changes.")
    proxy.clearContent
    if (Model.eId == Payload.defaultModel.eId) {
      log.debug("Update for defalt model.")
      tree.treeViewer.setInput(null)
      tree.treeViewer.getTree.clearAll(true)
      table.tableViewer.setInput(null)
      table.tableViewer.getTable.clearAll()
      return
    }
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
  /** Recreate table columns with preserve table selected elements */
  protected def recreateSmart() {
    if (Table.columnLabelProvider.isEmpty)
      return // there are not column definitions yet
    val selection = table.tableViewer.getSelection()
    updateColumns
    table.tableViewer.refresh()
    table.tableViewer.setSelection(selection)
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
  /** Recreate table columns */
  protected def updateColumns() {
        val disposedColumnsWidth = table.disposeColumns()
    //val columnList = context.toolbarView.view.value.map(selected => mutable.LinkedHashSet(TableView.COLUMN_ID) ++
    //  (selected.fields.map(_.name) - TableView.COLUMN_ID)).getOrElse(mutable.LinkedHashSet(TableView.COLUMN_ID, TableView.COLUMN_NAME))
    //table.createColumns(columnList, disposedColumnsWidth)
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
  /** Aggregation listener delay */
  private val aggregatorDelay = 250 // msec
  /** Shell -> PerShellContext map */
  //private val contextMap = new mutable.WeakHashMap[Shell, PerShellContext] with mutable.SynchronizedMap[Shell, PerShellContext]
  /** Significant changes(schema modification, model reloading,...) aggregator */
  private val reloadEventsAggregator = WritableValue(Long.box(0L))
  /** Structural changes(e.g. addition or removal of elements) aggregator */
  private val refreshEventsAggregator = WritableValue(Set[Element[_ <: Stash]]())
  /** Structural changes(e.g. addition or removal of elements) aggregator */
  private val refreshEventsExpandAggregator = WritableValue(Set[Element[_ <: Stash]]())
  /** Minor changes(element modification) aggregator */
  private val updateEventsAggregator = WritableValue(Set[Element[_ <: Stash]]())
  /** All views. */
  val views = new mutable.WeakHashMap[View, Unit] with mutable.SynchronizedMap[View, Unit]
  /** Global element events subscriber */
  private val elementEventsSubscriber = new Element.Event.Sub {
    def notify(pub: Element.Event.Pub, event: Element.Event) = event match {
      case Element.Event.ChildInclude(element, newElement, _) =>
        if (element.eStash.model.forall(_ eq Model.inner))
          App.exec {
            /*            viewMap.values.foreach { view =>
              if (view.context.ActionToggleExpand.isChecked())
                if (element.eChildren.size == 1) // if 1st child
                  view.tree.context.expandedItems += TreeProxy.Item(element) // expand parent
                else
                  view.tree.context.expandedItems ++=
                    newElement.eChildren.iteratorRecursive().map(TreeProxy.Item(_)) // expand children
            }
            refreshEventsAggregator.value = refreshEventsAggregator.value + element*/
          }
      case Element.Event.ChildRemove(element, _, _) =>
        if (element.eStash.model.forall(_ eq Model.inner))
          App.exec { refreshEventsAggregator.value = refreshEventsAggregator.value + element }
      case Element.Event.ChildrenReset(element, _) =>
        if (element.eStash.model.forall(_ eq Model.inner))
          App.exec { refreshEventsAggregator.value = refreshEventsAggregator.value + element }
      case Element.Event.ChildReplace(element, _, _, _) =>
        if (element.eStash.model.forall(_ eq Model.inner))
          App.exec { refreshEventsAggregator.value = refreshEventsAggregator.value + element }
      case Element.Event.StashReplace(element, _, _, _) =>
        if (element.eStash.model.forall(_ eq Model.inner))
          App.exec { updateEventsAggregator.value = updateEventsAggregator.value + element }
      case Element.Event.ValueInclude(element, _, _) =>
        if (element.eStash.model.forall(_ eq Model.inner))
          App.exec { updateEventsAggregator.value = updateEventsAggregator.value + element }
      case Element.Event.ValueRemove(element, _, _) =>
        if (element.eStash.model.forall(_ eq Model.inner))
          App.exec { updateEventsAggregator.value = updateEventsAggregator.value + element }
      case Element.Event.ValueUpdate(element, _, _, _) =>
        if (element.eStash.model.forall(_ eq Model.inner))
          App.exec { updateEventsAggregator.value = updateEventsAggregator.value + element }
      case Element.Event.ModelReplace(_, _, _) =>
        App.exec { Tree.FilterSystemElement.updateSystemElement }
      case _ =>
    }
  }

  /** Create editor view */
  def apply(parent: VComposite, style: Int): View = {
    val view = new View(parent, style)

    // handle data modification changes
    Data.modelName.addChangeListener { (_, _) => reloadEventsAggregator.value = System.currentTimeMillis() }
    Data.elementTemplates.addChangeListener { event => reloadEventsAggregator.value = System.currentTimeMillis() }
    Observables.observeDelayedValue(aggregatorDelay, reloadEventsAggregator).addValueChangeListener(new IValueChangeListener {
      def handleValueChange(event: ValueChangeEvent) = views.keys.foreach(view => withRedrawDelayed(view) {
        view.reload()
      })
    })
    // handle structural changes
    Observables.observeDelayedValue(aggregatorDelay, refreshEventsAggregator).addValueChangeListener(new IValueChangeListener {
      def handleValueChange(event: ValueChangeEvent) = views.keys.foreach(view => withRedrawDelayed(view) {
        val set = refreshEventsAggregator.value
        refreshEventsAggregator.value = Set()
        if (set.nonEmpty)
          view.refresh(set.toArray)
      })
    })
    // handle presentation changes
    Observables.observeDelayedValue(aggregatorDelay, updateEventsAggregator).addValueChangeListener(new IValueChangeListener {
      def handleValueChange(event: ValueChangeEvent) = views.keys.foreach(view => withRedrawDelayed(view) {
        val set = updateEventsAggregator.value
        updateEventsAggregator.value = Set()
        if (set.nonEmpty)
          view.update(set.toArray)
      })
    })
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
    // initial update of tree state
    View.withRedrawDelayed(view) {
      if (view.ActionToggleExpand.isChecked())
        Tree.expandAll(view)
      view.ActionToggleEmpty()
      view.ActionToggleSystem()
      view.ActionToggleIdentificators()
      view.ActionAutoResize(true)
    }
    view
  }
  def get(widget: Widget): Option[View] = None
  /**
   * This function is invoked at application start
   */
  def start() = Element.Event.subscribe(elementEventsSubscriber)
  /**
   * This function is invoked at application stop
   */
  def stop() = Element.Event.removeSubscription(elementEventsSubscriber)
  /** Disable the redraw while updating */
  def withRedrawDelayed[T](view: View)(f: => T): T = {
    view.getSashForm.setRedraw(false)
    view.table.tableViewer.getTable.setRedraw(false)
    view.tree.treeViewer.getTree.setRedraw(false)
    val result = f
    view.tree.treeViewer.getTree.setRedraw(true)
    view.table.tableViewer.getTable.setRedraw(true)
    view.getSashForm.setRedraw(true)
    result
  }
  /** Range information about a link in the StyledTextRootElement */
  case class RootPathLinkRange[T <: Element.Generic](val element: T, val index: Int, val length: Int)
}
