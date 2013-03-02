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

package org.digimead.tabuddy.desktop

import java.util.UUID

import scala.Option.option2Iterable
import scala.collection.immutable

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.tabuddy.desktop.payload.ElementTemplate
import org.digimead.tabuddy.desktop.payload.Enumeration
import org.digimead.tabuddy.desktop.payload.Payload
import org.digimead.tabuddy.desktop.payload.Payload.payload2implementation
import org.digimead.tabuddy.desktop.payload.PropertyType
import org.digimead.tabuddy.desktop.payload.TemplateProperty
import org.digimead.tabuddy.desktop.payload.TypeSchema
import org.digimead.tabuddy.desktop.support.WritableList
import org.digimead.tabuddy.desktop.support.WritableSet
import org.digimead.tabuddy.desktop.support.WritableValue
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Record
import org.digimead.tabuddy.model.dsl.DSLType
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.element.Stash
import org.eclipse.core.commands.operations.DefaultOperationHistory
import org.eclipse.core.commands.operations.IOperationHistory

import org.digimead.tabuddy.desktop.payload.DSL._

object Data extends DependencyInjection.PersistentInjectable with Loggable {
  implicit def bindingModule = DependencyInjection()
  /** The property representing available model list */
  lazy val availableModels = WritableList(List[String]())
  /** The property representing all available element templates for user, contains at least one predefined element */
  lazy val elementTemplates = WritableSet(Set[ElementTemplate.Interface]())
  /** The property representing all available enumerations*/
  lazy val enumerations = WritableSet(Set[Enumeration.Interface[_ <: AnyRef with java.io.Serializable]]())
  /** The property representing global current element field */
  // Element[_ <: Stash] == Element.Generic, avoid 'erroneous or inaccessible type' error
  lazy val fieldElement = WritableValue[Element[_ <: Stash]](Model)
  /** The property representing global model id field */
  lazy val fieldModelName = WritableValue[String]("")
  /**
   * A base implementation of IOperationHistory that implements a linear undo and
   * redo model . The most recently added operation is available for undo, and the
   * most recently undone operation is available for redo.
   */
  lazy val history = new DefaultOperationHistory()
  /** The property representing model ID that currently active */
  lazy val modelName = WritableValue[String]("")
  /*
   * Symbol ::= plainid
   *
   * op ::= opchar {opchar}
   * varid ::= lower idrest
   * plainid ::= upper idrest
   *           | varid
   *           | op
   * id ::= plainid
   *        | ‘\‘’ stringLit ‘\‘’
   * idrest ::= {letter | digit} [‘_’ op]
   *
   * Ll Letter, Lowercase
   * Lu Letter, Uppercase
   * Lt Letter, Titlecase
   * Lo Letter, Other
   * Lm Letter, Modifier
   * Nd Number, Decimal Digit
   * Nl (letter numbers like roman numerals)
   *
   * drop So, Sm and \u0020-\u007F
   */
  val symbolPattern = """[\p{Ll}\p{Lu}\p{Lt}\p{Lo}\p{Nd}\p{Nl}]+[\p{Ll}\p{Lu}\p{Lt}\p{Lo}\p{Nd}\p{Nl}_]*""".r.pattern
  /** The property representing all available type schemas */
  lazy val typeSchemas = WritableSet(Set[TypeSchema.Interface]())
  /** The property representing the active type schema */
  lazy val typeSchema = WritableValue[TypeSchema.Interface]

