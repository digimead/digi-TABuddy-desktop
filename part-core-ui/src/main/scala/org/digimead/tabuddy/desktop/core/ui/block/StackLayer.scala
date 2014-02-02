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

import akka.actor.{ Actor, ActorRef, Props, actorRef2Scala }
import akka.pattern.ask
import java.util.UUID
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.Core
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.core.ui.block.builder.StackBuilder
import org.digimead.tabuddy.desktop.core.ui.block.transform.TransformAttachView
import org.digimead.tabuddy.desktop.core.ui.definition.widget.{ SComposite, SCompositeTab, VComposite }
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.ScrolledComposite
import org.eclipse.swt.widgets.{ Event, Widget }
import scala.collection.mutable
import scala.concurrent.Await

/**
 * Stack layer implementation that contains lay between window and view.
 */
class StackLayer(val stackId: UUID, val parentContext: Context.Rich) extends Actor with Loggable {
  /** Akka communication timeout. */
  implicit val timeout = akka.util.Timeout(UI.communicationTimeout)
  /** Parent stack actor. */
  lazy val container = context.parent
  /** List of all child layers. */
  val children = mutable.Set[UUID]()
  /** Stack layer JFace instance. */
  @volatile var stack: Option[SComposite] = None
  /** Flag indicating whether the StackLayer is alive. */
  var terminated = false
  log.debug(s"Start actor ${self.path} ${stackId}")

  /** Is called asynchronously after 'actor.stop()' is invoked. */
  override def postStop() = log.debug(this + " is stopped.")
  /** Is called when an Actor is started. */
  override def preStart() = log.debug(this + " is started.")
  def receive = {
    // Create this stack layer JFace implementation.
    case message @ App.Message.Create(StackLayer.<>(stackConfiguration, parentWidget), Some(this.container), _) ⇒ App.traceMessage(message) {
      if (terminated) {
        App.Message.Error(s"${this} is terminated.", self)
      } else {
        create(stackConfiguration, parentWidget) match {
          case Some(stack) ⇒
            container ! App.Message.Create(stack, self)
            App.Message.Create(stack, self)
          case None ⇒
            App.Message.Error(s"Unable to create ${stackConfiguration}.", self)
        }
      }
    } foreach { sender ! _ }

    // Create a view with a new or exists widget.
    case message @ App.Message.Create(StackLayer.<+>(viewConfiguration, content), anySender, _) ⇒ App.traceMessage(message) {
      if (terminated) {
        App.Message.Error(s"${this} is terminated.", self)
      } else {
        create(viewConfiguration, content) match {
          case Some(view) ⇒
            App.Message.Create(view, self)
          case None ⇒
            App.Message.Error(s"Unable to create ${viewConfiguration}.", self)
        }
      }
    } foreach { sender ! _ }

    // Notification.
    case message @ App.Message.Create(stackLayer: SComposite, Some(origin), _) ⇒ App.traceMessage(message) {
      if (terminated) {
        App.Message.Error(s"${this} is terminated.", self)
      } else {
        if (App.execNGet { stack.map(UI.widgetHierarchy(stackLayer).contains).getOrElse(false) })
          onCreated(stackLayer, origin)
      }
      container ! message
    }

    // Destroy this layer.
    case message @ App.Message.Destroy(None, _, _) ⇒ App.traceMessage(message) {
      if (terminated) {
        App.Message.Error(s"${this} is terminated.", self)
      } else {
        destroy() match {
          case Some(stackWidget) ⇒
            if (children.isEmpty)
              container ! App.Message.Destroy(stackWidget, self) // send to container
            App.Message.Destroy(stackWidget, self) // send to sender that may be container too
          case None ⇒
            App.Message.Error(s"Unable to destroy ${stack}.", self)
        }
      }
    } foreach { sender ! _ }

    // Notification.
    case message @ App.Message.Destroy(stackLayer: SComposite, Some(origin), _) ⇒ App.traceMessage(message) {
      container ! message
      // ^ order is important v
      if (children(stackLayer.id))
        onDestroyed(stackLayer, origin)
    }

    case message @ App.Message.Get(Actor) ⇒ App.traceMessage(message) {
      val tree = context.children.map(child ⇒ child -> child ? App.Message.Get(Actor))
      Map(tree.map { case (child, map) ⇒ child -> Await.result(map, timeout.duration) }.toSeq: _*)
    } foreach { sender ! _ }

    case message @ App.Message.Start((widget: Widget, hierarchy: Seq[_]), _, _) if hierarchy.lastOption == stack ⇒ Option {
      if (terminated) {
        App.Message.Error(s"${this} is terminated.", self)
      } else {
        onStart(widget, hierarchy.asInstanceOf[Seq[SComposite]])
        App.Message.Start(widget, self)
      }
    } foreach { sender ! _ }

    case message @ App.Message.Stop(widget: Widget, _, _) ⇒ Option {
      if (terminated) {
        App.Message.Error(s"${this} is terminated.", self)
      } else {
        onStop(widget)
        App.Message.Stop(widget, self)
      }
    } foreach { sender ! _ }

    case App.Message.Error(Some(message), _) if message.endsWith("is terminated.") ⇒
      log.debug(message)
  }

