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

package org.digimead.tabuddy.desktop.core.ui.block

import akka.actor.{ Actor, ActorContext, ActorRef, Props, actorRef2Scala }
import akka.pattern.ask
import java.util.UUID
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.core.ui.block.builder.StackBuilder
import org.digimead.tabuddy.desktop.core.ui.definition.widget.SComposite
import org.eclipse.swt.custom.ScrolledComposite
import scala.concurrent.Await

/**
 * Stack layer implementation that contains lay between window and view.
 */
class StackLayer(stackId: UUID) extends Actor with Loggable {
  /** Akka communication timeout. */
  implicit val timeout = akka.util.Timeout(UI.communicationTimeout)
  /** Parent stack actor. */
  lazy val container = context.parent
  /** Stack layer JFace instance. */
  @volatile var stack: Option[SComposite] = None
  /** Flag indicating whether the StackLayer is alive. */
  var terminated = false
  log.debug("Start actor " + self.path)

  /** Is called asynchronously after 'actor.stop()' is invoked. */
  override def postStop() = log.debug(this + " is stopped.")
  /** Is called when an Actor is started. */
  override def preStart() = log.debug(this + " is started.")
  def receive = {
    case message @ App.Message.Create(Left(StackLayer.<>(stackConfiguration, parentWidget, parentContext, supervisorContext)), Some(this.container), _) ⇒ App.traceMessage(message) {
      if (terminated) {
        App.Message.Error(s"${this} is terminated.", self)
      } else {
        create(stackConfiguration, parentWidget, parentContext, sender, supervisorContext) match {
          case Some(stack) ⇒
            container ! App.Message.Create(stack, self)
            App.Message.Create(stack, self)
          case None ⇒
            App.Message.Error(s"Unable to create ${stackConfiguration}.", self)
        }
      }
    } foreach { sender ! _ }

    /* case message @ App.Message.Create(stack: SComposite, Some(this.self), _) if (this.stack == None) ⇒ App.traceMessage(message) {
      if (terminated) {
        App.Message.Error(s"${this} is terminated.", self)
      } else {
        log.debug(s"Bind ${stack} to ${this}.")
        this.stack = Option(stack)
      }
    }*/

    case message @ App.Message.Destroy(_, _, _) ⇒ App.traceMessage(message) {
      if (terminated) {
        App.Message.Error(s"${this} is terminated.", self)
      } else {
        destroy() match {
          case Some(stackWidget) ⇒
            container ! App.Message.Destroy(stackWidget, self)
            App.Message.Destroy(stackWidget, self)
          case None ⇒
            App.Message.Error(s"Unable to destroy ${stack}.", self)
        }
      }
    } foreach { sender ! _ }

    case message @ App.Message.Get(Actor) ⇒ App.traceMessage(message) {
      val tree = context.children.map(child ⇒ child -> child ? App.Message.Get(Actor))
      Map(tree.map { case (child, map) ⇒ child -> Await.result(map, timeout.duration) }.toSeq: _*)
    } foreach { sender ! _ }
  }

  /** Create stack. */
  protected def create(stackConfiguration: Configuration.CPlaceHolder, parentWidget: ScrolledComposite,
    parentContext: Context, supervisor: ActorRef, supervisorContext: ActorContext): Option[SComposite] = {
    if (stack.nonEmpty)
      throw new IllegalStateException("Unable to create stack. It is already created.")
    App.assertEventThread(false)
    this.stack = StackBuilder(stackConfiguration, parentWidget, parentContext, supervisor, supervisorContext, self)
    this.stack
  }
  /** Destroy view. */
  protected def destroy(): Option[SComposite] = stack.map { stack ⇒
    App.execNGet { stack.dispose() }
    context.children.foreach(_ ! App.Message.Destroy())
    terminated = true
    stack
  }
  override lazy val toString = "StackLayer[actor/%08X]".format(stackId.hashCode())
}

object StackLayer extends Loggable {
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  // Initialize descendant actor singletons
  View

  /** StackLayer actor reference configuration object. */
  def props = DI.props

  override def toString = "StackLayer[Singleton]"

  /** Wrapper for App.Message,Create argument. */
  case class <>(val stackConfiguration: Configuration.Stack, val parentWidget: ScrolledComposite, val parentContext: Context, supervisorContext: ActorContext)
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** StackLayer actor reference configuration object. */
    lazy val props = injectOptional[Props]("Core.UI.StackLayer") getOrElse Props(classOf[StackLayer],
      // stack layer id
      UUID.fromString("00000000-0000-0000-0000-000000000000"))
  }
}
