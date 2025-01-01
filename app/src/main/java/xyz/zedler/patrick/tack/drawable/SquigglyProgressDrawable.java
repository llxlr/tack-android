/*
 * This file is part of Tack Android.
 *
 * Tack Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Tack Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Tack Android. If not, see http://www.gnu.org/licenses/.
 *
 * Copyright (c) 2020-2025 by Patrick Zedler
 */

package xyz.zedler.patrick.tack.drawable;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import xyz.zedler.patrick.tack.R;
import xyz.zedler.patrick.tack.util.ResUtil;
import xyz.zedler.patrick.tack.util.UiUtil;

public class SquigglyProgressDrawable extends Drawable {

  private static final float TWO_PI = (float) (Math.PI * 2f);
  /* distance over which amplitude drops to zero, measured in wavelengths */
  private static final float TRANSITION_PERIODS = 1.5f;
  /* wave endpoint as percentage of bar when play position is zero */
  private static final float MIN_WAVE_ENDPOINT = 0.1f;
  /* wave endpoint as percentage of bar when play position matches wave endpoint */
  private static final float MATCHED_WAVE_ENDPOINT = 0.6f;
  /* Enables a transition region where the amplitude of the wave is reduced linearly across it */
  private static final boolean ENABLE_TRANSITION = false;

  private final Paint wavePaint = new Paint();
  private final Paint linePaint = new Paint();
  private final Path path = new Path();
  private float heightFraction = 0f;
  private ValueAnimator heightAnimator = null;
  private float phaseOffset = 0f;
  private long lastFrameTime = -1L;
  private boolean reduceAnimations = false;

  // Horizontal length of the sine wave
  private final float waveLength;
  // Height of each peak of the sine wave
  private final float lineAmplitude;
  // Line speed in px per second
  private final float phaseSpeed;
  // Progress stroke width, both for wave and solid line
  private float strokeWidth;
  private final float gapWidth;

  private boolean animate = false;
  private boolean loopInvalidation = false;

  public SquigglyProgressDrawable(@NonNull Context context) {
    linePaint.setAntiAlias(true);
    linePaint.setStyle(Style.STROKE);
    linePaint.setStrokeCap(Cap.ROUND);
    linePaint.setColor(ResUtil.getColor(context, R.attr.colorSecondaryContainer));
    wavePaint.setAntiAlias(true);
    wavePaint.setStyle(Style.STROKE);
    wavePaint.setStrokeCap(Cap.ROUND);
    wavePaint.setColor(ResUtil.getColor(context, R.attr.colorPrimary));

    waveLength = UiUtil.dpToPx(context, 20);
    lineAmplitude = UiUtil.dpToPx(context, 1.5f);
    phaseSpeed = UiUtil.dpToPx(context, 8);
    gapWidth = UiUtil.dpToPx(context, 4);
    setStrokeWidth(UiUtil.dpToPx(context, 4));
  }

  @Override
  public void draw(@NonNull Canvas canvas) {
    if (animate) {
      long now = SystemClock.uptimeMillis();
      phaseOffset += (now - lastFrameTime) / 1000f * phaseSpeed;
      phaseOffset %= waveLength;
      lastFrameTime = now;
    }
    if (loopInvalidation) {
      invalidateSelf();
    }

    float progress = getLevel() / 10_000f;
    float totalWidth = getBounds().width();
    float totalProgressPx = totalWidth * progress;
    float waveProgressPx = totalWidth * ((!ENABLE_TRANSITION || progress > MATCHED_WAVE_ENDPOINT)
        ? progress
        : lerp(
            MIN_WAVE_ENDPOINT,
            MATCHED_WAVE_ENDPOINT,
            lerpInv(0f, MATCHED_WAVE_ENDPOINT, progress)
        )
    );

    float waveStart = -phaseOffset - waveLength / 2f;
    float waveEnd = ENABLE_TRANSITION ? totalWidth : waveProgressPx;

    if (reduceAnimations) {
      // translate to the start position of the progress bar for all draw commands
      float clipTop = lineAmplitude + strokeWidth;
      canvas.save();
      canvas.translate(getBounds().left, getBounds().centerY());

      // Draw line up to progress position
      canvas.save();
      canvas.clipRect(0f, -1f * clipTop, totalProgressPx, clipTop);
      canvas.drawLine(waveStart, 0, totalWidth * progress, 0, wavePaint);
      canvas.restore();

      // Draw a flat line to the end of the region.
      // The discontinuity is hidden by the progress bar thumb shape.
      canvas.drawLine(
          Math.min(totalProgressPx + linePaint.getStrokeWidth() + gapWidth, totalWidth),
          0f, totalWidth, 0f,
          linePaint
      );
      // Draw stop indicator
      canvas.drawPoint(totalWidth, 0, wavePaint);

      // Draw round line cap at the beginning of the line
      canvas.drawPoint(0, 0, totalProgressPx > 0 ? wavePaint : linePaint);
      canvas.restore();
    } else {
      // Reset path object to the start
      path.rewind();
      path.moveTo(waveStart, 0f);

      // Build the wave, incrementing by half the wavelength each time
      float currentX = waveStart;
      float waveSign = 1f;
      float currentAmp = computeAmplitude(currentX, waveSign, waveProgressPx);
      float dist = waveLength / 2f;
      while (currentX < waveEnd) {
        waveSign = -waveSign;
        float nextX = currentX + dist;
        float midX = currentX + dist / 2;
        float nextAmp = computeAmplitude(nextX, waveSign, waveProgressPx);
        path.cubicTo(midX, currentAmp, midX, nextAmp, nextX, nextAmp);
        currentAmp = nextAmp;
        currentX = nextX;
      }

      // translate to the start position of the progress bar for all draw commands
      float clipTop = lineAmplitude + strokeWidth;
      canvas.save();
      canvas.translate(getBounds().left, getBounds().centerY());

      // Draw path up to progress position
      canvas.save();
      canvas.clipRect(0f, -1f * clipTop, totalProgressPx, clipTop);
      canvas.drawPath(path, wavePaint);
      canvas.restore();

      if (ENABLE_TRANSITION) {
        // If there's a smooth transition, we draw the rest of the
        // path in a different color (using different clip params)
        canvas.save();
        canvas.clipRect(totalProgressPx, -1f * clipTop, totalWidth, clipTop);
        canvas.drawPath(path, linePaint);
        canvas.restore();
      } else {
        // No transition, just draw a flat line to the end of the region.
        // The discontinuity is hidden by the progress bar thumb shape.
        canvas.drawLine(
            Math.min(totalProgressPx + linePaint.getStrokeWidth() + gapWidth, totalWidth),
            0f, totalWidth, 0f,
            linePaint
        );
      }

      // Draw stop indicator
      canvas.drawPoint(totalWidth, 0, wavePaint);

      // Draw round line cap at the beginning of the wave
      double startAmp = Math.cos(Math.abs(waveStart) / waveLength * TWO_PI);
      canvas.drawPoint(
          0f, (float) (startAmp * lineAmplitude * heightFraction),
          totalProgressPx > 0 ? wavePaint : linePaint
      );
      canvas.restore();
    }
  }

