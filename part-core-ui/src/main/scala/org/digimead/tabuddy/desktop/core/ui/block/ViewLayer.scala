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
import java.util.concurrent.atomic.AtomicReference
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.Core
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.Timeout
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.core.ui.block.builder.ViewContentBuilder
import org.digimead.tabuddy.desktop.core.ui.definition.widget.{ SCompositeTab, VComposite }
import org.eclipse.core.databinding.observable.Diffs
import org.eclipse.core.databinding.observable.value.AbstractObservableValue
import org.eclipse.jface.databinding.swt.SWTObservables
import org.eclipse.swt.custom.ScrolledComposite
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.widgets.Widget
import scala.collection.mutable
import scala.concurrent.Await

/** View actor binded to SComposite that contains an actual view from a view factory. */
class ViewLayer(viewId: UUID, viewContext: Context.Rich) extends Actor with Loggable {
  /** View JFace instance. */
  var view: Option[VComposite] = None
  log.debug("Start actor " + self.path)

  def receive = {
    case message @ App.Message.Create(Left(ViewLayer.<>(viewConfiguration, parentWidget)), None) ⇒ App.traceMessage(message) {
      create(viewConfiguration, parentWidget, sender) match {
        case Some(viewWidget) ⇒
          App.publish(App.Message.Create(Right(viewWidget), self))
          App.Message.Create(Right(viewWidget))
        case None ⇒
          App.Message.Error(s"Unable to create ${viewConfiguration}.")
      }
    } foreach { sender ! _ }

    case message @ App.Message.Destroy ⇒ App.traceMessage(message) {
      destroy(sender) match {
        case Some(viewWidget) ⇒
          App.publish(App.Message.Destroy(Right(viewWidget), self))
          App.Message.Destroy(Right(viewWidget))
        case None ⇒
          App.Message.Error(s"Unable to destroy ${view}.")
      }
    } foreach { sender ! _ }

    case message @ App.Message.Start(Left(widget: Widget), None) ⇒ App.traceMessage(message) {
      onStart(widget)
      App.Message.Start(Right(widget))
    } foreach { sender ! _ }

    case message @ App.Message.Stop(Left(widget: Widget), None) ⇒ App.traceMessage(message) {
      onStop(widget)
      App.Message.Stop(Right(widget))
    } foreach { sender ! _ }
  }

  /** Create view. */
  protected def create(viewConfiguration: Configuration.View, parentWidget: ScrolledComposite, supervisor: ActorRef): Option[VComposite] = {
    if (view.nonEmpty)
      throw new IllegalStateException("Unable to create view. It is already created.")
    App.assertEventThread(false)
    ViewContentBuilder(viewConfiguration, self, viewContext, parentWidget) match {
      case Some(viewWidgetWithContent) ⇒
        this.view = Some(viewWidgetWithContent)
        // Update parent tab title if any.
        App.execAsync {
          parentWidget.getParent() match {
            case tab: SCompositeTab ⇒
              tab.getItems().find { item ⇒ item.getData(UI.swtId) == viewConfiguration.id } match {
                case Some(tabItem) ⇒
                  App.bindingContext.bindValue(SWTObservables.observeText(tabItem), viewConfiguration.factory().title(viewWidgetWithContent.contentRef))
                  tabItem.setText(viewConfiguration.factory().title(viewWidgetWithContent.contentRef).getValue().asInstanceOf[String])
                case None ⇒
                  log.fatal(s"TabItem for ${viewConfiguration} in ${tab} not found.")
              }
            case other ⇒
          }
        }
        this.view
      case None ⇒
        log.fatal(s"Unable to build view ${viewConfiguration}.")
        None
    }
  }
  /** Destroy view. */
  protected def destroy(initiator: ActorRef): Option[VComposite] = view.flatMap { view ⇒
    // Ask widget.contentRef to destroy it
    Await.result(ask(view.contentRef, App.Message.Destroy(Left(view)))(Timeout.short), Timeout.short) match {
      case App.Message.Destroy(Right(viewDestroyed: VComposite), None) ⇒
        if (viewDestroyed != view)
          throw new IllegalArgumentException(s"Expected ${view}, but received ${viewDestroyed}")
        log.debug(s"View layer ${view} content is destroyed.")
        App.execNGet { view.dispose() }
        Some(view)
      case App.Message.Error(error, None) ⇒
        log.fatal(s"Unable to destroy content for view layer ${view}: ${error}.")
        None
      case _ ⇒
        log.fatal(s"Unable to destroy content for view layer ${view}.")
        None
    }
  }
  /** User start interaction with window/stack supervisor/view. Focus is gained. */
  protected def onStart(widget: Widget) = view match {
    case Some(view) ⇒
      log.debug("View layer started by focus event on " + widget)
      Core.context.set(UI.viewContextKey, view)
      viewContext.activateBranch()
      Await.ready(ask(view.contentRef, App.Message.Start(Left(widget)))(Timeout.short), Timeout.short)
    case None ⇒
      log.fatal("Unable to start unexists view.")
  }
  /** Focus is lost. */
  protected def onStop(widget: Widget) = view match {
    case Some(view) ⇒
      Await.ready(ask(view.contentRef, App.Message.Stop(Left(widget)))(Timeout.short), Timeout.short)
    case None ⇒
      log.fatal("Unable to stop unexists view.")
  }
}

