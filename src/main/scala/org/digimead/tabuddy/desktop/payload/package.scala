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

import scala.collection.immutable

import java.io.File
import java.util.UUID

import com.escalatesoft.subcut.inject.NewBindingModule

import org.digimead.tabuddy.desktop.payload.ElementTemplate
import org.digimead.tabuddy.desktop.payload.Payload
import org.digimead.tabuddy.desktop.payload.template.StringType
import org.digimead.tabuddy.desktop.payload.template.TextType
import org.digimead.tabuddy.desktop.res.Messages
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.dsl.DSLType
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.element.Stash
import org.digimead.tabuddy.model.serialization.BuiltinSerialization
import org.digimead.tabuddy.model.serialization.ProtobufSerialization
import org.digimead.tabuddy.model.serialization.Serialization

import org.digimead.tabuddy.desktop.payload.DSL._

package object payload {
  lazy val default = new NewBindingModule(module => {
    // The illegal leading '_' prevent the symbol duplication against user models
    module.bind[Symbol] identifiedBy "Payload.DefaultModel" toSingle (Symbol("_Default_"))
    module.bind[Model.Interface[Model.Stash]] toModuleSingle { implicit module =>
      new PayloadModel(new Model.Stash(module.inject[Symbol](Some("Payload.DefaultModel")), UUID.randomUUID()))
    }
    module.bind[File] identifiedBy "Payload" toModuleSingle { module =>
      module.inject[File](Some("Config")).getParentFile()
    }
    module.bind[String] identifiedBy "Payload.Element.Extension" toModuleSingle { module =>
      module.inject[Serialization[Array[Byte]]](Some("Payload.Serialization")) match {
        case _: BuiltinSerialization => "bcode"
        case _: ProtobufSerialization => "pbuf"
        case _ => "element"
      }
    }
    module.bind[Serialization[Array[Byte]]] identifiedBy "Payload.Serialization" toSingle { new BuiltinSerialization }
    module.bind[Payload.Interface] toModuleSingle { implicit module => new Payload }
    /** The map of the application property types (UI factories) */
    module.bind[Seq[PropertyType[_ <: AnyRef with java.io.Serializable]]] toSingle {
      Seq[PropertyType[_ <: AnyRef with java.io.Serializable]](StringType, TextType)
    }
    /** The sequence of the application predefined templates */
    module.bind[Seq[ElementTemplate.Interface]] toProvider {
      Seq[ElementTemplate.Interface](
        ElementTemplate.initPredefinedCustom,
        ElementTemplate.initPredefinedNote,
        ElementTemplate.initPredefinedTask)
    }
    /** A model settings container */
    // Element[_ <: Stash] == Element.Generic, avoid 'erroneous or inaccessible type' error
    module.bind[Element[_ <: Stash]] identifiedBy "Settings" toProvider { Model | RecordLocation('TABuddy) | RecordLocation('Settings) }
    /** A model element templates container */
    // Element[_ <: Stash] == Element.Generic, avoid 'erroneous or inaccessible type' error
    module.bind[Element[_ <: Stash]] identifiedBy "ElementTemplate" toProvider { module => module.inject[Element[_ <: Stash]](Some("Settings")) | RecordLocation('Templates) }
    /** A model enumerations container */
    // Element[_ <: Stash] == Element.Generic, avoid 'erroneous or inaccessible type' error
    module.bind[Element[_ <: Stash]] identifiedBy "Enumeration" toProvider { module => module.inject[Element[_ <: Stash]](Some("Settings")) | RecordLocation('Enumerations) }
    /** List of predefined type schemas */
    module.bind[Seq[TypeSchema.Interface]] toProvider {
      Seq[TypeSchema.Interface]({
        // add simple localized type schema
        TypeSchema(UUID.fromString("4ce08a80-6f10-11e2-bcfd-0800200c9a66"),
          Messages.localizedTypeSchemaName_text,
          Messages.localizedTypeSchemaDescription_text,
          immutable.HashMap(TypeSchema.entities.map(e => (e.ptypeId, e)).toSeq: _*))
      })
    }
    /** Default type schema */
    module.bind[UUID] identifiedBy "TypeSchema.Default" toSingle { UUID.fromString("4ce08a80-6f10-11e2-bcfd-0800200c9a66") }
  })
}
