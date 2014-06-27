/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2013-2014 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.core.console.api

/**
 * Console base trait.
 */
trait XConsole {
  /** Foreground color for ANSI black. */
  final val BLACK = "\u001b[0;30m"
  final val BBLACK = "\u001b[1;30m"
  /** Foreground color for ANSI red. */
  final val RED = "\u001b[0;31m"
  final val BRED = "\u001b[1;31m"
  /** Foreground color for ANSI green. */
  final val GREEN = "\u001b[0;32m"
  final val BGREEN = "\u001b[1;32m"
  /** Foreground color for ANSI yellow. */
  final val YELLOW = "\u001b[0;33m"
  final val BYELLOW = "\u001b[1;33m"
  /** Foreground color for ANSI blue. */
  final val BLUE = "\u001b[0;34m"
  final val BBLUE = "\u001b[1;34m"
  /** Foreground color for ANSI magenta. */
  final val MAGENTA = "\u001b[0;35m"
  final val BMAGENTA = "\u001b[1;35m"
  /** Foreground color for ANSI cyan. */
  final val CYAN = "\u001b[0;36m"
  final val BCYAN = "\u001b[1;36m"
  /** Foreground color for ANSI white. */
  final val WHITE = "\u001b[0;37m"
  final val BWHITE = "\u001b[1;37m"

  /** Background color for ANSI black. */
  def BLACK_B = scala.Console.BLACK_B
  /** Background color for ANSI red. */
  def RED_B = scala.Console.RED_B
  /** Background color for ANSI green. */
  def GREEN_B = scala.Console.GREEN_B
  /** Background color for ANSI yellow. */
  def YELLOW_B = scala.Console.YELLOW_B
  /** Background color for ANSI blue. */
  def BLUE_B = scala.Console.BLUE_B
  /** Background color for ANSI magenta. */
  def MAGENTA_B = scala.Console.MAGENTA_B
  /** Background color for ANSI cyan. */
  def CYAN_B = scala.Console.CYAN_B
  /** Background color for ANSI white. */
  def WHITE_B = scala.Console.WHITE_B

  /** Reset ANSI styles. */
  def RESET = scala.Console.RESET
  /** ANSI bold. */
  def BOLD = scala.Console.BOLD
  /** ANSI underlines. */
  def UNDERLINED = scala.Console.UNDERLINED
  /** ANSI blink. */
  def BLINK = scala.Console.BLINK
  /** ANSI reversed. */
  def REVERSED = scala.Console.REVERSED
  /** ANSI invisible. */
  def INVISIBLE = scala.Console.INVISIBLE

  /** Console message trait. */
  trait Message {
    /** */
    val string: String
  }
  object Message {
    /** Message with command that generated when user input is successfully parsed at one of consoles. */
    case class Command(val string: String, val origin: Option[XConsole.Projection] = None) extends Message
    /** Notice message. */
    case class Notice(val string: String) extends Message
    /** Info message. */
    case class Info(val string: String) extends Message
    /** Important message. */
    case class Important(val string: String) extends Message
    /** Warning message. */
    case class Warning(val string: String) extends Message
    /** Alert message. */
    case class Alert(val string: String) extends Message
  }
}

object XConsole extends XConsole {
  /**
   * Projection is a base trait for the specific console interface.
   */
  trait Projection {
    def echo(msg: String)
    /** Start prompt processing. */
    def enablePrompt()

    /** Start console. */
    def start()
    /** Stop console. */
    def stop()
  }
}
