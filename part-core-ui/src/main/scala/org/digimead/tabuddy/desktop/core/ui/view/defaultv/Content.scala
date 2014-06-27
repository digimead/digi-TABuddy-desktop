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

import java.util.concurrent.atomic.AtomicReference
import javafx.animation.{ FadeTransition, FadeTransitionBuilder }
import javafx.animation.{ PathTransitionBuilder, Transition }
import javafx.animation.PathTransition.OrientationType
import javafx.beans.value.{ ChangeListener, ObservableValue }
import javafx.event.{ ActionEvent, EventHandler }
import javafx.geometry.VPos
import javafx.scene.{ Group, Scene }
import javafx.scene.effect.DropShadow
import javafx.scene.layout.{ HBox, Pane, VBox }
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.scene.text.{ Font, FontPosture, FontWeight, Text, TextAlignment, TextBuilder }
import javafx.util.Duration
import org.digimead.digi.lib.jfx4swt.FXCanvas
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.Report
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.core.operation.OperationInfo
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.{ ResourceManager, UI }
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.layout.{ FormAttachment, FormData, GridData, GridLayout }
import org.eclipse.swt.widgets.{ Composite, Control, Event, Listener }

/**
 * Default view content.
 */
class Content(parent: Composite, style: Int = SWT.NONE) extends ContentSkel(parent, style) with XLoggable {
  /** About composite. */
  val aboutComposite = new FXCanvas(getCompositeAbout(), SWT.NONE, false)
  /** About original size. */
  val aboutOriginalSize = new AtomicReference(default)
  /** Default element size. */
  lazy val default = new Point(mininumSize, mininumSize)
  /** Element minimum size. */
  lazy val mininumSize = 16
  /** Quotation composite with FX canvas. */
  val quotationComposite = new FXCanvas(getCompositeQuotation(), SWT.NONE, false)
  /** Quotation original size. */
  val quotationOriginalSize = new AtomicReference(default)
  /** Title composite with FX canvas. */
  val titleComposite = new FXCanvas(getCompositeTitle, SWT.NONE, false)
  /** Title maximum margin. */
  lazy val titleMaximumMargin = 300
  /** Title minimum margin. */
  lazy val titleMinimumMargin = 10
  /** Title original size. */
  lazy val titleOriginalSize = new AtomicReference(default)

