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

package com.android.tools.build.bundletool.mergers;

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForAssetModule;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForFeature;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForMlModule;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withFeatureCondition;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstallTimeDelivery;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstallTimeRemovableElement;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMetadataValue;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withOnDemandDelivery;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitNameActivity;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitNameService;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Config.BundleConfig;
import com.android.tools.build.bundletool.TestData;
import com.android.tools.build.bundletool.io.ZipBuilder;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.testing.BundleConfigBuilder;
import com.google.common.truth.Correspondence;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the {@link BundleModuleMerger} class. */
@RunWith(JUnit4.class)
public class BundleModuleMergerTest {
  private static final byte[] TEST_CONTENT = new byte[1];
  private static final byte[] TEST_CONTENT_2 = new byte[2];
  private static final BundleConfig BUNDLE_CONFIG_1_0_0 =
      BundleConfigBuilder.create().setVersion("1.0.0").build();
  private static final BundleConfig BUNDLE_CONFIG_1_8_0 =
      BundleConfigBuilder.create().setVersion("1.8.0").build();
  private static final BundleConfig BUNDLE_CONFIG_0_14_0 =
      BundleConfigBuilder.create().setVersion("0.14.0").build();
  private static final XmlNode MANIFEST = androidManifest("com.test.app.detail");

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private Path bundleFile;

  @Before
  public void setUp() {
    bundleFile = tmp.getRoot().toPath().resolve("bundle.aab");
  }

  @Test
  public void testMultipleModulesWithInstallTime_implicitMerging() throws Exception {
    XmlNode installTimeModuleManifest =
        androidManifestForFeature("com.test.app.detail", withInstallTimeDelivery());
    createBasicZipBuilder(BUNDLE_CONFIG_1_0_0)
        .addFileWithProtoContent(ZipPath.create("base/manifest/AndroidManifest.xml"), MANIFEST)
        .addFileWithContent(ZipPath.create("base/dex/classes.dex"), TEST_CONTENT)
        .addFileWithContent(ZipPath.create("base/assets/baseAssetfile.txt"), TEST_CONTENT)
        .addFileWithProtoContent(
            ZipPath.create("detail/manifest/AndroidManifest.xml"), installTimeModuleManifest)
        .addFileWithContent(ZipPath.create("detail/assets/detailsAssetfile.txt"), TEST_CONTENT)
        .writeTo(bundleFile);

    try (ZipFile appBundleZip = new ZipFile(bundleFile.toFile())) {
      AppBundle appBundle =
          BundleModuleMerger.mergeNonRemovableInstallTimeModules(
              AppBundle.buildFromZip(appBundleZip), /* overrideBundleToolVersion = */ false);
      assertThat(appBundle.getFeatureModules().keySet())
          .comparingElementsUsing(equalsBundleModuleName())
          .containsExactly("base");
    }
  }

  @Test
  public void testMultipleModulesWithInstallTime_implicitMerging_duplicateModuleEntries()
      throws Exception {
    XmlNode installTimeModuleManifest =
        androidManifestForFeature("com.test.app.detail", withInstallTimeDelivery());
    createBasicZipBuilder(BUNDLE_CONFIG_1_0_0)
        .addFileWithProtoContent(ZipPath.create("base/manifest/AndroidManifest.xml"), MANIFEST)
        .addFileWithContent(ZipPath.create("base/dex/classes.dex"), TEST_CONTENT)
        .addFileWithContent(ZipPath.create("base/assets/baseAssetfile.txt"), TEST_CONTENT)
        .addFileWithProtoContent(
            ZipPath.create("detail1/manifest/AndroidManifest.xml"), installTimeModuleManifest)
        .addFileWithContent(ZipPath.create("detail1/assets/detailsAssetfile.txt"), TEST_CONTENT)
        .addFileWithProtoContent(
            ZipPath.create("detail2/manifest/AndroidManifest.xml"), installTimeModuleManifest)
        .addFileWithContent(ZipPath.create("detail2/assets/baseAssetfile.txt"), TEST_CONTENT)
        .addFileWithContent(ZipPath.create("detail2/assets/detailsAssetfile.txt"), TEST_CONTENT)
        .writeTo(bundleFile);

    try (ZipFile appBundleZip = new ZipFile(bundleFile.toFile())) {
      AppBundle appBundle =
          BundleModuleMerger.mergeNonRemovableInstallTimeModules(
              AppBundle.buildFromZip(appBundleZip), /* overrideBundleToolVersion = */ false);
      assertThat(appBundle.getFeatureModules().keySet())
          .comparingElementsUsing(equalsBundleModuleName())
          .containsExactly("base");
    }
  }

