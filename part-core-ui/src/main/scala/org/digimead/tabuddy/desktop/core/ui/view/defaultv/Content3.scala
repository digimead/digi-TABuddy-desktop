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
import javafx.scene.layout.BorderPane
import javafx.scene.canvas.Canvas
import javafx.scene.effect.GaussianBlur
import javafx.scene.canvas.GraphicsContext
import javafx.scene.canvas.Canvas
import javafx.animation.AnimationTimer
import javafx.scene.paint.Color

class Content3 {
  /** Get ViewDefault content. */
  def apply(parent: Composite): Composite = {
    val body = new FXCanvas(parent, SWT.NONE)
    val scene = createScene()
    body.setScene(scene)
    body
  }

  def createScene(): Scene = {
    val animater = new Content3.CanvasParticleAnimater(300, 300, new Content3.BallParticleRenderer())

    // Create a pane to contain the <code>Canvas</code> that will automatically center it.
    val pane = new BorderPane()
    pane.setCenter(animater.canvas)
    animater.canvas.widthProperty().bind(pane.widthProperty())
    animater.canvas.heightProperty().bind(pane.heightProperty())

    // Create a new JavaFX <code>Scene</code> with the pane as it's root in the scene graph.
    new Scene(pane)
  }
}

object Content3 {
  /** The absolute minimum radius we would like to see. */
  val MIN_RADIUS = 8
  /** The absolute maximum radius we would like to see. */
  val MAX_RADIUS = 120
  /** The absolute maximum x and y delta we would like to use to control motion. */
  val MAX_DELTA = 12
  /** The standard radius delta. */
  val RADIUS_DELTA = 2
  /** An array of the range of colours we would like to select from when creating a <code>Particle</code>. */
  val COLOURS = Array[Color](Color.RED, Color.INDIGO, Color.GOLD, Color.GREEN, Color.BROWN, Color.BLUE,
    Color.ORANGERED, Color.YELLOW, Color.AQUA, Color.LIGHTPINK)

  /**
   * A <code>BallParticleRenderer</code> is an implementation of the particle renderer that renders each particle as a
   * filled oval.
   */
  class BallParticleRenderer extends ParticleRenderer {
    // This type of renderer actually seems to support a high number of particles.
    def getNumberOfParticles(): Int = 100
    def init(canvas: Canvas) {
      // Set an alpha level across all rendering for the <code>Canvas</code> to apply a translucent effect.
      //canvas.getGraphicsContext2D().setGlobalAlpha(0.7)
      // Apply a Gaussian Blur to the entire <code>Canvas</code> for a cool, soft effect.
      //val blur = new GaussianBlur()
      //blur.setRadius(4d)
      //canvas.setEffect(blur)
      // Too sloooooooow :-(
    }
    override def render(gc: GraphicsContext, p: Particle) {
      // Set the current fill colour to that of the specified <code>Particle</code>.
      gc.setFill(p.colour)
      // Render the particle by filling the oval specified by the attributes of the <code>Particle</code> with the
      // current fill colour.
      gc.fillOval(p.x, p.y, p.r, p.r)
    }
  }
  /**
   * A <code>CanvasParticleAnimater</code> is the main object involved in testing a JavaFX <code>Canvas</code> by
   * rendering and animating a number of particles.
   *
   * The algorithm for animating the particles is very simplistic but still enables some interesting patterns of movement.
   * An animation timer is used to control the repeated rendering of "frames". During each frame both the coordinates and
   * the radius of the particles are changed in such a way that the particle is continually getting smaller or larger in
   * size until either minimum or maximum dimensions are reached and is moving in a straight path until it encounters the
   * bounds of the canvas.
   *
   * @param width The width of the actual <code>Canvas</code>
   * @param height The height of the actual <code>Canvas</code>
   * @param renderer The particular renderer to be used for rendering each particle.
   */
  class CanvasParticleAnimater(val initialWidth: Int, val initialHeight: Int, val renderer: ParticleRenderer) {
    /** The <code>Canvas</code> in which the particles are to be rendered. */
    val canvas = new Canvas(initialWidth, initialHeight)
    /** This is the context in which all graphical operations on the <code>Canvas</code> are performed. */
    val gc = canvas.getGraphicsContext2D()

    /**
     * An array of the particles to be rendered.
     */
    val particles = for (i ← 0 until renderer.getNumberOfParticles())
      yield new Particle(initialWidth, initialHeight)

    renderer.init(canvas)
    animate()

    /**
     * Animates the particles.
     *
     * The animation is driven by the
     * <code>AnimationTimer<code> which repeatedly invokes the method to render each frame.
     */
    protected def animate() {
      new AnimationTimer() {
        override def handle(now: Long) = renderFrame()
      }.start();
    }

    /** Renders each "frame" of the animation and adjusts the attributes of each <code>Particle</code>. */
    def renderFrame() {

      // Start by clearing the canvas by filling it with a solid colour.
          gc.setFill(Color.DARKSLATEGRAY)
          gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight())

