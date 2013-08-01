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

package org.digimead.tabuddy.desktop.gui.transform

import scala.collection.immutable

import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.gui.Configuration
import org.digimead.tabuddy.desktop.gui.GUI
import org.digimead.tabuddy.desktop.gui.StackLayer
import org.digimead.tabuddy.desktop.gui.StackSupervisor
import org.digimead.tabuddy.desktop.gui.builder.StackTabBuilder
import org.digimead.tabuddy.desktop.gui.builder.StackTabBuilder.builder2implementation
import org.digimead.tabuddy.desktop.gui.widget.SCompositeTab
import org.digimead.tabuddy.desktop.gui.widget.VComposite
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.eclipse.jface.databinding.swt.SWTObservables
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Shell

import language.implicitConversions

/** Wrap view with tab stack. */
class TransformViewToTab extends Loggable {
  def apply(ss: StackSupervisor, view: VComposite): Option[SCompositeTab] = {
    log.debug(s"Move ${view} to tab stack container.")
    App.checkThread
    val hierarchy = App.widgetHierarchy(view)
    if (hierarchy.headOption != Some(view) || hierarchy.size < 2)
      throw new IllegalStateException(s"Illegal hierarchy ${hierarchy}.")
    hierarchy(1) match {
      case tab: SCompositeTab =>
        log.debug(s"View ${view} is already wrapped with tab ${tab}.")
        Option(tab)
      case other: Shell =>
        val tabParentWidget = view.getParent
        val viewConfiguration = ss.configuration.element(view.id)._2.asInstanceOf[Configuration.View]
        val tabConfiguration = Configuration.Stack.Tab(Seq(viewConfiguration))
        log.debug(s"Reconfigure stack hierarchy. Bind %s to WComposite[%08X].".format(tabConfiguration,
          Option(other.getData(GUI.swtId)).map(_.hashCode()).getOrElse {
            log.fatal(s"Bind to unknown shell ${other}."); 0 // Unable to find UUID value in shell data
          }))
        val stackRef = ss.context.actorOf(StackLayer.props.copy(args = immutable.Seq(tabConfiguration.id)), StackLayer.id + "_%08X".format(tabConfiguration.id.hashCode()))
        val (tabComposite, containers) = StackTabBuilder(tabConfiguration, tabParentWidget, stackRef)
        val firstTab = containers.head
        if (!view.setParent(firstTab)) {
          log.fatal(s"Unable to change parent for ${view}.")
          tabComposite.dispose()
          None
        } else {
          firstTab.setContent(view)
          firstTab.setMinSize(view.computeSize(SWT.DEFAULT, SWT.DEFAULT))
          tabComposite.getItems().find { item => item.getData(GUI.swtId) == viewConfiguration.id } match {
            case Some(tabItem) =>
              GUI.factory(viewConfiguration.factorySingletonClassName) match {
                case Some(factory) =>
                  App.bindingContext.bindValue(SWTObservables.observeText(tabItem), factory.title(view.contentRef))
                  tabItem.setText(factory.title(view.contentRef).getValue().asInstanceOf[String])
                case None =>
                  log.fatal(s"Unable to find view factory for ${viewConfiguration.factorySingletonClassName}.")
              }
            case None =>
              log.fatal(s"TabItem for ${viewConfiguration} in ${tabComposite} not found.")
          }
          tabParentWidget.setContent(tabComposite)
          tabParentWidget.setMinSize(tabComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT))
          tabParentWidget.layout(true)
          App.publish(App.Message.Created(tabComposite, stackRef))
          App.publish(App.Message.Updated(view, stackRef))
          //ss.configuration.set(Configuration(ss.configuration.get.stack.map {
          //  case oldView: Configuration.View if oldView.id == view.id =>
          //    log.debug(s"Replace configuration element ${view} with ${tabConfiguration}. ")
          //    tabConfiguration
          //  case other => other
          //}))
          Option(tabComposite)
        }
      case unexpected =>
        throw new IllegalStateException(s"Unexpected parent element ${unexpected}.")
    }
  }
}

object TransformViewToTab {
  implicit def transform2implementation(t: TransformViewToTab.type): TransformViewToTab = inner

  def inner(): TransformViewToTab = DI.implementation

  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** TransformAttachView implementation */
    lazy val implementation = injectOptional[TransformViewToTab] getOrElse new TransformViewToTab
  }
}
