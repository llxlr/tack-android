package xyz.zedler.patrick.tack.util;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import xyz.zedler.patrick.tack.R;
import xyz.zedler.patrick.tack.model.Language;

public class LocaleUtil {

  public static boolean followsSystem() {
    return AppCompatDelegate.getApplicationLocales().isEmpty();
  }

  @NonNull
  public static Locale getLocale() {
    if (followsSystem()) {
      if (Build.VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
        return Locale.getDefault();
      } else if (Build.VERSION.SDK_INT >= VERSION_CODES.N) {
        return Resources.getSystem().getConfiguration().getLocales().get(0);
      } else {
        //noinspection deprecation
        return Resources.getSystem().getConfiguration().locale;
      }
    } else {
      Locale locale = AppCompatDelegate.getApplicationLocales().get(0);
      return locale != null ? locale : Locale.getDefault();
    }
  }

  public static String getLocaleName() {
    Locale locale = getLocale();
    return locale.getDisplayName(locale);
  }

  public static List<Language> getLanguages(Context context) {
    List<Language> languages = new ArrayList<>();
    String localesRaw = ResUtil.getRawText(context, R.raw.locales);
    if (localesRaw.trim().isEmpty()) {
      return languages;
    }
    String[] locales = localesRaw.split("\n\n");
    for (String locale : locales) {
      languages.add(new Language(locale));
    }
    Collections.sort(languages);
    return languages;
  }

  public static String getLanguageCode(LocaleListCompat locales) {
    if (!locales.isEmpty()) {
      return Objects.requireNonNull(locales.get(0)).toLanguageTag();
    } else {
      return null;
    }
  }

  public static Locale getLocaleFromCode(@Nullable String languageCode) {
    if (languageCode == null) {
      return Locale.getDefault();
    }
    try {
      String[] codeParts = languageCode.split("-");
      if (codeParts.length > 1) {
        return new Locale(codeParts[0], codeParts[1]);
      } else {
        return new Locale(languageCode);
      }
    } catch (Exception e) {
      return Locale.getDefault();
    }
  }

  public static String getLangFromLanguageCode(@NonNull String languageCode) {
    String[] codeParts = languageCode.split("-");
    if (codeParts.length > 1) {
      return codeParts[0];
    } else {
      return languageCode;
    }
  }
}
