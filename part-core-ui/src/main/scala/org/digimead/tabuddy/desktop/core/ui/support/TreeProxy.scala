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

package org.digimead.tabuddy.desktop.core.ui.support

import java.util.ArrayList
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.graph.Node
import org.eclipse.core.databinding.observable.list.{ WritableList ⇒ OriginalWritableList }
import org.eclipse.jface.viewers.TreeViewer
import scala.collection.{ mutable, parallel }
import scala.collection.JavaConverters.{ asJavaCollectionConverter, asScalaBufferConverter }
import scala.util.DynamicVariable

/**
 * Tree proxy object that updates observables
 * Observables is updated only after TreeProxy.content
 */
class TreeProxy(protected val treeViewer: TreeViewer, protected val observables: Seq[OriginalWritableList],
  protected val expandedItems: mutable.HashSet[TreeProxy.Item], protected val sortFn: Seq[Node[_ <: Element]] ⇒ Seq[Node[_ <: Element]]) extends XLoggable {
  /** Internal content that represents the actual flat projection of hierarchical structure. */
  protected var content = parallel.immutable.ParVector[TreeProxy.Item]()
  /** Set of hidden items in hierarchical structure. */
  protected var hidden = mutable.HashSet[TreeProxy.Item]()
  /** The last known root element. */
  protected var root: Option[TreeProxy.Item] = None
  /** Update flag for local thread. */
  protected val updateFlag = new DynamicVariable[Boolean](true)
  assert(observables.forall(_.getElementType() == classOf[TreeProxy.Item]), "Incorrect observable type")

  /** Clear proxy content. */
  def clearContent() {
    log.debug("clear content")
    App.assertEventThread()
    content = parallel.immutable.ParVector[TreeProxy.Item]()
    observables.foreach(_.clear())
  }
  /** Clear hidden items set. */
  def clearHiddenItems() {
    log.debug("clear hidden set")
    App.assertEventThread()
    hidden.clear
  }
  /** Get proxy content. */
  def getContent() = {
    App.assertEventThread()
    content
  }
  /** Get expanded state of item. */
  def getExpandedState(item: TreeProxy.Item) = {
    App.assertEventThread()
    expandedItems(item)
  }
  /** Get set of hidden items. */
  def getHiddenItems() = {
    App.assertEventThread()
    hidden
  }
  /** Collapse item. */
  def onCollapse(item: TreeProxy.Item): Unit = if (updateFlag.value) {
    log.debug(s"collapse $item")
    App.assertEventThread()
    if (hidden(item)) {
      log.debug("skip hidden item")
      return
    }
    expandedItems -= item
    val (from, to) = getItemRange(item)
    if (from == -1) {
      log.debug(s"skip invisible item $item")
      return
    }
    patchContent(from + 1, Seq(), to - from).foreach { expandedItems -= _ }
  }
  /** Collapse all items. */
  def onCollapseAll(): Unit = if (updateFlag.value) {
    log.debug("collapse all")
    App.assertEventThread()
    content = parallel.immutable.ParVector[TreeProxy.Item]()
    observables.foreach(_.clear())
    root.foreach { root ⇒
      patchContent(0, sortFn(root.element.eNode.safeRead(_.children)).map(node ⇒ TreeProxy.Item(node.rootBox.e)).filterNot(hidden).toSeq, 0)
    }
    expandedItems.clear
  }
  /** Expand item. */
  def onExpand(item: TreeProxy.Item): Unit = if (updateFlag.value && !getExpandedState(item)) {
    log.debug(s"expand $item")
    App.assertEventThread()
    if (hidden(item)) {
      log.debug("skip hidden item")
      return
    }
    expandedItems += item
    val index = content.indexOf(item)
    if (index < 0) {
      log.debug(s"skip invisible item $item")
      return
    }
    val newItems = sortFn(item.element.eNode.safeRead(_.children)).map(node ⇒ TreeProxy.Item(node.rootBox.e))
    patchContent(index + 1, newItems, 0)
  }
  /** Expand all items. */
  def onExpandAll(): Unit = if (updateFlag.value) {
    log.debug("expand all")
    App.assertEventThread()
    expandedItems.clear()
    content = parallel.immutable.ParVector[TreeProxy.Item]()
    observables.foreach(_.clear())
    root.foreach { root ⇒
      val expandedItems = root.element.eNode.safeRead(_.flatten(sortFn, (node, f) ⇒ node.safeRead(f))).map(node ⇒ TreeProxy.Item(node.rootBox.e)).filterNot(hidden).toSeq
      patchContent(0, expandedItems, 0)
      this.expandedItems ++= expandedItems
    }
  }
  /** Expand item recursively. */
  def onExpandRecursively(item: TreeProxy.Item): Unit = if (updateFlag.value) {
    log.debug(s"expand recursively $item")
    App.assertEventThread()
    if (hidden(item)) {
      log.debug("skip hidden item")
      return
    }
    val index = content.indexOf(item)
    def expandItem(item: TreeProxy.Item, index: Int): Int = 1
    if (item.element.eNode.safeRead(_.nonEmpty)) {
      var shift = 1 // right after the element
      if (expandedItems(item)) {
        for (childNode ← sortFn(item.element.eNode.safeRead(_.children)))
          shift += expandItem(TreeProxy.Item(childNode.rootBox.e), index + shift)
        shift
      } else {
        expandedItems += item
        val newItems = item.element.eNode.safeRead(_.flatten(sortFn, (node, f) ⇒ node.safeRead(f))).map(node ⇒ TreeProxy.Item(node.rootBox.e)).toVector
        // mark all children as expanded
        expandedItems ++= newItems.filter(_.element.eNode.safeRead(_.nonEmpty))
        // add children to content
        patchContent(index + shift, newItems, 0)
        1 + newItems.size // the element is expanded + children is expanded
      }
    } else
      1 // the element is expanded
    expandItem(item, index)
  }
  /** Hide item. */
  def onHide(item: TreeProxy.Item) {
    log.debug(s"Hide $item.")
    App.assertEventThread()
    hidden += item
    item.element.eParent.foreach(parentNode ⇒ onRefresh(TreeProxy.Item(parentNode.rootBox.e), Seq(), Seq(), false))
  }
  /** Change proxy input. */
  def onInputChanged(item: TreeProxy.Item) {
    log.debug(s"Input changed to $item.")
    App.assertEventThread()
    content = parallel.immutable.ParVector[TreeProxy.Item]()
    hidden.clear
    observables.foreach(_.clear())
    if (item != null) {
      root = Some(item)
      onRefresh(item, Seq(), Seq(), false, item)
    } else
      root = None
  }
  /** Refresh proxy content. */
  def onRefresh(toRefresh: TreeProxy.Item, traversal: Seq[TreeProxy.Item], toRefreshSubItems: Seq[TreeProxy.Item], refreshTree: Boolean, root: TreeProxy.Item = treeViewer.getInput.asInstanceOf[TreeProxy.Item]) {
    App.assertEventThread()
    updateFlag.withValue(false) {
      log.debug(s"Refresh $toRefresh.")
      log.debug(s"traversal: $traversal") // all possible ancestors for toRefreshSubItems
      log.debug(s"subrefresh: $toRefreshSubItems")
      if (refreshTree) {
        val expanded = treeViewer.getExpandedElements()
        treeViewer.refresh(toRefresh, true)
        // another bug in JFace
        // at this point treeViewer.getExpandedElements() will return the same array as 'expanded',
        // but actually there are unexpectedly collapsed elements
        // not always
        // so force to expand
        treeViewer.setExpandedElements(expanded)
      }
      if (hidden(toRefresh)) {
        log.debug("skip hidden item")
        return
      }
      val idxContentItem = if (toRefresh == root) -1 else content.indexOf(toRefresh)
      // we may refresh only the visible/exists item
      if (toRefresh != root) {
        if (idxContentItem == -1) {
          log.debug(s"skip invisible item $toRefresh")
          return
        }
        if (!expandedItems(toRefresh)) {
          log.debug(s"skip collapsed item $toRefresh")
          return
        }
      }
      val idxContentNextItem = if (toRefresh == root) {
        content.size
      } else {
        val (from, to) = getItemRange(toRefresh, Some(idxContentItem))
        to + 1
      }
      assert(idxContentNextItem >= 0)
      val slice = content.slice(idxContentItem + 1, idxContentNextItem)
      val after = sortFn(toRefresh.element.eNode.safeRead(_.children)).map(node ⇒ TreeProxy.Item(node.rootBox.e)).filterNot(hidden)
      val beforeIndex = Map[TreeProxy.Item, Int](after.par.map(item ⇒ (item, {
        val index = slice.indexOf(item)
        if (index < 0) index else index + idxContentItem + 1 // save -1 value
      })).seq: _*)
      val before = after.map(item ⇒ (item, beforeIndex(item))).toSeq.filterNot(_._2 < 0).sortBy(_._2).map(_._1)
      val common = LCS(before, after)
      var delete = before.diff(common)
      var append = after.diff(common)
      log.debug("before: " + before.mkString(","))
      log.debug("after: " + after.mkString(","))
      log.debug("common: " + common.mkString(","))
      log.debug("delete: " + delete.mkString(","))
      log.debug("append: " + append.mkString(","))
      if (delete.isEmpty && append.isEmpty && idxContentNextItem - idxContentItem == 1)
        return
      // delete
      // sequence description: element, index, size
      var toDelete = Seq[TreeProxy.Delete]()
      // delete ghosts
      var idxDelete = idxContentItem + 1
      for (i ← 0 until before.size) {
        val index = beforeIndex(before(i))
        if (index != idxDelete)
          toDelete = toDelete :+ TreeProxy.Delete(TreeProxy.UnknownItem, idxDelete, index - idxDelete)
        val (from, to) = getItemRange(before(i), Some(index))
        idxDelete = to + 1
      }
      if (idxDelete != idxContentNextItem)
        toDelete = toDelete :+ TreeProxy.Delete(TreeProxy.UnknownItem, idxDelete, idxContentNextItem - idxDelete)
      // delete before items
      idxDelete = slice.size // point by initial to next element, to the void
      for (i ← before.size - 1 to 0 by -1)
        if (delete.contains(before(i))) {
          toDelete = toDelete :+ TreeProxy.Delete(before(i), beforeIndex(before(i)), idxDelete - beforeIndex(before(i)))
        } else {
          // skip, already exists
          idxDelete = beforeIndex(before(i))
        }
      // append
      // sequence description: element, index, subelements
      var toAppend = Seq[TreeProxy.Append]()
      var idxAppend = idxContentNextItem
      for (i ← after.size - 1 to 0 by -1)
        if (append.contains(after(i))) {
          toAppend = toAppend :+ TreeProxy.Append(after(i), idxAppend, collectAppend(after(i)))
        } else {
          // skip, already exists
          idxAppend = beforeIndex(after(i))
        }
      // TODO ranges: merge(merge(toDelete) + merge(toAppend))
      log.debug("toDelete: " + toDelete)
      log.debug("toAppend: " + toAppend)
      // patch content
      (toDelete ++ toAppend).sortBy(_.index * -1).foreach(_ match {
        case TreeProxy.Append(item, index, sub) ⇒
          patchContent(index, item +: sub, 0)
        case TreeProxy.Delete(item, index, size) ⇒
          patchContent(index, Seq(), size)
      })
      if (before.isEmpty)
        App.exec { treeViewer.setExpandedState(toRefresh, true) }
      if (toRefresh == root)
        this.root = Option(root)
      // refresh sub elements
      if (common.nonEmpty)
        refreshSubItems(common, traversal, toRefreshSubItems, refreshTree)
    }
  }
  /** Unhide item. */
  def onUnhide(item: TreeProxy.Item) {
    log.debug(s"show $item")
    App.assertEventThread()
    hidden -= item
    if (expandedItems(item))
      treeViewer.setExpandedElements(expandedItems.toArray)
    item.element.eParent.foreach(parentNode ⇒ onRefresh(TreeProxy.Item(parentNode.rootBox.e), Seq(), Seq(), false))
  }

  protected def collectAppend(item: TreeProxy.Item): Seq[TreeProxy.Item] = {
    if (hidden(item))
      Seq()
    else if (expandedItems(item))
      sortFn(item.element.eNode.safeRead(_.children)).par.map(node ⇒
        TreeProxy.Item(node.rootBox.e) +: collectAppend(TreeProxy.Item(node.rootBox.e))).flatten.seq
    else
      Seq()
  }
  /** Get the item range in the content. */
  protected def getItemRange(item: TreeProxy.Item, precalculatedIndex: Option[Int] = None): (Int, Int) = {
    val index = precalculatedIndex getOrElse content.indexOf(item)
    if (index == -1)
      return (-1, -1)
    val ancestors = item.element.eAncestors
    val ancestorsN = ancestors.size
    var lastSubElementIndex = content.indexWhere(_.element.eAncestors.size <= ancestorsN, index + 1) - 1
    if (lastSubElementIndex < 0)
      lastSubElementIndex = content.size - 1
    (index, lastSubElementIndex)
  }
  /** Patch parallel array 'content' and update table. */
  protected def patchContent(from: Int, that: Seq[TreeProxy.Item], replaced: Int): Iterable[TreeProxy.Item] = {
    assert(replaced >= 0)
    val itemsToRemove = new ArrayList[TreeProxy.Item](replaced)
    if (replaced > 0)
      // .iterator suffix is important.
      // content is ParVector!
      content.slice(from, from + replaced).iterator.foreach(itemsToRemove.add)
    content = content.patch(from, that, replaced)
    observables.foreach { observable ⇒
      if (!itemsToRemove.isEmpty())
        observable.removeAll(itemsToRemove)
      if (!that.isEmpty)
        observable.addAll(from, that.asJavaCollection)
    }
    itemsToRemove.asScala
  }
  /** Refresh sub items if needed. */
  protected def refreshSubItems(toRefreshItems: Seq[TreeProxy.Item], traversal: Seq[TreeProxy.Item], toRefreshSubItems: Seq[TreeProxy.Item], refreshTree: Boolean) {
    var traversalBucket = traversal
    var toRefreshSubItemsBucket = toRefreshSubItems
    toRefreshItems.foreach { element ⇒
      if (toRefreshSubItems.contains(element)) {
        traversalBucket = traversalBucket.filterNot(_ == element)
        toRefreshSubItemsBucket = toRefreshSubItemsBucket.filterNot(_ == element)
        onRefresh(element, traversalBucket, toRefreshSubItemsBucket, refreshTree)
      } else if (traversal.contains(element)) {
        traversalBucket = traversalBucket.filterNot(_ == element)
        toRefreshSubItemsBucket = toRefreshSubItemsBucket.filterNot(_ == element)
        refreshSubItems(toRefreshItems, traversalBucket, toRefreshSubItemsBucket, refreshTree)
      }
    }
  }
  /**
   * King regards to Rex Kerr
   * http://stackoverflow.com/questions/5734020/operating-on-scala-collections-in-generic-way
   */
  protected def LCS[A, C](a: C, b: C)(
    implicit c2i: C ⇒ Iterable[A], cbf: collection.generic.CanBuildFrom[C, A, C]): C = {
    val builder = cbf()
    def ListLCS(a: Iterable[A], b: Iterable[A]): List[A] = {
      if (a.isEmpty || b.isEmpty) Nil
      else if (a.head == b.head) a.head :: ListLCS(a.tail, b)
      else {
        val case1 = ListLCS(a.tail, b)
        val case2 = ListLCS(a, b.tail)
        if (case1.length > case2.length) case1 else case2
      }
    }
    builder ++= ListLCS(c2i(a), c2i(b))
    builder.result()
  }
}

object TreeProxy {
  /** An item that wraps an element and persists hashCode even if the element changed */
  case class Item(hash: Int)(val element: Element) {
    override lazy val toString = "%s/%s".format(element, hash)
  }
  object Item {
    def apply(element: Element) = new Item(System.identityHashCode(element))(element)
  }

  case class Append(val item: Item, val index: Int, val sub: Seq[Item]) extends Modify
  case class Delete(val item: Item, val index: Int, val size: Int) extends Modify
  sealed trait Modify {
    val item: Item
    val index: Int
  }
  object UnknownItem extends Item(0)(null) {
    override lazy val toString = "unknown/unknown"
  }
}
