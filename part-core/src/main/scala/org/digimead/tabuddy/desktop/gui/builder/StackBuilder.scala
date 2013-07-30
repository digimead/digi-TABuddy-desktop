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

package org.digimead.tabuddy.desktop.gui.builder

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.gui.Configuration
import org.digimead.tabuddy.desktop.gui.ViewLayer
import org.digimead.tabuddy.desktop.gui.builder.StackHSashBuilder.builder2implementation
import org.digimead.tabuddy.desktop.gui.builder.StackTabBuilder.builder2implementation
import org.digimead.tabuddy.desktop.gui.builder.StackVSashBuilder.builder2implementation
import org.digimead.tabuddy.desktop.gui.builder.StackViewBuilder.builder2implementation
import org.digimead.tabuddy.desktop.gui.widget.SComposite
import org.digimead.tabuddy.desktop.gui.widget.VComposite
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.Timeout
import org.eclipse.e4.core.internal.contexts.EclipseContext
import org.eclipse.swt.custom.ScrolledComposite

import akka.actor.ActorRef
import akka.actor.actorRef2Scala
import akka.pattern.ask

import language.implicitConversions

class StackBuilder extends Loggable {
  /** Creates stack content. */
  @log
  def apply(view: Configuration.View, parentWidget: ScrolledComposite, parentContext: EclipseContext, supervisorRef: ActorRef, viewRef: ActorRef): Option[VComposite] = {
    App.execNGet { StackViewBuilder(view, viewRef, parentWidget, parentContext) }
  }
  /** Creates stack content. */
  @log
  def apply(stack: Configuration.PlaceHolder, parentWidget: ScrolledComposite, parentContext: EclipseContext, supervisorRef: ActorRef, stackRef: ActorRef): Option[SComposite] = {
    stack match {
      case tab: Configuration.Stack.Tab =>
        val (tabComposite, containers) = App.execNGet { StackTabBuilder(tab, parentWidget, stackRef) }
        // Attach list of Configuration.View(from tab.children) to ScrolledComposite(from containers)
        for {
          container <- containers
          viewConfiguration <- tab.children
        } {
          implicit val ec = App.system.dispatcher
          implicit val timeout = akka.util.Timeout(Timeout.short)
          // Create new view layer.
          supervisorRef ? App.Message.Attach(ViewLayer.props, ViewLayer.id + "@" + viewConfiguration.id) onSuccess {
            case viewRef: ActorRef =>
              // Create view within view layer.
              viewRef ! App.Message.Create(ViewLayer.CreateArgument(viewConfiguration, parentWidget, parentContext), supervisorRef)
          }
        }
        Option(tabComposite)
      case hsash: Configuration.Stack.HSash =>
        val (sashComposite, left, right) = StackHSashBuilder(hsash, parentWidget, stackRef)
        //buildLevel(hsash.left, left)
        //buildLevel(hsash.right, right)
        Option(sashComposite)
      case vsash: Configuration.Stack.VSash =>
        val (sashComposite, top, bottom) = StackVSashBuilder(vsash, parentWidget, stackRef)
        //buildLevel(vsash.top, top)
        //buildLevel(vsash.bottom, bottom)
        Option(sashComposite)
      case view: Configuration.View =>
        log.fatal("Unable to process view as stack.")
        None
    }
  }
}

object StackBuilder {
  implicit def builder2implementation(c: StackBuilder.type): StackBuilder = c.inner

  /** StackBuilder implementation. */
  def inner = DI.implementation

  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Window ContentBuilder implementation. */
    lazy val implementation = injectOptional[StackBuilder] getOrElse new StackBuilder
  }
}
