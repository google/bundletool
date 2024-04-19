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

import static com.android.tools.build.bundletool.model.utils.BundleParser.EXTRACTED_SDK_MODULES_FILE_NAME;
import static com.android.tools.build.bundletool.model.utils.BundleParser.SDK_MODULES_FILE_NAME;
import static com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider.ANDROID_HOME;
import static com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider.ANDROID_SERIAL;
import static com.android.tools.build.bundletool.testing.SdkBundleBuilder.sdkVersionBuilder;
import static com.android.tools.build.bundletool.testing.TestUtils.addKeyToKeystore;
import static com.android.tools.build.bundletool.testing.TestUtils.createDebugKeystore;
import static com.android.tools.build.bundletool.testing.TestUtils.createKeystore;
import static com.android.tools.build.bundletool.testing.TestUtils.createSdkAndroidManifest;
import static com.android.tools.build.bundletool.testing.TestUtils.createZipBuilderForModules;
import static com.android.tools.build.bundletool.testing.TestUtils.createZipBuilderForModulesWithInvalidManifest;
import static com.android.tools.build.bundletool.testing.TestUtils.createZipBuilderForSdkAsarWithModules;
import static com.android.tools.build.bundletool.testing.TestUtils.createZipBuilderForSdkBundleWithModules;
import static com.android.tools.build.bundletool.testing.TestUtils.expectMissingRequiredBuilderPropertyException;
import static com.android.tools.build.bundletool.testing.TestUtils.expectMissingRequiredFlagException;
import static com.google.common.base.StandardSystemProperty.USER_HOME;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.stream;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Config.BundleConfig;
import com.android.bundle.SdkMetadataOuterClass.SdkMetadata;
import com.android.tools.build.bundletool.commands.BuildSdkApksCommand.OutputFormat;
import com.android.tools.build.bundletool.flags.FlagParser;
import com.android.tools.build.bundletool.flags.FlagParser.FlagParseException;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.flags.ParsedFlags.UnknownFlagsException;
import com.android.tools.build.bundletool.io.ZipBuilder;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.utils.DefaultSystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.utils.files.FileUtils;
import com.android.tools.build.bundletool.testing.BundleConfigBuilder;
import com.android.tools.build.bundletool.testing.CertificateFactory;
import com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider;
import com.android.tools.build.bundletool.testing.SdkBundleBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BuildSdkApksCommandTest {

  private static final String KEYSTORE_PASSWORD = "keystore-password";
  private static final String KEY_PASSWORD = "key-password";
  private static final String KEY_ALIAS = "key-alias";
  private static final String DEBUG_KEYSTORE_PASSWORD = "android";
  private static final String DEBUG_KEY_PASSWORD = "android";
  private static final String DEBUG_KEY_ALIAS = "AndroidDebugKey";
  private static final String DEVICE_ID = "id1";

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  private static final byte[] TEST_CONTENT = new byte[1];
  private static final BundleConfig BUNDLE_CONFIG = BundleConfigBuilder.create().build();

  private static PrivateKey privateKey;
  private static X509Certificate certificate;

  private Path tmpDir;
  private Path sdkBundlePath;
  private Path sdkAsarPath;
  private Path modulesPath;
  private Path outputFilePath;
  private Path keystorePath;

  @BeforeClass
  public static void setUpClass() throws Exception {
    // Creating a new key takes in average 75ms (with peaks at 200ms), so creating a single one for
    // all the tests.
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(/* keysize= */ 3072);
    KeyPair keyPair = kpg.genKeyPair();
    privateKey = keyPair.getPrivate();
    certificate =
        CertificateFactory.buildSelfSignedCertificate(keyPair, "CN=BuildSdkApksCommandTest");
  }

  @Before
  public void setUp() throws Exception {
    tmpDir = tmp.getRoot().toPath();
    sdkBundlePath = tmpDir.resolve("SdkBundle.asb");
    sdkAsarPath = tmpDir.resolve("SdkArchive.asar");
    modulesPath = tmpDir.resolve(EXTRACTED_SDK_MODULES_FILE_NAME);
    outputFilePath = tmpDir.resolve("output.apks");

    // Keystore.
    keystorePath = tmpDir.resolve("keystore.jks");
    createKeystore(keystorePath, KEYSTORE_PASSWORD);
    addKeyToKeystore(
        keystorePath, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD, privateKey, certificate);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_withSdkBundle() {
    BuildSdkApksCommand commandViaFlags =
        BuildSdkApksCommand.fromFlags(
            getDefaultFlagsWithAdditionalFlags(
                "--sdk-bundle=" + sdkBundlePath,
                "--version-code=351",
                "--output-format=DIRECTORY",
                "--aapt2=path/to/aapt2",
                "--ks=" + keystorePath,
                "--ks-key-alias=" + KEY_ALIAS,
                "--ks-pass=pass:" + KEYSTORE_PASSWORD,
                "--key-pass=pass:" + KEY_PASSWORD,
                "--verbose"));

    BuildSdkApksCommand.Builder commandViaBuilder =
        BuildSdkApksCommand.builder()
            .setSdkBundlePath(sdkBundlePath)
            .setOutputFile(outputFilePath)
            .setVersionCode(351)
            .setOutputFormat(OutputFormat.DIRECTORY)
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setSigningConfiguration(
                SigningConfiguration.builder().setSignerConfig(privateKey, certificate).build())
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setVerbose(true);

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_withSdkArchive() {
    BuildSdkApksCommand commandViaFlags =
        BuildSdkApksCommand.fromFlags(
            getDefaultFlagsWithAdditionalFlags(
                "--sdk-archive=" + sdkAsarPath,
                "--version-code=351",
                "--output-format=DIRECTORY",
                "--aapt2=path/to/aapt2",
                "--ks=" + keystorePath,
                "--ks-key-alias=" + KEY_ALIAS,
                "--ks-pass=pass:" + KEYSTORE_PASSWORD,
                "--key-pass=pass:" + KEY_PASSWORD,
                "--verbose"));

    BuildSdkApksCommand.Builder commandViaBuilder =
        BuildSdkApksCommand.builder()
            .setSdkArchivePath(sdkAsarPath)
            .setOutputFile(outputFilePath)
            .setVersionCode(351)
            .setOutputFormat(OutputFormat.DIRECTORY)
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setSigningConfiguration(
                SigningConfiguration.builder().setSignerConfig(privateKey, certificate).build())
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setVerbose(true);

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingCommandViaFlags_sdkBundlePathSet_sdkArchivePathSet() {
    Throwable e =
        assertThrows(
            IllegalStateException.class,
            () ->
                BuildSdkApksCommand.fromFlags(
                    getDefaultFlagsWithAdditionalFlags(
                        "--sdk-bundle=" + sdkBundlePath, "--sdk-archive=" + sdkAsarPath)));
    assertThat(e)
        .hasMessageThat()
        .contains("One and only one of SdkBundlePath and SdkArchivePath should be set.");
  }

  @Test
  public void buildingCommandViaFlags_sdkBundlePathNotSet_sdkArchivePathNotSet() {
    Throwable e =
        assertThrows(
            IllegalStateException.class,
            () -> BuildSdkApksCommand.fromFlags(getDefaultFlagsWithAdditionalFlags()));
    assertThat(e)
        .hasMessageThat()
        .contains("One and only one of SdkBundlePath and SdkArchivePath should be set.");
  }

  @Test
  public void sdkBundleFileDoesNotExist_throws() {
    Throwable e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                BuildSdkApksCommand.builder()
                    .setSdkBundlePath(Path.of("non_existent.asb"))
                    .setOutputFile(outputFilePath)
                    .build()
                    .execute());
    assertThat(e).hasMessageThat().contains("File 'non_existent.asb' was not found.");
  }

  @Test
  public void sdkBundleFileHasBadExtension_throws() throws Exception {
    createZipBuilderForSdkBundleWithModules(createZipBuilderForModules(), modulesPath)
        .writeTo(sdkAsarPath);
    Throwable e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                BuildSdkApksCommand.builder()
                    .setSdkBundlePath(sdkAsarPath)
                    .setOutputFile(outputFilePath)
                    .build()
                    .execute());
    assertThat(e)
        .hasMessageThat()
        .contains("ASB file 'SdkArchive.asar' is expected to have '.asb' extension.");
  }

  @Test
  public void sdkArchiveFileDoesNotExist_throws() {
    Throwable e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                BuildSdkApksCommand.builder()
                    .setSdkArchivePath(Path.of("non_existent.asar"))
                    .setOutputFile(outputFilePath)
                    .build()
                    .execute());
    assertThat(e).hasMessageThat().contains("File 'non_existent.asar' was not found.");
  }

  @Test
  public void sdkArchiveFileHasBadExtension_throws() throws Exception {
    createZipBuilderForSdkAsarWithModules(createZipBuilderForModules(), modulesPath)
        .writeTo(sdkBundlePath);
    Throwable e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                BuildSdkApksCommand.builder()
                    .setSdkArchivePath(sdkBundlePath)
                    .setOutputFile(outputFilePath)
                    .build()
                    .execute());
    assertThat(e)
        .hasMessageThat()
        .contains("ASAR file 'SdkBundle.asb' is expected to have '.asar' extension.");
  }

  @Test
  public void outputNotSetViaFlags_throws() {
    expectMissingRequiredFlagException(
        "output",
        () ->
            BuildSdkApksCommand.fromFlags(new FlagParser().parse("--sdk-bundle=" + sdkBundlePath)));
  }

  @Test
  public void outputNotSetViaBuilder_throws() {
    expectMissingRequiredBuilderPropertyException(
        "outputFile", () -> BuildSdkApksCommand.builder().setSdkBundlePath(sdkBundlePath).build());
  }

  @Test
  public void keystoreSet_keyAliasNotSet_throws() {
    InvalidCommandException e =
        assertThrows(
            InvalidCommandException.class,
            () ->
                BuildSdkApksCommand.fromFlags(
                    getDefaultFlagsWithAdditionalFlags(
                        "--sdk-bundle=" + sdkBundlePath, "--ks=" + keystorePath)));
    assertThat(e).hasMessageThat().isEqualTo("Flag --ks-key-alias is required when --ks is set.");
  }

  @Test
  public void keyAliasSet_keystoreNotSet_throws() {
    InvalidCommandException e =
        assertThrows(
            InvalidCommandException.class,
            () ->
                BuildSdkApksCommand.fromFlags(
                    getDefaultFlagsWithAdditionalFlags(
                        "--sdk-bundle=" + sdkBundlePath, "--ks-key-alias=" + KEY_ALIAS)));
    assertThat(e).hasMessageThat().isEqualTo("Flag --ks is required when --ks-key-alias is set.");
  }

  @Test
  public void overwriteSetForDirectoryOutputFormat_throws() throws Exception {
    createZipBuilderForSdkBundleWithModules(createZipBuilderForModules(), modulesPath)
        .writeTo(sdkBundlePath);
    ParsedFlags flags =
        getDefaultFlagsWithAdditionalFlags(
            "--sdk-bundle=" + sdkBundlePath, "--overwrite", "--output-format=directory");
    BuildSdkApksCommand command = BuildSdkApksCommand.fromFlags(flags);

    Exception e = assertThrows(InvalidCommandException.class, command::execute);
    assertThat(e).hasMessageThat().contains("flag is not supported");
  }

  @Test
  public void overwriteNotSetOutputFileAlreadyExists_throws() throws Exception {
    createZipBuilderForSdkBundleWithModules(createZipBuilderForModules(), modulesPath)
        .writeTo(sdkBundlePath);
    new ZipBuilder()
        .addFileWithContent(ZipPath.create("BundleConfig.pb"), BUNDLE_CONFIG.toByteArray())
        .writeTo(outputFilePath);
    BuildSdkApksCommand command =
        BuildSdkApksCommand.fromFlags(
            getDefaultFlagsWithAdditionalFlags("--sdk-bundle=" + sdkBundlePath));

    Exception e = assertThrows(IllegalArgumentException.class, command::execute);
    assertThat(e).hasMessageThat().contains("already exists");
  }

  @Test
  public void nonPositiveMaxThreads_throws() throws Exception {
    FlagParseException zeroException =
        assertThrows(
            FlagParseException.class,
            () ->
                BuildSdkApksCommand.fromFlags(
                    getDefaultFlagsWithAdditionalFlags(
                        "--sdk-bundle=" + sdkBundlePath, "--max-threads=0")));
    assertThat(zeroException).hasMessageThat().contains("flag --max-threads has illegal value");

    FlagParseException negativeException =
        assertThrows(
            FlagParseException.class,
            () ->
                BuildSdkApksCommand.fromFlags(
                    getDefaultFlagsWithAdditionalFlags(
                        "--sdk-bundle=" + sdkBundlePath, "--max-threads=-3")));
    assertThat(negativeException).hasMessageThat().contains("flag --max-threads has illegal value");
  }

  @Test
  public void unknownFlag_throws() {
    UnknownFlagsException exception =
        assertThrows(
            UnknownFlagsException.class,
            () ->
                BuildSdkApksCommand.fromFlags(
                    getDefaultFlagsWithAdditionalFlags(
                        "--sdk-bundle=" + sdkBundlePath, "--unknownFlag=notSure")));

    assertThat(exception)
        .hasMessageThat()
        .contains(String.format("Unrecognized flags: --%s", "unknownFlag"));
  }

  // Ensures that validations are run on the bundle zip file.
  @Test
  public void bundleMissingFiles_throws() throws Exception {
    ZipBuilder zipBuilder = new ZipBuilder();
    zipBuilder.writeTo(sdkBundlePath);
    BuildSdkApksCommand command =
        BuildSdkApksCommand.fromFlags(
            getDefaultFlagsWithAdditionalFlags("--sdk-bundle=" + sdkBundlePath));

    Exception e = assertThrows(InvalidBundleException.class, command::execute);
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "The archive doesn't seem to be an SDK Bundle, it is missing required file '"
                + SDK_MODULES_FILE_NAME
                + "'.");
  }

  // Ensures that validations are run on the module zip file.
  @Test
  public void bundleMultipleModules_throws() throws Exception {
    ZipBuilder modules =
        createZipBuilderForModules()
            .addFileWithProtoContent(
                ZipPath.create("feature/manifest/AndroidManifest.xml"), createSdkAndroidManifest())
            .addFileWithContent(ZipPath.create("feature/dex/classes.dex"), TEST_CONTENT);
    createZipBuilderForSdkBundleWithModules(modules, modulesPath).writeTo(sdkBundlePath);

    BuildSdkApksCommand command =
        BuildSdkApksCommand.fromFlags(
            getDefaultFlagsWithAdditionalFlags("--sdk-bundle=" + sdkBundlePath));

    Exception e = assertThrows(InvalidBundleException.class, command::execute);
    assertThat(e).hasMessageThat().contains("SDK bundles need exactly one module");
  }

  @Test
  public void asarMultipleModules_throws() throws Exception {
    ZipBuilder modules =
        createZipBuilderForModules()
            .addFileWithProtoContent(
                ZipPath.create("feature/manifest/AndroidManifest.xml"), createSdkAndroidManifest())
            .addFileWithContent(ZipPath.create("feature/dex/classes.dex"), TEST_CONTENT);
    createZipBuilderForSdkAsarWithModules(modules, modulesPath).writeTo(sdkAsarPath);

    BuildSdkApksCommand command =
        BuildSdkApksCommand.fromFlags(
            getDefaultFlagsWithAdditionalFlags("--sdk-archive=" + sdkAsarPath));

    Exception e = assertThrows(InvalidBundleException.class, command::execute);
    assertThat(e).hasMessageThat().contains("SDK bundles need exactly one module");
  }

  // Ensures that validations are run on the bundle object.
  @Test
  public void invalidManifest_inSdkBundle_throws() throws Exception {
    createZipBuilderForSdkBundleWithModules(
            createZipBuilderForModulesWithInvalidManifest(), modulesPath)
        .writeTo(sdkBundlePath);
    BuildSdkApksCommand command =
        BuildSdkApksCommand.fromFlags(
            getDefaultFlagsWithAdditionalFlags("--sdk-bundle=" + sdkBundlePath));

    Exception e = assertThrows(InvalidBundleException.class, command::execute);
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "'installLocation' in <manifest> must be 'internalOnly' for SDK bundles if it is set.");
  }

  @Test
  public void invalidManifest_inSdkArchive_throws() throws Exception {
    createZipBuilderForSdkAsarWithModules(
            createZipBuilderForModulesWithInvalidManifest(),
            SdkMetadata.newBuilder()
                .setPackageName(SdkBundleBuilder.PACKAGE_NAME)
                .setSdkVersion(sdkVersionBuilder())
                .build(),
            modulesPath)
        .writeTo(sdkAsarPath);
    BuildSdkApksCommand command =
        BuildSdkApksCommand.fromFlags(
            getDefaultFlagsWithAdditionalFlags("--sdk-archive=" + sdkAsarPath));

    Exception e = assertThrows(InvalidBundleException.class, command::execute);
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "'installLocation' in <manifest> must be 'internalOnly' for SDK bundles if it is set.");
  }

  @Test
  public void executeCreatesFile_fromSdkBundle() throws Exception {
    createZipBuilderForSdkBundleWithModules(createZipBuilderForModules(), modulesPath)
        .writeTo(sdkBundlePath);
    BuildSdkApksCommand.fromFlags(
            getDefaultFlagsWithAdditionalFlags("--sdk-bundle=" + sdkBundlePath))
        .execute();
    assertThat(Files.exists(outputFilePath)).isTrue();
  }

  @Test
  public void executeCreatesFile_fromSdkArchive() throws Exception {
    createZipBuilderForSdkAsarWithModules(
            createZipBuilderForModules(),
            SdkMetadata.newBuilder()
                .setPackageName(SdkBundleBuilder.PACKAGE_NAME)
                .setSdkVersion(sdkVersionBuilder())
                .build(),
            modulesPath)
        .writeTo(sdkAsarPath);

    BuildSdkApksCommand.fromFlags(
            getDefaultFlagsWithAdditionalFlags("--sdk-archive=" + sdkAsarPath))
        .execute();

    assertThat(Files.exists(outputFilePath)).isTrue();
  }

  @Test
  public void executeReturnsOutputFile() throws Exception {
    createZipBuilderForSdkBundleWithModules(createZipBuilderForModules(), modulesPath)
        .writeTo(sdkBundlePath);

    assertThat(
            BuildSdkApksCommand.fromFlags(
                    getDefaultFlagsWithAdditionalFlags("--sdk-bundle=" + sdkBundlePath))
                .execute())
        .isEqualTo(outputFilePath);
  }

  @Test
  public void internalExecutorIsShutDownAfterExecute() throws Exception {
    createZipBuilderForSdkBundleWithModules(createZipBuilderForModules(), modulesPath)
        .writeTo(sdkBundlePath);
    BuildSdkApksCommand command =
        BuildSdkApksCommand.fromFlags(
            getDefaultFlagsWithAdditionalFlags(
                "--sdk-bundle=" + sdkBundlePath, "--max-threads=16"));
    command.execute();

    assertThat(command.getExecutorService().isShutdown()).isTrue();
  }

  @Test
  public void externalExecutorIsNotShutDownAfterExecute() throws Exception {
    createZipBuilderForSdkBundleWithModules(createZipBuilderForModules(), modulesPath)
        .writeTo(sdkBundlePath);
    BuildSdkApksCommand command =
        BuildSdkApksCommand.builder()
            .setSdkBundlePath(sdkBundlePath)
            .setOutputFile(outputFilePath)
            .setExecutorService(
                MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(/* nThreads= */ 4)))
            .build();
    command.execute();

    assertThat(command.getExecutorService().isShutdown()).isFalse();
  }

  @Test
  public void printHelpDoesNotCrash() {
    BuildSdkApksCommand.help();
  }

  @Test
  public void noKeystoreProvidedPrintsWarning() throws Exception {
    SystemEnvironmentProvider provider =
        new FakeSystemEnvironmentProvider(
            /* variables= */ ImmutableMap.of(
                ANDROID_HOME, "/android/home", ANDROID_SERIAL, DEVICE_ID),
            /* properties= */ ImmutableMap.of(USER_HOME.key(), "/"));

    try (ByteArrayOutputStream outputByteArrayStream = new ByteArrayOutputStream();
        PrintStream outputPrintStream = new PrintStream(outputByteArrayStream)) {
      BuildSdkApksCommand.fromFlags(
          getDefaultFlagsWithAdditionalFlags("--sdk-bundle=" + sdkBundlePath),
          outputPrintStream,
          provider);

      assertThat(new String(outputByteArrayStream.toByteArray(), UTF_8))
          .contains("WARNING: The APKs won't be signed");
    }
  }

  @Test
  public void noKeystoreProvidedPrintsInfo_debugKeystore() throws Exception {
    SystemEnvironmentProvider provider =
        new FakeSystemEnvironmentProvider(
            /* variables= */ ImmutableMap.of(
                ANDROID_HOME, "/android/home", ANDROID_SERIAL, DEVICE_ID),
            /* properties= */ ImmutableMap.of(USER_HOME.key(), tmpDir.toString()));
    Path debugKeystorePath = tmpDir.resolve(".android").resolve("debug.keystore");
    FileUtils.createParentDirectories(debugKeystorePath);
    createDebugKeystore(
        debugKeystorePath, DEBUG_KEYSTORE_PASSWORD, DEBUG_KEY_ALIAS, DEBUG_KEY_PASSWORD);

    try (ByteArrayOutputStream outputByteArrayStream = new ByteArrayOutputStream();
        PrintStream outputPrintStream = new PrintStream(outputByteArrayStream)) {
      BuildSdkApksCommand.fromFlags(
          getDefaultFlagsWithAdditionalFlags("--sdk-bundle=" + sdkBundlePath),
          outputPrintStream,
          provider);

      assertThat(new String(outputByteArrayStream.toByteArray(), UTF_8))
          .contains("INFO: The APKs will be signed with the debug keystore");
    }
  }

  @Test
  public void keystoreProvidedDoesNotPrint() throws Exception {
    try (ByteArrayOutputStream outputByteArrayStream = new ByteArrayOutputStream();
        PrintStream outputPrintStream = new PrintStream(outputByteArrayStream)) {
      BuildSdkApksCommand.fromFlags(
          getDefaultFlagsWithAdditionalFlags(
              "--sdk-bundle=" + sdkBundlePath,
              "--ks=" + keystorePath,
              "--ks-key-alias=" + KEY_ALIAS,
              "--ks-pass=pass:" + KEYSTORE_PASSWORD,
              "--key-pass=pass:" + KEY_PASSWORD),
          outputPrintStream,
          new DefaultSystemEnvironmentProvider());

      assertThat(new String(outputByteArrayStream.toByteArray(), UTF_8)).isEmpty();
    }
  }

  @Test
  public void verboseIsFalseByDefault() {
    BuildSdkApksCommand command =
        BuildSdkApksCommand.fromFlags(
            getDefaultFlagsWithAdditionalFlags("--sdk-bundle=" + sdkBundlePath));

    assertThat(command.getVerbose()).isFalse();
  }


  private ParsedFlags getDefaultFlagsWithAdditionalFlags(String... additionalFlags) {
    String[] flags =
        Stream.concat(getDefaultFlagList().stream(), stream(additionalFlags))
            .toArray(String[]::new);
    return new FlagParser().parse(flags);
  }

  private ImmutableList<String> getDefaultFlagList() {
    return ImmutableList.of("--output=" + outputFilePath);
  }
}
