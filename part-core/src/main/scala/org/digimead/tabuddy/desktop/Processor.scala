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

import scala.collection.JavaConversions._

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.b4e.Addon
import org.digimead.tabuddy.desktop.part.DefaultPart
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.eclipse.e4.core.contexts.IEclipseContext
import org.eclipse.e4.core.di.annotations.Execute
import org.eclipse.e4.ui.internal.workbench.E4Workbench
import org.eclipse.e4.ui.model.application.MApplication
import org.eclipse.e4.ui.model.application.MApplicationFactory
import org.eclipse.e4.ui.services.IServiceConstants
import org.eclipse.swt.widgets.Shell

import javax.inject.Inject

/**
 * E4 model processor.
 */
class Processor extends Loggable {
  /** List of default addons. */
  val addons = Seq(
    ("org.eclipse.e4.core.commands.service", "bundleclass://org.eclipse.e4.core.commands/org.eclipse.e4.core.commands.CommandServiceAddon"),
    ("org.eclipse.e4.ui.contexts.service", "bundleclass://org.eclipse.e4.ui.services/org.eclipse.e4.ui.services.ContextServiceAddon"),
    ("org.eclipse.e4.ui.bindings.service", "bundleclass://org.eclipse.e4.ui.bindings/org.eclipse.e4.ui.bindings.BindingServiceAddon"),
    ("org.eclipse.e4.ui.workbench.commands.model", "bundleclass://org.eclipse.e4.ui.workbench/org.eclipse.e4.ui.internal.workbench.addons.CommandProcessingAddon"),
    ("org.eclipse.e4.ui.workbench.contexts.model", "bundleclass://org.eclipse.e4.ui.workbench/org.eclipse.e4.ui.internal.workbench.addons.ContextProcessingAddon"),
    ("org.eclipse.e4.ui.workbench.bindings.model", "bundleclass://org.eclipse.e4.ui.workbench.swt/org.eclipse.e4.ui.workbench.swt.util.BindingProcessingAddon"),
    ("org.eclipse.e4.ui.workbench.handler.model", "bundleclass://org.eclipse.e4.ui.workbench/org.eclipse.e4.ui.internal.workbench.addons.HandlerProcessingAddon"))
  @Inject
  val application: MApplication = null
  @Inject
  val workbenchContext: IEclipseContext = null

  @Execute
  def execute() {
    fixActiveShell()
    processAddons()
    DefaultPart()
  }

  /** Add default addons to application model if needed. */
  protected def addDefaultAddonsIfAbsent() {
    val addons = application.getAddons()
    this.addons.foreach {
      case (id, uri) =>
        val Array(scheme, gap, bundleSymbolicName, className) = uri.split("/")
        if (!addons.exists(addon => addon.getElementId() == id || addon.getContributionURI().endsWith("/" + className))) try {
          App.bundle(getClass).getBundleContext().getBundles().find(_.getSymbolicName() == bundleSymbolicName).foreach { bundle =>
            val clazz = bundle.loadClass(className) // or throw exception
            val addon = MApplicationFactory.INSTANCE.createAddon()
            addon.setElementId(id)
            addon.setContributionURI(uri)
            application.getAddons().add(addon)
          }
          log.info(s"""Append addon with id "${id}", location "${className}".""")
        } catch {
          case e: ClassNotFoundException =>
            log.warn(s"Unable to append addon ${id}: ClassNotFoundException")
          case e: Throwable =>
            log.warn(s"Unable to append addon ${id}: " + e.getMessage(), e)
        }
    }
  }
  /** Add MainAddon to application model if needed. */
  protected def addMainAddonIfAbsent() {
    val addonId = classOf[Addon].getName()
    for (addon <- application.getAddons()) {
      if (addonId == addon.getElementId()) {
        log.debug("MainAddon addon was found.")
        return
      }
    }
    val addon = MApplicationFactory.INSTANCE.createAddon()
    addon.setElementId(addonId)
    addon.setContributionURI(s"bundleclass://${App.bundle(getClass).getSymbolicName()}/${addonId}")
    application.getAddons().add(addon)
    log.info(s"""Append addon with id ${addonId}", location "${addonId}".""")
  }
  /** Fix context active shell value if needed. */
  def fixActiveShell() {
    val rootContext = App.contextParents(workbenchContext).head
    Option(workbenchContext.get(IServiceConstants.ACTIVE_SHELL)) getOrElse {
      rootContext.set(E4Workbench.LOCAL_ACTIVE_SHELL, Processor.stubShell)
      try {
        assert(workbenchContext.get(E4Workbench.LOCAL_ACTIVE_SHELL) != null)
      } catch {
        case e: Throwable =>
          log.warn("Fix of IServiceConstants.ACTIVE_SHELL is failed.")
      }
    }
  }
  /** Process application addons */
  def processAddons() {
    application.getAddons().sortBy(_.getElementId()).foreach { addon => log.info(s"""Detect addon with id "${addon.getElementId()}", location "${addon.getContributionURI().split("/").last}".""") }
    addDefaultAddonsIfAbsent()
    addMainAddonIfAbsent()
  }
}

object Processor extends Loggable {
  // Stub shell that provides workaround for some hard coded shit that required ACTIVE_SHELL lookup
  // If Eclipse developers do everything right than this stub shell for OSGi context will never used (They didn't)
  /** Stub shell for E4Workbench.LOCAL_ACTIVE_SHELL of root context of workbench. */
  lazy val stubShell = {
    val shell = new Shell()
    shell.setText(getClass.getName + " stub")
    log.debug("Create stub shell " + shell)
    shell
  }
}
