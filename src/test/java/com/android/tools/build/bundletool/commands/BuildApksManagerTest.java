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

import static com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias.ATC;
import static com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias.ETC1_RGB8;
import static com.android.tools.build.bundletool.model.OptimizationDimension.ABI;
import static com.android.tools.build.bundletool.model.OptimizationDimension.LANGUAGE;
import static com.android.tools.build.bundletool.testing.Aapt2Helper.AAPT2_PATH;
import static com.android.tools.build.bundletool.testing.ApkSetUtils.extractFromApkSetFile;
import static com.android.tools.build.bundletool.testing.ApkSetUtils.extractTocFromApkSetFile;
import static com.android.tools.build.bundletool.testing.ApkSetUtils.parseTocFromFile;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForFeature;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withFusingAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstant;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMaxSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withOnDemandAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withTitle;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withUsesSplit;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.LDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.MDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.TEST_LABEL_RESOURCE_ID;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.USER_PACKAGE_OFFSET;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.createResourceTable;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.fileReference;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.locale;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.packageWithTestLabel;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.resourceTable;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.resourceTableWithTestLabel;
import static com.android.tools.build.bundletool.testing.TargetingUtils.abiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.alternativeLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assets;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.languageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeLibraries;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedAssetsDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedNativeDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.textureCompressionTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.toAbi;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantSdkTargeting;
import static com.android.tools.build.bundletool.testing.TestUtils.filesUnderPath;
import static com.android.tools.build.bundletool.testing.truth.zip.TruthZip.assertThat;
import static com.android.tools.build.bundletool.utils.ResourcesUtils.makeResourceIdentifier;
import static com.android.tools.build.bundletool.utils.ResultUtils.instantApkVariants;
import static com.android.tools.build.bundletool.utils.ResultUtils.splitApkVariants;
import static com.android.tools.build.bundletool.utils.ResultUtils.standaloneApkVariants;
import static com.android.tools.build.bundletool.utils.Versions.ANDROID_M_API_VERSION;
import static com.android.tools.build.bundletool.utils.Versions.ANDROID_P_API_VERSION;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableMultiset.toImmutableMultiset;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.ConfigurationOuterClass.Configuration;
import com.android.aapt.Resources.XmlNode;
import com.android.apksig.ApkVerifier;
import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.ApkSet;
import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Commands.ModuleMetadata;
import com.android.bundle.Commands.Variant;
import com.android.bundle.Targeting.Abi;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.AbiTargeting;
import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.TestData;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.exceptions.ValidationException;
import com.android.tools.build.bundletool.io.AppBundleSerializer;
import com.android.tools.build.bundletool.io.ZipBuilder;
import com.android.tools.build.bundletool.model.Aapt2Command;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.ApkModifier;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.testing.Aapt2Helper;
import com.android.tools.build.bundletool.testing.ApkSetUtils;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.BundleConfigBuilder;
import com.android.tools.build.bundletool.testing.CertificateFactory;
import com.android.tools.build.bundletool.testing.FakeAdbServer;
import com.android.tools.build.bundletool.testing.FileUtils;
import com.android.tools.build.bundletool.utils.files.FilePreconditions;
import com.android.tools.build.bundletool.utils.flags.FlagParser;
import com.android.tools.build.bundletool.utils.flags.ParsedFlags;
import com.android.tools.build.bundletool.version.BundleToolVersion;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BuildApksManagerTest {

  private static final int ANDROID_L_API_VERSION = 21;
  private static final String KEYSTORE_PASSWORD = "keystore-password";
  private static final String KEY_PASSWORD = "key-password";
  private static final String KEY_ALIAS = "key-alias";

  private static final VariantTargeting UNRESTRICTED_VARIANT_TARGETING =
      variantSdkTargeting(/* minSdkVersion= */ 1);
  private static final SdkVersion LOWEST_SDK_VERSION = sdkVersionFrom(1);

  private final AppBundleSerializer bundleSerializer = new AppBundleSerializer();

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();
  private Path tmpDir;

  private static PrivateKey privateKey;
  private static X509Certificate certificate;

  private final Aapt2Command aapt2Command = Aapt2Helper.getAapt2Command();
  private Path bundlePath;
  private Path outputDir;
  private Path outputFilePath;
  private Path keystorePath;

  private final AdbServer fakeAdbServer =
      new FakeAdbServer(/* hasInitialDeviceList= */ true, /* devices= */ ImmutableList.of());

  @BeforeClass
  public static void setUpClass() throws Exception {
    // Creating a new key takes in average 75ms (with peaks at 200ms), so creating a single one for
    // all the tests.
    KeyPair keyPair = KeyPairGenerator.getInstance("RSA").genKeyPair();
    privateKey = keyPair.getPrivate();
    certificate = CertificateFactory.buildSelfSignedCertificate(keyPair, "CN=BuildApksCommandTest");
  }

  @Before
  public void setUp() throws Exception {
    tmpDir = tmp.getRoot().toPath();

    bundlePath = tmpDir.resolve("bundle");
    outputDir = tmp.newFolder("output").toPath();
    outputFilePath = outputDir.resolve("app.apks");

    // KeyStore.
    keystorePath = tmpDir.resolve("keystore.jks");
    KeyStore keystore = KeyStore.getInstance("JKS");
    keystore.load(/* stream= */ null, KEYSTORE_PASSWORD.toCharArray());
    keystore.setKeyEntry(
        KEY_ALIAS, privateKey, KEY_PASSWORD.toCharArray(), new Certificate[] {certificate});
    keystore.store(new FileOutputStream(keystorePath.toFile()), KEYSTORE_PASSWORD.toCharArray());

    fakeAdbServer.init(Paths.get("path/to/adb"));
  }

  @Test
  public void missingBundleFile_throws() throws Exception {
    ParsedFlags flags =
        new FlagParser().parse("--bundle=" + bundlePath, "--output=" + outputFilePath);
    BuildApksCommand command = BuildApksCommand.fromFlags(flags, fakeAdbServer);

    Exception e = assertThrows(IllegalArgumentException.class, () -> execute(command));
    assertThat(e).hasMessageThat().contains("not found");
  }

  @Test
  public void outputFileAlreadyExists_throws() throws Exception {
    createAndStoreBundle(bundlePath);
    Files.createFile(outputFilePath);

    ParsedFlags flags =
        new FlagParser().parse("--bundle=" + bundlePath, "--output=" + outputFilePath);
    BuildApksCommand command = BuildApksCommand.fromFlags(flags, fakeAdbServer);

    Exception e = assertThrows(IllegalArgumentException.class, () -> execute(command));
    assertThat(e).hasMessageThat().contains("already exists");
  }

  @Test
  public void parallelExecutionSucceeds() throws Exception {
    Path bundlePath = FileUtils.getRandomFilePath(tmp, "bundle-", ".aab");
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(androidManifest("com.app")))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse("--bundle=" + bundlePath, "--output=" + outputFilePath, "--max-threads=3"),
            fakeAdbServer);

    execute(command);
  }

  @Test
  public void selectsRightModules() throws Exception {
    Path bundlePath = FileUtils.getRandomFilePath(tmp, "bundle-", ".aab");

    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                module ->
                    module
                        .addFile("assets/base.txt")
                        .setManifest(androidManifest("com.app"))
                        .setResourceTable(resourceTableWithTestLabel("Test feature")))
            .addModule(
                "fused",
                module ->
                    module
                        .addFile("assets/fused.txt")
                        .setManifest(
                            androidManifestForFeature(
                                "com.app",
                                withFusingAttribute(true),
                                withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID))))
            .addModule(
                "not_fused",
                module ->
                    module
                        .addFile("assets/not_fused.txt")
                        .setManifest(
                            androidManifestForFeature(
                                "com.app",
                                withFusingAttribute(false),
                                withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID))))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    Path apkSetFilePath =
        execute(
            BuildApksCommand.builder()
                .setBundlePath(bundlePath)
                .setOutputFile(outputFilePath)
                .setAapt2Command(aapt2Command)
                .build());

    ZipFile apkSetFile = new ZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    // Split APKs: All modules must be used.
    ImmutableSet<String> modulesInSplitApks =
        splitApkVariants(result)
            .stream()
            .flatMap(variant -> variant.getApkSetList().stream())
            .map(ApkSet::getModuleMetadata)
            .map(ModuleMetadata::getName)
            .collect(toImmutableSet());
    assertThat(modulesInSplitApks).containsExactly("base", "fused", "not_fused");

    // Standalone APKs: Only base and modules marked for fusing must be used.
    assertThat(standaloneApkVariants(result)).hasSize(1);
    ImmutableList<ApkDescription> standaloneApks =
        apkDescriptions(standaloneApkVariants(result).get(0));
    assertThat(standaloneApks).hasSize(1);
    assertThat(standaloneApks.get(0).hasStandaloneApkMetadata()).isTrue();
    assertThat(standaloneApks.get(0).getStandaloneApkMetadata().getFusedModuleNameList())
        .containsExactly("base", "fused");
    File standaloneApkFile =
        extractFromApkSetFile(apkSetFile, standaloneApks.get(0).getPath(), outputDir);
    // Validate that the standalone APK contains appropriate files.
    ZipFile standaloneApkZip = new ZipFile(standaloneApkFile);
    assertThat(filesUnderPath(standaloneApkZip, ZipPath.create("assets")))
        .containsExactly("assets/base.txt", "assets/fused.txt");
  }

  @Test
  public void bundleWithDirectoryZipEntries_throws() throws Exception {
    Path tmpBundlePath = FileUtils.getRandomFilePath(tmp, "bundle-", ".aab");
    AppBundle tmpBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(androidManifest("com.app")))
            .build();
    bundleSerializer.writeToDisk(tmpBundle, tmpBundlePath);
    // Copy the valid bundle, only add a directory zip entry.
    try (ZipFile tmpBundleZip = new ZipFile(tmpBundlePath.toFile())) {
      bundlePath =
          new ZipBuilder()
              .copyAllContentsFromZip(ZipPath.create(""), tmpBundleZip)
              .addDirectory(ZipPath.create("directory-entries-are-forbidden"))
              .writeTo(FileUtils.getRandomFilePath(tmp, "bundle-", ".aab"));
    }

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    ValidationException exception = assertThrows(ValidationException.class, () -> execute(command));

    assertThat(exception)
        .hasMessageThat()
        .contains("zip file contains directory zip entry 'directory-entries-are-forbidden/'");
  }

  @Test
  public void buildApksCommand_appTargetingAllSdks_buildsSplitAndStandaloneApks() throws Exception {
    bundlePath = FileUtils.getRandomFilePath(tmp, "bundle-", ".aab");
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder.addFile("dex/classes.dex").setManifest(androidManifest("com.test.app")))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = new ZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(splitApkVariants(result)).isNotEmpty();
    assertThat(standaloneApkVariants(result)).isNotEmpty();
  }

  @Test
  public void buildApksCommand_appTargetingLPlus_buildsSplitApksOnly() throws Exception {
    bundlePath = FileUtils.getRandomFilePath(tmp, "bundle-", ".aab");
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder.setManifest(
                        androidManifest("com.test.app", withMinSdkVersion(ANDROID_L_API_VERSION))))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = new ZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(result.getVariantList()).hasSize(1);
    assertThat(standaloneApkVariants(result)).isEmpty();
    assertThat(splitApkVariants(result)).hasSize(1);

    Variant splitApksVariant = splitApkVariants(result).get(0);
    assertThat(splitApksVariant.getTargeting())
        .isEqualTo(variantSdkTargeting(sdkVersionFrom(ANDROID_L_API_VERSION)));
  }

  @Test
  public void buildApksCommand_appTargetingPreL_buildsStandaloneApksOnly() throws Exception {
    bundlePath = FileUtils.getRandomFilePath(tmp, "bundle-", ".aab");
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder -> builder.setManifest(androidManifest("com.app", withMaxSdkVersion(20))))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH),
            fakeAdbServer);

    // Should not throw.
    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = new ZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(result.getVariantList()).hasSize(1);
    assertThat(splitApkVariants(result)).isEmpty();
    assertThat(standaloneApkVariants(result)).hasSize(1);
  }

  @Test
  public void buildApksCommand_minSdkVersion_presentInStandaloneSdkTargeting() throws Exception {
    bundlePath = FileUtils.getRandomFilePath(tmp, "bundle-", ".aab");
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder.setManifest(
                        androidManifest("com.app", withMinSdkVersion(15), withMaxSdkVersion(20))))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH),
            fakeAdbServer);

    // Should not throw.
    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = new ZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(result.getVariantList()).hasSize(1);
    assertThat(splitApkVariants(result)).isEmpty();
    assertThat(standaloneApkVariants(result)).hasSize(1);
    assertThat(standaloneApkVariants(result).get(0).getTargeting())
        .isEqualTo(variantSdkTargeting(/* minSdkVersion= */ 15));
  }

  @Test
  public void buildApksCommand_appTargetingPreL_failsGeneratingInstant() throws Exception {
    bundlePath = FileUtils.getRandomFilePath(tmp, "bundle-", ".aab");
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder.setManifest(
                        androidManifest("com.app", withInstant(true), withMaxSdkVersion(20))))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH),
            fakeAdbServer);

    Throwable exception = assertThrows(CommandExecutionException.class, () -> execute(command));
    assertThat(exception)
        .hasMessageThat()
        .contains("maxSdkVersion (20) is less than minimum sdk allowed for instant apps (21).");
  }

  @Test
  public void buildApksCommand_universal_selectsRightModulesForMerging() throws Exception {
    bundlePath = FileUtils.getRandomFilePath(tmp, "bundle-", ".aab");
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("assets/a.txt")
                        .setManifest(androidManifest("com.test.app"))
                        .setResourceTable(resourceTableWithTestLabel("Test feature")))
            .addModule(
                "feature",
                builder ->
                    builder
                        .addFile("assets/b.txt")
                        .setManifest(
                            androidManifestForFeature(
                                "com.test.app",
                                withFusingAttribute(true),
                                withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID))))
            .addModule(
                "not_fused",
                builder ->
                    builder
                        .addFile("assets/c.txt")
                        .setManifest(
                            androidManifestForFeature(
                                "com.test.app",
                                withFusingAttribute(false),
                                withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID))))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setGenerateOnlyUniversalApk(true)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = new ZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(result.getVariantList()).hasSize(1);
    assertThat(splitApkVariants(result)).isEmpty();
    assertThat(standaloneApkVariants(result)).hasSize(1);

    Variant universalVariant = standaloneApkVariants(result).get(0);
    assertThat(apkDescriptions(universalVariant)).hasSize(1);
    ApkDescription universalApk = apkDescriptions(universalVariant).get(0);

    // Correct modules selected for merging.
    assertThat(universalApk.getStandaloneApkMetadata().getFusedModuleNameList())
        .containsExactly("base", "feature");
    File universalApkFile = extractFromApkSetFile(apkSetFile, universalApk.getPath(), outputDir);
    try (ZipFile universalApkZipFile = new ZipFile(universalApkFile)) {
      assertThat(filesUnderPath(universalApkZipFile, ZipPath.create("assets")))
          .containsExactly("assets/a.txt", "assets/b.txt");
    }
  }

  @Test
  public void buildApksCommand_universal_generatesSingleApkWithNoOptimizations() throws Exception {
    bundlePath = FileUtils.getRandomFilePath(tmp, "bundle-", ".aab");
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        // Add some native libraries.
                        .addFile("lib/x86/libsome.so")
                        .addFile("lib/x86_64/libsome.so")
                        .setNativeConfig(
                            nativeLibraries(
                                targetedNativeDirectory(
                                    "lib/x86", nativeDirectoryTargeting(AbiAlias.X86)),
                                targetedNativeDirectory(
                                    "lib/x86_64", nativeDirectoryTargeting(AbiAlias.X86_64))))
                        // Add some density-specific resources.
                        .addFile("res/drawable-ldpi/image.jpg")
                        .addFile("res/drawable-mdpi/image.jpg")
                        .setResourceTable(
                            createResourceTable(
                                "image",
                                fileReference("res/drawable-ldpi/image.jpg", LDPI),
                                fileReference("res/drawable-mdpi/image.jpg", MDPI)))
                        .setManifest(androidManifest("com.test.app")))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setGenerateOnlyUniversalApk(true)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = new ZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    // Should not shard by any dimension and generate single APK with default targeting.
    assertThat(result.getVariantList()).hasSize(1);
    assertThat(splitApkVariants(result)).isEmpty();
    assertThat(standaloneApkVariants(result)).hasSize(1);

    Variant universalVariant = standaloneApkVariants(result).get(0);
    assertThat(universalVariant.getTargeting()).isEqualTo(UNRESTRICTED_VARIANT_TARGETING);

    assertThat(apkDescriptions(universalVariant)).hasSize(1);
    ApkDescription universalApk = apkDescriptions(universalVariant).get(0);
    assertThat(universalApk.getTargeting()).isEqualToDefaultInstance();

    // No ABI or density sharding.
    File universalApkFile = extractFromApkSetFile(apkSetFile, universalApk.getPath(), outputDir);
    try (ZipFile universalApkZipFile = new ZipFile(universalApkFile)) {
      assertThat(filesUnderPath(universalApkZipFile, ZipPath.create("lib")))
          .containsExactly("lib/x86/libsome.so", "lib/x86_64/libsome.so");
      assertThat(filesUnderPath(universalApkZipFile, ZipPath.create("res")))
          .containsExactly("res/drawable-ldpi/image.jpg", "res/drawable-mdpi/image.jpg");
    }
  }

  @Test
  public void buildApksCommand_universalApk_variantUsesMinSdkFromManifest() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("assets/a.txt")
                        .setManifest(androidManifest("com.test.app", withMinSdkVersion(23)))
                        .setResourceTable(resourceTableWithTestLabel("Test feature")))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setGenerateOnlyUniversalApk(true)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = new ZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    // Should not shard by any dimension and generate single APK with default targeting.
    assertThat(result.getVariantList()).hasSize(1);
    assertThat(splitApkVariants(result)).isEmpty();
    assertThat(standaloneApkVariants(result)).hasSize(1);

    Variant universalVariant = standaloneApkVariants(result).get(0);
    assertThat(universalVariant.getTargeting())
        .isEqualTo(variantSdkTargeting(/* minSdkVersion= */ 23));
  }

  @Test
  public void buildApksCommand_splitApks_twoModulesOneOnDemand() throws Exception {
    bundlePath = FileUtils.getRandomFilePath(tmp, "bundle-", ".aab");
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("dex/classes.dex")
                        .setManifest(androidManifest("com.test.app"))
                        .setResourceTable(resourceTableWithTestLabel("Test feature")))
            .addModule(
                "onDemand",
                builder ->
                    builder.setManifest(
                        androidManifest(
                            "com.test.app",
                            withOnDemandAttribute(true),
                            withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID),
                            withFusingAttribute(true))))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = new ZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(splitApkVariants(result)).hasSize(1);
    Variant splitApkVariant = splitApkVariants(result).get(0);
    ImmutableMap<String, ApkSet> splitApkSetByModuleName =
        Maps.uniqueIndex(
            splitApkVariant.getApkSetList(), apkSet -> apkSet.getModuleMetadata().getName());
    assertThat(splitApkSetByModuleName).hasSize(2);

    ApkSet baseSplits = splitApkSetByModuleName.get("base");
    assertThat(baseSplits.getModuleMetadata().getName()).isEqualTo("base");
    assertThat(baseSplits.getModuleMetadata().getOnDemand()).isFalse();
    assertThat(baseSplits.getApkDescriptionList()).hasSize(1);
    assertThat(apkSetFile).hasFile(baseSplits.getApkDescription(0).getPath());

    ApkSet onDemandSplits = splitApkSetByModuleName.get("onDemand");
    assertThat(onDemandSplits.getModuleMetadata().getName()).isEqualTo("onDemand");
    assertThat(onDemandSplits.getModuleMetadata().getOnDemand()).isTrue();
    assertThat(onDemandSplits.getApkDescriptionList()).hasSize(1);
    assertThat(apkSetFile).hasFile(onDemandSplits.getApkDescription(0).getPath());
  }

  @Test
  public void buildApksCommand_splitApks_targetLPlus() throws Exception {
    bundlePath = FileUtils.getRandomFilePath(tmp, "bundle-", ".aab");
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder.addFile("dex/classes.dex").setManifest(androidManifest("com.test.app")))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = new ZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(splitApkVariants(result)).hasSize(1);
    Variant variant = splitApkVariants(result).get(0);
    assertThat(variant.hasTargeting()).isTrue();
    assertThat(variant.getTargeting().getSdkVersionTargeting().getValueList())
        .containsExactly(sdkVersionFrom(ANDROID_L_API_VERSION));
  }

  @Test
  public void buildApksCommand_standalone_oneModuleOneVariant() throws Exception {
    bundlePath = FileUtils.getRandomFilePath(tmp, "bundle-", ".aab");

    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder.addFile("dex/classes.dex").setManifest(androidManifest("com.test.app")))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = new ZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(standaloneApkVariants(result)).hasSize(1);
    Variant standaloneApkVariant = standaloneApkVariants(result).get(0);
    assertThat(standaloneApkVariant.getApkSetList()).hasSize(1);
    ApkSet shards = standaloneApkVariant.getApkSet(0);
    assertThat(shards.getModuleMetadata().getName()).isEqualTo("base");
    assertThat(shards.getApkDescriptionList()).hasSize(1);
    assertThat(ZipPath.create(shards.getApkDescription(0).getPath()).getFileName())
        .isEqualTo(ZipPath.create("standalone.apk"));
    assertThat(apkSetFile).hasFile(shards.getApkDescription(0).getPath());
  }


  @Test
  public void buildApksCommand_standalone_oneModuleManyVariants() throws Exception {
    bundlePath = FileUtils.getRandomFilePath(tmp, "bundle-", ".aab");
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("dex/classes.dex")
                        .addFile("lib/x86/libsome.so")
                        .addFile("lib/x86_64/libsome.so")
                        .addFile("lib/mips/libsome.so")
                        .setNativeConfig(
                            nativeLibraries(
                                targetedNativeDirectory(
                                    "lib/x86", nativeDirectoryTargeting(AbiAlias.X86)),
                                targetedNativeDirectory(
                                    "lib/x86_64", nativeDirectoryTargeting(AbiAlias.X86_64)),
                                targetedNativeDirectory(
                                    "lib/mips", nativeDirectoryTargeting(AbiAlias.MIPS))))
                        .setManifest(androidManifest("com.test.app")))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOptimizationDimensions(ImmutableSet.of(ABI))
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = new ZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableMap<Abi, Variant> standaloneVariantsByAbi =
        Maps.uniqueIndex(
            standaloneApkVariants(result),
            variant -> {
              ApkDescription apkDescription = Iterables.getOnlyElement(apkDescriptions(variant));
              return Iterables.getOnlyElement(
                  apkDescription.getTargeting().getAbiTargeting().getValueList());
            });
    assertThat(standaloneVariantsByAbi.keySet())
        .containsExactly(toAbi(AbiAlias.X86), toAbi(AbiAlias.X86_64), toAbi(AbiAlias.MIPS));
    assertThat(standaloneVariantsByAbi.get(toAbi(AbiAlias.X86)).getTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeVariantTargeting(
                variantAbiTargeting(AbiAlias.X86, ImmutableSet.of(AbiAlias.X86_64, AbiAlias.MIPS)),
                variantSdkTargeting(
                    LOWEST_SDK_VERSION,
                    ImmutableSet.of(
                        sdkVersionFrom(ANDROID_L_API_VERSION),
                        sdkVersionFrom(ANDROID_M_API_VERSION)))));
    assertThat(standaloneVariantsByAbi.get(toAbi(AbiAlias.X86_64)).getTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeVariantTargeting(
                variantAbiTargeting(AbiAlias.X86_64, ImmutableSet.of(AbiAlias.X86, AbiAlias.MIPS)),
                variantSdkTargeting(
                    LOWEST_SDK_VERSION,
                    ImmutableSet.of(
                        sdkVersionFrom(ANDROID_L_API_VERSION),
                        sdkVersionFrom(ANDROID_M_API_VERSION)))));
    assertThat(standaloneVariantsByAbi.get(toAbi(AbiAlias.MIPS)).getTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeVariantTargeting(
                variantAbiTargeting(AbiAlias.MIPS, ImmutableSet.of(AbiAlias.X86, AbiAlias.X86_64)),
                variantSdkTargeting(
                    LOWEST_SDK_VERSION,
                    ImmutableSet.of(
                        sdkVersionFrom(ANDROID_L_API_VERSION),
                        sdkVersionFrom(ANDROID_M_API_VERSION)))));
    for (Variant variant : standaloneVariantsByAbi.values()) {
      assertThat(variant.getApkSetList()).hasSize(1);
      ApkSet apkSet = variant.getApkSet(0);
      assertThat(apkSet.getApkDescriptionList()).hasSize(1);
      assertThat(apkSetFile).hasFile(apkSet.getApkDescription(0).getPath());
    }
  }

  @Test
  public void buildApksCommand_standalone_mixedTargeting() throws Exception {
    bundlePath = FileUtils.getRandomFilePath(tmp, "bundle-", ".aab");
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .setManifest(androidManifest("com.test.app"))
                        .setResourceTable(resourceTableWithTestLabel("Test feature")))
            .addModule(
                "feature_abi_lib",
                builder ->
                    builder
                        .addFile("lib/x86/libfeature.so")
                        .addFile("lib/x86_64/libfeature.so")
                        .setNativeConfig(
                            nativeLibraries(
                                targetedNativeDirectory(
                                    "lib/x86", nativeDirectoryTargeting(AbiAlias.X86)),
                                targetedNativeDirectory(
                                    "lib/x86_64", nativeDirectoryTargeting(AbiAlias.X86_64))))
                        .setManifest(
                            androidManifestForFeature(
                                "com.test.app",
                                withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID))))
            .addModule(
                "feature_lang_assets",
                builder ->
                    builder
                        .addFile("assets/strings#lang_en/trans.txt")
                        .addFile("assets/strings#lang_fr/trans.txt")
                        .setAssetsConfig(
                            assets(
                                targetedAssetsDirectory(
                                    "assets/strings#lang_en",
                                    assetsDirectoryTargeting(languageTargeting("en"))),
                                targetedAssetsDirectory(
                                    "assets/strings#lang_fr",
                                    assetsDirectoryTargeting(languageTargeting("fr")))))
                        .setManifest(
                            androidManifestForFeature(
                                "com.test.app",
                                withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID))))
            .addModule(
                "feature_tcf_assets",
                builder ->
                    builder
                        .addFile("assets/textures#tcf_atc/texture.dat")
                        .addFile("assets/textures#tcf_etc1/texture.dat")
                        .setAssetsConfig(
                            assets(
                                targetedAssetsDirectory(
                                    "assets/textures#tcf_atc/texture.dat",
                                    assetsDirectoryTargeting(textureCompressionTargeting(ATC))),
                                targetedAssetsDirectory(
                                    "assets/textures#tcf_etc1/texture.dat",
                                    assetsDirectoryTargeting(
                                        textureCompressionTargeting(ETC1_RGB8)))))
                        .setManifest(
                            androidManifestForFeature(
                                "com.test.app",
                                withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID))))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOptimizationDimensions(ImmutableSet.of(ABI))
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = new ZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableMap<Abi, ApkDescription> standaloneApksByAbi =
        Maps.uniqueIndex(
            apkDescriptions(standaloneApkVariants(result)),
            apkDesc ->
                Iterables.getOnlyElement(apkDesc.getTargeting().getAbiTargeting().getValueList()));

    assertThat(standaloneApksByAbi.keySet())
        .containsExactly(toAbi(AbiAlias.X86), toAbi(AbiAlias.X86_64));

    File x86ApkFile =
        extractFromApkSetFile(
            apkSetFile, standaloneApksByAbi.get(toAbi(AbiAlias.X86)).getPath(), outputDir);
    try (ZipFile x86Zip = new ZipFile(x86ApkFile)) {
      // ABI-specific files.
      assertThat(x86Zip).hasFile("lib/x86/libfeature.so");
      assertThat(x86Zip).doesNotHaveFile("lib/x86_64/libfeature.so");
      // Included in each standalone APK.
      assertThat(x86Zip).hasFile("assets/strings#lang_en/trans.txt");
      assertThat(x86Zip).hasFile("assets/strings#lang_fr/trans.txt");
      assertThat(x86Zip).hasFile("assets/textures#tcf_atc/texture.dat");
      assertThat(x86Zip).hasFile("assets/textures#tcf_etc1/texture.dat");
    }

    File x64ApkFile =
        extractFromApkSetFile(
            apkSetFile, standaloneApksByAbi.get(toAbi(AbiAlias.X86_64)).getPath(), outputDir);
    try (ZipFile x64Zip = new ZipFile(x64ApkFile)) {
      // ABI-specific files.
      assertThat(x64Zip).hasFile("lib/x86_64/libfeature.so");
      assertThat(x64Zip).doesNotHaveFile("lib/x86/libfeature.so");
      // Included in each standalone APK.
      assertThat(x64Zip).hasFile("assets/strings#lang_en/trans.txt");
      assertThat(x64Zip).hasFile("assets/strings#lang_fr/trans.txt");
      assertThat(x64Zip).hasFile("assets/textures#tcf_atc/texture.dat");
      assertThat(x64Zip).hasFile("assets/textures#tcf_etc1/texture.dat");
    }
  }

  @Test
  public void buildApksCommand_standalone_mergesDexFiles() throws Exception {
    bundlePath = FileUtils.getRandomFilePath(tmp, "bundle-", ".aab");

    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("dex/classes.dex", TestData.readBytes("testdata/dex/classes.dex"))
                        .setManifest(androidManifest("com.test.app"))
                        .setResourceTable(resourceTableWithTestLabel("Test feature")))
            .addModule(
                "module",
                builder ->
                    builder
                        .addFile(
                            "dex/classes.dex", TestData.readBytes("testdata/dex/classes-other.dex"))
                        .setManifest(
                            androidManifestForFeature(
                                "com.test.app",
                                withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID))))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = new ZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(standaloneApkVariants(result)).hasSize(1);
    Variant standaloneApkVariant = standaloneApkVariants(result).get(0);
    assertThat(standaloneApkVariant.getApkSetList()).hasSize(1);
    ApkSet shards = standaloneApkVariant.getApkSet(0);
    assertThat(shards.getModuleMetadata().getName()).isEqualTo("base");
    assertThat(shards.getApkDescriptionList()).hasSize(1);

    ApkDescription shard = shards.getApkDescription(0);
    assertThat(apkSetFile).hasFile(shard.getPath());
    assertThat(ZipPath.create(shard.getPath()).getFileName())
        .isEqualTo(ZipPath.create("standalone.apk"));
    try (ZipFile shardZip =
        new ZipFile(extractFromApkSetFile(apkSetFile, shard.getPath(), outputDir))) {
      assertThat(shardZip).hasFile("classes.dex");
      byte[] mergedDexData =
          ByteStreams.toByteArray(shardZip.getInputStream(new ZipEntry("classes.dex")));
      assertThat(mergedDexData.length).isGreaterThan(0);
      assertThat(mergedDexData).isNotEqualTo(TestData.readBytes("testdata/dex/classes.dex"));
      assertThat(mergedDexData).isNotEqualTo(TestData.readBytes("testdata/dex/classes-other.dex"));
    }
  }

  @Test
  public void buildApksCommand_standalone_mergesDexFilesUsingMainDexList() throws Exception {
    bundlePath = FileUtils.getRandomFilePath(tmp, "bundle-", ".aab");
    Path mainDexListFile =
        FileUtils.createFileWithLines(
            tmp, "com/google/uam/aia/myapplication/feature/MainActivity.class");

    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile(
                            "dex/classes.dex", TestData.readBytes("testdata/dex/classes-large.dex"))
                        .setManifest(androidManifest("com.test.app"))
                        .setResourceTable(resourceTableWithTestLabel("Test feature")))
            .addModule(
                "module",
                builder ->
                    builder
                        .addFile(
                            "dex/classes.dex",
                            TestData.readBytes("testdata/dex/classes-large2.dex"))
                        .setManifest(
                            androidManifestForFeature(
                                "com.test.app",
                                withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID))))
            .addMetadataFile(
                "com.android.tools.build.bundletool", "mainDexList.txt", mainDexListFile)
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = new ZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(standaloneApkVariants(result)).hasSize(1);
    assertThat(apkDescriptions(standaloneApkVariants(result))).hasSize(1);
    ApkDescription shard = apkDescriptions(standaloneApkVariants(result)).get(0);

    assertThat(apkSetFile).hasFile(shard.getPath());
    try (ZipFile shardZip =
        new ZipFile(extractFromApkSetFile(apkSetFile, shard.getPath(), outputDir))) {
      assertThat(shardZip).hasFile("classes.dex");
      assertThat(shardZip).hasFile("classes2.dex");
      byte[] mergedDexData =
          ByteStreams.toByteArray(shardZip.getInputStream(new ZipEntry("classes.dex")));
      assertThat(mergedDexData.length).isGreaterThan(0);
      assertThat(mergedDexData).isNotEqualTo(TestData.readBytes("testdata/dex/classes-large.dex"));
      assertThat(mergedDexData).isNotEqualTo(TestData.readBytes("testdata/dex/classes-large2.dex"));
      byte[] mergedDex2Data =
          ByteStreams.toByteArray(shardZip.getInputStream(new ZipEntry("classes.dex")));
      assertThat(mergedDex2Data.length).isGreaterThan(0);
      assertThat(mergedDex2Data).isNotEqualTo(TestData.readBytes("testdata/dex/classes-large.dex"));
      assertThat(mergedDex2Data)
          .isNotEqualTo(TestData.readBytes("testdata/dex/classes-large2.dex"));
    }
  }

  @Test
  public void buildApksCommand_generateAll_populatesAlternativeVariantTargeting() throws Exception {
    bundlePath = FileUtils.getRandomFilePath(tmp, "bundle-", ".aab");
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("lib/x86/libsome.so")
                        .addFile("lib/x86_64/libsome.so")
                        .setNativeConfig(
                            nativeLibraries(
                                targetedNativeDirectory(
                                    "lib/x86", nativeDirectoryTargeting(AbiAlias.X86)),
                                targetedNativeDirectory(
                                    "lib/x86_64", nativeDirectoryTargeting(AbiAlias.X86_64))))
                        .setManifest(androidManifest("com.test.app")))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOptimizationDimensions(ImmutableSet.of(ABI))
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = new ZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    // 2 standalone APK variants, 1 split APK variant
    assertThat(result.getVariantList()).hasSize(4);

    VariantTargeting lSplitVariantTargeting =
        variantSdkTargeting(
            sdkVersionFrom(ANDROID_L_API_VERSION),
            ImmutableSet.of(sdkVersionFrom(ANDROID_M_API_VERSION), LOWEST_SDK_VERSION));
    VariantTargeting mSplitVariantTargeting =
        variantSdkTargeting(
            sdkVersionFrom(ANDROID_M_API_VERSION),
            ImmutableSet.of(sdkVersionFrom(ANDROID_L_API_VERSION), LOWEST_SDK_VERSION));
    VariantTargeting standaloneX86VariantTargeting =
        mergeVariantTargeting(
            variantAbiTargeting(AbiAlias.X86, ImmutableSet.of(AbiAlias.X86_64)),
            variantSdkTargeting(
                LOWEST_SDK_VERSION,
                ImmutableSet.of(
                    sdkVersionFrom(ANDROID_L_API_VERSION), sdkVersionFrom(ANDROID_M_API_VERSION))));
    VariantTargeting standaloneX64VariantTargeting =
        mergeVariantTargeting(
            variantAbiTargeting(AbiAlias.X86_64, ImmutableSet.of(AbiAlias.X86)),
            variantSdkTargeting(
                LOWEST_SDK_VERSION,
                ImmutableSet.of(
                    sdkVersionFrom(ANDROID_L_API_VERSION), sdkVersionFrom(ANDROID_M_API_VERSION))));

    ImmutableMap<VariantTargeting, Variant> variantsByTargeting =
        Maps.uniqueIndex(result.getVariantList(), Variant::getTargeting);
    assertThat(
            ImmutableSet.builder()
                .addAll(apkNamesInVariant(variantsByTargeting.get(lSplitVariantTargeting)))
                .addAll(apkNamesInVariant(variantsByTargeting.get(mSplitVariantTargeting)))
                .build())
        .containsExactly(
            "base-master.apk",
            "base-x86.apk",
            "base-x86_64.apk",
            "base-master_2.apk",
            "base-x86_2.apk",
            "base-x86_64_2.apk");
    assertThat(variantsByTargeting.keySet())
        .containsExactly(
            lSplitVariantTargeting,
            mSplitVariantTargeting,
            standaloneX86VariantTargeting,
            standaloneX64VariantTargeting);
    assertThat(apkNamesInVariant(variantsByTargeting.get(standaloneX86VariantTargeting)))
        .containsExactly("standalone-x86.apk");
    assertThat(apkNamesInVariant(variantsByTargeting.get(standaloneX64VariantTargeting)))
        .containsExactly("standalone-x86_64.apk");
  }

  @Test
  public void buildApksCommand_inconsistentAbis_discarded() throws Exception {
    Path bundlePath = FileUtils.getRandomFilePath(tmp, "bundle-", ".aab");

    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                module ->
                    module
                        .addFile("lib/x86/lib1.so")
                        .addFile("lib/x86/lib2.so")
                        .addFile("lib/x86/lib3.so")
                        .addFile("lib/x86_64/lib1.so")
                        .addFile("lib/x86_64/lib2.so")
                        .addFile("lib/mips/lib1.so")
                        .setManifest(androidManifest("com.app"))
                        .setNativeConfig(
                            nativeLibraries(
                                targetedNativeDirectory(
                                    "lib/x86", nativeDirectoryTargeting(AbiAlias.X86)),
                                targetedNativeDirectory(
                                    "lib/x86_64", nativeDirectoryTargeting(AbiAlias.X86_64)),
                                targetedNativeDirectory(
                                    "lib/mips", nativeDirectoryTargeting(AbiAlias.MIPS)))))
            // The inconsistent ABIs are discarded up until 0.3.0.
            .setBundleConfig(BundleConfigBuilder.create().setVersion("0.3.0").build())
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .setOptimizationDimensions(ImmutableSet.of(ABI))
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = new ZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    // The x86_64 and mips splits should not get generated.
    ImmutableMap<AbiTargeting, ApkDescription> splitApksByAbiTargeting =
        Maps.uniqueIndex(
            apkDescriptions(splitApkVariants(result)),
            apkDesc -> apkDesc.getTargeting().getAbiTargeting());
    assertThat(splitApksByAbiTargeting.keySet())
        .containsExactly(AbiTargeting.getDefaultInstance(), abiTargeting(AbiAlias.X86));

    // x86_64- and mips-specific files should be discarded in the standalone APK.
    ImmutableList<ApkDescription> standaloneApks = apkDescriptions(standaloneApkVariants(result));
    assertThat(standaloneApks).hasSize(1);
    assertThat(standaloneApks.get(0).getTargeting().getAbiTargeting())
        .isEqualTo(abiTargeting(AbiAlias.X86, ImmutableSet.of()));
    File standaloneApkFile =
        extractFromApkSetFile(apkSetFile, standaloneApks.get(0).getPath(), outputDir);
    try (ZipFile standaloneApkZipFile = new ZipFile(standaloneApkFile)) {
      assertThat(filesUnderPath(standaloneApkZipFile, ZipPath.create("lib")))
          .containsExactly("lib/x86/lib1.so", "lib/x86/lib2.so", "lib/x86/lib3.so");
    }
  }

  /**
   * This test executes the command with a reasonably complex bundle large number of times in the
   * hope to catch concurrency issues.
   */
  @Test
  public void buildApksCommand_concurrencyTest() throws Exception {
    int minThreads = 2;
    int maxThreads = 8;
    final int iterationCount = 50;

    for (int i = 0; i < iterationCount; i++) {
      // Change the number of threads in each test, generating repeating sequence:
      // minThreads, minThreads + 1, ..., maxThreads, minThreads, minThreads + 1, ...
      int threadCount = minThreads + (i % (maxThreads - minThreads + 1));
      try {
        runSingleConcurrencyTest_disableNativeLibrariesOptimization(threadCount);
      } catch (Exception e) {
        throw new RuntimeException(
            String.format(
                "Concurrency test failed in iteration #%d out of %d threads.", i, threadCount),
            e);
      }
    }
  }

  private void runSingleConcurrencyTest_disableNativeLibrariesOptimization(int threadCount)
      throws Exception {
    Path newTempDir = tmp.newFolder().toPath();
    bundlePath = newTempDir.resolve("bundle.aab");
    outputFilePath = newTempDir.resolve("bundle.apks");
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("assets/file.txt")
                        .addFile("dex/classes.dex")
                        .setManifest(androidManifest("com.test.app"))
                        .setResourceTable(
                            resourceTable(
                                packageWithTestLabel("Test feature", USER_PACKAGE_OFFSET - 1))))
            .addModule(
                "abi_feature",
                builder ->
                    builder
                        .addFile("lib/x86/libsome.so")
                        .addFile("lib/x86_64/libsome.so")
                        .setNativeConfig(
                            nativeLibraries(
                                targetedNativeDirectory(
                                    "lib/x86", nativeDirectoryTargeting(AbiAlias.X86)),
                                targetedNativeDirectory(
                                    "lib/x86_64", nativeDirectoryTargeting(AbiAlias.X86_64))))
                        .setManifest(
                            androidManifestForFeature(
                                "com.test.app",
                                withTitle(
                                    "@string/test_label",
                                    makeResourceIdentifier(USER_PACKAGE_OFFSET - 1, 0x01, 0x01)))))
            .addModule(
                "language_feature",
                builder ->
                    builder
                        .addFile("res/drawable/image.jpg")
                        .addFile("res/drawable-cz/image.jpg")
                        .addFile("res/drawable-fr/image.jpg")
                        .addFile("res/drawable-pl/image.jpg")
                        .setResourceTable(
                            createResourceTable(
                                "image",
                                fileReference(
                                    "res/drawable/image.jpg", Configuration.getDefaultInstance()),
                                fileReference("res/drawable-cz/image.jpg", locale("cz")),
                                fileReference("res/drawable-fr/image.jpg", locale("fr")),
                                fileReference("res/drawable-pl/image.jpg", locale("pl"))))
                        .setManifest(
                            androidManifestForFeature(
                                "com.test.app",
                                withTitle(
                                    "@string/test_label",
                                    makeResourceIdentifier(USER_PACKAGE_OFFSET - 1, 0x01, 0x01)))))
            .setBundleConfig(
                BundleConfigBuilder.create().setUncompressNativeLibraries(false).build())
            .build();

    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setExecutorService(
                MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(threadCount)))
            .setOptimizationDimensions(ImmutableSet.of(ABI, LANGUAGE))
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = new BuildApksManager(command).execute(newTempDir);
    ZipFile apkSetFile = new ZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    final String manifest = "AndroidManifest.xml";
    final String signature = "META-INF/MANIFEST.MF";

    // Validate split APKs.
    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);
    assertThat(splitApkVariants).hasSize(1);
    ImmutableMap<String, List<ApkDescription>> splitApksByModule =
        splitApkVariants
            .get(0)
            .getApkSetList()
            .stream()
            .collect(
                toImmutableMap(
                    apkSet -> apkSet.getModuleMetadata().getName(),
                    apkSet -> apkSet.getApkDescriptionList()));
    assertThat(splitApksByModule.keySet())
        .containsExactly("base", "abi_feature", "language_feature");

    // Correct number of APKs.
    // "base" module - master split.
    assertThat(splitApksByModule.get("base")).hasSize(1);
    // "abi_feature" module - master split + 2 ABI splits.
    assertThat(splitApksByModule.get("abi_feature")).hasSize(3);
    // "language_feature" module - master split + 3 language splits.
    assertThat(splitApksByModule.get("language_feature")).hasSize(4);

    // Correct files inside APKs.
    assertThat(filesInApks(splitApksByModule.get("base"), apkSetFile))
        .containsExactly(
            "res/xml/splits0.xml",
            "resources.arsc",
            "assets/file.txt",
            "classes.dex",
            manifest,
            signature);
    assertThat(filesInApks(splitApksByModule.get("abi_feature"), apkSetFile))
        .isEqualTo(
            ImmutableMultiset.builder()
                .add("lib/x86/libsome.so")
                .add("lib/x86_64/libsome.so")
                .addCopies(manifest, 3)
                .addCopies(signature, 3)
                .build());
    assertThat(filesInApks(splitApksByModule.get("language_feature"), apkSetFile))
        .isEqualTo(
            ImmutableMultiset.builder()
                .add("res/drawable/image.jpg")
                .add("res/drawable-cz/image.jpg")
                .add("res/drawable-fr/image.jpg")
                .add("res/drawable-pl/image.jpg")
                .addCopies("resources.arsc", 4)
                .addCopies(manifest, 4)
                .addCopies(signature, 4)
                .build());

    // Validate standalone APKs.
    ImmutableList<Variant> standaloneVariants = standaloneApkVariants(result);
    // Correct number of APKs.
    // 2 ABIs
    assertThat(standaloneVariants).hasSize(2);
    assertThat(apkDescriptions(standaloneVariants)).hasSize(2);

    // Correct files inside APKs.
    assertThat(filesInApks(apkDescriptions(standaloneVariants), apkSetFile))
        .isEqualTo(
            ImmutableMultiset.builder()
                .add("lib/x86/libsome.so")
                .add("lib/x86_64/libsome.so")
                .addCopies("assets/file.txt", 2)
                .addCopies("classes.dex", 2)
                .addCopies("res/drawable/image.jpg", 2)
                .addCopies("res/drawable-cz/image.jpg", 2)
                .addCopies("res/drawable-fr/image.jpg", 2)
                .addCopies("res/drawable-pl/image.jpg", 2)
                .addCopies("resources.arsc", 2)
                .addCopies(manifest, 2)
                .addCopies(signature, 2)
                .build());
  }

  @Test
  public void splitFileNames_abi() throws Exception {
    bundlePath = FileUtils.getRandomFilePath(tmp, "bundle-", ".aab");
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("dex/classes.dex")
                        .addFile("lib/x86/library.so")
                        .setManifest(androidManifest("com.test.app"))
                        .setNativeConfig(
                            nativeLibraries(
                                targetedNativeDirectory(
                                    "lib/x86", nativeDirectoryTargeting(AbiAlias.X86)))))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOptimizationDimensions(ImmutableSet.of(ABI))
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = new ZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableMap<VariantTargeting, Variant> splitVariantsByTargeting =
        Maps.uniqueIndex(splitApkVariants(result), Variant::getTargeting);

    VariantTargeting lSplitVariantTargeting =
        variantSdkTargeting(
            sdkVersionFrom(ANDROID_L_API_VERSION),
            ImmutableSet.of(sdkVersionFrom(ANDROID_M_API_VERSION), LOWEST_SDK_VERSION));
    VariantTargeting mSplitVariantTargeting =
        variantSdkTargeting(
            sdkVersionFrom(ANDROID_M_API_VERSION),
            ImmutableSet.of(sdkVersionFrom(ANDROID_L_API_VERSION), LOWEST_SDK_VERSION));

    assertThat(splitVariantsByTargeting.keySet())
        .containsExactly(lSplitVariantTargeting, mSplitVariantTargeting);
    assertThat(
            ImmutableSet.builder()
                .addAll(apkNamesInVariant(splitVariantsByTargeting.get(lSplitVariantTargeting)))
                .addAll(apkNamesInVariant(splitVariantsByTargeting.get(mSplitVariantTargeting)))
                .build())
        .containsExactly("base-master.apk", "base-x86.apk", "base-master_2.apk", "base-x86_2.apk");
  }

  @Test
  public void splitNames_assetLanguages() throws Exception {
    bundlePath = FileUtils.getRandomFilePath(tmp, "bundle-", ".aab");
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("dex/classes.dex")
                        .addFile("assets/paks#lang_es/es.pak")
                        .addFile("assets/paks/en.pak")
                        .setManifest(androidManifest("com.test.app"))
                        .setAssetsConfig(
                            assets(
                                targetedAssetsDirectory(
                                    "assets/paks#lang_es",
                                    assetsDirectoryTargeting(languageTargeting("es"))),
                                targetedAssetsDirectory(
                                    "assets/paks",
                                    assetsDirectoryTargeting(alternativeLanguageTargeting("es"))))))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = new ZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(splitApkVariants(result)).hasSize(1);
    Variant splitApkVariant = splitApkVariants(result).get(0);

    assertThat(splitApkVariant.getApkSetList()).hasSize(1);
    assertThat(apkNamesInVariant(splitApkVariant))
        .containsExactly("base-master.apk", "base-es.apk", "base-other_lang.apk");
  }

  @Test
  public void apksSigned() throws Exception {
    createAndStoreBundle(bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .setSigningConfiguration(
                SigningConfiguration.builder()
                    .setPrivateKey(privateKey)
                    .setCertificates(ImmutableList.of(certificate))
                    .build())
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = new ZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThatApksAreSigned(result, apkSetFile, certificate);
  }

  @Test
  public void aapt2ExtractedFromExecutableJar() throws Exception {
    bundlePath = FileUtils.getRandomFilePath(tmp, "bundle-", ".aab");
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", builder -> builder.setManifest(androidManifest("com.app")))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser().parse("--bundle=" + bundlePath, "--output=" + outputFilePath),
            fakeAdbServer);

    execute(command);
  }


  @Test
  public void bundleToolVersionSet() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", builder -> builder.setManifest(androidManifest("com.test.app")))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = new ZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(result.getBundletool().getVersion())
        .isEqualTo(BundleToolVersion.getCurrentVersion().toString());
  }

  @Test
  public void overwriteSet() throws Exception {
    createAndStoreBundle(bundlePath);
    Files.createFile(outputFilePath);
    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setOverwriteOutput(true)
            .setAapt2Command(aapt2Command)
            .build();

    assertThat(execute(command).toFile().length()).isGreaterThan(0L);
  }

  @Test
  public void overwriteNotSet_throws() throws Exception {
    createAndStoreBundle(bundlePath);
    Files.createFile(outputFilePath);
    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setOverwriteOutput(false)
            .setAapt2Command(aapt2Command)
            .build();

    Exception e = assertThrows(IllegalArgumentException.class, () -> execute(command));
    assertThat(e).hasMessageThat().contains("already exists");
  }

  @Test
  public void internalExecutorServiceShutsDown() throws Exception {
    createAndStoreBundle(bundlePath);
    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();
    execute(command);
    assertThat(command.getExecutorService().isShutdown()).isTrue();
  }

  @Test
  public void buildingViaFlagsInternalExecutorServiceShutsDown() throws Exception {
    createAndStoreBundle(bundlePath);
    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH,
                    "--max-threads=1"),
            fakeAdbServer);
    execute(command);
    assertThat(command.getExecutorService().isShutdown()).isTrue();
  }

  @Test
  public void buildingViaFlagsInternalDefaultExecutorServiceShutsDown() throws Exception {
    createAndStoreBundle(bundlePath);
    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH),
            fakeAdbServer);
    execute(command);
    assertThat(command.getExecutorService().isShutdown()).isTrue();
  }

  @Test
  public void externalExecutorServiceDoesNotShutDown() throws Exception {
    createAndStoreBundle(bundlePath);
    ListeningExecutorService listeningExecutorService =
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .setExecutorService(listeningExecutorService)
            .build();
    assertThat(command.getExecutorService()).isEqualTo(listeningExecutorService);
    execute(command);
    assertThat(command.getExecutorService().isShutdown()).isFalse();
  }

  @Test
  public void apkModifier_modifyingVersionCode() throws Exception {
    createAndStoreBundle(bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setApkModifier(
                new ApkModifier() {
                  @Override
                  public AndroidManifest modifyManifest(
                      AndroidManifest manifest, ApkDescription apkDescription) {
                    return manifest
                        .toEditor()
                        .setVersionCode(1000 + apkDescription.getVariantNumber())
                        .save();
                  }
                })
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSet = new ZipFile(apkSetFilePath.toFile());

    File standaloneApk = extractFromApkSetFile(apkSet, "standalones/standalone.apk", outputDir);
    File masterSplitApk = extractFromApkSetFile(apkSet, "splits/base-master.apk", outputDir);
    assertThat(extractVersionCode(standaloneApk)).isEqualTo(1000);
    assertThat(extractVersionCode(masterSplitApk)).isEqualTo(1001);
  }

  @Test
  public void buildApksCommand_populatesDependencies() throws Exception {
    bundlePath = FileUtils.getRandomFilePath(tmp, "bundle-", ".aab");
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", builder -> builder.setManifest(androidManifest("com.test.app")))
            .addModule(
                "feature1",
                builder ->
                    builder.setManifest(
                        androidManifest(
                            "com.test.app",
                            withOnDemandAttribute(false),
                            withFusingAttribute(true))))
            .addModule(
                "feature2",
                builder ->
                    builder.setManifest(
                        androidManifest(
                            "com.test.app",
                            withUsesSplit("feature1"),
                            withOnDemandAttribute(false),
                            withFusingAttribute(true))))
            .addModule(
                "feature3",
                builder ->
                    builder.setManifest(
                        androidManifest(
                            "com.test.app",
                            withUsesSplit("feature1", "feature2"),
                            withOnDemandAttribute(false),
                            withFusingAttribute(true))))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = new ZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    // split APKs
    assertThat(splitApkVariants(result)).hasSize(1);
    Variant splitApkVariant = splitApkVariants(result).get(0);
    ImmutableMap<String, ApkSet> splitApkSetByModuleName =
        Maps.uniqueIndex(
            splitApkVariant.getApkSetList(), apkSet -> apkSet.getModuleMetadata().getName());
    assertThat(splitApkSetByModuleName).hasSize(4);

    ApkSet baseSplits = splitApkSetByModuleName.get("base");
    assertThat(baseSplits.getModuleMetadata().getDependenciesList()).isEmpty();

    ApkSet feature1Splits = splitApkSetByModuleName.get("feature1");
    assertThat(feature1Splits.getModuleMetadata().getDependenciesList()).isEmpty();

    ApkSet feature2Splits = splitApkSetByModuleName.get("feature2");
    assertThat(feature2Splits.getModuleMetadata().getDependenciesList())
        .containsExactly("feature1");

    ApkSet feature3Splits = splitApkSetByModuleName.get("feature3");
    assertThat(feature3Splits.getModuleMetadata().getDependenciesList())
        .containsExactly("feature1", "feature2");

    // standalone APK
    assertThat(standaloneApkVariants(result)).hasSize(1);
    List<ApkSet> apkSetList = standaloneApkVariants(result).get(0).getApkSetList();
    assertThat(apkSetList).hasSize(1);
    assertThat(apkSetList.get(0).getModuleMetadata().getDependenciesList()).isEmpty();
  }

  @Test
  public void firstVariantNumber() throws Exception {
    final int firstVariantNumber = 100;

    createAndStoreBundle(bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setFirstVariantNumber(firstVariantNumber)
            .build();

    Path apkSetFilePath = execute(command);
    BuildApksResult buildApksResult =
        ApkSetUtils.extractTocFromApkSetFile(new ZipFile(apkSetFilePath.toFile()), outputDir);

    // This test is only interesting if there is more than one variant (though we don't care about
    // the exact number).
    assertThat(buildApksResult.getVariantCount()).isGreaterThan(1);

    int expectedVariantNumber = firstVariantNumber;
    for (Variant variant : buildApksResult.getVariantList()) {
      assertThat(variant.getVariantNumber()).isEqualTo(expectedVariantNumber);
      expectedVariantNumber++;
    }
  }

  private void assertThatApksAreSigned(
      BuildApksResult result, ZipFile apkSetFile, X509Certificate expectedCertificate)
      throws Exception {
    for (Variant variant : result.getVariantList()) {
      for (ApkSet apkSet : variant.getApkSetList()) {
        for (ApkDescription apkDescription : apkSet.getApkDescriptionList()) {
          File apk = extractFromApkSetFile(apkSetFile, apkDescription.getPath(), outputDir);
          ApkVerifier.Result verifierResult = new ApkVerifier.Builder(apk).build().verify();
          assertThat(verifierResult.isVerified()).isTrue();
          assertThat(verifierResult.getSignerCertificates()).containsExactly(expectedCertificate);
        }
      }
    }
  }

  @Test
  public void buildApksCommand_instantApks_targetLPlus() throws Exception {
    bundlePath = FileUtils.getRandomFilePath(tmp, "bundle-", ".aab");
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("dex/classes.dex")
                        .setManifest(androidManifest("com.test.app", withInstant(true))))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = new ZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(instantApkVariants(result)).hasSize(1);
    Variant variant = instantApkVariants(result).get(0);
    assertThat(variant.hasTargeting()).isTrue();
    assertThat(variant.getTargeting().getSdkVersionTargeting().getValueList())
        .containsExactly(sdkVersionFrom(ANDROID_L_API_VERSION));
  }

  @Test
  public void buildApksCommand_instantApksAndSplitsGenerated() throws Exception {
    bundlePath = FileUtils.getRandomFilePath(tmp, "bundle-", ".aab");
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("dex/classes.dex")
                        .setManifest(androidManifest("com.test.app", withInstant(true))))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = new ZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(instantApkVariants(result)).hasSize(1);
    Variant variant = instantApkVariants(result).get(0);
    assertThat(variant.getApkSetList()).hasSize(1);
    ApkSet apkSet = variant.getApkSetList().get(0);
    assertThat(apkSet.getModuleMetadata().getIsInstant()).isTrue();
    assertThat(apkSetFile).hasFile(apkSet.getApkDescription(0).getPath());

    // split apk also exists and has isInstant set on the module level.
    assertThat(splitApkVariants(result)).hasSize(1);
    variant = splitApkVariants(result).get(0);
    assertThat(variant.getApkSetList()).hasSize(1);
    apkSet = variant.getApkSetList().get(0);
    assertThat(apkSet.getModuleMetadata().getIsInstant()).isTrue();
    assertThat(apkSetFile).hasFile(apkSet.getApkDescription(0).getPath());
  }

  private static ImmutableList<ApkDescription> apkDescriptions(List<Variant> variants) {
    return variants
        .stream()
        .flatMap(variant -> apkDescriptions(variant).stream())
        .collect(toImmutableList());
  }

  private static ImmutableList<ApkDescription> apkDescriptions(Variant variant) {
    return variant
        .getApkSetList()
        .stream()
        .flatMap(apkSet -> apkSet.getApkDescriptionList().stream())
        .collect(toImmutableList());
  }

  private static ImmutableList<String> apkNamesInVariant(Variant variant) {
    return variant
        .getApkSetList()
        .stream()
        .flatMap(apkSet -> apkSet.getApkDescriptionList().stream())
        // Get just the filename.
        .map(ApkDescription::getPath)
        .map(ZipPath::create)
        .map(ZipPath::getFileName)
        .map(ZipPath::toString)
        .collect(toImmutableList());
  }

  /**
   * Extracts names of files inside the APKs described by {@code apkDescs}.
   *
   * @return a bag, preserving the aggregate count each file was encountered across the given APKs
   */
  private ImmutableMultiset<String> filesInApks(
      Collection<ApkDescription> apkDescs, ZipFile apkSetFile) {
    return apkDescs
        .stream()
        .flatMap(apkDesc -> filesInApk(apkDesc, apkSetFile).stream())
        .collect(toImmutableMultiset());
  }

  /** Verifies that all given ApkDescriptions point to the APK files in the output directory. */
  private void verifyApksExist(Collection<ApkDescription> apkDescs, Path outputDirectory) {
    apkDescs
        .stream()
        .map(apkDesc -> outputDirectory.resolve(apkDesc.getPath()))
        .forEach(FilePreconditions::checkFileExistsAndReadable);
  }

  /** Extracts names of files inside the APK described by {@code apkDesc}. */
  private ImmutableSet<String> filesInApk(ApkDescription apkDesc, ZipFile apkSetFile) {
    try {
      return filesInApk(extractFromApkSetFile(apkSetFile, apkDesc.getPath(), outputDir));
    } catch (Exception e) {
      throw new RuntimeException("Failed to verify files in APK.", e);
    }
  }

  private ImmutableSet<String> filesInApk(File apkFile) throws Exception {
    try (ZipFile apkZip = new ZipFile(apkFile)) {
      return Collections.list(apkZip.entries())
          .stream()
          .map(ZipEntry::getName)
          .collect(toImmutableSet());
    }
  }

  private void createAndStoreBundle(Path path) throws IOException {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", builder -> builder.setManifest(androidManifest("com.app")))
            .build();
    bundleSerializer.writeToDisk(appBundle, path);
  }

  private int extractVersionCode(File apk) {
    Path protoApkPath = tmpDir.resolve("proto.apk");
    Aapt2Helper.convertBinaryApkToProtoApk(apk.toPath(), protoApkPath);
    try {
      try (ZipFile protoApk = new ZipFile(protoApkPath.toFile())) {
        AndroidManifest androidManifest =
            AndroidManifest.create(
                XmlNode.parseFrom(
                    protoApk.getInputStream(protoApk.getEntry("AndroidManifest.xml"))));
        return androidManifest.getVersionCode();
      } finally {
        Files.deleteIfExists(protoApkPath);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private Path execute(BuildApksCommand command) {
    return new BuildApksManager(command).execute(tmpDir);
  }
}
