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
import scala.concurrent.Future

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.gui.stack.SComposite
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.eclipse.swt.custom.ScrolledComposite

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala

/**
 * Stack supervisor responsible for:
 * - restore all view
 * - track views
 * - provide view configuration
 * - save all views configuration
 */
class StackSupervisor extends Actor with Loggable {
  /** Window/StackSupervisor UUID. */
  protected val supervisorId = UUID.fromString(self.path.parent.name.split("@").last)
  /** Stack configuration. */
  protected var configuration = StackConfiguration.default
  /** Stack configuration map. */
  protected var configurationMap = toMap(configuration)
  /** Stack container. */
  protected var container: Option[ScrolledComposite] = None
  /** Reference to configurations save process future. */
  val configurationsSave = new AtomicReference[Option[Future[_]]](None)
  /** Flag indicating whether the configurations save process restart is required. */
  val configurationsSaveRestart = new AtomicBoolean()
  /** List of all window stacks. */
  val pointers = new StackSupervisor.PointerMap()
  log.debug("Start actor " + self.path)

  def receive = {
    case message @ App.Message.Attach(props, name) => App.traceMessage(message) {
      sender ! context.actorOf(props, name)
    }
    case message @ App.Message.Created(stack: stack.SComposite, sender) => App.traceMessage(message) {
      onCreated(stack, sender)
    }
    case message @ App.Message.Destroyed(stack: stack.SComposite, sender) => App.traceMessage(message) {
      onDestroyed(stack, sender)
    }
    case message @ App.Message.Restore(content: ScrolledComposite, sender) => App.traceMessage(message) {
      restore(content)
    }
    case message @ App.Message.Save => App.traceMessage(message) {
      save()
    }

    case message @ App.Message.Created(window, sender) =>
    case message @ App.Message.Destroyed(window, sender) =>
    case message @ App.Message.Started(element, sender) =>
    case message @ App.Message.Stopped(element, sender) =>
  }

  /** Create new stack element from configuration */
  protected def create(stackId: UUID, parent: ScrolledComposite) {
    log.debug(s"Create a top level stack element with id ${stackId}.")
    if (pointers.contains(stackId))
      throw new IllegalArgumentException(s"Stack with id ${stackId} is already exists.")
    if (!configurationMap.contains(stackId))
      throw new IllegalArgumentException(s"Stack with id ${stackId} is unknown.")
    configurationMap(stackId) match {
      case stackConfiguration: api.Configuration.Stack =>
        log.debug(s"Attach ${stackConfiguration} as top level element.")
        val stack = context.actorOf(Stack.props, Stack.id + "@" + stackId.toString())
        pointers += stackId -> StackSupervisor.StackPointer(stack)(new WeakReference(null))
        stack ! App.Message.Create(Stack.CreateArgument(stackConfiguration, parent), self)
      case viewConfiguration: api.Configuration.View =>
        // There is only a view that is directly attached to the window.
        log.debug(s"Attach ${viewConfiguration} as top level element.")
        val view = context.actorOf(View.props, View.id + "@" + stackId.toString())
        pointers += stackId -> StackSupervisor.StackPointer(view)(new WeakReference(null))
        view ! App.Message.Create(View.CreateArgument(viewConfiguration, parent), self)
    }
  }
  /** Register created stack element. */
  protected def onCreated(stack: SComposite, sender: ActorRef) {
    pointers += stack.id -> StackSupervisor.StackPointer(sender)(new WeakReference(stack))
  }
  /** Unregister destroyed stack element. */
  protected def onDestroyed(stack: SComposite, sender: ActorRef) {
    pointers -= stack.id
  }
  protected def restore(parent: ScrolledComposite) {
    // TODO destroy all current stacks
    StackConfiguration.load(supervisorId).foreach(configuration = _)
    configurationMap = toMap(configuration)
    container = Some(parent)
    create(configuration.stack.id, parent)
    // TODO activate last
  }
  /** Save windows configuration. */
  protected def save() {
    /*implicit val ec = App.system.dispatcher
    if (!configurationsSave.compareAndSet(None, Some(future {
      WindowConfiguration.save(immutable.HashMap(configurations.toSeq: _*))
      configurationsSave.set(None)
      if (configurationsSaveRestart.compareAndSet(true, false))
        save()
    }))) configurationsSaveRestart.set(true)*/
  }
  protected def toMap(configuration: api.Configuration): immutable.HashMap[UUID, api.Configuration.PlaceHolder] = {
    var entry = Seq[(UUID, api.Configuration.PlaceHolder)]()
    def visit(stack: api.Configuration.PlaceHolder) {
      entry = entry :+ stack.id -> stack
      stack match {
        case tab: api.Configuration.Stack.Tab =>
          tab.children.foreach(visit)
        case hsash: api.Configuration.Stack.HSash =>
          visit(hsash.left)
          visit(hsash.right)
        case vsash: api.Configuration.Stack.VSash =>
          visit(vsash.top)
          visit(vsash.bottom)
        case view: api.Configuration.View =>
      }
    }
    visit(configuration.stack)
    immutable.HashMap[UUID, api.Configuration.PlaceHolder](entry: _*)
  }
}

object StackSupervisor {
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  /** StackSupervisor actor reference configuration object. */
  def props = DI.props

  /**
   * Stack pointers map
   * Shut down application on empty.
   */
  class PointerMap extends mutable.HashMap[UUID, StackPointer]
  /** Wrapper that contains stack and ActorRef. */
  case class StackPointer(val actor: ActorRef)(val stack: WeakReference[SComposite])
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** WindowSupervisor actor reference configuration object. */
    lazy val props = injectOptional[Props]("StackSupervisor") getOrElse Props[StackSupervisor]
  }
}
