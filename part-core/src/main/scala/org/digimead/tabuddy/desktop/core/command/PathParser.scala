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

import java.io.File
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.tabuddy.desktop.core.definition.command.Command
import scala.language.implicitConversions
import scala.util.DynamicVariable

/**
 * Path argument parser builder.
 */
class PathParser {
  import Command.parser._
  /** Thread local cache with current default location. */
  protected val localDefault = new DynamicVariable[Option[File]](None)
  /** Thread local cache with listFiles filter. */
  protected val localFilter = new DynamicVariable[Option[File ⇒ Boolean]](None)
  /** Thread local cache with hint description. */
  protected val localHintDescription = new DynamicVariable[Option[String]](None)
  /** Thread local cache with hint label. */
  protected val localHintLabel = new DynamicVariable[Option[String]](None)
  /** Thread local cache with selected location. */
  protected val localSelected = new DynamicVariable[Option[File]](None)

  /** Create parser for the graph location. */
  def apply(default: () ⇒ File, hintLabel: () ⇒ String, hintDescription: () ⇒ Option[String] = () ⇒ None,
    keep: Boolean = false)(filter: File ⇒ Boolean): Command.parser.Parser[Any] = {
    sp ^^ { result ⇒
      // clear thread local values at the beginning
      threadLocalClear()
      localDefault.value = Some(default())
      localFilter.value = Some(filter)
      localHintDescription.value = hintDescription()
      localHintLabel.value = Some(hintLabel())
    }
  } ~> {
    commandRegex(".*".r, HintContainer) ^^ { result ⇒
      val file = new File(result)
      if (keep)
        localSelected.value = Some(file)
      else
        threadLocalClear()
      file
    }
  }
  /** Clear thread local values. */
  def threadLocalClear() = {
    localDefault.value = None
    localFilter.value = None
    localHintDescription.value = None
    localHintLabel.value = None
    localSelected.value = None
  }

  /** Get root from the file. */
  def getRoot(file: File): File = Option(file.getParentFile()) match {
    case Some(parent) ⇒ getRoot(parent)
    case None ⇒ file
  }

  object HintContainer extends Command.Hint.Container {
    /** Get parser hints for user provided path. */
    def apply(arg: String): Seq[Command.Hint] = {
      for {
        default ← localDefault.value
        filter ← localFilter.value
        hintLabel ← localHintLabel.value
      } yield {
        if (arg.trim.isEmpty) {
          if (default.isDirectory() && !default.toString().endsWith(File.separator))
            return Seq(Command.Hint(hintLabel, localHintDescription.value, Seq(default.toString() + File.separator)))
          else
            return Seq(Command.Hint(hintLabel, localHintDescription.value, Seq(default.toString())))
        }
        val hint = new File(arg.trim()) match {
          case path if path.isDirectory() && arg.endsWith(File.separator) ⇒
            val dirs = path.listFiles().filter(filter).sortBy(_.getName)
            Command.Hint(hintLabel, localHintDescription.value, dirs.map(_.getName()))
          case path ⇒
            val prefix = path.getName()
            val beginIndex = prefix.length()
            val parent = path.getParentFile()
            val dirs = if (parent.isDirectory())
              path.getParentFile().listFiles().filter(f ⇒ f.getName().startsWith(prefix) && filter(f))
            else
              Array[File]()
            Command.Hint(hintLabel, localHintDescription.value, dirs.map(_.getName().substring(beginIndex) + File.separator))
        }
        Seq(hint)
      }
    } getOrElse Seq()
  }
}

object PathParser {
  implicit def parser2implementation(c: PathParser.type): PathParser = c.inner

  def inner() = DI.implementation

  /**
   * Dependency injection routines
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** GraphParser implementation. */
    lazy val implementation = injectOptional[PathParser] getOrElse new PathParser
  }
}
