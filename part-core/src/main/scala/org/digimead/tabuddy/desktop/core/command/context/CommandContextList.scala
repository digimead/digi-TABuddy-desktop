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

package org.digimead.tabuddy.desktop.core.command.context

import java.util.UUID
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.{ Core, Messages }
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.desktop.core.definition.command.api.Command.Descriptor
import org.digimead.tabuddy.desktop.core.support.App
import org.eclipse.e4.core.internal.contexts.EclipseContext
import scala.concurrent.Future

/**
 * List command that show all application contexts.
 */
object CommandContextList extends Loggable {
  import Command.parser._
  private val fullArg = "-full"
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** Console converter. */
  lazy val converter: PartialFunction[(Descriptor, Any), String] = {
    case (this.descriptor, Left(seq)) ⇒
      // brief list
      seq.asInstanceOf[Seq[EclipseContext]].sortBy(ctx ⇒ Context.getName(ctx).getOrElse("")).map { context ⇒
        Option(context.getParent()) match {
          case Some(parent) ⇒
            s"""$context (with parent "$parent")"""
          case None ⇒
            s"""$context (root context)"""
        }
      }.mkString("\n")
    case (this.descriptor, Right((seq, full: Boolean))) ⇒
      seq.asInstanceOf[Seq[EclipseContext]].sortBy(ctx ⇒ Context.getName(ctx).getOrElse("")).map { context ⇒
        App.inner().contextDumpHierarchy(context, _ ⇒ true, full)
      }.mkString("\n")
  }
  /** Command description. */
  implicit lazy val descriptor = Command.Descriptor(UUID.randomUUID())(Messages.context_list_text,
    Messages.context_listDescriptionShort_text, Messages.context_listDescriptionLong_text,
    (activeContext, parserContext, parserResult) ⇒ Future[Either[Seq[EclipseContext], (Seq[EclipseContext], Boolean)]] {
      parserResult match {
        case Some(~(name, opt)) ⇒
          val nameList = (Context.getName(Core.context.context).map(name ⇒ (name, Core.context.context)) +:
            App.inner().contextChildren(Core.context).map(ctx ⇒ Context.getName(ctx).map(name ⇒ (name, ctx)))).flatten
          Right(nameList.filter(_._1 == name).map(_._2), opt != Some(fullArg)) // full
        case None ⇒
          Left(Core.context.context +: App.inner().contextChildren(Core.context)) // brief
      }
    })
  /** Command parser. */
  lazy val parser = Command.CmdParser(descriptor.name ~> opt(sp ~> contextParser ~ opt(sp ~> (fullArg, Command.Hint(fullArg, Some("list full information"))))))

  /** Create parser for the list of commands. */
  protected def contextParser: Command.parser.Parser[Any] = {
    val nameList = (Context.getName(Core.context.context) +: App.inner().contextChildren(Core.context).map(Context.getName)).flatten
    if (nameList.nonEmpty)
      nameList.map(name ⇒
        commandLiteral(name, Command.Hint(name, Some(s"show '${name}' content"))) ^^ { _ ⇒ name }).
        reduceLeft(_ | _)
    else
      success(None)
  }
}
