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
import org.digimead.tabuddy.desktop.logic.operation.{ OperationModifyElementTemplateList, OperationModifyEnumerationList, OperationModifyTypeSchemaList }
import org.digimead.tabuddy.desktop.logic.operation.view.{ OperationModifyFilterList, OperationModifySortingList, OperationModifyViewList }
import scala.language.implicitConversions

/**
 * The configurator is responsible for configure/unconfigure logic commands.
 */
class Commands extends Loggable {
  @volatile protected var contextParsers = Seq.empty[UUID]
  /** Synchronization lock. */
  protected val lock = new Object

  /** Configure component commands. */
  @log
  def configure() = lock.synchronized {
    /*
     * graph
     */
    Command.register(graph.CommandGraphNew.descriptor)
    Command.addToContext(Core.context, graph.CommandGraphNew.parser).
      foreach(uuid ⇒ contextParsers = contextParsers :+ uuid)
    Command.register(graph.CommandGraphList.descriptor)
    Command.addToContext(Core.context, graph.CommandGraphList.parser).
      foreach(uuid ⇒ contextParsers = contextParsers :+ uuid)
    Command.register(graph.CommandGraphOpen.descriptor)
    Command.addToContext(Core.context, graph.CommandGraphOpen.parser).
      foreach(uuid ⇒ contextParsers = contextParsers :+ uuid)
    Command.register(graph.CommandGraphSave.descriptor)
    Command.addToContext(Core.context, graph.CommandGraphSave.parser).
      foreach(uuid ⇒ contextParsers = contextParsers :+ uuid)
    Command.register(graph.CommandGraphSaveAs.descriptor)
    Command.addToContext(Core.context, graph.CommandGraphSaveAs.parser).
      foreach(uuid ⇒ contextParsers = contextParsers :+ uuid)
    Command.register(graph.CommandGraphClose.descriptor)
    Command.addToContext(Core.context, graph.CommandGraphClose.parser).
      foreach(uuid ⇒ contextParsers = contextParsers :+ uuid)
    Command.register(graph.CommandGraphDelete.descriptor)
    Command.addToContext(Core.context, graph.CommandGraphDelete.parser).
      foreach(uuid ⇒ contextParsers = contextParsers :+ uuid)
    Command.register(graph.CommandGraphImport.descriptor)
    Command.addToContext(Core.context, graph.CommandGraphImport.parser).
      foreach(uuid ⇒ contextParsers = contextParsers :+ uuid)
    Command.register(graph.CommandGraphExport.descriptor)
    Command.addToContext(Core.context, graph.CommandGraphExport.parser).
      foreach(uuid ⇒ contextParsers = contextParsers :+ uuid)
    Command.register(graph.CommandGraphShow.descriptor)
    Command.addToContext(Core.context, graph.CommandGraphShow.parser).
      foreach(uuid ⇒ contextParsers = contextParsers :+ uuid)
    /*
     * script
     */
    Command.register(script.CommandScriptRun.descriptor)
    Command.addToContext(Core.context, script.CommandScriptRun.parser).
      foreach(uuid ⇒ contextParsers = contextParsers :+ uuid)
    /*
     * payload
     */
    // Only if OperationModifyElementTemplateList is defined
    if (OperationModifyElementTemplateList.operation.nonEmpty) {
      Command.register(CommandModifyElementTemplateList.descriptor)
      Command.addToContext(Core.context, CommandModifyElementTemplateList.parser).
        foreach(uuid ⇒ contextParsers = contextParsers :+ uuid)
    }
    // Only if OperationModifyEnumerationList is defined
    if (OperationModifyEnumerationList.operation.nonEmpty) {
      Command.register(CommandModifyEnumerationList.descriptor)
      Command.addToContext(Core.context, CommandModifyEnumerationList.parser).
        foreach(uuid ⇒ contextParsers = contextParsers :+ uuid)
    }
    // Only if OperationModifyTypeSchemaList is defined
    if (OperationModifyTypeSchemaList.operation.nonEmpty) {
      Command.register(CommandModifyTypeSchemaList.descriptor)
      Command.addToContext(Core.context, CommandModifyTypeSchemaList.parser).
        foreach(uuid ⇒ contextParsers = contextParsers :+ uuid)
    }
    // Only if OperationModifyFilterList is defined
    if (OperationModifyFilterList.operation.nonEmpty) {
      Command.register(CommandModifyFilterList.descriptor)
      Command.addToContext(Core.context, CommandModifyFilterList.parser).
        foreach(uuid ⇒ contextParsers = contextParsers :+ uuid)
    }
    // Only if OperationModifySortingList is defined
    if (OperationModifySortingList.operation.nonEmpty) {
      Command.register(CommandModifySortingList.descriptor)
      Command.addToContext(Core.context, CommandModifySortingList.parser).
        foreach(uuid ⇒ contextParsers = contextParsers :+ uuid)
    }
    // Only if OperationModifyViewList is defined
    if (OperationModifyViewList.operation.nonEmpty) {
      Command.register(CommandModifyViewList.descriptor)
      Command.addToContext(Core.context, CommandModifyViewList.parser).
        foreach(uuid ⇒ contextParsers = contextParsers :+ uuid)
    }
  }
  /** Unconfigure component commands. */
  @log
  def unconfigure() = lock.synchronized {
    contextParsers.foreach(Command.removeFromContext(Core.context, _))
    Command.get(CommandModifyViewList.descriptor.parserId).foreach(Command.unregister)
    Command.get(CommandModifySortingList.descriptor.parserId).foreach(Command.unregister)
    Command.get(CommandModifyFilterList.descriptor.parserId).foreach(Command.unregister)
    Command.get(CommandModifyElementTemplateList.descriptor.parserId).foreach(Command.unregister)
    Command.get(CommandModifyEnumerationList.descriptor.parserId).foreach(Command.unregister)
    Command.get(CommandModifyTypeSchemaList.descriptor.parserId).foreach(Command.unregister)
    Command.unregister(script.CommandScriptRun.descriptor)
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
  implicit def commands2implementation(c: Commands.type): Commands = c.inner

  /** Commands implementation. */
  def inner: Commands = DI.implementation

  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Actions implementation */
    lazy val implementation = injectOptional[Commands] getOrElse new Commands
  }
}
