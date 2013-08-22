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

package org.digimead.tabuddy.desktop.logic

import java.util.UUID

import scala.Option.option2Iterable
import scala.collection.immutable

import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.definition.Context
import org.digimead.tabuddy.desktop.logic.payload.DSL._
import org.digimead.tabuddy.desktop.logic.payload.ElementTemplate
import org.digimead.tabuddy.desktop.logic.payload.Enumeration
import org.digimead.tabuddy.desktop.logic.payload.Payload
import org.digimead.tabuddy.desktop.logic.payload.Payload.payload2implementation
import org.digimead.tabuddy.desktop.logic.payload.PropertyType
import org.digimead.tabuddy.desktop.logic.payload.TemplateProperty
import org.digimead.tabuddy.desktop.logic.payload.TypeSchema
import org.digimead.tabuddy.desktop.logic.payload.view
import org.digimead.tabuddy.desktop.logic.payload.view.Filter
import org.digimead.tabuddy.desktop.logic.payload.view.Sorting
import org.digimead.tabuddy.desktop.logic.payload.view.View
import org.digimead.tabuddy.desktop.support.App
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
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext

object Data extends Loggable {
  /** The property representing available model list */
  lazy val availableModels = WritableList[String]
  /** The property representing all available element templates for user, contains at least one predefined element */
  lazy val elementTemplates = WritableMap[Symbol, payload.api.ElementTemplate]
  /** The property representing all available enumerations */
  lazy val enumerations = WritableMap[Symbol, payload.api.Enumeration[_ <: AnyRef with java.io.Serializable]]
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
  lazy val typeSchemas = WritableMap[UUID, payload.api.TypeSchema]
  /** The property representing the active type schema */
  lazy val typeSchema = WritableValue[payload.api.TypeSchema]
  /** The property representing all available view definitions */
  lazy val viewDefinitions = WritableMap[UUID, view.api.View]
  /** The property representing all available view filters */
  lazy val viewFilters = WritableMap[UUID, view.api.Filter]
  /** The property representing all available view sortings */
  lazy val viewSortings = WritableMap[UUID, view.api.Sorting]

