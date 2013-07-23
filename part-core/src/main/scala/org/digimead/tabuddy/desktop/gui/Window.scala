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
import java.util.concurrent.atomic.AtomicBoolean

import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Core
import org.digimead.tabuddy.desktop.gui.WindowSupervisor.windowGroup2actorSRef
import org.digimead.tabuddy.desktop.gui.window.Creator
import org.digimead.tabuddy.desktop.gui.window.Creator.creator2implementation
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.Timeout
import org.eclipse.e4.core.internal.contexts.EclipseContext
import org.eclipse.jface.window.ApplicationWindow
import org.eclipse.swt.custom.StackLayout
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Shell

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.pattern.ask

/**
 * Actor that represent application window.
 */
class Window extends Actor with Loggable {
  log.debug("Start actor " + self.path)
  protected var window: Option[Window.JFace] = None
  protected var windowContext: Option[EclipseContext] = None

  /** Get subscribers list. */
  def receive = {
    case message @ App.Message.Attach(props, name) => App.traceMessage(message) {
      sender ! context.actorOf(props, name)
    }
    case message @ App.Message.Close => App.traceMessage(message) { close(sender) }
    case message @ App.Message.Create => App.traceMessage(message) { create(UUID.randomUUID()) }
    case message @ App.Message.Create(uuid: UUID, sender) => App.traceMessage(message) { create(uuid) }
    case message @ App.Message.Destroy => App.traceMessage(message) { destroy(sender) }
    case message @ App.Message.Open => App.traceMessage(message) { open(sender) }
    case message @ App.Message.Start => App.traceMessage(message) { onStart() }
  }
  override def postStop() = log.debug(self.path.name + " actor is stopped.")

  /** Close window. */
  protected def close(sender: ActorRef) = this.window.foreach(window => App.exec {
    if (window.getShell() != null && !window.getShell().isDisposed())
      if (sender != context.system.deadLetters)
        sender ! Window.Message.CloseResult(window.close())
  })
  /** Create window. */
  protected def create(id: UUID) {
    implicit val ec = App.system.dispatcher
    implicit val timeout = akka.util.Timeout(Timeout.short)
    WindowSupervisor.actor ? WindowSupervisor.Message.Get(Some(id)) onSuccess {
      case configuration: WindowConfiguration =>
        this.windowContext = Option(Core.context.createChild().asInstanceOf[EclipseContext])
        this.window = Option(App.execNGet {
          val window = new Window.JFace(id, self, null)
          window.setBlockOnOpen(true)
          window.configuration = Some(configuration)
          window
        })
        this.window.foreach { window => App.publish(App.Message.Created(window, self)) }
    }
  }
  /** Destroy created window. */
  protected def destroy(sender: ActorRef) = this.window.foreach { window =>
    window.saveOnClose = false
    close(sender)
  }
  /** User start interaction with window. Focus gained. */
  protected def onStart() {
    windowContext.foreach { context =>
      context.set(Window.id, self)
      context.activateBranch()
    }
    window.foreach { window =>
      if (window.contentVisible.compareAndSet(false, true))
        App.exec { window.showContent }
    }
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
  /** SWT Data ID key */
  val swtId = getClass.getName()

  /** Window actor reference configuration object. */
  def props = DI.props

  trait Message extends App.Message
  object Message {
    case class OpenResult private[Window] (arg: Int) extends Message
    case class CloseResult private[Window] (arg: Boolean) extends Message
  }
  class JFace(val id: UUID, val ref: ActorRef, parentShell: Shell) extends ApplicationWindow(parentShell) {
    @volatile protected[Window] var configuration: Option[WindowConfiguration] = None
    @volatile protected[Window] var saveOnClose = true
    /** Filler composite that visible by default. */
    @volatile protected[Window] var filler: Option[Composite] = None
    /** Content composite that contains views. */
    @volatile protected[Window] var content: Option[Composite] = None
    /** Flag indicating whether the content is visible. */
    protected[Window] val contentVisible = new AtomicBoolean()

    /** Update window configuration. */
    def updateConfiguration() {
      val location = getShell.getBounds()
      this.configuration = Some(WindowConfiguration(getShell.isVisible(), location, Seq()))
    }

    /** Show content. */
    protected[Window] def showContent() = content.foreach { content =>
      val parent = content.getParent()
      val layout = parent.getLayout().asInstanceOf[StackLayout]
      layout.topControl = content
      parent.layout()
    }
    /** Add window ID to shell. */
    override protected def configureShell(shell: Shell) {
      super.configureShell(shell)
      shell.setData(swtId, id)
      configuration.foreach { configuration =>
        val oBounds = shell.getBounds
        val cBounds = configuration.location
        if (cBounds.x < 0 && cBounds.y < 0) {
          // Set only size.
          if (cBounds.width < 0) cBounds.width = oBounds.width
          if (cBounds.height < 0) cBounds.height = oBounds.height
          shell.setSize(cBounds.width, cBounds.height)
        } else {
          // Set size and position.
          if (cBounds.x < 0) cBounds.x = oBounds.x
          if (cBounds.y < 0) cBounds.y = oBounds.y
          if (cBounds.width < 0) cBounds.width = oBounds.width
          if (cBounds.height < 0) cBounds.height = oBounds.height
          shell.setBounds(cBounds)
        }
      }
    }
    /** Creates and returns this window's contents. */
    override protected def createContents(parent: Composite): Control = {
      val (container, filler, content) = Creator(this, parent)
      this.filler = Option(filler)
      this.content = Option(content)
      container
    }
    /** Add close listener. */
    override def handleShellCloseEvent() {
      updateConfiguration()
      for (configuration <- configuration if saveOnClose)
        WindowSupervisor ! WindowSupervisor.Message.Set(id, configuration)
      super.handleShellCloseEvent
      App.publish(App.Message.Destroyed(this, ref))
    }
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Window actor reference configuration object. */
    lazy val props = injectOptional[Props]("Window") getOrElse Props[Window]
  }
}
