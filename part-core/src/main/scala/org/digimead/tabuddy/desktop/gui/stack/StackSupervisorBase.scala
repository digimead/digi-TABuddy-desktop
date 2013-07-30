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

import java.util.UUID

import scala.collection.immutable

import org.digimead.tabuddy.desktop.gui.StackConfiguration
import org.eclipse.swt.custom.ScrolledComposite

import akka.actor.Actor

/**
 * Base trait for stack supervisor.
 */
trait StackSupervisorBase extends Actor {
  /** Window/StackSupervisor id. */
  lazy val supervisorId = UUID.fromString(self.path.parent.name.split("@").last)
  /** Stack configuration. */
  @volatile protected[stack] var configuration = StackConfiguration.default
  /** Stack configuration map. SComposite UUID -> configuration element. */
  @volatile protected[stack] var configurationMap = toMap(configuration)
  /** Top level stack hierarchy container. It is ScrolledComposite of content of WComposite. */
  @volatile protected[stack] var container: Option[ScrolledComposite] = None

  protected def toMap(configuration: org.digimead.tabuddy.desktop.gui.Configuration): immutable.HashMap[UUID, org.digimead.tabuddy.desktop.gui.Configuration.PlaceHolder] = {
    var entry = Seq[(UUID, org.digimead.tabuddy.desktop.gui.Configuration.PlaceHolder)]()
    def visit(stack: org.digimead.tabuddy.desktop.gui.Configuration.PlaceHolder) {
      entry = entry :+ stack.id -> stack
      stack match {
        case tab: org.digimead.tabuddy.desktop.gui.Configuration.Stack.Tab =>
          tab.children.foreach(visit)
        case hsash: org.digimead.tabuddy.desktop.gui.Configuration.Stack.HSash =>
          visit(hsash.left)
          visit(hsash.right)
        case vsash: org.digimead.tabuddy.desktop.gui.Configuration.Stack.VSash =>
          visit(vsash.top)
          visit(vsash.bottom)
        case view: org.digimead.tabuddy.desktop.gui.Configuration.View =>
      }
    }
    visit(configuration.stack)
    immutable.HashMap[UUID, org.digimead.tabuddy.desktop.gui.Configuration.PlaceHolder](entry: _*)
  }
}