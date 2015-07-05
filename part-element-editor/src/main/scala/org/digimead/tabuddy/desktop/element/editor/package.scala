/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2013-2015 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.element

import org.digimead.digi.lib.DependencyInjection
import com.escalatesoft.subcut.inject.NewBindingModule
import org.digimead.tabuddy.desktop.logic.operation.api.XOperationCreateElement
import org.digimead.tabuddy.desktop.logic.operation.api.XOperationCreateElementFromTemplate
import org.digimead.tabuddy.desktop.logic.operation.api.XOperationDeleteElement
import org.digimead.tabuddy.desktop.logic.operation.api.XOperationModifyElement
import org.digimead.tabuddy.desktop.core.definition.api.XOperationApprover

/**
 * Element editor component contains:
 *   create element dialog
 *   modify element dialog
 */
package object editor {
  /** Bundle id. */
  lazy val bundleId = getClass.getPackage.getName
  /** Default view modification DI. */
  lazy val default = new NewBindingModule(module ⇒ {
    // implementation of logic.operation.OperationCreateElement
    module.bind[XOperationCreateElement] toSingle {
      new operation.OperationCreateElement()
    }
    // implementation of logic.operation.OperationCreateElementFromTemplate
    module.bind[XOperationCreateElementFromTemplate[_]] toSingle {
      new operation.OperationCreateElementFromTemplate()
    }
    // implementation of logic.operation.OperationDeleteElement
    module.bind[XOperationDeleteElement] toSingle {
      new operation.OperationDeleteElement()
    }
    // implementation of logic.operation.OperationModifyElement
    module.bind[XOperationModifyElement] toSingle {
      new operation.OperationModifyElement()
    }
    // OperationDeleteElement approver
    module.bind[XOperationApprover] identifiedBy ("Approver.ElementEditor.OperationDeleteElement") toSingle {
      new approver.OperationDeleteElement()
    }
  })
  /** Default bundle DI. */
  lazy val defaultBundle = default

  DependencyInjection.setPersistentInjectable("org.digimead.tabuddy.desktop.element.editor.Default$DI$")
}
