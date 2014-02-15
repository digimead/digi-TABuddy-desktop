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

package org.digimead.tabuddy.desktop.core.ui.definition.widget

import akka.actor.{ ActorRef, actorRef2Scala }
import akka.pattern.ask
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.{ Inject, Named }
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.Messages
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.core.ui.block.{ Window, WindowConfiguration, WindowSupervisor }
import org.digimead.tabuddy.desktop.core.ui.block.StackConfiguration
import org.digimead.tabuddy.desktop.core.ui.block.builder.WindowContentBuilder
import org.digimead.tabuddy.desktop.core.ui.definition.ToolBarManager
import org.eclipse.e4.core.contexts.Active
import org.eclipse.e4.core.di.annotations.Optional
import org.eclipse.jface.action.{ CoolBarManager, StatusLineManager }
import org.eclipse.jface.window.ApplicationWindow
import org.eclipse.swt.{ SWT, SWTException }
import org.eclipse.swt.custom.StackLayout
import org.eclipse.swt.events.{ DisposeEvent, DisposeListener }
import org.eclipse.swt.widgets.{ Composite, Control, Event, Listener, Shell }
import scala.language.implicitConversions

/**
 * Instance that represents visible window which is based on JFace framework.
 */
class AppWindow @Inject() (val id: UUID, @Optional argInitialConfiguration: WindowConfiguration, val ref: ActorRef,
  @Named("StackSupervisorActorRef") val supervisorRef: ActorRef, val windowContext: Context, @Optional parentShell: Shell)
  extends ApplicationWindow(parentShell) with WComposite.ContextSetter with Window.WindowMapDisposer with Loggable {
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** Akka communication timeout. */
  implicit val timeout = akka.util.Timeout(UI.communicationTimeout)
  /** Window initial configuration. */
  val initialConfiguration = Option(argInitialConfiguration)
  /** Content composite that contains views. */
  @volatile protected var content: Option[WComposite] = None
  /** Filler composite that visible by default. */
  @volatile protected var filler: Option[Composite] = None
  /** Last window title. */
  @volatile protected var lastWindowTitle = ""
  /** On active listener flag */
  protected val onActiveFlag = new AtomicBoolean(true)
  /** On active listener */
  protected lazy val onActiveListener = new AppWindow.OnActiveListener(this)
  /** Flag indicating whether the window configuration should be saved. */
  @volatile protected var saveOnClose = true

  App.assertEventThread()
  initialize()

  /** Build window configuration. */
  def buildConfiguration(): WindowConfiguration = {
    val location = getShell.getBounds()
    WindowConfiguration(getShell.isVisible(), location, Seq())
  }
  /** Get window content widget. */
  def getContent() = content
  /** Returns the status line manager for this window (if it has one). */
  override def getStatusLineManager() = super.getStatusLineManager()
  /** Get window title. */
  def getTitle(context: Context.Rich = (windowContext: Context.Rich).getActiveLeaf()): String =
    getTitle(context.get(UI.Id.windowTitle).getOrElse(Messages.TABuddyDesktop),
      context.get(UI.Id.viewTitle), context.get(UI.Id.contentTitle))
  /** Get window title. */
  def getTitle(window: String, view: Option[String], content: Option[String]): String =
    window + view.map(v ⇒ " : " + v).getOrElse("") + content.map(c ⇒ " - " + c).getOrElse("")

  /** Add window ID to shell. */
  override protected def configureShell(shell: Shell) {
    shell.setData(UI.swtId, id) // order is important
    windowContext.set(classOf[Shell], shell)
    super.configureShell(shell)
    shell.setText(getTitle())
    // The onPaintListener solution is not sufficient
    App.display.addFilter(SWT.Paint, onActiveListener)
    shell.addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) {
        windowContext.dispose() // see WComposite dispose listener
        if (saveOnClose) {
          WindowSupervisor ! App.Message.Set(id, buildConfiguration())
          StackConfiguration.save(id, StackConfiguration.build(shell))
        }
        App.display.removeFilter(SWT.Paint, onActiveListener)
        windowRemoveFromCommonMap()
        ref ! App.Message.Destroy(AppWindow.this, ref)
      }
    })
    shell.setFocus()
    initialConfiguration.foreach { configuration ⇒
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
    val (container, filler, content) = WindowContentBuilder(this, parent)
    this.filler = Option(filler)
    this.content = Option(content)
    // Set the specific widget
    windowContext.set(classOf[WComposite], content)
    // Set the common top level widget
    windowContext.set(classOf[Composite], content)
    showContent(content)
    //    App.publish(App.Message.Open(this), None))
    container
  }
  /**
   * Returns a new cool bar manager for the window.
   * <p>
   * Subclasses may override this method to customize the cool bar manager.
   * </p>
   *
   * @param style swt style bits used to create the Coolbar
   *
   * @return a cool bar manager
   * @since 3.0
   * @see CoolBarManager#CoolBarManager(int)
   * @see CoolBar for style bits
   */
  override protected def createCoolBarManager(style: Int): CoolBarManager = {
    new CoolBarManager(style) {
      override def update(force: Boolean) {
        {
          for {
            control ← Option(getControl())
            composite ← Option(control.getParent())
          } yield {
            composite.setRedraw(false)
            super.update(force)
            composite.layout(true)
            composite.setRedraw(true)
          }
        } getOrElse {
          super.update(force)
        }
      }
    }
  }
  /** Create status bar manager. */
  override protected def createStatusLineManager(): StatusLineManager = {
    val manager = new status.StatusLineManager()
    status.StatusLineContributor(manager)
    manager
  }
  /** Initialize current window. */
  protected def initialize() {
    addStatusLine()
    addCoolBar(SWT.FLAT | SWT.WRAP)
    addMenuBar()
    setBlockOnOpen(false)
    getCoolBarManager.setOverrides(ToolBarManager.Overrides)
    windowContext.set(classOf[AppWindow], this)
  }
  /** On window active. */
  protected def onActive() = {}
  /** Show content. */
  protected def showContent(content: WComposite) {
    log.debug(s"Show content of ${this}.")
    // Bind window context to composite.
    setWCompositeContext(content, windowContext)
    // Bind composite to window context.
    windowContext.set(classOf[Composite], content)
    val result = supervisorRef ? App.Message.Restore(content, ref)
    result.onSuccess {
      case App.Message.Restore(Some(topWidget: SComposite), Some(origin), _) ⇒
        App.exec {
          if (!content.isDisposed()) // Yes, it is possible.
            this.content.foreach { content ⇒
              val parent = content.getParent()
              val layout = parent.getLayout().asInstanceOf[StackLayout]
              layout.topControl = content
              layout.topControl.setFocus()
              parent.layout()
              // Window content is restored.
              ref ! App.Message.Open(this, ref)
            }
        }
      case App.Message.Error(error, None) ⇒
        log.fatal(s"Unable to create top level content for window ${this}: ${error}.")
      case error ⇒
        log.fatal(s"Unable to create top level content for window ${this}: ${error}.")
    }
    result.onFailure {
      case failure ⇒
        log.fatal(s"Unable to create top level content for window ${this}: ${failure}.")
    }
  }
  /** Update window title. */
  @Inject @Optional
  protected def updateTitle(@Active context: Context, @Active @Optional @Named(UI.Id.windowTitle) window: String,
    @Active @Optional @Named(UI.Id.viewTitle) view: String, @Active @Optional @Named(UI.Id.contentTitle) content: String) = {
    val shell = getShell
    if (shell != null && !shell.isDisposed())
      if (App.contextParents(context).contains(windowContext) || window != lastWindowTitle)
        App.exec {
          lastWindowTitle = window
          try {
            val oldTitle = shell.getText()
            val newTitle = getTitle()
            if (oldTitle != newTitle)
              shell.setText(newTitle)
          } catch {
            case e: SWTException if e.getMessage == "Widget is disposed" ⇒
              // Yes, there is.
              /*
               * at org.eclipse.swt.SWT.error(SWT.java:4283)
	           * at org.eclipse.swt.widgets.Widget.error(Widget.java:481)
	           * at org.eclipse.swt.widgets.Widget.checkWidget(Widget.java:418)
               * at org.eclipse.swt.widgets.Decorations.getText(Decorations.java:434)
               * Shell is valid, but decoration is not.
               */
              "WTF? LOL?"
          }
        }
  }

  override lazy val toString = "AppWindow[%08X]".format(id.hashCode())
}

object AppWindow {
  trait Controller {
    implicit def appWindowAccess(appWindow: AppWindow): AppWindowAccessor = AppWindowAccessor(appWindow)
  }
  /** OnActive listener that trigger on first SWT.Paint event. */
  class OnActiveListener(window: AppWindow) extends Listener() {
    def handleEvent(event: Event) = event.widget match {
      case control: Control if control.getShell.eq(window.getShell) ⇒
        if (window.onActiveFlag.compareAndSet(true, false)) {
          App.display.removeFilter(SWT.Paint, this)
          window.onActive()
        }
      case other ⇒
    }
  }
  case class AppWindowAccessor(appWindow: AppWindow) {
    def content = appWindow.content
    def saveOnClose: Boolean = appWindow.saveOnClose
    def saveOnClose_=(arg: Boolean) = appWindow.saveOnClose = arg
  }
}
