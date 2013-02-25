/**
 * This file is part of the TABuddy project.
 * Copyright (c) 2012-2013 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop

import java.net.URL
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import org.digimead.configgy.Configgy
import org.digimead.configgy.Configgy.getImplementation
import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.tabuddy.desktop.Config.config2implementation
import org.digimead.tabuddy.desktop.approver.Approver
import org.digimead.tabuddy.desktop.approver.Approver.approver2implementation
import org.digimead.tabuddy.desktop.debug.Debug
import org.digimead.tabuddy.desktop.job.Job
import org.digimead.tabuddy.desktop.job.Job.job2implementation
import org.digimead.tabuddy.desktop.job.JobModelAcquire
import org.digimead.tabuddy.desktop.mesh.transport.Transport
import org.digimead.tabuddy.desktop.mesh.transport.Transport.transport2implementation
import org.digimead.tabuddy.desktop.payload.ElementTemplate
import org.digimead.tabuddy.desktop.payload.Payload
import org.digimead.tabuddy.desktop.payload.Payload.payload2implementation
import org.digimead.tabuddy.desktop.payload.TypeSchema
import org.digimead.tabuddy.desktop.ui.Window
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Model.model2implementation
import org.digimead.tabuddy.model.element.Element
import org.eclipse.core.databinding.DataBindingContext
import org.eclipse.core.databinding.observable.Realm
import org.eclipse.jface.databinding.swt.SWTObservables
import org.eclipse.jface.util.Policy
import org.eclipse.swt.widgets.Display

import akka.actor.ActorSystem

object Main extends App with Loggable {
  private val elementEventsSubscriber = new Element.Event.Sub {
    def notify(pub: Element.Event.Pub, event: Element.Event) = event match {
      case Element.Event.ModelReplace(oldModel, newModel, modified) =>
        onModelInitialization(oldModel, newModel, modified)
      case _ =>
    }
  }
  /** global exception handler */
  lazy val uncaughtExceptionHandler = new ExceptionHandler()
  /** The application-wide actor system */
  val system = ActorSystem("DesktopBuddySystem")
  /** The debug class that allow control application over JMX */
  val debug = new Debug
  /** The default display, available from ui.Window */
  lazy val display = Display.getDefault()
  /** The realm representing the UI thread for the given display */
  lazy val realm = SWTObservables.getRealm(display)
  /** The global application data binding context */
  lazy val bindingContext = new DataBindingContext(realm)
  /** The ui thread */
  lazy val thread = Thread.currentThread()
  // A flag to indicate whether actions are being traced.
  Policy.TRACE_ACTIONS = true
  // A flag to indicate whether toolbars are being traced.
  Policy.TRACE_TOOLBAR = true
  // Custom settings
  System.setProperty("sun.java2d.opengl", "True")

  DependencyInjection.set(default)
  uncaughtExceptionHandler.register()
  Realm.runWithDefault(realm, new Runnable() {
    def run() {
      // An early initialization, as soon as possible
      Main.exec { thread }
      // normal sequence
      start()
      showGUI()
      stop()
    }
  })

  /** Verify the current thread against the UI one */
  def checkThread() = if (!Main.thread.eq(Thread.currentThread())) {
    val throwable = new IllegalAccessException("Only the original thread that created a UI can touch its views and use observables.")
    // sometimes we throw exception in threads that haven't catch block, notify anyway
    log.error("Only the original thread that created a UI can touch its views and use observables.", throwable)
    throw throwable
  }
  /** Asynchronously execute runnable in UI thread */
  def exec[T](f: => T): Unit = display.asyncExec(new Runnable { def run = f })
  /** Asynchronously execute runnable in UI thread and return result or exception */
  def execNGet[T](f: => T): T = {
    if (Main.thread.eq(Thread.currentThread()))
      return f
    val result = new AtomicReference[Option[Either[Throwable, T]]](None)
    display.asyncExec(new Runnable {
      def run = result.synchronized {
        try {
          result.set(Some(Right(f)))
          result.notifyAll()
        } catch {
          case e: Throwable =>
            result.set(Some(Left(e)))
            result.notifyAll()
        }
      }
    })
    while (result.get.isEmpty)
      result.synchronized { result.wait() }
    result.get.get match {
      case Left(e) =>
        throw new ExecutionException(e)
      case Right(r) =>
        r
    }
  }
  /**
   * Asynchronously execute runnable in UI thread with timeout and return result or exception
   * NB This routine block UI thread, so it would unusual to freeze application for a few hours.
   */
  def execNGet[T](timeout: Int, unit: TimeUnit = TimeUnit.MILLISECONDS)(f: => T): T = {
    val mark = System.currentTimeMillis() + unit.toMillis(timeout)
    val result = new AtomicReference[Option[Either[Throwable, T]]](None)
    display.asyncExec(new Runnable {
      def run = result.synchronized {
        try {
          result.set(Some(Right(f)))
          result.notifyAll()
        } catch {
          case e: Throwable =>
            result.set(Some(Left(e)))
            result.notifyAll()
        }
      }
    })
    while (result.get.isEmpty && System.currentTimeMillis() < mark)
      result.synchronized { result.wait(mark - System.currentTimeMillis()) }
    result.get.get match {
      case Left(e) =>
        throw new ExecutionException(e)
      case Right(r) =>
        r
    }
  }
  /** This callback is invoked when UI initialization complete */
  def onApplicationStartup() {
    // load last active model at startup
    Configgy.getString("payload.model").foreach(lastModelID =>
      if (Payload.listModels.exists(_.name == lastModelID)) {
        exec { Data.fieldModelName.value = lastModelID }
        JobModelAcquire(None, Symbol(lastModelID)).foreach(_.execute)
      })
  }
  /** This callback is invoked at an every model initialization */
  def onModelInitialization(oldModel: Model.Generic, newModel: Model.Generic, modified: Element.Timestamp) {
    TypeSchema.onModelInitialization(oldModel, newModel, modified)
    ElementTemplate.onModelInitialization(oldModel, newModel, modified)
    Data.onModelInitialization(oldModel, newModel, modified)
  }
  def showGUI() = try {
    log.info("show GUI")
    val window = new Window()
    window.setBlockOnOpen(true)
    window.open()
    Display.getCurrent().dispose()
  } catch {
    case e: Exception =>
      log.error(e.getMessage, e)
  }
  /** This function is invoked at the application start */
  def start() {
    log.info("start application")
    Element.Event.subscribe(elementEventsSubscriber)
    onModelInitialization(Model, Model, Model.eModified)
    // startup sequence
    // Initialize the system resources, such as images, fonts, etc...
    Resources.start()
    // Initialize the application configuration based on Configgy
    Config.start()
    // Initialize the application data such as global variables, variable lists, predefined elements, etc...
    Data.start()
    // Initialize the user data, data storage, etc...
    Payload.start()
    // Initialize the network transport(s)
    Transport.start()
    // Initialize the job handler
    Job.start()
    // Initialize the job approver
    Approver.start()
  }
  /** This function is invoked at the application stop */
  def stop() {
    log.info("stop application")
    Element.Event.removeSubscription(elementEventsSubscriber)
    Approver.stop()
    Job.stop()
    Transport.stop()
    Payload.stop()
    Data.stop()
    Config.stop()
    Resources.stop()
    system.shutdown()
  }
  def findJarPath(): URL = {
    try {
      val source = this.getClass.getProtectionDomain.getCodeSource
      if (source != null)
        return source.getLocation
    } catch {
      // catch all possible throwables
      case e: Throwable =>
    }
    null
  }

  trait Interface extends Loggable {
    /** A flag that sets at application start */
    @volatile var active = false
    /**
     * This function is invoked at application start
     */
    def start()
    /**
     * This function is invoked at application stop
     */
    def stop()
  }
}
