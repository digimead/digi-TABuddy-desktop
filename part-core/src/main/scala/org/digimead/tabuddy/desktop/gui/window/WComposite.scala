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

package org.digimead.tabuddy.desktop.gui.window

import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import org.digimead.tabuddy.desktop.gui.GUI
import org.digimead.tabuddy.desktop.gui.WindowConfiguration
import org.digimead.tabuddy.desktop.gui.WindowSupervisor
import org.digimead.tabuddy.desktop.gui.WindowSupervisor.windowGroup2actorSRef
import org.digimead.tabuddy.desktop.gui.window.ContentBuilder.builder2implementation
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.eclipse.jface.action.StatusLineManager
import org.eclipse.jface.window.ApplicationWindow
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.ScrolledComposite
import org.eclipse.swt.custom.StackLayout
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Shell
import akka.actor.ActorRef
import akka.actor.actorRef2Scala
import language.implicitConversions
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Text
import org.eclipse.swt.graphics.Point
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.gui.window.status.StatusLineContributor
/**
 * Instance that represents visible window which is based on JFace framework.
 */
class WComposite(val id: UUID, val windowActorRef: ActorRef,
  val viewSupervisorActorRef: ActorRef, parentShell: Shell) extends ApplicationWindow(parentShell) with Loggable {
  @volatile protected var configuration: Option[WindowConfiguration] = None
  @volatile protected var saveOnClose = true
  /** Filler composite that visible by default. */
  @volatile protected var filler: Option[Composite] = None
  /** Content composite that contains views. */
  @volatile protected var content: Option[ScrolledComposite] = None
  /** Flag indicating whether the content is visible. */
  protected val contentVisible = new AtomicBoolean()

  initialize()

  def initialize() {
    addStatusLine()
    addToolBar(SWT.FLAT | SWT.WRAP)
    addMenuBar()
    setBlockOnOpen(false)
  }

  /** Update window configuration. */
  def updateConfiguration() {
    val location = getShell.getBounds()
    this.configuration = Some(WindowConfiguration(getShell.isVisible(), location, Seq()))
  }
  override def getStatusLineManager() = super.getStatusLineManager()

  /** Create status bar manager. */
  override protected def createStatusLineManager(): StatusLineManager = {
    val manager = new status.StatusLineManager()
    status.StatusLineContributor(manager)
    manager
  }
  /** Show content. */
  protected def showContent() = content.foreach { content =>
    val parent = content.getParent()
    val layout = parent.getLayout().asInstanceOf[StackLayout]
    layout.topControl = content
    parent.layout()
  }
  /** Add window ID to shell. */
  override protected def configureShell(shell: Shell) {
    super.configureShell(shell)
    shell.setData(GUI.swtId, id)
    configuration.foreach { configuration =>
      val oBounds = shell.getBounds
      val cBounds = configuration.location
      if (cBounds.x < 0 && cBounds.y < 0) {
        // Set only size.
        if (cBounds.width < 0) cBounds.width = oBounds.width
        if (cBounds.height < 0) cBounds.height = oBounds.height
        shell.setSize(cBounds.width, cBounds.height)
      } else {
        // Set size and position.
        if (cBounds.x < 0) cBounds.x = oBounds.x
        if (cBounds.y < 0) cBounds.y = oBounds.y
        if (cBounds.width < 0) cBounds.width = oBounds.width
        if (cBounds.height < 0) cBounds.height = oBounds.height
        shell.setBounds(cBounds)
      }
    }
  }
  /** Creates and returns this window's contents. */
  override protected def createContents(parent: Composite): Control = {
    val (container, filler, content) = ContentBuilder(this, parent)
    this.filler = Option(filler)
    this.content = Option(content)
    viewSupervisorActorRef ! App.Message.Restore(content, App.system.deadLetters)
    container
  }
  /** Add close listener. */
  override def handleShellCloseEvent() {
    updateConfiguration()
    for (configuration <- configuration if saveOnClose)
      WindowSupervisor ! WindowSupervisor.Message.Set(id, configuration)
    super.handleShellCloseEvent
    App.publish(App.Message.Destroyed(this, windowActorRef))
  }
}

object WComposite {
  trait Controller {
    implicit def wCompositeAccess(wComposite: WComposite): WCompositeAccessor = WCompositeAccessor(wComposite)
  }
  case class WCompositeAccessor(wComposite: WComposite) {
    def content = wComposite.content
    def configuration: Option[WindowConfiguration] = wComposite.configuration
    def configuration_=(arg: Option[WindowConfiguration]) = wComposite.configuration = arg
    def contentVisible = wComposite.contentVisible
    def saveOnClose: Boolean = wComposite.saveOnClose
    def saveOnClose_=(arg: Boolean) = wComposite.saveOnClose = arg
    def showContent = wComposite.showContent
  }
}
