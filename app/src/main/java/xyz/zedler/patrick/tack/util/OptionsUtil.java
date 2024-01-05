package xyz.zedler.patrick.tack.util;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.button.MaterialButtonToggleGroup.OnButtonCheckedListener;
import com.google.android.material.slider.Slider;
import com.google.android.material.slider.Slider.OnChangeListener;
import xyz.zedler.patrick.tack.Constants.UNIT;
import xyz.zedler.patrick.tack.R;
import xyz.zedler.patrick.tack.activity.MainActivity;
import xyz.zedler.patrick.tack.databinding.PartialDialogOptionsBinding;
import xyz.zedler.patrick.tack.databinding.PartialOptionsBinding;
import xyz.zedler.patrick.tack.fragment.MainFragment;

public class OptionsUtil implements OnButtonCheckedListener, OnChangeListener {

  private final MainActivity activity;
  private final MainFragment fragment;
  private final PartialOptionsBinding binding;
  private final boolean useDialog;
  private DialogUtil dialogUtil;
  private PartialDialogOptionsBinding bindingDialog;

  public OptionsUtil(MainActivity activity, MainFragment fragment) {
    this.activity = activity;
    this.fragment = fragment;

    useDialog = !UiUtil.isLandTablet(activity);
    if (useDialog) {
      bindingDialog = PartialDialogOptionsBinding.inflate(activity.getLayoutInflater());
      dialogUtil = new DialogUtil(activity, "options");
    }
    binding = useDialog ? bindingDialog.partialOptions : fragment.getBinding().partialOptions;

    if (useDialog) {
      dialogUtil.createCloseCustom(R.string.title_options, bindingDialog.getRoot());
    }
  }

  public void show() {
    update();
    if (useDialog) {
      dialogUtil.show();
    }
  }

  public void showIfWasShown(@Nullable Bundle state) {
    update();
    if (useDialog) {
      dialogUtil.showIfWasShown(state);
    }
  }

  public void dismiss() {
    if (useDialog) {
      dialogUtil.dismiss();
    }
  }

  public void saveState(@NonNull Bundle outState) {
    if (useDialog && dialogUtil != null) {
      dialogUtil.saveState(outState);
    }
  }

  public void update() {
    if (useDialog) {
      bindingDialog.scrollOptions.scrollTo(0, 0);
    }
    updateCountIn();
    updateIncremental();
    updateTimer();
    updateSwing();
  }

  private void updateCountIn() {
    int countIn = getMetronomeUtil().getCountIn();
    binding.sliderOptionsCountIn.removeOnChangeListener(this);
    binding.sliderOptionsCountIn.setValue(countIn);
    binding.sliderOptionsCountIn.addOnChangeListener(this);
    binding.sliderOptionsCountIn.setLabelFormatter(
        value -> activity.getResources().getQuantityString(
            R.plurals.options_unit_bars, (int) value, (int) value
        )
    );
    String barsQuantity = activity.getResources().getQuantityString(
        R.plurals.options_unit_bars, countIn, countIn
    );
    if (getMetronomeUtil().isCountInActive()) {
      binding.textOptionsCountIn.setText(
          activity.getString(R.string.options_count_in_description, barsQuantity)
      );
    } else {
      binding.textOptionsCountIn.setText(activity.getString(R.string.options_inactive));
    }
  }

