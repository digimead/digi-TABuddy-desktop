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

package org.digimead.tabuddy.desktop.logic.operation.view

import scala.reflect.runtime.universe

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.definition.Operation
import org.digimead.tabuddy.desktop.logic.payload.view.api.Filter
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Model.model2implementation

/**
 * OperationModifyFilter base trait.
 */
trait OperationModifyFilter extends api.OperationModifyFilter {
  /**
   * Create 'Modify filter' operation.
   *
   * @param filter the initial filter
   * @param filterList the list of exists filters
   * @param modelId current model Id
   * @return 'Modify filter' operation
   */
  override def operation(filter: Filter, filterList: Set[Filter], modelId: Symbol): OperationModifyFilter.Abstract

  /**
   * Checks that this class can be subclassed.
   * <p>
   * The API class is intended to be subclassed only at specific,
   * controlled point. This method enforces this rule
   * unless it is overridden.
   * </p><p>
   * <em>IMPORTANT:</em> By providing an implementation of this
   * method that allows a subclass of a class which does not
   * normally allow subclassing to be created, the implementer
   * agrees to be fully responsible for the fact that any such
   * subclass will likely fail.
   * </p>
   */
  override protected def checkSubclass() {}
}

/**
 * Modify a view's filter.
 */
object OperationModifyFilter extends Loggable {
  /** Stable identifier with OperationModifyFilter DI */
  lazy val operation = DI.operation.asInstanceOf[Option[OperationModifyFilter]]

  /**
   * Build a new 'Modify filter' operation
   * @param filter the initial filter
   * @param filterList the list of exists filters
   * @param modelId current model Id
   * @return 'Modify filter' operation
   */
  @log
  def apply(filter: Filter, filterList: Set[Filter], modelId: Symbol = Model.eId): Option[Abstract] = {
    operation match {
      case Some(operation) =>
        Some(operation.operation(filter, filterList, modelId))
      case None =>
        log.error("OperationModifyFilter implementation is not defined.")
        None
    }
  }

  /** Bridge between abstract api.Operation[Filter] and concrete Operation[Filter] */
  abstract class Abstract(val filter: Filter, val filterList: Set[Filter], val modelId: Symbol)
    extends Operation[Filter](s"Edit filter $filter for model $modelId.") {
    this: Loggable =>
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    lazy val operation = injectOptional[api.OperationModifyFilter]
  }
}