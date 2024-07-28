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
 * Copyright (c) 2020-2024 by Patrick Zedler
 */

package com.google.android.material.slider;

import static com.google.android.material.slider.LabelFormatter.LABEL_GONE;
import static com.google.android.material.slider.LabelFormatter.LABEL_VISIBLE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOverlay;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnScrollChangedListener;
import android.view.animation.DecelerateInterpolator;
import androidx.annotation.ColorInt;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.interpolator.view.animation.FastOutLinearInInterpolator;
import com.google.android.material.internal.ViewOverlayImpl;
import com.google.android.material.internal.ViewUtils;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.tooltip.TooltipDrawable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import xyz.zedler.patrick.tack.R;
import xyz.zedler.patrick.tack.util.ResUtil;
import xyz.zedler.patrick.tack.util.UiUtil;

@SuppressLint("RestrictedApi")
public class CustomSlider extends Slider {

  private static final String TAG = "CustomSlider";

  private static final float THUMB_WIDTH_PRESSED_RATIO = .5f;
  private static final long THUMB_WIDTH_ANIM_DURATION = 200;
  private enum FullCornerDirection {
    BOTH, LEFT, RIGHT, NONE
  }

  private final RectF clipRect = new RectF();
  private final RectF trackRect = new RectF();
  private final RectF cornerRect = new RectF();
  private final Path trackPath = new Path();
  private final Paint inactiveTrackPaint = new Paint();
  private final Paint activeTrackPaint = new Paint();
  private final Paint inactiveTicksPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint activeTicksPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint stopIndicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final List<TooltipDrawable> labels = new ArrayList<>();
  private ValueAnimator thumbWidthAnimator, thumbPositionAnimator;
  private ValueAnimator labelsInAnimator, labelsOutAnimator;
  private MaterialShapeDrawable thumbDrawable;
  private int thumbWidth, thumbWidthAnim, minTickSpacing;
  private float normalizedValueAnim;
  private float[] ticksCoordinates;
  private boolean dirtyConfig;
  // Whether the labels are showing or in the process of animating in.
  private boolean labelsAreAnimatedIn = false;
  @NonNull
  private final OnScrollChangedListener onScrollChangedListener = () -> {
    if (shouldAlwaysShowLabel() && isEnabled()) {
      Rect contentViewBounds = new Rect();
      ViewGroup contentView = ViewUtils.getContentView(this);
      if (contentView == null) {
        return;
      }
      contentView.getHitRect(contentViewBounds);
      boolean isSliderVisibleOnScreen = getLocalVisibleRect(contentViewBounds);
      ViewOverlayImpl contentViewOverlay = ViewUtils.getContentViewOverlay(this);
      if (contentViewOverlay == null) {
        return;
      }
      for (int i = 0; i < labels.size(); i++) {
        TooltipDrawable label = labels.get(i);
        // Get associated value for label
        if (i < getValues().size()) {
          positionLabel(label);
        }
        if (isSliderVisibleOnScreen) {
          contentViewOverlay.add(label);
        } else {
          contentViewOverlay.remove(label);
        }
      }
    }
  };

  public CustomSlider(@NonNull Context context) {
    super(context);
    init(context);
  }

  public CustomSlider(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init(context);
  }

  public CustomSlider(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init(context);
  }

