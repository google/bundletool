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

package com.android.tools.build.bundletool.testing;

import static com.android.tools.build.bundletool.testing.TargetingUtils.apkMultiAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantMultiAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantSdkTargeting;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.bundle.Commands.ApexApkMetadata;
import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.ApkSet;
import com.android.bundle.Commands.ArchivedApkMetadata;
import com.android.bundle.Commands.AssetModuleMetadata;
import com.android.bundle.Commands.AssetSliceSet;
import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Commands.BuildSdkApksResult;
import com.android.bundle.Commands.DeliveryType;
import com.android.bundle.Commands.ModuleMetadata;
import com.android.bundle.Commands.SplitApkMetadata;
import com.android.bundle.Commands.StandaloneApkMetadata;
import com.android.bundle.Commands.SystemApkMetadata;
import com.android.bundle.Commands.Variant;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.ModuleTargeting;
import com.android.bundle.Targeting.MultiAbiTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.io.ZipBuilder;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

/** Helpers related to creating APKs archives in tests. */
public final class ApksArchiveHelpers {

  private static final byte[] TEST_BYTES = new byte[100];

  /** Create an app APK set and serialize it to the provided path. */
  public static Path createApksArchiveFile(BuildApksResult result, Path location) throws Exception {
    ZipBuilder archiveBuilder = new ZipBuilder();

    apkDescriptionStream(result)
        .forEach(
            apkDesc ->
                archiveBuilder.addFileWithContent(ZipPath.create(apkDesc.getPath()), TEST_BYTES));
    archiveBuilder.addFileWithProtoContent(ZipPath.create("toc.pb"), result);

    return archiveBuilder.writeTo(location);
  }

  /** Create an SDK APK set and serialize it to the provided path. */
  public static Path createSdkApksArchiveFile(BuildSdkApksResult result, Path location)
      throws Exception {
    ZipBuilder archiveBuilder = new ZipBuilder();

    result.getVariantList().stream()
        .flatMap(variant -> variant.getApkSetList().stream())
        .flatMap(apkSet -> apkSet.getApkDescriptionList().stream())
        .forEach(
            apkDesc ->
                archiveBuilder.addFileWithContent(ZipPath.create(apkDesc.getPath()), TEST_BYTES));
    archiveBuilder.addFileWithProtoContent(ZipPath.create("toc.pb"), result);

    return archiveBuilder.writeTo(location);
  }

  public static Path createApksDirectory(BuildApksResult result, Path location) throws Exception {
    ImmutableList<ApkDescription> apkDescriptions =
        apkDescriptionStream(result).collect(toImmutableList());

    for (ApkDescription apkDescription : apkDescriptions) {
      Path apkPath = location.resolve(apkDescription.getPath());
      Files.createDirectories(apkPath.getParent());
      Files.write(apkPath, TEST_BYTES);
    }
    Files.write(location.resolve("toc.pb"), result.toByteArray());

    return location;
  }

  public static Variant createVariant(VariantTargeting variantTargeting, ApkSet... apkSets) {
    return Variant.newBuilder()
        .setTargeting(variantTargeting)
        .addAllApkSet(Arrays.asList(apkSets))
        .build();
  }

  public static Variant createVariantForSingleSplitApk(
      VariantTargeting variantTargeting, ApkTargeting apkTargeting, ZipPath apkPath) {
    return createVariant(
        variantTargeting,
        createSplitApkSet("base", createMasterApkDescription(apkTargeting, apkPath)));
  }

  public static Variant standaloneVariant(
      VariantTargeting variantTargeting, ApkTargeting apkTargeting, ZipPath apkPath) {
    // A standalone variant has only a single APK with module named "base".
    return createVariant(
        variantTargeting,
        ApkSet.newBuilder()
            .setModuleMetadata(
                ModuleMetadata.newBuilder()
                    .setName("base")
                    .setDeliveryType(DeliveryType.INSTALL_TIME))
            .addApkDescription(
                ApkDescription.newBuilder()
                    .setTargeting(apkTargeting)
                    .setPath(apkPath.toString())
                    // Contents of the standalone APK metadata is not important for these tests
                    // as long as the field is set.
                    .setStandaloneApkMetadata(StandaloneApkMetadata.getDefaultInstance()))
            .build());
  }