object ViewLayer {
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)

  /** ViewLayer actor reference configuration object. */
  def props = DI.props

  /** Wrapper for App.Message,Create argument. */
  case class <>(val viewConfiguration: Configuration.View, val parentWidget: ScrolledComposite)
  /**
   * User implemented factory, that returns ActorRef, responsible for view creation/destroying.
   */
  trait Factory {
    /** View name. */
    val name: Symbol
    /** Short view description (one line). */
    val shortDescription: String
    /** Long view description. */
    val longDescription: String
    /** View image. */
    val image: Option[Image]
    /** View title with regards of number of views. */
    protected val titlePerActor = new mutable.WeakHashMap[ActorRef, TitleObservableValue]() with mutable.SynchronizedMap[ActorRef, TitleObservableValue]
    /** All currently active actors. */
    protected val activeActorRefs = new AtomicReference[List[ActorRef]](List())
    protected val viewActorLock = new Object

    /** Get active actors list. */
    def actors = activeActorRefs.get
    /** Get factory configuration. */
    def configuration = Configuration.Factory(App.bundle(this.getClass()).getSymbolicName(), this.getClass().getName())
    /** Get title for the actor reference. */
    def title(ref: ActorRef): TitleObservableValue = viewActorLock.synchronized {
      if (!activeActorRefs.get.contains(ref))
        throw new IllegalStateException("Actor reference ${ref} in unknown in factory ${this}.")
      titlePerActor.get(ref) match {
        case Some(observable) ⇒ observable
        case None ⇒
          val observable = new TitleObservableValue(ref)
          titlePerActor(ref) = observable
          observable
      }
    }
    /**
     * Returns the actor reference that could handle Create/Destroy messages.
     * Add reference to activeActors list.
     */
    def viewActor(configuration: Configuration.View): Option[ActorRef]

    override def toString() = s"""View.Factory("${name}", "${shortDescription}")"""

    class TitleObservableValue(ref: ActorRef) extends AbstractObservableValue(App.realm) {
      @volatile protected var value: AnyRef = name
      update()

      /** The value type of this observable value. */
      def getValueType(): AnyRef = classOf[String]

      def update() = viewActorLock.synchronized {
        val n = activeActorRefs.get.indexOf(ref)
        value = s"${name} (${n})"
        fireValueChange(Diffs.createValueDiff(name, this.value))
      }
      /** Get actual value. */
      protected def doGetValue(): AnyRef = value
    }
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** ViewLayer actor reference configuration object. */
    lazy val props = injectOptional[Props]("Core.UI.ViewLayer") getOrElse Props(classOf[ViewLayer],
      // view layer id
      UUID.fromString("00000000-0000-0000-0000-000000000000"),
      // parent context
      Core.context)
  }
}
