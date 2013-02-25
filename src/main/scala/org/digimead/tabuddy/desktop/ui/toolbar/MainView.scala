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

package org.digimead.tabuddy.desktop.ui.toolbar

import scala.ref.WeakReference

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.tabuddy.desktop.Main
import org.digimead.tabuddy.desktop.res.Messages
import org.digimead.tabuddy.desktop.ui.Window
import org.digimead.tabuddy.desktop.ui.Window.instance2object
import org.digimead.tabuddy.desktop.ui.view.View
import org.digimead.tabuddy.desktop.ui.view.View.view2list
import org.eclipse.jface.action.ControlContribution
import org.eclipse.jface.action.ICoolBarManager
import org.eclipse.jface.action.ToolBarContributionItem
import org.eclipse.jface.action.ToolBarManager
import org.eclipse.jface.viewers.ArrayContentProvider
import org.eclipse.jface.viewers.ComboViewer
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.viewers.LabelProvider
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.jface.viewers.StructuredSelection
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Label

object MainView extends ToolBarManager with Loggable {
  lazy val defaultItem = Item(new WeakReference(View()))
  @volatile protected var viewLabel: Option[Label] = None
  @volatile protected var viewCombo: Option[ComboViewer] = None
  @volatile protected var toolBarContributionItem: Option[ToolBarContributionItem] = None
  protected val viewSubscriber = new View.Event.Sub {
    def notify(pub: View.Event.Pub, event: View.Event) = event match {
      case View.Event.ViewListChanged =>
        Main.exec { reloadItems() }
      case View.Event.ViewChanged(before, after) =>
        // skip events if toolbar is not ready
        if (toolBarContributionItem.nonEmpty)
          Main.exec { after.foreach(selectItem) }
    }
  }
  log.debug("alive")

  // initialize
  add(new ControlContribution(null) {
    protected def createControl(parent: Composite): Control = {
      val container = new Composite(parent, SWT.NONE)
      container.setLayout(new GridLayout(1, false))
      val label = new Label(container, SWT.NONE)
      label.setAlignment(SWT.CENTER);
      label.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true, 1, 1))
      label.setText(Messages.view_text)
      label.setToolTipText(Messages.view_tooltip_text)
      viewLabel = Some(label)
      container
    }
  })
  add(new ControlContribution(null) {
    protected def createControl(parent: Composite): Control = {
      val viewer = new ComboViewer(parent, SWT.BORDER | SWT.H_SCROLL | SWT.READ_ONLY)
      viewer.setContentProvider(ArrayContentProvider.getInstance())
      viewer.setLabelProvider(new LabelProvider() {
        override def getText(element: Object): String = element match {
          case element: Item =>
            element.item.get.map(_.title) getOrElse ("UNKNOWN")
          case element =>
            super.getText(element)
        }
      })
      viewer.addSelectionChangedListener(new ISelectionChangedListener() {
        override def selectionChanged(event: SelectionChangedEvent) = event.getSelection() match {
          case selection: IStructuredSelection if !selection.isEmpty() =>
            selection.getFirstElement().asInstanceOf[Item].item.get.foreach(_.show)
          case selection =>
        }
      })
      viewCombo = Some(viewer)
      reloadItems
      viewer.getControl()
    }
  })
  View.Event.subscribe(viewSubscriber)

  @log
  override def dispose() {
    View.Event.removeSubscription(viewSubscriber)
    getItems().foreach(_.dispose())
    super.dispose()
  }
  def selectItem(view: View): Unit = viewCombo.foreach { combo =>
    combo.getInput() match {
      case elements: Array[_] if !elements.isEmpty =>
        elements.asInstanceOf[Array[Item]].find(_.item.get == Some(view)) match {
          case Some(element) =>
            selectItem(element)
          case None =>
            log.fatal("Unable to select unknown view " + view)
        }
      case elements =>
        log.fatal("Unable to select unknown view " + view)
    }
  }
  def selectItem(element: Item): Unit = viewCombo.foreach { combo =>
    combo.getSelection() match {
      case selection: IStructuredSelection =>
        if (selection.getFirstElement() == element)
          return // already selected
      case selection =>
    }
    combo.setSelection(new StructuredSelection(element))
  }

  protected def reloadItems() = for {
    combo <- viewCombo if getControl().getItemCount() == 2 // update only if toolbar have all suitable children
    toolBarItem <- getToolBarContributionItem()
    comboToolBarItem = getControl().getItem(1)
  } {
    val previousWidth = combo.getCombo().getBounds().width
    // update elements
    val elements = View.map(view => Item(new WeakReference(view)))
    combo.setInput(elements.toArray)
    // set new width
    val newWidth = combo.getCombo().computeSize(SWT.DEFAULT, SWT.DEFAULT, true).x
    comboToolBarItem.setWidth(comboToolBarItem.getWidth() + newWidth - previousWidth)
    toolBarItem.update(ICoolBarManager.SIZE)
    // set current element
    elements.find(View.currentView.value.nonEmpty && _.item.get == View.currentView) match {
      case Some(element) =>
        selectItem(element)
      case None =>
        // select new value if old value is absent in element list
        // should we do an update of the current view?
        elements.headOption.foreach(selectItem)
    }
  }
  protected def getToolBarContributionItem(): Option[ToolBarContributionItem] = toolBarContributionItem orElse {
    Window.getCoolBarManager2().getItems().find {
      case item: ToolBarContributionItem =>
        item.getToolBarManager() == this
      case item =>
        false
    } map { item =>
      toolBarContributionItem = Some(item.asInstanceOf[ToolBarContributionItem])
      item.asInstanceOf[ToolBarContributionItem]
    }
  }
  case class Item(val item: WeakReference[View]) {
    override def toString() = item.get.map(_.title) getOrElse ("UNKNOWN")
  }
}

