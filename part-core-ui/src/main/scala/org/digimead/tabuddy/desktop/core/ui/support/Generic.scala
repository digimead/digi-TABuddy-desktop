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

package org.digimead.tabuddy.desktop.core.ui.support

import javafx.util.Builder
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.definition.widget.{ AppWindow, SComposite, VComposite, WComposite }
import org.eclipse.jface.viewers.TableViewerColumn
import org.eclipse.swt.custom.{ CTabItem, TableTreeItem }
import org.eclipse.swt.dnd.{ DragSource, DropTarget }
import org.eclipse.swt.widgets.{ Caret, Composite, Control, CoolItem, ExpandItem, Menu, MenuItem, ScrollBar, Shell, TabItem, TableColumn, TableItem, TaskBar, TaskItem, ToolItem, ToolTip, TreeColumn, TreeItem, Widget }
import scala.annotation.tailrec
import scala.concurrent.Future
import scala.ref.WeakReference

/**
 * Trait with support functions for org.digimead.tabuddy.desktop.core.ui.UI
 */
trait Generic {
  this: org.digimead.tabuddy.desktop.core.ui.UI.type ⇒

  /**
   * little wrapper that negate effect of Java <B extends T<B>>
   * and prevents crush of Scala compiler
   */
  def <>[T, S](b: Builder[_])(f: T ⇒ S) = f(b.asInstanceOf[T])
  /** Adjust table viewer column width. */
  def adjustTableViewerColumnWidth(viewerColumn: TableViewerColumn, padding: Int, n: Int = 3) {
    val bounds = viewerColumn.getViewer.getControl.getBounds()
    val column = viewerColumn.getColumn()
    column.pack()
    column.setWidth(math.min(column.getWidth() + padding, bounds.width / n))
  }
  /** Find SWT widget shell. */
  def findShell(widget: Widget): Option[Shell] = {
    if (widget == null || widget.isDisposed())
      return None
    widget match {
      case shell: Shell ⇒ Option(shell)
      case caret: Caret ⇒ Option(caret.getParent().getShell())
      case control: Control ⇒ Option(control.getShell())
      case dragSource: DragSource ⇒ Option(dragSource.getControl().getShell())
      case dropTarget: DropTarget ⇒ Option(dropTarget.getControl().getShell())
      case item: CoolItem ⇒ Option(item.getControl()) orElse Option(item.getParent) map (_.getShell())
      case item: CTabItem ⇒ Option(item.getControl()) orElse Option(item.getParent) map (_.getShell())
      case item: ExpandItem ⇒ Option(item.getControl()) orElse Option(item.getParent) map (_.getShell())
      case item: MenuItem ⇒ Option(item.getParent().getShell())
      case item: TabItem ⇒ Option(item.getControl()) orElse Option(item.getParent) map (_.getShell())
      case item: TableColumn ⇒ Option(item.getParent().getShell())
      case item: TableItem ⇒ Option(item.getParent().getShell())
      case item: TableTreeItem ⇒ Option(item.getParent().getShell())
      case item: TaskItem ⇒ Option(item.getMenu().getShell())
      case item: ToolItem ⇒ Option(item.getControl()) orElse Option(item.getParent) map (_.getShell())
      case item: TreeColumn ⇒ Option(item.getParent().getShell())
      case item: TreeItem ⇒ Option(item.getParent().getShell())
      case menu: Menu ⇒ Option(menu.getShell())
      case scrollbar: ScrollBar ⇒ Option(scrollbar.getParent().getShell())
      case taskbar: TaskBar ⇒ taskbar.getItems().find(item ⇒ Option(item.getMenu).nonEmpty).map(_.getMenu().getShell())
      case tooltip: ToolTip ⇒ Option(tooltip.getParent())
      case widget: Widget ⇒
        log.warn(s"Unable to find shell for unexpected SWT widget ${widget}(${widget.getClass})")
        None
    }
  }
  /** Find SWT widget parent. */
  def findParent(widget: Widget): Option[Composite] = {
    if (widget == null || widget.isDisposed())
      return None
    widget match {
      case shell: Shell ⇒ Option(shell.getParent())
      case caret: Caret ⇒ Option(caret.getParent())
      case control: Control ⇒ Option(control.getParent())
      case dragSource: DragSource ⇒ Option(dragSource.getControl().getParent())
      case dropTarget: DropTarget ⇒ Option(dropTarget.getControl().getParent())
      case item: CoolItem ⇒ Option(item.getControl().getParent()) orElse Option(item.getParent)
      case item: CTabItem ⇒ Option(item.getControl().getParent()) orElse Option(item.getParent)
      case item: ExpandItem ⇒ Option(item.getControl().getParent()) orElse Option(item.getParent)
      case item: MenuItem ⇒ Option(item.getParent().getParent())
      case item: TabItem ⇒ Option(item.getControl().getParent()) orElse Option(item.getParent)
      case item: TableColumn ⇒ Option(item.getParent())
      case item: TableItem ⇒ Option(item.getParent())
      case item: TableTreeItem ⇒ Option(item.getParent())
      case item: TaskItem ⇒ Option(item.getMenu().getParent())
      case item: ToolItem ⇒ Option(item.getControl().getParent()) orElse Option(item.getParent)
      case item: TreeColumn ⇒ Option(item.getParent())
      case item: TreeItem ⇒ Option(item.getParent())
      case menu: Menu ⇒ Option(menu.getParent())
      case scrollbar: ScrollBar ⇒ Option(scrollbar.getParent().getParent())
      case taskbar: TaskBar ⇒ taskbar.getItems().find(item ⇒ Option(item.getMenu).nonEmpty).map(_.getMenu().getParent())
      case tooltip: ToolTip ⇒ Option(tooltip.getParent())
      case widget: Widget ⇒
        log.warn(s"Unable to find parent for unexpected SWT widget ${widget}(${widget.getClass})")
        None
    }
  }
  /** Find WComposite by shell */
  def findWindowComposite(shell: Shell): Option[WComposite] = {
    App.assertEventThread()
    // There is only few levels, so recursion is unneeded.
    shell.getChildren().map {
      case windowContainer: WComposite ⇒
        Some(windowContainer)
      case composite: Composite ⇒
        composite.getChildren().map {
          case windowContainer: WComposite ⇒
            Some(windowContainer)
          case composite: Composite ⇒
            composite.getChildren().map {
              case windowContainer: WComposite ⇒
                Some(windowContainer)
              case other ⇒
                None
            }.flatten.headOption
          case other ⇒
            None
        }.flatten.headOption
      case other ⇒
        None
    }.flatten.headOption
  }
  /** Get active view. */
  def getActiveView(): Option[VComposite] = Option(App.getActiveLeaf().get(classOf[VComposite]))
  /** Get active window. */
  def getActiveWindow(): Option[AppWindow] = Option(App.getActiveLeaf().get(classOf[AppWindow]))
  /** Get active shell (from window or dialog). */
  def getActiveShell(): Option[Shell] = Option(App.getActiveLeaf().get(classOf[Shell]))
  /** Get all GUI components from the current widget to top level parent(shell). */
  def widgetHierarchy(widget: Widget): Seq[SComposite] = {
    App.assertEventThread()
    Option(widget) match {
      case Some(composite: SComposite) if !composite.isDisposed ⇒
        widgetHierarchy(composite, Seq(composite))
      case Some(shell: Shell) if !shell.isDisposed ⇒
        findWindowComposite(shell).toSeq
      case Some(widget) if !widget.isDisposed ⇒
        findParent(widget) match {
          case Some(parent) ⇒ widgetHierarchy(parent, Seq())
          case _ ⇒ Seq()
        }
      case _ ⇒ // None or disposed element
        Seq()
    }
  }

