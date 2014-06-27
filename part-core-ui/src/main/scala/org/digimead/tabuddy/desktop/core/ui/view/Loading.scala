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

package org.digimead.tabuddy.desktop.core.ui.view

import javafx.animation.{ Animation, FillTransition, KeyFrame, KeyValue, Timeline }
import javafx.beans.binding.DoubleExpression
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.value.{ ChangeListener, ObservableValue }
import javafx.geometry.VPos
import javafx.scene.{ Group, Scene }
import javafx.scene.effect.DropShadow
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.{ ArcTo, ClosePath, FillRule, LineTo, MoveTo, Path }
import javafx.scene.text.{ Font, FontWeight, Text, TextAlignment }
import javafx.scene.transform.{ Rotate, Scale }
import javafx.util.Duration
import org.digimead.digi.lib.jfx4swt.FXCanvas
import org.digimead.tabuddy.desktop.core.ui.{ ResourceManager, UI }
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.widgets.{ Composite, Control, Event, Listener }

/**
 * Loading content.
 */
class Loading(parent: Composite, style: Int) extends LoadingSkel(parent, style) {
  /** Loading composite. */
  val loadingComposite = new FXCanvas(getContainer(), SWT.NONE)

  def initializeJFX() {
    val (loadingScene, anim1, anim2, anim3, anim4, anim5) = createLoading(loadingComposite)
    loadingComposite.addDisposeListener { stage ⇒
      anim1.stop()
      anim2.stop()
      anim3.stop()
      anim4.stop()
      anim5.stop()
      loadingScene.rootProperty().set(new Group)
    }
    loadingComposite.setScene(loadingScene, _ ⇒ {
      anim1.play()
      anim2.play()
      anim3.play()
      anim4.play()
      anim5.play()
    })
  }
  def initializeSWT() {
    loadingComposite.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true, 1, 1))
    loadingComposite.setBackground(ResourceManager.getColor(SWT.COLOR_WHITE))
    getContainer().addListener(SWT.Resize, new Listener() {
      def handleEvent(e: Event) = e.widget match {
        case composite: Composite ⇒
          composite.getChildren().headOption.foreach {
            case fxCanvas: FXCanvas ⇒
              fxCanvas.getLayoutData() match {
                case loadingData: GridData ⇒
                  val container = composite.getSize()
                  val size = math.min(container.x, container.y)
                  loadingData.minimumHeight = size
                  loadingData.heightHint = size
                  loadingData.minimumWidth = size
                  loadingData.widthHint = size
                  getParent().layout(Array[Control](composite.getChildren().head), SWT.DEFER)
              }
          }
      }
    })
  }

  /** Create loading composite. */
  protected def createLoading(parent: FXCanvas): (Scene, Timeline, Timeline, Timeline, Timeline, Timeline) = {
    val radiusProperty = new SimpleDoubleProperty(50)

    val ds = new DropShadow()
    ds.setOffsetY(3.0f)
    ds.setColor(Color.color(0.4f, 0.4f, 0.4f))

    val text = new Text("Loading...")
    text.setFill(Color.LIGHTGRAY)
    text.setFont(Font.font(null, FontWeight.BOLD, 100))
    text.setTextAlignment(TextAlignment.CENTER)
    text.setEffect(ds)
    text.setTextOrigin(VPos.TOP)
    val tb = text.getBoundsInLocal()

    val pathX = new SimpleDoubleProperty()
    val pathY = new SimpleDoubleProperty()
    val pathWidth = new SimpleDoubleProperty(10)
    val path1 = {
      val angle = math.Pi * 1.9
      val path = drawSemiRing(pathX, pathY, radiusProperty.subtract(10), pathWidth, angle)
      path.setFill(Color.LIGHTGRAY)
      path.setStroke(Color.DARKGRAY)
      path.setFillRule(FillRule.EVEN_ODD)
      path.setStrokeWidth(1)
      path.effectProperty().setValue(ds)
      path
    }

    val path2 = {
      val angle = 1
      val path = drawSemiRing(pathX, pathY, radiusProperty.subtract(28), pathWidth, angle)
      path.setFill(Color.LIGHTGRAY)
      path.setStroke(Color.DARKGRAY)
      path.setFillRule(FillRule.EVEN_ODD)
      path.setStrokeWidth(1)
      path.effectProperty().setValue(ds)
      path
    }

    val path3 = {
      val angle = 2
      val path = drawSemiRing(pathX, pathY, radiusProperty.subtract(46), pathWidth, angle)
      path.setFill(Color.LIGHTGRAY)
      path.setStroke(Color.DARKGRAY)
      path.setFillRule(FillRule.EVEN_ODD)
      path.setStrokeWidth(1)
      path.effectProperty().setValue(ds)
      path
    }

    val path4 = {
      val angle = 0.5
      val path = drawSemiRing(pathX, pathY, radiusProperty.subtract(64), pathWidth, angle)
      path.setFill(Color.LIGHTGRAY)
      path.setStroke(Color.DARKGRAY)
      path.setFillRule(FillRule.EVEN_ODD)
      path.setStrokeWidth(1)
      path.effectProperty().setValue(ds)
      path
    }

    val path5 = {
      val angle = 0.2
      val path = drawSemiRing(pathX, pathY, radiusProperty.subtract(80), pathWidth, angle)
      path.setFill(Color.LIGHTGRAY)
      path.setStroke(Color.DARKGRAY)
      path.setFillRule(FillRule.EVEN_ODD)
      path.setStrokeWidth(1)
      path.effectProperty().setValue(ds)
      path
    }

    val content = new Pane()
    content.getChildren().addAll(text, path1, path2, path3, path4, path5)
    val scene = new Scene(content)

    val scaleTransform = new Scale()
    text.getTransforms().addAll(scaleTransform)
    radiusProperty.addListener(new ChangeListener[Number]() {
      override def changed(observable: ObservableValue[_ <: Number], oldRadius: Number, radius: Number) {
        val k = radius.doubleValue() * 0.7 / tb.getWidth()
        val width = tb.getWidth() * k
        val height = tb.getHeight() * k
        scaleTransform.setX(k)
        scaleTransform.setY(k)
        text.setLayoutX((radiusProperty.get() * 2 - width) / 2)
        text.setLayoutY((radiusProperty.get() * 2 - height) / 2)
      }
    })

    radiusProperty.bind(scene.widthProperty().divide(2))
    pathX.bind(radiusProperty)
    pathY.bind(radiusProperty)

    val rotationTransform1 = new Rotate(0)
    rotationTransform1.pivotXProperty().bind(radiusProperty)
    rotationTransform1.pivotYProperty().bind(radiusProperty)
    path1.getTransforms().add(rotationTransform1)
    val rotationTransform2 = new Rotate(360)
    rotationTransform2.pivotXProperty().bind(radiusProperty)
    rotationTransform2.pivotYProperty().bind(radiusProperty)
    path2.getTransforms().add(rotationTransform2)
    val rotationTransform3 = new Rotate(0)
    rotationTransform3.pivotXProperty().bind(radiusProperty)
    rotationTransform3.pivotYProperty().bind(radiusProperty)
    path3.getTransforms().add(rotationTransform3)
    val rotationTransform4 = new Rotate(360)
    rotationTransform4.pivotXProperty().bind(radiusProperty)
    rotationTransform4.pivotYProperty().bind(radiusProperty)
    path4.getTransforms().add(rotationTransform4)
    val rotationTransform5 = new Rotate(0)
    rotationTransform5.pivotXProperty().bind(radiusProperty)
    rotationTransform5.pivotYProperty().bind(radiusProperty)
    path5.getTransforms().add(rotationTransform5)

    val rotationAnimation1 = new Timeline()
    rotationAnimation1.getKeyFrames().add(new KeyFrame(Duration.seconds(20),
      new KeyValue(rotationTransform1.angleProperty(), 360: java.lang.Double)))
    rotationAnimation1.setCycleCount(Animation.INDEFINITE)

    val rotationAnimation2 = new Timeline()
    rotationAnimation2.getKeyFrames().add(new KeyFrame(Duration.seconds(18),
      new KeyValue(rotationTransform2.angleProperty(), 0: java.lang.Double)))
    rotationAnimation2.setCycleCount(Animation.INDEFINITE)

    val rotationAnimation3 = new Timeline()
    rotationAnimation3.getKeyFrames().add(new KeyFrame(Duration.seconds(16),
      new KeyValue(rotationTransform3.angleProperty(), 360: java.lang.Double)))
    rotationAnimation3.setCycleCount(Animation.INDEFINITE)

    val rotationAnimation4 = new Timeline()
    rotationAnimation4.getKeyFrames().add(new KeyFrame(Duration.seconds(14),
      new KeyValue(rotationTransform4.angleProperty(), 0: java.lang.Double)))
    rotationAnimation4.setCycleCount(Animation.INDEFINITE)

    val rotationAnimation5 = new Timeline()
    rotationAnimation5.getKeyFrames().add(new KeyFrame(Duration.seconds(12),
      new KeyValue(rotationTransform5.angleProperty(), 360: java.lang.Double)))
    rotationAnimation5.setCycleCount(Animation.INDEFINITE)

    val ft = new FillTransition(Duration.millis(2000), text, Color.LIGHTGRAY, Color.WHITE)
    ft.setCycleCount(Animation.INDEFINITE)
    ft.setAutoReverse(true)
    ft.play()
    (scene, rotationAnimation1, rotationAnimation2, rotationAnimation3, rotationAnimation4, rotationAnimation5)
  }

  /** Draw semiring. */
  protected def drawSemiRing(x: DoubleExpression, y: DoubleExpression, radius: DoubleExpression, width: DoubleExpression, angle: Double): Path = {
    val large = angle > math.Pi
    val innerRadius = new SimpleDoubleProperty()
    innerRadius.bind(radius.subtract(width))
    val x1 = new SimpleDoubleProperty()
    x1.bind(innerRadius.multiply(math.cos(0)))
    val y1 = new SimpleDoubleProperty()
    y1.bind(innerRadius.multiply(math.sin(0)))
    val x2 = new SimpleDoubleProperty()
    x2.bind(innerRadius.multiply(math.cos(angle)))
    val y2 = new SimpleDoubleProperty()
    y2.bind(innerRadius.multiply(math.sin(angle)))
    val ax1 = new SimpleDoubleProperty()
    ax1.bind(radius.multiply(math.cos(0)))
    val ay1 = new SimpleDoubleProperty()
    ay1.bind(radius.multiply(math.sin(0)))
    val ax2 = new SimpleDoubleProperty()
    ax2.bind(radius.multiply(math.cos(angle)))
    val ay2 = new SimpleDoubleProperty()
    ay2.bind(radius.multiply(math.sin(angle)))

    val moveTo = new MoveTo()
    moveTo.xProperty().bind(x.add(x1))
    moveTo.yProperty().bind(y.add(y1))

    val arcToInner = new ArcTo()
    arcToInner.radiusXProperty().bind(innerRadius)
    arcToInner.radiusYProperty().bind(innerRadius)
    arcToInner.xProperty().bind(x.add(x2))
    arcToInner.yProperty().bind(y.add(y2))
    arcToInner.setLargeArcFlag(large)
    arcToInner.setSweepFlag(true)

    val lineTo = new LineTo()
    lineTo.xProperty().bind(x.add(ax2))
    lineTo.yProperty().bind(y.add(ay2))

    val arcTo = new ArcTo()
    arcTo.radiusXProperty().bind(radius)
    arcTo.radiusYProperty().bind(radius)
    arcTo.xProperty().bind(x.add(ax1))
    arcTo.yProperty().bind(y.add(ay1))
    arcTo.setLargeArcFlag(large)
    arcTo.setSweepFlag(false)

    new Path(moveTo, arcToInner, lineTo, arcTo, new ClosePath)
  }
}