  private void init(Context context) {
    dirtyConfig = true;
    thumbDrawable = new MaterialShapeDrawable();
    thumbDrawable.setShadowCompatibilityMode(
        MaterialShapeDrawable.SHADOW_COMPAT_MODE_ALWAYS
    );
    thumbDrawable.setFillColor(getTrackActiveTintList());
    thumbWidth = UiUtil.dpToPx(context, 4);
    minTickSpacing = UiUtil.dpToPx(context, 12);
    setTrackActiveTintList(getTrackActiveTintList());
    setTrackInactiveTintList(getTrackInactiveTintList());
    setTickInactiveRadius(getTickInactiveRadius());
    setTickInactiveTintList(getTickInactiveTintList());
    setTickActiveRadius(getTickActiveRadius());
    setTickActiveTintList(getTickActiveTintList());
    setTrackStopIndicatorSize(getTrackStopIndicatorSize());

    inactiveTicksPaint.setStyle(Style.STROKE);
    inactiveTicksPaint.setStrokeCap(Cap.ROUND);

    activeTicksPaint.setStyle(Style.STROKE);
    activeTicksPaint.setStrokeCap(Cap.ROUND);

    stopIndicatorPaint.setStyle(Style.FILL);
    stopIndicatorPaint.setStrokeCap(Cap.ROUND);

    updateThumbWidth(false, false);
    addOnSliderTouchListener(new OnSliderTouchListener() {
      @Override
      public void onStartTrackingTouch(@NonNull Slider slider) {
        updateThumbWidth(true, true);
      }

      @Override
      public void onStopTrackingTouch(@NonNull Slider slider) {
        updateThumbWidth(false, true);
      }
    });
    addOnChangeListener((slider, value, fromUser) -> updateThumbPosition(
        value, fromUser && getStepSize() > 0
    ));
    getViewTreeObserver().addOnGlobalLayoutListener(
        new ViewTreeObserver.OnGlobalLayoutListener() {
          @Override
          public void onGlobalLayout() {
            updateThumbPosition(getValue(), false);
            if (getViewTreeObserver().isAlive()) {
              getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
          }
        });
  }

  @Override
  protected void onRestoreInstanceState(Parcelable state) {
    super.onRestoreInstanceState(state);
    createLabelPool();
  }

  @Override
  protected void onDraw(@NonNull Canvas canvas) {
    if (dirtyConfig) {
      validateConfigurationIfDirty();
      maybeCalculateTicksCoordinates();
    }

    int yCenter = calculateTrackCenter();
    drawInactiveTrack(canvas, yCenter);
    drawActiveTrack(canvas, yCenter);

    maybeDrawTicks(canvas);
    maybeDrawStopIndicator(canvas, yCenter);

    // Draw labels if there is an active thumb or the labels are always visible.
    if ((getActiveThumbIndex() != -1 || shouldAlwaysShowLabel()) && isEnabled()) {
      ensureLabelsAdded();
    } else {
      ensureLabelsRemoved();
    }

    drawThumb(canvas, yCenter);
  }

  @Override
  protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
    super.onVisibilityChanged(changedView, visibility);
    // When the visibility is set to VISIBLE, onDraw() is called again which adds or removes labels
    // according to the setting.
    if (visibility != VISIBLE) {
      ViewOverlay contentViewOverlay = getContentViewOverlay();
      if (contentViewOverlay == null) {
        return;
      }
      for (TooltipDrawable label : labels) {
        contentViewOverlay.remove(label);
      }
    }
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    getViewTreeObserver().addOnScrollChangedListener(onScrollChangedListener);
    // The label is attached on the Overlay relative to the content.
    for (TooltipDrawable label : labels) {
      attachLabelToContentView(label);
    }
  }

  @Override
  protected void onDetachedFromWindow() {
    labelsAreAnimatedIn = false;
    for (TooltipDrawable label : labels) {
      detachLabelFromContentView(label);
    }
    getViewTreeObserver().removeOnScrollChangedListener(onScrollChangedListener);
    super.onDetachedFromWindow();
  }

  @Override
  void setValues(@NonNull Float... values) {
    super.setValues(values);
    createLabelPool();
  }

  @Override
  void setValues(@NonNull List<Float> values) {
    super.setValues(values);
    createLabelPool();
  }

  @Override
  public void setValueFrom(float valueFrom) {
    dirtyConfig = true;
    super.setValueFrom(valueFrom);
  }

  @Override
  public void setValueTo(float valueTo) {
    dirtyConfig = true;
    super.setValueTo(valueTo);
  }

  @Override
  public void setStepSize(float stepSize) {
    dirtyConfig = true;
    super.setStepSize(stepSize);
  }

  @Override
  public void setThumbWidth(@IntRange(from = 0) @Px int width) {
    super.setThumbWidth(thumbWidth);
  }

  @Override
  public void setThumbHeight(int height) {
    if (thumbDrawable != null) {
      thumbDrawable.setBounds(0, 0, thumbWidthAnim, height);
    }
    super.setThumbHeight(height);
  }

  private void drawThumb(@NonNull Canvas canvas, int yCenter) {
    canvas.save();
    canvas.translate(
        getTrackSidePadding()
            + normalizedValueAnim * getTrackWidth()
            - (thumbDrawable.getBounds().width() / 2f),
        yCenter - (thumbDrawable.getBounds().height() / 2f));
    thumbDrawable.draw(canvas);
    canvas.restore();
  }

