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

package org.digimead.tabuddy.desktop.editor.part

import java.net.URI
import scala.collection.mutable
import scala.ref.WeakReference
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.ResourceManager
import org.digimead.tabuddy.desktop.Resources
import org.digimead.tabuddy.desktop.editor.MainPartActiveView
import org.digimead.tabuddy.desktop.editor.MainPartPassiveView
import org.digimead.tabuddy.desktop.logic.Data
import org.digimead.tabuddy.desktop.logic.payload.Payload
import org.digimead.tabuddy.desktop.logic.payload.api.ElementTemplate
import org.digimead.tabuddy.desktop.logic.payload.api.TemplateProperty
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.WritableValue
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Model.model2implementation
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.element.Stash
import org.eclipse.e4.core.contexts.IEclipseContext
import org.eclipse.e4.ui.di.Focus
import org.eclipse.jface.action.Action
import org.eclipse.jface.action.CoolBarManager
import org.eclipse.jface.action.MenuManager
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.StackLayout
import org.eclipse.swt.custom.StyleRange
import org.eclipse.swt.widgets.Composite
import org.eclipse.ui.internal.util.BundleUtility
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import javax.annotation.PostConstruct
import javax.inject.Inject
import javax.inject.Named
import org.eclipse.e4.core.contexts.RunAndTrack
import org.eclipse.e4.ui.model.application.ui.basic.MWindow
import org.eclipse.ui.ISources
import org.eclipse.e4.core.contexts.ContextFunction
import org.eclipse.e4.core.di.annotations.Optional
import org.eclipse.e4.ui.di.UIEventTopic
import org.eclipse.e4.ui.workbench.UIEvents

/** Model editor. */
class MainPart extends Loggable {
  /** Parent view. */
  protected var parent: Option[Composite] = None
  /** Active view. */
  protected lazy val active = new ActiveView(parent.
    getOrElse({ throw new IllegalStateException("Unable to create active view before user interface.") }), SWT.NONE)
  /** Passive view. */
  protected lazy val passive = new PassiveView(parent.
    getOrElse({ throw new IllegalStateException("Unable to create passive view before user interface.") }), SWT.NONE)
  /** Container layout */
  val layout = new StackLayout()
  MainPart.instances += this

  /** Create user interface. */
  @PostConstruct
  def createUserInterface(parent: Composite, window: MWindow) {
    parent.setLayout(layout)
    this.parent = Option(parent)
    updateTopControl()
  }
  /** Set focus. */
  @Focus
  def setFocus() = {
    layout.topControl.setFocus()
  }
  /** Update top control visibility with respect to current model. */
  def updateTopControl() = {
    App.assertUIThread()
    if (Model.eId != Payload.defaultModel.eId) {
      if (layout.topControl != active) {
        log.debug("Switch to active layout.")
        layout.topControl = active
        active.getParent().layout()
      }
    } else {
      if (layout.topControl != passive) {
        log.debug("Switch to passive layout.")
        layout.topControl = passive
        passive.getParent().layout()
      }
    }
  }
  /** Active view class. */
  class ActiveView(parent: Composite, style: Int) extends main.TableView(parent, style) with Loggable {
    log.debug("Create active view.")
  }
  /** Passive view class. */
  class PassiveView(parent: Composite, style: Int) extends MainPartPassiveView(parent, style) with Loggable {
    log.debug("Create passive view.")
  }
}

