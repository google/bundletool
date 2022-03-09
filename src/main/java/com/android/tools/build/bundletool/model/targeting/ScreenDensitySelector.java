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

package com.android.tools.build.bundletool.model.targeting;

import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.ANY_DENSITY_VALUE;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.DEFAULT_DENSITY_VALUE;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.DENSITY_ALIAS_TO_DPI_MAP;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.MDPI_VALUE;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.NONE_DENSITY_VALUE;
import static com.android.tools.build.bundletool.model.version.VersionGuardedFeature.PREFER_EXPLICIT_DPI_OVER_DEFAULT_CONFIG;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.primitives.Booleans.falseFirst;

import com.android.aapt.Resources.ConfigValue;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.tools.build.bundletool.model.utils.ResourcesUtils;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.google.common.collect.Range;
import com.google.errorprone.annotations.CheckReturnValue;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

/**
 * Selector for the best matching density for a given desired density.
 *
 * <p>This class follows closely the way Android Framework picks the best density for resources.
 */
public final class ScreenDensitySelector {

  /**
   * Selects all matching {@link ConfigValue} objects for a given target density alias split.
   *
   * <p>It takes into account all alternative splits to determine what range of devices would the
   * target split effectively serve. From that set of devices, calculates which {@link ConfigValue}
   * could be effectively served to those devices. The latter depends on alternative values for a
   * given resource.
   *
   * @param values {@link ConfigValue} objects to select a subset from
   * @param forDensityAlias a target density split
   * @param alternatives all other density splits that will be generated
   * @return only {@link ConfigValue} objects that can be matched by any device served the target
   *     density split
   */
  public ImmutableList<ConfigValue> selectAllMatchingConfigValues(
      ImmutableList<ConfigValue> values,
      DensityAlias forDensityAlias,
      Set<DensityAlias> alternatives,
      Version bundleVersion) {
    Integer targetDpi = DENSITY_ALIAS_TO_DPI_MAP.get(forDensityAlias);
    ImmutableSet<Integer> alternativeDpis =
        alternatives.stream().map(DENSITY_ALIAS_TO_DPI_MAP::get).collect(toImmutableSet());

    return getReachableConfigValues(getDpiRange(targetDpi, alternativeDpis), values, bundleVersion);
  }

  /**
   * For a given range of devices dpi served by a split, returns the range of resources that are
   * reachable from this split.
   *
   * <p>The reachable resources can cover a be different dpi range than that of the devices since a
   * resource with dpi smaller than the lowest dpi device can be still preferred by that device.
   *
   * @param deviceDpiRange range of device dpis to serve by a given split
   * @param values {@link ConfigValue} objects of a resource under consideration
   * @return range of resource config values that can be matched by any device served by the split
   */
  @CheckReturnValue
  private ImmutableList<ConfigValue> getReachableConfigValues(
      Range<Integer> deviceDpiRange, ImmutableList<ConfigValue> values, Version bundleVersion) {
    // We are calculating the lowest and highest dpi of the resource that could be matched by the
    // devices from the given deviceDpiRange.
    // We take the lowest eligible dpi device and find the best matching resource for it, similarly
    // at the other end of the servedDpiRange. Because the best matching is monotonic, all resource
    // configs between those extremes and no others could be matched by any device that falls into
    // that dpi range.

    Optional<Integer> lowestResourceDpi = Optional.empty();
    Optional<Integer> highestResourceDpi = Optional.empty();
    if (deviceDpiRange.hasLowerBound()) {
      lowestResourceDpi =
          Optional.of(
              selectBestConfigValue(values, deviceDpiRange.lowerEndpoint(), bundleVersion)
                  .getConfig()
                  .getDensity());
    }
    if (deviceDpiRange.hasUpperBound()) {
      highestResourceDpi =
          Optional.of(
              selectBestConfigValue(values, deviceDpiRange.upperEndpoint(), bundleVersion)
                  .getConfig()
                  .getDensity());
    }

    Range<Integer> effectiveDpiRange;

    if (deviceDpiRange.equals(Range.all())) {
      effectiveDpiRange = Range.all();
    } else if (!lowestResourceDpi.isPresent()) {
      effectiveDpiRange = Range.atMost(highestResourceDpi.get());
    } else if (!highestResourceDpi.isPresent()) {
      effectiveDpiRange = Range.atLeast(lowestResourceDpi.get());
    } else {
      effectiveDpiRange = Range.closed(lowestResourceDpi.get(), highestResourceDpi.get());
    }

    return values.stream()
        .filter(configValue -> effectiveDpiRange.contains(configValue.getConfig().getDensity()))
        .collect(toImmutableList());
  }

  private Range<Integer> getDpiRange(int targetDpi, ImmutableSet<Integer> alternatives) {
    if (alternatives.isEmpty()) {
      return Range.all();
    }

    Optional<Integer> lowMidPoint =
        getNearestLowerDpi(targetDpi, alternatives)
            .map(lowerDpi -> getMidPoint(lowerDpi, targetDpi))
            .map(val -> (int) Math.ceil(val));
    Optional<Integer> highMidPoint =
        getNearestHigherDpi(targetDpi, alternatives)
            .map(higherDpi -> getMidPoint(targetDpi, higherDpi))
            .map(val -> (int) Math.floor(val));

    if (!lowMidPoint.isPresent()) {
      return Range.atMost(highMidPoint.get());
    }
    if (!highMidPoint.isPresent()) {
      return Range.atLeast(lowMidPoint.get());
    }
    return Range.closed(lowMidPoint.get(), highMidPoint.get());
  }

