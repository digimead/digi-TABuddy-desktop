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

import java.util.UUID
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.Messages
import org.digimead.tabuddy.desktop.core.definition.NLS
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.logic.payload.maker.GraphMarker
import org.yaml.snakeyaml.{ DumperOptions, Yaml }
import org.yaml.snakeyaml.constructor.{ AbstractConstruct, Constructor }
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.nodes.{ MappingNode, Node, NodeTuple, SequenceNode, Tag }
import org.yaml.snakeyaml.representer.{ Represent, Representer }
import scala.collection.JavaConversions._
import scala.collection.immutable

/**
 * The TypeSchema
 * The equality is based on id: UUID
 */
class TypeSchema(
  /** A type schema name. */
  val id: UUID,
  /** The type schema name. */
  val name: String,
  /** The type schema description. */
  val description: String,
  /** Type schema entities. */
  val entity: immutable.HashMap[Symbol, api.TypeSchemaEntity[_ <: AnyRef with java.io.Serializable]]) extends api.TypeSchema {
  assert(entity.nonEmpty, "Type schema contain no entities")

  /** The copy constructor. */
  def copy(id: UUID = this.id,
    name: String = this.name,
    description: String = this.description,
    entity: immutable.HashMap[Symbol, api.TypeSchemaEntity[_ <: AnyRef with java.io.Serializable]] = this.entity) =
    new TypeSchema(id, name, description, entity).asInstanceOf[this.type]

  def canEqual(other: Any) =
    other.isInstanceOf[org.digimead.tabuddy.desktop.logic.payload.api.TypeSchema]
  override def equals(other: Any) = other match {
    case that: org.digimead.tabuddy.desktop.logic.payload.api.TypeSchema ⇒
      (this eq that) || {
        that.canEqual(this) &&
          id == that.id
      }
    case _ ⇒ false
  }
  override def hashCode() = id.hashCode
  override def toString() = "TypeSchema %s \"%s\"".format(id, name)
}

/**
 * Layer that provide a filter for template builder against possible type swarm
 * Layer that provide a localization for end user
 */
object TypeSchema extends Loggable {
  /** TypeSchema apply. */
  def apply(id: UUID, name: String, description: String, entities: immutable.HashMap[Symbol, api.TypeSchemaEntity[_ <: AnyRef with java.io.Serializable]]) =
    new TypeSchema(id, name, description, entities)
  /** The deep comparison of two schemas. */
  def compareDeep(a: api.TypeSchema, b: api.TypeSchema): Boolean =
    (a eq b) || (a.id == b.id && a.name == b.name && a.description == b.description && (a.entity, b.entity).zipped.forall((a, b) ⇒ compareDeep(a._2, b._2)))
  /** The deep comparison of two entities. */
  def compareDeep(a: api.TypeSchemaEntity[_ <: AnyRef with java.io.Serializable], b: api.TypeSchemaEntity[_ <: AnyRef with java.io.Serializable]): Boolean =
    (a eq b) || (a.ptypeId == b.ptypeId && a.alias == b.alias && a.availability == b.availability && a.description == b.description)
  /** Get default type schema. */
  def default() = predefinedSchemas.find(_.id == DI.default).get
  /** Get entities set */
  def entities = immutable.HashSet[api.TypeSchemaEntity[_ <: AnyRef with java.io.Serializable]](
    PropertyType.container.values.toSeq.map(ptype ⇒ new TypeSchema.Entity(ptype.id, "", true,
      Messages.typeSchemaDefaultDescription_text.format(getEntityTranslation(ptype.id, "")))): _*)
  /** Get translation by alias. */
  def getEntityTranslation(entityTypeId: Symbol, entityAlias: String): String = if (entityAlias.startsWith("*"))
    NLS.messages.get(entityAlias.substring(1)).getOrElse {
      val result = entityAlias.substring(1)
      val trimmed = if (result.endsWith("_text"))
        result.substring(0, result.length - 5)
      else
        result
      trimmed(0).toString.toUpperCase + trimmed.substring(1)
    }
  else if (entityAlias.isEmpty())
    NLS.messages.get(entityTypeId.name.toLowerCase() + "_text").getOrElse(entityTypeId.name)
  else
    entityAlias
  /** Get type name. */
  def getTypeName(marker: GraphMarker, ptypeId: Symbol) = marker.lockRead { state ⇒
    App.execNGet { state.payload.typeSchema.value.entity.get(ptypeId) } match {
      case Some(entity) ⇒ entity.view
      case None ⇒ ptypeId.name
    }
  }
  /** Get all schemas for the current graph. */
  def load(marker: GraphMarker): Set[api.TypeSchema] = marker.lockRead { state ⇒
    log.debug("Load schema list for graph " + state.graph)
    val schemas = try {
      marker.loadTypeSchemas
    } catch {
      case e: Throwable ⇒
        log.error("Unable to load type schemas: " + e, e)
        Set[api.TypeSchema]()
    }
    schemas.map { schema ⇒
      val lostEntities = TypeSchema.entities &~ schema.entity.values.toSet
      if (lostEntities.nonEmpty)
        schema.copy(entity = schema.entity ++ lostEntities.map(e ⇒ (e.ptypeId, e)))
      else
        schema
    } ++ TypeSchema.predefined.filter(predefined ⇒ !schemas.exists(_.id == predefined.id))
  }
  /** Get predefined type schemas that are available for this application. */
  def predefinedSchemas: Seq[api.TypeSchema] = Seq()
  /** Get predefined schemas. */
  def predefined() = predefinedSchemas
  /** Update only modified type schemas. */
  @log
  def save(marker: GraphMarker, schemas: Set[api.TypeSchema]) = marker.lockUpdate { state ⇒
    log.debug("Save type schema list for graph " + state.graph)
    val oldSchemas = App.execNGet { state.payload.typeSchemas.values }
    val deleted = oldSchemas.filterNot(oldSchema ⇒ schemas.exists(compareDeep(_, oldSchema)))
    val added = schemas.filterNot(newSchema ⇒ oldSchemas.exists(compareDeep(_, newSchema)))
    if (deleted.nonEmpty) {
      log.debug("Delete Set(%s)".format(deleted.mkString(", ")))
      App.execNGet { deleted.foreach { schema ⇒ state.payload.typeSchemas.remove(schema.id) } }
    }
    if (added.nonEmpty) {
      log.debug("Add Set(%s)".format(added.mkString(", ")))
      App.execNGet { added.foreach { schema ⇒ state.payload.typeSchemas(schema.id) = schema } }
    }
  }
  /** TypeSchema unapply. */
  def unapply(schema: api.TypeSchema): Option[(UUID, String, String, immutable.HashMap[Symbol, api.TypeSchemaEntity[_ <: AnyRef with java.io.Serializable]])] =
    Some(schema.id, schema.name, schema.description, schema.entity)

