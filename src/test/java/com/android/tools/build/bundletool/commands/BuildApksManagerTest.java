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
import static com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias.ATC;
import static com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias.ETC1_RGB8;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode.SYSTEM;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode.SYSTEM_COMPRESSED;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode.UNIVERSAL;
import static com.android.tools.build.bundletool.model.AndroidManifest.DEVELOPMENT_SDK_VERSION;
import static com.android.tools.build.bundletool.model.AndroidManifest.MODULE_TYPE_ASSET_VALUE;
import static com.android.tools.build.bundletool.model.OptimizationDimension.ABI;
import static com.android.tools.build.bundletool.model.OptimizationDimension.LANGUAGE;
import static com.android.tools.build.bundletool.model.utils.ResultUtils.apexApkVariants;
import static com.android.tools.build.bundletool.model.utils.ResultUtils.instantApkVariants;
import static com.android.tools.build.bundletool.model.utils.ResultUtils.splitApkVariants;
import static com.android.tools.build.bundletool.model.utils.ResultUtils.standaloneApkVariants;
import static com.android.tools.build.bundletool.model.utils.ResultUtils.systemApkVariants;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_M_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_P_API_VERSION;
import static com.android.tools.build.bundletool.testing.Aapt2Helper.AAPT2_PATH;
import static com.android.tools.build.bundletool.testing.ApkSetUtils.extractFromApkSetFile;
import static com.android.tools.build.bundletool.testing.ApkSetUtils.extractTocFromApkSetFile;
import static com.android.tools.build.bundletool.testing.ApkSetUtils.parseTocFromFile;
import static com.android.tools.build.bundletool.testing.DeviceFactory.abis;
import static com.android.tools.build.bundletool.testing.DeviceFactory.density;
import static com.android.tools.build.bundletool.testing.DeviceFactory.locales;
import static com.android.tools.build.bundletool.testing.DeviceFactory.mergeSpecs;
import static com.android.tools.build.bundletool.testing.DeviceFactory.sdkVersion;
import static com.android.tools.build.bundletool.testing.FileUtils.uncompressGzipFile;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForFeature;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.clearApplication;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withAppIcon;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withFusingAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstallTimeDelivery;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstant;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMaxSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withOnDemandAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withOnDemandDelivery;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withTargetSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withTitle;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withTypeAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withUsesSplit;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withoutVersionCode;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.LDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.MDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.TEST_LABEL_RESOURCE_ID;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.USER_PACKAGE_OFFSET;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.locale;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.resourceTableWithTestLabel;
import static com.android.tools.build.bundletool.testing.TargetingUtils.abiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.alternativeLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apexImageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apexImages;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkMultiAbiTargetingFromAllTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assets;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.languageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeLibraries;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedApexImage;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedAssetsDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedNativeDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.textureCompressionTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.toAbi;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantMultiAbiTargetingFromAllTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantSdkTargeting;
import static com.android.tools.build.bundletool.testing.TestUtils.filesUnderPath;
import static com.android.tools.build.bundletool.testing.truth.zip.TruthZip.assertThat;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableMultiset.toImmutableMultiset;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.MoreCollectors.onlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.ConfigurationOuterClass.Configuration;
import com.android.aapt.Resources.XmlNode;
import com.android.apksig.ApkVerifier;
import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.ApkSet;
import com.android.bundle.Commands.AssetSlice;
import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Commands.ModuleMetadata;
import com.android.bundle.Commands.SystemApkMetadata;
import com.android.bundle.Commands.SystemApkMetadata.SystemApkType;
import com.android.bundle.Commands.Variant;
import com.android.bundle.Config.SplitDimension.Value;
import com.android.bundle.Files.ApexImages;
import com.android.bundle.Targeting.Abi;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.AbiTargeting;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.LanguageTargeting;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.TestData;
import com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.flags.FlagParser;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.io.AppBundleSerializer;
import com.android.tools.build.bundletool.io.ZipBuilder;
import com.android.tools.build.bundletool.model.Aapt2Command;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.ApkModifier;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.exceptions.ValidationException;
import com.android.tools.build.bundletool.model.utils.files.FilePreconditions;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.testing.Aapt2Helper;
import com.android.tools.build.bundletool.testing.ApkSetUtils;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.BundleConfigBuilder;
import com.android.tools.build.bundletool.testing.CertificateFactory;
import com.android.tools.build.bundletool.testing.FakeAdbServer;
import com.android.tools.build.bundletool.testing.FileUtils;
import com.android.tools.build.bundletool.testing.ResourceTableBuilder;
import com.android.tools.build.bundletool.testing.truth.zip.TruthZip;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
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
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class BuildApksManagerTest {

  private static final int ANDROID_L_API_VERSION = 21;
  private static final String KEYSTORE_PASSWORD = "keystore-password";
  private static final String KEY_PASSWORD = "key-password";
  private static final String KEY_ALIAS = "key-alias";

  private static final VariantTargeting UNRESTRICTED_VARIANT_TARGETING =
      variantSdkTargeting(/* minSdkVersion= */ 1);
  private static final SdkVersion LOWEST_SDK_VERSION = sdkVersionFrom(1);

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  private static PrivateKey privateKey;
  private static X509Certificate certificate;

  private final Aapt2Command aapt2Command = Aapt2Helper.getAapt2Command();
  private final AppBundleSerializer bundleSerializer = new AppBundleSerializer();

  private Path tmpDir;
  private Path outputDir;
  private Path outputFilePath;
  private Closer openedZipFiles;

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

    outputDir = tmp.newFolder("output").toPath();
    outputFilePath = outputDir.resolve("app.apks");

    openedZipFiles = Closer.create();

    // KeyStore.
    Path keystorePath = tmpDir.resolve("keystore.jks");
    KeyStore keystore = KeyStore.getInstance("JKS");
    keystore.load(/* stream= */ null, KEYSTORE_PASSWORD.toCharArray());
    keystore.setKeyEntry(
        KEY_ALIAS, privateKey, KEY_PASSWORD.toCharArray(), new Certificate[] {certificate});
    keystore.store(new FileOutputStream(keystorePath.toFile()), KEYSTORE_PASSWORD.toCharArray());

    fakeAdbServer.init(Paths.get("path/to/adb"));
  }

  @After
  public void tearDown() throws Exception {
    openedZipFiles.close();
  }

  @Test
  public void missingBundleFile_throws() throws Exception {
    Path bundlePath = tmpDir.resolve("bundle.aab");
    ParsedFlags flags =
        new FlagParser().parse("--bundle=" + bundlePath, "--output=" + outputFilePath);
    BuildApksCommand command = BuildApksCommand.fromFlags(flags, fakeAdbServer);

    Exception e = assertThrows(IllegalArgumentException.class, () -> execute(command));
    assertThat(e).hasMessageThat().contains("not found");
  }

  @Test
  public void outputFileAlreadyExists_throws() throws Exception {
    Path bundlePath = createAndStoreBundle();
    Files.createFile(outputFilePath);

    ParsedFlags flags =
        new FlagParser().parse("--bundle=" + bundlePath, "--output=" + outputFilePath);
    BuildApksCommand command = BuildApksCommand.fromFlags(flags, fakeAdbServer);

    Exception e = assertThrows(IllegalArgumentException.class, () -> execute(command));
    assertThat(e).hasMessageThat().contains("already exists");
  }

  @Test
  public void parallelExecutionSucceeds() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(androidManifest("com.app")))
            .build();
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse("--bundle=" + bundlePath, "--output=" + outputFilePath, "--max-threads=3"),
            fakeAdbServer);

    execute(command);
  }

  @Test
  public void selectsRightModules() throws Exception {
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
    Path bundlePath = createAndStoreBundle(appBundle);

    Path apkSetFilePath =
        execute(
            BuildApksCommand.builder()
                .setBundlePath(bundlePath)
                .setOutputFile(outputFilePath)
                .setAapt2Command(aapt2Command)
                .build());

    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    // Split APKs: All modules must be used.
    ImmutableSet<String> modulesInSplitApks =
        splitApkVariants(result).stream()
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
    ZipFile standaloneApkZip = openZipFile(standaloneApkFile);
    assertThat(filesUnderPath(standaloneApkZip, ZipPath.create("assets")))
        .containsExactly("assets/base.txt", "assets/fused.txt");
  }

  @DataPoints("systemApkBuildModes")
  public static final ImmutableSet<ApkBuildMode> SYSTEM_APK_BUILD_MODE =
      ImmutableSet.of(SYSTEM, SYSTEM_COMPRESSED);

  @Test
  @Theory
  public void selectsRightModules_systemApks(
      @FromDataPoints("systemApkBuildModes") ApkBuildMode systemApkBuildMode) throws Exception {
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
    Path bundlePath = createAndStoreBundle(appBundle);

    Path apkSetFilePath =
        execute(
            BuildApksCommand.builder()
                .setBundlePath(bundlePath)
                .setOutputFile(outputFilePath)
                .setAapt2Command(aapt2Command)
                .setApkBuildMode(systemApkBuildMode)
                .setDeviceSpec(
                    mergeSpecs(
                        sdkVersion(28), abis("x86"), density(DensityAlias.MDPI), locales("en-US")))
                .build());

    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    // System APKs: Only base and modules marked for fusing must be used.
    assertThat(systemApkVariants(result)).hasSize(1);
    ImmutableList<ApkDescription> systemApks = apkDescriptions(systemApkVariants(result));

    File systemApkFile;
    if (systemApkBuildMode.equals(SYSTEM)) {
      assertThat(systemApks)
          .containsExactly(
              ApkDescription.newBuilder()
                  .setPath("system/system.apk")
                  .setTargeting(ApkTargeting.getDefaultInstance())
                  .setSystemApkMetadata(
                      SystemApkMetadata.newBuilder()
                          .setSystemApkType(SystemApkType.SYSTEM)
                          .addAllFusedModuleName(ImmutableList.of("base", "fused")))
                  .build());
      systemApkFile = extractFromApkSetFile(apkSetFile, "system/system.apk", outputDir);
    } else {
      assertThat(systemApks)
          .containsExactly(
              ApkDescription.newBuilder()
                  .setPath("system/system.apk")
                  .setTargeting(ApkTargeting.getDefaultInstance())
                  .setSystemApkMetadata(
                      SystemApkMetadata.newBuilder()
                          .setSystemApkType(SystemApkType.SYSTEM_STUB)
                          .addAllFusedModuleName(ImmutableList.of("base", "fused")))
                  .build(),
              ApkDescription.newBuilder()
                  .setPath("system/system.apk.gz")
                  .setTargeting(ApkTargeting.getDefaultInstance())
                  .setSystemApkMetadata(
                      SystemApkMetadata.newBuilder()
                          .setSystemApkType(SystemApkType.SYSTEM_COMPRESSED)
                          .addAllFusedModuleName(ImmutableList.of("base", "fused")))
                  .build());
      // Uncompress the compressed APK
      systemApkFile =
          uncompressGzipFile(
                  extractFromApkSetFile(apkSetFile, "system/system.apk.gz", outputDir).toPath(),
                  outputDir.resolve("output.apk"))
              .toFile();
    }
    // Validate that the system APK contains appropriate files.
    ZipFile systemApkZip = openZipFile(systemApkFile);
    assertThat(filesUnderPath(systemApkZip, ZipPath.create("assets")))
        .containsExactly("assets/base.txt", "assets/fused.txt");
  }

  @Test
  @Theory
  public void multipleModules_systemApks_hasCorrectAdditionalLanguageSplits(
      @FromDataPoints("systemApkBuildModes") ApkBuildMode systemApkBuildMode) throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                module ->
                    module
                        .addFile("assets/base.txt")
                        .addFile("assets/languages#lang_es/image.jpg")
                        .addFile("assets/languages#lang_fr/image.jpg")
                        .setAssetsConfig(
                            assets(
                                targetedAssetsDirectory(
                                    "assets/languages#lang_es",
                                    assetsDirectoryTargeting(languageTargeting("es"))),
                                targetedAssetsDirectory(
                                    "assets/languages#lang_fr",
                                    assetsDirectoryTargeting(languageTargeting("fr")))))
                        .setManifest(androidManifest("com.app"))
                        .setResourceTable(resourceTableWithTestLabel("Test feature")))
            .addModule(
                "fused",
                module ->
                    module
                        .addFile("assets/fused.txt")
                        .addFile("assets/fused/languages#lang_es/image.jpg")
                        .addFile("assets/fused/languages#lang_fr/image.jpg")
                        .setAssetsConfig(
                            assets(
                                targetedAssetsDirectory(
                                    "assets/fused/languages#lang_es",
                                    assetsDirectoryTargeting(languageTargeting("es"))),
                                targetedAssetsDirectory(
                                    "assets/fused/languages#lang_fr",
                                    assetsDirectoryTargeting(languageTargeting("fr")))))
                        .setManifest(
                            androidManifestForFeature(
                                "com.app",
                                withFusingAttribute(true),
                                withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID))))
            .build();
    Path bundlePath = createAndStoreBundle(appBundle);

    Path apkSetFilePath =
        execute(
            BuildApksCommand.builder()
                .setBundlePath(bundlePath)
                .setOutputFile(outputFilePath)
                .setAapt2Command(aapt2Command)
                .setApkBuildMode(systemApkBuildMode)
                .setDeviceSpec(
                    mergeSpecs(
                        sdkVersion(28), abis("x86"), density(DensityAlias.MDPI), locales("es")))
                .build());

    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(systemApkVariants(result)).hasSize(1);
    Variant systemVariant = result.getVariant(0);
    assertThat(systemVariant.getVariantNumber()).isEqualTo(0);
    assertThat(systemVariant.getApkSetList()).hasSize(2);
    ImmutableMap<String, ApkSet> apkSetByModule =
        Maps.uniqueIndex(
            systemVariant.getApkSetList(), apkSet -> apkSet.getModuleMetadata().getName());
    assertThat(apkSetByModule.keySet()).containsExactly("base", "fused");
    ApkSet baseApkSet = apkSetByModule.get("base");
    if (systemApkBuildMode.equals(SYSTEM)) {
      // Single System APK.
      assertThat(baseApkSet.getApkDescriptionList()).hasSize(2);
      assertThat(
              baseApkSet.getApkDescriptionList().stream()
                  .map(ApkDescription::getPath)
                  .collect(toImmutableSet()))
          .containsExactly("system/system.apk", "splits/base-fr.apk");
    } else {
      // Stub and Compressed APK.
      assertThat(baseApkSet.getApkDescriptionList()).hasSize(3);
      assertThat(
              baseApkSet.getApkDescriptionList().stream()
                  .map(ApkDescription::getPath)
                  .collect(toImmutableSet()))
          .containsExactly("system/system.apk", "system/system.apk.gz", "splits/base-fr.apk");
    }
    baseApkSet
        .getApkDescriptionList()
        .forEach(apkDescription -> assertThat(apkSetFile).hasFile(apkDescription.getPath()));

    ApkSet fusedApkSet = apkSetByModule.get("fused");
    assertThat(fusedApkSet.getApkDescriptionList()).hasSize(1);
    assertThat(
            fusedApkSet.getApkDescriptionList().stream()
                .map(ApkDescription::getPath)
                .collect(toImmutableSet()))
        .containsExactly("splits/fused-fr.apk");
    fusedApkSet
        .getApkDescriptionList()
        .forEach(apkDescription -> assertThat(apkSetFile).hasFile(apkDescription.getPath()));
  }

  @Test
  public void bundleWithDirectoryZipEntries_throws() throws Exception {
    AppBundle tmpBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(androidManifest("com.app")))
            .build();
    Path tmpBundlePath = createAndStoreBundle(tmpBundle);

    // Copy the valid bundle, only add a directory zip entry.
    Path bundlePath;
    try (ZipFile tmpBundleZip = openZipFile(tmpBundlePath.toFile())) {
      bundlePath =
          new ZipBuilder()
              .copyAllContentsFromZip(ZipPath.create(""), tmpBundleZip)
              .addDirectory(ZipPath.create("directory-entries-are-forbidden"))
              .writeTo(tmpDir.resolve("bundle-with-dir.aab"));
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
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder.addFile("dex/classes.dex").setManifest(androidManifest("com.test.app")))
            .build();
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(splitApkVariants(result)).isNotEmpty();
    assertThat(standaloneApkVariants(result)).isNotEmpty();
  }

  @Test
  public void buildApksCommand_appTargetingLPlus_buildsSplitApksOnly() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder.setManifest(
                        androidManifest("com.test.app", withMinSdkVersion(ANDROID_L_API_VERSION))))
            .build();
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
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
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder -> builder.setManifest(androidManifest("com.app", withMaxSdkVersion(20))))
            .build();
    Path bundlePath = createAndStoreBundle(appBundle);

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
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(result.getVariantList()).hasSize(1);
    assertThat(splitApkVariants(result)).isEmpty();
    assertThat(standaloneApkVariants(result)).hasSize(1);
  }

  @Test
  public void buildApksCommand_minSdkVersion_presentInStandaloneSdkTargeting() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder.setManifest(
                        androidManifest("com.app", withMinSdkVersion(15), withMaxSdkVersion(20))))
            .build();
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH),
            fakeAdbServer);

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(result.getVariantList()).hasSize(1);
    assertThat(splitApkVariants(result)).isEmpty();
    assertThat(standaloneApkVariants(result)).hasSize(1);
    assertThat(standaloneApkVariants(result).get(0).getTargeting())
        .isEqualTo(variantSdkTargeting(/* minSdkVersion= */ 15, ImmutableSet.of(21)));
  }

  @Test
  public void buildApksCommand_maxSdkVersion_createsExtraAlternative() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder.setManifest(
                        androidManifest("com.app", withMinSdkVersion(21), withMaxSdkVersion(25))))
            .build();
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH),
            fakeAdbServer);

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(result.getVariantList()).hasSize(1);
    assertThat(standaloneApkVariants(result)).isEmpty();
    assertThat(splitApkVariants(result)).hasSize(1);
    assertThat(splitApkVariants(result).get(0).getTargeting())
        .isEqualTo(variantSdkTargeting(/* minSdkVersion= */ 21, ImmutableSet.of(26)));
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
    Path bundlePath = createAndStoreBundle(appBundle);

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
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setApkBuildMode(UNIVERSAL)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
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
                            new ResourceTableBuilder()
                                .addPackage("com.test.app")
                                .addFileResourceForMultipleConfigs(
                                    "drawable",
                                    "image",
                                    ImmutableMap.of(
                                        LDPI,
                                        "res/drawable-ldpi/image.jpg",
                                        MDPI,
                                        "res/drawable-mdpi/image.jpg"))
                                .build())
                        .setManifest(androidManifest("com.test.app")))
            .build();
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setApkBuildMode(UNIVERSAL)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
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
      // "res/xml/splits0.xml" is created by bundletool with list of generated splits.
      assertThat(filesUnderPath(universalApkZipFile, ZipPath.create("res")))
          .containsExactly(
              "res/drawable-ldpi/image.jpg", "res/drawable-mdpi/image.jpg", "res/xml/splits0.xml");
    }
  }

  @Test
  public void buildApksCommand_compressedSystem_generatesSingleApkWithEmptyOptimizations()
      throws Exception {
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
                            new ResourceTableBuilder()
                                .addPackage("com.test.app")
                                .addFileResourceForMultipleConfigs(
                                    "drawable",
                                    "image",
                                    ImmutableMap.of(
                                        LDPI,
                                        "res/drawable-ldpi/image.jpg",
                                        MDPI,
                                        "res/drawable-mdpi/image.jpg"))
                                .build())
                        .setManifest(androidManifest("com.test.app")))
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .addSplitDimension(Value.ABI, /* negate= */ true)
                    .addSplitDimension(Value.SCREEN_DENSITY, /* negate= */ true)
                    .build())
            .build();
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setApkBuildMode(SYSTEM_COMPRESSED)
            .setAapt2Command(aapt2Command)
            .setDeviceSpec(
                mergeSpecs(
                    sdkVersion(28), abis("x86"), density(DensityAlias.MDPI), locales("en-US")))
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    // Should not shard by any dimension and generate single APK with default targeting.
    assertThat(result.getVariantList()).hasSize(1);
    assertThat(splitApkVariants(result)).isEmpty();
    assertThat(standaloneApkVariants(result)).isEmpty();
    assertThat(systemApkVariants(result)).hasSize(1);

    Variant systemVariant = systemApkVariants(result).get(0);
    assertThat(systemVariant.getTargeting()).isEqualTo(UNRESTRICTED_VARIANT_TARGETING);

    assertThat(apkDescriptions(systemVariant)).hasSize(2);

    ImmutableMap<SystemApkType, ApkDescription> apkTypeApkMap =
        Maps.uniqueIndex(
            apkDescriptions(systemVariant),
            apkDescription -> apkDescription.getSystemApkMetadata().getSystemApkType());
    assertThat(apkTypeApkMap.keySet())
        .containsExactly(SystemApkType.SYSTEM_STUB, SystemApkType.SYSTEM_COMPRESSED);

    ApkDescription stubSystemApk = apkTypeApkMap.get(SystemApkType.SYSTEM_STUB);

    assertThat(stubSystemApk.getTargeting()).isEqualToDefaultInstance();
    File systemApkFile = extractFromApkSetFile(apkSetFile, stubSystemApk.getPath(), outputDir);
    try (ZipFile systemApkZipFile = new ZipFile(systemApkFile)) {
      assertThat(systemApkZipFile)
          .containsExactlyEntries("AndroidManifest.xml", "META-INF/MANIFEST.MF");
    }

    ApkDescription compressedSystemApk = apkTypeApkMap.get(SystemApkType.SYSTEM_COMPRESSED);
    Path compressedSystemApkFile =
        uncompressGzipFile(
            extractFromApkSetFile(apkSetFile, compressedSystemApk.getPath(), outputDir).toPath(),
            outputDir.resolve("output.apk"));
    try (ZipFile compressedSystemApkZipFile = new ZipFile(compressedSystemApkFile.toFile())) {
      // "res/xml/splits0.xml" is created by bundletool with list of generated splits.
      assertThat(compressedSystemApkZipFile)
          .containsExactlyEntries(
              "AndroidManifest.xml",
              "META-INF/MANIFEST.MF",
              "lib/x86/libsome.so",
              "lib/x86_64/libsome.so",
              "res/drawable-ldpi/image.jpg",
              "res/drawable-mdpi/image.jpg",
              "res/xml/splits0.xml",
              "resources.arsc");
    }
  }

  @Test
  public void buildApksCommand_system_generatesSingleApkWithEmptyOptimizations() throws Exception {
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
                            new ResourceTableBuilder()
                                .addPackage("com.test.app")
                                .addFileResourceForMultipleConfigs(
                                    "drawable",
                                    "image",
                                    ImmutableMap.of(
                                        LDPI,
                                        "res/drawable-ldpi/image.jpg",
                                        MDPI,
                                        "res/drawable-mdpi/image.jpg"))
                                .build())
                        .setManifest(androidManifest("com.test.app")))
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .addSplitDimension(Value.ABI, /* negate= */ true)
                    .addSplitDimension(Value.SCREEN_DENSITY, /* negate= */ true)
                    .build())
            .build();
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setApkBuildMode(SYSTEM)
            .setAapt2Command(aapt2Command)
            .setDeviceSpec(
                mergeSpecs(
                    sdkVersion(28), abis("x86"), density(DensityAlias.MDPI), locales("en-US")))
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    // Should not shard by any dimension and generate single APK with default targeting.
    assertThat(result.getVariantList()).hasSize(1);
    assertThat(splitApkVariants(result)).isEmpty();
    assertThat(standaloneApkVariants(result)).isEmpty();
    assertThat(systemApkVariants(result)).hasSize(1);

    Variant systemVariant = systemApkVariants(result).get(0);
    assertThat(systemVariant.getTargeting()).isEqualTo(UNRESTRICTED_VARIANT_TARGETING);

    assertThat(apkDescriptions(systemVariant)).hasSize(1);
    ApkDescription systemApk = apkDescriptions(systemVariant).get(0);
    assertThat(systemApk.getTargeting()).isEqualToDefaultInstance();

    File systemApkFile = extractFromApkSetFile(apkSetFile, systemApk.getPath(), outputDir);
    try (ZipFile systemApkZipFile = new ZipFile(systemApkFile)) {
      // "res/xml/splits0.xml" is created by bundletool with list of generated splits.
      TruthZip.assertThat(systemApkZipFile)
          .containsExactlyEntries(
              "AndroidManifest.xml",
              "META-INF/MANIFEST.MF",
              "lib/x86/libsome.so",
              "lib/x86_64/libsome.so",
              "res/drawable-ldpi/image.jpg",
              "res/drawable-mdpi/image.jpg",
              "res/xml/splits0.xml",
              "resources.arsc");
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
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setApkBuildMode(UNIVERSAL)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
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
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
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
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder.addFile("dex/classes.dex").setManifest(androidManifest("com.test.app")))
            .build();
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(splitApkVariants(result)).hasSize(1);
    Variant variant = splitApkVariants(result).get(0);
    assertThat(variant.hasTargeting()).isTrue();
    assertThat(variant.getTargeting().getSdkVersionTargeting().getValueList())
        .containsExactly(sdkVersionFrom(ANDROID_L_API_VERSION));
  }

  @Test
  public void buildApksCommand_splitApks_targetMinSdkVersion() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("dex/classes.dex")
                        .addFile("lib/x86/libsome.so")
                        .setNativeConfig(
                            nativeLibraries(
                                targetedNativeDirectory(
                                    "lib/x86", nativeDirectoryTargeting(AbiAlias.X86))))
                        .setManifest(androidManifest("com.test.app", withMinSdkVersion(25))))
            .build();
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(splitApkVariants(result)).hasSize(1);
    Variant variant = splitApkVariants(result).get(0);
    assertThat(variant.hasTargeting()).isTrue();
    assertThat(variant.getTargeting().getSdkVersionTargeting().getValueList())
        .containsExactly(sdkVersionFrom(25));
  }

  /** This test verifies uncompressed native libraries variant is not generated if not necessary. */
  @Test
  public void buildApksCommand_splitApks_honorsMaxSdkVersion() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("dex/classes.dex")
                        .addFile("lib/x86/libsome.so")
                        .setNativeConfig(
                            nativeLibraries(
                                targetedNativeDirectory(
                                    "lib/x86", nativeDirectoryTargeting(AbiAlias.X86))))
                        .setManifest(androidManifest("com.test.app", withMaxSdkVersion(21))))
            .build();
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(splitApkVariants(result)).hasSize(1);
    Variant variant = splitApkVariants(result).get(0);
    assertThat(variant.hasTargeting()).isTrue();
    assertThat(variant.getTargeting().getSdkVersionTargeting().getValueList())
        .containsExactly(sdkVersionFrom(21));
  }

  @Test
  public void buildApksCommand_standalone_oneModuleOneVariant() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder.addFile("dex/classes.dex").setManifest(androidManifest("com.test.app")))
            .build();
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
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
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOptimizationDimensions(ImmutableSet.of(ABI))
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableMap<Abi, Variant> standaloneVariantsByAbi =
        Maps.uniqueIndex(
            standaloneApkVariants(result),
            variant -> {
              ApkDescription apkDescription = getOnlyElement(apkDescriptions(variant));
              return getOnlyElement(apkDescription.getTargeting().getAbiTargeting().getValueList());
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
  @Theory
  public void buildApksCommand_system_withoutLanguageTargeting(
      @FromDataPoints("systemApkBuildModes") ApkBuildMode systemApkBuildMode) throws Exception {
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
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .addSplitDimension(Value.ABI)
                    .addSplitDimension(Value.SCREEN_DENSITY, /* negate= */ true)
                    .build())
            .build();
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setApkBuildMode(systemApkBuildMode)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .setDeviceSpec(
                mergeSpecs(
                    sdkVersion(28), abis("mips"), density(DensityAlias.MDPI), locales("en-US")))
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(result.getVariantList()).hasSize(1);
    assertThat(splitApkVariants(result)).isEmpty();
    assertThat(standaloneApkVariants(result)).isEmpty();
    assertThat(systemApkVariants(result)).hasSize(1);

    Variant systemVariant = systemApkVariants(result).get(0);
    assertThat(systemVariant.getTargeting())
        .isEqualTo(
            mergeVariantTargeting(
                variantAbiTargeting(AbiAlias.MIPS, ImmutableSet.of(AbiAlias.X86, AbiAlias.X86_64)),
                variantSdkTargeting(LOWEST_SDK_VERSION)));

    if (systemApkBuildMode.equals(SYSTEM)) {
      assertThat(apkDescriptions(systemVariant)).hasSize(1);

    } else {
      assertThat(apkDescriptions(systemVariant)).hasSize(2);
    }

    assertThat(systemVariant.getApkSetList()).hasSize(1);
    ApkSet apkSet = systemVariant.getApkSet(0);
    apkSet
        .getApkDescriptionList()
        .forEach(apkDescription -> assertThat(apkSetFile).hasFile(apkDescription.getPath()));
  }

  @Test
  @Theory
  public void buildApksCommand_system_withLanguageTargeting(
      @FromDataPoints("systemApkBuildModes") ApkBuildMode systemApkBuildMode) throws Exception {
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
                        .addFile("assets/languages#lang_es/image.jpg")
                        .addFile("assets/languages#lang_fr/image.jpg")
                        .addFile("assets/languages#lang_it/image.jpg")
                        .setAssetsConfig(
                            assets(
                                targetedAssetsDirectory(
                                    "assets/languages#lang_it",
                                    assetsDirectoryTargeting(languageTargeting("it"))),
                                targetedAssetsDirectory(
                                    "assets/languages#lang_es",
                                    assetsDirectoryTargeting(languageTargeting("es"))),
                                targetedAssetsDirectory(
                                    "assets/languages#lang_fr",
                                    assetsDirectoryTargeting(languageTargeting("fr")))))
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
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setApkBuildMode(systemApkBuildMode)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .setDeviceSpec(
                mergeSpecs(sdkVersion(28), abis("x86"), density(DensityAlias.MDPI), locales("es")))
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(result.getVariantList()).hasSize(1);
    Variant x86Variant = result.getVariant(0);
    assertThat(x86Variant.getVariantNumber()).isEqualTo(0);
    assertThat(x86Variant.getTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeVariantTargeting(
                variantAbiTargeting(AbiAlias.X86, ImmutableSet.of(AbiAlias.X86_64, AbiAlias.MIPS)),
                variantSdkTargeting(LOWEST_SDK_VERSION)));
    assertThat(x86Variant.getApkSetList()).hasSize(1);
    ApkSet apkSet = x86Variant.getApkSet(0);
    if (systemApkBuildMode.equals(SYSTEM)) {
      // Single System APK.
      assertThat(apkSet.getApkDescriptionList()).hasSize(3);
      assertThat(
              apkSet.getApkDescriptionList().stream()
                  .map(ApkDescription::getPath)
                  .collect(toImmutableSet()))
          .containsExactly("system/system.apk", "splits/base-it.apk", "splits/base-fr.apk");
    } else {
      // Stub and Compressed APK.
      assertThat(apkSet.getApkDescriptionList()).hasSize(4);
      assertThat(
              apkSet.getApkDescriptionList().stream()
                  .map(ApkDescription::getPath)
                  .collect(toImmutableSet()))
          .containsExactly(
              "system/system.apk",
              "system/system.apk.gz",
              "splits/base-it.apk",
              "splits/base-fr.apk");
    }
    apkSet
        .getApkDescriptionList()
        .forEach(apkDescription -> assertThat(apkSetFile).hasFile(apkDescription.getPath()));
  }

  @Test
  public void buildApksCommand_standalone_mixedTargeting() throws Exception {
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
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOptimizationDimensions(ImmutableSet.of(ABI))
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableMap<Abi, ApkDescription> standaloneApksByAbi =
        Maps.uniqueIndex(
            apkDescriptions(standaloneApkVariants(result)),
            apkDesc -> getOnlyElement(apkDesc.getTargeting().getAbiTargeting().getValueList()));

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
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
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
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
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
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOptimizationDimensions(ImmutableSet.of(ABI))
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
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
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .setOptimizationDimensions(ImmutableSet.of(ABI))
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
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

  @Test
  public void buildApksCommand_apexBundle() throws Exception {
    ApexImages apexConfig =
        apexImages(
            targetedApexImage("apex/x86_64.x86.img", apexImageTargeting("x86_64", "x86")),
            targetedApexImage(
                "apex/x86_64.armeabi-v7a.img", apexImageTargeting("x86_64", "armeabi-v7a")),
            targetedApexImage("apex/x86_64.img", apexImageTargeting("x86_64")),
            targetedApexImage("apex/x86.armeabi-v7a.img", apexImageTargeting("x86", "armeabi-v7a")),
            targetedApexImage("apex/x86.img", apexImageTargeting("x86")),
            targetedApexImage("apex/arm64-v8a.img", apexImageTargeting("arm64-v8a")),
            targetedApexImage("apex/armeabi-v7a.img", apexImageTargeting("armeabi-v7a")));
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("root/apex_manifest.json")
                        .addFile("apex/x86_64.x86.img")
                        .addFile("apex/x86_64.armeabi-v7a.img")
                        .addFile("apex/x86_64.img")
                        .addFile("apex/x86.armeabi-v7a.img")
                        .addFile("apex/x86.img")
                        .addFile("apex/arm64-v8a.img")
                        .addFile("apex/armeabi-v7a.img")
                        .setApexConfig(apexConfig)
                        .setManifest(androidManifest("com.test.app")))
            .build();
    Path bundlePath = createAndStoreBundle(appBundle);

    Path apkSetFilePath =
        execute(
            BuildApksCommand.builder()
                .setBundlePath(bundlePath)
                .setOutputFile(outputFilePath)
                .build());

    ImmutableSet<AbiAlias> x64X86Set = ImmutableSet.of(X86_64, X86);
    ImmutableSet<AbiAlias> x64ArmSet = ImmutableSet.of(X86_64, ARMEABI_V7A);
    ImmutableSet<AbiAlias> x64Set = ImmutableSet.of(X86_64);
    ImmutableSet<AbiAlias> x86ArmSet = ImmutableSet.of(X86, ARMEABI_V7A);
    ImmutableSet<AbiAlias> x86Set = ImmutableSet.of(X86);
    ImmutableSet<AbiAlias> arm8Set = ImmutableSet.of(ARM64_V8A);
    ImmutableSet<AbiAlias> arm7Set = ImmutableSet.of(ARMEABI_V7A);

    ImmutableSet<ImmutableSet<AbiAlias>> allTargeting =
        ImmutableSet.of(x64X86Set, x64ArmSet, x64Set, x86ArmSet, x86Set, arm8Set, arm7Set);
    ApkTargeting x64X86Targeting = apkMultiAbiTargetingFromAllTargeting(x64X86Set, allTargeting);
    ApkTargeting x64ArmTargeting = apkMultiAbiTargetingFromAllTargeting(x64ArmSet, allTargeting);
    ApkTargeting x64Targeting = apkMultiAbiTargetingFromAllTargeting(x64Set, allTargeting);
    ApkTargeting x86ArmTargeting = apkMultiAbiTargetingFromAllTargeting(x86ArmSet, allTargeting);
    ApkTargeting x86Targeting = apkMultiAbiTargetingFromAllTargeting(x86Set, allTargeting);
    ApkTargeting arm8Targeting = apkMultiAbiTargetingFromAllTargeting(arm8Set, allTargeting);
    ApkTargeting arm7Targeting = apkMultiAbiTargetingFromAllTargeting(arm7Set, allTargeting);

    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);
    ImmutableMap<ApkTargeting, Variant> apexVariantsByAbi = extractApexVariantsByTargeting(result);
    assertThat(apexVariantsByAbi.keySet())
        .containsExactly(
            x64X86Targeting,
            x64ArmTargeting,
            x64Targeting,
            x86ArmTargeting,
            x86Targeting,
            arm8Targeting,
            arm7Targeting);

    checkVariantMultiAbiTargeting(
        apexVariantsByAbi.get(x64X86Targeting),
        variantMultiAbiTargetingFromAllTargeting(x64X86Set, allTargeting));
    checkVariantMultiAbiTargeting(
        apexVariantsByAbi.get(x64ArmTargeting),
        variantMultiAbiTargetingFromAllTargeting(x64ArmSet, allTargeting));
    checkVariantMultiAbiTargeting(
        apexVariantsByAbi.get(x64Targeting),
        variantMultiAbiTargetingFromAllTargeting(x64Set, allTargeting));
    checkVariantMultiAbiTargeting(
        apexVariantsByAbi.get(x86ArmTargeting),
        variantMultiAbiTargetingFromAllTargeting(x86ArmSet, allTargeting));
    checkVariantMultiAbiTargeting(
        apexVariantsByAbi.get(x86Targeting),
        variantMultiAbiTargetingFromAllTargeting(x86Set, allTargeting));
    checkVariantMultiAbiTargeting(
        apexVariantsByAbi.get(arm8Targeting),
        variantMultiAbiTargetingFromAllTargeting(arm8Set, allTargeting));
    checkVariantMultiAbiTargeting(
        apexVariantsByAbi.get(arm7Targeting),
        variantMultiAbiTargetingFromAllTargeting(arm7Set, allTargeting));
    for (Variant variant : apexVariantsByAbi.values()) {
      ApkSet apkSet = getOnlyElement(variant.getApkSetList());
      ApkDescription apkDescription = getOnlyElement(apkSet.getApkDescriptionList());
      assertThat(apkSetFile).hasFile(apkDescription.getPath());
    }
  }

  @Test
  public void buildApksCommand_apexBundle_previewTargetSdk() throws Exception {
    ApexImages apexConfig =
        apexImages(
            targetedApexImage("apex/x86_64.img", apexImageTargeting("x86_64")),
            targetedApexImage("apex/x86.img", apexImageTargeting("x86")),
            targetedApexImage("apex/arm64-v8a.img", apexImageTargeting("arm64-v8a")),
            targetedApexImage("apex/armeabi-v7a.img", apexImageTargeting("armeabi-v7a")));
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("root/apex_manifest.json")
                        .addFile("apex/x86_64.img")
                        .addFile("apex/x86.img")
                        .addFile("apex/arm64-v8a.img")
                        .addFile("apex/armeabi-v7a.img")
                        .setApexConfig(apexConfig)
                        .setManifest(
                            androidManifest(
                                "com.test.app",
                                withTargetSdkVersion("Q.fingerprint"),
                                withMinSdkVersion("Q.fingerprint"))))
            .build();
    Path bundlePath = createAndStoreBundle(appBundle);

    Path apkSetFilePath =
        execute(
            BuildApksCommand.builder()
                .setBundlePath(bundlePath)
                .setOutputFile(outputFilePath)
                .build());

    ImmutableSet<AbiAlias> x64Set = ImmutableSet.of(X86_64);
    ImmutableSet<AbiAlias> x86Set = ImmutableSet.of(X86);
    ImmutableSet<AbiAlias> arm8Set = ImmutableSet.of(ARM64_V8A);
    ImmutableSet<AbiAlias> arm7Set = ImmutableSet.of(ARMEABI_V7A);

    ImmutableSet<ImmutableSet<AbiAlias>> allTargeting =
        ImmutableSet.of(x64Set, x86Set, arm8Set, arm7Set);
    ApkTargeting x64Targeting = apkMultiAbiTargetingFromAllTargeting(x64Set, allTargeting);
    ApkTargeting x86Targeting = apkMultiAbiTargetingFromAllTargeting(x86Set, allTargeting);
    ApkTargeting arm8Targeting = apkMultiAbiTargetingFromAllTargeting(arm8Set, allTargeting);
    ApkTargeting arm7Targeting = apkMultiAbiTargetingFromAllTargeting(arm7Set, allTargeting);

    ZipFile apkSetFile = new ZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);
    ImmutableMap<ApkTargeting, Variant> apexVariantsByAbi = extractApexVariantsByTargeting(result);

    assertThat(apexVariantsByAbi.keySet())
        .containsExactly(x64Targeting, x86Targeting, arm8Targeting, arm7Targeting);
    assertThat(
            apexVariantsByAbi.values().stream()
                .map(variant -> variant.getTargeting().getSdkVersionTargeting())
                .collect(toImmutableSet()))
        .containsExactly(
            sdkVersionTargeting(sdkVersionFrom(DEVELOPMENT_SDK_VERSION), ImmutableSet.of()));
  }

  @Test
  public void buildApksCommand_apexBundle_hasRightSuffix() throws Exception {
    ApexImages apexConfig =
        apexImages(
            targetedApexImage("apex/x86_64.img", apexImageTargeting("x86_64")),
            targetedApexImage("apex/x86.img", apexImageTargeting("x86")),
            targetedApexImage("apex/arm64-v8a.img", apexImageTargeting("arm64-v8a")),
            targetedApexImage("apex/armeabi-v7a.img", apexImageTargeting("armeabi-v7a")),
            targetedApexImage(
                "apex/arm64-v8a.armeabi-v7a.img", apexImageTargeting("arm64-v8a", "armeabi-v7a")));
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("root/apex_manifest.json")
                        .addFile("apex/x86_64.img")
                        .addFile("apex/x86.img")
                        .addFile("apex/arm64-v8a.img")
                        .addFile("apex/armeabi-v7a.img")
                        .addFile("apex/arm64-v8a.armeabi-v7a.img")
                        .setApexConfig(apexConfig)
                        .setManifest(androidManifest("com.test.app")))
            .build();
    Path bundlePath = createAndStoreBundle(appBundle);

    Path apkSetFilePath =
        execute(
            BuildApksCommand.builder()
                .setBundlePath(bundlePath)
                .setOutputFile(outputFilePath)
                .build());

    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);
    ImmutableList<Variant> variants = apexApkVariants(result);

    ImmutableSet<String> apkPaths =
        variants.stream()
            .map(
                variant ->
                    getOnlyElement(getOnlyElement(variant.getApkSetList()).getApkDescriptionList())
                        .getPath())
            .collect(toImmutableSet());
    assertThat(apkPaths)
        .containsExactly(
            "standalones/standalone-x86_64.apex",
            "standalones/standalone-x86.apex",
            "standalones/standalone-arm64_v8a.apex",
            "standalones/standalone-armeabi_v7a.apex",
            "standalones/standalone-arm64_v8a.armeabi_v7a.apex");
  }

  @Test
  public void buildApksCommand_featureAndAssetModules_generatesAssetSlices() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("dex/classes.dex")
                        .setManifest(
                            androidManifest(
                                "com.test.app", withMinSdkVersion(15), withMaxSdkVersion(27)))
                        .setResourceTable(resourceTableWithTestLabel("Test feature")))
            .addModule(
                "asset_module",
                builder ->
                    builder
                        .setManifest(
                            androidManifest(
                                "com.test.app",
                                withTypeAttribute(MODULE_TYPE_ASSET_VALUE),
                                withOnDemandDelivery(),
                                withFusingAttribute(true),
                                clearApplication(),
                                withoutVersionCode()))
                        .addFile("assets/images/image.jpg"))
            .build();
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    // Variants
    ImmutableList<Variant> variants = splitApkVariants(result);
    assertThat(variants).hasSize(1);
    Variant splitApkVariant = variants.get(0);
    List<ApkSet> apks = splitApkVariant.getApkSetList();
    assertThat(apks).hasSize(1);

    ApkSet baseSplits = apks.get(0);
    assertThat(baseSplits.getModuleMetadata().getName()).isEqualTo("base");
    assertThat(baseSplits.getModuleMetadata().getOnDemand()).isFalse();
    assertThat(baseSplits.getApkDescriptionList()).hasSize(1);
    assertThat(apkSetFile).hasFile(baseSplits.getApkDescription(0).getPath());

    // Asset Slices
    List<AssetSlice> slices = result.getAssetSliceList();
    assertThat(slices).hasSize(1);

    AssetSlice slice = slices.get(0);
    assertThat(slice.getAssetModuleMetadata().getName()).isEqualTo("asset_module");
    assertThat(slice.getAssetModuleMetadata().hasInstantMetadata()).isFalse();
    assertThat(slice.getApkDescriptionList()).hasSize(1);
    assertThat(apkSetFile).hasFile(slice.getApkDescription(0).getPath());

    ApkDescription apkDescription = slice.getApkDescription(0);
    assertThat(apkDescription.getTargeting()).isEqualToDefaultInstance();
    assertThat(apkDescription.getPath()).isEqualTo("asset-slices/asset_module-master.apk");
    assertThat(apkDescription.hasAssetSliceMetadata()).isTrue();
    assertThat(apkDescription.getAssetSliceMetadata().getIsMasterSplit()).isTrue();
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
                        .setResourceTable(resourceTableWithTestLabel("Test feature")))
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
                                withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID))))
            .addModule(
                "language_feature",
                builder ->
                    builder
                        .addFile("res/drawable/image.jpg")
                        .addFile("res/drawable-cz/image.jpg")
                        .addFile("res/drawable-fr/image.jpg")
                        .addFile("res/drawable-pl/image.jpg")
                        .setResourceTable(
                            new ResourceTableBuilder()
                                .addPackage("com.test.app", USER_PACKAGE_OFFSET - 1)
                                .addFileResourceForMultipleConfigs(
                                    "drawable",
                                    "image",
                                    ImmutableMap.of(
                                        Configuration.getDefaultInstance(),
                                        "res/drawable/image.jpg",
                                        locale("cz"),
                                        "res/drawable-cz/image.jpg",
                                        locale("fr"),
                                        "res/drawable-fr/image.jpg",
                                        locale("pl"),
                                        "res/drawable-pl/image.jpg"))
                                .build())
                        .setManifest(
                            androidManifestForFeature(
                                "com.test.app",
                                withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID))))
            .setBundleConfig(
                BundleConfigBuilder.create().setUncompressNativeLibraries(false).build())
            .build();

    Path bundlePath = createAndStoreBundle(appBundle);

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
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    final String manifest = "AndroidManifest.xml";
    final String signature = "META-INF/MANIFEST.MF";

    // Validate split APKs.
    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);
    assertThat(splitApkVariants).hasSize(1);
    ImmutableMap<String, List<ApkDescription>> splitApksByModule =
        splitApkVariants.get(0).getApkSetList().stream()
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
                .addCopies("res/xml/splits0.xml", 2)
                // "res/xml/splits0.xml" is created by bundletool with list of generated splits.
                .addCopies("resources.arsc", 2)
                .addCopies(manifest, 2)
                .addCopies(signature, 2)
                .build());
  }


  @Test
  public void splitFileNames_abi() throws Exception {
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
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOptimizationDimensions(ImmutableSet.of(ABI))
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
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
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(splitApkVariants(result)).hasSize(1);
    Variant splitApkVariant = splitApkVariants(result).get(0);

    assertThat(splitApkVariant.getApkSetList()).hasSize(1);
    assertThat(apkNamesInVariant(splitApkVariant))
        .containsExactly("base-master.apk", "base-es.apk", "base-other_lang.apk");
  }

  @Test
  public void apksSigned() throws Exception {
    Path bundlePath = createAndStoreBundle();

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
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThatApksAreSigned(result, apkSetFile, certificate);
  }

  @Test
  public void aapt2ExtractedFromExecutableJar() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", builder -> builder.setManifest(androidManifest("com.app")))
            .build();
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser().parse("--bundle=" + bundlePath, "--output=" + outputFilePath),
            fakeAdbServer);

    execute(command);
  }

  @Test
  public void extractApkSet_outputApksWithoutArchive() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("assets/file.txt")
                        .addFile("dex/classes.dex")
                        .setManifest(androidManifest("com.test.app"))
                        .setResourceTable(resourceTableWithTestLabel("test_label")))
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
                                withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID))))
            .addModule(
                "language_feature",
                builder ->
                    builder
                        .addFile("res/drawable/image.jpg")
                        .addFile("res/drawable-cz/image.jpg")
                        .addFile("res/drawable-fr/image.jpg")
                        .addFile("res/drawable-pl/image.jpg")
                        .setResourceTable(
                            new ResourceTableBuilder()
                                .addPackage("com.test.app", USER_PACKAGE_OFFSET - 1)
                                .addFileResourceForMultipleConfigs(
                                    "drawable",
                                    "image",
                                    ImmutableMap.of(
                                        Configuration.getDefaultInstance(),
                                        "res/drawable/image.jpg",
                                        locale("cz"),
                                        "res/drawable-cz/image.jpg",
                                        locale("fr"),
                                        "res/drawable-fr/image.jpg",
                                        locale("pl"),
                                        "res/drawable-pl/image.jpg"))
                                .build())
                        .setManifest(
                            androidManifestForFeature(
                                "com.test.app",
                                withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID))))
            .build();
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOptimizationDimensions(ImmutableSet.of(ABI, LANGUAGE))
            .setOutputFile(outputDir)
            .setAapt2Command(aapt2Command)
            .setCreateApkSetArchive(false)
            .build();

    Path outputDirectory = execute(command);
    assertThat(outputDirectory).isEqualTo(outputDir);

    BuildApksResult result = parseTocFromFile(outputDirectory.resolve("toc.pb").toFile());

    // Validate all APKs were created.
    verifyApksExist(apkDescriptions(result.getVariantList()), outputDir);
  }

  @Test
  public void bundleToolVersionSet() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", builder -> builder.setManifest(androidManifest("com.test.app")))
            .build();
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    try (ZipFile apkSetFile = new ZipFile(apkSetFilePath.toFile())) {
      BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

      assertThat(result.getBundletool().getVersion())
          .isEqualTo(BundleToolVersion.getCurrentVersion().toString());
    }
  }

  @Test
  public void overwriteSet() throws Exception {
    Path bundlePath = createAndStoreBundle();
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
    Path bundlePath = createAndStoreBundle();
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
    Path bundlePath = createAndStoreBundle();
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
    Path bundlePath = createAndStoreBundle();
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
    Path bundlePath = createAndStoreBundle();
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
    Path bundlePath = createAndStoreBundle();
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
    Path bundlePath = createAndStoreBundle();

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
    ZipFile apkSet = openZipFile(apkSetFilePath.toFile());

    File standaloneApk = extractFromApkSetFile(apkSet, "standalones/standalone.apk", outputDir);
    File masterSplitApk = extractFromApkSetFile(apkSet, "splits/base-master.apk", outputDir);
    assertThat(extractVersionCode(standaloneApk)).isEqualTo(1000);
    assertThat(extractVersionCode(masterSplitApk)).isEqualTo(1001);
  }

  @Test
  public void buildApksCommand_populatesDependencies() throws Exception {
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
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
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

    Path bundlePath = createAndStoreBundle();

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setFirstVariantNumber(firstVariantNumber)
            .build();

    Path apkSetFilePath = execute(command);
    BuildApksResult buildApksResult =
        ApkSetUtils.extractTocFromApkSetFile(openZipFile(apkSetFilePath.toFile()), outputDir);

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
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("dex/classes.dex")
                        .setManifest(androidManifest("com.test.app", withInstant(true))))
            .build();
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(instantApkVariants(result)).hasSize(1);
    Variant variant = instantApkVariants(result).get(0);
    assertThat(variant.hasTargeting()).isTrue();
    assertThat(variant.getTargeting().getSdkVersionTargeting().getValueList())
        .containsExactly(sdkVersionFrom(ANDROID_L_API_VERSION));
  }

  @Test
  public void buildApksCommand_instantApksAndSplitsGenerated() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("dex/classes.dex")
                        .setManifest(androidManifest("com.test.app", withInstant(true))))
            .build();
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
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

  @Test
  public void renderscript32Bit_warningMessageDisplayed() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
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
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .setOutputPrintStream(new PrintStream(output))
            .build();

    execute(command);
    assertThat(new String(output.toByteArray(), UTF_8))
        .contains("WARNING: App Bundle contains 32-bit RenderScript bitcode file (.bc)");
  }

  @Test
  public void renderscript32Bit_64BitStandaloneAndSplitApksFilteredOut() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
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
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);
    assertThat(standaloneApkVariants(result)).hasSize(1);
    assertThat(standaloneApkVariants(result).get(0).getTargeting().getAbiTargeting())
        .isEqualTo(abiTargeting(ARMEABI_V7A));
    assertThat(splitApkVariants(result)).hasSize(1);
    ImmutableSet<AbiTargeting> abiTargetings =
        splitApkVariants(result).get(0).getApkSetList().stream()
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
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    CommandExecutionException exception =
        assertThrows(CommandExecutionException.class, () -> command.execute());
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Generation of 64-bit native libraries is "
                + "disabled, but App Bundle contains only 64-bit native libraries");
  }

  @Test
  public void pinningOfManifestReachableResources_enabledSince_0_8_1() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("res/drawable-mdpi/manifest_image.jpg")
                        .addFile("res/drawable-hdpi/manifest_image.jpg")
                        .addFile("res/drawable-mdpi/other_image.jpg")
                        .addFile("res/drawable-hdpi/other_image.jpg")
                        .setManifest(
                            androidManifest(
                                "com.test.app",
                                withAppIcon(/* ID of "drawable/manifest_image" */ 0x7f010000)))
                        .setResourceTable(
                            new ResourceTableBuilder()
                                .addPackage("com.test.app")
                                .addDrawableResourceForMultipleDensities(
                                    "manifest_image",
                                    ImmutableMap.of(
                                        /* mdpi */ 160, "res/drawable-mdpi/manifest_image.jpg",
                                        /* hdpi */ 240, "res/drawable-hdpi/manifest_image.jpg"))
                                .addDrawableResourceForMultipleDensities(
                                    "other_image",
                                    ImmutableMap.of(
                                        /* mdpi */ 160, "res/drawable-mdpi/other_image.jpg",
                                        /* hdpi */ 240, "res/drawable-hdpi/other_image.jpg"))
                                .build()))
            .setBundleConfig(BundleConfigBuilder.create().setVersion("0.8.1").build())
            .build();
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);
    ImmutableList<ApkDescription> splitApks =
        splitApkVariants.stream()
            .flatMap(variant -> variant.getApkSetList().stream())
            .flatMap(apkSet -> apkSet.getApkDescriptionList().stream())
            .collect(toImmutableList());

    // The lowest density (mdpi) of "drawable/manifest_reachable_image" is in the master.
    ImmutableList<ApkDescription> masterSplits =
        splitApks.stream()
            .filter(apkDesc -> apkDesc.getSplitApkMetadata().getIsMasterSplit())
            .collect(toImmutableList());
    assertThat(masterSplits).isNotEmpty();
    for (ApkDescription masterSplit : masterSplits) {
      assertThat(filesInApk(masterSplit, apkSetFile))
          .contains("res/drawable-mdpi/manifest_image.jpg");
    }

    ImmutableList<ApkDescription> densitySplits =
        splitApks.stream()
            .filter(apkDesc -> apkDesc.getTargeting().hasScreenDensityTargeting())
            .collect(toImmutableList());
    assertThat(densitySplits).isNotEmpty();
    for (ApkDescription densitySplit : densitySplits) {
      assertThat(filesInApk(densitySplit, apkSetFile))
          .doesNotContain("res/drawable-mdpi/manifest_image.jpg");
    }
  }

  @Test
  public void pinningOfManifestReachableResources_disabledBefore_0_8_1() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("res/drawable-mdpi/manifest_image.jpg")
                        .addFile("res/drawable-hdpi/manifest_image.jpg")
                        .setManifest(
                            androidManifest(
                                "com.test.app",
                                withAppIcon(/* ID of "drawable/manifest_image" */ 0x7f010000)))
                        .setResourceTable(
                            new ResourceTableBuilder()
                                .addPackage("com.test.app")
                                .addDrawableResourceForMultipleDensities(
                                    "manifest_image",
                                    ImmutableMap.of(
                                        /* mdpi */ 160, "res/drawable-mdpi/manifest_image.jpg",
                                        /* hdpi */ 240, "res/drawable-hdpi/manifest_image.jpg"))
                                .build()))
            .setBundleConfig(BundleConfigBuilder.create().setVersion("0.8.0").build())
            .build();
    Path bundlePath = createAndStoreBundle(appBundle);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = execute(command);
    ZipFile apkSetFile = openZipFile(apkSetFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);
    ImmutableList<ApkDescription> splitApks =
        splitApkVariants.stream()
            .flatMap(variant -> variant.getApkSetList().stream())
            .flatMap(apkSet -> apkSet.getApkDescriptionList().stream())
            .collect(toImmutableList());

    ImmutableList<ApkDescription> masterSplits =
        splitApks.stream()
            .filter(apkDesc -> apkDesc.getSplitApkMetadata().getIsMasterSplit())
            .collect(toImmutableList());
    assertThat(masterSplits).isNotEmpty();
    for (ApkDescription masterSplit : masterSplits) {
      assertThat(filesInApk(masterSplit, apkSetFile))
          .doesNotContain("res/drawable-mdpi/manifest_image.jpg");
    }

    ImmutableList<ApkDescription> densitySplits =
        splitApks.stream()
            .filter(apkDesc -> apkDesc.getTargeting().hasScreenDensityTargeting())
            .collect(toImmutableList());
    assertThat(densitySplits).isNotEmpty();
    for (ApkDescription densitySplit : densitySplits) {
      assertThat(filesInApk(densitySplit, apkSetFile))
          .containsAnyOf(
              "res/drawable-mdpi/manifest_image.jpg", "res/drawable-hdpi/manifest_image.jpg");
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

  private static ImmutableList<String> apkNamesInVariant(Variant variant) {
    return variant.getApkSetList().stream()
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
    return apkDescs.stream()
        .flatMap(apkDesc -> filesInApk(apkDesc, apkSetFile).stream())
        .collect(toImmutableMultiset());
  }

  /** Verifies that all given ApkDescriptions point to the APK files in the output directory. */
  private void verifyApksExist(Collection<ApkDescription> apkDescs, Path outputDirectory) {
    apkDescs.stream()
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
      return Collections.list(apkZip.entries()).stream()
          .map(ZipEntry::getName)
          .collect(toImmutableSet());
    }
  }

  private Path createAndStoreBundle() throws IOException {
    return createAndStoreBundle(
        new AppBundleBuilder()
            .addModule("base", builder -> builder.setManifest(androidManifest("com.app")))
            .build());
  }

  private Path createAndStoreBundle(AppBundle appBundle) throws IOException {
    Path bundlePath = tmp.newFolder().toPath().resolve("bundle.aab");
    bundleSerializer.writeToDisk(appBundle, bundlePath);
    return bundlePath;
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

  private ImmutableMap<ApkTargeting, Variant> extractApexVariantsByTargeting(
      BuildApksResult result) {
    return Maps.uniqueIndex(
        apexApkVariants(result),
        variant -> getOnlyElement(apkDescriptions(variant)).getTargeting());
  }

  private void checkVariantMultiAbiTargeting(Variant variant, VariantTargeting targeting) {
    assertThat(variant.getTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(mergeVariantTargeting(targeting, variantSdkTargeting(LOWEST_SDK_VERSION)));
  }

  private ZipFile openZipFile(File zipPath) throws IOException {
    ZipFile zipFile = new ZipFile(zipPath);
    openedZipFiles.register(zipFile);
    return zipFile;
  }
}
