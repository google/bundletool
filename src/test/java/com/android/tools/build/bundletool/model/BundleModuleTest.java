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

package com.android.tools.build.bundletool.model;

import static com.android.tools.build.bundletool.model.AndroidManifest.MODULE_TYPE_FEATURE_VALUE;
import static com.android.tools.build.bundletool.model.ModuleDeliveryType.ALWAYS_INITIAL_INSTALL;
import static com.android.tools.build.bundletool.model.ModuleDeliveryType.NO_INITIAL_INSTALL;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForAssetModule;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForMlModule;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withFeatureCondition;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withFusingAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstant;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstantInstallTimeDelivery;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstantOnDemandDelivery;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkCondition;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitId;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withTypeAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withUsesSplit;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeModuleTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.moduleFeatureTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.moduleMinSdkVersionTargeting;
import static com.android.tools.build.bundletool.testing.TestUtils.createModuleEntryForFile;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.Package;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Commands.RuntimeEnabledSdkDependency;
import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Files.ApexImages;
import com.android.bundle.Files.Assets;
import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.Files.TargetedApexImage;
import com.android.bundle.Files.TargetedAssetsDirectory;
import com.android.bundle.Files.TargetedNativeDirectory;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdk;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdkConfig;
import com.android.bundle.Targeting.ModuleTargeting;
import com.android.tools.build.bundletool.model.BundleModule.ModuleType;
import com.android.tools.build.bundletool.model.version.Version;
import com.android.tools.build.bundletool.testing.BundleConfigBuilder;
import java.io.UncheckedIOException;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BundleModuleTest {

  private static final BundleConfig DEFAULT_BUNDLE_CONFIG = BundleConfigBuilder.create().build();
  private static final byte[] TEST_CONTENT = new byte[0];

  @Test
  public void missingAssetsProtoFile_returnsEmptyProto() {
    BundleModule bundleModule = createMinimalModuleBuilder().build();

    assertThat(bundleModule.getAssetsConfig()).isEmpty();
  }

  @Test
  public void correctAssetsProtoFile_parsedAndReturned() throws Exception {
    Assets assetsConfig =
        Assets.newBuilder()
            .addDirectory(TargetedAssetsDirectory.newBuilder().setPath("assets/data-armv6"))
            .build();

    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .addEntry(createModuleEntryForFile("assets.pb", assetsConfig.toByteArray()))
            .build();

    assertThat(bundleModule.getAssetsConfig()).hasValue(assetsConfig);
  }

  @Test
  public void incorrectAssetsProtoFile_throws() throws Exception {
    byte[] badAssetsFile = new byte[] {'b', 'a', 'd'};

    assertThrows(
        UncheckedIOException.class,
        () ->
            createMinimalModuleBuilder()
                .addEntry(createModuleEntryForFile("assets.pb", badAssetsFile)));
  }

  @Test
  public void missingNativeProtoFile_returnsEmptyProto() {
    BundleModule bundleModule = createMinimalModuleBuilder().build();

    assertThat(bundleModule.getNativeConfig()).isEmpty();
  }

  @Test
  public void correctNativeProtoFile_parsedAndReturned() throws Exception {
    NativeLibraries nativeConfig =
        NativeLibraries.newBuilder()
            .addDirectory(TargetedNativeDirectory.newBuilder().setPath("native/x86"))
            .build();

    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .addEntry(createModuleEntryForFile("native.pb", nativeConfig.toByteArray()))
            .build();

    assertThat(bundleModule.getNativeConfig()).hasValue(nativeConfig);
  }

  @Test
  public void incorrectNativeProtoFile_throws() throws Exception {
    byte[] badNativeFile = new byte[] {'b', 'a', 'd'};

    assertThrows(
        UncheckedIOException.class,
        () ->
            createMinimalModuleBuilder()
                .addEntry(createModuleEntryForFile("native.pb", badNativeFile)));
  }

  @Test
  public void missingResourceTableProtoFile_returnsEmptyProto() throws Exception {
    BundleModule bundleModule = createMinimalModuleBuilder().build();

    assertThat(bundleModule.getResourceTable()).isEmpty();
  }

  @Test
  public void correctResourceTableProtoFile_parsedAndReturned() throws Exception {
    ResourceTable resourceTable =
        ResourceTable.newBuilder().addPackage(Package.getDefaultInstance()).build();

    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .addEntry(createModuleEntryForFile("resources.pb", resourceTable.toByteArray()))
            .build();

    assertThat(bundleModule.getResourceTable()).hasValue(resourceTable);
  }

  @Test
  public void incorrectResourceTable_throws() throws Exception {
    byte[] badResourcesFile = new byte[] {'b', 'a', 'd'};

    assertThrows(
        UncheckedIOException.class,
        () ->
            createMinimalModuleBuilder()
                .addEntry(createModuleEntryForFile("resources.pb", badResourcesFile)));
  }

  @Test
  public void missingProtoManifestFile_throws() {
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> createModuleWithoutManifest().build());

    assertThat(exception).hasMessageThat().contains("Missing required properties: androidManifest");
  }

  @Test
  public void correctProtoManifestFile_parsedAndReturned() throws Exception {
    XmlNode manifestXml = androidManifest("com.test.app");

    BundleModule bundleModule =
        createModuleWithoutManifest()
            .addEntry(
                createModuleEntryForFile("manifest/AndroidManifest.xml", manifestXml.toByteArray()))
            .build();

    assertThat(bundleModule.getAndroidManifest().getManifestRoot().getProto())
        .isEqualTo(manifestXml);
  }

  @Test
  public void incorrectProtoManifest_throws() throws Exception {
    byte[] badManifestFile = new byte[] {'b', 'a', 'd'};
    BundleModule.Builder minimalModuleWithoutManifest =
        BundleModule.builder().setName(BundleModuleName.create("testModule"));

    assertThrows(
        UncheckedIOException.class,
        () ->
            minimalModuleWithoutManifest.addEntry(
                createModuleEntryForFile("manifest/AndroidManifest.xml", badManifestFile)));
  }

  @Test
  public void missingApexProtoFile_returnsEmptyProto() {
    BundleModule bundleModule = createMinimalModuleBuilder().build();

    assertThat(bundleModule.getApexConfig()).isEmpty();
  }

  @Test
  public void correctApexProtoFile_parsedAndReturned() throws Exception {
    ApexImages apexConfig =
        ApexImages.newBuilder()
            .addImage(TargetedApexImage.newBuilder().setPath("apex/x86.img"))
            .build();

    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .addEntry(createModuleEntryForFile("apex.pb", apexConfig.toByteArray()))
            .build();

    assertThat(bundleModule.getApexConfig()).hasValue(apexConfig);
  }

  @Test
  public void incorrectApexProtoFile_throws() throws Exception {
    byte[] badApexFile = new byte[] {'b', 'a', 'd'};

    assertThrows(
        UncheckedIOException.class,
        () ->
            createMinimalModuleBuilder()
                .addEntry(createModuleEntryForFile("apex.pb", badApexFile)));
  }

  @Test
  public void specialFiles_areNotStoredAsEntries() throws Exception {
    BundleModule bundleModule =
        createModuleWithoutManifest()
            .addEntry(
                createModuleEntryForFile(
                    "manifest/AndroidManifest.xml", androidManifest("com.test.app").toByteArray()))
            .addEntry(
                createModuleEntryForFile("assets.pb", Assets.getDefaultInstance().toByteArray()))
            .addEntry(
                createModuleEntryForFile(
                    "native.pb", NativeLibraries.getDefaultInstance().toByteArray()))
            .addEntry(
                createModuleEntryForFile(
                    "resources.pb", ResourceTable.getDefaultInstance().toByteArray()))
            .build();

    assertThat(bundleModule.getEntries()).isEmpty();
  }

  @Test
  public void baseAlwaysIncludedInFusing() throws Exception {
    BundleModule baseWithoutFusingConfig =
        createMinimalModuleBuilder()
            .setName(BundleModuleName.create("base"))
            .setAndroidManifestProto(androidManifest("com.test.app"))
            .build();
    assertThat(baseWithoutFusingConfig.isIncludedInFusing()).isTrue();

    BundleModule baseWithFusingConfigTrue =
        createMinimalModuleBuilder()
            .setName(BundleModuleName.create("base"))
            .setAndroidManifestProto(androidManifest("com.test.app", withFusingAttribute(true)))
            .build();
    assertThat(baseWithFusingConfigTrue.isIncludedInFusing()).isTrue();

    // This module is technically illegal and could not pass validations, but it makes sense for the
    // test.
    BundleModule baseWithFusingConfigFalse =
        createMinimalModuleBuilder()
            .setName(BundleModuleName.create("base"))
            .setAndroidManifestProto(androidManifest("com.test.app", withFusingAttribute(false)))
            .build();
    assertThat(baseWithFusingConfigFalse.isIncludedInFusing()).isTrue();
  }

  /** Tests that we skip directories that contain a directory that we want to find entries under. */
  @Test
  public void entriesUnderPath_withPrefixDirectory() throws Exception {
    ModuleEntry entry1 = createModuleEntryForFile("dir1/entry1", TEST_CONTENT);
    ModuleEntry entry2 = createModuleEntryForFile("dir1/entry2", TEST_CONTENT);
    ModuleEntry entry3 = createModuleEntryForFile("dir1longer/entry3", TEST_CONTENT);

    BundleModule bundleModule =
        createMinimalModuleBuilder().addEntries(Arrays.asList(entry1, entry2, entry3)).build();

    assertThat(bundleModule.findEntriesUnderPath(ZipPath.create("dir1")).collect(toList()))
        .containsExactly(entry1, entry2);
  }

  @Test
  public void getEntry_existing_found() throws Exception {
    ModuleEntry entry = createModuleEntryForFile("dir/entry", TEST_CONTENT);

    BundleModule bundleModule =
        createMinimalModuleBuilder().addEntries(Arrays.asList(entry)).build();

    assertThat(bundleModule.getEntry(ZipPath.create("dir/entry"))).hasValue(entry);
  }

  @Test
  public void getEntry_unknown_notFound() throws Exception {
    BundleModule bundleModule = createMinimalModuleBuilder().build();

    assertThat(bundleModule.getEntry(ZipPath.create("unknown-entry"))).isEmpty();
  }

  @Test
  public void getModuleMetadata_dependencies_parsedAndReturned() throws Exception {
    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .setAndroidManifestProto(
                androidManifest("com.test.app", withUsesSplit("feature1", "feature2")))
            .build();

    assertThat(bundleModule.getModuleMetadata().getDependenciesList())
        .containsExactly("feature1", "feature2");
  }

  @Test
  public void getModuleMetadata_targeting_emptyIfNoConditions() throws Exception {
    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .setAndroidManifestProto(androidManifest("com.test.app"))
            .build();
    assertThat(bundleModule.getModuleMetadata().getTargeting()).isEqualToDefaultInstance();
  }

  @Test
  public void getModuleMetadata_targeting_presentIfConditionsUsed() throws Exception {
    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .setAndroidManifestProto(
                androidManifest(
                    "com.test.app",
                    withFeatureCondition("com.android.hardware.feature"),
                    withMinSdkCondition(24)))
            .build();
    assertThat(bundleModule.getModuleMetadata().getTargeting())
        .isEqualTo(
            mergeModuleTargeting(
                moduleFeatureTargeting("com.android.hardware.feature"),
                moduleMinSdkVersionTargeting(/* minSdkVersion= */ 24)));
  }

  @Test
  public void getModuleMetadataForSdkRuntimeVariant_moduleHasNoRuntimeEnabledSdkDependencies() {
    BundleModule bundleModule = createMinimalModuleBuilder().build();

    assertThat(
            bundleModule
                .getModuleMetadata(/* isSdkRuntimeVariant= */ true)
                .getRuntimeEnabledSdkDependenciesCount())
        .isEqualTo(0);
  }

  @Test
  public void getModuleMetadataForSdkRuntimeVariant_moduleHasRuntimeEnabledSdkDependencies() {
    RuntimeEnabledSdkConfig runtimeEnabledSdkConfig =
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("sdk.package.name1")
                    .setVersionMajor(1)
                    .setVersionMinor(1))
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("sdk.package.name2")
                    .setVersionMajor(2)
                    .setVersionMinor(2))
            .build();

    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .addEntry(
                createModuleEntryForFile(
                    "runtime_enabled_sdk_config.pb", runtimeEnabledSdkConfig.toByteArray()))
            .build();

    assertThat(
            bundleModule
                .getModuleMetadata(/* isSdkRuntimeVariant= */ true)
                .getRuntimeEnabledSdkDependenciesList())
        .containsExactly(
            RuntimeEnabledSdkDependency.newBuilder()
                .setPackageName("sdk.package.name1")
                .setMajorVersion(1)
                .setMinorVersion(1)
                .build(),
            RuntimeEnabledSdkDependency.newBuilder()
                .setPackageName("sdk.package.name2")
                .setMajorVersion(2)
                .setMinorVersion(2)
                .build());
  }

  @Test
  public void getModuleMetadataForNonSdkRuntimeVariant_moduleHasRuntimeEnabledSdkDependencies() {
    RuntimeEnabledSdkConfig runtimeEnabledSdkConfig =
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("sdk.package.name1")
                    .setVersionMajor(1)
                    .setVersionMinor(1))
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("sdk.package.name2")
                    .setVersionMajor(2)
                    .setVersionMinor(2))
            .build();

    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .addEntry(
                createModuleEntryForFile(
                    "runtime_enabled_sdk_config.pb", runtimeEnabledSdkConfig.toByteArray()))
            .build();

    assertThat(
            bundleModule
                .getModuleMetadata(/* isSdkRuntimeVariant= */ false)
                .getRuntimeEnabledSdkDependenciesList())
        .isEmpty();
  }

  @Test
  public void getInstantDeliveryType_noConfig() {
    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .setAndroidManifestProto(androidManifestForAssetModule("com.test.app"))
            .build();

    assertThat(bundleModule.getInstantDeliveryType()).isEmpty();
  }

  @Test
  public void getInstantDeliveryType_instantAttributeTrue() {
    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .setAndroidManifestProto(
                androidManifestForAssetModule("com.test.app", withInstant(true)))
            .build();

    assertThat(bundleModule.getInstantDeliveryType()).hasValue(NO_INITIAL_INSTALL);
  }

  @Test
  public void getInstantDeliveryType_instantAttributeFalse() {
    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .setAndroidManifestProto(
                androidManifestForAssetModule("com.test.app", withInstant(false)))
            .build();

    assertThat(bundleModule.getInstantDeliveryType()).isEmpty();
  }

  @Test
  public void getInstantDeliveryType_onDemandElement() {
    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .setAndroidManifestProto(
                androidManifestForAssetModule("com.test.app", withInstantOnDemandDelivery()))
            .build();

    assertThat(bundleModule.getInstantDeliveryType()).hasValue(NO_INITIAL_INSTALL);
  }

  @Test
  public void getInstantDeliveryType_installTimeElement() {
    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .setAndroidManifestProto(
                androidManifestForAssetModule("com.test.app", withInstantInstallTimeDelivery()))
            .build();

    assertThat(bundleModule.getInstantDeliveryType()).hasValue(ALWAYS_INITIAL_INSTALL);
  }

  @Test
  public void getModuleType_notSpecified_defaultsToFeature() {
    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .setAndroidManifestProto(androidManifest("com.test.app"))
            .build();

    assertThat(bundleModule.getModuleType()).isEqualTo(ModuleType.FEATURE_MODULE);
  }

  @Test
  public void getModuleType_feature() {
    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .setAndroidManifestProto(
                androidManifest("com.test.app", withTypeAttribute(MODULE_TYPE_FEATURE_VALUE)))
            .build();

    assertThat(bundleModule.getModuleType()).isEqualTo(ModuleType.FEATURE_MODULE);
  }

  @Test
  public void getModuleType_assetModule() {
    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .setAndroidManifestProto(androidManifestForAssetModule("com.test.app"))
            .build();

    assertThat(bundleModule.getModuleType()).isEqualTo(ModuleType.ASSET_MODULE);
  }

  @Test
  public void getModuleType_mlModule() {
    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .setAndroidManifestProto(androidManifestForMlModule("com.test.app"))
            .build();

    assertThat(bundleModule.getModuleType()).isEqualTo(ModuleType.ML_MODULE);
  }

  @Test
  public void renderscriptFiles_present() throws Exception {
    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .setAndroidManifestProto(androidManifest("com.test.app"))
            .addEntry(createModuleEntryForFile("dex/classes.dex", TEST_CONTENT))
            .addEntry(createModuleEntryForFile("res/raw/yuv2rgb.bc", TEST_CONTENT))
            .build();

    assertThat(bundleModule.hasRenderscript32Bitcode()).isTrue();
  }

  @Test
  public void renderscriptFiles_absent() throws Exception {
    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .setAndroidManifestProto(androidManifest("com.test.app"))
            .addEntry(createModuleEntryForFile("dex/classes.dex", TEST_CONTENT))
            .build();

    assertThat(bundleModule.hasRenderscript32Bitcode()).isFalse();
  }

  @Test
  public void moduleTargeting_noModuleMinSdkVersion_noConditionsAddded() {
    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .setAndroidManifestProto(
                androidManifest("com.test.app", withFeatureCondition("com.feature1")))
            .build();

    ModuleTargeting moduleTargeting = bundleModule.getModuleMetadata().getTargeting();
    assertThat(moduleTargeting)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(moduleFeatureTargeting("com.feature1"));
  }

  @Test
  public void moduleTargeting_moduleMinSdkVersionInherited() {
    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .setAndroidManifestProto(
                androidManifest(
                    "com.test.app", withMinSdkVersion(24), withFeatureCondition("com.feature1")))
            .build();

    ModuleTargeting moduleTargeting = bundleModule.getModuleMetadata().getTargeting();
    assertThat(moduleTargeting)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeModuleTargeting(
                moduleFeatureTargeting("com.feature1"), moduleMinSdkVersionTargeting(24)));
  }

  @Test
  public void moduleTargeting_moduleMinSdkVersion_minSdkConditionPreferred() {
    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .setAndroidManifestProto(
                androidManifest(
                    "com.test.app",
                    withMinSdkVersion(24),
                    withMinSdkCondition(28),
                    withFeatureCondition("com.feature1")))
            .build();

    ModuleTargeting moduleTargeting = bundleModule.getModuleMetadata().getTargeting();
    assertThat(moduleTargeting)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeModuleTargeting(
                moduleFeatureTargeting("com.feature1"), moduleMinSdkVersionTargeting(28)));
  }

  @Test
  public void moduleTargeting_noConditions_noMinSdkInherited() {
    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .setAndroidManifestProto(androidManifest("com.test.app", withMinSdkVersion(24)))
            .build();

    ModuleTargeting moduleTargeting = bundleModule.getModuleMetadata().getTargeting();
    assertThat(moduleTargeting).isEqualToDefaultInstance();
  }

  @Test
  public void missingRuntimeEnabledSdkConfigFile_returnsEmptyProto() {
    BundleModule bundleModule = createMinimalModuleBuilder().build();

    assertThat(bundleModule.getRuntimeEnabledSdkConfig()).isEmpty();
  }

  @Test
  public void correctRuntimeEnabledSdkConfigFile_parsedAndReturned() {
    RuntimeEnabledSdkConfig runtimeEnabledSdkConfig =
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("sdk.package.name")
                    .setVersionMajor(1234)
                    .setCertificateDigest("AA:BB:CC:DD"))
            .build();

    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .addEntry(
                createModuleEntryForFile(
                    "runtime_enabled_sdk_config.pb", runtimeEnabledSdkConfig.toByteArray()))
            .build();

    assertThat(bundleModule.getRuntimeEnabledSdkConfig()).hasValue(runtimeEnabledSdkConfig);
  }

  @Test
  public void incorrectRuntimeEnabledSdkConfigFile_throws() {
    byte[] badRuntimeEnabledSdkConfig = new byte[] {'b', 'a', 'd'};

    assertThrows(
        UncheckedIOException.class,
        () ->
            createMinimalModuleBuilder()
                .addEntry(
                    createModuleEntryForFile(
                        "runtime_enabled_sdk_config.pb", badRuntimeEnabledSdkConfig)));
  }

  private static BundleModule.Builder createMinimalModuleBuilder() {
    return createModuleWithoutManifest()
        .setAndroidManifestProto(androidManifest("com.test.app", withSplitId("testModule")));
  }

  private static BundleModule.Builder createModuleWithoutManifest() {
    return BundleModule.builder()
        .setName(BundleModuleName.create("testModule"))
        .setBundleType(DEFAULT_BUNDLE_CONFIG.getType())
        .setBundletoolVersion(Version.of(DEFAULT_BUNDLE_CONFIG.getBundletool().getVersion()));
  }
}
