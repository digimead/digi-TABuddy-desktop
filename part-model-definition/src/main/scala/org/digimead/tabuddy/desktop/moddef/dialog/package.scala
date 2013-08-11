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

package org.digimead.tabuddy.desktop.ui.dialog

/*import org.digimead.tabuddy.desktop.job.OperationCreateElement
import org.digimead.tabuddy.desktop.job.OperationCreateElementFromTemplate
import org.digimead.tabuddy.desktop.job.OperationCreateElementFromTemplateImplementation
import org.digimead.tabuddy.desktop.job.OperationCreateElementImplementation
import org.digimead.tabuddy.desktop.job.JobModifyElement
import org.digimead.tabuddy.desktop.job.JobModifyElementImplementation
import org.digimead.tabuddy.desktop.job.JobModifyElementTemplate
import org.digimead.tabuddy.desktop.job.JobModifyElementTemplateImplementation
import org.digimead.tabuddy.desktop.job.OperationModifyElementTemplateList
import org.digimead.tabuddy.desktop.job.OperationModifyElementTemplateListImplementation
import org.digimead.tabuddy.desktop.job.JobModifyEnumeration
import org.digimead.tabuddy.desktop.job.JobModifyEnumerationImplementation
import org.digimead.tabuddy.desktop.job.JobModifyEnumerationList
import org.digimead.tabuddy.desktop.job.JobModifyEnumerationListImplementation
import org.digimead.tabuddy.desktop.job.JobModifyTypeSchema
import org.digimead.tabuddy.desktop.job.JobModifyTypeSchemaImplementation
import org.digimead.tabuddy.desktop.job.JobModifyTypeSchemaList
import org.digimead.tabuddy.desktop.job.JobModifyTypeSchemaListImplementation
import org.digimead.tabuddy.desktop.payload.ElementTemplate
import org.digimead.tabuddy.desktop.payload.Enumeration
import org.digimead.tabuddy.desktop.payload.TypeSchema
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.element.Stash*/

import com.escalatesoft.subcut.inject.NewBindingModule

package object model {
/*  lazy val default = new NewBindingModule(module => {
    // OperationCreateElementImplementation
    module.bind[(Element[_ <: Stash], Symbol) => OperationCreateElement] toSingle {
      (container: Element.Generic, modelID: Symbol) => new OperationCreateElementImplementation(container, modelID)
    }
    // OperationCreateElementFromTemplateImplementation
    module.bind[(ElementTemplate.Interface, Element[_ <: Stash], Symbol) => OperationCreateElementFromTemplate] toSingle {
      (template: ElementTemplate.Interface, container: Element.Generic, modelID: Symbol) => new OperationCreateElementFromTemplateImplementation(template, container, modelID)
    }
    // JobModifyElementImplementation
    module.bind[(Element[_ <: Stash], Symbol) => JobModifyElement] toSingle {
      (element: Element.Generic, modelID: Symbol) => new JobModifyElementImplementation(element, modelID)
    }
    // JobModifyElementTemplateImplementation
    module.bind[(ElementTemplate.Interface, Set[ElementTemplate.Interface], Symbol) => JobModifyElementTemplate] toSingle {
      (template: ElementTemplate.Interface, templateList: Set[ElementTemplate.Interface], modelID: Symbol) => new JobModifyElementTemplateImplementation(template, templateList, modelID)
    }
    // OperationModifyElementTemplateListImplementation
    module.bind[(Set[ElementTemplate.Interface], Symbol) => OperationModifyElementTemplateList] toSingle {
      (templateList: Set[ElementTemplate.Interface], modelID: Symbol) => new OperationModifyElementTemplateListImplementation(templateList, modelID)
    }
    // JobModifyEnumerationImplementation
    module.bind[(Enumeration.Interface[_ <: AnyRef with java.io.Serializable], Set[Enumeration.Interface[_ <: AnyRef with java.io.Serializable]], Symbol) => JobModifyEnumeration] toSingle {
      (enumeration: Enumeration.Interface[_ <: AnyRef with java.io.Serializable],
      enumerationList: Set[Enumeration.Interface[_ <: AnyRef with java.io.Serializable]],
      modelID: Symbol) => new JobModifyEnumerationImplementation(enumeration, enumerationList, modelID)
    }
    // JobModifyEnumerationListImplementation
    module.bind[(Set[Enumeration.Interface[_ <: AnyRef with java.io.Serializable]], Symbol) => JobModifyEnumerationList] toSingle {
      (enumerationList: Set[Enumeration.Interface[_ <: AnyRef with java.io.Serializable]],
      modelID: Symbol) => new JobModifyEnumerationListImplementation(enumerationList, modelID)
    }
    // JobModifyTypeSchemaImplementation
    module.bind[(TypeSchema.Interface, Set[TypeSchema.Interface], Boolean, Symbol) => JobModifyTypeSchema] toSingle {
      (schema: TypeSchema.Interface,
      schemaList: Set[TypeSchema.Interface],
      isActive: Boolean,
      modelID: Symbol) => new JobModifyTypeSchemaImplementation(schema, schemaList, isActive, modelID)
    }
    // JobModifyTypeSchemaListImplementation
    module.bind[(Set[TypeSchema.Interface], TypeSchema.Interface, Symbol) => JobModifyTypeSchemaList] toSingle {
      (before: Set[TypeSchema.Interface],
      active: TypeSchema.Interface,
      modelID: Symbol) => new JobModifyTypeSchemaListImplementation(before, active, modelID)
    }
  })*/
}
