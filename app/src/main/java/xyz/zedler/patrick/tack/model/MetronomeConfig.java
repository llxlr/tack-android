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

package xyz.zedler.patrick.tack.model;

import android.content.SharedPreferences;
import xyz.zedler.patrick.tack.Constants.DEF;
import xyz.zedler.patrick.tack.Constants.PREF;

public class MetronomeConfig {

  // count in
  private int countIn;
  // tempo
  private int tempo;
  // beats
  private String[] beats, subdivisions;
  // incremental tempo change
  private int incrementalAmount, incrementalInterval, incrementalLimit;
  private String incrementalUnit;
  private boolean incrementalIncrease;
  // duration
  private int timerDuration;
  private String timerUnit;
  // muted beats
  private int mutePlay, muteMute;
  private String muteUnit;
  private boolean muteRandom;

  public MetronomeConfig(
      int countIn,
      int tempo,
      String[] beats, String[] subdivisions,
      int incrementalAmount, int incrementalInterval, int incrementalLimit,
      String incrementalUnit, boolean incrementalIncrease,
      int timerDuration, String timerUnit,
      int mutePlay, int muteMute, String muteUnit, boolean muteRandom
  ) {
    this.countIn = countIn;

    this.tempo = tempo;

    this.beats = beats;
    this.subdivisions = subdivisions;

    this.incrementalAmount = incrementalAmount;
    this.incrementalInterval = incrementalInterval;
    this.incrementalLimit = incrementalLimit;
    this.incrementalUnit = incrementalUnit;
    this.incrementalIncrease = incrementalIncrease;

    this.timerDuration = timerDuration;
    this.timerUnit = timerUnit;

    this.mutePlay = mutePlay;
    this.muteMute = muteMute;
    this.muteUnit = muteUnit;
    this.muteRandom = muteRandom;
  }

  public MetronomeConfig() {
    this.countIn = DEF.COUNT_IN;

    this.tempo = DEF.TEMPO;

    this.beats = DEF.BEATS.split(",");
    this.subdivisions = DEF.SUBDIVISIONS.split(",");

    this.incrementalAmount = DEF.INCREMENTAL_AMOUNT;
    this.incrementalInterval = DEF.INCREMENTAL_INTERVAL;
    this.incrementalLimit = DEF.INCREMENTAL_LIMIT;
    this.incrementalUnit = DEF.INCREMENTAL_UNIT;
    this.incrementalIncrease = DEF.INCREMENTAL_INCREASE;

    this.timerDuration = DEF.TIMER_DURATION;
    this.timerUnit = DEF.TIMER_UNIT;

    this.mutePlay = DEF.MUTE_PLAY;
    this.muteMute = DEF.MUTE_MUTE;
    this.muteUnit = DEF.MUTE_UNIT;
    this.muteRandom = DEF.MUTE_RANDOM;
  }

  public void setToPreferences(SharedPreferences sharedPrefs) {
    countIn = sharedPrefs.getInt(PREF.COUNT_IN, DEF.COUNT_IN);

    tempo = sharedPrefs.getInt(PREF.TEMPO, DEF.TEMPO);

    beats = sharedPrefs.getString(PREF.BEATS, DEF.BEATS).split(",");
    subdivisions = sharedPrefs.getString(PREF.SUBDIVISIONS, DEF.SUBDIVISIONS).split(",");

    incrementalAmount = sharedPrefs.getInt(PREF.INCREMENTAL_AMOUNT, DEF.INCREMENTAL_AMOUNT);
    incrementalInterval = sharedPrefs.getInt(PREF.INCREMENTAL_INTERVAL, DEF.INCREMENTAL_INTERVAL);
    incrementalLimit = sharedPrefs.getInt(PREF.INCREMENTAL_LIMIT, DEF.INCREMENTAL_LIMIT);
    incrementalUnit = sharedPrefs.getString(PREF.INCREMENTAL_UNIT, DEF.INCREMENTAL_UNIT);
    incrementalIncrease = sharedPrefs.getBoolean(
        PREF.INCREMENTAL_INCREASE, DEF.INCREMENTAL_INCREASE
    );

    timerDuration = sharedPrefs.getInt(PREF.TIMER_DURATION, DEF.TIMER_DURATION);
    timerUnit = sharedPrefs.getString(PREF.TIMER_UNIT, DEF.TIMER_UNIT);

    mutePlay = sharedPrefs.getInt(PREF.MUTE_PLAY, DEF.MUTE_PLAY);
    muteMute = sharedPrefs.getInt(PREF.MUTE_MUTE, DEF.MUTE_MUTE);
    muteUnit = sharedPrefs.getString(PREF.MUTE_UNIT, DEF.MUTE_UNIT);
    muteRandom = sharedPrefs.getBoolean(PREF.MUTE_RANDOM, DEF.MUTE_RANDOM);
  }

