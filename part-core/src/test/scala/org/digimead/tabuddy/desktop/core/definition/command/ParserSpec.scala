/**
 * This file is part of the TA Buddy project.
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

package org.digimead.tabuddy.desktop.core.definition.command

import Command.parser.{ commandLiteral, opt, sp }
import java.util.UUID
import org.digimead.tabuddy.desktop.core.Test
import org.digimead.tabuddy.desktop.core.definition.command.Command.{ Failure, MissingCompletionOrFailure, Success, cmdLine2implementation }
import org.scalatest.{ Finders, WordSpec }
import scala.concurrent.Future
import org.digimead.tabuddy.desktop.core.command.CommandHelp

class ParserTest extends WordSpec with Test.Base {
  "A Parser" should {
    "successful parse multiple commands" in {
      val command1id = UUID.fromString("2b565600-f693-11e2-b778-0800200c9a66")
      val command1 = {
        import Command.parser._
        implicit val descriptor = Command.Descriptor(command1id)("open", "my open", "open my mind",
          (activeContext, parserContext, parserResult) ⇒ { Future.successful(parserResult) })
        Command.CmdParser("name")
      }
      val command2id = UUID.fromString("337c0f50-f693-11e2-b778-0800200c9a66")
      val command2 = {
        import Command.parser._
        implicit val descriptor = Command.Descriptor(command2id)("close", "my close", "close my eyes",
          (activeContext, parserContext, parserResult) ⇒ { Future.successful(parserResult) })
        Command.CmdParser("name2")
      }
      val command3id = UUID.fromString("0bf42cf0-f6a3-11e2-b778-0800200c9a66")
      val command3 = {
        import Command.parser._
        implicit val descriptor = Command.Descriptor(command3id)("info", "my info", "grant access to assimilated information",
          (activeContext, parserContext, parserResult) ⇒ { Future.successful(parserResult) })
        Command.CmdParser("name3")
      }
      Command.parse(command1 | command2 | command3, "name") should be(Command.Success(command1id, "name"))
      Command.parse(command1 | command2 | command3, "name2") should be(Command.Success(command2id, "name2"))
      Command.parse(command1 | command2 | command3, "name3") should be(Command.Success(command3id, "name3"))
      Command.parse(command1, "name") should be(Command.Success(command1id, "name"))
    }
    "parse command with parameters" taggedAs (Mark) in {
      val command1id = UUID.fromString("2b565600-f693-11e2-b778-0800200c9a66")
      val command1 = {
        import Command.parser._
        implicit val descriptor = Command.Descriptor(command1id)("test", "test short description", "test long description",
          (activeContext, parserContext, parserResult) ⇒ { Future.successful(parserResult) })
        Command.CmdParser("test" ~ opt(sp ~> ("abc", Command.Hint("abc"))))
      }
      (Command.parse(command1, ""): @unchecked) match {
        case MissingCompletionOrFailure(true, l, _) ⇒ l should have size (1) // replace with " "
      }
      (Command.parse(CommandHelp.parser | command1, ""): @unchecked) match {
        case MissingCompletionOrFailure(true, l, _) ⇒ l should have size (2) // replace with " "
      }
      (Command.parse(command1, "test"): @unchecked) match { case Success(_, _) ⇒ }
      (Command.parse(CommandHelp.parser | command1, "test"): @unchecked) match { case Success(_, _) ⇒ }
      (Command.parse(command1, "test abc"): @unchecked) match { case Success(_, _) ⇒ }
      (Command.parse(CommandHelp.parser | command1, "test abc"): @unchecked) match { case Success(_, _) ⇒ }
      (Command.parse(command1, "testa"): @unchecked) match {
        case MissingCompletionOrFailure(false, List((" ", List())), _) ⇒ // replace with " "
      }
      (Command.parse(CommandHelp.parser | command1, "testa"): @unchecked) match {
        case MissingCompletionOrFailure(false, List((" ", List())), _) ⇒ // replace with " "
      }
      Command.parse(command1, "test a") match {
        case MissingCompletionOrFailure(true, List(("bc", List(Command.Hint("abc", None, None)))), _) ⇒ // append with "bc"
      }
      Command.parse(CommandHelp.parser | command1, "test a") match {
        case MissingCompletionOrFailure(true, List(("bc", List(Command.Hint("abc", None, None)))), _) ⇒ // append with "bc"
      }
      Command.parse(command1, "test v") match {
        case MissingCompletionOrFailure(false, List(("abc", List())), _) ⇒ // replace with "abc"
      }
      Command.parse(CommandHelp.parser | command1, "test v") match {
        case MissingCompletionOrFailure(false, List(("abc", List())), _) ⇒ // replace with "abc"
      }
      Command.parse(command1, "test ") match {
        case MissingCompletionOrFailure(true, List(("abc", List(Command.Hint("abc", None, None)))), _) ⇒ // append with "abc"
      }
      Command.parse(CommandHelp.parser | command1, "test ") match {
        case MissingCompletionOrFailure(true, List(("abc", List(Command.Hint("abc", None, None)))), _) ⇒ // append with "abc"
      }
    }
  }

  override def beforeAll(configMap: org.scalatest.ConfigMap) {
    super.beforeAll(configMap)
    startCoreBeforeAll()
  }
}