object MainPart extends BundleActivator {
  /** Part instance. */
  @volatile private var instances = Set[MainPart]()
  /** Part descriptor */
  private lazy val descriptor = App.modelCreatePartDescriptor(
    contributionURI = new URI(s"bundleclass://${App.bundle(getClass).getSymbolicName()}/${classOf[MainPart].getName()}"),
    contributorURI = new URI(s"platform:/plugin/${App.bundle(getClass).getSymbolicName()}"),
    iconURI = Some(BundleUtility.find(App.bundle(getClass), "icons/16x16/documentation.png").toURI()),
    id = classOf[MainPart].getName(),
    label = "Model Editor")
  /** Part id. */
  lazy val id = getClass.getName.dropRight(1)
  /** The column special identifier */
  protected[part] val COLUMN_ID = "id"
  /** The column special identifier */
  protected[part] val COLUMN_NAME = "name"
  /** Aggregation listener delay */
  private val aggregatorDelay = 250 // msec
  /** Significant changes(schema modification, model reloading,...) aggregator */
  private val reloadEventsAggregator = WritableValue(Long.box(0L))
  /** Structural changes(e.g. addition or removal of elements) aggregator */
  private val refreshEventsAggregator = WritableValue(Set[Element[_ <: Stash]]())
  /** Structural changes(e.g. addition or removal of elements) aggregator */
  private val refreshEventsExpandAggregator = WritableValue(Set[Element[_ <: Stash]]())
  /** Minor changes(element modification) aggregator */
  private val updateEventsAggregator = WritableValue(Set[Element[_ <: Stash]]())
  /** Global element events subscriber */
  private val elementEventsSubscriber = new Element.Event.Sub {
    def notify(pub: Element.Event.Pub, event: Element.Event) = event match {
      case Element.Event.ChildInclude(element, newElement, _) =>
        if (element.eStash.model.forall(_ eq Model.inner))
          App.exec {
            instances.foreach { instance =>
              /*              if (view.context.ActionToggleExpand.isChecked())
                if (element.eChildren.size == 1) // if 1st child
                  view.tree.context.expandedItems += TreeProxy.Item(element) // expand parent
                else
                  view.tree.context.expandedItems ++=
                    newElement.eChildren.iteratorRecursive().map(TreeProxy.Item(_)) // expand children*/
            }
            refreshEventsAggregator.value = refreshEventsAggregator.value + element
          }
      case Element.Event.ChildRemove(element, _, _) =>
        if (element.eStash.model.forall(_ eq Model.inner))
          App.exec { refreshEventsAggregator.value = refreshEventsAggregator.value + element }
      case Element.Event.ChildrenReset(element, _) =>
        if (element.eStash.model.forall(_ eq Model.inner))
          App.exec { refreshEventsAggregator.value = refreshEventsAggregator.value + element }
      case Element.Event.ChildReplace(element, _, _, _) =>
        if (element.eStash.model.forall(_ eq Model.inner))
          App.exec { refreshEventsAggregator.value = refreshEventsAggregator.value + element }
      case Element.Event.StashReplace(element, _, _, _) =>
        if (element.eStash.model.forall(_ eq Model.inner))
          App.exec { updateEventsAggregator.value = updateEventsAggregator.value + element }
      case Element.Event.ValueInclude(element, _, _) =>
        if (element.eStash.model.forall(_ eq Model.inner))
          App.exec { updateEventsAggregator.value = updateEventsAggregator.value + element }
      case Element.Event.ValueRemove(element, _, _) =>
        if (element.eStash.model.forall(_ eq Model.inner))
          App.exec { updateEventsAggregator.value = updateEventsAggregator.value + element }
      case Element.Event.ValueUpdate(element, _, _, _) =>
        if (element.eStash.model.forall(_ eq Model.inner))
          App.exec { updateEventsAggregator.value = updateEventsAggregator.value + element }
      case Element.Event.ModelReplace(_, _, _) =>
      //App.exec { Tree.FilterSystemElement.updateSystemElement }
      case _ =>
    }
  }

  /** Add descriptor to model. */
  def apply() {
    if (!App.model.getDescriptors().contains(descriptor))
      App.model.getDescriptors().add(descriptor)
  }
  /** Update active view for part with regards to current model. */
  def onModelInitialization() = App.exec { instances.foreach(_.updateTopControl) }
  /**
   * This function is invoked at application start
   */
  @log
  def start(context: BundleContext) = Element.Event.subscribe(elementEventsSubscriber)
  /**
   * This function is invoked at application stop
   */
  @log
  def stop(context: BundleContext) = Element.Event.removeSubscription(elementEventsSubscriber)
  /** Disable the redraw while updating */
  def withRedrawDelayed[T](view: MainPart)(f: => T): Seq[T] = Seq()
  /*for {
      view <- instances.toSeq
      //active <- view.active
    } yield {
      /*active.getSashForm.setRedraw(false)
      view.table.tableViewer.getTable.setRedraw(false)
      view.tree.treeViewer.getTree.setRedraw(false)
      val result = f
      view.tree.treeViewer.getTree.setRedraw(true)
      view.table.tableViewer.getTable.setRedraw(true)
      active.getSashForm.setRedraw(true)
      result*/
      null
    }*/

  /** Range information about a link in the StyledTextRootElement */
  case class RootPathLinkRange[T <: Element.Generic](val element: T, val index: Int, val length: Int)
}
