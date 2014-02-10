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

package org.digimead.tabuddy.desktop.core.ui.view.console

import akka.actor.{ Actor, ActorContext, ActorRef, Props, actorRef2Scala }
import java.util.UUID
import java.util.concurrent.TimeoutException
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.Timeout
import org.digimead.tabuddy.desktop.core.ui.Messages
import org.digimead.tabuddy.desktop.core.ui.block.{ Configuration, View }
import org.digimead.tabuddy.desktop.core.ui.definition.widget.VComposite
import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.widgets.{ Composite, Widget }
import org.eclipse.ui.console.MessageConsole
import scala.collection.immutable

class ViewConsole(val contentId: UUID) extends Actor with Loggable {
  /** View body widget. */
  @volatile var body: Option[Composite] = None
  /** View parent widget. */
  @volatile var parent: Option[VComposite] = None
  /** Container view actor. */
  lazy val view = context.parent
  log.debug("Start actor " + self.path)

  def receive = {
    case message @ App.Message.Create(parentWidget: VComposite, Some(this.view), _) ⇒ App.traceMessage(message) {
      create(parentWidget) match {
        case Some(contentWidget) ⇒
          App.publish(App.Message.Create(contentWidget, self))
          App.Message.Create(contentWidget, self)
        case None ⇒
          App.Message.Error(s"Unable to create ${this} for ${parentWidget}.", None)
      }
    } foreach { sender ! _ }

    case message @ App.Message.Destroy(Left(viewLayerWidget: VComposite), None, _) ⇒ App.traceMessage(message) {
      this.parent.map { viewLayerWidget ⇒
        App.execNGet { destroy(sender) }
        this.parent = None
        App.Message.Destroy(viewLayerWidget, None)
      } getOrElse App.Message.Error(s"Unable to destroy ${this} in ${viewLayerWidget}.", None)
    } foreach { sender ! _ }

    case message @ App.Message.Start(Left(widget: Widget), None, _) ⇒ App.traceMessage(message) {
      App.Message.Start(widget, None)
    } foreach { sender ! _ }

    case message @ App.Message.Stop(Left(widget: Widget), None, _) ⇒ App.traceMessage(message) {
      App.Message.Stop(widget, None)
    } foreach { sender ! _ }
  }

  /**
   * Create view content.
   * @return content container.
   */
  @log
  protected def create(parent: VComposite): Option[Composite] = {
    if (this.parent.nonEmpty)
      throw new IllegalStateException("Unable to create view. It is already created.")
    App.assertEventThread(false)
    this.parent = Option(parent)
    App.execNGet {
      parent.setLayout(new FillLayout())
      new Console(parent, SWT.NONE, new MessageConsole("console", null, false))
      parent.getParent().setMinSize(parent.computeSize(SWT.DEFAULT, SWT.DEFAULT))
      parent.layout()
    }
    Some(null)
  }
  /** Destroy created window. */
  @log
  protected def destroy(sender: ActorRef) = this.parent.foreach { view ⇒
    App.assertEventThread()
  }
}

object ViewConsole extends View.Factory with Loggable {
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  /** View name. */
  lazy val name = DI.name
  /** Short view description (one line). */
  lazy val shortDescription = DI.shortDescription
  /** Long view description. */
  lazy val longDescription = DI.longDescription
  /** View image. */
  lazy val image = DI.image

  /** Default view actor reference configuration object. */
  def props = DI.props

  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** View name. */
    lazy val name = injectOptional[Symbol]("Core.View.Console.Name") getOrElse Symbol({
      val name = Messages.console_text
      if (!App.symbolPattern.matcher(name).matches())
        throw new IllegalArgumentException(s"'${name}' isn't a correct Scala symbol.")
      name
    })
    /** Short view description (one line). */
    lazy val shortDescription = injectOptional[String]("Core.View.Console.ShortDescription") getOrElse Messages.consoleViewShortDescription
    /** Long view description. */
    lazy val longDescription = injectOptional[String]("Core.View.Console.LongDescription") getOrElse Messages.consoleViewLongDescription
    /** View image. */
    lazy val image = injectOptional[Image]("Core.View.Console.Image")
    /** Console view actor reference configuration object. */
    lazy val props = injectOptional[Props]("Core.View.Console") getOrElse Props(classOf[ViewConsole],
      // content id == view layer id
      UUID.fromString("00000000-0000-0000-0000-000000000000"))
  }
}