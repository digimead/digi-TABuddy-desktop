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

import akka.actor.{ Actor, ActorRef, Props, actorRef2Scala }
import akka.pattern.ask
import java.util.UUID
import java.util.concurrent.TimeoutException
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.Core
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.Timeout
import org.digimead.tabuddy.desktop.core.ui.Messages
import org.digimead.tabuddy.desktop.core.ui.block.{ Configuration, ViewLayer }
import org.digimead.tabuddy.desktop.core.ui.definition.widget.VComposite
import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.widgets.{ Composite, Widget }
import org.eclipse.ui.console.MessageConsole
import scala.collection.immutable
import scala.concurrent.{ Await, Future }

class ViewConsole(val contentId: UUID) extends Actor with Loggable {
  /** Parent view widget. */
  @volatile protected var view: Option[VComposite] = None
  log.debug("Start actor " + self.path)

  def receive = {
    case message @ App.Message.Create(Left(parentWidget: VComposite), None) ⇒ App.traceMessage(message) {
      create(parentWidget) match {
        case Some(contentWidget) ⇒
          App.publish(App.Message.Create(Right(contentWidget), self))
          App.Message.Create(Right(contentWidget))
        case None ⇒
          App.Message.Error(s"Unable to create ${this} for ${parentWidget}.")
      }
    } foreach { sender ! _ }

    case message @ App.Message.Destroy ⇒ App.traceMessage(message) {
      App.execNGet { destroy(sender) }
      this.view = None
    }

    case message @ App.Message.Start(Left(widget: Widget), None) ⇒ App.traceMessage(message) {
      onStart(widget)
      App.Message.Start(Right(widget))
    } foreach { sender ! _ }

    case message @ App.Message.Stop(Left(widget: Widget), None) ⇒ App.traceMessage(message) {
      App.Message.Stop(Right(widget))
    } foreach { sender ! _ }
  }

  /**
   * Create view content.
   * @return content container.
   */
  protected def create(parent: VComposite): Option[Composite] = {
    if (view.nonEmpty)
      throw new IllegalStateException("Unable to create view. It is already created.")
    App.assertEventThread(false)
    view = Option(parent)
    App.execNGet {
      parent.setLayout(new FillLayout())
      new Console(parent, SWT.NONE, new MessageConsole("console", null, false))
      parent.getParent().setMinSize(parent.computeSize(SWT.DEFAULT, SWT.DEFAULT))
      parent.layout()
    }
    Some(null)
  }
  /** Destroy created window. */
  protected def destroy(sender: ActorRef) = this.view.foreach { view ⇒
    App.assertEventThread()
  }
  /** User start interaction with window/stack supervisor/view/this content. Focus is gained. */
  protected def onStart(widget: Widget) = view match {
    case Some(view) ⇒
      log.debug("View started by focus event on " + widget)
    case None ⇒
      log.fatal("Unable to start view without widget.")
  }
}

object ViewConsole extends ViewLayer.Factory with Loggable {
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

  /** Returns actor reference that could handle Create/Destroy messages. */
  @log
  def viewActor(configuration: Configuration.View): Option[ActorRef] = viewActorLock.synchronized {
    implicit val ec = App.system.dispatcher
    implicit val timeout = akka.util.Timeout(Timeout.short)
    val viewName = "Content_" + id + "_%08X".format(configuration.id.hashCode())
    val future = Core.actor ? App.Message.Attach(props.copy(args = immutable.Seq(configuration.id)), viewName)
    try {
      val newActorRef = Await.result(future.asInstanceOf[Future[ActorRef]], timeout.duration)
      activeActorRefs.set(activeActorRefs.get() :+ newActorRef)
      titlePerActor.values.foreach(_.update)
      Some(newActorRef)
    } catch {
      case e: InterruptedException ⇒
        log.error(e.getMessage, e)
        None
      case e: TimeoutException ⇒
        log.error(e.getMessage, e)
        None
    }
  }
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
