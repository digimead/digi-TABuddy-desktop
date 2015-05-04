/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2015 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.logic.payload.template

import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.support.WritableValue
import org.digimead.tabuddy.desktop.logic.payload.PropertyType
import org.digimead.tabuddy.model.element.Element
import org.eclipse.jface.viewers.{ CellEditor, ColumnViewer, ILabelProviderListener, LabelProvider, TextCellEditor, ViewerCell, ViewerColumn }
import org.eclipse.swt.widgets.Composite

class IntegerType extends NumberType[java.lang.Integer] {
  /** The property type name */
  val id = 'Integer
  /** The type class */
  val typeClass = classOf[java.lang.Integer]

  /** The property that contains an adapter for the given type */
  def adapter() = new IntegerType.Adapter
  /**
   * Result of comparing 'value1' with 'value2'.
   * returns `x' where
   * x < 0 iff value1 < value2
   * x == 0 iff value1 == value2
   * x > 0 iff value1 > value2
   */
  def compare(value1: Integer, value2: Integer): Int = value1.compareTo(value2)
  /** Create an editor for the given type */
  def createEditor(initial: Option[java.lang.Integer], propertyId: Symbol, element: Element): PropertyType.Editor[java.lang.Integer] =
    new IntegerType.Editor(WritableValue(initial.getOrElse(0: java.lang.Integer)), propertyId, element)
  /** Returns the new value */
  def createValue = 0
  /** Returns an iterator for the new value generation */
  def createValues = new Iterator[java.lang.Integer] {
    @volatile private var n = 0
    private val base = 0
    def hasNext = true
    def next = {
      n += 1
      base + n
    }
  }
  /** Create a viewer for the given type */
  def createViewer(initial: Option[java.lang.Integer], propertyId: Symbol, element: Element): PropertyType.Viewer[java.lang.Integer] =
    new IntegerType.Viewer(WritableValue(initial.getOrElse(0: java.lang.Integer)), propertyId, element)
  /** Convert value to string */
  def valueToString(value: Integer): String = String.valueOf(value)
  /** Convert string to value */
  def valueFromString(value: String): Integer = Integer.getInteger(value)
}

object IntegerType extends IntegerType with XLoggable {
  class Adapter(implicit val argManifest: Manifest[java.lang.Integer]) extends PropertyType.Adapter[java.lang.Integer] {
    /** Cell label provider singleton with limited API for proxy use case. */
    val cellLabelProvider: PropertyType.CellLabelProviderAdapter[java.lang.Integer] = new CellLabelProviderAdapter() {
      def dispose(viewer: ColumnViewer, column: ViewerColumn) = throw new UnsupportedOperationException
    }
    /** Label provider singleton with limited API for proxy use case. */
    val labelProvider: PropertyType.LabelProviderAdapter[java.lang.Integer] = new LabelProviderAdapter() {
      def addListener(listener: ILabelProviderListener) {}
      def dispose() {}
      def removeListener(listener: ILabelProviderListener) {}
    }

    /** Get a cell editor. */
    def createCellEditor(parent: Composite, style: Int): CellEditor = new TextCellEditor(parent, style) {
      override protected def doSetValue(value: AnyRef) = // allow an incorrect value transformation, useful while types are changed
        if (value == null || value.isInstanceOf[String]) super.doSetValue("") else super.doSetValue(value)
    }
    /** Get a LabelProvider. */
    def createLabelProvider(): LabelProvider = new IntegerTypeLabelProvider()
  }
  /**
   * IntegerType class that provides an editor widget.
   */
  class Editor(val data: WritableValue[java.lang.Integer], val propertyId: Symbol,
    val element: Element)(implicit val argManifest: Manifest[java.lang.Integer]) extends NumberType.Editor[java.lang.Integer] {
    val NumberType = IntegerType
  }
  /**
   * IntegerType class that provides a viewer widget.
   */
  class Viewer(val data: WritableValue[java.lang.Integer], val propertyId: Symbol,
    val element: Element)(implicit val argManifest: Manifest[java.lang.Integer]) extends NumberType.Viewer[java.lang.Integer] {
    val NumberType = IntegerType
  }
  /*
   * Support classes
   */
  class CellLabelProviderAdapter extends PropertyType.CellLabelProviderAdapter[java.lang.Integer] {
    /** Update the label for cell. */
    def update(cell: ViewerCell, value: Option[java.lang.Integer]) = value match {
      case Some(value) if value != null ⇒ cell.setText(IntegerType.valueToString(value))
      case _ ⇒ cell.setText("")
    }
  }
  class LabelProviderAdapter extends PropertyType.LabelProviderAdapter[java.lang.Integer] {
    /**
     * The <code>LabelProvider</code> implementation of this
     * <code>ILabelProvider</code> method returns the element's
     * <code>toString</code> string.
     */
    override def getText(value: Option[java.lang.Integer]): String = value match {
      case Some(value) if value != null ⇒ IntegerType.valueToString(value)
      case _ ⇒ ""
    }
  }
  class IntegerTypeLabelProvider extends LabelProvider {
    override def getText(element: AnyRef): String = element match {
      case value: Integer ⇒
        IntegerType.valueToString(value)
      case unknown ⇒
        log.fatal("Unknown item " + unknown.getClass())
        unknown.toString()
    }
  }
}
