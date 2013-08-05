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

package org.digimead.tabuddy.desktop.support.app

import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import scala.Array.canBuildFrom
import scala.Option.option2Iterable
import scala.annotation.tailrec

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.gui.widget.SComposite
import org.digimead.tabuddy.desktop.gui.widget.WComposite
import org.eclipse.swt.custom.CTabItem
import org.eclipse.swt.custom.TableTreeItem
import org.eclipse.swt.dnd.DragSource
import org.eclipse.swt.dnd.DropTarget
import org.eclipse.swt.widgets.Caret
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.CoolItem
import org.eclipse.swt.widgets.ExpandItem
import org.eclipse.swt.widgets.Menu
import org.eclipse.swt.widgets.MenuItem
import org.eclipse.swt.widgets.ScrollBar
import org.eclipse.swt.widgets.Shell
import org.eclipse.swt.widgets.TabItem
import org.eclipse.swt.widgets.TableColumn
import org.eclipse.swt.widgets.TableItem
import org.eclipse.swt.widgets.TaskBar
import org.eclipse.swt.widgets.TaskItem
import org.eclipse.swt.widgets.ToolItem
import org.eclipse.swt.widgets.ToolTip
import org.eclipse.swt.widgets.TreeColumn
import org.eclipse.swt.widgets.TreeItem
import org.eclipse.swt.widgets.Widget

