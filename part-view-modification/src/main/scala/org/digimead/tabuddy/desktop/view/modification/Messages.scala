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

package org.digimead.tabuddy.desktop.view.modification

import java.util.ResourceBundle
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.definition.NLS

/**
 * Resource bundle implementation.
 *
 * This code is directly evaluated in IDE (WindowBuilderPro).
 * Any runtime references that may prevent creation are prohibited.
 */
class Messages extends ResourceBundle {
  def getKeys() = new java.util.Enumeration[String] {
    private val iterator = Messages.T.messages.keys.iterator
    def hasMoreElements(): Boolean = iterator.hasNext
    def nextElement(): String = iterator.next()
  }
  protected def handleGetObject(key: String): Object = try {
    Messages.T.messages.get(key).
      getOrElse { Messages.log.error(s"'${key}' not found in ${this.getClass()}"); key }
  } catch {
    case e: Throwable ⇒
      key
  }
}

object Messages extends NLS with Loggable {
  val ascending_text = ""
  val descending_text = ""
  val no_text = ""
  val viewEditorDescription_text = ""
  val viewEditorDialog_text = ""
  val viewEditorTitle_text = ""
  val viewFilterEditorDescription_text = ""
  val viewFilterEditorDialog_text = ""
  val viewFilterEditorTitle_text = ""
  val viewFilterListDescription_text = ""
  val viewFilterListDialog_text = ""
  val viewFilterListTitle_text = ""
  val viewListDescription_text = ""
  val viewListDialog_text = ""
  val viewListTitle_text = ""
  val viewSortingEditorDescription_text = ""
  val viewSortingEditorDialog_text = ""
  val viewSortingEditorTitle_text = ""
  val viewSortingListDescription_text = ""
  val viewSortingListDialog_text = ""
  val viewSortingListTitle_text = ""
  val yes_text = ""
  val lookupFilter_text = ""
  val nameIsNotDefined_text = ""
  val nameIsAlreadyInUse_text = ""
  val thereAreNoSelectedProperties_text = ""
  val autoresize_key = ""
  val copy_item_text = ""
  val create_text = ""
  val createFrom_text = ""
  val edit_text = ""
  val remove_text = ""
val up_text = ""
val down_text = ""

  T.ranslate("org.digimead.tabuddy.desktop.view.modification.messages")
}