  private void drawInactiveTrack(@NonNull Canvas canvas, int yCenter) {
    int trackWidth = getTrackWidth();
    int trackHeight = getTrackHeight();
    int trackSidePadding = getTrackSidePadding();
    int thumbTrackGapSize = getThumbTrackGapSize();

    float[] activeRange = getActiveRange();
    float left = trackSidePadding + activeRange[1] * trackWidth + thumbWidthAnim / 2f;

    if (thumbTrackGapSize > 0) {
      left += thumbTrackGapSize;
      trackRect.set(
          left,
          yCenter - trackHeight / 2f,
          trackSidePadding + trackWidth + trackHeight / 2f,
          yCenter + trackHeight / 2f
      );
      float thumbPosition = trackSidePadding + activeRange[1] * trackWidth;
      clipRect.set(
          thumbPosition + thumbTrackGapSize + thumbWidthAnim / 2f,
          yCenter - trackHeight / 2f,
          getTrackSidePadding() + getTrackWidth() + trackHeight / 2f,
          yCenter + trackHeight / 2f
      );
      boolean isThumbAtEnd = activeRange[1] * trackWidth == trackWidth;
      if (!isThumbAtEnd) {
        updateTrack(canvas, inactiveTrackPaint, FullCornerDirection.RIGHT);
      }
    } else {
      inactiveTrackPaint.setStyle(Style.STROKE);
      inactiveTrackPaint.setStrokeCap(Cap.ROUND);
      canvas.drawLine(
          left, yCenter, trackSidePadding + trackWidth, yCenter, inactiveTrackPaint
      );
    }
  }

  private void drawActiveTrack(@NonNull Canvas canvas, int yCenter) {
    int trackWidth = getTrackWidth();
    int trackHeight = getTrackHeight();
    int trackSidePadding = getTrackSidePadding();
    int thumbTrackGapSize = getThumbTrackGapSize();

    float[] activeRange = getActiveRange();
    float left = trackSidePadding + activeRange[0] * trackWidth;
    float right = trackSidePadding + activeRange[1] * trackWidth - thumbWidthAnim / 2f;

    if (thumbTrackGapSize > 0) {
      FullCornerDirection direction;
      direction = isRtl() ? FullCornerDirection.RIGHT : FullCornerDirection.LEFT;

      if (isRtl()) { // Swap left right
        float temp = left;
        left = right;
        right = temp;
      }

      switch (direction) {
        case LEFT:
          left -= trackHeight / 2f;
          right -= thumbTrackGapSize;
          break;
        case RIGHT:
          left += thumbTrackGapSize;
          right += trackHeight / 2f;
          break;
        default:
          // fall through
      }
      float top = yCenter - trackHeight / 2f;
      float bottom = yCenter + trackHeight / 2f;
      trackRect.set(left, top, right, bottom);

      float thumbPosition = trackSidePadding + activeRange[1] * trackWidth;
      clipRect.set(
          left,
          yCenter - trackHeight / 2f,
          thumbPosition - thumbWidthAnim / 2f - thumbTrackGapSize,
          yCenter + trackHeight / 2f
      );

      boolean isThumbAtStart = activeRange[1] * trackWidth == 0;
      if (!isThumbAtStart) {
        // Only draw active track if thumb is not at start
        // Else the thumb and gaps won't cover the track entirely
        updateTrack(canvas, activeTrackPaint, direction);
      }
    } else {
      activeTrackPaint.setStyle(Style.STROKE);
      activeTrackPaint.setStrokeCap(Cap.ROUND);
      canvas.drawLine(left, yCenter, right, yCenter, activeTrackPaint);
    }
  }