  public int getCountIn() {
    return countIn;
  }

  public void setCountIn(int countIn) {
    this.countIn = countIn;
  }

  public boolean isCountInActive() {
    return countIn > 0;
  }

  public int getTempo() {
    return tempo;
  }

  public void setTempo(int tempo) {
    this.tempo = tempo;
  }

  public String[] getBeats() {
    return beats;
  }

  public void setBeats(String[] beats) {
    this.beats = beats;
  }

  public void setBeats(String beats) {
    this.beats = beats.split(",");
  }

  public String[] getSubdivisions() {
    return subdivisions;
  }

  public void setSubdivisions(String[] subdivisions) {
    this.subdivisions = subdivisions;
  }

  public void setSubdivisions(String subdivisions) {
    this.subdivisions = subdivisions.split(",");
  }

  public int getIncrementalAmount() {
    return incrementalAmount;
  }

  public void setIncrementalAmount(int incrementalAmount) {
    this.incrementalAmount = incrementalAmount;
  }

  public int getIncrementalInterval() {
    return incrementalInterval;
  }

  public void setIncrementalInterval(int incrementalInterval) {
    this.incrementalInterval = incrementalInterval;
  }

  public int getIncrementalLimit() {
    return incrementalLimit;
  }

  public void setIncrementalLimit(int incrementalLimit) {
    this.incrementalLimit = incrementalLimit;
  }

  public String getIncrementalUnit() {
    return incrementalUnit;
  }

  public void setIncrementalUnit(String incrementalUnit) {
    this.incrementalUnit = incrementalUnit;
  }

  public boolean isIncrementalIncrease() {
    return incrementalIncrease;
  }

  public void setIncrementalIncrease(boolean incrementalIncrease) {
    this.incrementalIncrease = incrementalIncrease;
  }

  public boolean isIncrementalActive() {
    return incrementalAmount > 0;
  }

  public int getTimerDuration() {
    return timerDuration;
  }

  public void setTimerDuration(int timerDuration) {
    this.timerDuration = timerDuration;
  }

  public boolean isTimerActive() {
    return timerDuration > 0;
  }

  public String getTimerUnit() {
    return timerUnit;
  }

  public void setTimerUnit(String timerUnit) {
    this.timerUnit = timerUnit;
  }

  public int getMutePlay() {
    return mutePlay;
  }

  public void setMutePlay(int mutePlay) {
    this.mutePlay = mutePlay;
  }

  public boolean isMuteActive() {
    return mutePlay > 0;
  }

  public int getMuteMute() {
    return muteMute;
  }

  public void setMuteMute(int muteMute) {
    this.muteMute = muteMute;
  }

  public String getMuteUnit() {
    return muteUnit;
  }

  public void setMuteUnit(String muteUnit) {
    this.muteUnit = muteUnit;
  }

  public boolean isMuteRandom() {
    return muteRandom;
  }

  public void setMuteRandom(boolean muteRandom) {
    this.muteRandom = muteRandom;
  }
}