  @Test
  public void testMultipleModulesWithInstallTime_throws_duplicateModuleEntriesWithDifferentContent()
      throws Exception {
    XmlNode installTimeModuleManifest =
        androidManifestForFeature("com.test.app.detail", withInstallTimeDelivery());
    createBasicZipBuilder(BUNDLE_CONFIG_1_0_0)
        .addFileWithProtoContent(ZipPath.create("base/manifest/AndroidManifest.xml"), MANIFEST)
        .addFileWithContent(ZipPath.create("base/dex/classes.dex"), TEST_CONTENT)
        .addFileWithContent(ZipPath.create("base/assets/baseAssetfile.txt"), TEST_CONTENT)
        .addFileWithProtoContent(
            ZipPath.create("detail1/manifest/AndroidManifest.xml"), installTimeModuleManifest)
        .addFileWithContent(ZipPath.create("detail1/assets/detailsAssetfile.txt"), TEST_CONTENT)
        .addFileWithProtoContent(
            ZipPath.create("detail2/manifest/AndroidManifest.xml"), installTimeModuleManifest)
        .addFileWithContent(ZipPath.create("detail2/assets/baseAssetfile.txt"), TEST_CONTENT_2)
        .writeTo(bundleFile);

    try (ZipFile appBundleZip = new ZipFile(bundleFile.toFile())) {
      InvalidBundleException exception =
          assertThrows(
              InvalidBundleException.class,
              () ->
                  BundleModuleMerger.mergeNonRemovableInstallTimeModules(
                      AppBundle.buildFromZip(appBundleZip),
                      /* overrideBundleToolVersion = */ false));
      assertThat(exception)
          .hasMessageThat()
          .contains("Existing module entry 'assets/baseAssetfile.txt' with different contents.");
    }
  }

  @Test
  public void testMultipleModulesWithInstallTime_noMergingIfBuiltWithOlderBundleTool()
      throws Exception {
    XmlNode installTimeModuleManifest =
        androidManifestForFeature("com.test.app.detail", withInstallTimeDelivery());
    createBasicZipBuilder(BUNDLE_CONFIG_0_14_0)
        .addFileWithProtoContent(ZipPath.create("base/manifest/AndroidManifest.xml"), MANIFEST)
        .addFileWithContent(ZipPath.create("base/dex/classes.dex"), TEST_CONTENT)
        .addFileWithContent(ZipPath.create("base/assets/baseAssetfile.txt"), TEST_CONTENT)
        .addFileWithProtoContent(
            ZipPath.create("detail/manifest/AndroidManifest.xml"), installTimeModuleManifest)
        .addFileWithContent(ZipPath.create("detail/assets/detailsAssetfile.txt"), TEST_CONTENT)
        .writeTo(bundleFile);

    try (ZipFile appBundleZip = new ZipFile(bundleFile.toFile())) {
      AppBundle appBundle =
          BundleModuleMerger.mergeNonRemovableInstallTimeModules(
              AppBundle.buildFromZip(appBundleZip), /* overrideBundleToolVersion = */ false);
      assertThat(appBundle.getFeatureModules().keySet())
          .comparingElementsUsing(equalsBundleModuleName())
          .containsExactly("base", "detail");
    }
  }

