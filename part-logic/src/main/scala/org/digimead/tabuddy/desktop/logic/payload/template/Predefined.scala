/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2012-2014 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.logic.payload.template

import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.logic.payload.{ ElementTemplate, PropertyType, TemplateProperty, TemplatePropertyGroup }
import org.digimead.tabuddy.desktop.logic.payload.DSL._
import org.digimead.tabuddy.model.Record
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.predef.{ Note, Task }
import scala.collection.immutable
import scala.language.implicitConversions

/**
 * Predefined element template factories.
 */
class Predefined extends Loggable {
  /**
   * Predefined record template.
   */
  def record(container: Record.Like): ElementTemplate = {
    val id = Record.scope.modificator
    val factory = (container: Element, id: Symbol, scopeModificator: Symbol) ⇒
      container | RecordLocation(id = id, scope = new Record.Scope(scopeModificator))
    container elementAt RecordLocation(id = id, scope = new Record.Scope(id)) match {
      case Some(element) ⇒
        log.debug("Get exists predefined Record template")
        new ElementTemplate(element.eRelative, factory)
      case None ⇒
        log.debug("Initialize new predefined Record template")
        val element = factory(container, id, id)
        new ElementTemplate(element.eRelative, factory, "Predefined custom element", true,
          immutable.HashMap(TemplatePropertyGroup.default -> Seq(new TemplateProperty[String]('name, false, None, PropertyType.get('String)))))
    }
  }
  /**
   * Predefined note template.
   */
  def note(container: Record.Like) = {
    val id = Note.scope.modificator
    val factory = (container: Element, id: Symbol, scopeModificator: Symbol) ⇒
      container | NoteLocation(id = id, scope = new Note.Scope(scopeModificator))
    container elementAt NoteLocation(id = id, scope = new Note.Scope(id)) match {
      case Some(element) ⇒
        log.debug("Get exists predefined Record template")
        new ElementTemplate(element.eRelative, factory)
      case None ⇒
        log.debug("Initialize new predefined Record template")
        val element = factory(container, id, id)
        new ElementTemplate(element.eRelative, factory, "Predefined note element", true,
          immutable.HashMap(TemplatePropertyGroup.default -> Seq(new TemplateProperty[String]('name, false, None, PropertyType.get('String)))))
    }
  }
  /**
   * Predefined task template.
   */
  def task(container: Record.Like) = {
    val id = Task.scope.modificator
    val factory = (container: Element, id: Symbol, scopeModificator: Symbol) ⇒
      container | TaskLocation(id = id, scope = new Task.Scope(scopeModificator))
    container elementAt TaskLocation(id = id, scope = new Task.Scope(id)) match {
      case Some(element) ⇒
        log.debug("Get exists predefined Task template")
        new ElementTemplate(element.eRelative, factory)
      case None ⇒
        log.debug("Initialize new predefined Task template")
        val element = factory(container, id, id)
        new ElementTemplate(element.eRelative, factory, "Predefined task element", true,
          immutable.HashMap(TemplatePropertyGroup.default -> Seq(new TemplateProperty[String]('name, false, None, PropertyType.get('String)))))
    }
  }
}

object Predefined {
  implicit def config2implementation(c: Predefined.type): Predefined = c.inner

  def inner = DI.implementation

  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Predefined implementation. */
    lazy val implementation = injectOptional[Predefined] getOrElse new Predefined
  }
}
