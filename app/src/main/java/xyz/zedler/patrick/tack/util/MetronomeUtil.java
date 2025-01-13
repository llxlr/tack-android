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

package xyz.zedler.patrick.tack.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.animation.LinearInterpolator;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import xyz.zedler.patrick.tack.Constants;
import xyz.zedler.patrick.tack.Constants.DEF;
import xyz.zedler.patrick.tack.Constants.PREF;
import xyz.zedler.patrick.tack.Constants.TICK_TYPE;
import xyz.zedler.patrick.tack.Constants.UNIT;
import xyz.zedler.patrick.tack.R;

public class MetronomeUtil {

  private static final String TAG = MetronomeUtil.class.getSimpleName();

  private final Context context;
  private final SharedPreferences sharedPrefs;
  private final AudioUtil audioUtil;
  private final HapticUtil hapticUtil;
  private final ShortcutUtil shortcutUtil;
  private final Set<MetronomeListener> listeners = new HashSet<>();
  private final boolean fromService;
  private HandlerThread audioThread, callbackThread;
  private Handler tickHandler, latencyHandler;
  private Handler countInHandler, incrementalHandler, elapsedHandler, timerHandler;
  private String incrementalUnit, timerUnit;
  private String[] beats, subdivisions;
  private ValueAnimator timerAnimator;
  private int tempo, countIn, timerDuration;
  private int incrementalAmount, incrementalInterval, incrementalLimit;
  private long tickIndex, latency, elapsedStartTime, elapsedTime, elapsedPrevious, timerStartTime;
  private float timerProgress;
  private boolean playing, tempPlaying, useSubdivisions, beatModeVibrate, isCountingIn;
  private boolean showElapsed, resetElapsed, resetTimer;
  private boolean alwaysVibrate, incrementalIncrease, flashScreen, keepAwake;
  private boolean neverStartedWithGain = true;

  public MetronomeUtil(@NonNull Context context, boolean fromService) {
    this.context = context;
    this.fromService = fromService;

    sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);

    audioUtil = new AudioUtil(context, this::stop);
    hapticUtil = new HapticUtil(context);
    shortcutUtil = new ShortcutUtil(context);

