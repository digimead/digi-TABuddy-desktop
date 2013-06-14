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

package org.digimead.tabuddy.desktop.launcher

import java.net.URL
import java.net.URLClassLoader
import java.net.URLStreamHandlerFactory

import org.digimead.digi.lib.log.api.Loggable

/**
 * Standard parent-first class loader with additional search over delegationLoader
 */
class RootClassLoader(
  /** The URLs from which to load classes and resources. */
  val urls: Array[URL],
  /** The parent class loader for delegation. */
  val parent: ClassLoader,
  /** The URLStreamHandlerFactory to use when creating URLs. */
  val factory: URLStreamHandlerFactory,
  /** The boot delegation class loader for custom delegation expression */
  val delegationLoader: ClassLoader)
  extends URLClassLoader(urls, parent, factory) with RootClassLoader.Interface with Loggable {
  /** List of regular expressions with propagated entities from this class loader to OSGi. */
  @volatile protected var bootDelegations = Set[String]()

  /** Add class with associated class loader to boot class list. */
  def addBootDelegationExpression(expression: String): Unit = {
    if (delegationLoader == null)
      throw new IllegalStateException("Unable to add boot delegation expression without boot delegation class loader")
    bootDelegations = bootDelegations + expression
  }
  /** List all boot delegation expressions. */
  def listBootDelegation(): Set[String] = {
    if (delegationLoader == null)
      throw new IllegalStateException("Unable to list boot delegation expression without boot delegation class loader")
    bootDelegations
  }
  /** Remove expression from boot delegation expressions. */
  def removeBootDelegationExpression(expression: String): Option[String] = {
    if (delegationLoader == null)
      throw new IllegalStateException("Unable to remove boot delegation expression without boot delegation class loader")
    if (bootDelegations(expression)) {
      bootDelegations = bootDelegations - expression
      Some(expression)
    } else {
      None
    }
  }

  // It is never returns null, as the specification defines
  /** Loads the class with the specified binary name. */
  override protected def loadClass(name: String, resolve: Boolean): Class[_] = {
    // Try to load from parent loader.
    try {
      return super.loadClass(name, resolve)
    } catch {
      case _: ClassNotFoundException =>
    }

    // Try to load from delegation loader.
    if (delegationLoader != null) {
      val iterator = bootDelegations.iterator
      while (iterator.hasNext)
        if (name.matches(iterator.next)) try {
          return delegationLoader.loadClass(name)
        } catch {
          case _: ClassNotFoundException =>
        }
    }

    throw new ClassNotFoundException(name)
  }
}

/**
 * Application root class loader
 */
object RootClassLoader {
  /**
   * Root class loader interface
   */
  trait Interface extends URLClassLoader {
    /** The URLs from which to load classes and resources. */
    val urls: Array[URL]
    /** The parent class loader for delegation. */
    val parent: ClassLoader
    /** The URLStreamHandlerFactory to use when creating URLs. */
    val factory: URLStreamHandlerFactory
    /** The boot delegation class loader for custom delegation expression */
    val delegationLoader: ClassLoader

    /** Add class with associated class loader to boot class list. */
    def addBootDelegationExpression(expression: String): Unit
    /** List all boot delegation expressions. */
    def listBootDelegation(): Set[String]
    /** Remove expression from boot delegation expressions. */
    def removeBootDelegationExpression(expression: String): Option[String]
  }
}