  /**
   * The type schema entity. It is a tuple templateTypeSymbol -> context information
   * The equality is based on ptypeId: Symbol
   */
  class Entity[T <: AnyRef with java.io.Serializable](
    /** The property type id */
    val ptypeId: Symbol,
    /** typeSymbol alias */
    val alias: String,
    /** Availability flag for user (some types may exists, but not involved in new element template creation) */
    val availability: Boolean,
    /** The entity description */
    val description: String) extends api.TypeSchemaEntity[T] {
    /** The type schema entity user's representation */
    lazy val view: String = TypeSchema.getEntityTranslation(ptypeId, alias)

    /** The copy constructor */
    def copy(ptypeId: Symbol = this.ptypeId,
      alias: String = this.alias,
      availability: Boolean = this.availability,
      description: String = this.description): this.type =
      new Entity(ptypeId, alias, availability, description).asInstanceOf[this.type]

    def canEqual(other: Any) =
      other.isInstanceOf[org.digimead.tabuddy.desktop.logic.payload.api.TypeSchemaEntity[_]]
    override def equals(other: Any) = other match {
      case that: org.digimead.tabuddy.desktop.logic.payload.api.TypeSchemaEntity[_] ⇒
        (this eq that) || {
          that.canEqual(this) &&
            ptypeId == that.ptypeId
        }
      case _ ⇒ false
    }
    override def hashCode() = ptypeId.hashCode
  }
  object YAML extends Payload.YAMLProcessor[api.TypeSchema] {
    /** Convert JSON to the object */
    def from(data: String): Option[api.TypeSchema] = {
      val yaml = new Yaml(new TypeSchemaConstructor)
      Option(yaml.load(data).asInstanceOf[api.TypeSchema])
    }
    /** Convert the object to JSON */
    def to(value: api.TypeSchema): String = {
      val options = new DumperOptions()
      options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)
      val yaml = new Yaml(new TypeSchemaRepresenter, new DumperOptions())
      yaml.dump(value)
    }

    class TypeSchemaConstructor extends Constructor(classOf[api.TypeSchema]) {
      val interfaceTag = new Tag(classOf[api.TypeSchema])
      val entityTag = new Tag(classOf[Entity[_]])
      this.yamlConstructors.put(interfaceTag, new InterfaceConstruct())
      this.yamlConstructors.put(entityTag, new EntityConstruct())

      def safeConstruct[T: Manifest](tuple: NodeTuple)(implicit owner: String): Option[T] = constructObject(tuple.getValueNode()) match {
        case value: T ⇒ Option(value)
        case unknown ⇒ throw new YAMLException("Unexpected %s key '%s' with type %s".
          format(owner, constructObject(tuple.getKeyNode), tuple.getValueNode().getTag()))
      }
      class InterfaceConstruct extends AbstractConstruct {
        implicit val owner = "api.TypeSchema"