  def initializeJFX() {
    val (titleScene, titlePathTransition, titlePenTransition, titleSize) = createTitle(titleComposite)
    titleComposite.addDisposeListener { stage ⇒ titleScene.rootProperty().set(new Group) }
    titlePenTransition.setOnFinished(new EventHandler[ActionEvent]() {
      override def handle(t: ActionEvent) = titleComposite.sceneNeedsRepaint()
    })
    titleComposite.setScene(titleScene, { _ ⇒ titlePathTransition.play() })
    titleOriginalSize.set(titleSize)

    val (quotationScene, quotationTransition, quotationSize) = createQuotation(quotationComposite)
    quotationComposite.addDisposeListener { stage ⇒ quotationScene.rootProperty().set(new Group) }
    quotationTransition.setOnFinished(new EventHandler[ActionEvent]() {
      override def handle(t: ActionEvent) = quotationComposite.sceneNeedsRepaint()
    })
    quotationComposite.setScene(quotationScene, _ ⇒ quotationTransition.play())
    quotationOriginalSize.set(quotationSize)

    val aboutScene = createAbout(aboutComposite)
    aboutComposite.addDisposeListener { stage ⇒ aboutScene.rootProperty().set(new Group) }
    aboutComposite.setScene(aboutScene)

    App.exec {
      if (!titleComposite.isDisposed() && !quotationComposite.isDisposed() && !aboutComposite.isDisposed()) {
        getCompositeAbout.getParent().notifyListeners(SWT.Resize, null)
        getCompositeQuotation.notifyListeners(SWT.Resize, null)
        getCompositeTitle.notifyListeners(SWT.Resize, null)
      }
    }
  }
  def initializeSWT() {
    titleComposite.setLayoutData({
      val layoutData = new GridData(SWT.CENTER, SWT.CENTER, true, false, 1, 1)
      layoutData.minimumWidth = mininumSize
      layoutData.minimumHeight = mininumSize
      layoutData
    })
    titleComposite.setBackground(ResourceManager.getColor(SWT.COLOR_WHITE))
    getCompositeTitle.addListener(SWT.Resize, new Listener() {
      def handleEvent(e: Event) = e.widget match {
        case composite: Composite ⇒
          val titlePreferredSize = titleOriginalSize.get
          composite.getLayout() match {
            case containerLayout: GridLayout ⇒
              if (titlePreferredSize != default)
                composite.getChildren().headOption match {
                  case Some(fxCanvas: FXCanvas) ⇒
                    val width = composite.getSize.x
                    val margin = math.min(math.max(titleMinimumMargin, width / 8), titleMaximumMargin)
                    containerLayout.marginLeft = margin
                    containerLayout.marginRight = margin
                    containerLayout.marginWidth = 0
                    fxCanvas.getLayoutData() match {
                      case titleData: GridData ⇒
                        val k = (width - margin * 2).toDouble / titlePreferredSize.x
                        titleData.heightHint = math.ceil(titlePreferredSize.y * k).toInt
                        titleData.widthHint = math.ceil(titlePreferredSize.x * k).toInt
                        fxCanvas.setPreferredSize(titleData.widthHint, titleData.heightHint)
                        getParent().layout(Array[Control](composite.getChildren().head), SWT.DEFER)
                    }
                  case _ ⇒
                }
          }
      }
    })

    quotationComposite.setBackground(ResourceManager.getColor(SWT.COLOR_WHITE))
    getCompositeQuotation.addListener(SWT.Resize, new Listener() {
      def handleEvent(e: Event) = e.widget match {
        case composite: Composite ⇒
          val quotationPrefferedSize = quotationOriginalSize.get
          val size = composite.getSize()
          if (size.x > 0 && size.y > 0 && quotationPrefferedSize != default) {
            composite.getLayoutData() match {
              case quotationData: FormData ⇒
                quotationData.height = (quotationPrefferedSize.y * size.x / quotationPrefferedSize.x) + 1
                composite.getChildren().headOption.map {
                  case fxCanvas: FXCanvas ⇒ fxCanvas.setPreferredSize(size.x, quotationData.height)
                }
                getParent().layout(Array[Control](composite), SWT.DEFER)
            }
          }
        case _ ⇒
      }
    })

    aboutComposite.setBackground(ResourceManager.getColor(SWT.COLOR_WHITE))
    getCompositeAbout().getParent.addListener(SWT.Resize, new Listener() {
      def handleEvent(e: Event) = e.widget match {
        case composite: Composite ⇒
          val size = composite.getSize()
          if (size.x > 0 && size.y > 0) {
            getCompositeAbout().getLayoutData() match {
              case aboutData: FormData ⇒
                val aboutPreferredSize = aboutOriginalSize.get
                if (size.x > aboutPreferredSize.x)
                  aboutData.left = new FormAttachment(0, (size.x - aboutPreferredSize.x) / 2)
                else
                  aboutData.left = new FormAttachment(0, 0)
                composite.layout(Array[Control](aboutComposite), SWT.DEFER)
            }
          }
        case _ ⇒
      }
    })
  }

