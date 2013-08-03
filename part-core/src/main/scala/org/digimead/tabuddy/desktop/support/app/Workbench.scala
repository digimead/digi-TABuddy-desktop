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

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.util.control.Breaks._

import org.digimead.digi.lib.log.api.Loggable
import org.eclipse.e4.ui.model.application.ui.basic.MTrimBar
import org.eclipse.e4.ui.model.application.ui.basic.MTrimElement
import org.eclipse.e4.ui.model.application.ui.menu.MToolBar
import org.eclipse.jface.wizard.WizardDialog
import org.eclipse.swt.custom.CTabItem
import org.eclipse.swt.custom.TableTreeItem
import org.eclipse.swt.dnd.DragSource
import org.eclipse.swt.dnd.DropTarget
import org.eclipse.swt.widgets.Caret
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
import org.eclipse.ui.IWorkbenchPage
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.internal.{ Workbench => EWorkbench }

trait Workbench {
  this: Generic with Loggable =>


  /** Get active workbench page for active window. */
  def getActivePage(): Option[IWorkbenchPage] = Option(PlatformUI.getWorkbench().getActiveWorkbenchWindow()).flatMap(window => Option(window.getActivePage()))
  /** Get active perspective id for active window. */
  def getActivePerspectiveId(): Option[String] = getActivePage().flatMap(page => Option(page.getPerspective()).map(_.getId))
  /**
   * Hide toolbar in trimbar.
   * This method is dispose widget.
   * @param toolbar toolbar to hide
   */
  def hideToolBar(toolbar: MToolBar) {
    log.debug("Hide toolbar " + toolbar)
    assertUIThread()
    toolbar.setVisible(false)
    toolbar.setToBeRendered(false)
    display.update() // reenter to the new UI transaction
  }
  /** Invoking an Wizard by id. */
  def openWizard(id: String) {
    assertUIThread()
    // First see if this is a "new wizard".
    val descriptor = Option(workbench.getNewWizardRegistry().findWizard(id)).orElse {
      // If not check if it is an "import wizard".
      Option(workbench.getImportWizardRegistry().findWizard(id))
    }.orElse {
      // Or maybe an export wizard
      Option(workbench.getExportWizardRegistry().findWizard(id))
    }.getOrElse {
      throw new IllegalArgumentException(s"Wizard with id '${id}' not found.")
    }
    // Then if we have a wizard, open it.
    val wizard = descriptor.createWizard()
    val wd = new WizardDialog(workbench.getActiveWorkbenchWindow().getShell(), wizard)
    wd.setTitle(wizard.getWindowTitle())
    wd.open()
  }
  /**
   * Show toolbar in trimbar.
   * This method is create widget.
   * @param toolbar toolbar to show
   */
  def showToolBar(toolbar: MToolBar) {
    log.debug("Show toolbar " + toolbar)
    assertUIThread()
    toolbar.setVisible(true)
    toolbar.setToBeRendered(true)
    display.update() // reenter to the new UI transaction
  }
  /** Get application workbench. */
  def workbench = PlatformUI.getWorkbench() match {
    case workbench: EWorkbench => workbench
    case other => throw new IllegalStateException(s"Unable to find suitable workbench. PlatformUI returns '$other'")
  }

  /** Add hidden elements to trim bar children. */
  protected def addHiddenChildren(children: java.util.List[MTrimElement], saved: Option[mutable.LinkedHashMap[MTrimElement, Boolean]]): Seq[MTrimElement] =
    saved match {
      case Some(saved) =>
        var result = mutable.ArrayBuffer()
        var i = -1
        val insert = for ((element, visibility) <- saved) yield {
          i += 1
          if (visibility == false) {
            // search for any  suitable element before 'i' in children
            var insertAt = 0
            val searchChunk = children.take(i)
            val savedChunk = saved.take(i - 1).toSeq.reverse
            breakable {
              for ((savedElement, savedVisibility) <- savedChunk) {
                if (savedVisibility)
                  searchChunk.indexOf(savedElement) match {
                    case n if n >= 0 =>
                      insertAt = n + 1
                      break
                    case n =>
                  }
              }
            }
            Some(insertAt, element)
          } else
            None
        }
        insert.flatten.toSeq.sortBy(-_._1).foreach {
          case (index, element) =>

        }

        log.___glance("!!" + insert)
        children
      case None =>
        children
    }
}