  @Test
  public void testMultipleModulesWithInstallTime_bundleToolVersionOverride() throws Exception {
    XmlNode installTimeModuleManifest =
        androidManifestForFeature("com.test.app.detail", withInstallTimeDelivery());
    createBasicZipBuilder(BUNDLE_CONFIG_0_14_0)
        .addFileWithProtoContent(ZipPath.create("base/manifest/AndroidManifest.xml"), MANIFEST)
        .addFileWithContent(ZipPath.create("base/dex/classes.dex"), TEST_CONTENT)
        .addFileWithContent(ZipPath.create("base/assets/baseAssetfile.txt"), TEST_CONTENT)
        .addFileWithProtoContent(
            ZipPath.create("detail/manifest/AndroidManifest.xml"), installTimeModuleManifest)
        .addFileWithContent(ZipPath.create("detail/assets/detailsAssetfile.txt"), TEST_CONTENT)
        .writeTo(bundleFile);

    try (ZipFile appBundleZip = new ZipFile(bundleFile.toFile())) {
      AppBundle appBundle =
          BundleModuleMerger.mergeNonRemovableInstallTimeModules(
              AppBundle.buildFromZip(appBundleZip), /* overrideBundleToolVersion = */ true);

      assertThat(appBundle.getFeatureModules().keySet())
          .comparingElementsUsing(equalsBundleModuleName())
          .containsExactly("base");
    }
  }

  // Override ignored for bundles built by bundletool version >= 1.0.0. Allows developers to
  // selectively merge modules.
  @Test
  public void testMultipleModulesWithInstallTime_bundleToolVersionOverrideIgnored()
      throws Exception {
    XmlNode installTimeModuleManifest =
        androidManifestForFeature("com.test.app.detail", withInstallTimeDelivery());
    XmlNode installTimeNonRemovableModuleManifest =
        androidManifestForFeature("com.test.app.detail", withInstallTimeRemovableElement(false));
    XmlNode installTimeRemovableModuleManifest =
        androidManifestForFeature("com.test.app.detail", withInstallTimeRemovableElement(true));
    createBasicZipBuilder(BUNDLE_CONFIG_1_0_0)
        .addFileWithProtoContent(ZipPath.create("base/manifest/AndroidManifest.xml"), MANIFEST)
        .addFileWithContent(ZipPath.create("base/dex/classes.dex"), TEST_CONTENT)
        .addFileWithContent(ZipPath.create("base/assets/baseAssetfile.txt"), TEST_CONTENT)
        // merged by default
        .addFileWithProtoContent(
            ZipPath.create("detail/manifest/AndroidManifest.xml"), installTimeModuleManifest)
        .addFileWithContent(ZipPath.create("detail/assets/detailsAssetfile.txt"), TEST_CONTENT)
        .addFileWithProtoContent(
            ZipPath.create("detail2/manifest/AndroidManifest.xml"),
            installTimeNonRemovableModuleManifest)
        .addFileWithContent(ZipPath.create("detail2/assets/detailsAssetfile.txt"), TEST_CONTENT)
        .addFileWithProtoContent(
            ZipPath.create("detail3/manifest/AndroidManifest.xml"),
            installTimeRemovableModuleManifest)
        .addFileWithContent(ZipPath.create("detail3/assets/detailsAssetfile.txt"), TEST_CONTENT)
        .writeTo(bundleFile);

    try (ZipFile appBundleZip = new ZipFile(bundleFile.toFile())) {
      AppBundle appBundle =
          BundleModuleMerger.mergeNonRemovableInstallTimeModules(
              AppBundle.buildFromZip(appBundleZip), /* overrideBundleToolVersion = */ true);

      assertThat(appBundle.getFeatureModules().keySet())
          .comparingElementsUsing(equalsBundleModuleName())
          .containsExactly("base", "detail3");
    }
  }

  @Test
  public void testMultipleModulesWithInstallTime_notMergingAssetModules() throws Exception {
    XmlNode assetModuleManifest =
        androidManifestForAssetModule("com.test.app.detail", withInstallTimeDelivery());
    createBasicZipBuilder(BUNDLE_CONFIG_1_0_0)
        .addFileWithProtoContent(ZipPath.create("base/manifest/AndroidManifest.xml"), MANIFEST)
        .addFileWithContent(ZipPath.create("base/dex/classes.dex"), TEST_CONTENT)
        .addFileWithContent(ZipPath.create("base/assets/baseAssetfile.txt"), TEST_CONTENT)
        .addFileWithProtoContent(
            ZipPath.create("detail/manifest/AndroidManifest.xml"), assetModuleManifest)
        .addFileWithContent(ZipPath.create("detail/assets/detailsAssetfile.txt"), TEST_CONTENT)
        .writeTo(bundleFile);

    try (ZipFile appBundleZip = new ZipFile(bundleFile.toFile())) {
      AppBundle appBundle =
          BundleModuleMerger.mergeNonRemovableInstallTimeModules(
              AppBundle.buildFromZip(appBundleZip), /* overrideBundleToolVersion = */ false);

      assertThat(appBundle.getAssetModules().keySet())
          .comparingElementsUsing(equalsBundleModuleName())
          .containsExactly("detail");
      assertThat(appBundle.getFeatureModules().keySet())
          .comparingElementsUsing(equalsBundleModuleName())
          .containsExactly("base");
    }
  }

