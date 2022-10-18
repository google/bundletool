/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.build.bundletool.commands;

import static com.android.tools.build.bundletool.model.utils.ResultUtils.splitApkVariants;
import static com.android.tools.build.bundletool.model.utils.ResultUtils.standaloneApkVariants;
import static com.android.tools.build.bundletool.testing.ApkSetUtils.extractFromApkSetFile;
import static com.android.tools.build.bundletool.testing.ApkSetUtils.extractTocFromApkSetFile;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withFusingAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withOnDemandDelivery;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withTitle;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.locale;
import static com.android.tools.build.bundletool.testing.TestUtils.filesUnderPath;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.aapt.ConfigurationOuterClass.Configuration;
import com.android.aapt.Resources.ResourceTable;
import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.ApkSet;
import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Commands.Variant;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.BundleConfigBuilder;
import com.android.tools.build.bundletool.testing.ResourceTableBuilder;
import com.android.tools.build.bundletool.testing.TestModule;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import dagger.Component;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** End to end tests for resources pinning to master splits. */
@RunWith(JUnit4.class)
public class BuildApksResourcePinningTest {

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  private Path outputDir;
  private Path outputFilePath;

  @Inject BuildApksManager buildApksManager;

  @Before
  public void setUp() throws Exception {
    outputDir = tmp.newFolder("output").toPath();
    outputFilePath = outputDir.resolve("app.apks");
  }

