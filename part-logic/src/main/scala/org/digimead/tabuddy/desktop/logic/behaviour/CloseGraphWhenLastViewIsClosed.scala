/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2014 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.logic.behaviour

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.logic.operation.graph.OperationGraphClose
import org.digimead.tabuddy.desktop.logic.payload.maker.GraphMarker
import org.eclipse.core.runtime.jobs.Job
import scala.language.implicitConversions

class CloseGraphWhenLastViewIsClosed extends Loggable {
  def run() {
    log.debug("Check for last view.")
    val allOpened = GraphMarker.list().map(GraphMarker(_)).filter(m ⇒ m.markerIsValid && m.graphIsOpen()).toSet
    val allExists = UI.viewMap.flatMap(_._2.getContext().flatMap(context ⇒ Option(context.getActive(classOf[GraphMarker])))).toSet
    val diff = allOpened.diff(allExists)
    if (diff.isEmpty)
      return
    log.debug(s"Close unbinded ${diff.mkString(",")}.")
    diff.foreach { marker ⇒
      OperationGraphClose(marker.safeRead(_.graph), false).foreach { operation ⇒
        operation.getExecuteJob() match {
          case Some(job) ⇒
            job.setPriority(Job.LONG)
            job.schedule()
          case None ⇒
            throw new RuntimeException(s"Unable to create job for ${operation}.")
        }
      }
    }
  }
}

object CloseGraphWhenLastViewIsClosed {
  implicit def class2implementation(c: CloseGraphWhenLastViewIsClosed.type): CloseGraphWhenLastViewIsClosed = c.inner

  /** CloseGraphWhenLastViewIsClosed implementation. */
  def inner = DI.implementation

  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** CloseGraphWhenLastViewIsClosed implementation. */
    lazy val implementation = injectOptional[CloseGraphWhenLastViewIsClosed] getOrElse new CloseGraphWhenLastViewIsClosed
  }
}
