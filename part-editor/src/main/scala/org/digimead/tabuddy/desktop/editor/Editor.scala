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

package org.digimead.tabuddy.desktop.editor

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.duration.MILLISECONDS
import scala.concurrent.future

import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Core
import org.digimead.tabuddy.desktop.Core.core2actorRef
import org.digimead.tabuddy.desktop.b4e.WorkbenchAdvisor
import org.digimead.tabuddy.desktop.editor.toolbar.EditorToolBar
import org.digimead.tabuddy.desktop.editor.toolbar.ElementToolBar
import org.digimead.tabuddy.desktop.logic.Logic
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.Handler
import org.digimead.tabuddy.desktop.support.Timeout
import org.digimead.tabuddy.model.element.Element

import akka.actor.ActorRef
import akka.actor.ActorSelection.toScala
import akka.actor.Inbox
import akka.actor.Props
import akka.actor.ScalaActorRef
import akka.actor.actorRef2Scala

import language.implicitConversions

/**
 * Root actor of the Editor component.
 */
class Editor extends akka.actor.Actor with Loggable {
  /** Inconsistent elements. */
  @volatile protected var inconsistentSet = Set[AnyRef]()
  /** Current bundle */
  protected lazy val thisBundle = App.bundle(getClass())
  log.debug("Start actor " + self.path)

  // ACTORS
  /** EditorToolBar actor. */
  val editorToolBarActor = context.actorOf(EditorToolBar.props, EditorToolBar.id)
  /** ElementToolBar actor. */
  val elementToolBarActor = context.actorOf(ElementToolBar.props, ElementToolBar.id)

  /** Get subscribers list. */
  def receive = {
    case message @ App.Message.Attach(props, name) =>
      log.debug(s"Process '${message}'.")
      sender ! context.actorOf(props, name)

    case message @ App.Message.Inconsistent(element, _) if element != Editor && App.bundle(element.getClass()) == thisBundle =>
      log.debug(s"Process '${message}'.")
      if (inconsistentSet.isEmpty) {
        log.debug("Lost consistency.")
        context.actorSelection(self.path / "*") ! Handler.Message.Disable
        context.system.eventStream.publish(App.Message.Inconsistent(Editor, self))
      }
      inconsistentSet = inconsistentSet + element

    case message @ App.Message.Consistent(element, _) if element != Editor && App.bundle(element.getClass()) == thisBundle =>
      log.debug(s"Process '${message}'.")
      inconsistentSet = inconsistentSet - element
      if (inconsistentSet.isEmpty) {
        log.debug("Return integrity.")
        context.actorSelection(self.path / "*") ! Handler.Message.Enable
        context.system.eventStream.publish(App.Message.Consistent(Editor, self))
      }

    case message @ Element.Event.ModelReplace(oldModel, newModel, modified) =>
      log.debug(s"Process '${message}'.")
    //onModelInitialization(oldModel, newModel, modified)

    case message: Handler.Message =>
      log.trace(s"Container actor '${self.path.name}' received message '${message}' from actor ${sender.path}. Propagate.")
      context.children.foreach(_.forward(message))

    case message @ WorkbenchAdvisor.Message.PostStartup(configurer) =>
      log.debug(s"Process '${message}'.")
      future {
        App.afterStart("Desktop Editor", Timeout.normal.toMillis, Logic.getClass()) {
          //Approver.start()
          App.markAsStarted(Editor.getClass)
        }
      } onFailure {
        case e: Exception => log.error(e.getMessage(), e)
        case e => log.error(e.toString())
      }

    case message @ WorkbenchAdvisor.Message.PreShutdown(configurer) =>
      log.debug(s"Process '${message}'.")
      future {
        App.markAsStopped(Editor.getClass())
        if (inconsistentSet.nonEmpty)
          log.fatal("Inconsistent elements detected: " + inconsistentSet)
      } onFailure {
        case e: Exception => log.error(e.getMessage(), e)
        case e => log.error(e.toString())
      }

    case message @ App.Message.Inconsistent(element, _) => // skip
    case message @ App.Message.Consistent(element, _) => // skip
  }
  override def postStop() = log.debug("Editor actor is stopped.")
}

object Editor {
  implicit def editor2actorRef(c: Editor.type): ActorRef = c.actor
  implicit def editor2actorSRef(c: Editor.type): ScalaActorRef = c.actor
  /** Editor actor reference. */
  lazy val actor = {
    val inbox = Inbox.create(App.system)
    inbox.send(Core, App.Message.Attach(props, id))
    inbox.receive(Timeout.long) match {
      case actorRef: ActorRef =>
        actorRef
      case other =>
        throw new IllegalStateException(s"Unable to attach actor ${id} to ${Core.path}.")
    }
  }
  /** Editor actor path. */
  lazy val actorPath = Core.path / id
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  /** Editor actor reference configuration object. */
  lazy val props = DI.props
  // Initialize descendant actor singletons
  toolbar.EditorToolBar
  toolbar.ElementToolBar

  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Editor actor reference configuration object. */
    lazy val props = injectOptional[Props]("Editor") getOrElse Props[Editor]
  }
}
