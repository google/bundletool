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

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForAssetModule;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForFeature;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSharedUserId;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeLibraries;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedNativeDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.toAbi;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.BundleConfig.BundleType;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdk;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdkConfig;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.tools.build.bundletool.io.ZipBuilder;
import com.android.tools.build.bundletool.model.ModuleEntry.ModuleEntryLocationInZipSource;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.BundleConfigBuilder;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipFile;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the AppBundle class. */
@RunWith(JUnit4.class)
public class AppBundleTest {

  private static final byte[] TEST_CONTENT = new byte[1];
  private static final String PACKAGE_NAME = "com.test.app.detail";
  private static final BundleConfig BUNDLE_CONFIG = BundleConfigBuilder.create().build();
  public static final XmlNode MANIFEST = androidManifest(PACKAGE_NAME);
  public static final XmlNode ASSET_MODULE_MANIFEST = androidManifestForAssetModule(PACKAGE_NAME);

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private Path bundleFile;

  @Before
  public void setUp() {
    bundleFile = tmp.getRoot().toPath().resolve("bundle.aab");
  }

  @Test
  public void testSingleModuleBundle() throws Exception {
    createBasicZipBuilderWithManifest()
        .addFileWithContent(ZipPath.create("base/dex/classes.dex"), TEST_CONTENT)
        .writeTo(bundleFile);

    try (ZipFile appBundleZip = new ZipFile(bundleFile.toFile())) {
      AppBundle appBundle = AppBundle.buildFromZip(appBundleZip);
      assertThat(appBundle.getFeatureModules().keySet())
          .containsExactly(BundleModuleName.create("base"));
    }
  }

  @Test
  public void testMultipleModules() throws Exception {
    createBasicZipBuilder(BUNDLE_CONFIG)
        .addFileWithProtoContent(ZipPath.create("base/manifest/AndroidManifest.xml"), MANIFEST)
        .addFileWithContent(ZipPath.create("base/dex/classes.dex"), TEST_CONTENT)
        .addFileWithContent(ZipPath.create("base/assets/file.txt"), TEST_CONTENT)
        .addFileWithProtoContent(ZipPath.create("detail/manifest/AndroidManifest.xml"), MANIFEST)
        .addFileWithContent(ZipPath.create("detail/assets/file.txt"), TEST_CONTENT)
        .writeTo(bundleFile);

    try (ZipFile appBundleZip = new ZipFile(bundleFile.toFile())) {
      AppBundle appBundle = AppBundle.buildFromZip(appBundleZip);
      assertThat(appBundle.getFeatureModules().keySet())
          .containsExactly(BundleModuleName.create("base"), BundleModuleName.create("detail"));
    }
  }

  @Test
  public void classFilesNotAddedToModule() throws Exception {
    createBasicZipBuilderWithManifest()
        .addFileWithContent(ZipPath.create("base/root/Foo.classes"), TEST_CONTENT)
        .addFileWithContent(ZipPath.create("base/root/class.txt"), TEST_CONTENT)
        .addFileWithContent(ZipPath.create("base/root/Foo.class"), TEST_CONTENT)
        .writeTo(bundleFile);

    try (ZipFile appBundleZip = new ZipFile(bundleFile.toFile())) {
      AppBundle appBundle = AppBundle.buildFromZip(appBundleZip);
      assertThat(appBundle.getBaseModule().getEntry(ZipPath.create("root/Foo.classes")))
          .isPresent();
      assertThat(appBundle.getBaseModule().getEntry(ZipPath.create("root/class.txt"))).isPresent();
      assertThat(appBundle.getBaseModule().getEntry(ZipPath.create("root/Foo.class"))).isEmpty();
    }
  }

  // Ensures that the ClassesDexEntriesMutator is invoked.
  @Test
  public void wronglyNamedDexFilesAreRenamed() throws Exception {
    createBasicZipBuilderWithManifest()
        .addFileWithContent(ZipPath.create("base/dex/classes.dex"), TEST_CONTENT)
        .addFileWithContent(ZipPath.create("base/dex/classes1.dex"), TEST_CONTENT)
        .addFileWithContent(ZipPath.create("base/dex/classes2.dex"), TEST_CONTENT)
        .writeTo(bundleFile);

    try (ZipFile appBundleZip = new ZipFile(bundleFile.toFile())) {
      AppBundle appBundle = AppBundle.buildFromZip(appBundleZip);
      assertThat(appBundle.getBaseModule().getEntry(ZipPath.create("dex/classes.dex"))).isPresent();
      assertThat(appBundle.getBaseModule().getEntry(ZipPath.create("dex/classes1.dex"))).isEmpty();
      assertThat(appBundle.getBaseModule().getEntry(ZipPath.create("dex/classes2.dex")))
          .isPresent();
      assertThat(appBundle.getBaseModule().getEntry(ZipPath.create("dex/classes3.dex")))
          .isPresent();
    }
  }

