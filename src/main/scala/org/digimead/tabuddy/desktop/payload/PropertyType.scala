/**
 * This file is part of the TABuddy project.
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

package org.digimead.tabuddy.desktop.payload

import scala.collection.immutable

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.log.Loggable
import org.digimead.tabuddy.desktop.support.Validator
import org.digimead.tabuddy.desktop.support.WritableValue
import org.digimead.tabuddy.model.dsl.DSLType
import org.eclipse.swt.events.VerifyEvent
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.ui.forms.widgets.FormToolkit

/**
 * Base class of the handler for the property of the particular type
 * The equality is based on id: Symbol
 */
trait PropertyType[T <: AnyRef with java.io.Serializable] {
  /** The type wrapper name */
  val id: Symbol
  /** The type class */
  val typeClass: Class[T]
  /** The type symbol */
  lazy val typeSymbol: Symbol = DSLType.classSymbolMap(typeClass)

  /** Create an editor for the given type */
  def createEditor(initial: Option[T]): PropertyType.Editor[T]
  /** Create a provider for the given type */
  def createProvider(): PropertyType.Provider[T]
  /** Create a viewer for the given type */
  def createViewer(initial: Option[T]): PropertyType.Viewer[T]
  /** Returns an iterator for the new value generation */
  def createValues: Iterator[T]

  def canEqual(other: Any) =
    other.isInstanceOf[org.digimead.tabuddy.desktop.payload.PropertyType[_]]
  override def equals(other: Any) = other match {
    case that: org.digimead.tabuddy.desktop.payload.PropertyType[_] =>
      (this eq that) || {
        that.canEqual(this) &&
          id == that.id
      }
    case _ => false
  }
  override def hashCode() = id.hashCode
  override def toString() = s"PropertyType[$id]"
}

object PropertyType extends DependencyInjection.PersistentInjectable with Loggable {
  implicit def bindingModule = DependencyInjection()
  /** Predefined element property types that are available for this application */
  @volatile private var types: immutable.HashMap[Symbol, PropertyType[_ <: AnyRef with java.io.Serializable]] =
    immutable.HashMap(inject[Seq[PropertyType[_ <: AnyRef with java.io.Serializable]]].map(n => (n.id, n)): _*)
  assert(types.nonEmpty, "unable to start application with empty properyTypes map")
  types.values.foreach(ptype => log.debug("register property handler %s -> %s".format(ptype.typeSymbol, ptype.id.name)))

  /** Get the default type class (for new element property, for example) */
  def defaultType(): PropertyType[_ <: AnyRef with java.io.Serializable] = types.get('String).getOrElse(types.head._2)
  /** Get type wrapper map */
  def container = types
  /** Get type wrapper of the specific type */
  def get[T <: AnyRef with java.io.Serializable](id: Symbol) = types(id).asInstanceOf[PropertyType[T]]

  def commitInjection() {}
  def updateInjection() {
    val result = inject[immutable.HashMap[Class[_ <: AnyRef with java.io.Serializable], PropertyType[_ <: AnyRef with java.io.Serializable]]]
    types = immutable.HashMap(inject[Seq[PropertyType[_ <: AnyRef with java.io.Serializable]]].map(n => (n.id, n)): _*)
    assert(types.nonEmpty, "unable to start application with empty properyTypes map")
    types.values.foreach(ptype => log.debug("register property handler %s -> %s".format(ptype.typeSymbol, ptype.id.name)))
  }

  /**
   * Element property provider
   */
  trait Provider[T <: AnyRef with java.io.Serializable] extends Viewer[T] {
  }
  /**
   * Element property editor
   */
  trait Editor[T <: AnyRef with java.io.Serializable] extends Viewer[T] {
    /** Add the validator */
    def addValidator(control: Control, showOnlyOnFocus: Boolean = true): Option[Validator]
    /** The validator function */
    def validate(validator: Validator, event: VerifyEvent): Unit
  }
  /**
   * Element property viewer
   */
  trait Viewer[T <: AnyRef with java.io.Serializable] {
    /** The property representing the UI control value */
    val data: WritableValue[T]
    /** Get an UI control */
    def createControl(parent: Composite, style: Int): Control = createControl(parent, style, 50)
    /** Get an UI control */
    def createControl(parent: Composite, style: Int, updateDelay: Int): Control
    /** Get an UI control */
    def createControl(toolkit: FormToolkit, parent: Composite, style: Int): Control = createControl(toolkit, parent, style, 50)
    /** Get an UI control */
    def createControl(toolkit: FormToolkit, parent: Composite, style: Int, updateDelay: Int): Control
    /** Returns true if the data is empty, false otherwise. */
    def isEmpty: Boolean
  }
}
