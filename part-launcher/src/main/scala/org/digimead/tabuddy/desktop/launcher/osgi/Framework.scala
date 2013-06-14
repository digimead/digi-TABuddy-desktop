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

package org.digimead.tabuddy.desktop.launcher.osgi

import java.util.concurrent.atomic.AtomicBoolean

import org.digimead.digi.lib.log.api.Loggable
import org.eclipse.osgi.framework.adaptor.FrameworkAdaptor
import org.eclipse.osgi.framework.internal.core.FrameworkProperties
import org.osgi.framework.Bundle
import org.osgi.framework.BundleEvent
import org.osgi.framework.BundleListener
import org.osgi.framework.SynchronousBundleListener

/*
 * Fucking EclipseStarter designers. What a reason to hide the FINAL immutable variable like REFERENCE_PROTOCOL or REFERENCE_SCHEME? Hide shit?
 * Very wise, stupid assholes :-/ Fucking secrets... Junkies. And then they discuss about bugs, poor tests coverage and complex architecture.
 * Look for Apache Felix source code at contrast.
 * But I must admit that there are really wise people in Eclipse infrastructure, especially in SWT team.
 *   Ezh.
 */

class Framework(val frameworkAdaptor: FrameworkAdaptor)
  extends org.eclipse.osgi.framework.internal.core.Framework(frameworkAdaptor) {
  /**
   * Register a framework shutdown handler. <p>
   * A handler implements the {@link Runnable} interface.  When the framework is shutdown
   * the {@link Runnable#run()} method is called for each registered handler.  Handlers should
   * make no assumptions on the thread it is being called from.  If a handler object is
   * registered multiple times it will be called once for each registration.
   * <p>
   * At the time a handler is called the framework is shutdown.  Handlers must not depend on
   * a running framework to execute or attempt to load additional classes from bundles
   * installed in the framework.
   * @param handler the framework shutdown handler
   */
  def registerShutdownHandlers(shutdownHandler: Runnable): Unit =
    registerShutdownHandlers(Seq(shutdownHandler))
  /**
   * Register a framework shutdown handlers. <p>
   * A handler implements the {@link Runnable} interface.  When the framework is shutdown
   * the {@link Runnable#run()} method is called for each registered handler.  Handlers should
   * make no assumptions on the thread it is being called from.  If a handler object is
   * registered multiple times it will be called once for each registration.
   * <p>
   * At the time a handler is called the framework is shutdown.  Handlers must not depend on
   * a running framework to execute or attempt to load additional classes from bundles
   * installed in the framework.
   * @param handler the framework shutdown handler
   */
  def registerShutdownHandlers(shutdownHandlers: Seq[Runnable]): Seq[BundleListener] =
    for (handler <- shutdownHandlers) yield {
      val listener = new SynchronousBundleListener() {
        val processed = new AtomicBoolean(false)
        def bundleChanged(event: BundleEvent) {
          if (event.getBundle() == systemBundle && event.getType() == BundleEvent.STOPPED)
            if (processed.compareAndSet(false, true))
              new Thread(handler).start()
        }
      }
      getSystemBundleContext().addBundleListener(listener)
      listener
    }
  /**
   * Used by ServiceReferenceImpl for isAssignableTo
   * @param registrant Bundle registering service
   * @param client Bundle desiring to use service
   * @param className class name to use
   * @param serviceClass class of original service object
   * @return true if assignable given package wiring
   */
  override def isServiceAssignableTo(registrant: Bundle, client: Bundle, className: String, serviceClass: Class[_]): Boolean = {
    if (super.isServiceAssignableTo(registrant, client, className, serviceClass))
      return true
    // If service is registered in system bundle
    // And system bundle may load this class with FWK loader
    // Then client may load it too. :-)
    if (registrant.getBundleId() == 0)
      try { registrant.loadClass(className); true } catch { case e: ClassNotFoundException => false }
    else
      false
  }
}

object Framework extends Loggable {
  val FILE_SCHEME = "file:"
  val INITIAL_LOCATION = "initial@"
  val PROP_ALLOW_APPRELAUNCH = "eclipse.allowAppRelaunch"
  val PROP_APPLICATION_LAUNCHDEFAULT = "eclipse.application.launchDefault"
  val PROP_FORCED_RESTART = "osgi.forcedRestart"
  val PROP_LAUNCHER = "eclipse.launcher"
  val PROP_NL_EXTENSIONS = "osgi.nl.extensions"
  val REFERENCE_PROTOCOL = "reference"
  val REFERENCE_SCHEME = "reference:"

  def isForcedRestart(): Boolean =
    Option(FrameworkProperties.getProperty(PROP_FORCED_RESTART)).map(value => try {
      value.toBoolean
    } catch {
      case e: Throwable =>
        log.error("Invalid 'osgi.forcedRestart' value: " + value)
        false
    }) getOrElse true
}
