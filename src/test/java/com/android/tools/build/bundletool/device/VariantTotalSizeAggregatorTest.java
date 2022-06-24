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

package com.android.tools.build.bundletool.device;

import static com.android.bundle.Targeting.Abi.AbiAlias.ARM64_V8A;
import static com.android.bundle.Targeting.Abi.AbiAlias.ARMEABI;
import static com.android.bundle.Targeting.Abi.AbiAlias.ARMEABI_V7A;
import static com.android.bundle.Targeting.Abi.AbiAlias.MIPS;
import static com.android.bundle.Targeting.Abi.AbiAlias.MIPS64;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86_64;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.LDPI;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.MDPI;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.XHDPI;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.XXHDPI;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.XXXHDPI;
import static com.android.tools.build.bundletool.model.GetSizeRequest.Dimension.ABI;
import static com.android.tools.build.bundletool.model.GetSizeRequest.Dimension.LANGUAGE;
import static com.android.tools.build.bundletool.model.GetSizeRequest.Dimension.SCREEN_DENSITY;
import static com.android.tools.build.bundletool.model.GetSizeRequest.Dimension.SDK;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createApkDescription;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createInstantApkSet;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createMasterApkDescription;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createSplitApkSet;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createVariant;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.standaloneVariant;
import static com.android.tools.build.bundletool.testing.DeviceFactory.abis;
import static com.android.tools.build.bundletool.testing.DeviceFactory.density;
import static com.android.tools.build.bundletool.testing.DeviceFactory.locales;
import static com.android.tools.build.bundletool.testing.DeviceFactory.mergeSpecs;
import static com.android.tools.build.bundletool.testing.DeviceFactory.sdkVersion;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkDensityTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.lPlusVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantDensityTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantSdkTargeting;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Commands.DeliveryType;
import com.android.bundle.Commands.Variant;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.tools.build.bundletool.commands.GetSizeCommand;
import com.android.tools.build.bundletool.commands.GetSizeCommand.GetSizeSubcommand;
import com.android.tools.build.bundletool.model.ConfigurationSizes;
import com.android.tools.build.bundletool.model.SizeConfiguration;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.IncompatibleDeviceException;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class VariantTotalSizeAggregatorTest {

  private final GetSizeCommand.Builder getSizeCommand =
      GetSizeCommand.builder()
          .setApksArchivePath(Paths.get("test.apks"))
          .setGetSizeSubCommand(GetSizeSubcommand.TOTAL);

  @Test
  public void splitVariant_singleModule_SingleTargeting() {
    Variant lVariant =
        createVariant(
            lPlusVariantTargeting(),
            createSplitApkSet(
                /* moduleName= */ "base",
                createMasterApkDescription(
                    ApkTargeting.getDefaultInstance(), ZipPath.create("base-master.apk"))));
    ConfigurationSizes configurationSizes =
        new VariantTotalSizeAggregator(
                ImmutableMap.of("base-master.apk", 10L),
                BundleToolVersion.getCurrentVersion(),
                lVariant,
                getSizeCommand.build())
            .getSize();
    assertThat(configurationSizes.getMaxSizeConfigurationMap())
        .containsExactly(SizeConfiguration.getDefaultInstance(), 10L);
    assertThat(configurationSizes.getMinSizeConfigurationMap())
        .containsExactly(SizeConfiguration.getDefaultInstance(), 10L);
  }

  @Test
  public void splitVariant_singleModule_multipleTargeting() {
    Variant lVariant =
        createVariant(
            lPlusVariantTargeting(),
            createSplitApkSet(
                /* moduleName= */ "base",
                createMasterApkDescription(
                    ApkTargeting.getDefaultInstance(), ZipPath.create("base-master.apk")),
                createApkDescription(
                    apkAbiTargeting(X86, ImmutableSet.of(X86_64)),
                    ZipPath.create("base-x86.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkAbiTargeting(X86_64, ImmutableSet.of(X86)),
                    ZipPath.create("base-x86_64.apk"),
                    /* isMasterSplit= */ false)));
    ConfigurationSizes configurationSizes =
        new VariantTotalSizeAggregator(
                ImmutableMap.of("base-master.apk", 10L, "base-x86.apk", 4L, "base-x86_64.apk", 6L),
                BundleToolVersion.getCurrentVersion(),
                lVariant,
                getSizeCommand.build())
            .getSize();

    assertThat(configurationSizes.getMaxSizeConfigurationMap())
        .containsExactly(SizeConfiguration.getDefaultInstance(), 16L);
    assertThat(configurationSizes.getMinSizeConfigurationMap())
        .containsExactly(SizeConfiguration.getDefaultInstance(), 14L);
  }

  @Test
  public void splitVariant_singleModule_multipleTargeting_withDimensions() {
    Variant lVariant =
        createVariant(
            lPlusVariantTargeting(),
            createSplitApkSet(
                /* moduleName= */ "base",
                createMasterApkDescription(
                    ApkTargeting.getDefaultInstance(), ZipPath.create("base-master.apk")),
                createApkDescription(
                    apkAbiTargeting(ARMEABI, ImmutableSet.of(ARM64_V8A)),
                    ZipPath.create("base-armeabi.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkAbiTargeting(ARM64_V8A, ImmutableSet.of(ARMEABI)),
                    ZipPath.create("base-arm64_v8a.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkDensityTargeting(XHDPI, ImmutableSet.of(XXHDPI)),
                    ZipPath.create("base-xhdpi.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkDensityTargeting(XXHDPI, ImmutableSet.of(XHDPI)),
                    ZipPath.create("base-xxhdpi.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkLanguageTargeting("en"),
                    ZipPath.create("base-en.apk"),
                    /* isMasterSplit= */ false)));

    ConfigurationSizes configurationSizes =
        new VariantTotalSizeAggregator(
                ImmutableMap.<String, Long>builder()
                    .put("base-master.apk", 10L)
                    .put("base-armeabi.apk", 4L)
                    .put("base-arm64_v8a.apk", 6L)
                    .put("base-xhdpi.apk", 2L)
                    .put("base-xxhdpi.apk", 3L)
                    .put("base-en.apk", 1L)
                    .build(),
                BundleToolVersion.getCurrentVersion(),
                lVariant,
                getSizeCommand.setDimensions(ImmutableSet.of(ABI, SCREEN_DENSITY)).build())
            .getSize();

    assertThat(configurationSizes.getMaxSizeConfigurationMap())
        .containsExactly(
            SizeConfiguration.builder().setAbi("armeabi").setScreenDensity("XHDPI").build(),
            17L,
            SizeConfiguration.builder().setAbi("armeabi").setScreenDensity("XXHDPI").build(),
            18L,
            SizeConfiguration.builder().setAbi("arm64-v8a").setScreenDensity("XHDPI").build(),
            19L,
            SizeConfiguration.builder().setAbi("arm64-v8a").setScreenDensity("XXHDPI").build(),
            20L);

    assertThat(configurationSizes.getMinSizeConfigurationMap())
        .containsExactly(
            SizeConfiguration.builder().setAbi("armeabi").setScreenDensity("XHDPI").build(),
            17L,
            SizeConfiguration.builder().setAbi("armeabi").setScreenDensity("XXHDPI").build(),
            18L,
            SizeConfiguration.builder().setAbi("arm64-v8a").setScreenDensity("XHDPI").build(),
            19L,
            SizeConfiguration.builder().setAbi("arm64-v8a").setScreenDensity("XXHDPI").build(),
            20L);
  }

  @Test
  public void splitVariant_singleModule_multipleTargeting_withDimensionsAndDeviceSpec() {
    Variant lVariant =
        createVariant(
            lPlusVariantTargeting(),
            createSplitApkSet(
                /* moduleName= */ "base",
                createMasterApkDescription(
                    ApkTargeting.getDefaultInstance(), ZipPath.create("base-master.apk")),
                createApkDescription(
                    apkLanguageTargeting("en"),
                    ZipPath.create("base-en.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkLanguageTargeting("fr"),
                    ZipPath.create("base-fr.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkDensityTargeting(LDPI, ImmutableSet.of(MDPI)),
                    ZipPath.create("base-ldpi.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkDensityTargeting(MDPI, ImmutableSet.of(LDPI)),
                    ZipPath.create("base-mdpi.apk"),
                    /* isMasterSplit= */ false)));

    ConfigurationSizes configurationSizes =
        new VariantTotalSizeAggregator(
                ImmutableMap.<String, Long>builder()
                    .put("base-master.apk", 10L)
                    .put("base-en.apk", 1L)
                    .put("base-fr.apk", 2L)
                    .put("base-mdpi.apk", 6L)
                    .put("base-ldpi.apk", 3L)
                    .build(),
                BundleToolVersion.getCurrentVersion(),
                lVariant,
                getSizeCommand
                    .setDimensions(ImmutableSet.of(LANGUAGE))
                    .setDeviceSpec(mergeSpecs(locales("en")))
                    .build())
            .getSize();

    assertThat(configurationSizes.getMaxSizeConfigurationMap())
        .containsExactly(SizeConfiguration.builder().setLocale("en").build(), 17L);

    assertThat(configurationSizes.getMinSizeConfigurationMap())
        .containsExactly(SizeConfiguration.builder().setLocale("en").build(), 14L);
  }

  @Test
  public void splitVariant_singleModule_multipleTargeting_withDifferentDimensionsAndDeviceSpec() {
    Variant lVariant =
        createVariant(
            lPlusVariantTargeting(),
            createSplitApkSet(
                /* moduleName= */ "base",
                createMasterApkDescription(
                    ApkTargeting.getDefaultInstance(), ZipPath.create("base-master.apk")),
                createApkDescription(
                    apkLanguageTargeting("jp"),
                    ZipPath.create("base-jp.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkLanguageTargeting("en-US"),
                    ZipPath.create("base-en-US.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkDensityTargeting(XHDPI, ImmutableSet.of(XXHDPI)),
                    ZipPath.create("base-xhdpi.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkDensityTargeting(XXHDPI, ImmutableSet.of(XHDPI)),
                    ZipPath.create("base-xxhdpi.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkAbiTargeting(X86, ImmutableSet.of(X86_64)),
                    ZipPath.create("base-x86.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkAbiTargeting(X86_64, ImmutableSet.of(X86)),
                    ZipPath.create("base-x86_64.apk"),
                    /* isMasterSplit= */ false)));

    ConfigurationSizes configurationSizes =
        new VariantTotalSizeAggregator(
                ImmutableMap.<String, Long>builder()
                    .put("base-master.apk", 10L)
                    .put("base-jp.apk", 1L)
                    .put("base-en-US.apk", 2L)
                    .put("base-xhdpi.apk", 6L)
                    .put("base-xxhdpi.apk", 3L)
                    .put("base-x86.apk", 4L)
                    .put("base-x86_64.apk", 5L)
                    .build(),
                BundleToolVersion.getCurrentVersion(),
                lVariant,
                getSizeCommand
                    .setDimensions(ImmutableSet.of(SCREEN_DENSITY, SDK))
                    .setDeviceSpec(mergeSpecs(locales("jp"), abis("x86")))
                    .build())
            .getSize();

    assertThat(configurationSizes.getMaxSizeConfigurationMap())
        .containsExactly(
            SizeConfiguration.builder().setSdkVersion("21-").setScreenDensity("XHDPI").build(),
            21L,
            SizeConfiguration.builder().setSdkVersion("21-").setScreenDensity("XXHDPI").build(),
            18L);

    assertThat(configurationSizes.getMinSizeConfigurationMap())
        .containsExactly(
            SizeConfiguration.builder().setSdkVersion("21-").setScreenDensity("XHDPI").build(),
            21L,
            SizeConfiguration.builder().setSdkVersion("21-").setScreenDensity("XXHDPI").build(),
            18L);
  }

  @Test
  public void splitVariant_singleModule_multipleTargeting_withAllDeviceSpecsAndDimensions() {
    Variant lVariant =
        createVariant(
            lPlusVariantTargeting(),
            createSplitApkSet(
                /* moduleName= */ "base",
                createMasterApkDescription(
                    ApkTargeting.getDefaultInstance(), ZipPath.create("base-master.apk")),
                createApkDescription(
                    apkLanguageTargeting("fr"),
                    ZipPath.create("base-fr.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkLanguageTargeting("en-GB"),
                    ZipPath.create("base-en-GB.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkDensityTargeting(XHDPI, ImmutableSet.of(XXHDPI)),
                    ZipPath.create("base-xhdpi.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkDensityTargeting(XXHDPI, ImmutableSet.of(XHDPI)),
                    ZipPath.create("base-xxhdpi.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkAbiTargeting(MIPS, ImmutableSet.of(MIPS64)),
                    ZipPath.create("base-mips.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkAbiTargeting(MIPS64, ImmutableSet.of(MIPS)),
                    ZipPath.create("base-mips64.apk"),
                    /* isMasterSplit= */ false)));

    ConfigurationSizes configurationSizes =
        new VariantTotalSizeAggregator(
                ImmutableMap.<String, Long>builder()
                    .put("base-master.apk", 10L)
                    .put("base-fr.apk", 1L)
                    .put("base-en-GB.apk", 2L)
                    .put("base-xhdpi.apk", 6L)
                    .put("base-xxhdpi.apk", 3L)
                    .put("base-mips.apk", 4L)
                    .put("base-mips64.apk", 5L)
                    .build(),
                BundleToolVersion.getCurrentVersion(),
                lVariant,
                getSizeCommand
                    .setDimensions(ImmutableSet.of(SCREEN_DENSITY, SDK, LANGUAGE, ABI))
                    .setDeviceSpec(
                        mergeSpecs(
                            locales("fr", "jp"),
                            abis("x86", "mips"),
                            density(XHDPI),
                            sdkVersion(21)))
                    .build())
            .getSize();

    assertThat(configurationSizes.getMaxSizeConfigurationMap())
        .containsExactly(
            SizeConfiguration.builder()
                .setSdkVersion("21")
                .setScreenDensity("320")
                .setLocale("fr,jp")
                .setAbi("x86,mips")
                .build(),
            21L);

    assertThat(configurationSizes.getMinSizeConfigurationMap())
        .containsExactly(
            SizeConfiguration.builder()
                .setSdkVersion("21")
                .setScreenDensity("320")
                .setLocale("fr,jp")
                .setAbi("x86,mips")
                .build(),
            21L);
  }

  @Test
  public void splitVariant_singleModule_multipleTargeting_withIncompatibleAbiDeviceSpecs() {
    Variant lVariant =
        createVariant(
            variantSdkTargeting(sdkVersionFrom(21)),
            createSplitApkSet(
                "base",
                createMasterApkDescription(
                    ApkTargeting.getDefaultInstance(), ZipPath.create("apkL.apk")),
                createApkDescription(
                    apkAbiTargeting(AbiAlias.X86, ImmutableSet.of()),
                    ZipPath.create("apkL-x86.apk"),
                    /* isMasterSplit= */ false)));

    VariantTotalSizeAggregator variantTotalSizeAggregator =
        new VariantTotalSizeAggregator(
            ImmutableMap.<String, Long>builder()
                .put("apkL.apk", 10L)
                .put("apkL-x86.apk", 6L)
                .build(),
            BundleToolVersion.getCurrentVersion(),
            lVariant,
            getSizeCommand
                .setDeviceSpec(mergeSpecs(abis("arm64-v8a"), locales("en"), density(XHDPI)))
                .build());
    Throwable exception =
        assertThrows(IncompatibleDeviceException.class, variantTotalSizeAggregator::getSize);
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "The app doesn't support ABI architectures of the device. Device ABIs: [arm64-v8a], "
                + "app ABIs: [x86]");
  }

  @Test
  public void splitVariant_multipleModules_selectModules() {
    Variant lVariant =
        createVariant(
            lPlusVariantTargeting(),
            createSplitApkSet(
                /* moduleName= */ "base",
                createMasterApkDescription(
                    ApkTargeting.getDefaultInstance(), ZipPath.create("base-master.apk"))),
            createSplitApkSet(
                /* moduleName= */ "feature",
                DeliveryType.ON_DEMAND,
                /* moduleDependencies= */ ImmutableList.of(),
                createMasterApkDescription(
                    ApkTargeting.getDefaultInstance(), ZipPath.create("feature-master.apk"))),
            createSplitApkSet(
                /* moduleName= */ "feature1",
                DeliveryType.ON_DEMAND,
                /* moduleDependencies= */ ImmutableList.of(),
                createMasterApkDescription(
                    ApkTargeting.getDefaultInstance(), ZipPath.create("feature1-master.apk"))),
            createSplitApkSet(
                /* moduleName= */ "feature2",
                createMasterApkDescription(
                    ApkTargeting.getDefaultInstance(), ZipPath.create("feature2-master.apk"))));

    ConfigurationSizes configurationSizes =
        new VariantTotalSizeAggregator(
                ImmutableMap.<String, Long>builder()
                    .put("base-master.apk", 10L)
                    .put("feature-master.apk", 15L)
                    .put("feature1-master.apk", 6L)
                    .put("feature2-master.apk", 4L)
                    .build(),
                BundleToolVersion.getCurrentVersion(),
                lVariant,
                getSizeCommand.setModules(ImmutableSet.of("base", "feature1")).build())
            .getSize();

    // base, feature1, feature2 (install-time) modules are selected.
    assertThat(configurationSizes.getMaxSizeConfigurationMap())
        .containsExactly(SizeConfiguration.getDefaultInstance(), 20L);

    assertThat(configurationSizes.getMinSizeConfigurationMap())
        .containsExactly(SizeConfiguration.getDefaultInstance(), 20L);
  }

  @Test
  public void instantVariant_multipleModules_withInstantVariant() {
    // Instant variants only have instant split APKs.
    Variant lVariant =
        createVariant(
            lPlusVariantTargeting(),
            createInstantApkSet(
                /* moduleName= */ "base",
                ApkTargeting.getDefaultInstance(),
                ZipPath.create("base-master.apk")),
            createInstantApkSet(
                /* moduleName= */ "feature",
                ApkTargeting.getDefaultInstance(),
                ZipPath.create("feature-master.apk")),
            createInstantApkSet(
                /* moduleName= */ "feature1",
                ApkTargeting.getDefaultInstance(),
                ZipPath.create("feature1-master.apk")));

    ConfigurationSizes configurationSizes =
        new VariantTotalSizeAggregator(
                ImmutableMap.<String, Long>builder()
                    .put("base-master.apk", 10L)
                    .put("feature-master.apk", 15L)
                    .put("feature1-master.apk", 6L)
                    .build(),
                BundleToolVersion.getCurrentVersion(),
                lVariant,
                getSizeCommand.setInstant(true).build())
            .getSize();

    assertThat(configurationSizes.getMaxSizeConfigurationMap())
        .containsExactly(SizeConfiguration.getDefaultInstance(), 31L);

    assertThat(configurationSizes.getMinSizeConfigurationMap())
        .containsExactly(SizeConfiguration.getDefaultInstance(), 31L);
  }

  @Test
  public void splitVariant_multipleModules_multipleTargeting_withDeviceSpec() {
    Variant lVariant =
        createVariant(
            lPlusVariantTargeting(),
            createSplitApkSet(
                /* moduleName= */ "base",
                createMasterApkDescription(
                    ApkTargeting.getDefaultInstance(), ZipPath.create("base-master.apk")),
                createApkDescription(
                    apkAbiTargeting(MIPS, ImmutableSet.of(X86)),
                    ZipPath.create("base-mips.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkAbiTargeting(X86, ImmutableSet.of(MIPS)),
                    ZipPath.create("base-x86.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkLanguageTargeting("en"),
                    ZipPath.create("base-en.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkLanguageTargeting("fr"),
                    ZipPath.create("base-fr.apk"),
                    /* isMasterSplit= */ false)),
            createSplitApkSet(
                /* moduleName= */ "feature",
                createMasterApkDescription(
                    ApkTargeting.getDefaultInstance(), ZipPath.create("feature-master.apk")),
                createApkDescription(
                    apkDensityTargeting(XHDPI, ImmutableSet.of(XXHDPI)),
                    ZipPath.create("feature-xhdpi.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkDensityTargeting(XXHDPI, ImmutableSet.of(XHDPI)),
                    ZipPath.create("feature-xxhdpi.apk"),
                    /* isMasterSplit= */ false)));

    ConfigurationSizes configurationSizes =
        new VariantTotalSizeAggregator(
                ImmutableMap.<String, Long>builder()
                    .put("base-master.apk", 10L)
                    .put("base-mips.apk", 3L)
                    .put("base-x86.apk", 4L)
                    .put("base-en.apk", 1L)
                    .put("base-fr.apk", 2L)
                    .put("feature-master.apk", 15L)
                    .put("feature-xhdpi.apk", 6L)
                    .put("feature-xxhdpi.apk", 5L)
                    .build(),
                BundleToolVersion.getCurrentVersion(),
                lVariant,
                getSizeCommand.setDeviceSpec(mergeSpecs(density(XHDPI), abis("mips"))).build())
            .getSize();

    assertThat(configurationSizes.getMaxSizeConfigurationMap())
        .containsExactly(SizeConfiguration.getDefaultInstance(), 36L); // 10+3+2+15+6

    assertThat(configurationSizes.getMinSizeConfigurationMap())
        .containsExactly(SizeConfiguration.getDefaultInstance(), 35L); // 10+3+1+15+6
  }

  @Test
  public void getSize_standaloneVariant_withoutDimensionsAndDeviceSpec() {
    ZipPath apk = ZipPath.create("sample.apk");
    Variant variant =
        standaloneVariant(
            mergeVariantTargeting(
                variantSdkTargeting(sdkVersionFrom(1), ImmutableSet.of(sdkVersionFrom(21))),
                variantAbiTargeting(X86, ImmutableSet.of(ARMEABI_V7A, ARM64_V8A))),
            ApkTargeting.getDefaultInstance(),
            apk);

    ConfigurationSizes configurationSizes =
        new VariantTotalSizeAggregator(
                ImmutableMap.of("sample.apk", 10L),
                BundleToolVersion.getCurrentVersion(),
                variant,
                getSizeCommand.build())
            .getSize();
    assertThat(configurationSizes.getMinSizeConfigurationMap())
        .isEqualTo(configurationSizes.getMaxSizeConfigurationMap());
    assertThat(configurationSizes.getMinSizeConfigurationMap())
        .containsExactly(SizeConfiguration.getDefaultInstance(), 10L);
  }

  @Test
  public void getSize_standaloneVariant_withModules() {
    ZipPath apk = ZipPath.create("sample.apk");
    Variant variant =
        standaloneVariant(
            mergeVariantTargeting(
                variantSdkTargeting(sdkVersionFrom(1), ImmutableSet.of(sdkVersionFrom(21)))),
            ApkTargeting.getDefaultInstance(),
            apk);

    ConfigurationSizes configurationSizes =
        new VariantTotalSizeAggregator(
                ImmutableMap.of("sample.apk", 10L),
                BundleToolVersion.getCurrentVersion(),
                variant,
                getSizeCommand.setModules(ImmutableSet.of("base")).build())
            .getSize();
    assertThat(configurationSizes.getMaxSizeConfigurationMap()).isEmpty();
    assertThat(configurationSizes.getMinSizeConfigurationMap()).isEmpty();
  }

  @Test
  public void getSize_standaloneVariant_withDimensionsAndWithoutDeviceSpec() {
    ZipPath apk = ZipPath.create("sample.apk");
    Variant variant =
        standaloneVariant(
            mergeVariantTargeting(
                variantSdkTargeting(sdkVersionFrom(1), ImmutableSet.of(sdkVersionFrom(21))),
                variantAbiTargeting(ARMEABI_V7A, ImmutableSet.of(X86, ARM64_V8A)),
                variantDensityTargeting(XHDPI, ImmutableSet.of(MDPI, LDPI))),
            ApkTargeting.getDefaultInstance(),
            apk);

    ConfigurationSizes configurationSizes =
        new VariantTotalSizeAggregator(
                ImmutableMap.of("sample.apk", 20L),
                BundleToolVersion.getCurrentVersion(),
                variant,
                getSizeCommand
                    .setDimensions(ImmutableSet.of(ABI, SCREEN_DENSITY, LANGUAGE, SDK))
                    .build())
            .getSize();
    assertThat(configurationSizes.getMinSizeConfigurationMap())
        .isEqualTo(configurationSizes.getMaxSizeConfigurationMap());
    assertThat(configurationSizes.getMinSizeConfigurationMap())
        .containsExactly(
            SizeConfiguration.builder()
                .setAbi("armeabi-v7a")
                .setScreenDensity("XHDPI")
                .setSdkVersion("1-20")
                .build(),
            20L);
  }

  @Test
  public void getSize_standaloneVariant_withDeviceSpecAndWithoutDimensions() {
    ZipPath apk = ZipPath.create("sample.apk");
    Variant variant =
        standaloneVariant(
            mergeVariantTargeting(
                variantSdkTargeting(sdkVersionFrom(1), ImmutableSet.of(sdkVersionFrom(21))),
                variantAbiTargeting(MIPS),
                variantDensityTargeting(LDPI, ImmutableSet.of(XXXHDPI))),
            ApkTargeting.getDefaultInstance(),
            apk);

    ConfigurationSizes configurationSizes =
        new VariantTotalSizeAggregator(
                ImmutableMap.of("sample.apk", 15L),
                BundleToolVersion.getCurrentVersion(),
                variant,
                getSizeCommand.setDeviceSpec(mergeSpecs(locales("jp"), abis("mips"))).build())
            .getSize();
    assertThat(configurationSizes.getMinSizeConfigurationMap())
        .isEqualTo(configurationSizes.getMaxSizeConfigurationMap());
    assertThat(configurationSizes.getMinSizeConfigurationMap())
        .containsExactly(SizeConfiguration.getDefaultInstance(), 15L);
  }

  @Test
  public void getSize_standaloneVariants_withAllDeviceSpecAndDimensions() {
    ZipPath apk = ZipPath.create("sample.apk");
    Variant variant =
        standaloneVariant(
            mergeVariantTargeting(
                variantSdkTargeting(sdkVersionFrom(15), ImmutableSet.of(sdkVersionFrom(21))),
                variantAbiTargeting(ARM64_V8A, ImmutableSet.of(X86)),
                variantDensityTargeting(MDPI, ImmutableSet.of(XXHDPI, XXXHDPI))),
            ApkTargeting.getDefaultInstance(),
            apk);

    ConfigurationSizes configurationSizes =
        new VariantTotalSizeAggregator(
                ImmutableMap.of("sample.apk", 11L),
                BundleToolVersion.getCurrentVersion(),
                variant,
                getSizeCommand
                    .setDimensions(ImmutableSet.of(ABI, SCREEN_DENSITY, LANGUAGE, SDK))
                    .setDeviceSpec(
                        mergeSpecs(
                            locales("fr", "jp"),
                            abis("arm64-v8a", "mips"),
                            density(MDPI),
                            sdkVersion(15)))
                    .build())
            .getSize();
    assertThat(configurationSizes.getMinSizeConfigurationMap())
        .isEqualTo(configurationSizes.getMaxSizeConfigurationMap());
    assertThat(configurationSizes.getMinSizeConfigurationMap())
        .containsExactly(
            SizeConfiguration.builder()
                .setSdkVersion("15")
                .setScreenDensity("160")
                .setLocale("fr,jp")
                .setAbi("arm64-v8a,mips")
                .build(),
            11L);
  }
}
