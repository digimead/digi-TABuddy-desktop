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
import org.digimead.tabuddy.desktop.payload.view.Filter
import org.digimead.tabuddy.desktop.payload.view.Sorting
import org.digimead.tabuddy.desktop.payload.view.View
import org.digimead.tabuddy.desktop.support.WritableList
import org.digimead.tabuddy.desktop.support.WritableMap
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
  lazy val availableModels = WritableList[String]
  /** The property representing all available element templates for user, contains at least one predefined element */
  lazy val elementTemplates = WritableMap[Symbol, ElementTemplate.Interface]
  /** The property representing all available enumerations */
  lazy val enumerations = WritableMap[Symbol, Enumeration.Interface[_ <: AnyRef with java.io.Serializable]]
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
  lazy val typeSchemas = WritableMap[UUID, TypeSchema.Interface]
  /** The property representing the active type schema */
  lazy val typeSchema = WritableValue[TypeSchema.Interface]
  /** The property representing all available view definitions */
  lazy val viewDefinitions = WritableMap[UUID, View]
  /** The property representing all available view filters */
  lazy val viewFilters = WritableMap[UUID, Filter]
  /** The property representing all available view sortings */
  lazy val viewSortings = WritableMap[UUID, Sorting]

  // save type schema value to the current model at every change
  typeSchema.addChangeListener { (schema, event) => Payload.settings.eSet[String]('Data_typeSchema, schema.id.toString, "") }

  /** Get user enumerations */
  def getAvailableElementTemplates(): List[ElementTemplate.Interface] =
    Main.execNGet { Data.elementTemplates.values.filter(_.availability).toList }
  /** Get user enumerations */
  def getAvailableEnumerations(): List[Enumeration.Interface[_ <: AnyRef with java.io.Serializable]] =
    Main.execNGet { Data.enumerations.values.filter(_.availability).toList }
  /** Get user types */
  def getAvailableTypes(defaultValue: Boolean = true): List[PropertyType[_ <: AnyRef with java.io.Serializable]] = Main.execNGet {
    val currentTypeSchema = Data.typeSchema.value
    PropertyType.container.values.toList.filter(ptype =>
      currentTypeSchema.entity.get(ptype.id).map(_.availability).getOrElse(defaultValue))
  }
  /** Get user view definitions */
  def getAvailableViewDefinitions(): Set[View] = Main.execNGet {
    val result = View.default +: Data.viewDefinitions.values.filter(_.availability).toList.sortBy(_.name)
    if (result.isEmpty) Set(View.default) else result.toSet
  }
  /** Get user view filters */
  def getAvailableViewFilters(): Set[Filter] = Main.execNGet {
    val result = Filter.default +: Data.viewFilters.values.filter(_.availability).toList.sortBy(_.name)
    if (result.isEmpty) Set(Filter.default) else result.toSet
  }
  /** Get user view sortings */
  def getAvailableViewSortings(): Set[Sorting] = Main.execNGet {
    val result = Sorting.default +: Data.viewSortings.values.filter(_.availability).toList.sortBy(_.name)
    if (result.isEmpty) Set(Sorting.default) else result.toSet
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
    val typeSchemaSet = TypeSchema.load()
    Main.exec {
      // reload type schemas
      log.debug("update type schemas")
      Data.typeSchemas.clear
      typeSchemaSet.foreach(schema => Data.typeSchemas(schema.id) = schema)
    }
    // Enumerations
    val enumerationSet = Enumeration.load()
    Main.exec {
      // reload enumerations
      log.debug("update enumerations")
      Data.enumerations.clear
      enumerationSet.foreach(enumeration => Data.enumerations(enumeration.id) = enumeration)
    }
    // Templates
    val elementTemplateSet = ElementTemplate.load()
    Main.exec {
      // reload element templates
      log.debug("update element templates")
      Data.elementTemplates.clear
      elementTemplateSet.foreach(template => Data.elementTemplates(template.id) = template)
      // set active type schema
      Payload.settings.eGet[String]('Data_typeSchema) match {
        case Some(schemaValue) =>
          val schemaUUID = UUID.fromString(schemaValue)
          Data.typeSchemas.get(schemaUUID) match {
            case Some(schema) => Data.typeSchema.value = schema
            case None => Data.typeSchema.value = TypeSchema.predefined.head
          }
        case None =>
          Data.typeSchema.value = TypeSchema.predefined.head
      }
    }
    // Model
    Main.exec {
      // update model name
      log.debug("set actial model name to " + newModel.eId.name)
      Data.modelName.value = newModel.eId.name
      // update available models
      log.debug("update an available model list")
      modelsRemoved.foreach(name => Data.availableModels -= name)
      modelsAdded.foreach(name => Data.availableModels += name)
    }
    // View
    Main.exec {
      log.debug("update view difinitions")
      Data.viewDefinitions.clear
      View.load.foreach(view => Data.viewDefinitions(view.id) = view)
      Data.viewFilters.clear
      Filter.load.foreach(filter => Data.viewFilters(filter.id) = filter)
      Data.viewSortings.clear
      Sorting.load.foreach(sorting => Data.viewSortings(sorting.id) = sorting)
    }
    /*
     * fields
     */
    Main.exec {
      Data.fieldElement.value = Model
      // skip fieldModelName
    }
    /*
     * create elements if needed
     * add description to elements
     * modify model values
     */
    updateModelElements()
  }
  /** Update model elements */
  def updateModelElements() {
    // Record.Interface[_ <: Record.Stash]] == Record.Generic: avoid 'erroneous or inaccessible type' error
    val eTABuddy = inject[Record.Interface[_ <: Record.Stash]]("eTABuddy")
    if (eTABuddy.name.trim.isEmpty())
      eTABuddy.name = "TABuddy Desktop internal treespace"
    eTABuddy.eParent match {
      case Some(eTABaddyContainer: Record.Generic) =>
        if (eTABaddyContainer.name.trim.isEmpty())
          eTABaddyContainer.name = "TABuddy internal treespace"
      case _ =>
    }
    // settings
    val eSettings = inject[Record.Interface[_ <: Record.Stash]]("eSettings")
    if (eSettings.name.trim.isEmpty())
      eSettings.name = "TABuddy Desktop settings"
    val eEnumerations = inject[Record.Interface[_ <: Record.Stash]]("eEnumeration")
    if (eEnumerations.name.trim.isEmpty())
      eEnumerations.name = "Enumeration definitions"
    val eTemplates = inject[Record.Interface[_ <: Record.Stash]]("eElementTemplate")
    if (eTemplates.name.trim.isEmpty())
      eTemplates.name = "Element template definitions"
    // view
    val eView = inject[Record.Interface[_ <: Record.Stash]]("eView")
    if (eView.name.trim.isEmpty())
      eView.name = "TABuddy Desktop view modificator elements"
    // view definition
    val eViewDefinition = inject[Record.Interface[_ <: Record.Stash]]("eViewDefinition")
    if (eViewDefinition.name.trim.isEmpty())
      eViewDefinition.name = "View definition"
    // view sorting
    val eViewSorting = inject[Record.Interface[_ <: Record.Stash]]("eViewSorting")
    if (eViewSorting.name.trim.isEmpty())
      eViewSorting.name = "View sorting"
    // view filter
    val eViewFilter = inject[Record.Interface[_ <: Record.Stash]]("eViewFilter")
    if (eViewFilter.name.trim.isEmpty())
      eViewFilter.name = "View filter"
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
