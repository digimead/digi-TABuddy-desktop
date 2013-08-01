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

package org.digimead.tabuddy.desktop.logic

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future

import org.digimead.configgy.Configgy
import org.digimead.configgy.Configgy.getImplementation
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Core
import org.digimead.tabuddy.desktop.Core.core2actorRef
import org.digimead.tabuddy.desktop.gui.GUI
import org.digimead.tabuddy.desktop.logic.Config.config2implementation
import org.digimead.tabuddy.desktop.logic.payload.ElementTemplate
import org.digimead.tabuddy.desktop.logic.payload.Payload
import org.digimead.tabuddy.desktop.logic.payload.Payload.payload2implementation
import org.digimead.tabuddy.desktop.logic.payload.TypeSchema
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.Timeout
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Model.model2implementation
import org.digimead.tabuddy.model.element.Element
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jface.commands.ToggleState

import akka.actor.ActorRef
import akka.actor.Inbox
import akka.actor.Props
import akka.actor.ScalaActorRef
import akka.actor.actorRef2Scala

import language.implicitConversions

/**
 * Root actor of the Logic component.
 */
class Logic extends akka.actor.Actor with Loggable {
  /** Inconsistent elements. */
  @volatile protected var inconsistentSet = Set[AnyRef]()
  /** Flag indicating whether workbench is valid. */
  @volatile protected var fGUIStarted = false
  /** Current bundle */
  protected lazy val thisBundle = App.bundle(getClass())
  private val initializationLock = new Object
  log.debug("Start actor " + self.path)

  /*
   * Logic component actors.
   */
  //val actionRef = context.actorOf(action.Action.props, action.Action.id)
  //val modelToolBar = context.actorOf(ModelToolBar.props, ModelToolBar.id)