  @Test
  public void resourceIds_pinnedToMasterSplits() throws Exception {
    ResourceTable baseResourceTable =
        new ResourceTableBuilder()
            .addPackage("com.test.app")
            // 0x7f010000
            .addStringResource("test_label", "Module title")
            // 0x7f020000
            .addFileResourceForMultipleConfigs(
                "drawable",
                "image",
                ImmutableMap.of(
                    Configuration.getDefaultInstance(),
                    "res/drawable/image1.jpg",
                    locale("fr"),
                    "res/drawable-fr/image1.jpg"))
            // 0x7f020001
            .addFileResourceForMultipleConfigs(
                "drawable",
                "image2",
                ImmutableMap.of(
                    Configuration.getDefaultInstance(),
                    "res/drawable/image2.jpg",
                    locale("fr"),
                    "res/drawable-fr/image2.jpg"))
            .build();

    ResourceTable featureResourceTable =
        new ResourceTableBuilder()
            .addPackage("com.test.app.feature", 0x80)
            // 0x80010000
            .addFileResourceForMultipleConfigs(
                "drawable",
                "image3",
                ImmutableMap.of(
                    Configuration.getDefaultInstance(),
                    "res/drawable/image3.jpg",
                    locale("fr"),
                    "res/drawable-fr/image3.jpg"))
            // 0x80010001
            .addFileResourceForMultipleConfigs(
                "drawable",
                "image4",
                ImmutableMap.of(
                    Configuration.getDefaultInstance(),
                    "res/drawable/image4.jpg",
                    locale("fr"),
                    "res/drawable-fr/image4.jpg"))
            .build();

    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("dex/classes.dex")
                        .addFile("res/drawable/image1.jpg")
                        .addFile("res/drawable-fr/image1.jpg")
                        .addFile("res/drawable/image2.jpg")
                        .addFile("res/drawable-fr/image2.jpg")
                        .setManifest(androidManifest("com.test.app", withMinSdkVersion(14)))
                        .setResourceTable(baseResourceTable))
            .addModule(
                "feature",
                builder ->
                    builder
                        .addFile("res/drawable/image3.jpg")
                        .addFile("res/drawable-fr/image3.jpg")
                        .addFile("res/drawable/image4.jpg")
                        .addFile("res/drawable-fr/image4.jpg")
                        .setManifest(
                            androidManifest(
                                "com.test.app",
                                withMinSdkVersion(14),
                                withOnDemandDelivery(),
                                withFusingAttribute(true),
                                withTitle("@string/test_label", 0x7f010000)))
                        .setResourceTable(featureResourceTable))
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .addResourcePinnedToMasterSplit(0x7f020000) // image1 from "base" module
                    .addResourcePinnedToMasterSplit(0x80010001) // image4 from "feature" module
                    .build())
            .build();

    TestComponent.useTestModule(
        this, TestModule.builder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();
    ZipFile apkSetFile = new ZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    // Verifying that standalone APKs contain all entries.
    assertThat(standaloneApkVariants(result)).hasSize(1);
    List<ApkSet> standaloneApkSets = standaloneApkVariants(result).get(0).getApkSetList();
    assertThat(standaloneApkSets).hasSize(1);
    List<ApkDescription> standaloneApkDescription =
        standaloneApkSets.get(0).getApkDescriptionList();
    assertThat(standaloneApkDescription).hasSize(1);
    File standaloneApkFile =
        extractFromApkSetFile(apkSetFile, standaloneApkDescription.get(0).getPath(), outputDir);
    try (ZipFile standaloneZip = new ZipFile(standaloneApkFile)) {
      assertThat(filesUnderPath(standaloneZip, ZipPath.create("res")))
          .containsExactly(
              "res/drawable/image1.jpg",
              "res/drawable-fr/image1.jpg",
              "res/drawable/image2.jpg",
              "res/drawable-fr/image2.jpg",
              "res/drawable/image3.jpg",
              "res/drawable-fr/image3.jpg",
              "res/drawable/image4.jpg",
              "res/drawable-fr/image4.jpg",
              "res/xml/splits0.xml");
    }

    // Verifying split APKs.
    assertThat(splitApkVariants(result)).hasSize(2);
    for (Variant variant : splitApkVariants(result)) {
      List<ApkSet> splitApkSetList = variant.getApkSetList();
      ImmutableMap<String, ApkSet> modules =
          Maps.uniqueIndex(splitApkSetList, apkSet -> apkSet.getModuleMetadata().getName());
      assertThat(modules.keySet()).containsExactly("base", "feature");

      List<ApkDescription> baseModuleApks = modules.get("base").getApkDescriptionList();
      assertThat(baseModuleApks).hasSize(2);
      ImmutableMap<Boolean, ApkDescription> apkBaseMaster =
          Maps.uniqueIndex(
              baseModuleApks,
              apkDescription -> apkDescription.getSplitApkMetadata().getIsMasterSplit());

      ApkDescription baseMaster = apkBaseMaster.get(/* isMasterSplit= */ true);
      File baseMasterFile = extractFromApkSetFile(apkSetFile, baseMaster.getPath(), outputDir);
      try (ZipFile baseMasterZip = new ZipFile(baseMasterFile)) {
        assertThat(filesUnderPath(baseMasterZip, ZipPath.create("res")))
            .containsExactly(
                "res/drawable/image1.jpg",
                "res/drawable-fr/image1.jpg",
                "res/drawable/image2.jpg",
                "res/xml/splits0.xml");

        ApkDescription baseFr = apkBaseMaster.get(/* isMasterSplit= */ false);
        File baseFrFile = extractFromApkSetFile(apkSetFile, baseFr.getPath(), outputDir);
        try (ZipFile baseFrZip = new ZipFile(baseFrFile)) {
          assertThat(filesUnderPath(baseFrZip, ZipPath.create("res")))
              .containsExactly("res/drawable-fr/image2.jpg");
        }

        List<ApkDescription> featureModuleApks = modules.get("feature").getApkDescriptionList();
        assertThat(featureModuleApks).hasSize(2);
        ImmutableMap<Boolean, ApkDescription> apkFeatureMaster =
            Maps.uniqueIndex(
                featureModuleApks,
                apkDescription -> apkDescription.getSplitApkMetadata().getIsMasterSplit());

        ApkDescription featureMaster = apkFeatureMaster.get(/* isMasterSplit= */ true);
        File featureMasterFile =
            extractFromApkSetFile(apkSetFile, featureMaster.getPath(), outputDir);
        try (ZipFile featureMasterZip = new ZipFile(featureMasterFile)) {
          assertThat(filesUnderPath(featureMasterZip, ZipPath.create("res")))
              .containsExactly(
                  "res/drawable/image3.jpg",
                  "res/drawable/image4.jpg",
                  "res/drawable-fr/image4.jpg");
        }

        ApkDescription featureFr = apkFeatureMaster.get(/* isMasterSplit= */ false);
        File featureFrFile = extractFromApkSetFile(apkSetFile, featureFr.getPath(), outputDir);
        try (ZipFile featureFrZip = new ZipFile(featureFrFile)) {
          assertThat(filesUnderPath(featureFrZip, ZipPath.create("res")))
              .containsExactly("res/drawable-fr/image3.jpg");
        }
      }
    }
  }

  @CommandScoped
  @Component(modules = {BuildApksModule.class, TestModule.class})
  interface TestComponent {

    void inject(BuildApksResourcePinningTest test);

    static void useTestModule(BuildApksResourcePinningTest testInstance, TestModule testModule) {
      DaggerBuildApksResourcePinningTest_TestComponent.builder()
          .testModule(testModule)
          .build()
          .inject(testInstance);
    }
  }
}
