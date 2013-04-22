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

package org.digimead.tabuddy.desktop.payload

import java.util.UUID
import org.digimead.digi.lib.DependencyInjection
import org.digimead.lib.test.TestHelperLogging
import org.digimead.tabuddy.model.Model.model2implementation
import org.digimead.tabuddy.model.element.Axis.intToAxis
import org.digimead.tabuddy.model.element.Coordinate
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.predef.Note
import org.digimead.tabuddy.model.predef.Task
import org.scalatest.fixture.FunSpec
import org.scalatest.matchers.ShouldMatchers
import com.escalatesoft.subcut.inject.NewBindingModule
import org.digimead.tabuddy.desktop.payload.DSL._
import org.digimead.tabuddy.model.Model

class ElementTemplateSpec_j1 extends FunSpec with ShouldMatchers with TestHelperLogging {
  type FixtureParam = Map[String, Any]

  override def withFixture(test: OneArgTest) {
    DependencyInjection.get.foreach(_ => DependencyInjection.clear)
    DependencyInjection.set(defaultConfig(test.configMap) ~ org.digimead.tabuddy.desktop.default)
    withLogging(test.configMap) {
      // initialize the ElementTemplate before test
      ElementTemplate.onModelInitialization(Model.inner, Model.inner, Model.eModified)
      test(test.configMap)
    }
  }

  def resetConfig(newConfig: NewBindingModule = new NewBindingModule(module => {})) = DependencyInjection.reset(newConfig ~ DependencyInjection())

  describe("An ElementTemplate") {
    it("should have proper equality") {
      config =>
        ElementTemplate.predefined should not be ('empty)
        val note = ElementTemplate.predefined.find(_.id == 'Note).get
        val task = ElementTemplate.predefined.find(_.id == 'Task).get
        note should not be (task)
        note should be(note)
        note.element.eGet[String](note.getFieldIDName).get.get should be("Predefined note element")
        note.name should be("Predefined note element")
        assert(note.element.eGet[java.lang.Boolean](note.getFieldIDAvailability).get.get === true)
        note.availability should be(true)
        note.id.name should be(note.element.eId.name)
    }
    it("should have proper getters and setters") {
      config =>
        val note = ElementTemplate.predefined.find(_.id == 'Note).get
        note.properties should have size (1)
        note.properties should contain key (TemplatePropertyGroup.default)
        note.properties(TemplatePropertyGroup.default) should have size (1)
        note.properties(TemplatePropertyGroup.default).head.id.name should be("name")
        //note.properties = note.properties
        note.properties should have size (1)
        note.properties should contain key (TemplatePropertyGroup.default)
        note.properties(TemplatePropertyGroup.default) should have size (1)
        note.properties(TemplatePropertyGroup.default).head.id.name should be("name")
        val task = ElementTemplate.predefined.find(_.id == 'Task).get
    }
    it("should have proper copy constructor") {
      config =>
        val note = ElementTemplate.predefined.find(_.id == 'Note).get
        val note1 = note.copy(element = note.element.eCopy)
        note.element.eq(note1.element) should be(false)
        note.availability should be(note1.availability)
        note.name should be(note1.name)
        note.id.name should be(note1.id.name)
        note.properties should be(note1.properties)
        assert(note === note1)
        val note2 = note1.copy(name = "123")
        note2.element.eGet[String](note.getFieldIDName).get.get should be("123")
        note1.element.eGet[String](note.getFieldIDName).get.get should be("123")
        note1.name should be(note.name)
        note2.name should be("123")
    }
  }
}
