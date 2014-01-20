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

package org.digimead.tabuddy.desktop.core.ui

import akka.actor.{ ActorRef, Inbox, Props, ScalaActorRef, actorRef2Scala }
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.Core
import org.digimead.tabuddy.desktop.core.console.Console
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.Timeout
import org.digimead.tabuddy.desktop.core.ui.block.WindowSupervisor
import org.eclipse.jface.commands.ToggleState
import scala.concurrent.Future
import scala.language.implicitConversions

/**
 * Root actor of the UI component.
 */
class UI extends akka.actor.Actor with Loggable {
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** Inconsistent elements. */
  @volatile protected var inconsistentSet = Set[AnyRef](UI)
  /** Current bundle */
  protected lazy val thisBundle = App.bundle(getClass())
  /** Start/stop initialization lock. */
  private val initializationLock = new Object
  log.debug("Start actor " + self.path)

  /*
   * UI component actors.
   */
  val watcherRef = context.actorOf(WindowWatcher.props, WindowWatcher.id)
  val windowSupervisorRef = context.actorOf(WindowSupervisor.props, WindowSupervisor.id)

  if (App.watch(Activator, Core, this).hooks.isEmpty)
    App.watch(Activator, Core, this).always().
      makeAfterStart { onCoreStarted() }.
      makeBeforeStop { onCoreStopped() }.sync()

  /** Is called asynchronously after 'actor.stop()' is invoked. */
  override def postStop() = {
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Consistent[_]])
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Inconsistent[_]])
    App.watch(this) off ()
    log.debug(self.path.name + " actor is stopped.")
  }
  /** Is called when an Actor is started. */
  override def preStart() {
    App.system.eventStream.subscribe(self, classOf[App.Message.Inconsistent[_]])
    App.system.eventStream.subscribe(self, classOf[App.Message.Consistent[_]])
    App.watch(this) on ()
    log.debug(self.path.name + " actor is started.")
  }
  def receive = {
    case message @ App.Message.Attach(props, name) ⇒ App.traceMessage(message) {
      sender ! context.actorOf(props, name)
    }
    case message @ App.Message.Consistent(element, from) if from != Some(self) &&
      App.bundle(element.getClass()) == thisBundle ⇒ App.traceMessage(message) {
      inconsistentSet = inconsistentSet - element
      if (inconsistentSet.isEmpty) {
        log.debug("Return integrity.")
        context.system.eventStream.publish(App.Message.Consistent(UI, self))
      }
    }
    case message @ App.Message.Inconsistent(element, from) if from != Some(self) &&
      App.bundle(element.getClass()) == thisBundle ⇒ App.traceMessage(message) {
      if (inconsistentSet.isEmpty) {
        log.debug("Lost consistency.")
        context.system.eventStream.publish(App.Message.Inconsistent(UI, self))
      }
      inconsistentSet = inconsistentSet + element
    }

    case message @ App.Message.Consistent(_, _) ⇒ // skip
    case message @ App.Message.Inconsistent(_, _) ⇒ // skip
  }

  /** Invoked on Core started. */
  protected def onCoreStarted() = initializationLock.synchronized {
    App.watch(UI) on {
      App.execNGet { Resources.start(App.bundle(getClass).getBundleContext()) }
      view.Views.configure()
      command.Commands.configure()
      WindowSupervisor ! App.Message.Restore
      Console ! Console.Message.Notice("UI component is started.")
      self ! App.Message.Consistent(UI, None)
    }
  }
  /** Invoked on Core stopped. */
  protected def onCoreStopped() = initializationLock.synchronized {
    App.watch(UI) off {
      command.Commands.unconfigure()
      view.Views.unconfigure()
      Console ! Console.Message.Notice("UI component is stopped.")
    }
    Future {
      val display = App.display
      while (!display.isDisposed())
        Thread.sleep(100)
      Resources.validateOnShutdown()
      Resources.stop(App.bundle(getClass).getBundleContext())
    } onFailure { case e: Throwable ⇒ log.error(e.getMessage(), e) }
  }
}

object UI extends support.Generic with Loggable {
  implicit def ui2actorRef(u: UI.type): ActorRef = u.actor
  implicit def ui2actorSRef(u: UI.type): ScalaActorRef = u.actor
  /** UI actor reference. */
  lazy val actor = {
    val inbox = Inbox.create(App.system)
    inbox.send(Core, App.Message.Attach(props, id))
    inbox.receive(Timeout.long) match {
      case actorRef: ActorRef ⇒
        actorRef
      case other ⇒
        throw new IllegalStateException(s"Unable to attach actor ${id} to ${Core.path}.")
    }
  }
  /** UI actor path. */
  lazy val actorPath = Core.path / id
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  /** UI actor reference configuration object. */
  lazy val props = DI.props
  /** SWT Data ID key */
  val swtId = getClass.getName() + "#ID"
  /** Context key with the current shell. */
  final val shellContextKey = "shell"
  /** Context key with current view. */
  final val viewContextKey = "view"
  /** Context key with current window. */
  final val windowContextKey = "window"
  // Initialize descendant actor singletons
  Core
  WindowSupervisor

  override def toString = "UI[Singleton]"

  /*
   * Explicit import of runtime components.
   * Provides information for bundle manifest generation.
   */
  private def explicitToggleState: ToggleState = ???

  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** UI actor reference configuration object. */
    lazy val props = injectOptional[Props]("Core.UI") getOrElse Props[UI]
  }
}