  private void updateTrack(Canvas canvas, Paint paint, FullCornerDirection direction) {
    int trackHeight = getTrackHeight();
    int trackInsideCornerSize = getTrackInsideCornerSize();
    float leftCornerSize = trackHeight / 2f;
    float rightCornerSize = trackHeight / 2f;
    switch (direction) {
      case BOTH:
        break;
      case LEFT:
        rightCornerSize = trackInsideCornerSize;
        break;
      case RIGHT:
        leftCornerSize = trackInsideCornerSize;
        break;
      case NONE:
        leftCornerSize = trackInsideCornerSize;
        rightCornerSize = trackInsideCornerSize;
        break;
    }

    // Draw the track
    paint.setStyle(Style.FILL);
    paint.setStrokeCap(Cap.BUTT);
    paint.setAntiAlias(true);

    trackPath.reset();
    if (trackRect.width() >= leftCornerSize + rightCornerSize) {
      // Fills one rounded rectangle.
      trackPath.addRoundRect(trackRect, getCornerRadii(leftCornerSize, rightCornerSize), Direction.CW);
      canvas.drawPath(trackPath, paint);
    } else {
      // Clips the canvas and draws the fully rounded track.
      float minCornerSize = Math.min(leftCornerSize, rightCornerSize);
      float maxCornerSize = Math.max(leftCornerSize, rightCornerSize);
      canvas.save();
      // Clips the canvas using the current bounds with the smaller corner size.
      trackPath.addRoundRect(trackRect, minCornerSize, minCornerSize, Direction.CW);
      canvas.clipPath(trackPath);
      // Then draws a rectangle with the minimum width for full corners.
      switch (direction) {
        case LEFT:
          cornerRect.set(trackRect.left, trackRect.top, trackRect.left + 2 * maxCornerSize, trackRect.bottom);
          break;
        case RIGHT:
          cornerRect.set(trackRect.right - 2 * maxCornerSize, trackRect.top, trackRect.right, trackRect.bottom);
          break;
        default:
          cornerRect.set(
              trackRect.centerX() - maxCornerSize,
              trackRect.top,
              trackRect.centerX() + maxCornerSize,
              trackRect.bottom);
      }
      canvas.drawRoundRect(cornerRect, maxCornerSize, maxCornerSize, paint);
      canvas.restore();
    }
  }

  private float[] getCornerRadii(float leftSide, float rightSide) {
    return new float[] {
        leftSide, leftSide,
        rightSide, rightSide,
        rightSide, rightSide,
        leftSide, leftSide
    };
  }

  private void maybeDrawTicks(@NonNull Canvas canvas) {
    if (!isTickVisible() || getStepSize() <= 0.0f) {
      return;
    }

    float[] activeRange = getActiveRange();
    int leftPivotIndex = pivotIndex(ticksCoordinates, activeRange[0]);
    int rightPivotIndex = pivotIndex(ticksCoordinates, activeRange[1]);

    canvas.save();
    int trackHeight = getTrackHeight();
    int trackCenter = calculateTrackCenter();
    int gapSize = getThumbTrackGapSize();
    float thumbPosition = getTrackSidePadding() + activeRange[1] * getTrackWidth();
    clipRect.set(
        getTrackSidePadding() - trackHeight / 2f,
        trackCenter - trackHeight / 2f,
        thumbPosition - thumbWidthAnim / 2f - gapSize,
        trackCenter + trackHeight / 2f
    );
    canvas.clipRect(clipRect);

    // Draw active ticks.
    canvas.drawPoints(
        ticksCoordinates,
        leftPivotIndex * 2,
        rightPivotIndex * 2 - leftPivotIndex * 2,
        activeTicksPaint
    );
    canvas.restore();

    int length = ticksCoordinates.length - rightPivotIndex * 2;
    if (shouldDrawStopIndicator() && length > 0) {
      length -= 2; // reduce length so that the last tick is not drawn
    }

    canvas.save();
    clipRect.set(
        thumbPosition + gapSize + thumbWidthAnim / 2f,
        trackCenter - trackHeight / 2f,
        getTrackSidePadding() + getTrackWidth() + trackHeight / 2f,
        trackCenter + trackHeight / 2f
    );
    canvas.clipRect(clipRect);

    // Draw inactive ticks to the right of the thumb.
    canvas.drawPoints(
        ticksCoordinates,
        rightPivotIndex * 2,
        length,
        inactiveTicksPaint
    );

    canvas.restore();
  }

  private static int pivotIndex(float[] coordinates, float position) {
    return (int) Math.ceil((position * (coordinates.length / 2f - 1)));
  }

  private boolean shouldDrawStopIndicator() {
    return getTrackStopIndicatorSize() > 0 && normalizedValueAnim < normalizeValue(getValueTo());
  }

  private void maybeDrawStopIndicator(@NonNull Canvas canvas, int yCenter) {
    // Draw stop indicator at the end of the track.
    if (shouldDrawStopIndicator()) {
      float x = normalizeValue(getValueTo()) * getTrackWidth() + getTrackSidePadding();
      canvas.drawPoint(x, yCenter, stopIndicatorPaint);
    }
  }

