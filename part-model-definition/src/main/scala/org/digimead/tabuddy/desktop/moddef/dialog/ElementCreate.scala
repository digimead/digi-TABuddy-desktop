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

package org.digimead.tabuddy.desktop.moddef.dialog

import java.awt.MouseInfo
import org.digimead.tabuddy.desktop.logic.Data
import org.digimead.tabuddy.desktop.Resources
import org.digimead.tabuddy.desktop.logic.payload.api.ElementTemplate
import org.digimead.tabuddy.desktop.ResourceManager
import org.digimead.tabuddy.model.element.Element
import org.eclipse.jface.dialogs.IDialogConstants
import org.eclipse.swt.SWT
import org.eclipse.swt.events.DisposeEvent
import org.eclipse.swt.events.DisposeListener
import org.eclipse.swt.events.PaintEvent
import org.eclipse.swt.events.PaintListener
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.graphics.Rectangle
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Event
import org.eclipse.swt.widgets.Listener
import org.eclipse.swt.widgets.Shell
import swing2swt.layout.BorderLayout
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.support.App

class ElementCreate(val parentShell: Shell, container: Element.Generic)
  extends org.digimead.tabuddy.desktop.res.dialog.model.ElementCreate(parentShell) with Loggable {
  /** Newly created element */
  protected var element: Option[Element.Generic] = None
  /** Close dialog on mouse over */
  val guargThread = new Thread(new Runnable {
    def run = {
      var run = !getShell.isDisposed()
      while (run) {
        val bounds = App.execNGet {
          val shell = getShell
          run = shell != null && !shell.isDisposed()
          if (run) shell.getBounds() else null
        }
        val cursorPoint = {
          val pt = MouseInfo.getPointerInfo.getLocation()
          new Point(pt.getX.toInt, pt.getY.toInt)
        }
        Option(bounds).foreach { bounds =>
          val adjusted = new Rectangle(bounds.x - ElementCreate.gap, bounds.y - ElementCreate.gap,
            bounds.width + ElementCreate.gap * 2, bounds.height + ElementCreate.gap * 2)
          if (!adjusted.contains(cursorPoint)) {
            run = false
            App.exec(parentShell.close())
          }
        }
        Thread.sleep(200)
      }
    }
  })
  /** Available templates */
  val templateList = Data.elementTemplates.values.filter(_.availability).toList.sortBy(_.id.name)
  assert(ElementCreate.dialog.isEmpty, "ElementCreate dialog is already active")

  def getCreatedElement(): Option[Element.Generic] = element

  protected def createButton(template: ElementTemplate, container: Composite): Button = {
    val button = new Button(container, SWT.PUSH)
    button.setText(template.id.name)
    button.setToolTipText(template.name)
    button.setBackground(ResourceManager.getColor(SWT.COLOR_WHITE))
    button.setLayoutData(new GridData(GridData.FILL_BOTH))
    button.addListener(SWT.Selection, new ElementCreate.ButtonListener(this, template, this.container))
    button.setFont(Resources.fontSmall)
    //button.setImage(image)
    button.pack()
    button
  }
  /** Create contents of the dialog. */
  override protected def createDialogArea(parent: Composite): Control = {
    val container = super.createDialogArea(parent).asInstanceOf[Composite]
    val shell = getShell
    val total = templateList.size
    val cols = math.ceil(math.sqrt(total)).toInt
    val rows = math.ceil(total.toDouble / cols).toInt
    log.debug("build ElementCreate for %d element(s) with size %dx%d".format(total, cols, rows))
    val templates = new Composite(container, SWT.NONE)
    val templatesLayout = new GridLayout(cols, true)
    templates.setLayout(templatesLayout)
    templatesLayout.horizontalSpacing = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING)
    templatesLayout.verticalSpacing = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_SPACING)
    templatesLayout.marginHeight = templatesLayout.horizontalSpacing
    templatesLayout.marginWidth = templatesLayout.verticalSpacing
    val templatesBorderGapX = math.floor(templatesLayout.marginHeight.toDouble / 2).toInt
    val templatesBorderGapY = math.floor(templatesLayout.marginWidth.toDouble / 2).toInt
    templates.setLayoutData(BorderLayout.CENTER)
    templates.setBackground(ResourceManager.getColor(SWT.COLOR_WHITE))
    templates.addPaintListener(new PaintListener() {
      def paintControl(e: PaintEvent) {
        e.gc.setForeground(ResourceManager.getColor(SWT.COLOR_WIDGET_NORMAL_SHADOW))
        e.gc.drawRoundRectangle(templatesBorderGapX, templatesBorderGapY,
          templates.getBounds().width - templatesBorderGapX * 2, templates.getBounds().height - templatesBorderGapY * 2,
          math.min(templatesBorderGapX, 4) * 2, math.min(templatesBorderGapY, 4) * 2)
      }
    })
    for (i <- 0 until rows) {
      log.debug("process row %d".format(i + 1))
      for (j <- (i * cols) until (i * cols + cols) if j < total) {
        log.debug("process element %d: %s".format(j + 1, templateList(j)))
        val lastRow = j == rows - 1
        val button = createButton(templateList(total - j - 1), templates)
      }
    }
    // Add the dispose listener
    getShell().addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) {
        ElementCreate.dialog = None
      }
    })
    // Update the templates size
    val length = templates.getChildren().foldLeft(0) { (acc, child) =>
      val size = child.computeSize(SWT.DEFAULT, SWT.DEFAULT)
      math.max(math.max(size.x, size.y), acc)
    }
    templates.getChildren().foreach { child =>
      val data = child.getLayoutData.asInstanceOf[GridData]
      data.widthHint = length
      data.heightHint = length
    }
    // Update the shell size
    val defaultShellSize = shell.getSize()
    shell.pack()
    val packedShellSize = shell.getSize()
    val shellWidth = math.min(defaultShellSize.x, packedShellSize.x)
    val shellHeight = math.min(defaultShellSize.y, packedShellSize.y)
    shell.setSize(new Point(shellWidth, shellHeight))
    // Update the shell location
    val cursorPoint = {
      val pt = MouseInfo.getPointerInfo.getLocation()
      new Point(pt.getX.toInt, pt.getY.toInt)
    }
    App.display.getMonitors().find(_.getBounds.contains(cursorPoint)) match {
      case Some(currentMonitor) =>
        val bounds = currentMonitor.getBounds()
        val prefferedLocationX = math.max(bounds.x + convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN),
          cursorPoint.x - convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN) * 2)
        val prefferedLocationY = math.max(bounds.y + convertHorizontalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN),
          cursorPoint.y - convertHorizontalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN) * 2)
        val xMax = bounds.x + bounds.width
        val yMax = bounds.y + bounds.height
        if (prefferedLocationX + shellWidth > xMax && prefferedLocationY + shellHeight > yMax)
          getShell.setLocation(xMax - shellWidth - convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN),
            yMax - shellHeight - convertHorizontalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN))
        else if (prefferedLocationX + shellWidth > xMax)
          getShell.setLocation(xMax - shellWidth - convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN), prefferedLocationY)
        else if (prefferedLocationY + shellHeight > yMax)
          getShell.setLocation(prefferedLocationX, yMax - shellHeight - convertHorizontalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN))
        else
          shell.setLocation(prefferedLocationX, prefferedLocationY)
      case None =>
        val prefferedLocationX = cursorPoint.x - convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN) * 2
        val prefferedLocationY = cursorPoint.y - convertHorizontalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN) * 2
        shell.setLocation(prefferedLocationX, prefferedLocationY)
    }
    ElementCreate.dialog = Some(this)
    container
  }
  /** onActive callback */
  protected def onActive {
    guargThread.setDaemon(true)
    guargThread.start()
  }
}

object ElementCreate {
  /** There is may be only one dialog instance at time */
  @volatile private var dialog: Option[ElementCreate] = None
  val gap = 10
  class ButtonListener(dialog: ElementCreate, template: ElementTemplate, container: Element.Generic) extends Listener {
    def handleEvent(event: Event) = {
      dialog.close()
      /*JobCreateElementFromTemplate(template, container).foreach(_.setOnSucceeded { job =>
        job.getValue.foreach {
          case (element) => Main.exec {
            assert(dialog.element.isEmpty)
            dialog.element = Some(element)
          }
        }
      }.execute)*/
    }
  }
}
