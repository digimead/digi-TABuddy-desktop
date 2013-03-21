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

package org.digimead.tabuddy.desktop

import java.lang.reflect.Modifier

import scala.Array.canBuildFrom
import scala.collection.immutable

import org.digimead.tabuddy.desktop.res.Messages
import org.digimead.tabuddy.desktop.res.SWTResourceManager
import org.digimead.tabuddy.desktop.ui.Window
import org.eclipse.jface.fieldassist.FieldDecorationRegistry
import org.eclipse.swt.graphics.Font
import org.eclipse.swt.graphics.Image

object Resources extends Main.Interface {
  val small = 0.315
  lazy val CHECKED = getImage("/light/icons/appbar.checkmark.thick.png", small)
  lazy val UNCHECKED = getImage("/light/icons/appbar.checkmark.thick.unchecked.png", small)
  /** the large font */
  lazy val fontLarge = {
    val fD = Main.display.getSystemFont().getFontData()
    fD.head.setHeight(fD.head.getHeight + 1)
    new Font(Main.display, fD.head)
  }
  /** The small font */
  lazy val fontSmall = {
    val fD = Main.display.getSystemFont().getFontData()
    fD.head.setHeight(fD.head.getHeight - 1)
    new Font(Main.display, fD.head)
  }
  lazy val imageError = FieldDecorationRegistry.getDefault().getFieldDecoration(FieldDecorationRegistry.DEC_ERROR).getImage()
  lazy val imageRequired = FieldDecorationRegistry.getDefault().getFieldDecoration(FieldDecorationRegistry.DEC_REQUIRED).getImage()

  /** Sorted map of the translation bundle */
  lazy val messages = immutable.ListMap(
    classOf[Messages].getFields().filter(field => field.getType() == classOf[String] &&
      Modifier.isStatic(field.getModifiers) && Modifier.isPublic(field.getModifiers)).sortBy(_.getName).
      map(field => (field.getName(), field.get(null).asInstanceOf[String])): _*)

  def getImage(path: String, k: Double) = {
    val image = SWTResourceManager.getImage(getClass, path)
    scale(image, k)
  }
  def scale(image: Image, k: Double): Image = {
    val width = image.getBounds().width
    val height = image.getBounds().height
    new Image(Main.display, image.getImageData().scaledTo((width * k).toInt, (height * k).toInt))
  }
  def start() {
    fontLarge
    fontSmall
  }
  def stop() = {
    CHECKED.dispose()
    UNCHECKED.dispose()
    fontLarge.dispose()
    fontSmall.dispose()
    imageError.dispose()
    imageRequired.dispose()
    SWTResourceManager.dispose()
  }
}