  private void updateThumbWidth(boolean dragged, boolean animate) {
    if (thumbWidthAnimator != null) {
      thumbWidthAnimator.cancel();
      thumbWidthAnimator.removeAllUpdateListeners();
      thumbWidthAnimator.removeAllListeners();
    }
    if (thumbDrawable == null) {
      return;
    }
    int thumbWidthNew = dragged ? (int) (thumbWidth * THUMB_WIDTH_PRESSED_RATIO) : thumbWidth;
    if (animate) {
      thumbWidthAnimator = ValueAnimator.ofInt(thumbWidthAnim, thumbWidthNew);
      thumbWidthAnimator.addUpdateListener(animation -> {
        thumbWidthAnim = (int) animation.getAnimatedValue();
        thumbDrawable.setShapeAppearanceModel(
            thumbDrawable.getShapeAppearanceModel().withCornerSize(thumbWidthAnim / 2f)
        );
        thumbDrawable.setBounds(0, 0, thumbWidthAnim, getThumbHeight());
        invalidate();
      });
      thumbWidthAnimator.setDuration(THUMB_WIDTH_ANIM_DURATION);
      thumbWidthAnimator.start();
    } else {
      thumbWidthAnim = thumbWidthNew;
      thumbDrawable.setShapeAppearanceModel(
          thumbDrawable.getShapeAppearanceModel().withCornerSize(thumbWidthAnim / 2f)
      );
      thumbDrawable.setBounds(0, 0, thumbWidthAnim, getThumbHeight());
      invalidate();
    }
  }

  private void updateThumbPosition(float value, boolean animate) {
    if (thumbPositionAnimator != null) {
      thumbPositionAnimator.cancel();
      thumbPositionAnimator.removeAllUpdateListeners();
      thumbPositionAnimator.removeAllListeners();
    }
    float thumbPositionNew = normalizeValue(value);
    long stepSizeDp = UiUtil.dpFromPx(getContext(), getTickInterval(getRealTickCount()));
    if (animate && stepSizeDp > 8) {
      thumbPositionAnimator = ValueAnimator.ofFloat(normalizedValueAnim, thumbPositionNew);
      thumbPositionAnimator.addUpdateListener(animation -> {
        normalizedValueAnim = (float) animation.getAnimatedValue();
        invalidate();
      });
      thumbPositionAnimator.setDuration(Math.min(stepSizeDp * 4L, 200));
      thumbPositionAnimator.start();
    } else {
      normalizedValueAnim = thumbPositionNew;
      invalidate();
    }
  }

  private float normalizeValue(float value) {
    float normalized = (value - getValueFrom()) / (getValueTo() - getValueFrom());
    if (isRtl()) {
      return 1 - normalized;
    }
    return normalized;
  }

  private int calculateTrackCenter() {
    try {
      Method method = BaseSlider.class.getDeclaredMethod("calculateTrackCenter");
      method.setAccessible(true);
      Integer result = (Integer) method.invoke(this);
      return Objects.requireNonNullElse(result, getHeight() / 2);
    } catch (Exception e) {
      return getHeight() / 2;
    }
  }

  private void validateConfigurationIfDirty() {
    try {
      Method method = BaseSlider.class.getDeclaredMethod("validateConfigurationIfDirty");
      method.setAccessible(true);
      method.invoke(this);
      dirtyConfig = false;
    } catch (Exception e) {
      Log.e(TAG, "validateConfigurationIfDirty: ", e);
    }
  }

  private int getFocusedThumbIdx() {
    try {
      Field field = BaseSlider.class.getDeclaredField("focusedThumbIdx");
      field.setAccessible(true);
      Integer result = (Integer) field.get(this);
      return Objects.requireNonNullElse(result, -1);
    } catch (Exception e) {
      Log.e(TAG, "getFocusedThumbIdx: ", e);
      return -1;
    }
  }

  private void maybeCalculateTicksCoordinates() {
    if (getStepSize() <= 0.0f) {
      return;
    }
    validateConfigurationIfDirty();

    int tickCount = getTickCount();
    if (ticksCoordinates == null || ticksCoordinates.length != tickCount * 2) {
      ticksCoordinates = new float[tickCount * 2];
    }
    float interval = getTickInterval(tickCount);
    for (int i = 0; i < tickCount * 2; i += 2) {
      ticksCoordinates[i] = getTrackSidePadding() + i / 2f * interval;
      ticksCoordinates[i + 1] = calculateTrackCenter();
    }
  }

