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

package org.digimead.tabuddy.desktop.logic.payload

import java.util.UUID
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.logic.payload.api.XElementTemplate
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.model.{ Model, Record }
import org.digimead.tabuddy.model.dsl.DSLType
import org.digimead.tabuddy.model.element.{ Element, Value }
import org.digimead.tabuddy.model.graph.{ Graph, Node }
import org.digimead.tabuddy.model.serialization.StubSerialization
import scala.collection.immutable

/**
 * model.Element from the point of view of TA Buddy
 */
class ElementTemplate(
  /** The template element */
  val element: Element#RelativeType,
  /** The factory for the element that contains template data (container, id, scopeModificator) */
  val factory: (Element, Symbol, Symbol) ⇒ Element,
  /** Fn thats do something before the instance initialization */
  preinitialization: ElementTemplate ⇒ Unit = _ ⇒ {}) extends ElementTemplate.Interface with XLoggable {
  def this(element: Element#RelativeType, factory: (Element, Symbol, Symbol) ⇒ Element,
    initialName: String, initialAvailability: Boolean, initialProperties: ElementTemplate.PropertyMap) = {
    this(element, factory, (template) ⇒ {
      // This code is invoked before availability, name and properties fields initialization
      if (template.element.eGet[java.lang.Boolean](template.getFieldIDAvailability).map(_.get) != Some(initialAvailability))
        template.element.eSet(template.getFieldIDAvailability, initialAvailability)
      if (template.element.eGet[String](template.getFieldIDName).map(_.get) != Some(initialName))
        template.element.eSet(template.getFieldIDName, initialName, "")
      if (!ElementTemplate.compareDeep(template.getProperties(), initialProperties))
        initialProperties.foreach {
          case (group, properties) ⇒
            properties.foreach(property ⇒ template.setProperty(property.id, property.ptype, group, Some(property)))
        }
    })
  }
  preinitialization(this)
  /** The flag that indicates whether template available for user or not */
  val availability: Boolean = element.eGet[java.lang.Boolean](getFieldIDAvailability) match {
    case Some(value) ⇒ value.get
    case _ ⇒ true
  }
  /** The template name */
  val name: String = element.eGet[String](getFieldIDName) match {
    case Some(value) ⇒ value.get
    case _ ⇒ ""
  }
  /** The template id */
  val id = element.eId
  /** Template properties */
  val properties: ElementTemplate.PropertyMap = getProperties()

  /** The copy constructor */
  def copy(availability: Boolean = this.availability,
    name: String = this.name,
    element: Element#RelativeType = this.element,
    factory: (Element, Symbol, Symbol) ⇒ Element = this.factory,
    id: Symbol = this.id,
    properties: ElementTemplate.PropertyMap = this.properties) =
    if (id == this.id)
      new ElementTemplate(element, factory, name, availability, properties).asInstanceOf[this.type]
    else {
      element.eNode.parent match {
        case Some(parent) ⇒
          parent.freezeWrite { node ⇒
            val nodeCopy = element.eNode.copy(target = node, id = id, unique = UUID.randomUUID()): Node[_ <: Element]
            new ElementTemplate(nodeCopy.projection(element.eCoordinate).e.eRelative, factory, name, availability, properties).asInstanceOf[this.type]
          }
        case None ⇒
          throw new IllegalStateException(s"Unable to copy unbinded $this.")
      }
    }

  protected def getProperties(): ElementTemplate.PropertyMap =
    immutable.HashMap(getPropertyArray().map {
      case (id, ptypeID, typeSymbol) ⇒
        PropertyType.container.get(ptypeID) match {
          case Some(ptype) if ptype.typeSymbol == typeSymbol ⇒
            Some(getProperty(GraphMarker(element.eGraph), id, ptype)(Manifest.classType(ptype.typeClass))).
              // as common TemplateProperty
              asInstanceOf[Option[(TemplatePropertyGroup, TemplateProperty[_ <: AnyRef with java.io.Serializable])]]
          case None ⇒
            log.fatal("unable to get property %s with unknown type wrapper id %s".format(id, ptypeID))
            None
        }
    }.flatten.toSeq.
      // group by TemplatePropertyGroup
      groupBy(_._1).map(t ⇒ (t._1,
        // transform the value from Seq((group,property), ...) to Seq(property) sorted by id
        t._2.map(_._2).sortBy(_.id.name))).toSeq: _*)
  /** Get property map */
  protected def getProperty[T <: AnyRef with java.io.Serializable: Manifest](marker: GraphMarker, id: Symbol,
    ptype: PropertyType[T]): (TemplatePropertyGroup, TemplateProperty[T]) = marker.safeRead { state ⇒
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
    val elementPropertyEnumeration = enumerationField.flatMap { idRaw ⇒
      val id = Symbol(idRaw)
      App.execNGet {
        val enumeration = state.payload.enumerations.get(id).find(_.ptype == ptype).asInstanceOf[Option[Enumeration[T]]]
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
      grouped(3).filter(element ⇒
        element match {
          case Array(id, ptypeId, typeSymbol) if DSLType.symbols(typeSymbol) && PropertyType.container.get(ptypeId).map(_.typeSymbol == typeSymbol) == Some(true) ⇒
            // pass only arrays with 3 elements and known ptype and typeSymbol
            true
          case broken ⇒
            log.fatal("skip illegal element property: [%s]".format(broken.mkString(",")))
            false
        })
    properties.map(arr ⇒ Tuple3(arr(0), arr(1), arr(2))).toArray
  }
  /** Set property map */
  protected def setProperty(id: Symbol, ptype: PropertyType[_], group: TemplatePropertyGroup, data: Option[TemplateProperty[_ <: AnyRef with java.io.Serializable]]) {
    val properties = getPropertyArray()
    data match {
      case Some(elementProperty) ⇒
        // update the default field
        // asInstanceOf[Value.Static[_ <: AnyRef with java.io.Serializable]] is workaround for existential type warning
        val defaultValue = elementProperty.defaultValue.map(default ⇒
          new Value.Static(default)(Manifest.classType(elementProperty.ptype.typeClass)).
            asInstanceOf[Value.Static[_ <: AnyRef with java.io.Serializable]])
        element.eSet(getFieldIDPropertyDefault(id), ptype.typeSymbol, defaultValue)
        // update the enumeration field
        elementProperty.enumeration match {
          case Some(enumeration) ⇒
            element.eSet(getFieldIDPropertyEnumeration(id), 'String, enumeration.name)
          case None ⇒
            element.eSet(getFieldIDPropertyEnumeration(id), 'String, None)
        }
        // update the required field
        element.eSet(getFieldIDPropertyRequired(id), 'Boolean, elementProperty.required)
        // update the group field
        element.eSet(getFieldIDPropertyGroup(id), 'String, group.id.name)
        // update the property list
        // filter if any then add
        val newProperties = (properties.filterNot(t ⇒ t._1 == id) :+ (id, ptype.id, ptype.typeSymbol)).map(t ⇒ Array(t._1, t._2, t._3)).flatten
        element.eSet(getFieldIDProperties, Some(new Value.Static(newProperties)))
      case None ⇒
        // update the default field
        element.eSet(getFieldIDPropertyDefault(id), ptype.typeSymbol, None)
        // update the enumeration field
        element.eSet(getFieldIDPropertyEnumeration(id), 'String, None)
        // update the required field
        element.eSet(getFieldIDPropertyRequired(id), 'Boolean, None)
        // update the group field
        element.eSet(getFieldIDPropertyGroup(id), 'String, None)
        // update the property list
        val newProperties = properties.filterNot(t ⇒ t._1 == id).map(t ⇒ Array(t._1, t._2, t._3)).flatten
        element.eSet(getFieldIDProperties, Some(new Value.Static(newProperties)))
    }
  }
}

object ElementTemplate extends XLoggable {
  type PropertyMap = XElementTemplate.PropertyMap[TemplatePropertyGroup, TemplateProperty[_ <: AnyRef with java.io.Serializable]]
  /** Get list of element template builders. */
  def builders = DI.builders
  /**
   * The deep comparison of two element templates
   * Comparison is not includes check for element and factory equality
   */
  def compareDeep(a: ElementTemplate, b: ElementTemplate): Boolean =
    (a eq b) || ((a == b) && ElementTemplate.compareDeep(a.properties, b.properties))
  /** The deep comparison of two property maps */
  def compareDeep(a: ElementTemplate.PropertyMap, b: ElementTemplate.PropertyMap): Boolean = (a eq b) || {
    val aKeys = a.keys.toList.sortBy(_.id.name)
    val bKeys = b.keys.toList.sortBy(_.id.name)
    if (!aKeys.sameElements(bKeys)) return false
    aKeys.forall { key ⇒
      val aProperties = a(key).sortBy(_.id.name)
      val bProperties = b(key).sortBy(_.id.name)
      if (!aProperties.sameElements(bProperties)) return false
      (aProperties, bProperties).zipped.forall(TemplateProperty.compareDeep(_, _))
    }
  }
  /** Get all element templates. */
  def load(marker: GraphMarker): (Set[ElementTemplate], Set[ElementTemplate]) = marker.safeRead { state ⇒
    log.debug("Load element template list for graph " + state.graph)
    // Renew/update original templates
    val tempGraph = {
      implicit val tempGraphModelStashClass: Class[_ <: Model.Stash] = classOf[Model.Stash]
      Graph[Model]('temp, Model.scope, StubSerialization.Identifier, UUID.randomUUID()) { g ⇒ }
    }
    val tempMarker = GraphMarker.temporary(tempGraph)
    tempMarker.register()
    try {
      val temp = tempGraph.model.eRelative
      val originalTemplatesContainer = PredefinedElements.eElementTemplateOriginal(state.graph)
      val userTemplatesContainer = PredefinedElements.eElementTemplateUser(state.graph)
      temp.eNode.freezeWrite { tempNode ⇒
        // Create new list of templates per builder from application configuration
        val temporaryTemplates = builders.map { builder ⇒ (builder(temp.absolute), builder) }
        // Update original templates
        // Only add new templates, but keep the old ones and skip unknown.
        val originalTemplates: Set[ElementTemplate] = originalTemplatesContainer.eNode.freezeWrite { node ⇒
          val original = node.children.map(_.rootBox.e.eRelative)
          temporaryTemplates.map {
            case (example, builder) ⇒
              original.find(element ⇒ element.canEqual(example.element) &&
                example.element.canEqual(element) &&
                element.eStash.canEqual(example.element.eStash) &&
                example.element.eStash.canEqual(element.eStash) &&
                element.eId == example.element.eId) match {
                case Some(original) ⇒
                  log.debug("Keep original template %s based on %s.".format(original, example))
                  new ElementTemplate(original, example.factory)
                case None ⇒
                  val original = builder(originalTemplatesContainer.absolute)
                  log.debug("Create original template %s based on %s.".format(original, example))
                  original
              }
          }.toSet
        }
        // Update user templates
        // Only add new templates, but keep the old ones and skip unknown.
        val userTemplates: Set[ElementTemplate] = userTemplatesContainer.eNode.freezeWrite { node ⇒
          val user = node.children.map(_.rootBox.e.eRelative)
          val templatesBasedOnOriginalSet = temporaryTemplates.map {
            case (example, builder) ⇒
              user.find(element ⇒ element.canEqual(example.element) &&
                example.element.canEqual(element) &&
                element.eStash.canEqual(example.element.eStash) &&
                example.element.eStash.canEqual(element.eStash) &&
                element.eId == example.element.eId) match {
                case Some(element) ⇒
                  log.debug("Keep user template %s based on %s.".format(element, example))
                  new ElementTemplate(element, example.factory)
                case None ⇒
                  val template = builder(userTemplatesContainer.absolute)
                  log.debug("Create user template %s based on %s.".format(template.element, example))
                  template
              }
          }.toSet
          val templatesBasedOnUserSet = user.flatMap { element ⇒
            temporaryTemplates.find {
              case (example, builder) ⇒ element.canEqual(example.element) &&
                example.element.canEqual(element) &&
                element.eStash.canEqual(example.element.eStash) &&
                example.element.eStash.canEqual(element.eStash)
            }.map {
              case (example, builder) ⇒
                log.debug("Keep user template %s based on %s.".format(element, example))
                new ElementTemplate(element, example.factory)
            }
          }.toSet
          templatesBasedOnUserSet ++ templatesBasedOnOriginalSet
        }
        assert(userTemplates.nonEmpty, "There are no element templates that ara available for user.")
        tempNode.clear()
        (originalTemplates, userTemplates)
      }
    } finally tempMarker.unregister()
  }
  /** Update only modified element templates. */
  def save(marker: GraphMarker, templates: Set[ElementTemplate]) = marker.safeRead { state ⇒
    log.debug("Save element template list for graph " + state.graph)
    val oldTemplates = App.execNGet { state.payload.elementTemplates.values.toSet }
    val deleted = oldTemplates.filterNot(oldTemplate ⇒ templates.exists(compareDeep(_, oldTemplate)))
    val added = templates.filterNot(newTemplate ⇒ oldTemplates.exists(compareDeep(_, newTemplate)))
    if (deleted.nonEmpty) {
      log.debug("Delete Set(%s)".format(deleted.mkString(", ")))
      App.execNGet { deleted.foreach { template ⇒ state.payload.elementTemplates.remove(template.id) } }
      deleted.foreach(template ⇒ template.element.eNode.parent.foreach(_.safeWrite { _ -= template.element.eNode }))
    }
    if (added.nonEmpty) {
      log.debug("Add Set(%s)".format(added.mkString(", ")))
      added.foreach(_.element.eNode.attach())
      App.execNGet { added.foreach { template ⇒ state.payload.elementTemplates(template.id) = template } }
    }
  }

  /**
   * ElementTemplate builder.
   */
  trait Builder extends XElementTemplate.Builder[TemplatePropertyGroup, TemplateProperty[_ <: AnyRef with java.io.Serializable]]
  /**
   * model.Element from the application point of view.
   */
  private[ElementTemplate] trait Interface extends XElementTemplate[TemplatePropertyGroup, TemplateProperty[_ <: AnyRef with java.io.Serializable]] {
    /** Availability flag for user (some template may exists, but not involved in new element creation) */
    val availability: Boolean
    /** The template name */
    val name: String
    /** The template element */
    val element: Element#RelativeType
    /** The factory for the element that contains template data */
    val factory: (Element, Symbol, Symbol) ⇒ Element
    /** The template id/name/element scope */
    val id: Symbol
    /**
     * Map of element properties from the model point of view
     * or map of form fields from the application point of view
     * Key - property group, Value - sequence of element properties
     */
    val properties: ElementTemplate.PropertyMap

    /** The copy constructor */
    def copy(availability: Boolean = this.availability,
      name: String = this.name,
      element: Element#RelativeType = this.element,
      factory: (Element, Symbol, Symbol) ⇒ Element = this.factory,
      id: Symbol = this.id,
      properties: ElementTemplate.PropertyMap = this.properties): this.type
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

    def canEqual(other: Any) = other.isInstanceOf[Interface]
    override def equals(other: Any) = other match {
      case that: Interface ⇒
        (this eq that) || {
          that.canEqual(this) &&
            availability == that.availability &&
            name == that.name &&
            id == that.id &&
            properties == that.properties
        }
      case _ ⇒ false
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
    override lazy val toString = "ElementTemplate[%s based on %s]".format(element.eId, element.eStash.scope)
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /**
     * Collection of element template builders.
     *
     * Each collected builder must be:
     *  1. an instance of Record.Like ⇒ api.ElementTemplate
     *  2. has name that starts with "Template."
     */
    lazy val builders = bindingModule.bindings.filter {
      case (key, value) ⇒ classOf[XElementTemplate.Builder[_, _]].isAssignableFrom(key.m.runtimeClass)
    }.map {
      case (key, value) ⇒
        key.name match {
          case Some(name) if name.startsWith("Template.") ⇒
            log.debug(s"Element template builder '${name}' loaded.")
            bindingModule.injectOptional(key).asInstanceOf[Option[Record.Like ⇒ ElementTemplate]]
          case _ ⇒
            log.debug(s"'${key.name.getOrElse("Unnamed")}' element template builder skipped.")
            None
        }
    }.flatten
  }
}