        def construct(node: Node): AnyRef = node match {
          case node: MappingNode ⇒
            var id: Option[String] = None
            var name: Option[String] = None
            var description: Option[String] = None
            var entities: scala.collection.mutable.Buffer[api.TypeSchemaEntity[_ <: AnyRef with java.io.Serializable]] = new scala.collection.mutable.ArrayBuffer
            for (value ← node.getValue())
              constructObject(value.getKeyNode()) match {
                case "id" ⇒ id = safeConstruct[String](value)
                case "name" ⇒ name = safeConstruct[String](value)
                case "description" ⇒ description = safeConstruct[String](value)
                case "entities" ⇒
                  val node = value.getValueNode() match {
                    case seq: SequenceNode ⇒
                      entities = (for (value ← seq.getValue()) yield {
                        value.setTag(entityTag)
                        Option(constructObject(value)).asInstanceOf[Option[api.TypeSchemaEntity[_ <: AnyRef with java.io.Serializable]]]
                      }).flatten
                    case unknown ⇒ throw new YAMLException("Unexpected api.TypeSchema 'entities' type " + unknown.getClass())
                  }
                case other ⇒ log.warn(s"Unknown api.TypeSchema key: $other")
              }
            val schema = for {
              id ← id
              name ← name
              description ← description
            } yield new TypeSchema(UUID.fromString(id), name, description, immutable.HashMap(entities.map(e ⇒ (e.ptypeId, e)): _*))
            schema getOrElse {
              log.error(s"Unable to load api.TypeSchema id:$id, name:$name, description:$description, entities:" + entities.mkString)
              null
            }
          case unknown ⇒
            throw new YAMLException("Unexpected api.TypeSchema node type " + unknown.getTag())
        }
      }
      class EntityConstruct extends AbstractConstruct {
        implicit val owner = "api.TypeSchemaEntity"

        def construct(node: Node): AnyRef = node match {
          case node: MappingNode ⇒
            var ptypeId: Option[String] = None
            var alias: Option[String] = None
            var availability: Option[Boolean] = None
            var description: Option[String] = None
            for (value ← node.getValue())
              constructObject(value.getKeyNode()) match {
                case "type" ⇒ ptypeId = safeConstruct[String](value)
                case "alias" ⇒ alias = safeConstruct[String](value)
                case "availability" ⇒ availability = safeConstruct[java.lang.Boolean](value).map(Boolean.box(_))
                case "description" ⇒ description = safeConstruct[String](value)
                case other ⇒ log.warn(s"unknown TypeSchema.Entity key: $other")
              }
            val entity = for {
              ptypeId ← ptypeId
              alias ← alias
              availability ← availability
              description ← description
            } yield new TypeSchema.Entity(Symbol(ptypeId), alias, availability, description)
            entity.getOrElse {
              log.error(s"Unable to load TypeSchema.Entity ptype:$ptypeId, alias:$alias, availability:$availability, description:$description")
              null
            }
          case unknown ⇒
            throw new YAMLException("Unexpected TypeSchema.Entity node type " + unknown.getTag())
        }
      }
    }

    class TypeSchemaRepresenter extends Representer {
      multiRepresenters.put(classOf[api.TypeSchema], new InterfaceRepresent)
      multiRepresenters.put(classOf[Entity[_]], new EntityRepresent)

      class InterfaceRepresent extends Represent {
        override def representData(data: AnyRef): Node = {
          val schema = data.asInstanceOf[TypeSchema]
          val map = new java.util.HashMap[String, AnyRef]()
          map.put("id", schema.id.toString())
          map.put("name", schema.name)
          map.put("description", schema.description)
          map.put("entities", seqAsJavaList(schema.entity.values.toSeq))
          representMapping(Tag.MAP, map, null)
        }
      }
      class EntityRepresent extends Represent {
        override def representData(data: AnyRef): Node = {
          val entity = data.asInstanceOf[Entity[_ <: AnyRef with java.io.Serializable]]
          val map = new java.util.HashMap[String, AnyRef]()
          map.put("type", entity.ptypeId.name)
          map.put("alias", entity.alias)
          map.put("availability", Boolean.box(entity.availability))
          map.put("description", entity.description)
          representMapping(Tag.MAP, map, null)
        }
      }
    }
  }
  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    org.digimead.digi.lib.DependencyInjection.assertDynamic[Seq[api.TypeSchema]]
    lazy val default = inject[UUID]("TypeSchema.Default")
    /** Predefined type schemas that are available for this application */
    def predefinedTypeSchemas: Seq[api.TypeSchema] = {
      val predefinedSchemas = inject[Seq[api.TypeSchema]]
      assert(predefinedSchemas.map(_.name).distinct.size == predefinedSchemas.size, "There are type schemas with duplicated names.")
      predefinedSchemas
    }
  }
}
