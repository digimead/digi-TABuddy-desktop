/**
 * This file is part of the TA Buddy project.
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

package org.digimead.tabuddy.desktop

import com.escalatesoft.subcut.inject.NewBindingModule
import java.io.File
import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.util.Util

/**
 * This is the core bundle of the TA Buddy desktop application.
 */
package object core {
  lazy val default = new NewBindingModule(module ⇒ {
    // Get 'tabuddy.rootURL' path from System.getProperty("tabuddy.rootURL") or
    // Try to get jar location or get current directory
    module.bind[File] identifiedBy "Root" toSingle { Util.getPath("tabuddy.rootURL", true) orElse Util.getPath(getClass, true) getOrElse new File(".") }
    // Configuration file located at {data}/configuration/tabuddy.conf by default
    module.bind[File] identifiedBy "Config" toModuleSingle { module ⇒
      val configName = "tabuddy.conf"
      val dataPath = module.inject[File](Some("Root"))
      val configurationPath = new File(dataPath, "configuration")
      new File(configurationPath, configName)
    }
  })
  lazy val defaultBundle = default ~ command.default ~ operation.default
  DependencyInjection.setPersistentInjectable("org.digimead.tabuddy.desktop.core.definition.NLS$DI$")
}
