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
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.support.Validator
import org.digimead.tabuddy.desktop.logic.payload.{ Enumeration, PropertyType }
import org.eclipse.core.databinding.observable.{ ChangeEvent, IChangeListener }
import org.eclipse.jface.databinding.swt.WidgetProperties
import org.eclipse.jface.databinding.viewers.{ IViewerObservableValue, ViewersObservables }
import org.eclipse.jface.viewers.{ ComboViewer, IStructuredSelection, StructuredSelection }
import org.eclipse.swt.SWT
import org.eclipse.swt.events.VerifyEvent
import org.eclipse.swt.widgets.{ Composite, Control, Label, Text }
import org.eclipse.ui.forms.widgets.FormToolkit
import scala.util.control.Breaks.{ break, breakable }

trait NumberType[T <: java.lang.Number] extends PropertyType[T] {
  /** The property that determines that enumeration is supported */
  val enumerationSupported: Boolean = true
}

object NumberType {
  /**
   * Number type class that provides an editor widget.
   */
  trait Editor[T <: java.lang.Number] extends PropertyType.Editor[T] with XLoggable {
    /** Number type object. */
    protected val NumberType: NumberType[T]
    /** Validation pattern for validate function. */
    protected val pattern = """[\p{Print}]*""".r.pattern

    /** Add the validator */
    def addValidator(control: Control, showOnlyOnFocus: Boolean): Option[Validator[VerifyEvent]] =
      Some(Validator(control, showOnlyOnFocus)(validate))
    /** Get an UI control */
    def createControl(parent: Composite, style: Int, updateDelay: Int): Control =
      prepareControl(new Text(parent, style), updateDelay)
    /** Get an UI control */
    def createControl(toolkit: FormToolkit, parent: Composite, style: Int, updateDelay: Int): Control =
      prepareControl(toolkit.createText(parent, NumberType.valueToString(data.value), style), updateDelay)
    /** Get a combo viewer UI control */
    def createCControl(parent: Composite, style: Int, updateDelay: Int): ComboViewer = {
      val viewer = new ComboViewer(parent, style)
      val widgetObservable = ViewersObservables.observeDelayedValue(updateDelay, ViewersObservables.observeSingleSelection(viewer))
      widgetObservable.addChangeListener(new IChangeListener {
        override def handleChange(event: ChangeEvent): Unit = event.getObservable() match {
          case observable: IViewerObservableValue ⇒
            // throw an exception if the value has an unexpected type
            if (data.value != observable.getValue.asInstanceOf[Enumeration.Constant[T]].value)
              data.value = observable.getValue.asInstanceOf[Enumeration.Constant[T]].value
          case observable ⇒ log.fatal("unknown observable '%s' with type '%s'".format(observable, observable.getClass()))
        }
      })
      data.underlying.addChangeListener(new IChangeListener {
        override def handleChange(event: ChangeEvent): Unit = event.getSource match {
          case value: org.eclipse.core.databinding.observable.value.WritableValue ⇒
            value.getValue() match {
              case value: Integer ⇒
                viewer.getSelection match {
                  case selection: IStructuredSelection ⇒
                    // throw an exception if the value has an unexpected type
                    if (value != selection.getFirstElement.asInstanceOf[Enumeration.Constant[java.lang.Integer]].value)
                      breakable {
                        for {
                          i ← 0 until Int.MaxValue
                          element ← Option(viewer.getElementAt(i))
                        } if (element.asInstanceOf[Enumeration.Constant[java.lang.Integer]].value == value) {
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
    def isEmpty = data.value == null
    /** The validator function */
    def validate(validator: Validator[VerifyEvent], event: VerifyEvent) {
      if (event.text.nonEmpty && event.character != '\u0000')
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
   * Number type class that provides a viewer widget.
   */
  trait Viewer[T <: java.lang.Number] extends PropertyType.Viewer[T] with XLoggable  {
    /** Number type object. */
    protected val NumberType: NumberType[T]

    /** Get an UI control */
    def createControl(parent: Composite, style: Int, updateDelay: Int): Control =
      prepareControl(new Label(parent, style), updateDelay)
    /** Get an UI control */
    def createControl(toolkit: FormToolkit, parent: Composite, style: Int, updateDelay: Int): Control =
      prepareControl(toolkit.createLabel(parent, NumberType.valueToString(data.value), style), updateDelay)
    /** Returns true if the data is empty, false otherwise. */
    def isEmpty = data.value == null

    protected def prepareControl(control: Control, updateDelay: Int): Control = {
      App.bindingContext.bindValue(WidgetProperties.text().observeDelayed(updateDelay, control), data)
      control
    }
  }
}
