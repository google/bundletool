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

import static com.android.bundle.Targeting.Abi.AbiAlias.X86;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withFusingAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withHasCode;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withOnDemandAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withUsesSplit;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.createResourceTable;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.fileReference;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.graphicsApiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeAssetsTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.openGlVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.textureCompressionTargeting;
import static com.android.tools.build.bundletool.testing.TestUtils.expectMissingRequiredBuilderPropertyException;
import static com.android.tools.build.bundletool.testing.TestUtils.expectMissingRequiredFlagException;
import static com.android.tools.build.bundletool.testing.truth.zip.TruthZip.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.ConfigurationOuterClass.Configuration;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.Compression;
import com.android.bundle.Files.Assets;
import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.Files.TargetedAssetsDirectory;
import com.android.bundle.Files.TargetedNativeDirectory;
import com.android.bundle.Targeting.Abi;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.NativeDirectoryTargeting;
import com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias;
import com.android.tools.build.bundletool.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.exceptions.ValidationException;
import com.android.tools.build.bundletool.io.ZipBuilder;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.testing.BundleConfigBuilder;
import com.android.tools.build.bundletool.utils.flags.FlagParser;
import com.android.tools.build.bundletool.version.BundleToolVersion;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
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

  @Rule public TemporaryFolder tmp = new TemporaryFolder();
  private Path tmpDir;

  private Path bundlePath;

  @Before
  public void setUp() throws IOException {
    tmpDir = tmp.getRoot().toPath();
    bundlePath = tmpDir.resolve("bundle.aab");
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult() throws Exception {
    Path baseModulePath = buildSampleModule("base");
    Path featureModulePath = buildSampleModule("feature");
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
  public void buildingViaFlagsAndBuilderHasSameResult_optionalBundleConfig() throws Exception {
    Path modulePath = createSimpleBaseModule();

    Path bundleConfigJsonPath = tmpDir.resolve("BundleConfig.pb.json");
    Files.write(
        bundleConfigJsonPath,
        ("{ \"compression\": { \"uncompressedGlob\": [\"foo\"] } }").getBytes(UTF_8));

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
  public void buildingViaFlagsAndBuilderHasSameResult_optionalMetadata() throws Exception {
    Path baseModulePath = buildSampleModule("base");
    Path featureModulePath = buildSampleModule("feature");
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

    // Cannot compare the command objects directly, because the InputStreamSupplier instances in
    // BundleMetadata would not compare equal.
    assertThat(commandViaBuilder.getBundleMetadata().getFileDataMap().keySet())
        .containsExactlyElementsIn(commandViaFlags.getBundleMetadata().getFileDataMap().keySet());
  }

  @Test
  public void validModule() throws Exception {
    XmlNode manifest = androidManifest(PKG_NAME, withHasCode(true));
    ResourceTable resourceTable =
        createResourceTable(
            "icon", fileReference("res/drawable/icon.png", Configuration.getDefaultInstance()));
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

    ZipFile bundle = new ZipFile(bundlePath.toFile());

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
                    .setPath("assets/gfx#opengl_3.0/texture#tcf_atc")
                    .setTargeting(
                        mergeAssetsTargeting(
                            assetsDirectoryTargeting(graphicsApiTargeting(openGlVersionFrom(3))),
                            assetsDirectoryTargeting(
                                textureCompressionTargeting(TextureCompressionFormatAlias.ATC)))))
            .build();
    Path module =
        new ZipBuilder()
            .addFileWithContent(ZipPath.create("assets/anything.dat"), "any".getBytes(UTF_8))
            .addFileWithContent(
                ZipPath.create("assets/gfx#opengl_3.0/texture#tcf_atc/file.dat"),
                "any2".getBytes(UTF_8))
            .addFileWithContent(ZipPath.create("dex/classes.dex"), "dex".getBytes(UTF_8))
            .addFileWithProtoContent(ZipPath.create("manifest/AndroidManifest.xml"), manifest)
            .writeTo(tmpDir.resolve("base.zip"));

    BuildBundleCommand.builder()
        .setOutputPath(bundlePath)
        .setModulesPaths(ImmutableList.of(module))
        .build()
        .execute();

    ZipFile bundle = new ZipFile(bundlePath.toFile());

    assertThat(bundle).hasFile("base/assets/anything.dat").withContent("any".getBytes(UTF_8));
    assertThat(bundle)
        .hasFile("base/assets/gfx#opengl_3.0/texture#tcf_atc/file.dat")
        .withContent("any2".getBytes(UTF_8));
    assertThat(bundle).hasFile("base/dex/classes.dex").withContent("dex".getBytes(UTF_8));
    assertThat(bundle)
        .hasFile("base/manifest/AndroidManifest.xml")
        .withContent(manifest.toByteArray());
    assertThat(bundle).hasFile("base/assets.pb").withContent(assetsConfig.toByteArray());
  }

  @Test
  public void assetsAbsent_assetsTargetingIsAbsent() throws Exception {
    Path moduleWithoutAssets = createSimpleBaseModule();

    BuildBundleCommand.builder()
        .setOutputPath(bundlePath)
        .setModulesPaths(ImmutableList.of(moduleWithoutAssets))
        .build()
        .execute();

    ZipFile bundleZip = new ZipFile(bundlePath.toFile());
    assertThat(bundleZip).doesNotHaveFile("base/assets.pb");
  }

  @Test
  public void nativeLibrariesAbsent_abiTargetingIsAbsent() throws Exception {
    Path moduleWithoutAbi = createSimpleBaseModule();

    BuildBundleCommand.builder()
        .setOutputPath(bundlePath)
        .setModulesPaths(ImmutableList.of(moduleWithoutAbi))
        .build()
        .execute();

    ZipFile bundleZip = new ZipFile(bundlePath.toFile());
    assertThat(bundleZip).doesNotHaveFile("base/native.pb");
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

    ZipFile bundle = new ZipFile(bundlePath.toFile());
    assertThat(bundle).hasFile("base/native.pb");

    NativeLibraries actualTargeting =
        NativeLibraries.parseFrom(bundle.getInputStream(new ZipEntry("base/native.pb")));

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

    AppBundle appBundle = AppBundle.buildFromZip(new ZipFile(bundlePath.toFile()));
    assertThat(appBundle.getBundleConfig()).isEqualTo(bundleConfigInBundle);
  }

  @Test
  public void bundleConfig_invalidJsonFile_throws() throws Exception {
    Path modulePath = createSimpleBaseModule();

    Path bundleConfigJsonPath = tmp.newFile("BundleConfig.pb.json").toPath();
    Files.write(
        bundleConfigJsonPath,
        ("{ \"compression\": { \"uncompressedGlob\": \"foo\" } }").getBytes(UTF_8));

    Exception e =
        assertThrows(
            CommandExecutionException.class,
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
    Path baseModulePath = buildSampleModule("base");
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

    ZipFile bundleZip = new ZipFile(bundlePath.toFile());
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

  @Test
  public void mainDexListFile_isStored() throws Exception {
    Path baseModulePath = buildSampleModule("base");
    Path mainDexListFile = tmpDir.resolve("file.txt");
    Files.write(mainDexListFile, new byte[] {0x42});

    BuildBundleCommand.builder()
        .setOutputPath(bundlePath)
        .setModulesPaths(ImmutableList.of(baseModulePath))
        .setMainDexListFile(mainDexListFile)
        .build()
        .execute();

    ZipFile bundleZip = new ZipFile(bundlePath.toFile());
    assertThat(bundleZip)
        .hasFile("BUNDLE-METADATA/com.android.tools.build.bundletool/mainDexList.txt")
        .withContent(new byte[] {0x42});
  }

  @Test
  public void mainDexListFile_setTwice_throws() throws Exception {
    Path baseModulePath = buildSampleModule("base");
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
    Path tmpBaseModulePath = Files.move(buildSampleModule("base"), tmpDir.resolve("base.zip.tmp"));
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
    Path baseModulePath = buildSampleModule("base");

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
    Path baseModulePath = buildSampleModule("base");
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
    Path moduleInDirA = tmp.newFolder("a").toPath().resolve("module.zip");
    new ZipBuilder().writeTo(moduleInDirA);
    Path moduleInDirB = tmp.newFolder("b").toPath().resolve("module.zip");
    new ZipBuilder().writeTo(moduleInDirB);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                BuildBundleCommand.builder()
                    .setOutputPath(bundlePath)
                    .setModulesPaths(ImmutableList.of(moduleInDirA, moduleInDirB))
                    .build()
                    .execute());

    assertThat(exception).hasMessageThat().contains("must have unique filenames");
  }

  @Test
  public void metadataFileDoesNotExist_throws() throws Exception {
    Path baseModulePath = buildSampleModule("base");
    Path nonExistentFilePath = tmpDir.resolve("metadata.txt");

    ValidationException exceptionViaApi =
        assertThrows(
            ValidationException.class,
            () ->
                BuildBundleCommand.builder()
                    .setOutputPath(bundlePath)
                    .setModulesPaths(ImmutableList.of(baseModulePath))
                    .addMetadataFile("com.some.namespace", "file-name", nonExistentFilePath)
                    .build()
                    .execute());
    assertThat(exceptionViaApi).hasMessageThat().matches("Metadata file .* does not exist.");

    ValidationException exceptionViaFlags =
        assertThrows(
            ValidationException.class,
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
    Path baseModulePath = buildSampleModule("base");
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
    Path baseModulePath = buildSampleModule("base");
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

    ValidationException exception =
        assertThrows(ValidationException.class, () -> command.execute());

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

    ValidationException exception =
        assertThrows(ValidationException.class, () -> command.execute());

    assertThat(exception).hasMessageThat().contains("Found cyclic dependency between modules");
  }

  @Test
  public void runsResourceTableValidator_resourceTableReferencingNonExistingFile_throws()
      throws Exception {

    ResourceTable resourceTable =
        createResourceTable(
            "icon", fileReference("res/drawable/icon.png", Configuration.getDefaultInstance()));
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

    ValidationException exception =
        assertThrows(ValidationException.class, () -> command.execute());

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

    AppBundle appBundle = AppBundle.buildFromZip(new ZipFile(bundlePath.toFile()));
    assertThat(appBundle.getBundleConfig().getBundletool().getVersion())
        .isEqualTo(BundleToolVersion.getCurrentVersion().toString());
  }

  @Test
  public void printHelp_doesNotCrash() {
    BuildBundleCommand.help();
  }

  private Path createSimpleBaseModule() throws IOException {
    return new ZipBuilder()
        .addFileWithProtoContent(
            ZipPath.create("manifest/AndroidManifest.xml"), androidManifest(PKG_NAME))
        .writeTo(tmpDir.resolve("base.zip"));
  }

  private Path buildSampleModule(String moduleName) throws IOException {
    return new ZipBuilder()
        .addFileWithProtoContent(
            ZipPath.create("manifest/AndroidManifest.xml"), androidManifest(PKG_NAME))
        .writeTo(tmpDir.resolve(moduleName + ".zip"));
  }
}
