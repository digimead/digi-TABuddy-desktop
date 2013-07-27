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

import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

import scala.collection.immutable
import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.future

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Core
import org.digimead.tabuddy.desktop.Core.core2actorRef
import org.digimead.tabuddy.desktop.gui.GUI.gui2implementation
import org.digimead.tabuddy.desktop.gui.WindowConfiguration.windowConfiguration2implementation
import org.digimead.tabuddy.desktop.gui.window.WComposite
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.Timeout
import org.eclipse.jface.window.{ Window => JWindow }
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Event
import org.eclipse.swt.widgets.Listener

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.ScalaActorRef
import akka.actor.actorRef2Scala
import akka.pattern.ask

import language.implicitConversions

/**
 * Window supervisor responsible for:
 * - start application
 * - restore all windows
 * - track windows
 * - provide window configuration
 * - save all windows configuration
 * - shut down application
 */
class WindowSupervisor extends Actor with Loggable {
  /** All known window configurations. */
  val configurations = new mutable.HashMap[UUID, WindowConfiguration]() with mutable.SynchronizedMap[UUID, WindowConfiguration]
  /** Reference to configurations save process future. */
  val configurationsSave = new AtomicReference[Option[Future[_]]](None)
  /** Flag indicating whether the configurations save process restart is required. */
  val configurationsSaveRestart = new AtomicBoolean()
  /** List of all application windows. */
  val pointers = new WindowSupervisor.PointerMap()
  log.debug("Start actor " + self.path)

  def receive = {
    case message @ App.Message.Attach(props, name) => App.traceMessage(message) {
      sender ! context.actorOf(props, name)
    }
    case message @ App.Message.Created(window: window.WComposite, sender) => App.traceMessage(message) {
      onCreated(window, sender)
    }
    case message @ App.Message.Destroyed(window: window.WComposite, sender) => App.traceMessage(message) {
      onDestroyed(window, sender)
    }
    case message @ App.Message.Restore => App.traceMessage(message) {
      restore()
    }
    case message @ App.Message.Save => App.traceMessage(message) {
      save()
    }
    case message @ App.Message.Started(element: GUI.type, sender) => App.traceMessage(message) {
      onGUIStarted()
    }
    case message @ App.Message.Stopped(element: GUI.type, sender) => App.traceMessage(message) {
      onGUIStopped()
    }
    case message @ WindowSupervisor.Message.Get(windowId) => App.traceMessage(message) {
      get(sender, windowId)
    }
    case message @ WindowSupervisor.Message.Peek =>
      sender ! pointers.toSeq
    case message @ WindowSupervisor.Message.Set(windowId, configuration) => App.traceMessage(message) {
      set(sender, windowId, configuration)
    }

    case message @ App.Message.Created(window, sender) =>
    case message @ App.Message.Destroyed(window, sender) =>
    case message @ App.Message.Started(element, sender) =>
    case message @ App.Message.Stopped(element, sender) =>
  }
  override def postStop() {
    val saveFuture = configurationsSave.get
    saveFuture.map(future => Await.result(future, Timeout.short))
    if (configurationsSaveRestart.get) {
      for (i <- 0 to 10 if saveFuture == configurationsSave.get)
        Thread.sleep(100) // User have limited patience - 1 second is enough
      if (saveFuture != configurationsSave.get)
        configurationsSave.get.map(future => Await.result(future, Timeout.short))
    }
    log.debug(self.path.name + " actor is stopped.")
  }

