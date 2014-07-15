/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2012-2014 Alexey Aksenov ezh@ezh.msk.ru
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

import java.io.File
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.definition.IPreferencePage
import org.digimead.tabuddy.desktop.core.definition.api.XPreferencePage
import org.eclipse.jface.preference.{ IPreferencePage ⇒ EIPreferencePage, PreferenceNode, PreferenceStore }
import org.eclipse.jface.resource.ImageDescriptor
import org.eclipse.ui.PlatformUI
import org.osgi.framework.{ BundleActivator, BundleContext }
import scala.language.implicitConversions

/**
 * Handle application preferences.
 */
class Preferences extends BundleActivator with XLoggable {
  /** Preference stores. */
  protected var stores = Map.empty[String, PreferenceStore]

  /** Get preference store for the specific class. */
  def getPreferenceStore(clazz: Class[_], container: File = null, load: Boolean = true): PreferenceStore = synchronized {
    if (container != null && container.exists() && !container.isDirectory())
      throw new IllegalArgumentException("Invalid container for preference store: " + container)
    val location = if (container != null)
      new File(container, clazz.getName() + ".preferences")
    else
      App.bundle(clazz).getDataFile(clazz.getName() + ".preferences")
    stores.get(location.getAbsolutePath()) match {
      case Some(store) ⇒
        store
      case None ⇒
        log.debug("Create preference store at " + location)
        if (!location.getParentFile().exists())
          location.getParentFile().mkdirs()
        val store = new PreferenceStore(location.getAbsolutePath())
        stores = stores.updated(location.getAbsolutePath(), store)
        if (load && location.exists())
          store.load()
        store
    }
  }
  @log
  def start(context: BundleContext) {
    log.debug("Initialize application preferences.")
    val pmgr = PlatformUI.getWorkbench().getPreferenceManager()
    pmgr.removeAll()
    Preferences.pages.foreach { page ⇒
      log.debug("Register preference page " + page)
      page.register(pmgr)
    }
  }
  @log
  def stop(context: BundleContext) = {
    log.debug("Dispose application preferences.")
    val mgr = PlatformUI.getWorkbench().getPreferenceManager()
    mgr.removeAll()
  }
}

object Preferences extends XLoggable {
  implicit def preferences2implementation(p: Preferences.type): Preferences = p.inner

  /** Get Preferences implementation. */
  def inner = DI.implementation
  /** Get preference pages. */
  def pages = DI.pages

  /**
   * OSGi friendly preference node implementation.
   */
  case class Node(id: String, label: String, image: Option[ImageDescriptor])(builder: () ⇒ AnyRef)
    extends PreferenceNode(id, label, image.getOrElse(null), "") {
    override def createPage() {
      val page = builder().asInstanceOf[EIPreferencePage]
      setPage(page)
      image.foreach(page.setImageDescriptor)
      page.setTitle(label)
    }
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** Preferences implementation. */
    lazy val implementation = injectOptional[Preferences] getOrElse new Preferences()
    /**
     * Collection of preference pages.
     *
     * Each collected adapter must be:
     *  1. an instance of (X)IPreferencePage
     *  2. has name that starts with "UI.Preference."
     */
    lazy val pages: Set[IPreferencePage] = {
      val pages = bindingModule.bindings.filter {
        case (key, value) ⇒ classOf[XPreferencePage].isAssignableFrom(key.m.runtimeClass)
      }.map {
        case (key, value) ⇒
          key.name match {
            case Some(name) if name.startsWith("UI.Preference.") ⇒
              log.debug(s"'${name}' loaded.")
              bindingModule.injectOptional(key).asInstanceOf[Option[IPreferencePage]]
            case _ ⇒
              log.debug(s"'${key.name.getOrElse("Unnamed")}' preference page skipped.")
              None
          }
      }.flatten.toSeq
      assert(pages.distinct.size == pages.size, "preference pages contain duplicated entities in " + pages)
      pages.toSet
    }
  }
}
