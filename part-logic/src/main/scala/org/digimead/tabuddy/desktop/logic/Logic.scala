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

package org.digimead.tabuddy.desktop.logic

import akka.actor.{ ActorRef, Inbox, Props, ScalaActorRef, actorRef2Scala }
import java.io.File
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.Core
import org.digimead.tabuddy.desktop.core.console.Console
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.Timeout
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.core.ui.definition.widget.VComposite
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.desktop.logic.ui.WindowWatcher
import org.eclipse.core.internal.resources.ResourceException
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jface.commands.ToggleState
import scala.language.implicitConversions

/**
 * Root actor of the Logic component.
 */
class Logic extends akka.actor.Actor with XLoggable {
  /** Inconsistent elements. */
  @volatile protected var inconsistentSet = Set[AnyRef](Logic)
  /** Current bundle */
  protected lazy val thisBundle = App.bundle(getClass())
  /** Start/stop initialization lock. */
  private val initializationLock = new Object
  log.debug("Start actor " + self.path)

  /*
   * Logic component actors.
   */
  lazy val windowWatcherRef = context.actorOf(WindowWatcher.props, WindowWatcher.id)

  if (App.watch(Activator, Core, this).hooks.isEmpty)
    App.watch(Activator, Core, this).always().
      makeAfterStart('logic_Logic__onCoreStarted) { onCoreStarted() }.
      makeBeforeStop('logic_Logic__onCoreStopped) { onCoreStopped() }.sync()
  if (App.watch(Activator, UI, this).hooks.isEmpty)
    App.watch(Activator, UI, this).always().
      makeAfterStart('logic_Logic__onGUIStarted) { onGUIStarted() }.
      makeBeforeStop('logic_Logic__onGUIStopped) { onGUIStopped() }.sync()

  /** Is called asynchronously after 'actor.stop()' is invoked. */
  override def postStop() = {
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Open[_]])
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Inconsistent[_]])
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Destroy[_]])
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Consistent[_]])
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Close[_]])
    App.watch(this) off ()
    log.debug(self.path.name + " actor is stopped.")
  }
  /** Is called when an Actor is started. */
  override def preStart() {
    App.system.eventStream.subscribe(self, classOf[App.Message.Close[_]])
    App.system.eventStream.subscribe(self, classOf[App.Message.Consistent[_]])
    App.system.eventStream.subscribe(self, classOf[App.Message.Destroy[_]])
    App.system.eventStream.subscribe(self, classOf[App.Message.Inconsistent[_]])
    App.system.eventStream.subscribe(self, classOf[App.Message.Open[_]])
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
          context.system.eventStream.publish(App.Message.Consistent(Logic, self))
        }
      } else
        log.debug(s"Skip message ${message}. Logic is already consistent.")
    }

    case message @ App.Message.Inconsistent(element, from, _) if from != Some(self) &&
      App.bundle(element.getClass()) == thisBundle ⇒ App.traceMessage(message) {
      if (inconsistentSet.isEmpty) {
        log.debug("Lost consistency.")
        context.system.eventStream.publish(App.Message.Inconsistent(Logic, self))
      }
      inconsistentSet = inconsistentSet + element
    }

    case message @ App.Message.Close(marker: GraphMarker, _, _) ⇒ App.traceMessage(message) {
      behaviour.TrackActiveGraph.close(marker)
      behaviour.CloseRelatedWhenGraphIsClosed.run(marker)
    }

    case message @ App.Message.Destroy(vComposite: VComposite, _, _) ⇒ App.traceMessage(message) {
      if (vComposite.factory().features.contains(Logic.Feature.graph))
        behaviour.CloseGraphWhenLastViewIsClosed.run()
    }

    case message @ App.Message.Open(marker: GraphMarker, _, _) ⇒ App.traceMessage(message) {
      behaviour.TrackActiveGraph.open(marker)
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
    log.info(s"Close infrastructure wide container '${Logic.container.getName()}'.")
    App.publish(App.Message.Inconsistent(this, self))
    val progressMonitor = new NullProgressMonitor()
    if (!Logic.container.isOpen()) {
      //  Payload.getGraphMarker(Model).foreach(_.save)
      Logic.container.close(progressMonitor)
    }
    App.publish(App.Message.Consistent(this, self))
  }
  /** This callback is invoked when UI initialization complete */
  @log
  def onApplicationStartup() {
    // load last active model at startup
    /*Configgy.getString("payload.model").foreach(lastModelID ⇒
      Payload.listModels.find(m ⇒ m.isValid && m.id.name == lastModelID).foreach { marker ⇒
        OperationModelOpen(None, Symbol(lastModelID), true).foreach { operation ⇒
          operation.getExecuteJob() match {
            case Some(job) ⇒
              job.setPriority(Job.LONG)
              job.onComplete(_ match {
                case Operation.Result.OK(result, message) ⇒
                  log.info(s"Operation completed successfully: ${result}")
                case Operation.Result.Cancel(message) ⇒
                  log.warn(s"Operation canceled, reason: ${message}.")
                case other ⇒
                  log.error(s"Unable to complete operation: ${other}.")
              }).schedule()
              job.schedule(Timeout.shortest.toMillis)
            case None ⇒
              log.fatal(s"Unable to create job for ${operation}.")
          }
        }
      })*/
  }
  /** Open infrastructure wide container. */
  @log
  protected def openContainer() {
    log.info(s"Open infrastructure wide container '${Logic.container.getName()}' at " + Logic.container.getLocationURI())
    App.publish(App.Message.Inconsistent(this, self))
    val progressMonitor = new NullProgressMonitor()
    if (!Logic.container.exists())
      Logic.container.create(progressMonitor)
    try Logic.container.open(progressMonitor)
    catch {
      case e: ResourceException if e.getMessage.startsWith("The project description file")
        && e.getMessage.endsWith("The project will not function properly until this file is restored.") ⇒
        Logic.container.delete(true, true, progressMonitor)
        Logic.container.create(progressMonitor)
    }
    App.publish(App.Message.Consistent(this, self))
  }
  /** Invoked on Core started. */
  protected def onCoreStarted() = initializationLock.synchronized {
    App.watch(Logic) on {
      self ! App.Message.Inconsistent(Logic, None)
      // Initialize lazy actors
      Logic.actor
      if (App.isUIAvailable)
        windowWatcherRef
      val context = thisBundle.getBundleContext()
      openContainer()
      Config.start(context) // Initialize the application configuration based on Configgy
      //Transport.start() // Initialize the network transport(s)
      command.Commands.configure()
      Console ! Console.Message.Notice("Logic component is started.")
      self ! App.Message.Consistent(Logic, None)
    }
  }
  /** Invoked on Core stopped. */
  protected def onCoreStopped() = initializationLock.synchronized {
    App.watch(Logic) off {
      self ! App.Message.Inconsistent(Logic, None)
      val context = thisBundle.getBundleContext()
      command.Commands.unconfigure()
      Config.stop(context)
      closeContainer()
      val lost = inconsistentSet - Logic
      if (lost.nonEmpty)
        log.fatal("Inconsistent elements detected: " + lost)
      Console ! Console.Message.Notice("Logic component is stopped.")
    }
  }
  /** This callback is invoked when GUI is valid. */
  @log
  protected def onGUIStarted() = initializationLock.synchronized {
    ui.wizard.Wizards.configure()
    ui.view.Views.configure()
  }
  /** This callback is invoked when GUI is invalid. */
  @log
  protected def onGUIStopped() = initializationLock.synchronized {
    ui.view.Views.unconfigure()
    ui.wizard.Wizards.unconfigure()
  }

  override def toString = "logic.Logic"
}

