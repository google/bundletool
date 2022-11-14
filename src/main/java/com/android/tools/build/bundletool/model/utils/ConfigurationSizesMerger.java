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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.tools.build.bundletool.model.ConfigurationSizes;
import com.android.tools.build.bundletool.model.SizeConfiguration;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Optional;

/**
 * Utility to merge two {@link ConfigurationSizes} by combining all compatible entries.
 *
 * <p>The resulting sizes for matching configurations are added together. If the configurations
 * target different dimensions, new combinations will be generated using the cartesian product of
 * the disjoint dimensions.
 */
public class ConfigurationSizesMerger {

  /** Merges two {@link ConfigurationSizes} */
  public static ConfigurationSizes merge(ConfigurationSizes config1, ConfigurationSizes config2) {
    return ConfigurationSizes.create(
        mergeSizeConfigurationMap(
            config1.getMinSizeConfigurationMap(), config2.getMinSizeConfigurationMap()),
        mergeSizeConfigurationMap(
            config1.getMaxSizeConfigurationMap(), config2.getMaxSizeConfigurationMap()));
  }

  private static ImmutableMap<SizeConfiguration, Long> mergeSizeConfigurationMap(
      ImmutableMap<SizeConfiguration, Long> map1, ImmutableMap<SizeConfiguration, Long> map2) {
    return map1.entrySet().stream()
        .flatMap(
            entry1 ->
                map2.entrySet().stream()
                    .filter(entry2 -> areCompatible(entry1.getKey(), entry2.getKey()))
                    .map(entry2 -> combineEntries(entry1, entry2)))
        .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private static boolean areCompatible(
      SizeConfiguration sizeConfig1, SizeConfiguration sizeConfig2) {

    return areCompatible(sizeConfig1.getAbi(), sizeConfig2.getAbi())
        && areCompatible(sizeConfig1.getLocale(), sizeConfig2.getLocale())
        && areCompatible(sizeConfig1.getScreenDensity(), sizeConfig2.getScreenDensity())
        && areCompatible(sizeConfig1.getSdkVersion(), sizeConfig2.getSdkVersion())
        && areCompatible(
            sizeConfig1.getTextureCompressionFormat(), sizeConfig2.getTextureCompressionFormat())
        && areCompatible(sizeConfig1.getDeviceTier(), sizeConfig2.getDeviceTier())
        && areCompatible(sizeConfig1.getCountrySet(), sizeConfig2.getCountrySet())
        && areCompatible(sizeConfig1.getSdkRuntime(), sizeConfig2.getSdkRuntime());
  }

  /**
   * Checks whether two values for a single dimension are compatible.
   *
   * <p>This happens if they have the same value or any of them is absent.
   */
  private static <T> boolean areCompatible(Optional<T> value1, Optional<T> value2) {
    return value1.equals(value2) || !value1.isPresent() || !value2.isPresent();
  }

  /**
   * Combine two compatible entries by merging the size configuration and adding the compressed
   * sizes.
   */
  private static Map.Entry<SizeConfiguration, Long> combineEntries(
      Map.Entry<SizeConfiguration, Long> entry1, Map.Entry<SizeConfiguration, Long> entry2) {
    checkState(
        areCompatible(entry1.getKey(), entry2.getKey()),
        "Tried to combine incompatible size configurations.");
    SizeConfiguration.Builder configBuilder = entry1.getKey().toBuilder();
    entry2.getKey().getAbi().ifPresent(configBuilder::setAbi);
    entry2.getKey().getLocale().ifPresent(configBuilder::setLocale);
    entry2.getKey().getScreenDensity().ifPresent(configBuilder::setScreenDensity);
    entry2.getKey().getSdkVersion().ifPresent(configBuilder::setSdkVersion);
    entry2
        .getKey()
        .getTextureCompressionFormat()
        .ifPresent(configBuilder::setTextureCompressionFormat);
    entry2.getKey().getDeviceTier().ifPresent(configBuilder::setDeviceTier);
    entry2.getKey().getCountrySet().ifPresent(configBuilder::setCountrySet);
    entry2.getKey().getSdkRuntime().ifPresent(configBuilder::setSdkRuntime);
    return Maps.immutableEntry(configBuilder.build(), entry1.getValue() + entry2.getValue());
  }

  private ConfigurationSizesMerger() {}
}
