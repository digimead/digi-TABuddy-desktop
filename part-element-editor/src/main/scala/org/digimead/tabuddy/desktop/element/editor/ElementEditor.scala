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

package org.digimead.tabuddy.desktop.element.editor

import akka.actor.{ ActorRef, Inbox, Props, ScalaActorRef, actorRef2Scala }
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.console.Console
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.Timeout
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.logic.Logic
import scala.language.implicitConversions

/**
 * Root actor of the Element Editor component.
 */
class ElementEditor extends akka.actor.Actor with XLoggable {
  /** Inconsistent elements. */
  @volatile protected var inconsistentSet = Set[AnyRef]()
  /** Flag indicating whether GUI is valid. */
  @volatile protected var fGUIStarted = false
  /** Current bundle */
  protected lazy val thisBundle = App.bundle(getClass())
  private val initializationLock = new Object
  log.debug("Start actor " + self.path)

  /*
   * ElementEditor component actors.
   */
  lazy val windowWatcherRef = context.actorOf(ui.WindowWatcher.props, ui.WindowWatcher.id)

  if (App.watch(Activator, Logic, UI, this).hooks.isEmpty)
    App.watch(Activator, Logic, UI, this).always().
      makeAfterStart { onGUIStarted() }.
      makeBeforeStop { onGUIStopped() }.sync()

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
    case message @ App.Message.Attach(props, name, _) ⇒ App.traceMessage(message) {
      sender ! context.actorOf(props, name)
    }

    case message @ App.Message.Consistent(element, from, _) if from != Some(self) &&
      App.bundle(element.getClass()) == thisBundle ⇒ App.traceMessage(message) {
      if (inconsistentSet.nonEmpty) {
        inconsistentSet = inconsistentSet - element
        if (inconsistentSet.isEmpty) {
          log.debug("Return integrity.")
          context.system.eventStream.publish(App.Message.Consistent(ElementEditor, self))
        }
      } else
        log.debug(s"Skip message ${message}. ModelDefinition is already consistent.")
    }

    case message @ App.Message.Inconsistent(element, from, _) if from != Some(self) &&
      App.bundle(element.getClass()) == thisBundle ⇒ App.traceMessage(message) {
      if (inconsistentSet.isEmpty) {
        log.debug("Lost consistency.")
        context.system.eventStream.publish(App.Message.Inconsistent(ElementEditor, self))
      }
      inconsistentSet = inconsistentSet + element
    }

    case message @ App.Message.Inconsistent(element, _, _) ⇒ // skip
    case message @ App.Message.Consistent(element, _, _) ⇒ // skip
  }

  /** This callback is invoked when GUI is valid. */
  @log
  protected def onGUIStarted() = initializationLock.synchronized {
    App.watch(ElementEditor) on {
      self ! App.Message.Inconsistent(ElementEditor, None)
      // Initialize lazy actors
      ElementEditor.actor
      if (App.isUIAvailable)
        windowWatcherRef
      Console ! Console.Message.Notice("ElementEditor component is started.")
      self ! App.Message.Consistent(ElementEditor, None)
    }
  }
  /** This callback is invoked when GUI is invalid. */
  @log
  protected def onGUIStopped() = initializationLock.synchronized {
    App.watch(ElementEditor) off {
      self ! App.Message.Inconsistent(ElementEditor, None)
      val lost = inconsistentSet - ElementEditor
      if (lost.nonEmpty)
        log.fatal("Inconsistent elements detected: " + lost)
      Console ! Console.Message.Notice("ModelDefinition component is stopped.")
    }
  }

  override def toString = "element.editor.ElementEditor"
}

object ElementEditor {
  implicit def elementEditor2actorRef(m: ElementEditor.type): ActorRef = m.actor
  implicit def elementEditor2actorSRef(m: ElementEditor.type): ScalaActorRef = m.actor
  /** ElementEditor actor reference. */
  lazy val actor = {
    val inbox = Inbox.create(App.system)
    inbox.send(Logic, App.Message.Attach(props, id))
    inbox.receive(Timeout.long) match {
      case actorRef: ActorRef ⇒
        actorRef
      case other ⇒
        throw new IllegalStateException(s"Unable to attach actor ${id} to ${Logic.path}.")
    }
  }
  /** ElementEditor actor path. */
  lazy val actorPath = Logic.path / id
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  /** ElementEditor actor reference configuration object. */
  lazy val props = DI.props

  // Initialize descendant actor singletons
  if (App.isUIAvailable)
    ui.WindowWatcher

  override def toString = "element.editor.ElementEditor[Singleton]"

  /**
   * Dependency injection routines
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** ElementEditor actor reference configuration object. */
    lazy val props = injectOptional[Props]("ElementEditor") getOrElse Props[ElementEditor]
  }
}
