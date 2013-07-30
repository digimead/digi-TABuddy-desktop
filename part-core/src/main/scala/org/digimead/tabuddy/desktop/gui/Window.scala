/**
 * This file is part of the TABuddy project.
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

package org.digimead.tabuddy.desktop.gui

import java.util.UUID

import scala.collection.immutable

import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Core
import org.digimead.tabuddy.desktop.gui.widget.WComposite
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.Timeout
import org.eclipse.e4.core.internal.contexts.EclipseContext
import org.eclipse.swt.widgets.Widget

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.pattern.ask

/**
 * Actor that represents application window, responsible for:
 * - create window
 * - open window
 * - start window
 * - close/destroy window
 */
class Window(parentContext: EclipseContext) extends Actor with WComposite.Controller with Loggable {
  /** Window id. */
  lazy val windowId = UUID.fromString(self.path.name.split("@").last)
  /** Window JFace instance. */
  var window: Option[WComposite] = None
  /** Window context. */
  lazy val windowContext = parentContext.createChild(self.path.name).asInstanceOf[EclipseContext]
  /** Window views supervisor. */
  lazy val stackSupervisor = context.actorOf(StackSupervisor.props.copy(args = immutable.Seq[Any](windowContext)), StackSupervisor.id)
  log.debug("Start actor " + self.path)

  def receive = {
    case message @ App.Message.Attach(props, name) => App.traceMessage(message) {
      sender ! context.actorOf(props, name)
    }
    case message @ App.Message.Close => App.traceMessage(message) {
      close(sender)
    }
    case message @ App.Message.Create(windowId: UUID, supervisor) => App.traceMessage(message) {
      assert(windowId == this.windowId)
      create(supervisor)
    }
    case message @ App.Message.Create(viewFactory: ViewLayer.Factory, supervisor) => App.traceMessage(message) {
      create(viewFactory)
    }
    case message @ App.Message.Destroy => App.traceMessage(message) {
      destroy(sender)
    }
    case message @ App.Message.Open => App.traceMessage(message) {
      open(sender)
    }
    case message @ App.Message.Start(widget: Widget, supervisor) => App.traceMessage(message) {
      onStart(widget)
    }
    case message @ Window.Message.Get => App.traceMessage(message) {
      sender ! window
    }
  }
  override def postStop() = log.debug(self.path.name + " actor is stopped.")

  /** Close window. */
  protected def close(sender: ActorRef) = {
    log.debug(s"Close window ${windowId}.")
    this.window.foreach(window => App.exec {
      if (window.getShell() != null && !window.getShell().isDisposed())
        if (sender != context.system.deadLetters)
          sender ! Window.Message.CloseResult(window.close())
    })
  }
  /** Create window. */
  protected def create(supervisor: ActorRef) {
    if (window.nonEmpty)
      throw new IllegalStateException("Unable to create window. It is already created.")
    log.debug(s"Create window ${windowId}.")
    implicit val ec = App.system.dispatcher
    implicit val timeout = akka.util.Timeout(Timeout.short)
    supervisor ? WindowSupervisor.Message.Get(Some(windowId)) onSuccess {
      case configuration: WindowConfiguration =>
        this.window = Option(App.execNGet {
          val window = new WComposite(windowId, self, stackSupervisor, windowContext, null)
          window.configuration = Some(configuration)
          window
        })
        this.window.foreach(window => App.publish(App.Message.Created(window, self)))
    }
  }
  /** Create new view within window. */
  protected def create(viewFactory: ViewLayer.Factory) {
    if (window.isEmpty)
      throw new IllegalStateException(s"Unable to create view from ${viewFactory}. Window isn't created.")
    log.debug(s"Create new view from ${viewFactory}.")
    stackSupervisor ! App.Message.Create(viewFactory, self)
  }
  /** Destroy created window. */
  protected def destroy(sender: ActorRef) = this.window.foreach { window =>
    window.saveOnClose = false
    close(sender)
  }
  /** User start interaction with window. Focus is gained. */
  protected def onStart(widget: Widget) = window match {
    case Some(window) =>
      Core.context.set(GUI.windowContextKey, window)
      windowContext.activateBranch()
      stackSupervisor ! App.Message.Start(widget, self)
      if (window.contentVisible.compareAndSet(false, true))
        App.exec { window.showContent }
    case None =>
      log.fatal("Unable to start window without widget.")
  }
  /** Open created window. */
  protected def open(sender: ActorRef) = this.window.foreach(window => App.exec {
    if (window.getShell() == null || (window.getShell() != null && !window.getShell().isDisposed()))
      if (sender != context.system.deadLetters)
        sender ! Window.Message.OpenResult(window.open())
  })
}

object Window extends Loggable {
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  // Initialize descendant actor singletons
  StackSupervisor

  /** Window actor reference configuration object. */
  def props = DI.props

  trait Message extends App.Message
  object Message {
    /** Get window composite. */
    case object Get
    case class OpenResult private[Window] (arg: Int) extends Message
    case class CloseResult private[Window] (arg: Boolean) extends Message
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Window actor reference configuration object. */
    lazy val props = injectOptional[Props]("Core.GUI.Window") getOrElse Props(classOf[Window], Core.context)
  }
}
