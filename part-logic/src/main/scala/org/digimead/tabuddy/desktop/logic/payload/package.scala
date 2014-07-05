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

package org.digimead.tabuddy.desktop.logic

import com.escalatesoft.subcut.inject.NewBindingModule
import java.io.File
import java.util.UUID
import org.digimead.digi.lib.DependencyInjection
import org.digimead.tabuddy.desktop.logic.payload.DSL._
import org.digimead.tabuddy.desktop.logic.payload.TypeSchema
import org.digimead.tabuddy.desktop.logic.payload.api.{ XElementTemplate, XPropertyType, XTypeSchema }
import org.digimead.tabuddy.desktop.logic.payload.template.{ Predefined, StringType, TextType }
import org.digimead.tabuddy.model.graph.Graph
import org.digimead.tabuddy.model.serialization.{ BuiltinSerialization, Serialization, YAMLSerialization }
import org.digimead.tabuddy.model.{ Model, Record }
import scala.collection.immutable

package object payload {
  type AnySRef = AnyRef with java.io.Serializable
  lazy val default = new NewBindingModule(module ⇒ {
    module.bind[File] identifiedBy "Payload" toModuleSingle { module ⇒
      module.inject[File](Some("Config")).getParentFile()
    }
    module.bind[Serialization.Identifier] identifiedBy "Payload.Serialization" toSingle { YAMLSerialization.Identifier }
    module.bind[Set[Serialization.Identifier]] identifiedBy "Payload.Serialization.Available" toSingle { Set(YAMLSerialization.Identifier, BuiltinSerialization.Identifier) }
    /** The map of the application property types (UI factories). */
    module.bind[XPropertyType[_ <: AnyRef with java.io.Serializable]] identifiedBy "PropertyType.String" toSingle { StringType }
    module.bind[XPropertyType[_ <: AnyRef with java.io.Serializable]] identifiedBy "PropertyType.Text" toSingle { TextType }
    /** The predefined template for Record element. */
    module.bind[XElementTemplate.Builder] identifiedBy "Template.Record" toSingle {
      new XElementTemplate.Builder { def apply(container: Record.Like) = Predefined.record(container) }
    }
    /** The predefined template for Note element. */
    module.bind[XElementTemplate.Builder] identifiedBy "Template.Note" toSingle {
      new XElementTemplate.Builder { def apply(container: Record.Like) = Predefined.note(container) }
    }
    /** The predefined template for Task element. */
    module.bind[XElementTemplate.Builder] identifiedBy "Template.Task" toSingle {
      new XElementTemplate.Builder { def apply(container: Record.Like) = Predefined.task(container) }
    }
    /** The default predefined type schema. */
    module.bind[XTypeSchema] identifiedBy "Schema.Default" toSingle {
      // add simple localized type schema
      TypeSchema(UUID.fromString("4ce08a80-6f10-11e2-bcfd-0800200c9a66"),
        Messages.localizedTypeSchemaName_text,
        Messages.localizedTypeSchemaDescription_text,
        immutable.HashMap(TypeSchema.entities.map(e ⇒ (e.ptypeId, e)).toSeq: _*))
    }
    /** Default type schema. */
    module.bind[UUID] identifiedBy "TypeSchema.Default" toSingle { UUID.fromString("4ce08a80-6f10-11e2-bcfd-0800200c9a66") }
    /*
     * Model elements
     */
    /** The TABuddy desktop container. */
    module.bind[Graph[_ <: Model.Like] ⇒ Record.Like] identifiedBy "eTABuddy" toProvider {
      graph: Graph[_ <: Model.Like] ⇒ graph.model | RecordLocation('TABuddy) | RecordLocation('Desktop)
    }
    /** A model settings container. */
    module.bind[Graph[_ <: Model.Like] ⇒ Record.Like] identifiedBy "eSettings" toProvider { module ⇒
      graph: Graph[_ <: Model.Like] ⇒ {
        val eTABuddyFn = module.inject[Graph[_ <: Model.Like] ⇒ Record.Like](Some("eTABuddy"))
        eTABuddyFn(graph) | RecordLocation('Settings)
      }
    }
    /** A model element templates container. */
    module.bind[Graph[_ <: Model.Like] ⇒ Record.Like] identifiedBy "eElementTemplate" toProvider { module ⇒
      graph: Graph[_ <: Model.Like] ⇒ {
        val eSettingsFn = module.inject[Graph[_ <: Model.Like] ⇒ Record.Like](Some("eSettings"))
        eSettingsFn(graph) | RecordLocation('Templates)
      }
    }
    /** A model element templates container with original templates. */
    module.bind[Graph[_ <: Model.Like] ⇒ Record.Like] identifiedBy "eElementTemplateOriginal" toProvider { module ⇒
      graph: Graph[_ <: Model.Like] ⇒ {
        val eElementTemplateFn = module.inject[Graph[_ <: Model.Like] ⇒ Record.Like](Some("eElementTemplate"))
        eElementTemplateFn(graph) | RecordLocation('Original)
      }
    }
    /** A model element templates container with user templates. */
    module.bind[Graph[_ <: Model.Like] ⇒ Record.Like] identifiedBy "eElementTemplateUser" toProvider { module ⇒
      graph: Graph[_ <: Model.Like] ⇒ {
        val eElementTemplateFn = module.inject[Graph[_ <: Model.Like] ⇒ Record.Like](Some("eElementTemplate"))
        eElementTemplateFn(graph) | RecordLocation('User)
      }
    }
    /** A model enumerations container. */
    module.bind[Graph[_ <: Model.Like] ⇒ Record.Like] identifiedBy "eEnumeration" toProvider { module ⇒
      graph: Graph[_ <: Model.Like] ⇒ {
        val eSettingsFn = module.inject[Graph[_ <: Model.Like] ⇒ Record.Like](Some("eSettings"))
        eSettingsFn(graph) | RecordLocation('Enumerations)
      }
    }
    /** A graph view modificator elements container. */
    module.bind[Graph[_ <: Model.Like] ⇒ Record.Like] identifiedBy "eView" toProvider { module ⇒
      graph: Graph[_ <: Model.Like] ⇒ {
        val eSettingsFn = module.inject[Graph[_ <: Model.Like] ⇒ Record.Like](Some("eSettings"))
        eSettingsFn(graph) | RecordLocation('Views)
      }
    }
    /** A graph view definitions container. */
    module.bind[Graph[_ <: Model.Like] ⇒ Record.Like] identifiedBy "eViewDefinition" toProvider { module ⇒
      graph: Graph[_ <: Model.Like] ⇒ {
        val eSettingsFn = module.inject[Graph[_ <: Model.Like] ⇒ Record.Like](Some("eView"))
        eSettingsFn(graph) | RecordLocation('Definitions)
      }
    }
    /** A graph view sortings container. */
    module.bind[Graph[_ <: Model.Like] ⇒ Record.Like] identifiedBy "eViewSorting" toProvider { module ⇒
      graph: Graph[_ <: Model.Like] ⇒ {
        val eSettingsFn = module.inject[Graph[_ <: Model.Like] ⇒ Record.Like](Some("eView"))
        eSettingsFn(graph) | RecordLocation('Definitions)
      }
    }
    /** A graph view filters container. */
    module.bind[Graph[_ <: Model.Like] ⇒ Record.Like] identifiedBy "eViewFilter" toProvider { module ⇒
      graph: Graph[_ <: Model.Like] ⇒ {
        val eSettingsFn = module.inject[Graph[_ <: Model.Like] ⇒ Record.Like](Some("eView"))
        eSettingsFn(graph) | RecordLocation('Definitions)
      }
    }
  })
  DependencyInjection.setPersistentInjectable("org.digimead.tabuddy.desktop.logic.payload.ElementTemplate$DI$")
  DependencyInjection.setPersistentInjectable("org.digimead.tabuddy.desktop.logic.payload.Payload$DI$")
  DependencyInjection.setPersistentInjectable("org.digimead.tabuddy.desktop.logic.payload.PropertyType$DI$")
  DependencyInjection.setPersistentInjectable("org.digimead.tabuddy.desktop.logic.payload.TypeSchema$DI$")
  DependencyInjection.setPersistentInjectable("org.digimead.tabuddy.desktop.logic.payload.template.Predefined$DI$")
}
