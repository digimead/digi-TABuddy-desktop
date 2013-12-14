/**
 * This file is part of the TA Buddy project.
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

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.Messages
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.WritableValue
import org.digimead.tabuddy.desktop.logic.payload.{ Enumeration, PropertyType }
import org.digimead.tabuddy.desktop.ui.support.Validator
import org.digimead.tabuddy.model.element.Element
import org.eclipse.core.databinding.observable.{ ChangeEvent, IChangeListener }
import org.eclipse.jface.databinding.swt.WidgetProperties
import org.eclipse.jface.databinding.viewers.{ IViewerObservableValue, ViewersObservables }
import org.eclipse.jface.viewers.{ CellEditor, ColumnViewer, ComboViewer, ILabelProviderListener, IStructuredSelection, LabelProvider, StructuredSelection, TextCellEditor, ViewerCell, ViewerColumn }
import org.eclipse.swt.SWT
import org.eclipse.swt.events.VerifyEvent
import org.eclipse.swt.widgets.{ Composite, Control, Label, Text }
import org.eclipse.ui.forms.widgets.FormToolkit
import scala.util.control.Breaks.{ break, breakable }

class StringType extends PropertyType[String] {
  /** The property that determines that enumeration is supported */
  val enumerationSupported: Boolean = true
  /** The property type name */
  val id = 'String
  /** The type class */
  val typeClass: Class[String] = classOf[String]

  /** The property that contains an adapter for the given type */
  def adapter(): PropertyType.Adapter[String] =
    new StringType.Adapter

  /**
   * Result of comparing 'value1' with 'value2'.
   * returns `x' where
   * x < 0 iff value1 < value2
   * x == 0 iff value1 == value2
   * x > 0 iff value1 > value2
   */
  def compare(value1: String, value2: String): Int = value1.compareTo(value2)
  /** Create an editor for the given type */
  def createEditor(initial: Option[String], propertyId: Symbol, element: Element): PropertyType.Editor[String] =
    new StringType.Editor(WritableValue(initial.getOrElse("")), propertyId, element)
  /** Returns the new value */
  def createValue: String = Messages.newValue_text
  /** Returns an iterator for the new value generation */
  def createValues: Iterator[String] = new Iterator[String] {
    @volatile private var n = 0
    private val base = Messages.newValue_text
    def hasNext = true
    def next = {
      val result = if (n == 0) base else base + " " + n
      n += 1
      result
    }
  }
  /** Create a viewer for the given type */
  def createViewer(initial: Option[String], propertyId: Symbol, element: Element): PropertyType.Viewer[String] =
    new StringType.Viewer(WritableValue(initial.getOrElse("")), propertyId, element)
  /** Convert value to string */
  def valueToString(value: String): String = value
  /** Convert string to value */
  def valueFromString(value: String): String = value
}

object StringType extends StringType with Loggable {
  class Adapter(implicit val argManifest: Manifest[String]) extends PropertyType.Adapter[String] {
    /** Cell label provider singleton with limited API for proxy use case */
    val cellLabelProvider: PropertyType.CellLabelProviderAdapter[String] = new CellLabelProviderAdapter() {
      def dispose(viewer: ColumnViewer, column: ViewerColumn) = throw new UnsupportedOperationException
    }
    /** Label provider singleton with limited API for proxy use case */
    val labelProvider: PropertyType.LabelProviderAdapter[String] = new LabelProviderAdapter() {
      def addListener(listener: ILabelProviderListener) {}
      def dispose() {}
      def removeListener(listener: ILabelProviderListener) {}
    }

