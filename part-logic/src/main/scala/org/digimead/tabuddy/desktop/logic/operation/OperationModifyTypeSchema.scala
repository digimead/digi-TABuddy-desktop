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

package org.digimead.tabuddy.desktop.logic.operation

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.logic.operation.api.XOperationModifyTypeSchema
import org.digimead.tabuddy.desktop.logic.payload.api.XTypeSchema
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.graph.Graph

/**
 * OperationModifyTypeSchema base trait.
 */
trait OperationModifyTypeSchema extends XOperationModifyTypeSchema {
  /**
   * Create 'Modify a type schema' operation.
   *
   * @param graph graph that contains a type schema
   * @param schema the initial type schema
   * @param schemaList exists type schemas
   * @param isSchemaActive the flag indicating whether the type schema is active
   * @return 'Modify a type schema' operation
   */
  def operation(graph: Graph[_ <: Model.Like], schema: XTypeSchema, schemaList: Set[XTypeSchema], isSchemaActive: Boolean): OperationModifyTypeSchema.Abstract

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
 * Modify a type schema.
 */
object OperationModifyTypeSchema extends XLoggable {
  /** Stable identifier with OperationModifyTypeSchema DI */
  lazy val operation = DI.operation.asInstanceOf[Option[OperationModifyTypeSchema]]

  @log
  def apply(graph: Graph[_ <: Model.Like], schema: XTypeSchema, schemaList: Set[XTypeSchema], isSchemaActive: Boolean): Option[Abstract] =
    operation match {
      case Some(operation) ⇒
        Some(operation.operation(graph, schema, schemaList, isSchemaActive))
      case None ⇒
        log.error("OperationModifyTypeSchema implementation is not defined.")
        None
    }

  abstract class Abstract(val graph: Graph[_ <: Model.Like], val schema: XTypeSchema, val schemaList: Set[XTypeSchema], val isActive: Boolean)
    extends Operation[(XTypeSchema, Boolean)](s"Edit $schema for graph $graph.") {
    this: XLoggable ⇒
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    lazy val operation = injectOptional[XOperationModifyTypeSchema]
  }
}
