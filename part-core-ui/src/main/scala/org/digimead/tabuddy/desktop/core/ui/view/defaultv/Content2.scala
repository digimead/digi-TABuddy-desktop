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

package org.digimead.tabuddy.desktop.core.ui.view.defaultv

import javafx.animation.{ PathTransition, PathTransitionBuilder }
import javafx.embed.swt.FXCanvas
import javafx.event.EventHandler
import javafx.scene.{ Group, Scene }
import javafx.scene.image.{ Image, ImageView }
import javafx.scene.input.MouseEvent
import javafx.scene.paint.Color
import javafx.scene.shape.{ LineTo, MoveTo, Path }
import javafx.util.Duration
import org.digimead.tabuddy.desktop.core.support.App
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Composite

class Content2 {
  /** Get ViewDefault content. */
  def apply(parent: Composite): Composite = {
    val body = new FXCanvas(parent, SWT.NONE)
    val scene = createScene()
    body.setScene(scene)
    body
  }

  lazy val image = {
    val logo = App.bundle(getClass).getEntry("/ta.png")
    new Image(logo.openStream())
  }

  def createScene(): Scene = {
    val root = new Group()
    val scene = new Scene(root, 400, 300, Color.WHITE)

    val imageView = new ImageView()
    imageView.setImage(image)

    val path = new Path()
    path.setStrokeWidth(1);
    path.setStroke(Color.BLACK)

    //Mouse button pressed - clear path and start from the current X, Y.
    scene.onMousePressedProperty().set(new EventHandler[MouseEvent]() {
      override def handle(event: MouseEvent) {
        path.getElements().clear()
        path.getElements().add(new MoveTo(event.getX(), event.getY()))
      }
    })

    //Mouse dragged - add current point.
    scene.onMouseDraggedProperty().set(new EventHandler[MouseEvent]() {
      override def handle(event: MouseEvent) {
        path.getElements().add(new LineTo(event.getX(), event.getY()));
      }
    })

    //Mouse button released,  finish path.
    scene.onMouseReleasedProperty().set(new EventHandler[MouseEvent]() {
      override def handle(event: MouseEvent) {
        val pathTransition = PathTransitionBuilder.create()
          .node(imageView)
          .path(path)
          .duration(Duration.millis(5000))
          .orientation(PathTransition.OrientationType.ORTHOGONAL_TO_TANGENT)
          .cycleCount(1)
          .build();

        pathTransition.play()
      }
    })

    root.getChildren().add(imageView);
    root.getChildren().add(path)
    scene
  }
}
