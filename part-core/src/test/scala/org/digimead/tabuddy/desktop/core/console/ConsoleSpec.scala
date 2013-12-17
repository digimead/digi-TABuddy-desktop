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

package org.digimead.tabuddy.desktop.core.console

import akka.pattern.ask
import akka.util.Timeout
import com.escalatesoft.subcut.inject.NewBindingModule
import java.util.UUID
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.{ Core, Test }
import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.desktop.core.definition.command.api.Command.Descriptor
import org.digimead.tabuddy.desktop.core.support.App
import org.mockito.Mockito.{ never, reset, spy, timeout, times, verify }
import org.scalatest.WordSpec
import scala.concurrent.{ Await, Future }
import scala.util.{ Success, Try }

class ConsoleSpec extends WordSpec with Test.Base {
  val consoleMock = spy(new ConsoleSpec.TestProjection)
  val converterMock = spy(new ConsoleSpec.Converter)
  val converter: PartialFunction[(Descriptor, Any), String] = _ match {
    case (_, ConsoleSpec.ConsoleSpecCommandComplexResult(inner)) ⇒ converterMock(inner)
  }

  "The console" which {
    "provides an interface for the application" should {
      "have proper colors" in {
        println(Console.BLACK + "BLACK")
        println(Console.BBLACK + "BBLACK")
        println(Console.RED + "RED")
        println(Console.BRED + "BRED")
        println(Console.GREEN + "GREEN")
        println(Console.BGREEN + "BGREEN")
        println(Console.YELLOW + "YELLOW")
        println(Console.BYELLOW + "BYELLOW")
        println(Console.BLUE + "BLUE")
        println(Console.BBLUE + "BBLUE")
        println(Console.MAGENTA + "MAGENTA")
        println(Console.BMAGENTA + "BMAGENTA")
        println(Console.CYAN + "CYAN")
        println(Console.BCYAN + "BCYAN")
        println(Console.WHITE + "WHITE")
        println(Console.BWHITE + "BWHITE")
      }
      "generate Notice messages" in {
        reset(consoleMock)
        Console ! Console.Message.Notice("1")
        verify(consoleMock, timeout(1000).times(1)).echo(Console.BBLACK + "1" + Console.RESET)
      }
      "generate Info messages" in {
        reset(consoleMock)
        Console ! Console.Message.Info("1")
        verify(consoleMock, timeout(1000).times(1)).echo(Console.WHITE + "1" + Console.RESET)
      }
      "generate Important messages" in {
        reset(consoleMock)
        Console ! Console.Message.Important("1")
        verify(consoleMock, timeout(1000).times(1)).echo(Console.BWHITE + "1" + Console.RESET)
      }
      "generate Warning messages" in {
        reset(consoleMock)
        Console ! Console.Message.Warning("1")
        verify(consoleMock, timeout(1000).times(1)).echo(Console.BYELLOW + "1" + Console.RESET)
      }
      "generate Alert messages" in {
        reset(consoleMock)
        Console ! Console.Message.Alert("1")
        verify(consoleMock, timeout(1000).times(1)).echo(
          Console.RED + "*ALERT*" + Console.WHITE + "1" + Console.RED + "*ALERT*" + Console.WHITE + Console.RESET)
      }
    }
    "provides an interface for a user" should {
      "convert command result to text representation" taggedAs (Mark) in {
        Console.convert(ConsoleSpec.TestCommand.descriptor, ()) should be("")
        Console.convert(ConsoleSpec.TestCommand.descriptor, 1) should be("1")
        Console.convert(ConsoleSpec.TestCommand.descriptor, "abc") should be("abc")
        Console.convert(ConsoleSpec.TestCommand.descriptor, ConsoleSpec.ConsoleSpecCommandComplexResult("abc")) should
          be("Text representation: abc")
        verify(converterMock, times(1)).apply("abc")
      }
      "parse and process commands" taggedAs (Mark) in {
        implicit val t = Timeout(1000)
        Command.register(ConsoleSpec.TestCommand.descriptor)
        Command.addToContext(Core.context, ConsoleSpec.TestCommand.parser)

        reset(consoleMock)
        reset(converterMock)
        Console.actor ! Console.Message.Command(ConsoleSpec.TestCommand.descriptor.name)
        verify(consoleMock, timeout(1000).times(1)).echo(
          s"""${Console.BBLACK}Command "TestCommand" is completed:\n${Console.RESET}Text representation: TestCommandResult${Console.RESET}""")
        verify(converterMock, timeout(1000).times(1)).apply("TestCommandResult")

        reset(consoleMock)
        reset(converterMock)
        val future = Console.actor ? Console.Message.Command(ConsoleSpec.TestCommand.descriptor.name)
        Await.result(future.mapTo[Try[String]], t.duration) should
          be(Success(ConsoleSpec.ConsoleSpecCommandComplexResult("TestCommandResult")))
        verify(consoleMock, timeout(1000).times(1)).echo(
          s"""${Console.BBLACK}Command "TestCommand" is completed:\n${Console.RESET}Text representation: TestCommandResult${Console.RESET}""")
        verify(converterMock, timeout(1000).times(1)).apply("TestCommandResult")

        Command.unregister(ConsoleSpec.TestCommand.descriptor)
      }
    }
  }
  override def afterAll(configMap: org.scalatest.ConfigMap) {
    // test that consoleMock stopped
    verify(consoleMock, never).stop()
    super.afterAll(configMap)
    verify(consoleMock, times(1)).stop()
  }
  override def beforeAll(configMap: org.scalatest.ConfigMap) {
    // test that consoleMock started
    reset(consoleMock)
    verify(consoleMock, never).start()
    super.beforeAll(configMap)
    startCoreBeforeAll(38)
    verify(consoleMock, times(1)).start()
  }
  override def config = super.config ~ new NewBindingModule(module ⇒ {
    module.bind[api.Console.Projection] identifiedBy "Console.Mock" toSingle { consoleMock }
    module.bind[PartialFunction[(Descriptor, Any), String]] identifiedBy "Console.Converter.Mock" toSingle { converter }
  })
}

object ConsoleSpec {
  class TestProjection extends Projection with Loggable {
    def echoAndRefresh(msg: String) {}
    def echo(msg: String) {}
    def echoNoNL(msg: String) {}
    def start() {}
    def stop() {}
  }
  object TestCommand {
    import Command.parser._
    implicit lazy val ec = App.system.dispatcher
    implicit lazy val descriptor = Command.Descriptor(UUID.randomUUID())("TestCommand",
      "TestCommand short description", "TestCommand\nvery\nlong\ndescription",
      (activeContext, parserContext, parserResult) ⇒ Future {
        ConsoleSpecCommandComplexResult("TestCommandResult")
      })
    /** Command parser. */
    lazy val parser = Command.CmdParser(descriptor.name)
  }
  case class ConsoleSpecCommandComplexResult(val inner: String)
  class Converter extends Function1[String, String] {
    def apply(inner: String): String = "Text representation: " + inner
  }
}
