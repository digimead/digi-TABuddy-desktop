/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2014 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.core.ui.inspector.command

import java.util.UUID
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.Core
import org.digimead.tabuddy.desktop.core.definition.command.Command
import scala.language.implicitConversions

/**
 * The configurator is responsible for configure/unconfigure UI inspector commands.
 */
class Commands extends XLoggable {
  @volatile protected var contextParsers = Seq.empty[UUID]
  private val lock = new Object

  /** Configure component commands. */
  @log
  def configure() = lock.synchronized {
    Command.register(CommandInspector.descriptor)
    Command.addToContext(Core.context, CommandInspector.parser).
      foreach(uuid ⇒ contextParsers = contextParsers :+ uuid)
  }
  /** Unconfigure component commands. */
  @log
  def unconfigure() = lock.synchronized {
    contextParsers.foreach(Command.removeFromContext(Core.context, _))
    Command.unregister(CommandInspector.descriptor)
  }
}

object Commands {
  implicit def commands2implementation(c: Commands.type): Commands = c.inner

  /** Commands implementation. */
  def inner: Commands = DI.implementation

  /**
   * Dependency injection routines
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** Actions implementation */
    lazy val implementation = injectOptional[Commands] getOrElse new Commands
  }
}
