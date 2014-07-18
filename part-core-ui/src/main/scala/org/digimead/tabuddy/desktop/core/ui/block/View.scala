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

import akka.actor.{ Actor, ActorRef, PoisonPill, Props, actorRef2Scala }
import akka.pattern.{ AskTimeoutException, ask }
import java.util.UUID
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantReadWriteLock
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.Core
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.core.ui.block.builder.ViewContentBuilder
import org.digimead.tabuddy.desktop.core.ui.definition.widget.{ SComposite, SCompositeTab, VComposite }
import org.digimead.tabuddy.desktop.core.ui.support.Generic
import org.eclipse.core.databinding.observable.value.WritableValue
import org.eclipse.e4.core.contexts.{ IEclipseContext, RunAndTrack }
import org.eclipse.jface.databinding.swt.SWTObservables
import org.eclipse.swt.custom.ScrolledComposite
import org.eclipse.swt.events.{ DisposeEvent, DisposeListener }
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.widgets.{ Composite, Widget }
import scala.collection.{ immutable, mutable }
import scala.concurrent.{ Await, Future }
import scala.ref.WeakReference

/** View actor binded to SComposite that contains an actual view from a view factory. */
class View(val viewId: UUID, val viewContext: Context.Rich) extends Actor with XLoggable {
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
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
      // !Danger! see org.digimead.tabuddy.desktop.core.ui.UI ERR01
      App.execNGet { if (view.isDisposed()) { true } else { view.dispose(); false } } && {
        log.debug(s"Terminate ${view.contentRef.path.name}.")
        /*
         * Stop view content actor only if view is disposed.
         * All UI elements ALWAYS disposed before actor termination by design.
         * If view content isn't disposed then it maybe moved to different location.
         */
        view.contentRef ! PoisonPill
        true
      }
    }
  }
  /** Is called when an Actor is started. */
  override def preStart() = log.debug(this + " is started.")
  def receive = {
    // Create new content.
    case message @ App.Message.Create(View.<>(viewConfiguration, parentWidget, content), Some(this.container), None) ⇒ App.traceMessage(message) {
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

    case message @ App.Message.Destroy(None, _, None) ⇒ App.traceMessage(message) {
      if (terminated) {
        view.foreach(view ⇒ container ! App.Message.Destroy(view, self))
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

    case message @ App.Message.Start((widget: Widget, Seq(view: VComposite)), _, None) if (Some(view) == this.view) ⇒ Option {
      if (terminated) {
        App.Message.Error(s"${this} is terminated.", self)
      } else {
        try onStart(widget) catch { case e: Throwable ⇒ log.error(e.getMessage, e) }
        App.Message.Start(widget, None)
      }
    } foreach { sender ! _ }

    case message @ App.Message.Stop((widget: Widget, Seq(view: VComposite)), _, None) if (Some(view) == this.view) ⇒ Option {
      if (terminated) {
        App.Message.Error(s"${this} is terminated.", self)
      } else {
        try onStop(widget) catch { case e: Throwable ⇒ log.error(e.getMessage, e) }
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
        // Yes. This is MUCH faster then simple App.exec.
        Future {
          val factory = viewConfiguration.factory()
          App.execAsync {
            if (!parentWidget.isDisposed())
              parentWidget.getParent() match {
                case tab: SCompositeTab ⇒
                  tab.getItems().find { item ⇒ item.getData(UI.swtId) == viewConfiguration.id } match {
                    case Some(tabItem) ⇒
                      val binding = App.bindingContext.bindValue(SWTObservables.observeText(tabItem), factory.titleObservable(viewWidgetWithContent.contentRef.path.name))
                      tabItem.addDisposeListener(new DisposeListener {
                        def widgetDisposed(e: DisposeEvent) = binding.dispose()
                      })
                    case None ⇒
                      log.fatal(s"TabItem for ${viewConfiguration} in ${tab} not found.")
                  }
                case other ⇒
              }
          }
        } onFailure { case e: Throwable ⇒ log.error(e.getMessage(), e) }
        // Add new view to the common map.
        View.viewMapRWL.writeLock().lock()
        try View.viewMap += viewWidgetWithContent.id -> viewWidgetWithContent
        finally View.viewMapRWL.writeLock().unlock()
        this.view
      case None ⇒
        App.exec {
          if (!parentWidget.isDisposed())
            log.fatal(s"Unable to build view ${viewConfiguration}.")
        }
        None
    }
  }
  /** Destroy view. */
  protected def destroy(): Option[VComposite] = view.flatMap { view ⇒
    try {
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
    } catch {
      case e: AskTimeoutException ⇒
        // Content may had already been terminated.
        log.debug(s"View layer ${view} content is destroyed.")
        App.execNGet { view.dispose() }
        terminated = true
        Some(view)
    }
  }
  /** User start interaction with window/stack supervisor/view. Focus is gained. */
  protected def onStart(widget: Widget) = view match {
    case Some(view) ⇒
      log.debug("View layer started by focus event on " + widget)
      viewContext.getChildren().find(ctx ⇒ Context.getName(ctx).map(_.endsWith(self.path.name)) getOrElse false) match {
        case Some(contentContext) ⇒
          contentContext.activateBranch()
        case None ⇒
          log.debug(s"Unable to find content context. Activate ${viewContext} branch.")
          viewContext.activateBranch()
      }
      Await.ready(view.contentRef ? App.Message.Start(widget, self), timeout.duration)
    case None ⇒
      log.debug("Unable to start unexists view.")
  }
  /** Focus is lost. */
  protected def onStop(widget: Widget) = view match {
    case Some(view) ⇒
      log.debug(s"View layer $this lost focus.")
      Await.ready(view.contentRef ? App.Message.Stop(widget, self), timeout.duration)
    case None ⇒
      log.debug("Unable to stop unexists view.")
  }

  override lazy val toString = "View[actor/%08X]".format(viewId.hashCode())
}

object View extends XLoggable {
  /** Akka execution context. */
  implicit protected lazy val ec = App.system.dispatcher
  /** Akka communication timeout. */
  implicit protected lazy val timeout = akka.util.Timeout(UI.communicationTimeout)
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  /** All application view layers. */
  protected val viewMap = mutable.HashMap[UUID, VComposite]()
  /** View layer map lock. */
  protected val viewMapRWL = new ReentrantReadWriteLock

  /** Get content name. */
  def contentName(id: UUID) = "Content_" + name(id)
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
    /** Features. */
    val features: Seq[String]
    /** Factory view title synchronizer. */
    val titleSynchronizer = new TitleSynchronizer
    /** All available content contexts for this factory Actor name -> (counter, content context). */
    protected val contentContexts = mutable.HashMap[String, (Long, Context)]()
    /** Factory lock. */
    protected val factoryRWL = new ReentrantReadWriteLock()
    /** View instance counter. */
    // There are only 2^64 - 1 views. Please restart this software before the limit will be reached. ;-)
    protected var instanceCounter = Long.MinValue

    /** Get contexts map Actor name -> (counter, content context). */
    def contexts: Map[String, (Long, Context)] = {
      factoryRWL.readLock().lock()
      try contentContexts.toMap
      finally factoryRWL.readLock().unlock()
    }
    /** Get factory configuration. */
    def configuration = Configuration.Factory(App.bundle(this.getClass()).getSymbolicName(), this.getClass().getName())
    /** Get a title of the view. */
    def getTitle(actorName: String): Option[String] = {
      factoryRWL.readLock().lock()
      try {
        contentContexts.get(actorName).flatMap {
          case (_, viewContext) ⇒ Option(viewContext.get(UI.Id.viewTitle).asInstanceOf[String])
        }
      } finally factoryRWL.readLock().unlock()
    }
    /** View actor reference configuration object. */
    def props: Props
    /** Get title for the actor reference. */
    def titleObservable(actorName: String): WritableValue = {
      App.assertEventThread()
      factoryRWL.readLock().lock()
      try {
        contentContexts.get(actorName) match {
          case Some((instanceNumber, contentContext)) ⇒
            val initialValue = Option(contentContext.get(UI.Id.viewTitle)) getOrElse {
              contentContext.set(UI.Id.viewTitle, name.name)
              name.name
            }
            val observable = new WritableValue(App.realm, initialValue)
            contentContext.runAndTrack(new ViewTitleRunAndTrack(WeakReference(observable)))
            observable
          case None ⇒
            throw new IllegalStateException(s"Actor with name ${actorName} is unknown at factory ${this}. Is it registered with `def IView.preStart()`?")
        }
      } finally factoryRWL.readLock().unlock()
    }
    /**
     * Returns the actor reference that could handle Create/Destroy messages.
     * Add reference to activeActors list.
     */
    def viewActor(container: ActorRef, configuration: Configuration.CView): Option[ActorRef] = {
      val viewName = contentName(configuration.id)
      log.debug(s"Create actor ${viewName}.")
      val future = UI.actor ? App.Message.Attach(props.copy(args = immutable.Seq(configuration.id, Factory.this)), viewName)
      try {
        Some(Await.result(future.mapTo[ActorRef], timeout.duration))
      } catch {
        case e: InterruptedException ⇒
          log.error(e.getMessage, e)
          None
        case e: TimeoutException ⇒
          log.error(e.getMessage, e)
          None
      }
    }
    /** Register view as active in activeActorRefs. */
    def register(actorName: String, context: Context) {
      if (Context.getName(context) != Some(actorName))
        throw new IllegalArgumentException(s"Content actor name ${actorName} is different than ${context} name.")
      factoryRWL.writeLock().lock()
      try {
        log.debug(s"Register ${actorName} in ${this}.")
        if (contentContexts.isDefinedAt(actorName))
          throw new IllegalArgumentException(s"Content actor name ${actorName} is already registered.")
        instanceCounter += 1
        contentContexts(actorName) = (instanceCounter, context)
        context.set(UI.Id.viewTitle, name.name)
        val views = contentContexts.values.toSeq
        Future { titleSynchronizer.notify(views.sortBy(_._1).map(_._2)) }.
          onFailure { case e: Throwable ⇒ log.error(e.getMessage(), e) }
      } finally factoryRWL.writeLock().unlock()
    }
    /** Register view as active in activeActorRefs. */
    def unregister(actorName: String) {
      factoryRWL.writeLock().lock()
      try {
        log.debug(s"Unregister ${actorName} in ${this}.")
        if (!contentContexts.isDefinedAt(actorName))
          throw new IllegalArgumentException(s"Content actor name ${actorName} is not registered.")
        contentContexts.remove(actorName)
        val views = contentContexts.values.toSeq
        Future { titleSynchronizer.notify(views.sortBy(_._1).map(_._2)) }.
          onFailure { case e: Throwable ⇒ log.error(e.getMessage(), e) }
      } finally factoryRWL.writeLock().unlock()
    }

    override lazy val toString = s"""View.Factory("${name.name}", "${shortDescription}")"""

    /**
     * View's context title synchronizer.
     */
    class TitleSynchronizer extends Generic.TitleSynchronizer[Context] {
      /** Update view's contexts. */
      protected def synchronize(viewContexts: Seq[Context]) {
        var n = 0
        viewContexts.foreach { viewContext ⇒
          n += 1
          if (n > 1)
            viewContext.set(UI.Id.viewTitle, s"${Factory.this.name.name}(${n})")
          else
            viewContext.set(UI.Id.viewTitle, Factory.this.name.name)
        }
      }
    }
    /**
     * View title runAndTrack
     */
    class ViewTitleRunAndTrack(observable: WeakReference[WritableValue]) extends RunAndTrack {
      override def changed(context: IEclipseContext): Boolean = {
        if (context == null)
          return false
        val newValue = context.get(UI.Id.viewTitle)
        observable.get.foreach(observable ⇒ App.exec { observable.setValue(newValue) })
        true
      }
    }
  }
  /**
   * View map consumer.
   */
  trait ViewMapConsumer {
    /** Get map with all application view layers. */
    def viewMap: immutable.Map[UUID, VComposite] = {
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
      try {
        View.viewMap.get(id) match {
          // This is false if we replaced vComposite with one's copy.
          case Some(vComposite) if vComposite == this ⇒ View.viewMap -= this.id
          case _ ⇒
        }
      } finally View.viewMapRWL.writeLock().unlock()
    }
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** View actor reference configuration object. */
    lazy val props = injectOptional[Props]("Core.UI.View") getOrElse Props(classOf[View],
      // view layer id
      UUID.fromString("00000000-0000-0000-0000-000000000000"),
      // parent context
      Core.context)
  }
}
