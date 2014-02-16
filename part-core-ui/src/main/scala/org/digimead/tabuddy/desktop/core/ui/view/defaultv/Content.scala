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

import javafx.animation.{ FadeTransitionBuilder, PathTransition }
import javafx.animation.PathTransition.OrientationType
import javafx.animation.PathTransitionBuilder
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.value.{ ChangeListener, ObservableValue }
import javafx.embed.swt.FXCanvas
import javafx.event.{ ActionEvent, EventHandler }
import javafx.geometry.VPos
import javafx.scene.{ Group, Scene }
import javafx.scene.effect.DropShadow
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.scene.text.{ Font, FontPosture, FontWeight, Text, TextAlignment, TextBuilder }
import javafx.util.Duration
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.Report
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.core.operation.OperationInfo
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.{ ResourceManager, UI }
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.{ GridData, GridLayout }
import org.eclipse.swt.widgets.{ Composite, Event, Listener }

/**
 * Default view content.
 */
class Content(parent: Composite, style: Int = SWT.NONE) extends ContentSkel(parent, style) with Loggable {
  val scaleK = new SimpleDoubleProperty()

  def initialize() {
    val titleComposite = new FXCanvas(getCompositeTitle, SWT.NONE)
    titleComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1))
    titleComposite.setBackground(ResourceManager.getColor(SWT.COLOR_WHITE))
    getCompositeTitle.setBackground(ResourceManager.getColor(SWT.COLOR_WHITE))
    val titleTransition = createTitle(titleComposite)
    val aboutComposite = new FXCanvas(getCompositeAbout, SWT.NONE)
    aboutComposite.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1))
    aboutComposite.setBackground(ResourceManager.getColor(SWT.COLOR_WHITE))
    getCompositeTitle.setBackground(ResourceManager.getColor(SWT.COLOR_WHITE))
    createAbout(aboutComposite)
    titleTransition.play()
  }

  /** Create title composite. */
  protected def createTitle(parent: FXCanvas): PathTransition = {
    val pen = new Rectangle(0, 0, 20, 20)

    // This pane contain clipping.
    val clip = new Pane()

    // Listener to update clipping area.
    val changeListener = new ChangeListener[AnyRef]() {
      override def changed(ov: ObservableValue[_ <: AnyRef], t: AnyRef, t1: AnyRef) {
        val newrect = new Rectangle(pen.getTranslateX(), pen.getTranslateY(), pen.getWidth(), pen.getHeight())
        newrect.setRotate(pen.getRotate())
        clip.getChildren().add(newrect)
      }
    }

    // Rectangle coordinates will be changed during animation, so we will listen to them.
    pen.translateXProperty().addListener(changeListener);
    pen.translateYProperty().addListener(changeListener);
    pen.rotateProperty().addListener(changeListener);
    pen.setFill(Color.ORANGERED)

    val ds = new DropShadow()
    ds.setOffsetY(3.0f)
    ds.setColor(Color.color(0.4f, 0.4f, 0.4f))

    val text = UI.<>[TextBuilder[_], Text](TextBuilder.create()) { b ⇒
      b.text("TA Buddy: Desktop")
      b.font(Font.font(null, FontWeight.BOLD, 100))
      b.effect(ds)
      b.cache(true)
      b.clip(clip)
      b.textOrigin(VPos.TOP)
      b.fill(Color.RED)
      b.build()
    }
    text.snapshot(null, null)
    val textBounds = text.getLayoutBounds()

    val root = new Pane()
    root.getChildren().addAll(text, pen)

    // Wrap the resizable content in a non-resizable container (Group).
    val group = new Group(root)
    val scene = new Scene(group)
    parent.setScene(scene)

    group.layoutXProperty().bind(scene.widthProperty().subtract(textBounds.getWidth()).divide(2))
    group.layoutYProperty().bind(scene.heightProperty().subtract(textBounds.getHeight()).divide(2))
    group.scaleXProperty().bind(scaleK)
    group.scaleYProperty().bind(scaleK)
    scene.widthProperty().addListener(new ChangeListener[AnyRef] {
      def changed(arg0: ObservableValue[_ <: AnyRef], arg1: AnyRef, arg2: AnyRef) {
        val k = scene.widthProperty().get() / textBounds.getWidth()
        val height = math.round((textBounds.getHeight() * k * 1.1).toFloat)
        if (height > 1) App.exec {
          parent.getLayoutData().asInstanceOf[GridData].minimumHeight = height
          parent.getLayoutData().asInstanceOf[GridData].heightHint = height
          parent.getParent().getParent().layout()
        }
        scaleK.set(k)
      }
    })
    parent.getParent().addListener(SWT.Resize, new Listener() {
      def handleEvent(e: Event) = e.widget match {
        case composite: Composite ⇒
          composite.getLayout() match {
            case gridLayout: GridLayout ⇒
              val width = composite.getSize.x
              val margin = math.min(math.max(10, width / 8), 300)
              gridLayout.marginLeft = margin
              gridLayout.marginRight = margin
            case _ ⇒
          }
        case _ ⇒
      }
    })

    val pathTransition = PathTransitionBuilder.create()
      .duration(Duration.seconds(15))
      .path(text)
      .node(pen)
      .orientation(OrientationType.ORTHOGONAL_TO_TANGENT)
      .build()

    // once we done we don't want to store thousands of rectangles used to clip
    pathTransition.setOnFinished(new EventHandler[ActionEvent]() {
      override def handle(t: ActionEvent) {
        text.setClip(null)
        clip.getChildren().clear()
        val penTransition = FadeTransitionBuilder.create()
          .duration(Duration.seconds(1))
          .toValue(0.2)
          .node(pen)
          .build()
        penTransition.play()
        penTransition.setOnFinished(new EventHandler[ActionEvent]() {
          override def handle(t: ActionEvent) {
            root.getChildren().remove(pen)
            System.gc()
          }
        })
      }
    })

    pathTransition
  }
  /** Create about composite. */
  protected def createAbout(parent: FXCanvas) {
    val text = UI.<>[TextBuilder[_], Text](TextBuilder.create()) { b ⇒
      b.font(Font.font(null, FontPosture.ITALIC, 14))
      b.textAlignment(TextAlignment.CENTER)
      b.textOrigin(VPos.TOP)
      b.fill(Color.DARKGRAY)
      b.build()
    }
    text.textProperty().addListener(new ChangeListener[AnyRef]() {
      override def changed(ov: ObservableValue[_ <: AnyRef], t: AnyRef, t1: AnyRef) {
        val bounds = text.getLayoutBounds()
        val height = math.round(bounds.getHeight() + 0.5).toInt
        val width = math.round(bounds.getWidth() + 0.5).toInt
        if (height > 1 && width > 1) App.exec {
          parent.getLayoutData().asInstanceOf[GridData].minimumHeight = height
          parent.getLayoutData().asInstanceOf[GridData].heightHint = height
          parent.getLayoutData().asInstanceOf[GridData].minimumWidth = width
          parent.getLayoutData().asInstanceOf[GridData].widthHint = width
          parent.getParent().getParent().layout()
        }
      }
    })
    text.textProperty().set("Copyright © 2014 Alexey B. Aksenov/Ezh. All rights reserved.")
    OperationInfo().foreach { operation ⇒
      operation.getExecuteJob() match {
        case Some(job) ⇒
          job.setPriority(Job.LONG)
          job.onComplete {
            case Operation.Result.OK(Some(info), message) ⇒
              info.component.find(App.bundle(App.getClass()).getSymbolicName() == _.bundleSymbolicName).map { c ⇒
                val info = s"Version: ${c.version}. Build ${Report.dateString(c.build)}\n" +
                  "Copyright © 2014 Alexey B. Aksenov/Ezh. All rights reserved."
                text.textProperty().set(info)
              }
            case Operation.Result.Cancel(message) ⇒
              log.warn(s"OperationInfo canceled, reason: ${message}.")
            case other ⇒
              throw new RuntimeException(s"Unable to complete OperationInfo: ${other}.")
          }.schedule()
        case None ⇒
          throw new RuntimeException(s"Unable to create job for ${operation}.")
      }
    }
    val root = new Pane()
    root.getChildren().addAll(text)
    val group = new Group(root)
    val scene = new Scene(group)
    parent.setScene(scene)
  }
}
