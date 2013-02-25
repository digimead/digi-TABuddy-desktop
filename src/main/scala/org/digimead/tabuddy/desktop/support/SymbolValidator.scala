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

import org.digimead.tabuddy.desktop.Data
import org.digimead.tabuddy.desktop.Resources
import org.digimead.tabuddy.desktop.res.Messages
import org.eclipse.jface.fieldassist.ControlDecoration
import org.eclipse.swt.events.VerifyEvent
import org.eclipse.swt.widgets.Combo
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Text

class SymbolValidator private[support] (override val decoration: WeakReference[ControlDecoration], showOnlyOnFocus: Boolean, callback: (Validator, VerifyEvent) => Any)
  extends Validator(decoration, showOnlyOnFocus, callback) {
  override def showDecorationRequired(decoration: ControlDecoration, message: String = Messages.identificatorIsNotDefined_text) =
    showDecoration(decoration, message, Resources.imageRequired)
  override def showDecorationError(decoration: ControlDecoration, message: String = Messages.identificatorCharacterIsNotValid_text) =
    showDecoration(decoration, message, Resources.imageError)

  /** Sent when the text is about to be modified. */
  override def verifyText(e: VerifyEvent) {
    if (e.text.nonEmpty && e.character != '\0') {
      // add lead letter if _ is a first symbol and start is not 0
      val text = if (e.start != 0 && e.text(0) == '_') "a" + e.text else e.text
      e.doit = Data.symbolPattern.matcher(text).matches()
    }
    callback(this, e)
  }
}

object SymbolValidator {
  /** Add validation listener to control */
  def apply[T](control: Control, showOnlyOnFocus: Boolean)(onValidation: (Validator, VerifyEvent) => T): SymbolValidator =
    apply(control, showOnlyOnFocus, null)(onValidation)
  /** Add validation listener to control */
  def apply[T](control: Control, showOnlyOnFocus: Boolean, decoration: ControlDecoration)(onValidation: (Validator, VerifyEvent) => T): SymbolValidator = {
    val viewerDecoration = Option(decoration).getOrElse(new ControlDecoration(control, Validator.defaultDecorationLocation))
    val validator = new SymbolValidator(WeakReference(viewerDecoration), showOnlyOnFocus, onValidation)
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
