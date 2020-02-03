/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.tools.build.bundletool.model.ConfigurationSizes;
import com.android.tools.build.bundletool.model.GetSizeRequest.Dimension;
import com.android.tools.build.bundletool.model.SizeConfiguration;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** Utils to format output of size information into CSV format. */
public final class GetSizeCsvUtils {

  private static final Ordering<Dimension> DIMENSIONS_COMPARATOR =
      Ordering.explicit(Dimension.SDK, Dimension.ABI, Dimension.SCREEN_DENSITY, Dimension.LANGUAGE);

  public static String getSizeTotalOutputInCsv(
      ConfigurationSizes configurationSizes, ImmutableSet<Dimension> dimensions) {

    checkState(
        configurationSizes
            .getMinSizeConfigurationMap()
            .keySet()
            .equals(configurationSizes.getMaxSizeConfigurationMap().keySet()),
        "Min and Max maps should contains same keys.");

    CsvFormatter.Builder csvFormatter = CsvFormatter.builder();

    csvFormatter.setHeader(getSizeTotalCsvHeader(dimensions));
    for (SizeConfiguration sizeConfiguration :
        configurationSizes.getMinSizeConfigurationMap().keySet()) {
      csvFormatter.addRow(
          getSizeTotalCsvRow(
              dimensions,
              sizeConfiguration,
              configurationSizes.getMinSizeConfigurationMap().get(sizeConfiguration),
              configurationSizes.getMaxSizeConfigurationMap().get(sizeConfiguration)));
    }

    return csvFormatter.build().format();
  }

  private static ImmutableList<String> getSizeTotalCsvHeader(ImmutableSet<Dimension> dimensions) {
    return Stream.concat(
            dimensions.stream().sorted(DIMENSIONS_COMPARATOR).map(Enum::name),
            Stream.of("MIN", "MAX"))
        .collect(toImmutableList());
  }

  private static ImmutableList<String> getSizeTotalCsvRow(
      ImmutableSet<Dimension> dimensions,
      SizeConfiguration sizeConfiguration,
      long minSize,
      long maxSize) {
    ImmutableMap<Dimension, Supplier<Optional<String>>> dimensionToTextMap =
        ImmutableMap.of(
            Dimension.ABI, sizeConfiguration::getAbi,
            Dimension.SDK, sizeConfiguration::getSdkVersion,
            Dimension.LANGUAGE, sizeConfiguration::getLocale,
            Dimension.SCREEN_DENSITY, sizeConfiguration::getScreenDensity);

    return Stream.concat(
            dimensions.stream()
                .sorted(DIMENSIONS_COMPARATOR)
                .map(dimension -> dimensionToTextMap.get(dimension).get().orElse("")),
            Stream.of(String.valueOf(minSize), String.valueOf(maxSize)))
        .collect(toImmutableList());
  }

  // Don't subclass. Hide the implicit constructor from IDEs/docs.
  private GetSizeCsvUtils() {}
}
