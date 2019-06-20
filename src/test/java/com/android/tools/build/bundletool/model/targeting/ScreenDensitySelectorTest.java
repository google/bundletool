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
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.HDPI_VALUE;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.MDPI_VALUE;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.TVDPI_VALUE;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.XHDPI_VALUE;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.ANY_DPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.DEFAULT_DPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.HDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.LDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.MDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.TVDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.XHDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.XXHDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.XXXHDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.forDpi;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.onlyConfig;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.value;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.aapt.ConfigurationOuterClass.Configuration;
import com.android.aapt.Resources.ConfigValue;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ScreenDensitySelectorTest {

  private static final int NEXUS_7_2013_DPI = 323;

  private static final Version DEFAULT_BUNDLE_VERSION = BundleToolVersion.getCurrentVersion();

  @Test
  public void sortedOrder_deviceMatchesExistingDpi_notGreaterThan() {
    DensityAlias desiredDensity = DensityAlias.HDPI;
    ImmutableList<ConfigValue> densityConfigs =
        ImmutableList.of(onlyConfig(LDPI), onlyConfig(MDPI), onlyConfig(TVDPI), onlyConfig(HDPI));

    assertThat(
            new ScreenDensitySelector()
                .selectBestConfigValue(densityConfigs, desiredDensity, DEFAULT_BUNDLE_VERSION))
        .isEqualTo(onlyConfig(HDPI));
    assertThat(
            new ScreenDensitySelector()
                .selectBestDensity(toDensities(densityConfigs), toDpi(desiredDensity)))
        .isEqualTo(HDPI_VALUE);
  }

  @Test
  public void sortedOrder_deviceMatchesExistingDpi_notLessThan() {
    DensityAlias desiredDensity = DensityAlias.HDPI;
    ImmutableList<ConfigValue> densityConfigs =
        ImmutableList.of(
            onlyConfig(HDPI), onlyConfig(XHDPI), onlyConfig(XXHDPI), onlyConfig(XXXHDPI));

    assertThat(
            new ScreenDensitySelector()
                .selectBestConfigValue(densityConfigs, desiredDensity, DEFAULT_BUNDLE_VERSION))
        .isEqualTo(onlyConfig(HDPI));
    assertThat(
            new ScreenDensitySelector()
                .selectBestDensity(toDensities(densityConfigs), toDpi(desiredDensity)))
        .isEqualTo(HDPI_VALUE);
  }

  @Test
  public void anyDensityWins() {
    ImmutableList<ConfigValue> densityConfigs =
        ImmutableList.of(
            onlyConfig(MDPI),
            onlyConfig(HDPI),
            onlyConfig(XHDPI),
            onlyConfig(XXHDPI),
            onlyConfig(ANY_DPI),
            onlyConfig(XXXHDPI));

    assertThat(
            new ScreenDensitySelector()
                .selectBestConfigValue(densityConfigs, DensityAlias.XXHDPI, DEFAULT_BUNDLE_VERSION))
        .isEqualTo(onlyConfig(ANY_DPI));
    assertThat(
            new ScreenDensitySelector()
                .selectBestDensity(toDensities(densityConfigs), toDpi(DensityAlias.XXHDPI)))
        .isEqualTo(ANY_DENSITY_VALUE);
    assertThat(
            new ScreenDensitySelector()
                .selectBestConfigValue(densityConfigs, DensityAlias.MDPI, DEFAULT_BUNDLE_VERSION))
        .isEqualTo(onlyConfig(ANY_DPI));
    assertThat(
            new ScreenDensitySelector()
                .selectBestDensity(toDensities(densityConfigs), toDpi(DensityAlias.XXHDPI)))
        .isEqualTo(ANY_DENSITY_VALUE);
    assertThat(
            new ScreenDensitySelector()
                .selectBestConfigValue(densityConfigs, DensityAlias.LDPI, DEFAULT_BUNDLE_VERSION))
        .isEqualTo(onlyConfig(ANY_DPI));
    assertThat(
            new ScreenDensitySelector()
                .selectBestDensity(toDensities(densityConfigs), toDpi(DensityAlias.LDPI)))
        .isEqualTo(ANY_DENSITY_VALUE);
  }

  @Test
  public void anyDesiredDpi_treatedAsMdpi() {
    ImmutableList<ConfigValue> densityConfigs =
        ImmutableList.of(
            onlyConfig(MDPI),
            onlyConfig(HDPI),
            onlyConfig(XHDPI),
            onlyConfig(XXHDPI),
            onlyConfig(XXXHDPI));

    assertThat(
            new ScreenDensitySelector()
                .selectBestConfigValue(densityConfigs, ANY_DENSITY_VALUE, DEFAULT_BUNDLE_VERSION))
        .isEqualTo(onlyConfig(MDPI));
    assertThat(
            new ScreenDensitySelector()
                .selectBestDensity(toDensities(densityConfigs), ANY_DENSITY_VALUE))
        .isEqualTo(MDPI_VALUE);
    assertThat(
            new ScreenDensitySelector()
                .selectBestConfigValue(densityConfigs, MDPI_VALUE, DEFAULT_BUNDLE_VERSION))
        .isEqualTo(onlyConfig(MDPI));
    assertThat(
            new ScreenDensitySelector().selectBestDensity(toDensities(densityConfigs), MDPI_VALUE))
        .isEqualTo(MDPI_VALUE);
  }

  @Test
  public void defaultDesiredDpi_treatedAsMdpi() {
    ImmutableList<ConfigValue> densityConfigs =
        ImmutableList.of(
            onlyConfig(MDPI),
            onlyConfig(HDPI),
            onlyConfig(XHDPI),
            onlyConfig(XXHDPI),
            onlyConfig(XXXHDPI));

    assertThat(
            new ScreenDensitySelector()
                .selectBestConfigValue(
                    densityConfigs, DEFAULT_DENSITY_VALUE, DEFAULT_BUNDLE_VERSION))
        .isEqualTo(onlyConfig(MDPI));
    assertThat(
            new ScreenDensitySelector()
                .selectBestDensity(toDensities(densityConfigs), DEFAULT_DENSITY_VALUE))
        .isEqualTo(MDPI_VALUE);
    assertThat(
            new ScreenDensitySelector()
                .selectBestConfigValue(densityConfigs, MDPI_VALUE, DEFAULT_BUNDLE_VERSION))
        .isEqualTo(onlyConfig(MDPI));
    assertThat(
            new ScreenDensitySelector().selectBestDensity(toDensities(densityConfigs), MDPI_VALUE))
        .isEqualTo(MDPI_VALUE);
  }

  @Test
  public void prefersExplicitMdpiOverDefault_enabledSince_0_9_1() {
    ImmutableList<ConfigValue> densityConfigs =
        ImmutableList.of(value("default", DEFAULT_DPI), value("explicit", MDPI));

    // When enabled.
    assertThat(
            new ScreenDensitySelector()
                .selectBestConfigValue(densityConfigs, MDPI_VALUE, Version.of("0.9.1")))
        .isEqualTo(value("explicit", MDPI));
    // When disabled.
    assertThat(
            new ScreenDensitySelector()
                .selectBestConfigValue(densityConfigs, MDPI_VALUE, Version.of("0.9.0")))
        .isEqualTo(value("default", DEFAULT_DPI));
  }

  @Test
  public void desiredDensityBetweenBoth_lowerMatches() {
    // tvdpi = 213, (requested-dpi) hdpi = 240, xhdpi = 320
    // left side = ((2 * l) - requested-dpi) * h = ((2* 213) - 240) * 320 = 426 * 320
    // right side = requested-dpi * requested-dpi = 240 * 240
    // left side > right side, so lower dpi resource wins. (see ResourceTypes.cpp)

    DensityAlias desiredDensity = DensityAlias.HDPI;
    ImmutableList<ConfigValue> densityConfigs =
        ImmutableList.of(onlyConfig(TVDPI), onlyConfig(XHDPI));

    assertThat(
            new ScreenDensitySelector()
                .selectBestConfigValue(densityConfigs, desiredDensity, DEFAULT_BUNDLE_VERSION))
        .isEqualTo(onlyConfig(TVDPI));
    assertThat(
            new ScreenDensitySelector()
                .selectBestDensity(toDensities(densityConfigs), toDpi(desiredDensity)))
        .isEqualTo(TVDPI_VALUE);
  }

  @Test
  public void desiredDensityBetweenBoth_lowerMatches_reversed() {
    // xhdpi = 320, requested-dpi = 323 (Nexus 7 '13), xxhdpi = 480
    // left side = ((2 * l) - requested-dpi) * h = ((2* 320) - 323) * 480 = 317 * 480 = 152160
    // right side = requested-dpi * requested-dpi = 323 * 323 = 104329
    // left side > right side, so lower dpi resource wins. (see ResourceTypes.cpp)

    int desiredDpi = NEXUS_7_2013_DPI;
    ImmutableList<ConfigValue> densityConfigs =
        ImmutableList.of(onlyConfig(XXHDPI), onlyConfig(XHDPI));

    assertThat(
            new ScreenDensitySelector()
                .selectBestConfigValue(densityConfigs, desiredDpi, DEFAULT_BUNDLE_VERSION))
        .isEqualTo(onlyConfig(XHDPI));
    assertThat(
            new ScreenDensitySelector().selectBestDensity(toDensities(densityConfigs), desiredDpi))
        .isEqualTo(XHDPI_VALUE);
  }

  @Test
  public void desiredDensityBetweenBoth_higherMatches() {
    // ldpi = 120, mdpi = 160, hdpi = 240
    // left side = ((2 * l) - requested-dpi) * h = ((2* 120) - 160) * 240 = 80 * 240 = 19200
    // right side = requested-dpi * requested-dpi = 160 * 160 = 25600
    // left side < right side, so higher dpi resource wins. (see ResourceTypes.cpp)

    DensityAlias desiredDensity = DensityAlias.MDPI;
    ImmutableList<ConfigValue> densityConfigs =
        ImmutableList.of(onlyConfig(HDPI), onlyConfig(LDPI));

    assertThat(
            new ScreenDensitySelector()
                .selectBestConfigValue(densityConfigs, desiredDensity, DEFAULT_BUNDLE_VERSION))
        .isEqualTo(onlyConfig(HDPI));
    assertThat(
            new ScreenDensitySelector()
                .selectBestDensity(toDensities(densityConfigs), toDpi(desiredDensity)))
        .isEqualTo(HDPI_VALUE);
  }

  @Test
  public void desiredDensityBetweenBoth_higherMatches_reversed() {
    // mdpi = 160, hdpi = 240, xhdpi = 320
    // left side = ((2 * l) - requested-dpi) * h = ((2* 160) - 240) * 320 = 80 * 320 = 25600
    // right side = requested-dpi * requested-dpi = 240 * 240 = 57600
    // left side < right side, so higher dpi resource wins. (see ResourceTypes.cpp)

    DensityAlias desiredDensity = DensityAlias.MDPI;
    ImmutableList<ConfigValue> densityConfigs =
        ImmutableList.of(onlyConfig(LDPI), onlyConfig(HDPI));

    assertThat(
            new ScreenDensitySelector()
                .selectBestConfigValue(densityConfigs, desiredDensity, DEFAULT_BUNDLE_VERSION))
        .isEqualTo(onlyConfig(HDPI));
    assertThat(
            new ScreenDensitySelector()
                .selectBestDensity(toDensities(densityConfigs), toDpi(desiredDensity)))
        .isEqualTo(HDPI_VALUE);
  }

  @Test
  public void theOnlyResourceSplit_matchesAllConfigs() {

    DensityAlias desiredDensity = DensityAlias.MDPI;
    ImmutableList<ConfigValue> densityConfigs =
        ImmutableList.of(onlyConfig(LDPI), onlyConfig(HDPI), onlyConfig(XXXHDPI));

    assertThat(
            new ScreenDensitySelector()
                .selectAllMatchingConfigValues(
                    densityConfigs,
                    desiredDensity,
                    /* alternatives= */ ImmutableSet.of(),
                    DEFAULT_BUNDLE_VERSION))
        .containsExactlyElementsIn(densityConfigs);
  }

  @Test
  public void theLowestResourceSplit_matchesAllConfigsUpTo() {
    // The cut-off for MDPI(160 dpi) when XXHDPI (480 dpi) and XXXHDPI are present is 219 dpi
    // device.
    // 219dpi device still prefers 240 dpi resource over 160dpi.

    DensityAlias desiredDensity = DensityAlias.MDPI;
    ImmutableSet<DensityAlias> alternatives =
        ImmutableSet.of(DensityAlias.XXHDPI, DensityAlias.XXXHDPI);
    ImmutableList<ConfigValue> densityConfigs =
        ImmutableList.of(onlyConfig(LDPI), onlyConfig(MDPI), onlyConfig(HDPI), onlyConfig(XXXHDPI));

    assertThat(
            new ScreenDensitySelector()
                .selectAllMatchingConfigValues(
                    densityConfigs, desiredDensity, alternatives, DEFAULT_BUNDLE_VERSION))
        .containsExactlyElementsIn(
            ImmutableList.of(onlyConfig(LDPI), onlyConfig(MDPI), onlyConfig(HDPI)));
  }

  @Test
  public void theHighestResourceSplit_matchesAllConfigsDownTo() {
    // The cut-off for XXXHDPI(640 dpi) when XXHDPI (480 dpi) is present is 527 dpi device.
    // 527dpi device still prefers 481dpi (1 dpi more than XXHDPI) resource over 640dpi.

    DensityAlias desiredDensity = DensityAlias.XXXHDPI;
    ImmutableSet<DensityAlias> alternatives = ImmutableSet.of(DensityAlias.XXHDPI);
    ImmutableList<ConfigValue> densityConfigs =
        ImmutableList.of(onlyConfig(XXHDPI), onlyConfig(forDpi(481)), onlyConfig(XXXHDPI));

    assertThat(
            new ScreenDensitySelector()
                .selectAllMatchingConfigValues(
                    densityConfigs, desiredDensity, alternatives, DEFAULT_BUNDLE_VERSION))
        .containsExactlyElementsIn(ImmutableList.of(onlyConfig(forDpi(481)), onlyConfig(XXXHDPI)));
  }

  @Test
  public void theMidResourceSplit_matchesGivenConfigs() {
    // The range for XHDPI(320 dpi) when XXHDPI(480 dpi) and HDPI(240dpi) are present is [264;363]
    // device dpi.
    // 264dpi device should match 241dpi resource (1 dpi more than HDPI).
    // 363dpi device should match 475dpi resource (5 dpi less than XXHDPI).

    DensityAlias desiredDensity = DensityAlias.XHDPI;
    ImmutableSet<DensityAlias> alternatives =
        ImmutableSet.of(DensityAlias.HDPI, DensityAlias.XXHDPI);
    ImmutableList<ConfigValue> densityConfigs =
        ImmutableList.of(
            onlyConfig(HDPI),
            onlyConfig(forDpi(241)),
            onlyConfig(XHDPI),
            onlyConfig(forDpi(475)),
            onlyConfig(XXHDPI));

    assertThat(
            new ScreenDensitySelector()
                .selectAllMatchingConfigValues(
                    densityConfigs, desiredDensity, alternatives, DEFAULT_BUNDLE_VERSION))
        .containsExactlyElementsIn(
            ImmutableList.of(onlyConfig(forDpi(241)), onlyConfig(XHDPI), onlyConfig(forDpi(475))));
  }

  private static ImmutableList<Integer> toDensities(ImmutableList<ConfigValue> densities) {
    return densities.stream()
        .map(ConfigValue::getConfig)
        .map(Configuration::getDensity)
        .collect(toImmutableList());
  }

  private static int toDpi(DensityAlias densityAlias) {
    return DENSITY_ALIAS_TO_DPI_MAP.get(densityAlias);
  }
}