  private void updateIncremental() {
    int incrementalAmount = getMetronomeUtil().getIncrementalAmount();
    boolean incrementalIncrease = getMetronomeUtil().getIncrementalIncrease();
    boolean isIncrementalActive = getMetronomeUtil().isIncrementalActive();
    if (isIncrementalActive) {
      binding.textOptionsIncrementalAmount.setText(activity.getString(
          incrementalIncrease
              ? R.string.options_incremental_amount_increase
              : R.string.options_incremental_amount_decrease,
          incrementalAmount
      ));
    } else {
      binding.textOptionsIncrementalAmount.setText(activity.getString(R.string.options_inactive));
    }

    binding.sliderOptionsIncrementalAmount.removeOnChangeListener(this);
    binding.sliderOptionsIncrementalAmount.setValue(incrementalAmount);
    binding.sliderOptionsIncrementalAmount.addOnChangeListener(this);
    binding.sliderOptionsIncrementalAmount.setLabelFormatter(
        value -> activity.getString(R.string.label_bpm_value, (int) value)
    );

    boolean visibleControls = isIncrementalActive || !useDialog;
    binding.linearMainIncrementalContainer.setVisibility(
        visibleControls ? View.VISIBLE : View.GONE
    );

    binding.toggleOptionsIncrementalDirection.removeOnButtonCheckedListener(this);
    binding.toggleOptionsIncrementalDirection.check(
        incrementalIncrease
            ? R.id.button_options_incremental_increase
            : R.id.button_options_incremental_decrease
    );
    binding.toggleOptionsIncrementalDirection.addOnButtonCheckedListener(this);
    binding.toggleOptionsIncrementalDirection.setEnabled(isIncrementalActive);

    int incrementalInterval = getMetronomeUtil().getIncrementalInterval();
    String incrementalUnit = getMetronomeUtil().getIncrementalUnit();
    int unitResId, checkedId;
    switch (incrementalUnit) {
      case UNIT.SECONDS:
        unitResId = R.plurals.options_unit_seconds;
        checkedId = R.id.button_options_incremental_unit_seconds;
        break;
      case UNIT.MINUTES:
        unitResId = R.plurals.options_unit_minutes;
        checkedId = R.id.button_options_incremental_unit_minutes;
        break;
      default:
        unitResId = R.plurals.options_unit_bars;
        checkedId = R.id.button_options_incremental_unit_bars;
        break;
    }
    String unitQuantity = activity.getResources().getQuantityString(
        unitResId, incrementalInterval, incrementalInterval
    );
    binding.textOptionsIncrementalInterval.setText(
        activity.getString(R.string.options_incremental_interval, unitQuantity)
    );
    binding.textOptionsIncrementalInterval.setAlpha(isIncrementalActive ? 1 : 0.5f);

    binding.sliderOptionsIncrementalInterval.removeOnChangeListener(this);
    binding.sliderOptionsIncrementalInterval.setValue(incrementalInterval);
    binding.sliderOptionsIncrementalInterval.addOnChangeListener(this);
    binding.sliderOptionsIncrementalInterval.setLabelFormatter(value -> {
      int resId;
      switch (incrementalUnit) {
        case UNIT.SECONDS:
          resId = R.plurals.options_unit_seconds;
          break;
        case UNIT.MINUTES:
          resId = R.plurals.options_unit_minutes;
          break;
        default:
          resId = R.plurals.options_unit_bars;
          break;
      }
      int interval = (int) value;
      return activity.getResources().getQuantityString(resId, interval, interval);
    });
    binding.sliderOptionsIncrementalInterval.setEnabled(isIncrementalActive);

    binding.toggleOptionsIncrementalUnit.removeOnButtonCheckedListener(this);
    binding.toggleOptionsIncrementalUnit.check(checkedId);
    binding.toggleOptionsIncrementalUnit.addOnButtonCheckedListener(this);
    binding.toggleOptionsIncrementalUnit.setEnabled(isIncrementalActive);

    // Update timer unit selection, see below for an explanation
    updateTimer();
  }

  private void updateTimer() {
    int timerDuration = getMetronomeUtil().getTimerDuration();
    boolean isTimerActive = getMetronomeUtil().isTimerActive();
    String timerUnit = getMetronomeUtil().getTimerUnit();
    int unitResId, checkedId;
    switch (timerUnit) {
      case UNIT.SECONDS:
        unitResId = R.plurals.options_unit_seconds;
        checkedId = R.id.button_options_timer_unit_seconds;
        break;
      case UNIT.MINUTES:
        unitResId = R.plurals.options_unit_minutes;
        checkedId = R.id.button_options_timer_unit_minutes;
        break;
      default:
        unitResId = R.plurals.options_unit_bars;
        checkedId = R.id.button_options_timer_unit_bars;
        break;
    }
    String unitQuantity = activity.getResources().getQuantityString(
        unitResId, timerDuration, timerDuration
    );
    if (isTimerActive) {
      binding.textOptionsTimerDuration.setText(
          activity.getString(R.string.options_timer_description, unitQuantity)
      );
    } else {
      binding.textOptionsTimerDuration.setText(activity.getString(R.string.options_inactive));
    }

    binding.sliderOptionsTimerDuration.removeOnChangeListener(this);
    binding.sliderOptionsTimerDuration.setValue(timerDuration);
    binding.sliderOptionsTimerDuration.addOnChangeListener(this);
    binding.sliderOptionsTimerDuration.setLabelFormatter(value -> {
      int resId;
      switch (timerUnit) {
        case UNIT.SECONDS:
          resId = R.plurals.options_unit_seconds;
          break;
        case UNIT.MINUTES:
          resId = R.plurals.options_unit_minutes;
          break;
        default:
          resId = R.plurals.options_unit_bars;
          break;
      }
      int interval = (int) value;
      return activity.getResources().getQuantityString(resId, interval, interval);
    });

    boolean visibleControls = isTimerActive || !useDialog;
    binding.linearMainTimerContainer.setVisibility(visibleControls ? View.VISIBLE : View.GONE);

    // Single-selection mode with auto de-selection only effective if buttons enabled
    binding.buttonOptionsTimerUnitBars.setEnabled(true);
    binding.toggleOptionsTimerUnit.removeOnButtonCheckedListener(this);
    binding.toggleOptionsTimerUnit.check(checkedId);
    binding.toggleOptionsTimerUnit.addOnButtonCheckedListener(this);
    binding.toggleOptionsTimerUnit.setEnabled(isTimerActive);

    // Bars unit not supported for timer in connection with incremental tempo change!
    // On tempo changes with units different to bars, beats would not start at bar start
    // Disable bars unit button for timer
    boolean isIncrementalActive = getMetronomeUtil().isIncrementalActive();
    boolean convertToNonBarUnit = timerUnit.equals(UNIT.BARS) && isIncrementalActive;
    binding.buttonOptionsTimerUnitBars.setEnabled(isTimerActive && !isIncrementalActive);
    if (convertToNonBarUnit) {
      long timerInterval = getMetronomeUtil().getTimerInterval();
      int intervalSeconds = (int) (timerInterval / 1000);
      if (intervalSeconds > binding.sliderOptionsTimerDuration.getValueTo()) {
        getMetronomeUtil().setTimerUnit(UNIT.MINUTES);
        getMetronomeUtil().setTimerDuration(intervalSeconds / 60);
      } else {
        getMetronomeUtil().setTimerUnit(UNIT.SECONDS);
        getMetronomeUtil().setTimerDuration(intervalSeconds);
      }
      updateTimer();
    }
    binding.textOptionsTimerUnsupported.setVisibility(
        isTimerActive && isIncrementalActive ? View.VISIBLE : View.GONE
    );
  }

