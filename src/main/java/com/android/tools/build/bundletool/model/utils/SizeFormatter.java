/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package com.android.tools.build.bundletool.model.utils;

import java.math.BigDecimal;
import java.text.DecimalFormat;

/** Interface for size formatter. */
public interface SizeFormatter {

  String format(long sizeInBytes);

  static SizeFormatter rawFormatter() {
    return String::valueOf;
  }

  static SizeFormatter humanReadableFormatter() {
    return new HumanReadableSizeFormatter();
  }

  /** Human readable file size formatter. */
  class HumanReadableSizeFormatter implements SizeFormatter {
    private static final String[] UNIT_ABBREVIATIONS = {"B", "KB", "MB", "GB"};
    private static final long[] UNIT_SCALES = {1, 1000, 1000 * 1000, 1000 * 1000 * 1000};
    // Min value for each unit is different from unit scales for MB and GB because of rounding half
    // up. So 999995 bytes is min value for MB unit because it will be rounded to 1 MB instead of
    // 1000 KB, but 999994 bytes will be rounded to 999.99 KB.
    private static final long[] MIN_VALUE_IN_UNIT = {
      0, 1000, 1000 * 1000 - 5, 1000 * 1000 * 1000 - 5 * 1000
    };

    @Override
    public String format(long sizeInBytes) {
      int unitIndex = UNIT_SCALES.length - 1;
      while (unitIndex > 0 && sizeInBytes < MIN_VALUE_IN_UNIT[unitIndex]) {
        unitIndex--;
      }
      return unitIndex == 0
          ? formatBytes(sizeInBytes)
          : formatHigherUnit(sizeInBytes, UNIT_ABBREVIATIONS[unitIndex], UNIT_SCALES[unitIndex]);
    }

    private static String formatBytes(long sizeInBytes) {
      return String.format("%d B", sizeInBytes);
    }

    private static String formatHigherUnit(
        long sizeInBytes, String unitAbbreviation, long unitScale) {
      String formattedSize =
          new DecimalFormat("#.##")
              .format(BigDecimal.valueOf(sizeInBytes).divide(BigDecimal.valueOf(unitScale)));
      return String.format("%s %s", formattedSize, unitAbbreviation);
    }

    private HumanReadableSizeFormatter() {}
  }
}
