/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2012-2015 Alexey Aksenov ezh@ezh.msk.ru
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

import java.util.UUID
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.definition.NLS
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.logic.payload.DSL._
import org.digimead.tabuddy.desktop.logic.payload.api.XEnumeration
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.model.{ Model, Record }
import org.digimead.tabuddy.model.element.Value
import org.digimead.tabuddy.model.element.{ Coordinate, Element }
import org.digimead.tabuddy.model.graph.{ ElementBox, Graph, Node }
import scala.collection.immutable
import scala.reflect.runtime.universe

/**
 * An enumeration.
 * The equality is based on the element reference.
 */
class Enumeration[T <: AnySRef: Manifest](
    /** The enumeration element */
    val element: Element#RelativeType,
    /** The enumeration type wrapper */
    val ptype: PropertyType[T],
    /** Fn thats do something before the instance initialization */
    /* Fn's enumeration type must be [_], not [T] because of the construction like this:
     * new Enumeration(element, ptype)(Manifest.classType(ptype.typeClass))
     * where ptype is an unknown generic type
     */
    preinitialization: Enumeration[_] ⇒ Unit = (enum: Enumeration[_]) ⇒
      // create the enumeration element if needed and set the type field
      enum.element.eSet[String](enum.getFieldIDType, enum.ptype.id.name)) extends Enumeration.Interface[T] with XLoggable {
  def this(element: Element#RelativeType, ptype: PropertyType[T], initialAvailability: Boolean,
    initialName: String, initialConstants: Set[Enumeration.Constant[T]]) = {
    this(element, ptype, (enumerationWithoutErasure) ⇒ {
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
    case Some(value) ⇒ value.get
    case _ ⇒ true
  }
  /** An enumeration name */
  val name: String = element.eGet[String](getFieldIDName) match {
    case Some(value) ⇒ value.get
    case _ ⇒ ""
  }
  /** Enumeration constants */
  val constants: Set[Enumeration.Constant[T]] = getConstants()
  /** The enumeration id */
  val id = element.eId

  /** Get explicit general enumeration. */
  def **(): Enumeration[AnyRef with java.io.Serializable] = this.asInstanceOf[Enumeration[AnyRef with java.io.Serializable]]
  /** The copy constructor */
  def copy(availability: Boolean = this.availability,
    constants: Set[Enumeration.Constant[T]] = this.constants,
    element: Element#RelativeType = this.element,
    id: Symbol = this.id,
    name: String = this.name,
    ptype: PropertyType[T] = this.ptype) =
    if (id == this.id)
      new Enumeration(element, ptype, availability, name, constants).asInstanceOf[this.type]
    else {
      element.eNode.parent match {
        case Some(parent) ⇒
          parent.freezeWrite { node ⇒
            val nodeCopy = element.eNode.copy(target = node, id = id, unique = UUID.randomUUID()): Node[_ <: Element]
            new Enumeration(nodeCopy.projection(element.eCoordinate).e.eRelative, ptype, availability, name, constants).asInstanceOf[this.type]
          }
        case None ⇒
          throw new IllegalStateException(s"Unable to copy unbinded $this.")
      }
    }
  /** Get the specific constant for the property or the first entry */
  def getConstantSafe(property: TemplateProperty[T]): Enumeration.Constant[T] = property.defaultValue match {
    case Some(default) ⇒ getConstantSafe(default)
    case None ⇒ constants.toList.sortBy(_.view).head
  }
  /** Get the specific constant for the value or the first entry */
  def getConstantSafe(value: T): Enumeration.Constant[T] =
    constants.find(_.value == value) match {
      case Some(constant) ⇒ constant
      case None ⇒ constants.toList.sortBy(_.view).head
    }
  /** Get enumeration constants */
  protected def getConstants(): Set[Enumeration.Constant[T]] = {
    var next = true
    val constants = for (i ← 0 until Enumeration.collectionMaximum if next) yield {
      element.eGet[T](getFieldIDConstantValue(i)) match {
        case Some(value) ⇒
          val alias: String = element.eGet[String](getFieldIDConstantAlias(i)).map(_.get).getOrElse("")
          val description = element.eGet[String](getFieldIDConstantDescription(i)).map(_.get).getOrElse("")
          Some(Enumeration.Constant(value.get, alias, description)(ptype, Manifest.classType(ptype.typeClass)))
        case None ⇒
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
      case (key, valueMap) ⇒ valueMap.keys.filter(_ match {
        case 'String if key == getFieldIDName ⇒ false
        case 'Boolean if key == getFieldIDAvailability ⇒ false
        case 'String if key == getFieldIDType ⇒ false
        case _ ⇒ true
      }).map(typeSymbol ⇒ (key, typeSymbol))
    }.flatten.foreach({
      case (id, typeSymbol) ⇒
        element.eRemove(id, typeSymbol)
    })
    // add new constants
    if (set.size > Enumeration.collectionMaximum)
      Enumeration.log.error("%s constant sequence too long, %d elements will be dropped".format(this, set.size - Enumeration.collectionMaximum))
    val iterator = set.toIterator
    for {
      i ← 0 until math.min(set.size, Enumeration.collectionMaximum)
      constant = iterator.next
    } {
      element.eSet(getFieldIDConstantValue(i), Some(Value.static(constant.value)))
      if (constant.alias.trim.nonEmpty)
        element.eSet(getFieldIDConstantAlias(i), constant.alias)
      if (constant.description.trim.nonEmpty)
        element.eSet(getFieldIDConstantDescription(i), constant.description)
    }
  }

  override lazy val toString = "Enumeration[%s/%s]".format(element.eId, ptype.id.name)
}

object Enumeration extends XLoggable {
  type propertyMap = immutable.HashMap[TemplatePropertyGroup, Seq[TemplateProperty[_ <: AnySRef]]]
  /** Constants limit per enumeration */
  val collectionMaximum = 100

  /** The deep comparison of two enumerations. */
  def compareDeep(a: Enumeration[_ <: AnySRef], b: Enumeration[_ <: AnySRef]): Boolean =
    (a eq b) || (a == b && a.ptype == b.ptype && a.availability == b.availability && a.name == b.name &&
      a.id == b.id && (a.constants, b.constants).zipped.forall(compareDeep(_, _)))
  /** The deep comparison of two constants. */
  def compareDeep(a: Enumeration.Constant[_ <: AnySRef], b: Enumeration.Constant[_ <: AnySRef]): Boolean =
    (a eq b) || (a.value == b.value && a.alias == b.alias && a.description == b.description)
  /** The factory for the element that contains enumeration data. */
  def factory(graph: Graph[_ <: Model.Like], elementId: Symbol, attach: Boolean = true): Element =
    PredefinedElements.eEnumeration(graph).eNode.safeWrite { containerNode ⇒
      containerNode.createChild[Record](elementId, UUID.randomUUID(), attach).safeWrite { child ⇒
        ElementBox.getOrCreate[Record](Coordinate.root, child, Record.scope, graph.node.rootBox.serialization)
      }
    }
  /** Return enumeration element type wrapper id. */
  def getElementTypeWrapperId(e: Element): Option[Symbol] = e.eGet[String]('type).map(t ⇒ Symbol(t.get))
  /** Get type name*/
  def getEnumerationName(marker: GraphMarker, enumerationId: Symbol) = marker.safeRead { state ⇒
    App.execNGet { state.payload.enumerations.get(enumerationId) } match {
      case Some(enumeration) ⇒ enumeration.name
      case None ⇒ enumerationId.name
    }
  }
  /** Get translation by alias. */
  def getConstantTranslation(constant: Constant[_ <: AnySRef]): String =
    if (constant.alias.startsWith("*"))
      NLS.messages.get(constant.alias.substring(1)).getOrElse {
        val result = constant.alias.substring(1)
        val trimmed = if (result.endsWith("_text"))
          result.substring(0, result.length - 5)
        else
          result
        trimmed(0).toString.toUpperCase + trimmed.substring(1)
      }
    else if (constant.alias.isEmpty())
      constant.ptype.asInstanceOf[PropertyType[AnySRef]].valueToString(constant.value)
    else
      constant.alias
  /** Get all enumerations. */
  def load(marker: GraphMarker): Set[Enumeration[_ <: AnySRef]] = marker.safeRead { state ⇒
    log.debug("Load enumerations list for graph " + state.graph)
    val container = PredefinedElements.eEnumeration(state.graph)
    container.eNode.freezeRead(_.children.map(_.rootBox.e).map { element ⇒
      Enumeration.getElementTypeWrapperId(element).flatMap(PropertyType.container.get) match {
        case Some(ptype) ⇒
          log.debug("load enumeration %s with type %s".format(element.eId.name, ptype.id))
          // provide type information at runtime
          Some(new Enumeration(element.eRelative, ptype)(Manifest.classType(ptype.typeClass))).
            asInstanceOf[Option[Enumeration[_ <: AnySRef]]]
        case None ⇒
          log.warn("unable to find apropriate type wrapper for enumeration " + element)
          None
      }
    }).flatten.toSet
  }
  /** Update only modified enumerations. */
  def save(marker: GraphMarker, enumerations: Set[Enumeration[_ <: AnySRef]]) = marker.safeRead { state ⇒
    log.debug("Save enumeration list for graph " + state.graph)
    val oldEnums = App.execNGet { state.payload.enumerations.values.toSet }
    val deleted = oldEnums.filterNot(oldEnum ⇒ enumerations.exists(compareDeep(oldEnum, _)))
    val added = enumerations.filterNot(newEnum ⇒ oldEnums.exists(compareDeep(newEnum, _)))
    if (deleted.nonEmpty) {
      log.debug("delete Set(%s)".format(deleted.mkString(", ")))
      App.execNGet { deleted.foreach { enumeration ⇒ state.payload.enumerations.remove(enumeration.id) } }
      deleted.foreach(enumeration ⇒ enumeration.element.eNode.parent.foreach(_.safeWrite { _ -= enumeration.element.eNode }))
    }
    if (added.nonEmpty) {
      log.debug("add Set(%s)".format(added.mkString(", ")))
      added.foreach(_.element.eNode.attach())
      App.execNGet { added.foreach { enumeration ⇒ state.payload.enumerations(enumeration.id) = enumeration } }
    }
  }

  /**
   * The enumeration constant class
   * The equality is based on constant value
   */
  case class Constant[T <: AnySRef](val value: T,
      val alias: String, val description: String)(val ptype: PropertyType[T], implicit val m: Manifest[T]) extends XEnumeration.Constant[T, PropertyType[T]] {
    type ConstantPropertyType = PropertyType[T]
    /** The enumeration constant user's representation. */
    lazy val view: String = Enumeration.getConstantTranslation(this)

    def canEqual(other: Any) =
      other.isInstanceOf[org.digimead.tabuddy.desktop.logic.payload.Enumeration.Constant[_]]
    override def equals(other: Any) = other match {
      case that: org.digimead.tabuddy.desktop.logic.payload.Enumeration.Constant[_] ⇒
        (this eq that) || {
          that.canEqual(this) &&
            value == that.value
        }
      case _ ⇒ false
    }
    override def hashCode() = value.hashCode
  }
  /**
   * The base enumeration interface
   * The equality is based on element reference
   */
  private[Enumeration] trait Interface[T <: AnySRef] extends XEnumeration[T, PropertyType[T], TemplateProperty[T], Constant[T]] {
    /** Availability flag for user (some enumeration may exists, but not involved in new element creation). */
    val availability: Boolean
    /** The sequence of enumeration constants. */
    val constants: Set[Constant[T]]
    /** The enumeration element. */
    val element: Element#RelativeType
    /** The enumeration id/name. */
    val id: Symbol
    /** The enumeration name. */
    val name: String
    /** The type wrapper. */
    val ptype: PropertyType[T]

    /** The copy constructor. */
    def copy(availability: Boolean = this.availability,
      constants: Set[Constant[T]] = this.constants,
      element: Element#RelativeType = this.element,
      id: Symbol = this.id,
      name: String = this.name,
      ptype: PropertyType[T] = this.ptype): this.type
    /** Get the specific constant for the property or the first entry. */
    def getConstantSafe(property: TemplateProperty[T]): Constant[T]
    /** Get the specific constant for the value or the first entry. */
    def getConstantSafe(value: T): Constant[T]
    /** Returns an identificator for the availability field. */
    def getFieldIDAvailability() = 'availability
    /** Returns an identificator for the name field. */
    def getFieldIDName() = 'name
    /** Returns an identificator for the type wrapper field. */
    def getFieldIDType() = 'type // hardcoded in getElementTypeWrapperId
    /** Returns an identificator for the value field of the enumeration constant. */
    def getFieldIDConstantValue(n: Int) = Symbol(n + "_enumeration")
    /** Returns an identificator for the alias field of the enumeration constant. */
    def getFieldIDConstantAlias(n: Int) = Symbol(n + "_alias")
    /** Returns an identificator for the description field of the enumeration constant. */
    def getFieldIDConstantDescription(n: Int) = Symbol(n + "_description")

    def canEqual(other: Any) = other.isInstanceOf[Interface[_]]
    override def equals(other: Any) = other match {
      case that: Interface[_] ⇒
        (this eq that) || {
          that.canEqual(this) &&
            element.eReference == that.element.eReference
        }
      case _ ⇒ false
    }
    override def hashCode() = element.eReference.hashCode
    override lazy val toString = "Enumeration[%s]%s".format(ptype.id, element.eId)
  }
}
