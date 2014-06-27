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

package org.digimead.tabuddy.desktop.core.definition.command

import java.util.UUID
import org.digimead.digi.lib.log.api.XLoggable
import scala.language.implicitConversions
import scala.util.DynamicVariable
import scala.util.matching.Regex

/**
 * Parser implementation for commands.
 * Thanks a lot to to Marcus Schulte for an idea.
 */
class CommandParsers extends JavaTokenParsers with XLoggable {
  /** Stub parser. */
  val stubParser = new StubParser
  /** Make whiteSpace public. */
  override val whiteSpace = """[ \t]+""".r
  /** Special parser for whiteSpaces. */
  val sp = new Parser[String] {
    def apply(in: Input) = {
      val source = in.source
      val offset = in.offset
      val start = offset
      (whiteSpace findPrefixMatchOf (source.subSequence(start, source.length))) match {
        case Some(matched) ⇒
          Success(source.subSequence(start, start + matched.end).toString,
            in.drop(start + matched.end - offset))
        case None ⇒
          val found = if (start == source.length()) "end of source" else "`" + source.charAt(start) + "'"
          if (start == source.length()) {
            val completion = MissingCompletionOrFailure(Seq(Command.Hint(" ")),
              "expected whitespace", in.drop(start - offset))
            Command.completionProposal.value = Command.completionProposal.value :+ completion
            completion
          } else {
            Failure("string matching whitespace expected but " + found + " found", in.drop(start - offset))
          }
      }
    }
  }

  override def skipWhitespace = false

  /** Simple stub parser. */
  class StubParser[T] extends Parser[T] {
    named("Stub")
    def apply(in: Input): ParseResult[T] = Error("Stub parser.", in)
  }
  /**
   * Command parser. It wrap base parser with 'phrase' sentence.
   */
  class CmdParser[T](parserId: UUID, base: Parser[T]) extends Parser[T] {
    def apply(in: Input): ParseResult[T] = {
      phrase(base)(in) match {
        case result: Success[_] ⇒
          Command.triggeredCmdParserId.value match {
            case Some(previousSuccessful) ⇒
              if (previousSuccessful != parserId)
                Error(s"Unable to process command parser ${parserId}, other command parser ${previousSuccessful} is already successfully parsed.", in)
              else
                result
            case None ⇒
              Command.triggeredCmdParserId.value = Some(parserId)
              result
          }
        case result ⇒ result
      }
    }
  }
  /** A parser that matches a literal string. */
  implicit def commandLiteral(s: String)(implicit descriptor: Command.Descriptor): Parser[String] =
    commandLiteral(s, Command.Hint(descriptor.name, Some(descriptor.shortDescription)))
  /** A parser that matches a literal string. */
  implicit def commandLiteral(t: (String, Command.Hint)): Parser[String] =
    commandLiteral(t._1, t._2)
  /** A parser that matches a literal string. */
  implicit def commandLiteral(s: String, hint: Command.Hint): Parser[String] = new Parser[String] {
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
        // if j == all text that we have then give our proposal
        val missing = s.substring(i)
        val completionHint = if (hint.completions.isEmpty) hint.copyWithCompletion(missing) else hint
        val completion = MissingCompletionOrFailure(Seq(completionHint), "expected one of " + missing, in.drop(start - offset))
        Command.completionProposal.value = Command.completionProposal.value :+ completion
        completion
      } else {
        val found = if (start == source.length()) "end of source" else "`" + source.charAt(start) + "'"
        Failure("`" + s + "' expected but " + found + " found", in.drop(start - offset))
      }
    }
  }
  /** A parser that matches a regex string. */
  implicit def commandRegex(r: Regex)(implicit descriptor: Command.Descriptor): Parser[String] =
    commandRegex(r, Command.Hint.Container(Command.Hint(descriptor.name, Some(descriptor.shortDescription))))
  /** A parser that matches a regex string. */
  implicit def commandRegex(t: (Regex, Command.Hint.Container)): Parser[String] =
    commandRegex(t._1, t._2)
  /** A parser that matches a regex string. */
  implicit def commandRegex(r: Regex, hints: Command.Hint.Container): Parser[String] = new Parser[String] {
    def apply(in: Input) = {
      val source = in.source
      val offset = in.offset
      val start = handleWhiteSpace(source, offset)
      val subject = source.subSequence(start, source.length).toString()
      val hintList = hints(subject)
      (r findPrefixMatchOf subject) match {
        case Some(matched) if Command.completionProposalMode.value && hintList.nonEmpty && matched.end == subject.length() ⇒
          // if we are at completionProposalMode
          // and there are some proposals
          // and this is last parser that covers whole subject
          val completion = MissingCompletionOrFailure(hintList, "string matching regex `" + r + "' expected", in.drop(start - offset))
          Command.completionProposal.value = Command.completionProposal.value :+ completion
          completion
        case Some(matched) ⇒
          Success(source.subSequence(start, start + matched.end).toString,
            in.drop(start + matched.end - offset))
        case None ⇒
          val found = if (start == source.length()) "end of source" else "`" + source.charAt(start) + "'"
          if (start == source.length()) {
            val completion = MissingCompletionOrFailure(hintList, "string matching regex `" + r + "' expected", in.drop(start - offset))
            Command.completionProposal.value = Command.completionProposal.value :+ completion
            completion
          } else {
            Failure("string matching regex `" + r + "' expected but " + found + " found", in.drop(start - offset))
          }
      }
    }
  }
  /**
   * A `Failure` which is curable by adding one of several possible completions.
   *
   * @param appender flag indicating whether the completion should be append, not replace
   * @param completions what would make the parser succeed if added to `in`.
   *        1st arg - completion
   * @param msg error message
   * @param next the input about to be read
   */
  case class MissingCompletionOrFailure(val completions: Seq[Command.Hint],
    override val msg: String,
    override val next: Input) extends Failure(msg, next) {
    /** The toString method of a Failure yields an error message. */
    override lazy val toString = "[" + next.pos + "] failure: " + msg + "\n\n" + next.pos.longString

    override def append[U >: Nothing](alt: ⇒ ParseResult[U]): ParseResult[U] = alt match {
      case MissingCompletionOrFailure(newCompletions, _, _) ⇒
        val comps = completions ++ newCompletions
        new MissingCompletionOrFailure(comps, if (comps.isEmpty) msg else "expected one of " + comps, next)
      case Success(_, _) ⇒
        alt
      case ns: NoSuccess ⇒
        if (alt.next.pos < next.pos)
          this
        else
          alt
    }
  }
}