  @Test
  public void testMultipleModulesWithInstallTime_notMergingMlModules() throws Exception {
    XmlNode installTimeModuleManifest =
        androidManifestForMlModule("com.test.app.detail", withInstallTimeDelivery());
    createBasicZipBuilder(BUNDLE_CONFIG_1_0_0)
        .addFileWithProtoContent(ZipPath.create("base/manifest/AndroidManifest.xml"), MANIFEST)
        .addFileWithContent(ZipPath.create("base/dex/classes.dex"), TEST_CONTENT)
        .addFileWithContent(ZipPath.create("base/assets/baseAssetfile.txt"), TEST_CONTENT)
        .addFileWithProtoContent(
            ZipPath.create("detail/manifest/AndroidManifest.xml"), installTimeModuleManifest)
        .addFileWithContent(ZipPath.create("detail/assets/detailsAssetfile.txt"), TEST_CONTENT)
        .writeTo(bundleFile);

    try (ZipFile appBundleZip = new ZipFile(bundleFile.toFile())) {
      AppBundle appBundle =
          BundleModuleMerger.mergeNonRemovableInstallTimeModules(
              AppBundle.buildFromZip(appBundleZip), /* overrideBundleToolVersion = */ false);

      assertThat(appBundle.getFeatureModules().keySet())
          .comparingElementsUsing(equalsBundleModuleName())
          .containsExactly("base", "detail");
    }
  }

  @Test
  public void testMultipleModulesWithInstallTime_explicitMerging() throws Exception {
    XmlNode installTimeModuleManifest =
        androidManifestForFeature("com.test.app.detail", withInstallTimeRemovableElement(false));
    createBasicZipBuilder(BUNDLE_CONFIG_1_0_0)
        .addFileWithProtoContent(ZipPath.create("base/manifest/AndroidManifest.xml"), MANIFEST)
        .addFileWithContent(ZipPath.create("base/dex/classes.dex"), TEST_CONTENT)
        .addFileWithContent(ZipPath.create("base/assets/baseAssetfile.txt"), TEST_CONTENT)
        .addFileWithProtoContent(
            ZipPath.create("detail/manifest/AndroidManifest.xml"), installTimeModuleManifest)
        .addFileWithContent(ZipPath.create("detail/assets/detailsAssetfile.txt"), TEST_CONTENT)
        .writeTo(bundleFile);

    try (ZipFile appBundleZip = new ZipFile(bundleFile.toFile())) {
      AppBundle appBundlePreMerge = AppBundle.buildFromZip(appBundleZip);
      AppBundle appBundlePostMerge =
          BundleModuleMerger.mergeNonRemovableInstallTimeModules(
              appBundlePreMerge, /* overrideBundleToolVersion = */ false);

      assertThat(
              appBundlePreMerge.getModules().values().stream()
                  .mapToLong(module -> module.getEntries().size())
                  .sum())
          .isEqualTo(
              appBundlePostMerge.getModules().values().stream()
                  .mapToLong(module -> module.getEntries().size())
                  .sum());
      assertThat(appBundlePostMerge.getFeatureModules().keySet())
          .comparingElementsUsing(equalsBundleModuleName())
          .containsExactly("base");
    }
  }