  @Test
  public void bundleMetadataProcessedCorrectly() throws Exception {
    createBasicZipBuilderWithManifest()
        .addFileWithContent(
            ZipPath.create("BUNDLE-METADATA/some.namespace/metadata1"), new byte[] {0x01})
        .addFileWithContent(
            ZipPath.create("BUNDLE-METADATA/some.namespace/sub-dir/metadata2"), new byte[] {0x02})
        .writeTo(bundleFile);

    try (ZipFile appBundleZip = new ZipFile(bundleFile.toFile())) {
      AppBundle appBundle = AppBundle.buildFromZip(appBundleZip);

      Optional<ByteSource> existingMetadataFile =
          appBundle
              .getBundleMetadata()
              .getFileAsByteSource(
                  /* namespacedDir= */ "some.namespace", /* fileName= */ "metadata1");
      assertThat(existingMetadataFile).isPresent();
      assertThat(existingMetadataFile.get().read()).isEqualTo(new byte[] {0x01});

      Optional<ByteSource> existingMetadataFileInSubDir =
          appBundle
              .getBundleMetadata()
              .getFileAsByteSource(
                  /* namespacedDir= */ "some.namespace/sub-dir", /* fileName= */ "metadata2");
      assertThat(existingMetadataFileInSubDir).isPresent();
      assertThat(existingMetadataFileInSubDir.get().read()).isEqualTo(new byte[] {0x02});

      Optional<ByteSource> nonExistingMetadataFile =
          appBundle
              .getBundleMetadata()
              .getFileAsByteSource(
                  /* namespacedDir= */ "unknown.namespace", /* fileName= */ "blah");
      assertThat(nonExistingMetadataFile).isEmpty();
    }
  }

  @Test
  public void bundleMetadataDirectoryNotAModule() throws Exception {
    createBasicZipBuilderWithManifest()
        .addFileWithContent(
            ZipPath.create("BUNDLE-METADATA/some.namespace/metadata-file.txt"), TEST_CONTENT)
        .writeTo(bundleFile);

    try (ZipFile appBundleZip = new ZipFile(bundleFile.toFile())) {
      AppBundle appBundle = AppBundle.buildFromZip(appBundleZip);
      assertThat(appBundle.getFeatureModules().keySet())
          .containsExactly(BundleModuleName.create("base"));
    }
  }

  @Test
  public void metaInfDirectoryNotAModule() throws Exception {
    createBasicZipBuilderWithManifest()
        .addFileWithContent(ZipPath.create("META-INF/GOOG.RSA"), TEST_CONTENT)
        .addFileWithContent(ZipPath.create("META-INF/MANIFEST.SF"), TEST_CONTENT)
        .writeTo(bundleFile);

    try (ZipFile appBundleZip = new ZipFile(bundleFile.toFile())) {
      AppBundle appBundle = AppBundle.buildFromZip(appBundleZip);
      assertThat(appBundle.getFeatureModules().keySet())
          .containsExactly(BundleModuleName.create("base"));
    }
  }

  @Test
  public void bundleConfigExtracted() throws Exception {
    createBasicZipBuilderWithManifest(BUNDLE_CONFIG).writeTo(bundleFile);

    try (ZipFile appBundleZip = new ZipFile(bundleFile.toFile())) {
      AppBundle appBundle = AppBundle.buildFromZip(appBundleZip);
      assertThat(appBundle.getBundleConfig()).isEqualTo(BUNDLE_CONFIG);
    }
  }

  @Test
  public void manifestRequired() throws Exception {
    createBasicZipBuilder(BUNDLE_CONFIG)
        .addFileWithContent(ZipPath.create("base/dex/classes.dex"), TEST_CONTENT)
        .writeTo(bundleFile);

    try (ZipFile appBundleZip = new ZipFile(bundleFile.toFile())) {
      assertThrows(InvalidBundleException.class, () -> AppBundle.buildFromZip(appBundleZip));
    }
  }

