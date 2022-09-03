/*
 * Copyright (C) 2022 The Android Open Source Project
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
import static com.android.bundle.Targeting.Abi.AbiAlias.X86_64;
import static com.android.tools.build.bundletool.model.RuntimeEnabledSdkVersionEncoder.VERSION_MAJOR_MAX_VALUE;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitId;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.languageTargeting;
import static com.android.tools.build.bundletool.testing.TestUtils.expectMissingRequiredBuilderPropertyException;
import static com.android.tools.build.bundletool.testing.TestUtils.expectMissingRequiredFlagException;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Config.Bundletool;
import com.android.bundle.Files.Assets;
import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.Files.TargetedAssetsDirectory;
import com.android.bundle.Files.TargetedNativeDirectory;
import com.android.bundle.SdkBundleConfigProto.SdkBundle;
import com.android.bundle.SdkBundleConfigProto.SdkBundleConfig;
import com.android.bundle.SdkModulesConfigOuterClass.RuntimeEnabledSdkVersion;
import com.android.bundle.SdkModulesConfigOuterClass.SdkModulesConfig;
import com.android.bundle.Targeting.Abi;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.NativeDirectoryTargeting;
import com.android.tools.build.bundletool.flags.FlagParser;
import com.android.tools.build.bundletool.io.ZipBuilder;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.utils.ZipUtils;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.testing.ManifestProtoUtils.ManifestMutator;
import com.android.tools.build.bundletool.testing.ResourceTableBuilder;
import com.android.tools.build.bundletool.testing.truth.zip.TruthZip;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BuildSdkBundleCommandTest {

  private static final String PKG_NAME = "com.sdk";
  private static final byte[] SDK_MODULES_CONFIG_CONTENTS =
      ("{ "
              + "bundletool: { version: \"0.0.0\" }, "
              + "sdk_package_name: "
              + PKG_NAME
              + ", "
              + "sdk_version: { major: 123, minor: 234, patch: 345}, "
              + "sdk_provider_class_name: \"com.sdk.EntryPoint\""
              + "}")
          .getBytes(UTF_8);
  private static final SdkModulesConfig SDK_MODULES_CONFIG =
      SdkModulesConfig.newBuilder()
          .setBundletool(Bundletool.newBuilder().setVersion("0.0.0"))
          .setSdkPackageName(PKG_NAME)
          .setSdkVersion(
              RuntimeEnabledSdkVersion.newBuilder().setMajor(123).setMinor(234).setPatch(345))
          .setSdkProviderClassName("com.sdk.EntryPoint")
          .build();
  private static final String VALID_CERT_FINGERPRINT =
      "96:C7:EC:89:3E:69:2A:25:BA:4D:EE:C1:84:E8:33:3F:34:7D:6D:12:26:A1:C1:AA:70:A2:8A:DB:75:3E:02:0A";
  private static final SdkBundleConfig SDK_BUNDLE_CONFIG =
      SdkBundleConfig.newBuilder()
          .addSdkDependencies(
              SdkBundle.newBuilder()
                  .setPackageName(PKG_NAME)
                  .setVersionMajor(123)
                  .setVersionMinor(234)
                  .setBuildTimeVersionPatch(345)
                  .setCertificateDigest(VALID_CERT_FINGERPRINT))
          .build();
  private static final byte[] SDK_BUNDLE_CONFIG_CONTENTS =
      ("{ sdk_dependencies: [{ "
              + "package_name: \"com.sdk\", "
              + "version_major: 123, "
              + "version_minor: 234, "
              + "build_time_version_patch: 345, "
              + "certificate_digest: \""
              + VALID_CERT_FINGERPRINT
              + "\" }]}")
          .getBytes(UTF_8);

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  private Path tmpDir;
  private Path bundlePath;
  private Path sdkModulesConfigPath;

  @Before
  public void setUp() throws IOException {
    tmpDir = tmp.getRoot().toPath();
    bundlePath = tmpDir.resolve("bundle.aab");
    sdkModulesConfigPath = tmpDir.resolve("SdkModulesConfig.pb.json");
    Files.write(sdkModulesConfigPath, SDK_MODULES_CONFIG_CONTENTS);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_modulesConfigInJavaViaProto()
      throws Exception {
    Path baseModulePath = buildSimpleModule("base");
    BuildSdkBundleCommand commandViaBuilder =
        BuildSdkBundleCommand.builder()
            .setOutputPath(bundlePath)
            .setSdkModulesConfig(SDK_MODULES_CONFIG)
            .setModulesPaths(ImmutableList.of(baseModulePath))
            .build();
    BuildSdkBundleCommand commandViaFlags =
        BuildSdkBundleCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--output=" + bundlePath,
                    "--modules=" + baseModulePath,
                    "--sdk-modules-config=" + sdkModulesConfigPath));

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_modulesConfigInJavaViaFile()
      throws Exception {
    Path baseModulePath = buildSimpleModule("base");
    BuildSdkBundleCommand commandViaBuilder =
        BuildSdkBundleCommand.builder()
            .setOutputPath(bundlePath)
            .setSdkModulesConfig(sdkModulesConfigPath)
            .setModulesPaths(ImmutableList.of(baseModulePath))
            .build();
    BuildSdkBundleCommand commandViaFlags =
        BuildSdkBundleCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--output=" + bundlePath,
                    "--modules=" + baseModulePath,
                    "--sdk-modules-config=" + sdkModulesConfigPath));

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_optionalBundleConfig_inJavaViaProto()
      throws Exception {
    Path modulePath = createSimpleBaseModule();

    Path sdkBundleConfigJsonPath = tmpDir.resolve("SdkBundleConfig.pb.json");
    Files.write(sdkBundleConfigJsonPath, SDK_BUNDLE_CONFIG_CONTENTS);

    BuildSdkBundleCommand commandViaBuilder =
        BuildSdkBundleCommand.builder()
            .setOutputPath(bundlePath)
            .setSdkModulesConfig(sdkModulesConfigPath)
            .setModulesPaths(ImmutableList.of(modulePath))
            // Optional values.
            .setSdkBundleConfig(SDK_BUNDLE_CONFIG)
            .build();

    BuildSdkBundleCommand commandViaFlags =
        BuildSdkBundleCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--output=" + bundlePath,
                    "--sdk-modules-config=" + sdkModulesConfigPath,
                    "--modules=" + modulePath,
                    // Optional values.
                    "--sdk-bundle-config=" + sdkBundleConfigJsonPath));

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_optionalBundleConfig_inJavaViaFile()
      throws Exception {
    Path modulePath = createSimpleBaseModule();

    Path sdkBundleConfigJsonPath = tmpDir.resolve("SdkBundleConfig.pb.json");
    Files.write(sdkBundleConfigJsonPath, SDK_BUNDLE_CONFIG_CONTENTS);

    BuildSdkBundleCommand commandViaBuilder =
        BuildSdkBundleCommand.builder()
            .setOutputPath(bundlePath)
            .setSdkModulesConfig(sdkModulesConfigPath)
            .setModulesPaths(ImmutableList.of(modulePath))
            // Optional values.
            .setSdkBundleConfig(sdkBundleConfigJsonPath)
            .build();

    BuildSdkBundleCommand commandViaFlags =
        BuildSdkBundleCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--output=" + bundlePath,
                    "--sdk-modules-config=" + sdkModulesConfigPath,
                    "--modules=" + modulePath,
                    // Optional values.
                    "--sdk-bundle-config=" + sdkBundleConfigJsonPath));

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_optionalMetadata() throws Exception {
    Path baseModulePath = buildSimpleModule("base");
    Path metadataFileAPath = Files.createFile(tmpDir.resolve("metadata-A.txt"));
    Path metadataFileBPath = Files.createFile(tmpDir.resolve("metadata-B.txt"));
    BuildSdkBundleCommand commandViaBuilder =
        BuildSdkBundleCommand.builder()
            .setOutputPath(bundlePath)
            .setSdkModulesConfig(sdkModulesConfigPath)
            .setModulesPaths(ImmutableList.of(baseModulePath))
            // Optional values.
            .addMetadataFile("com.some.namespace", "metadata-A.txt", metadataFileAPath)
            .addMetadataFile("com.some.namespace", "metadata-B.txt", metadataFileBPath)
            .build();
    BuildSdkBundleCommand commandViaFlags =
        BuildSdkBundleCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--output=" + bundlePath,
                    "--sdk-modules-config=" + sdkModulesConfigPath,
                    "--modules=" + baseModulePath,
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
  public void buildingViaFlagsAndBuilderHasSameResult_optionalOverwrite() throws Exception {
    Path baseModulePath = buildSimpleModule("base");
    BuildSdkBundleCommand commandViaBuilder =
        BuildSdkBundleCommand.builder()
            .setOutputPath(bundlePath)
            .setSdkModulesConfig(sdkModulesConfigPath)
            .setModulesPaths(ImmutableList.of(baseModulePath))
            .setOverwriteOutput(true)
            .build();

    BuildSdkBundleCommand commandViaFlags =
        BuildSdkBundleCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--output=" + bundlePath,
                    "--modules=" + baseModulePath,
                    "--sdk-modules-config=" + sdkModulesConfigPath,
                    "--overwrite"));

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_optionalSdkInterfaceDescriptors()
      throws Exception {
    Path baseModulePath = buildSimpleModule("base");
    Path sdkInterfaceDescriptorsPath = buildSdkInterfaceDescriptors("sdk-api.jar");
    BuildSdkBundleCommand commandViaBuilder =
        BuildSdkBundleCommand.builder()
            .setOutputPath(bundlePath)
            .setSdkModulesConfig(sdkModulesConfigPath)
            .setModulesPaths(ImmutableList.of(baseModulePath))
            .setSdkInterfaceDescriptors(sdkInterfaceDescriptorsPath)
            .build();

    BuildSdkBundleCommand commandViaFlags =
        BuildSdkBundleCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--output=" + bundlePath,
                    "--modules=" + baseModulePath,
                    "--sdk-modules-config=" + sdkModulesConfigPath,
                    "--sdk-interface-descriptors=" + sdkInterfaceDescriptorsPath));

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void sdkModulesConfig_invalidJsonFile_throws() throws Exception {
    Path modulePath = createSimpleBaseModule();

    Files.write(sdkModulesConfigPath, "{ bundletool: { unknown_field: false } }".getBytes(UTF_8));

    Exception e =
        assertThrows(
            InvalidCommandException.class,
            () ->
                BuildSdkBundleCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--output=" + bundlePath,
                            "--sdk-modules-config=" + sdkModulesConfigPath,
                            "--modules=" + modulePath)));
    assertThat(e)
        .hasMessageThat()
        .matches("The file '.*' is not a valid SdkModulesConfig JSON file.");
  }

  @Test
  public void sdkBundleConfig_invalidJsonFile_throws() throws Exception {
    Path modulePath = createSimpleBaseModule();

    Path bundleConfigJsonPath = tmp.newFile("SdkBundleConfig.pb.json").toPath();
    Files.write(
        bundleConfigJsonPath,
        ("{ sdk_dependencies: { "
                + "package_name: \"com.sdk\", "
                + "version_major: 123, "
                + "version_minor: 234, "
                + "certificate_digest: \"digest\" "
                + "} }")
            .getBytes(UTF_8));

    Exception e =
        assertThrows(
            InvalidCommandException.class,
            () ->
                BuildSdkBundleCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--output=" + bundlePath,
                            "--sdk-modules-config=" + sdkModulesConfigPath,
                            "--modules=" + modulePath,
                            "--sdk-bundle-config=" + bundleConfigJsonPath)));
    assertThat(e)
        .hasMessageThat()
        .matches("The file '.*' is not a valid SdkBundleConfig JSON file.");
  }

  @Test
  public void modulesNotSet_throws() throws Exception {
    expectMissingRequiredBuilderPropertyException(
        "modulesPaths",
        () ->
            BuildSdkBundleCommand.builder()
                .setOutputPath(bundlePath)
                .setSdkModulesConfig(sdkModulesConfigPath)
                .build());

    expectMissingRequiredFlagException(
        "modules",
        () ->
            BuildSdkBundleCommand.fromFlags(
                new FlagParser()
                    .parse(
                        "--output=" + bundlePath, "--sdk-modules-config=" + sdkModulesConfigPath)));
  }

  @Test
  public void outputNotSet_throws() throws Exception {
    Path baseModulePath = buildSimpleModule("base");

    expectMissingRequiredBuilderPropertyException(
        "outputPath",
        () ->
            BuildSdkBundleCommand.builder()
                .setModulesPaths(ImmutableList.of(baseModulePath))
                .setSdkModulesConfig(sdkModulesConfigPath)
                .build());

    expectMissingRequiredFlagException(
        "output",
        () ->
            BuildSdkBundleCommand.fromFlags(
                new FlagParser()
                    .parse(
                        "--modules=" + baseModulePath,
                        "--sdk-modules-config=" + sdkModulesConfigPath)));
  }

  @Test
  public void sdkModulesConfigNotSet_throws() throws Exception {
    Path baseModulePath = buildSimpleModule("base");

    expectMissingRequiredBuilderPropertyException(
        "sdkModulesConfig",
        () ->
            BuildSdkBundleCommand.builder()
                .setModulesPaths(ImmutableList.of(baseModulePath))
                .setOutputPath(bundlePath)
                .build());

    expectMissingRequiredFlagException(
        "sdk-modules-config",
        () ->
            BuildSdkBundleCommand.fromFlags(
                new FlagParser().parse("--modules=" + baseModulePath, "--output=" + bundlePath)));
  }

  @Test
  public void outputExists_throws() throws Exception {
    Path baseModulePath = buildSimpleModule("base");
    bundlePath = tmp.newFile("existing-bundle.aab").toPath();

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                BuildSdkBundleCommand.builder()
                    .setOutputPath(bundlePath)
                    .setModulesPaths(ImmutableList.of(baseModulePath))
                    .setSdkModulesConfig(sdkModulesConfigPath)
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
                BuildSdkBundleCommand.builder()
                    .setOutputPath(bundlePath)
                    .setModulesPaths(ImmutableList.of(nonExistentModulePath))
                    .setSdkModulesConfig(sdkModulesConfigPath)
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
                BuildSdkBundleCommand.builder()
                    .setOutputPath(bundlePath)
                    .setModulesPaths(ImmutableList.of(nonZipModule))
                    .setSdkModulesConfig(sdkModulesConfigPath)
                    .build()
                    .execute());

    assertThat(exception).hasMessageThat().contains("expected to have '.zip' extension");
  }

  @Test
  public void moduleWithoutManifest_throws() throws Exception {
    Path module = new ZipBuilder().writeTo(tmpDir.resolve("base.zip"));

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                BuildSdkBundleCommand.builder()
                    .setOutputPath(bundlePath)
                    .setModulesPaths(ImmutableList.of(module))
                    .setSdkModulesConfig(sdkModulesConfigPath)
                    .build()
                    .execute());

    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("Module 'base' is missing mandatory file 'manifest/AndroidManifest.xml'.");
  }

  @Test
  public void moduleWithWrongName_throws() throws Exception {
    Path module = buildSimpleModule("module1");

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                BuildSdkBundleCommand.builder()
                    .setOutputPath(bundlePath)
                    .setModulesPaths(ImmutableList.of(module))
                    .setSdkModulesConfig(sdkModulesConfigPath)
                    .build()
                    .execute());

    assertThat(exception).hasMessageThat().isEqualTo("The SDK bundle module must be named 'base'.");
  }

  @Test
  public void metadataFileDoesNotExist_throws() throws Exception {
    Path baseModulePath = buildSimpleModule("base");
    Path nonExistentFilePath = tmpDir.resolve("metadata.txt");

    InvalidCommandException exceptionViaApi =
        assertThrows(
            InvalidCommandException.class,
            () ->
                BuildSdkBundleCommand.builder()
                    .setOutputPath(bundlePath)
                    .setModulesPaths(ImmutableList.of(baseModulePath))
                    .setSdkModulesConfig(sdkModulesConfigPath)
                    .addMetadataFile("com.some.namespace", "file-name", nonExistentFilePath)
                    .build()
                    .execute());
    assertThat(exceptionViaApi).hasMessageThat().matches("Metadata file .* does not exist.");

    InvalidCommandException exceptionViaFlags =
        assertThrows(
            InvalidCommandException.class,
            () ->
                BuildSdkBundleCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--output=" + bundlePath,
                            "--modules=" + baseModulePath,
                            "--sdk-modules-config=" + sdkModulesConfigPath,
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
                BuildSdkBundleCommand.builder()
                    .setOutputPath(bundlePath)
                    .setModulesPaths(ImmutableList.of(baseModulePath))
                    .setSdkModulesConfig(sdkModulesConfigPath)
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
                BuildSdkBundleCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--output=" + bundlePath,
                            "--modules=" + baseModulePath,
                            "--sdk-modules-config=" + sdkModulesConfigPath,
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
                BuildSdkBundleCommand.builder()
                    .setOutputPath(bundlePath)
                    .setModulesPaths(ImmutableList.of(baseModulePath))
                    .setSdkModulesConfig(sdkModulesConfigPath)
                    .addMetadataFile("com.some.namespace", "duplicate", metadataFile1)
                    .addMetadataFile("com.some.namespace", "duplicate", metadataFile2)
                    .build()
                    .execute());
    assertThat(exceptionViaApi).hasMessageThat().contains("Multiple entries with same key");

    IllegalArgumentException exceptionViaFlags =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                BuildSdkBundleCommand.fromFlags(
                        new FlagParser()
                            .parse(
                                "--output=" + bundlePath,
                                "--modules=" + baseModulePath,
                                "--sdk-modules-config=" + sdkModulesConfigPath,
                                "--metadata-file=com.some.namespace/duplicate:" + metadataFile1,
                                "--metadata-file=com.some.namespace/duplicate:" + metadataFile2))
                    .execute());
    assertThat(exceptionViaFlags).hasMessageThat().contains("Multiple entries with same key");
  }

  @Test
  public void invalidSdkBundleConfig_throws() throws Exception {
    Path baseModulePath = buildSimpleModule("base");
    SdkBundleConfig invalidBundleConfig =
        SdkBundleConfig.newBuilder()
            .addSdkDependencies(
                SdkBundle.newBuilder()
                    .setPackageName("com.sdk")
                    .setVersionMajor(VERSION_MAJOR_MAX_VALUE + 1))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                BuildSdkBundleCommand.builder()
                    .setOutputPath(bundlePath)
                    .setModulesPaths(ImmutableList.of(baseModulePath))
                    .setSdkModulesConfig(sdkModulesConfigPath)
                    .setSdkBundleConfig(invalidBundleConfig)
                    .build()
                    .execute());
    assertThat(exception)
        .hasMessageThat()
        .contains("SDK major version for dependency 'com.sdk' must be an integer between 0 and");
  }

  @Test
  public void invalidSdkModulesConfig_throws() throws Exception {
    Path baseModulePath = buildSimpleModule("base");
    SdkModulesConfig invalidModulesConfig =
        SdkModulesConfig.newBuilder()
            .setSdkVersion(
                RuntimeEnabledSdkVersion.newBuilder().setMajor(VERSION_MAJOR_MAX_VALUE + 1))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                BuildSdkBundleCommand.builder()
                    .setOutputPath(bundlePath)
                    .setModulesPaths(ImmutableList.of(baseModulePath))
                    .setSdkModulesConfig(invalidModulesConfig)
                    .build()
                    .execute());
    assertThat(exception)
        .hasMessageThat()
        .contains("SDK major version must be an integer between 0 and");
  }

  @Test
  public void validModule() throws Exception {
    XmlNode manifest = androidManifest(PKG_NAME);
    ResourceTable resourceTable =
        new ResourceTableBuilder()
            .addPackage(PKG_NAME)
            .addDrawableResource("icon", "res/drawable/icon.png")
            .build();
    Path module =
        new ZipBuilder()
            .addFileWithProtoContent(ZipPath.create("manifest/AndroidManifest.xml"), manifest)
            .addFileWithContent(ZipPath.create("assets/anything.dat"), "any".getBytes(UTF_8))
            .addFileWithContent(ZipPath.create("dex/classes.dex"), "dex".getBytes(UTF_8))
            .addFileWithContent(ZipPath.create("res/drawable/icon.png"), "image".getBytes(UTF_8))
            .addFileWithProtoContent(ZipPath.create("resources.pb"), resourceTable)
            .writeTo(tmpDir.resolve("base.zip"));
    Path sdkInterfaceDescriptorsPath = buildSdkInterfaceDescriptors("sdk-api.jar");

    BuildSdkBundleCommand.builder()
        .setOutputPath(bundlePath)
        .setModulesPaths(ImmutableList.of(module))
        .setSdkModulesConfig(sdkModulesConfigPath)
        .setSdkInterfaceDescriptors(sdkInterfaceDescriptorsPath)
        .build()
        .execute();

    try (ZipFile bundle = new ZipFile(bundlePath.toFile())) {
      ZipEntry modulesEntry = bundle.getEntry("modules.resm");
      Path modulesPath = tmpDir.resolve("modules.resm");
      Files.write(modulesPath, ZipUtils.asByteSource(bundle, modulesEntry).read());
      try (ZipFile modules = new ZipFile(modulesPath.toFile())) {
        TruthZip.assertThat(modules)
            .hasFile("SdkModulesConfig.pb")
            .withContent(
                SDK_MODULES_CONFIG.toBuilder()
                    .setBundletool(
                        Bundletool.newBuilder()
                            .setVersion(BundleToolVersion.getCurrentVersion().toString()))
                    .build()
                    .toByteArray());
        TruthZip.assertThat(modules)
            .hasFile("base/manifest/AndroidManifest.xml")
            .withContent(manifest.toByteArray());
        TruthZip.assertThat(modules)
            .hasFile("base/assets/anything.dat")
            .withContent("any".getBytes(UTF_8));
        TruthZip.assertThat(modules)
            .hasFile("base/dex/classes.dex")
            .withContent("dex".getBytes(UTF_8));
        TruthZip.assertThat(modules)
            .hasFile("base/res/drawable/icon.png")
            .withContent("image".getBytes(UTF_8));
        TruthZip.assertThat(modules)
            .hasFile("base/resources.pb")
            .withContent(resourceTable.toByteArray());
      }
      TruthZip.assertThat(bundle)
          .hasFile("SdkBundleConfig.pb")
          .withContent(SdkBundleConfig.getDefaultInstance().toByteArray());
      TruthZip.assertThat(bundle)
          .hasFile("sdk-interface-descriptors.jar")
          .withContent(Files.readAllBytes(sdkInterfaceDescriptorsPath));
    }
  }

  @Test
  public void assetsTargeting_generated() throws Exception {
    XmlNode manifest = androidManifest(PKG_NAME);
    Path module =
        new ZipBuilder()
            .addFileWithProtoContent(ZipPath.create("manifest/AndroidManifest.xml"), manifest)
            .addFileWithContent(ZipPath.create("dex/classes.dex"), "dex".getBytes(UTF_8))
            .addFileWithContent(ZipPath.create("assets/anything.dat"), "any".getBytes(UTF_8))
            .addFileWithContent(
                ZipPath.create("assets/sounds#lang_en/speech.mp3"), "hello".getBytes(UTF_8))
            .addFileWithContent(
                ZipPath.create("assets/sounds#lang_es/speech.mp3"), "hola".getBytes(UTF_8))
            .writeTo(tmpDir.resolve("base.zip"));
    BuildSdkBundleCommand.builder()
        .setOutputPath(bundlePath)
        .setModulesPaths(ImmutableList.of(module))
        .setSdkModulesConfig(sdkModulesConfigPath)
        .build()
        .execute();

    Assets expectedAssetsConfig =
        Assets.newBuilder()
            .addDirectory(
                TargetedAssetsDirectory.newBuilder()
                    .setPath("assets")
                    .setTargeting(AssetsDirectoryTargeting.getDefaultInstance()))
            .addDirectory(
                TargetedAssetsDirectory.newBuilder()
                    .setPath("assets/sounds#lang_en")
                    .setTargeting(assetsDirectoryTargeting(languageTargeting("en"))))
            .addDirectory(
                TargetedAssetsDirectory.newBuilder()
                    .setPath("assets/sounds#lang_es")
                    .setTargeting(assetsDirectoryTargeting(languageTargeting("es"))))
            .build();
    try (ZipFile bundle = new ZipFile(bundlePath.toFile())) {
      ZipEntry modulesEntry = bundle.getEntry("modules.resm");
      Path modulesPath = tmpDir.resolve("modules.resm");
      Files.write(modulesPath, ZipUtils.asByteSource(bundle, modulesEntry).read());
      try (ZipFile modules = new ZipFile(modulesPath.toFile())) {
        TruthZip.assertThat(modules)
            .hasFile("base/assets/anything.dat")
            .withContent("any".getBytes(UTF_8));
        TruthZip.assertThat(modules)
            .hasFile("base/assets/sounds#lang_en/speech.mp3")
            .withContent("hello".getBytes(UTF_8));
        TruthZip.assertThat(modules)
            .hasFile("base/assets/sounds#lang_es/speech.mp3")
            .withContent("hola".getBytes(UTF_8));
        TruthZip.assertThat(modules)
            .hasFile("base/assets.pb")
            .withContent(expectedAssetsConfig.toByteArray());
      }
    }
  }

  @Test
  public void nativeTargeting_generated() throws Exception {
    XmlNode manifest = androidManifest(PKG_NAME);
    Path module =
        new ZipBuilder()
            .addFileWithProtoContent(ZipPath.create("manifest/AndroidManifest.xml"), manifest)
            .addFileWithContent(ZipPath.create("dex/classes.dex"), "dex".getBytes(UTF_8))
            .addFileWithContent(
                ZipPath.create("lib/x86/libexample.so"), "return 0;".getBytes(UTF_8))
            .addFileWithContent(
                ZipPath.create("lib/x86_64/libexample.so"), "return 1;".getBytes(UTF_8))
            .writeTo(tmpDir.resolve("base.zip"));
    BuildSdkBundleCommand.builder()
        .setOutputPath(bundlePath)
        .setModulesPaths(ImmutableList.of(module))
        .setSdkModulesConfig(sdkModulesConfigPath)
        .build()
        .execute();

    NativeLibraries expectedNativeConfig =
        NativeLibraries.newBuilder()
            .addDirectory(
                TargetedNativeDirectory.newBuilder()
                    .setPath("lib/x86")
                    .setTargeting(
                        NativeDirectoryTargeting.newBuilder()
                            .setAbi(Abi.newBuilder().setAlias(X86))))
            .addDirectory(
                TargetedNativeDirectory.newBuilder()
                    .setPath("lib/x86_64")
                    .setTargeting(
                        NativeDirectoryTargeting.newBuilder()
                            .setAbi(Abi.newBuilder().setAlias(X86_64))))
            .build();

    try (ZipFile bundle = new ZipFile(bundlePath.toFile())) {
      ZipEntry modulesEntry = bundle.getEntry("modules.resm");
      Path modulesPath = tmpDir.resolve("modules.resm");
      Files.write(modulesPath, ZipUtils.asByteSource(bundle, modulesEntry).read());
      try (ZipFile modules = new ZipFile(modulesPath.toFile())) {
        TruthZip.assertThat(modules)
            .hasFile("base/lib/x86/libexample.so")
            .withContent("return 0;".getBytes(UTF_8));
        TruthZip.assertThat(modules)
            .hasFile("base/lib/x86_64/libexample.so")
            .withContent("return 1;".getBytes(UTF_8));
        TruthZip.assertThat(modules)
            .hasFile("base/native.pb")
            .withContent(expectedNativeConfig.toByteArray());
      }
    }
  }

  @Test
  public void sdkBundleConfig_isSaved() throws Exception {
    XmlNode manifest = androidManifest(PKG_NAME);
    Path module =
        new ZipBuilder()
            .addFileWithProtoContent(ZipPath.create("manifest/AndroidManifest.xml"), manifest)
            .writeTo(tmpDir.resolve("base.zip"));

    BuildSdkBundleCommand.builder()
        .setOutputPath(bundlePath)
        .setModulesPaths(ImmutableList.of(module))
        .setSdkModulesConfig(sdkModulesConfigPath)
        .setSdkBundleConfig(SDK_BUNDLE_CONFIG)
        .build()
        .execute();

    try (ZipFile bundle = new ZipFile(bundlePath.toFile())) {
      TruthZip.assertThat(bundle)
          .hasFile("SdkBundleConfig.pb")
          .withContent(SDK_BUNDLE_CONFIG.toByteArray());
    }
  }

  @Test
  public void printHelp_doesNotCrash() {
    BuildSdkBundleCommand.help();
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
                BuildSdkBundleCommand.builder()
                    .setModulesPaths(ImmutableList.of(module))
                    .setOutputPath(bundlePath)
                    .setSdkModulesConfig(sdkModulesConfigPath)
                    .build()
                    .execute());
    assertThat(expected).hasMessageThat().matches("File .* already exists.");
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

  private Path buildSdkInterfaceDescriptors(String fileName) throws IOException {
    return new ZipBuilder()
        .addFile(ZipPath.create("MyInterface.class"), ByteSource.wrap(new byte[] {1, 2, 3}))
        .writeTo(tmpDir.resolve(fileName + ".jar"));
  }
}
