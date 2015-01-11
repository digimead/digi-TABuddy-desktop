/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2014 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.core.keyring

import akka.actor.{ ActorRef, Inbox, Props, ScalaActorRef, actorRef2Scala }
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.Core
import org.digimead.tabuddy.desktop.core.console.Console
import org.digimead.tabuddy.desktop.core.keyring.random.SimpleRandom
import org.digimead.tabuddy.desktop.core.keyring.random.api.XSecureRandom
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.Timeout
import org.eclipse.core.internal.resources.ResourceException
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.NullProgressMonitor
import scala.language.implicitConversions

/**
 * Root actor of the KeyRing(RFC4880) component.
 */
class KeyRing extends akka.actor.Actor with XLoggable {
  /** Inconsistent elements. */
  @volatile protected var inconsistentSet = Set[AnyRef](KeyRing)
  /** Current bundle */
  protected lazy val thisBundle = App.bundle(getClass())
  /** Start/stop initialization lock. */
  private val initializationLock = new Object
  log.debug("Start actor " + self.path)

  if (App.watch(Activator, Core, this).hooks.isEmpty)
    App.watch(Activator, Core, this).always().
      makeAfterStart('core_keyring_KeyRing__onCoreStarted) { onCoreStarted() }.
      makeBeforeStop('core_keyring_KeyRing__onCoreStopped) { onCoreStopped() }.sync()

  /** Is called asynchronously after 'actor.stop()' is invoked. */
  override def postStop() = {
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Inconsistent[_]])
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Consistent[_]])
    App.watch(this) off ()
    log.debug(self.path.name + " actor is stopped.")
  }
  /** Is called when an Actor is started. */
  override def preStart() {
    App.system.eventStream.subscribe(self, classOf[App.Message.Consistent[_]])
    App.system.eventStream.subscribe(self, classOf[App.Message.Inconsistent[_]])
    App.watch(this) on ()
    log.debug(self.path.name + " actor is started.")
  }
  def receive = {
    case message @ App.Message.Attach(props, name, _) ⇒ App.traceMessage(message) {
      sender ! context.actorOf(props, name)
    }

    case message @ App.Message.Consistent(element, from, _) if from != Some(self) &&
      App.bundle(element.getClass()) == thisBundle ⇒ App.traceMessage(message) {
      if (inconsistentSet.nonEmpty) {
        inconsistentSet = inconsistentSet - element
        if (inconsistentSet.isEmpty) {
          log.debug("Return integrity.")
          context.system.eventStream.publish(App.Message.Consistent(KeyRing, self))
        }
      } else
        log.debug(s"Skip message ${message}. Logic is already consistent.")
    }

    case message @ App.Message.Inconsistent(element, from, _) if from != Some(self) &&
      App.bundle(element.getClass()) == thisBundle ⇒ App.traceMessage(message) {
      if (inconsistentSet.isEmpty) {
        log.debug("Lost consistency.")
        context.system.eventStream.publish(App.Message.Inconsistent(KeyRing, self))
      }
      inconsistentSet = inconsistentSet + element
    }

    case message @ App.Message.Close(_, _, _) ⇒ // skip
    case message @ App.Message.Consistent(_, _, _) ⇒ // skip
    case message @ App.Message.Destroy(_, _, _) ⇒ // skip
    case message @ App.Message.Inconsistent(_, _, _) ⇒ // skip
    case message @ App.Message.Open(_, _, _) ⇒ // skip
  }

  /** Close infrastructure wide container. */
  @log
  protected def closeContainer() {
    log.info(s"Close infrastructure wide container '${KeyRing.container.getName()}'.")
    App.publish(App.Message.Inconsistent(this, self))
    val progressMonitor = new NullProgressMonitor()
    if (!KeyRing.container.isOpen())
      KeyRing.container.close(progressMonitor)
    App.publish(App.Message.Consistent(this, self))
  }
  /** Open infrastructure wide container. */
  @log
  protected def openContainer() {
    log.info(s"Open infrastructure wide container '${KeyRing.container.getName()}' at " + KeyRing.container.getLocationURI())
    App.publish(App.Message.Inconsistent(this, self))
    val progressMonitor = new NullProgressMonitor()
    if (!KeyRing.container.exists())
      KeyRing.container.create(progressMonitor)
    try KeyRing.container.open(progressMonitor)
    catch {
      case e: ResourceException if e.getMessage.startsWith("The project description file")
        && e.getMessage.endsWith("The project will not function properly until this file is restored.") ⇒
        KeyRing.container.delete(true, true, progressMonitor)
        KeyRing.container.create(progressMonitor)
    }
    App.publish(App.Message.Consistent(this, self))
  }
  /** Invoked on Core started. */
  @log
  protected def onCoreStarted() = initializationLock.synchronized {
    App.watch(KeyRing) on {
      self ! App.Message.Inconsistent(KeyRing, None)
      // Initialize lazy actors
      KeyRing.actor
      val context = thisBundle.getBundleContext()
      openContainer()
      Console ! Console.Message.Notice("KeyRing component is started.")
      self ! App.Message.Consistent(KeyRing, None)
    }
  }
  /** Invoked on Core stopped. */
  @log
  protected def onCoreStopped() = initializationLock.synchronized {
    App.watch(KeyRing) off {
      self ! App.Message.Inconsistent(KeyRing, None)
      val context = thisBundle.getBundleContext()
      closeContainer()
      val lost = inconsistentSet - KeyRing
      if (lost.nonEmpty)
        log.fatal("Inconsistent elements detected: " + lost)
      Console ! Console.Message.Notice("KeyRing component is stopped.")
    }
  }

  override def toString = "core.KeyRing"
}