  @Test
  public void targetedAbis_noNativeCode() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                baseModule ->
                    baseModule.setManifest(MANIFEST).addFile("dex/classes.dex", TEST_CONTENT))
            .addModule(
                "detail",
                module -> module.setManifest(MANIFEST).addFile("dex/classes.dex", TEST_CONTENT))
            .build();

    assertThat(appBundle.getTargetedAbis()).isEmpty();
  }

  @Test
  public void targetedAbis_abiInAllModules() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                baseModule ->
                    baseModule
                        .setManifest(MANIFEST)
                        .addFile("dex/classes.dex", TEST_CONTENT)
                        .addFile("lib/x86_64/libfoo.so", TEST_CONTENT)
                        .addFile("lib/armeabi/libfoo.so", TEST_CONTENT)
                        .setNativeConfig(
                            nativeLibraries(
                                targetedNativeDirectory(
                                    "lib/x86_64", nativeDirectoryTargeting(AbiAlias.X86_64)),
                                targetedNativeDirectory(
                                    "lib/armeabi", nativeDirectoryTargeting(AbiAlias.ARMEABI)))))
            .addModule(
                "detail",
                module ->
                    module
                        .setManifest(MANIFEST)
                        .addFile("dex/classes.dex", TEST_CONTENT)
                        .addFile("lib/x86_64/libbar.so", TEST_CONTENT)
                        .addFile("lib/armeabi/libbar.so", TEST_CONTENT)
                        .setNativeConfig(
                            nativeLibraries(
                                targetedNativeDirectory(
                                    "lib/x86_64", nativeDirectoryTargeting(AbiAlias.X86_64)),
                                targetedNativeDirectory(
                                    "lib/armeabi", nativeDirectoryTargeting(AbiAlias.ARMEABI)))))
            .build();

    assertThat(appBundle.getTargetedAbis())
        .containsExactly(toAbi(AbiAlias.ARMEABI), toAbi(AbiAlias.X86_64));
  }

  @Test
  public void targetedAbis_abiInSomeModules() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                baseModule ->
                    baseModule.setManifest(MANIFEST).addFile("dex/classes.dex", TEST_CONTENT))
            .addModule(
                "detail",
                module ->
                    module
                        .setManifest(MANIFEST)
                        .addFile("dex/classes.dex", TEST_CONTENT)
                        .addFile("lib/arm64-v8a/libbar.so", TEST_CONTENT)
                        .addFile("lib/x86/libbar.so", TEST_CONTENT)
                        .setNativeConfig(
                            nativeLibraries(
                                targetedNativeDirectory(
                                    "lib/arm64-v8a", nativeDirectoryTargeting(AbiAlias.ARM64_V8A)),
                                targetedNativeDirectory(
                                    "lib/x86", nativeDirectoryTargeting(AbiAlias.X86)))))
            .build();

    assertThat(appBundle.getTargetedAbis())
        .containsExactly(toAbi(AbiAlias.X86), toAbi(AbiAlias.ARM64_V8A));
  }

  @Test
  public void baseAndAssetModule_fromModules_areSeparated() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                baseModule ->
                    baseModule.setManifest(MANIFEST).addFile("dex/classes.dex", TEST_CONTENT))
            .addModule(
                "some_asset_module",
                module ->
                    module
                        .setManifest(ASSET_MODULE_MANIFEST)
                        .addFile("assets/img1.png", TEST_CONTENT))
            .build();

    assertThat(appBundle.getFeatureModules().keySet())
        .containsExactly(BundleModuleName.create("base"));
    assertThat(appBundle.getAssetModules().keySet())
        .containsExactly(BundleModuleName.create("some_asset_module"));
  }

  @Test
  public void baseAndAssetModule_fromZipFile_areSeparated() throws Exception {
    createBasicZipBuilder(BUNDLE_CONFIG)
        .addFileWithProtoContent(ZipPath.create("base/manifest/AndroidManifest.xml"), MANIFEST)
        .addFileWithContent(ZipPath.create("base/dex/classes.dex"), TEST_CONTENT)
        .addFileWithContent(ZipPath.create("base/assets/file.txt"), TEST_CONTENT)
        .addFileWithProtoContent(
            ZipPath.create("asset_module/manifest/AndroidManifest.xml"), ASSET_MODULE_MANIFEST)
        .addFileWithContent(ZipPath.create("asset_module/assets/file.txt"), TEST_CONTENT)
        .writeTo(bundleFile);

    try (ZipFile appBundleZip = new ZipFile(bundleFile.toFile())) {
      AppBundle appBundle = AppBundle.buildFromZip(appBundleZip);
      assertThat(appBundle.getFeatureModules().keySet())
          .containsExactly(BundleModuleName.create("base"));
      assertThat(appBundle.getAssetModules().keySet())
          .containsExactly(BundleModuleName.create("asset_module"));
    }
  }

  @Test
  public void getPackageName() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", baseModule -> baseModule.setManifest(MANIFEST))
            .build();
    assertThat(appBundle.getPackageName()).isEqualTo(PACKAGE_NAME);
  }

  @Test
  public void getPackageName_assetOnly() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .setBundleConfig(BundleConfig.newBuilder().setType(BundleType.ASSET_ONLY).build())
            .addModule("asset1", baseModule -> baseModule.setManifest(ASSET_MODULE_MANIFEST))
            .build();
    assertThat(appBundle.getPackageName()).isEqualTo(PACKAGE_NAME);
  }

  @Test
  public void isAssetOnly() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .setBundleConfig(BundleConfig.newBuilder().setType(BundleType.ASSET_ONLY).build())
            .addModule("asset1", baseModule -> baseModule.setManifest(ASSET_MODULE_MANIFEST))
            .build();
    assertThat(appBundle.isAssetOnly()).isTrue();
  }

  @Test
  public void hasSharedUserId_specifiedInBaseModule_returnsTrue() {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                baseModule ->
                    baseModule.setManifest(
                        androidManifest(PACKAGE_NAME, withSharedUserId("shared_user_id"))))
            .build();
    assertThat(appBundle.hasSharedUserId()).isTrue();
  }

  @Test
  public void hasSharedUserId_specifiedInFeatureModule_returnsFalse() {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", baseModule -> baseModule.setManifest(androidManifest(PACKAGE_NAME)))
            .addModule(
                "feature1",
                featureModule ->
                    featureModule.setManifest(
                        androidManifestForFeature(
                            PACKAGE_NAME, withSharedUserId("shared_user_id"))))
            .build();
    assertThat(appBundle.hasSharedUserId()).isFalse();
  }

  @Test
  public void hasSharedUserId_unSpecified_returnsFalse() {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", baseModule -> baseModule.setManifest(androidManifest(PACKAGE_NAME)))
            .build();
    assertThat(appBundle.hasSharedUserId()).isFalse();
  }

  @Test
  public void bundleLocation_fromZip_areSet() throws Exception {
    String dexZipEntry = "base/dex/classes.dex";
    ZipPath dexModuleEntryPath = ZipPath.create("dex/classes.dex");

    createBasicZipBuilderWithManifest()
        .addFileWithContent(ZipPath.create(dexZipEntry), TEST_CONTENT)
        .writeTo(bundleFile);

    try (ZipFile appBundleZip = new ZipFile(bundleFile.toFile())) {
      AppBundle appBundle = AppBundle.buildFromZip(appBundleZip);
      assertThat(appBundle.getFeatureModules().keySet())
          .containsExactly(BundleModuleName.create("base"));
      assertThat(appBundle.getBaseModule().getEntry(dexModuleEntryPath)).isPresent();
      assertThat(appBundle.getBaseModule().getEntry(dexModuleEntryPath).get().getFileLocation())
          .hasValue(ModuleEntryLocationInZipSource.create(bundleFile, ZipPath.create(dexZipEntry)));
    }
  }

  @Test
  public void bundleLocation_notFromZip_notSet() throws Exception {
    String dexFilePath = "dex/classes.dex";
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", builder -> builder.setManifest(MANIFEST).addFile(dexFilePath))
            .build();
    assertThat(appBundle.getBaseModule().getEntry(ZipPath.create(dexFilePath))).isPresent();
    assertThat(
            appBundle.getBaseModule().getEntry(ZipPath.create(dexFilePath)).get().getFileLocation())
        .isEmpty();
  }

  @Test
  public void storeArchiveEnabled_notPresent_empty() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .setBundleConfig(BundleConfigBuilder.create().build())
            .addModule("base", builder -> builder.setManifest(MANIFEST))
            .build();
    assertThat(appBundle.getStoreArchive()).isEmpty();
  }

  @Test
  public void storeArchiveEnabled_true() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .setBundleConfig(BundleConfigBuilder.create().setStoreArchive(true).build())
            .addModule("base", builder -> builder.setManifest(MANIFEST))
            .build();
    assertThat(appBundle.getStoreArchive().get()).isTrue();
  }

  @Test
  public void storeArchiveEnabled_false() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .setBundleConfig(BundleConfigBuilder.create().setStoreArchive(false).build())
            .addModule("base", builder -> builder.setManifest(MANIFEST))
            .build();
    assertThat(appBundle.getStoreArchive().get()).isFalse();
  }

  @Test
  public void storeArchiveEnabled_optedOutArchive_withXmlFile() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .setBundleConfig(BundleConfigBuilder.create().build())
            .addModule(
                "base",
                builder ->
                    builder.setManifest(MANIFEST).addFile(AppBundle.ARCHIVE_OPT_OUT_XML_PATH))
            .build();
    assertThat(appBundle.getStoreArchive().get()).isFalse();
  }

  @Test
  public void localeConfigEnabled_notPresent_false() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .setBundleConfig(BundleConfigBuilder.create().build())
            .addModule("base", builder -> builder.setManifest(MANIFEST))
            .build();
    assertThat(appBundle.injectLocaleConfig()).isFalse();
  }

  @Test
  public void localeConfigEnabled_true() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .setBundleConfig(BundleConfigBuilder.create().setInjectLocaleConfig(true).build())
            .addModule("base", builder -> builder.setManifest(MANIFEST))
            .build();
    assertThat(appBundle.injectLocaleConfig()).isTrue();
  }

  @Test
  public void localeConfigEnabled_false() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .setBundleConfig(BundleConfigBuilder.create().setInjectLocaleConfig(false).build())
            .addModule("base", builder -> builder.setManifest(MANIFEST))
            .build();
    assertThat(appBundle.injectLocaleConfig()).isFalse();
  }

  @Test
  public void getRuntimeEnabledSdkDependencies_empty() {
    AppBundle appBundle =
        new AppBundleBuilder().addModule("base", builder -> builder.setManifest(MANIFEST)).build();

    assertThat(appBundle.getRuntimeEnabledSdkDependencies()).isEmpty();
  }

  @Test
  public void getRuntimeEnabledSdkDependencies_notEmpty() {
    RuntimeEnabledSdk runtimeEnabledSdk1 =
        RuntimeEnabledSdk.newBuilder()
            .setPackageName("com.test.sdk1")
            .setVersionMajor(12345)
            .setCertificateDigest("AA:BB:CC")
            .build();
    RuntimeEnabledSdk runtimeEnabledSdk2 =
        RuntimeEnabledSdk.newBuilder()
            .setPackageName("com.test.sdk2")
            .setVersionMajor(12345)
            .setCertificateDigest("AA:BB:CC")
            .build();
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .setManifest(MANIFEST)
                        .setRuntimeEnabledSdkConfig(
                            RuntimeEnabledSdkConfig.newBuilder()
                                .addRuntimeEnabledSdk(runtimeEnabledSdk1)
                                .build()))
            .addModule(
                "feature1",
                featureModule ->
                    featureModule
                        .setManifest(androidManifestForFeature(PACKAGE_NAME))
                        .setRuntimeEnabledSdkConfig(
                            RuntimeEnabledSdkConfig.newBuilder()
                                .addRuntimeEnabledSdk(runtimeEnabledSdk2)
                                .build()))
            .build();

    assertThat(appBundle.getRuntimeEnabledSdkDependencies().entrySet())
        .containsExactlyElementsIn(
            ImmutableMap.of(
                    "com.test.sdk1", runtimeEnabledSdk1, "com.test.sdk2", runtimeEnabledSdk2)
                .entrySet());
  }

  @Test
  public void duplicateRuntimeEnabledSdkDependencies_buildThrows() {
    RuntimeEnabledSdkConfig runtimeEnabledSdkConfig =
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk")
                    .setVersionMajor(12345)
                    .setCertificateDigest("AA:BB:CC"))
            .build();
    BundleModule module1 =
        new BundleModuleBuilder("base")
            .setManifest(MANIFEST)
            .setRuntimeEnabledSdkConfig(runtimeEnabledSdkConfig)
            .build();
    BundleModule module2 =
        new BundleModuleBuilder("feature")
            .setManifest(MANIFEST)
            .setRuntimeEnabledSdkConfig(runtimeEnabledSdkConfig)
            .build();

    Throwable e =
        assertThrows(
            InvalidBundleException.class,
            () ->
                AppBundle.buildFromModules(
                    ImmutableList.of(module1, module2),
                    BundleConfig.getDefaultInstance(),
                    BundleMetadata.builder().build()));
    assertThat(e)
        .hasMessageThat()
        .contains("Found multiple dependencies on the same runtime-enabled SDK 'com.test.sdk'.");
  }

  private static ZipBuilder createBasicZipBuilder(BundleConfig config) {
    ZipBuilder zipBuilder = new ZipBuilder();
    zipBuilder.addFileWithContent(ZipPath.create("BundleConfig.pb"), config.toByteArray());
    return zipBuilder;
  }

  private static ZipBuilder createBasicZipBuilderWithManifest() {
    return createBasicZipBuilderWithManifest(BUNDLE_CONFIG);
  }

  private static ZipBuilder createBasicZipBuilderWithManifest(BundleConfig config) {
    return createBasicZipBuilder(config)
        .addFileWithProtoContent(ZipPath.create("base/manifest/AndroidManifest.xml"), MANIFEST);
  }
}
