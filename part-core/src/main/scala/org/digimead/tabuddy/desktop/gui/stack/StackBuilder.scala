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

package org.digimead.tabuddy.desktop.gui.stack

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.gui.StackConfiguration
import org.digimead.tabuddy.desktop.gui.View
import org.digimead.tabuddy.desktop.gui.api
import org.digimead.tabuddy.desktop.gui.stack.StackHSashBuilder.builder2implementation
import org.digimead.tabuddy.desktop.gui.stack.StackTabBuilder.builder2implementation
import org.digimead.tabuddy.desktop.gui.stack.StackVSashBuilder.builder2implementation
import org.digimead.tabuddy.desktop.gui.stack.StackViewBuilder.builder2implementation
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.Timeout
import org.eclipse.swt.custom.ScrolledComposite

import akka.actor.ActorRef
import akka.actor.actorRef2Scala
import akka.pattern.ask

import language.implicitConversions

class StackBuilder extends Loggable {
  /** Creates stack content. */
  @log
  def apply(stack: api.Configuration.PlaceHolder, parent: ScrolledComposite, supervisor: ActorRef, stackActor: ActorRef): SComposite = {
    stack match {
      case tab: api.Configuration.Stack.Tab =>
        val (scomposite, containers) = App.execNGet { StackTabBuilder(tab, stackActor, parent) }
        for {
          container <- containers
          viewConfiguration <- tab.children
        } {
          implicit val ec = App.system.dispatcher
          implicit val timeout = akka.util.Timeout(Timeout.short)
          supervisor ? App.Message.Attach(View.props, View.id + "@" + viewConfiguration.id) onSuccess {
            case viewActorRef: ActorRef =>
              viewActorRef ! App.Message.Create(View.CreateArgument(viewConfiguration, parent), supervisor)
          }
        }
        scomposite
      case hsash: api.Configuration.Stack.HSash =>
        val (left, right) = StackHSashBuilder(hsash, parent)
        //buildLevel(hsash.left, left)
        //buildLevel(hsash.right, right)
        null
      case vsash: api.Configuration.Stack.VSash =>
        val (top, bottom) = StackVSashBuilder(vsash, parent)
        //buildLevel(vsash.top, top)
        //buildLevel(vsash.bottom, bottom)
        null
      case view: api.Configuration.View =>
        App.execNGet { StackViewBuilder(view, stackActor, parent) }
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
