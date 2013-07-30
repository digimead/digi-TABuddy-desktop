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

package org.digimead.tabuddy.desktop.command

import java.util.UUID

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.lib.test.LoggingHelper
import org.digimead.lib.test.OSGiHelper
import org.digimead.tabuddy.desktop.command.Command.cmdLine2implementation
import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers

import Command.parser.commandLiteral

class ParserTest extends WordSpec with ShouldMatchers with OSGiHelper with LoggingHelper with Loggable {
  val testBundleClass = org.digimead.digi.lib.default.getClass()

  after {
    adjustOSGiAfter
    adjustLoggingAfter
  }
  before {
    DependencyInjection(org.digimead.digi.lib.default, false)
    adjustLoggingBefore
    adjustOSGiBefore
  }
  "A Parser" should {
    "should successful parse multiple commands" in {
      val command1id = UUID.fromString("2b565600-f693-11e2-b778-0800200c9a66")
      val command1 = {
        import Command.parser._
        implicit val descriptor = Command.Descriptor(command1id)("open", "my open",
          (activeContext, parserContext, parserResult) => { System.out.println(parserResult) })
        Command.CmdParser("name")
      }
      val command2id = UUID.fromString("337c0f50-f693-11e2-b778-0800200c9a66")
      val command2 = {
        import Command.parser._
        implicit val descriptor = Command.Descriptor(command2id)("close", "my close",
          (activeContext, parserContext, parserResult) => { System.out.println(parserResult) })
        Command.CmdParser("name2")
      }
      val command3id = UUID.fromString("0bf42cf0-f6a3-11e2-b778-0800200c9a66")
      val command3 = {
        import Command.parser._
        implicit val descriptor = Command.Descriptor(command3id)("info", "my info",
          (activeContext, parserContext, parserResult) => { System.out.println(parserResult) })
        Command.CmdParser("name3")
      }
      Command.parse(command1 | command2 | command3, "name") should be(Command.Success(command1id, "name"))
      Command.parse(command1 | command2 | command3, "name2") should be(Command.Success(command2id, "name2"))
      Command.parse(command1 | command2 | command3, "name3") should be(Command.Success(command3id, "name3"))
      Command.parse(command1, "name") should be(Command.Success(command1id, "name"))
    }
  }
}
