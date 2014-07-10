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

import akka.actor.{ Inbox, PoisonPill, Terminated }
import com.escalatesoft.subcut.inject.BindingException
import org.digimead.digi.lib.{ DependencyInjection, Disposable }
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.jfx4swt.JFXPlatformFactory
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.Timeout
import org.osgi.framework.{ BundleActivator, BundleContext }
import scala.concurrent.Future
import scala.ref.WeakReference

/**
 * OSGi entry point.
 */
class Activator extends BundleActivator with XLoggable {
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher

  /** Start bundle. */
  def start(context: BundleContext) = Activator.startStopLock.synchronized {
    if (Option(Activator.disposable).isEmpty)
      throw new IllegalStateException("Bundle is already disposed. Please reinstall it before activation.")
    log.debug("Start TABuddy Desktop UI interface.")
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
      case None ⇒
        log.warn("Skip DI initialization in test environment.")
    }
    try JFXPlatformFactory.application catch {
      case e: BindingException ⇒
        throw new IllegalStateException("JavaFX bridge is not available. JFXApplication DI binging is absent.")
    }
    // Mark UI as available; see App.isUIAvailable
    App.watch(App.UIFlag) on ()
    // Start component actors hierarchy
    val f = Future {
      Activator.startStopLock.synchronized {
        App.watch(Activator).once.makeBeforeStop {
          // This hook is hold Activator.stop() while initialization is incomplete.
          App.watch(context).waitForStart(Timeout.normal)
          // Initialization complete.
          App.watch(context).off()
        } on { UI.actor }
      }
    }
    f onFailure { case e: Throwable ⇒ log.error("Error while starting UI: " + e.getMessage(), e) }
    f onComplete { case _ ⇒ App.watch(context).on() }
    // Prevents stop Core bundle before this one.
    App.watch(core.Activator).once.makeBeforeStop {
      if (!App.isDevelopmentMode)
        App.watch(Activator).waitForStop(Timeout.short)
    }
  }
  /** Stop bundle. */
  def stop(context: BundleContext) = Activator.startStopLock.synchronized {
    log.debug("Stop TABuddy Desktop UI interface.")
    UI ! App.Message.Inconsistent(UI, None)
    // Mark UI as unavailable; see App.isUIAvailable
    App.watch(App.UIFlag) off ()
    App.watch(Activator) off {}
    try {
      // Stop component actors.
      val inbox = Inbox.create(App.system)
      inbox.watch(UI)
      UI ! PoisonPill
      if (inbox.receive(Timeout.long).isInstanceOf[Terminated])
        log.debug("UI actors hierarchy is terminated.")
      else
        log.fatal("Unable to shutdown UI actors hierarchy.")
    } catch {
      case e if App.system == null ⇒
        log.debug("Skip Akka cleanup: ecosystem is already shut down.")
    }
    Activator.dispose()
  }

  override def toString = "core.ui.Activator"
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

  override def toString = "core.ui.Activator[Singleton]"
}
