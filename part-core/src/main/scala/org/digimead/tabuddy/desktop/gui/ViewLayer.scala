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
import java.util.concurrent.atomic.AtomicReference

import scala.collection.mutable

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Core
import org.digimead.tabuddy.desktop.gui.builder.StackBuilder
import org.digimead.tabuddy.desktop.gui.builder.StackBuilder.builder2implementation
import org.digimead.tabuddy.desktop.gui.widget.SCompositeTab
import org.digimead.tabuddy.desktop.gui.widget.VComposite
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.eclipse.core.databinding.observable.Diffs
import org.eclipse.core.databinding.observable.value.AbstractObservableValue
import org.eclipse.e4.core.internal.contexts.EclipseContext
import org.eclipse.jface.databinding.swt.SWTObservables
import org.eclipse.swt.custom.ScrolledComposite
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.widgets.Widget

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala

/** View actor binded to SComposite that contains an actual view from a view factory. */
class ViewLayer(viewId: UUID, parentContext: EclipseContext) extends Actor with Loggable {
  /** View JFace instance. */
  var view: Option[VComposite] = None
  /** View context. */
  val viewContext = parentContext.createChild("Context_" + self.path.name).asInstanceOf[EclipseContext]
  log.debug("Start actor " + self.path)

  def receive = {
    case message @ App.Message.Create(ViewLayer.<>(viewConfiguration, parentWidget, parentContext), supervisor) => App.traceMessage(message) {
      App.execNGet { create(viewConfiguration, parentWidget, parentContext, supervisor) }
    }
    case message @ App.Message.Start(widget: Widget, supervisor) => App.traceMessage(message) {
      onStart(widget)
    }
  }

  /** Create view. */
  protected def create(viewConfiguration: Configuration.View, parentWidget: ScrolledComposite, parentContext: EclipseContext, supervisor: ActorRef) {
    if (view.nonEmpty)
      throw new IllegalStateException("Unable to create view. It is already created.")
    App.checkThread
    StackBuilder(viewConfiguration, parentWidget, viewContext, supervisor, self) match {
      case Some(viewLayer) =>
        App.publish(App.Message.Created(viewLayer, self))
        this.view = Some(viewLayer)
        // Update parent tab title if any.
        App.execAsync {
          parentWidget.getParent() match {
            case tab: SCompositeTab =>
              tab.getItems().find { item => item.getData(GUI.swtId) == viewConfiguration.id } match {
                case Some(tabItem) =>
                  GUI.factory(viewConfiguration.factorySingletonClassName) match {
                    case Some(factory) =>
                      App.bindingContext.bindValue(SWTObservables.observeText(tabItem), factory.title(viewLayer.contentRef))
                      tabItem.setText(factory.title(viewLayer.contentRef).getValue().asInstanceOf[String])
                    case None =>
                      log.fatal(s"Unable to find view factory for ${viewConfiguration.factorySingletonClassName}.")
                  }
                case None =>
                  log.fatal(s"TabItem for ${viewConfiguration} in ${tab} not found.")
              }
            case other =>
          }
        }
      case None =>
        log.fatal(s"Unable to build view ${viewConfiguration}.")
    }
  }
  /** User start interaction with window/stack supervisor/view. Focus is gained. */
  protected def onStart(widget: Widget) = view match {
    case Some(view) =>
      log.debug("View layer started by focus event on " + widget)
      Core.context.set(GUI.viewContextKey, view)
      viewContext.activateBranch()
      view.contentRef ! App.Message.Start(widget, self)
    case None =>
      log.fatal("Unable to start view without widget.")
  }
}

object ViewLayer {
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)

  /** ViewLayer actor reference configuration object. */
  def props = DI.props

  /** Wrapper for App.Message,Create argument. */
  case class <>(val viewConfiguration: Configuration.View, val parentWidget: ScrolledComposite, val parentContext: EclipseContext)
  /**
   * User implemented factory, that returns ActorRef, responsible for view creation/destroying.
   */
  trait Factory {
    /** View name. */
    val name: String
    /** View description. */
    val description: Option[String]
    /** View image. */
    val image: Option[Image]
    /** View title with regards of number of views. */
    protected val titlePerActor = new mutable.WeakHashMap[ActorRef, TitleObservableValue]() with mutable.SynchronizedMap[ActorRef, TitleObservableValue]
    /** All currently active actors. */
    protected val activeActorRefs = new AtomicReference[List[ActorRef]](List())
    protected val viewActorLock = new Object

    /** Get active actors list. */
    def actors = activeActorRefs.get
    /** Get title for the actor reference. */
    def title(ref: ActorRef): TitleObservableValue = viewActorLock.synchronized {
      if (!activeActorRefs.get.contains(ref))
        throw new IllegalStateException("Actor reference ${ref} in unknown in factory ${this}.")
      titlePerActor.get(ref) match {
        case Some(observable) => observable
        case None =>
          val observable = new TitleObservableValue(ref)
          titlePerActor(ref) = observable
          observable
      }
    }
    /**
     * Returns the actor reference that could handle Create/Destroy messages.
     * Add reference to activeActors list.
     */
    def viewActor(configuration: Configuration.View, parentContext: EclipseContext): Option[ActorRef]

    override def toString() = s"""View.Factory("${name}", "${description}")"""

    class TitleObservableValue(ref: ActorRef) extends AbstractObservableValue(App.realm) {
      @volatile protected var value: AnyRef = name
      update()

      /** The value type of this observable value. */
      def getValueType(): AnyRef = classOf[String]

      def update() = viewActorLock.synchronized {
        val n = activeActorRefs.get.filter(!_.isTerminated).indexOf(ref)
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
    lazy val props = injectOptional[Props]("Core.GUI.ViewLayer") getOrElse Props(classOf[ViewLayer],
      // view layer id
      UUID.fromString("00000000-0000-0000-0000-000000000000"),
      // parent context
      Core.context)
  }
}