  private static double getMidPoint(int lowerDpi, int higherDpi) {
    checkArgument(lowerDpi < higherDpi);
    // Positive solution of the quadratic equation for selecting best matching resources
    // from the Android Framework.
    // See comment in ResourceTypes.cpp: "saying that scaling down is 2x better than up".
    return (-higherDpi + Math.sqrt(higherDpi * higherDpi + 8 * lowerDpi * higherDpi)) / 2;
  }

  private static Optional<Integer> getNearestHigherDpi(
      int targetDpi, ImmutableSet<Integer> alternatives) {
    return alternatives.stream().filter(alt -> alt > targetDpi).min(Comparator.naturalOrder());
  }

  private static Optional<Integer> getNearestLowerDpi(
      int targetDpi, ImmutableSet<Integer> alternatives) {
    return alternatives.stream().filter(alt -> alt < targetDpi).max(Comparator.naturalOrder());
  }

  /**
   * Selects the best matching density from the given {@link ConfigValue} objects for an ideal
   * density.
   */
  public ConfigValue selectBestConfigValue(
      Iterable<ConfigValue> values, DensityAlias desiredDensityAlias, Version bundleVersion) {
    checkArgument(DENSITY_ALIAS_TO_DPI_MAP.containsKey(desiredDensityAlias));
    return selectBestConfigValue(
        values, DENSITY_ALIAS_TO_DPI_MAP.get(desiredDensityAlias), bundleVersion);
  }

  public ConfigValue selectBestConfigValue(
      Iterable<ConfigValue> values, int desiredDpi, Version bundleVersion) {
    return Ordering.from(comparatorForConfigValues(desiredDpi, bundleVersion)).max(values);
  }

  /*
   * Selects the best matching density from the given list of available densities for an
   * ideal density.
   */
  public int selectBestDensity(Iterable<Integer> densities, int desiredDpi) {
    return Ordering.from(new ScreenDensityComparator(desiredDpi)).max(densities);
  }

  /**
   * Convenient comparator wrapper for analyzing {@link ConfigValue} objects.
   *
   * <p>Note: If two densities have an implicit and explicit representation (eg. MDPI is considered
   * the same as default (no density qualifier)), then the explicit value is preferred (since bundle
   * version 0.9.1).
   */
  private Comparator<ConfigValue> comparatorForConfigValues(int desiredDpi, Version bundleVersion) {
    // The bundle version is passed in as a method parameter instead of being a field of the whole
    // class to avoid plumbing the version from every place that uses the class, but not the methods
    // sensitive to bundle version.
    Comparator<ConfigValue> compositeComparator =
        Comparator.comparing(
            ScreenDensitySelector::getDpiValue, new ScreenDensityComparator(desiredDpi));
    if (PREFER_EXPLICIT_DPI_OVER_DEFAULT_CONFIG.enabledForVersion(bundleVersion)) {
      compositeComparator =
          compositeComparator.thenComparing(ScreenDensitySelector::isExplicitDpi, falseFirst());
    }
    return compositeComparator;
  }

  private static int getDpiValue(ConfigValue configValue) {
    if (configValue.getConfig().getDensity() == DEFAULT_DENSITY_VALUE) {
      return MDPI_VALUE;
    } else {
      return configValue.getConfig().getDensity();
    }
  }

  private static boolean isExplicitDpi(ConfigValue configValue) {
    int configDpi = configValue.getConfig().getDensity();
    return configDpi != ANY_DENSITY_VALUE
        && configDpi != DEFAULT_DENSITY_VALUE
        && configDpi != NONE_DENSITY_VALUE;
  }

  /*
   * Comparator implementation on dpi values, following the Android Framework resource matching
   * algorithm.
   */
  private static class ScreenDensityComparator implements Comparator<Integer> {

    private final int desiredDpi;

    public ScreenDensityComparator(int desiredDpi) {
      checkArgument(desiredDpi != ResourcesUtils.NONE_DENSITY_VALUE);

      if (desiredDpi == DEFAULT_DENSITY_VALUE || desiredDpi == ANY_DENSITY_VALUE) {
        this.desiredDpi = MDPI_VALUE;
      } else {
        this.desiredDpi = desiredDpi;
      }
    }

    /**
     * Picks which dpi matches better the desired dpi. The better matching dpi is considered a
     * "greater" element.
     */
    @Override
    public int compare(Integer dpiA, Integer dpiB) {
      checkNotNull(dpiA);
      checkNotNull(dpiB);

      if (dpiA.equals(dpiB)) {
        return 0;
      }
      // The resource with ANY_DPI qualifier always wins.
      if (dpiA.equals(ANY_DENSITY_VALUE)) {
        return 1;
      }
      if (dpiB.equals(ANY_DENSITY_VALUE)) {
        return -1;
      }

      if (dpiA > dpiB) {
        return -1 * compareOrdered(dpiB, dpiA);
      } else {
        return compareOrdered(dpiA, dpiB);
      }
    }

    /**
     * Picks which dpi matches better the desired dpi taking into account the scaling formula.
     *
     * @param lowerDpi lower dpi candidate to compare.
     * @param higherDpi higher dpi candidate to compare.
     * @return 1 if lowerDpi is better, -1 if higherDpi is better.
     */
    private int compareOrdered(int lowerDpi, int higherDpi) {
      if (desiredDpi >= higherDpi) {
        return -1;
      }
      if (desiredDpi <= lowerDpi) {
        return 1;
      }
      // See comment in ResourceTypes.cpp: "saying that scaling down is 2x better than up".
      if (((2 * lowerDpi) - desiredDpi) * higherDpi > desiredDpi * desiredDpi) {
        return 1;
      } else {
        return -1;
      }
    }
  }
}
