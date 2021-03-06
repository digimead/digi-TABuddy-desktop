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

package org.digimead.tabuddy.desktop.core.ui

import akka.actor.{ ActorRef, Inbox, Props, ScalaActorRef }
import java.io.File
import java.util.concurrent.{ CountDownLatch, TimeUnit }
import javax.swing.UIManager
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.jfx4swt.JFX
import org.digimead.digi.lib.jfx4swt.JFXApplication
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.Core
import org.digimead.tabuddy.desktop.core.console.Console
import org.digimead.tabuddy.desktop.core.support.{ App, Timeout }
import org.digimead.tabuddy.desktop.core.ui.block.{ View, Window, WindowSupervisor }
import org.eclipse.core.runtime.Platform
import org.eclipse.jface.commands.ToggleState
import org.eclipse.swt.widgets.Canvas
import scala.concurrent.Future
import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.language.implicitConversions

/*
 * ERR 01
 *
 * !Danger! Native deadlock under GTK with 100% CPU usage
 * "Application event loop" daemon prio=10 tid=0x00007f26284d6000 nid=0x2e1d runnable [0x00007f267e69a000]
 * java.lang.Thread.State: RUNNABLE
 *      at org.eclipse.swt.internal.gtk.OS._gtk_main_do_event(Native Method)
 *      at org.eclipse.swt.internal.gtk.OS.gtk_main_do_event(OS.java:8742)
 *      at org.eclipse.swt.widgets.Display.eventProc(Display.java:1243)
 *      at org.eclipse.swt.internal.gtk.OS._g_main_context_iteration(Native Method)
 *      at org.eclipse.swt.internal.gtk.OS.g_main_context_iteration(OS.java:2288)
 *      at org.eclipse.swt.widgets.Display.readAndDispatch(Display.java:3361)
 *      at org.digimead.tabuddy.desktop.core.EventLoop.loop(EventLoop.scala:172)
 *      at org.digimead.tabuddy.desktop.core.EventLoop$$anon$1.run(EventLoop.scala:104)
 *      at org.eclipse.core.databinding.observable.Realm.runWithDefault(Realm.java:332)
 *      at org.digimead.tabuddy.desktop.core.EventLoop.run(EventLoop.scala:104)
 */

/**
 * Root actor of the UI component.
 */
class UI extends akka.actor.Actor with XLoggable {
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** Inconsistent elements. */
  @volatile protected var inconsistentSet = Set[AnyRef](UI)
  /** JavaFX initialization helper. */
  // I suspect that group of developers that wrote FXCanvas must be punished in extremely violent form :-/
  // Those code monkeys from the Oracle corporation wrote really ugly shit.
  // One more reason for closed source at a big company...
  //protected lazy val javaFXInitializationHelper = new javafx.embed.swt.FXCanvas(new Shell(), SWT.None)
  /** Current bundle */
  protected lazy val thisBundle = App.bundle(getClass())
  /** Start/stop initialization lock. */
  private val initializationLock = new Object
  log.debug("Start actor " + self.path)

  /*
   * UI component actors.
   */
  lazy val windowWatcherRef = context.actorOf(WindowWatcher.props, WindowWatcher.id)
  lazy val windowSupervisorRef = context.actorOf(WindowSupervisor.props, WindowSupervisor.id)

  if (App.watch(Activator, Core, this).hooks.isEmpty)
    App.watch(Activator, Core, this).always().
      makeAfterStart('core_ui_UI__onCoreStarted) { onCoreStarted() }.
      makeBeforeStop('core_ui_UI__onCoreStopped) { onCoreStopped() }.sync()

  /** Is called asynchronously after 'actor.stop()' is invoked. */
  override def postStop() = {
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Consistent[_]])
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Inconsistent[_]])
    App.watch(this) off ()
    log.debug(self.path.name + " actor is stopped.")
  }
  /** Is called when an Actor is started. */
  override def preStart() {
    App.system.eventStream.subscribe(self, classOf[App.Message.Inconsistent[_]])
    App.system.eventStream.subscribe(self, classOf[App.Message.Consistent[_]])
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
          //message.source.printStackTrace()
          context.system.eventStream.publish(App.Message.Consistent(UI, self))
        }
      } else
        log.debug(s"Skip message ${message}. UI is already consistent.")
    }

    case message @ App.Message.Inconsistent(element, from, _) if from != Some(self) &&
      App.bundle(element.getClass()) == thisBundle ⇒ App.traceMessage(message) {
      if (inconsistentSet.isEmpty) {
        log.debug("Lost consistency.")
        context.system.eventStream.publish(App.Message.Inconsistent(UI, self))
      }
      inconsistentSet = inconsistentSet + element
    }

    case message @ App.Message.Consistent(_, _, _) ⇒ // skip
    case message @ App.Message.Inconsistent(_, _, _) ⇒ // skip
  }

  /** Invoked on Core started. */
  protected def onCoreStarted() = initializationLock.synchronized {
    App.watch(UI) on {
      self ! App.Message.Inconsistent(UI, None)
      // Initialize lazy actors.
      UI.actor
      windowWatcherRef
      windowSupervisorRef
      // Initialize Java FX.
      val fxStartLatch = new CountDownLatch(1)
      JFX.start(new Runnable { def run = fxStartLatch.countDown() })
      fxStartLatch.await(Timeout.short.toMillis, TimeUnit.MILLISECONDS)
      // Prevent deadlock SWT<->JFX<->App with screen initialization.
      log.debug(s"Java FX environment ready. Actual screen: ${JFXApplication.virtualScreen}.")
      // Start event in separated thread since watcher is synchronous
      // and watcher hook may depends on event loop
      // Eclipse Bug 341799 workaround
      try if (Platform.WS_GTK.equals(Platform.getWS()))
        UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel")
      else
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
      catch { case e: Throwable ⇒ log.error(e.getMessage(), e) }
      App.execNGet { Resources.start(App.bundle(getClass).getBundleContext()) }(App.LongRunnable)
      App.execNGet { (new InfrastructureFixes).fix() }(App.LongRunnable)
      view.Views.configure()
      command.Commands.configure()
      WindowSupervisor ! App.Message.Restore(None, None)
      Console ! Console.Message.Notice("UI component is started.")
      self ! App.Message.Consistent(UI, None)
    }
  }
  /** Invoked on Core stopped. */
  protected def onCoreStopped() = initializationLock.synchronized {
    App.watch(UI) off {
      self ! App.Message.Inconsistent(UI, None)
      command.Commands.unconfigure()
      view.Views.unconfigure()
      val lost = inconsistentSet - UI
      if (lost.nonEmpty)
        log.fatal("Inconsistent elements detected: " + lost)
      Console ! Console.Message.Notice("UI component is stopped.")
      val fxStopLatch = new CountDownLatch(1)
      // Java FX platform restart routine is absent. :-) Say hello to Java FX designers.
      JFX.stop(new Runnable { def run = fxStopLatch.countDown() }, App.isDevelopmentMode)
      fxStopLatch.await(Timeout.short.toMillis, TimeUnit.MILLISECONDS)
    }
    Future {
      val display = App.display
      while (!display.isDisposed())
        Thread.sleep(100)
      Resources.validateOnShutdown()
      Resources.stop(App.bundle(getClass).getBundleContext())
    } onFailure { case e: Throwable ⇒ log.error(e.getMessage(), e) }
  }

  override def toString = "core.ui.UI"
}