    resetHandlersIfRequired();
    setToPreferences();
  }

  public void setToPreferences() {
    tempo = sharedPrefs.getInt(PREF.TEMPO, DEF.TEMPO);
    beats = sharedPrefs.getString(PREF.BEATS, DEF.BEATS).split(",");
    subdivisions = sharedPrefs.getString(PREF.SUBDIVISIONS, DEF.SUBDIVISIONS).split(",");
    useSubdivisions = sharedPrefs.getBoolean(PREF.USE_SUBS, DEF.USE_SUBS);
    countIn = sharedPrefs.getInt(PREF.COUNT_IN, DEF.COUNT_IN);
    latency = sharedPrefs.getLong(PREF.LATENCY, DEF.LATENCY);
    incrementalAmount = sharedPrefs.getInt(PREF.INCREMENTAL_AMOUNT, DEF.INCREMENTAL_AMOUNT);
    incrementalIncrease = sharedPrefs.getBoolean(
        PREF.INCREMENTAL_INCREASE, DEF.INCREMENTAL_INCREASE
    );
    incrementalInterval = sharedPrefs.getInt(PREF.INCREMENTAL_INTERVAL, DEF.INCREMENTAL_INTERVAL);
    incrementalUnit = sharedPrefs.getString(PREF.INCREMENTAL_UNIT, DEF.INCREMENTAL_UNIT);
    incrementalLimit = sharedPrefs.getInt(PREF.INCREMENTAL_LIMIT, 0);
    timerDuration = sharedPrefs.getInt(PREF.TIMER_DURATION, DEF.TIMER_DURATION);
    timerUnit = sharedPrefs.getString(PREF.TIMER_UNIT, DEF.TIMER_UNIT);
    alwaysVibrate = sharedPrefs.getBoolean(PREF.ALWAYS_VIBRATE, DEF.ALWAYS_VIBRATE);
    showElapsed = sharedPrefs.getBoolean(PREF.SHOW_ELAPSED, DEF.SHOW_ELAPSED);
    resetElapsed = sharedPrefs.getBoolean(PREF.RESET_ELAPSED, DEF.RESET_ELAPSED);
    resetTimer = sharedPrefs.getBoolean(PREF.RESET_TIMER, DEF.RESET_TIMER);
    flashScreen = sharedPrefs.getBoolean(PREF.FLASH_SCREEN, DEF.FLASH_SCREEN);
    keepAwake = sharedPrefs.getBoolean(PREF.KEEP_AWAKE, DEF.KEEP_AWAKE);

    setSound(sharedPrefs.getString(PREF.SOUND, DEF.SOUND));
    setIgnoreFocus(sharedPrefs.getBoolean(PREF.IGNORE_FOCUS, DEF.IGNORE_FOCUS));
    setGain(sharedPrefs.getInt(PREF.GAIN, DEF.GAIN));
    setBeatModeVibrate(sharedPrefs.getBoolean(PREF.BEAT_MODE_VIBRATE, DEF.BEAT_MODE_VIBRATE));
  }

  private void resetHandlersIfRequired() {
    if (!fromService) {
      return;
    }
    if (audioThread == null || !audioThread.isAlive()) {
      audioThread = new HandlerThread("metronome_audio");
      audioThread.start();
      removeHandlerCallbacks();
      tickHandler = new Handler(audioThread.getLooper());
    }
    if (callbackThread == null || !callbackThread.isAlive()) {
      callbackThread = new HandlerThread("metronome_callback");
      callbackThread.start();
      removeHandlerCallbacks();
      latencyHandler = new Handler(callbackThread.getLooper());
      countInHandler = new Handler(callbackThread.getLooper());
      incrementalHandler = new Handler(callbackThread.getLooper());
      elapsedHandler = new Handler(callbackThread.getLooper());
      timerHandler = new Handler(callbackThread.getLooper());
    }
  }

  private void removeHandlerCallbacks() {
    if (tickHandler != null) {
      tickHandler.removeCallbacksAndMessages(null);
    }
    if (latencyHandler != null) {
      latencyHandler.removeCallbacksAndMessages(null);
      countInHandler.removeCallbacksAndMessages(null);
      incrementalHandler.removeCallbacksAndMessages(null);
      elapsedHandler.removeCallbacksAndMessages(null);
      timerHandler.removeCallbacksAndMessages(null);
    }
  }

  public void savePlayingState() {
    tempPlaying = isPlaying();
  }

  public void restorePlayingState() {
    if (tempPlaying) {
      start(false);
    } else {
      stop();
    }
  }

  public void setUpLatencyCalibration() {
    tempo = 80;
    beats = DEF.BEATS.split(",");
    subdivisions = DEF.SUBDIVISIONS.split(",");
    alwaysVibrate = true;
    countIn = 0;
    incrementalAmount = 0;
    timerDuration = 0;
    setGain(0);
    setBeatModeVibrate(false);
    start(false);
  }

  public void destroy() {
    listeners.clear();
    if (fromService) {
      removeHandlerCallbacks();
      audioThread.quitSafely();
      callbackThread.quit();
    }
  }

  public void addListener(MetronomeListener listener) {
    listeners.add(listener);
  }

  public void addListeners(Set<MetronomeListener> listeners) {
    this.listeners.addAll(listeners);
  }

  public void removeListener(MetronomeListener listener) {
    listeners.remove(listener);
  }

  public Set<MetronomeListener> getListeners() {
    return Collections.unmodifiableSet(listeners);
  }

  public void start() {
    start(true);
  }

  public void start(boolean resetElapsedAndTimerIfNecessary) {
    if (!NotificationUtil.hasPermission(context)) {
      for (MetronomeListener listener : listeners) {
        listener.onPermissionMissing();
      }
      return;
    }
    if (resetElapsedAndTimerIfNecessary) {
      // notify system for shortcut usage prediction
      shortcutUtil.reportUsage(getTempo());
    }
    if (isPlaying()) {
      return;
    }
    if (!fromService) {
      for (MetronomeListener listener : listeners) {
        listener.onMetronomeConnectionMissing();
      }
      return;
    } else {
      resetHandlersIfRequired();
    }

    playing = true;
    audioUtil.play();
    tickIndex = 0;
    tickHandler.post(new Runnable() {
      @Override
      public void run() {
        if (isPlaying()) {
          tickHandler.postDelayed(this, getInterval() / getSubdivisionsCount());
          Tick tick = new Tick(
              tickIndex, getCurrentBeat(), getCurrentSubdivision(), getCurrentTickType()
          );
          performTick(tick);
          audioUtil.tick(tick, tempo, getSubdivisionsCount());
          tickIndex++;
        }
      }
    });

    isCountingIn = isCountInActive();
    countInHandler.postDelayed(() -> {
      isCountingIn = false;
      updateIncrementalHandler();
      elapsedStartTime = System.currentTimeMillis();
      updateElapsedHandler(resetElapsed && resetElapsedAndTimerIfNecessary);
      timerStartTime = System.currentTimeMillis();
      updateTimerHandler(
          resetTimer && resetElapsedAndTimerIfNecessary ? 0 : timerProgress,
          true
      );
    }, getCountInInterval()); // 0 if count-in is disabled

    if (getGain() > 0) {
      neverStartedWithGain = false;
    }

    for (MetronomeListener listener : listeners) {
      listener.onMetronomeStart();
    }
    Log.i(TAG, "start: started metronome handler");
  }

  public void stop() {
    if (!isPlaying()) {
      return;
    }
    timerProgress = getTimerProgress(); // must be called before playing is set to false
    elapsedPrevious = elapsedTime;

    playing = false;
    audioUtil.stop();
    isCountingIn = false;

    if (fromService) {
      removeHandlerCallbacks();
    }

    for (MetronomeListener listener : listeners) {
      listener.onMetronomeStop();
    }
    Log.i(TAG, "stop: stopped metronome handler");
  }

  public boolean isPlaying() {
    return playing;
  }

  public void setBeats(String[] beats) {
    this.beats = beats;
    sharedPrefs.edit().putString(PREF.BEATS, String.join(",", beats)).apply();
    if (isTimerActive() && timerUnit.equals(UNIT.BARS)) {
      updateTimerHandler(isPlaying() ? 0 : timerProgress, true);
    }
  }

  public String[] getBeats() {
    return beats;
  }

  public int getBeatsCount() {
    return beats.length;
  }

  public void setBeat(int beat, String tickType) {
    String[] beats = getBeats();
    beats[beat] = tickType;
    setBeats(beats);
  }

  public boolean addBeat() {
    if (beats.length >= Constants.BEATS_MAX) {
      return false;
    }
    String[] beats = Arrays.copyOf(this.beats, this.beats.length + 1);
    beats[beats.length - 1] = TICK_TYPE.NORMAL;
    setBeats(beats);
    return true;
  }

  public boolean removeBeat() {
    if (beats.length <= 1) {
      return false;
    }
    setBeats(Arrays.copyOf(beats, beats.length - 1));
    return true;
  }

  public void setSubdivisions(String[] subdivisions) {
    this.subdivisions = subdivisions;
    sharedPrefs.edit()
        .putString(PREF.SUBDIVISIONS, String.join(",", getSubdivisions()))
        .apply();
  }

  public String[] getSubdivisions() {
    return useSubdivisions ? subdivisions : DEF.SUBDIVISIONS.split(",");
  }

  public int getSubdivisionsCount() {
    return useSubdivisions ? subdivisions.length : 1;
  }

  public void setSubdivision(int subdivision, String tickType) {
    String[] subdivisions = getSubdivisions();
    subdivisions[subdivision] = tickType;
    setSubdivisions(subdivisions);
  }

  public boolean addSubdivision() {
    if (subdivisions.length >= Constants.SUBS_MAX) {
      return false;
    }
    String[] subdivisions = Arrays.copyOf(
        this.subdivisions, this.subdivisions.length + 1
    );
    subdivisions[subdivisions.length - 1] = TICK_TYPE.SUB;
    setSubdivisions(subdivisions);
    return true;
  }

  public boolean removeSubdivision() {
    if (subdivisions.length <= 1) {
      return false;
    }
    setSubdivisions(Arrays.copyOf(subdivisions, subdivisions.length - 1));
    return true;
  }

  public void setSubdivisionsUsed(boolean used) {
    useSubdivisions = used;
    sharedPrefs.edit().putBoolean(PREF.USE_SUBS, used).apply();
  }

  public boolean getSubdivisionsUsed() {
    return useSubdivisions;
  }

  public void setSwing3() {
    setSubdivisions(String.join(
        ",", TICK_TYPE.MUTED, TICK_TYPE.MUTED, TICK_TYPE.NORMAL).split(","));
  }

  public boolean isSwing3() {
    String triplet = String.join(",", TICK_TYPE.MUTED, TICK_TYPE.MUTED, TICK_TYPE.SUB);
    String tripletAlt = String.join(
        ",", TICK_TYPE.MUTED, TICK_TYPE.MUTED, TICK_TYPE.NORMAL
    );
    String subdivisions = String.join(",", getSubdivisions());
    return subdivisions.equals(triplet) || subdivisions.equals(tripletAlt);
  }

  public void setSwing5() {
    setSubdivisions(String.join(
        ",",
        TICK_TYPE.MUTED, TICK_TYPE.MUTED, TICK_TYPE.MUTED, TICK_TYPE.NORMAL, TICK_TYPE.MUTED
    ).split(","));
  }

  public boolean isSwing5() {
    String quintuplet = String.join(
        ",",
        TICK_TYPE.MUTED, TICK_TYPE.MUTED, TICK_TYPE.MUTED, TICK_TYPE.SUB, TICK_TYPE.MUTED
    );
    String quintupletAlt = String.join(
        ",",
        TICK_TYPE.MUTED, TICK_TYPE.MUTED, TICK_TYPE.MUTED, TICK_TYPE.NORMAL, TICK_TYPE.MUTED
    );
    String subdivisions = String.join(",", getSubdivisions());
    return subdivisions.equals(quintuplet) || subdivisions.equals(quintupletAlt);
  }

  public void setSwing7() {
    setSubdivisions(String.join(
        ",",
        TICK_TYPE.MUTED, TICK_TYPE.MUTED, TICK_TYPE.MUTED, TICK_TYPE.MUTED,
        TICK_TYPE.NORMAL, TICK_TYPE.MUTED, TICK_TYPE.MUTED
    ).split(","));
  }

  public boolean isSwing7() {
    String septuplet = String.join(
        ",",
        TICK_TYPE.MUTED, TICK_TYPE.MUTED, TICK_TYPE.MUTED, TICK_TYPE.MUTED,
        TICK_TYPE.SUB, TICK_TYPE.MUTED, TICK_TYPE.MUTED
    );
    String septupletAlt = String.join(
        ",",
        TICK_TYPE.MUTED, TICK_TYPE.MUTED, TICK_TYPE.MUTED, TICK_TYPE.MUTED,
        TICK_TYPE.NORMAL, TICK_TYPE.MUTED, TICK_TYPE.MUTED
    );
    String subdivisions = String.join(",", getSubdivisions());
    return subdivisions.equals(septuplet) || subdivisions.equals(septupletAlt);
  }

  public boolean isSwingActive() {
    return isSwing3() || isSwing5() || isSwing7();
  }

  public void setTempo(int tempo) {
    if (this.tempo != tempo) {
      this.tempo = tempo;
      sharedPrefs.edit().putInt(PREF.TEMPO, tempo).apply();
      if (isTimerActive() && timerUnit.equals(UNIT.BARS)) {
        updateTimerHandler(false);
      }
    }
  }

  public int getTempo() {
    return tempo;
  }

  private void changeTempo(int change) {
    int tempoOld = getTempo();
    int tempoNew = tempoOld + change;
    // setTempo will only be called by callback below, else we would break timer animation
    for (MetronomeListener listener : listeners) {
      listener.onMetronomeTempoChanged(tempoOld, tempoNew);
    }
  }

  public long getInterval() {
    return 1000 * 60 / tempo;
  }

  public void setSound(String sound) {
    audioUtil.setSound(sound);
    sharedPrefs.edit().putString(PREF.SOUND, sound).apply();
  }

  public String getSound() {
    return sharedPrefs.getString(PREF.SOUND, DEF.SOUND);
  }

  public void setBeatModeVibrate(boolean vibrate) {
    if (!hapticUtil.hasVibrator()) {
      vibrate = false;
    }
    beatModeVibrate = vibrate;
    audioUtil.setMuted(vibrate);
    hapticUtil.setEnabled(vibrate || alwaysVibrate);
    sharedPrefs.edit().putBoolean(PREF.BEAT_MODE_VIBRATE, vibrate).apply();
  }

  public boolean isBeatModeVibrate() {
    return beatModeVibrate;
  }

  public void setAlwaysVibrate(boolean always) {
    alwaysVibrate = always;
    hapticUtil.setEnabled(always || beatModeVibrate);
    sharedPrefs.edit().putBoolean(PREF.ALWAYS_VIBRATE, always).apply();
  }

  public boolean isAlwaysVibrate() {
    return alwaysVibrate;
  }

  public boolean areHapticEffectsPossible() {
    return !isPlaying() || (!beatModeVibrate && !alwaysVibrate);
  }

  public void setLatency(long offset) {
    latency = offset;
    sharedPrefs.edit().putLong(PREF.LATENCY, offset).apply();
  }

  public long getLatency() {
    return latency;
  }

  public void setIgnoreFocus(boolean ignore) {
    audioUtil.setIgnoreFocus(ignore);
    sharedPrefs.edit().putBoolean(PREF.IGNORE_FOCUS, ignore).apply();
  }

  public boolean getIgnoreAudioFocus() {
    return audioUtil.getIgnoreFocus();
  }

  public void setGain(int gain) {
    audioUtil.setGain(gain);
    sharedPrefs.edit().putInt(PREF.GAIN, gain).apply();
  }

  public int getGain() {
    return audioUtil.getGain();
  }

  public boolean neverStartedWithGainBefore() {
    return neverStartedWithGain;
  }

  public void setFlashScreen(boolean flash) {
    flashScreen = flash;
    sharedPrefs.edit().putBoolean(PREF.FLASH_SCREEN, flash).apply();
  }

  public boolean getFlashScreen() {
    return flashScreen;
  }

  public void setKeepAwake(boolean keepAwake) {
    this.keepAwake = keepAwake;
    sharedPrefs.edit().putBoolean(PREF.KEEP_AWAKE, keepAwake).apply();
  }

  public boolean getKeepAwake() {
    return keepAwake;
  }

  public void setCountIn(int bars) {
    countIn = bars;
    sharedPrefs.edit().putInt(PREF.COUNT_IN, bars).apply();
  }

  public int getCountIn() {
    return countIn;
  }

  public boolean isCountInActive() {
    return countIn > 0;
  }

  public boolean isCountingIn() {
    return isCountingIn;
  }

  public long getCountInInterval() {
    return getInterval() * getBeatsCount() * countIn;
  }

  public void setIncrementalAmount(int bpm) {
    incrementalAmount = bpm;
    sharedPrefs.edit().putInt(PREF.INCREMENTAL_AMOUNT, bpm).apply();
    updateIncrementalHandler();
  }

  public int getIncrementalAmount() {
    return incrementalAmount;
  }

  public boolean isIncrementalActive() {
    return incrementalAmount > 0;
  }

  public void setIncrementalIncrease(boolean increase) {
    incrementalIncrease = increase;
    sharedPrefs.edit().putBoolean(PREF.INCREMENTAL_INCREASE, increase).apply();
  }

  public boolean getIncrementalIncrease() {
    return incrementalIncrease;
  }

  public void setIncrementalInterval(int interval) {
    incrementalInterval = interval;
    sharedPrefs.edit().putInt(PREF.INCREMENTAL_INTERVAL, interval).apply();
    updateIncrementalHandler();
  }

  public int getIncrementalInterval() {
    return incrementalInterval;
  }

  public void setIncrementalUnit(String unit) {
    if (unit.equals(incrementalUnit)) {
      return;
    }
    incrementalUnit = unit;
    sharedPrefs.edit().putString(PREF.INCREMENTAL_UNIT, unit).apply();
    updateIncrementalHandler();
  }

  public String getIncrementalUnit() {
    return incrementalUnit;
  }

  public void setIncrementalLimit(int limit) {
    incrementalLimit = limit;
    sharedPrefs.edit().putInt(PREF.INCREMENTAL_LIMIT, limit).apply();
  }

  public int getIncrementalLimit() {
    return incrementalLimit;
  }

  private void updateIncrementalHandler() {
    if (!fromService || !isPlaying()) {
      return;
    }
    incrementalHandler.removeCallbacksAndMessages(null);
    if (!incrementalUnit.equals(UNIT.BARS) && isIncrementalActive()) {
      long factor = incrementalUnit.equals(UNIT.SECONDS) ? 1000L : 60000L;
      long interval = factor * incrementalInterval;
      incrementalHandler.postDelayed(new Runnable() {
        @Override
        public void run() {
          incrementalHandler.postDelayed(this, interval);
          if (incrementalIncrease && tempo + incrementalAmount <= incrementalLimit) {
            changeTempo(incrementalAmount);
          } else if (!incrementalIncrease && tempo - incrementalAmount >= incrementalLimit) {
            changeTempo(-incrementalAmount);
          }
        }
      }, interval);
    }
  }

  public void setShowElapsed(boolean show) {
    showElapsed = show;
    sharedPrefs.edit().putBoolean(PREF.SHOW_ELAPSED, show).apply();
  }

  public boolean getShowElapsed() {
    return showElapsed;
  }

  public boolean isElapsedActive() {
    return showElapsed;
  }

  public void setResetElapsed(boolean reset) {
    resetElapsed = reset;
    sharedPrefs.edit().putBoolean(PREF.RESET_ELAPSED, reset).apply();
  }

  public boolean getResetElapsed() {
    return resetElapsed;
  }

  public void updateElapsedHandler(boolean reset) {
    if (!fromService || !isPlaying()) {
      return;
    }
    elapsedHandler.removeCallbacksAndMessages(null);
    if (!isElapsedActive()) {
      return;
    }
    if (reset) {
      elapsedPrevious = 0;
    }
    elapsedHandler.post(new Runnable() {
      @Override
      public void run() {
        if (isPlaying()) {
          elapsedTime = System.currentTimeMillis() - elapsedStartTime + elapsedPrevious;
          elapsedHandler.postDelayed(this, 1000);
          for (MetronomeListener listener : listeners) {
            listener.onElapsedTimeSecondsChanged();
          }
        }
      }
    });
  }

  public String getElapsedTimeString() {
    if (!isElapsedActive()) {
      return "";
    }
    int seconds = (int) (elapsedTime / 1000);
    int minutes = seconds / 60;
    int hours = minutes / 60;
    if (hours > 0) {
      return String.format(
          Locale.ENGLISH, "%02d:%02d:%02d", hours, minutes % 60, seconds % 60
      );
    } else {
      return String.format(Locale.ENGLISH, "%02d:%02d", minutes, seconds % 60);
    }
  }

  public void setTimerDuration(int duration) {
    timerDuration = duration;
    sharedPrefs.edit().putInt(PREF.TIMER_DURATION, duration).apply();
    updateTimerHandler(0, false);
  }

  public int getTimerDuration() {
    return timerDuration;
  }

  public boolean isTimerActive() {
    return timerDuration > 0;
  }

  public long getTimerInterval() {
    long factor;
    switch (timerUnit) {
      case UNIT.SECONDS:
        factor = 1000L;
        break;
      case UNIT.MINUTES:
        factor = 60000L;
        break;
      default:
        factor = getInterval() * getBeatsCount();
        break;
    }
    return factor * timerDuration;
  }

  public long getTimerIntervalRemaining() {
    return (long) (getTimerInterval() * (1 - getTimerProgress()));
  }

  public void setTimerUnit(String unit) {
    if (unit.equals(timerUnit)) {
      return;
    }
    timerUnit = unit;
    sharedPrefs.edit().putString(PREF.TIMER_UNIT, unit).apply();
    updateTimerHandler(0, false);
  }

  public String getTimerUnit() {
    return timerUnit;
  }

  public void setResetTimer(boolean reset) {
    resetTimer = reset;
    sharedPrefs.edit().putBoolean(PREF.RESET_TIMER, reset).apply();
  }

  public boolean getResetTimer() {
    return resetTimer;
  }

  public float getTimerProgress() {
    if (isTimerActive()) {
      if (!timerUnit.equals(UNIT.BARS) && isPlaying() && !isCountingIn) {
        long previousDuration = (long) (timerProgress * getTimerInterval());
        long elapsedTime = System.currentTimeMillis() - timerStartTime + previousDuration;
        float fraction = elapsedTime / (float) getTimerInterval();
        return Math.min(1, Math.max(0, fraction));
      } else {
        return timerProgress;
      }
    } else {
      return 0;
    }
  }

  public boolean equalsTimerProgress(float fraction) {
    try {
      BigDecimal bdProgress = BigDecimal.valueOf(getTimerProgress()).setScale(
          2, RoundingMode.HALF_UP
      );
      BigDecimal bdFraction = new BigDecimal(fraction).setScale(2, RoundingMode.HALF_UP);
      return bdProgress.equals(bdFraction);
    } catch (NumberFormatException e) {
      return false;
    }
  }

  public void updateTimerHandler(float fraction, boolean startAtFirstBeat) {
    timerProgress = fraction;
    updateTimerHandler(startAtFirstBeat);
  }

  public void updateTimerHandler(boolean startAtFirstBeat) {
    if (!fromService || !isPlaying()) {
      return;
    }
    stopTimerAnimator();
    timerHandler.removeCallbacksAndMessages(null);
    if (!isTimerActive()) {
      return;
    }

    if (equalsTimerProgress(1)) {
      timerProgress = 0;
    } else if (startAtFirstBeat) {
      // set timer progress on start of this bar
      long progressInterval = (long) (getTimerProgress() * getTimerInterval());
      long barInterval = getInterval() * getBeatsCount();
      int progressBarCount = (int) (progressInterval / barInterval);
      long progressIntervalFullBars = progressBarCount * barInterval;
      timerProgress = (float) progressIntervalFullBars / getTimerInterval();
    }

    if (timerUnit.equals(UNIT.BARS)) {
      timerAnimator = ValueAnimator.ofFloat(timerProgress, 1);
      timerAnimator.addUpdateListener(animation -> {
        if (isPlaying()) {
          timerProgress = (float) animation.getAnimatedValue();
        } else {
          stopTimerAnimator();
        }
      });
      timerAnimator.addListener(new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
          new Handler(Looper.getMainLooper()).post(() -> stop());
          timerAnimator = null;
        }
      });
      timerAnimator.setDuration(getTimerIntervalRemaining());
      timerAnimator.setInterpolator(new LinearInterpolator());
      timerAnimator.start();
    } else {
      timerHandler.postDelayed(
          () -> new Handler(Looper.getMainLooper()).post(this::stop), getTimerIntervalRemaining()
      );
      timerHandler.post(new Runnable() {
        @Override
        public void run() {
          if (isPlaying() && !timerUnit.equals(UNIT.BARS)) {
            timerHandler.postDelayed(this, 1000);
            for (MetronomeListener listener : listeners) {
              listener.onTimerSecondsChanged();
            }
          }
        }
      });
    }

    for (MetronomeListener listener : listeners) {
      listener.onMetronomeTimerStarted();
    }
  }

  private void stopTimerAnimator() {
    if (timerAnimator != null) {
      timerAnimator.pause();
      timerAnimator.removeAllListeners();
      timerAnimator.removeAllUpdateListeners();
      timerAnimator.cancel();
      timerAnimator = null;
    }
  }

  public String getCurrentTimerString() {
    if (!isTimerActive()) {
      return "";
    }
    long elapsedTime = (long) (getTimerProgress() * getTimerInterval());
    switch (timerUnit) {
      case UNIT.SECONDS:
      case UNIT.MINUTES:
        int seconds = (int) (elapsedTime / 1000);
        int minutes = seconds / 60;
        return String.format(Locale.ENGLISH, "%02d:%02d", minutes, seconds % 60);
      default:
        long barInterval = getInterval() * getBeatsCount();
        int progressBarCount = Math.min((int) (elapsedTime / barInterval), timerDuration - 1);

        long elapsedTimeFullBars = progressBarCount * barInterval;
        long remaining = elapsedTime - elapsedTimeFullBars;
        int beatCount = Math.min((int) (remaining / getInterval()), getBeatsCount() - 1);

        String format = getBeatsCount() < 10 ? "%d.%01d" : "%d.%02d";
        return String.format(Locale.ENGLISH, format, progressBarCount + 1, beatCount + 1);
    }
  }

  public String getTotalTimeString() {
    if (!isTimerActive()) {
      return "";
    }
    switch (timerUnit) {
      case UNIT.SECONDS:
        return String.format(Locale.ENGLISH, "00:%02d", timerDuration);
      case UNIT.MINUTES:
        return String.format(Locale.ENGLISH, "%02d:00", timerDuration);
      default:
        return context.getResources().getQuantityString(
            R.plurals.options_unit_bars, timerDuration, timerDuration
        );
    }
  }

  private void performTick(Tick tick) {
    latencyHandler.postDelayed(() -> {
      for (MetronomeListener listener : listeners) {
        listener.onMetronomePreTick(tick);
      }
    }, Math.max(0, latency - Constants.BEAT_ANIM_OFFSET));
    latencyHandler.postDelayed(() -> {
      if (beatModeVibrate || alwaysVibrate) {
        switch (tick.type) {
          case TICK_TYPE.STRONG:
            hapticUtil.heavyClick();
            break;
          case TICK_TYPE.SUB:
            hapticUtil.tick();
            break;
          case TICK_TYPE.MUTED:
            break;
          default:
            hapticUtil.click();
        }
      }
      for (MetronomeListener listener : listeners) {
        listener.onMetronomeTick(tick);
      }
    }, latency);

    boolean isBeat = tick.subdivision == 1;
    boolean isFirstBeat = ((tick.index / getSubdivisionsCount()) % getBeatsCount()) == 0;
    if (isBeat && isFirstBeat) {
      long beatIndex = tick.index / getSubdivisionsCount();
      long barIndex = beatIndex / getBeatsCount();
      boolean isCountIn = barIndex < getCountIn();
      if (isIncrementalActive() && incrementalUnit.equals(UNIT.BARS) && !isCountIn) {
        barIndex = barIndex - getCountIn();
        if (barIndex >= incrementalInterval && barIndex % incrementalInterval == 0) {
          if (incrementalIncrease && tempo + incrementalAmount <= incrementalLimit) {
            changeTempo(incrementalAmount);
          } else if (!incrementalIncrease && tempo - incrementalAmount >= incrementalLimit) {
            changeTempo(-incrementalAmount);
          }
        }
      }
    }
  }

  private int getCurrentBeat() {
    return (int) ((tickIndex / getSubdivisionsCount()) % beats.length) + 1;
  }

  private int getCurrentSubdivision() {
    return (int) (tickIndex % getSubdivisionsCount()) + 1;
  }

  private String getCurrentTickType() {
    int subdivisionsCount = getSubdivisionsCount();
    if ((tickIndex % subdivisionsCount) == 0) {
      return beats[(int) ((tickIndex / subdivisionsCount) % beats.length)];
    } else {
      return subdivisions[(int) (tickIndex % subdivisionsCount)];
    }
  }

  public interface MetronomeListener {
    void onMetronomeStart();
    void onMetronomeStop();
    void onMetronomePreTick(Tick tick);
    void onMetronomeTick(Tick tick);
    void onMetronomeTempoChanged(int tempoOld, int tempoNew);
    void onElapsedTimeSecondsChanged();
    void onMetronomeTimerStarted();
    void onTimerSecondsChanged();
    void onMetronomeConnectionMissing();
    void onPermissionMissing();
  }

  public static class MetronomeListenerAdapter implements MetronomeListener {
    public void onMetronomeStart() {}
    public void onMetronomeStop() {}
    public void onMetronomePreTick(Tick tick) {}
    public void onMetronomeTick(Tick tick) {}
    public void onMetronomeTempoChanged(int tempoOld, int tempoNew) {}
    public void onElapsedTimeSecondsChanged() {}
    public void onMetronomeTimerStarted() {}
    public void onTimerSecondsChanged() {}
    public void onMetronomeConnectionMissing() {}
    public void onPermissionMissing() {}
  }

  public static class Tick {
    public final long index;
    public final int beat, subdivision;
    @NonNull
    public final String type;

    public Tick(long index, int beat, int subdivision, @NonNull String type) {
      this.index = index;
      this.beat = beat;
      this.subdivision = subdivision;
      this.type = type;
    }

    @NonNull
    @Override
    public String toString() {
      return "Tick{index = " + index +
          ", beat=" + beat +
          ", sub=" + subdivision +
          ", type=" + type + '}';
    }
  }
}