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

package org.digimead.tabuddy.desktop.b4e

import org.digimead.digi.lib.Disposable
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Activator
import org.digimead.tabuddy.desktop.Resources
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.eclipse.e4.core.services.events.IEventBroker
import org.eclipse.e4.ui.model.application.ui.basic.MWindow
import org.eclipse.e4.ui.model.application.ui.menu.MToolBar
import org.eclipse.e4.ui.model.application.ui.menu.impl.MenuImpl
import org.eclipse.e4.ui.workbench.UIEvents
import org.eclipse.e4.ui.workbench.renderers.swt.MenuManagerRenderer
import org.eclipse.e4.ui.workbench.renderers.swt.ToolBarManagerRenderer
import org.eclipse.swt.widgets.Menu
import org.eclipse.swt.widgets.Shell
import org.eclipse.swt.widgets.ToolBar
import org.osgi.service.event.Event
import org.osgi.service.event.EventHandler

import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import javax.inject.Inject

/**
 * Main addon that injected via Processor.
 */
class Addon extends EventHandler with Resources.ResourceWatcher with Disposable.Default with Loggable {
  /** Application EventBroker. */
  @Inject
  protected val eventBroker: IEventBroker = null
  /** Weak reference with manager for disposable marker. */
  protected val disposeable = Disposable(this, Activator)

  /** Deinitialize addon. */
  @PreDestroy
  override def dispose() = Addon.disposeableLock.synchronized {
    if (Option(disposeable).nonEmpty) {
      log.debug("Stop core addon.")
      eventBroker.unsubscribe(this)
      // clear everything
      super.dispose()
    }
  }
  /** Initialize addon. */
  @PostConstruct
  def init() = {
    log.debug("Start core addon.")
    eventBroker.subscribe(UIEvents.UIElement.TOPIC_WIDGET, this)
  }
  /** Process UIEventPublisher events. */
  def handleEvent(event: Event) = {
    val oldValue = Option(event.getProperty(UIEvents.EventTags.OLD_VALUE))
    val newValue = Option(event.getProperty(UIEvents.EventTags.NEW_VALUE))
    event.getProperty(UIEvents.EventTags.ELEMENT) match {
      case menu: MenuImpl if UIEvents.isSET(event) =>
        (oldValue, newValue) match {
          case (None, Some(widget: Menu)) =>
            addMenu(menu, App.getRenderer[MenuManagerRenderer](menu).getManager(menu))
          case (None, Some(other)) =>
            log.debug(s"Skip menu ${menu} with unsuitable widget ${other.getClass}.")
          case (Some(widget: Menu), None) =>
            removeMenu(menu)
          case (_, _) =>
        }
      case toolbar: MToolBar if UIEvents.isSET(event) =>
        (oldValue, newValue) match {
          case (None, Some(widget: ToolBar)) =>
            addToolBar(toolbar, App.getRenderer[ToolBarManagerRenderer](toolbar).getManager(toolbar))
          case (None, Some(other)) =>
            log.debug(s"Skip toolbar ${toolbar} with unsuitable widget ${other.getClass}.")
          case (Some(widget: ToolBar), None) =>
            removeToolBar(toolbar)
          case (_, _) =>
        }
      case window: MWindow if UIEvents.isSET(event) && window.getTags.contains("topLevel") =>
        (oldValue, newValue) match {
          case (None, Some(shell: Shell)) =>
            addTopWindow(window, shell)
          case (None, Some(other)) =>
            log.debug(s"Skip window ${window} with unsuitable widget ${other.getClass}.")
          case (Some(shell: Shell), None) =>
            removeTopWindow(window)
          case (_, _) =>
        }
      case _ =>
    }
  }
}

object Addon {
  private val disposeableLock = new Object
}
