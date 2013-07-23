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

package org.digimead.tabuddy.desktop.logic.toolbar

import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Core
import org.digimead.tabuddy.desktop.logic.Logic
import org.digimead.tabuddy.desktop.logic.handler
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation

import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.ScalaActorRef
import akka.actor.actorRef2Scala

import language.implicitConversions

class ModelToolBar extends App.ContainerActor with Loggable {
  val lockActor = this.context.actorOf(handler.Lock.props, handler.Lock.id)
  val deleteActor = this.context.actorOf(handler.Delete.props, handler.Delete.id)
  //val expandAllActor = this.context.actorOf(ExpandAll.props, ExpandAll.id)
  //val collapseAllActor = this.context.actorOf(CollapseAll.props, CollapseAll.id)
}

object ModelToolBar {
  implicit def toolbar2actorRef(t: ModelToolBar.type): ActorRef = t.actor
  implicit def toolbar2actorSRef(t: ModelToolBar.type): ScalaActorRef = t.actor
  /** ModelToolBar actor reference. */
  lazy val actor = App.getActorRef(App.system.actorSelection(actorPath)) getOrElse {
    throw new IllegalStateException("Unable to locate actor with path " + actorPath)
  }
  /** ModelToolBar actor path. */
  lazy val actorPath = App.system / Core.id / Logic.id / id
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  /** ModelToolBar actor reference configuration object. */
  lazy val props = DI.props
  // Initialize descendant actor singletons
  handler.Lock
  handler.Delete

  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** EditorToolBar actor reference configuration object. */
    lazy val props = injectOptional[Props]("ModelToolBar") getOrElse Props[ModelToolBar]
  }
}
