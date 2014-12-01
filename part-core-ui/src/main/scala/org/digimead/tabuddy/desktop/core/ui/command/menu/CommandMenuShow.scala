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

package org.digimead.tabuddy.desktop.core.ui.command.menu

import java.util.UUID
import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.Messages
import scala.concurrent.Future
import org.digimead.tabuddy.desktop.core.ui.SmartMenuManager

/**
 * Show menu entry.
 */
object CommandMenuShow {
  import Command.parser._
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** Command description. */
  implicit lazy val descriptor = Command.Descriptor(UUID.randomUUID())(Messages.menu_show_text,
    Messages.menu_showDescriptionShort_text, Messages.menu_showDescriptionLong_text,
    (activeContext, parserContext, parserResult) ⇒ Future {
      val id = parserResult.asInstanceOf[String]
      SmartMenuManager.show(id)
      "Show " + id
    })
  /** Command parser. */
  lazy val parser = Command.CmdParser(descriptor.name ~> item)
  /** Menu items. */
  protected lazy val items = SmartMenuManager.genericItems.keys.toSeq.sorted

  /** Create parser with list of menu items. */
  protected def item: Command.parser.Parser[Any] = sp ~> commandRegex("\\S+".r, ItemHintContainer) ^^ {
    case r @ CompletionRequest(id) ⇒ r
    case id ⇒
      if (!items.contains(id))
        throw new Command.ParseException(s"Menu identifier ${id} is not found.")
      id
  }

  /** Hint container for list of menu items. */
  object ItemHintContainer extends Command.Hint.Container {
    /** Get parser hints for user provided argument. */
    def apply(arg: String): Seq[Command.Hint] =
      items.sorted.filter(_.startsWith(arg)).map(proposal ⇒
        Command.Hint(proposal, None, Seq(proposal.drop(arg.length)))).filter(_.completions.head.nonEmpty)
  }
}