  public static Variant apexVariant(
      VariantTargeting variantTargeting, ApkTargeting apkTargeting, ZipPath apkPath) {
    // An apex variant has only a single APK with module named "base".
    return createVariant(
        variantTargeting,
        ApkSet.newBuilder()
            .setModuleMetadata(
                ModuleMetadata.newBuilder()
                    .setName("base")
                    .setDeliveryType(DeliveryType.INSTALL_TIME))
            .addApkDescription(
                ApkDescription.newBuilder()
                    .setTargeting(apkTargeting)
                    .setPath(apkPath.toString())
                    // Contents of the apex APK metadata is not important for these tests
                    // as long as the field is set.
                    .setApexApkMetadata(ApexApkMetadata.getDefaultInstance()))
            .build());
  }

  /** Create standalone variant with multi ABI targeting only. */
  public static Variant multiAbiTargetingApexVariant(MultiAbiTargeting targeting, ZipPath apkPath) {
    return apexVariant(
        mergeVariantTargeting(
            variantSdkTargeting(sdkVersionFrom(1)), variantMultiAbiTargeting(targeting)),
        apkMultiAbiTargeting(targeting),
        apkPath);
  }

  public static ApkSet createSplitApkSet(String moduleName, ApkDescription... apkDescription) {
    return createSplitApkSet(
        moduleName,
        DeliveryType.INSTALL_TIME,
        /* moduleDependencies= */ ImmutableList.of(),
        apkDescription);
  }

  public static ApkSet createSplitApkSet(
      String moduleName,
      DeliveryType deliveryType,
      ImmutableList<String> moduleDependencies,
      ApkDescription... apkDescription) {
    ModuleMetadata.Builder moduleMetadata =
        ModuleMetadata.newBuilder().setName(moduleName).addAllDependencies(moduleDependencies);
    if (BundleToolVersion.getCurrentVersion().isNewerThan(Version.of("0.10.1"))) {
      moduleMetadata.setDeliveryType(deliveryType);
    } else {
      moduleMetadata.setOnDemandDeprecated(deliveryType != DeliveryType.INSTALL_TIME);
    }
    return ApkSet.newBuilder()
        .setModuleMetadata(moduleMetadata)
        .addAllApkDescription(Arrays.asList(apkDescription))
        .build();
  }

  public static ApkSet createConditionalApkSet(
      String moduleName, ModuleTargeting moduleTargeting, ApkDescription... apkDescriptions) {
    return ApkSet.newBuilder()
        .setModuleMetadata(
            ModuleMetadata.newBuilder()
                .setName(moduleName)
                .setTargeting(moduleTargeting)
                .setDeliveryType(DeliveryType.INSTALL_TIME))
        .addAllApkDescription(Arrays.asList(apkDescriptions))
        .build();
  }

  public static ApkDescription createMasterApkDescription(
      ApkTargeting apkTargeting, ZipPath apkPath) {
    return createApkDescription(apkTargeting, apkPath, /* isMasterSplit= */ true);
  }

  public static ApkDescription createApkDescription(
      ApkTargeting apkTargeting, ZipPath apkPath, boolean isMasterSplit) {
    return ApkDescription.newBuilder()
        .setPath(apkPath.toString())
        .setTargeting(apkTargeting)
        .setSplitApkMetadata(SplitApkMetadata.newBuilder().setIsMasterSplit(isMasterSplit))
        .build();
  }

  public static ApkDescription splitApkDescription(ApkTargeting apkTargeting, ZipPath apkPath) {
    return ApkDescription.newBuilder()
        .setTargeting(apkTargeting)
        .setPath(apkPath.toString())
        // Contents of the split APK metadata is not important for these tests as long as
        // the field is set.
        .setSplitApkMetadata(SplitApkMetadata.getDefaultInstance())
        .build();
  }