  @Test
  public void testMultipleModulesWithInstallTime_mergingOptedOut() throws Exception {
    XmlNode installTimeModuleManifest =
        androidManifestForFeature("com.test.app.detail", withInstallTimeRemovableElement(true));
    createBasicZipBuilder(BUNDLE_CONFIG_1_0_0)
        .addFileWithProtoContent(ZipPath.create("base/manifest/AndroidManifest.xml"), MANIFEST)
        .addFileWithContent(ZipPath.create("base/dex/classes.dex"), TEST_CONTENT)
        .addFileWithContent(ZipPath.create("base/assets/baseAssetfile.txt"), TEST_CONTENT)
        .addFileWithProtoContent(
            ZipPath.create("detail/manifest/AndroidManifest.xml"), installTimeModuleManifest)
        .addFileWithContent(ZipPath.create("detail/assets/detailsAssetfile.txt"), TEST_CONTENT)
        .writeTo(bundleFile);

    try (ZipFile appBundleZip = new ZipFile(bundleFile.toFile())) {
      AppBundle appBundle =
          BundleModuleMerger.mergeNonRemovableInstallTimeModules(
              AppBundle.buildFromZip(appBundleZip), /* overrideBundleToolVersion = */ false);

      assertThat(appBundle.getFeatureModules().keySet())
          .comparingElementsUsing(equalsBundleModuleName())
          .containsExactly("base", "detail");
    }
  }

  @Test
  public void testDoNotMergeIfNotInstallTime() throws Exception {
    XmlNode onDemandModuleManifest =
        androidManifestForFeature("com.test.app.detail", withOnDemandDelivery());
    createBasicZipBuilder(BUNDLE_CONFIG_1_0_0)
        .addFileWithProtoContent(ZipPath.create("base/manifest/AndroidManifest.xml"), MANIFEST)
        .addFileWithContent(ZipPath.create("base/dex/classes.dex"), TEST_CONTENT)
        .addFileWithContent(ZipPath.create("base/assets/baseAssetfile.txt"), TEST_CONTENT)
        .addFileWithProtoContent(
            ZipPath.create("detail/manifest/AndroidManifest.xml"), onDemandModuleManifest)
        .addFileWithContent(ZipPath.create("detail/assets/detailsAssetfile.txt"), TEST_CONTENT)
        .writeTo(bundleFile);

    try (ZipFile appBundleZip = new ZipFile(bundleFile.toFile())) {
      AppBundle appBundle =
          BundleModuleMerger.mergeNonRemovableInstallTimeModules(
              AppBundle.buildFromZip(appBundleZip), /* overrideBundleToolVersion = */ false);

      assertThat(appBundle.getFeatureModules().keySet())
          .comparingElementsUsing(equalsBundleModuleName())
          .containsExactly("base", "detail");
    }
  }

  @Test
  public void testDoNotMergeIfConditionalModule() throws Exception {
    XmlNode conditionalModuleManifest =
        androidManifest(
            "com.test.app.detail", withMinSdkVersion(24), withFeatureCondition("android.feature"));
    createBasicZipBuilder(BUNDLE_CONFIG_1_0_0)
        .addFileWithProtoContent(ZipPath.create("base/manifest/AndroidManifest.xml"), MANIFEST)
        .addFileWithContent(ZipPath.create("base/dex/classes.dex"), TEST_CONTENT)
        .addFileWithContent(ZipPath.create("base/assets/baseAssetfile.txt"), TEST_CONTENT)
        .addFileWithProtoContent(
            ZipPath.create("detail/manifest/AndroidManifest.xml"), conditionalModuleManifest)
        .addFileWithContent(ZipPath.create("detail/assets/detailsAssetfile.txt"), TEST_CONTENT)
        .writeTo(bundleFile);

    try (ZipFile appBundleZip = new ZipFile(bundleFile.toFile())) {
      AppBundle appBundle =
          BundleModuleMerger.mergeNonRemovableInstallTimeModules(
              AppBundle.buildFromZip(appBundleZip), /* overrideBundleToolVersion = */ false);

      assertThat(appBundle.getFeatureModules().keySet())
          .comparingElementsUsing(equalsBundleModuleName())
          .containsExactly("base", "detail");
    }
  }

