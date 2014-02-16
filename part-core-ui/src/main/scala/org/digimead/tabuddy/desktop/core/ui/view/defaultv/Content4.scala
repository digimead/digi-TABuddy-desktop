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

import javafx.animation.{ Animation, FadeTransition, ParallelTransition, PathTransition }
import javafx.animation.{ PathTransitionBuilder, RotateTransition }
import javafx.animation.PathTransition.OrientationType
import javafx.beans.value.{ ChangeListener, ObservableValue }
import javafx.embed.swt.FXCanvas
import javafx.event.{ ActionEvent, EventHandler }
import javafx.scene.Scene
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.{ Rectangle, RectangleBuilder }
import javafx.scene.text.{ Font, Text, TextBuilder }
import javafx.scene.transform.Rotate
import javafx.util.{ Builder, Duration }
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Composite

class Content4 {
  /** Get ViewDefault content. */
  def apply(parent: Composite): Composite = {
    val body = new FXCanvas(parent, SWT.NONE)
    val (scene, pathTransition, transition) = createScene()
    body.setScene(scene)
    //scene.getStylesheets().add(null)
    transition.play()
    pathTransition.play()
    body
  }

  def createScene(): (Scene, PathTransition, ParallelTransition) = {
    val pen = new Rectangle(0, 0, 20, 20)

    // this pane this contain clipping
    val clip = new Pane()

    // listener to update clipping area
    val changeListener = new ChangeListener[AnyRef]() {
      override def changed(ov: ObservableValue[_ <: AnyRef], t: AnyRef, t1: AnyRef) {
        val newrect = new Rectangle(pen.getTranslateX(), pen.getTranslateY(), pen.getWidth(), pen.getHeight())
        newrect.setRotate(pen.getRotate())
        clip.getChildren().add(newrect)
      }
    }

    // rect coordinates will be changed during animation, so we will listen to them
    pen.translateXProperty().addListener(changeListener);
    pen.translateYProperty().addListener(changeListener);
    pen.rotateProperty().addListener(changeListener);
    pen.setFill(Color.ORANGERED)

    val text = <>[TextBuilder[_], Text](TextBuilder.create()) { b ⇒
      b.text("TA Buddy: Desktop")
      b.font(new Font(50))
      b.clip(clip)
      b.x(65)
      b.y(100)
      b.build()
    }
    text.setFill(Color.RED)

    val pathTransition = PathTransitionBuilder.create()
      .duration(Duration.seconds(15))
      .path(text)
      .node(pen)
      .orientation(OrientationType.ORTHOGONAL_TO_TANGENT)
      .build()

    val rect = <>[RectangleBuilder[_], Rectangle](RectangleBuilder.create()) { b ⇒
      b.width(100)
      b.height(100)
      b.x(350)
      b.y(250)
      b.fill(Color.BLUE)
      b.build()
    }
    val rotationY = new RotateTransition()
    rotationY.setAxis(Rotate.Y_AXIS)
    rotationY.setDuration(Duration.seconds(5))
    rotationY.setByAngle(360)
    rotationY.setNode(rect)
    rotationY.setAutoReverse(true)
    rotationY.setCycleCount(Animation.INDEFINITE)

    val rotationX = new RotateTransition()
    rotationX.setAxis(Rotate.X_AXIS)
    rotationX.setDuration(Duration.seconds(5))
    rotationX.setByAngle(360)
    rotationX.setNode(rect)
    rotationX.setAutoReverse(true)
    rotationX.setCycleCount(Animation.INDEFINITE)

    val fade = new FadeTransition();
    fade.setDuration(Duration.seconds(5));
    fade.setToValue(0.2);
    fade.setNode(rect);
    fade.setAutoReverse(true);
    fade.setCycleCount(Animation.INDEFINITE);

    // once we done we don't want to store thousands of rectangles used to clip
    pathTransition.setOnFinished(new EventHandler[ActionEvent]() {
      override def handle(t: ActionEvent) {
        text.setClip(null)
        clip.getChildren().clear()
        System.gc()
      }
    })

    val transition = new ParallelTransition(rect,
      rotationX, rotationY, fade)
    transition.setAutoReverse(true)
    val root = new Pane()
    root.getChildren().addAll(text, pen, rect)
    (new Scene(root), pathTransition, transition)
  }
  /**
   * little wrapper that negate effect of Java <B extends T<B>>
   * and prevents crush of Scala compiler
   */
  def <>[T, S](b: Builder[_])(f: T ⇒ S) = f(b.asInstanceOf[T])
}
