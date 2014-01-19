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

package org.digimead.tabuddy.desktop.core.ui.block

import java.io.{ File, FileInputStream, FileOutputStream, FilenameFilter, IOException, ObjectOutputStream }
import java.util.UUID
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.CustomObjectInputStream
import org.eclipse.swt.graphics.Rectangle
import org.osgi.framework.wiring.BundleWiring
import scala.collection.immutable
import scala.language.implicitConversions

/**
 * Window configuration container. It contains:
 * - activation flag
 * - location
 * - time stamp
 */
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
  /** WindowConfiguration implementation. */
  def inner = DI.implementation

  class Implementation extends Loggable {
    /** Persistent storage. */
    lazy val configurationContainer = {
      val container = new File(DI.location.getParentFile(), DI.configurationName)
      if (!container.exists)
        if (!container.mkdirs())
          throw new IOException("Unable to create " + container)
      if (!container.canWrite())
        throw new IOException(s"Configuration container ${container} is read only.")
      container
    }
    /** Regular expression for saved configuration file. */
    lazy val configurationFileNameRegexp = ("""([0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}\.""" + DI.configurationExtenstion + ")").r
    private val loadSaveLock = new Object

    /** Load window configurations. */
    @log
    def load(): immutable.HashMap[UUID, WindowConfiguration] = loadSaveLock.synchronized {
      log.debug("Load windows configuration from " + configurationContainer)
      if (!configurationContainer.exists()) {
        log.debug("WindowConfiguration presistent storage is empty.")
        return immutable.HashMap()
      }
      val files = configurationContainer.listFiles(new FilenameFilter() {
        override def accept(parent: File, name: String) =
          name match {
            case configurationFileNameRegexp(_) ⇒ true
            case _ ⇒ false
          }
      })
      if (files.isEmpty) {
        log.debug("WindowConfiguration presistent storage is empty.")
        return immutable.HashMap()
      }
      val configurations = for (configurationFile ← files) yield try {
        log.debug("Load configuration from " + configurationFile.getName)
        val fis = new FileInputStream(configurationFile)
        val in = new CustomObjectInputStream(fis, App.bundle(getClass).adapt(classOf[BundleWiring]).getClassLoader())
        val result = in.readObject().asInstanceOf[WindowConfiguration]
        in.close()
        Some(UUID.fromString(configurationFile.getName().takeWhile(_ != '.')) -> result)
      } catch {
        case e: Throwable ⇒
          log.error(s"Unable to load window ${configurationFile.getName} configuration: ${e.getMessage()}.", e)
          None
      }
      immutable.HashMap(configurations.flatten: _*)
    }
    /** Save window configurations. */
    @log
    def save(configurations: immutable.HashMap[UUID, WindowConfiguration]) = loadSaveLock.synchronized {
      log.debug("Save windows configuration to " + configurationContainer)
      log.trace("Save windows:\n" + configurations.toSeq.sortBy(_._1).mkString("\n"))
      for ((id, configuration) ← configurations) {
        val configurationFile = new File(configurationContainer, id.toString + "." + DI.configurationExtenstion)
        log.debug("Save configuration to " + configurationFile.getName)
        val fos = new FileOutputStream(configurationFile)
        val out = new ObjectOutputStream(fos)
        out.writeObject(configuration)
        out.close()
      }
    }
  }
  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Name of the storage container for window configurations. */
    lazy val configurationName = injectOptional[String]("Core.UI.WindowConfiguration.Name") getOrElse "WindowConfiguration"
    /** Extension for stored configurations. */
    lazy val configurationExtenstion = injectOptional[String]("Core.UI.WindowConfiguration.Extension") getOrElse "jblob"
    /** Default window configuration. */
    lazy val default = injectOptional[WindowConfiguration]("Core.UI.WindowConfiguration.Default") getOrElse
      WindowConfiguration(false, new Rectangle(-1, -1, 400, 300), Seq())
    /** WindowConfiguration implementation. */
    lazy val implementation = injectOptional[Implementation] getOrElse new Implementation()
    /** Application's configuration file. */
    val location = inject[File]("Config")
  }
}