      // Loop through each of our particles.
      for (p ← particles) {

        // Firstly invoke the particular implementation of the renderer to render each particle.
        renderer.render(gc, p)

        // Now we want to adjust the radius of the particle. If the current radius is less than the minimum we allow
        // then set the radius delta to the standard positive value so that in future it will grow larger. If the
        // current radius has grown larger than the maximum for this particle then set the radius delta to a
        // negative value to ensure that it decreases in size in future. In all other cases we don't alter the
        // radius delta.
        if (p.r <= Content3.MIN_RADIUS)
          p.dr = Content3.RADIUS_DELTA
        else if (p.r > p.maxR)
          p.dr = -Content3.RADIUS_DELTA

        // Use the radius delta to change the size of the particle's radius.
        p.r = p.r + p.dr

        // Now we want to actually move the particle so increment or decrement the x and y coordinates of the
        // particle by the x delta and y delta defined for it.
        p.x = p.x + p.dx
        p.y = p.y + p.dy

        // We need to ensure that the particle does not move outside the bounds of the canvas so if the current x
        // coordinate is greater than the width of the canvas or less than zero then we need to reverse the motion
        // by changing the sign of the x delta.
        if (p.x > canvas.getWidth()) {
          p.x = canvas.getWidth()
          p.dx = -p.dx
        } else if (p.x < 0) {
          p.x = 0
          p.dx = -p.dx
        }

        // We also need to handle a similar situation with the y coordinate so if the current y
        // coordinate is greater than the height of the canvas or less than zero then we need to reverse the motion
        // by changing the sign of the y delta.
        if (p.y > canvas.getHeight()) {
          p.y = canvas.getHeight()
          p.dy = -p.dy
        } else if (p.y < 0) {
          p.y = 0
          p.dy = -p.dy
        }
      }
    }
  }
  /**
   * Creates a new instance of <code>Particle</code> based onthe specified dimensions of the <code>Canvas</code>.
   *
   * @param canvasWidth The width of the canvas where the particle will be rendered.
   * @param canvasHeight The height of the canvas where the particle will be rendered.
   */
  class Particle(val canvasWidth: Int, val canvasHeight: Int) {
    // Initialise the x and y coordinates as being randomly positioned somewhere within the dimensions of the
    // canvas. For approximately half the particles we set the x coordinate to be oriented toward the left and half
    // oriented toward the right. Similarly half the particles have the y coordinate set to be oriented toward the
    // top and half are oriented toward the bottom.
    /** The current x coordinate of the particle. */
    var x: Double = {
      val x = Math.round(Math.random() * 50)
      if (Math.random() > 0.5) canvasWidth - x else x
    }
    /** The current y coordinate of the particle. */
    var y: Double = {
      val y = Math.round(Math.random() * 50);
      if (Math.random() > 0.5) canvasHeight - y else y
    }

    // Select a random colour from our preselected range.
    /** The colour of the particle.	 */
    val colour = Content3.COLOURS(Math.round(Math.random() * (Content3.COLOURS.length - 1)).toInt)

    // Randomly set the x and y deltas to be a positive or negative value within the allowable range.
    /** The current value for the x delta of the particle. */
    var dx = Math.round(Math.random() * Content3.MAX_DELTA) - Content3.MAX_DELTA / 2
    /** The current value for the y delta of the particle. */
    var dy = Math.round(Math.random() * Content3.MAX_DELTA) - Content3.MAX_DELTA / 2

    // Randomly set the radius, maximum radius and radius deltas to be within the allowable range.
    /** The current radius of the particle. */
    var r = Math.round(Math.random() * Content3.MAX_RADIUS) - Content3.MAX_RADIUS / 2

    /** The maximum radius permitted for this particle. */
    val maxR = Math.round(Math.random() * Content3.MAX_RADIUS / 2) + 10
    /** The current radius delta for this particle. */
    var dr = if (Math.random() > 0.5) Content3.RADIUS_DELTA else -Content3.RADIUS_DELTA
  }
  /**
   * A <code>ParticleRenderer</code> is an object responsible for rendering a particle at a particular location in the
   * <code>Canvas</code>.
   */
  trait ParticleRenderer {
    /**
     * Returns the recommended number of particles supported by this renderer in a <code>Canvas</code>. The more complex
     * the rendering, the lower the number of particles.
     *
     * @return The number of particles.
     */
    def getNumberOfParticles(): Int

    /**
     * Initialises the renderer for the specified <code>Canvas</code>.
     *
     * @param canvas The <code>Canvas</code> to render the particles.
     */
    def init(canvas: Canvas)

    /**
     * Renders the given <code>Particle</code> using the specified <code>GraphicsContext</code>.
     *
     * @param gc The graphics context.
     * @param p The particle to be rendered.
     */
    def render(gc: GraphicsContext, p: Particle)
  }
}
