/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2012-2014 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.logic.command

import java.util.UUID
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.Core
import org.digimead.tabuddy.desktop.core.definition.command.Command
import scala.language.implicitConversions

/**
 * The configurator is responsible for configure/unconfigure logic commands.
 */
class Commands extends Loggable {
  @volatile protected var contextParsers = Seq.empty[UUID]
  private val lock = new Object

  /** Configure component actions. */
  @log
  def configure() = lock.synchronized {
    /*
     * graph
     */
    Command.register(graph.CommandGraphNew.descriptor)
    val coreGraphNew = Command.addToContext(Core.context, graph.CommandGraphNew.parser)
    Command.register(graph.CommandGraphList.descriptor)
    val coreGraphList = Command.addToContext(Core.context, graph.CommandGraphList.parser)
    Command.register(graph.CommandGraphOpen.descriptor)
    val coreGraphOpen = Command.addToContext(Core.context, graph.CommandGraphOpen.parser)
    Command.register(graph.CommandGraphSave.descriptor)
    val coreGraphSave = Command.addToContext(Core.context, graph.CommandGraphSave.parser)
    Command.register(graph.CommandGraphSaveAs.descriptor)
    val coreGraphSaveAs = Command.addToContext(Core.context, graph.CommandGraphSaveAs.parser)
    Command.register(graph.CommandGraphClose.descriptor)
    val coreGraphClose = Command.addToContext(Core.context, graph.CommandGraphClose.parser)
    Command.register(graph.CommandGraphDelete.descriptor)
    val coreGraphDelete = Command.addToContext(Core.context, graph.CommandGraphDelete.parser)
    Command.register(graph.CommandGraphImport.descriptor)
    val coreGraphImport = Command.addToContext(Core.context, graph.CommandGraphImport.parser)
    Command.register(graph.CommandGraphExport.descriptor)
    val coreGraphExport = Command.addToContext(Core.context, graph.CommandGraphExport.parser)
    Command.register(graph.CommandGraphShow.descriptor)
    val coreGraphShow = Command.addToContext(Core.context, graph.CommandGraphShow.parser)
    contextParsers = Seq(coreGraphNew, coreGraphList, coreGraphOpen, coreGraphSave, coreGraphSaveAs,
      coreGraphClose, coreGraphDelete, coreGraphImport, coreGraphExport, coreGraphShow).flatten
  }
  /** Unconfigure component actions. */
  @log
  def unconfigure() = lock.synchronized {
    contextParsers.foreach(Command.removeFromContext(Core.context, _))
    Command.unregister(graph.CommandGraphShow.descriptor)
    Command.unregister(graph.CommandGraphExport.descriptor)
    Command.unregister(graph.CommandGraphImport.descriptor)
    Command.unregister(graph.CommandGraphDelete.descriptor)
    Command.unregister(graph.CommandGraphClose.descriptor)
    Command.unregister(graph.CommandGraphSaveAs.descriptor)
    Command.unregister(graph.CommandGraphSave.descriptor)
    Command.unregister(graph.CommandGraphOpen.descriptor)
    Command.unregister(graph.CommandGraphList.descriptor)
    Command.unregister(graph.CommandGraphNew.descriptor)
  }
}

object Commands {
  implicit def configurator2implementation(c: Commands.type): Commands = c.inner

  /** Commands implementation. */
  def inner(): Commands = DI.implementation

  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Actions implementation */
    lazy val implementation = injectOptional[Commands] getOrElse new Commands
  }
}