  private int getTickCount() {
    int tickCount = getRealTickCount();
    // Limit the tickCount if they will be too dense.
    while (getTickInterval(tickCount) < minTickSpacing) {
      if (tickCount == tickCount / 2 + 1) {
        break;
      }
      tickCount = tickCount / 2 + 1;
    }
    return tickCount;
  }

  private int getRealTickCount() {
    return (int) ((getValueTo() - getValueFrom()) / getStepSize() + 1);
  }

  private float getTickInterval(int tickCount) {
    return getTrackWidth() / (float) (tickCount - 1);
  }

  @Override
  protected void drawableStateChanged() {
    super.drawableStateChanged();
    if (thumbDrawable.isStateful()) {
      thumbDrawable.setState(getDrawableState());
    }
    inactiveTicksPaint.setColor(getColorForState(getTickInactiveTintList()));
    activeTicksPaint.setColor(getColorForState(getTickActiveTintList()));
    stopIndicatorPaint.setColor(getColorForState(getTrackActiveTintList()));
    activeTrackPaint.setColor(getColorForState(getTrackActiveTintList()));
    inactiveTrackPaint.setColor(getColorForState(getTrackInactiveTintList()));
    for (TooltipDrawable label : labels) {
      if (label.isStateful()) {
        label.setState(getDrawableState());
      }
    }
  }

  @Override
  public void setTickActiveRadius(@IntRange(from = 0) @Px int tickActiveRadius) {
    if (activeTicksPaint != null) {
      activeTicksPaint.setStrokeWidth(tickActiveRadius * 2);
    }
    super.setTickActiveRadius(tickActiveRadius);
  }

  public void setTickActiveTintList(@NonNull ColorStateList tickColor) {
    if (activeTicksPaint != null) {
      activeTicksPaint.setColor(getColorForState(tickColor));
    }
    super.setTickActiveTintList(tickColor);
  }

  @NonNull
  @Override
  public ColorStateList getTickActiveTintList() {
    return new ColorStateList(
        new int[][] {
            new int[] {android.R.attr.state_enabled},
            new int[] {},
        },
        new int[] {
            ResUtil.getColor(getContext(), R.attr.colorOnPrimary, 0.8f),
            ResUtil.getColor(getContext(), R.attr.colorSurface, 0.7f)
        }
    );
  }

  @Override
  public void setTickInactiveRadius(@IntRange(from = 0) @Px int tickInactiveRadius) {
    if (inactiveTicksPaint != null) {
      inactiveTicksPaint.setStrokeWidth(tickInactiveRadius * 2);
    }
    super.setTickInactiveRadius(tickInactiveRadius);
  }

  public void setTickInactiveTintList(@NonNull ColorStateList tickColor) {
    if (inactiveTicksPaint != null) {
      inactiveTicksPaint.setColor(getColorForState(tickColor));
    }
    super.setTickInactiveTintList(tickColor);
  }

  @NonNull
  @Override
  public ColorStateList getTickInactiveTintList() {
    return new ColorStateList(
        new int[][] {
            new int[] {android.R.attr.state_enabled},
            new int[] {},
        },
        new int[] {
            ResUtil.getColor(getContext(), R.attr.colorOnPrimaryContainer, 0.8f),
            ResUtil.getColor(getContext(), R.attr.colorOnSurface, 0.38f)
        }
    );
  }

  public void setTrackStopIndicatorSize(@Px int trackStopIndicatorSize) {
    if (stopIndicatorPaint != null) {
      stopIndicatorPaint.setStrokeWidth(trackStopIndicatorSize);
    }
    super.setTrackStopIndicatorSize(trackStopIndicatorSize);
  }

  public void setTrackActiveTintList(@NonNull ColorStateList trackColor) {
    if (activeTrackPaint != null) {
      activeTrackPaint.setColor(getColorForState(trackColor));
    }
    if (stopIndicatorPaint != null) {
      stopIndicatorPaint.setColor(getColorForState(trackColor));
    }
    super.setTrackActiveTintList(trackColor);
  }

