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
import static com.android.bundle.Targeting.Abi.AbiAlias.MIPS;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86_64;
import static com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias.ATC;
import static com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias.ETC1_RGB8;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode.ARCHIVE;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode.INSTANT;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode.PERSISTENT;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode.SYSTEM;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode.UNIVERSAL;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.OutputFormat.DIRECTORY;
import static com.android.tools.build.bundletool.commands.ExtractApksCommand.ALL_MODULES_SHORTCUT;
import static com.android.tools.build.bundletool.model.AndroidManifest.DEVELOPMENT_SDK_VERSION;
import static com.android.tools.build.bundletool.model.BundleModule.ASSETS_DIRECTORY;
import static com.android.tools.build.bundletool.model.OptimizationDimension.ABI;
import static com.android.tools.build.bundletool.model.OptimizationDimension.LANGUAGE;
import static com.android.tools.build.bundletool.model.OptimizationDimension.TEXTURE_COMPRESSION_FORMAT;
import static com.android.tools.build.bundletool.model.SourceStampConstants.STAMP_SOURCE_METADATA_KEY;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.MDPI_VALUE;
import static com.android.tools.build.bundletool.model.utils.ResultUtils.apexApkVariants;
import static com.android.tools.build.bundletool.model.utils.ResultUtils.archivedApkVariants;
import static com.android.tools.build.bundletool.model.utils.ResultUtils.instantApkVariants;
import static com.android.tools.build.bundletool.model.utils.ResultUtils.splitApkVariants;
import static com.android.tools.build.bundletool.model.utils.ResultUtils.standaloneApkVariants;
import static com.android.tools.build.bundletool.model.utils.ResultUtils.systemApkVariants;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_M_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_N_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_P_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_Q_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_S_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_S_V2_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_T_API_VERSION;
import static com.android.tools.build.bundletool.testing.ApkSetUtils.extractFromApkSetFile;
import static com.android.tools.build.bundletool.testing.ApkSetUtils.extractTocFromApkSetFile;
import static com.android.tools.build.bundletool.testing.ApkSetUtils.parseTocFromFile;
import static com.android.tools.build.bundletool.testing.CodeTransparencyTestUtils.createJwsToken;
import static com.android.tools.build.bundletool.testing.DeviceFactory.abis;
import static com.android.tools.build.bundletool.testing.DeviceFactory.density;
import static com.android.tools.build.bundletool.testing.DeviceFactory.deviceTier;
import static com.android.tools.build.bundletool.testing.DeviceFactory.locales;
import static com.android.tools.build.bundletool.testing.DeviceFactory.mergeSpecs;
import static com.android.tools.build.bundletool.testing.DeviceFactory.sdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForAssetModule;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForFeature;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withAppIcon;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withCustomThemeActivity;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withDelivery;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withDeviceGroupsCondition;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withFeatureCondition;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withFusingAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstallLocation;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstallTimeDelivery;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstallTimeRemovableElement;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstant;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstantOnDemandDelivery;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMainActivity;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMaxSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withNativeActivity;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withOnDemandAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withOnDemandDelivery;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitNameService;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withTargetSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withTitle;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withUsesSplit;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.LDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.MDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.TEST_LABEL_RESOURCE_ID;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.USER_PACKAGE_OFFSET;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.locale;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.resourceTableWithTestLabel;
import static com.android.tools.build.bundletool.testing.TargetingUtils.alternativeLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.alternativeTextureCompressionTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apexImageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apexImages;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkMultiAbiTargetingFromAllTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assets;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.deviceTierTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.languageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.moduleDeviceGroupsTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeLibraries;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkRuntimeVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedApexImage;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedAssetsDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedNativeDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.textureCompressionFormat;
import static com.android.tools.build.bundletool.testing.TargetingUtils.textureCompressionTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.toAbi;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantMultiAbiTargetingFromAllTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantSdkTargeting;
import static com.android.tools.build.bundletool.testing.TestUtils.extractAndroidManifest;
import static com.android.tools.build.bundletool.testing.TestUtils.filesUnderPath;
import static com.android.tools.build.bundletool.testing.truth.zip.TruthZip.assertThat;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableMultiset.toImmutableMultiset;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.MoreCollectors.onlyElement;
import static com.google.common.collect.Multimaps.transformValues;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static java.util.Comparator.comparing;
import static java.util.function.Function.identity;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.ConfigurationOuterClass.Configuration;
import com.android.apex.ApexManifestProto.ApexManifest;
import com.android.apksig.ApkVerifier;
import com.android.bundle.CodeTransparencyOuterClass.CodeRelatedFile;
import com.android.bundle.CodeTransparencyOuterClass.CodeTransparency;
import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.ApkSet;
import com.android.bundle.Commands.AssetModulesInfo;
import com.android.bundle.Commands.AssetSliceSet;
import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Commands.DefaultTargetingValue;
import com.android.bundle.Commands.DeliveryType;
import com.android.bundle.Commands.InstantMetadata;
import com.android.bundle.Commands.ModuleMetadata;
import com.android.bundle.Commands.PermanentlyFusedModule;
import com.android.bundle.Commands.RuntimeEnabledSdkDependency;
import com.android.bundle.Commands.SplitApkMetadata;
import com.android.bundle.Commands.StandaloneApkMetadata;
import com.android.bundle.Commands.SystemApkMetadata;
import com.android.bundle.Commands.Variant;
import com.android.bundle.Commands.VariantProperties;
import com.android.bundle.Config.AssetModulesConfig;
import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.BundleConfig.BundleType;
import com.android.bundle.Config.Bundletool;
import com.android.bundle.Config.Optimizations;
import com.android.bundle.Config.ResourceOptimizations;
import com.android.bundle.Config.ResourceOptimizations.CollapsedResourceNames;
import com.android.bundle.Config.ResourceOptimizations.ResourceTypeAndName;
import com.android.bundle.Config.SplitDimension.Value;
import com.android.bundle.Config.StandaloneConfig;
import com.android.bundle.Config.UncompressDexFiles.UncompressedDexTargetSdk;
import com.android.bundle.Files.ApexImages;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdk;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdkConfig;
import com.android.bundle.Targeting.Abi;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.DeviceTierTargeting;
import com.android.bundle.Targeting.LanguageTargeting;
import com.android.bundle.Targeting.ScreenDensity;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.bundle.Targeting.SdkRuntimeTargeting;
import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.bundle.Targeting.TextureCompressionFormat;
import com.android.bundle.Targeting.TextureCompressionFormatTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.TestData;
import com.android.tools.build.bundletool.commands.BuildApksCommand.SystemApkOption;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.io.AppBundleSerializer;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.ApkModifier;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.model.SourceStamp;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.exceptions.InvalidVersionCodeException;
import com.android.tools.build.bundletool.model.utils.CertificateHelper;
import com.android.tools.build.bundletool.model.utils.files.FilePreconditions;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.model.version.Version;
import com.android.tools.build.bundletool.testing.ApkSetUtils;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.BundleConfigBuilder;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.android.tools.build.bundletool.testing.CertificateFactory;
import com.android.tools.build.bundletool.testing.CodeRelatedFileBuilderHelper;
import com.android.tools.build.bundletool.testing.FakeAdbServer;
import com.android.tools.build.bundletool.testing.FileUtils;
import com.android.tools.build.bundletool.testing.ResourceTableBuilder;
import com.android.tools.build.bundletool.testing.TestModule;
import com.android.tools.build.bundletool.testing.truth.zip.TruthZip;
import com.android.zipflinger.ZipMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharSource;
import com.google.common.io.Closer;
import com.google.common.truth.Correspondence;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.Int32Value;
import dagger.Component;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
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
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.inject.Inject;
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

  private static final String SIGNER_CONFIG_NAME = "BNDLTOOL";
  private static final int ANDROID_L_API_VERSION = 21;
  private static final String KEYSTORE_PASSWORD = "keystore-password";
  private static final String KEY_PASSWORD = "key-password";
  private static final String KEY_ALIAS = "key-alias";

  private static final VariantTargeting UNRESTRICTED_VARIANT_TARGETING =
      variantSdkTargeting(/* minSdkVersion= */ 1);
  private static final SdkVersion LOWEST_SDK_VERSION = sdkVersionFrom(1);

  private static final String APEX_MANIFEST_PATH = "root/apex_manifest.pb";
  private static final byte[] APEX_MANIFEST =
      ApexManifest.newBuilder().setName("com.test.app").build().toByteArray();

  private static final SdkVersion L_SDK_VERSION = sdkVersionFrom(ANDROID_L_API_VERSION);
  private static final SdkVersion M_SDK_VERSION = sdkVersionFrom(ANDROID_M_API_VERSION);
  private static final SdkVersion N_SDK_VERSION = sdkVersionFrom(ANDROID_N_API_VERSION);
  private static final SdkVersion P_SDK_VERSION = sdkVersionFrom(ANDROID_P_API_VERSION);
  private static final SdkVersion Q_SDK_VERSION = sdkVersionFrom(ANDROID_Q_API_VERSION);
  private static final SdkVersion S_SDK_VERSION = sdkVersionFrom(ANDROID_S_API_VERSION);
  private static final SdkVersion S2_V2_SDK_VERSION = sdkVersionFrom(ANDROID_S_V2_API_VERSION);

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  private static PrivateKey privateKey;
  private static X509Certificate certificate;

  private final AppBundleSerializer bundleSerializer = new AppBundleSerializer();

  private Path tmpDir;
  private Path outputDir;
  private Path outputFilePath;
  private Closer openedZipFiles;

  private final AdbServer fakeAdbServer =
      new FakeAdbServer(/* hasInitialDeviceList= */ true, /* devices= */ ImmutableList.of());

  @DataPoints("bundleFeatureEnabled")
  public static final ImmutableSet<Boolean> BUNDLE_FEATURE_ENABLED_DATA_POINTS =
      ImmutableSet.of(false, true);

  @Inject BuildApksManager buildApksManager;
  @Inject BuildApksCommand command;

  protected TestModule.Builder createTestModuleBuilder() {
    return TestModule.builder();
  }

  @BeforeClass
  public static void setUpClass() throws Exception {
    // Creating a new key takes in average 75ms (with peaks at 200ms), so creating a single one for
    // all the tests.
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(/* keysize= */ 3072);
    KeyPair keyPair = kpg.genKeyPair();
    privateKey = keyPair.getPrivate();
    certificate = CertificateFactory.buildSelfSignedCertificate(keyPair, "CN=BuildApksCommandTest");
  }

  @Before
  public void setUp() throws Exception {
    TestComponent.useTestModule(this, createTestModuleBuilder().build());
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
  public void parallelExecutionSucceeds() throws Exception {
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withExecutorService(MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(3)))
            .build());

    buildApksManager.execute();
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
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
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

  @Test
  public void selectsRightModules_universalMode_withModulesFlag() throws Exception {
    AppBundle appBundle = createAppBundleWithBaseAndFeatureModules("ar", "vr");
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withApkBuildMode(UNIVERSAL)
            .withModules("base", "vr")
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);
    ImmutableList<Variant> standaloneVariants = standaloneApkVariants(result);
    assertThat(standaloneVariants).hasSize(1);
    assertThat(splitApkVariants(result)).isEmpty();
    assertThat(apkDescriptions(standaloneVariants))
        .containsExactly(
            ApkDescription.newBuilder()
                .setPath("universal.apk")
                .setTargeting(ApkTargeting.getDefaultInstance())
                .setStandaloneApkMetadata(
                    StandaloneApkMetadata.newBuilder()
                        .addAllFusedModuleName(ImmutableList.of("base", "vr")))
                .build());
  }

  @Test
  public void selectsRightModules_universalMode_withModulesFlag_allModulesShortcut()
      throws Exception {
    AppBundle appBundle = createAppBundleWithBaseAndFeatureModules("ar", "vr");
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withApkBuildMode(UNIVERSAL)
            .withModules(ALL_MODULES_SHORTCUT)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);
    assertThat(apkDescriptions(standaloneApkVariants(result)))
        .ignoringRepeatedFieldOrder()
        .containsExactly(
            ApkDescription.newBuilder()
                .setPath("universal.apk")
                .setTargeting(ApkTargeting.getDefaultInstance())
                .setStandaloneApkMetadata(
                    StandaloneApkMetadata.newBuilder()
                        .addAllFusedModuleName(ImmutableList.of("base", "vr", "ar")))
                .build());
  }

  @Test
  public void selectsRightModules_systemMode_withModulesFlag() throws Exception {
    AppBundle appBundle = createAppBundleWithBaseAndFeatureModules("ar", "vr");
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withApkBuildMode(SYSTEM)
            .withModules("base", "vr")
            .withDeviceSpec(
                mergeSpecs(
                    sdkVersion(28), abis("x86"), density(DensityAlias.MDPI), locales("en-US")))
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);
    ImmutableList<Variant> systemVariants = systemApkVariants(result);
    assertThat(apkDescriptions(systemVariants))
        .containsExactly(
            ApkDescription.newBuilder()
                .setPath("system/system.apk")
                .setTargeting(ApkTargeting.getDefaultInstance())
                .setSystemApkMetadata(
                    SystemApkMetadata.newBuilder()
                        .addAllFusedModuleName(ImmutableList.of("base", "vr")))
                .build(),
            ApkDescription.newBuilder()
                .setPath("splits/ar-master.apk")
                .setTargeting(ApkTargeting.getDefaultInstance())
                .setSplitApkMetadata(
                    SplitApkMetadata.newBuilder().setSplitId("ar").setIsMasterSplit(true))
                .build());
  }

  @Test
  public void systemMode_withModulesFlag_includesDependenciesOfModules() throws Exception {
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
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withApkBuildMode(SYSTEM)
            .withModules("base", "feature2")
            .withDeviceSpec(
                mergeSpecs(
                    sdkVersion(28), abis("x86"), density(DensityAlias.MDPI), locales("en-US")))
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);
    ImmutableList<Variant> systemVariants = systemApkVariants(result);
    // feature1 is automatically included because feature2 module depends on it.
    assertThat(apkDescriptions(systemVariants))
        .containsExactly(
            ApkDescription.newBuilder()
                .setPath("system/system.apk")
                .setTargeting(ApkTargeting.getDefaultInstance())
                .setSystemApkMetadata(
                    SystemApkMetadata.newBuilder()
                        .addAllFusedModuleName(ImmutableList.of("base", "feature1", "feature2")))
                .build());
  }

  @Test
  @Theory
  public void selectsRightModules_systemApks() throws Exception {
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
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withApkBuildMode(SYSTEM)
            .withDeviceSpec(
                mergeSpecs(
                    sdkVersion(28), abis("x86"), density(DensityAlias.MDPI), locales("en-US")))
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    // System APKs: Only base and modules marked for fusing must be used.
    assertThat(systemApkVariants(result)).hasSize(1);
    ImmutableList<ApkDescription> systemApks = apkDescriptions(systemApkVariants(result));

    assertThat(systemApks)
        .containsExactly(
            ApkDescription.newBuilder()
                .setPath("system/system.apk")
                .setTargeting(ApkTargeting.getDefaultInstance())
                .setSystemApkMetadata(
                    SystemApkMetadata.newBuilder()
                        .addAllFusedModuleName(ImmutableList.of("base", "fused")))
                .build(),
            ApkDescription.newBuilder()
                .setPath("splits/not_fused-master.apk")
                .setTargeting(ApkTargeting.getDefaultInstance())
                .setSplitApkMetadata(
                    SplitApkMetadata.newBuilder().setSplitId("not_fused").setIsMasterSplit(true))
                .build());
    File systemApkFile = extractFromApkSetFile(apkSetFile, "system/system.apk", outputDir);

    // Validate that the system APK contains appropriate files.
    ZipFile systemApkZip = openZipFile(systemApkFile);
    assertThat(filesUnderPath(systemApkZip, ZipPath.create("assets")))
        .containsExactly("assets/base.txt", "assets/fused.txt");
  }

  @Test
  @Theory
  public void fuseConditionalModulesMatchedToGivenDevice_systemApks() throws Exception {
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
                "with_fuse_no_match",
                module ->
                    module
                        .addFile("assets/fused.txt")
                        .setManifest(
                            androidManifestForFeature(
                                "com.app",
                                withFusingAttribute(true),
                                withDeviceGroupsCondition(ImmutableList.of("group2")),
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
            .addModule(
                "with_fuse_and_match",
                module ->
                    module
                        .addFile("assets/with_fuse_and_match.txt")
                        .setManifest(
                            androidManifestForFeature(
                                "com.app",
                                withFusingAttribute(true),
                                withDeviceGroupsCondition(ImmutableList.of("group1")),
                                withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID))))
            .addModule(
                "without_fuse_and_match",
                module ->
                    module
                        .addFile("assets/without_fuse_and_match.txt")
                        .setManifest(
                            androidManifestForFeature(
                                "com.app",
                                withFusingAttribute(false),
                                withDeviceGroupsCondition(ImmutableList.of("group1")),
                                withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID))))
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withApkBuildMode(SYSTEM)
            .withFuseOnlyDeviceMatchingModules(true)
            .withDeviceSpec(
                mergeSpecs(
                        sdkVersion(28), abis("x86"), density(DensityAlias.MDPI), locales("en-US"))
                    .toBuilder()
                    .addDeviceGroups("group1")
                    .build())
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    // System APKs: Only base and modules marked for fusing must be used.
    assertThat(systemApkVariants(result)).hasSize(1);
    ImmutableList<ApkDescription> systemApks = apkDescriptions(systemApkVariants(result));

    assertThat(systemApks)
        .containsExactly(
            ApkDescription.newBuilder()
                .setPath("system/system.apk")
                .setTargeting(ApkTargeting.getDefaultInstance())
                .setSystemApkMetadata(
                    SystemApkMetadata.newBuilder()
                        .addAllFusedModuleName(ImmutableList.of("base", "with_fuse_and_match")))
                .build(),
            ApkDescription.newBuilder()
                .setPath("splits/not_fused-master.apk")
                .setTargeting(ApkTargeting.getDefaultInstance())
                .setSplitApkMetadata(
                    SplitApkMetadata.newBuilder().setSplitId("not_fused").setIsMasterSplit(true))
                .build(),
            ApkDescription.newBuilder()
                .setPath("splits/without_fuse_and_match-master.apk")
                .setTargeting(ApkTargeting.getDefaultInstance())
                .setSplitApkMetadata(
                    SplitApkMetadata.newBuilder()
                        .setSplitId("without_fuse_and_match")
                        .setIsMasterSplit(true))
                .build(),
            ApkDescription.newBuilder()
                .setPath("splits/with_fuse_no_match-master.apk")
                .setTargeting(ApkTargeting.getDefaultInstance())
                .setSplitApkMetadata(
                    SplitApkMetadata.newBuilder()
                        .setSplitId("with_fuse_no_match")
                        .setIsMasterSplit(true))
                .build());
    File systemApkFile = extractFromApkSetFile(apkSetFile, "system/system.apk", outputDir);

    // Validate that the system APK contains appropriate files.
    ZipFile systemApkZip = openZipFile(systemApkFile);
    assertThat(filesUnderPath(systemApkZip, ZipPath.create("assets")))
        .containsExactly("assets/base.txt", "assets/with_fuse_and_match.txt");
  }

  @Test
  @Theory
  public void
      fuseConditionalModulesMatchedToGivenDevice_withDeviceFeature_withNoSdkVersion_systemApks()
          throws Exception {
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
                "with_fuse_no_match",
                module ->
                    module
                        .addFile("assets/fused.txt")
                        .setManifest(
                            androidManifestForFeature(
                                "com.app",
                                withFusingAttribute(true),
                                withDeviceGroupsCondition(ImmutableList.of("group2")),
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
            .addModule(
                "with_fuse_and_match",
                module ->
                    module
                        .addFile("assets/with_fuse_and_match.txt")
                        .setManifest(
                            androidManifestForFeature(
                                "com.app",
                                withFusingAttribute(true),
                                withDeviceGroupsCondition(ImmutableList.of("group1")),
                                withFeatureCondition("feature1"),
                                withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID))))
            .addModule(
                "without_fuse_and_match",
                module ->
                    module
                        .addFile("assets/without_fuse_and_match.txt")
                        .setManifest(
                            androidManifestForFeature(
                                "com.app",
                                withFusingAttribute(false),
                                withDeviceGroupsCondition(ImmutableList.of("group1")),
                                withFeatureCondition("feature1"),
                                withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID))))
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withApkBuildMode(SYSTEM)
            .withFuseOnlyDeviceMatchingModules(true)
            .withDeviceSpec(
                mergeSpecs(abis("x86"), density(DensityAlias.MDPI), locales("en-US")).toBuilder()
                    .addDeviceGroups("group1")
                    .addDeviceFeatures("feature1")
                    .build())
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    // System APKs: Only base and modules marked for fusing must be used.
    assertThat(systemApkVariants(result)).hasSize(1);
    ImmutableList<ApkDescription> systemApks = apkDescriptions(systemApkVariants(result));

    assertThat(systemApks)
        .containsExactly(
            ApkDescription.newBuilder()
                .setPath("system/system.apk")
                .setTargeting(ApkTargeting.getDefaultInstance())
                .setSystemApkMetadata(
                    SystemApkMetadata.newBuilder()
                        .addAllFusedModuleName(ImmutableList.of("base", "with_fuse_and_match")))
                .build(),
            ApkDescription.newBuilder()
                .setPath("splits/not_fused-master.apk")
                .setTargeting(ApkTargeting.getDefaultInstance())
                .setSplitApkMetadata(
                    SplitApkMetadata.newBuilder().setSplitId("not_fused").setIsMasterSplit(true))
                .build(),
            ApkDescription.newBuilder()
                .setPath("splits/without_fuse_and_match-master.apk")
                .setTargeting(ApkTargeting.getDefaultInstance())
                .setSplitApkMetadata(
                    SplitApkMetadata.newBuilder()
                        .setSplitId("without_fuse_and_match")
                        .setIsMasterSplit(true))
                .build(),
            ApkDescription.newBuilder()
                .setPath("splits/with_fuse_no_match-master.apk")
                .setTargeting(ApkTargeting.getDefaultInstance())
                .setSplitApkMetadata(
                    SplitApkMetadata.newBuilder()
                        .setSplitId("with_fuse_no_match")
                        .setIsMasterSplit(true))
                .build());
    File systemApkFile = extractFromApkSetFile(apkSetFile, "system/system.apk", outputDir);

    // Validate that the system APK contains appropriate files.
    ZipFile systemApkZip = openZipFile(systemApkFile);
    assertThat(filesUnderPath(systemApkZip, ZipPath.create("assets")))
        .containsExactly("assets/base.txt", "assets/with_fuse_and_match.txt");
  }

  @Test
  @Theory
  public void multipleModules_systemApks_hasCorrectAdditionalLanguageSplits() throws Exception {
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
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withApkBuildMode(SYSTEM)
            .withDeviceSpec(
                mergeSpecs(sdkVersion(28), abis("x86"), density(DensityAlias.MDPI), locales("es")))
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(systemApkVariants(result)).hasSize(1);
    Variant systemVariant = result.getVariant(0);
    assertThat(systemVariant.getVariantNumber()).isEqualTo(0);
    assertThat(systemVariant.getApkSetList()).hasSize(1);
    ApkSet baseApkSet = Iterables.getOnlyElement(systemVariant.getApkSetList());
    assertThat(baseApkSet.getModuleMetadata().getName()).isEqualTo("base");
    assertThat(baseApkSet.getApkDescriptionList()).hasSize(2);
    assertThat(
            baseApkSet.getApkDescriptionList().stream()
                .map(ApkDescription::getPath)
                .collect(toImmutableSet()))
        .containsExactly("system/system.apk", "splits/base-fr.apk");
    baseApkSet
        .getApkDescriptionList()
        .forEach(apkDescription -> assertThat(apkSetFile).hasFile(apkDescription.getPath()));
  }

  @Test
  @Theory
  public void multipleModulesFusedAndNotFused_systemApks_hasCorrectAdditionalLanguageSplits()
      throws Exception {
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
            .addModule(
                "notfused",
                module ->
                    module
                        .addFile("assets/notfused.txt")
                        .addFile("assets/notfused/languages#lang_it/image.jpg")
                        .setAssetsConfig(
                            assets(
                                targetedAssetsDirectory(
                                    "assets/notfused/languages#lang_it",
                                    assetsDirectoryTargeting(languageTargeting("it")))))
                        .setManifest(
                            androidManifestForFeature(
                                "com.app",
                                withFusingAttribute(false),
                                withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID))))
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withApkBuildMode(SYSTEM)
            .withDeviceSpec(
                mergeSpecs(sdkVersion(28), abis("x86"), density(DensityAlias.MDPI), locales("es")))
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(systemApkVariants(result)).hasSize(1);
    Variant systemVariant = result.getVariant(0);
    assertThat(systemVariant.getVariantNumber()).isEqualTo(0);
    assertThat(systemVariant.getApkSetList()).hasSize(2);
    ImmutableMap<String, ApkSet> apkSetByModule =
        Maps.uniqueIndex(
            systemVariant.getApkSetList(), apkSet -> apkSet.getModuleMetadata().getName());
    assertThat(apkSetByModule.keySet()).containsExactly("base", "notfused");
    ApkSet baseApkSet = apkSetByModule.get("base");
    assertThat(baseApkSet.getApkDescriptionList()).hasSize(2);
    assertThat(
            baseApkSet.getApkDescriptionList().stream()
                .map(ApkDescription::getPath)
                .collect(toImmutableSet()))
        .containsExactly("system/system.apk", "splits/base-fr.apk");
    baseApkSet
        .getApkDescriptionList()
        .forEach(apkDescription -> assertThat(apkSetFile).hasFile(apkDescription.getPath()));

    ApkSet nonFusedApkSet = apkSetByModule.get("notfused");
    assertThat(nonFusedApkSet.getApkDescriptionList()).hasSize(2);
    assertThat(
            nonFusedApkSet.getApkDescriptionList().stream()
                .map(ApkDescription::getPath)
                .collect(toImmutableSet()))
        .containsExactly("splits/notfused-master.apk", "splits/notfused-it.apk");
    nonFusedApkSet
        .getApkDescriptionList()
        .forEach(apkDescription -> assertThat(apkSetFile).hasFile(apkDescription.getPath()));
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
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
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
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();
    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(result.getVariantList()).hasSize(1);
    assertThat(standaloneApkVariants(result)).isEmpty();
    assertThat(splitApkVariants(result)).hasSize(1);

    Variant splitApksVariant = splitApkVariants(result).get(0);
    assertThat(splitApksVariant.getTargeting()).isEqualTo(variantSdkTargeting(L_SDK_VERSION));
  }


  @Test
  public void buildApksCommand_appTargetingPreL_buildsStandaloneApksOnly() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder -> builder.setManifest(androidManifest("com.app", withMaxSdkVersion(20))))
            .build();

    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    // Should not throw.
    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
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
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
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
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(result.getVariantList()).hasSize(1);
    assertThat(standaloneApkVariants(result)).isEmpty();
    assertThat(splitApkVariants(result)).hasSize(1);
    assertThat(splitApkVariants(result).get(0).getTargeting())
        .isEqualTo(variantSdkTargeting(/* minSdkVersion= */ 21, ImmutableSet.of(26)));
  }

  @Test
  public void buildApksCommand_instantMode() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder -> builder.setManifest(androidManifest("com.app", withInstant(true))))
            .build();

    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withApkBuildMode(INSTANT)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(splitApkVariants(result)).isEmpty();
    assertThat(standaloneApkVariants(result)).isEmpty();
    assertThat(systemApkVariants(result)).isEmpty();
    assertThat(instantApkVariants(result)).isNotEmpty();
  }

  @Test
  public void buildApksCommand_persistentMode() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder -> builder.setManifest(androidManifest("com.app", withInstant(true))))
            .build();

    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withApkBuildMode(PERSISTENT)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(splitApkVariants(result)).isNotEmpty();
    assertThat(standaloneApkVariants(result)).isNotEmpty();
    assertThat(systemApkVariants(result)).isEmpty();
    assertThat(instantApkVariants(result)).isEmpty();
  }

  @Test
  public void buildApksCommand_instantModeWithAssetModules() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder -> builder.setManifest(androidManifest("com.app", withInstant(true))))
            .addModule(
                "asset_module",
                builder ->
                    builder.setManifest(
                        androidManifestForAssetModule("com.app", withInstantOnDemandDelivery())))
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withApkBuildMode(INSTANT)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(result.getAssetSliceSetList()).isNotEmpty();
  }

  @Test
  public void buildApksCommand_persistentModeWithAssetModules() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder -> builder.setManifest(androidManifest("com.app", withInstant(true))))
            .addModule(
                "asset_module",
                builder -> builder.setManifest(androidManifestForAssetModule("com.app")))
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withApkBuildMode(PERSISTENT)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(result.getAssetSliceSetList()).isNotEmpty();
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
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withApkBuildMode(UNIVERSAL)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
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
  public void buildApksCommand_universal_mergeApplicationElementsFromFeatureManifest()
      throws Exception {
    final int baseThemeRefId = 123;
    final int featureThemeRefId = 4456;
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .setManifest(
                            androidManifest(
                                "com.test.app",
                                withCustomThemeActivity("activity1", baseThemeRefId),
                                withCustomThemeActivity("activity2", baseThemeRefId)))
                        .setResourceTable(resourceTableWithTestLabel("Test feature")))
            .addModule(
                "feature",
                builder ->
                    builder.setManifest(
                        androidManifestForFeature(
                            "com.test.app.feature1",
                            withFusingAttribute(true),
                            withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID),
                            withCustomThemeActivity("activity1", featureThemeRefId),
                            withSplitNameService("service1", "feature"))))
            .addModule(
                "not_fused",
                builder ->
                    builder.setManifest(
                        androidManifestForFeature(
                            "com.test.app.feature2",
                            withFusingAttribute(false),
                            withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID),
                            withCustomThemeActivity("activity2", featureThemeRefId),
                            withSplitNameService("service2", "not_fused"))))
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withApkBuildMode(UNIVERSAL)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    Variant universalVariant = standaloneApkVariants(result).get(0);
    assertThat(apkDescriptions(universalVariant)).hasSize(1);
    ApkDescription universalApk = apkDescriptions(universalVariant).get(0);

    // Correct modules selected for merging.
    assertThat(universalApk.getStandaloneApkMetadata().getFusedModuleNameList())
        .containsExactly("base", "feature");
    File universalApkFile = extractFromApkSetFile(apkSetFile, universalApk.getPath(), outputDir);
    AndroidManifest manifest = extractAndroidManifest(universalApkFile, tmpDir);

    ListMultimap<String, Integer> refIdByActivity =
        transformValues(
            manifest.getActivitiesByName(),
            activity ->
                activity
                    .getAndroidAttribute(AndroidManifest.THEME_RESOURCE_ID)
                    .get()
                    .getValueAsRefId());
    assertThat(refIdByActivity)
        .containsExactly("activity1", featureThemeRefId, "activity2", baseThemeRefId);
    assertThat(getServicesFromManifest(manifest)).containsExactly("service1");
  }

  @Test
  public void buildApksCommand_universal_mergeActivitiesFromFeatureManifest_0_13_4()
      throws Exception {
    final int baseThemeRefId = 123;
    final int featureThemeRefId = 4456;
    AppBundle appBundle =
        new AppBundleBuilder()
            .setBundleConfig(
                BundleConfig.newBuilder()
                    .setBundletool(Bundletool.newBuilder().setVersion("0.13.4"))
                    .build())
            .addModule(
                "base",
                builder ->
                    builder
                        .setManifest(
                            androidManifest(
                                "com.test.app",
                                withCustomThemeActivity("activity1", baseThemeRefId),
                                withCustomThemeActivity("activity2", baseThemeRefId)))
                        .setResourceTable(resourceTableWithTestLabel("Test feature")))
            .addModule(
                "feature",
                builder ->
                    builder.setManifest(
                        androidManifestForFeature(
                            "com.test.app.feature1",
                            withFusingAttribute(true),
                            withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID),
                            withCustomThemeActivity("activity1", featureThemeRefId),
                            withSplitNameService("service1", "feature"))))
            .addModule(
                "not_fused",
                builder ->
                    builder.setManifest(
                        androidManifestForFeature(
                            "com.test.app.feature2",
                            withFusingAttribute(false),
                            withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID),
                            withCustomThemeActivity("activity2", featureThemeRefId),
                            withSplitNameService("service2", "not_fused"))))
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withApkBuildMode(UNIVERSAL)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    Variant universalVariant = standaloneApkVariants(result).get(0);
    assertThat(apkDescriptions(universalVariant)).hasSize(1);
    ApkDescription universalApk = apkDescriptions(universalVariant).get(0);

    // Correct modules selected for merging.
    assertThat(universalApk.getStandaloneApkMetadata().getFusedModuleNameList())
        .containsExactly("base", "feature");
    File universalApkFile = extractFromApkSetFile(apkSetFile, universalApk.getPath(), outputDir);

    AndroidManifest manifest = extractAndroidManifest(universalApkFile, tmpDir);
    ListMultimap<String, Integer> refIdByActivity =
        transformValues(
            manifest.getActivitiesByName(),
            activity ->
                activity
                    .getAndroidAttribute(AndroidManifest.THEME_RESOURCE_ID)
                    .get()
                    .getValueAsRefId());
    assertThat(refIdByActivity)
        .containsExactly("activity1", featureThemeRefId, "activity2", baseThemeRefId);
    assertThat(getServicesFromManifest(manifest)).isEmpty();
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
                                targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86)),
                                targetedNativeDirectory(
                                    "lib/x86_64", nativeDirectoryTargeting(X86_64))))
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
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withApkBuildMode(UNIVERSAL)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
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
  public void buildApksCommand_universal_generatesSingleApkWithAllTcfAssets() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", builder -> builder.setManifest(androidManifest("com.test.app")))
            .addModule(
                "tcf_assets",
                builder ->
                    builder
                        .addFile("assets/textures#tcf_atc/texture.dat")
                        .addFile("assets/textures#tcf_etc1/texture.dat")
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
                            androidManifestForAssetModule(
                                "com.test.app", withInstallTimeDelivery())))
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withApkBuildMode(UNIVERSAL)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);
    assertThat(standaloneApkVariants(result)).hasSize(1);

    Variant universalVariant = standaloneApkVariants(result).get(0);
    assertThat(apkDescriptions(universalVariant)).hasSize(1);

    ApkDescription universalApk = apkDescriptions(universalVariant).get(0);

    // Check APK content
    File universalApkFile = extractFromApkSetFile(apkSetFile, universalApk.getPath(), outputDir);
    try (ZipFile universalApkZipFile = new ZipFile(universalApkFile)) {
      // Even if we used targeted folders, they are all included because we did not activate
      // texture targeting optimization.
      assertThat(filesUnderPath(universalApkZipFile, ASSETS_DIRECTORY))
          .containsExactly(
              "assets/textures#tcf_atc/texture.dat", "assets/textures#tcf_etc1/texture.dat");
    }
  }

  @Test
  public void buildApksCommand_universal_generatesSingleApkWithSingleTcf() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", builder -> builder.setManifest(androidManifest("com.test.app")))
            .addModule(
                "tcf_assets",
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
                            androidManifestForAssetModule(
                                "com.test.app", withInstallTimeDelivery())))
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .addSplitDimension(
                        Value.TEXTURE_COMPRESSION_FORMAT,
                        /* negate= */ false,
                        /* stripSuffix= */ false,
                        /* defaultSuffix= */ "etc1")
                    .build())
            .build();

    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withApkBuildMode(UNIVERSAL)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);
    assertThat(standaloneApkVariants(result)).hasSize(1);

    Variant universalVariant = standaloneApkVariants(result).get(0);
    assertThat(apkDescriptions(universalVariant)).hasSize(1);

    ApkDescription universalApk = apkDescriptions(universalVariant).get(0);

    // Check APK content
    File universalApkFile = extractFromApkSetFile(apkSetFile, universalApk.getPath(), outputDir);
    try (ZipFile universalApkZipFile = new ZipFile(universalApkFile)) {
      // Only the requested format texture is included in the standalone APK.
      // Even if suffix stripping is not activated, the universal APK must only contain one TCF.
      assertThat(filesUnderPath(universalApkZipFile, ASSETS_DIRECTORY))
          .containsExactly("assets/textures#tcf_etc1/etc1_texture.dat");
    }
  }

  @Test
  public void buildApksCommand_universal_generatesSingleApkWithSingleFallbackTcf()
      throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", builder -> builder.setManifest(androidManifest("com.test.app")))
            .addModule(
                "tcf_assets",
                builder ->
                    builder
                        .addFile("assets/textures/fallback_texture.dat")
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
                            androidManifestForAssetModule(
                                "com.test.app", withInstallTimeDelivery())))
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .addSplitDimension(Value.TEXTURE_COMPRESSION_FORMAT)
                    .build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withApkBuildMode(UNIVERSAL)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);
    assertThat(standaloneApkVariants(result)).hasSize(1);

    Variant universalVariant = standaloneApkVariants(result).get(0);
    assertThat(apkDescriptions(universalVariant)).hasSize(1);

    ApkDescription universalApk = apkDescriptions(universalVariant).get(0);

    // Check APK content
    File universalApkFile = extractFromApkSetFile(apkSetFile, universalApk.getPath(), outputDir);
    try (ZipFile universalApkZipFile = new ZipFile(universalApkFile)) {
      // Only the requested format texture is included in the standalone APK.
      // Even if suffix stripping is not activated, the universal APK must only contain one TCF.
      assertThat(filesUnderPath(universalApkZipFile, ASSETS_DIRECTORY))
          .containsExactly("assets/textures/fallback_texture.dat");
    }
  }

  @Test
  public void buildApksCommand_universal_generatesSingleApkWithSuffixStrippedTcfAssets()
      throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", builder -> builder.setManifest(androidManifest("com.test.app")))
            .addModule(
                "tcf_assets",
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
                            androidManifestForAssetModule(
                                "com.test.app", withInstallTimeDelivery())))
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .addSplitDimension(
                        Value.TEXTURE_COMPRESSION_FORMAT,
                        /* negate= */ false,
                        /* stripSuffix= */ true,
                        /* defaultSuffix= */ "etc1")
                    .build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withApkBuildMode(UNIVERSAL)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);
    assertThat(standaloneApkVariants(result)).hasSize(1);

    Variant universalVariant = standaloneApkVariants(result).get(0);
    assertThat(apkDescriptions(universalVariant)).hasSize(1);

    ApkDescription universalApk = apkDescriptions(universalVariant).get(0);

    // Only assets from "tcf_assets" which are etc1 should be included,
    // and the targeting suffix stripped.
    File universalApkFile = extractFromApkSetFile(apkSetFile, universalApk.getPath(), outputDir);
    try (ZipFile universalApkZipFile = new ZipFile(universalApkFile)) {
      assertThat(filesUnderPath(universalApkZipFile, ASSETS_DIRECTORY))
          .containsExactly("assets/textures/etc1_texture.dat");
    }

    // Check that targeting was applied to both the APK and the variant
    assertThat(
            universalVariant.getTargeting().getTextureCompressionFormatTargeting().getValueList())
        .containsExactly(textureCompressionFormat(ETC1_RGB8));
    assertThat(universalApk.getTargeting().getTextureCompressionFormatTargeting().getValueList())
        .containsExactly(textureCompressionFormat(ETC1_RGB8));
  }

  @Test
  public void buildApksCommand_universal_strip64BitLibraries_doesNotStrip() throws Exception {
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
                                targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86)),
                                targetedNativeDirectory(
                                    "lib/x86_64", nativeDirectoryTargeting(X86_64))))
                        .setManifest(androidManifest("com.test.app")))
            .setBundleConfig(
                BundleConfig.newBuilder()
                    .setOptimizations(
                        Optimizations.newBuilder()
                            .setStandaloneConfig(
                                StandaloneConfig.newBuilder().setStrip64BitLibraries(true))
                            .build())
                    .setBundletool(Bundletool.newBuilder().setVersion("0.11.0"))
                    .build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withApkBuildMode(UNIVERSAL)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
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

    File universalApkFile = extractFromApkSetFile(apkSetFile, universalApk.getPath(), outputDir);
    try (ZipFile universalApkZipFile = new ZipFile(universalApkFile)) {
      assertThat(filesUnderPath(universalApkZipFile, ZipPath.create("lib")))
          .containsExactly("lib/x86/libsome.so", "lib/x86_64/libsome.so");
    }
  }

  @Test
  public void buildApksCommand_universal_withAssetModules() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("lib/x86/libsome.so")
                        .setManifest(androidManifest("com.test.app")))
            .addModule(
                "upfront_asset_module",
                builder ->
                    builder
                        .addFile("assets/upfront_asset.jpg")
                        .setManifest(
                            androidManifestForAssetModule(
                                "com.test.app", withInstallTimeDelivery())))
            .addModule(
                "on_demand_asset_module",
                builder ->
                    builder
                        .addFile("assets/on_demand_asset.jpg")
                        .setManifest(
                            androidManifestForAssetModule("com.test.app", withOnDemandDelivery())))
            .build();

    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withApkBuildMode(UNIVERSAL)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(result.getVariantList()).hasSize(1);
    assertThat(splitApkVariants(result)).isEmpty();
    assertThat(standaloneApkVariants(result)).hasSize(1);

    Variant universalVariant = standaloneApkVariants(result).get(0);
    assertThat(universalVariant.getTargeting()).isEqualTo(UNRESTRICTED_VARIANT_TARGETING);

    assertThat(apkDescriptions(universalVariant)).hasSize(1);
    ApkDescription universalApk = apkDescriptions(universalVariant).get(0);
    assertThat(universalApk.getTargeting()).isEqualToDefaultInstance();

    File universalApkFile = extractFromApkSetFile(apkSetFile, universalApk.getPath(), outputDir);
    try (ZipFile universalApkZipFile = new ZipFile(universalApkFile)) {
      assertThat(filesUnderPath(universalApkZipFile, ZipPath.create("lib")))
          .containsExactly("lib/x86/libsome.so");
      assertThat(filesUnderPath(universalApkZipFile, ZipPath.create("assets")))
          .containsExactly("assets/upfront_asset.jpg");
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
                                targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86)),
                                targetedNativeDirectory(
                                    "lib/x86_64", nativeDirectoryTargeting(X86_64))))
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
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withApkBuildMode(SYSTEM)
            .withDeviceSpec(
                mergeSpecs(
                    sdkVersion(28), abis("x86"), density(DensityAlias.MDPI), locales("en-US")))
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
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
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withApkBuildMode(UNIVERSAL)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
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
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(splitApkVariants(result)).hasSize(2);
    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);
    for (Variant splitApkVariant : splitApkVariants) {
      ImmutableMap<String, ApkSet> splitApkSetByModuleName =
          Maps.uniqueIndex(
              splitApkVariant.getApkSetList(), apkSet -> apkSet.getModuleMetadata().getName());
      assertThat(splitApkSetByModuleName).hasSize(2);

      ApkSet baseSplits = splitApkSetByModuleName.get("base");
      assertThat(baseSplits.getModuleMetadata().getName()).isEqualTo("base");
      assertThat(baseSplits.getModuleMetadata().getDeliveryType())
          .isEqualTo(DeliveryType.INSTALL_TIME);
      assertThat(baseSplits.getApkDescriptionList()).hasSize(1);
      assertThat(apkSetFile).hasFile(baseSplits.getApkDescription(0).getPath());

      ApkSet onDemandSplits = splitApkSetByModuleName.get("onDemand");
      assertThat(onDemandSplits.getModuleMetadata().getName()).isEqualTo("onDemand");
      assertThat(onDemandSplits.getModuleMetadata().getDeliveryType())
          .isEqualTo(DeliveryType.ON_DEMAND);
      assertThat(onDemandSplits.getApkDescriptionList()).hasSize(1);
      assertThat(apkSetFile).hasFile(onDemandSplits.getApkDescription(0).getPath());
    }
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
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(splitApkVariants(result)).hasSize(2);
    ImmutableList<Variant> variants = splitApkVariants(result);
    variants.forEach(variant -> assertThat(variant.hasTargeting()).isTrue());
    assertThat(
            variants.stream()
                .flatMap(
                    variant ->
                        variant.getTargeting().getSdkVersionTargeting().getValueList().stream())
                .collect(toImmutableList()))
        .containsExactly(L_SDK_VERSION, S_SDK_VERSION);
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
                                targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86))))
                        .setManifest(androidManifest("com.test.app", withMinSdkVersion(25))))
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(splitApkVariants(result)).hasSize(2);
    ImmutableList<Variant> variants = splitApkVariants(result);
    variants.forEach(variant -> assertThat(variant.hasTargeting()).isTrue());
    assertThat(
            variants.stream()
                .flatMap(
                    variant ->
                        variant.getTargeting().getSdkVersionTargeting().getValueList().stream())
                .collect(toImmutableList()))
        .containsExactly(sdkVersionFrom(25), S_SDK_VERSION);
  }

  @Test
  public void buildApksCommand_splitApks_collapsedResourceNamesOptimization() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("dex/classes.dex")
                        .setResourceTable(
                            new ResourceTableBuilder()
                                .addPackage("com.test.app")
                                .addStringResourceForMultipleLocales(
                                    "application_string",
                                    ImmutableMap.of("", "str", "es", "es-str", "fr", "fr-str"))
                                .addStringResourceForMultipleLocales(
                                    "the_same_string",
                                    ImmutableMap.of("", "str", "es", "es-str", "fr", "fr-str"))
                                .addStringResourceForMultipleLocales(
                                    "no_collapse_string",
                                    ImmutableMap.of("", "str", "es", "es-str", "fr", "fr-str"))
                                .addStringResourceForMultipleLocales(
                                    "another_string",
                                    ImmutableMap.of("", "oth", "es", "oth", "fr", "oth"))
                                .build())
                        .setManifest(androidManifest("com.test.app", withMinSdkVersion(25))))
            .build();
    ImmutableList<ResourceTypeAndName> noCollapseList =
        ImmutableList.of(
            ResourceTypeAndName.newBuilder()
                .setType("string")
                .setName("no_collapse_string")
                .build());

    Path collapsedAndDeduplicatedApks = outputDir.resolve("collapsed-dedup.apks");
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withBundleConfig(
                createCollapsedResourceNameConfig(
                    /* collapseResourceNames= */ true,
                    /* deduplicateResourceEntries= */ true,
                    noCollapseList))
            .withAppBundle(appBundle)
            .withOutputPath(collapsedAndDeduplicatedApks)
            .build());
    buildApksManager.execute();

    Path onlyCollapsedApks = outputDir.resolve("collapsed.apks");
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withBundleConfig(
                createCollapsedResourceNameConfig(
                    /* collapseResourceNames= */ true,
                    /* deduplicateResourceEntries= */ false,
                    noCollapseList))
            .withAppBundle(appBundle)
            .withOutputPath(onlyCollapsedApks)
            .build());
    buildApksManager.execute();

    Path notOptimizedApks = outputDir.resolve("not-optimized.apks");
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withBundleConfig(
                createCollapsedResourceNameConfig(
                    /* collapseResourceNames= */ false,
                    /* deduplicateResourceEntries= */ false,
                    noCollapseList))
            .withAppBundle(appBundle)
            .withOutputPath(notOptimizedApks)
            .build());
    buildApksManager.execute();

    // 2 resources are deduplicated (stored only once) in main and each language split which
    // allows 16 bytes size savings.
    assertThat(getApkSize(collapsedAndDeduplicatedApks, "splits/base-master.apk"))
        .isEqualTo(getApkSize(onlyCollapsedApks, "splits/base-master.apk") - 16);
    assertThat(getApkSize(collapsedAndDeduplicatedApks, "splits/base-es.apk"))
        .isEqualTo(getApkSize(onlyCollapsedApks, "splits/base-es.apk") - 16);
    assertThat(getApkSize(collapsedAndDeduplicatedApks, "splits/base-fr.apk"))
        .isEqualTo(getApkSize(onlyCollapsedApks, "splits/base-fr.apk") - 16);

    // Resource names are collapsed which provides size savings.
    assertThat(getApkSize(onlyCollapsedApks, "splits/base-master.apk"))
        .isLessThan(getApkSize(notOptimizedApks, "splits/base-master.apk"));
    assertThat(getApkSize(onlyCollapsedApks, "splits/base-es.apk"))
        .isLessThan(getApkSize(notOptimizedApks, "splits/base-es.apk"));
    assertThat(getApkSize(onlyCollapsedApks, "splits/base-fr.apk"))
        .isLessThan(getApkSize(notOptimizedApks, "splits/base-fr.apk"));
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
                                targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86))))
                        .setManifest(androidManifest("com.test.app", withMaxSdkVersion(21))))
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(splitApkVariants(result)).hasSize(1);
    Variant variant = splitApkVariants(result).get(0);
    assertThat(variant.hasTargeting()).isTrue();
    assertThat(variant.getTargeting().getSdkVersionTargeting().getValueList())
        .containsExactly(L_SDK_VERSION);
  }

  @Test
  public void buildApksCommand_splitApks_localeConfigXmlNotGenerated() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .setManifest(androidManifest("com.test.app"))
                        .setResourceTable(
                            new ResourceTableBuilder()
                                .addPackage("com.test.app")
                                .addStringResourceForMultipleLocales(
                                    "module", ImmutableMap.of("ru-RU", "module ru-RU"))
                                .build()))
            .build();

    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());
    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    for (Variant splitApkVariant : splitApkVariants(result)) {
      ApkSet baseModule =
          splitApkVariant.getApkSetList().stream()
              .filter(apkSet -> apkSet.getModuleMetadata().getName().equals("base"))
              .collect(onlyElement());

      ApkDescription baseMasterSplit =
          baseModule.getApkDescriptionList().stream()
              .filter(apkDescription -> apkDescription.getSplitApkMetadata().getIsMasterSplit())
              .collect(onlyElement());

      assertThat(filesInApks(ImmutableList.of(baseMasterSplit), apkSetFile))
          .doesNotContain("res/xml/locales_config.xml");
    }
  }

  @Test
  public void buildApksCommand_splitApks_localeConfigXmlGenerated() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .setManifest(androidManifest("com.test.app"))
                        .setResourceTable(
                            new ResourceTableBuilder()
                                .addPackage("com.test.app")
                                .addStringResourceForMultipleLocales(
                                    "module", ImmutableMap.of("ru-RU", "module ru-RU"))
                                .build()))
            .setBundleConfig(BundleConfigBuilder.create().setInjectLocaleConfig(true).build())
            .build();

    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());
    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    for (Variant splitApkVariant : splitApkVariants(result)) {
      ApkSet baseModule =
          splitApkVariant.getApkSetList().stream()
              .filter(apkSet -> apkSet.getModuleMetadata().getName().equals("base"))
              .collect(onlyElement());

      ApkDescription baseMasterSplit =
          baseModule.getApkDescriptionList().stream()
              .filter(apkDescription -> apkDescription.getSplitApkMetadata().getIsMasterSplit())
              .collect(onlyElement());

      assertThat(filesInApks(ImmutableList.of(baseMasterSplit), apkSetFile))
          .contains("res/xml/locales_config.xml");
    }
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
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
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
  public void disabledUncompressedNativeLibraries_singleSplitVariant() throws Exception {
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
                                targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86))))
                        .setManifest(androidManifest("com.test.app")))
            .setBundleConfig(
                BundleConfigBuilder.create().setUncompressNativeLibraries(false).build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);

    assertThat(splitApkVariants).hasSize(2);
    assertThat(
            splitApkVariants.stream()
                .map(variant -> variant.getTargeting().getSdkVersionTargeting())
                .collect(toImmutableList()))
        .containsExactly(
            sdkVersionTargeting(L_SDK_VERSION, ImmutableSet.of(LOWEST_SDK_VERSION, S_SDK_VERSION)),
            sdkVersionTargeting(S_SDK_VERSION, ImmutableSet.of(LOWEST_SDK_VERSION, L_SDK_VERSION)));
  }

  @Test
  public void defaultUncompressedLibraries_after_0_6_0_enabled_multipleSplitVariants()
      throws Exception {
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
                                targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86))))
                        .setManifest(androidManifest("com.test.app")))
            .setBundleConfig(BundleConfigBuilder.create().build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);
    assertThat(
            splitApkVariants.stream()
                .map(variant -> variant.getTargeting().getSdkVersionTargeting()))
        .containsExactly(
            sdkVersionTargeting(
                L_SDK_VERSION, ImmutableSet.of(LOWEST_SDK_VERSION, M_SDK_VERSION, S_SDK_VERSION)),
            sdkVersionTargeting(
                M_SDK_VERSION, ImmutableSet.of(LOWEST_SDK_VERSION, L_SDK_VERSION, S_SDK_VERSION)),
            sdkVersionTargeting(
                S_SDK_VERSION, ImmutableSet.of(LOWEST_SDK_VERSION, L_SDK_VERSION, M_SDK_VERSION)));
  }

  @Test
  public void defaultUncompressedLibraries_before_0_6_0_disabled_singleVariant() throws Exception {
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
                                targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86))))
                        .setManifest(androidManifest("com.test.app")))
            .setBundleConfig(BundleConfigBuilder.create().setVersion("0.5.1").build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);

    assertThat(splitApkVariants).hasSize(1);
    assertThat(splitApkVariants.get(0).getTargeting().getSdkVersionTargeting())
        .isEqualTo(sdkVersionTargeting(L_SDK_VERSION, ImmutableSet.of(LOWEST_SDK_VERSION)));
  }

  @Test
  public void enabledUncompressedNativeLibraries_nativeActivities_multipleSplitVariants()
      throws Exception {
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
                                targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86))))
                        .setManifest(androidManifest("com.test.app", withNativeActivity("some"))))
            .setBundleConfig(
                BundleConfigBuilder.create().setUncompressNativeLibraries(true).build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);
    assertThat(
            splitApkVariants.stream()
                .map(variant -> variant.getTargeting().getSdkVersionTargeting()))
        .containsExactly(
            sdkVersionTargeting(
                L_SDK_VERSION, ImmutableSet.of(LOWEST_SDK_VERSION, N_SDK_VERSION, S_SDK_VERSION)),
            sdkVersionTargeting(
                N_SDK_VERSION, ImmutableSet.of(LOWEST_SDK_VERSION, L_SDK_VERSION, S_SDK_VERSION)),
            sdkVersionTargeting(
                S_SDK_VERSION, ImmutableSet.of(LOWEST_SDK_VERSION, L_SDK_VERSION, N_SDK_VERSION)));
    VariantProperties expectedVariantProperties =
        VariantProperties.newBuilder().setUncompressedNativeLibraries(true).build();
    assertThat(
            splitApkVariants.stream()
                .filter(
                    variant ->
                        variant
                            .getTargeting()
                            .getSdkVersionTargeting()
                            .getValue(0)
                            .equals(N_SDK_VERSION))
                .findAny()
                .get()
                .getVariantProperties())
        .isEqualTo(expectedVariantProperties);
  }

  @Test
  public void enableNativeLibraryCompressionWithExternalStorage_multipleSplitVariants()
      throws Exception {
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
                                targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86))))
                        .setManifest(androidManifest("com.test.app", withInstallLocation("auto"))))
            .setBundleConfig(
                BundleConfigBuilder.create().setUncompressNativeLibraries(true).build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);
    assertThat(
            splitApkVariants.stream()
                .map(variant -> variant.getTargeting().getSdkVersionTargeting()))
        .containsExactly(
            sdkVersionTargeting(
                L_SDK_VERSION, ImmutableSet.of(LOWEST_SDK_VERSION, P_SDK_VERSION, S_SDK_VERSION)),
            sdkVersionTargeting(
                P_SDK_VERSION, ImmutableSet.of(LOWEST_SDK_VERSION, L_SDK_VERSION, S_SDK_VERSION)),
            sdkVersionTargeting(
                S_SDK_VERSION, ImmutableSet.of(LOWEST_SDK_VERSION, L_SDK_VERSION, P_SDK_VERSION)));
  }

  @Test
  public void
      enabledDexCompressionSplitter_enabledUncompressedDexForSVariant_multipleSplitVariants()
          throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder.addFile("dex/classes.dex").setManifest(androidManifest("com.test.app")))
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .setUncompressDexFilesForVariant(UncompressedDexTargetSdk.SDK_31)
                    .setUncompressDexFiles(true)
                    .build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);
    assertThat(
            splitApkVariants.stream()
                .map(variant -> variant.getTargeting().getSdkVersionTargeting()))
        .containsExactly(
            sdkVersionTargeting(L_SDK_VERSION, ImmutableSet.of(LOWEST_SDK_VERSION, S_SDK_VERSION)),
            sdkVersionTargeting(S_SDK_VERSION, ImmutableSet.of(LOWEST_SDK_VERSION, L_SDK_VERSION)));
  }

  @Test
  public void enabledDexCompressionSplitter_enabledUncompressedDex_multipleSplitVariants()
      throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder.addFile("dex/classes.dex").setManifest(androidManifest("com.test.app")))
            .setBundleConfig(BundleConfigBuilder.create().setUncompressDexFiles(true).build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);
    assertThat(
            splitApkVariants.stream()
                .map(variant -> variant.getTargeting().getSdkVersionTargeting()))
        .containsExactly(
            sdkVersionTargeting(L_SDK_VERSION, ImmutableSet.of(LOWEST_SDK_VERSION, Q_SDK_VERSION)),
            sdkVersionTargeting(Q_SDK_VERSION, ImmutableSet.of(LOWEST_SDK_VERSION, L_SDK_VERSION)));
    VariantProperties expectedVariantProperties =
        VariantProperties.newBuilder().setUncompressedDex(true).build();
    assertThat(
            splitApkVariants.stream()
                .filter(
                    variant ->
                        variant
                            .getTargeting()
                            .getSdkVersionTargeting()
                            .getValue(0)
                            .equals(Q_SDK_VERSION))
                .findAny()
                .get()
                .getVariantProperties())
        .isEqualTo(expectedVariantProperties);
  }

  @Test
  public void enabledDexCompressionSplitter_disabledUncompressedDex_noUncompressedDexVariant()
      throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder.addFile("dex/classes.dex").setManifest(androidManifest("com.test.app")))
            .setBundleConfig(BundleConfigBuilder.create().setUncompressDexFiles(false).build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);
    assertThat(
            splitApkVariants.stream()
                .map(variant -> variant.getTargeting().getSdkVersionTargeting()))
        .containsExactly(sdkVersionTargeting(L_SDK_VERSION, ImmutableSet.of(LOWEST_SDK_VERSION)));
  }

  @Test
  public void dexCompressionIsNotSet_enabledByDefault() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder.addFile("dex/classes.dex").setManifest(androidManifest("com.test.app")))
            .setBundleConfig(BundleConfig.getDefaultInstance())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);
    assertThat(
            splitApkVariants.stream()
                .map(variant -> variant.getTargeting().getSdkVersionTargeting()))
        .containsExactly(
            sdkVersionTargeting(L_SDK_VERSION, ImmutableSet.of(LOWEST_SDK_VERSION, S_SDK_VERSION)),
            sdkVersionTargeting(S_SDK_VERSION, ImmutableSet.of(LOWEST_SDK_VERSION, L_SDK_VERSION)));
    Variant uncompressedDexVariant =
        splitApkVariants.stream()
            .filter(
                variant ->
                    variant
                        .getTargeting()
                        .getSdkVersionTargeting()
                        .getValue(0)
                        .equals(S_SDK_VERSION))
            .collect(onlyElement());
    assertThat(uncompressedDexVariant.getVariantProperties().getUncompressedDex()).isTrue();
  }

  @Test
  public void enabledSparseEncodingVariant() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder.setManifest(
                        androidManifest("com.test.app", withTargetSdkVersion("O.fingerprint"))))
            .setBundleConfig(BundleConfigBuilder.create().setSparseEncodingForSdk32().build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);
    assertThat(
            splitApkVariants.stream()
                .map(variant -> variant.getTargeting().getSdkVersionTargeting()))
        .containsExactly(
            sdkVersionTargeting(
                L_SDK_VERSION, ImmutableSet.of(LOWEST_SDK_VERSION, S2_V2_SDK_VERSION)),
            sdkVersionTargeting(
                S2_V2_SDK_VERSION, ImmutableSet.of(LOWEST_SDK_VERSION, L_SDK_VERSION)));
    VariantProperties expectedVariantProperties =
        VariantProperties.newBuilder().setSparseEncoding(true).build();
    assertThat(
            splitApkVariants.stream()
                .filter(
                    variant ->
                        variant
                            .getTargeting()
                            .getSdkVersionTargeting()
                            .getValue(0)
                            .equals(S2_V2_SDK_VERSION))
                .findAny()
                .get()
                .getVariantProperties())
        .isEqualTo(expectedVariantProperties);
  }

  @Test
  public void
      enableNativeLibsDexCompressionSparseEncodingSplitterEnabled_multipleSplitVariants_correctVariantProperties()
          throws Exception {
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
                                targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86))))
                        .setManifest(
                            androidManifest("com.test.app", withTargetSdkVersion("O.fingerprint"))))
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .setSparseEncodingForSdk32()
                    .setUncompressNativeLibraries(true)
                    .setUncompressDexFiles(true)
                    .build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);
    VariantProperties variantLProperties =
        splitApkVariants.stream()
            .filter(
                variant ->
                    variant
                        .getTargeting()
                        .getSdkVersionTargeting()
                        .getValue(0)
                        .equals(L_SDK_VERSION))
            .findAny()
            .get()
            .getVariantProperties();
    VariantProperties variantMProperties =
        splitApkVariants.stream()
            .filter(
                variant ->
                    variant
                        .getTargeting()
                        .getSdkVersionTargeting()
                        .getValue(0)
                        .equals(M_SDK_VERSION))
            .findAny()
            .get()
            .getVariantProperties();
    VariantProperties variantQProperties =
        splitApkVariants.stream()
            .filter(
                variant ->
                    variant
                        .getTargeting()
                        .getSdkVersionTargeting()
                        .getValue(0)
                        .equals(Q_SDK_VERSION))
            .findAny()
            .get()
            .getVariantProperties();
    VariantProperties variantSV2Properties =
        splitApkVariants.stream()
            .filter(
                variant ->
                    variant
                        .getTargeting()
                        .getSdkVersionTargeting()
                        .getValue(0)
                        .equals(S2_V2_SDK_VERSION))
            .findAny()
            .get()
            .getVariantProperties();
    VariantProperties variantLExpectedProperties =
        VariantProperties.newBuilder()
            .setUncompressedDex(false)
            .setSparseEncoding(false)
            .setUncompressedNativeLibraries(false)
            .build();
    VariantProperties variantMExpectedProperties =
        VariantProperties.newBuilder()
            .setUncompressedDex(false)
            .setSparseEncoding(false)
            .setUncompressedNativeLibraries(true)
            .build();
    VariantProperties variantQExpectedProperties =
        VariantProperties.newBuilder()
            .setUncompressedDex(true)
            .setSparseEncoding(false)
            .setUncompressedNativeLibraries(true)
            .build();
    VariantProperties variantSV2ExpectedProperties =
        VariantProperties.newBuilder()
            .setUncompressedDex(true)
            .setSparseEncoding(true)
            .setUncompressedNativeLibraries(true)
            .build();
    assertThat(variantLProperties).isEqualTo(variantLExpectedProperties);
    assertThat(variantMProperties).isEqualTo(variantMExpectedProperties);
    assertThat(variantQProperties).isEqualTo(variantQExpectedProperties);
    assertThat(variantSV2Properties).isEqualTo(variantSV2ExpectedProperties);
  }

  @Test
  public void noOptimizations_correctVariantProperties() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", builder -> builder.setManifest(androidManifest("com.test.app")))
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);
    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);
    assertThat(splitApkVariants).hasSize(1);
    assertThat(splitApkVariants.get(0).getVariantProperties()).isEqualToDefaultInstance();
  }

  @Test
  public void enableNativeLibsDexCompressionSplitterEnabled_multipleSplitVariants()
      throws Exception {
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
                                targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86))))
                        .setManifest(androidManifest("com.test.app")))
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .setUncompressNativeLibraries(true)
                    .setUncompressDexFiles(true)
                    .build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);
    assertThat(
            splitApkVariants.stream()
                .map(variant -> variant.getTargeting().getSdkVersionTargeting()))
        .containsExactly(
            sdkVersionTargeting(
                L_SDK_VERSION, ImmutableSet.of(LOWEST_SDK_VERSION, M_SDK_VERSION, Q_SDK_VERSION)),
            sdkVersionTargeting(
                M_SDK_VERSION, ImmutableSet.of(LOWEST_SDK_VERSION, L_SDK_VERSION, Q_SDK_VERSION)),
            sdkVersionTargeting(
                Q_SDK_VERSION, ImmutableSet.of(LOWEST_SDK_VERSION, L_SDK_VERSION, M_SDK_VERSION)));
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
                                targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86)),
                                targetedNativeDirectory(
                                    "lib/x86_64", nativeDirectoryTargeting(X86_64)),
                                targetedNativeDirectory(
                                    "lib/mips", nativeDirectoryTargeting(MIPS))))
                        .setManifest(androidManifest("com.test.app")))
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withOptimizationDimensions(ABI)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableMap<Abi, Variant> standaloneVariantsByAbi =
        Maps.uniqueIndex(
            standaloneApkVariants(result),
            variant -> {
              ApkDescription apkDescription = getOnlyElement(apkDescriptions(variant));
              return getOnlyElement(apkDescription.getTargeting().getAbiTargeting().getValueList());
            });
    assertThat(standaloneVariantsByAbi.keySet())
        .containsExactly(toAbi(X86), toAbi(X86_64), toAbi(MIPS));
    assertThat(standaloneVariantsByAbi.get(toAbi(X86)).getTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeVariantTargeting(
                variantAbiTargeting(X86, ImmutableSet.of(X86_64, MIPS)),
                variantSdkTargeting(
                    LOWEST_SDK_VERSION,
                    ImmutableSet.of(L_SDK_VERSION, M_SDK_VERSION, S_SDK_VERSION))));
    assertThat(standaloneVariantsByAbi.get(toAbi(X86_64)).getTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeVariantTargeting(
                variantAbiTargeting(X86_64, ImmutableSet.of(X86, MIPS)),
                variantSdkTargeting(
                    LOWEST_SDK_VERSION,
                    ImmutableSet.of(L_SDK_VERSION, M_SDK_VERSION, S_SDK_VERSION))));
    assertThat(standaloneVariantsByAbi.get(toAbi(MIPS)).getTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeVariantTargeting(
                variantAbiTargeting(MIPS, ImmutableSet.of(X86, X86_64)),
                variantSdkTargeting(
                    LOWEST_SDK_VERSION,
                    ImmutableSet.of(L_SDK_VERSION, M_SDK_VERSION, S_SDK_VERSION))));
    for (Variant variant : standaloneVariantsByAbi.values()) {
      assertThat(variant.getApkSetList()).hasSize(1);
      ApkSet apkSet = variant.getApkSet(0);
      assertThat(apkSet.getApkDescriptionList()).hasSize(1);
      assertThat(apkSetFile).hasFile(apkSet.getApkDescription(0).getPath());
    }
  }

  @Test
  @Theory
  public void buildApksCommand_system_withoutLanguageTargeting() throws Exception {
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
                                targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86)),
                                targetedNativeDirectory(
                                    "lib/x86_64", nativeDirectoryTargeting(X86_64)),
                                targetedNativeDirectory(
                                    "lib/mips", nativeDirectoryTargeting(MIPS))))
                        .setManifest(androidManifest("com.test.app")))
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .addSplitDimension(Value.ABI)
                    .addSplitDimension(Value.SCREEN_DENSITY, /* negate= */ true)
                    .build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withApkBuildMode(SYSTEM)
            .withDeviceSpec(
                mergeSpecs(
                    sdkVersion(28), abis("mips"), density(DensityAlias.MDPI), locales("en-US")))
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(result.getVariantList()).hasSize(1);
    assertThat(splitApkVariants(result)).isEmpty();
    assertThat(standaloneApkVariants(result)).isEmpty();
    assertThat(systemApkVariants(result)).hasSize(1);

    Variant systemVariant = systemApkVariants(result).get(0);
    assertThat(systemVariant.getTargeting())
        .isEqualTo(
            mergeVariantTargeting(
                variantAbiTargeting(MIPS, ImmutableSet.of(X86, X86_64)),
                variantSdkTargeting(LOWEST_SDK_VERSION)));

    assertThat(apkDescriptions(systemVariant)).hasSize(1);
    assertThat(systemVariant.getApkSetList()).hasSize(1);
    ApkSet apkSet = systemVariant.getApkSet(0);
    apkSet
        .getApkDescriptionList()
        .forEach(apkDescription -> assertThat(apkSetFile).hasFile(apkDescription.getPath()));
  }

  @Test
  public void buildApksCommand_system_uncompressedOptions() throws Exception {
    byte[] data = TestData.readBytes("testdata/dex/classes.dex");
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("dex/classes.dex", data)
                        .addFile("lib/x86/libsome.so", data)
                        .addFile("res/raw/some.bin", data)
                        .setResourceTable(
                            new ResourceTableBuilder()
                                .addPackage("com.test.app")
                                .addFileResource("raw", "some", "res/raw/some.bin")
                                .build())
                        .setNativeConfig(
                            nativeLibraries(
                                targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86))))
                        .setManifest(androidManifest("com.test.app")))
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .addSplitDimension(Value.ABI)
                    .setUncompressDexFiles(true)
                    .setUncompressNativeLibraries(true)
                    .build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withApkBuildMode(SYSTEM)
            .withDeviceSpec(
                mergeSpecs(
                    sdkVersion(28), abis("x86"), density(DensityAlias.MDPI), locales("en-US")))
            .withCustomBuildApksCommandSetter(
                builder ->
                    builder.setSystemApkOptions(
                        ImmutableSet.of(
                            SystemApkOption.UNCOMPRESSED_DEX_FILES,
                            SystemApkOption.UNCOMPRESSED_NATIVE_LIBRARIES)))
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(result.getVariantList()).hasSize(1);
    assertThat(systemApkVariants(result)).hasSize(1);

    Variant systemVariant = systemApkVariants(result).get(0);
    try (ZipFile systemApkFile =
        new ZipFile(
            extractFromApkSetFile(
                apkSetFile,
                systemVariant.getApkSet(0).getApkDescriptionList().get(0).getPath(),
                outputDir))) {
      assertThat(systemApkFile).hasFile("lib/x86/libsome.so").thatIsUncompressed();
      assertThat(systemApkFile).hasFile("classes.dex").thatIsUncompressed();
      assertThat(systemApkFile).hasFile("res/raw/some.bin").thatIsCompressed();
      assertThat(systemApkFile).hasFile("AndroidManifest.xml").thatIsCompressed();
    }
  }

  @Test
  @Theory
  public void buildApksCommand_system_withLanguageTargeting() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("lib/x86/libsome.so")
                        .addFile("lib/x86_64/libsome.so")
                        .addFile("lib/mips/libsome.so")
                        .addFile("assets/languages#lang_es/image.jpg")
                        .addFile("assets/languages#lang_fr/image.jpg")
                        .addFile("assets/languages#lang_it/image.jpg")
                        .setResourceTable(
                            new ResourceTableBuilder()
                                .addPackage("com.test.app")
                                .addStringResource("title", "Not fused")
                                .build())
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
                                targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86)),
                                targetedNativeDirectory(
                                    "lib/x86_64", nativeDirectoryTargeting(X86_64)),
                                targetedNativeDirectory(
                                    "lib/mips", nativeDirectoryTargeting(MIPS))))
                        .setManifest(androidManifest("com.test.app")))
            .addModule(
                "not_fused",
                builder ->
                    builder
                        .addFile("lib/x86/libother.so")
                        .addFile("lib/x86_64/libother.so")
                        .addFile("lib/mips/libother.so")
                        .addFile("assets/other_languages#lang_es/image.jpg")
                        .addFile("assets/other_languages#lang_fr/image.jpg")
                        .addFile("assets/other_languages#lang_it/image.jpg")
                        .setAssetsConfig(
                            assets(
                                targetedAssetsDirectory(
                                    "assets/other_languages#lang_it",
                                    assetsDirectoryTargeting(languageTargeting("it"))),
                                targetedAssetsDirectory(
                                    "assets/other_languages#lang_es",
                                    assetsDirectoryTargeting(languageTargeting("es"))),
                                targetedAssetsDirectory(
                                    "assets/other_languages#lang_fr",
                                    assetsDirectoryTargeting(languageTargeting("fr")))))
                        .setNativeConfig(
                            nativeLibraries(
                                targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86)),
                                targetedNativeDirectory(
                                    "lib/x86_64", nativeDirectoryTargeting(X86_64)),
                                targetedNativeDirectory(
                                    "lib/mips", nativeDirectoryTargeting(MIPS))))
                        .setManifest(
                            androidManifestForFeature(
                                "com.test.app",
                                withTitle("Not fused", 0x7f010000),
                                withFusingAttribute(false))))
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withApkBuildMode(SYSTEM)
            .withDeviceSpec(
                mergeSpecs(sdkVersion(28), abis("x86"), density(DensityAlias.MDPI), locales("es")))
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(result.getVariantList()).hasSize(1);
    Variant x86Variant = result.getVariant(0);
    assertThat(x86Variant.getVariantNumber()).isEqualTo(0);
    assertThat(x86Variant.getTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeVariantTargeting(
                variantAbiTargeting(X86, ImmutableSet.of(X86_64, MIPS)),
                variantSdkTargeting(LOWEST_SDK_VERSION)));
    assertThat(x86Variant.getApkSetList()).hasSize(2);

    ApkSet apkSet = x86Variant.getApkSet(0);
    assertThat(
            apkSet.getApkDescriptionList().stream()
                .map(ApkDescription::getPath)
                .collect(toImmutableSet()))
        .containsExactly("system/system.apk", "splits/base-it.apk", "splits/base-fr.apk");

    ApkSet apkSetNotFusedModule = x86Variant.getApkSet(1);
    assertThat(
            apkSetNotFusedModule.getApkDescriptionList().stream()
                .map(ApkDescription::getPath)
                .collect(toImmutableSet()))
        .containsExactly(
            "splits/not_fused-master.apk",
            "splits/not_fused-es.apk",
            "splits/not_fused-it.apk",
            "splits/not_fused-fr.apk",
            "splits/not_fused-x86.apk");

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
                                targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86)),
                                targetedNativeDirectory(
                                    "lib/x86_64", nativeDirectoryTargeting(X86_64))))
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
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withOptimizationDimensions(ABI)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableMap<Abi, ApkDescription> standaloneApksByAbi =
        Maps.uniqueIndex(
            apkDescriptions(standaloneApkVariants(result)),
            apkDesc -> getOnlyElement(apkDesc.getTargeting().getAbiTargeting().getValueList()));

    assertThat(standaloneApksByAbi.keySet()).containsExactly(toAbi(X86), toAbi(X86_64));

    File x86ApkFile =
        extractFromApkSetFile(apkSetFile, standaloneApksByAbi.get(toAbi(X86)).getPath(), outputDir);
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
            apkSetFile, standaloneApksByAbi.get(toAbi(X86_64)).getPath(), outputDir);
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
  public void buildApksCommand_standalone_noTextureTargeting() throws Exception {
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
                        .addFile("assets/textures#tcf_atc/texture.dat")
                        .addFile("assets/textures#tcf_etc1/texture.dat")
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
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withOptimizationDimensions(TEXTURE_COMPRESSION_FORMAT)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(standaloneApkVariants(result)).hasSize(1);
    assertThat(apkDescriptions(standaloneApkVariants(result))).hasSize(1);
    ApkDescription shard = apkDescriptions(standaloneApkVariants(result)).get(0);

    assertThat(apkSetFile).hasFile(shard.getPath());
    try (ZipFile shardZip =
        new ZipFile(extractFromApkSetFile(apkSetFile, shard.getPath(), outputDir))) {
      // Even if we used targeted folders, they are all included because we did not activate
      // texture targeting optimization.
      assertThat(shardZip).hasFile("assets/textures#tcf_atc/texture.dat");
      assertThat(shardZip).hasFile("assets/textures#tcf_etc1/texture.dat");

      // Suffix stripping was NOT applied (because again we did not even activate texture targeting
      // optimization).
      assertThat(shardZip).doesNotHaveFile("assets/textures/texture.dat");
    }
  }

  @Test
  public void buildApksCommand_standalone_mixedTextureTargeting() throws Exception {
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
                    .addSplitDimension(Value.TEXTURE_COMPRESSION_FORMAT, /* negate= */ false)
                    .build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withOptimizationDimensions(TEXTURE_COMPRESSION_FORMAT)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(standaloneApkVariants(result)).hasSize(1);
    assertThat(apkDescriptions(standaloneApkVariants(result))).hasSize(1);
    ApkDescription shard = apkDescriptions(standaloneApkVariants(result)).get(0);

    // Check APK content
    assertThat(apkSetFile).hasFile(shard.getPath());
    try (ZipFile shardZip =
        new ZipFile(extractFromApkSetFile(apkSetFile, shard.getPath(), outputDir))) {
      // Only the default format texture is included in the standalone APK.
      // Even if suffix stripping is not activated, the standalone APK must only contain one TCF.
      assertThat(shardZip).hasFile("assets/textures/untargeted_texture.dat");
      assertThat(shardZip).doesNotHaveFile("assets/textures#tcf_etc1/texture.dat");
    }
  }

  @Test
  public void buildApksCommand_standalone_mixedTextureTargetingWithoutSuffixStripping()
      throws Exception {
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
                        .addFile("assets/textures#tcf_atc/atc_texture.dat")
                        .setAssetsConfig(
                            assets(
                                targetedAssetsDirectory(
                                    "assets/textures",
                                    assetsDirectoryTargeting(
                                        alternativeTextureCompressionTargeting(ETC1_RGB8, ATC))),
                                targetedAssetsDirectory(
                                    "assets/textures#tcf_etc1",
                                    assetsDirectoryTargeting(
                                        textureCompressionTargeting(ETC1_RGB8))),
                                targetedAssetsDirectory(
                                    "assets/textures#tcf_atc",
                                    assetsDirectoryTargeting(textureCompressionTargeting(ATC)))))
                        .setManifest(
                            androidManifestForAssetModule(
                                "com.test.app", withInstallTimeDelivery())))
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .addSplitDimension(
                        Value.TEXTURE_COMPRESSION_FORMAT,
                        /* negate= */ false,
                        /* stripSuffix= */ false,
                        /* defaultSuffix= */ "etc1")
                    .build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withOptimizationDimensions(TEXTURE_COMPRESSION_FORMAT)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(standaloneApkVariants(result)).hasSize(1);
    assertThat(apkDescriptions(standaloneApkVariants(result))).hasSize(1);
    ApkDescription shard = apkDescriptions(standaloneApkVariants(result)).get(0);

    // Check APK content
    assertThat(apkSetFile).hasFile(shard.getPath());
    try (ZipFile shardZip =
        new ZipFile(extractFromApkSetFile(apkSetFile, shard.getPath(), outputDir))) {
      // Only the requested format texture is included in the standalone APK.
      // Even if suffix stripping is not activated, the standalone APK must only contain one TCF.
      assertThat(shardZip).doesNotHaveFile("assets/textures/untargeted_texture.dat");
      assertThat(shardZip).doesNotHaveFile("assets/textures#tcf_atc/atc_texture.dat");
      assertThat(shardZip).hasFile("assets/textures#tcf_etc1/etc1_texture.dat");
    }
  }

  @Test
  public void buildApksCommand_standalone_textureTargetingWithSuffixStripped() throws Exception {
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
                        .addFile("assets/textures#tcf_atc/texture.dat")
                        .addFile("assets/textures#tcf_etc1/texture.dat")
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
                    .addSplitDimension(
                        Value.TEXTURE_COMPRESSION_FORMAT,
                        /* negate= */ false,
                        /* stripSuffix= */ true,
                        /* defaultSuffix= */ "etc1")
                    .build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withOptimizationDimensions(TEXTURE_COMPRESSION_FORMAT)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(standaloneApkVariants(result)).hasSize(1);
    assertThat(apkDescriptions(standaloneApkVariants(result))).hasSize(1);
    ApkDescription shard = apkDescriptions(standaloneApkVariants(result)).get(0);

    // Check APK content
    assertThat(apkSetFile).hasFile(shard.getPath());
    try (ZipFile shardZip =
        new ZipFile(extractFromApkSetFile(apkSetFile, shard.getPath(), outputDir))) {
      assertThat(shardZip).hasFile("assets/textures/texture.dat");
      assertThat(shardZip).doesNotHaveFile("assets/textures#tcf_atc/texture.dat");
      assertThat(shardZip).doesNotHaveFile("assets/textures#tcf_etc1/texture.dat");
    }

    // Check that targeting was applied to both the APK and the variant
    assertThat(
            standaloneApkVariants(result)
                .get(0)
                .getTargeting()
                .getTextureCompressionFormatTargeting()
                .getValueList())
        .containsExactly(textureCompressionFormat(ETC1_RGB8));
    assertThat(shard.getTargeting().getTextureCompressionFormatTargeting().getValueList())
        .containsExactly(textureCompressionFormat(ETC1_RGB8));
  }

  @Test
  public void buildApksCommand_standalone_mixedTextureTargetingWithSuffixStripped()
      throws Exception {
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
                        /* stripSuffix= */ true,
                        /* defaultSuffix= */ "etc1")
                    .build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withOptimizationDimensions(TEXTURE_COMPRESSION_FORMAT)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(standaloneApkVariants(result)).hasSize(1);
    assertThat(apkDescriptions(standaloneApkVariants(result))).hasSize(1);
    ApkDescription shard = apkDescriptions(standaloneApkVariants(result)).get(0);

    // Check APK content
    assertThat(apkSetFile).hasFile(shard.getPath());
    try (ZipFile shardZip =
        new ZipFile(extractFromApkSetFile(apkSetFile, shard.getPath(), outputDir))) {
      assertThat(shardZip).hasFile("assets/textures/etc1_texture.dat");
      assertThat(shardZip).doesNotHaveFile("assets/textures#tcf_etc1/etc1_texture.dat");
      assertThat(shardZip).doesNotHaveFile("assets/textures/untargeted_texture.dat");
    }

    // Check that targeting was applied to both the APK and the variant
    assertThat(
            standaloneApkVariants(result)
                .get(0)
                .getTargeting()
                .getTextureCompressionFormatTargeting()
                .getValueList())
        .containsExactly(textureCompressionFormat(ETC1_RGB8));
    assertThat(shard.getTargeting().getTextureCompressionFormatTargeting().getValueList())
        .containsExactly(textureCompressionFormat(ETC1_RGB8));
  }

  @Test
  public void buildApksCommand_standalone_mixedTextureTargetingWithFallbackAsDefault()
      throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", builder -> builder.setManifest(androidManifest("com.test.app")))
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
                            androidManifest(
                                "com.test.app",
                                withDelivery(DeliveryType.INSTALL_TIME),
                                withFusingAttribute(true))))
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .addSplitDimension(
                        Value.TEXTURE_COMPRESSION_FORMAT,
                        /* negate= */ false,
                        /* stripSuffix= */ true,
                        /* defaultSuffix= */ "")
                    .build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withOptimizationDimensions(TEXTURE_COMPRESSION_FORMAT)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(standaloneApkVariants(result)).hasSize(1);
    assertThat(apkDescriptions(standaloneApkVariants(result))).hasSize(1);
    ApkDescription shard = apkDescriptions(standaloneApkVariants(result)).get(0);

    // Check APK content.
    assertThat(apkSetFile).hasFile(shard.getPath());
    try (ZipFile shardZip =
        new ZipFile(extractFromApkSetFile(apkSetFile, shard.getPath(), outputDir))) {
      assertThat(shardZip).hasFile("assets/textures/untargeted_texture.dat");
      assertThat(shardZip).doesNotHaveFile("assets/textures#tcf_etc1/etc1_texture.dat");
    }

    // Check that no targeting was applied.
    assertThat(
            Iterables.getOnlyElement(standaloneApkVariants(result))
                .getTargeting()
                .hasTextureCompressionFormatTargeting())
        .isFalse();
    assertThat(shard.getTargeting().hasTextureCompressionFormatTargeting()).isFalse();
  }

  @Test
  public void buildApksCommand_standalone_mixedTextureTargetingInDifferentPacksWithSameFolderName()
      throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", builder -> builder.setManifest(androidManifest("com.test.app")))
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
                            androidManifest(
                                "com.test.app",
                                withDelivery(DeliveryType.INSTALL_TIME),
                                withFusingAttribute(true))))
            .addModule(
                "feature_assets_without_tcf",
                builder ->
                    builder
                        .addFile("assets/textures/another_untargeted_texture.dat")
                        .setAssetsConfig(
                            assets(
                                targetedAssetsDirectory(
                                    "assets/textures",
                                    AssetsDirectoryTargeting.getDefaultInstance())))
                        .setManifest(
                            androidManifest(
                                "com.test.app",
                                withDelivery(DeliveryType.INSTALL_TIME),
                                withFusingAttribute(true))))
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .addSplitDimension(
                        Value.TEXTURE_COMPRESSION_FORMAT,
                        /* negate= */ false,
                        /* stripSuffix= */ true,
                        /* defaultSuffix= */ "")
                    .build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withOptimizationDimensions(TEXTURE_COMPRESSION_FORMAT)
            .build());

    InvalidBundleException e =
        assertThrows(InvalidBundleException.class, () -> buildApksManager.execute());
    assertThat(e)
        .hasMessageThat()
        .contains("Encountered conflicting targeting values while merging assets config.");
  }

  @Test
  public void
      buildApksCommand_standalone_mixedTextureTargetingInDifferentPacksWithSameFolderNameAndNonFallbackDefault()
          throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder.setManifest(androidManifest("com.test.app", withFusingAttribute(true))))
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
                            androidManifest(
                                "com.test.app",
                                withDelivery(DeliveryType.INSTALL_TIME),
                                withFusingAttribute(true))))
            .addModule(
                "feature_assets_without_tcf",
                builder ->
                    builder
                        .addFile("assets/textures/another_untargeted_texture.dat")
                        .setAssetsConfig(
                            assets(
                                targetedAssetsDirectory(
                                    "assets/textures",
                                    AssetsDirectoryTargeting.getDefaultInstance())))
                        .setManifest(
                            androidManifest(
                                "com.test.app",
                                withDelivery(DeliveryType.INSTALL_TIME),
                                withFusingAttribute(true))))
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .addSplitDimension(
                        Value.TEXTURE_COMPRESSION_FORMAT,
                        /* negate= */ false,
                        /* stripSuffix= */ true,
                        /* defaultSuffix= */ "etc1")
                    .build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withOptimizationDimensions(TEXTURE_COMPRESSION_FORMAT)
            .build());

    InvalidBundleException e =
        assertThrows(InvalidBundleException.class, () -> buildApksManager.execute());
    assertThat(e)
        .hasMessageThat()
        .contains("Encountered conflicting targeting values while merging assets config.");
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
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
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
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
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
  public void buildApksCommand_standalone_deviceTierTargetingWithSuffixStripped() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .setManifest(androidManifest("com.test.app"))
                        .setResourceTable(resourceTableWithTestLabel("Test feature")))
            .addModule(
                "device_tier_assets",
                builder ->
                    builder
                        .addFile("assets/img#tier_0/asset_low.dat")
                        .addFile("assets/img#tier_1/asset_high.dat")
                        .setAssetsConfig(
                            assets(
                                targetedAssetsDirectory(
                                    "assets/img#tier_0",
                                    assetsDirectoryTargeting(deviceTierTargeting(0))),
                                targetedAssetsDirectory(
                                    "assets/img#tier_1",
                                    assetsDirectoryTargeting(deviceTierTargeting(1)))))
                        .setManifest(
                            androidManifestForAssetModule(
                                "com.test.app", withInstallTimeDelivery())))
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .addSplitDimension(
                        Value.DEVICE_TIER,
                        /* negate= */ false,
                        /* stripSuffix= */ true,
                        /* defaultSuffix= */ "0")
                    .build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(standaloneApkVariants(result)).hasSize(1);
    assertThat(apkDescriptions(standaloneApkVariants(result))).hasSize(1);
    ApkDescription shard = apkDescriptions(standaloneApkVariants(result)).get(0);

    // Check APK content
    assertThat(apkSetFile).hasFile(shard.getPath());
    try (ZipFile shardZip =
        new ZipFile(extractFromApkSetFile(apkSetFile, shard.getPath(), outputDir))) {
      assertThat(shardZip).hasFile("assets/img/asset_low.dat");
      assertThat(shardZip).doesNotHaveFile("assets/img/asset_high.dat");
      assertThat(shardZip).doesNotHaveFile("assets/img#tier_0/asset_low.dat");
      assertThat(shardZip).doesNotHaveFile("assets/img#tier_1/asset_high.dat");
    }

    // Check that default device tier targeting was applied to the APK
    assertThat(shard.getTargeting().getDeviceTierTargeting().getValueList())
        .containsExactly(Int32Value.of(0));
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
                                targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86)),
                                targetedNativeDirectory(
                                    "lib/x86_64", nativeDirectoryTargeting(X86_64))))
                        .setManifest(androidManifest("com.test.app")))
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withOptimizationDimensions(ABI)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    // 2 standalone APK variants, 1 split APK variant
    assertThat(result.getVariantList()).hasSize(4);

    VariantTargeting lSplitVariantTargeting =
        variantSdkTargeting(L_SDK_VERSION, ImmutableSet.of(LOWEST_SDK_VERSION, M_SDK_VERSION));
    VariantTargeting mSplitVariantTargeting =
        variantSdkTargeting(M_SDK_VERSION, ImmutableSet.of(LOWEST_SDK_VERSION, L_SDK_VERSION));
    VariantTargeting standaloneX86VariantTargeting =
        mergeVariantTargeting(
            variantAbiTargeting(X86, ImmutableSet.of(X86_64)),
            variantSdkTargeting(LOWEST_SDK_VERSION, ImmutableSet.of(L_SDK_VERSION, M_SDK_VERSION)));
    VariantTargeting standaloneX64VariantTargeting =
        mergeVariantTargeting(
            variantAbiTargeting(X86_64, ImmutableSet.of(X86)),
            variantSdkTargeting(LOWEST_SDK_VERSION, ImmutableSet.of(L_SDK_VERSION, M_SDK_VERSION)));

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
  public void buildApksCommand_apkNotificationMessageKeyApexBundle() throws Exception {
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
                        .addFile(APEX_MANIFEST_PATH, APEX_MANIFEST)
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
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ImmutableSet<AbiAlias> x64X86Set = ImmutableSet.of(X86, X86_64);
    ImmutableSet<AbiAlias> x64ArmSet = ImmutableSet.of(ARMEABI_V7A, X86_64);
    ImmutableSet<AbiAlias> x64Set = ImmutableSet.of(X86_64);
    ImmutableSet<AbiAlias> x86ArmSet = ImmutableSet.of(ARMEABI_V7A, X86);
    ImmutableSet<AbiAlias> x86Set = ImmutableSet.of(X86);
    ImmutableSet<AbiAlias> arm8Set = ImmutableSet.of(ARM64_V8A);
    ImmutableSet<AbiAlias> arm7Set = ImmutableSet.of(ARMEABI_V7A);

    ImmutableSet<ImmutableSet<AbiAlias>> allTargeting =
        ImmutableSet.of(arm7Set, x86ArmSet, x64ArmSet, arm8Set, x86Set, x64X86Set, x64Set);
    ApkTargeting x64X86Targeting = apkMultiAbiTargetingFromAllTargeting(x64X86Set, allTargeting);
    ApkTargeting x64ArmTargeting = apkMultiAbiTargetingFromAllTargeting(x64ArmSet, allTargeting);
    ApkTargeting x64Targeting = apkMultiAbiTargetingFromAllTargeting(x64Set, allTargeting);
    ApkTargeting x86ArmTargeting = apkMultiAbiTargetingFromAllTargeting(x86ArmSet, allTargeting);
    ApkTargeting x86Targeting = apkMultiAbiTargetingFromAllTargeting(x86Set, allTargeting);
    ApkTargeting arm8Targeting = apkMultiAbiTargetingFromAllTargeting(arm8Set, allTargeting);
    ApkTargeting arm7Targeting = apkMultiAbiTargetingFromAllTargeting(arm7Set, allTargeting);

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
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
  public void buildApksCommand_apkNotificationMessageKeyApexBundle_previewTargetSdk()
      throws Exception {
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
                        .addFile(APEX_MANIFEST_PATH, APEX_MANIFEST)
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
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ImmutableSet<AbiAlias> x64Set = ImmutableSet.of(X86_64);
    ImmutableSet<AbiAlias> x86Set = ImmutableSet.of(X86);
    ImmutableSet<AbiAlias> arm8Set = ImmutableSet.of(ARM64_V8A);
    ImmutableSet<AbiAlias> arm7Set = ImmutableSet.of(ARMEABI_V7A);

    ImmutableSet<ImmutableSet<AbiAlias>> allTargeting =
        ImmutableSet.of(arm7Set, arm8Set, x86Set, x64Set);
    ApkTargeting x64Targeting = apkMultiAbiTargetingFromAllTargeting(x64Set, allTargeting);
    ApkTargeting x86Targeting = apkMultiAbiTargetingFromAllTargeting(x86Set, allTargeting);
    ApkTargeting arm8Targeting = apkMultiAbiTargetingFromAllTargeting(arm8Set, allTargeting);
    ApkTargeting arm7Targeting = apkMultiAbiTargetingFromAllTargeting(arm7Set, allTargeting);

    ZipFile apkSetFile = new ZipFile(outputFilePath.toFile());
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
  public void buildApksCommand_apkNotificationMessageKeyApexBundle_hasRightSuffix()
      throws Exception {
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
                        .addFile(APEX_MANIFEST_PATH, APEX_MANIFEST)
                        .addFile("apex/x86_64.img")
                        .addFile("apex/x86.img")
                        .addFile("apex/arm64-v8a.img")
                        .addFile("apex/armeabi-v7a.img")
                        .addFile("apex/arm64-v8a.armeabi-v7a.img")
                        .setApexConfig(apexConfig)
                        .setManifest(androidManifest("com.test.app")))
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    BuildApksResult result =
        extractTocFromApkSetFile(openZipFile(outputFilePath.toFile()), outputDir);
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
            "standalones/standalone-armeabi_v7a.arm64_v8a.apex");
  }

  @DataPoints("bundleVersion")
  public static final ImmutableSet<Version> BUNDLE_VERSION =
      ImmutableSet.of(Version.of("0.10.1"), Version.of("0.10.2"));

  @Test
  @Theory
  public void buildApksCommand_featureAndAssetModules_generatesAssetSlices(
      @FromDataPoints("bundleVersion") Version bundleVersion) throws Exception {
    AppBundle appBundle =
        createAppBundleBuilder(bundleVersion)
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("dex/classes.dex")
                        .setManifest(
                            androidManifest(
                                "com.test.app",
                                withMinSdkVersion(15),
                                withMaxSdkVersion(27),
                                withInstant(true)))
                        .setResourceTable(resourceTableWithTestLabel("Test feature")))
            .addModule(
                "asset_module1",
                builder ->
                    builder
                        .setManifest(
                            androidManifestForAssetModule(
                                "com.test.app", withInstallTimeDelivery()))
                        .addFile("assets/images/image.jpg"))
            .addModule(
                "asset_module2",
                builder ->
                    builder
                        .setManifest(
                            androidManifestForAssetModule(
                                "com.test.app",
                                withOnDemandDelivery(),
                                withInstantOnDemandDelivery()))
                        .addFile("assets/images/image2.jpg"))
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    // Variants
    ImmutableList<Variant> variants = splitApkVariants(result);
    assertThat(variants).hasSize(1);
    Variant splitApkVariant = variants.get(0);
    List<ApkSet> apks = splitApkVariant.getApkSetList();
    assertThat(apks).hasSize(1);

    ApkSet baseSplits = apks.get(0);
    assertThat(baseSplits.getModuleMetadata().getName()).isEqualTo("base");
    assertThat(baseSplits.getModuleMetadata().getDeliveryType())
        .isEqualTo(DeliveryType.INSTALL_TIME);
    assertThat(baseSplits.getApkDescriptionList()).hasSize(1);
    assertThat(apkSetFile).hasFile(baseSplits.getApkDescription(0).getPath());

    // Asset Slices
    List<AssetSliceSet> sliceSets = result.getAssetSliceSetList();
    assertThat(sliceSets).hasSize(2);

    for (AssetSliceSet slice : sliceSets) {
      assertThat(slice.getAssetModuleMetadata().hasInstantMetadata()).isTrue();
      assertThat(slice.getApkDescriptionList()).hasSize(1);
      assertThat(apkSetFile).hasFile(slice.getApkDescription(0).getPath());

      ApkDescription apkDescription = slice.getApkDescription(0);
      assertThat(apkDescription.getPath())
          .isEqualTo(
              String.format(
                  "asset-slices/%s-master.apk", slice.getAssetModuleMetadata().getName()));
      assertThat(apkDescription.hasAssetSliceMetadata()).isTrue();
      assertThat(apkDescription.getAssetSliceMetadata().getIsMasterSplit()).isTrue();
    }
    ImmutableMap<String, AssetSliceSet> sliceSetsByName =
        Maps.uniqueIndex(sliceSets, set -> set.getAssetModuleMetadata().getName());

    assertThat(
            sliceSetsByName
                .get("asset_module1")
                .getAssetModuleMetadata()
                .getInstantMetadata()
                .getIsInstant())
        .isFalse();
    assertThat(sliceSetsByName.get("asset_module1").getApkDescription(0).getTargeting())
        .isEqualTo(
            ApkTargeting.newBuilder()
                .setSdkVersionTargeting(
                    SdkVersionTargeting.newBuilder()
                        .addValue(
                            SdkVersion.newBuilder()
                                .setMin(Int32Value.newBuilder().setValue(ANDROID_L_API_VERSION))))
                .build());
    assertThat(sliceSetsByName.get("asset_module2").getApkDescription(0).getTargeting())
        .isEqualToDefaultInstance();
    InstantMetadata.Builder instantMetadata = InstantMetadata.newBuilder().setIsInstant(true);
    instantMetadata.setDeliveryType(DeliveryType.ON_DEMAND);
    assertThat(sliceSetsByName.get("asset_module2").getAssetModuleMetadata().getInstantMetadata())
        .isEqualTo(instantMetadata.build());
  }

  @Test
  @Theory
  public void buildApksCommand_assetOnly(@FromDataPoints("bundleVersion") Version bundleVersion)
      throws Exception {
    AssetModulesConfig assetModulesConfig =
        AssetModulesConfig.newBuilder().setAssetVersionTag("qwe").addAppVersion(11).build();
    AssetModulesInfo assetModulesInfo =
        AssetModulesInfo.newBuilder().setAssetVersionTag("qwe").addAppVersion(11).build();
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "asset_module1",
                builder ->
                    builder
                        .setManifest(
                            androidManifestForAssetModule("com.test.app", withOnDemandDelivery()))
                        .addFile("assets/images/image.jpg"))
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withBundleConfig(
                BundleConfig.newBuilder()
                    .setAssetModulesConfig(assetModulesConfig)
                    .setType(BundleType.ASSET_ONLY))
            .withBundletoolVersion(bundleVersion.toString())
            .withOutputPath(outputFilePath)
            .withCustomBuildApksCommandSetter(
                command -> command.setAssetModulesVersionOverride(123L))
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    // Variants
    assertThat(splitApkVariants(result)).isEmpty();

    // Asset Slices
    List<AssetSliceSet> sliceSets = result.getAssetSliceSetList();
    assertThat(sliceSets).hasSize(1);
    AssetSliceSet assetSliceSet = sliceSets.get(0);

    assertThat(assetSliceSet.getAssetModuleMetadata().hasInstantMetadata()).isTrue();
    assertThat(assetSliceSet.getApkDescriptionList()).hasSize(1);
    assertThat(apkSetFile).hasFile(assetSliceSet.getApkDescription(0).getPath());

    ApkDescription apkDescription = assetSliceSet.getApkDescription(0);
    assertThat(apkDescription.getPath())
        .isEqualTo(
            String.format(
                "asset-slices/%s-master.apk", assetSliceSet.getAssetModuleMetadata().getName()));
    assertThat(apkDescription.hasAssetSliceMetadata()).isTrue();
    assertThat(apkDescription.getAssetSliceMetadata().getIsMasterSplit()).isTrue();

    assertThat(result.getAssetModulesInfo()).isEqualTo(assetModulesInfo);
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
                                targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86)),
                                targetedNativeDirectory(
                                    "lib/x86_64", nativeDirectoryTargeting(X86_64))))
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

    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withExecutorService(
                MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(threadCount)))
            .withOptimizationDimensions(ABI, LANGUAGE)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    final String manifest = "AndroidManifest.xml";

    // Validate split APKs.
    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);
    assertThat(splitApkVariants).hasSize(2);
    for (Variant splitApkVariant : splitApkVariants) {
      ImmutableMap<String, List<ApkDescription>> splitApksByModule =
          splitApkVariant.getApkSetList().stream()
              .collect(
                  toImmutableMap(
                      apkSet -> apkSet.getModuleMetadata().getName(),
                      ApkSet::getApkDescriptionList));
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
              "res/xml/splits0.xml", "resources.arsc", "assets/file.txt", "classes.dex", manifest);
      assertThat(filesInApks(splitApksByModule.get("abi_feature"), apkSetFile))
          .isEqualTo(
              ImmutableMultiset.builder()
                  .add("lib/x86/libsome.so")
                  .add("lib/x86_64/libsome.so")
                  .addCopies(manifest, 3)
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
                  .build());
    }
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
                .build());
  }


  @Test
  public void mergeInstallTimeModulesByDefault() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder.addFile("dex/classes.dex").setManifest(androidManifest("com.test.app")))
            .addModule(
                "abi_feature",
                builder ->
                    builder
                        .addFile("lib/x86/libsome.so")
                        .addFile("lib/x86_64/libsome.so")
                        .setNativeConfig(
                            nativeLibraries(
                                targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86)),
                                targetedNativeDirectory(
                                    "lib/x86_64", nativeDirectoryTargeting(X86_64))))
                        .setManifest(
                            androidManifest(
                                "com.test.app",
                                withInstallTimeDelivery(),
                                withFusingAttribute(true))))
            .setBundleConfig(BundleConfigBuilder.create().setVersion("1.0.0").build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);
    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);
    assertThat(splitApkVariants).isNotEmpty();
    assertThat(result.getPermanentlyFusedModulesList())
        .containsExactly(PermanentlyFusedModule.newBuilder().setName("abi_feature").build());

    for (Variant splitApkVariant : splitApkVariants(result)) {
      assertThat(splitApkVariant.getApkSetList())
          .comparingElementsUsing(
              Correspondence.from(
                  (ApkSet apkSet, String moduleName) ->
                      apkSet != null && apkSet.getModuleMetadata().getName().equals(moduleName),
                  "equals"))
          .containsExactly("base");
    }
  }

  @Test
  public void mergeInstallTimeDisabled() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder.addFile("dex/classes.dex").setManifest(androidManifest("com.test.app")))
            .addModule(
                "abi_feature",
                builder ->
                    builder
                        .addFile("lib/x86/libsome.so")
                        .addFile("lib/x86_64/libsome.so")
                        .setNativeConfig(
                            nativeLibraries(
                                targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86)),
                                targetedNativeDirectory(
                                    "lib/x86_64", nativeDirectoryTargeting(X86_64))))
                        .setManifest(
                            androidManifest(
                                "com.test.app",
                                withInstallTimeDelivery(),
                                withFusingAttribute(true),
                                withInstallTimeRemovableElement(true))))
            .setBundleConfig(BundleConfigBuilder.create().setVersion("1.0.0").build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);
    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);
    assertThat(splitApkVariants).isNotEmpty();
    assertThat(result.getPermanentlyFusedModulesList()).isEmpty();

    for (Variant splitApkVariant : splitApkVariants(result)) {
      assertThat(splitApkVariant.getApkSetList())
          .comparingElementsUsing(
              Correspondence.from(
                  (ApkSet apkSet, String moduleName) ->
                      apkSet != null && apkSet.getModuleMetadata().getName().equals(moduleName),
                  "equals"))
          .containsExactly("base", "abi_feature");
    }
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
                                targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86)))))
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withOptimizationDimensions(ABI)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableMap<VariantTargeting, Variant> splitVariantsByTargeting =
        Maps.uniqueIndex(splitApkVariants(result), Variant::getTargeting);

    VariantTargeting lSplitVariantTargeting =
        variantSdkTargeting(
            L_SDK_VERSION, ImmutableSet.of(LOWEST_SDK_VERSION, M_SDK_VERSION, S_SDK_VERSION));
    VariantTargeting mSplitVariantTargeting =
        variantSdkTargeting(
            M_SDK_VERSION, ImmutableSet.of(LOWEST_SDK_VERSION, L_SDK_VERSION, S_SDK_VERSION));
    VariantTargeting sSplitVariantTargeting =
        variantSdkTargeting(
            S_SDK_VERSION, ImmutableSet.of(LOWEST_SDK_VERSION, L_SDK_VERSION, M_SDK_VERSION));

    assertThat(splitVariantsByTargeting.keySet())
        .containsExactly(lSplitVariantTargeting, mSplitVariantTargeting, sSplitVariantTargeting);
    assertThat(
            ImmutableSet.builder()
                .addAll(apkNamesInVariant(splitVariantsByTargeting.get(lSplitVariantTargeting)))
                .addAll(apkNamesInVariant(splitVariantsByTargeting.get(mSplitVariantTargeting)))
                .addAll(apkNamesInVariant(splitVariantsByTargeting.get(sSplitVariantTargeting)))
                .build())
        // for uncompressed dex variant (sdk 31) we don't have "base-x86_3.apk" because it is the
        // same as "base-x86_2.apk" with  uncompressed native libs
        .containsExactly(
            "base-master.apk",
            "base-x86.apk",
            "base-master_2.apk",
            "base-x86_2.apk",
            "base-master_3.apk");
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
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(splitApkVariants(result)).hasSize(2);
    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);

    Variant lVariant =
        splitApkVariants.stream()
            .filter(
                variant ->
                    variant
                        .getTargeting()
                        .getSdkVersionTargeting()
                        .getValueList()
                        .contains(L_SDK_VERSION))
            .findFirst()
            .get();
    Variant sVariant =
        splitApkVariants.stream()
            .filter(
                variant ->
                    variant
                        .getTargeting()
                        .getSdkVersionTargeting()
                        .getValueList()
                        .contains(S_SDK_VERSION))
            .findFirst()
            .get();
    assertThat(lVariant.getApkSetList()).hasSize(1);
    assertThat(apkNamesInVariant(lVariant))
        .containsExactly("base-master.apk", "base-es.apk", "base-other_lang.apk");
    assertThat(sVariant.getApkSetList()).hasSize(1);
    assertThat(apkNamesInVariant(sVariant))
        .containsExactly("base-master_2.apk", "base-es.apk", "base-other_lang.apk");
  }

  @Test
  public void splits_assetTextureCompressionFormatWithSuffixStripped() throws Exception {
    Path bundlePath =
        createAppBundleWithBaseModuleWithTextureTargeting(
            /* tcfSplittingEnabled= */ true, /* stripTargetingSuffixEnabled= */ true);

    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withBundlePath(bundlePath)
            .withOutputPath(outputFilePath)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);
    ImmutableList<ApkDescription> splitApks = apkDescriptions(splitApkVariants);

    assertThat(splitApkVariants(result)).hasSize(1);
    Variant splitApkVariant = splitApkVariants(result).get(0);

    assertThat(splitApkVariant.getApkSetList()).hasSize(1);
    ImmutableList<ApkDescription> tcfSplits =
        splitApks.stream()
            .filter(apkDesc -> apkDesc.getTargeting().hasTextureCompressionFormatTargeting())
            .collect(toImmutableList());
    assertThat(apkNamesInApkDescriptions(tcfSplits))
        .containsExactly("base-atc.apk", "base-etc1_rgb8.apk");

    for (ApkDescription split : tcfSplits) {
      TextureCompressionFormatTargeting textureFormatTargeting =
          split.getTargeting().getTextureCompressionFormatTargeting();
      assertThat(textureFormatTargeting.getValueList()).hasSize(1);
      TextureCompressionFormat format = textureFormatTargeting.getValueList().get(0);
      Set<String> files = filesInApk(split, apkSetFile);
      switch (format.getAlias()) {
        case ATC:
          assertThat(files).contains("assets/textures/texture.dat");
          break;
        case ETC1_RGB8:
          assertThat(files).contains("assets/textures/texture.dat");
          break;
        default:
          fail("Unexpected texture compression format");
      }
    }
  }

  @Test
  public void splits_assetMixedTextureCompressionFormat() throws Exception {
    // Create a bundle with assets containing both ETC1 textures and textures without
    // targeting specified.
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("assets/textures/untargeted_texture.dat")
                        .addFile("assets/textures#tcf_etc1/etc1_texture.dat")
                        .setManifest(androidManifest("com.test.app"))
                        .setAssetsConfig(
                            assets(
                                targetedAssetsDirectory(
                                    "assets/textures",
                                    assetsDirectoryTargeting(
                                        alternativeTextureCompressionTargeting(ETC1_RGB8))),
                                targetedAssetsDirectory(
                                    "assets/textures#tcf_etc1",
                                    assetsDirectoryTargeting(
                                        textureCompressionTargeting(ETC1_RGB8))))))
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .addSplitDimension(Value.TEXTURE_COMPRESSION_FORMAT, /* negate= */ false)
                    .build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);
    ImmutableList<ApkDescription> splitApks = apkDescriptions(splitApkVariants);

    assertThat(splitApkVariants(result)).hasSize(1);
    Variant splitApkVariant = splitApkVariants(result).get(0);

    // Check that apks for ETC1 and "Other TCF" have been created
    assertThat(splitApkVariant.getApkSetList()).hasSize(1);
    ImmutableList<ApkDescription> tcfSplits =
        splitApks.stream()
            .filter(apkDesc -> apkDesc.getTargeting().hasTextureCompressionFormatTargeting())
            .collect(toImmutableList());
    assertThat(apkNamesInApkDescriptions(tcfSplits))
        .containsExactly("base-other_tcf.apk", "base-etc1_rgb8.apk");

    // Check the content of the apks
    for (ApkDescription split : tcfSplits) {
      TextureCompressionFormatTargeting textureFormatTargeting =
          split.getTargeting().getTextureCompressionFormatTargeting();

      Set<String> files = filesInApk(split, apkSetFile);

      if (textureFormatTargeting.getValueList().isEmpty()) {
        // The "Other TCF" split contains the untargeted texture only.
        assertThat(files).contains("assets/textures/untargeted_texture.dat");
      } else {
        // The "ETC1" split contains the ETC1 texture only.
        TextureCompressionFormat format = textureFormatTargeting.getValueList().get(0);
        assertThat(format.getAlias()).isEqualTo(ETC1_RGB8);
        assertThat(files).contains("assets/textures#tcf_etc1/etc1_texture.dat");
      }
    }
  }

  @Test
  public void splits_assetMixedTextureTargetingWithSuffixStripped_featureModule() throws Exception {
    // Create a bundle with assets containing both ETC1 textures and textures without
    // targeting specified.
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
                        /* stripSuffix= */ true,
                        /* defaultSuffix= */ "etc1")
                    .build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);
    ImmutableList<ApkDescription> splitApks = apkDescriptions(splitApkVariants);

    assertThat(splitApkVariants(result)).hasSize(1);
    Variant splitApkVariant = splitApkVariants(result).get(0);
    assertThat(splitApkVariant.getApkSetList()).hasSize(2);

    // Check that apks for ETC1 and "Other TCF" have been created
    ImmutableList<ApkDescription> tcfSplits =
        splitApks.stream()
            .filter(apkDesc -> apkDesc.getTargeting().hasTextureCompressionFormatTargeting())
            .collect(toImmutableList());
    assertThat(apkNamesInApkDescriptions(tcfSplits))
        .containsExactly("feature_tcf_assets-other_tcf.apk", "feature_tcf_assets-etc1_rgb8.apk");

    // Check the content of the apks
    for (ApkDescription split : tcfSplits) {
      TextureCompressionFormatTargeting textureFormatTargeting =
          split.getTargeting().getTextureCompressionFormatTargeting();

      Set<String> files = filesInApk(split, apkSetFile);

      if (textureFormatTargeting.getValueList().isEmpty()) {
        // The "Other TCF" split contains the untargeted texture only.
        assertThat(files).contains("assets/textures/untargeted_texture.dat");
        assertThat(files).doesNotContain("assets/textures#tcf_etc1/etc1_texture.dat");
      } else {
        // The "ETC1" split contains the ETC1 texture only.
        TextureCompressionFormat format = textureFormatTargeting.getValueList().get(0);
        assertThat(format.getAlias()).isEqualTo(ETC1_RGB8);

        // Suffix stripping was enabled, so "textures#tcf_etc1" folder is now renamed to "textures".
        assertThat(files).contains("assets/textures/etc1_texture.dat");
        assertThat(files).doesNotContain("assets/textures#tcf_etc1/etc1_texture.dat");
        assertThat(files).doesNotContain("assets/textures/untargeted_texture.dat");
      }
    }
  }

  @Test
  public void splits_assetMixedTextureTargetingWithSuffixStripped_assetModule() throws Exception {
    // Create a bundle with assets containing both ETC1 textures and textures without
    // targeting specified.
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", builder -> builder.setManifest(androidManifest("com.test.app")))
            .addModule(
                "tcf_assets",
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
                        .setManifest(androidManifestForAssetModule("com.test.app")))
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .addSplitDimension(
                        Value.TEXTURE_COMPRESSION_FORMAT,
                        /* negate= */ false,
                        /* stripSuffix= */ true,
                        /* defaultSuffix= */ "etc1")
                    .build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(result.getAssetSliceSetList()).hasSize(1);
    List<ApkDescription> assetApks = result.getAssetSliceSet(0).getApkDescriptionList();
    // Check that apks for ETC1 and "Other TCF" have been created
    ImmutableList<ApkDescription> tcfSplits =
        assetApks.stream()
            .filter(apkDesc -> apkDesc.getTargeting().hasTextureCompressionFormatTargeting())
            .collect(toImmutableList());
    assertThat(apkNamesInApkDescriptions(tcfSplits))
        .containsExactly("tcf_assets-other_tcf.apk", "tcf_assets-etc1_rgb8.apk");

    // Check the content of the apks
    for (ApkDescription split : tcfSplits) {
      TextureCompressionFormatTargeting textureFormatTargeting =
          split.getTargeting().getTextureCompressionFormatTargeting();

      Set<String> files = filesInApk(split, apkSetFile);

      if (textureFormatTargeting.getValueList().isEmpty()) {
        // The "Other TCF" split contains the untargeted texture only.
        assertThat(files).contains("assets/textures/untargeted_texture.dat");
        assertThat(files).doesNotContain("assets/textures#tcf_etc1/etc1_texture.dat");
      } else {
        // The "ETC1" split contains the ETC1 texture only.
        TextureCompressionFormat format = textureFormatTargeting.getValueList().get(0);
        assertThat(format.getAlias()).isEqualTo(ETC1_RGB8);

        // Suffix stripping was enabled, so "textures#tcf_etc1" folder is now renamed to "textures".
        assertThat(files).contains("assets/textures/etc1_texture.dat");
        assertThat(files).doesNotContain("assets/textures#tcf_etc1/etc1_texture.dat");
        assertThat(files).doesNotContain("assets/textures/untargeted_texture.dat");
      }
    }
  }

  @Test
  public void splits_assetTextureCompressionFormatWithoutSuffixStripped() throws Exception {
    Path bundlePath =
        createAppBundleWithBaseModuleWithTextureTargeting(
            /* tcfSplittingEnabled= */ true, /* stripTargetingSuffixEnabled= */ false);

    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withBundlePath(bundlePath)
            .withOutputPath(outputFilePath)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);
    ImmutableList<ApkDescription> splitApks = apkDescriptions(splitApkVariants);

    assertThat(splitApkVariants(result)).hasSize(1);
    Variant splitApkVariant = splitApkVariants(result).get(0);

    assertThat(splitApkVariant.getApkSetList()).hasSize(1);
    ImmutableList<ApkDescription> tcfSplits =
        splitApks.stream()
            .filter(apkDesc -> apkDesc.getTargeting().hasTextureCompressionFormatTargeting())
            .collect(toImmutableList());
    assertThat(apkNamesInApkDescriptions(tcfSplits))
        .containsExactly("base-atc.apk", "base-etc1_rgb8.apk");

    for (ApkDescription split : tcfSplits) {
      TextureCompressionFormatTargeting textureFormatTargeting =
          split.getTargeting().getTextureCompressionFormatTargeting();
      assertThat(textureFormatTargeting.getValueList()).hasSize(1);
      TextureCompressionFormat format = textureFormatTargeting.getValueList().get(0);
      Set<String> files = filesInApk(split, apkSetFile);
      switch (format.getAlias()) {
        case ATC:
          assertThat(files).contains("assets/textures#tcf_atc/texture.dat");
          break;
        case ETC1_RGB8:
          assertThat(files).contains("assets/textures#tcf_etc1/texture.dat");
          break;
        default:
          fail("Unexpected texture compression format");
      }
    }
  }

  @Test
  public void splits_assetTextureCompressionFormatDisabled() throws Exception {
    Path bundlePath = createAppBundleWithBaseModuleWithTextureTargeting(false, false);

    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withBundlePath(bundlePath)
            .withOutputPath(outputFilePath)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);
    ImmutableList<ApkDescription> splitApks = apkDescriptions(splitApkVariants);

    assertThat(splitApkVariants(result)).hasSize(1);
    Variant splitApkVariant = splitApkVariants(result).get(0);

    assertThat(splitApkVariant.getApkSetList()).hasSize(1);
    assertThat(apkNamesInVariant(splitApkVariant)).containsExactly("base-master.apk");

    ImmutableList<ApkDescription> tcfSplits =
        splitApks.stream()
            .filter(apkDesc -> apkDesc.getTargeting().hasTextureCompressionFormatTargeting())
            .collect(toImmutableList());
    assertThat(tcfSplits).isEmpty();

    ApkDescription masterSplit =
        splitApks.stream()
            .filter(apkDesc -> apkDesc.getSplitApkMetadata().getIsMasterSplit())
            .collect(onlyElement());

    assertThat(filesInApk(masterSplit, apkSetFile))
        .containsAtLeast(
            "assets/textures#tcf_atc/texture.dat", "assets/textures#tcf_etc1/texture.dat");
  }

  private Path createAppBundleWithBaseModuleWithTextureTargeting(
      boolean tcfSplittingEnabled, boolean stripTargetingSuffixEnabled) throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("assets/textures#tcf_atc/texture.dat")
                        .addFile("assets/textures#tcf_etc1/texture.dat")
                        .setManifest(androidManifest("com.test.app"))
                        .setAssetsConfig(
                            assets(
                                targetedAssetsDirectory(
                                    "assets/textures#tcf_atc",
                                    assetsDirectoryTargeting(textureCompressionTargeting(ATC))),
                                targetedAssetsDirectory(
                                    "assets/textures#tcf_etc1",
                                    assetsDirectoryTargeting(
                                        textureCompressionTargeting(ETC1_RGB8))))))
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .addSplitDimension(
                        Value.TEXTURE_COMPRESSION_FORMAT,
                        /* negate= */ !tcfSplittingEnabled,
                        /* stripSuffix= */ stripTargetingSuffixEnabled,
                        /* defaultSuffix= */ "etc1")
                    .build())
            .build();
    return createAndStoreBundle(appBundle);
  }

  @Test
  public void deviceTieredAssets_inBaseModule() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("assets/images#tier_0/image.jpg")
                        .addFile("assets/images#tier_1/image.jpg")
                        .setManifest(androidManifest("com.test.app"))
                        .setAssetsConfig(
                            assets(
                                targetedAssetsDirectory(
                                    "assets/images#tier_0",
                                    assetsDirectoryTargeting(
                                        deviceTierTargeting(
                                            /* value= */ 0,
                                            /* alternatives= */ ImmutableList.of(1)))),
                                targetedAssetsDirectory(
                                    "assets/images#tier_1",
                                    assetsDirectoryTargeting(
                                        deviceTierTargeting(
                                            /* value= */ 1,
                                            /* alternatives= */ ImmutableList.of(0)))))))
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .addSplitDimension(
                        Value.DEVICE_TIER,
                        /* negate= */ false,
                        /* stripSuffix= */ false,
                        /* defaultSuffix= */ "0")
                    .build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);
    ImmutableList<ApkDescription> splitApks = apkDescriptions(splitApkVariants);

    assertThat(splitApkVariants(result)).hasSize(1);
    Variant splitApkVariant = splitApkVariants(result).get(0);

    // Check that apks for tier 0 and 1 have been created
    assertThat(splitApkVariant.getApkSetList()).hasSize(1);
    ImmutableList<ApkDescription> deviceTierSplits =
        splitApks.stream()
            .filter(apkDesc -> apkDesc.getTargeting().hasDeviceTierTargeting())
            .collect(toImmutableList());
    assertThat(apkNamesInApkDescriptions(deviceTierSplits))
        .containsExactly("base-tier_0.apk", "base-tier_1.apk");

    // Check the content of the APKs
    ImmutableMap<DeviceTierTargeting, ApkDescription> deviceTierSplitsByTargeting =
        Maps.uniqueIndex(deviceTierSplits, apk -> apk.getTargeting().getDeviceTierTargeting());
    ApkDescription lowTierSplit =
        deviceTierSplitsByTargeting.get(
            deviceTierTargeting(/* value= */ 0, /* alternatives= */ ImmutableList.of(1)));
    assertThat(ZipPath.create(lowTierSplit.getPath()).getFileName().toString())
        .isEqualTo("base-tier_0.apk");
    assertThat(filesInApk(lowTierSplit, apkSetFile)).contains("assets/images#tier_0/image.jpg");

    ApkDescription mediumTierSplit =
        deviceTierSplitsByTargeting.get(
            deviceTierTargeting(/* value= */ 1, /* alternatives= */ ImmutableList.of(0)));
    assertThat(ZipPath.create(mediumTierSplit.getPath()).getFileName().toString())
        .isEqualTo("base-tier_1.apk");
    assertThat(filesInApk(mediumTierSplit, apkSetFile)).contains("assets/images#tier_1/image.jpg");

    assertThat(result.getDefaultTargetingValueList())
        .containsExactly(
            DefaultTargetingValue.newBuilder()
                .setDimension(Value.DEVICE_TIER)
                .setDefaultValue("0")
                .build());
  }

  @Test
  public void deviceTieredAssets_inAssetModule() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", builder -> builder.setManifest(androidManifest("com.test.app")))
            .addModule(
                "assetmodule",
                builder ->
                    builder
                        .addFile("assets/images#tier_0/image.jpg")
                        .addFile("assets/images#tier_1/image.jpg")
                        .setManifest(androidManifestForAssetModule("com.test.app"))
                        .setAssetsConfig(
                            assets(
                                targetedAssetsDirectory(
                                    "assets/images#tier_0",
                                    assetsDirectoryTargeting(
                                        deviceTierTargeting(
                                            /* value= */ 0,
                                            /* alternatives= */ ImmutableList.of(1)))),
                                targetedAssetsDirectory(
                                    "assets/images#tier_1",
                                    assetsDirectoryTargeting(
                                        deviceTierTargeting(
                                            /* value= */ 1,
                                            /* alternatives= */ ImmutableList.of(0)))))))
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .addSplitDimension(
                        Value.DEVICE_TIER,
                        /* negate= */ false,
                        /* stripSuffix= */ false,
                        /* defaultSuffix= */ "0")
                    .build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(result.getAssetSliceSetList()).hasSize(1);
    List<ApkDescription> assetSlices = result.getAssetSliceSet(0).getApkDescriptionList();

    // Check that apks for tier 0 and 1 have been created
    ImmutableList<ApkDescription> deviceTierSplits =
        assetSlices.stream()
            .filter(apkDesc -> apkDesc.getTargeting().hasDeviceTierTargeting())
            .collect(toImmutableList());
    assertThat(apkNamesInApkDescriptions(deviceTierSplits))
        .containsExactly("assetmodule-tier_0.apk", "assetmodule-tier_1.apk");

    // Check the content of the APKs
    ImmutableMap<DeviceTierTargeting, ApkDescription> deviceTierSplitsByTargeting =
        Maps.uniqueIndex(deviceTierSplits, apk -> apk.getTargeting().getDeviceTierTargeting());

    ApkDescription lowTierSplit =
        deviceTierSplitsByTargeting.get(
            deviceTierTargeting(/* value= */ 0, /* alternatives= */ ImmutableList.of(1)));
    assertThat(ZipPath.create(lowTierSplit.getPath()).getFileName().toString())
        .isEqualTo("assetmodule-tier_0.apk");
    assertThat(filesInApk(lowTierSplit, apkSetFile)).contains("assets/images#tier_0/image.jpg");

    ApkDescription mediumTierSplit =
        deviceTierSplitsByTargeting.get(
            deviceTierTargeting(/* value= */ 1, /* alternatives= */ ImmutableList.of(0)));
    assertThat(ZipPath.create(mediumTierSplit.getPath()).getFileName().toString())
        .isEqualTo("assetmodule-tier_1.apk");
    assertThat(filesInApk(mediumTierSplit, apkSetFile)).contains("assets/images#tier_1/image.jpg");
  }

  @Test
  public void deviceTieredAssets_withDeviceSpec_deviceTierSet() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("assets/images#tier_0/image.jpg")
                        .addFile("assets/images#tier_1/image.jpg")
                        .setManifest(androidManifest("com.test.app"))
                        .setAssetsConfig(
                            assets(
                                targetedAssetsDirectory(
                                    "assets/images#tier_0",
                                    assetsDirectoryTargeting(
                                        deviceTierTargeting(
                                            /* value= */ 0,
                                            /* alternatives= */ ImmutableList.of(1)))),
                                targetedAssetsDirectory(
                                    "assets/images#tier_1",
                                    assetsDirectoryTargeting(
                                        deviceTierTargeting(
                                            /* value= */ 1,
                                            /* alternatives= */ ImmutableList.of(0)))))))
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .addSplitDimension(
                        Value.DEVICE_TIER,
                        /* negate= */ false,
                        /* stripSuffix= */ false,
                        /* defaultSuffix= */ "0")
                    .build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withDeviceSpec(
                mergeSpecs(
                    sdkVersion(29),
                    abis("x86"),
                    density(DensityAlias.MDPI),
                    locales("en-US"),
                    deviceTier(1)))
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);
    ImmutableList<ApkDescription> splitApks = apkDescriptions(splitApkVariants);

    // Check that only an APK for tier 1 has been created, not for 0
    ImmutableList<ApkDescription> deviceTierSplits =
        splitApks.stream()
            .filter(apkDesc -> apkDesc.getTargeting().hasDeviceTierTargeting())
            .collect(toImmutableList());
    assertThat(apkNamesInApkDescriptions(deviceTierSplits)).containsExactly("base-tier_1.apk");

    // Check the content of the APK
    ImmutableMap<DeviceTierTargeting, ApkDescription> deviceTierSplitsByTargeting =
        Maps.uniqueIndex(deviceTierSplits, apk -> apk.getTargeting().getDeviceTierTargeting());

    ApkDescription mediumTierSplit =
        deviceTierSplitsByTargeting.get(
            deviceTierTargeting(/* value= */ 1, /* alternatives= */ ImmutableList.of(0)));
    assertThat(filesInApk(mediumTierSplit, apkSetFile)).contains("assets/images#tier_1/image.jpg");
  }

  @Test
  public void deviceTieredAssets_withDeviceSpec_deviceTierNotSet_defaultIsUsed() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("assets/images#tier_0/image.jpg")
                        .addFile("assets/images#tier_1/image.jpg")
                        .setManifest(androidManifest("com.test.app"))
                        .setAssetsConfig(
                            assets(
                                targetedAssetsDirectory(
                                    "assets/images#tier_0",
                                    assetsDirectoryTargeting(
                                        deviceTierTargeting(
                                            /* value= */ 0,
                                            /* alternatives= */ ImmutableList.of(1)))),
                                targetedAssetsDirectory(
                                    "assets/images#tier_1",
                                    assetsDirectoryTargeting(
                                        deviceTierTargeting(
                                            /* value= */ 1,
                                            /* alternatives= */ ImmutableList.of(0)))))))
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .addSplitDimension(
                        Value.DEVICE_TIER,
                        /* negate= */ false,
                        /* stripSuffix= */ false,
                        /* defaultSuffix= */ "0")
                    .build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withDeviceSpec(
                mergeSpecs(
                    sdkVersion(29), abis("x86"), density(DensityAlias.MDPI), locales("en-US")))
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);
    ImmutableList<ApkDescription> splitApks = apkDescriptions(splitApkVariants);

    // Check that only an APK for tier 0 has been created, not for 1
    ImmutableList<ApkDescription> deviceTierSplits =
        splitApks.stream()
            .filter(apkDesc -> apkDesc.getTargeting().hasDeviceTierTargeting())
            .collect(toImmutableList());
    assertThat(apkNamesInApkDescriptions(deviceTierSplits)).containsExactly("base-tier_0.apk");

    // Check the content of the APK
    ImmutableMap<DeviceTierTargeting, ApkDescription> deviceTierSplitsByTargeting =
        Maps.uniqueIndex(deviceTierSplits, apk -> apk.getTargeting().getDeviceTierTargeting());

    ApkDescription lowTierSplit =
        deviceTierSplitsByTargeting.get(
            deviceTierTargeting(/* value= */ 0, /* alternatives= */ ImmutableList.of(1)));
    assertThat(filesInApk(lowTierSplit, apkSetFile)).contains("assets/images#tier_0/image.jpg");
  }

  @Test
  public void deviceGroupTargetedConditionalModule() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .setManifest(androidManifest("com.test"))
                        .setResourceTable(resourceTableWithTestLabel("Test feature")))
            .addModule(
                "device_tier_feature",
                builder ->
                    builder.setManifest(
                        androidManifest(
                            "com.test",
                            withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID),
                            withFusingAttribute(false),
                            withDeviceGroupsCondition(ImmutableList.of("group1", "group2")))))
            .build();

    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ModuleMetadata deviceTierModule =
        result.getVariantList().stream()
            .flatMap(variant -> variant.getApkSetList().stream())
            .map(ApkSet::getModuleMetadata)
            .filter(moduleMetadata -> moduleMetadata.getName().equals("device_tier_feature"))
            .distinct()
            .collect(onlyElement());
    assertThat(deviceTierModule.getTargeting())
        .isEqualTo(moduleDeviceGroupsTargeting("group1", "group2"));
  }

  @Test
  public void apksSigned() throws Exception {
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withOutputPath(outputFilePath)
            .withSigningConfig(
                SigningConfiguration.builder().setSignerConfig(privateKey, certificate).build())
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThatApksAreSigned(result, apkSetFile, certificate);
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
                                targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86)),
                                targetedNativeDirectory(
                                    "lib/x86_64", nativeDirectoryTargeting(X86_64))))
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
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputDir)
            .withOptimizationDimensions(ABI, LANGUAGE)
            .withCustomBuildApksCommandSetter(command -> command.setOutputFormat(DIRECTORY))
            .build());

    buildApksManager.execute();

    assertThat(command.getOutputFile()).isEqualTo(outputDir);

    BuildApksResult result = parseTocFromFile(outputDir.resolve("toc.pb").toFile());

    // Validate all APKs were created.
    verifyApksExist(apkDescriptions(result.getVariantList()), outputDir);
  }

  @Test
  public void bundleToolVersionSet() throws Exception {
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withOutputPath(outputFilePath)
            .withOptimizationDimensions(ABI, LANGUAGE)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(result.getBundletool().getVersion())
        .isEqualTo(BundleToolVersion.getCurrentVersion().toString());
  }

  @Test
  public void overwriteSet() throws Exception {
    Files.createFile(outputFilePath);

    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withOutputPath(outputFilePath)
            .withCustomBuildApksCommandSetter(command -> command.setOverwriteOutput(true))
            .build());

    buildApksManager.execute();

    assertThat(outputFilePath.toFile().length()).isGreaterThan(0L);
  }

  @Test
  public void externalExecutorServiceDoesNotShutDown() throws Exception {
    ListeningExecutorService listeningExecutorService =
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
    TestComponent.useTestModule(
        this, createTestModuleBuilder().withExecutorService(listeningExecutorService).build());

    buildApksManager.execute();
    assertThat(listeningExecutorService.isShutdown()).isFalse();
  }

  @Test
  public void apkModifier_modifyingVersionCode() throws Exception {
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withOutputPath(outputFilePath)
            .withApkModifier(
                new ApkModifier() {
                  @Override
                  public AndroidManifest modifyManifest(
                      AndroidManifest manifest, ApkModifier.ApkDescription apkDescription) {
                    return manifest
                        .toEditor()
                        .setVersionCode(1000 + apkDescription.getVariantNumber())
                        .save();
                  }
                })
            .build());

    buildApksManager.execute();

    ZipFile apkSet = openZipFile(outputFilePath.toFile());

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
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
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

    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withFirstVariantNumber(100)
            .withOutputPath(outputFilePath)
            .build());

    buildApksManager.execute();

    BuildApksResult buildApksResult =
        ApkSetUtils.extractTocFromApkSetFile(openZipFile(outputFilePath.toFile()), outputDir);

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
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(instantApkVariants(result)).hasSize(1);
    Variant variant = instantApkVariants(result).get(0);
    assertThat(variant.hasTargeting()).isTrue();
    assertThat(variant.getTargeting().getSdkVersionTargeting().getValueList())
        .containsExactly(L_SDK_VERSION);
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
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(instantApkVariants(result)).hasSize(1);
    Variant variant = instantApkVariants(result).get(0);
    assertThat(variant.getApkSetList()).hasSize(1);
    ApkSet apkSet = variant.getApkSetList().get(0);
    assertThat(apkSet.getModuleMetadata().getIsInstant()).isTrue();
    assertThat(apkSetFile).hasFile(apkSet.getApkDescription(0).getPath());

    // split apk also exists and has isInstant set on the module level.
    assertThat(splitApkVariants(result)).hasSize(2);
    ImmutableList<Variant> variants = splitApkVariants(result);
    for (Variant nonInstantVariant : variants) {
      assertThat(nonInstantVariant.getApkSetList()).hasSize(1);
      apkSet = nonInstantVariant.getApkSetList().get(0);
      assertThat(apkSet.getModuleMetadata().getIsInstant()).isTrue();
      assertThat(apkSetFile).hasFile(apkSet.getApkDescription(0).getPath());
    }
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
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);
    ImmutableList<ApkDescription> splitApks = apkDescriptions(splitApkVariants);

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

  @DataPoints("manifestReachableResourcesDisabledVersions")
  public static final ImmutableSet<Version> MANIFEST_REACHABLE_RESOURCES_DISABLED_VERSIONS =
      ImmutableSet.of(Version.of("0.8.0"), Version.of("1.7.0"));

  @Test
  @Theory
  public void pinningOfManifestReachableResources_disabled(
      @FromDataPoints("manifestReachableResourcesDisabledVersions") Version bundleVersion)
      throws Exception {
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
            .setBundleConfig(
                BundleConfigBuilder.create().setVersion(bundleVersion.toString()).build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);
    ImmutableList<ApkDescription> splitApks = apkDescriptions(splitApkVariants);

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

  @Test
  @Theory
  public void explicitMdpiPreferredOverDefault_enabledSince_0_9_1(
      @FromDataPoints("bundleFeatureEnabled") boolean bundleFeatureEnabled) throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .addFile("res/drawable/image.jpg")
                        .addFile("res/drawable-mdpi/image.jpg")
                        .setManifest(androidManifest("com.test.app"))
                        .setResourceTable(
                            new ResourceTableBuilder()
                                .addPackage("com.test.app")
                                .addDrawableResourceForMultipleDensities(
                                    "image",
                                    ImmutableMap.of(
                                        /* default */ 0, "res/drawable/image.jpg",
                                        /* mdpi */ 160, "res/drawable-mdpi/image.jpg"))
                                .build()))
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .setVersion(bundleFeatureEnabled ? "0.9.1" : "0.9.0")
                    .build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder().withAppBundle(appBundle).withOutputPath(outputFilePath).build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableList<Variant> splitApkVariants = splitApkVariants(result);
    ImmutableList<ApkDescription> mdpiSplits =
        apkDescriptions(splitApkVariants).stream()
            .filter(
                apkDesc -> {
                  List<ScreenDensity> targetDensities =
                      apkDesc.getTargeting().getScreenDensityTargeting().getValueList();
                  return targetDensities.stream()
                          .anyMatch(density -> density.getDensityAlias() == DensityAlias.MDPI)
                      || targetDensities.stream()
                          .anyMatch(density -> density.getDensityDpi() == MDPI_VALUE);
                })
            .collect(toImmutableList());

    String fileToBePresent;
    String fileToBeAbsent;
    if (bundleFeatureEnabled) {
      fileToBePresent = "res/drawable-mdpi/image.jpg";
      fileToBeAbsent = "res/drawable/image.jpg";
    } else {
      fileToBeAbsent = "res/drawable-mdpi/image.jpg";
      fileToBePresent = "res/drawable/image.jpg";
    }
    assertThat(mdpiSplits).isNotEmpty();
    for (ApkDescription mdpiSplit : mdpiSplits) {
      assertThat(filesInApk(mdpiSplit, apkSetFile)).contains(fileToBePresent);
      assertThat(filesInApk(mdpiSplit, apkSetFile)).doesNotContain(fileToBeAbsent);
    }
  }

  @Test
  public void allApksSignedWithV1_minSdkLessThan24() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder -> builder.setManifest(androidManifest("com.app", withMinSdkVersion(23))))
            .setBundleConfig(
                BundleConfig.newBuilder()
                    .setBundletool(Bundletool.newBuilder().setVersion("0.11.0"))
                    .build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withSigningConfig(
                SigningConfiguration.builder().setSignerConfig(privateKey, certificate).build())
            .build());

    buildApksManager.execute();

    try (ZipFile apkSet = new ZipFile(outputFilePath.toFile())) {
      BuildApksResult result = extractTocFromApkSetFile(apkSet, outputDir);
      ImmutableList<ApkDescription> apkDescriptions = apkDescriptions(result.getVariantList());
      assertThat(apkDescriptions).isNotEmpty();
      for (ApkDescription apkDescription : apkDescriptions) {
        ImmutableSet<String> filesInApk = filesInApk(apkDescription, apkSet);
        assertThat(filesInApk).contains(String.format("META-INF/%s.RSA", SIGNER_CONFIG_NAME));
      }
    }
  }

  @Test
  public void allApksSignedWithV1_minSdkAtLeast24_oldBundletool() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder -> builder.setManifest(androidManifest("com.app", withMinSdkVersion(24))))
            .setBundleConfig(
                BundleConfig.newBuilder()
                    .setBundletool(Bundletool.newBuilder().setVersion("0.10.0"))
                    .build())
            .build();

    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withSigningConfig(
                SigningConfiguration.builder().setSignerConfig(privateKey, certificate).build())
            .build());

    buildApksManager.execute();

    try (ZipFile apkSet = new ZipFile(outputFilePath.toFile())) {
      BuildApksResult result = extractTocFromApkSetFile(apkSet, outputDir);
      ImmutableList<ApkDescription> apkDescriptions = apkDescriptions(result.getVariantList());
      assertThat(apkDescriptions).isNotEmpty();
      for (ApkDescription apkDescription : apkDescriptions) {
        ImmutableSet<String> filesInApk = filesInApk(apkDescription, apkSet);
        assertThat(filesInApk).contains(String.format("META-INF/%s.RSA", SIGNER_CONFIG_NAME));
      }
    }
  }

  @Test
  public void allApksNotSignedWithV1_minSdkAtLeast24_recentBundletool() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder -> builder.setManifest(androidManifest("com.app", withMinSdkVersion(24))))
            .setBundleConfig(
                BundleConfig.newBuilder()
                    .setBundletool(Bundletool.newBuilder().setVersion("0.11.0"))
                    .build())
            .build();

    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withSigningConfig(
                SigningConfiguration.builder().setSignerConfig(privateKey, certificate).build())
            .build());

    buildApksManager.execute();

    try (ZipFile apkSet = new ZipFile(outputFilePath.toFile())) {
      BuildApksResult result = extractTocFromApkSetFile(apkSet, outputDir);
      ImmutableList<ApkDescription> apkDescriptions = apkDescriptions(result.getVariantList());
      assertThat(apkDescriptions).isNotEmpty();
      for (ApkDescription apkDescription : apkDescriptions) {
        ImmutableSet<String> filesInApk = filesInApk(apkDescription, apkSet);
        assertThat(filesInApk).doesNotContain(String.format("META-INF/%s.RSA", SIGNER_CONFIG_NAME));
      }
    }
  }

  @Test
  public void apkWithSourceStamp() throws Exception {
    String stampSource = "https://www.example.com";
    SigningConfiguration signingConfiguration =
        SigningConfiguration.builder().setSignerConfig(privateKey, certificate).build();

    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withOutputPath(outputFilePath)
            .withSigningConfig(signingConfiguration)
            .withSourceStamp(
                SourceStamp.builder()
                    .setSource(stampSource)
                    .setSigningConfiguration(signingConfiguration)
                    .build())
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);
    for (Variant variant : result.getVariantList()) {
      for (ApkSet apkSet : variant.getApkSetList()) {
        for (ApkDescription apkDescription : apkSet.getApkDescriptionList()) {
          File apk = extractFromApkSetFile(apkSetFile, apkDescription.getPath(), outputDir);

          ApkVerifier.Result verifierResult = new ApkVerifier.Builder(apk).build().verify();
          assertThat(verifierResult.isSourceStampVerified()).isTrue();
          assertThat(verifierResult.getSourceStampInfo().getCertificate()).isEqualTo(certificate);

          AndroidManifest manifest = extractAndroidManifest(apk, tmpDir);
          assertThat(manifest.getMetadataValue(STAMP_SOURCE_METADATA_KEY)).hasValue(stampSource);

          try (ZipFile apkZip = new ZipFile(apk)) {
            ZipEntry sourceStampCertEntry = apkZip.getEntry("stamp-cert-sha256");
            assertNotNull(sourceStampCertEntry);

            byte[] sourceStampCertHash =
                ByteStreams.toByteArray(apkZip.getInputStream(sourceStampCertEntry));
            assertThat(sourceStampCertHash)
                .isEqualTo(CertificateHelper.getSha256Bytes(certificate.getEncoded()));
          }
        }
      }
    }
  }

  @Test
  public void packageNameIsPropagatedToBuildResult() throws Exception {
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withOutputPath(outputFilePath)
            .withAppBundle(
                new AppBundleBuilder()
                    .addModule("base", module -> module.setManifest(androidManifest("com.app")))
                    .build())
            .build());
    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);
    assertThat(result.getPackageName()).isEqualTo("com.app");
  }

  @Test
  public void transparencyFilePropagatedAsExpected() throws Exception {
    String dexFilePath = "dex/classes.dex";
    byte[] dexFileInBaseModuleContent = TestData.readBytes("testdata/dex/classes.dex");
    byte[] dexFileInFeatureModuleContent = TestData.readBytes("testdata/dex/classes-other.dex");
    String libFilePath = "lib/x86_64/libsome.so";
    byte[] libFileInBaseModuleContent = new byte[] {4, 5, 6};
    CodeTransparency codeTransparency =
        CodeTransparency.newBuilder()
            .addCodeRelatedFile(
                CodeRelatedFile.newBuilder()
                    .setType(CodeRelatedFile.Type.DEX)
                    .setPath("base/" + dexFilePath)
                    .setSha256(
                        ByteSource.wrap(dexFileInBaseModuleContent)
                            .hash(Hashing.sha256())
                            .toString()))
            .addCodeRelatedFile(
                CodeRelatedFile.newBuilder()
                    .setType(CodeRelatedFile.Type.NATIVE_LIBRARY)
                    .setPath("base/" + libFilePath)
                    .setSha256(
                        ByteSource.wrap(libFileInBaseModuleContent)
                            .hash(Hashing.sha256())
                            .toString())
                    .setApkPath(libFilePath))
            .addCodeRelatedFile(
                CodeRelatedFile.newBuilder()
                    .setType(CodeRelatedFile.Type.DEX)
                    .setPath("feature/" + dexFilePath)
                    .setSha256(
                        ByteSource.wrap(dexFileInFeatureModuleContent)
                            .hash(Hashing.sha256())
                            .toString()))
            .addCodeRelatedFile(
                CodeRelatedFileBuilderHelper.archivedDexCodeRelatedFile(
                    BundleToolVersion.getCurrentVersion()))
            .build();
    Path bundlePath = tmpDir.resolve("bundle.aab");
    AppBundleBuilder appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                module ->
                    module
                        .setManifest(androidManifest("com.test.app", withMinSdkVersion(20)))
                        .setResourceTable(resourceTableWithTestLabel("Test feature"))
                        .addFile(
                            dexFilePath,
                            bundlePath,
                            ZipPath.create("base/" + dexFilePath),
                            dexFileInBaseModuleContent)
                        .addFile(
                            libFilePath,
                            bundlePath,
                            ZipPath.create("base/" + libFilePath),
                            libFileInBaseModuleContent)
                        .setNativeConfig(
                            nativeLibraries(
                                targetedNativeDirectory(
                                    "lib/x86_64", nativeDirectoryTargeting(AbiAlias.X86_64)))))
            .addModule(
                "feature",
                module ->
                    module
                        .setManifest(
                            androidManifest(
                                "com.test.app",
                                withDelivery(DeliveryType.ON_DEMAND),
                                withFusingAttribute(true),
                                withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID)))
                        .addFile(
                            dexFilePath,
                            bundlePath,
                            ZipPath.create("feature/" + dexFilePath),
                            dexFileInFeatureModuleContent))
            .addMetadataFile(
                BundleMetadata.BUNDLETOOL_NAMESPACE,
                BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME,
                CharSource.wrap(createJwsToken(codeTransparency, certificate, privateKey))
                    .asByteSource(Charset.defaultCharset()));

    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withOutputPath(outputFilePath)
            .withAppBundle(appBundle.build())
            .build());
    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    ImmutableList<ApkDescription> splitApks = apkDescriptions(splitApkVariants(result));

    // Transparency file should be propagated to main split of the base module.
    ImmutableList<ApkDescription> mainSplitsOfBaseModule =
        splitApks.stream()
            .filter(
                apk ->
                    apk.getSplitApkMetadata().getSplitId().isEmpty()
                        && apk.getSplitApkMetadata().getIsMasterSplit())
            .collect(toImmutableList());
    assertThat(mainSplitsOfBaseModule).hasSize(3);
    for (ApkDescription apk : mainSplitsOfBaseModule) {
      ZipFile zipFile = openZipFile(extractFromApkSetFile(apkSetFile, apk.getPath(), outputDir));
      assertThat(filesUnderPath(zipFile, ZipPath.create("META-INF")))
          .contains("META-INF/" + BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME);
    }

    // Other splits should not contain transparency file.
    ImmutableList<ApkDescription> otherSplits =
        splitApks.stream()
            .filter(apk -> !apk.getSplitApkMetadata().getSplitId().isEmpty())
            .collect(toImmutableList());
    assertThat(otherSplits).hasSize(6);
    for (ApkDescription apk : otherSplits) {
      ZipFile zipFile = openZipFile(extractFromApkSetFile(apkSetFile, apk.getPath(), outputDir));
      assertThat(filesUnderPath(zipFile, ZipPath.create("META-INF"))).isEmpty();
    }

    // Because minSdkVersion < 21, bundle has a feature module and merging strategy is
    // MERGE_IF_NEEDED (default), transparency file should not be propagated to standalone APK.
    assertThat(standaloneApkVariants(result)).hasSize(1);
    ImmutableList<ApkDescription> standaloneApks =
        apkDescriptions(standaloneApkVariants(result).get(0));
    File standaloneApkFile =
        extractFromApkSetFile(apkSetFile, standaloneApks.get(0).getPath(), outputDir);
    ZipFile standaloneApkZip = openZipFile(standaloneApkFile);
    assertThat(filesUnderPath(standaloneApkZip, ZipPath.create("META-INF"))).isEmpty();
  }

  @Test
  public void buildApksCommand_archive_success() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder.setManifest(
                        androidManifest("com.test.app", withMainActivity("com.test.app.Main"))))
            .setBundleConfig(BundleConfigBuilder.create().setStoreArchive(true).build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withApkBuildMode(ARCHIVE)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(result.getVariantList()).hasSize(1);
    assertThat(splitApkVariants(result)).isEmpty();
    assertThat(standaloneApkVariants(result)).isEmpty();
    assertThat(systemApkVariants(result)).isEmpty();
    assertThat(archivedApkVariants(result)).hasSize(1);
    Variant archivedVariant = archivedApkVariants(result).get(0);
    assertThat(archivedVariant.getTargeting()).isEqualToDefaultInstance();

    assertThat(apkDescriptions(archivedVariant)).hasSize(1);
    assertThat(archivedVariant.getApkSetList()).hasSize(1);
    ApkSet apkSet = archivedVariant.getApkSet(0);
    assertThat(
            apkSet.getApkDescriptionList().stream()
                .map(ApkDescription::getPath)
                .collect(toImmutableSet()))
        .containsExactly("archive/archive.apk");
    apkSet
        .getApkDescriptionList()
        .forEach(apkDescription -> assertThat(apkSetFile).hasFile(apkDescription.getPath()));
  }

  @Test
  public void buildApksCommand_archive_apex_archivedNotGenerated() throws Exception {
    ApexImages apexConfig =
        apexImages(targetedApexImage("apex/x86_64.img", apexImageTargeting("x86_64")));
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder.setManifest(androidManifest("com.test.app")).setApexConfig(apexConfig))
            .setBundleConfig(BundleConfigBuilder.create().setStoreArchive(true).build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withOutputPath(outputFilePath)
            .withApkBuildMode(ARCHIVE)
            .build());

    Exception e = assertThrows(InvalidCommandException.class, () -> buildApksManager.execute());
    assertThat(e).hasMessageThat().contains("No APKs to generate");
  }

  @Test
  public void buildApksCommand_archive_assetOnly_archivedNotGenerated() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", builder -> builder.setManifest(androidManifest("com.test.app")))
            .setBundleConfig(BundleConfigBuilder.create().setStoreArchive(true).build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundle)
            .withBundleConfig(BundleConfig.newBuilder().setType(BundleType.ASSET_ONLY))
            .withOutputPath(outputFilePath)
            .withApkBuildMode(ARCHIVE)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(archivedApkVariants(result)).isEmpty();
  }

  @Test
  public void appBundleHasRuntimeEnabledSdkDeps_generatesSdkRuntimeVariant() throws Exception {
    String validCertDigest =
        "96:C7:EC:89:3E:69:2A:25:BA:4D:EE:C1:84:E8:33:3F:34:7D:6D:12:26:A1:C1:AA:70:A2:8A:DB:75:3E:02:0A";
    AppBundle appBundleWithRuntimeEnabledSdkDeps =
        new AppBundleBuilder()
            .addModule(
                new BundleModuleBuilder("base")
                    .setManifest(
                        androidManifest("com.test.app", withMinSdkVersion(ANDROID_L_API_VERSION)))
                    .setResourceTable(resourceTableWithTestLabel("Test feature"))
                    .setRuntimeEnabledSdkConfig(
                        RuntimeEnabledSdkConfig.newBuilder()
                            .addRuntimeEnabledSdk(
                                RuntimeEnabledSdk.newBuilder()
                                    .setPackageName("com.test.sdk1")
                                    .setVersionMajor(1)
                                    .setVersionMinor(1)
                                    .setCertificateDigest(validCertDigest)
                                    .setResourcesPackageId(2))
                            .build())
                    .build())
            .addModule(
                new BundleModuleBuilder("feature")
                    .setManifest(
                        androidManifestForFeature(
                            "com.test.app",
                            withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID)))
                    .setRuntimeEnabledSdkConfig(
                        RuntimeEnabledSdkConfig.newBuilder()
                            .addRuntimeEnabledSdk(
                                RuntimeEnabledSdk.newBuilder()
                                    .setPackageName("com.test.sdk2")
                                    .setVersionMajor(2)
                                    .setVersionMinor(2)
                                    .setCertificateDigest(validCertDigest)
                                    .setResourcesPackageId(3))
                            .build())
                    .build())
            .build();
    TestComponent.useTestModule(
        this,
        createTestModuleBuilder()
            .withAppBundle(appBundleWithRuntimeEnabledSdkDeps)
            .withOutputPath(outputFilePath)
            .build());

    buildApksManager.execute();

    ZipFile apkSetFile = openZipFile(outputFilePath.toFile());
    BuildApksResult result = extractTocFromApkSetFile(apkSetFile, outputDir);

    assertThat(result.getVariantList()).hasSize(2);
    ImmutableList<Variant> sortedVariantList =
        ImmutableList.sortedCopyOf(comparing(Variant::getVariantNumber), result.getVariantList());
    Variant nonSdkRuntimeVariant = sortedVariantList.get(0);
    assertThat(nonSdkRuntimeVariant.getVariantNumber()).isEqualTo(0);
    assertThat(nonSdkRuntimeVariant.getTargeting())
        .isEqualTo(variantSdkTargeting(ANDROID_L_API_VERSION));
    Variant sdkRuntimeVariant = sortedVariantList.get(1);
    assertThat(sdkRuntimeVariant.getVariantNumber()).isEqualTo(1);
    assertThat(sdkRuntimeVariant.getTargeting())
        .isEqualTo(
            sdkRuntimeVariantTargeting(ANDROID_T_API_VERSION).toBuilder()
                .setSdkRuntimeTargeting(
                    SdkRuntimeTargeting.newBuilder().setRequiresSdkRuntime(true))
                .build());
    ImmutableMap<String, ModuleMetadata> sdkRuntimeModulesMetadata =
        sdkRuntimeVariant.getApkSetList().stream()
            .map(ApkSet::getModuleMetadata)
            .collect(toImmutableMap(ModuleMetadata::getName, identity()));
    assertThat(sdkRuntimeModulesMetadata).hasSize(2);
    assertThat(sdkRuntimeModulesMetadata.get("base").getRuntimeEnabledSdkDependenciesList())
        .containsExactly(
            RuntimeEnabledSdkDependency.newBuilder()
                .setPackageName("com.test.sdk1")
                .setMajorVersion(1)
                .setMinorVersion(1)
                .build());
    assertThat(sdkRuntimeModulesMetadata.get("feature").getRuntimeEnabledSdkDependenciesList())
        .containsExactly(
            RuntimeEnabledSdkDependency.newBuilder()
                .setPackageName("com.test.sdk2")
                .setMajorVersion(2)
                .setMinorVersion(2)
                .build());
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

  private static ImmutableList<String> apkNamesInApkDescriptions(
      Collection<ApkDescription> apkDescs) {
    return apkDescs.stream()
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

  private Path createAndStoreBundle(AppBundle appBundle) throws IOException {
    Path bundlePath = tmp.newFolder().toPath().resolve("bundle.aab");
    bundleSerializer.writeToDisk(appBundle, bundlePath);
    return bundlePath;
  }

  private int extractVersionCode(File apk) {
    return extractAndroidManifest(apk, tmpDir)
        .getVersionCode()
        .orElseThrow(InvalidVersionCodeException::createMissingVersionCodeException);
  }

  private static ImmutableList<String> getServicesFromManifest(AndroidManifest manifest) {
    return manifest
        .getManifestRoot()
        .getElement()
        .getChildElement(AndroidManifest.APPLICATION_ELEMENT_NAME)
        .getChildrenElements(AndroidManifest.SERVICE_ELEMENT_NAME)
        .map(
            element ->
                element
                    .getAndroidAttribute(AndroidManifest.NAME_RESOURCE_ID)
                    .get()
                    .getValueAsString())
        .collect(toImmutableList());
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

  private static AppBundleBuilder createAppBundleBuilder(Version bundleVersion) {
    return new AppBundleBuilder()
        .setBundleConfig(
            BundleConfig.newBuilder()
                .setBundletool(Bundletool.newBuilder().setVersion(bundleVersion.toString()))
                .build());
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

  private static BundleConfig.Builder createCollapsedResourceNameConfig(
      boolean collapseResourceNames,
      boolean deduplicateResourceEntries,
      ImmutableList<ResourceTypeAndName> noCollapse) {
    return BundleConfig.newBuilder()
        .setOptimizations(
            Optimizations.newBuilder()
                .setResourceOptimizations(
                    ResourceOptimizations.newBuilder()
                        .setCollapsedResourceNames(
                            CollapsedResourceNames.newBuilder()
                                .setCollapseResourceNames(collapseResourceNames)
                                .setDeduplicateResourceEntries(deduplicateResourceEntries)
                                .addAllNoCollapseResources(noCollapse))));
  }

  private static long getApkSize(Path apksPath, String innerApk) throws IOException {
    return ZipMap.from(apksPath).getEntries().get(innerApk).getUncompressedSize();
  }

  @CommandScoped
  @Component(modules = {BuildApksModule.class, TestModule.class})
  interface TestComponent {

    void inject(BuildApksManagerTest test);

    static void useTestModule(BuildApksManagerTest testInstance, TestModule testModule) {
      DaggerBuildApksManagerTest_TestComponent.builder()
          .testModule(testModule)
          .build()
          .inject(testInstance);
    }
  }
}
