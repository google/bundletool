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

import static com.android.bundle.Targeting.Abi.AbiAlias.ARMEABI;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86_64;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.HDPI;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.MDPI;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.XXXHDPI;
import static com.android.tools.build.bundletool.testing.ApkSetUtils.splitApkSet;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createVariant;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.multiAbiTargetingApexVariant;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.splitApkDescription;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.standaloneVariant;
import static com.android.tools.build.bundletool.testing.DeviceFactory.abis;
import static com.android.tools.build.bundletool.testing.DeviceFactory.density;
import static com.android.tools.build.bundletool.testing.DeviceFactory.locales;
import static com.android.tools.build.bundletool.testing.DeviceFactory.mergeSpecs;
import static com.android.tools.build.bundletool.testing.DeviceFactory.sdkRuntimeSupported;
import static com.android.tools.build.bundletool.testing.DeviceFactory.sdkVersion;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkDensityTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeApkTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.multiAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkRuntimeVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantDensityTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantSdkTargeting;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Commands.Variant;
import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.MultiAbiTargeting;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.IncompatibleDeviceException;
import com.android.tools.build.bundletool.model.utils.Versions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class VariantMatcherTest {

  @Test
  public void getAllMatchingVariants_emptyDeviceSpecMatchesAllVariants() {
    ZipPath standaloneArmXxxhdpiApk = ZipPath.create("standalone-arm.xxxhdpi.apk");
    ZipPath baseMasterSplitApk = ZipPath.create("base-master.apk");
    ZipPath baseArmSplitApk = ZipPath.create("base-arm.apk");
    ZipPath screenXxxhdpiApk = ZipPath.create("screen-xxxhdpi.apk");

    ImmutableList<Variant> variants =
        ImmutableList.of(
            // Standalone ARM XXXHDPI
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
            createVariant(
                variantSdkTargeting(sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(1))),
                splitApkSet(
                    /* moduleName= */ "base",
                    splitApkDescription(ApkTargeting.getDefaultInstance(), baseMasterSplitApk),
                    splitApkDescription(
                        apkAbiTargeting(ARMEABI, ImmutableSet.of(X86)), baseArmSplitApk)),
                splitApkSet(
                    /* moduleName= */ "screen",
                    splitApkDescription(
                        apkDensityTargeting(XXXHDPI, ImmutableSet.of(MDPI)), screenXxxhdpiApk))));
    BuildApksResult buildApksResult = BuildApksResult.newBuilder().addAllVariant(variants).build();

    assertThat(
            new VariantMatcher(DeviceSpec.getDefaultInstance())
                .getAllMatchingVariants(buildApksResult))
        .isEqualTo(variants);
  }

  @Test
  public void getAllMatchingVariants_partialDeviceSpec() {
    ZipPath standaloneArmXxxhdpiApk = ZipPath.create("standalone-arm.xxxhdpi.apk");
    ZipPath baseMasterSplitApk = ZipPath.create("base-master.apk");
    ZipPath baseArmSplitApk = ZipPath.create("base-arm.apk");
    ZipPath screenXxxhdpiApk = ZipPath.create("screen-xxxhdpi.apk");

    Variant standaloneVariant =
        standaloneVariant(
            mergeVariantTargeting(
                variantSdkTargeting(sdkVersionFrom(1), ImmutableSet.of(sdkVersionFrom(21))),
                variantAbiTargeting(ARMEABI, ImmutableSet.of(X86)),
                variantDensityTargeting(XXXHDPI, ImmutableSet.of(MDPI))),
            mergeApkTargeting(
                apkAbiTargeting(ARMEABI, ImmutableSet.of(X86)),
                apkDensityTargeting(XXXHDPI, ImmutableSet.of(MDPI))),
            standaloneArmXxxhdpiApk);

    Variant splitVariant =
        createVariant(
            variantSdkTargeting(sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(1))),
            splitApkSet(
                /* moduleName= */ "base",
                splitApkDescription(ApkTargeting.getDefaultInstance(), baseMasterSplitApk),
                splitApkDescription(
                    apkAbiTargeting(ARMEABI, ImmutableSet.of(X86)), baseArmSplitApk)),
            splitApkSet(
                /* moduleName= */ "screen",
                splitApkDescription(
                    apkDensityTargeting(XXXHDPI, ImmutableSet.of(MDPI)), screenXxxhdpiApk)));

    ImmutableList<Variant> variants = ImmutableList.of(standaloneVariant, splitVariant);

    BuildApksResult buildApksResult = BuildApksResult.newBuilder().addAllVariant(variants).build();

    DeviceSpec preLDevice = mergeSpecs(sdkVersion(19));
    assertThat(new VariantMatcher(preLDevice).getAllMatchingVariants(buildApksResult))
        .containsExactly(standaloneVariant);

    DeviceSpec preLXXXHDPIDevice = mergeSpecs(sdkVersion(19), density(XXXHDPI));
    assertThat(new VariantMatcher(preLXXXHDPIDevice).getAllMatchingVariants(buildApksResult))
        .containsExactly(standaloneVariant);

    DeviceSpec preLX86Device = mergeSpecs(sdkVersion(19), abis("x86"));
    assertThat(new VariantMatcher(preLX86Device).getAllMatchingVariants(buildApksResult)).isEmpty();

    DeviceSpec postLDevice = mergeSpecs(sdkVersion(21));
    assertThat(new VariantMatcher(postLDevice).getAllMatchingVariants(buildApksResult))
        .containsExactly(splitVariant);
  }

  @Test
  public void getAllMatchingVariants_fullDeviceSpec() {
    ZipPath standaloneX86MdpiApk = ZipPath.create("standalone-x86.mdpi.apk");
    ZipPath baseMasterSplitApk = ZipPath.create("base-master.apk");
    ZipPath baseArmSplitApk = ZipPath.create("base-arm.apk");
    ZipPath screenXxxhdpiApk = ZipPath.create("screen-xxxhdpi.apk");

    Variant standaloneX86MdpiVariant =
        standaloneVariant(
            mergeVariantTargeting(
                variantSdkTargeting(sdkVersionFrom(1), ImmutableSet.of(sdkVersionFrom(21))),
                variantAbiTargeting(X86, ImmutableSet.of(ARMEABI)),
                variantDensityTargeting(MDPI, ImmutableSet.of(XXXHDPI))),
            mergeApkTargeting(
                apkAbiTargeting(X86, ImmutableSet.of(ARMEABI)),
                apkDensityTargeting(MDPI, ImmutableSet.of(XXXHDPI))),
            standaloneX86MdpiApk);
    Variant splitVariant =
        createVariant(
            variantSdkTargeting(sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(1))),
            splitApkSet(
                /* moduleName= */ "base",
                splitApkDescription(ApkTargeting.getDefaultInstance(), baseMasterSplitApk),
                splitApkDescription(
                    apkAbiTargeting(ARMEABI, ImmutableSet.of(X86)), baseArmSplitApk)),
            splitApkSet(
                /* moduleName= */ "screen",
                splitApkDescription(
                    apkDensityTargeting(XXXHDPI, ImmutableSet.of(MDPI)), screenXxxhdpiApk)));

    ImmutableList<Variant> variants = ImmutableList.of(standaloneX86MdpiVariant, splitVariant);

    BuildApksResult buildApksResult = BuildApksResult.newBuilder().addAllVariant(variants).build();

    DeviceSpec preLX86MdpiDevice =
        mergeSpecs(sdkVersion(19), abis("x86"), density(MDPI), locales("en"));
    assertThat(new VariantMatcher(preLX86MdpiDevice).getAllMatchingVariants(buildApksResult))
        .containsExactly(standaloneX86MdpiVariant);

    DeviceSpec preLX86HdpiDevice =
        mergeSpecs(sdkVersion(19), abis("x86"), density(HDPI), locales("en"));
    assertThat(new VariantMatcher(preLX86HdpiDevice).getAllMatchingVariants(buildApksResult))
        .isEmpty();

    DeviceSpec postLDevice = mergeSpecs(sdkVersion(21), abis("x86"), density(MDPI), locales("en"));
    assertThat(new VariantMatcher(postLDevice).getAllMatchingVariants(buildApksResult))
        .containsExactly(splitVariant);
  }

  @Test
  public void getAllMatchingVariants_apexVariants_noMatch_throws() {
    ZipPath x86Apk = ZipPath.create("standalone-x86.apk");
    ZipPath x64X86Apk = ZipPath.create("standalone-x86_64.x86.apk");

    ImmutableSet<ImmutableSet<AbiAlias>> x86Set = ImmutableSet.of(ImmutableSet.of(X86));
    ImmutableSet<ImmutableSet<AbiAlias>> x64X86Set = ImmutableSet.of(ImmutableSet.of(X86_64, X86));

    MultiAbiTargeting x86Targeting = multiAbiTargeting(x86Set, x64X86Set);
    MultiAbiTargeting x64X86Targeting = multiAbiTargeting(x64X86Set, x86Set);

    Variant x86Variant = multiAbiTargetingApexVariant(x86Targeting, x86Apk);
    Variant x64X86Variant = multiAbiTargetingApexVariant(x64X86Targeting, x64X86Apk);
    BuildApksResult buildApksResult =
        BuildApksResult.newBuilder()
            .addAllVariant(ImmutableList.of(x86Variant, x64X86Variant))
            .build();

    IncompatibleDeviceException e =
        assertThrows(
            IncompatibleDeviceException.class,
            () ->
                new VariantMatcher(abis("x86_64", "armeabi-v7a"))
                    .getAllMatchingVariants(buildApksResult));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "No set of ABI architectures that the app supports is contained in the ABI "
                + "architecture set of the device");
  }

  @Test
  public void getAllMatchingVariants_apexVariants_fullDeviceSpec() {
    ZipPath x86Apk = ZipPath.create("standalone-x86.apk");
    ZipPath x64X86Apk = ZipPath.create("standalone-x86_64.x86.apk");

    ImmutableSet<ImmutableSet<AbiAlias>> x86Set = ImmutableSet.of(ImmutableSet.of(X86));
    ImmutableSet<ImmutableSet<AbiAlias>> x64X86Set = ImmutableSet.of(ImmutableSet.of(X86_64, X86));

    MultiAbiTargeting x86Targeting = multiAbiTargeting(x86Set, x64X86Set);
    MultiAbiTargeting x64X86Targeting = multiAbiTargeting(x64X86Set, x86Set);

    Variant x86Variant = multiAbiTargetingApexVariant(x86Targeting, x86Apk);
    Variant x64X86Variant = multiAbiTargetingApexVariant(x64X86Targeting, x64X86Apk);
    BuildApksResult buildApksResult =
        BuildApksResult.newBuilder()
            .addAllVariant(ImmutableList.of(x86Variant, x64X86Variant))
            .build();

    assertThat(new VariantMatcher(abis("x86")).getAllMatchingVariants(buildApksResult))
        .containsExactly(x86Variant);
    assertThat(new VariantMatcher(abis("x86_64", "x86")).getAllMatchingVariants(buildApksResult))
        .containsExactly(x64X86Variant);
    assertThat(
            new VariantMatcher(abis("x86_64", "x86", "armeabi-v7a"))
                .getAllMatchingVariants(buildApksResult))
        .containsExactly(x64X86Variant);
    // Other device specs don't affect the matching variant.
    assertThat(
            new VariantMatcher(mergeSpecs(abis("x86"), density(HDPI)))
                .getAllMatchingVariants(buildApksResult))
        .containsExactly(x86Variant);
  }

  @Test
  public void getMatchingVariant_sameSdkVersionDifferentSdkRuntime_sdkRuntimeReturned() {
    ZipPath mainApk = ZipPath.create("main.apk");
    Variant sdkRuntimeVariant =
        createVariant(
            sdkRuntimeVariantTargeting(Versions.ANDROID_T_API_VERSION),
            splitApkSet(
                /* moduleName= */ "base",
                splitApkDescription(ApkTargeting.getDefaultInstance(), mainApk)));
    Variant nonSdkRuntimeVariant =
        createVariant(
            variantSdkTargeting(Versions.ANDROID_L_API_VERSION),
            splitApkSet(
                /* moduleName= */ "base",
                splitApkDescription(ApkTargeting.getDefaultInstance(), mainApk)));
    BuildApksResult buildApksResult =
        BuildApksResult.newBuilder()
            .addAllVariant(ImmutableList.of(sdkRuntimeVariant, nonSdkRuntimeVariant))
            .build();
    DeviceSpec deviceWithSdkRuntime =
        mergeSpecs(
            sdkVersion(Versions.ANDROID_T_API_VERSION), sdkRuntimeSupported(/* supported= */ true));
    VariantMatcher variantMatcher = new VariantMatcher(deviceWithSdkRuntime);

    assertThat(variantMatcher.getMatchingVariant(buildApksResult)).hasValue(sdkRuntimeVariant);
  }

  @Test
  public void getMatchingVariant_androidLDeviceWithSdkRuntimeVariant_noSdkRuntimeReturned() {
    ZipPath mainApk = ZipPath.create("main.apk");
    Variant sdkRuntimeVariant =
        createVariant(
            sdkRuntimeVariantTargeting(Versions.ANDROID_T_API_VERSION),
            splitApkSet(
                /* moduleName= */ "base",
                splitApkDescription(ApkTargeting.getDefaultInstance(), mainApk)));
    Variant nonSdkRuntimeVariant =
        createVariant(
            variantSdkTargeting(Versions.ANDROID_L_API_VERSION),
            splitApkSet(
                /* moduleName= */ "base",
                splitApkDescription(ApkTargeting.getDefaultInstance(), mainApk)));
    BuildApksResult buildApksResult =
        BuildApksResult.newBuilder()
            .addAllVariant(ImmutableList.of(sdkRuntimeVariant, nonSdkRuntimeVariant))
            .build();
    DeviceSpec deviceWithoutSdkRuntime = mergeSpecs(sdkVersion(Versions.ANDROID_L_API_VERSION));
    VariantMatcher variantMatcher = new VariantMatcher(deviceWithoutSdkRuntime);

    assertThat(variantMatcher.getMatchingVariant(buildApksResult)).hasValue(nonSdkRuntimeVariant);
  }

  @Test
  public void getMatchingVariant_differentSdkVersionSameSdkRuntime_bestSdkVersionMatched() {
    ZipPath mainApk = ZipPath.create("main.apk");
    int androidU = Versions.ANDROID_T_API_VERSION + 1;
    DeviceSpec androidUDevice =
        mergeSpecs(sdkVersion(androidU), sdkRuntimeSupported(/* supported= */ true));
    Variant sdkRuntimeVariantWithNonOptimalAndroidVersion =
        createVariant(
            sdkRuntimeVariantTargeting(
                Versions.ANDROID_T_API_VERSION,
                /* alternativeSdkVersions= */ ImmutableSet.of(androidU)),
            splitApkSet(
                /* moduleName= */ "base",
                splitApkDescription(ApkTargeting.getDefaultInstance(), mainApk)));
    Variant sdkRuntimeVariantWithMatchingAndroidVersion =
        createVariant(
            sdkRuntimeVariantTargeting(
                androidU,
                /* alternativeSdkVersions= */ ImmutableSet.of(Versions.ANDROID_T_API_VERSION)),
            splitApkSet(
                /* moduleName= */ "base",
                splitApkDescription(ApkTargeting.getDefaultInstance(), mainApk)));
    BuildApksResult buildApksResult =
        BuildApksResult.newBuilder()
            .addAllVariant(
                ImmutableList.of(
                    sdkRuntimeVariantWithNonOptimalAndroidVersion,
                    sdkRuntimeVariantWithMatchingAndroidVersion))
            .build();
    VariantMatcher variantMatcher = new VariantMatcher(androidUDevice);

    assertThat(variantMatcher.getMatchingVariant(buildApksResult))
        .hasValue(sdkRuntimeVariantWithMatchingAndroidVersion);
  }

  @Test
  public void getMatchingVariant_defaultDeviceSpecWithRuntimeEnabledApp_allVariantsMatched() {
    ZipPath mainApk = ZipPath.create("main.apk");
    Variant sdkRuntimeVariant =
        createVariant(
            sdkRuntimeVariantTargeting(Versions.ANDROID_T_API_VERSION),
            splitApkSet(
                /* moduleName= */ "base",
                splitApkDescription(ApkTargeting.getDefaultInstance(), mainApk)));
    Variant nonSdkRuntimeVariant =
        createVariant(
            variantSdkTargeting(Versions.ANDROID_L_API_VERSION),
            splitApkSet(
                /* moduleName= */ "base",
                splitApkDescription(ApkTargeting.getDefaultInstance(), mainApk)));
    BuildApksResult buildApksResult =
        BuildApksResult.newBuilder()
            .addAllVariant(ImmutableList.of(sdkRuntimeVariant, nonSdkRuntimeVariant))
            .build();

    ImmutableList<Variant> matched =
        new VariantMatcher(DeviceSpec.getDefaultInstance()).getAllMatchingVariants(buildApksResult);

    assertThat(matched).containsExactly(sdkRuntimeVariant, nonSdkRuntimeVariant);
  }
}
