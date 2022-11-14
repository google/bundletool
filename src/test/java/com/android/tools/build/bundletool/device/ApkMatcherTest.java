/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.bundle.Commands.DeliveryType.FAST_FOLLOW;
import static com.android.bundle.Commands.DeliveryType.INSTALL_TIME;
import static com.android.bundle.Commands.DeliveryType.ON_DEMAND;
import static com.android.bundle.Targeting.Abi.AbiAlias.ARM64_V8A;
import static com.android.bundle.Targeting.Abi.AbiAlias.ARMEABI;
import static com.android.bundle.Targeting.Abi.AbiAlias.ARMEABI_V7A;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86_64;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.HDPI;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.LDPI;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.MDPI;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.XHDPI;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.XXHDPI;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.XXXHDPI;
import static com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias.ATC;
import static com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias.ETC1_RGB8;
import static com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias.PVRTC;
import static com.android.tools.build.bundletool.testing.ApkSetUtils.splitApkSet;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createConditionalApkSet;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createVariant;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.instantApkDescription;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.multiAbiTargetingApexVariant;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.splitApkDescription;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.standaloneVariant;
import static com.android.tools.build.bundletool.testing.DeviceFactory.abis;
import static com.android.tools.build.bundletool.testing.DeviceFactory.density;
import static com.android.tools.build.bundletool.testing.DeviceFactory.deviceFeatures;
import static com.android.tools.build.bundletool.testing.DeviceFactory.deviceGroups;
import static com.android.tools.build.bundletool.testing.DeviceFactory.deviceWithSdk;
import static com.android.tools.build.bundletool.testing.DeviceFactory.lDevice;
import static com.android.tools.build.bundletool.testing.DeviceFactory.lDeviceWithAbis;
import static com.android.tools.build.bundletool.testing.DeviceFactory.lDeviceWithDensity;
import static com.android.tools.build.bundletool.testing.DeviceFactory.lDeviceWithGlExtensions;
import static com.android.tools.build.bundletool.testing.DeviceFactory.lDeviceWithLocales;
import static com.android.tools.build.bundletool.testing.DeviceFactory.locales;
import static com.android.tools.build.bundletool.testing.DeviceFactory.mergeSpecs;
import static com.android.tools.build.bundletool.testing.DeviceFactory.preLDeviceWithGlExtensions;
import static com.android.tools.build.bundletool.testing.DeviceFactory.sdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkAlternativeLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkDensityTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkTextureTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeApkTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeModuleTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.moduleDeviceGroupsTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.moduleFeatureTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.moduleMinSdkVersionTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.multiAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantDensityTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantSdkTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantTextureTargeting;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Commands.ApkSet;
import com.android.bundle.Commands.AssetModuleMetadata;
import com.android.bundle.Commands.AssetSliceSet;
import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Commands.DeliveryType;
import com.android.bundle.Commands.PermanentlyFusedModule;
import com.android.bundle.Commands.Variant;
import com.android.bundle.Config.Bundletool;
import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.MultiAbiTargeting;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.device.ApkMatcher.GeneratedApk;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.IncompatibleDeviceException;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.utils.Versions;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ApkMatcherTest {

  // SDK variant matching.

  @Test
  public void apkMatch_deviceMatchesVariantValue_noAlternatives() {
    ZipPath apk = ZipPath.create("sample.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            oneApkSplitApkVariant(
                variantSdkTargeting(sdkVersionFrom(21)), ApkTargeting.getDefaultInstance(), apk));

    assertThat(new ApkMatcher(deviceWithSdk(21)).getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(apk));
  }

  @Test
  public void apkMatch_deviceMatchesVariantValue_noBetterAlternativeDefaultValue() {
    ZipPath apk = ZipPath.create("sample.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            oneApkSplitApkVariant(
                variantSdkTargeting(sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(1))),
                ApkTargeting.getDefaultInstance(),
                apk));

    assertThat(new ApkMatcher(deviceWithSdk(21)).getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(apk));
    assertThat(new ApkMatcher(deviceWithSdk(22)).getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(apk));
  }

  @Test
  public void apkMatch_deviceMatchesVariantValue_noBetterAlternative() {
    ZipPath apk = ZipPath.create("sample.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            oneApkSplitApkVariant(
                variantSdkTargeting(sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(19))),
                ApkTargeting.getDefaultInstance(),
                apk));

    assertThat(new ApkMatcher(deviceWithSdk(21)).getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(apk));
    assertThat(new ApkMatcher(deviceWithSdk(22)).getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(apk));
  }

  @Test
  public void apkNoMatch_deviceMatchesVariantValue_betterAlternatives() {
    ZipPath apk = ZipPath.create("sample.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            oneApkSplitApkVariant(
                variantSdkTargeting(sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(23))),
                ApkTargeting.getDefaultInstance(),
                apk));

    assertThat(new ApkMatcher(deviceWithSdk(25)).getMatchingApks(buildApksResult)).isEmpty();
  }

  @Test
  public void apkNoMatch_deviceMatchesVariantValue_bothBetterAndNoBetterAlternatives() {
    ZipPath apk = ZipPath.create("sample.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            oneApkSplitApkVariant(
                variantSdkTargeting(
                    sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(23), sdkVersionFrom(19))),
                ApkTargeting.getDefaultInstance(),
                apk));

    assertThat(new ApkMatcher(deviceWithSdk(25)).getMatchingApks(buildApksResult)).isEmpty();
  }

  @Test
  public void apkMatch_deviceMatchesVariantDefaultValue_noAlternatives() {
    ZipPath apk = ZipPath.create("sample.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            standaloneVariant(
                variantSdkTargeting(sdkVersionFrom(1), ImmutableSet.of()),
                ApkTargeting.getDefaultInstance(),
                apk));

    assertThat(new ApkMatcher(deviceWithSdk(21)).getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(apk));
  }

  @Test
  public void apkNoMatch_deviceMatchesVariantDefaultValue_betterAlternative() {
    ZipPath apk = ZipPath.create("sample.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            standaloneVariant(
                variantSdkTargeting(sdkVersionFrom(1), ImmutableSet.of(sdkVersionFrom(21))),
                ApkTargeting.getDefaultInstance(),
                apk));

    assertThat(new ApkMatcher(deviceWithSdk(21)).getMatchingApks(buildApksResult)).isEmpty();
  }

  @Test
  public void apkNoMatch_deviceDoesntMatchVariantValue_noAlternatives() {
    ZipPath apk = ZipPath.create("sample.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            oneApkSplitApkVariant(
                variantSdkTargeting(sdkVersionFrom(21)), ApkTargeting.getDefaultInstance(), apk));

    // The device of SDK 19 is incompatible with the app.
    Throwable exception =
        assertThrows(
            IncompatibleDeviceException.class,
            () -> new ApkMatcher(deviceWithSdk(19)).getMatchingApks(buildApksResult));
    assertThat(exception)
        .hasMessageThat()
        .contains("SDK version (19) of the device is not supported.");
  }

  @Test
  public void apkNoMatch_deviceDoesntMatchVariantValue_betterAlternatives() {
    ZipPath apk = ZipPath.create("sample.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            oneApkSplitApkVariant(
                variantSdkTargeting(sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(1))),
                ApkTargeting.getDefaultInstance(),
                apk));

    assertThat(new ApkMatcher(deviceWithSdk(19)).getMatchingApks(buildApksResult)).isEmpty();
  }

  @Test
  public void apkNoMatch_deviceSdkIncompatible() {
    ZipPath apk = ZipPath.create("sample.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            oneApkSplitApkVariant(
                variantSdkTargeting(
                    sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(23), sdkVersionFrom(25))),
                ApkTargeting.getDefaultInstance(),
                apk));

    Throwable exception =
        assertThrows(
            IncompatibleDeviceException.class,
            () -> new ApkMatcher(deviceWithSdk(19)).getMatchingApks(buildApksResult));
    assertThat(exception)
        .hasMessageThat()
        .contains("SDK version (19) of the device is not supported.");
  }

  @Test
  public void apkNoMatch_deviceDoesntMatchVariantValue_bothBetterAndNoBetterAlternatives() {
    ZipPath apk = ZipPath.create("sample.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            oneApkSplitApkVariant(
                variantSdkTargeting(
                    sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(1), sdkVersionFrom(23))),
                ApkTargeting.getDefaultInstance(),
                apk));

    assertThat(new ApkMatcher(deviceWithSdk(19)).getMatchingApks(buildApksResult)).isEmpty();
  }

  // Pre-L sharding variants tests.

  @Test
  public void variantMatch_preL_abiShard() {
    ZipPath apk = ZipPath.create("sample.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            standaloneVariant(
                mergeVariantTargeting(
                    variantSdkTargeting(sdkVersionFrom(1), ImmutableSet.of(sdkVersionFrom(21))),
                    variantAbiTargeting(X86, ImmutableSet.of(ARMEABI_V7A, ARM64_V8A))),
                ApkTargeting.getDefaultInstance(),
                apk));

    assertThat(
            new ApkMatcher(deviceWith(15, ImmutableList.of("x86"), MDPI))
                .getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(apk));
  }

  @Test
  public void variantDoesntMatch_preL_abiShard() {
    ZipPath apk = ZipPath.create("sample.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            standaloneVariant(
                mergeVariantTargeting(
                    variantSdkTargeting(sdkVersionFrom(1), ImmutableSet.of(sdkVersionFrom(21))),
                    variantAbiTargeting(X86, ImmutableSet.of(ARMEABI_V7A, ARM64_V8A))),
                ApkTargeting.getDefaultInstance(),
                apk));

    assertThat(
            new ApkMatcher(deviceWith(19, ImmutableList.of("arm64-v8a"), MDPI))
                .getMatchingApks(buildApksResult))
        .isEmpty();
  }

  @Test
  public void variantBetterAlternative_preL_abiShard() {
    ZipPath apk = ZipPath.create("sample.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            standaloneVariant(
                mergeVariantTargeting(
                    variantSdkTargeting(sdkVersionFrom(1), ImmutableSet.of(sdkVersionFrom(21))),
                    variantAbiTargeting(X86, ImmutableSet.of(ARMEABI_V7A, ARM64_V8A, X86_64))),
                ApkTargeting.getDefaultInstance(),
                apk));

    assertThat(
            new ApkMatcher(deviceWith(19, ImmutableList.of("x86_64", "x86"), MDPI))
                .getMatchingApks(buildApksResult))
        .isEmpty();
  }

  @Test
  public void variantWorseAlternatives_preL_abiShard() {
    ZipPath apk = ZipPath.create("sample.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            standaloneVariant(
                mergeVariantTargeting(
                    variantSdkTargeting(sdkVersionFrom(1), ImmutableSet.of(sdkVersionFrom(21))),
                    variantAbiTargeting(X86, ImmutableSet.of(ARMEABI_V7A, ARM64_V8A))),
                ApkTargeting.getDefaultInstance(),
                apk));

    assertThat(
            new ApkMatcher(deviceWith(19, ImmutableList.of("x86_64", "x86", "arm64-v8a"), MDPI))
                .getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(apk));
  }

  @Test
  public void variantMatch_preL_screenDensityShard() {
    ZipPath apk = ZipPath.create("sample.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            standaloneVariant(
                mergeVariantTargeting(
                    variantSdkTargeting(sdkVersionFrom(1), ImmutableSet.of(sdkVersionFrom(21))),
                    variantDensityTargeting(MDPI)),
                ApkTargeting.getDefaultInstance(),
                apk));

    assertThat(
            new ApkMatcher(deviceWith(17, ImmutableList.of("x86"), MDPI))
                .getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(apk));
  }

  @Test
  public void variantDoesntMatch_preL_screenDensityShard() {
    ZipPath apk = ZipPath.create("sample.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            standaloneVariant(
                mergeVariantTargeting(
                    variantSdkTargeting(sdkVersionFrom(1), ImmutableSet.of(sdkVersionFrom(21))),
                    variantDensityTargeting(XHDPI, ImmutableSet.of(MDPI))),
                ApkTargeting.getDefaultInstance(),
                apk));

    assertThat(
            new ApkMatcher(deviceWith(17, ImmutableList.of("x86"), MDPI))
                .getMatchingApks(buildApksResult))
        .isEmpty();
  }

  @Test
  public void variantBetterAlternative_preL_screenDensityShard() {
    ZipPath apk = ZipPath.create("sample.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            standaloneVariant(
                mergeVariantTargeting(
                    variantSdkTargeting(sdkVersionFrom(1), ImmutableSet.of(sdkVersionFrom(21))),
                    variantDensityTargeting(XHDPI, ImmutableSet.of(MDPI, HDPI))),
                ApkTargeting.getDefaultInstance(),
                apk));

    assertThat(
            new ApkMatcher(deviceWith(19, ImmutableList.of("x86_64", "x86"), HDPI))
                .getMatchingApks(buildApksResult))
        .isEmpty();
  }

  @Test
  public void variantWorseAlternatives_preL_screenDensityShard() {
    ZipPath apk = ZipPath.create("sample.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            standaloneVariant(
                mergeVariantTargeting(
                    variantSdkTargeting(sdkVersionFrom(1), ImmutableSet.of(sdkVersionFrom(21))),
                    variantDensityTargeting(XHDPI, ImmutableSet.of(LDPI, XXHDPI))),
                ApkTargeting.getDefaultInstance(),
                apk));

    assertThat(
            new ApkMatcher(deviceWith(19, ImmutableList.of("x86_64", "x86", "arm64-v8a"), HDPI))
                .getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(apk));
  }

  @Test
  public void variantMatching_SdkAbiDensity() {
    ZipPath apk = ZipPath.create("sample.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            standaloneVariant(
                mergeVariantTargeting(
                    variantSdkTargeting(sdkVersionFrom(1), ImmutableSet.of(sdkVersionFrom(21))),
                    variantAbiTargeting(X86, ImmutableSet.of(ARMEABI_V7A, ARM64_V8A)),
                    variantDensityTargeting(XHDPI, ImmutableSet.of(LDPI, XXHDPI))),
                ApkTargeting.getDefaultInstance(),
                apk));

    assertThat(
            new ApkMatcher(deviceWith(19, ImmutableList.of("x86_64", "x86", "arm64-v8a"), HDPI))
                .getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(apk));
  }

  @Test
  public void variantNotMatchingAtDensity_SdkAbiDensity() {
    ZipPath apk = ZipPath.create("sample.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            standaloneVariant(
                mergeVariantTargeting(
                    variantSdkTargeting(sdkVersionFrom(1), ImmutableSet.of(sdkVersionFrom(21))),
                    variantAbiTargeting(X86, ImmutableSet.of(ARMEABI_V7A, ARM64_V8A)),
                    variantDensityTargeting(XHDPI, ImmutableSet.of(LDPI, HDPI, XXHDPI))),
                ApkTargeting.getDefaultInstance(),
                apk));

    assertThat(
            new ApkMatcher(deviceWith(19, ImmutableList.of("x86_64", "x86", "arm64-v8a"), HDPI))
                .getMatchingApks(buildApksResult))
        .isEmpty();
  }

  @Test
  public void variantNotMatchingAtAbi_SdkAbiDensity() {
    ZipPath apk = ZipPath.create("sample.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            standaloneVariant(
                mergeVariantTargeting(
                    variantSdkTargeting(sdkVersionFrom(1), ImmutableSet.of(sdkVersionFrom(21))),
                    variantAbiTargeting(ARM64_V8A, ImmutableSet.of(ARMEABI_V7A, X86)),
                    variantDensityTargeting(XHDPI, ImmutableSet.of(LDPI, HDPI, XXHDPI))),
                ApkTargeting.getDefaultInstance(),
                apk));

    assertThat(
            new ApkMatcher(deviceWith(19, ImmutableList.of("x86_64", "x86", "arm64-v8a"), HDPI))
                .getMatchingApks(buildApksResult))
        .isEmpty();
  }

  // APEX variants tests.

  @Test
  public void apexVariantMatch_noMatch_throws() {
    ZipPath x86Apk = ZipPath.create("standalone-x86.apk");
    ZipPath x64X86Apk = ZipPath.create("standalone-x86_64.x86.apk");

    ImmutableSet<ImmutableSet<AbiAlias>> x86Set = ImmutableSet.of(ImmutableSet.of(X86));
    ImmutableSet<ImmutableSet<AbiAlias>> x64X86Set = ImmutableSet.of(ImmutableSet.of(X86_64, X86));

    MultiAbiTargeting x86Targeting = multiAbiTargeting(x86Set, x64X86Set);
    MultiAbiTargeting x64X86Targeting = multiAbiTargeting(x64X86Set, x86Set);

    BuildApksResult buildApksResult =
        BuildApksResult.newBuilder()
            .addVariant(multiAbiTargetingApexVariant(x86Targeting, x86Apk))
            .addVariant(multiAbiTargetingApexVariant(x64X86Targeting, x64X86Apk))
            .build();

    IncompatibleDeviceException e =
        assertThrows(
            IncompatibleDeviceException.class,
            () -> new ApkMatcher(abis("x86_64", "armeabi-v7a")).getMatchingApks(buildApksResult));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "No set of ABI architectures that the app supports is contained in the ABI "
                + "architecture set of the device");
  }

  @Test
  public void apexVariantMatch_matchesRightVariant() {
    ZipPath x86Apk = ZipPath.create("standalone-x86.apk");
    ZipPath x64X86Apk = ZipPath.create("standalone-x86_64.x86.apk");

    ImmutableSet<ImmutableSet<AbiAlias>> x86Set = ImmutableSet.of(ImmutableSet.of(X86));
    ImmutableSet<ImmutableSet<AbiAlias>> x64X86Set = ImmutableSet.of(ImmutableSet.of(X86_64, X86));

    MultiAbiTargeting x86Targeting = multiAbiTargeting(x86Set, x64X86Set);
    MultiAbiTargeting x64X86Targeting = multiAbiTargeting(x64X86Set, x86Set);

    BuildApksResult buildApksResult =
        BuildApksResult.newBuilder()
            .addVariant(multiAbiTargetingApexVariant(x86Targeting, x86Apk))
            .addVariant(multiAbiTargetingApexVariant(x64X86Targeting, x64X86Apk))
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .build();

    assertThat(new ApkMatcher(abis("x86")).getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(x86Apk));
    assertThat(new ApkMatcher(abis("x86_64", "x86")).getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(x64X86Apk));
    assertThat(
            new ApkMatcher(abis("x86_64", "x86", "armeabi-v7a")).getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(x64X86Apk));
    // Other device specs don't affect the matching variant.
    assertThat(
            new ApkMatcher(deviceWith(26, ImmutableList.of("x86"), HDPI))
                .getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(x86Apk));
  }

  private static DeviceSpec deviceWith(
      int sdkVersion, ImmutableList<String> abis, DensityAlias densityAlias) {
    return mergeSpecs(
        sdkVersion(sdkVersion),
        abis(abis.toArray(new String[0])),
        density(densityAlias),
        locales("en-US"));
  }

  // APK targeting tests.

  // ABI splits.

  @Test
  public void apkMatch_abiSplit() {
    ZipPath apk = ZipPath.create("sample.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            oneApkSplitApkVariant(
                variantSdkTargeting(sdkVersionFrom(Versions.ANDROID_L_API_VERSION)),
                apkAbiTargeting(X86),
                apk));

    assertThat(new ApkMatcher(lDeviceWithAbis("x86_64", "x86")).getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(apk));
  }

  @Test
  public void apkDoesntMatch_abiSplit_betterAlternative() {
    ZipPath apk = ZipPath.create("sample.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            oneApkSplitApkVariant(
                variantSdkTargeting(sdkVersionFrom(Versions.ANDROID_L_API_VERSION)),
                apkAbiTargeting(ARM64_V8A, ImmutableSet.of(X86_64)),
                apk));

    assertThat(
            new ApkMatcher(lDeviceWithAbis("x86_64", "x86", "arm64-v8a"))
                .getMatchingApks(buildApksResult))
        .isEmpty();
  }

  @Test
  public void apkMatch_abiSplit_noBetterAlternative() {
    ZipPath apk = ZipPath.create("sample.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            oneApkSplitApkVariant(
                variantSdkTargeting(sdkVersionFrom(Versions.ANDROID_L_API_VERSION)),
                apkAbiTargeting(X86, ImmutableSet.of(ARM64_V8A, ARMEABI)),
                apk));

    assertThat(
            new ApkMatcher(lDeviceWithAbis("x86_64", "x86", "arm64-v8a"))
                .getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(apk));
  }

  // Density splits.

  @Test
  public void apkMatch_screenDensitySplit() {
    ZipPath apk = ZipPath.create("sample.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            oneApkSplitApkVariant(
                variantSdkTargeting(sdkVersionFrom(Versions.ANDROID_L_API_VERSION)),
                apkDensityTargeting(HDPI),
                apk));

    assertThat(new ApkMatcher(lDeviceWithDensity(HDPI)).getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(apk));
  }

  @Test
  public void apkNoMatch_screenDensitySplit_betterAlternative() {
    ZipPath apk = ZipPath.create("sample.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            oneApkSplitApkVariant(
                variantSdkTargeting(sdkVersionFrom(Versions.ANDROID_L_API_VERSION)),
                apkDensityTargeting(HDPI, ImmutableSet.of(XXXHDPI)),
                apk));

    assertThat(new ApkMatcher(lDeviceWithDensity(XXHDPI)).getMatchingApks(buildApksResult))
        .isEmpty();
  }

  @Test
  public void apkMatch_screenDensitySplit_noBetterAlternative() {
    ZipPath apk = ZipPath.create("sample.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            oneApkSplitApkVariant(
                variantSdkTargeting(sdkVersionFrom(Versions.ANDROID_L_API_VERSION)),
                apkDensityTargeting(XXXHDPI, ImmutableSet.of(MDPI, HDPI)),
                apk));

    assertThat(new ApkMatcher(lDeviceWithDensity(XXHDPI)).getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(apk));
  }

  @Test
  public void apkMatch_screenDensitySplit_noBetterAlternative_complex() {
    ZipPath apk = ZipPath.create("sample.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            oneApkSplitApkVariant(
                variantSdkTargeting(sdkVersionFrom(Versions.ANDROID_L_API_VERSION)),
                apkDensityTargeting(XXHDPI, ImmutableSet.of(MDPI, HDPI, XXXHDPI)),
                apk));

    assertThat(new ApkMatcher(lDeviceWithDensity(XXHDPI)).getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(apk));
  }

  @Test
  public void apkNoMatch_screenDensitySplit_betterAlternative_complex() {
    ZipPath apk = ZipPath.create("sample.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            oneApkSplitApkVariant(
                variantSdkTargeting(sdkVersionFrom(Versions.ANDROID_L_API_VERSION)),
                apkDensityTargeting(XXXHDPI, ImmutableSet.of(MDPI, HDPI, XXHDPI)),
                apk));

    assertThat(new ApkMatcher(lDeviceWithDensity(XXHDPI)).getMatchingApks(buildApksResult))
        .isEmpty();
  }

  // Language splits.

  @Test
  public void apkMatch_languageSplit() {
    ZipPath apk = ZipPath.create("master-fr.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            oneApkSplitApkVariant(
                variantSdkTargeting(sdkVersionFrom(Versions.ANDROID_L_API_VERSION)),
                apkLanguageTargeting("fr"),
                apk));

    assertThat(new ApkMatcher(lDeviceWithLocales("fr-FR")).getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(apk));
  }

  @Test
  public void apkMatch_languageSplit_alternatives() {
    ZipPath enApk = ZipPath.create("master-en.apk");
    ZipPath frApk = ZipPath.create("master-fr.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            createVariant(
                variantSdkTargeting(sdkVersionFrom(Versions.ANDROID_L_API_VERSION)),
                splitApkSet("base", splitApkDescription(apkLanguageTargeting("en"), enApk)),
                splitApkSet("base", splitApkDescription(apkLanguageTargeting("fr"), frApk))));

    // Even though there is a 'better alternative' from the point of view of the phone settings
    // we pick all languages that match the device. The list of locales on device is ordered based
    // on preference.
    assertThat(
            new ApkMatcher(lDeviceWithLocales("en-GB", "fr-FR")).getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(enApk), baseMatchedApk(frApk));
  }

  @Test
  public void apkMatch_languageSplit_fallback() {
    ZipPath deEnFrFallbackApk = ZipPath.create("master-not-de-en-fr.apk");
    ZipPath deFrFallbackApk = ZipPath.create("master-not-de-fr.apk");
    ZipPath deFallbackApk = ZipPath.create("master-not-de.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            createVariant(
                variantSdkTargeting(sdkVersionFrom(Versions.ANDROID_L_API_VERSION)),
                splitApkSet(
                    "base",
                    splitApkDescription(
                        apkAlternativeLanguageTargeting("de", "en", "fr"), deEnFrFallbackApk)),
                splitApkSet(
                    "base",
                    splitApkDescription(
                        apkAlternativeLanguageTargeting("de", "fr"), deFrFallbackApk)),
                splitApkSet(
                    "base",
                    splitApkDescription(apkAlternativeLanguageTargeting("de"), deFallbackApk))));

    // A fallback language split should be selected iff the alternatives don't fully cover the
    // device languages.
    assertThat(
            new ApkMatcher(lDeviceWithLocales("en-GB", "fr-FR")).getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(deFrFallbackApk), baseMatchedApk(deFallbackApk));
  }

  @Test
  public void apkMatch_languageSplit_fusedLanguages() {
    ZipPath apk = ZipPath.create("master-de-fr.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            oneApkSplitApkVariant(
                variantSdkTargeting(sdkVersionFrom(Versions.ANDROID_L_API_VERSION)),
                apkLanguageTargeting("de", "fr"),
                apk));

    assertThat(
            new ApkMatcher(lDeviceWithLocales("en-GB", "fr-FR")).getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(apk));
    assertThat(
            new ApkMatcher(lDeviceWithLocales("de-DE", "de-AT", "en-US"))
                .getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(apk));
  }

  // Texture variants

  @Test
  public void textureVariant() {
    ZipPath apk = ZipPath.create("master-etc1_rgb8.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            createVariant(
                mergeVariantTargeting(
                    variantSdkTargeting(sdkVersionFrom(Versions.ANDROID_L_API_VERSION)),
                    variantTextureTargeting(ETC1_RGB8, ImmutableSet.of(ATC, PVRTC))),
                splitApkSet(
                    /* moduleName= */ "base",
                    splitApkDescription(ApkTargeting.getDefaultInstance(), apk))));

    assertThat(
            new ApkMatcher(lDeviceWithGlExtensions("GL_OES_compressed_ETC1_RGB8_texture"))
                .getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(apk));
  }

  @Test
  public void textureVariant_preL() {
    ZipPath apk = ZipPath.create("master-etc1_rgb8.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            standaloneVariant(
                mergeVariantTargeting(
                    variantSdkTargeting(sdkVersionFrom(1), ImmutableSet.of(sdkVersionFrom(21))),
                    variantTextureTargeting(ETC1_RGB8, ImmutableSet.of(ATC, PVRTC))),
                ApkTargeting.getDefaultInstance(),
                apk));

    assertThat(
            new ApkMatcher(preLDeviceWithGlExtensions("GL_OES_compressed_ETC1_RGB8_texture"))
                .getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(apk));
  }

  @Test
  public void textureVariant_betterAlternative() {
    ZipPath apk = ZipPath.create("master-etc1_rgb8.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            createVariant(
                mergeVariantTargeting(
                    variantSdkTargeting(sdkVersionFrom(Versions.ANDROID_L_API_VERSION)),
                    variantTextureTargeting(ATC, ImmutableSet.of(ETC1_RGB8, PVRTC))),
                splitApkSet(
                    /* moduleName= */ "base",
                    splitApkDescription(ApkTargeting.getDefaultInstance(), apk))));

    assertThat(
            new ApkMatcher(lDeviceWithGlExtensions("GL_OES_compressed_ETC1_RGB8_texture"))
                .getMatchingApks(buildApksResult))
        .isEmpty();
  }

  @Test
  public void textureVariant_preL_betterAlternative() {
    ZipPath apk = ZipPath.create("master-etc1_rgb8.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            standaloneVariant(
                mergeVariantTargeting(
                    variantSdkTargeting(sdkVersionFrom(1), ImmutableSet.of(sdkVersionFrom(21))),
                    variantTextureTargeting(ATC, ImmutableSet.of(ETC1_RGB8, PVRTC))),
                ApkTargeting.getDefaultInstance(),
                apk));

    assertThat(
            new ApkMatcher(preLDeviceWithGlExtensions("GL_OES_compressed_ETC1_RGB8_texture"))
                .getMatchingApks(buildApksResult))
        .isEmpty();
  }

  @Test
  public void textureVariant_incompatibleDevice() {
    ZipPath apk = ZipPath.create("master-etc1_rgb8.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            createVariant(
                mergeVariantTargeting(
                    variantSdkTargeting(sdkVersionFrom(Versions.ANDROID_L_API_VERSION)),
                    variantTextureTargeting(ATC)),
                splitApkSet(
                    /* moduleName= */ "base",
                    splitApkDescription(ApkTargeting.getDefaultInstance(), apk))));

    IncompatibleDeviceException exception =
        assertThrows(
            IncompatibleDeviceException.class,
            () ->
                new ApkMatcher(lDeviceWithGlExtensions("GL_OES_compressed_ETC1_RGB8_texture"))
                    .getMatchingApks(buildApksResult));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "The app doesn't support texture compression formats of the device. Device formats:"
                + " [ETC1_RGB8], app formats: [ATC]");
  }

  @Test
  public void textureVariant_fallback() {
    ZipPath apk = ZipPath.create("master-other_tcf.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            createVariant(
                mergeVariantTargeting(
                    variantSdkTargeting(sdkVersionFrom(Versions.ANDROID_L_API_VERSION)),
                    variantTextureTargeting(ImmutableSet.of(), ImmutableSet.of(ATC))),
                splitApkSet(
                    /* moduleName= */ "base",
                    splitApkDescription(ApkTargeting.getDefaultInstance(), apk))));

    assertThat(new ApkMatcher(lDevice()).getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(apk));
  }

  @Test
  public void textureVariant_preL_incompatibleDevice() {
    ZipPath apk = ZipPath.create("master-etc1_rgb8.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            standaloneVariant(
                mergeVariantTargeting(
                    variantSdkTargeting(sdkVersionFrom(1), ImmutableSet.of(sdkVersionFrom(21))),
                    variantTextureTargeting(ATC)),
                ApkTargeting.getDefaultInstance(),
                apk));

    IncompatibleDeviceException exception =
        assertThrows(
            IncompatibleDeviceException.class,
            () ->
                new ApkMatcher(preLDeviceWithGlExtensions("GL_OES_compressed_ETC1_RGB8_texture"))
                    .getMatchingApks(buildApksResult));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "The app doesn't support texture compression formats of the device. Device formats:"
                + " [ETC1_RGB8], app formats: [ATC]");
  }

  // Texture splits

  @Test
  public void apkMatch_textureSplit() {
    ZipPath apk = ZipPath.create("master-etc1_rgb8.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            oneApkSplitApkVariant(
                variantSdkTargeting(sdkVersionFrom(Versions.ANDROID_L_API_VERSION)),
                apkTextureTargeting(ETC1_RGB8, ImmutableSet.of(ATC, PVRTC)),
                apk));

    assertThat(
            new ApkMatcher(lDeviceWithGlExtensions("GL_OES_compressed_ETC1_RGB8_texture"))
                .getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(apk));
  }

  @Test
  public void apkMatch_textureSplit_betterAlternative() {
    ZipPath apk = ZipPath.create("master-etc1_rgb8.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            oneApkSplitApkVariant(
                variantSdkTargeting(sdkVersionFrom(Versions.ANDROID_L_API_VERSION)),
                apkTextureTargeting(ETC1_RGB8, ImmutableSet.of(ATC, PVRTC)),
                apk));

    assertThat(
            new ApkMatcher(
                    lDeviceWithGlExtensions(
                        "GL_OES_compressed_ETC1_RGB8_texture", "GL_IMG_texture_compression_pvrtc"))
                .getMatchingApks(buildApksResult))
        .isEmpty();
  }

  @Test
  public void apkMatch_textureSplit_noBetterAlternative() {
    ZipPath apk = ZipPath.create("master-etc1_rgb8.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            oneApkSplitApkVariant(
                variantSdkTargeting(sdkVersionFrom(Versions.ANDROID_L_API_VERSION)),
                apkTextureTargeting(ETC1_RGB8, ImmutableSet.of(ATC, PVRTC)),
                apk));

    assertThat(
            new ApkMatcher(lDeviceWithGlExtensions("GL_OES_compressed_ETC1_RGB8_texture"))
                .getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(apk));
  }

  @Test
  public void apkMatch_textureSplit_fallback() {
    ZipPath apk = ZipPath.create("master-other_tcf.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            oneApkSplitApkVariant(
                variantSdkTargeting(sdkVersionFrom(Versions.ANDROID_L_API_VERSION)),
                apkTextureTargeting(ImmutableSet.of(), ImmutableSet.of(ATC, PVRTC)),
                apk));

    assertThat(new ApkMatcher(lDevice()).getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(apk));
  }
  // Module name filtering.

  @Test
  public void apkMatch_withModuleNameFiltering_splitApks_checksModuleName() {
    DeviceSpec device = deviceWithSdk(21);
    ZipPath apk = ZipPath.create("master-de-fr.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            createVariant(
                VariantTargeting.getDefaultInstance(),
                splitApkSet(
                    /* moduleName= */ "base",
                    splitApkDescription(ApkTargeting.getDefaultInstance(), apk))));

    Optional<ImmutableSet<String>> allModules = Optional.empty();
    assertThat(createMatcher(device, allModules).getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(apk));

    Optional<ImmutableSet<String>> baseModuleOnly = Optional.of(ImmutableSet.of("base"));
    assertThat(createMatcher(device, baseModuleOnly).getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(apk));

    Optional<ImmutableSet<String>> unknownModuleOnly =
        Optional.of(ImmutableSet.of("unknown_module"));
    InvalidCommandException exception =
        assertThrows(
            InvalidCommandException.class,
            () -> createMatcher(device, unknownModuleOnly).getMatchingApks(buildApksResult));

    assertThat(exception)
        .hasMessageThat()
        .contains("The APK Set archive does not contain the following modules: [unknown_module]");
  }

  @Test
  public void apkMatch_withModuleNameFiltering_splitApks_permanentlyMergedModule() {
    DeviceSpec device = deviceWithSdk(21);
    ZipPath apk = ZipPath.create("master-de-fr.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
                createVariant(
                    VariantTargeting.getDefaultInstance(),
                    splitApkSet(
                        /* moduleName= */ "base",
                        splitApkDescription(ApkTargeting.getDefaultInstance(), apk))))
            .toBuilder()
            .addPermanentlyFusedModules(PermanentlyFusedModule.newBuilder().setName("my-module"))
            .build();

    ImmutableList<GeneratedApk> matchedApks =
        createMatcher(device, Optional.of(ImmutableSet.of("my-module")))
            .getMatchingApks(buildApksResult);
    assertThat(matchedApks).containsExactly(baseMatchedApk(apk));
  }

  @Test
  public void apkMatch_withModuleNameFiltering_instantApks_checksModuleName_isEmpty() {
    DeviceSpec device = deviceWithSdk(21);
    ZipPath apk = ZipPath.create("master-de-fr.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            createVariant(
                VariantTargeting.getDefaultInstance(),
                splitApkSet(
                    /* moduleName= */ "base",
                    splitApkDescription(ApkTargeting.getDefaultInstance(), apk))));

    Optional<ImmutableSet<String>> allModules = Optional.empty();
    assertThat(createInstantMatcher(device, allModules).getMatchingApks(buildApksResult)).isEmpty();

    Optional<ImmutableSet<String>> baseModuleOnly = Optional.of(ImmutableSet.of("base"));
    assertThat(createInstantMatcher(device, baseModuleOnly).getMatchingApks(buildApksResult))
        .isEmpty();

    Optional<ImmutableSet<String>> featureModuleOnly = Optional.of(ImmutableSet.of("feature"));
    // No matching instant variant
    assertThat(createInstantMatcher(device, featureModuleOnly).getMatchingApks(buildApksResult))
        .isEmpty();
  }

  @Test
  public void apkMatch_withModuleNameFiltering_instantApks_checksModuleName_exists() {
    DeviceSpec device = deviceWithSdk(21);
    ZipPath apk = ZipPath.create("master-de-fr.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            createVariant(
                VariantTargeting.getDefaultInstance(),
                splitApkSet(
                    /* moduleName= */ "base",
                    instantApkDescription(ApkTargeting.getDefaultInstance(), apk))));

    Optional<ImmutableSet<String>> allModules = Optional.empty();
    assertThat(createInstantMatcher(device, allModules).getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(apk));

    Optional<ImmutableSet<String>> baseModuleOnly = Optional.of(ImmutableSet.of("base"));
    assertThat(createInstantMatcher(device, baseModuleOnly).getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(apk));

    Optional<ImmutableSet<String>> unknownModuleOnly =
        Optional.of(ImmutableSet.of("unknown_module"));
    InvalidCommandException exception =
        assertThrows(
            InvalidCommandException.class,
            () -> createInstantMatcher(device, unknownModuleOnly).getMatchingApks(buildApksResult));

    assertThat(exception)
        .hasMessageThat()
        .contains("The APK Set archive does not contain the following modules: [unknown_module]");
  }

  @Test
  public void apkMatch_withModuleNameFiltering_standaloneApkMatches_throws() {
    DeviceSpec x86Device = lDeviceWithAbis("x86");

    ZipPath apk = ZipPath.create("master-x86.apk");
    BuildApksResult buildApksResult =
        buildApksResult(standaloneVariant(variantAbiTargeting(X86), apkAbiTargeting(X86), apk));

    // Sanity-check that the device matches the standalone APK.
    assertThat(new ApkMatcher(x86Device).getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(apk));

    Optional<ImmutableSet<String>> baseModuleOnly = Optional.of(ImmutableSet.of("base"));
    InvalidCommandException exception =
        assertThrows(
            InvalidCommandException.class,
            () -> createMatcher(x86Device, baseModuleOnly).getMatchingApks(buildApksResult));

    assertThat(exception).hasMessageThat().contains("Cannot restrict modules");
  }

  @Test
  public void apkMatch_withModuleNameFiltering_splitApks_moduleWithDependency() {
    DeviceSpec device = deviceWithSdk(21);
    ZipPath baseApk = ZipPath.create("master-base.apk");
    ZipPath feature1Apk = ZipPath.create("master-feature1.apk");
    ZipPath feature2Apk = ZipPath.create("master-feature2.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            createVariant(
                VariantTargeting.getDefaultInstance(),
                splitApkSet(
                    /* moduleName= */ "base",
                    splitApkDescription(ApkTargeting.getDefaultInstance(), baseApk)),
                splitApkSet(
                    /* moduleName= */ "feature1",
                    DeliveryType.ON_DEMAND,
                    /* moduleDependencies= */ ImmutableList.of(),
                    splitApkDescription(ApkTargeting.getDefaultInstance(), feature1Apk)),
                splitApkSet(
                    /* moduleName= */ "feature2",
                    DeliveryType.ON_DEMAND,
                    /* moduleDependencies= */ ImmutableList.of("feature1"),
                    splitApkDescription(ApkTargeting.getDefaultInstance(), feature2Apk))));

    // By default on-demand features are not installed.
    Optional<ImmutableSet<String>> allModules = Optional.empty();
    assertThat(createMatcher(device, allModules).getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(baseApk));

    Optional<ImmutableSet<String>> baseModuleOnly = Optional.of(ImmutableSet.of("base"));
    assertThat(createMatcher(device, baseModuleOnly).getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(baseApk));

    Optional<ImmutableSet<String>> feature2ModuleOnly = Optional.of(ImmutableSet.of("feature2"));
    assertThat(createMatcher(device, feature2ModuleOnly).getMatchingApks(buildApksResult))
        .containsExactly(
            baseMatchedApk(baseApk),
            matchedApk(feature1Apk, /* moduleName=*/ "feature1", ON_DEMAND),
            matchedApk(feature2Apk, /* moduleName=*/ "feature2", ON_DEMAND));

    Optional<ImmutableSet<String>> feature1ModuleOnly = Optional.of(ImmutableSet.of("feature1"));
    assertThat(createMatcher(device, feature1ModuleOnly).getMatchingApks(buildApksResult))
        .containsExactly(
            baseMatchedApk(baseApk),
            matchedApk(feature1Apk, /* moduleName=*/ "feature1", ON_DEMAND));
  }

  @Test
  public void apkMatch_withModuleNameFiltering_splitApks_diamondModuleDependenciesGraph() {
    DeviceSpec device = deviceWithSdk(21);
    ZipPath baseApk = ZipPath.create("master-base.apk");
    ZipPath feature1Apk = ZipPath.create("master-feature1.apk");
    ZipPath feature2Apk = ZipPath.create("master-feature2.apk");
    ZipPath feature3Apk = ZipPath.create("master-feature3.apk");
    ZipPath feature4Apk = ZipPath.create("master-feature4.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            createVariant(
                VariantTargeting.getDefaultInstance(),
                splitApkSet(
                    /* moduleName= */ "base",
                    splitApkDescription(ApkTargeting.getDefaultInstance(), baseApk)),
                splitApkSet(
                    /* moduleName= */ "feature1",
                    DeliveryType.ON_DEMAND,
                    /* moduleDependencies= */ ImmutableList.of(),
                    splitApkDescription(ApkTargeting.getDefaultInstance(), feature1Apk)),
                splitApkSet(
                    /* moduleName= */ "feature2",
                    DeliveryType.ON_DEMAND,
                    /* moduleDependencies= */ ImmutableList.of("feature1"),
                    splitApkDescription(ApkTargeting.getDefaultInstance(), feature2Apk)),
                splitApkSet(
                    /* moduleName= */ "feature3",
                    DeliveryType.ON_DEMAND,
                    /* moduleDependencies= */ ImmutableList.of("feature1"),
                    splitApkDescription(ApkTargeting.getDefaultInstance(), feature3Apk)),
                splitApkSet(
                    /* moduleName= */ "feature4",
                    DeliveryType.ON_DEMAND,
                    /* moduleDependencies= */ ImmutableList.of("feature2", "feature3"),
                    splitApkDescription(ApkTargeting.getDefaultInstance(), feature4Apk))));

    // By default on-demand features are not installed.
    Optional<ImmutableSet<String>> allModules = Optional.empty();
    assertThat(createMatcher(device, allModules).getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(baseApk));

    Optional<ImmutableSet<String>> feature4ModuleOnly = Optional.of(ImmutableSet.of("feature4"));
    assertThat(createMatcher(device, feature4ModuleOnly).getMatchingApks(buildApksResult))
        .containsExactly(
            baseMatchedApk(baseApk),
            matchedApk(feature1Apk, /* moduleName=*/ "feature1", ON_DEMAND),
            matchedApk(feature2Apk, /* moduleName=*/ "feature2", ON_DEMAND),
            matchedApk(feature3Apk, /* moduleName=*/ "feature3", ON_DEMAND),
            matchedApk(feature4Apk, /* moduleName=*/ "feature4", ON_DEMAND));

    Optional<ImmutableSet<String>> feature2ModuleOnly = Optional.of(ImmutableSet.of("feature2"));
    assertThat(createMatcher(device, feature2ModuleOnly).getMatchingApks(buildApksResult))
        .containsExactly(
            baseMatchedApk(baseApk),
            matchedApk(feature1Apk, /* moduleName=*/ "feature1", ON_DEMAND),
            matchedApk(feature2Apk, /* moduleName=*/ "feature2", ON_DEMAND));
  }

  @Test
  public void apkMatch_withModuleTypeFiltering_splitApks_installTimeModules() {
    DeviceSpec device = deviceWithSdk(21);
    ZipPath baseApk = ZipPath.create("master-base.apk");
    ZipPath onDemandFeatureApk = ZipPath.create("master-feature1.apk");
    ZipPath installTimeFeatureApk = ZipPath.create("master-feature2.apk");
    ZipPath fastFollowFeatureApk = ZipPath.create("master-feature3.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            createVariant(
                VariantTargeting.getDefaultInstance(),
                splitApkSet(
                    /* moduleName= */ "base",
                    splitApkDescription(ApkTargeting.getDefaultInstance(), baseApk)),
                splitApkSet(
                    /* moduleName= */ "onDemandFeature",
                    DeliveryType.ON_DEMAND,
                    /* moduleDependencies= */ ImmutableList.of(),
                    splitApkDescription(ApkTargeting.getDefaultInstance(), onDemandFeatureApk)),
                splitApkSet(
                    /* moduleName= */ "installTimeFeature",
                    INSTALL_TIME,
                    /* moduleDependencies= */ ImmutableList.of(),
                    splitApkDescription(ApkTargeting.getDefaultInstance(), installTimeFeatureApk)),
                splitApkSet(
                    /* moduleName= */ "fastFollowFeature",
                    /* deliveryType= */ DeliveryType.FAST_FOLLOW,
                    /* moduleDependencies= */ ImmutableList.of(),
                    splitApkDescription(ApkTargeting.getDefaultInstance(), fastFollowFeatureApk))));

    // By default only install-time module are matched.
    Optional<ImmutableSet<String>> allModules = Optional.empty();
    assertThat(createMatcher(device, allModules).getMatchingApks(buildApksResult))
        .containsExactly(
            matchedApk(baseApk, /* moduleName=*/ "base", INSTALL_TIME),
            matchedApk(installTimeFeatureApk, /* moduleName=*/ "installTimeFeature", INSTALL_TIME));

    Optional<ImmutableSet<String>> baseModuleOnly = Optional.of(ImmutableSet.of("base"));
    assertThat(createMatcher(device, baseModuleOnly).getMatchingApks(buildApksResult))
        .containsExactly(
            matchedApk(baseApk, /* moduleName=*/ "base", INSTALL_TIME),
            matchedApk(installTimeFeatureApk, /* moduleName=*/ "installTimeFeature", INSTALL_TIME));

    Optional<ImmutableSet<String>> installTimeModuleOnly =
        Optional.of(ImmutableSet.of("installTimeFeature"));
    assertThat(createMatcher(device, installTimeModuleOnly).getMatchingApks(buildApksResult))
        .containsExactly(
            matchedApk(baseApk, /* moduleName=*/ "base", INSTALL_TIME),
            matchedApk(installTimeFeatureApk, /* moduleName=*/ "installTimeFeature", INSTALL_TIME));

    Optional<ImmutableSet<String>> onDemandModuleOnly =
        Optional.of(ImmutableSet.of("onDemandFeature"));
    assertThat(createMatcher(device, onDemandModuleOnly).getMatchingApks(buildApksResult))
        .containsExactly(
            matchedApk(baseApk, /* moduleName=*/ "base", INSTALL_TIME),
            matchedApk(onDemandFeatureApk, /* moduleName=*/ "onDemandFeature", ON_DEMAND),
            matchedApk(installTimeFeatureApk, /* moduleName=*/ "installTimeFeature", INSTALL_TIME));

    Optional<ImmutableSet<String>> fastFollowModuleOnly =
        Optional.of(ImmutableSet.of("fastFollowFeature"));
    assertThat(createMatcher(device, fastFollowModuleOnly).getMatchingApks(buildApksResult))
        .containsExactly(
            matchedApk(baseApk, /* moduleName=*/ "base", INSTALL_TIME),
            matchedApk(fastFollowFeatureApk, /* moduleName=*/ "fastFollowFeature", FAST_FOLLOW),
            matchedApk(installTimeFeatureApk, /* moduleName=*/ "installTimeFeature", INSTALL_TIME));
  }

  @Test
  public void apkMatch_withModuleTypeFiltering_splitApks_installTimeModules_deprecatedOnDemand() {
    DeviceSpec device = deviceWithSdk(21);
    ZipPath baseApk = ZipPath.create("master-base.apk");
    ZipPath onDemandFeatureApk = ZipPath.create("master-feature1.apk");
    ApkSet onDemandFeature =
        splitApkSet(
            /* moduleName= */ "onDemandFeature",
            DeliveryType.ON_DEMAND,
            /* moduleDependencies= */ ImmutableList.of(),
            splitApkDescription(ApkTargeting.getDefaultInstance(), onDemandFeatureApk));
    ApkSet onDemandFeatureWithDeprecatedField =
        onDemandFeature.toBuilder()
            .setModuleMetadata(
                onDemandFeature.getModuleMetadata().toBuilder().setOnDemandDeprecated(true))
            .build();

    BuildApksResult buildApksResult =
        buildApksResult(
            /* bundletoolVersion= */ "0.10.0",
            createVariant(
                VariantTargeting.getDefaultInstance(),
                splitApkSet(
                    /* moduleName= */ "base",
                    splitApkDescription(ApkTargeting.getDefaultInstance(), baseApk)),
                onDemandFeatureWithDeprecatedField));

    // By default only install-time module are matched.
    Optional<ImmutableSet<String>> allModules = Optional.empty();
    assertThat(createMatcher(device, allModules).getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(baseApk));
  }

  @Test
  public void splitApk_conditionalModule_deviceEligible() {
    DeviceSpec device =
        mergeSpecs(
            deviceWithSdk(24),
            deviceFeatures("android.hardware.camera.ar", "reqGlEsVersion=0x30002"),
            deviceGroups("highRam"));

    ZipPath baseApk = ZipPath.create("base-master.apk");
    ZipPath feature1Apk = ZipPath.create("ar-master.apk");
    ZipPath feature2Apk = ZipPath.create("opengl-master.apk");
    ZipPath feature3Apk = ZipPath.create("high_end-master.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            createVariant(
                variantSdkTargeting(21),
                splitApkSet(
                    /* moduleName= */ "base",
                    splitApkDescription(ApkTargeting.getDefaultInstance(), baseApk)),
                createConditionalApkSet(
                    /* moduleName= */ "ar",
                    mergeModuleTargeting(
                        moduleFeatureTargeting("android.hardware.camera.ar"),
                        moduleMinSdkVersionTargeting(24)),
                    splitApkDescription(ApkTargeting.getDefaultInstance(), feature1Apk)),
                createConditionalApkSet(
                    /* moduleName= */ "opengl",
                    mergeModuleTargeting(
                        moduleFeatureTargeting("android.hardware.opengles.version", 0x30001),
                        moduleMinSdkVersionTargeting(21)),
                    splitApkDescription(ApkTargeting.getDefaultInstance(), feature2Apk)),
                createConditionalApkSet(
                    /* moduleName= */ "high_end",
                    mergeModuleTargeting(
                        moduleDeviceGroupsTargeting("highRam"), moduleMinSdkVersionTargeting(21)),
                    splitApkDescription(ApkTargeting.getDefaultInstance(), feature3Apk))));
    assertThat(new ApkMatcher(device).getMatchingApks(buildApksResult))
        .containsExactly(
            baseMatchedApk(baseApk),
            matchedApk(feature1Apk, /* moduleName= */ "ar", INSTALL_TIME),
            matchedApk(feature2Apk, /* moduleName= */ "opengl", INSTALL_TIME),
            matchedApk(feature3Apk, /* moduleName= */ "high_end", INSTALL_TIME));
  }

  @Test
  public void splitApk_conditionalModule_deviceNotEligible() {
    DeviceSpec device = mergeSpecs(deviceWithSdk(21), deviceFeatures("reqGlEsVersion=0x30000"));

    ZipPath baseApk = ZipPath.create("base-master.apk");
    ZipPath feature1Apk = ZipPath.create("ar-master.apk");
    ZipPath feature2Apk = ZipPath.create("opengl-master.apk");
    ZipPath feature3Apk = ZipPath.create("high_end-master.apk");
    BuildApksResult buildApksResult =
        buildApksResult(
            createVariant(
                variantSdkTargeting(21),
                splitApkSet(
                    /* moduleName= */ "base",
                    splitApkDescription(ApkTargeting.getDefaultInstance(), baseApk)),
                createConditionalApkSet(
                    /* moduleName= */ "ar",
                    mergeModuleTargeting(
                        moduleFeatureTargeting("android.hardware.camera.ar"),
                        moduleMinSdkVersionTargeting(24)),
                    splitApkDescription(ApkTargeting.getDefaultInstance(), feature1Apk)),
                createConditionalApkSet(
                    /* moduleName= */ "opengl",
                    mergeModuleTargeting(
                        moduleFeatureTargeting("android.hardware.opengles.version", 0x30001),
                        moduleMinSdkVersionTargeting(21)),
                    splitApkDescription(ApkTargeting.getDefaultInstance(), feature2Apk)),
                createConditionalApkSet(
                    /* moduleName= */ "high_end",
                    mergeModuleTargeting(
                        moduleDeviceGroupsTargeting("highRam"), moduleMinSdkVersionTargeting(21)),
                    splitApkDescription(ApkTargeting.getDefaultInstance(), feature3Apk))));
    assertThat(new ApkMatcher(device).getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(baseApk));
  }

  // Real-world complex selections.

  @Test
  public void splitAndStandaloneApks_variousDevices() {
    ZipPath standaloneX86MdpiApk = ZipPath.create("standalone-x86.mdpi.apk");
    ZipPath standaloneX86XxxhdpiApk = ZipPath.create("standalone-x86.xxxhdpi.apk");
    ZipPath standaloneArmMdpiApk = ZipPath.create("standalone-arm.mdpi.apk");
    ZipPath standaloneArmXxxhdpiApk = ZipPath.create("standalone-arm.xxxhdpi.apk");
    ZipPath baseMasterSplitApk = ZipPath.create("base-master.apk");
    ZipPath baseX86SplitApk = ZipPath.create("base-x86.apk");
    ZipPath baseArmSplitApk = ZipPath.create("base-arm.apk");
    ZipPath screenMdpiApk = ZipPath.create("screen-mdpi.apk");
    ZipPath screenXxxhdpiApk = ZipPath.create("screen-xxxhdpi.apk");
    ZipPath langsEnSplitApk = ZipPath.create("langs-en.apk");
    ZipPath langsDeSplitApk = ZipPath.create("langs-de.apk");

    BuildApksResult buildApksResult =
        buildApksResult(
            // Standalone X86 MDPI
            standaloneVariant(
                mergeVariantTargeting(
                    variantSdkTargeting(sdkVersionFrom(1), ImmutableSet.of(sdkVersionFrom(21))),
                    variantAbiTargeting(X86, ImmutableSet.of(ARMEABI)),
                    variantDensityTargeting(MDPI, ImmutableSet.of(XXXHDPI))),
                mergeApkTargeting(
                    apkAbiTargeting(X86, ImmutableSet.of(ARMEABI)),
                    apkDensityTargeting(MDPI, ImmutableSet.of(XXXHDPI))),
                standaloneX86MdpiApk),
            // Standalone X86 XXXHDI
            standaloneVariant(
                mergeVariantTargeting(
                    variantSdkTargeting(sdkVersionFrom(1), ImmutableSet.of(sdkVersionFrom(21))),
                    variantAbiTargeting(X86, ImmutableSet.of(ARMEABI)),
                    variantDensityTargeting(XXXHDPI, ImmutableSet.of(MDPI))),
                mergeApkTargeting(
                    apkAbiTargeting(X86, ImmutableSet.of(ARMEABI)),
                    apkDensityTargeting(XXXHDPI, ImmutableSet.of(MDPI))),
                standaloneX86XxxhdpiApk),
            // Standalone ARM MDPI
            standaloneVariant(
                mergeVariantTargeting(
                    variantSdkTargeting(sdkVersionFrom(1), ImmutableSet.of(sdkVersionFrom(21))),
                    variantAbiTargeting(ARMEABI, ImmutableSet.of(X86)),
                    variantDensityTargeting(MDPI, ImmutableSet.of(XXXHDPI))),
                mergeApkTargeting(
                    apkAbiTargeting(ARMEABI, ImmutableSet.of(X86)),
                    apkDensityTargeting(MDPI, ImmutableSet.of(XXXHDPI))),
                standaloneArmMdpiApk),
            // Standalone ARM XXXHDI
            standaloneVariant(
                mergeVariantTargeting(
                    variantSdkTargeting(sdkVersionFrom(1), ImmutableSet.of(sdkVersionFrom(21))),
                    variantAbiTargeting(ARMEABI, ImmutableSet.of(X86)),
                    variantDensityTargeting(XXXHDPI, ImmutableSet.of(MDPI))),
                mergeApkTargeting(
                    apkAbiTargeting(ARMEABI, ImmutableSet.of(X86)),
                    apkDensityTargeting(XXXHDPI, ImmutableSet.of(MDPI))),
                standaloneArmXxxhdpiApk),
            // Splits L+
            splitApkVariant(
                variantSdkTargeting(sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(1))),
                splitApkSet(
                    /* moduleName= */ "base",
                    splitApkDescription(ApkTargeting.getDefaultInstance(), baseMasterSplitApk),
                    splitApkDescription(
                        apkAbiTargeting(X86, ImmutableSet.of(ARMEABI)), baseX86SplitApk),
                    splitApkDescription(
                        apkAbiTargeting(ARMEABI, ImmutableSet.of(X86)), baseArmSplitApk)),
                splitApkSet(
                    /* moduleName= */ "screen",
                    splitApkDescription(
                        apkDensityTargeting(MDPI, ImmutableSet.of(XXXHDPI)), screenMdpiApk),
                    splitApkDescription(
                        apkDensityTargeting(XXXHDPI, ImmutableSet.of(MDPI)), screenXxxhdpiApk)),
                splitApkSet(
                    /* moduleName= */ "langs",
                    splitApkDescription(apkLanguageTargeting("en"), langsEnSplitApk),
                    splitApkDescription(apkLanguageTargeting("de"), langsDeSplitApk))));

    DeviceSpec preLX86MdpiDevice =
        mergeSpecs(sdkVersion(19), abis("x86"), density(MDPI), locales("en"));
    assertThat(new ApkMatcher(preLX86MdpiDevice).getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(standaloneX86MdpiApk));

    DeviceSpec preLArmXxxdpiDevice =
        mergeSpecs(sdkVersion(19), abis("armeabi"), density(XXXHDPI), locales("en"));
    assertThat(new ApkMatcher(preLArmXxxdpiDevice).getMatchingApks(buildApksResult))
        .containsExactly(baseMatchedApk(standaloneArmXxxhdpiApk));

    DeviceSpec lX86MdpiEnDevice =
        mergeSpecs(sdkVersion(21), abis("x86"), density(MDPI), locales("en"));
    assertThat(new ApkMatcher(lX86MdpiEnDevice).getMatchingApks(buildApksResult))
        .containsExactly(
            baseMatchedApk(baseMasterSplitApk),
            baseMatchedApk(baseX86SplitApk),
            matchedApk(screenMdpiApk, /* moduleName=*/ "screen", INSTALL_TIME),
            matchedApk(langsEnSplitApk, /* moduleName=*/ "langs", INSTALL_TIME));

    // MIPS ABI is not supported by the app.
    DeviceSpec preLMipsDevice =
        mergeSpecs(sdkVersion(19), abis("mips"), density(MDPI), locales("en"));
    Throwable exception =
        assertThrows(
            IncompatibleDeviceException.class,
            () -> new ApkMatcher(preLMipsDevice).getMatchingApks(buildApksResult));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "The app doesn't support ABI architectures of the device. Device ABIs: [mips], "
                + "app ABIs: [x86, armeabi]");
  }

  @Test
  public void matchesModuleSplit_incompatibleDeviceThrows() {
    // MIPS ABI is not supported by the split.
    DeviceSpec mipsDevice = mergeSpecs(sdkVersion(21), abis("mips"), density(MDPI), locales("en"));

    ModuleSplit moduleSplit =
        ModuleSplit.builder()
            .setAndroidManifest(AndroidManifest.create(androidManifest("com.test.app")))
            .setEntries(ImmutableList.of())
            .setMasterSplit(false)
            .setSplitType(SplitType.SPLIT)
            .setModuleName(BundleModuleName.create("base"))
            .setApkTargeting(apkAbiTargeting(AbiAlias.ARM64_V8A, ImmutableSet.of(AbiAlias.X86)))
            .setVariantTargeting(variantSdkTargeting(21))
            .build();

    Throwable exception =
        assertThrows(
            IncompatibleDeviceException.class,
            () -> new ApkMatcher(mipsDevice).matchesModuleSplitByTargeting(moduleSplit));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "The app doesn't support ABI architectures of the device. Device ABIs: [mips], "
                + "app ABIs: [arm64-v8a, x86]");
  }

  @Test
  public void assetModuleMatch() {
    String installTimeModule1 = "installtime_assetmodule1";
    String installTimeModule2 = "installtime_assetmodule2";
    String onDemandModule = "ondemand_assetmodule";
    ZipPath installTimeMasterApk1 = ZipPath.create(installTimeModule1 + "-master.apk");
    ZipPath installTimeEnApk1 = ZipPath.create(installTimeModule1 + "-en.apk");
    ZipPath installTimeMasterApk2 = ZipPath.create(installTimeModule2 + "-master.apk");
    ZipPath installTimeEnApk2 = ZipPath.create(installTimeModule2 + "-en.apk");
    ZipPath onDemandMasterApk = ZipPath.create(onDemandModule + "-master.apk");
    ZipPath baseApk = ZipPath.create("base-master.apk");
    BuildApksResult buildApksResult =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                oneApkSplitApkVariant(
                    variantSdkTargeting(sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(1))),
                    ApkTargeting.getDefaultInstance(),
                    baseApk))
            .addAssetSliceSet(
                AssetSliceSet.newBuilder()
                    .setAssetModuleMetadata(
                        AssetModuleMetadata.newBuilder()
                            .setName(installTimeModule1)
                            .setDeliveryType(INSTALL_TIME))
                    .addApkDescription(
                        splitApkDescription(
                            ApkTargeting.getDefaultInstance(), installTimeMasterApk1))
                    .addApkDescription(
                        splitApkDescription(apkLanguageTargeting("en"), installTimeEnApk1)))
            .addAssetSliceSet(
                AssetSliceSet.newBuilder()
                    .setAssetModuleMetadata(
                        AssetModuleMetadata.newBuilder()
                            .setName(installTimeModule2)
                            .setDeliveryType(INSTALL_TIME))
                    .addApkDescription(
                        splitApkDescription(
                            ApkTargeting.getDefaultInstance(), installTimeMasterApk2))
                    .addApkDescription(
                        splitApkDescription(apkLanguageTargeting("en"), installTimeEnApk2)))
            .addAssetSliceSet(
                AssetSliceSet.newBuilder()
                    .setAssetModuleMetadata(
                        AssetModuleMetadata.newBuilder()
                            .setName(onDemandModule)
                            .setDeliveryType(DeliveryType.ON_DEMAND))
                    .addApkDescription(
                        splitApkDescription(ApkTargeting.getDefaultInstance(), onDemandMasterApk)))
            .build();

    DeviceSpec enDevice = lDeviceWithLocales("en");
    assertThat(new ApkMatcher(enDevice).getMatchingApks(buildApksResult))
        .containsExactly(
            baseMatchedApk(baseApk),
            matchedApk(installTimeMasterApk1, installTimeModule1, INSTALL_TIME),
            matchedApk(installTimeEnApk1, installTimeModule1, INSTALL_TIME),
            matchedApk(installTimeMasterApk2, installTimeModule2, INSTALL_TIME),
            matchedApk(installTimeEnApk2, installTimeModule2, INSTALL_TIME));

    DeviceSpec esDevice = lDeviceWithLocales("fr");
    assertThat(new ApkMatcher(esDevice).getMatchingApks(buildApksResult))
        .containsExactly(
            baseMatchedApk(baseApk),
            matchedApk(installTimeMasterApk1, installTimeModule1, INSTALL_TIME),
            matchedApk(installTimeMasterApk2, installTimeModule2, INSTALL_TIME));

    assertThat(
            createMatcher(
                    enDevice, Optional.of(ImmutableSet.of(installTimeModule1, onDemandModule)))
                .getMatchingApks(buildApksResult))
        .containsExactly(
            baseMatchedApk(baseApk),
            matchedApk(installTimeMasterApk1, installTimeModule1, INSTALL_TIME),
            matchedApk(installTimeEnApk1, installTimeModule1, INSTALL_TIME),
            matchedApk(installTimeMasterApk2, installTimeModule2, INSTALL_TIME),
            matchedApk(installTimeEnApk2, installTimeModule2, INSTALL_TIME),
            matchedApk(onDemandMasterApk, onDemandModule, ON_DEMAND));

    assertThat(
            createMatcher(enDevice, Optional.of(ImmutableSet.of(onDemandModule)))
                .getMatchingApks(buildApksResult))
        .containsExactly(
            baseMatchedApk(baseApk),
            matchedApk(installTimeMasterApk1, installTimeModule1, INSTALL_TIME),
            matchedApk(installTimeEnApk1, installTimeModule1, INSTALL_TIME),
            matchedApk(installTimeMasterApk2, installTimeModule2, INSTALL_TIME),
            matchedApk(installTimeEnApk2, installTimeModule2, INSTALL_TIME),
            matchedApk(onDemandMasterApk, onDemandModule, ON_DEMAND));

    assertThat(
            createMatcherExcludingInstallTimeAssetModules(
                    enDevice, Optional.of(ImmutableSet.of(installTimeModule1, onDemandModule)))
                .getMatchingApks(buildApksResult))
        .containsExactly(
            baseMatchedApk(baseApk),
            matchedApk(installTimeMasterApk1, installTimeModule1, INSTALL_TIME),
            matchedApk(installTimeEnApk1, installTimeModule1, INSTALL_TIME),
            matchedApk(onDemandMasterApk, onDemandModule, ON_DEMAND));
  }

  @Test
  public void ensureDensityAndAbiSplitIsMatchedPerEachModule_split() {
    ZipPath baseMasterSplitApk = ZipPath.create("base-master.apk");
    ZipPath baseX86SplitApk = ZipPath.create("base-x86.apk");
    ZipPath baseMdpiSplitApk = ZipPath.create("base-mdpi.apk");
    ZipPath baseHdpiSplitApk = ZipPath.create("base-hdpi.apk");
    ZipPath screenMdpiApk = ZipPath.create("screen-mdpi.apk");

    BuildApksResult buildApksResult =
        buildApksResult(
            splitApkVariant(
                variantSdkTargeting(sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(1))),
                splitApkSet(
                    /* moduleName= */ "base",
                    splitApkDescription(ApkTargeting.getDefaultInstance(), baseMasterSplitApk),
                    splitApkDescription(
                        apkAbiTargeting(X86, ImmutableSet.of(X86_64)), baseX86SplitApk),
                    splitApkDescription(
                        apkDensityTargeting(MDPI, ImmutableSet.of(HDPI, XHDPI)), baseMdpiSplitApk),
                    splitApkDescription(
                        apkDensityTargeting(HDPI, ImmutableSet.of(MDPI, XHDPI)), baseHdpiSplitApk)),
                splitApkSet(
                    /* moduleName= */ "screen",
                    splitApkDescription(
                        apkDensityTargeting(MDPI, ImmutableSet.of(HDPI)), screenMdpiApk))));

    Optional<ImmutableSet<String>> allModules = Optional.of(ImmutableSet.of("base", "screen"));

    assertThat(
            createSafeMatcher(mergeSpecs(density(MDPI), abis("x86")), allModules)
                .getMatchingApks(buildApksResult))
        .containsExactly(
            matchedApk(baseMasterSplitApk, "base", INSTALL_TIME),
            matchedApk(baseX86SplitApk, "base", INSTALL_TIME),
            matchedApk(baseMdpiSplitApk, "base", INSTALL_TIME),
            matchedApk(screenMdpiApk, "screen", INSTALL_TIME));

    IncompatibleDeviceException baseException =
        assertThrows(
            IncompatibleDeviceException.class,
            () ->
                createSafeMatcher(mergeSpecs(density(MDPI), abis("x86_64")), allModules)
                    .getMatchingApks(buildApksResult));
    assertThat(baseException)
        .hasMessageThat()
        .isEqualTo(
            "Missing APKs for [ABI] dimensions in the module 'base' for the provided device.");

    IncompatibleDeviceException screenException =
        assertThrows(
            IncompatibleDeviceException.class,
            () ->
                createSafeMatcher(mergeSpecs(density(HDPI), abis("x86")), allModules)
                    .getMatchingApks(buildApksResult));
    assertThat(screenException)
        .hasMessageThat()
        .isEqualTo(
            "Missing APKs for [SCREEN_DENSITY] dimensions in the module 'screen' for the provided"
                + " device.");

    IncompatibleDeviceException multipleDimensionsException =
        assertThrows(
            IncompatibleDeviceException.class,
            () ->
                createSafeMatcher(mergeSpecs(density(XHDPI), abis("x86_64")), allModules)
                    .getMatchingApks(buildApksResult));
    assertThat(multipleDimensionsException)
        .hasMessageThat()
        .isEqualTo(
            "Missing APKs for [ABI, SCREEN_DENSITY] dimensions in the module 'base' for the"
                + " provided device.");
  }

  private static BuildApksResult buildApksResult(Variant... variants) {
    return buildApksResult(BundleToolVersion.getCurrentVersion().toString(), variants);
  }

  private static BuildApksResult buildApksResult(String bundletoolVersion, Variant... variants) {
    return BuildApksResult.newBuilder()
        .addAllVariant(Arrays.asList(variants))
        .setBundletool(Bundletool.newBuilder().setVersion(bundletoolVersion))
        .build();
  }

  private static Variant splitApkVariant(
      VariantTargeting variantTargeting, ApkSet... splitApkSets) {
    return createVariant(variantTargeting, splitApkSets);
  }

  private static Variant oneApkSplitApkVariant(
      VariantTargeting variantTargeting, ApkTargeting apkTargeting, ZipPath apkPath) {
    return createVariant(
        variantTargeting,
        splitApkSet(/* moduleName= */ "base", splitApkDescription(apkTargeting, apkPath)));
  }

  private static GeneratedApk matchedApk(
      ZipPath path, String moduleName, DeliveryType deliveryType) {
    return GeneratedApk.create(path, moduleName, deliveryType);
  }

  private static GeneratedApk baseMatchedApk(ZipPath path) {
    return matchedApk(path, /* moduleName=*/ "base", INSTALL_TIME);
  }

  private static ApkMatcher createMatcher(DeviceSpec spec, Optional<ImmutableSet<String>> modules) {
    return new ApkMatcher(
        spec,
        modules,
        /* includeInstallTimeAssetModules= */ true,
        /* matchInstant= */ false,
        /* ensureDensityAndAbiApksMatched= */ false);
  }

  private static ApkMatcher createMatcherExcludingInstallTimeAssetModules(
      DeviceSpec spec, Optional<ImmutableSet<String>> modules) {
    return new ApkMatcher(
        spec,
        modules,
        /* includeInstallTimeAssetModules= */ false,
        /* matchInstant= */ false,
        /* ensureDensityAndAbiApksMatched= */ false);
  }

  private static ApkMatcher createInstantMatcher(
      DeviceSpec spec, Optional<ImmutableSet<String>> modules) {
    return new ApkMatcher(
        spec,
        modules,
        /* includeInstallTimeAssetModules= */ true,
        /* matchInstant= */ true,
        /* ensureDensityAndAbiApksMatched= */ false);
  }

  private static ApkMatcher createSafeMatcher(
      DeviceSpec spec, Optional<ImmutableSet<String>> modules) {
    return new ApkMatcher(
        spec,
        modules,
        /* includeInstallTimeAssetModules= */ true,
        /* matchInstant= */ false,
        /* ensureDensityAndAbiApksMatched= */ true);
  }
}
