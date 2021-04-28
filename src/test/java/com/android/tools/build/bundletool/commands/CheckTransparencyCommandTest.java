/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSharedUserId;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.XmlNode;
import com.android.bundle.CodeTransparencyOuterClass.CodeRelatedFile;
import com.android.bundle.CodeTransparencyOuterClass.CodeTransparency;
import com.android.tools.build.bundletool.flags.Flag.RequiredFlagNotSetException;
import com.android.tools.build.bundletool.flags.FlagParser;
import com.android.tools.build.bundletool.flags.ParsedFlags.UnknownFlagsException;
import com.android.tools.build.bundletool.io.AppBundleSerializer;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.protobuf.util.JsonFormat;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CheckTransparencyCommandTest {

  private static final String BASE_MODULE = "base";
  private static final String FEATURE_MODULE1 = "feature1";
  private static final String FEATURE_MODULE2 = "feature2";
  private static final String DEX_PATH1 = "dex/classes.dex";
  private static final String DEX_PATH2 = "dex/classes2.dex";
  private static final String NATIVE_LIB_PATH1 = "lib/arm64-v8a/libnative.so";
  private static final String NATIVE_LIB_PATH2 = "lib/armeabi-v7a/libnative.so";
  private static final byte[] FILE_CONTENT = new byte[] {1, 2, 3};

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  private Path tmpDir;
  private Path bundlePath;

  @Before
  public void setUp() {
    tmpDir = tmp.getRoot().toPath();
    bundlePath = tmpDir.resolve("bundle.aab");
  }

  @Test
  public void buildingCommandViaFlags_bundlePathNotSet() {
    Throwable e =
        assertThrows(
            RequiredFlagNotSetException.class,
            () -> CheckTransparencyCommand.fromFlags(new FlagParser().parse("")));
    assertThat(e).hasMessageThat().contains("Missing the required --bundle flag");
  }

  @Test
  public void buildingCommandViaFlags_unknownFlagSet() {
    Throwable e =
        assertThrows(
            UnknownFlagsException.class,
            () ->
                CheckTransparencyCommand.fromFlags(
                    new FlagParser().parse("--bundle=" + bundlePath, "--unknownFlag=hello")));
    assertThat(e).hasMessageThat().contains("Unrecognized flags");
  }

  @Test
  public void buildingCommandViaFlagsAndBuilderHasSameResult() {
    CheckTransparencyCommand commandViaFlags =
        CheckTransparencyCommand.fromFlags(new FlagParser().parse("--bundle=" + bundlePath));
    CheckTransparencyCommand commandViaBuilder =
        CheckTransparencyCommand.builder().setBundlePath(bundlePath).build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void execute_bundleNotFound() {
    CheckTransparencyCommand checkTransparencyCommand =
        CheckTransparencyCommand.builder().setBundlePath(bundlePath).build();

    Throwable e = assertThrows(IllegalArgumentException.class, checkTransparencyCommand::execute);
    assertThat(e).hasMessageThat().contains("not found");
  }

  @Test
  public void execute_wrongInputFileFormat() {
    CheckTransparencyCommand checkTransparencyCommand =
        CheckTransparencyCommand.builder().setBundlePath(tmpDir.resolve("bundle.txt")).build();

    Throwable e = assertThrows(IllegalArgumentException.class, checkTransparencyCommand::execute);
    assertThat(e).hasMessageThat().contains("expected to have '.aab' extension.");
  }

  @Test
  public void execute_transparencyFileMissing() throws Exception {
    createBundle(bundlePath, /* transparencyMetadata= */ Optional.empty());
    CheckTransparencyCommand checkTransparencyCommand =
        CheckTransparencyCommand.builder().setBundlePath(bundlePath).build();

    Throwable e = assertThrows(InvalidBundleException.class, checkTransparencyCommand::execute);
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "Bundle does not include code transparency metadata. Run `add-transparency` command to"
                + " add code transparency metadata to the bundle.");
  }

  @Test
  public void execute_transparencyVerificationFailed() throws Exception {
    CodeTransparency.Builder codeTransparency =
        createValidTransparencyProto(
            ByteSource.wrap(FILE_CONTENT).hash(Hashing.sha256()).toString())
            .toBuilder();
    Map<String, CodeRelatedFile> codeRelatedFileMap =
        codeTransparency.getCodeRelatedFileList().stream()
            .collect(toMap(CodeRelatedFile::getPath, codeRelatedFile -> codeRelatedFile));
    codeRelatedFileMap.put(
        "dex/deleted.dex",
        CodeRelatedFile.newBuilder()
            .setType(CodeRelatedFile.Type.DEX)
            .setPath("dex/deleted.dex")
            .build());
    codeRelatedFileMap.remove(BASE_MODULE + "/" + DEX_PATH1);
    codeRelatedFileMap.put(
        BASE_MODULE + "/" + DEX_PATH2,
        CodeRelatedFile.newBuilder()
            .setType(CodeRelatedFile.Type.DEX)
            .setPath(BASE_MODULE + "/" + DEX_PATH2)
            .setSha256("modifiedSHa256")
            .build());
    codeTransparency.clearCodeRelatedFile().addAllCodeRelatedFile(codeRelatedFileMap.values());
    createBundle(bundlePath, Optional.of(codeTransparency.build()));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    CheckTransparencyCommand.builder()
        .setBundlePath(bundlePath)
        .build()
        .checkTransparency(new PrintStream(outputStream));

    String output = new String(outputStream.toByteArray(), UTF_8);
    assertThat(output).contains("Code transparency verification failed.");
    assertThat(output)
        .contains("Files deleted after transparency metadata generation: [dex/deleted.dex]");
    assertThat(output)
        .contains("Files added after transparency metadata generation: [base/dex/classes.dex]");
    assertThat(output)
        .contains("Files modified after transparency metadata generation: [base/dex/classes2.dex]");
  }

  @Test
  public void execute_transparencyVerified() throws Exception {
    CodeTransparency validCodeTransparency =
        createValidTransparencyProto(
            ByteSource.wrap(FILE_CONTENT).hash(Hashing.sha256()).toString());
    createBundle(bundlePath, Optional.of(validCodeTransparency));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    CheckTransparencyCommand.builder()
        .setBundlePath(bundlePath)
        .build()
        .checkTransparency(new PrintStream(outputStream));

    assertThat(new String(outputStream.toByteArray(), UTF_8))
        .contains("Code transparency verified.");
  }

  @Test
  public void execute_transparencyFilePresent_sharedUserIdSpecifiedInManifest() throws Exception {
    CodeTransparency validCodeTransparency =
        createValidTransparencyProto(
            ByteSource.wrap(FILE_CONTENT).hash(Hashing.sha256()).toString());
    createBundle(bundlePath, Optional.of(validCodeTransparency), /* hasSharedUserId= */ true);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    Throwable e =
        assertThrows(
            InvalidBundleException.class,
            () ->
                CheckTransparencyCommand.builder()
                    .setBundlePath(bundlePath)
                    .build()
                    .checkTransparency(new PrintStream(outputStream)));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "Transparency file is present in the bundle, but it can not be verified because"
                + " `sharedUserId` attribute is specified in one of the manifests.");
  }

  @Test
  public void printHelpDoesNotCrash() {
    CheckTransparencyCommand.help();
  }

  private void createBundle(Path path, Optional<CodeTransparency> transparencyMetadata)
      throws Exception {
    createBundle(path, transparencyMetadata, /* hasSharedUserId= */ false);
  }

  private void createBundle(
      Path path, Optional<CodeTransparency> transparencyMetadata, boolean hasSharedUserId)
      throws Exception {
    AppBundleBuilder appBundle =
        new AppBundleBuilder()
            .addModule(BASE_MODULE, module -> addCodeFilesToBundleModule(module, hasSharedUserId))
            .addModule(
                FEATURE_MODULE1, module -> addCodeFilesToBundleModule(module, hasSharedUserId))
            .addModule(
                FEATURE_MODULE2, module -> addCodeFilesToBundleModule(module, hasSharedUserId));
    if (transparencyMetadata.isPresent()) {
      appBundle.addMetadataFile(
          BundleMetadata.BUNDLETOOL_NAMESPACE,
          BundleMetadata.TRANSPARENCY_FILE_NAME,
          CharSource.wrap(JsonFormat.printer().print(transparencyMetadata.get()))
              .asByteSource(Charset.defaultCharset()));
    }
    new AppBundleSerializer().writeToDisk(appBundle.build(), path);
  }

  private static BundleModule addCodeFilesToBundleModule(
      BundleModuleBuilder module, boolean hasSharedUserId) {
    XmlNode manifest =
        hasSharedUserId
            ? androidManifest("com.test.app", withSharedUserId("sharedUserId"))
            : androidManifest("com.test.app");
    return module
        .setManifest(manifest)
        .addFile(DEX_PATH1, FILE_CONTENT)
        .addFile(DEX_PATH2, FILE_CONTENT)
        .addFile(NATIVE_LIB_PATH1, FILE_CONTENT)
        .addFile(NATIVE_LIB_PATH2, FILE_CONTENT)
        .build();
  }

  private static CodeTransparency createValidTransparencyProto(String fileContentHash) {
    CodeTransparency.Builder transparencyBuilder = CodeTransparency.newBuilder();
    addCodeFilesToTransparencyProto(transparencyBuilder, BASE_MODULE, fileContentHash);
    addCodeFilesToTransparencyProto(transparencyBuilder, FEATURE_MODULE1, fileContentHash);
    addCodeFilesToTransparencyProto(transparencyBuilder, FEATURE_MODULE2, fileContentHash);
    return transparencyBuilder.build();
  }

  private static void addCodeFilesToTransparencyProto(
      CodeTransparency.Builder transparencyBuilder, String moduleName, String fileContentHash) {
    transparencyBuilder
        .addCodeRelatedFile(
            CodeRelatedFile.newBuilder()
                .setPath(moduleName + "/" + DEX_PATH1)
                .setType(CodeRelatedFile.Type.DEX)
                .setApkPath("")
                .setSha256(fileContentHash)
                .build())
        .addCodeRelatedFile(
            CodeRelatedFile.newBuilder()
                .setPath(moduleName + "/" + DEX_PATH2)
                .setType(CodeRelatedFile.Type.DEX)
                .setApkPath("")
                .setSha256(fileContentHash)
                .build())
        .addCodeRelatedFile(
            CodeRelatedFile.newBuilder()
                .setPath(moduleName + "/" + NATIVE_LIB_PATH1)
                .setType(CodeRelatedFile.Type.NATIVE_LIBRARY)
                .setApkPath(NATIVE_LIB_PATH1)
                .setSha256(fileContentHash)
                .build())
        .addCodeRelatedFile(
            CodeRelatedFile.newBuilder()
                .setPath(moduleName + "/" + NATIVE_LIB_PATH2)
                .setType(CodeRelatedFile.Type.NATIVE_LIBRARY)
                .setApkPath(NATIVE_LIB_PATH2)
                .setSha256(fileContentHash)
                .build());
  }
}