  /** Create about composite. */
  protected def createAbout(parent: FXCanvas): Scene = {
    //val text = UI.<>[TextBuilder[_], Text](TextBuilder.create()) { b ⇒
    //  b.font(Font.font(null, FontWeight.LIGHT, 12))
    //  b.textAlignment(TextAlignment.CENTER)
    //  b.textOrigin(VPos.TOP)
    //  b.fill(Color.DARKGRAY)
    //  b.build()
    //}
    val text = new Text()
    text.setFont(Font.font(null, FontWeight.LIGHT, 12))
    text.setTextAlignment(TextAlignment.CENTER)
    text.setTextOrigin(VPos.TOP)
    text.setFill(Color.DARKGRAY)

    text.textProperty().addListener(new ChangeListener[AnyRef]() {
      override def changed(ov: ObservableValue[_ <: AnyRef], t: AnyRef, t1: AnyRef) {
        val bounds = text.getLayoutBounds()
        val preferredSize = new Point(bounds.getWidth().toInt + 1, bounds.getHeight().toInt + 1)
        aboutOriginalSize.set(preferredSize)
        App.exec {
          if (!parent.isDisposed()) {
            parent.setPreferredSize(preferredSize.x, preferredSize.y)
            parent.getParent().getParent().notifyListeners(SWT.Resize, null)
          }
        }
      }
    })

    // I see no reason to translate this in the future too.
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
            case Operation.Result.OK(None, message) ⇒
              log.warn("Information is not available.")
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
    group.setAutoSizeChildren(false)
    val scene = new Scene(group)

    scene
  }
  /** Create quotation composite. */
  protected def createQuotation(parent: FXCanvas): (Scene, FadeTransition, Point) = {
    // I see no reason to translate this in the future.
    //val prePart1 = UI.<>[TextBuilder[_], Text](TextBuilder.create()) { b ⇒
    //  b.text("A human being should be able ")
    //  b.cache(true)
    //  b.fill(Color.BLACK)
    //  b.font(Font.font(null, FontWeight.BOLD, 4)) // set minimum size
    //  b.build()
    //}
    val prePart1 = new Text("A human being should be able ")
    prePart1.setCache(true)
    prePart1.setFill(Color.BLACK)
    prePart1.setFont(Font.font(null, FontWeight.BOLD, 4)) // set minimum size
    //val prePart2 = UI.<>[TextBuilder[_], Text](TextBuilder.create()) { b ⇒
    //  b.text("to change a diaper, plan an invasion, butcher a hog, conn a ship,")
    //  b.cache(true)
    //  b.fill(Color.DARKGRAY)
    //  b.build()
    //}
    val prePart2 = new Text("to change a diaper, plan an invasion, butcher a hog, conn a ship,")
    prePart2.setCache(true)
    prePart2.setFill(Color.DARKGRAY)
    //val body = UI.<>[TextBuilder[_], Text](TextBuilder.create()) { b ⇒
    //  b.text("design a building, write a sonnet, balance accounts, build a wall, set a bone, " +
    //    "comfort the dying,\ntake orders, give orders, cooperate, act alone, " +
    //    "solve equations, analyze a new problem,\npitch manure, program a computer, " +
    //    "cook a tasty meal, fight efficiently, die gallantly.")
    //  b.cache(true)
    //  b.fill(Color.DARKGRAY)
    //  b.build()
    //}
    val body = new Text("design a building, write a sonnet, balance accounts, build a wall, set a bone, " +
      "comfort the dying,\ntake orders, give orders, cooperate, act alone, " +
      "solve equations, analyze a new problem,\npitch manure, program a computer, " +
      "cook a tasty meal, fight efficiently, die gallantly.")
    body.setCache(true)
    body.setFill(Color.DARKGRAY)
    //val post = UI.<>[TextBuilder[_], Text](TextBuilder.create()) { b ⇒
    //  b.text("Specialization is for insects.")
    //  b.cache(true)
    //  b.fill(Color.BLACK)
    //  b.build()
    //}
    val post = new Text("Specialization is for insects.")
    post.setCache(true)
    post.setFill(Color.BLACK)
    //val signature = UI.<>[TextBuilder[_], Text](TextBuilder.create()) { b ⇒
    //  b.text("\n    - Robert A. Heinlein")
    //  b.cache(true)
    //  b.fill(Color.DARKGRAY)
    //  b.build()
    //}
    val signature = new Text("\n    - Robert A. Heinlein")
    signature.setCache(true)
    signature.setFill(Color.DARKGRAY)
    def adjust(size: Int) {
      val italic = Font.font(null, FontPosture.ITALIC, size)
      prePart1.setFont(italic)
      prePart2.setFont(italic)
      body.setFont(italic)
      post.setFont(italic)
      signature.setFont(italic)
    }
    val pre = new HBox()
    pre.getChildren().addAll(prePart1, prePart2)
    val root = new VBox()
    root.getChildren().addAll(pre, body, post, signature)
    root.setOpacity(0)
    val minimumHeight = prePart1.getLayoutBounds.getHeight()
    adjust(20) // set maximum size
    val maximumHeight = prePart1.getLayoutBounds.getHeight()
    val fontSizeStep = (maximumHeight - minimumHeight) / 16
    val fontGradation = for (i ← 1 to 16)
      yield (i * fontSizeStep + minimumHeight) * 8

    // Wrap the resizable content in a non-resizable container (Group).
    val prePartBounds = prePart1.getLayoutBounds()
    val bodyBounds = body.getLayoutBounds()
    val postBounds = post.getLayoutBounds()
    val signatureBounds = signature.getLayoutBounds()
    val quotationWidth = Seq(bodyBounds.getWidth(), postBounds.getWidth(), signatureBounds.getWidth()).foldLeft(pre.getLayoutBounds().getWidth())(math.max)
    val quotationHeight = prePartBounds.getHeight() * 8
    val quotationSize = new Point(math.ceil(quotationWidth).toInt, math.ceil(quotationHeight).toInt)
    val group = new Group(root)
    group.setAutoSizeChildren(false)
    val scene = new Scene(group)

    scene.heightProperty().addListener(new ChangeListener[AnyRef]() {
      override def changed(ov: ObservableValue[_ <: AnyRef], t: AnyRef, t1: AnyRef) = {
        val height = t1.asInstanceOf[Double]
        val index = fontGradation.indexWhere(_ > height)
        val size = if (index == -1) 20 else index + 4
        adjust(size)
        group.layout()
        val rootBounds = root.getBoundsInLocal()
        if (rootBounds.getWidth() > scene.getWidth()) {
          val k = scene.getWidth() / rootBounds.getWidth()
          root.setScaleX(k)
          root.setScaleY(k)
        } else {
          root.setScaleX(1)
          root.setScaleY(1)
        }
      }
    })
    scene.widthProperty().addListener(new ChangeListener[AnyRef]() {
      override def changed(ov: ObservableValue[_ <: AnyRef], t: AnyRef, t1: AnyRef) =
        if (t1.asInstanceOf[Double] > 0) {
          val rootBounds = root.getBoundsInLocal()
          if (rootBounds.getWidth() > scene.getWidth()) {
            val k = scene.getWidth() / rootBounds.getWidth()
            root.setScaleX(k)
            root.setScaleY(k)
          } else {
            root.setScaleX(1)
            root.setScaleY(1)
          }
        }
    })

    val fadeTransition = FadeTransitionBuilder.create()
      .duration(Duration.seconds(7))
      .fromValue(0)
      .toValue(1.0)
      .node(root)
      .build()
    fadeTransition.setOnFinished(new EventHandler[ActionEvent]() {
      override def handle(t: ActionEvent) = fadeTransition.stop()
    })

    (scene, fadeTransition, quotationSize)
  }
  /** Create title composite. */
  protected def createTitle(parent: FXCanvas): (Scene, Transition, Transition, Point) = {
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
    pen.translateXProperty().addListener(changeListener)
    pen.translateYProperty().addListener(changeListener)
    pen.rotateProperty().addListener(changeListener)
    pen.setFill(Color.ORANGERED)

    val ds = new DropShadow()
    ds.setOffsetY(3.0f)
    ds.setColor(Color.color(0.4f, 0.4f, 0.4f))

    // I see no reason to translate this in the future.
    //val text = UI.<>[TextBuilder[_], Text](TextBuilder.create()) { b ⇒
    //  b.text("TA Buddy: Desktop")
    //  b.font(Font.font(null, FontWeight.BOLD, 100))
    //  b.effect(ds)
    //  b.cache(true)
    //b.clip(clip)
    //  b.textOrigin(VPos.TOP)
    //  b.fill(Color.RED)
    //  b.build()
    //}
    val text = new Text("TA Buddy: Desktop")
    text.setFont(Font.font(null, FontWeight.BOLD, 100))
    text.setEffect(ds)
    text.setCache(true)
    //text.setclip(clip)
    text.setTextOrigin(VPos.TOP)
    text.setFill(Color.RED)

    val root = new Pane()
    root.setCache(true)
    root.getChildren().addAll(text, pen)

    // Wrap the resizable content in a non-resizable container (Group).
    val group = new Group(root)
    val scene = new Scene(group)
    val titleBounds = group.getLayoutBounds()
    val titleSize = new Point(math.ceil(titleBounds.getWidth()).toInt, math.ceil(titleBounds.getHeight()).toInt)
    text.setClip(clip)
    text.setX(-1.5 * titleBounds.getMinX())
    text.setY(-1.5 * titleBounds.getMinY())

    scene.widthProperty().addListener(new ChangeListener[AnyRef]() {
      override def changed(ov: ObservableValue[_ <: AnyRef], t: AnyRef, t1: AnyRef) =
        if (t1.asInstanceOf[Double] > 0) {
          val k = t1.asInstanceOf[Double] / titleSize.x
          root.setScaleX(k)
          root.setScaleY(k)
          root.setLayoutX((scene.getWidth() - titleSize.x) / 2)
        }
    })
    scene.heightProperty().addListener(new ChangeListener[AnyRef]() {
      override def changed(ov: ObservableValue[_ <: AnyRef], t: AnyRef, t1: AnyRef) =
        if (t1.asInstanceOf[Double] > 0)
          root.setLayoutY((scene.getHeight() - titleSize.y) / 2)
    })

    val pathTransition = PathTransitionBuilder.create().
      duration(Duration.seconds(15)).
      path(text).
      node(pen).
      orientation(OrientationType.ORTHOGONAL_TO_TANGENT).
      build()
    val penTransition = FadeTransitionBuilder.create().
      duration(Duration.seconds(1)).
      toValue(0.2).
      node(pen).
      build()

    // Once we done we don't want to store thousands of rectangles used to clip.
    pathTransition.setOnFinished(new EventHandler[ActionEvent]() {
      override def handle(t: ActionEvent) {
        text.setClip(null)
        clip.getChildren().clear()
        pathTransition.stop()
        penTransition.play()
        penTransition.setOnFinished(new EventHandler[ActionEvent]() {
          override def handle(t: ActionEvent) {
            penTransition.stop()
            root.getChildren().remove(pen)
          }
        })
      }
    })

    (scene, pathTransition, penTransition, titleSize)
  }
}
