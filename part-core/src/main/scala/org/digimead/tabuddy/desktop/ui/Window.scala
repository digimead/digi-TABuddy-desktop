/**
 * This file is part of the TABuddy project.
 * Copyright (c) 2012-2013 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.ui

import scala.util.DynamicVariable

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.tabuddy.desktop.Data
import org.digimead.tabuddy.desktop.Main
import org.digimead.tabuddy.desktop.payload.Payload
import org.digimead.tabuddy.desktop.res.Messages
import org.digimead.tabuddy.desktop.support.WritableValue.wrapper2underlying
import org.digimead.tabuddy.desktop.ui.action.ActionConsole
import org.digimead.tabuddy.desktop.ui.action.ActionExit
import org.digimead.tabuddy.desktop.ui.action.ActionModifyElementTemplateList
import org.digimead.tabuddy.desktop.ui.action.ActionModifyEnumerationList
import org.digimead.tabuddy.desktop.ui.action.ActionModifyTypeSchemaList
import org.digimead.tabuddy.desktop.ui.view.View
import org.eclipse.core.databinding.observable.value.IValueChangeListener
import org.eclipse.core.databinding.observable.value.ValueChangeEvent
import org.eclipse.jface.action.Separator
import org.eclipse.swt.widgets.Shell

import language.implicitConversions

class Window extends org.digimead.tabuddy.desktop.res.Window with Loggable {
  assert(Window.instance == null, "MainWindow already initialized")
  Window.instance = this

  // initialize
  // initialize menu
  // file
  getMenuFile.add(ActionConsole)
  getMenuFile.add(ActionExit)
  // model
  getMenuModel.add(ActionModifyElementTemplateList)
  getMenuModel.add(ActionModifyEnumerationList)
  getMenuModel.add(ActionModifyTypeSchemaList)
  // other
  getMenuTopLevel().add(new Separator)

  // initialize toolbars
  getCoolBarManager2().add(toolbar.MainView)
  //getCoolBarManager2().add(toolbar.MainCommon)
  getCoolBarManager2().add(toolbar.MainLocalStorage)
  //getCoolBarManager2().add(toolbar.MainCoreStorage)
  //getCoolBarManager2().add(toolbar.MainCoreSelector)
  // initialize title
  Data.modelName.addValueChangeListener(new IValueChangeListener() { def handleValueChange(event: ValueChangeEvent) = updateTitle })
  View.currentView.addValueChangeListener(new IValueChangeListener() { def handleValueChange(event: ValueChangeEvent) = updateTitle })

  @log
  override def close(): Boolean = synchronized {
    log.info("close application window")
    val manager = getCoolBarManager2()
    manager.getItems.foreach(_.dispose())
    super.close
  }
  override def onActive {
    super.onActive
    // load all sub views
    View.load
    updateTitle
    new Thread(new Runnable { def run = Main.onApplicationStartup }).start
  }
  protected def updateTitle() {
    val modelName = Data.modelName.value match {
      case name if name.trim.nonEmpty && name != Payload.defaultModel.name => name
      case name => Messages.untitled_text
    }
    Window.shell.setText(Messages.shell_detailed_text.format(modelName, Messages.shell_text))
  }
}

object Window {
  implicit def instance2object(mw: Window.type) = {
    assert(instance != null, "MainWindow not initialized")
    instance
  }
  @volatile private var instance: Window = null
  // The thread local variable that points to the top-most shell instance (dialog, for example)
  val currentShell = new ShellDynamicVariable

  /** provide access to Window instance */
  def apply() = {
    assert(instance != null, "MainWindow not initialized")
    instance
  }
  /** Provide access to the Shell instance */
  def shell = {
    assert(instance != null, "MainWindow not initialized")
    instance.getShell
  }
  private[Window] class ShellDynamicVariable extends DynamicVariable[Option[() => Shell]](None) {
    /** Retrieve the current value */
    def apply(): Shell = super.value.flatMap(f => Option(f())) getOrElse Window.shell
  }
}
