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

package org.digimead.tabuddy.desktop.core.ui.block.transform

import java.util.concurrent.ExecutionException
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.block.builder.ViewContentBuilder
import org.digimead.tabuddy.desktop.core.ui.block.{ Configuration, StackSupervisor }
import org.digimead.tabuddy.desktop.core.ui.definition.widget.{ SCompositeTab, VComposite }
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.ScrolledComposite
import org.eclipse.swt.widgets.Control
import scala.language.implicitConversions

/** Unwrap view from tab stack. */
class TransformTabToView extends Loggable {
  /**
   * Unwrap view from tab stack.
   *
   * @return Right(new VComposite) or if fail Left(Option(old VComposite))
   */
  def apply(ss: StackSupervisor, tab: SCompositeTab): Either[Option[VComposite], VComposite] = {
    ss.wComposite map { wComposite ⇒
      // Throw runtime error if something wrong.
      val view = try App.execNGet {
        val children = if (tab.isDisposed()) Array[Control]() else tab.getChildren()
        if (children.size > 1) {
          val Some(scrolledComposite) = tab.getChildren().lastOption
          scrolledComposite.asInstanceOf[ScrolledComposite].getChildren() match {
            case Array(view: VComposite) ⇒
              Some(view.asInstanceOf[VComposite])
            case Array() ⇒
              // dispose vComposite
              log.debug("ScrolledComposite is empty. Dispose container.")
              scrolledComposite.getParent().dispose()
              None
            case unexpected ⇒
              log.fatal("Unexpected structure: " + unexpected.mkString(", "))
              None
          }
        } else {
          // last view was disposed
          // there is only tab with ToolBar
          tab.dispose()
          None
        }
      } catch {
        case e: ExecutionException if e.getCause() != null ⇒ throw e.getCause()
      }
      val result: Option[Either[Option[VComposite], VComposite]] = view.map { view ⇒
        ss.buildConfiguration().asMap.get(view.id) match {
          case Some((parentId, viewConfiguration: Configuration.CView)) ⇒
            log.debug(s"Attach ${viewConfiguration} as top level element.")
            val result = ViewContentBuilder.container(viewConfiguration, wComposite, ss.parentContext, ss.context, Some(view))
            result.foreach { result ⇒
              ss.context.stop(view.ref)
              App.execNGet { tab.dispose() }
              // Resize VComposite.
              // VComposite -> WComposite . layout()
              App.execWithTimer(100) {
                if (!result.isDisposed()) result.getParent().layout(true, true)
              }
            }
            result.toRight(Some(view))
          case Some((parent, configuration)) ⇒
            throw new IllegalStateException(s"Unexpected configuration ${configuration}.")
          case None ⇒
            log.debug(s"${view} is deleted.")
            ss.context.stop(view.ref)
            App.execNGet {
              view.dispose()
              tab.dispose()
            }
            Left[Option[VComposite], VComposite](Some(view))
        }
      }
      result getOrElse Left(view)
    } getOrElse Left(None)
  }
}

object TransformTabToView {
  implicit def transform2implementation(t: TransformTabToView.type): TransformTabToView = inner

  def inner(): TransformTabToView = DI.implementation

  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** TransformTabToView implementation */
    lazy val implementation = injectOptional[TransformTabToView] getOrElse new TransformTabToView
  }
}
