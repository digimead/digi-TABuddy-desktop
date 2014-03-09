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

package org.digimead.tabuddy.desktop.core.ui.view.defaultv

import akka.actor.{ Actor, Props }
import java.util.UUID
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.jfx4swt.JFX
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.{ Messages, block }
import org.digimead.tabuddy.desktop.core.ui.definition.IView
import org.digimead.tabuddy.desktop.core.ui.definition.widget.VComposite
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.widgets.Composite

/**
 * Default application view.
 */
/* http://stackoverflow.com/questions/15421527/how-to-animate-2d-curve-arrows-between-two-nodes-in-javafx?rq=1 */
class View(val contentId: UUID, val factory: block.View.Factory) extends Actor with IView with Loggable {
  log.debug("Start actor " + self.path)

  /** Creates and returns this window's contents. */
  protected def createContents(parent: VComposite): Composite = {
    val body = new Content(parent)
    body.initializeSWT()
    JFX.exec { body.initializeJFX() }
    body
  }
}

object View extends Loggable {
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)

  /** Default view factory. */
  def factory = DI.factory

  class Factory extends block.View.Factory {
    /** View name. */
    lazy val name = View.DI.name
    /** Short view description (one line). */
    lazy val shortDescription = View.DI.shortDescription
    /** Long view description. */
    lazy val longDescription = View.DI.longDescription
    /** View image. */
    lazy val image = View.DI.image
    /** Features. */
    val features: Seq[String] = Seq()

    /** Default view actor reference configuration object. */
    def props = View.DI.props
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Default view factory. */
    lazy val factory = injectOptional[Factory] getOrElse new Factory
    /** View name. */
    lazy val name = injectOptional[Symbol]("Core.View.Default.Name") getOrElse Symbol({
      val name = Messages.default_text
      if (!App.symbolPattern.matcher(name).matches())
        throw new IllegalArgumentException(s"'${name}' isn't a correct Scala symbol.")
      name
    })
    /** Short view description (one line). */
    lazy val shortDescription = injectOptional[String]("Core.View.Default.ShortDescription") getOrElse Messages.defaultViewShortDescription
    /** Long view description. */
    lazy val longDescription = injectOptional[String]("Core.View.Default.LongDescription") getOrElse Messages.defaultViewLongDescription
    /** View image. */
    lazy val image = injectOptional[Image]("Core.View.Default.Image")
    /** Default view actor reference configuration object. */
    lazy val props = injectOptional[Props]("Core.View.Default") getOrElse Props(classOf[View],
      // content id == view layer id
      UUID.fromString("00000000-0000-0000-0000-000000000000"),
      // stub factory
      null.asInstanceOf[View.Factory])
  }
}
