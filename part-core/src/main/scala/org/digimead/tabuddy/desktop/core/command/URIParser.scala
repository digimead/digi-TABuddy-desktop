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

package org.digimead.tabuddy.desktop.core.command

import java.net.URI
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.model.serialization.Serialization
import org.digimead.tabuddy.model.serialization.transport.Transport
import scala.language.implicitConversions

/**
 * Builder of URI path argument parser.
 */
class URIParser {
  import Command.parser._
  lazy val uri = "/\\S*"

  /** Create parser for the graph location. */
  def apply(defaultFn: () ⇒ URI, hintLabelFn: () ⇒ String,
    hintDescriptionFn: () ⇒ Option[String] = () ⇒ None)(filterFn: (URI, Transport) ⇒ Boolean): Command.parser.Parser[Any] = schemes into { scheme ⇒
    commandRegex(uri.r, new HintContainer(Serialization.perScheme(scheme.takeWhile(_ != ':')),
      defaultFn, hintLabelFn, hintDescriptionFn, filterFn)) ^? {
      case CompletionRequest(uri) if { try new URI(scheme + uri).isAbsolute() catch { case e: Throwable ⇒ false } } ⇒
        new URI(scheme + uri)
      case uri if { try new URI(scheme + uri).isAbsolute() catch { case e: Throwable ⇒ false } } ⇒
        new URI(scheme + uri)
    }
  }
  /** Get available schemes. */
  def schemes = Serialization.perScheme.toSeq.sortBy(_._1) match {
    case Nil ⇒
      failure("There are no serialization transports")
    case seq ⇒
      seq.map {
        case (scheme, transport) ⇒
          commandLiteral(scheme + ":", Command.Hint(scheme + ":", Some(s"'${transport.scheme}:' transport")))
      }.reduce(_ | _)
  }

  class HintContainer(transport: Transport, defaultFn: () ⇒ URI, hintLabelFn: () ⇒ String,
    hintDescriptionFn: () ⇒ Option[String], filterFn: (URI, Transport) ⇒ Boolean) extends Command.Hint.Container {
    /** Get parser hints for user provided path. */
    def apply(arg: String): Seq[Command.Hint] = {
      val input = arg match {
        case CompletionRequest(arg) ⇒ arg.trim
        case arg ⇒ arg.trim
      }
      val default = defaultFn()
      val hintLabel = hintLabelFn()
      val hintDescription = hintDescriptionFn()
      if (input.isEmpty) {
        if (default.getScheme() == transport.scheme) {
          val path = default.toString.drop(transport.scheme.length() + 1)
          transport.isDirectory(default) match {
            case Some(true) ⇒
              if (!path.endsWith("/"))
                Seq(Command.Hint(hintLabel, hintDescription, Seq(path + "/")))
              else
                Seq(Command.Hint(hintLabel, hintDescription, Seq(path)))
            case Some(false) ⇒
              Seq(Command.Hint(hintLabel, hintDescription, Seq(path)))
            case None ⇒
              Seq(Command.Hint(hintLabel, hintDescription, Seq(path)))
          }
        } else
          Seq(Command.Hint(hintLabel, hintDescription, Seq("/")))
      } else {
        new URI(transport.scheme + ":" + input) match {
          case path if transport.isDirectory(path) == Some(true) && input.endsWith("/") ⇒
            val dirs = transport.list(path) match {
              case Some(seq) ⇒
                seq.filter(name ⇒ filterFn(transport.append(path, name), transport)).sorted
              case None ⇒
                Nil
            }
            Seq(Command.Hint(hintLabel, hintDescription, dirs))
          case path if transport.isDirectory(path) == Some(true) ⇒
            Seq(Command.Hint(hintLabel, hintDescription, Seq("/")))
          case path ⇒
            val lastSlashIndex = path.toString.lastIndexOf("/")
            if (lastSlashIndex > 0)
              path.toString.drop(lastSlashIndex)
            else
              return Seq(Command.Hint(hintLabel, hintDescription, Seq("")))
            val prefix = path.toString.drop(lastSlashIndex + 1).trim
            val beginIndex = prefix.length()
            val parent = new URI(path.toString.take(lastSlashIndex + 1).trim)
            val proposals = if (prefix.nonEmpty && transport.isDirectory(parent) == Some(true))
              transport.list(parent) match {
                case Some(seq) ⇒
                  seq.filter(name ⇒ name.startsWith(prefix) && filterFn(transport.append(parent, name), transport))
                case None ⇒
                  Seq.empty
              }
            else
              Seq.empty
            Seq(Command.Hint(hintLabel, hintDescription, proposals.map(_.substring(beginIndex))))
        }
      }
    }
  }
}

object URIParser {
  implicit def parser2implementation(c: URIParser.type): URIParser = c.inner

  def inner() = DI.implementation

  /**
   * Dependency injection routines
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** PathParser implementation. */
    lazy val implementation = injectOptional[URIParser] getOrElse new URIParser
  }
}