object Logic extends XLoggable {
  implicit def logic2actorRef(c: Logic.type): ActorRef = c.actor
  implicit def logic2actorSRef(c: Logic.type): ScalaActorRef = c.actor
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
  lazy val container = try {
    val root = ResourcesPlugin.getWorkspace().getRoot()
    // Prevents NPE at org.eclipse.core.resources bundle stop() method
    ResourcesPlugin.getWorkspace().getRuleFactory()
    root.getProject(Logic.containerName)
  } catch {
    case e: Throwable ⇒
      log.error(s"Unable to get project '${Logic.containerName}'", e)
      throw e
  }
  /** Location of user scripts. */
  lazy val scriptContainer = {
    val scripts = new File(Core.root, "script")
    if (!scripts.exists())
      scripts.mkdirs()
    scripts
  }

  // Initialize descendant actor singletons
  if (App.isUIAvailable)
    WindowWatcher

  def containerName = DI.infrastructureWideProjectName
  /** Default location of user data. */
  def graphContainer = {
    val container = DI.graphContainer
    if (!container.exists)
      container.mkdirs()
    container
  }

  override def toString = "logic.Logic[Singleton]"

  /*
   * Explicit import for runtime components/bundle manifest generation.
   */
  private def explicitToggleState: ToggleState = ???

  /**
   * List of predefined identifiers that are used as feature flags.
   */
  object Feature {
    /** Flag indicating whether this view is required a graph argument. */
    final val graph = "org.digimead.tabuddy.desktop.logic/graph"
    /** Flag indicating whether this view is using 'view definitions/filters/sortings' [java.lang.Boolean]. */
    final val viewDefinition = "org.digimead.tabuddy.desktop.logic/viewDefinition"
  }
  /**
   * List of predefined identifiers that are used as context keys.
   */
  object Id {
    /** Value of the selected model element [Element]. */
    final val selectedElement = "org.digimead.tabuddy.desktop.logic/selectedElement"
    /** Value of the selected view ID [UUID]. */
    final val selectedView = "org.digimead.tabuddy.desktop.logic/selectedView"
    /** Value of the selected sorting ID [UUID]. */
    final val selectedSorting = "org.digimead.tabuddy.desktop.logic/selectedSorting"
    /** Value of the selected filter ID [UUID]. */
    final val selectedFilter = "org.digimead.tabuddy.desktop.logic/selectedFilter"
  }
  /**
   * Dependency injection routines
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** Logic actor reference configuration object. */
    lazy val props = injectOptional[Props]("Logic") getOrElse Props[Logic]
    /**
     * Infrastructure wide container name that required for minimization of resources complexity.
     * It is IProject singleton label.
     */
    lazy val infrastructureWideProjectName = injectOptional[String]("Logic.Container") getOrElse "TABuddyLogic"
    /** Default location of user data. */
    lazy val graphContainer = injectOptional[File]("Graph.Location") getOrElse new File(App.data, "graph")
  }
}
