/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2012-2013 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.logic.payload

import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Record
import org.digimead.tabuddy.model.dsl.{ DSL ⇒ DSLCore }
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.predef.Note
import org.digimead.tabuddy.model.predef.Task
import scala.language.implicitConversions

object DSL extends DSLCore
  with Model.DSL
  with Record.DSL
  with Note.DSL
  with Task.DSL {
  implicit def e2DSL(e: Element) = new ElementGenericDSL(e)
  implicit def me2DSL(me: Element.Relative[_ <: Element]) = new ElementGenericDSL(me.absolute)
  implicit def eRecord2DSL[A <: Record.Like](e: A) = new RecordSpecificDSL(e)
  implicit def meRecord2DSL[A <: Record.Like](me: Element.Relative[A]) = new RecordSpecificDSL(me.absolute)
  implicit def eNote2DSL[A <: Note.Like](e: A) = new NoteSpecificDSL(e)
  implicit def meNote2DSL[A <: Note.Like](me: Element.Relative[A]) = new NoteSpecificDSL(me.absolute)
  implicit def eTask2DSL[A <: Task.Like](e: A) = new TaskSpecificDSL(e)
  implicit def meTask2DSL[A <: Task.Like](me: Element.Relative[A]) = new TaskSpecificDSL(me.absolute)
  implicit def eModel2DSL[A <: Model.Like](e: A) = new ModelSpecificDSL(e)
  implicit def meModel2DSL[A <: Model.Like](me: Element.Relative[A]) = new ModelSpecificDSL(me.absolute)

  implicit val modelStashClass: Class[_ <: Model.Stash] = classOf[Model.Stash]
  implicit val recordStashClass: Class[_ <: Record.Stash] = classOf[Record.Stash]
  implicit val noteStashClass: Class[_ <: Note.Stash] = classOf[Note.Stash]
  implicit val taskStashClass: Class[_ <: Task.Stash] = classOf[Task.Stash]

  implicit def relative2absolute[A <: Element](relative: Element.Relative[A]): A = relative.absolute
}
