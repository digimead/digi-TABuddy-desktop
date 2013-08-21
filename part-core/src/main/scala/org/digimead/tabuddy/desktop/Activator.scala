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

package org.digimead.tabuddy.desktop

import java.util.concurrent.atomic.AtomicReference

import scala.ref.WeakReference

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.Disposable
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Core.core2actorRef
import org.digimead.tabuddy.desktop.Core.core2actorSRef
import org.digimead.tabuddy.desktop.MainService.main2implementation
import org.digimead.tabuddy.desktop.Report.report2implementation
import org.digimead.tabuddy.desktop.api.Main
import org.digimead.tabuddy.desktop.api.Translation
import org.digimead.tabuddy.desktop.command.Command
import org.digimead.tabuddy.desktop.gui.GUI.gui2implementation
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.Timeout
import org.eclipse.core.databinding.DataBindingContext
import org.eclipse.core.databinding.observable.Realm
import org.eclipse.jface.databinding.swt.SWTObservables
import org.eclipse.swt.widgets.Display
import org.eclipse.ui.PlatformUI
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceRegistration
import org.osgi.util.tracker.ServiceTracker

import akka.actor.Inbox
import akka.actor.PoisonPill
import akka.actor.Terminated

/**
 * OSGi entry point.
 */
class Activator extends BundleActivator with definition.NLS.Initializer with UIThread.Initializer with Loggable {
  @volatile protected var mainRegistration: Option[ServiceRegistration[api.Main]] = None
  @volatile protected var reportServiceTracker: Option[ServiceTracker[AnyRef, AnyRef]] = None
  @volatile protected var translationServiceTracker: Option[ServiceTracker[api.Translation, api.Translation]] = None

  /** Start bundle. */
  def start(context: BundleContext) = Activator.startStopLock.synchronized {
    if (Option(Activator.disposable).isEmpty)
      throw new IllegalStateException("Bundle is already disposed. Please reinstall it before activation.")
    log.debug("Start TABuddy Desktop core.")
    // Subscribe for report service
    val reportServiceTracker = new ServiceTracker(context, "org.digimead.digi.launcher.report.api.Report", Report)
    reportServiceTracker.open()
    this.reportServiceTracker = Some(reportServiceTracker)
    // Subscribe for translation service
    val translationServiceTracker = new ServiceTracker[api.Translation, api.Translation](context, classOf[api.Translation], null)
    translationServiceTracker.open()
    this.translationServiceTracker = Some(translationServiceTracker)
    // Setup DI for this bundle
    Option(context.getServiceReference(classOf[org.digimead.digi.lib.api.DependencyInjection])).
      map { currencyServiceRef => (currencyServiceRef, context.getService(currencyServiceRef)) } match {
        case Some((reference, diService)) =>
          diService.getDependencyValidator.foreach { validator =>
            val invalid = DependencyInjection.validate(validator, this)
            if (invalid.nonEmpty)
              throw new IllegalArgumentException("Illegal DI keys found: " + invalid.mkString(","))
          }
          context.ungetService(reference)
        case None =>
          log.warn("DI service not found.")
      }
    DependencyInjection.inject()
    // Start UI thread
    UIThread.thread.start()
    uiThreadStartSync()
    // Initialize translation service
    setTranslationServiceTracker(this.translationServiceTracker)
    // Start "main" service
    mainRegistration = Option(context.registerService(classOf[api.Main], MainService, null))
    mainRegistration match {
      case Some(service) => log.debug("Register TABuddy Desktop application entry point service as: " + service)
      case None => log.error("Unable to register TABuddy Desktop application entry point service.")
    }
    // Start component actors hierarchy
    Core.actor
    // Start global components that haven't dispose methods.
    Command
    System.out.println("Core component is started.")
  }
  /** Stop bundle. */
  def stop(context: BundleContext) = Activator.startStopLock.synchronized {
    log.debug("Stop TABuddy Desktop core.")
    // Stop component actors.
    val inbox = Inbox.create(App.system)
    inbox.watch(Core)
    Core ! PoisonPill
    if (inbox.receive(Timeout.long).isInstanceOf[Terminated])
      log.debug("Core actors hierarchy is terminated.")
    else
      log.fatal("Unable to shutdown Core actors hierarchy.")
    val display = App.display
    // There are no actors. So dispose it for sure.
    App.exec { display.dispose() }
    // Stop "main" service.
    mainRegistration.foreach { serviceRegistration =>
      log.debug("Unregister TABuddy Desktop application entry point service.")
      serviceRegistration.unregister()
    }
    mainRegistration = None
    setTranslationServiceTracker(None)
    translationServiceTracker.foreach(_.close())
    translationServiceTracker = None
    reportServiceTracker.foreach(_.close())
    reportServiceTracker = None
    Activator.dispose()
    System.out.println("Core component is stopped.")
  }
}

/**
 * Disposable manager. There is always only one singleton per class loader.
 */
object Activator extends Disposable.Manager with Loggable {
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
    disposable.reverse.foreach(_.get.foreach { disposable =>
      callDispose(disposable)
    })
    disposable = null
  }
}
