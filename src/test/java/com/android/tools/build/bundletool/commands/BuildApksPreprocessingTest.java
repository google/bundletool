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

import static com.android.bundle.Targeting.Abi.AbiAlias.ARM64_V8A;
import static com.android.bundle.Targeting.Abi.AbiAlias.ARMEABI_V7A;
import static com.android.tools.build.bundletool.model.utils.ResultUtils.splitApkVariants;
import static com.android.tools.build.bundletool.model.utils.ResultUtils.standaloneApkVariants;
import static com.android.tools.build.bundletool.testing.ApkSetUtils.extractFromApkSetFile;
import static com.android.tools.build.bundletool.testing.ApkSetUtils.extractTocFromApkSetFile;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForAssetModule;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForFeature;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withFusingAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstallTimeDelivery;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMaxSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withTitle;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.TEST_LABEL_RESOURCE_ID;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.resourceTableWithTestLabel;
import static com.android.tools.build.bundletool.testing.TargetingUtils.abiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeLibraries;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedNativeDirectory;
import static com.android.tools.build.bundletool.testing.truth.zip.TruthZip.assertThat;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.ApkSet;
import com.android.bundle.Commands.AssetSliceSet;
import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Commands.Variant;
import com.android.bundle.Targeting.AbiTargeting;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.tools.build.bundletool.io.AppBundleSerializer;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.preprocessors.LocalTestingPreprocessor;
import com.android.tools.build.bundletool.testing.Aapt2Helper;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.BundleConfigBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ExtensionRegistry;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class BuildApksPreprocessingTest {

  private static final byte[] TEST_CONTENT = new byte[100];

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private Path bundlePath;
  private Path outputFilePath;
  private Path outputDir;

  @Before
  public void setUp() throws Exception {
    Path rootPath = temporaryFolder.getRoot().toPath();
    bundlePath = rootPath.resolve("bundle.aab");
    outputFilePath = rootPath.resolve("output.apks");
    outputDir = temporaryFolder.newFolder("output").toPath();
  }

  @Test
  public void renderscript32Bit_warningMessageDisplayed() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("dex/classes.dex")
                        .addFile("assets/script.bc")
                        .setManifest(androidManifest("com.test.app")))
            .build();
    new AppBundleSerializer().writeToDisk(appBundle, bundlePath);

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setOutputPrintStream(new PrintStream(output))
            .build();
    command.execute();

    assertThat(new String(output.toByteArray(), UTF_8))
        .contains("WARNING: App Bundle contains 32-bit RenderScript bitcode file (.bc)");
  }

  @Test
  public void renderscript32Bit_64BitStandaloneAndSplitApksFilteredOut() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                module ->
                    module
                        .addFile("dex/classes.dex")
                        .addFile("assets/script.bc")
                        .addFile("lib/armeabi-v7a/libfoo.so")
                        .addFile("lib/arm64-v8a/libfoo.so")
                        .setManifest(androidManifest("com.test.app", withMinSdkVersion(14)))
                        .setNativeConfig(
                            nativeLibraries(
                                targetedNativeDirectory(
                                    "lib/armeabi-v7a", nativeDirectoryTargeting(ARMEABI_V7A)),
                                targetedNativeDirectory(
                                    "lib/arm64-v8a", nativeDirectoryTargeting(ARM64_V8A)))))
            .setBundleConfig(
                BundleConfigBuilder.create().setUncompressNativeLibraries(false).build())
            .build();
    new AppBundleSerializer().writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder().setBundlePath(bundlePath).setOutputFile(outputFilePath).build();
    command.execute();

    BuildApksResult result;
    try (ZipFile apkSetFile = new ZipFile(outputFilePath.toFile())) {
      result = extractTocFromApkSetFile(apkSetFile, outputDir);
    }
    assertThat(standaloneApkVariants(result)).hasSize(1);
    assertThat(standaloneApkVariants(result).get(0).getTargeting().getAbiTargeting())
        .isEqualTo(abiTargeting(ARMEABI_V7A));
    assertThat(splitApkVariants(result)).hasSize(2);
    ImmutableSet<AbiTargeting> abiTargetings =
        splitApkVariants(result).stream()
            .flatMap(variant -> variant.getApkSetList().stream())
            .map(ApkSet::getApkDescriptionList)
            .flatMap(list -> list.stream().map(ApkDescription::getTargeting))
            .map(ApkTargeting::getAbiTargeting)
            .collect(toImmutableSet());
    assertThat(abiTargetings)
        .containsExactly(AbiTargeting.getDefaultInstance(), abiTargeting(ARMEABI_V7A));
  }

  @Test
  public void renderscript32Bit_64BitLibsOnly_throws() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("dex/classes.dex")
                        .addFile("assets/script.bc")
                        .addFile("lib/arm64-v8a/libfoo.so")
                        .setManifest(androidManifest("com.test.app", withMinSdkVersion(14)))
                        .setNativeConfig(
                            nativeLibraries(
                                targetedNativeDirectory(
                                    "lib/arm64-v8a", nativeDirectoryTargeting(ARM64_V8A)))))
            .build();
    new AppBundleSerializer().writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder().setBundlePath(bundlePath).setOutputFile(outputFilePath).build();
    InvalidBundleException exception = assertThrows(InvalidBundleException.class, command::execute);

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Usage of 64-bit native libraries is disabled, but App Bundle contains only 64-bit"
                + " native libraries.");
  }

  @Test
  public void localTestingMode_enabled_addsMetadata() throws Exception {
    AppBundle appBundle = createAppBundleWithBaseAndFeatureModules("feature");
    new AppBundleSerializer().writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setLocalTestingMode(true)
            .build();

    command.execute();

    try (ZipFile apkSet = new ZipFile(outputFilePath.toFile())) {
      BuildApksResult result = extractTocFromApkSetFile(apkSet, outputDir);
      assertThat(result.hasLocalTestingInfo()).isTrue();
      assertThat(result.getLocalTestingInfo().getEnabled()).isTrue();
      assertThat(result.getLocalTestingInfo().getLocalTestingPath()).isNotEmpty();
      ImmutableList<ApkDescription> apkDescriptions = apkDescriptions(result.getVariantList());
      assertThat(apkDescriptions).isNotEmpty();
      assertThat(apkDescriptions.stream().map(ApkDescription::getPath))
          .contains("splits/base-master.apk");
      for (ApkDescription apkDescription : apkDescriptions) {
        File apk = extractFromApkSetFile(apkSet, apkDescription.getPath(), outputDir);
        // The local testing metadata is set if and only if the apk is the base master.
        assertThat(
                (apkDescription.hasSplitApkMetadata()
                        && apkDescription.getSplitApkMetadata().getSplitId().isEmpty()
                        && apkDescription.getSplitApkMetadata().getIsMasterSplit())
                    || apkDescription.hasStandaloneApkMetadata())
            .isEqualTo(
                extractAndroidManifest(apk)
                    .getMetadataValue(LocalTestingPreprocessor.METADATA_NAME)
                    .isPresent());
      }
    }
  }

  @Test
  public void localTestingMode_disabled_doesNotAddMetadata() throws Exception {
    AppBundle appBundle = createAppBundleWithBaseAndFeatureModules("feature");
    new AppBundleSerializer().writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setLocalTestingMode(false)
            .build();

    command.execute();

    try (ZipFile apkSet = new ZipFile(outputFilePath.toFile())) {
      BuildApksResult result = extractTocFromApkSetFile(apkSet, outputDir);
      assertThat(result.getLocalTestingInfo().getEnabled()).isFalse();
      ImmutableList<ApkDescription> apkDescriptions = apkDescriptions(result.getVariantList());
      assertThat(apkDescriptions).isNotEmpty();
      for (ApkDescription apkDescription : apkDescriptions) {
        File apk = extractFromApkSetFile(apkSet, apkDescription.getPath(), outputDir);
        assertThat(
                extractAndroidManifest(apk)
                    .getMetadataValue(LocalTestingPreprocessor.METADATA_NAME))
            .isEmpty();
      }
    }
  }

  @Test
  public void buildApksCommand_overridesAssetModuleCompression() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                module ->
                    module
                        .addFile("dex/classes.dex", TEST_CONTENT)
                        .addFile("assets/images/image.jpg", TEST_CONTENT)
                        .setManifest(
                            androidManifest(
                                "com.test.app", withMinSdkVersion(15), withMaxSdkVersion(27)))
                        .setResourceTable(resourceTableWithTestLabel("Test feature")))
            .addModule(
                "asset_module",
                module ->
                    module
                        .setManifest(
                            androidManifestForAssetModule(
                                "com.test.app", withInstallTimeDelivery()))
                        .addFile("assets/textures/texture.etc", TEST_CONTENT))
            .build();
    new AppBundleSerializer().writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder().setBundlePath(bundlePath).setOutputFile(outputFilePath).build();
    command.execute();

    try (ZipFile apkSetFile = new ZipFile(outputFilePath.toFile())) {
      BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

      // Standalone variant.
      ImmutableList<Variant> standaloneVariants = standaloneApkVariants(result);
      assertThat(standaloneVariants).hasSize(1);
      Variant standaloneVariant = standaloneVariants.get(0);

      assertThat(standaloneVariant.getApkSetList()).hasSize(1);
      ApkSet standaloneApk = standaloneVariant.getApkSet(0);
      assertThat(standaloneApk.getApkDescriptionList()).hasSize(1);
      assertThat(apkSetFile).hasFile(standaloneApk.getApkDescription(0).getPath());

      File standaloneApkFile =
          extractFromApkSetFile(
              apkSetFile, standaloneApk.getApkDescription(0).getPath(), outputDir);

      try (ZipFile apkZip = new ZipFile(standaloneApkFile)) {
        assertThat(apkZip).hasFile("classes.dex").thatIsCompressed();
        assertThat(apkZip).hasFile("assets/images/image.jpg").thatIsCompressed();
        assertThat(apkZip).hasFile("assets/textures/texture.etc").thatIsUncompressed();
      }

      // L+ assets.
      assertThat(result.getAssetSliceSetCount()).isEqualTo(1);
      AssetSliceSet assetSlice = result.getAssetSliceSet(0);
      assertThat(assetSlice.getApkDescriptionCount()).isEqualTo(1);

      File apkFile =
          extractFromApkSetFile(apkSetFile, assetSlice.getApkDescription(0).getPath(), outputDir);

      try (ZipFile apkZip = new ZipFile(apkFile)) {
        assertThat(apkZip).hasFile("assets/textures/texture.etc").thatIsUncompressed();
      }
    }
  }

  private static AppBundle createAppBundleWithBaseAndFeatureModules(String... featureModuleNames)
      throws IOException {
    AppBundleBuilder appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                module ->
                    module
                        .addFile("assets/base.txt")
                        .setManifest(androidManifest("com.app"))
                        .setResourceTable(resourceTableWithTestLabel("Test feature")));

    for (String featureModuleName : featureModuleNames) {
      appBundle.addModule(
          featureModuleName,
          module ->
              module
                  .addFile("assets/" + featureModuleName)
                  .setManifest(
                      androidManifestForFeature(
                          "com.app",
                          withFusingAttribute(true),
                          withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID))));
    }
    return appBundle.build();
  }

  private AndroidManifest extractAndroidManifest(File apk) {
    Path protoApkPath = outputDir.resolve("proto.apk");
    Aapt2Helper.convertBinaryApkToProtoApk(apk.toPath(), protoApkPath);
    try {
      try (ZipFile protoApk = new ZipFile(protoApkPath.toFile())) {
        return AndroidManifest.create(
            XmlNode.parseFrom(
                protoApk.getInputStream(protoApk.getEntry("AndroidManifest.xml")),
                ExtensionRegistry.getEmptyRegistry()));
      } finally {
        Files.deleteIfExists(protoApkPath);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static ImmutableList<ApkDescription> apkDescriptions(List<Variant> variants) {
    return variants.stream()
        .flatMap(variant -> apkDescriptions(variant).stream())
        .collect(toImmutableList());
  }

  private static ImmutableList<ApkDescription> apkDescriptions(Variant variant) {
    return variant.getApkSetList().stream()
        .flatMap(apkSet -> apkSet.getApkDescriptionList().stream())
        .collect(toImmutableList());
  }
}