  protected def create(windowId: UUID) {
    if (pointers.contains(windowId))
      throw new IllegalArgumentException(s"Window with id ${windowId} is already exists.")
    val window = context.actorOf(Window.props, Window.id + "@" + windowId.toString())
    pointers += windowId -> WindowSupervisor.WindowPointer(window)(new WeakReference(null))
    window ! App.Message.Create(windowId, self)
  }
  protected def get(sender: ActorRef, windowId: Option[UUID]) = windowId match {
    case Some(id: UUID) =>
      sender ! (configurations.get(id) getOrElse WindowConfiguration.default)
    case None =>
      sender ! WindowConfiguration.default
  }
  protected def onCreated(window: WComposite, sender: ActorRef) {
    pointers += window.id -> WindowSupervisor.WindowPointer(sender)(new WeakReference(window))
    implicit val timeout = akka.util.Timeout(Timeout.short)
    sender ? App.Message.Open
  }
  protected def onDestroyed(window: WComposite, sender: ActorRef) {
    pointers -= window.id
  }
  protected def onGUIStarted() = App.exec {
    App.display.addFilter(SWT.FocusIn, FocusListener)
    App.display.addFilter(SWT.FocusOut, FocusListener)
  }
  protected def onGUIStopped() = App.exec {
    Option(App.display).foreach { display =>
      display.removeFilter(SWT.FocusIn, FocusListener)
      display.removeFilter(SWT.FocusOut, FocusListener)
    }
  }
  protected def restore() {
    // destroy all current windows
    configurations.clear
    WindowConfiguration.load.foreach(kv => configurations += kv)
    val configurationsSeq = configurations.toSeq
    configurationsSeq.filter(_._2.active) match {
      case Nil =>
        // there are no visible windows
        if (configurations.isEmpty) {
          // there are no windows
          val id = UUID.randomUUID()
          log.debug("Create new window " + id)
          create(id)
        } else {
          // there was last visible window
          val lastVisibleId = configurationsSeq.sortBy(-_._2.timestamp).head._1
          log.debug("Restore last active window " + lastVisibleId)
          create(lastVisibleId)
        }
      case visibleWindows =>
        // there were visible windows
        visibleWindows.foreach {
          case (id, value) =>
            log.debug("Restore active window " + id)
            create(id)
        }
    }
  }
  /** Save windows configuration. */
  protected def save() {
    implicit val ec = App.system.dispatcher
    if (!configurationsSave.compareAndSet(None, Some(future {
      WindowConfiguration.save(immutable.HashMap(configurations.toSeq: _*))
      configurationsSave.set(None)
      if (configurationsSaveRestart.compareAndSet(true, false))
        save()
    }))) configurationsSaveRestart.set(true)
  }
  protected def set(sender: ActorRef, windowId: UUID, configuration: WindowConfiguration) = {
    configurations(windowId) = configuration
  }

  /** Start window. */
  object FocusListener extends Listener() {
    def handleEvent(event: Event) {
      App.findShell(event.widget).foreach { shell =>
        Option(shell.getData(GUI.swtId).asInstanceOf[UUID]).foreach(id =>
          pointers.get(id).foreach(pointer => pointer.actor ! App.Message.Start))
      }
    }
  }
}

object WindowSupervisor extends Loggable {
  implicit def windowGroup2actorRef(w: WindowSupervisor.type): ActorRef = w.actor
  implicit def windowGroup2actorSRef(w: WindowSupervisor.type): ScalaActorRef = w.actor
  /** WindowSupervisor actor reference. */
  lazy val actor = App.getActorRef(App.system.actorSelection(actorPath)) getOrElse {
    throw new IllegalStateException("Unable to locate actor with path " + actorPath)
  }
  /** WindowSupervisor actor path. */
  lazy val actorPath = Core.path / id
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  /** WindowSupervisor actor reference configuration object. */
  def props = DI.props

  trait Message extends App.Message
  object Message {
    /** Get window configuration. */
    case class Get(windowId: Option[UUID])
    object Get {
      def apply(window: WComposite): Get = apply(Option(window.id))
      def apply(window: JWindow): Get = {
        val id = Option(window.getShell().getData(GUI.swtId).asInstanceOf[UUID])
        if (id.isEmpty) log.fatal(s"${window} window ID not found.")
        apply(id)
      }
    }
    /** Get all known windows. */
    case object Peek extends Message
    /** Save window configuration. */
    case class Set(windowId: UUID, configuration: WindowConfiguration)
    object Set {
      def apply(window: WComposite, configuration: WindowConfiguration): Set = apply(window.id, configuration)
      def apply(window: JWindow, configuration: WindowConfiguration): Set = {
        val id = Option(window.getShell().getData(GUI.swtId).asInstanceOf[UUID])
        id match {
          case Some(id) =>
            apply(id, configuration)
          case None =>
            throw new IllegalArgumentException(s"${window} window ID not found.")
        }
      }
    }
  }
  /**
   * Window pointers map.
   * Shut down application on empty.
   */
  class PointerMap extends mutable.HashMap[UUID, WindowPointer] {
    override def -=(key: UUID): this.type = {
      get(key) match {
        case None =>
        case Some(old) =>
          super.-=(key)
          if (isEmpty) {
            log.debug("There are no active windows. Shut down main loop.")
            WindowSupervisor ! App.Message.Save
            GUI.stop(GUI.Exit.Ok)
          }
      }
      this
    }
    override def clear(): Unit = {
      super.clear()
      log.debug("There are no active windows. Shut down main loop.")
      WindowSupervisor ! App.Message.Save
      GUI.stop(GUI.Exit.Ok)
    }
  }
  /** Wrapper that contains window and ActorRef. */
  case class WindowPointer(val actor: ActorRef)(val window: WeakReference[WComposite])
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** WindowSupervisor actor reference configuration object. */
    lazy val props = injectOptional[Props]("WindowSupervisor") getOrElse Props[WindowSupervisor]
  }
}