  @NonNull
  @Override
  public ColorStateList getTrackActiveTintList() {
    return new ColorStateList(
        new int[][] {
            new int[] {android.R.attr.state_enabled},
            new int[] {},
        },
        new int[] {
            ResUtil.getColor(getContext(), R.attr.colorPrimary),
            ResUtil.getColor(getContext(), R.attr.colorOnSurface, 0.38f)
        }
    );
  }

  @Override
  public void setTrackInactiveTintList(@NonNull ColorStateList trackColor) {
    if (inactiveTrackPaint != null) {
      inactiveTrackPaint.setColor(getColorForState(trackColor));
    }
    super.setTrackInactiveTintList(trackColor);
  }

  @NonNull
  @Override
  public ColorStateList getTrackInactiveTintList() {
    return new ColorStateList(
        new int[][] {
            new int[] {android.R.attr.state_enabled},
            new int[] {},
        },
        new int[] {
            ResUtil.getColor(getContext(), R.attr.colorPrimaryContainer),
            ResUtil.getColor(getContext(), R.attr.colorOnSurfaceVariant, 0.12f)
        }
    );
  }

  @ColorInt
  private int getColorForState(@NonNull ColorStateList colorStateList) {
    return colorStateList.getColorForState(getDrawableState(), colorStateList.getDefaultColor());
  }

  private float[] getActiveRange() {
    float left = normalizeValue(getValueFrom());
    float right = normalizedValueAnim;
    // In RTL we draw things in reverse, so swap the left and right range values
    return isRtl() ? new float[] {right, left} : new float[] {left, right};
  }

  private void ensureLabelsAdded() {
    if (getLabelBehavior() == LABEL_GONE) {
      // If the label shouldn't be drawn we can skip this.
      return;
    }

    // If the labels are not animating in, start an animator to show them. ensureLabelsAdded will
    // be called multiple times by BaseSlider's draw method, making this check necessary to avoid
    // creating and starting an animator for each draw call.
    if (!labelsAreAnimatedIn) {
      labelsAreAnimatedIn = true;
      labelsInAnimator = createLabelAnimator(true);
      labelsOutAnimator = null;
      labelsInAnimator.start();
    }

    Iterator<TooltipDrawable> labelItr = labels.iterator();
    for (int i = 0; i < getValues().size() && labelItr.hasNext(); i++) {
      if (i == getFocusedThumbIdx()) {
        // We position the focused thumb last so it's displayed on top, so skip it for now.
        continue;
      }
      setValueForLabel(labelItr.next(), getValues().get(i));
    }

    if (!labelItr.hasNext()) {
      throw new IllegalStateException(
          String.format(
              "Not enough labels(%d) to display all the values(%d)",
              labels.size(), getValues().size())
      );
    }
    // Now set the label for the focused thumb so it's on top.
    setValueForLabel(labelItr.next(), getValues().get(getFocusedThumbIdx()));
  }

  private ValueAnimator createLabelAnimator(boolean enter) {
    float startFraction = enter ? 0F : 1F;
    // Update the start fraction to the current animated value of the label, if any.
    startFraction = getAnimatorCurrentValueOrDefault(
        enter ? labelsOutAnimator : labelsInAnimator, startFraction
    );
    float endFraction = enter ? 1F : 0F;
    ValueAnimator animator = ValueAnimator.ofFloat(startFraction, endFraction);
    int duration;
    TimeInterpolator interpolator;
    if (enter) {
      duration = 250;
      interpolator = new DecelerateInterpolator();
    } else {
      duration = 200;
      interpolator = new FastOutLinearInInterpolator();
    }
    animator.setDuration(duration);
    animator.setInterpolator(interpolator);
    animator.addUpdateListener(animation -> {
      float fraction = (float) animation.getAnimatedValue();
      for (TooltipDrawable label : labels) {
        label.setRevealFraction(fraction);
      }
      // Ensure the labels are redrawn even if the slider has stopped moving
      postInvalidateOnAnimation();
    });
    return animator;
  }

  /**
   * A helper method to get the current animated value of a {@link ValueAnimator}. If the target
   * animator is null or not running, return the default value provided.
   */
  private static float getAnimatorCurrentValueOrDefault(
      ValueAnimator animator, float defaultValue
  ) {
    // If the in animation is interrupting the out animation, attempt to smoothly interrupt by
    // getting the current value of the out animator.
    if (animator != null && animator.isRunning()) {
      float value = (float) animator.getAnimatedValue();
      animator.cancel();
      return value;
    }

    return defaultValue;
  }