  // save type schema value to the current model at every change
  typeSchema.addChangeListener { (schema, event) => Payload.settings.eSet[String]('activeTypeSchema, schema.id.toString, "") }

  /** Get user enumerations */
  def getAvailableElementTemplates(): List[payload.api.ElementTemplate] =
    App.execNGet { Data.elementTemplates.values.filter(_.availability).toList }
  /** Get user enumerations */
  def getAvailableEnumerations(): List[payload.api.Enumeration[_ <: AnyRef with java.io.Serializable]] =
    App.execNGet { Data.enumerations.values.filter(_.availability).toList }
  /** Get user types */
  def getAvailableTypes(defaultValue: Boolean = true): List[payload.api.PropertyType[_ <: AnyRef with java.io.Serializable]] = App.execNGet {
    val currentTypeSchema = Data.typeSchema.value
    PropertyType.container.values.toList.filter(ptype =>
      currentTypeSchema.entity.get(ptype.id).map(_.availability).getOrElse(defaultValue))
  }
  /** Get all available view definitions. */
  def getAvailableViewDefinitions(): Set[view.api.View] = App.execNGet {
    val result = View.displayName +: Data.viewDefinitions.values.filter(_.availability).toList.sortBy(_.name)
    if (result.isEmpty) Set(View.displayName) else result.toSet
  }
  /** Get all available view filters. */
  def getAvailableViewFilters(): Set[view.api.Filter] = App.execNGet {
    val result = Filter.allowAllFilter +: Data.viewFilters.values.filter(_.availability).toList.sortBy(_.name)
    if (result.isEmpty) Set(Filter.allowAllFilter) else result.toSet
  }
  /** Get all available view sortings. */
  def getAvailableViewSortings(): Set[view.api.Sorting] = App.execNGet {
    val result = Sorting.simpleSorting +: Data.viewSortings.values.filter(_.availability).toList.sortBy(_.name)
    if (result.isEmpty) Set(Sorting.simpleSorting) else result.toSet
  }
  /** Get selected view definitions. */
  def getSelectedViewDefinition(context: Context): Option[view.api.View] = None
  /** Get selected view filter. */
  def getSelectedViewFilter(context: Context): Option[view.api.Filter] = None
  /** Get selected view sorting. */
  def getSelectedViewSorting(context: Context): Option[view.api.Sorting] = None
  /** This function is invoked at every model initialization */
  def onModelInitialization(oldModel: Model.Generic, newModel: Model.Generic, modified: Element.Timestamp) = {
    log.info(s"Initialize model $newModel.")
    // The load order is important
    App.execNGet {
      // Type schemas
      val typeSchemaSet = TypeSchema.load()
      // reload type schemas
      log.debug("Update type schemas.")
      Data.typeSchemas.clear
      typeSchemaSet.foreach(schema => Data.typeSchemas(schema.id) = schema)
      // Enumerations
      val enumerationSet = Enumeration.load()
      // reload enumerations
      log.debug("Update enumerations.")
      Data.enumerations.clear
      enumerationSet.foreach(enumeration => Data.enumerations(enumeration.id) = enumeration)
      // Templates
      val elementTemplateSet = ElementTemplate.load()
      // reload element templates
      log.debug("Update element templates.")
      Data.elementTemplates.clear
      elementTemplateSet.foreach(template => Data.elementTemplates(template.id) = template)
      // set active type schema
      Payload.settings.eGet[String]('activeTypeSchema) match {
        case Some(schemaValue) =>
          val schemaUUID = UUID.fromString(schemaValue)
          Data.typeSchemas.get(schemaUUID) match {
            case Some(schema) => Data.typeSchema.value = schema
            case None => Data.typeSchema.value = TypeSchema.predefined.head
          }
        case None =>
          Data.typeSchema.value = TypeSchema.predefined.head
      }
      // Model
      // update model name
      log.debug(s"Set actial model name to ${newModel.eId.name}.")
      Data.modelName.value = newModel.eId.name
      // update available models
      updateAvailableModels()
      // View
      log.debug("Update view difinitions.")
      Data.viewDefinitions.clear
      View.load.foreach(view => Data.viewDefinitions(view.id) = view)
      Data.viewFilters.clear
      Filter.load.foreach(filter => Data.viewFilters(filter.id) = filter)
      Data.viewSortings.clear
      Sorting.load.foreach(sorting => Data.viewSortings(sorting.id) = sorting)
    }
    /*
     * create elements if needed
     * add description to elements
     * modify model values
     */
    updateModelElements()
  }
  /** Update available models. */
  def updateAvailableModels() {
    log.debug("Update an available model list.")
    App.assertUIThread()
    val modelSet1 = immutable.SortedSet(availableModels: _*)
    val modelSet2 = immutable.SortedSet(Payload.listModels.flatMap { marker =>
      try {
        Option(marker.id.name)
      } catch {
        case e: Throwable =>
          log.warn(s"Broken model marker ${marker}: " + e.getMessage)
          None
      }
    }: _*)
    val modelsRemoved = modelSet1 &~ modelSet2
    val modelsAdded = modelSet2 &~ modelSet1
    modelsRemoved.foreach(name => Data.availableModels -= name)
    modelsAdded.foreach(name => Data.availableModels += name)
  }
  /** Update model elements. */
  def updateModelElements() {
    // Record.Interface[_ <: Record.Stash]] == Record.Generic: avoid 'erroneous or inaccessible type' error
    val eTABuddy = DI.inject[Record.Interface[_ <: Record.Stash]]("eTABuddy")
    if (eTABuddy.name.trim.isEmpty())
      eTABuddy.name = "TABuddy Desktop internal treespace"
    eTABuddy.eParent match {
      case Some(eTABaddyContainer: Record.Generic) =>
        if (eTABaddyContainer.name.trim.isEmpty())
          eTABaddyContainer.name = "TABuddy internal treespace"
      case _ =>
    }
    // settings
    val eSettings = DI.inject[Record.Interface[_ <: Record.Stash]]("eSettings")
    if (eSettings.name.trim.isEmpty())
      eSettings.name = "TABuddy Desktop settings"
    val eEnumerations = DI.inject[Record.Interface[_ <: Record.Stash]]("eEnumeration")
    if (eEnumerations.name.trim.isEmpty())
      eEnumerations.name = "Enumeration definitions"
    val eTemplates = DI.inject[Record.Interface[_ <: Record.Stash]]("eElementTemplate")
    if (eTemplates.name.trim.isEmpty())
      eTemplates.name = "Element template definitions"
    // view
    val eView = DI.inject[Record.Interface[_ <: Record.Stash]]("eView")
    if (eView.name.trim.isEmpty())
      eView.name = "TABuddy Desktop view modificator elements"
    // view definition
    val eViewDefinition = DI.inject[Record.Interface[_ <: Record.Stash]]("eViewDefinition")
    if (eViewDefinition.name.trim.isEmpty())
      eViewDefinition.name = "View definition"
    // view sorting
    val eViewSorting = DI.inject[Record.Interface[_ <: Record.Stash]]("eViewSorting")
    if (eViewSorting.name.trim.isEmpty())
      eViewSorting.name = "View sorting"
    // view filter
    val eViewFilter = DI.inject[Record.Interface[_ <: Record.Stash]]("eViewFilter")
    if (eViewFilter.name.trim.isEmpty())
      eViewFilter.name = "View filter"
  }
  object Id {
    /** Value of the various UI elements with model id value. */
    final val modelIdUserInput = "org.digimead.tabuddy.desktop.logic.Data/modelIdUserInput"
    /** Value of the selected model element. */
    final val selectedElementUserInput = "org.digimead.tabuddy.desktop.logic.Data/selectedElementUserInput"
    /** Value of the selected view ID. */
    final val selectedView = "org.digimead.tabuddy.desktop.logic.Data/selectedView"
    /** Value of the selected sorting ID. */
    final val selectedSorting = "org.digimead.tabuddy.desktop.logic.Data/selectedSorting"
    /** Value of the selected filter ID. */
    final val selectedFilter = "org.digimead.tabuddy.desktop.logic.Data/selectedFilter"
    /** Flag indicating whether this view is using 'view definitions/filters/sortings'. */
    final val usingViewDefinition = "org.digimead.tabuddy.desktop.logic.Data/usingViewDefinition"
  }
  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    lazy val default = inject[UUID]("TypeSchema.Default")
    /** Predefined type schemas that are available for this application */
    lazy val predefinedSchemas: Seq[payload.api.TypeSchema] = {
      val predefinedSchemas = inject[Seq[payload.api.TypeSchema]]
      assert(predefinedSchemas.map(_.name).distinct.size == predefinedSchemas.size, "There are type schemas with duplicated names.")
      predefinedSchemas
    }
  }
}
