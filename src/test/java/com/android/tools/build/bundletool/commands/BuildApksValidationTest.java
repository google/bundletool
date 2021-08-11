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
package com.android.tools.build.bundletool.commands;

import static com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias.ATC;
import static com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias.ETC1_RGB8;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForFeature;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstant;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMaxSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withTitle;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.TEST_LABEL_RESOURCE_ID;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.resourceTableWithTestLabel;
import static com.android.tools.build.bundletool.testing.TargetingUtils.alternativeTextureCompressionTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assets;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedAssetsDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.textureCompressionTargeting;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Config.SplitDimension.Value;
import com.android.tools.build.bundletool.io.AppBundleSerializer;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.BundleConfigBuilder;
import com.android.tools.build.bundletool.validation.SubValidator;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class BuildApksValidationTest {

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private Path bundlePath;
  private Path outputFilePath;

  @Before
  public void setUp() throws Exception {
    Path rootPath = temporaryFolder.getRoot().toPath();
    bundlePath = rootPath.resolve("bundle.aab");
    outputFilePath = rootPath.resolve("output.apks");
  }

  @Test
  public void buildApksCommand_appTargetingPreL_failsGeneratingInstant() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder.setManifest(
                        androidManifest("com.app", withInstant(true), withMaxSdkVersion(20))))
            .build();
    new AppBundleSerializer().writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder().setBundlePath(bundlePath).setOutputFile(outputFilePath).build();

    Throwable exception = assertThrows(InvalidBundleException.class, command::execute);
    assertThat(exception)
        .hasMessageThat()
        .contains("maxSdkVersion (20) is less than minimum sdk allowed for instant apps (21).");
  }

  @Test
  public void splits_textureFallbackNotPresentInAssetPacks() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .setManifest(androidManifest("com.test.app"))
                        .setResourceTable(resourceTableWithTestLabel("Test feature")))
            .addModule(
                "feature_tcf_assets",
                builder ->
                    builder
                        .addFile("assets/textures#tcf_atc/atc_texture.dat")
                        .addFile("assets/textures#tcf_etc1/etc1_texture.dat")
                        .setAssetsConfig(
                            assets(
                                targetedAssetsDirectory(
                                    "assets/textures#tcf_atc",
                                    assetsDirectoryTargeting(textureCompressionTargeting(ATC))),
                                targetedAssetsDirectory(
                                    "assets/textures#tcf_etc1",
                                    assetsDirectoryTargeting(
                                        textureCompressionTargeting(ETC1_RGB8)))))
                        .setManifest(
                            androidManifestForFeature(
                                "com.test.app",
                                withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID))))
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .addSplitDimension(Value.TEXTURE_COMPRESSION_FORMAT, /* negate= */ false)
                    .build())
            .build();
    new AppBundleSerializer().writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder().setBundlePath(bundlePath).setOutputFile(outputFilePath).build();

    // Splitting by texture compression format is activated, but the textures in
    // one module don't include a fallback folder, used for standalone and universal
    // APKs, so we'll consider the bundle invalid:
    InvalidBundleException exception = assertThrows(InvalidBundleException.class, command::execute);
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "the fallback texture folders (folders without #tcf suffixes) will be used, but module"
                + " 'feature_tcf_assets' has no such folders.");
  }

  @Test
  public void splits_textureDefaultSuffixNotPresentInAssetPacks() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .setManifest(androidManifest("com.test.app"))
                        .setResourceTable(resourceTableWithTestLabel("Test feature")))
            .addModule(
                "feature_tcf_assets",
                builder ->
                    builder
                        .addFile("assets/textures/untargeted_texture.dat")
                        .addFile("assets/textures#tcf_etc1/etc1_texture.dat")
                        .setAssetsConfig(
                            assets(
                                targetedAssetsDirectory(
                                    "assets/textures",
                                    assetsDirectoryTargeting(
                                        alternativeTextureCompressionTargeting(ETC1_RGB8))),
                                targetedAssetsDirectory(
                                    "assets/textures#tcf_etc1",
                                    assetsDirectoryTargeting(
                                        textureCompressionTargeting(ETC1_RGB8)))))
                        .setManifest(
                            androidManifestForFeature(
                                "com.test.app",
                                withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID))))
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .addSplitDimension(
                        Value.TEXTURE_COMPRESSION_FORMAT,
                        /* negate= */ false,
                        /* stripSuffix= */ false,
                        /* defaultSuffix= */ "astc")
                    .build())
            .build();
    new AppBundleSerializer().writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder().setBundlePath(bundlePath).setOutputFile(outputFilePath).build();

    // ASTC format is not present in one module with TCF targeting, so we'll
    // consider the bundle invalid as the configuration specified that this is the default
    // format to use for generating standalone or universal APKs.
    InvalidBundleException exception = assertThrows(InvalidBundleException.class, command::execute);
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "the texture folders for format 'ASTC' will be used, but module 'feature_tcf_assets'"
                + " has no such folders");
  }

  @Test
  public void extraValidator_runsValidation() throws Exception {
    createAppBundle(bundlePath);
    SubValidator extraValidator =
        new SubValidator() {
          @Override
          public void validateBundle(AppBundle bundle) {
            throw InvalidBundleException.builder().withUserMessage("Custom validator").build();
          }
        };

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setExtraValidators(ImmutableList.of(extraValidator))
            .build();

    Exception e = assertThrows(InvalidBundleException.class, command::execute);
    assertThat(e).hasMessageThat().contains("Custom validator");
  }

  private static void createAppBundle(Path path) throws IOException {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(androidManifest("com.app")).build())
            .build();
    new AppBundleSerializer().writeToDisk(appBundle, path);
  }
}
