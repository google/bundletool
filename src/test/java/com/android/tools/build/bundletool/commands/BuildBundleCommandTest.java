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

package com.android.tools.build.bundletool.commands;

import static com.android.bundle.Targeting.Abi.AbiAlias.ARM64_V8A;
import static com.android.bundle.Targeting.Abi.AbiAlias.ARMEABI_V7A;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86_64;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withFusingAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withHasCode;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withOnDemandAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitId;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withUsesSplit;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apexImages;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.deviceTierTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeAssetsTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.multiAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedApexImage;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedApexImageWithBuildInfo;
import static com.android.tools.build.bundletool.testing.TargetingUtils.textureCompressionTargeting;
import static com.android.tools.build.bundletool.testing.TestUtils.expectMissingRequiredBuilderPropertyException;
import static com.android.tools.build.bundletool.testing.TestUtils.expectMissingRequiredFlagException;
import static com.android.tools.build.bundletool.testing.truth.zip.TruthZip.assertThat;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.Compression;
import com.android.bundle.Files.ApexImages;
import com.android.bundle.Files.Assets;
import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.Files.TargetedApexImage;
import com.android.bundle.Files.TargetedAssetsDirectory;
import com.android.bundle.Files.TargetedNativeDirectory;
import com.android.bundle.Targeting.Abi;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.NativeDirectoryTargeting;
import com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias;
import com.android.tools.build.bundletool.flags.FlagParser;
import com.android.tools.build.bundletool.io.ZipBuilder;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.testing.BundleConfigBuilder;
import com.android.tools.build.bundletool.testing.ManifestProtoUtils.ManifestMutator;
import com.android.tools.build.bundletool.testing.ResourceTableBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BuildBundleCommandTest {

  private static final String PKG_NAME = "com.test.app";

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  private Path tmpDir;
  private Path bundlePath;

  @Before
  public void setUp() throws IOException {
    tmpDir = tmp.getRoot().toPath();
    bundlePath = tmpDir.resolve("bundle.aab");
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult() throws Exception {
    Path baseModulePath = buildSimpleModule("base");
    Path featureModulePath = buildSimpleModule("feature");
    BuildBundleCommand commandViaBuilder =
        BuildBundleCommand.builder()
            .setOutputPath(bundlePath)
            .setModulesPaths(ImmutableList.of(baseModulePath, featureModulePath))
            .build();
    BuildBundleCommand commandViaFlags =
        BuildBundleCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--output=" + bundlePath,
                    "--modules=" + baseModulePath + "," + featureModulePath));

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_optionalBundleConfig_inJavaViaProto()
      throws Exception {
    Path modulePath = createSimpleBaseModule();

    Path bundleConfigJsonPath = tmpDir.resolve("BundleConfig.pb.json");
    Files.write(
        bundleConfigJsonPath,
        "{ \"compression\": { \"uncompressedGlob\": [\"foo\"] } }".getBytes(UTF_8));

    BuildBundleCommand commandViaBuilder =
        BuildBundleCommand.builder()
            .setOutputPath(bundlePath)
            .setModulesPaths(ImmutableList.of(modulePath))
            // Optional values.
            .setBundleConfig(
                BundleConfig.newBuilder()
                    .setCompression(Compression.newBuilder().addUncompressedGlob("foo"))
                    .build())
            .build();

    BuildBundleCommand commandViaFlags =
        BuildBundleCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--output=" + bundlePath,
                    "--modules=" + modulePath,
                    // Optional values.
                    "--config=" + bundleConfigJsonPath));

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_optionalBundleConfig_inJavaViaFile()
      throws Exception {
    Path modulePath = createSimpleBaseModule();

    Path bundleConfigJsonPath = tmpDir.resolve("BundleConfig.pb.json");
    Files.write(
        bundleConfigJsonPath,
        "{ \"compression\": { \"uncompressedGlob\": [\"foo\"] } }".getBytes(UTF_8));

    BuildBundleCommand commandViaBuilder =
        BuildBundleCommand.builder()
            .setOutputPath(bundlePath)
            .setModulesPaths(ImmutableList.of(modulePath))
            // Optional values.
            .setBundleConfig(bundleConfigJsonPath)
            .build();

    BuildBundleCommand commandViaFlags =
        BuildBundleCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--output=" + bundlePath,
                    "--modules=" + modulePath,
                    // Optional values.
                    "--config=" + bundleConfigJsonPath));

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_optionalMetadata() throws Exception {
    Path baseModulePath = buildSimpleModule("base");
    Path featureModulePath = buildSimpleModule("feature");
    Path metadataFileAPath = Files.createFile(tmpDir.resolve("metadata-A.txt"));
    Path metadataFileBPath = Files.createFile(tmpDir.resolve("metadata-B.txt"));
    BuildBundleCommand commandViaBuilder =
        BuildBundleCommand.builder()
            .setOutputPath(bundlePath)
            .setModulesPaths(ImmutableList.of(baseModulePath, featureModulePath))
            // Optional values.
            .addMetadataFile("com.some.namespace", "metadata-A.txt", metadataFileAPath)
            .addMetadataFile("com.some.namespace", "metadata-B.txt", metadataFileBPath)
            .build();
    BuildBundleCommand commandViaFlags =
        BuildBundleCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--output=" + bundlePath,
                    "--modules=" + baseModulePath + "," + featureModulePath,
                    // Optional values.
                    "--metadata-file=com.some.namespace/metadata-A.txt:" + metadataFileAPath,
                    "--metadata-file=com.some.namespace/metadata-B.txt:" + metadataFileBPath));

    // Cannot compare the command objects directly, because the ByteSource instances in
    // BundleMetadata would not compare equal.
    assertThat(commandViaBuilder.getBundleMetadata().getFileContentMap().keySet())
        .containsExactlyElementsIn(
            commandViaFlags.getBundleMetadata().getFileContentMap().keySet());
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_optionalUncompressed() throws Exception {
    Path baseModulePath = buildSimpleModule("base");
    BuildBundleCommand commandViaBuilder =
        BuildBundleCommand.builder()
            .setOutputPath(bundlePath)
            .setModulesPaths(ImmutableList.of(baseModulePath))
            .setUncompressedBundle(true)
            .build();

    BuildBundleCommand commandViaFlags =
        BuildBundleCommand.fromFlags(
            new FlagParser()
                .parse("--output=" + bundlePath, "--modules=" + baseModulePath, "--uncompressed"));

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_optionalOverwrite() throws Exception {
    Path baseModulePath = buildSimpleModule("base");
    BuildBundleCommand commandViaBuilder =
        BuildBundleCommand.builder()
            .setOutputPath(bundlePath)
            .setModulesPaths(ImmutableList.of(baseModulePath))
            .setOverwriteOutput(true)
            .build();

    BuildBundleCommand commandViaFlags =
        BuildBundleCommand.fromFlags(
            new FlagParser()
                .parse("--output=" + bundlePath, "--modules=" + baseModulePath, "--overwrite"));

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void validModule() throws Exception {
    XmlNode manifest = androidManifest(PKG_NAME, withHasCode(true));
    ResourceTable resourceTable =
        new ResourceTableBuilder()
            .addPackage(PKG_NAME)
            .addDrawableResource("icon", "res/drawable/icon.png")
            .build();
    Path module =
        new ZipBuilder()
            .addFileWithContent(ZipPath.create("assets/anything.dat"), "any".getBytes(UTF_8))
            .addFileWithContent(ZipPath.create("dex/classes.dex"), "dex".getBytes(UTF_8))
            .addFileWithContent(ZipPath.create("lib/x86/libX.so"), "native".getBytes(UTF_8))
            .addFileWithProtoContent(ZipPath.create("manifest/AndroidManifest.xml"), manifest)
            .addFileWithContent(ZipPath.create("res/drawable/icon.png"), "image".getBytes(UTF_8))
            .addFileWithContent(ZipPath.create("root/anything2.dat"), "any2".getBytes(UTF_8))
            .addFileWithProtoContent(ZipPath.create("resources.pb"), resourceTable)
            .writeTo(tmpDir.resolve("base.zip"));

    BuildBundleCommand.builder()
        .setOutputPath(bundlePath)
        .setModulesPaths(ImmutableList.of(module))
        .build()
        .execute();

    try (ZipFile bundle = new ZipFile(bundlePath.toFile())) {
      assertThat(bundle).hasFile("base/assets/anything.dat").withContent("any".getBytes(UTF_8));
      assertThat(bundle).hasFile("base/dex/classes.dex").withContent("dex".getBytes(UTF_8));
      assertThat(bundle).hasFile("base/lib/x86/libX.so").withContent("native".getBytes(UTF_8));
      assertThat(bundle)
          .hasFile("base/manifest/AndroidManifest.xml")
          .withContent(manifest.toByteArray());
      assertThat(bundle).hasFile("base/res/drawable/icon.png").withContent("image".getBytes(UTF_8));
      assertThat(bundle).hasFile("base/root/anything2.dat").withContent("any2".getBytes(UTF_8));
      assertThat(bundle).hasFile("base/resources.pb").withContent(resourceTable.toByteArray());
    }
  }

  @Test
  public void module_riscV64Arch() throws Exception {
    XmlNode manifest = androidManifest(PKG_NAME, withHasCode(true));
    ResourceTable resourceTable =
        new ResourceTableBuilder()
            .addPackage(PKG_NAME)
            .addDrawableResource("icon", "res/drawable/icon.png")
            .build();
    Path module =
        new ZipBuilder()
            .addFileWithContent(ZipPath.create("dex/classes.dex"), "dex".getBytes(UTF_8))
            .addFileWithContent(ZipPath.create("lib/armeabi-v7a/libX.so"), "arm32".getBytes(UTF_8))
            .addFileWithContent(ZipPath.create("lib/arm64-v8a/libX.so"), "arm64".getBytes(UTF_8))
            .addFileWithContent(ZipPath.create("lib/riscv64/libX.so"), "riscv64".getBytes(UTF_8))
            .addFileWithProtoContent(ZipPath.create("manifest/AndroidManifest.xml"), manifest)
            .addFileWithContent(ZipPath.create("res/drawable/icon.png"), "image".getBytes(UTF_8))
            .addFileWithProtoContent(ZipPath.create("resources.pb"), resourceTable)
            .writeTo(tmpDir.resolve("base.zip"));
    NativeLibraries nativeLibraries =
        NativeLibraries.newBuilder()
            .addDirectory(
                TargetedNativeDirectory.newBuilder()
                    .setPath("lib/armeabi-v7a")
                    .setTargeting(
                        NativeDirectoryTargeting.newBuilder()
                            .setAbi(Abi.newBuilder().setAlias(ARMEABI_V7A))))
            .addDirectory(
                TargetedNativeDirectory.newBuilder()
                    .setPath("lib/arm64-v8a")
                    .setTargeting(
                        NativeDirectoryTargeting.newBuilder()
                            .setAbi(Abi.newBuilder().setAlias(ARM64_V8A))))
            .addDirectory(
                TargetedNativeDirectory.newBuilder()
                    .setPath("lib/riscv64")
                    .setTargeting(
                        NativeDirectoryTargeting.newBuilder()
                            .setAbi(Abi.newBuilder().setAlias(AbiAlias.RISCV64))))
            .build();
    BuildBundleCommand.builder()
        .setOutputPath(bundlePath)
        .setModulesPaths(ImmutableList.of(module))
        .build()
        .execute();

    try (ZipFile bundle = new ZipFile(bundlePath.toFile())) {
      assertThat(bundle).hasFile("base/dex/classes.dex").withContent("dex".getBytes(UTF_8));
      assertThat(bundle)
          .hasFile("base/lib/armeabi-v7a/libX.so")
          .withContent("arm32".getBytes(UTF_8));
      assertThat(bundle).hasFile("base/lib/arm64-v8a/libX.so").withContent("arm64".getBytes(UTF_8));
      assertThat(bundle).hasFile("base/lib/riscv64/libX.so").withContent("riscv64".getBytes(UTF_8));
      assertThat(bundle)
          .hasFile("base/manifest/AndroidManifest.xml")
          .withContent(manifest.toByteArray());
      assertThat(bundle).hasFile("base/res/drawable/icon.png").withContent("image".getBytes(UTF_8));
      assertThat(bundle).hasFile("base/resources.pb").withContent(resourceTable.toByteArray());
      assertThat(bundle).hasFile("base/native.pb").withContent(nativeLibraries.toByteArray());
    }
  }

  @Test
  public void validApexModule() throws Exception {
    XmlNode manifest = androidManifest(PKG_NAME, withHasCode(false));
    ImmutableSet<AbiAlias> targetedAbis = ImmutableSet.of(X86_64, X86, ARM64_V8A, ARMEABI_V7A);
    ApexImages apexConfig =
        apexImages(
            targetedImageWithAlternatives("apex/x86_64.img", X86_64, targetedAbis),
            targetedImageWithAlternatives("apex/x86.img", X86, targetedAbis),
            targetedImageWithAlternatives("apex/arm64-v8a.img", ARM64_V8A, targetedAbis),
            targetedImageWithAlternatives("apex/armeabi-v7a.img", ARMEABI_V7A, targetedAbis));
    byte[] apexManifest = "{\"name\": \"com.test.app\"}".getBytes(UTF_8);
    Path module =
        new ZipBuilder()
            .addFileWithContent(ZipPath.create("apex/x86_64.img"), "x86_64".getBytes(UTF_8))
            .addFileWithContent(ZipPath.create("apex/x86.img"), "x86".getBytes(UTF_8))
            .addFileWithContent(ZipPath.create("apex/arm64-v8a.img"), "arm64-v8a".getBytes(UTF_8))
            .addFileWithContent(
                ZipPath.create("apex/armeabi-v7a.img"), "armeabi-v7a".getBytes(UTF_8))
            .addFileWithProtoContent(ZipPath.create("manifest/AndroidManifest.xml"), manifest)
            .addFileWithContent(ZipPath.create("root/apex_manifest.json"), apexManifest)
            .writeTo(tmpDir.resolve("base.zip"));

    BuildBundleCommand.builder()
        .setOutputPath(bundlePath)
        .setModulesPaths(ImmutableList.of(module))
        .build()
        .execute();

    try (ZipFile bundle = new ZipFile(bundlePath.toFile())) {
      assertThat(bundle)
          .hasFile("base/manifest/AndroidManifest.xml")
          .withContent(manifest.toByteArray());
      assertThat(bundle).hasFile("base/apex/x86_64.img").withContent("x86_64".getBytes(UTF_8));
      assertThat(bundle).hasFile("base/apex/x86.img").withContent("x86".getBytes(UTF_8));
      assertThat(bundle)
          .hasFile("base/apex/arm64-v8a.img")
          .withContent("arm64-v8a".getBytes(UTF_8));
      assertThat(bundle)
          .hasFile("base/apex/armeabi-v7a.img")
          .withContent("armeabi-v7a".getBytes(UTF_8));
      assertThat(bundle).hasFile("base/root/apex_manifest.json").withContent(apexManifest);
      assertThat(bundle).hasFile("base/apex.pb").withContent(apexConfig.toByteArray());
    }
  }

  @Test
  public void validApexModuleWithBuildInfo() throws Exception {
    XmlNode manifest = androidManifest(PKG_NAME, withHasCode(false));
    ImmutableSet<AbiAlias> targetedAbis = ImmutableSet.of(X86_64, X86, ARM64_V8A, ARMEABI_V7A);
    ApexImages apexConfig =
        apexImages(
            targetedImageWithBuildInfoAndAlternatives(
                "apex/x86_64.img", "apex/x86_64.build_info.pb", X86_64, targetedAbis),
            targetedImageWithBuildInfoAndAlternatives(
                "apex/x86.img", "apex/x86.build_info.pb", X86, targetedAbis),
            targetedImageWithBuildInfoAndAlternatives(
                "apex/arm64-v8a.img", "apex/arm64-v8a.build_info.pb", ARM64_V8A, targetedAbis),
            targetedImageWithBuildInfoAndAlternatives(
                "apex/armeabi-v7a.img",
                "apex/armeabi-v7a.build_info.pb",
                ARMEABI_V7A,
                targetedAbis));
    byte[] apexManifest = "{\"name\": \"com.test.app\"}".getBytes(UTF_8);
    Path module =
        new ZipBuilder()
            .addFileWithContent(ZipPath.create("apex/x86_64.img"), "x86_64".getBytes(UTF_8))
            .addFileWithContent(
                ZipPath.create("apex/x86_64.build_info.pb"), "x86_64.build_info".getBytes(UTF_8))
            .addFileWithContent(ZipPath.create("apex/x86.img"), "x86".getBytes(UTF_8))
            .addFileWithContent(
                ZipPath.create("apex/x86.build_info.pb"), "x86.build_info".getBytes(UTF_8))
            .addFileWithContent(ZipPath.create("apex/arm64-v8a.img"), "arm64-v8a".getBytes(UTF_8))
            .addFileWithContent(
                ZipPath.create("apex/arm64-v8a.build_info.pb"),
                "arm64-v8a.build_info".getBytes(UTF_8))
            .addFileWithContent(
                ZipPath.create("apex/armeabi-v7a.img"), "armeabi-v7a".getBytes(UTF_8))
            .addFileWithContent(
                ZipPath.create("apex/armeabi-v7a.build_info.pb"),
                "armeabi-v7a.build_info".getBytes(UTF_8))
            .addFileWithProtoContent(ZipPath.create("manifest/AndroidManifest.xml"), manifest)
            .addFileWithContent(ZipPath.create("root/apex_manifest.json"), apexManifest)
            .writeTo(tmpDir.resolve("base.zip"));

    BuildBundleCommand.builder()
        .setOutputPath(bundlePath)
        .setModulesPaths(ImmutableList.of(module))
        .build()
        .execute();

    try (ZipFile bundle = new ZipFile(bundlePath.toFile())) {
      assertThat(bundle)
          .hasFile("base/manifest/AndroidManifest.xml")
          .withContent(manifest.toByteArray());
      assertThat(bundle).hasFile("base/apex/x86_64.img").withContent("x86_64".getBytes(UTF_8));
      assertThat(bundle)
          .hasFile("base/apex/x86_64.build_info.pb")
          .withContent("x86_64.build_info".getBytes(UTF_8));
      assertThat(bundle).hasFile("base/apex/x86.img").withContent("x86".getBytes(UTF_8));
      assertThat(bundle)
          .hasFile("base/apex/x86.build_info.pb")
          .withContent("x86.build_info".getBytes(UTF_8));
      assertThat(bundle)
          .hasFile("base/apex/arm64-v8a.img")
          .withContent("arm64-v8a".getBytes(UTF_8));
      assertThat(bundle)
          .hasFile("base/apex/arm64-v8a.build_info.pb")
          .withContent("arm64-v8a.build_info".getBytes(UTF_8));
      assertThat(bundle)
          .hasFile("base/apex/armeabi-v7a.img")
          .withContent("armeabi-v7a".getBytes(UTF_8));
      assertThat(bundle)
          .hasFile("base/apex/armeabi-v7a.build_info.pb")
          .withContent("armeabi-v7a.build_info".getBytes(UTF_8));
      assertThat(bundle).hasFile("base/root/apex_manifest.json").withContent(apexManifest);
      assertThat(bundle).hasFile("base/apex.pb").withContent(apexConfig.toByteArray());
    }
  }

  @Test
  public void assetsTargeting_generated() throws Exception {
    XmlNode manifest = androidManifest(PKG_NAME, withHasCode(true));
    Assets assetsConfig =
        Assets.newBuilder()
            .addDirectory(
                TargetedAssetsDirectory.newBuilder()
                    .setPath("assets")
                    .setTargeting(AssetsDirectoryTargeting.getDefaultInstance()))
            .addDirectory(
                TargetedAssetsDirectory.newBuilder()
                    .setPath("assets/texture#tcf_atc/device#tier_0")
                    .setTargeting(
                        mergeAssetsTargeting(
                            assetsDirectoryTargeting(
                                textureCompressionTargeting(TextureCompressionFormatAlias.ATC)),
                            assetsDirectoryTargeting(deviceTierTargeting(0)))))
            .build();
    Path module =
        new ZipBuilder()
            .addFileWithContent(ZipPath.create("assets/anything.dat"), "any".getBytes(UTF_8))
            .addFileWithContent(
                ZipPath.create("assets/texture#tcf_atc/device#tier_0/file.dat"),
                "any2".getBytes(UTF_8))
            .addFileWithContent(ZipPath.create("dex/classes.dex"), "dex".getBytes(UTF_8))
            .addFileWithProtoContent(ZipPath.create("manifest/AndroidManifest.xml"), manifest)
            .writeTo(tmpDir.resolve("base.zip"));

    BuildBundleCommand.builder()
        .setOutputPath(bundlePath)
        .setModulesPaths(ImmutableList.of(module))
        .build()
        .execute();

    try (ZipFile bundle = new ZipFile(bundlePath.toFile())) {
      assertThat(bundle).hasFile("base/assets/anything.dat").withContent("any".getBytes(UTF_8));
      assertThat(bundle)
          .hasFile("base/assets/texture#tcf_atc/device#tier_0/file.dat")
          .withContent("any2".getBytes(UTF_8));
      assertThat(bundle).hasFile("base/dex/classes.dex").withContent("dex".getBytes(UTF_8));
      assertThat(bundle)
          .hasFile("base/manifest/AndroidManifest.xml")
          .withContent(manifest.toByteArray());
      assertThat(bundle).hasFile("base/assets.pb").withContent(assetsConfig.toByteArray());
    }
  }

  @Test
  public void assetsAbsent_assetsTargetingIsAbsent() throws Exception {
    Path moduleWithoutAssets = createSimpleBaseModule();

    BuildBundleCommand.builder()
        .setOutputPath(bundlePath)
        .setModulesPaths(ImmutableList.of(moduleWithoutAssets))
        .build()
        .execute();

    try (ZipFile bundleZip = new ZipFile(bundlePath.toFile())) {
      assertThat(bundleZip).doesNotHaveFile("base/assets.pb");
    }
  }

  @Test
  public void nativeLibrariesAbsent_abiTargetingIsAbsent() throws Exception {
    Path moduleWithoutAbi = createSimpleBaseModule();

    BuildBundleCommand.builder()
        .setOutputPath(bundlePath)
        .setModulesPaths(ImmutableList.of(moduleWithoutAbi))
        .build()
        .execute();

    try (ZipFile bundleZip = new ZipFile(bundlePath.toFile())) {
      assertThat(bundleZip).doesNotHaveFile("base/native.pb");
    }
  }

  @Test
  public void apexImagesAbsent_apexTargetingIsAbsent() throws Exception {
    Path moduleWithoutApexImages = createSimpleBaseModule();

    BuildBundleCommand.builder()
        .setOutputPath(bundlePath)
        .setModulesPaths(ImmutableList.of(moduleWithoutApexImages))
        .build()
        .execute();

    try (ZipFile bundleZip = new ZipFile(bundlePath.toFile())) {
      assertThat(bundleZip).doesNotHaveFile("base/apex.pb");
    }
  }

  @Test
  public void nativeLibrariesPresent_abiTargetingIsPresent() throws Exception {
    Path moduleWithAbi =
        new ZipBuilder()
            .addFileWithContent(ZipPath.create("lib/x86/libfast.so"), "native data".getBytes(UTF_8))
            .addFileWithContent(
                ZipPath.create("lib/x86/libfaster.so"), "native data".getBytes(UTF_8))
            .addFileWithProtoContent(
                ZipPath.create("manifest/AndroidManifest.xml"), androidManifest(PKG_NAME))
            .writeTo(tmpDir.resolve("base.zip"));

    BuildBundleCommand.builder()
        .setOutputPath(bundlePath)
        .setModulesPaths(ImmutableList.of(moduleWithAbi))
        .build()
        .execute();

    NativeLibraries actualTargeting;
    try (ZipFile bundle = new ZipFile(bundlePath.toFile())) {
      assertThat(bundle).hasFile("base/native.pb");

      actualTargeting =
          NativeLibraries.parseFrom(bundle.getInputStream(new ZipEntry("base/native.pb")));
    }

    NativeLibraries expectedTargeting =
        NativeLibraries.newBuilder()
            .addDirectory(
                TargetedNativeDirectory.newBuilder()
                    .setPath("lib/x86")
                    .setTargeting(
                        NativeDirectoryTargeting.newBuilder()
                            .setAbi(Abi.newBuilder().setAlias(X86))))
            .build();

    Truth.assertThat(actualTargeting).isEqualTo(expectedTargeting);
  }

  @Test
  public void bundleConfig_saved() throws Exception {
    Path module = createSimpleBaseModule();

    // Any version supplied by the user is ignored and overwritten.
    BundleConfig bundleConfigFromUser = BundleConfigBuilder.create().setVersion("0.0.0").build();
    BundleConfig bundleConfigInBundle =
        new BundleConfigBuilder(bundleConfigFromUser)
            .setVersion(BundleToolVersion.getCurrentVersion().toString())
            .build();
    assertThat(bundleConfigFromUser.getBundletool().getVersion())
        .isNotEqualTo(bundleConfigInBundle.getBundletool().getVersion());

    BuildBundleCommand.builder()
        .setModulesPaths(ImmutableList.of(module))
        .setOutputPath(bundlePath)
        .setBundleConfig(bundleConfigFromUser)
        .build()
        .execute();

    try (ZipFile appBundleZip = new ZipFile(bundlePath.toFile())) {
      AppBundle appBundle = AppBundle.buildFromZip(appBundleZip);
      assertThat(appBundle.getBundleConfig()).isEqualTo(bundleConfigInBundle);
    }
  }

  @Test
  public void bundleConfig_invalidJsonFile_throws() throws Exception {
    Path modulePath = createSimpleBaseModule();

    Path bundleConfigJsonPath = tmp.newFile("BundleConfig.pb.json").toPath();
    Files.write(
        bundleConfigJsonPath,
        "{ \"compression\": { \"uncompressedGlob\": \"foo\" } }".getBytes(UTF_8));

    Exception e =
        assertThrows(
            InvalidCommandException.class,
            () ->
                BuildBundleCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--output=" + bundlePath,
                            "--modules=" + modulePath,
                            "--config=" + bundleConfigJsonPath)));
    assertThat(e).hasMessageThat().matches("The file '.*' is not a valid BundleConfig JSON file.");
  }

  @Test
  public void metadataFiles_areStored() throws Exception {
    Path baseModulePath = buildSimpleModule("base");
    Path metadataFile1 = tmpDir.resolve("physical-name-ignored.1");
    Path metadataFile2 = tmpDir.resolve("physical-name-ignored.2");
    Path metadataFile3 = tmpDir.resolve("physical-name-ignored.3");
    Files.write(metadataFile1, new byte[] {0x01});
    Files.write(metadataFile2, new byte[] {0x02});
    Files.write(metadataFile3, new byte[] {0x03});

    BuildBundleCommand.builder()
        .setOutputPath(bundlePath)
        .setModulesPaths(ImmutableList.of(baseModulePath))
        .addMetadataFile("first.namespace", "metadata-1.txt", metadataFile1)
        .addMetadataFile("first.namespace", "metadata-2.dat", metadataFile2)
        .addMetadataFile("second.namespace", "metadata-3.bin", metadataFile3)
        .build()
        .execute();

    try (ZipFile bundleZip = new ZipFile(bundlePath.toFile())) {
      assertThat(bundleZip)
          .hasFile("BUNDLE-METADATA/first.namespace/metadata-1.txt")
          .withContent(new byte[] {0x01});
      assertThat(bundleZip)
          .hasFile("BUNDLE-METADATA/first.namespace/metadata-2.dat")
          .withContent(new byte[] {0x02});
      assertThat(bundleZip)
          .hasFile("BUNDLE-METADATA/second.namespace/metadata-3.bin")
          .withContent(new byte[] {0x03});
    }
  }

  @Test
  public void mainDexListFile_isStored() throws Exception {
    Path baseModulePath = buildSimpleModule("base");
    Path mainDexListFile = tmpDir.resolve("file.txt");
    Files.write(mainDexListFile, new byte[] {0x42});

    BuildBundleCommand.builder()
        .setOutputPath(bundlePath)
        .setModulesPaths(ImmutableList.of(baseModulePath))
        .setMainDexListFile(mainDexListFile)
        .build()
        .execute();

    try (ZipFile bundleZip = new ZipFile(bundlePath.toFile())) {
      assertThat(bundleZip)
          .hasFile("BUNDLE-METADATA/com.android.tools.build.bundletool/mainDexList.txt")
          .withContent(new byte[] {0x42});
    }
  }

  @Test
  public void mainDexListFile_setTwice_throws() throws Exception {
    Path baseModulePath = buildSimpleModule("base");
    Path mainDexListFile = tmpDir.resolve("file.txt");
    Files.write(mainDexListFile, new byte[] {0x42});

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                BuildBundleCommand.builder()
                    .setOutputPath(bundlePath)
                    .setModulesPaths(ImmutableList.of(baseModulePath))
                    .setMainDexListFile(mainDexListFile)
                    .setMainDexListFile(mainDexListFile)
                    .build());

    assertThat(exception).hasMessageThat().contains("Multiple entries with same key");
  }

  @Test
  public void directoryZipEntriesInModuleFiles_notIncludedInBundle() throws Exception {
    Path tmpBaseModulePath = Files.move(buildSimpleModule("base"), tmpDir.resolve("base.zip.tmp"));
    Path baseModulePath;
    // Copy the valid bundle, only add a directory zip entry.
    try (ZipFile tmpBaseModuleZip = new ZipFile(tmpBaseModulePath.toFile())) {
      baseModulePath =
          new ZipBuilder()
              .copyAllContentsFromZip(ZipPath.create(""), tmpBaseModuleZip)
              .addDirectory(ZipPath.create("directory-entry"))
              .writeTo(tmpDir.resolve("base.zip"));
    }

    BuildBundleCommand.builder()
        .setOutputPath(bundlePath)
        .setModulesPaths(ImmutableList.of(baseModulePath))
        .build()
        .execute();

    try (ZipFile bundleZip = new ZipFile(bundlePath.toFile())) {
      assertThat(Collections.list(bundleZip.entries()).stream().filter(ZipEntry::isDirectory))
          .isEmpty();
    }
  }

  @Test
  public void modulesNotSet_throws() throws Exception {
    expectMissingRequiredBuilderPropertyException(
        "modulesPaths", () -> BuildBundleCommand.builder().setOutputPath(bundlePath).build());

    expectMissingRequiredFlagException(
        "modules",
        () -> BuildBundleCommand.fromFlags(new FlagParser().parse("--output=" + bundlePath)));
  }

  @Test
  public void outputNotSet_throws() throws Exception {
    Path baseModulePath = buildSimpleModule("base");

    expectMissingRequiredBuilderPropertyException(
        "outputPath",
        () ->
            BuildBundleCommand.builder().setModulesPaths(ImmutableList.of(baseModulePath)).build());

    expectMissingRequiredFlagException(
        "output",
        () -> BuildBundleCommand.fromFlags(new FlagParser().parse("--modules=" + baseModulePath)));
  }

  @Test
  public void outputExists_throws() throws Exception {
    Path baseModulePath = buildSimpleModule("base");
    bundlePath = tmp.newFile("existing-bundle.aab").toPath();

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                BuildBundleCommand.builder()
                    .setOutputPath(bundlePath)
                    .setModulesPaths(ImmutableList.of(baseModulePath))
                    .build()
                    .execute());

    assertThat(exception).hasMessageThat().contains("already exists");
  }

  @Test
  public void moduleDoesNotExist_throws() throws Exception {
    Path nonExistentModulePath = tmpDir.resolve("non_existent.zip");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                BuildBundleCommand.builder()
                    .setOutputPath(bundlePath)
                    .setModulesPaths(ImmutableList.of(nonExistentModulePath))
                    .build()
                    .execute());

    assertThat(exception).hasMessageThat().contains("was not found");
  }

  @Test
  public void moduleWithWrongExtension_throws() throws Exception {
    Path nonZipModule = new ZipBuilder().writeTo(tmpDir.resolve("not_a_zip.txt"));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                BuildBundleCommand.builder()
                    .setOutputPath(bundlePath)
                    .setModulesPaths(ImmutableList.of(nonZipModule))
                    .build()
                    .execute());

    assertThat(exception).hasMessageThat().contains("expected to have '.zip' extension");
  }

  @Test
  public void duplicateModules_throws() throws Exception {
    Path moduleBase = buildSimpleModule("base");
    Path moduleFeature1 = buildSimpleModule("feature");
    Path moduleFeature2 = buildSimpleModule("feature", /* fileName= */ "feature2");

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                BuildBundleCommand.builder()
                    .setOutputPath(bundlePath)
                    .setModulesPaths(ImmutableList.of(moduleBase, moduleFeature1, moduleFeature2))
                    .build()
                    .execute());

    assertThat(exception)
        .hasMessageThat()
        .contains("More than one module have the 'split' attribute set to 'feature'");
  }

  @Test
  public void metadataFileDoesNotExist_throws() throws Exception {
    Path baseModulePath = buildSimpleModule("base");
    Path nonExistentFilePath = tmpDir.resolve("metadata.txt");

    InvalidCommandException exceptionViaApi =
        assertThrows(
            InvalidCommandException.class,
            () ->
                BuildBundleCommand.builder()
                    .setOutputPath(bundlePath)
                    .setModulesPaths(ImmutableList.of(baseModulePath))
                    .addMetadataFile("com.some.namespace", "file-name", nonExistentFilePath)
                    .build()
                    .execute());
    assertThat(exceptionViaApi).hasMessageThat().matches("Metadata file .* does not exist.");

    InvalidCommandException exceptionViaFlags =
        assertThrows(
            InvalidCommandException.class,
            () ->
                BuildBundleCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--output=" + bundlePath,
                            "--modules=" + baseModulePath,
                            "--metadata-file=com.some.namespace/file-name:"
                                + nonExistentFilePath)));
    assertThat(exceptionViaFlags).hasMessageThat().matches("Metadata file .* does not exist.");
  }

  @Test
  public void metadataNamespaceInvalid_throws() throws Exception {
    Path baseModulePath = buildSimpleModule("base");
    Path metadataFilePath = Files.createFile(tmpDir.resolve("metadata.txt"));

    IllegalArgumentException exceptionViaApi =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                BuildBundleCommand.builder()
                    .setOutputPath(bundlePath)
                    .setModulesPaths(ImmutableList.of(baseModulePath))
                    .addMetadataFile("not_namespace_dir", "file-name", metadataFilePath)
                    .build()
                    .execute());
    assertThat(exceptionViaApi)
        .hasMessageThat()
        .contains("Top-level directories for metadata files must be namespaced");

    IllegalArgumentException exceptionViaFlags =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                BuildBundleCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--output=" + bundlePath,
                            "--modules=" + baseModulePath,
                            "--metadata-file=not_namespace_dir/file-name:" + metadataFilePath)));
    assertThat(exceptionViaFlags)
        .hasMessageThat()
        .contains("Top-level directories for metadata files must be namespaced");
  }

  @Test
  public void duplicateMetadataFiles_throws() throws Exception {
    Path baseModulePath = buildSimpleModule("base");
    Path metadataFile1 = Files.createFile(tmpDir.resolve("metadata1.txt"));
    Path metadataFile2 = Files.createFile(tmpDir.resolve("metadata2.txt"));

    IllegalArgumentException exceptionViaApi =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                BuildBundleCommand.builder()
                    .setOutputPath(bundlePath)
                    .setModulesPaths(ImmutableList.of(baseModulePath))
                    .addMetadataFile("com.some.namespace", "duplicate", metadataFile1)
                    .addMetadataFile("com.some.namespace", "duplicate", metadataFile2)
                    .build()
                    .execute());
    assertThat(exceptionViaApi).hasMessageThat().contains("Multiple entries with same key");

    IllegalArgumentException exceptionViaFlags =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                BuildBundleCommand.fromFlags(
                        new FlagParser()
                            .parse(
                                "--output=" + bundlePath,
                                "--modules=" + baseModulePath,
                                "--metadata-file=com.some.namespace/duplicate:" + metadataFile1,
                                "--metadata-file=com.some.namespace/duplicate:" + metadataFile2))
                    .execute());
    assertThat(exceptionViaFlags).hasMessageThat().contains("Multiple entries with same key");
  }

  // Tests bellow validate that specific validators are run for the input modules. For the lack
  // of better options, for each validator there is a single test that is most representative of
  // the validator.
  @Test
  public void runsBundleFilesValidator_rogueFileInModuleRoot_throws() throws Exception {
    Path module =
        new ZipBuilder()
            .addFileWithContent(ZipPath.create("rogue.txt"), "".getBytes(UTF_8))
            .addFileWithProtoContent(
                ZipPath.create("manifest/AndroidManifest.xml"), androidManifest(PKG_NAME))
            .writeTo(tmpDir.resolve("base.zip"));

    BuildBundleCommand command =
        BuildBundleCommand.builder()
            .setOutputPath(bundlePath)
            .setModulesPaths(ImmutableList.of(module))
            .build();

    InvalidBundleException exception = assertThrows(InvalidBundleException.class, command::execute);

    assertThat(exception)
        .hasMessageThat()
        .contains("Module files can be only in pre-defined directories");
  }

  @Test
  public void runsModuleDependencyValidator_cycle_throws() throws Exception {
    Path baseModulePath =
        new ZipBuilder()
            .addFileWithProtoContent(
                ZipPath.create("manifest/AndroidManifest.xml"), androidManifest(PKG_NAME))
            .writeTo(tmpDir.resolve("base.zip"));
    Path module1Path =
        new ZipBuilder()
            .addFileWithProtoContent(
                ZipPath.create("manifest/AndroidManifest.xml"),
                androidManifest(
                    PKG_NAME,
                    withSplitId("module1"),
                    withUsesSplit("module2"),
                    withOnDemandAttribute(false),
                    withFusingAttribute(true)))
            .writeTo(tmpDir.resolve("module1.zip"));
    Path module2Path =
        new ZipBuilder()
            .addFileWithProtoContent(
                ZipPath.create("manifest/AndroidManifest.xml"),
                androidManifest(
                    PKG_NAME,
                    withSplitId("module2"),
                    withUsesSplit("module3"),
                    withOnDemandAttribute(false),
                    withFusingAttribute(true)))
            .writeTo(tmpDir.resolve("module2.zip"));
    Path module3Path =
        new ZipBuilder()
            .addFileWithProtoContent(
                ZipPath.create("manifest/AndroidManifest.xml"),
                androidManifest(
                    PKG_NAME,
                    withSplitId("module3"),
                    withUsesSplit("module1"),
                    withOnDemandAttribute(false),
                    withFusingAttribute(true)))
            .writeTo(tmpDir.resolve("module3.zip"));
    BuildBundleCommand command =
        BuildBundleCommand.builder()
            .setOutputPath(bundlePath)
            .setModulesPaths(
                ImmutableList.of(baseModulePath, module1Path, module2Path, module3Path))
            .build();

    InvalidBundleException exception = assertThrows(InvalidBundleException.class, command::execute);

    assertThat(exception).hasMessageThat().contains("Found cyclic dependency between modules");
  }

  @Test
  public void runsResourceTableValidator_resourceTableReferencingNonExistingFile_throws()
      throws Exception {

    ResourceTable resourceTable =
        new ResourceTableBuilder()
            .addPackage(PKG_NAME)
            .addDrawableResource("icon", "res/drawable/icon.png")
            .build();
    Path module =
        new ZipBuilder()
            .addFileWithProtoContent(ZipPath.create("resources.pb"), resourceTable)
            .addFileWithProtoContent(
                ZipPath.create("manifest/AndroidManifest.xml"), androidManifest(PKG_NAME))
            .writeTo(tmpDir.resolve("base.zip"));

    BuildBundleCommand command =
        BuildBundleCommand.builder()
            .setOutputPath(bundlePath)
            .setModulesPaths(ImmutableList.of(module))
            .build();

    InvalidBundleException exception = assertThrows(InvalidBundleException.class, command::execute);

    assertThat(exception).hasMessageThat().contains("contains references to non-existing files");
  }

  @Test
  public void bundleToolVersionSet() throws Exception {
    Path module = createSimpleBaseModule();

    BuildBundleCommand.builder()
        .setModulesPaths(ImmutableList.of(module))
        .setOutputPath(bundlePath)
        .build()
        .execute();

    try (ZipFile appBundleZip = new ZipFile(bundlePath.toFile())) {
      AppBundle appBundle = AppBundle.buildFromZip(appBundleZip);
      assertThat(appBundle.getBundleConfig().getBundletool().getVersion())
          .isEqualTo(BundleToolVersion.getCurrentVersion().toString());
    }
  }

  @Test
  public void printHelp_doesNotCrash() {
    BuildBundleCommand.help();
  }

  @Test
  public void buildUncompressedBundle() throws Exception {
    Path module =
        new ZipBuilder()
            .addFileWithProtoContent(
                ZipPath.create("manifest/AndroidManifest.xml"), androidManifest(PKG_NAME))
            .addFileWithContent(ZipPath.create("dex/classes.dex"), new byte[8])
            .addFileWithContent(ZipPath.create("res/raw/hello.png"), new byte[8])
            .addFileWithProtoContent(
                ZipPath.create("resources.pb"),
                new ResourceTableBuilder()
                    .addPackage(PKG_NAME)
                    .addDrawableResource("hello", "res/raw/hello.png")
                    .build())
            .writeTo(tmpDir.resolve("base.zip"));

    Path mainDexList = Files.createFile(tmp.getRoot().toPath().resolve("mainDexList.txt"));
    BuildBundleCommand.builder()
        .setModulesPaths(ImmutableList.of(module))
        .setOutputPath(bundlePath)
        .setMainDexListFile(mainDexList)
        .setUncompressedBundle(true)
        .build()
        .execute();

    try (ZipFile bundle = new ZipFile(bundlePath.toFile())) {
      Enumeration<? extends ZipEntry> entries = bundle.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        assertWithMessage("Entry %s is not uncompressed.", entry.getName())
            .that(entry.getMethod())
            .isEqualTo(ZipEntry.STORED);
      }
    }
  }

  @Test
  public void overwriteFlagNotSetRejectsCommandIfOutputAlreadyExists() throws Exception {
    // Create the output.
    Files.createFile(bundlePath);

    Path module = createSimpleBaseModule();
    Exception expected =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                BuildBundleCommand.builder()
                    .setModulesPaths(ImmutableList.of(module))
                    .setOutputPath(bundlePath)
                    .build()
                    .execute());
    assertThat(expected).hasMessageThat().matches("File .* already exists.");
  }

  @Test
  public void overwriteFlagSetOverritesOutput() throws Exception {
    // Create an empty file output.
    Files.createFile(bundlePath);
    checkState(Files.size(bundlePath) == 0);

    Path module = createSimpleBaseModule();
    BuildBundleCommand.builder()
        .setModulesPaths(ImmutableList.of(module))
        .setOverwriteOutput(true)
        .setOutputPath(bundlePath)
        .build()
        .execute();
    assertThat(Files.size(bundlePath)).isGreaterThan(0L);
  }

  @Test
  public void allParentDirectoriesCreated() throws IOException {
    Path outputPath = tmpDir.resolve("non-existing-dir").resolve("bundle.aab");

    Path module = createSimpleBaseModule();
    BuildBundleCommand.builder()
        .setModulesPaths(ImmutableList.of(module))
        .setOverwriteOutput(true)
        .setOutputPath(outputPath)
        .build()
        .execute();

    assertThat(Files.exists(outputPath)).isTrue();
  }

  private Path createSimpleBaseModule() throws IOException {
    return new ZipBuilder()
        .addFileWithProtoContent(
            ZipPath.create("manifest/AndroidManifest.xml"), androidManifest(PKG_NAME))
        .writeTo(tmpDir.resolve("base.zip"));
  }

  /** Builds a module zip with the moduleName as filename. */
  private Path buildSimpleModule(String moduleName) throws IOException {
    return buildSimpleModule(moduleName, /* fileName= */ moduleName);
  }

  private Path buildSimpleModule(String moduleName, String fileName) throws IOException {
    ManifestMutator[] manifestMutators =
        moduleName.equals("base")
            ? new ManifestMutator[0]
            : new ManifestMutator[] {withSplitId(moduleName)};

    return new ZipBuilder()
        .addFileWithProtoContent(
            ZipPath.create("manifest/AndroidManifest.xml"),
            androidManifest(PKG_NAME, manifestMutators))
        .writeTo(tmpDir.resolve(fileName + ".zip"));
  }

  private static TargetedApexImage targetedImageWithAlternatives(
      String path, AbiAlias abi, ImmutableSet<AbiAlias> targetedAbis) {
    return targetedApexImage(
        path,
        multiAbiTargeting(
            abi, Sets.difference(targetedAbis, ImmutableSet.of(abi)).immutableCopy()));
  }

  private static TargetedApexImage targetedImageWithBuildInfoAndAlternatives(
      String path, String buildInfoPath, AbiAlias abi, ImmutableSet<AbiAlias> targetedAbis) {
    return targetedApexImageWithBuildInfo(
        path,
        buildInfoPath,
        multiAbiTargeting(
            abi, Sets.difference(targetedAbis, ImmutableSet.of(abi)).immutableCopy()));
  }
}