  /** Create stack. */
  protected def create(stackConfiguration: Configuration.CPlaceHolder, parentWidget: ScrolledComposite): Option[SComposite] = {
    if (stack.nonEmpty)
      throw new IllegalStateException("Unable to create stack. It is already created.")
    this.stack = StackBuilder(stackConfiguration, parentWidget, parentContext, context)
    this.stack
  }
  /** Create view. */
  protected def create(viewConfiguration: Configuration.CView, content: Option[VComposite]): Option[VComposite] = this.stack flatMap {
    case stackTab: SCompositeTab ⇒
      log.debug(s"Add ${viewConfiguration} to ${this}")
      TransformAttachView(this, stackTab, viewConfiguration, content)
  }
  /** Destroy view. */
  protected def destroy(): Option[SComposite] = stack.map { stack ⇒
    App.execNGet { stack.dispose() }
    context.children.foreach(_ ! App.Message.Destroy())
    terminated = true
    stack
  }
  /** Register created stack element. */
  @log
  protected def onCreated(child: SComposite, origin: ActorRef) {
    log.debug(s"Add created ${stack} to ${this}.")
    children += child.id
    stack match {
      case Some(tab: SCompositeTab) ⇒
        child match {
          case vComposite: VComposite ⇒
            App.execNGet {
              tab.getItems().find(item ⇒ item.getData(UI.swtId) == vComposite.id).foreach { tabItem ⇒
                log.debug(s"Select tab with ${vComposite}.")
                tab.setSelection(tabItem)
                val event = new Event()
                event.item = tabItem
                tab.notifyListeners(SWT.Selection, event)
              }
            }
          case _ ⇒
        }
      case _ ⇒
        throw new UnsupportedOperationException()
    }
  }
  /** Unregister destroyed stack element. */
  @log
  protected def onDestroyed(child: SComposite, origin: ActorRef) {
    if (children.nonEmpty) {
      log.debug(s"Remove destroyed ${child} from ${this} and keep ${children.size} element(s).")
      children -= child.id
      stack.foreach { stack ⇒
        if (children.isEmpty) {
          log.debug(s"There are no children. Destroy ${this}.")
          container ! App.Message.Destroy(stack, self)
        }
      }
    }
  }
  /** User start interaction with stack layer. Focus is gained. */
  //@log
  protected def onStart(widget: Widget, hierarchy: Seq[SComposite]): Unit = this.stack match {
    case Some(tab: SCompositeTab) ⇒
      val viewName = hierarchy.headOption.map(view ⇒ View.name(view.id)) getOrElse {
        throw new IllegalStateException("Unable to find view name for hierarchy " + hierarchy)
      }
      context.children.find(_.path.name == viewName).foreach { viewActor ⇒
        Await.ready(viewActor ? App.Message.Start((widget, hierarchy.dropRight(1)), self), timeout.duration)
      }
    case _ ⇒
      throw new UnsupportedOperationException()
  }
  /** Focus is lost. */
  //@log
  protected def onStop(widget: Widget) {
  }

  override lazy val toString = "StackLayer[actor/%08X]".format(stackId.hashCode())
}

object StackLayer extends Loggable {
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  // Initialize descendant actor singletons
  View

  /** Get stack layer name. */
  def name(id: UUID) = StackLayer.id + "_%08X".format(id.hashCode())
  /** StackLayer actor reference configuration object. */
  def props = DI.props

  override def toString = "StackLayer[Singleton]"

  /** Wrapper for App.Message.Create argument. */
  case class <>(val stackConfiguration: Configuration.Stack, val parentWidget: ScrolledComposite)
  /** Wrapper for App.Message.Create argument. */
  case class <+>(val viewConfiguration: Configuration.CView, content: Option[VComposite] = None)

  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** StackLayer actor reference configuration object. */
    lazy val props = injectOptional[Props]("Core.UI.StackLayer") getOrElse Props(classOf[StackLayer],
      // stack layer id
      UUID.fromString("00000000-0000-0000-0000-000000000000"),
      // parent context
      Core.context)
  }
}
