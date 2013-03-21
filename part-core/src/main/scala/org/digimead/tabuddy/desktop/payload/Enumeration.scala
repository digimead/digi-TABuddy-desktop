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
import org.digimead.digi.lib.log.Loggable
import org.digimead.tabuddy.desktop.Resources
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Record
import org.digimead.tabuddy.model.dsl.DSLType
import org.digimead.tabuddy.model.element.Context
import org.digimead.tabuddy.model.element.Coordinate
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.element.Stash
import org.digimead.tabuddy.model.element.Value
import org.digimead.tabuddy.desktop.payload.DSL._
import org.digimead.tabuddy.desktop.Main
import org.digimead.tabuddy.desktop.Data

class Enumeration[T <: AnyRef with java.io.Serializable: Manifest](
  /** The enumeration element */
  val element: Element.Generic,
  /** The enumeration type wrapper */
  val ptype: PropertyType[T],
  /** Fn thats do something before the instance initialization */
  /* Fn's enumeration type must be [_], not [T] because of the construction like this:
   * new Enumeration(element, ptype)(Manifest.classType(ptype.typeClass))
   * where ptype is an unknown generic type
   */
  preinitialization: Enumeration[_] => Unit = (enum: Enumeration[_]) =>
    // create the enumeration element if needed and set the type field
    enum.element.eSet[String](enum.getFieldIDType, enum.ptype.id.name)) extends Enumeration.Interface[T] with Loggable {
  def this(element: Element.Generic, ptype: PropertyType[T], initialAvailability: Boolean,
    initialName: String, initialConstants: Set[Enumeration.Constant[T]]) = {
    this(element, ptype, (enumerationWithoutErasure) => {
      val enumeration = enumerationWithoutErasure.asInstanceOf[Enumeration[T]]
      // This code is invoked before availability, name and properties fields initialization
      if (enumeration.element.eGet[java.lang.Boolean](enumeration.getFieldIDAvailability).map(_.get) != Some(initialAvailability))
        enumeration.element.eSet(enumeration.getFieldIDAvailability, initialAvailability)
      if (enumeration.element.eGet[String](enumeration.getFieldIDName).map(_.get) != Some(initialName))
        enumeration.element.eSet(enumeration.getFieldIDName, initialName, "")
      val existsConstants = enumeration.getConstants.toList.sortBy(_.hashCode).toSet
      val added = initialConstants.toList.sortBy(_.hashCode).toSet
      if (existsConstants.size != added.size || !(existsConstants, added).zipped.forall(Enumeration.compareDeep(_, _)))
        enumeration.setConstants(initialConstants)
    })
  }
  preinitialization(this)
  log.debug("create new " + this)

  /** The flag that indicates whether enumeration available for user or not */
  val availability: Boolean = element.eGet[java.lang.Boolean](getFieldIDAvailability) match {
    case Some(value) => value.get
    case _ => true
  }
  /** An enumeration name */
  val name: String = element.eGet[String](getFieldIDName) match {
    case Some(value) => value.get
    case _ => ""
  }
  /** Enumeration constants */
  val constants: Set[Enumeration.Constant[T]] = getConstants()
  /** The enumeration id */
  val id = element.eId

  /** The copy constructor */
  def copy(availability: Boolean = this.availability,
    name: String = this.name,
    element: Element.Generic = this.element,
    ptype: PropertyType[T] = this.ptype,
    id: Symbol = this.id,
    constants: Set[Enumeration.Constant[T]] = this.constants) =
    if (id == this.id)
      new Enumeration(element, ptype, availability, name, constants).asInstanceOf[this.type]
    else {
      element.asInstanceOf[Element[Stash]].eStash = element.eStash.copy(id = id)
      new Enumeration(element, ptype, availability, name, constants).asInstanceOf[this.type]
    }
  /** Get the specific constant for the property or the first entry */
  def getConstantSafe(property: TemplateProperty[T]): Enumeration.Constant[T] = property.defaultValue match {
    case Some(default) => getConstantSafe(default)
    case None => constants.toList.sortBy(_.view).head
  }
  /** Get the specific constant for the value or the first entry */
  def getConstantSafe(value: T): Enumeration.Constant[T] =
    constants.find(_.value == value) match {
      case Some(constant) => constant
      case None => constants.toList.sortBy(_.view).head
    }
  /** Get enumeration constants */
  protected def getConstants(): Set[Enumeration.Constant[T]] = {
    var next = true
    val constants = for (i <- 0 until Enumeration.collectionMaximum if next) yield {
      element.eGet[T](getFieldIDConstantValue(i)) match {
        case Some(value) =>
          val alias: String = element.eGet[String](getFieldIDConstantAlias(i)).map(_.get).getOrElse("")
          val description = element.eGet[String](getFieldIDConstantDescription(i)).map(_.get).getOrElse("")
          Some(Enumeration.Constant(value.get, alias, description)(ptype, Manifest.classType(ptype.typeClass)))
        case None =>
          next = false
          None
      }
    }
    constants.flatten.toList.sortBy(_.hashCode).toSet
  }
  /** Set enumeration constants */
  protected def setConstants(set: Set[Enumeration.Constant[T]]) = {
    // remove all element properties except Availability, Label, Type
    val toDelete = element.eStash.property.toSeq.map {
      case (key, valueMap) => valueMap.keys.filter(_ match {
        case 'String if key == getFieldIDName => false
        case 'Boolean if key == getFieldIDAvailability => false
        case 'String if key == getFieldIDType => false
        case _ => true
      }).map(typeSymbol => (key, typeSymbol))
    }.flatten.foreach({
      case (id, typeSymbol) =>
        element.eRemove(id, typeSymbol)
    })
    // add new constants
    if (set.size > Enumeration.collectionMaximum)
      Enumeration.log.error("%s constant sequence too long, %d elements will be dropped".format(this, set.size - Enumeration.collectionMaximum))
    val iterator = set.toIterator
    for {
      i <- 0 until math.min(set.size, Enumeration.collectionMaximum)
      constant = iterator.next
    } {
      element.eSet(getFieldIDConstantValue(i), Value.static(constant.value))
      if (constant.alias.trim.nonEmpty)
        element.eSet(getFieldIDConstantAlias(i), constant.alias)
      if (constant.description.trim.nonEmpty)
        element.eSet(getFieldIDConstantDescription(i), constant.description)
    }
  }

  override def toString() = "Enumeration[%s/%s]%s".format(ptype.id, scala.reflect.runtime.universe.typeOf[T], element.eId)
}

