package xyz.zedler.patrick.tack.fragment;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import xyz.zedler.patrick.tack.Constants;
import xyz.zedler.patrick.tack.Constants.ACTION;
import xyz.zedler.patrick.tack.Constants.DEF;
import xyz.zedler.patrick.tack.Constants.EXTRA;
import xyz.zedler.patrick.tack.Constants.PREF;
import xyz.zedler.patrick.tack.Constants.TICK_TYPE;
import xyz.zedler.patrick.tack.R;
import xyz.zedler.patrick.tack.activity.MainActivity;
import xyz.zedler.patrick.tack.activity.ShortcutActivity;
import xyz.zedler.patrick.tack.behavior.ScrollBehavior;
import xyz.zedler.patrick.tack.behavior.SystemBarBehavior;
import xyz.zedler.patrick.tack.databinding.FragmentMainAppBinding;
import xyz.zedler.patrick.tack.service.MetronomeService.MetronomeListener;
import xyz.zedler.patrick.tack.util.DialogUtil;
import xyz.zedler.patrick.tack.util.LogoUtil;
import xyz.zedler.patrick.tack.util.MetronomeUtil;
import xyz.zedler.patrick.tack.util.MetronomeUtil.Tick;
import xyz.zedler.patrick.tack.util.ResUtil;
import xyz.zedler.patrick.tack.util.UiUtil;
import xyz.zedler.patrick.tack.util.ViewUtil;
import xyz.zedler.patrick.tack.view.BeatView;
import xyz.zedler.patrick.tack.view.BpmPickerView;

