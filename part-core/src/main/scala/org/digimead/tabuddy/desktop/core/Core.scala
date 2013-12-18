/**
 * This file is part of the TA Buddy project.
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

package org.digimead.tabuddy.desktop.core

import akka.actor.{ ActorRef, Props, ScalaActorRef, UnhandledMessage, actorRef2Scala }
import java.util.concurrent.atomic.AtomicLong
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.api.Main
import org.digimead.tabuddy.desktop.core.console.Console
import org.digimead.tabuddy.desktop.core.definition.{ NLS, Operation }
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.definition.api.OperationApprover
import org.digimead.tabuddy.desktop.core.support.App
import org.osgi.framework.{ BundleContext, BundleEvent, BundleListener, ServiceRegistration }
import scala.concurrent.Future
import scala.language.implicitConversions

/**
 * Root actor of the Core component.
 */
class Core extends akka.actor.Actor with Loggable {
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** Inconsistent elements. */
  protected var inconsistentSet = Set[AnyRef](Console, Core)
  @volatile protected var mainRegistration: Option[ServiceRegistration[api.Main]] = None
  /** Start/stop initialization lock. */
  private val initializationLock = new Object
  log.debug("Start actor " + self.path)

  /** Console actor. */
  val consoleRef = context.actorOf(console.Console.props, console.Console.id)

  if (App.watch(Activator, EventLoop, this).hooks.isEmpty)
    App.watch(Activator, EventLoop, this).always().
      makeAfterStart { onAppStarted() }.makeBeforeStop { onAppStopped() }.sync()

  /** Is called asynchronously after 'actor.stop()' is invoked. */
  override def postStop() = {
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Consistent[_]])
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Inconsistent[_]])
    App.system.eventStream.unsubscribe(self, classOf[UnhandledMessage])
    // Stop "main" service.
    mainRegistration.foreach { serviceRegistration ⇒
      log.debug("Unregister TA Buddy Desktop application entry point service.")
      serviceRegistration.unregister()
    }
    mainRegistration = None
    App.watch(this) off ()
    log.debug(self.path.name + " actor is stopped.")
  }
  /** Is called when an Actor is started. */
  override def preStart() {
    App.system.eventStream.subscribe(self, classOf[UnhandledMessage])
    App.system.eventStream.subscribe(self, classOf[App.Message.Inconsistent[_]])
    App.system.eventStream.subscribe(self, classOf[App.Message.Consistent[_]])
    App.watch(this) on ()
    log.debug(self.path.name + " actor is started.")
  }
  def receive = {
    case message @ App.Message.Attach(props, name) ⇒ App.traceMessage(message) {
      sender ! context.actorOf(props, name)
    }

    case message @ App.Message.Inconsistent(element, from) if from != Some(self) ⇒ App.traceMessage(message) {
      if (inconsistentSet.isEmpty) {
        log.debug("Lost consistency.")
        context.system.eventStream.publish(App.Message.Inconsistent(Core, self))
      }
      inconsistentSet = inconsistentSet + element
    }

    case message @ App.Message.Consistent(element, from) if from != Some(self) ⇒ App.traceMessage(message) {
      inconsistentSet = inconsistentSet - element
      if (inconsistentSet.isEmpty) {
        log.debug("Return integrity.")
        context.system.eventStream.publish(App.Message.Consistent(Core, self))
      }
    }

    case message @ App.Message.Consistency(set, from) if set.isEmpty ⇒ App.traceMessage(message) {
      from.foreach(_ ! App.Message.Consistency(inconsistentSet, Some(self)))
    }

    case message @ App.Message.Consistent(element, from) if from == Some(self) ⇒ // skip
    case message @ App.Message.Inconsistent(element, from) if from == Some(self) ⇒ // skip

    case message: BundleContext ⇒ App.traceMessage(message) { main(message) }

    case UnhandledMessage(message, sender, self) ⇒
      log.fatal(s"Received unexpected message '${sender}' -> '${self}': '${message}'")
  }

  /** Starts main service when OSGi environment will be stable. */
  @log
  protected def main(context: BundleContext) = try {
    val lastEventTS = new AtomicLong(System.currentTimeMillis())
    val listener = new BundleListener {
      def bundleChanged(event: BundleEvent) = lastEventTS.set(System.currentTimeMillis())
    }
    context.addBundleListener(listener)
    // OSGi is infinite black box of bundles
    // waiting for stabilization
    log.debug("Waiting OSGi for stabilization.")
    val frame = 400 // 0.4s for decision
    while ((System.currentTimeMillis - lastEventTS.get()) < frame)
      Thread.sleep(100) // 0.1s for iteration
    log.debug("OSGi stabilization is achieved.")
    context.removeBundleListener(listener)
    // Start "main" service
    mainRegistration = Option(context.registerService(classOf[api.Main], AppService, null))
    mainRegistration match {
      case Some(service) ⇒ log.debug("Register TA Buddy Desktop application entry point service as: " + service)
      case None ⇒ log.error("Unable to register TA Buddy Desktop application entry point service.")
    }
    self ! App.Message.Consistent(Core, None)
  } finally App.watch(context).on() // Send notice to Activator that initialization is complete.
  /** Invoked when application started. */
  protected def onAppStarted(): Unit = initializationLock.synchronized {
    App.watch(Core) on {
      self ! App.Message.Inconsistent(Core, None)
      App.verifyApplicationEnvironment
      // Wait for translationService
      NLS.translationService
      // Translate all messages
      Messages
      if (App.isUIAvailable) {
        log.info("Start application with GUI.")
        Core.DI.approvers.foreach(Operation.history.addOperationApprover)
      } else
        log.info("Start application without GUI.")
      command.Commands.configure()
      Console ! Console.Message.Notice("\n" + Console.welcomeMessage())
      Console ! App.Message.Start(Left(Console))
      Console ! Console.Message.Notice("Core component is started.")
      self ! App.Message.Consistent(Core, None)
    }
  }
  /** Invoked when application stopped. */
  protected def onAppStopped(): Unit = initializationLock.synchronized {
    App.watch(Core) off {
      self ! App.Message.Inconsistent(Core, None)
      command.Commands.unconfigure()
      if (App.isUIAvailable)
        Core.DI.approvers.foreach(Operation.history.removeOperationApprover)
      Console ! Console.Message.Notice("Core component is stopped.")
    }
  }
}