object Enumeration extends DependencyInjection.PersistentInjectable with Loggable {
  type propertyMap = immutable.HashMap[TemplatePropertyGroup, Seq[TemplateProperty[_ <: AnyRef with java.io.Serializable]]]
  implicit def bindingModule = DependencyInjection()
  /** Constants limit per enumeration */
  val collectionMaximum = 100

  /** Enumerations container */
  def container(): Element.Generic = inject[Record.Interface[_ <: Record.Stash]]("eEnumeration")
  /** The deep comparison of two enumerations */
  def compareDeep(a: Interface[_ <: AnyRef with java.io.Serializable], b: Interface[_ <: AnyRef with java.io.Serializable]): Boolean =
    (a eq b) || (a == b && a.ptype == b.ptype && a.availability == b.availability && a.name == b.name &&
      a.id == b.id && (a.constants, b.constants).zipped.forall(compareDeep(_, _)))
  /** The deep comparison of two constants */
  def compareDeep(a: Constant[_ <: AnyRef with java.io.Serializable], b: Constant[_ <: AnyRef with java.io.Serializable]): Boolean =
    (a eq b) || (a.value == b.value && a.alias == b.alias && a.description == b.description)
  /** The factory for the element that contains enumeration data */
  def factory(container: Element.Generic, elementId: Symbol): Element.Generic = container | RecordLocation(elementId)
  /** The factory for the element that contains enumeration data */
  def factory(elementId: Symbol): Element.Generic = Record(elementId, Coordinate.root.coordinate)
  /** Return enumeration element type wrapper id */
  def getElementTypeWrapperId(e: Element.Generic): Option[Symbol] = e.eGet[String]('type).map(t => Symbol(t.get))
  /** Get type name*/
  def getEnumerationName(enumerationId: Symbol) = Main.execNGet {
    Data.enumerations.get(enumerationId) match {
      case Some(enumeration) => enumeration.name
      case None => enumerationId.name
    }
  }
  /** Get translation by alias */
  def getConstantTranslation(constant: Constant[_ <: AnyRef with java.io.Serializable]): String =
    if (constant.alias.startsWith("*"))
      Resources.messages.get(constant.alias.substring(1)).getOrElse {
        val result = constant.alias.substring(1)
        val trimmed = if (result.endsWith("_text"))
          result.substring(0, result.length - 5)
        else
          result
        trimmed(0).toString.toUpperCase + trimmed.substring(1)
      }
    else if (constant.alias.isEmpty())
      constant.ptype.asInstanceOf[PropertyType[AnyRef with java.io.Serializable]].valueToString(constant.value)
    else
      constant.alias
  /** Get all enumerations. */
  def load(): Set[Enumeration.Interface[_ <: AnyRef with java.io.Serializable]] = {
    log.debug("load enumerations list for model " + Model.eId)
    Enumeration.container.eChildren.map { element =>
      Enumeration.getElementTypeWrapperId(element).flatMap(PropertyType.container.get) match {
        case Some(ptype) =>
          log.debug("load enumeration %s with type %s".format(element.eId.name, ptype.id))
          // provide type information at runtime
          Some(new Enumeration(element, ptype)(Manifest.classType(ptype.typeClass))).
            asInstanceOf[Option[Enumeration[_ <: AnyRef with java.io.Serializable]]]
        case None =>
          log.warn("unable to find apropriate type wrapper for enumeration " + element)
          None
      }
    }.flatten.toSet
  }
  /** Update only modified enumeration */
  def save(enumerations: Set[Enumeration.Interface[_ <: AnyRef with java.io.Serializable]]) = Main.exec {
    log.debug("save enumeration list for model " + Model.eId)
    val oldEnums = Data.enumerations.values
    val deleted = oldEnums.filterNot(oldEnum => enumerations.exists(compareDeep(oldEnum, _)))
    val added = enumerations.filterNot(newEnum => oldEnums.exists(compareDeep(newEnum, _)))
    if (deleted.nonEmpty) {
      log.debug("delete Set(%s)".format(deleted.mkString(", ")))
      deleted.foreach { enumeration =>
        Data.enumerations.remove(enumeration.id)
        container.eChildren -= enumeration.element
      }
    }
    if (added.nonEmpty) {
      log.debug("add Set(%s)".format(added.mkString(", ")))
      added.foreach { enumeration =>
        container.eChildren += enumeration.element
        Data.enumerations(enumeration.id) = enumeration
      }
    }
  }

  def commitInjection() {}
  def updateInjection() {}

  /**
   * The enumeration constant class
   * The equality is based on constant value
   */
  case class Constant[T <: AnyRef with java.io.Serializable](val value: T,
    val alias: String, val description: String)(val ptype: PropertyType[T], implicit val m: Manifest[T]) {
    /** The enumeration constant user's representation */
    lazy val view: String = Enumeration.getConstantTranslation(this)

    def canEqual(other: Any) =
      other.isInstanceOf[org.digimead.tabuddy.desktop.payload.Enumeration.Constant[_]]
    override def equals(other: Any) = other match {
      case that: org.digimead.tabuddy.desktop.payload.Enumeration.Constant[_] =>
        (this eq that) || {
          that.canEqual(this) &&
            value == that.value
        }
      case _ => false
    }
    override def hashCode() = value.hashCode
  }
  /**
   * The base enumeration interface
   * The equality is based on element reference
   */
  trait Interface[T <: AnyRef with java.io.Serializable] {
    /** Availability flag for user (some enumeration may exists, but not involved in new element creation) */
    val availability: Boolean
    /** The enumeration name */
    val name: String
    /** The enumeration element */
    val element: Element.Generic
    /** The enumeration id/name */
    val id: Symbol
    /** The type wrapper */
    val ptype: PropertyType[T]
    /**
     * Sequence of enumeration constants
     */
    val constants: Set[Constant[T]]

    /** Convert enumeration to generic with AnyRef type */
    def generic = this.asInstanceOf[Interface[AnyRef with java.io.Serializable]]
    /** The copy constructor */
    def copy(availability: Boolean = this.availability,
      name: String = this.name,
      element: Element.Generic = this.element,
      ptype: PropertyType[T] = this.ptype,
      id: Symbol = this.id,
      constants: Set[Constant[T]] = this.constants): this.type
    /** Get the specific constant for the property or the first entry */
    def getConstantSafe(property: TemplateProperty[T]): Enumeration.Constant[T]
    /** Get the specific constant for the value or the first entry */
    def getConstantSafe(value: T): Enumeration.Constant[T]
    /** Returns an identificator for the availability field */
    def getFieldIDAvailability() = 'availability
    /** Returns an identificator for the name field */
    def getFieldIDName() = 'name
    /** Returns an identificator for the type wrapper field */
    def getFieldIDType() = 'type // hardcoded in getElementTypeWrapperId
    /** Returns an identificator for the value field of the enumeration constant */
    def getFieldIDConstantValue(n: Int) = Symbol(n + "_enumeration")
    /** Returns an identificator for the alias field of the enumeration constant */
    def getFieldIDConstantAlias(n: Int) = Symbol(n + "_alias")
    /** Returns an identificator for the description field of the enumeration constant */
    def getFieldIDConstantDescription(n: Int) = Symbol(n + "_description")

    def canEqual(other: Any) =
      other.isInstanceOf[org.digimead.tabuddy.desktop.payload.Enumeration.Interface[_]]
    override def equals(other: Any) = other match {
      case that: org.digimead.tabuddy.desktop.payload.Enumeration.Interface[_] =>
        (this eq that) || {
          that.canEqual(this) &&
            element.eReference == that.element.eReference
        }
      case _ => false
    }
    override def hashCode() = element.eReference.hashCode
    override def toString() = "Enumeration[%s]%s".format(ptype.id, element.eId)
  }
}
