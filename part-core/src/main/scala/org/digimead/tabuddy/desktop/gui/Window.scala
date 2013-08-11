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
import scala.concurrent.Await

import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Core
import org.digimead.tabuddy.desktop.gui.widget.AppWindow
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.definition.Context
import org.digimead.tabuddy.desktop.definition.Context.rich2appContext
import org.digimead.tabuddy.desktop.support.Timeout
import org.eclipse.swt.widgets.Widget

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.pattern.ask
import akka.util.Timeout.durationToTimeout

/**
 * Actor that represents application window, responsible for:
 * - create window
 * - open window
 * - start window
 * - close/destroy window
 */
class Window(val windowId: UUID, val windowContext: Context.Rich) extends Actor with AppWindow.Controller with Loggable {
  /** Window JFace instance. */
  var window: Option[AppWindow] = None
  /** Window views supervisor. */
  lazy val stackSupervisor = context.actorOf(StackSupervisor.props.copy(args = immutable.Seq(windowId, windowContext)), StackSupervisor.id)
  log.debug("Start actor " + self.path)

  def receive = {
    case message @ App.Message.Close => App.traceMessage(message) {
      close(sender)
    }

    case message @ App.Message.Create(Left(Window.<>(windowId, configuration)), None) => App.traceMessage(message) {
      assert(windowId == this.windowId)
      create(configuration, sender) match {
        case Some(appWindow) =>
          App.publish(App.Message.Create(Right(appWindow), self))
          App.Message.Create(Right(appWindow))
        case None =>
          App.Message.Error("Unable to create ${viewConfiguration}.")
      }
    } foreach { sender ! _ }

    case message @ App.Message.Destroy => App.traceMessage(message) {
      destroy(sender)
    }

    case message @ App.Message.Open => App.traceMessage(message) {
      open(sender)
    }

    case message @ App.Message.Start(Left(widget: Widget), None) => App.traceMessage(message) {
      onStart(widget)
      App.Message.Start(Right(widget))
    } foreach { sender ! _ }

    case message @ App.Message.Stop(Left(widget: Widget), None) => App.traceMessage(message) {
      onStop(widget)
      App.Message.Stop(Right(widget))
    } foreach { sender ! _ }

    case message @ Window.Message.Get => App.traceMessage(message) {
      window
    } foreach { sender ! _ }

    case message @ App.Message.Create(Left(viewFactory: ViewLayer.Factory), None) =>
      stackSupervisor.forward(message)

    case message @ App.Message.Save =>
      stackSupervisor.forward(message)
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
  protected def create(configuration: WindowConfiguration, supervisor: ActorRef): Option[AppWindow] = {
    if (this.window.nonEmpty)
      throw new IllegalStateException("Unable to create window. It is already created.")
    App.assertUIThread(false)
    log.debug(s"Create window ${windowId}.")
    val window = new AppWindow(windowId, self, stackSupervisor, windowContext, null)
    window.configuration = Some(configuration)
    this.window = Option(window)
    this.window
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
      Await.ready(ask(stackSupervisor, App.Message.Start(Left(widget)))(Timeout.short), Timeout.short)
    case None =>
      log.fatal("Unable to start unexists window.")
  }
  /** Focus is lost. */
  protected def onStop(widget: Widget) = window match {
    case Some(window) =>
      Await.ready(ask(stackSupervisor, App.Message.Stop(Left(widget)))(Timeout.short), Timeout.short)
    case None =>
      log.fatal("Unable to stop unexists window.")
  }
  /** Open created window. */
  protected def open(sender: ActorRef) = this.window.foreach(window => App.execNGet {
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

  /** Wrapper for App.Message,Create argument. */
  case class <>(val windowId: UUID, val configuration: WindowConfiguration) {
    override def toString() = "<>([%08X]=%s, %s)".format(windowId.hashCode(), windowId.toString(), configuration)
  }
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
    lazy val props = injectOptional[Props]("Core.GUI.Window") getOrElse Props(classOf[Window],
      // window id
      UUID.fromString("00000000-0000-0000-0000-000000000000"),
      // parent context
      Core.context)
  }
}