  public static ApkDescription instantApkDescription(ApkTargeting apkTargeting, ZipPath apkPath) {
    return ApkDescription.newBuilder()
        .setTargeting(apkTargeting)
        .setPath(apkPath.toString())
        // Contents of the instant APK metadata is not important for these tests as long as
        // the field is set.
        .setInstantApkMetadata(SplitApkMetadata.getDefaultInstance())
        .build();
  }

  /** Creates an instant apk set with the given module name, ApkTargeting, and path for the apk. */
  public static ApkSet createInstantApkSet(
      String moduleName, ApkTargeting apkTargeting, ZipPath apkPath) {
    return ApkSet.newBuilder()
        .setModuleMetadata(
            ModuleMetadata.newBuilder()
                .setName(moduleName)
                .setIsInstant(true)
                .setDeliveryType(DeliveryType.INSTALL_TIME))
        .addApkDescription(
            ApkDescription.newBuilder()
                .setPath(apkPath.toString())
                .setTargeting(apkTargeting)
                .setInstantApkMetadata(SplitApkMetadata.newBuilder().setIsMasterSplit(true)))
        .build();
  }

  public static ApkSet createStandaloneApkSet(ApkTargeting apkTargeting, ZipPath apkPath) {
    // Note: Standalone APK is represented as a module named "base".
    return ApkSet.newBuilder()
        .setModuleMetadata(
            ModuleMetadata.newBuilder().setName("base").setDeliveryType(DeliveryType.INSTALL_TIME))
        .addApkDescription(
            ApkDescription.newBuilder()
                .setPath(apkPath.toString())
                .setTargeting(apkTargeting)
                .setStandaloneApkMetadata(
                    StandaloneApkMetadata.newBuilder().addFusedModuleName("base")))
        .build();
  }

  public static ApkSet createSystemApkSet(ApkTargeting apkTargeting, ZipPath apkPath) {
    // Note: System APK is represented as a module named "base".
    return ApkSet.newBuilder()
        .setModuleMetadata(
            ModuleMetadata.newBuilder().setName("base").setDeliveryType(DeliveryType.INSTALL_TIME))
        .addApkDescription(
            ApkDescription.newBuilder()
                .setPath(apkPath.toString())
                .setTargeting(apkTargeting)
                .setSystemApkMetadata(SystemApkMetadata.newBuilder().addFusedModuleName("base")))
        .build();
  }

  public static ApkSet createArchivedApkSet(ApkTargeting apkTargeting, ZipPath apkPath) {
    // Note: Archived APK is represented as a module named "base".
    return ApkSet.newBuilder()
        .setModuleMetadata(
            ModuleMetadata.newBuilder().setName("base").setDeliveryType(DeliveryType.INSTALL_TIME))
        .addApkDescription(
            ApkDescription.newBuilder()
                .setPath(apkPath.toString())
                .setTargeting(apkTargeting)
                .setArchivedApkMetadata(ArchivedApkMetadata.getDefaultInstance()))
        .build();
  }

  public static AssetSliceSet createAssetSliceSet(
      String moduleName, DeliveryType deliveryType, ApkDescription... apkDescriptions) {
    return AssetSliceSet.newBuilder()
        .setAssetModuleMetadata(
            AssetModuleMetadata.newBuilder().setName(moduleName).setDeliveryType(deliveryType))
        .addAllApkDescription(Arrays.asList(apkDescriptions))
        .build();
  }

  public static Stream<ApkDescription> apkDescriptionStream(BuildApksResult buildApksResult) {
    return Stream.concat(
        buildApksResult.getVariantList().stream()
            .flatMap(variant -> variant.getApkSetList().stream())
            .flatMap(apkSet -> apkSet.getApkDescriptionList().stream()),
        buildApksResult.getAssetSliceSetList().stream()
            .flatMap(assetSliceSet -> assetSliceSet.getApkDescriptionList().stream()));
  }

  private ApksArchiveHelpers() {}
}