  @Override
  public void setAlpha(int alpha) {}

  @Override
  public void setColorFilter(@Nullable ColorFilter colorFilter) {}

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }

  @Override
  protected boolean onLevelChange(int level) {
    return true;
  }

  public void setStrokeWidth(float strokeWidth) {
    if (this.strokeWidth == strokeWidth) {
      return;
    }
    this.strokeWidth = strokeWidth;
    wavePaint.setStrokeWidth(strokeWidth);
    linePaint.setStrokeWidth(strokeWidth);
  }

  public void setAnimate(boolean animate, boolean animateTransition) {
    if (this.animate == animate) {
      return;
    }
    this.animate = animate;
    if (animate) {
      loopInvalidation = true;
      lastFrameTime = SystemClock.uptimeMillis();
    }
    if (heightAnimator != null) {
      heightAnimator.pause();
      heightAnimator.removeAllListeners();
      heightAnimator.removeAllUpdateListeners();
      heightAnimator.cancel();
      heightAnimator = null;
    }
    if (animateTransition) {
      heightAnimator = ValueAnimator.ofFloat(heightFraction, animate ? 1 : 0);
      heightAnimator.setDuration(animate ? 800 : 500);
      heightAnimator.setInterpolator(new FastOutSlowInInterpolator());
      heightAnimator.addUpdateListener(
          animation -> heightFraction = (float) animation.getAnimatedValue()
      );
      heightAnimator.addListener(new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
          heightAnimator = null;
          if (!animate) {
            loopInvalidation = false;
          }
        }
      });
      heightAnimator.start();
    } else {
      heightFraction = animate ? 1 : 0;
      loopInvalidation = false;
    }
    invalidateSelf();
  }

  public void setReduceAnimations(boolean reduce) {
    this.reduceAnimations = reduce;
    invalidateSelf();
  }

  public void pauseAnimation() {
    loopInvalidation = false;
  }

  public void resumeAnimation() {
    loopInvalidation = true;
    invalidateSelf();
  }

  /**
   * Helper function, computes amplitude for wave segment
   */
  private float computeAmplitude(float x, float sign, float waveProgressPx) {
    if (ENABLE_TRANSITION) {
      float length = TRANSITION_PERIODS * waveLength;
      float coeff = lerpInvSat(waveProgressPx + length / 2f, waveProgressPx - length / 2f, x);
      return sign * heightFraction * lineAmplitude * coeff;
    } else {
      return sign * heightFraction * lineAmplitude;
    }
  }

  private static float lerp(float start, float stop, float amount) {
    return start + (stop - start) * amount;
  }

  private static float lerpInv(float a, float b, float value) {
    return a != b ? ((value - a) / (b - a)) : 0.0f;
  }

  public static float constrain(float amount, float low, float high) {
    return amount < low ? low : (Math.min(amount, high));
  }

  public static float saturate(float value) {
    return constrain(value, 0.0f, 1.0f);
  }

  public static float lerpInvSat(float a, float b, float value) {
    return saturate(lerpInv(a, b, value));
  }
}