  // save type schema value to the current model at every change
  typeSchema.addChangeListener { event => Payload.settings.eSet[String]('Data_typeSchema, typeSchema.value.id.toString, "") }

  /** Get all template elements from the current model. */
  def getElementTemplates(): List[ElementTemplate.Interface] = {
    log.debug("load element template list for model " + Model.eId)
    val templates: scala.collection.mutable.HashSet[ElementTemplate.Interface] =
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
    templates.toList
  }
  /** Get all enumerations from the current model. */
  def getEnumerations(): Set[Enumeration.Interface[_ <: AnyRef with java.io.Serializable]] = {
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
  /** Get all schemas from the current settings. */
  def getTypeSchemas(): Set[TypeSchema.Interface] = {
    log.debug("load schema list for model " + Model.eId)
    val schemas = try {
      Payload.loadTypeSchemas(Model.eId)
    } catch {
      case e: Throwable =>
        log.error("unable to load type schemas: " + e, e)
        scala.collection.immutable.HashSet[TypeSchema.Interface]()
    }
    schemas.map { schema =>
      val lostEntities = TypeSchema.entities &~ schema.entity.values.toSet
      if (lostEntities.nonEmpty)
        schema.copy(entity = schema.entity ++ lostEntities.map(e => (e.ptypeId, e)))
      else
        schema
    } ++ TypeSchema.predefined.filter(predefined => !schemas.exists(_.id == predefined.id))
  }
  /** Get user types */
  def getAvailableTypes(defaultValue: Boolean = true): List[PropertyType[_ <: AnyRef with java.io.Serializable]] = Main.execNGet {
    val currentTypeSchema = Data.typeSchema.value
    PropertyType.container.values.toList.filter { ptype =>
      currentTypeSchema.entity.get(ptype.id).map(_.availability).getOrElse(defaultValue)
    }
  }
  /** Get user enumerations */
  def getAvailableEnumerations(): List[Enumeration.Interface[_ <: AnyRef with java.io.Serializable]] = Main.execNGet {
    Data.enumerations.toList.filter(_.availability)
  }
  /** This function is invoked at every model initialization */
  def onModelInitialization(oldModel: Model.Generic, newModel: Model.Generic, modified: Element.Timestamp) = {
    log.info(s"initialize model $newModel")
    val modelSet1 = Main.execNGet { immutable.SortedSet(availableModels: _*) }
    val modelSet2 = immutable.SortedSet(Payload.listModels.map(_.name): _*)
    val modelsRemoved = modelSet1 &~ modelSet2
    val modelsAdded = modelSet2 &~ modelSet1
    // The load order is important
    // Type schemas
    val typeSchemaSet = getTypeSchemas()
    Main.exec {
      // reload type schemas
      log.debug("update type schemas")
      typeSchemas.clear
      typeSchemas ++= typeSchemaSet
    }
    // Enumerations
    val enumerationSet = getEnumerations()
    Main.exec {
      // reload enumerations
      log.debug("update enumerations")
      enumerations.clear
      enumerations ++= enumerationSet
    }
    // Templates
    val elementTemplateList = getElementTemplates()
    Main.exec {
      // reload element templates
      log.debug("update element templates")
      elementTemplates.clear
      elementTemplates ++= elementTemplateList
      // set active type schema
      Payload.settings.eGet[String]('Data_typeSchema) match {
        case Some(schemaValue) =>
          val schemaUUID = UUID.fromString(schemaValue)
          typeSchemas.find(_.id == schemaUUID) match {
            case Some(schema) => typeSchema.value = schema
            case None => typeSchema.value = TypeSchema.predefined.head
          }
        case None =>
          typeSchema.value = TypeSchema.predefined.head
      }
    }
    // Model
    Main.exec {
      // update model name
      log.debug("set actial model name to " + newModel.eId.name)
      modelName.value = newModel.eId.name
      // update available models
      log.debug("update an available model list")
      modelsRemoved.foreach(name => availableModels -= name)
      modelsAdded.foreach(name => availableModels += name)
    }
    /*
     * fields
     */
    Main.exec {
      fieldElement.value = Model
      // skip fieldModelName
    }
    updateModelElements()
  }
  /** Update model elements */
  def updateModelElements() {
    // Record.Interface[_ <: Record.Stash]] == Record.Generic: avoid 'erroneous or inaccessible type' error
    val eTABuddy = inject[Record.Interface[_ <: Record.Stash]]("eTABuddy")
    if (eTABuddy.label.trim.isEmpty())
      eTABuddy.label = "TABuddy Desktop internal treespace"
    val eSettings = inject[Record.Interface[_ <: Record.Stash]]("eSettings")
    if (eSettings.label.trim.isEmpty())
      eSettings.label = "TABuddy Desktop settings"
    val eEnumerations = inject[Record.Interface[_ <: Record.Stash]]("eEnumeration")
    if (eEnumerations.label.trim.isEmpty())
      eEnumerations.label = "enumeration definitions"
    val eTemplates = inject[Record.Interface[_ <: Record.Stash]]("eElementTemplate")
    if (eTemplates.label.trim.isEmpty())
      eTemplates.label = "element template definitions"
  }
  /**
   * This function is invoked at application start
   */
  def start() {}
  /**
   * This function is invoked at application stop
   */
  def stop() {
    history.dispose(IOperationHistory.GLOBAL_UNDO_CONTEXT, true, true, true)
  }

  def commitInjection() {}
  def updateInjection() {}
}
