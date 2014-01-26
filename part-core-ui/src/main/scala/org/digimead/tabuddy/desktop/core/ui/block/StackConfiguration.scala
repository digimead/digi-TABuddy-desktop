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

package org.digimead.tabuddy.desktop.core.ui.block

import java.io.{ File, FileInputStream, FileOutputStream, IOException, ObjectOutputStream }
import java.util.UUID
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.CustomObjectInputStream
import org.digimead.tabuddy.desktop.core.ui.definition.widget.{ SCompositeHSash, SCompositeTab, SCompositeVSash, VComposite }
import org.digimead.tabuddy.desktop.core.ui.view.ViewDefault
import org.eclipse.swt.widgets.{ Composite, Shell, Widget }
import org.osgi.framework.wiring.BundleWiring
import scala.language.implicitConversions

/**
 * Stack layer configuration.
 */
class StackConfiguration extends Loggable {
  /** Persistent storage. */
  lazy val configurationContainer = {
    val container = StackConfiguration.configurationContainer
    if (!container.exists)
      if (!container.mkdirs())
        throw new IOException("Unable to create " + container)
    if (!container.canWrite())
      throw new IOException(s"Configuration container ${container} is read only.")
    container
  }
  private val loadSaveLock = new Object

  /** Build configuration for the specific shell. */
  def build(shell: Shell) = {
    App.assertEventThread()
    shell.getChildren().flatMap(rebuildConfiguration) match {
      case Array(topLayerConfigurationElement) ⇒ Configuration(topLayerConfigurationElement)
      case Array() ⇒ Configuration(Configuration.CEmpty())
    }
  }
  /** Default view configuration. */
  def default() = StackConfiguration.DI.default()
  /** List all available configurations. */
  def list(): Seq[UUID] = loadSaveLock.synchronized {
    val suffix = "." + StackConfiguration.configurationExtenstion
    configurationContainer.listFiles().flatMap(_.getName() match {
      case name if name endsWith suffix ⇒
        try Some(UUID.fromString(name.dropRight(suffix.length())))
        catch { case e: Throwable ⇒ None }
      case _ ⇒
        None
    })
  }
  /** Load views configurations. */
  @log
  def load(stackId: UUID): Option[Configuration] = loadSaveLock.synchronized {
    val configurationFile = new File(configurationContainer, stackId.toString + "." + StackConfiguration.configurationExtenstion)
    log.debug("Load stack configuration from " + configurationFile.getName)
    if (!configurationFile.exists() || configurationFile.length() == 0) {
      log.debug(s"StackConfiguration for ${stackId.toString} in not exists.")
      return None
    }
    try {
      val fis = new FileInputStream(configurationFile)
      val in = new CustomObjectInputStream(fis, App.bundle(getClass).adapt(classOf[BundleWiring]).getClassLoader())
      val result = in.readObject()
      in.close()
      log.trace("Loaded configuration is:\n" + result.asInstanceOf[Configuration].dump.mkString("\n"))
      Option(result.asInstanceOf[Configuration])
    } catch {
      case e: Throwable ⇒
        log.error("Unable to load stack configuration: " + e.getMessage(), e)
        None
    }
  }
  /** Remove saved configuration. */
  @log
  def remove(stackId: UUID) = loadSaveLock.synchronized {
    val configurationFile = new File(configurationContainer, stackId.toString + "." + StackConfiguration.configurationExtenstion)
    configurationFile.delete()
  }
  /** Save views configurations. */
  @log
  def save(stackId: UUID, configuration: Configuration) = loadSaveLock.synchronized {
    val configurationFile = new File(configurationContainer, stackId.toString + "." + StackConfiguration.configurationExtenstion)
    log.debug("Save stack configuration to " + configurationFile)
    log.trace("Saved configuration is:\n" + configuration.dump.mkString("\n"))
    val fos = new FileOutputStream(configurationFile)
    val out = new ObjectOutputStream(fos)
    out.writeObject(configuration)
    out.close()
  }

  /** Rebuild configuration from the actual widgets hierarchy */
  protected def rebuildConfiguration(widget: Widget): Option[Configuration.CPlaceHolder] = widget match {
    case vcomposite: VComposite ⇒
      Some(Configuration.CView(vcomposite.factory, vcomposite.id))
    case scomposite: SCompositeTab ⇒
      val children: Array[Configuration.CView] = scomposite.getItems().map { tabItem ⇒
        rebuildConfiguration(tabItem.getControl()) match {
          case Some(view: Configuration.CView) ⇒
            Some(view)
          case Some(unexpected) ⇒
            log.fatal("Unexpected configuration element: ${unexpected}.")
            None
          case None ⇒
            None
        }
      }.flatten
      if (children.nonEmpty)
        Some(Configuration.Stack.CTab(children, scomposite.id))
      else
        None
    case scomposite: SCompositeHSash ⇒
      // TODO
      None
    case scomposite: SCompositeVSash ⇒
      // TODO
      None
    case widget: Composite ⇒
      // pass through via other composite
      widget.getChildren().map(rebuildConfiguration).flatten match {
        case Array() ⇒
          None
        case Array(configuration) ⇒
          Some(configuration)
        case unexpected ⇒
          log.fatal(s"Unexpected configuration: ${unexpected.mkString(", ")}")
          None
      }
    case unknownWidget ⇒
      None
  }
}

object StackConfiguration {
  implicit def configuration2implementation(c: StackConfiguration.type): StackConfiguration = c.inner

  /** Stack configuration file extension. */
  def configurationExtenstion = DI.configurationExtenstion
  /** Stack configuration directory name. */
  def configurationContainer = new File(DI.location.getParentFile(), DI.configurationName)
  /** StackConfiguration implementation. */
  def inner = DI.implementation

  trait Builder extends Function0[Configuration]
  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Name of the storage container for stack configurations. */
    lazy val configurationName = injectOptional[String]("Core.UI.StackConfiguration.Name") getOrElse "StackConfiguration"
    /** Extension for stored configurations. */
    lazy val configurationExtenstion = injectOptional[String]("Core.UI.StackConfiguration.Extension") getOrElse "jblob"
    /** Default window configuration. */
    lazy val default = injectOptional[StackConfiguration.Builder]("Core.UI.StackConfiguration.Default") getOrElse {
      () ⇒ Configuration(Configuration.CView(ViewDefault.configuration))
    }
    //      StackConfiguration(Stack.Tab(Seq(View(UUID.fromString("00000000-0000-0000-0000-000000000000")))))
    /** WindowConfiguration implementation. */
    lazy val implementation = injectOptional[StackConfiguration] getOrElse new StackConfiguration()
    /** Application's configuration file. */
    val location = inject[File]("Config")
  }
}
