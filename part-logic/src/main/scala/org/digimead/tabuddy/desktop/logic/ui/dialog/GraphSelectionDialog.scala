/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2014 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.logic.ui.dialog

import javax.inject.Inject
import org.digimead.digi.lib.jfx4swt.util.JFXUtil
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.ui.{ ResourceManager, UI }
import org.digimead.tabuddy.desktop.core.ui.definition.Dialog
import org.eclipse.e4.core.contexts.IEclipseContext
import org.eclipse.swt.SWT
import org.eclipse.swt.events.{ DisposeEvent, DisposeListener, FocusEvent, FocusListener, PaintEvent, PaintListener, ShellAdapter, ShellEvent }
import org.eclipse.swt.widgets.{ Composite, Control, Sash, Shell }
import org.digimead.tabuddy.desktop.core.ui.Resources

/**
 * Graph selection dialog.
 */
class GraphSelectionDialog @Inject() (
  /** This dialog context. */
  val context: IEclipseContext,
  /** Parent shell. */
  val parentShell: Shell) extends GraphSelectionDialogSkel(parentShell) with Dialog with Loggable {
  /** Create contents of the dialog. */
  override protected def createDialogArea(parent: Composite): Control = {
    val result = super.createDialogArea(parent)
    context.set(classOf[Composite], parent)
    val sashForm = getSashForm()
    sashForm.setSize(UI.DEFAULT_WIDTH, UI.DEFAULT_HEIGHT)
    sashForm.layout()
    sashForm.getChildren().filter(_.isInstanceOf[Sash]).foreach { sash ⇒
      sash.addPaintListener(SashPaintListener)
      sash.redraw()
    }
    val sashBackground = JFXUtil.fromRGB(sashForm.getBackground().getRGB())
    if (JFXUtil.isDark(sashBackground))
      SashPaintListener.sashColor = ResourceManager.getColor(JFXUtil.toRGB(sashBackground.brighter()))
    else
      SashPaintListener.sashColor = ResourceManager.getColor(JFXUtil.toRGB(sashBackground.darker()))
    getShell().addShellListener(ShellContextActivator)
    getShell().addFocusListener(FocusContextActivator)
    // Add the dispose listener
    getShell().addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) {
        getSashForm().removePaintListener(SashPaintListener)
        getShell().removeFocusListener(FocusContextActivator)
        getShell().removeShellListener(ShellContextActivator)
      }
    })
    result
  }

  /** Activate context on focus. */
  object FocusContextActivator extends FocusListener() {
    def focusGained(e: FocusEvent) = context.activateBranch()
    def focusLost(e: FocusEvent) {}
  }
  /** Activate context on shell events. */
  object ShellContextActivator extends ShellAdapter() {
    override def shellActivated(e: ShellEvent) = context.activateBranch()
  }
  /** Sash delimiter painter. */
  object SashPaintListener extends PaintListener {
    val delimeterSize = Resources.convertHeightInCharsToPixels(1)
    var sashColor = ResourceManager.getColor(SWT.COLOR_WIDGET_BACKGROUND)
    def paintControl(e: PaintEvent) = {
      e.widget match {
        case sash: Sash ⇒
          val vertical = (sash.getStyle() & SWT.VERTICAL) != 0
          val background = e.gc.getBackground()
          val size = sash.getSize()
          e.gc.setBackground(sashColor)
          if (vertical) {
            val from = (size.y - delimeterSize) / 2
            e.gc.fillRectangle(0, from, size.x, delimeterSize)
          } else {
            val from = (size.x - delimeterSize) / 2
            e.gc.fillRectangle(from, 0, delimeterSize, size.y)
          }
      }
    }
  }
}