object KeyRing {
  implicit def keyRing2implementation(l: KeyRing.type): Implementation = inner
  implicit def keyRing2actorRef(c: KeyRing.type): ActorRef = c.actor
  implicit def keyRing2actorSRef(c: KeyRing.type): ScalaActorRef = c.actor
  /** Logic actor reference. */
  lazy val actor = {
    val inbox = Inbox.create(App.system)
    inbox.send(Core, App.Message.Attach(props, id))
    inbox.receive(Timeout.long) match {
      case actorRef: ActorRef ⇒
        actorRef
      case other ⇒
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
    // Prevents NPE at org.eclipse.core.resources bundle stop() method
    ResourcesPlugin.getWorkspace().getRuleFactory()
    root.getProject(KeyRing.containerName)
  }

  /** Get container name. */
  def containerName = DI.infrastructureWideProjectName
  /** Get default pass phrase. */
  def defaultPassPhrase = DI.defaultPassPhrase
  /** KeyRing implementation. */
  def inner(): Implementation = DI.implementation
  /** Get public keyring resource name. */
  def publicKeyRingName = DI.publicKeyRingName
  /** Get random implementation. */
  def random = DI.random
  /** Get private keyring resource name. */
  def secretKeyRingName = DI.secretKeyRingName

  override def toString = "core.KeyRing[Singleton]"

  /**
   * Set of predefined fields for key properties.
   */
  object Attr extends Attr
  /**
   * Asymmetric cipher decryptor.
   */
  trait Decryptor extends Function1[Array[Byte], Array[Byte]]
  /**
   * Asymmetric cipher encryptor.
   */
  trait Encryptor extends Function1[Array[Byte], Array[Byte]] {
    /**
     * Return the maximum size for an input block to this engine.
     *
     * @return maximum size for an input block.
     */
    def getInputBlockSize(): Int
  }
  /**
   * Keyring implementation.
   */
  class Implementation extends KeyRingExchange with KeyRingGeneral
    with KeyRingSignature with KeyRingTransform with XLoggable
  /**
   * Signature notation. rfc2440 5.2.3.15.
   */
  case class Notation(val name: String, val value: String, val critical: Boolean)
  /**
   * Dependency injection routines
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** Default pass phrase for all keys. */
    lazy val defaultPassPhrase = injectOptional[String]("KeyRing.DefaultPassPhrase") getOrElse "TABuddyPublicPassPhrase"
    /**
     * Infrastructure wide container name that required for minimization of resources complexity.
     * It is IProject singleton label.
     */
    lazy val infrastructureWideProjectName = injectOptional[String]("KeyRing.Container") getOrElse "org.digimead.tabuddy.desktop.core.keyring"
    /** KeyRing implementation. */
    lazy val implementation = injectOptional[Implementation] getOrElse new Implementation
    /** KeyRing actor reference configuration object. */
    lazy val props = injectOptional[Props]("KeyRing") getOrElse Props[KeyRing]
    /** Public keyring resource name. */
    lazy val publicKeyRingName = injectOptional[String]("KeyRing.Container.Public") getOrElse "tabuddy.pkr"
    /** KeyRing random implementation. */
    lazy val random = injectOptional[XSecureRandom] getOrElse new SimpleRandom()
    /** Secret keyring resource name. */
    lazy val secretKeyRingName = injectOptional[String]("KeyRing.Container.Secret") getOrElse "tabuddy.skr"
  }
}