object UI extends support.Generic with Window.WindowMapConsumer with View.ViewMapConsumer with XLoggable {
  implicit def ui2actorRef(u: UI.type): ActorRef = u.actor
  implicit def ui2actorSRef(u: UI.type): ScalaActorRef = u.actor
  /** UI actor reference. */
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
  /** UI actor path. */
  lazy val actorPath = Core.path / id
  /** UI container. */
  lazy val container = {
    val container = new File(DI.location.getParentFile(), "org.digimead.tabuddy.desktop.core.ui")
    if (!container.exists())
      container.mkdirs()
    container
  }
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  /** UI actor reference configuration object. */
  lazy val props = DI.props
  /** SWT Data ID key */
  val swtId = getClass.getName() + "#ID"
  // Initialize descendant actor singletons
  Core
  WindowSupervisor

  /** Close window when last view is closed. */
  def closeWindowWithLastView = DI.closeWindowWithLastView
  /** Communication timeout for rapid requests with Akka 'await' or similar pattern. */
  def communicationTimeout = DI.communicationTimeout
  /** Widget default(minimum) height. */
  def DEFAULT_HEIGHT = DI.defaultHeight
  /** Widget default(minimum) width. */
  def DEFAULT_WIDTH = DI.defaultWidth
  /** UI focus timeout for start/stop event. */
  def focusTimeout = DI.focusTimeout
  /** Stop event loop when last window is closed. */
  def stopEventLoopWithLastWindow = DI.stopEventLoopWithLastWindow
  /** Sets the shape that the CTabFolder will use to render itself. */
  def tabFolderSimple = DI.tabFolderSimple

  override def toString = "core.ui.UI[Singleton]"

  /*
   * Explicit import of runtime components.
   * Provides information for bundle manifest generation.
   */
  private def explicitToggleState: ToggleState = ???
  private def explicitCanvas: Canvas = ???

  /**
   * List of predefined identifiers that are used as context keys.
   */
  object Id {
    /** Value with view title [String]. */
    final val contentTitle = "org.digimead.tabuddy.desktop.core.ui/contentTitle"
    /** Value of the available shell list [Seq[Shell]. */
    final val shellList = "org.digimead.tabuddy.desktop.core.ui/shellList"
    /** Value with view title [String]. */
    final val viewTitle = "org.digimead.tabuddy.desktop.core.ui/viewTitle"
    /** Value with window title [String]. */
    final val windowTitle = "org.digimead.tabuddy.desktop.core.ui/windowTitle"
  }
  /**
   * Dependency injection routines
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** Close window when last view is closed. */
    lazy val closeWindowWithLastView = injectOptional[Boolean]("Core.UI.closeWindowWithLastView") getOrElse true
    /** Communication timeout for rapid requests with Akka 'await' or similar pattern. */
    lazy val communicationTimeout = injectOptional[FiniteDuration]("Core.UI.communicationTimeout") getOrElse Timeout.short
    /** Widget default(minimum) height. */
    lazy val defaultHeight = injectOptional[Int]("Core.UI.defaultHeight") getOrElse 64
    /** Widget default(minimum) width. */
    lazy val defaultWidth = injectOptional[Int]("Core.UI.defaultWidth") getOrElse 64
    /** UI focus timeout for start/stop event. */
    lazy val focusTimeout = injectOptional[FiniteDuration]("Core.UI.focusTimeout") getOrElse Duration(1000, TimeUnit.MILLISECONDS)
    /** Application's configuration file. */
    val location = inject[File]("Config")
    /** UI actor reference configuration object. */
    lazy val props = injectOptional[Props]("Core.UI") getOrElse Props[UI]
    /** Stop event loop when last window is closed. */
    lazy val stopEventLoopWithLastWindow = injectOptional[Boolean]("Core.UI.stopEventLoopWithLastWindow") getOrElse true
    /** Sets the shape that the CTabFolder will use to render itself. */
    lazy val tabFolderSimple = injectOptional[Boolean]("Core.UI.tabFolderSimple") getOrElse false
  }
}