  @Test
  public void testMultipleModulesWithInstallTime_implicitMergingDexRenaming() throws Exception {
    XmlNode installTimeModuleManifest =
        androidManifestForFeature("com.test.app.detail", withInstallTimeDelivery());
    createBasicZipBuilder(BUNDLE_CONFIG_1_0_0)
        .addFileWithProtoContent(ZipPath.create("base/manifest/AndroidManifest.xml"), MANIFEST)
        .addFileWithContent(ZipPath.create("base/dex/classes.dex"), TEST_CONTENT)
        .addFileWithContent(ZipPath.create("base/assets/baseAssetfile.txt"), TEST_CONTENT)
        .addFileWithProtoContent(
            ZipPath.create("detail/manifest/AndroidManifest.xml"), installTimeModuleManifest)
        .addFileWithContent(ZipPath.create("detail/assets/detailsAssetfile.txt"), TEST_CONTENT)
        .addFileWithContent(ZipPath.create("detail/dex/classes.dex"), TEST_CONTENT)
        .writeTo(bundleFile);

    try (ZipFile appBundleZip = new ZipFile(bundleFile.toFile())) {
      AppBundle appBundlePreMerge = AppBundle.buildFromZip(appBundleZip);
      AppBundle appBundlePostMerge =
          BundleModuleMerger.mergeNonRemovableInstallTimeModules(
              appBundlePreMerge, /* overrideBundleToolVersion = */ false);
      assertThat(appBundlePostMerge.getModules().keySet().stream().map(BundleModuleName::getName))
          .containsExactly("base");
      assertThat(
              appBundlePreMerge.getModules().values().stream()
                  .mapToLong(module -> module.getEntries().size())
                  .sum())
          .isEqualTo(
              appBundlePostMerge.getModules().values().stream()
                  .mapToLong(module -> module.getEntries().size())
                  .sum());
      assertThat(appBundlePostMerge.getBaseModule().getEntry(ZipPath.create("dex/classes.dex")))
          .isPresent();
      assertThat(appBundlePostMerge.getBaseModule().getEntry(ZipPath.create("dex/classes2.dex")))
          .isPresent();
    }
  }

  @Test
  public void testRealBundle() throws Exception {
    File file = tmp.newFile();
    try (FileOutputStream os = new FileOutputStream(file)) {
      // This bundle has a base module and 3 feature modules: java, assets and initialInstall.
      // initialInstall should be merged into base.
      os.write(TestData.readBytes("testdata/bundle/install-time-permanent-modules.aab"));
    }
    Path bundleFile = file.toPath();

    try (ZipFile appBundleZip = new ZipFile(bundleFile.toFile())) {
      AppBundle appBundlePreMerge = AppBundle.buildFromZip(appBundleZip);
      AppBundle appBundlePostMerge =
          BundleModuleMerger.mergeNonRemovableInstallTimeModules(
              appBundlePreMerge, /* overrideBundleToolVersion = */ false);

      assertThat(appBundlePostMerge.getModules().keySet().stream().map(BundleModuleName::getName))
          .containsExactly("base", "java", "assets");
      assertEntriesPreserved(appBundlePreMerge, appBundlePostMerge);
      assertThat(appBundlePostMerge.getBaseModule().getEntry(ZipPath.create("dex/classes.dex")))
          .isPresent();
      assertThat(appBundlePostMerge.getBaseModule().getEntry(ZipPath.create("dex/classes2.dex")))
          .isPresent();
    }
  }

  @Test
  public void fuseApplicationElementsInManifest_1_8_0() throws Exception {
    XmlNode installTimeModuleManifest =
        androidManifestForFeature(
            "com.test.app.detail",
            withInstallTimeDelivery(),
            withSplitNameActivity("activity1", "detail"),
            withSplitNameService("service", "detail"));
    createBasicZipBuilder(BUNDLE_CONFIG_1_8_0)
        .addFileWithProtoContent(ZipPath.create("base/manifest/AndroidManifest.xml"), MANIFEST)
        .addFileWithProtoContent(
            ZipPath.create("detail/manifest/AndroidManifest.xml"), installTimeModuleManifest)
        .writeTo(bundleFile);

    try (ZipFile appBundleZip = new ZipFile(bundleFile.toFile())) {
      AppBundle appBundle =
          BundleModuleMerger.mergeNonRemovableInstallTimeModules(
              AppBundle.buildFromZip(appBundleZip), /* overrideBundleToolVersion = */ false);

      XmlNode fusedManifest =
          androidManifest(
              "com.test.app.detail",
              withSplitNameActivity("activity1", "detail"),
              withSplitNameService("service", "detail"),
              withMetadataValue("com.android.dynamic.apk.fused.modules", "base,detail"));
      assertThat(appBundle.getBaseModule().getAndroidManifest().getManifestRoot().getProto())
          .isEqualTo(fusedManifest);
    }
  }

