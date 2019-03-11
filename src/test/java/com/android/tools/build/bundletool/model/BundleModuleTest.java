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

import static com.android.tools.build.bundletool.model.AndroidManifest.MODULE_TYPE_ASSET_VALUE;
import static com.android.tools.build.bundletool.model.AndroidManifest.MODULE_TYPE_FEATURE_VALUE;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withFeatureCondition;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withFusingAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstallTimeDelivery;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkCondition;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withOnDemandAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withOnDemandDelivery;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitId;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withTypeAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withUsesSplit;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeModuleTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.moduleFeatureTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.moduleMinSdkVersionTargeting;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.Package;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Files.ApexImages;
import com.android.bundle.Files.Assets;
import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.Files.TargetedApexImage;
import com.android.bundle.Files.TargetedAssetsDirectory;
import com.android.bundle.Files.TargetedNativeDirectory;
import com.android.bundle.Targeting.ModuleTargeting;
import com.android.tools.build.bundletool.model.BundleModule.ModuleDeliveryType;
import com.android.tools.build.bundletool.model.BundleModule.ModuleType;
import com.android.tools.build.bundletool.testing.BundleConfigBuilder;
import java.io.IOException;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public class BundleModuleTest {

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  private static final BundleConfig DEFAULT_BUNDLE_CONFIG = BundleConfigBuilder.create().build();
  private static final byte[] DUMMY_CONTENT = new byte[0];

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
            .addEntry(InMemoryModuleEntry.ofFile("assets.pb", assetsConfig.toByteArray()))
            .build();

    assertThat(bundleModule.getAssetsConfig()).hasValue(assetsConfig);
  }

  @Test
  public void incorrectAssetsProtoFile_throws() throws Exception {
    byte[] badAssetsFile = new byte[] {'b', 'a', 'd'};

    assertThrows(
        IOException.class,
        () ->
            createMinimalModuleBuilder()
                .addEntry(InMemoryModuleEntry.ofFile("assets.pb", badAssetsFile)));
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
            .addEntry(InMemoryModuleEntry.ofFile("native.pb", nativeConfig.toByteArray()))
            .build();

    assertThat(bundleModule.getNativeConfig()).hasValue(nativeConfig);
  }

  @Test
  public void incorrectNativeProtoFile_throws() throws Exception {
    byte[] badNativeFile = new byte[] {'b', 'a', 'd'};

    assertThrows(
        IOException.class,
        () ->
            createMinimalModuleBuilder()
                .addEntry(InMemoryModuleEntry.ofFile("native.pb", badNativeFile)));
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
            .addEntry(InMemoryModuleEntry.ofFile("resources.pb", resourceTable.toByteArray()))
            .build();

    assertThat(bundleModule.getResourceTable()).hasValue(resourceTable);
  }

  @Test
  public void incorrectResourceTable_throws() throws Exception {
    byte[] badResourcesFile = new byte[] {'b', 'a', 'd'};

    assertThrows(
        IOException.class,
        () ->
            createMinimalModuleBuilder()
                .addEntry(InMemoryModuleEntry.ofFile("resources.pb", badResourcesFile)));
  }

  @Test
  public void missingProtoManifestFile_throws() {
    BundleModule.Builder minimalModuleWithoutManifest =
        BundleModule.builder()
            .setName(BundleModuleName.create("testModule"))
            .setBundleConfig(DEFAULT_BUNDLE_CONFIG);

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> minimalModuleWithoutManifest.build());

    assertThat(exception).hasMessageThat().contains("Missing required properties: androidManifest");
  }

  @Test
  public void correctProtoManifestFile_parsedAndReturned() throws Exception {
    XmlNode manifestXml = androidManifest("com.test.app");

    BundleModule bundleModule =
        BundleModule.builder()
            .setName(BundleModuleName.create("testModule"))
            .setBundleConfig(DEFAULT_BUNDLE_CONFIG)
            .addEntry(
                InMemoryModuleEntry.ofFile(
                    "manifest/AndroidManifest.xml", manifestXml.toByteArray()))
            .build();

    assertThat(bundleModule.getAndroidManifest().getManifestRoot().getProto())
        .isEqualTo(manifestXml);
  }

  @Test
  public void incorrectProtoManifest_throws() throws Exception {
    byte[] badManifestFile = new byte[] {'b', 'a', 'd'};
    BundleModule.Builder minimalModuleWithoutManifest =
        BundleModule.builder()
            .setName(BundleModuleName.create("testModule"));

    assertThrows(
        IOException.class,
        () ->
            minimalModuleWithoutManifest.addEntry(
                InMemoryModuleEntry.ofFile("manifest/AndroidManifest.xml", badManifestFile)));
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
            .addEntry(InMemoryModuleEntry.ofFile("apex.pb", apexConfig.toByteArray()))
            .build();

    assertThat(bundleModule.getApexConfig()).hasValue(apexConfig);
  }

  @Test
  public void incorrectApexProtoFile_throws() throws Exception {
    byte[] badApexFile = new byte[] {'b', 'a', 'd'};

    assertThrows(
        IOException.class,
        () ->
            createMinimalModuleBuilder()
                .addEntry(InMemoryModuleEntry.ofFile("apex.pb", badApexFile)));
  }

  @Test
  public void specialFiles_areNotStoredAsEntries() throws Exception {
    BundleModule bundleModule =
        BundleModule.builder()
            .setName(BundleModuleName.create("testModule"))
            .setBundleConfig(DEFAULT_BUNDLE_CONFIG)
            .addEntry(
                InMemoryModuleEntry.ofFile(
                    "manifest/AndroidManifest.xml", androidManifest("com.test.app").toByteArray()))
            .addEntry(
                InMemoryModuleEntry.ofFile("assets.pb", Assets.getDefaultInstance().toByteArray()))
            .addEntry(
                InMemoryModuleEntry.ofFile(
                    "native.pb", NativeLibraries.getDefaultInstance().toByteArray()))
            .addEntry(
                InMemoryModuleEntry.ofFile(
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
    ModuleEntry entry1 = InMemoryModuleEntry.ofFile("dir1/entry1", DUMMY_CONTENT);
    ModuleEntry entry2 = InMemoryModuleEntry.ofFile("dir1/entry2", DUMMY_CONTENT);
    ModuleEntry entry3 = InMemoryModuleEntry.ofFile("dir1longer/entry3", DUMMY_CONTENT);

    BundleModule bundleModule =
        createMinimalModuleBuilder().addEntries(Arrays.asList(entry1, entry2, entry3)).build();

    assertThat(bundleModule.findEntriesUnderPath(ZipPath.create("dir1")).collect(toList()))
        .containsExactly(entry1, entry2);
  }

  @Test
  public void getEntry_existing_found() throws Exception {
    ModuleEntry entry = InMemoryModuleEntry.ofFile("dir/entry", DUMMY_CONTENT);

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
  public void getDeliveryType_noConfig() throws Exception {
    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .setAndroidManifestProto(androidManifest("com.test.app"))
            .build();

    assertThat(bundleModule.getDeliveryType()).isEqualTo(ModuleDeliveryType.ALWAYS_INITIAL_INSTALL);
  }

  @Test
  public void getDeliveryType_legacy_onDemandTrue() throws Exception {
    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .setAndroidManifestProto(androidManifest("com.test.app", withOnDemandAttribute(true)))
            .build();

    assertThat(bundleModule.getDeliveryType()).isEqualTo(ModuleDeliveryType.NO_INITIAL_INSTALL);
  }

  @Test
  public void getdeliveryType_legacy_onDemandFalse() throws Exception {
    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .setAndroidManifestProto(androidManifest("com.test.app", withOnDemandAttribute(false)))
            .build();

    assertThat(bundleModule.getDeliveryType()).isEqualTo(ModuleDeliveryType.ALWAYS_INITIAL_INSTALL);
  }

  @Test
  public void getdeliveryType_onDemandElement_only() throws Exception {
    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .setAndroidManifestProto(androidManifest("com.test.app", withOnDemandDelivery()))
            .build();

    assertThat(bundleModule.getDeliveryType()).isEqualTo(ModuleDeliveryType.NO_INITIAL_INSTALL);
  }

  @Test
  public void getdeliveryType_onDemandElement_andConditions() throws Exception {
    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .setAndroidManifestProto(
                androidManifest("com.test.app", withOnDemandDelivery(), withMinSdkCondition(21)))
            .build();

    assertThat(bundleModule.getDeliveryType())
        .isEqualTo(ModuleDeliveryType.CONDITIONAL_INITIAL_INSTALL);
  }

  @Test
  public void getdeliveryType_installTimeElement_noConditions() throws Exception {
    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .setAndroidManifestProto(
                androidManifest("com.test.app", withInstallTimeDelivery(), withOnDemandDelivery()))
            .build();

    assertThat(bundleModule.getDeliveryType()).isEqualTo(ModuleDeliveryType.ALWAYS_INITIAL_INSTALL);
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
  public void getModuleType_remoteAsset() {
    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .setAndroidManifestProto(
                androidManifest("com.test.app", withTypeAttribute(MODULE_TYPE_ASSET_VALUE)))
            .build();

    assertThat(bundleModule.getModuleType()).isEqualTo(ModuleType.ASSET_MODULE);
  }

  @Test
  public void renderscriptFiles_present() throws Exception {
    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .setAndroidManifestProto(androidManifest("com.test.app"))
            .addEntry(InMemoryModuleEntry.ofFile("dex/classes.dex", DUMMY_CONTENT))
            .addEntry(InMemoryModuleEntry.ofFile("res/raw/yuv2rgb.bc", DUMMY_CONTENT))
            .build();

    assertThat(bundleModule.hasRenderscript32Bitcode()).isTrue();
  }

  @Test
  public void renderscriptFiles_absent() throws Exception {
    BundleModule bundleModule =
        createMinimalModuleBuilder()
            .setAndroidManifestProto(androidManifest("com.test.app"))
            .addEntry(InMemoryModuleEntry.ofFile("dex/classes.dex", DUMMY_CONTENT))
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

  private static BundleModule.Builder createMinimalModuleBuilder() {
    return BundleModule.builder()
        .setName(BundleModuleName.create("testModule"))
        .setAndroidManifestProto(androidManifest("com.test.app", withSplitId("testModule")))
        .setBundleConfig(DEFAULT_BUNDLE_CONFIG);
  }
}
