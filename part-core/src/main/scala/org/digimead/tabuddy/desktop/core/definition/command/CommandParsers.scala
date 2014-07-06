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
  /** Placeholder parser. */
  val nop = new Parser[String] { def apply(in: Input) = Success(in.source.toString, in) }
  /** Special parser for white spaces. */
  val sp = new Parser[String] {
    def apply(in: Input): ParseResult[String] = {
      if (in.atEnd) {
        val completion = MissingCompletionOrFailure(Seq(Command.Hint(" ")),
          "expected whitespace", in)
        Command.completionProposal.value = Command.completionProposal.value :+ completion
        return completion
      }
      val (source, completionMode) = in.source match {
        case CompletionRequest(source) ⇒ (source, true)
        case source ⇒ (source, false)
      }
      val offset = in.offset
      val start = offset
      if (source.length() == 0 && completionMode) {
        val completion = MissingCompletionOrFailure(Seq(Command.Hint(" ")),
          "expected whitespace", in.drop(start - offset))
        Command.completionProposal.value = Command.completionProposal.value :+ completion
        completion
      } else {
        (whiteSpace findPrefixMatchOf (source.subSequence(start, source.length))) match {
          case Some(matched) if completionMode ⇒
            if (start + matched.end == source.length())
              Success(CompletionRequest(source.subSequence(start, start + matched.end).toString),
                in.drop(start + matched.end + 1 - offset))
            else
              Success(source.subSequence(start, start + matched.end).toString,
                in.drop(start + matched.end - offset))
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
  }
  /** The leading single quote parser. */
  val sqB = ("'", Command.Hint("'", Some("The leading single quote"))): Parser[String]
  /** The trailing single quote parser. */
  val sqE = ("'", Command.Hint("'", Some("The trailing single quote"))): Parser[String]
  /** Stub parser. */
  val stubParser = new StubParser
  /** Make whiteSpace public. */
  override val whiteSpace = """[ \t]+""".r

  /** Check whether the string is completion request. */
  def isCompletionRequest(arg: CharSequence) = arg.length() > 0 && arg.charAt(arg.length() - 1) == CompletionRequest.character
  /** If true, skips anything matching `whiteSpace` starting from the current offset. */
  override def skipWhitespace = false

  /** A parser that matches a literal string. */
  implicit def commandLiteral(s: String)(implicit descriptor: Command.Descriptor): Parser[String] =
    commandLiteral(s, Command.Hint(descriptor.name, Some(descriptor.shortDescription)))
  /** A parser that matches a literal string. */
  implicit def commandLiteral(t: (String, Command.Hint)): Parser[String] =
    commandLiteral(t._1, t._2)
  /** A parser that matches a literal string. */
  implicit def commandLiteral(s: String, hint: Command.Hint): Parser[String] = new Parser[String] {
    def apply(in: Input): ParseResult[String] = {
      if (in.atEnd) {
        val missing = s
        val completionHint = if (hint.completions.isEmpty) hint.copyWithCompletion(missing) else hint
        val completion = MissingCompletionOrFailure(Seq(completionHint), "expected one of " + missing, in)
        Command.completionProposal.value = Command.completionProposal.value :+ completion
        return completion
      }
      val (source, completionMode) = in.source match {
        case CompletionRequest(source) ⇒ (source, true)
        case source ⇒ (source, false)
      }
      val offset = in.offset
      val start = handleWhiteSpace(source, offset)
      var matchLen = 0
      var matchPos = start
      while (matchLen < s.length && matchPos < source.length && s.charAt(matchLen) == source.charAt(matchPos)) {
        matchLen += 1
        matchPos += 1
      }
      if (matchLen == s.length) {
        if (completionMode) {
          if (matchPos == source.length)
            Success(CompletionRequest(s), in.drop(matchPos + 1 - offset))
          else
            Success(s, in.drop(matchPos - offset))
        } else {
          Success(s, in.drop(matchPos - offset))
        }
      } else if (matchPos == source.length) {
        // if matchPos == all text that we have then give our proposal
        val missing = s.substring(matchLen)
        val completionHint = if (hint.completions.isEmpty) hint.copyWithCompletion(missing) else hint
        val completion = MissingCompletionOrFailure(Seq(completionHint), "expected one of " + missing, in.drop(start - offset))
        Command.completionProposal.value = Command.completionProposal.value :+ completion
        completion
      } else {
        if (source.charAt(start) == CompletionRequest.character) {
          val missing = s
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
  }
  /** A parser that matches a regex string. */
  implicit def commandRegex(r: Regex)(implicit descriptor: Command.Descriptor): Parser[String] =
    commandRegex(r, Command.Hint.Container(Command.Hint(descriptor.name, Some(descriptor.shortDescription))))
  /** A parser that matches a regex string. */
  implicit def commandRegex(t: (Regex, Command.Hint.Container)): Parser[String] =
    commandRegex(t._1, t._2)
  /** A parser that matches a regex string. */
  implicit def commandRegex(r: Regex, hints: Command.Hint.Container): Parser[String] = new Parser[String] {
    def apply(in: Input): ParseResult[String] = {
      if (in.atEnd) {
        val completion = MissingCompletionOrFailure(hints(""), "string matching regex `" + r + "' expected", in)
        Command.completionProposal.value = Command.completionProposal.value :+ completion
        return completion
      }
      val source = in.source
      val offset = in.offset
      val start = handleWhiteSpace(source, offset)
      val completionRequestMode = isCompletionRequest(source)
      val subject =
        if (completionRequestMode)
          source.subSequence(start, source.length - 1).toString()
        else
          source.subSequence(start, source.length).toString()
      if (start == (source.length() - 1) && completionRequestMode) {
        val hintList = hints(subject)
        if (hintList.nonEmpty) {
          val completion = MissingCompletionOrFailure(hintList, "string matching regex `" + r + "' expected", in.drop(start - offset))
          Command.completionProposal.value = Command.completionProposal.value :+ completion
          return completion
        }
      }

      val matched = r findPrefixMatchOf subject
      val hintList = matched.map(m ⇒ hints(m.matched))
      (matched, hintList) match {
        case (Some(matched), Some(hintList)) if completionRequestMode && matched.matched == subject && hintList.nonEmpty ⇒
          // If we are at completion request mode
          // and there are some proposals
          // and this is last parser that covers whole subject
          val completion = MissingCompletionOrFailure(hintList, "string matching regex `" + r + "' expected", in.drop(start - offset))
          Command.completionProposal.value = Command.completionProposal.value :+ completion
          // But we return success, so we will collect proposal from other parsers
          Success(CompletionRequest(source.subSequence(start, start + matched.end).toString),
            in.drop(start + matched.end - offset))
        case (Some(matched), _) ⇒
          Success(source.subSequence(start, start + matched.end).toString,
            in.drop(start + matched.end - offset))
        case (None, hintList) ⇒
          val found = if (start == source.length()) "end of source" else "`" + source.charAt(start) + "'"
          if (start == source.length()) {
            val completion = MissingCompletionOrFailure(hintList getOrElse Seq.empty, "string matching regex `" + r + "' expected", in.drop(start - offset))
            Command.completionProposal.value = Command.completionProposal.value :+ completion
            completion
          } else {
            Failure("string matching regex `" + r + "' expected but " + found + " found", in.drop(start - offset))
          }
      }
    }
  }
  /** The leading single quote parser. */
  def sqB(subject: String) = ("'", Command.Hint("'", Some("The leading single quote for " + subject))): Parser[String]

  /**
   * Completion request routines.
   */
  object CompletionRequest {
    /** Special character that appends to string if we want to create completion request. */
    val character: Char = '\u0001'

    /** Convert string to completion request. */
    def apply(arg: String) = arg + character
    /** Get proposals. */
    def getProposals(buffer: String, parser: Command.parser.Parser[Any] = null): Command.Result = {
      val completionPri = {
        if (parser != null)
          Command.parse(CompletionRequest(buffer), parser)
        else
          Command.parse(CompletionRequest(buffer))
      } match {
        case Command.MissingCompletionOrFailure(list, message) ⇒
          Command.MissingCompletionOrFailure(list.distinct, message)
        case result ⇒
          result
      }
      val completionSec = if (buffer.length() > 1)
        // Ok, but there may be more...
        // Remove one character and search for append proposals...
        {
          if (parser != null)
            Command.parse(CompletionRequest(buffer.dropRight(1)), parser)
          else
            Command.parse(CompletionRequest(buffer.dropRight(1)))
        } match {
          case Command.MissingCompletionOrFailure(List(Command.Hint(None, None, Seq(" "))), message) ⇒
            Command.Failure("skip")
          case Command.MissingCompletionOrFailure(completionList, message) if completionList.nonEmpty ⇒
            val previousCharacter = Character.toString(buffer.last)
            Command.MissingCompletionOrFailure(completionList.flatMap { hint ⇒
              val completions = hint.completions.filter(_.startsWith(previousCharacter)).map(_.drop(1))
              if (completions.filter(_.trim().nonEmpty).isEmpty)
                None
              else
                Some(hint.copyWithCompletion(completions: _*))
            }.distinct, message)
          case _ ⇒
            Command.Failure("skip")
        }
      else
        Command.Failure("skip")
      (completionPri, completionSec) match {
        case (Command.MissingCompletionOrFailure(listA, _), Command.MissingCompletionOrFailure(listB, _)) ⇒
          Command.MissingCompletionOrFailure((listA ++ listB).distinct, "union")
        case (primary @ Command.MissingCompletionOrFailure(_, _), _) ⇒
          primary
        case (_, secondary @ Command.MissingCompletionOrFailure(_, _)) ⇒
          secondary
        case (primary, secondary) ⇒
          primary
      }
    }
    /** Convert completion request to string. */
    def unapply(arg: String) = if (isCompletionRequest(arg)) Some(arg.dropRight(1)) else None
  }
  /**
   * Simple stub parser.
   */
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

