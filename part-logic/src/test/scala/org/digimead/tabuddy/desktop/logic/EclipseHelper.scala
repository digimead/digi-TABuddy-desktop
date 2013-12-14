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

package org.digimead.tabuddy.desktop.logic

import org.osgi.framework.BundleContext
import org.eclipse.osgi.service.environment.EnvironmentInfo

object EclipseHelper {
  /**
   * Fix NPE for ContextDebugHelper.getDebugger
   */
  def helpWithContextDebugHelper() {
    val activator = new org.eclipse.e4.core.internal.contexts.osgi.ContextsActivator()
    val activatorContextField = activator.getClass().getDeclaredField("bundleContext")
    if (!activatorContextField.isAccessible())
      activatorContextField.setAccessible(true)
    activatorContextField.set(activator, new BundleContext {
      def addBundleListener(x$1: org.osgi.framework.BundleListener): Unit = ???
      def addFrameworkListener(x$1: org.osgi.framework.FrameworkListener): Unit = ???
      def addServiceListener(x$1: org.osgi.framework.ServiceListener): Unit = ???
      def addServiceListener(x$1: org.osgi.framework.ServiceListener, x$2: String): Unit = ???
      def createFilter(x$1: String): org.osgi.framework.Filter = ???
      def getAllServiceReferences(x$1: String, x$2: String): Array[org.osgi.framework.ServiceReference[_]] = ???
      def getBundle(x$1: String): org.osgi.framework.Bundle = ???
      def getBundle(x$1: Long): org.osgi.framework.Bundle = ???
      def getBundle(): org.osgi.framework.Bundle = ???
      def getBundles(): Array[org.osgi.framework.Bundle] = ???
      def getDataFile(x$1: String): java.io.File = ???
      def getProperty(x$1: String): String = ???
      def getService[S](x$1: org.osgi.framework.ServiceReference[S]): S = ???
      def getServiceReference[S](x$1: Class[S]): org.osgi.framework.ServiceReference[S] = null
      def getServiceReference(x$1: String): org.osgi.framework.ServiceReference[_] = ???
      def getServiceReferences[S](x$1: Class[S], x$2: String): java.util.Collection[org.osgi.framework.ServiceReference[S]] = ???
      def getServiceReferences(x$1: String, x$2: String): Array[org.osgi.framework.ServiceReference[_]] = ???
      def installBundle(x$1: String): org.osgi.framework.Bundle = ???
      def installBundle(x$1: String, x$2: java.io.InputStream): org.osgi.framework.Bundle = ???
      def registerService[S](x$1: Class[S], x$2: S, x$3: java.util.Dictionary[String, _]): org.osgi.framework.ServiceRegistration[S] = ???
      def registerService(x$1: String, x$2: Any, x$3: java.util.Dictionary[String, _]): org.osgi.framework.ServiceRegistration[_] = ???
      def registerService(x$1: Array[String], x$2: Any, x$3: java.util.Dictionary[String, _]): org.osgi.framework.ServiceRegistration[_] = ???
      def removeBundleListener(x$1: org.osgi.framework.BundleListener): Unit = ???
      def removeFrameworkListener(x$1: org.osgi.framework.FrameworkListener): Unit = ???
      def removeServiceListener(x$1: org.osgi.framework.ServiceListener): Unit = ???
      def ungetService(x$1: org.osgi.framework.ServiceReference[_]): Boolean = ???
    })
  }
  class TestEnvironmentInfo extends EnvironmentInfo {
    def getCommandLineArgs() = Array()
    def getFrameworkArgs() = Array()
    def getNL() = ""
    def getNonFrameworkArgs() = Array()
    def getOS() = ""
    def getOSArch() = ""
    def getWS() = ""
    def inDebugMode() = false
    def inDevelopmentMode() = false
    def getProperty(key: String): String = ???
    def setProperty(key: String, value: String) = ???
  }
}