  public void updateSwing() {
    binding.textOptionsSwing.setText(activity.getString(
        getMetronomeUtil().isSwingActive()
            ? R.string.options_swing_description
            : R.string.options_inactive
    ));
    binding.toggleOptionsSwing.removeOnButtonCheckedListener(this);
    if (getMetronomeUtil().isSwing3()) {
      binding.toggleOptionsSwing.check(R.id.button_options_swing_3);
    } else if (getMetronomeUtil().isSwing5()) {
      binding.toggleOptionsSwing.check(R.id.button_options_swing_5);
    } else if (getMetronomeUtil().isSwing7()) {
      binding.toggleOptionsSwing.check(R.id.button_options_swing_7);
    } else {
      binding.toggleOptionsSwing.clearChecked();
    }
    binding.toggleOptionsSwing.addOnButtonCheckedListener(this);
  }

  @Override
  public void onButtonChecked(MaterialButtonToggleGroup group, int checkedId, boolean isChecked) {
    if (!isChecked) {
      return;
    }
    activity.performHapticClick();
    int groupId = group.getId();
    if (groupId == R.id.toggle_options_incremental_direction) {
      getMetronomeUtil().setIncrementalIncrease(
          checkedId == R.id.button_options_incremental_increase
      );
      updateIncremental();
    } else if (groupId == R.id.toggle_options_incremental_unit) {
      if (checkedId == R.id.button_options_incremental_unit_bars) {
        getMetronomeUtil().setIncrementalUnit(UNIT.BARS);
      } else if (checkedId == R.id.button_options_incremental_unit_seconds) {
        getMetronomeUtil().setIncrementalUnit(UNIT.SECONDS);
      } else if (checkedId == R.id.button_options_incremental_unit_minutes) {
        getMetronomeUtil().setIncrementalUnit(UNIT.MINUTES);
      }
      updateIncremental();
    } else if (groupId == R.id.toggle_options_timer_unit) {
      if (checkedId == R.id.button_options_timer_unit_bars) {
        getMetronomeUtil().setTimerUnit(UNIT.BARS);
      } else if (checkedId == R.id.button_options_timer_unit_seconds) {
        getMetronomeUtil().setTimerUnit(UNIT.SECONDS);
      } else if (checkedId == R.id.button_options_timer_unit_minutes) {
        getMetronomeUtil().setTimerUnit(UNIT.MINUTES);
      }
      updateTimer();
      fragment.updateTimerDisplay();
    } else if (groupId == R.id.toggle_options_swing) {
      if (checkedId == R.id.button_options_swing_3) {
        getMetronomeUtil().setSwing3();
      } else if (checkedId == R.id.button_options_swing_5) {
        getMetronomeUtil().setSwing5();
      } else if (checkedId == R.id.button_options_swing_7) {
        getMetronomeUtil().setSwing7();
      }
      updateSwing();
      getMetronomeUtil().setSubdivisionsUsed(true);
      fragment.updateSubs(getMetronomeUtil().getSubdivisions());
      fragment.updateSubControls();
    }
  }

  @Override
  public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
    if (!fromUser) {
      return;
    }
    activity.performHapticTick();
    int id = slider.getId();
    if (id == R.id.slider_options_count_in) {
      getMetronomeUtil().setCountIn((int) value);
      updateCountIn();
    } else if (id == R.id.slider_options_incremental_amount) {
      getMetronomeUtil().setIncrementalAmount((int) value);
      updateIncremental();
    } else if (id == R.id.slider_options_incremental_interval) {
      getMetronomeUtil().setIncrementalInterval((int) value);
      updateIncremental();
    } else if (id == R.id.slider_options_timer_duration) {
      getMetronomeUtil().setTimerDuration((int) value);
      updateTimer();
      fragment.updateTimerControls();
    }
  }

  private MetronomeUtil getMetronomeUtil() {
    return activity.getMetronomeUtil();
  }
}