    /** Get a cell editor */
    def createCellEditor(parent: Composite, style: Int): CellEditor = new TextCellEditor(parent, style) {
      override protected def doSetValue(value: AnyRef) = // allow an incorrect value transformation, useful while types are changed
        if (value == null || value.isInstanceOf[String]) super.doSetValue("") else super.doSetValue(value)
    }
    /** Get a LabelProveder*/
    def createLabelProvider(): LabelProvider = new StringTypeLabelProvider()
  }
  /**
   * StringType class that provides an editor widget
   */
  class Editor(val data: WritableValue[String], val propertyId: Symbol, val element: Element)(implicit val argManifest: Manifest[String]) extends PropertyType.Editor[String] {
    protected val pattern = """[\p{Print}]*""".r.pattern
    /** Add the validator */
    def addValidator(control: Control, showOnlyOnFocus: Boolean): Option[Validator] =
      Some(Validator(control, showOnlyOnFocus)(validate))
    /** Get an UI control */
    def createControl(parent: Composite, style: Int, updateDelay: Int): Control =
      prepareControl(new Text(parent, style), updateDelay)
    /** Get an UI control */
    def createControl(toolkit: FormToolkit, parent: Composite, style: Int, updateDelay: Int): Control =
      prepareControl(toolkit.createText(parent, data.value, style), updateDelay)
    /** Get a combo viewer UI control */
    def createCControl(parent: Composite, style: Int, updateDelay: Int): ComboViewer = {
      val viewer = new ComboViewer(parent, style)
      val widgetObservable = ViewersObservables.observeDelayedValue(updateDelay, ViewersObservables.observeSingleSelection(viewer))
      widgetObservable.addChangeListener(new IChangeListener {
        override def handleChange(event: ChangeEvent): Unit = event.getObservable() match {
          case observable: IViewerObservableValue ⇒
            // throw an exception if the value has an unexpected type
            if (data.value != observable.getValue.asInstanceOf[Enumeration.Constant[String]].value)
              data.value = observable.getValue.asInstanceOf[Enumeration.Constant[String]].value
          case observable ⇒ log.fatal("unknown observable '%s' with type '%s'".format(observable, observable.getClass()))
        }
      })
      data.underlying.addChangeListener(new IChangeListener {
        override def handleChange(event: ChangeEvent): Unit = event.getSource match {
          case value: org.eclipse.core.databinding.observable.value.WritableValue ⇒
            value.getValue() match {
              case value: String ⇒
                viewer.getSelection match {
                  case selection: IStructuredSelection ⇒
                    // throw an exception if the value has an unexpected type
                    if (value != selection.getFirstElement.asInstanceOf[Enumeration.Constant[String]].value)
                      breakable {
                        for {
                          i ← 0 until Int.MaxValue
                          element ← Option(viewer.getElementAt(i))
                        } if (element.asInstanceOf[Enumeration.Constant[String]].value == value) {
                          viewer.setSelection(new StructuredSelection(element))
                          break
                        }
                      }
                  case selection ⇒ log.fatal("unknown value '%s' with type '%s'".format(selection, selection.getClass()))
                }
              case other ⇒ log.fatal("unknown value '%s' with type '%s'".format(other, other.getClass()))
            }
          case other ⇒
            log.fatal("unknown event '%s' type '%s'".format(event, event.getClass()))
        }
      })
      viewer
    }
    /** Get a combo viewer UI control */
    def createCControl(toolkit: FormToolkit, parent: Composite, style: Int, updateDelay: Int): ComboViewer = {
      val viewer = createCControl(parent, style, updateDelay)
      toolkit.adapt(viewer.getCombo())
      viewer
    }
    /** Returns true if the data is empty, false otherwise. */
    def isEmpty = data.value == null || data.value.trim.isEmpty
    /** The validator function */
    def validate(validator: Validator, event: VerifyEvent) {
      if (event.text.nonEmpty && event.character != '\0')
        event.doit = pattern.matcher(event.text).matches()
      if (!event.doit)
        validator.withDecoration { validator.showDecorationError(_) }
      else
        validator.withDecoration { _.hide() }
    }
    protected def prepareControl(control: Control, updateDelay: Int): Control = {
      App.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(updateDelay, control), data)
      control
    }
  }
  /**
   * StringType class that provides a viewer widget
   */
  class Viewer(val data: WritableValue[String], val propertyId: Symbol, val element: Element)(implicit val argManifest: Manifest[String]) extends PropertyType.Viewer[String] {
    /** Get an UI control */
    def createControl(parent: Composite, style: Int, updateDelay: Int): Control =
      prepareControl(new Label(parent, style), updateDelay)
    /** Get an UI control */
    def createControl(toolkit: FormToolkit, parent: Composite, style: Int, updateDelay: Int): Control =
      prepareControl(toolkit.createLabel(parent, data.value, style), updateDelay)
    /** Returns true if the data is empty, false otherwise. */
    def isEmpty = data.value == null || data.value.trim.isEmpty

    protected def prepareControl(control: Control, updateDelay: Int): Control = {
      App.bindingContext.bindValue(WidgetProperties.text().observeDelayed(updateDelay, control), data)
      control
    }
  }
  /*
   * Support classes
   */
  class CellLabelProviderAdapter extends PropertyType.CellLabelProviderAdapter[String] {
    /** Update the label for cell. */
    def update(cell: ViewerCell, value: Option[String]) = value match {
      case Some(value) if value != null ⇒ cell.setText(StringType.valueToString(value))
      case _ ⇒ cell.setText("")
    }
  }
  class LabelProviderAdapter extends PropertyType.LabelProviderAdapter[String] {
    /**
     * The <code>LabelProvider</code> implementation of this
     * <code>ILabelProvider</code> method returns the element's
     * <code>toString</code> string.
     */
    override def getText(value: Option[String]): String = value match {
      case Some(value) if value != null ⇒ StringType.valueToString(value)
      case _ ⇒ ""
    }
  }
  class StringTypeLabelProvider extends LabelProvider {
    override def getText(element: AnyRef): String = element match {
      case value: String ⇒
        StringType.valueToString(value)
      case unknown ⇒
        log.fatal("Unknown item " + unknown.getClass())
        unknown.toString()
    }
  }
}