trait GUI {
  this: Generic with Loggable =>
  /** Execute runnable in UI thread. */
  def exec[T](f: => T): Unit =
    if (thread.eq(Thread.currentThread())) { f } else execAsync({ f })
  /** Asynchronously execute runnable in UI thread. */
  def execAsync[T](f: => T): Unit = display.asyncExec(new Runnable {
    def run = try { f } catch { case e: Throwable => log.error("UI Thread exception: " + e, e) }
  })
  /** Execute runnable in UI thread and return result or exception */
  def execNGet[T](f: => T): T =
    if (thread.eq(Thread.currentThread())) { f } else execNGetAsync({ f })
  /** Asynchronously execute runnable in UI thread and return result or exception */
  def execNGetAsync[T](f: => T): T = {
    val result = new AtomicReference[Option[Either[Throwable, T]]](None)
    display.asyncExec(new Runnable {
      def run = result.synchronized {
        try {
          result.set(Some(Right(f)))
          result.notifyAll()
        } catch {
          case e: Throwable =>
            result.set(Some(Left(e)))
            result.notifyAll()
        }
      }
    })
    while (result.get.isEmpty)
      result.synchronized { result.wait() }
    result.get.get match {
      case Left(e) =>
        throw e
      case Right(r) =>
        r
    }
  }
  /**
   * Asynchronously execute runnable in UI thread with timeout and return result or exception
   * NB This routine block UI thread, so it would unusual to freeze application for a few hours.
   */
  def execNGetAsync[T](timeout: Int, unit: TimeUnit = TimeUnit.MILLISECONDS)(f: => T): T = {
    if (thread.eq(Thread.currentThread()))
      throw new IllegalStateException("Unable to spawn execNGetAsync runnable with timeout within UI thread.")
    val mark = System.currentTimeMillis() + unit.toMillis(timeout)
    val result = new AtomicReference[Option[Either[Throwable, T]]](None)
    display.asyncExec(new Runnable {
      def run = result.synchronized {
        try {
          result.set(Some(Right(f)))
          result.notifyAll()
        } catch {
          case e: Throwable =>
            result.set(Some(Left(e)))
            result.notifyAll()
        }
      }
    })
    while (result.get.isEmpty && System.currentTimeMillis() < mark)
      result.synchronized { result.wait(mark - System.currentTimeMillis()) }
    result.get.get match {
      case Left(e) =>
        throw new ExecutionException(e)
      case Right(r) =>
        r
    }
  }
  /** Find SWT widget shell. */
  def findShell(widget: Widget): Option[Shell] = {
    if (widget == null)
      return None
    widget match {
      case shell: Shell => Option(shell)
      case caret: Caret => Option(caret.getParent().getShell())
      case control: Control => Option(control.getShell())
      case dragSource: DragSource => Option(dragSource.getControl().getShell())
      case dropTarget: DropTarget => Option(dropTarget.getControl().getShell())
      case item: CoolItem => Option(item.getControl()) orElse Option(item.getParent) map (_.getShell())
      case item: CTabItem => Option(item.getControl()) orElse Option(item.getParent) map (_.getShell())
      case item: ExpandItem => Option(item.getControl()) orElse Option(item.getParent) map (_.getShell())
      case item: MenuItem => Option(item.getParent().getShell())
      case item: TabItem => Option(item.getControl()) orElse Option(item.getParent) map (_.getShell())
      case item: TableColumn => Option(item.getParent().getShell())
      case item: TableItem => Option(item.getParent().getShell())
      case item: TableTreeItem => Option(item.getParent().getShell())
      case item: TaskItem => Option(item.getMenu().getShell())
      case item: ToolItem => Option(item.getControl()) orElse Option(item.getParent) map (_.getShell())
      case item: TreeColumn => Option(item.getParent().getShell())
      case item: TreeItem => Option(item.getParent().getShell())
      case menu: Menu => Option(menu.getShell())
      case scrollbar: ScrollBar => Option(scrollbar.getParent().getShell())
      case taskbar: TaskBar => taskbar.getItems().find(item => Option(item.getMenu).nonEmpty).map(_.getMenu().getShell())
      case tooltip: ToolTip => Option(tooltip.getParent())
      case widget: Widget =>
        log.warn(s"Unable to find shell for unexpected SWT widget ${widget}(${widget.getClass})")
        None
    }
  }
  /** Find SWT widget parent. */
  def findParent(widget: Widget): Option[Composite] = {
    if (widget == null)
      return None
    widget match {
      case shell: Shell => Option(shell.getParent())
      case caret: Caret => Option(caret.getParent())
      case control: Control => Option(control.getParent())
      case dragSource: DragSource => Option(dragSource.getControl().getParent())
      case dropTarget: DropTarget => Option(dropTarget.getControl().getParent())
      case item: CoolItem => Option(item.getControl().getParent()) orElse Option(item.getParent)
      case item: CTabItem => Option(item.getControl().getParent()) orElse Option(item.getParent)
      case item: ExpandItem => Option(item.getControl().getParent()) orElse Option(item.getParent)
      case item: MenuItem => Option(item.getParent().getParent())
      case item: TabItem => Option(item.getControl().getParent()) orElse Option(item.getParent)
      case item: TableColumn => Option(item.getParent())
      case item: TableItem => Option(item.getParent())
      case item: TableTreeItem => Option(item.getParent())
      case item: TaskItem => Option(item.getMenu().getParent())
      case item: ToolItem => Option(item.getControl().getParent()) orElse Option(item.getParent)
      case item: TreeColumn => Option(item.getParent())
      case item: TreeItem => Option(item.getParent())
      case menu: Menu => Option(menu.getParent())
      case scrollbar: ScrollBar => Option(scrollbar.getParent().getParent())
      case taskbar: TaskBar => taskbar.getItems().find(item => Option(item.getMenu).nonEmpty).map(_.getMenu().getParent())
      case tooltip: ToolTip => Option(tooltip.getParent())
      case widget: Widget =>
        log.warn(s"Unable to find parent for unexpected SWT widget ${widget}(${widget.getClass})")
        None
    }
  }
  /** Find WComposite by shell */
  def findWindowComposite(shell: Shell): Option[WComposite] = {
    assertUIThread()
    // There is only few levels, so recursion is unneeded.
    shell.getChildren().map {
      case windowContainer: WComposite =>
        Some(windowContainer)
      case composite: Composite =>
        composite.getChildren().map {
          case windowContainer: WComposite =>
            Some(windowContainer)
          case composite: Composite =>
            composite.getChildren().map {
              case windowContainer: WComposite =>
                Some(windowContainer)
              case other =>
                None
            }.flatten.headOption
          case other =>
            None
        }.flatten.headOption
      case other =>
        None
    }.flatten.headOption
  }
  /** Get all GUI components from the current widget to top level parent(shell). */
  def widgetHierarchy(widget: Widget): Seq[Widget] = Option(widget) match {
    case Some(composite: SComposite) =>
      assertUIThread()
      widgetHierarchy(composite, Seq(composite))
    case Some(shell: Shell) =>
      findWindowComposite(shell).toSeq
    case Some(parent) =>
      assertUIThread()
      widgetHierarchy(parent, Seq())
    case None =>
      Seq()
  }
  /** Get all GUI components. */
  @tailrec
  private def widgetHierarchy(widget: Widget, acc: Seq[Widget]): Seq[Widget] = {
    findParent(widget) match {
      case Some(parent: WComposite) =>
        acc :+ parent
      case Some(parent: SComposite) =>
        widgetHierarchy(parent, acc :+ parent)
      case Some(parent: Shell) =>
        acc
      case Some(parent) =>
        widgetHierarchy(parent, acc)
      case None =>
        acc
    }
  }
}