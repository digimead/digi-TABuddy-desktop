/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2013-2014 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.core.ui

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.ui.definition.IWizard
import org.eclipse.jface.wizard.WizardDialog
import org.eclipse.swt.widgets.Shell
import scala.language.implicitConversions

class Wizards extends XLoggable {
  private val lock = new Object

  /** Show wizard by class. */
  def open(clazz: Class[_ <: IWizard], shell: Shell): AnyRef = open(clazz, shell, None)
  /** Show wizard by class. */
  @log
  def open(clazz: Class[_ <: IWizard], shell: Shell, argument: Option[AnyRef]): AnyRef = lock.synchronized {
    log.debug(s"Open wizard ${clazz.getName()}.")
    val instance = clazz.newInstance()
    argument.foreach { argument ⇒
      instance match {
        case wizard: IWizard ⇒
          wizard.init(argument)
        case wizard ⇒
      }
    }
    val wd = new WizardDialog(shell, instance)
    wd.setTitle(instance.getWindowTitle())
    val result = wd.open(): Integer
    instance match {
      case wizard: IWizard ⇒
        wizard.result getOrElse result
      case wizard ⇒
        result
    }
  }
  /** Show wizard by class name. */
  def open(name: String, shell: Shell): AnyRef = open(name, shell, None)
  /** Show wizard by class name. */
  @log
  def open(name: String, shell: Shell, argument: Option[AnyRef]): AnyRef = lock.synchronized {
    Resources.wizards.find(_.getName == name) match {
      case Some(clazz) ⇒
        open(clazz, shell, argument)
      case None ⇒
        log.warn(s"Wizard with name ${name} not found.")
        -1: Integer
    }
  }
}

object Wizards {
  implicit def registry2implementation(w: Wizards.type): Wizards = w.inner

  /** Wizards implementation. */
  def inner(): Wizards = DI.implementation

  /**
   * Dependency injection routines.
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** Wizards implementation */
    lazy val implementation = injectOptional[Wizards] getOrElse new Wizards
  }
}
