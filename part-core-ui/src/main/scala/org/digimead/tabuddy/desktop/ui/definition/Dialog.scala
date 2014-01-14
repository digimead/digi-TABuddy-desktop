/**
 * This file is part of the TA Buddy project.
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

package org.digimead.tabuddy.desktop.ui.definition

import java.util.concurrent.atomic.AtomicBoolean
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.tabuddy.desktop.core.support.App
import org.eclipse.jface.dialogs.IDialogSettings
import org.eclipse.swt.SWT
import org.eclipse.swt.events.{ DisposeEvent, DisposeListener }
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.widgets.{ Composite, Control, Event, Listener, Shell }

trait Dialog extends org.eclipse.jface.dialogs.Dialog {
  protected val blockOnOpen = false
  /** On active listener flag */
  protected val onActiveFlag = new AtomicBoolean(true)
  /** On active listener */
  protected val onActiveListener = new Dialog.OnActiveListener(this)
  /** Dialog parent shell. */
  protected val parentShell: Shell
  /** On close handler. */
  protected val onClose = new ThreadLocal[(Int) ⇒ Any]()

  /** Opens this dialog, creating it first if it has not yet been created. */
  override def open(): Int = {
    App.assertEventThread()
    onClose.remove()
    super.open()
  }
  /** Opens this dialog, creating it first if it has not yet been created. */
  def open[T](f: (Int) ⇒ T) {
    App.assertEventThread()
    onClose.set(f)
    super.open()
  }
  /** Focuses exists dialog or opens this one, creating it first if it has not yet been created. */
  def openOrFocus(): Int = {
    App.assertEventThread()
    onClose.remove()
    super.open()
  }
  /** Focuses exists dialog or opens this one, creating it first if it has not yet been created. */
  def openOrFocus[T](f: (Int) ⇒ T) {
    App.assertEventThread()
    onClose.set(f)
    super.open()
  }
  /**
   * Closes this window, disposes its shell, and removes this window from its
   * window manager (if it has one).
   */
  override protected def close(): Boolean = {
    val result = super.close
    if (result) {
      Option(onClose.get()).foreach { f ⇒ f(getReturnCode()) }
      onClose.remove()
    }
    result
  }
  /** Create contents of the dialog. */
  override protected def createDialogArea(parent: Composite): Control = {
    setBlockOnOpen(blockOnOpen)
    val result = super.createDialogArea(parent)
    // The onPaintListener solution is not sufficient
    App.display.addFilter(SWT.Paint, onActiveListener)
    parentShell.addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) = App.display.removeFilter(SWT.Paint, onActiveListener)
    })
    result
  }
  /**
   * Gets the dialog settings that should be used for remembering the bounds of
   * of the dialog, according to the dialog bounds strategy.
   *
   * @return settings the dialog settings used to store the dialog's location
   *         and/or size, or <code>null</code> if the dialog's bounds should
   *         never be stored.
   *
   */
  override protected def getDialogBoundsSettings(): IDialogSettings =
    Dialog.DI.dialogSettingsFactory("settings", this.getClass.getName).asInstanceOf[IDialogSettings]
  /** Return the initial size of the dialog. */
  override protected def getInitialSize(): Point =
    if (getDialogBoundsSettings().getSection(null) != null)
      // there is already saved settings
      super.getInitialSize()
    else {
      val default = super.getInitialSize
      val parent = parentShell.getBounds()
      val initialWidth = (parent.width * 0.9).toInt
      val initialHeight = (parent.height * 0.8).toInt
      val lowerX = math.min(math.max(initialWidth, default.x), parent.width)
      val lowerY = math.min(math.max(initialHeight, default.y), parent.height)
      val upperX = math.max(lowerX, default.x)
      val upperY = math.max(lowerY, default.y)
      new Point(upperX, upperY)
    }
  /** Return the initial location to use for the shell. */
  override protected def getInitialLocation(initialSize: Point): Point =
    if (getDialogBoundsSettings().getSection(null) != null)
      // there is already saved settings
      super.getInitialLocation(initialSize)
    else {
      val parent = parentShell.getBounds()
      val paddingX = parent.x + ((parent.width - initialSize.x) / 2).toInt
      val paddingY = parent.y + ((parent.height - initialSize.y) / 2).toInt
      new Point(paddingX, paddingY)
    }
  /** onActive callback */
  protected def onActive() {}
}

object Dialog {
  def openOrFocus(dialog: Dialog): Int = {
    dialog.open()
  }
  /** OnActive listener. */
  class OnActiveListener(dialog: Dialog) extends Listener() {
    def handleEvent(event: Event) = event.widget match {
      case control: Control if control.getShell.eq(dialog.getShell) ⇒
        if (dialog.onActiveFlag.compareAndSet(true, false)) {
          App.display.removeFilter(SWT.Paint, this)
          dialog.onActive()
        }
      case other ⇒
    }
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Dialog settings factory. */
    lazy val dialogSettingsFactory = inject[api.Dialog.Factory]
  }
}