object Core extends Loggable {
  implicit def core2actorRef(c: Core.type): ActorRef = c.actor
  implicit def core2actorSRef(c: Core.type): ScalaActorRef = c.actor
  /** Core actor reference. */
  lazy val actor = App.system.actorOf(props, id)
  /** Root context. */
  val context = Context("Core"): Context.Rich
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  /** Component name. */
  val name = "digi-tabuddy-desktop-core"
  /** Core actor path. */
  lazy val path = actor.path

  /** Core actor reference configuration object. */
  def props = DI.props

  override def toString = "Core[Singleton]"

  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /**
     * Collection of operation approvers.
     *
     * Each collected approver must be:
     *  1. an instance of definition.api.OperationApprover
     *  2. has name that starts with "Approver."
     */
    lazy val approvers = bindingModule.bindings.filter {
      case (key, value) ⇒ classOf[org.digimead.tabuddy.desktop.core.definition.api.OperationApprover].isAssignableFrom(key.m.runtimeClass)
    }.map {
      case (key, value) ⇒
        key.name match {
          case Some(name) if name.startsWith("Approver.") ⇒
            log.debug(s"Operation '${name}' loaded.")
          case _ ⇒
            log.debug(s"'${key.name.getOrElse("Unnamed")}' operation approver skipped.")
        }
        bindingModule.injectOptional(key).asInstanceOf[Option[org.digimead.tabuddy.desktop.core.definition.OperationApprover]]
    }.flatten
    /** Core Akka factory. */
    lazy val props = injectOptional[Props]("Core") getOrElse Props[Core]
  }
}
