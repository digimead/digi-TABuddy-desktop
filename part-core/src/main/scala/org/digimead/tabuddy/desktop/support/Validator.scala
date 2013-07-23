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

package org.digimead.tabuddy.desktop.support

import scala.ref.WeakReference

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Resources
import org.digimead.tabuddy.desktop.Messages
import org.eclipse.jface.fieldassist.ControlDecoration
import org.eclipse.swt.SWT
import org.eclipse.swt.events.FocusAdapter
import org.eclipse.swt.events.FocusEvent
import org.eclipse.swt.events.VerifyEvent
import org.eclipse.swt.events.VerifyListener
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.widgets.Combo
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Text

class Validator private[support] (
  /** ControlDecoration instance */
  val decoration: WeakReference[ControlDecoration],
  /** Set the boolean that controls whether the decoration is shown only when the control has focus. */
  showOnlyOnFocus: Boolean,
  /** Validation callback */
  callback: (Validator, VerifyEvent) => Any)
  extends VerifyListener with Loggable {
  withDecoration { decoration =>
    val control = decoration.getControl()
    decoration.setShowOnlyOnFocus(true)
    control.addFocusListener(new FocusAdapter() {
      override def focusLost(e: FocusEvent) = withDecoration(decoration =>
        if (decoration.getShowOnlyOnFocus()) decoration.hide())
    })
  }

  /**
   * Get the boolean that controls whether the decoration is shown only when
   * the control has focus. The default value of this setting is
   * <code>false</code>.
   */
  def getShowOnlyOnFocus(): Boolean = withDecoration(_.getShowOnlyOnFocus()).getOrElse(false)
  /**
   * Set the boolean that controls whether the decoration is shown only when
   * the control has focus. The default value of this setting is
   * <code>false</code>.
   */
  def setShowOnlyOnFocus(showOnlyOnFocus: Boolean) = withDecoration(_.setShowOnlyOnFocus(showOnlyOnFocus))
  def showDecorationRequired(decoration: ControlDecoration, message: String = Messages.valueIsNotDefined_text) =
    showDecoration(decoration, message, Resources.Image.required)
  def showDecorationError(decoration: ControlDecoration, message: String = Messages.valueCharacterIsNotValid_text) =
    showDecoration(decoration, message, Resources.Image.error)
  def showDecoration(decoration: ControlDecoration, message: String, image: Image) {
    decoration.hide()
    decoration.setImage(image)
    decoration.setDescriptionText(message)
    decoration.show()
    decoration.setShowHover(true)
    decoration.showHoverText(message)
  }
  /** Sent when the text is about to be modified. */
  override def verifyText(e: VerifyEvent) = callback(this, e)
  /** Get decoration if any */
  def withDecoration[T](f: ControlDecoration => T): Option[T] = decoration.get.map(f(_))
}

object Validator {
  /** The default decoration location for the new validator */
  val defaultDecorationLocation = SWT.TOP | SWT.LEFT

  /** Add validation listener to control */
  def apply[T](control: Control, showOnlyOnFocus: Boolean)(onValidation: (Validator, VerifyEvent) => T): Validator =
    apply(control, showOnlyOnFocus, null)(onValidation)
  /** Add validation listener to control */
  def apply[T](control: Control, showOnlyOnFocus: Boolean, decoration: ControlDecoration)(onValidation: (Validator, VerifyEvent) => T): Validator = {
    val viewerDecoration = Option(decoration).getOrElse(new ControlDecoration(control, defaultDecorationLocation))
    val validator = new Validator(WeakReference(viewerDecoration), showOnlyOnFocus, onValidation)
    control match {
      case combo: Combo =>
        combo.addVerifyListener(validator)
      case text: Text =>
        text.addVerifyListener(validator)
      case _ =>
    }
    validator
  }
}
