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
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.Core
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.core.ui.block.builder.ViewContentBuilder
import org.digimead.tabuddy.desktop.core.ui.definition.widget.{ SComposite, SCompositeTab, VComposite }
import org.eclipse.core.databinding.observable.Diffs
import org.eclipse.core.databinding.observable.value.AbstractObservableValue
import org.eclipse.jface.databinding.swt.SWTObservables
import org.eclipse.swt.custom.ScrolledComposite
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.widgets.{ Composite, Widget }
import scala.collection.{ immutable, mutable }
import scala.concurrent.Await

/** View actor binded to SComposite that contains an actual view from a view factory. */
class View(val viewId: UUID, val viewContext: Context.Rich) extends Actor with Loggable {
  /** Akka communication timeout. */
  implicit val timeout = akka.util.Timeout(UI.communicationTimeout)
  /** Parent stack actor. */
  lazy val container = context.parent
  /** Flag indicating whether the View is alive. */
  var terminated = false
  /** View JFace instance. */
  var view: Option[VComposite] = None
  log.debug(s"Start actor ${self.path} ${viewId}")

  /** Is called asynchronously after 'actor.stop()' is invoked. */
  override def postStop() = {
    log.debug(this + " is stopped.")
    view.foreach { view ⇒
      App.execNGet {
        if (view.isDisposed()) {
          log.debug(s"Terminate ${view.contentRef.path.name}.")
          /*
           * Stop view content actor only if view is disposed.
           * All UI elements ALWAYS disposed before actor termination by design.
           * If view content isn't disposed then it maybe moved to different window.
           */
          context.stop(view.contentRef)
        } else
          view.dispose()
      }
    }
  }
  /** Is called when an Actor is started. */
  override def preStart() = log.debug(this + " is started.")
  def receive = {
    // Create new content.
    case message @ App.Message.Create(View.<>(viewConfiguration, parentWidget, content), Some(this.container), _) ⇒ App.traceMessage(message) {
      if (terminated) {
        App.Message.Error(s"${this} is terminated.", self)
      } else {
        create(viewConfiguration, parentWidget, content) match {
          case Some(viewWidget) ⇒
            container ! App.Message.Create(viewWidget, self)
            App.Message.Create(viewWidget, self)
          case None ⇒
            App.Message.Error(s"Unable to create ${viewConfiguration}.", self)
        }
      }
    } foreach { sender ! _ }

    case message @ App.Message.Destroy(_, _, _) ⇒ App.traceMessage(message) {
      if (terminated) {
        App.Message.Error(s"${this} is terminated.", self)
      } else {
        destroy() match {
          case Some(viewWidget) ⇒
            container ! App.Message.Destroy(viewWidget, self)
            App.Message.Destroy(viewWidget, self)
          case None ⇒
            App.Message.Error(s"Unable to destroy ${view}.", self)
        }
      }
    } foreach { sender ! _ }

    case message @ App.Message.Get(Actor) ⇒ App.traceMessage(message) {
      Map(context.children.map { case child ⇒ child -> Map() }.toSeq: _*)
    } foreach { sender ! _ }

    case message @ App.Message.Start((widget: Widget, Seq(view)), _, _) if (Some(view) == this.view) ⇒ App.traceMessage(message) {
      if (terminated) {
        App.Message.Error(s"${this} is terminated.", self)
      } else {
        onStart(widget)
        App.Message.Start(widget, None)
      }
    } foreach { sender ! _ }

    case message @ App.Message.Stop(widget: Widget, _, _) ⇒ App.traceMessage(message) {
      if (terminated) {
        App.Message.Error(s"${this} is terminated.", self)
      } else {
        onStop(widget)
        App.Message.Stop(widget, None)
      }
    } foreach { sender ! _ }

    case App.Message.Error(Some(message), _) if message.endsWith("is terminated.") ⇒
      log.debug(message)
  }