public class MainFragment extends BaseFragment
    implements OnClickListener, MetronomeListener {

  private static final String TAG = MainFragment.class.getSimpleName();

  private FragmentMainAppBinding binding;
  private MainActivity activity;
  private long prevTouchTime;
  private final List<Long> intervals = new ArrayList<>();
  private boolean flashScreen, keepAwake;
  private LogoUtil logoUtil;
  private ValueAnimator fabAnimator;
  private float cornerSizePause, cornerSizePlay, cornerSizeCurrent;
  private int colorFlashNormal, colorFlashStrong, colorFlashSub, colorFlashMuted;
  private DialogUtil dialogUtilGain;
  private List<Integer> bookmarks;

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState
  ) {
    binding = FragmentMainAppBinding.inflate(inflater, container, false);
    return binding.getRoot();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();

    if (fabAnimator != null) {
      fabAnimator.pause();
      fabAnimator.cancel();
    }
    binding = null;
    dialogUtilGain.dismiss();
  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    activity = (MainActivity) requireActivity();

    SystemBarBehavior systemBarBehavior = new SystemBarBehavior(activity);
    systemBarBehavior.setAppBar(binding.appBarMain);
    systemBarBehavior.setContainer(binding.linearMainContainer);
    systemBarBehavior.setUp();

    new ScrollBehavior().setUpScroll(binding.appBarMain, null, true);

    binding.toolbarMain.setOnMenuItemClickListener(item -> {
      int id = item.getItemId();
      if (getViewUtil().isClickDisabled(id)) {
        return false;
      }
      performHapticClick();
      if (id == R.id.action_settings) {
        activity.navigateToFragment(MainFragmentDirections.actionMainToSettings());
      } else if (id == R.id.action_about) {
        activity.navigateToFragment(MainFragmentDirections.actionMainToAbout());
      } else if (id == R.id.action_feedback) {
        activity.showFeedbackBottomSheet();
      } else if (id == R.id.action_recommend) {
        ResUtil.share(activity, R.string.msg_recommend);
      }
      return true;
    });

    logoUtil = new LogoUtil(binding.imageMainLogo);

    flashScreen = getSharedPrefs().getBoolean(PREF.FLASH_SCREEN, DEF.FLASH_SCREEN);
    keepAwake = getSharedPrefs().getBoolean(PREF.KEEP_AWAKE, DEF.KEEP_AWAKE);

    colorFlashNormal = ResUtil.getColorAttr(activity, R.attr.colorPrimary);
    colorFlashStrong = ResUtil.getColorAttr(activity, R.attr.colorError);
    colorFlashSub = ResUtil.getColorAttr(activity, R.attr.colorPrimaryContainer);
    colorFlashMuted = ResUtil.getColorAttr(activity, android.R.attr.colorBackground);

    binding.linearMainBeats.getLayoutTransition().setDuration(Constants.ANIM_DURATION_LONG);
    updateBeats(getSharedPrefs().getString(PREF.BEATS, DEF.BEATS).split(" "));
    binding.linearMainSubs.getLayoutTransition().setDuration(Constants.ANIM_DURATION_LONG);
    updateSubs(getSharedPrefs().getString(PREF.SUBDIVISIONS, DEF.SUBDIVISIONS).split(" "));

    binding.textSwitcherMainTempoTerm.setFactory(() -> {
      TextView textView = new TextView(activity);
      textView.setGravity(Gravity.CENTER_HORIZONTAL);
      textView.setTextSize(
          TypedValue.COMPLEX_UNIT_PX,
          getResources().getDimension(R.dimen.label_text_size)
      );
      textView.setTextColor(ResUtil.getColorAttr(activity, R.attr.colorOnSecondaryContainer));
      return textView;
    });

    binding.bpmPickerMain.setOnRotationListener(new BpmPickerView.OnRotationListener() {
      @Override
      public void onRotate(int bpm) {
        changeTempo(bpm);
      }

      @Override
      public void onRotate(float degrees) {
        binding.circleMain.setRotation(
            binding.circleMain.getRotation() + degrees
        );
      }
    });
    binding.bpmPickerMain.setOnPickListener(new BpmPickerView.OnPickListener() {
      @Override
      public void onPickDown(float x, float y) {
        binding.circleMain.setDragged(true, x, y);
      }

      @Override
      public void onDrag(float x, float y) {
        binding.circleMain.onDrag(x, y);
      }

      @Override
      public void onPickUpOrCancel() {
        binding.circleMain.setDragged(false, 0, 0);
      }
    });

    binding.buttonMainLess.setOnTouchListener(new View.OnTouchListener() {
      private Handler handler;
      private int nextRun = 400;
      private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
          if (isBound()) {
            if (getMetronomeService().getTempo() > 1) {
              changeTempo(-1);
              handler.postDelayed(this, nextRun);
              if (nextRun > 60) {
                nextRun = (int) (nextRun * 0.9);
              }
            } else {
              handler.removeCallbacks(runnable);
              handler = null;
              nextRun = 400;
            }
          }
        }
      };

      @Override
      public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
          case MotionEvent.ACTION_DOWN:
            if (handler != null) {
              return true;
            }
            handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(runnable, ViewConfiguration.getLongPressTimeout());
            break;
          case MotionEvent.ACTION_CANCEL:
          case MotionEvent.ACTION_UP:
            if (handler == null) {
              return true;
            }
            handler.removeCallbacks(runnable);
            handler = null;
            nextRun = 400;
            break;
        }
        return false;
      }
    });

    binding.buttonMainMore.setOnTouchListener(new View.OnTouchListener() {
      private Handler handler;
      private int nextRun = 400;
      private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
          if (isBound()) {
            if (getMetronomeService().getTempo() < 400) {
              changeTempo(1);
              handler.postDelayed(this, nextRun);
              if (nextRun > 60) {
                nextRun = (int) (nextRun * 0.9);
              }
            } else {
              handler.removeCallbacks(runnable);
              handler = null;
              nextRun = 400;
            }
          }
        }
      };

      @Override
      public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
          case MotionEvent.ACTION_DOWN:
            if (handler != null) {
              return true;
            }
            handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(runnable, ViewConfiguration.getLongPressTimeout());
            break;
          case MotionEvent.ACTION_CANCEL:
          case MotionEvent.ACTION_UP:
            if (handler == null) {
              return true;
            }
            handler.removeCallbacks(runnable);
            handler = null;
            nextRun = 400;
            break;
        }
        return false;
      }
    });

    binding.buttonMainTempoTap.setOnTouchListener((v, event) -> {
      if (event.getAction() != MotionEvent.ACTION_DOWN) {
        return false;
      }
      ViewUtil.startIcon(binding.buttonMainTempoTap.getIcon());

      long interval = System.currentTimeMillis() - prevTouchTime;
      if (prevTouchTime > 0 && interval <= 5000) {
        while (intervals.size() >= 10) {
          intervals.remove(0);
        }
        intervals.add(System.currentTimeMillis() - prevTouchTime);
        if (intervals.size() > 1) {
          long intervalAverage;
          long sum = 0L;
          for (long e : intervals) {
            sum += e;
          }
          intervalAverage = (long) ((double) sum / intervals.size());
          if (isBoundOrShowWarning()) {
            setTempo((int) (60000 / intervalAverage));
          }
        }
      }
      prevTouchTime = System.currentTimeMillis();

      performHapticHeavyClick();
      return true;
    });

    boolean alwaysVibrate = getSharedPrefs().getBoolean(PREF.ALWAYS_VIBRATE, DEF.ALWAYS_VIBRATE);
    if (getSharedPrefs().getBoolean(PREF.BEAT_MODE_VIBRATE, DEF.BEAT_MODE_VIBRATE)) {
      binding.buttonMainBeatMode.setIconResource(
          alwaysVibrate
              ? R.drawable.ic_round_volume_off_to_volume_on_anim
              : R.drawable.ic_round_vibrate_to_volume_anim
      );
    } else {
      binding.buttonMainBeatMode.setIconResource(
          alwaysVibrate
              ? R.drawable.ic_round_volume_on_to_volume_off_anim
              : R.drawable.ic_round_volume_to_vibrate_anim
      );
    }

    setButtonStates();

    String prefBookmarks = getSharedPrefs().getString(PREF.BOOKMARKS, null);
    List<String> bookmarksArray;
    if (prefBookmarks != null) {
      bookmarksArray = Arrays.asList(prefBookmarks.split(","));
    } else {
      bookmarksArray = new ArrayList<>();
    }
    bookmarks = new ArrayList<>(bookmarksArray.size());
    for (int i = 0; i < bookmarksArray.size(); i++) {
      if (!bookmarksArray.get(i).isEmpty()) {
        bookmarks.add(Integer.parseInt(bookmarksArray.get(i)));
      }
    }
    for (int i = 0; i < bookmarks.size(); i++) {
      binding.chipGroupMainBookmarks.addView(getBookmarkChip(bookmarks.get(i)));
    }

    ViewUtil.resetAnimatedIcon(binding.fabMainPlayPause);
    binding.fabMainPlayPause.setImageResource(R.drawable.ic_round_play_to_pause_anim);
    cornerSizePause = UiUtil.dpToPx(activity, 28);
    cornerSizePlay = UiUtil.dpToPx(activity, 48);
    cornerSizeCurrent = cornerSizePause;

    dialogUtilGain = new DialogUtil(activity, "gain");
    dialogUtilGain.createCaution(
        R.string.msg_gain,
        R.string.msg_gain_description,
        R.string.action_play,
        () -> {
          if (isBound() && !getMetronomeService().isPlaying()) {
            getMetronomeService().start();
          }
        },
        R.string.action_deactivate_gain,
        () -> {
          if (isBound()) {
            getMetronomeService().setGain(0);
            if (!getMetronomeService().isPlaying()) {
              getMetronomeService().start();
            }
          }
        });
    dialogUtilGain.showIfWasShown(savedInstanceState);

    if (isBound()) {
      onMetronomeServiceConnected();
    }

    ViewUtil.setTooltipText(binding.buttonMainAddBeat, R.string.action_add_beat);
    ViewUtil.setTooltipText(binding.buttonMainRemoveBeat, R.string.action_remove_beat);
    ViewUtil.setTooltipText(binding.buttonMainAddSubdivision, R.string.action_add_sub);
    ViewUtil.setTooltipText(binding.buttonMainRemoveSubdivision, R.string.action_remove_sub);
    ViewUtil.setTooltipText(binding.buttonMainBeatMode, R.string.action_beat_mode);

    ViewUtil.setOnClickListeners(
        this,
        binding.buttonMainAddBeat,
        binding.buttonMainRemoveBeat,
        binding.buttonMainAddSubdivision,
        binding.buttonMainRemoveSubdivision,
        binding.buttonMainLess,
        binding.buttonMainMore,
        binding.buttonMainBeatMode,
        binding.buttonMainBookmark,
        binding.buttonMainOptions,
        binding.fabMainPlayPause
    );
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    if (dialogUtilGain != null) {
      dialogUtilGain.saveState(outState);
    }
  }

  public void onMetronomeServiceConnected() {
    if (binding == null) {
      return;
    }
    getMetronomeService().setMetronomeListener(this);

    if (getMetronomeService().isBeatModeVibrate()) {
      binding.buttonMainBeatMode.setIconResource(
          getMetronomeService().isAlwaysVibrate()
              ? R.drawable.ic_round_volume_off_to_volume_on_anim
              : R.drawable.ic_round_vibrate_to_volume_anim
      );
    } else {
      binding.buttonMainBeatMode.setIconResource(
          getMetronomeService().isAlwaysVibrate()
              ? R.drawable.ic_round_volume_on_to_volume_off_anim
              : R.drawable.ic_round_volume_to_vibrate_anim
      );
    }

    updateBeats(getMetronomeService().getBeats());
    updateSubs(getMetronomeService().getSubdivisions());
    refreshBookmarks();

    setTempo(getMetronomeService().getTempo());
    binding.textSwitcherMainTempoTerm.setCurrentText(getMetronomeService().getTempoTerm());

    ViewUtil.resetAnimatedIcon(binding.fabMainPlayPause);
    binding.fabMainPlayPause.setImageResource(
        getMetronomeService().isPlaying()
            ? R.drawable.ic_round_pause
            : R.drawable.ic_round_play_arrow
    );
    updateFabCornerRadius(getMetronomeService().isPlaying(), false);

    UiUtil.keepScreenAwake(activity, keepAwake && getMetronomeService().isPlaying());
  }

  @Override
  public void onMetronomeStart() {
    activity.runOnUiThread(() -> {
      if (binding != null) {
        binding.fabMainPlayPause.setImageResource(R.drawable.ic_round_play_to_pause_anim);
        Drawable fabIcon = binding.fabMainPlayPause.getDrawable();
        if (fabIcon != null) {
          ((Animatable) fabIcon).start();
        }
        updateFabCornerRadius(true, true);
      }
    });
    UiUtil.keepScreenAwake(activity, keepAwake);
  }

  @Override
  public void onMetronomeStop() {
    activity.runOnUiThread(() -> {
      if (binding != null) {
        binding.fabMainPlayPause.setImageResource(R.drawable.ic_round_pause_to_play_anim);
        Drawable icon = binding.fabMainPlayPause.getDrawable();
        if (icon != null) {
          ((Animatable) icon).start();
        }
        updateFabCornerRadius(false, true);
      }
    });
    UiUtil.keepScreenAwake(activity, false);
  }

  @Override
  public void onMetronomeTick(Tick tick) {
    if (!isBound()) {
      return;
    }
    activity.runOnUiThread(() -> {
      if (binding == null) {
        return;
      }
      if (flashScreen) {
        int color;
        switch (tick.type) {
          case TICK_TYPE.STRONG:
            color = colorFlashStrong;
            break;
          case TICK_TYPE.SUB:
            color = colorFlashSub;
            break;
          case TICK_TYPE.MUTED:
            color = colorFlashMuted;
            break;
          default:
            color = colorFlashNormal;
            break;
        }
        binding.coordinatorContainer.setBackgroundColor(color);
        binding.coordinatorContainer.postDelayed(() -> {
          if (binding != null) {
            binding.coordinatorContainer.setBackgroundColor(colorFlashMuted);
          }
        }, 100);
      }
      View beat = binding.linearMainBeats.getChildAt(tick.beat - 1);
      if (beat instanceof BeatView && tick.subdivision == 1) {
        ((BeatView) beat).setTickType(tick.type);
        ((BeatView) beat).beat();
      }
      View subdivision = binding.linearMainSubs.getChildAt(tick.subdivision - 1);
      if (subdivision instanceof BeatView) {
        ((BeatView) subdivision).setTickType(tick.subdivision == 1 ? TICK_TYPE.MUTED : tick.type);
        ((BeatView) subdivision).beat();
      }
      if (tick.subdivision == 1) {
        logoUtil.nextBeat(getMetronomeService().getInterval());
      }
    });
  }

  @Override
  public void onClick(View v) {
    int id = v.getId();
    if (!isBoundOrShowWarning()) {
      return;
    }
    if (id == R.id.button_main_add_beat) {
      performHapticClick();
      boolean success = getMetronomeService().addBeat();
      if (success) {
        BeatView beatView = new BeatView(activity);
        beatView.setIndex(binding.linearMainBeats.getChildCount());
        beatView.setOnClickListener(beat -> {
          performHapticClick();
          getMetronomeService().setBeat(beatView.getIndex(), beatView.nextTickType());
        });
        binding.linearMainBeats.addView(beatView);
        updateBeatControls();
      }
    } else if (id == R.id.button_main_remove_beat) {
      performHapticClick();
      boolean success = getMetronomeService().removeBeat();
      if (success) {
        binding.linearMainBeats.removeViewAt(binding.linearMainBeats.getChildCount() - 1);
        updateBeatControls();
      }
    } else if (id == R.id.button_main_add_subdivision) {
      performHapticClick();
      boolean success = getMetronomeService().addSubdivision();
      if (success) {
        BeatView beatView = new BeatView(activity);
        beatView.setIsSubdivision(true);
        beatView.setIndex(binding.linearMainSubs.getChildCount());
        beatView.setOnClickListener(subdivision -> {
          performHapticClick();
          getMetronomeService().setSubdivision(beatView.getIndex(), beatView.nextTickType());
        });
        binding.linearMainSubs.addView(beatView);
        updateSubControls();
      }
    } else if (id == R.id.button_main_remove_subdivision) {
      performHapticClick();
      boolean success = getMetronomeService().removeSubdivision();
      if (success) {
        binding.linearMainSubs.removeViewAt(binding.linearMainSubs.getChildCount() - 1);
        updateSubControls();
      }
    } else if (id == R.id.fab_main_play_pause) {
      if (getMetronomeService().isPlaying()) {
        performHapticClick();
        getMetronomeService().stop();
      } else {
        if (getMetronomeService().getGain() > 0) {
          dialogUtilGain.show();
        } else {
          getMetronomeService().start();
        }
        performHapticClick();
      }
    } else if (id == R.id.button_main_less) {
      ViewUtil.startIcon(binding.buttonMainLess.getIcon());
      changeTempo(-1);
    } else if (id == R.id.button_main_more) {
      ViewUtil.startIcon(binding.buttonMainMore.getIcon());
      changeTempo(1);
    } else if (id == R.id.button_main_beat_mode) {
      boolean beatModeVibrateNew = !getMetronomeService().isBeatModeVibrate();
      if (beatModeVibrateNew && !activity.getHapticUtil().hasVibrator()) {
        showSnackbar(
            activity.getSnackbar(R.string.msg_vibration_unavailable, Snackbar.LENGTH_SHORT)
        );
        return;
      }
      if (!beatModeVibrateNew) {
        performHapticClick();
      }
      getMetronomeService().setBeatModeVibrate(beatModeVibrateNew);
      if (beatModeVibrateNew) {
        performHapticClick();
      }
      ViewUtil.startIcon(binding.buttonMainBeatMode.getIcon());
      new Handler(Looper.getMainLooper()).postDelayed(() -> {
        if (beatModeVibrateNew) {
          binding.buttonMainBeatMode.setIconResource(
              getMetronomeService().isAlwaysVibrate()
                  ? R.drawable.ic_round_volume_off_to_volume_on_anim
                  : R.drawable.ic_round_vibrate_to_volume_anim
          );
        } else {
          binding.buttonMainBeatMode.setIconResource(
              getMetronomeService().isAlwaysVibrate()
                  ? R.drawable.ic_round_volume_on_to_volume_off_anim
                  : R.drawable.ic_round_volume_to_vibrate_anim
          );
        }
      }, 300);
    } else if (id == R.id.button_main_bookmark) {
      ViewUtil.startIcon(binding.buttonMainBookmark.getIcon());
      performHapticClick();
      if (bookmarks.size() < 3 && !bookmarks.contains(getMetronomeService().getTempo())) {
        binding.chipGroupMainBookmarks.addView(getBookmarkChip(getMetronomeService().getTempo()));
        bookmarks.add(getMetronomeService().getTempo());
        updateBookmarks();
        refreshBookmarks();
      } else if (bookmarks.size() >= 3) {
        Snackbar snackbar = activity.getSnackbar(
            R.string.msg_bookmarks_max, Snackbar.LENGTH_SHORT
        );
        snackbar.setAction(
            getString(R.string.action_clear_all),
            view -> {
              binding.chipGroupMainBookmarks.removeAllViews();
              bookmarks.clear();
              updateBookmarks();
              refreshBookmarks();
            }
        );
        showSnackbar(snackbar);
      }
    } else if (id == R.id.button_main_options) {
      performHapticClick();
    }
  }

  public void onBpmChanged(int bpm) {
    if (isBound()) {
      binding.textMainBpm.setText(String.valueOf(bpm));
      refreshBookmarks();
    }
  }

  private void updateBeats(String[] beats) {
    if (isBound()
        && binding.linearMainBeats.getChildCount() == getMetronomeService().getBeatsCount()) {
      return;
    }
    binding.linearMainBeats.removeAllViews();
    for (int i = 0; i < beats.length; i++) {
      String tickType = beats[i];
      BeatView beatView = new BeatView(activity);
      beatView.setTickType(tickType);
      beatView.setIndex(i);
      beatView.setOnClickListener(beat -> {
        performHapticClick();
        getMetronomeService().setBeat(beatView.getIndex(), beatView.nextTickType());
      });
      binding.linearMainBeats.addView(beatView);
    }
    updateBeatControls();
  }

  private void updateBeatControls() {
    if (isBound()) {
      int beats = getMetronomeService().getBeatsCount();
      binding.buttonMainAddBeat.setEnabled(beats < MetronomeUtil.BEATS_MAX);
      binding.buttonMainRemoveBeat.setEnabled(beats > 1);
    }
  }

  private void updateSubs(String[] subdivision) {
    if (isBound()
        && binding.linearMainSubs.getChildCount() == getMetronomeService().getSubsCount()) {
      return;
    }
    binding.linearMainSubs.removeAllViews();
    for (int i = 0; i < subdivision.length; i++) {
      String tickType = subdivision[i];
      BeatView beatView = new BeatView(activity);
      beatView.setIsSubdivision(true);
      beatView.setTickType(i == 0 ? TICK_TYPE.MUTED : tickType);
      beatView.setIndex(i);
      beatView.setOnClickListener(beat -> {
        performHapticClick();
        getMetronomeService().setSubdivision(beatView.getIndex(), beatView.nextTickType());
      });
      binding.linearMainSubs.addView(beatView);
    }
    updateSubControls();
  }

  private void updateSubControls() {
    if (isBound()) {
      int subdivisions = getMetronomeService().getSubsCount();
      binding.buttonMainAddSubdivision.setEnabled(subdivisions < MetronomeUtil.SUBS_MAX);
      binding.buttonMainRemoveSubdivision.setEnabled(subdivisions > 1);
    }
  }

  private Chip getBookmarkChip(int tempo) {
    Chip chip = new Chip(activity);
    chip.setCheckable(false);
    chip.setChipIconResource(R.drawable.ic_round_audiotrack_anim);
    chip.setCloseIconResource(R.drawable.ic_round_cancel);
    chip.setCloseIconVisible(true);
    chip.setOnCloseIconClickListener(v -> {
      performHapticClick();
      binding.chipGroupMainBookmarks.removeView(chip);
      bookmarks.remove((Integer) tempo); // Integer cast required
      updateBookmarks();
      refreshBookmarks();
    });
    chip.setStateListAnimator(null);
    chip.setText(String.valueOf(tempo));
    chip.setTextAppearance(R.style.TextAppearance_Tack_LabelLarge);
    chip.setOnClickListener(v -> {
      ViewUtil.startIcon(chip.getChipIcon());
      setTempo(tempo);
    });
    return chip;
  }

  private void updateBookmarks() {
    StringBuilder stringBuilder = new StringBuilder();
    for (Integer bpm : bookmarks) {
      stringBuilder.append(bpm).append(",");
    }
    getSharedPrefs().edit().putString(PREF.BOOKMARKS, stringBuilder.toString()).apply();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
      Collections.sort(bookmarks);
      ShortcutManager manager = (ShortcutManager) activity.getSystemService(
          Context.SHORTCUT_SERVICE);
      if (manager != null) {
        List<ShortcutInfo> shortcuts = new ArrayList<>();
        for (int bpm : bookmarks) {
          shortcuts.add(
              new ShortcutInfo.Builder(activity, String.valueOf(bpm))
                  .setShortLabel(getString(R.string.label_bpm_number, String.valueOf(bpm)))
                  .setIcon(Icon.createWithResource(activity, R.mipmap.ic_shortcut))
                  .setIntent(new Intent(activity, ShortcutActivity.class)
                      .setAction(ACTION.START)
                      .putExtra(EXTRA.TEMPO, bpm)
                      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                  .build()
          );
        }
        manager.setDynamicShortcuts(shortcuts);
      }
    }
  }

  private void refreshBookmarks() {
    if (!isBound()) {
      return;
    }
    binding.buttonMainBookmark.setEnabled(!bookmarks.contains(getMetronomeService().getTempo()));
    for (int i = 0; i < binding.chipGroupMainBookmarks.getChildCount(); i++) {
      Chip chip = (Chip) binding.chipGroupMainBookmarks.getChildAt(i);
      if (chip == null) {
        continue;
      }
      boolean isActive = Integer.parseInt(chip.getText().toString())
          == getMetronomeService().getTempo();
      int colorBg = isActive
          ? ResUtil.getColorAttr(activity, R.attr.colorTertiaryContainer)
          : Color.TRANSPARENT;
      int colorIcon = ResUtil.getColorAttr(
          activity, isActive ? R.attr.colorOnTertiaryContainer : R.attr.colorOnSurfaceVariant
      );
      int colorStroke = ResUtil.getColorAttr(
          activity, isActive ? R.attr.colorTertiary : R.attr.colorOutline
      );
      chip.setChipBackgroundColor(ColorStateList.valueOf(colorBg));
      chip.setChipIconTint(ColorStateList.valueOf(colorIcon));
      chip.setChipStrokeColor(ColorStateList.valueOf(colorStroke));
    }
  }

  private void changeTempo(int change) {
    if (isBound()) {
      int bpmNew = getMetronomeService().getTempo() + change;
      setTempo(bpmNew);
      if (bpmNew >= MetronomeUtil.TEMPO_MIN && bpmNew <= MetronomeUtil.TEMPO_MAX) {
        performHapticTick();
      }
    }
  }

  private void setTempo(int bpm) {
    if (isBound()) {
      int tempoOld = getMetronomeService().getTempo();
      String termOld = getMetronomeService().getTempoTerm();
      getMetronomeService().setTempo(
          Math.min(Math.max(bpm, MetronomeUtil.TEMPO_MIN), MetronomeUtil.TEMPO_MAX)
      );
      binding.textMainBpm.setText(String.valueOf(getMetronomeService().getTempo()));
      String termNew = getMetronomeService().getTempoTerm();
      if (!termNew.equals(termOld)) {
        boolean isFaster = getMetronomeService().getTempo() > tempoOld;
        binding.textSwitcherMainTempoTerm.setInAnimation(
            activity, isFaster ? R.anim.tempo_term_open_enter : R.anim.tempo_term_close_enter
        );
        binding.textSwitcherMainTempoTerm.setOutAnimation(
            activity, isFaster ? R.anim.tempo_term_open_exit : R.anim.tempo_term_close_exit
        );
        binding.textSwitcherMainTempoTerm.setText(termNew);
      }
      refreshBookmarks();
      setButtonStates();
    }
  }

  private void setButtonStates() {
    if (isBound()) {
      int bpm = getMetronomeService().getTempo();
      binding.buttonMainLess.setEnabled(bpm > 1);
      binding.buttonMainMore.setEnabled(bpm < MetronomeUtil.TEMPO_MAX);
    }
  }

  private void updateFabCornerRadius(boolean playing, boolean animated) {
    if (fabAnimator != null) {
      fabAnimator.pause();
      fabAnimator.cancel();
      fabAnimator = null;
    }
    float cornerSizeNew = playing ? cornerSizePlay : cornerSizePause;
    if (animated) {
      fabAnimator = ValueAnimator.ofFloat(cornerSizeCurrent, cornerSizeNew);
      fabAnimator.addUpdateListener(animation -> {
        cornerSizeCurrent = (float) animation.getAnimatedValue();
        binding.fabMainPlayPause.setShapeAppearanceModel(
            binding.fabMainPlayPause.getShapeAppearanceModel().withCornerSize(cornerSizeCurrent)
        );
      });
      fabAnimator.setInterpolator(new FastOutSlowInInterpolator());
      fabAnimator.setDuration(300);
      fabAnimator.start();
    } else {
      cornerSizeCurrent = cornerSizeNew;
      binding.fabMainPlayPause.setShapeAppearanceModel(
          binding.fabMainPlayPause.getShapeAppearanceModel().withCornerSize(cornerSizeNew)
      );
    }
  }

  private boolean isBoundOrShowWarning() {
    boolean isBound = isBound();
    if (!isBound) {
      showSnackbar(activity.getSnackbar(R.string.msg_connection_lost, Snackbar.LENGTH_SHORT));
    }
    return isBound;
  }

  private void showSnackbar(Snackbar snackbar) {
    snackbar.setAnchorView(binding.fabMainPlayPause);
    activity.showSnackbar(snackbar);
  }
}
