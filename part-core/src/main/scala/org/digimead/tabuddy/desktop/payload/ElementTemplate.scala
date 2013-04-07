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

import scala.Array.canBuildFrom
import scala.Option.option2Iterable
import scala.collection.immutable

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.tabuddy.desktop.Data
import org.digimead.tabuddy.desktop.Main
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Record
import org.digimead.tabuddy.model.dsl.DSLType
import org.digimead.tabuddy.model.dsl.DSLType.dsltype2implementation
import org.digimead.tabuddy.model.element.Context
import org.digimead.tabuddy.model.element.Coordinate
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.element.Stash
import org.digimead.tabuddy.model.element.Value
import org.digimead.tabuddy.model.element.Value.bool2value
import org.digimead.tabuddy.model.element.Value.string2value
import org.digimead.tabuddy.model.predef.Note
import org.digimead.tabuddy.model.predef.Task

import org.digimead.tabuddy.desktop.payload.DSL._

class ElementTemplate(
  /** The template element */
  val element: Element.Generic,
  /** The factory for the element that contains template data */
  val factory: (Element.Generic, Symbol, Symbol) => Element.Generic,
  /** Fn thats do something before the instance initialization */
  preinitialization: ElementTemplate => Unit = tpl => {}) extends ElementTemplate.Interface with Loggable {
  def this(element: Element.Generic, factory: (Element.Generic, Symbol, Symbol) => Element.Generic,
    initialName: String, initialAvailability: Boolean, initialProperties: ElementTemplate.propertyMap) = {
    this(element, factory, (template) => {
      // This code is invoked before availability, name and properties fields initialization
      if (template.element.eGet[java.lang.Boolean](template.getFieldIDAvailability).map(_.get) != Some(initialAvailability))
        template.element.eSet(template.getFieldIDAvailability, initialAvailability)
      if (template.element.eGet[String](template.getFieldIDName).map(_.get) != Some(initialName))
        template.element.eSet(template.getFieldIDName, initialName, "")
      if (!ElementTemplate.compareDeep(template.getProperties(), initialProperties))
        initialProperties.foreach {
          case (group, properties) =>
            properties.foreach(property => template.setProperty(property.id, property.ptype, group, Some(property)))
        }
    })
  }
  preinitialization(this)
  /** The flag that indicates whether template available for user or not */
  val availability: Boolean = element.eGet[java.lang.Boolean](getFieldIDAvailability) match {
    case Some(value) => value.get
    case _ => true
  }
  /** The template name */
  val name: String = element.eGet[String](getFieldIDName) match {
    case Some(value) => value.get
    case _ => ""
  }
  /** The template id */
  val id = element.eId
  /** Template properties */
  val properties: ElementTemplate.propertyMap = getProperties()

  /** The copy constructor */
  def copy(availability: Boolean = this.availability,
    name: String = this.name,
    element: Element.Generic = this.element,
    factory: (Element.Generic, Symbol, Symbol) => Element.Generic = this.factory,
    id: Symbol = this.id,
    properties: ElementTemplate.propertyMap = this.properties) =
    if (id == this.id)
      new ElementTemplate(element, factory, name, availability, properties).asInstanceOf[this.type]
    else {
      element.asInstanceOf[Element[Stash]].eStash = element.eStash.copy(id = id)
      new ElementTemplate(element, factory, name, availability, properties).asInstanceOf[this.type]
    }

  protected def getProperties(): ElementTemplate.propertyMap =
    immutable.HashMap(getPropertyArray().map {
      case (id, ptypeID, typeSymbol) =>
        PropertyType.container.get(ptypeID) match {
          case Some(ptype) if ptype.typeSymbol == typeSymbol =>
            Some(getProperty(id, ptype)(Manifest.classType(ptype.typeClass))).
              // as common TemplateProperty
              asInstanceOf[Option[(TemplatePropertyGroup, TemplateProperty[_ <: AnyRef with java.io.Serializable])]]
          case None =>
            log.fatal("unable to get property %s with unknown type wrapper id %s".format(id, ptypeID))
            None
        }
    }.flatten.toSeq.
      // group by TemplatePropertyGroup
      groupBy(_._1).map(t => (t._1,
        // transform the value from Seq((group,property), ...) to Seq(property) sorted by id
        t._2.map(_._2).sortBy(_.id.name))).toSeq: _*)
  /** Get property map */
  protected def getProperty[T <: AnyRef with java.io.Serializable: Manifest](id: Symbol, ptype: PropertyType[T]): (TemplatePropertyGroup, TemplateProperty[T]) = {
    // get the default field
    val defaultField = element.eGet[T](getFieldIDPropertyDefault(id))
    // get enumeration field
    val enumerationField = element.eGet[String](getFieldIDPropertyEnumeration(id))
    // get the required field
    val requiredField = element.eGet[java.lang.Boolean](getFieldIDPropertyRequired(id))
    // get the group field
    val groupField = element.eGet[String](getFieldIDPropertyGroup(id))
    // result
    val requiredVal = requiredField.map(_.get).getOrElse(Boolean.box(false))
    val elementPropertyEnumeration = enumerationField.flatMap { idRaw =>
      val id = Symbol(idRaw)
      Main.execNGet {
        val enumeration = Data.enumerations.get(id).find(_.ptype == ptype).asInstanceOf[Option[Enumeration.Interface[T]]]
        if (enumeration.isEmpty)
          log.error(s"Unable to load an unknown enumeration $idRaw")
        enumeration
      }
    }
    val elementPropertyGroup = TemplatePropertyGroup.default
    val elementProperty = new TemplateProperty[T](id, requiredVal, elementPropertyEnumeration.map(_.id), ptype, defaultField.map(_.get))
    (elementPropertyGroup, elementProperty)
  }
  /** Build array[property.id, ptype.id, ptype.typesymbol] of properties from getFieldProperties() field */
  protected def getPropertyArray(): Array[(Symbol, Symbol, Symbol)] = {
    val properties = (element.eGet[Array[Symbol]](getFieldIDProperties()).map(_.get) getOrElse Array[Symbol]()).
      grouped(3).filter(element =>
        element match {
          case Array(id, ptypeId, typeSymbol) if DSLType.symbols(typeSymbol) && PropertyType.container.get(ptypeId).map(_.typeSymbol == typeSymbol) == Some(true) =>
            // pass only arrays with 3 elements and known ptype and typeSymbol
            true
          case broken =>
            log.fatal("skip illegal element property: [%s]".format(broken.mkString(",")))
            false
        })
    properties.map(arr => Tuple3(arr(0), arr(1), arr(2))).toArray
  }
  /** Set property map */
  //protected def setProperty[T <: AnyRef with java.io.Serializable](id: Symbol, group: TemplatePropertyGroup, data: Option[TemplateProperty[T]])(implicit m: Manifest[T]): Unit =
  //  DSLType.classSymbolMap.get(m.runtimeClass).map(typeSymbol => setProperty(id, typeSymbol, group, data))
  /** Set property map */
  protected def setProperty(id: Symbol, ptype: PropertyType[_], group: TemplatePropertyGroup, data: Option[TemplateProperty[_ <: AnyRef with java.io.Serializable]]) {
    val properties = getPropertyArray()
    data match {
      case Some(elementProperty) =>
        // update the default field
        // asInstanceOf[Value.Static[_ <: AnyRef with java.io.Serializable]] is workaround for existential type warning
        val defaultValue = elementProperty.defaultValue.map(default =>
          new Value.Static(default, Context.virtual(element))(Manifest.classType(elementProperty.ptype.typeClass)).
            asInstanceOf[Value.Static[_ <: AnyRef with java.io.Serializable]])
        element.eSet(getFieldIDPropertyDefault(id), ptype.typeSymbol, defaultValue)
        // update the enumeration field
        elementProperty.enumeration match {
          case Some(enumeration) =>
            element.eSet(getFieldIDPropertyEnumeration(id), 'String, enumeration.name)
          case None =>
            element.eSet(getFieldIDPropertyEnumeration(id), 'String, None)
        }
        // update the required field
        element.eSet(getFieldIDPropertyRequired(id), 'Boolean, elementProperty.required)
        // update the group field
        element.eSet(getFieldIDPropertyGroup(id), 'String, group.id.name)
        // update the property list
        // filter if any then add
        val newProperties = (properties.filterNot(t => t._1 == id) :+ (id, ptype.id, ptype.typeSymbol)).map(t => Array(t._1, t._2, t._3)).flatten
        element.eSet(getFieldIDProperties, Some(new Value.Static(newProperties, Context.virtual(element))))
      case None =>
        // update the default field
        element.eSet(getFieldIDPropertyDefault(id), ptype.typeSymbol, None)
        // update the enumeration field
        element.eSet(getFieldIDPropertyEnumeration(id), 'String, None)
        // update the required field
        element.eSet(getFieldIDPropertyRequired(id), 'Boolean, None)
        // update the group field
        element.eSet(getFieldIDPropertyGroup(id), 'String, None)
        // update the property list
        val newProperties = properties.filterNot(t => t._1 == id).map(t => Array(t._1, t._2, t._3)).flatten
        element.eSet(getFieldIDProperties, Some(new Value.Static(newProperties, Context.virtual(element))))
    }
  }
}

object ElementTemplate extends DependencyInjection.PersistentInjectable with Loggable {
  type propertyMap = immutable.HashMap[TemplatePropertyGroup, Seq[TemplateProperty[_ <: AnyRef with java.io.Serializable]]]
  implicit def bindingModule = DependencyInjection()
  /** Predefined element templates modified by user that are available for this application */
  @volatile private var userPredefinedTemplates: Seq[ElementTemplate.Interface] = Seq()
  /** Predefined unmodified element templates that are available for this application */
  // The original list is needed for recovering broken/modified predefined templates
  @volatile private var originalPredefinedTemplates: Seq[ElementTemplate.Interface] = Seq()

  /**
   * The deep comparison of two element templates
   * Comparison is not includes check for element and factory equality
   */
  def compareDeep(a: Interface, b: Interface): Boolean =
    (a eq b) || ((a == b) && ElementTemplate.compareDeep(a.properties, b.properties))
  /** The deep comparison of two property maps */
  def compareDeep(a: ElementTemplate.propertyMap, b: ElementTemplate.propertyMap): Boolean = (a eq b) || {
    val aKeys = a.keys.toList.sortBy(_.id.name)
    val bKeys = b.keys.toList.sortBy(_.id.name)
    if (!aKeys.sameElements(bKeys)) return false
    aKeys.forall { key =>
      val aProperties = a(key).sortBy(_.id.name)
      val bProperties = b(key).sortBy(_.id.name)
      if (!aProperties.sameElements(bProperties)) return false
      (aProperties, bProperties).zipped.forall(TemplateProperty.compareDeep(_, _))
    }
  }
  /** Predefined element templates container. */
  def container() = inject[Record.Interface[_ <: Record.Stash]]("eElementTemplate")
  /** Get all element templates. */
  def load(): Set[ElementTemplate.Interface] = {
    log.debug("load element template list for model " + Model.eId)
    val templates: scala.collection.mutable.LinkedHashSet[ElementTemplate.Interface] =
      ElementTemplate.container.eChildren.map({ element =>
        ElementTemplate.predefined.find(predefined =>
          element.canEqual(predefined.element.getClass(), predefined.element.eStash.getClass())) match {
          case Some(predefined) =>
            log.debug("load template %s based on %s".format(element, predefined))
            Some(new ElementTemplate(element, predefined.factory))
          case None =>
            log.warn("unable to find apropriate element wrapper for " + element)
            None
        }
      }).flatten
    // add predefined element templates if not exists
    ElementTemplate.predefined.foreach { predefined =>
      if (!templates.exists(_.element.eId == predefined.element.eId)) {
        log.debug("template for predefined element %s not found, recreate".format(predefined))
        templates += predefined
      }
    }
    assert(templates.nonEmpty, "There are no element templates")
    templates.toSet
  }
  /** This function is invoked at every model initialization */
  def onModelInitialization(oldModel: Model.Generic, newModel: Model.Generic, modified: Element.Timestamp) = {
    userPredefinedTemplates = inject[Seq[ElementTemplate.Interface]]("User")
    originalPredefinedTemplates = inject[Seq[ElementTemplate.Interface]]("Original")
    assert(userPredefinedTemplates.map(_.id) == originalPredefinedTemplates.map(_.id),
      "User modified predefined template list must contain the same elements as original predefined template list")
  }
  /** Update only modified element templates */
  def save(templates: Set[ElementTemplate.Interface]) = Main.exec {
    log.debug("save element template list for model " + Model.eId)
    val oldTemplates = Data.elementTemplates.values
    val deleted = oldTemplates.filterNot(oldTemplate => templates.exists(compareDeep(_, oldTemplate)))
    val added = templates.filterNot(newTemplate => oldTemplates.exists(compareDeep(_, newTemplate)))
    if (deleted.nonEmpty) {
      log.debug("delete Set(%s)".format(deleted.mkString(", ")))
      deleted.foreach { template =>
        Data.elementTemplates.remove(template.id)
        container.eChildren -= template.element
      }
    }
    if (added.nonEmpty) {
      log.debug("add Set(%s)".format(added.mkString(", ")))
      added.foreach { template =>
        container.eChildren += template.element
        Data.elementTemplates(template.id) = template
      }
    }
  }
  /**
   * Predefined custom record template
   * 'lazy' modifier prohibited, modify current model while construction
   */
  def initPredefinedCustom(sample: Boolean) = {
    val id = Record.scope.modificator
    val factory = (container: Element.Generic, id: Symbol, scopeModificator: Symbol) =>
      Record(container, id, new Record.Scope(scopeModificator), Coordinate.root.coordinate)
    if (sample || (container & RecordLocation(id)).isEmpty) {
      log.debug("initialize new predefined Record template")
      val element = if (sample)
        Record(id, Coordinate.root.coordinate)
      else
        factory(ElementTemplate.container, id, id)
      new ElementTemplate(element, factory, "Predefined custom element", true,
        immutable.HashMap(TemplatePropertyGroup.default -> Seq(new TemplateProperty[String]('name, false, None, PropertyType.get('String)))))
    } else {
      log.debug("initialize exists predefined Record template")
      new ElementTemplate(factory(ElementTemplate.container, id, id), factory)
    }
  }
  /**
   * Predefined note record template
   * 'lazy' modifier prohibited, modify current model while construction
   */
  def initPredefinedNote(sample: Boolean) = {
    val id = Note.scope.modificator
    val factory = (container: Element.Generic, id: Symbol, scopeModificator: Symbol) =>
      Note(container, id, new Note.Scope(scopeModificator), Coordinate.root.coordinate)
    if (sample || (container & NoteLocation(id)).isEmpty) {
      log.debug("initialize new predefined Note template")
      val element = if (sample)
        Record(id, Coordinate.root.coordinate)
      else
        factory(ElementTemplate.container, id, id)
      new ElementTemplate(element, factory, "Predefined note element", true,
        immutable.HashMap(TemplatePropertyGroup.default -> Seq(new TemplateProperty[String]('name, false, None, PropertyType.get('String)))))
    } else {
      log.debug("initialize exists predefined Note template")
      new ElementTemplate(factory(ElementTemplate.container, id, id), factory)
    }
  }
  /**
   * Predefined task record template
   * 'lazy' modifier prohibited, modify current model while construction
   */
  def initPredefinedTask(sample: Boolean) = {
    val id = Task.scope.modificator
    val factory = (container: Element.Generic, id: Symbol, scopeModificator: Symbol) =>
      Task(container, id, new Task.Scope(scopeModificator), Coordinate.root.coordinate)
    if (sample || (container & TaskLocation(id)).isEmpty) {
      log.debug("initialize new predefined Task template")
      val element = if (sample)
        Record(id, Coordinate.root.coordinate)
      else
        factory(ElementTemplate.container, id, id)
      new ElementTemplate(element, factory, "Predefined task element", true,
        immutable.HashMap(TemplatePropertyGroup.default -> Seq(new TemplateProperty[String]('name, false, None, PropertyType.get('String)))))
    } else {
      log.debug("initialize exists predefined Task template")
      new ElementTemplate(factory(ElementTemplate.container, id, id), factory)
    }
  }
  def original() = originalPredefinedTemplates
  def predefined() = userPredefinedTemplates

  /**
   * model.Element from the application point of view
   */
  trait Interface {
    /** Availability flag for user (some template may exists, but not involved in new element creation) */
    val availability: Boolean
    /** The template name */
    val name: String
    /** The template element */
    val element: Element.Generic
    /** The factory for the element that contains template data */
    val factory: (Element.Generic, Symbol, Symbol) => Element.Generic
    /** The template id/name/element scope */
    val id: Symbol
    /**
     * Map of element properties from the model point of view
     * or map of form fields from the application point of view
     * Key - property group, Value - sequence of element properties
     */
    val properties: ElementTemplate.propertyMap

    /** The copy constructor */
    def copy(availability: Boolean = this.availability,
      name: String = this.name,
      element: Element.Generic = this.element,
      factory: (Element.Generic, Symbol, Symbol) => Element.Generic = this.factory,
      id: Symbol = this.id,
      properties: ElementTemplate.propertyMap = this.properties): this.type
    /** Returns an ID for the availability field */
    def getFieldIDAvailability() = 'availability
    /** Returns an ID for the name field */
    def getFieldIDName() = 'name
    /** Returns an ID for the sequence of id/type tuples field */
    def getFieldIDProperties() = 'properties
    /** Returns an ID for the property default value field */
    def getFieldIDPropertyDefault(id: Symbol) = Symbol(id.name + "_default")
    /** Returns an ID for the property enumeration field */
    def getFieldIDPropertyEnumeration(id: Symbol) = Symbol(id.name + "_enumeration")
    /** Returns an ID for the property required field */
    def getFieldIDPropertyRequired(id: Symbol) = Symbol(id.name + "_required")
    /** Returns an ID for the property group field */
    def getFieldIDPropertyGroup(id: Symbol) = Symbol(id.name + "_group")
    /** Returns a new ElementTemplate with the updated availability */
    def updated(availability: Boolean): this.type = copy(availability = availability)
    /** Returns a new ElementTemplate with the updated name */
    def updated(name: String): this.type = copy(name = name)
    /** Returns a new ElementTemplate with the updated id */
    def updated(id: Symbol): this.type = copy(id = id)

    def canEqual(other: Any) =
      other.isInstanceOf[org.digimead.tabuddy.desktop.payload.ElementTemplate.Interface]
    override def equals(other: Any) = other match {
      case that: org.digimead.tabuddy.desktop.payload.ElementTemplate =>
        (this eq that) || {
          that.canEqual(this) &&
            availability == that.availability &&
            name == that.name &&
            id == that.id &&
            properties == that.properties
        }
      case _ => false
    }
    override def hashCode() = {
      /*
       * Of the remaining four, I'd probably select P(31), as it's the cheapest to calculate on a
       * RISC machine (because 31 is the difference of two powers of two). P(33) is
       * similarly cheap to calculate, but it's performance is marginally worse, and
       * 33 is composite, which makes me a bit nervous.
       */
      val p = 31
      p * (p * (p * (p + availability.hashCode) + name.hashCode) + id.hashCode) + properties.hashCode
    }
    override def toString() = "ElementTemplate(%s based on %s)".format(element.eId, element.eStash.scope)
  }
}
