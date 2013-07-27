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

import scala.util.DynamicVariable

import org.digimead.digi.lib.log.api.Loggable

import language.implicitConversions

/**
 * Parser implementation for commands.
 * Thanks a lot to to Marcus Schulte for an idea.
 */
class Parser extends JavaTokenParsers with Loggable {
  /** Spool of successful results for various commands. */
  val successfullCommand = new DynamicVariable[Option[UUID]](None)

  /**
   * Command parser. It wrap base parser with 'phrase' sentence.
   */
  class CmdParser[T](uniqueId: UUID, base: Parser[T]) extends Parser[T] {
    def apply(in: Input): ParseResult[T] = {
      phrase(base)(in) match {
        case result: Success[_] =>
          successfullCommand.value match {
            case Some(previousSuccessful) =>
              Error(s"Unable to process command parser ${uniqueId}, other command parser ${previousSuccessful} is already successfully parsed.", in)
            case None =>
              successfullCommand.value = Some(uniqueId)
              result
          }
        case result => result
      }
    }
  }
  implicit def commandLiteral(s: String)(implicit description: Command.Description): Parser[String] =
    commandLiteral(s, description.commandId)
  implicit def commandLiteral(s: String, commandId: UUID): Parser[String] = new Parser[String] {
    def apply(in: Input): ParseResult[String] = {
      val source = in.source
      val offset = in.offset
      val start = handleWhiteSpace(source, offset)
      var i = 0
      var j = start
      while (i < s.length && j < source.length && s.charAt(i) == source.charAt(j)) {
        i += 1
        j += 1
      }
      if (i == s.length)
        Success(source.subSequence(start, j).toString, in.drop(j - offset))
      else if (j == source.length()) {
        val missing = s.substring(i)
        MissingCompletionOrFailure(List((missing, commandId)), "expected one of " + missing, in.drop(start - offset))
      } else {
        val found = if (start == source.length()) "end of source" else "`" + source.charAt(start) + "'"
        MissingCompletionOrFailure(List(), "`" + s + "' expected but " + found + " found", in.drop(start - offset))
      }
    }
  }
  /**
   * A `Failure` which is curable by adding one of several possible completions.
   *
   * @param completions what would make the parser succeed if added to `in`.
   * @param next the input about to be read
   */
  case class MissingCompletionOrFailure(val completions: List[(String, UUID)],
    override val msg: String,
    override val next: Input) extends Failure(msg, next) {
    /** The toString method of a Failure yields an error message. */
    override def toString = "[" + next.pos + "] failure: " + msg + "\n\n" + next.pos.longString

    override def append[U >: Nothing](alt: => ParseResult[U]): ParseResult[U] = alt match {
      case MissingCompletionOrFailure(newCompletions, _, _) =>
        val comps = completions ++ newCompletions
        new MissingCompletionOrFailure(comps, if (comps.isEmpty) msg else "expected one of " + comps, next)
      case Success(_, _) =>
        alt
      case ns: NoSuccess =>
        if (alt.next.pos < next.pos)
          this
        else
          alt
    }
  }
}
