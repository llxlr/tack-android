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

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.RequiresApi;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import xyz.zedler.patrick.tack.Constants.ACTION;
import xyz.zedler.patrick.tack.Constants.EXTRA;
import xyz.zedler.patrick.tack.R;
import xyz.zedler.patrick.tack.activity.ShortcutActivity;

public class ShortcutUtil {

  private static final String TAG = ShortcutUtil.class.getSimpleName();

  private final Context context;
  private ShortcutManager manager;
  private final ExecutorService executorService = Executors.newSingleThreadExecutor();
  private final Handler mainHandler = new Handler(Looper.getMainLooper());

  public ShortcutUtil(Context context) {
    this.context = context;
    if (isSupported()) {
      manager = (ShortcutManager) context.getSystemService(Context.SHORTCUT_SERVICE);
    }
  }

  public void addShortcut(int tempo) {
    hasShortcutAsync(tempo, hasShortcut -> {
      if (isSupported() && !hasShortcut) {
        if (manager.getDynamicShortcuts().size() < manager.getMaxShortcutCountPerActivity()) {
          manager.addDynamicShortcuts(Collections.singletonList(getShortcutInfo(tempo)));
        }
      }
    });
  }

  public void removeShortcut(int tempo) {
    hasShortcutAsync(tempo, hasShortcut -> {
      if (isSupported() && hasShortcut) {
        manager.removeDynamicShortcuts(Collections.singletonList(String.valueOf(tempo)));
      }
    });
  }

  public void removeAllShortcuts() {
    if (isSupported()) {
      manager.removeAllDynamicShortcuts();
    }
  }

  public void reportUsage(int tempo) {
    hasShortcutAsync(tempo, hasShortcut -> {
      if (isSupported() && hasShortcut) {
        manager.reportShortcutUsed(String.valueOf(tempo));
      }
    });
  }

  /**
   * Asynchronous because there was an ANR reported caused by manager.getDynamicShortcuts()
   */
  private void hasShortcutAsync(int tempo, ShortcutCallback callback) {
    if (isSupported()) {
      executorService.execute(() -> {
        boolean result = false;
        try {
          for (ShortcutInfo info : manager.getDynamicShortcuts()) {
            if (String.valueOf(tempo).equals(info.getId())) {
              result = true;
              break;
            }
          }
        } catch (Exception e) {
          Log.e(TAG, "hasShortcutAsync: ", e);
        }
        final boolean finalResult = result;
        mainHandler.post(() -> callback.onResult(finalResult));
      });
    } else {
      mainHandler.post(() -> callback.onResult(false));
    }
  }

  @RequiresApi(api = VERSION_CODES.N_MR1)
  private ShortcutInfo getShortcutInfo(int tempo) {
    ShortcutInfo.Builder builder = new ShortcutInfo.Builder(context, String.valueOf(tempo));
    builder.setShortLabel(context.getString(R.string.label_bpm_value, tempo));
    builder.setIcon(Icon.createWithResource(context, R.mipmap.ic_shortcut));
    builder.setIntent(new Intent(context, ShortcutActivity.class)
        .setAction(ACTION.START_TEMPO)
        .putExtra(EXTRA.TEMPO, tempo)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    );
    return builder.build();
  }

  private static boolean isSupported() {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1;
  }

  private interface ShortcutCallback {
    void onResult(boolean hasShortcut);
  }
}