  @Test
  public void fuseOnlyActivitiesInManifest_1_0_0() throws Exception {
    XmlNode installTimeModuleManifest =
        androidManifestForFeature(
            "com.test.app.detail",
            withInstallTimeDelivery(),
            withSplitNameActivity("activity1", "detail"),
            withSplitNameService("service", "detail"));
    createBasicZipBuilder(BUNDLE_CONFIG_1_0_0)
        .addFileWithProtoContent(ZipPath.create("base/manifest/AndroidManifest.xml"), MANIFEST)
        .addFileWithProtoContent(
            ZipPath.create("detail/manifest/AndroidManifest.xml"), installTimeModuleManifest)
        .writeTo(bundleFile);

    try (ZipFile appBundleZip = new ZipFile(bundleFile.toFile())) {
      AppBundle appBundle =
          BundleModuleMerger.mergeNonRemovableInstallTimeModules(
              AppBundle.buildFromZip(appBundleZip), /* overrideBundleToolVersion = */ false);

      XmlNode fusedManifest =
          androidManifest(
              "com.test.app.detail",
              withSplitNameActivity("activity1", "detail"),
              withMetadataValue("com.android.dynamic.apk.fused.modules", "base,detail"));
      assertThat(appBundle.getBaseModule().getAndroidManifest().getManifestRoot().getProto())
          .isEqualTo(fusedManifest);
    }
  }

  private static Correspondence<BundleModuleName, String> equalsBundleModuleName() {
    return Correspondence.from(
        (BundleModuleName bundleModuleName, String moduleName) ->
            bundleModuleName.getName().equals(moduleName),
        "equals");
  }

  private static void assertEntriesPreserved(
      AppBundle appBundlePreMerge, AppBundle appBundlePostMerge) {
    assertThat(
            appBundlePreMerge.getModules().values().stream()
                .mapToLong(module -> module.getEntries().size())
                .sum())
        .isEqualTo(
            appBundlePostMerge.getModules().values().stream()
                .mapToLong(module -> module.getEntries().size())
                .sum());

    // Doesn't account for classes2.dex. Verified later.
    assertThat(
            appBundlePostMerge.getModule(BundleModuleName.create("base")).getEntries().stream()
                .map(moduleEntry -> moduleEntry.getPath().toString())
                .collect(Collectors.toSet()))
        .containsAtLeastElementsIn(
            Stream.concat(
                    appBundlePreMerge
                        .getModule(BundleModuleName.create("base"))
                        .getEntries()
                        .stream()
                        .map(moduleEntry -> moduleEntry.getPath().toString()),
                    appBundlePreMerge
                        .getModule(BundleModuleName.create("initialInstall"))
                        .getEntries()
                        .stream()
                        .map(moduleEntry -> moduleEntry.getPath().toString()))
                .collect(Collectors.toSet()));

    assertThat(
            appBundlePreMerge.getModule(BundleModuleName.create("java")).getEntries().stream()
                .map(moduleEntry -> moduleEntry.getPath().toString())
                .collect(Collectors.toSet()))
        .containsExactlyElementsIn(
            appBundlePostMerge.getModule(BundleModuleName.create("java")).getEntries().stream()
                .map(moduleEntry -> moduleEntry.getPath().toString())
                .collect(Collectors.toSet()));
    assertThat(
            appBundlePreMerge.getModule(BundleModuleName.create("assets")).getEntries().stream()
                .map(moduleEntry -> moduleEntry.getPath().toString())
                .collect(Collectors.toSet()))
        .containsExactlyElementsIn(
            appBundlePostMerge.getModule(BundleModuleName.create("assets")).getEntries().stream()
                .map(moduleEntry -> moduleEntry.getPath().toString())
                .collect(Collectors.toSet()));
  }

  private static ZipBuilder createBasicZipBuilder(BundleConfig config) {
    ZipBuilder zipBuilder = new ZipBuilder();
    return zipBuilder.addFileWithContent(ZipPath.create("BundleConfig.pb"), config.toByteArray());
  }
}
