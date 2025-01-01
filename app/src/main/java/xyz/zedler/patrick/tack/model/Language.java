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

import java.util.Locale;
import xyz.zedler.patrick.tack.util.LocaleUtil;

public class Language implements Comparable<Language> {

  private final String code;
  private final String translators;
  private final String name;

  public Language(String codeTranslators) {
    String[] parts = codeTranslators.split("\n");
    code = parts[0];
    translators = parts[1];
    Locale locale = LocaleUtil.getLocaleFromCode(code);
    String displayName = locale.getDisplayName(locale);
    name = displayName.substring(0, 1).toUpperCase() + displayName.substring(1);
  }

  public String getCode() {
    return code;
  }

  public String getTranslators() {
    return translators;
  }

  public String getName() {
    return name;
  }

  @Override
  public int compareTo(Language other) {
    return name.toLowerCase().compareTo(other.getName().toLowerCase());
  }
}
