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

package org.digimead.tabuddy.desktop.gui

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectOutputStream
import java.util.UUID

import scala.collection.immutable

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.support.CustomObjectInputStream
import org.eclipse.swt.graphics.Rectangle

import language.implicitConversions

/** Window configuration container */
case class WindowConfiguration(
  /** Is window active/visible. */
  val active: Boolean,
  /** Window location. -1 for default value. */
  val location: Rectangle,
  /** List of window view */
  valviews: Seq[UUID]) {
  val timestamp = System.currentTimeMillis()
}

object WindowConfiguration extends Loggable {
  implicit def windowConfiguration2implementation(c: WindowConfiguration.type): Implementation = c.inner

  /** Default window configuration. */
  def default = DI.default
  /** Creator implementation. */
  def inner = DI.implementation

  class Implementation extends Loggable {
    /** Persistent storage. */
    lazy val configurationFile = {
      val container = DI.location.getParentFile()
      if (!container.exists)
        if (!container.mkdirs())
          throw new IOException("Unable to create " + container)
      val configuration = new File(DI.location.getParentFile(), DI.configurationFileName)
      if (!configuration.exists() && !configuration.createNewFile())
        throw new IOException(s"Unable to create configuration file ${configuration}.")
      if (!configuration.canWrite())
        throw new IOException(s"Configuration file ${configuration} is read only.")
      configuration
    }
    private val loadSaveLock = new Object

    /** Load window configurations. */
    @log
    def load(): immutable.HashMap[UUID, WindowConfiguration] = loadSaveLock.synchronized {
      log.debug("Load windows configuration from " + configurationFile)
      if (!configurationFile.exists() || configurationFile.length() == 0) {
        log.debug("WindowConfiguration presistent storage is empty.")
        return immutable.HashMap()
      }
      try {
        val fis = new FileInputStream(configurationFile)
        val in = new CustomObjectInputStream(fis)
        val result = in.readObject().asInstanceOf[immutable.HashMap[UUID, WindowConfiguration]]
        in.close()
        result
      } catch {
        // catch all throwables, return None if any
        case e: Throwable =>
          log.error("Unable to load windows configuration: " + e.getMessage(), e)
          immutable.HashMap()
      }
    }
    /** Save window configurations. */
    @log
    def save(configurations: immutable.HashMap[UUID, WindowConfiguration]) = loadSaveLock.synchronized {
      log.debug("Save windows configuration to " + configurationFile)
      log.trace("Save windows:\n" + configurations.toSeq.sortBy(_._1).mkString("\n"))
      val fos = new FileOutputStream(configurationFile)
      val out = new ObjectOutputStream(fos)
      out.writeObject(configurations)
      out.close()
    }
  }
  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    lazy val configurationFileName = injectOptional[String]("WindowConfiguration.Configuration.Name") getOrElse "WindowConfiguration.jblob"
    /** Default window configuration. */
    lazy val default = injectOptional[WindowConfiguration]("Default") getOrElse
      WindowConfiguration(false, new Rectangle(-1, -1, 400, 300), Seq())
    /** WindowConfiguration implementation. */
    lazy val implementation = injectOptional[Implementation] getOrElse new Implementation()
    /** Application's configuration file. */
    val location = inject[File]("Config")
  }
}