  /**
   * Create view.
   *
   * @param viewConfiguration configuration of this view.
   * @param parentWidget container of the new VComposite
   * @param innerContent exists content (from DND source or something like that)
   * @return VComposite if everything is fine
   */
  protected def create(viewConfiguration: Configuration.CView,
    parentWidget: ScrolledComposite, content: Option[VComposite]): Option[VComposite] = {
    if (view.nonEmpty)
      throw new IllegalStateException("Unable to create view. It is already created.")
    App.assertEventThread(false)
    ViewContentBuilder.content(viewConfiguration, parentWidget, viewContext, context, content) match {
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
        // Add new view to the common map.
        View.viewMapRWL.writeLock().lock()
        try View.viewMap += viewWidgetWithContent -> self
        finally View.viewMapRWL.writeLock().unlock()
        this.view
      case None ⇒
        log.fatal(s"Unable to build view ${viewConfiguration}.")
        None
    }
  }
  /** Destroy view. */
  protected def destroy(): Option[VComposite] = view.flatMap { view ⇒
    // Ask widget.contentRef to destroy it
    Await.result(view.contentRef ? App.Message.Destroy(view, self), timeout.duration) match {
      case App.Message.Destroy(body: Composite, _, _) ⇒
        if (body.isInstanceOf[SComposite]) // This must be not a part of stack.
          throw new IllegalArgumentException(s"Illegal body received ${body}")
        log.debug(s"View layer ${view} content is destroyed.")
        App.execNGet { view.dispose() }
        terminated = true
        Some(view)
      case App.Message.Error(error, None) ⇒
        log.fatal(s"Unable to destroy content for ${view}: ${error.getOrElse("unknown")}")
        None
      case error ⇒
        log.fatal(s"Unable to destroy content for ${view}: ${error}.")
        None
    }
  }
  /** User start interaction with window/stack supervisor/view. Focus is gained. */
  protected def onStart(widget: Widget) = view match {
    case Some(view) ⇒
      log.debug("View layer started by focus event on " + widget)
      viewContext.activateBranch()
      Await.ready(view.contentRef ? App.Message.Start(widget, self), timeout.duration)
    case None ⇒
      log.debug("Unable to start unexists view.")
  }
  /** Focus is lost. */
  protected def onStop(widget: Widget) = view match {
    case Some(view) ⇒
      Await.ready(view.contentRef ? App.Message.Stop(widget, self), timeout.duration)
    case None ⇒
      log.debug("Unable to stop unexists view.")
  }

  override lazy val toString = "View[actor/%08X]".format(viewId.hashCode())
}

object View extends Loggable {
  /** Akka execution context. */
  implicit protected lazy val ec = App.system.dispatcher
  /** Akka communication timeout. */
  implicit protected lazy val timeout = akka.util.Timeout(UI.communicationTimeout)
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  /** All application view layers. */
  protected val viewMap = mutable.WeakHashMap[VComposite, ActorRef]()
  /** View layer map lock. */
  protected val viewMapRWL = new ReentrantReadWriteLock

  /** Get view name. */
  def name(id: UUID) = View.id + "_%08X".format(id.hashCode())
  /** View actor reference configuration object. */
  def props = DI.props

  override def toString = "View[Singleton]"

  /** Wrapper for App.Message,Create argument. */
  case class <>(val viewConfiguration: Configuration.CView,
    val parentWidget: ScrolledComposite, val innerContent: Option[VComposite])
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
    /** View actor reference configuration object. */
    def props: Props
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
    def viewActor(container: ActorRef, configuration: Configuration.CView): Option[ActorRef] = viewActorLock.synchronized {
      val viewName = "Content_" + id + "_%08X".format(configuration.id.hashCode())
      log.debug(s"Create actor ${viewName}.")
      val future = UI.actor ? App.Message.Attach(props.copy(args = immutable.Seq(configuration.id)), viewName)
      try {
        val newActorRef = Await.result(future.mapTo[ActorRef], timeout.duration)
        newActorRef ! App.Message.Set(container)
        activeActorRefs.set(activeActorRefs.get() :+ newActorRef)
        titlePerActor.values.foreach(_.update)
        Some(newActorRef)
      } catch {
        case e: InterruptedException ⇒
          log.error(e.getMessage, e)
          None
        case e: TimeoutException ⇒
          log.error(e.getMessage, e)
          None
      }
    }

    override lazy val toString = s"""View.Factory("${name}", "${shortDescription}")"""

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
   * View map consumer.
   */
  trait ViewMapConsumer {
    /** Get map with all application view layers. */
    def viewMap: immutable.Map[VComposite, ActorRef] = {
      View.viewMapRWL.readLock().lock()
      try View.viewMap.toMap
      finally View.viewMapRWL.readLock().unlock()
    }
  }
  /**
   * View map consumer.
   */
  trait ViewMapDisposer {
    this: VComposite ⇒
    /** Remove this VComposite from the common map. */
    def viewRemoveFromCommonMap() {
      // Remove view from the common map.
      View.viewMapRWL.writeLock().lock()
      try View.viewMap -= this
      finally View.viewMapRWL.writeLock().unlock()
    }
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** View actor reference configuration object. */
    lazy val props = injectOptional[Props]("Core.UI.View") getOrElse Props(classOf[View],
      // view layer id
      UUID.fromString("00000000-0000-0000-0000-000000000000"),
      // parent context
      Core.context)
  }
}
