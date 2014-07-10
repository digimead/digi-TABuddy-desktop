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

package org.digimead.tabuddy.desktop.core

import akka.actor.{ Inbox, PoisonPill, Terminated }
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.digi.lib.{ DependencyInjection, Disposable }
import org.digimead.tabuddy.desktop.core.api.XTranslation
import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.Timeout
import org.osgi.framework.{ BundleActivator, BundleContext, BundleEvent, BundleListener }
import org.osgi.util.tracker.ServiceTracker
import scala.concurrent.Future
import scala.ref.WeakReference

/**
 * OSGi entry point.
 */
class Activator extends BundleActivator with definition.NLS.Initializer with EventLoop.Initializer with XLoggable {
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  @volatile protected var reportServiceTracker: Option[ServiceTracker[AnyRef, AnyRef]] = None
  @volatile protected var translationServiceTracker: Option[ServiceTracker[XTranslation, XTranslation]] = None

  /** Start bundle. */
  def start(context: BundleContext) = Activator.startStopLock.synchronized {
    if (Option(Activator.disposable).isEmpty)
      throw new IllegalStateException("Bundle is already disposed. Please reinstall it before activation.")
    log.debug("Start TA Buddy Desktop core.")
    // Subscribe for report service
    val reportServiceTracker = new ServiceTracker(context, "org.digimead.digi.launcher.report.api.XReport", Report)
    reportServiceTracker.open()
    this.reportServiceTracker = Some(reportServiceTracker)
    // Subscribe for translation service
    val translationServiceTracker = new ServiceTracker[XTranslation, XTranslation](context, classOf[XTranslation], null)
    translationServiceTracker.open()
    this.translationServiceTracker = Some(translationServiceTracker)
    // Setup DI for this bundle
    val diReady = Option(context.getServiceReference(classOf[org.digimead.digi.lib.api.XDependencyInjection])).
      map { currencyServiceRef ⇒ (currencyServiceRef, context.getService(currencyServiceRef)) } match {
        case Some((reference, diService)) ⇒
          diService.getDependencyValidator.foreach { validator ⇒
            val invalid = DependencyInjection.validate(validator, this)
            if (invalid.nonEmpty)
              throw new IllegalArgumentException("Illegal DI keys found: " + invalid.mkString(","))
          }
          context.ungetService(reference)
          Some(diService.getDependencyInjection())
        case None ⇒
          log.warn("DI service not found.")
          None
      }
    diReady match {
      case Some(di) ⇒
        DependencyInjection.reset()
        DependencyInjection(di, false)
        // Start UI thread
        EventLoop.thread.start()
        eventLoopThreadSync()
      case None ⇒
        log.warn("Skip DI initialization and event loop creation in test environment.")
    }
    Future {
      Activator.startStopLock.synchronized {
        App.watch(Activator).once.makeBeforeStop {
          // This hook is hold Activator.stop() while initialization is incomplete.
          App.watch(context).waitForStart(Timeout.normal) // at Core.main()
          // Initialization complete.
          App.watch(context).off()
        } on {
          // Initialize translation service
          setTranslationServiceTracker(this.translationServiceTracker)
          // Start component actors hierarchy
          Core.actor
          /*
           * Start global components that haven't dispose methods.
           */
          Command
          // Start application environment
          Core ! context
          /*
         * Huge operations that freeze application.
         */
          // Initialize messages
          Future { Messages.default_text } onFailure { case e: Throwable ⇒ log.error(e.getMessage(), e) }
          // Initialize UI flag
          Future { App.isUIAvailable } onFailure { case e: Throwable ⇒ log.error(e.getMessage(), e) }
        }
      }
    } onFailure {
      case e: Throwable ⇒ log.error("Error while starting Core: " + e.getMessage(), e)
    }
  }
  /** Stop bundle. */
  def stop(context: BundleContext) = Activator.startStopLock.synchronized {
    log.debug("Stop TA Buddy Desktop core.")
    Core ! App.Message.Inconsistent(Core, None)
    App.watch(Activator) off {
      // Shutdown event loop.
      if (EventLoop.thread != null && EventLoop.thread.exitCode.isEmpty) {
        log.debug("Initiate event loop shutdown. Bundle is stopping.")
        // Unexpected shutdown.
        EventLoop.thread.stopEventLoop(EventLoop.Code.Error)
      }
    }
    // Stop component actors.
    val inbox = Inbox.create(App.system)
    inbox.watch(Core)
    Core ! PoisonPill
    if (inbox.receive(Timeout.long).isInstanceOf[Terminated])
      log.debug("Core actors hierarchy is terminated.")
    else
      log.fatal("Unable to shutdown Core actors hierarchy.")
    if (EventLoop.thread != null && EventLoop.thread.isInitialized) {
      val display = App.display
      // There are no actors. So dispose it for sure.
      App.exec { display.dispose() }
      // Waiting for event loop termination.
      // 'code' should be null if main service isn't invoked
      EventLoop.thread.waitWhile(code ⇒ (code != null && code.isEmpty))
      eventLoopThreadSync()
    }
    Core.context.cleanup()
    Core.context.dispose()
    App.watchResetList()
    setTranslationServiceTracker(None)
    translationServiceTracker.foreach(_.close())
    translationServiceTracker = None
    reportServiceTracker.foreach(_.close())
    reportServiceTracker = None
    Activator.dispose()
  }

  override def toString = "core.Activator"
}

/**
 * Disposable manager. There is always only one singleton per class loader.
 */
object Activator extends Disposable.Manager with XLoggable {
  @volatile private var disposable = Seq[WeakReference[Disposable]]()
  private val disposableLock = new Object
  private val startStopLock = new Object

  /** Register the disposable instance. */
  def register(disposable: Disposable) = disposableLock.synchronized {
    this.disposable = this.disposable :+ WeakReference(disposable)
  }
  /** Dispose all registered instances. */
  protected def dispose() = disposableLock.synchronized {
    log.debug(s"Dispose ${disposable.size} instance(s).")
    disposable.reverse.foreach(_.get.foreach { disposable ⇒
      callDispose(disposable)
    })
    disposable = null
  }

  override def toString = "core.Activator[Singleton]"
}