  /** Get all GUI components. */
  @tailrec
  private def widgetHierarchy(widget: Widget, acc: Seq[SComposite]): Seq[SComposite] = {
    findParent(widget) match {
      case Some(parent: WComposite) if parent != widget ⇒
        acc :+ parent
      case Some(parent: SComposite) if parent != widget ⇒
        widgetHierarchy(parent, acc :+ parent)
      case Some(parent: Shell) ⇒
        acc
      case Some(parent) if parent != null && parent != widget ⇒
        widgetHierarchy(parent, acc)
      case None ⇒
        acc
    }
  }
}

object Generic extends XLoggable {
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher

  /** N title synchronizer. */
  abstract class TitleSynchronizer[T] {
    /** TitleSynchronizer routine. */
    protected var pending = Option.empty[Synchronizer]
    /** Active routine. */
    protected var active = Option.empty[Synchronizer]
    /** TitleSynchronizer lock. */
    protected val lock = new Object

    /** Update element title. */
    def notify(elements: Seq[T]): Unit = lock.synchronized {
      active match {
        case Some(active) ⇒
          pending = Some(new Synchronizer(elements, WeakReference(this)))
        case None ⇒
          var f = new Synchronizer(elements, WeakReference(this))
          pending = None
          active = Some(f)
          f.run()
      }
    }
    /** Update elements. */
    protected def synchronize(elements: Seq[T])

    /** Synchronizer runnable. */
    class Synchronizer(elements: Seq[T], parent: WeakReference[TitleSynchronizer[T]]) extends Runnable {
      def run() = Future { update() } onFailure { case e: Throwable ⇒ log.error(e.getMessage(), e) }
      /** Update elements title. */
      protected def update() {
        parent.get.foreach { parent ⇒
          log.trace("Synchronize " + elements)
          parent.synchronize(elements.filter(_ != null))
          lock.synchronized {
            parent.pending match {
              case p @ Some(pending) ⇒
                parent.pending = None
                parent.active = p
                pending.run()
              case None ⇒
                parent.active = None
            }
          }
        }
      }
    }
  }
}
