/**
 * This file is part of the TABuddy project.
 * Copyright (c) 2013 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.model

import com.escalatesoft.subcut.inject.NewBindingModule

import org.digimead.digi.lib.DependencyInjection
import org.digimead.tabuddy.desktop.logic.payload.api.ElementTemplate
import org.digimead.tabuddy.desktop.logic.payload.api.Enumeration
import org.digimead.tabuddy.desktop.logic.payload.api.TypeSchema
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.element.Stash

/**
 * Model definition component contains:
 *   create element dialog
 *   modify element template dialog
 *   modify element template list dialog
 *   modify enumeration dialog
 *   modify enumeration list dialog
 *   modify type schema dialog
 *   modify type schema list dialog
 */
package object definition {
  lazy val default = new NewBindingModule(module => {
    // implementation of logic.operation.OperationCreateElement
    module.bind[(Element[_ <: Stash], Symbol) => org.digimead.tabuddy.desktop.logic.operation.api.OperationCreateElement] toSingle {
      (container, modelId) => new operation.OperationCreateElement(container, modelId)
    }
    // implementation of logic.operation.OperationModifyElementTemplate
    module.bind[(ElementTemplate, Set[ElementTemplate], Symbol) => org.digimead.tabuddy.desktop.logic.operation.api.OperationModifyElementTemplate] toSingle {
      (template, templateList, modelId) => new operation.OperationModifyElementTemplate(template, templateList, modelId)
    }
    // implementation of logic.operation.OperationModifyElementTemplateList
    module.bind[(Set[ElementTemplate], Symbol) => org.digimead.tabuddy.desktop.logic.operation.api.OperationModifyElementTemplateList] toSingle {
      (elementTemplates, modelId) => new operation.OperationModifyElementTemplateList(elementTemplates, modelId)
    }
    // implementation of logic.operation.OperationModifyEnumeration
    module.bind[(Enumeration[_ <: AnyRef with java.io.Serializable], Set[Enumeration[_ <: AnyRef with java.io.Serializable]], Symbol) => org.digimead.tabuddy.desktop.logic.operation.api.OperationModifyEnumeration] toSingle {
      (enumeration, enumerationList, modelId) => new operation.OperationModifyEnumeration(enumeration, enumerationList, modelId)
    }
    // implementation of logic.operation.OperationModifyEnumerationList
    module.bind[(Set[Enumeration[_ <: AnyRef with java.io.Serializable]], Symbol) => org.digimead.tabuddy.desktop.logic.operation.api.OperationModifyEnumerationList] toSingle {
      (enumerationList, modelId) => new operation.OperationModifyEnumerationList(enumerationList, modelId)
    }
    // implementation of logic.operation.OperationModifyTypeSchema
    module.bind[(TypeSchema, Set[TypeSchema], Boolean, Symbol) => org.digimead.tabuddy.desktop.logic.operation.api.OperationModifyTypeSchema] toSingle {
      (schema, schemaList, isActive, modelId) => new operation.OperationModifyTypeSchema(schema, schemaList, isActive, modelId)
    }
    // implementation of logic.operation.OperationModifyTypeSchemaList
    module.bind[(Set[TypeSchema], TypeSchema, Symbol) => org.digimead.tabuddy.desktop.logic.operation.api.OperationModifyTypeSchemaList] toSingle {
      (before, active, modelId) => new operation.OperationModifyTypeSchemaList(before, active, modelId)
    }
  })
  DependencyInjection.setPersistentInjectable("org.digimead.tabuddy.desktop.model.definition.Default$DI$")
}