  /** Is called asynchronously after 'actor.stop()' is invoked. */
  override def postStop() = {
    App.system.eventStream.unsubscribe(self, classOf[Element.Event.ModelReplace[_ <: Model.Interface[_ <: Model.Stash], _ <: Model.Interface[_ <: Model.Stash]]])
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Consistent[_]])
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Inconsistent[_]])
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Stopped[_]])
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Started[_]])
    log.debug(self.path.name + " actor is stopped.")
  }
  /** Is called when an Actor is started. */
  override def preStart() {
    App.system.eventStream.subscribe(self, classOf[App.Message.Started[_]])
    App.system.eventStream.subscribe(self, classOf[App.Message.Stopped[_]])
    App.system.eventStream.subscribe(self, classOf[App.Message.Inconsistent[_]])
    App.system.eventStream.subscribe(self, classOf[App.Message.Consistent[_]])
    App.system.eventStream.subscribe(self, classOf[Element.Event.ModelReplace[_ <: Model.Interface[_ <: Model.Stash], _ <: Model.Interface[_ <: Model.Stash]]])
    log.debug(self.path.name + " actor is started.")
  }
  def receive = {
    case message @ App.Message.Attach(props, name) => App.traceMessage(message) {
      sender ! context.actorOf(props, name)
    }
    case message @ App.Message.Inconsistent(element, sender) if element != Logic && App.bundle(element.getClass()) == thisBundle => App.traceMessage(message) {
      if (inconsistentSet.isEmpty) {
        log.debug("Lost consistency.")
        context.system.eventStream.publish(App.Message.Inconsistent(Logic, self))
      }
      inconsistentSet = inconsistentSet + element
    }
    case message @ App.Message.Consistent(element, sender) if element != Logic && App.bundle(element.getClass()) == thisBundle => App.traceMessage(message) {
      inconsistentSet = inconsistentSet - element
      if (inconsistentSet.isEmpty) {
        log.debug("Return integrity.")
        context.system.eventStream.publish(App.Message.Consistent(Logic, self))
      }
    }
    case message @ Element.Event.ModelReplace(oldModel, newModel, modified) => App.traceMessage(message) {
      if (fGUIStarted) onModelInitialization(oldModel, newModel, modified)
    }
    case message @ App.Message.Started(GUI, sender) => App.traceMessage(message) {
      fGUIStarted = true
      future { onWorkbenchValid() } onFailure {
        case e: Exception => log.error(e.getMessage(), e)
        case e => log.error(e.toString())
      }
    }
    case message @ App.Message.Stopped(GUI, sender) => App.traceMessage(message) {
      fGUIStarted = false
      future { onWorkbenchInvalid } onFailure {
        case e: Exception => log.error(e.getMessage(), e)
        case e => log.error(e.toString())
      }
    }
    case message @ App.Message.Inconsistent(element, sender) => // skip
    case message @ App.Message.Consistent(element, sender) => // skip
  }

  /** Close infrastructure wide container. */
  @log
  protected def closeContainer() {
    log.info(s"Close infrastructure wide container '${Logic.container.getName()}' ")
    App.publish(App.Message.Inconsistent(this, self))
    val progressMonitor = new NullProgressMonitor()
    if (Logic.container.isOpen()) {
      Payload.getModelMarker(Model).foreach(_.save)
      Logic.container.close(progressMonitor)
    }
    App.publish(App.Message.Consistent(this, self))
  }
  /** This callback is invoked when UI initialization complete */
  @log
  def onApplicationStartup() {
    // load last active model at startup
    Configgy.getString("payload.model").foreach(lastModelID =>
      Payload.listModels.find(m => m.isValid && m.id.name == lastModelID).foreach { marker =>
        Payload.acquireModel(marker)
        //TODO JobModelAcquire
        //JobModelAcquire(None, Symbol(lastModelID)).foreach(_.execute)
      })
  }
  /** This callback is invoked at an every model initialization. */
  @log
  protected def onModelInitialization(oldModel: Model.Generic, newModel: Model.Generic, modified: Element.Timestamp) {
    TypeSchema.onModelInitialization(oldModel, newModel, modified)
    ElementTemplate.onModelInitialization(oldModel, newModel, modified)
    Data.onModelInitialization(oldModel, newModel, modified)
    Config.save()
  }
  /** This callback is invoked when workbench is valid. */
  @log
  protected def onWorkbenchValid() = initializationLock.synchronized {
    App.afterStart("Desktop Logic", Timeout.normal.toMillis, Core.getClass()) {
      val context = thisBundle.getBundleContext()
      openContainer()
      // Prepare for startup.
      // Reset for sure. There is maybe still something in memory after the bundle reload.
      Model.reset(Payload.defaultModel)
      // Startup sequence.
      Config.start(context) // Initialize the application configuration based on Configgy
      //Transport.start() // Initialize the network transport(s)
      //Job.start()           // Initialize the job handler
      //Approver.start()           // Initialize the job approver
      App.markAsStarted(Logic.getClass)
      future { onApplicationStartup() } onFailure {
        case e: Exception => log.error(e.getMessage(), e)
        case e => log.error(e.toString())
      }
    }
  }
  /** This callback is invoked when workbench is invalid. */
  @log
  protected def onWorkbenchInvalid() = initializationLock.synchronized {
    val context = thisBundle.getBundleContext()
    App.markAsStopped(Logic.getClass())
    // Prepare for shutdown.
    //Approver.stop()
    //Job.stop()
    //Transport.stop()
    Config.stop(context)
    closeContainer()
    // Avoid deadlock.
    App.system.eventStream.unsubscribe(self, classOf[Element.Event.ModelReplace[_ <: Model.Interface[_ <: Model.Stash], _ <: Model.Interface[_ <: Model.Stash]]])
    Model.reset(Payload.defaultModel)
    if (inconsistentSet.nonEmpty)
      log.fatal("Inconsistent elements detected: " + inconsistentSet)
    // The everything is stopped. Absolutely consistent.
    App.publish(App.Message.Consistent(Logic, self))
  }
  /** Open infrastructure wide container. */
  @log
  protected def openContainer() {
    log.info(s"Open infrastructure wide container '${Logic.container.getName()}' ")
    App.publish(App.Message.Inconsistent(this, self))
    val progressMonitor = new NullProgressMonitor()
    if (!Logic.container.exists())
      Logic.container.create(progressMonitor)
    Logic.container.open(progressMonitor)
    App.publish(App.Message.Consistent(this, self))
  }

}

object Logic {
  implicit def logic2actorRef(c: Logic.type): ActorRef = c.actor
  implicit def logic2actorSRef(c: Logic.type): ScalaActorRef = c.actor
  /** Logic actor reference. */
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
  /** Logic actor path. */
  lazy val actorPath = Core.path / id
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  /** Logic actor reference configuration object. */
  lazy val props = DI.props
  /** Infrastructure wide container. */
  lazy val container = {
    val root = ResourcesPlugin.getWorkspace().getRoot()
    root.getProject(Logic.containerName)
  }
  // Initialize descendant actor singletons
  toolbar.ModelToolBar

  def containerName() = DI.infrastructureWideProjectName
  override def toString = "Logic[Singleton]"

  /*
   * Explicit import for runtime components/bundle manifest generation.
   */
  private def explicitToggleState: ToggleState = ???

  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Logic actor reference configuration object. */
    lazy val props = injectOptional[Props]("Logic") getOrElse Props[Logic]
    /**
     * Infrastructure wide container name that required for minimization of resources complexity.
     * It is IProject singleton label.
     */
    lazy val infrastructureWideProjectName = "tabuddy"
  }
}
