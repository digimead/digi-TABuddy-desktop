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

package org.digimead.tabuddy.desktop.ui.action

import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.tabuddy.desktop.Data
import org.digimead.tabuddy.desktop.job.JobModelAcquire
import org.digimead.tabuddy.desktop.job.JobModelFreeze
import org.digimead.tabuddy.desktop.payload.Payload
import org.digimead.tabuddy.desktop.res.Messages
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Model.model2implementation
import org.eclipse.jface.action.Action
import org.eclipse.jface.action.IAction

object ActionLocalStorageLock extends Action(Messages.lock_text, IAction.AS_CHECK_BOX) with Loggable {
  Data.fieldModelName.addChangeListener { (fieldName, event) => setEnabled(Option(fieldName).getOrElse("").trim.nonEmpty) }
  Data.modelName.addChangeListener { (name, event) => setChecked(name != Payload.defaultModelIdentifier.name) }

  override def run() = if (isChecked()) {
    // if model == default return None else Some
    // see module.bind[Model.Interface[Model.Stash]] at org.digimead.tabuddy.desktop.payload.default)
    val oldModelName = if (Model.eId == Payload.defaultModelIdentifier) None else Some(Model.eId)
    val newModelName = Symbol(Data.fieldModelName.value.trim)
    if (newModelName == Payload.defaultModelIdentifier || newModelName == Symbol("")) {
      log.warn("unable to create model with the default name")
      setChecked(false)
    } else
      JobModelAcquire(oldModelName, newModelName).foreach(_.execute)
  } else {
    JobModelFreeze(Symbol(Data.fieldModelName.value)).foreach(_.setOnSucceeded { job =>
      // Reset a model to default after successful freeze and unlock
      Model.reset()
    }.execute)
  }
}