  private void ensureLabelsRemoved() {
    // If the labels are animated in or in the process of animating in, create and start a new
    // animator to animate out the labels and remove them once the animation ends.
    if (labelsAreAnimatedIn) {
      labelsAreAnimatedIn = false;
      labelsOutAnimator = createLabelAnimator(false);
      labelsInAnimator = null;
      labelsOutAnimator.addListener(
          new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
              super.onAnimationEnd(animation);
              ViewOverlay contentViewOverlay = getContentViewOverlay();
              for (TooltipDrawable label : labels) {
                contentViewOverlay.remove(label);
              }
            }
          });
      labelsOutAnimator.start();
    }
  }

  private boolean shouldAlwaysShowLabel() {
    return getLabelBehavior() == LABEL_VISIBLE;
  }

  private void createLabelPool() {
    if (labels == null) {
      return;
    }
    // If there's not enough labels, add more.
    while (labels.size() < getValues().size()) {
      // Because there's currently no way to copy the TooltipDrawable we use this to make more
      // if more thumbs are added.
      TooltipDrawable tooltipDrawable = TooltipDrawable.createFromAttributes(
          getContext(), null, 0, getLabelStyle()
      );
      labels.add(tooltipDrawable);
      if (isAttachedToWindow()) {
        attachLabelToContentView(tooltipDrawable);
      }
    }
  }

  private void setValueForLabel(TooltipDrawable label, float value) {
    label.setText(formatValue(value));
    positionLabel(label);
    getContentViewOverlay().add(label);
  }

  private void positionLabel(Drawable label) {
    int left = getTrackSidePadding()
        + (int) (normalizedValueAnim * getTrackWidth()) - label.getIntrinsicWidth() / 2;
    int top = calculateTrackCenter() - (getLabelPadding() + getThumbHeight() / 2);
    label.setBounds(
        left, top - label.getIntrinsicHeight(), left + label.getIntrinsicWidth(), top
    );
    // Calculate the difference between the bounds of this view and the bounds of the root view to
    // correctly position this view in the overlay layer.
    Rect rect = new Rect(label.getBounds());
    getContentView().offsetDescendantRectToMyCoords(this, rect);
    label.setBounds(rect);
  }

  private void attachLabelToContentView(TooltipDrawable label) {
    label.setRelativeToView(getContentView());
  }

  private void detachLabelFromContentView(TooltipDrawable label) {
    ViewOverlay contentViewOverlay = getContentViewOverlay();
    if (contentViewOverlay != null) {
      contentViewOverlay.remove(label);
      label.detachView(getContentView());
    }
  }

  private String formatValue(float value) {
    if (hasLabelFormatter()) {
      try {
        Field formatterField = BaseSlider.class.getDeclaredField("formatter");
        formatterField.setAccessible(true);
        Object result = formatterField.get(this);
        if (result instanceof LabelFormatter) {
          return ((LabelFormatter) result).getFormattedValue(value);
        }
      } catch (Exception ignore) {}
    }
    return String.format((int) value == value ? "%.0f" : "%.2f", value);
  }

  /** Returns the content view that is the parent of the provided view. */
  public ViewGroup getContentView() {
    View rootView = getRootView();
    ViewGroup contentView = rootView.findViewById(android.R.id.content);
    if (contentView != null) {
      return contentView;
    }
    // Account for edge cases: Parent's parent can be null without ever having found
    // android.R.id.content (e.g. if view is in an overlay during a transition).
    // Additionally, sometimes parent's parent is neither a ViewGroup nor a View (e.g. if view
    // is in a PopupWindow).
    if (rootView instanceof ViewGroup) {
      return (ViewGroup) rootView;
    }
    return null;
  }

  private ViewOverlay getContentViewOverlay() {
    return getContentView().getOverlay();
  }

  private int getLabelPadding() {
    try {
      Field someIntField = BaseSlider.class.getDeclaredField("labelPadding");
      someIntField.setAccessible(true);
      Object result = someIntField.getInt(this);
      return (int) result;
    } catch (Exception e) {
      return 0;
    }
  }

  private int getLabelStyle() {
    try {
      Field someIntField = BaseSlider.class.getDeclaredField("labelStyle");
      someIntField.setAccessible(true);
      Object result = someIntField.getInt(this);
      return (int) result;
    } catch (Exception e) {
      return -1;
    }
  }
}