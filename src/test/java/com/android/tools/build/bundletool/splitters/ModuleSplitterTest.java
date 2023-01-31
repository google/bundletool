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

package com.android.tools.build.bundletool.splitters;

import static com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias.ATC;
import static com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias.ETC1_RGB8;
import static com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias.S3TC;
import static com.android.tools.build.bundletool.model.AndroidManifest.ACTIVITY_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.ANDROID_NAMESPACE_URI;
import static com.android.tools.build.bundletool.model.AndroidManifest.APPLICATION_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.NAME_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.PERMISSION_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.PROPERTY_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SDK_SANDBOX_MIN_VERSION;
import static com.android.tools.build.bundletool.model.AndroidManifest.SPLIT_NAME_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.ManifestMutator.withExtractNativeLibs;
import static com.android.tools.build.bundletool.model.OptimizationDimension.ABI;
import static com.android.tools.build.bundletool.model.OptimizationDimension.COUNTRY_SET;
import static com.android.tools.build.bundletool.model.OptimizationDimension.DEVICE_TIER;
import static com.android.tools.build.bundletool.model.OptimizationDimension.LANGUAGE;
import static com.android.tools.build.bundletool.model.OptimizationDimension.SCREEN_DENSITY;
import static com.android.tools.build.bundletool.model.OptimizationDimension.TEXTURE_COMPRESSION_FORMAT;
import static com.android.tools.build.bundletool.model.RuntimeEnabledSdkVersionEncoder.encodeSdkMajorAndMinorVersion;
import static com.android.tools.build.bundletool.model.SourceStampConstants.STAMP_SOURCE_METADATA_KEY;
import static com.android.tools.build.bundletool.model.SourceStampConstants.STAMP_TYPE_METADATA_KEY;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_L_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_M_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_Q_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_R_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_S_V2_API_VERSION;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.clearApplication;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstant;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMainActivity;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMaxSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMetadataResource;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withRequiredByPrivacySandboxElement;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitNameActivity;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withUsesSdkLibraryElement;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.xmlAttribute;
import static com.android.tools.build.bundletool.testing.ModuleSplitUtils.applyManifestMutators;
import static com.android.tools.build.bundletool.testing.ModuleSplitUtils.createModuleSplitBuilder;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.HDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.USER_PACKAGE_OFFSET;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.XHDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.entry;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.fileReference;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.locale;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.mergeConfigs;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.pkg;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.resourceTable;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.type;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.value;
import static com.android.tools.build.bundletool.testing.TargetingUtils.alternativeLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkAlternativeLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkCountrySetTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkDensityTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkDeviceTierTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkMinSdkTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkTextureTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assets;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.countrySetTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.deviceTierTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.getSplitsWithTargetingEqualTo;
import static com.android.tools.build.bundletool.testing.TargetingUtils.lPlusVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.languageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeApkTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeLibraries;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkRuntimeVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedAssetsDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedNativeDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.textureCompressionTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.toAbi;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantMinSdkTargeting;
import static com.android.tools.build.bundletool.testing.TestUtils.extractPaths;
import static com.android.tools.build.bundletool.testing.truth.resources.TruthResourceTable.assertThat;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.MoreCollectors.onlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static junit.framework.TestCase.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.ConfigurationOuterClass.Configuration;
import com.android.aapt.Resources.ConfigValue;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.XmlElement;
import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.SuffixStripping;
import com.android.bundle.Files.Assets;
import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdk;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdkConfig;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.OptimizationDimension;
import com.android.tools.build.bundletool.model.ResourceId;
import com.android.tools.build.bundletool.model.SourceStampConstants.StampType;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.utils.Versions;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElement;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.model.version.Version;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.ByteSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ModuleSplitterTest {

  // The master split will by default have minSdkVersion targeting set to L+, because split APKs
  // were not supported before then.
  private static final ApkTargeting DEFAULT_MASTER_SPLIT_SDK_TARGETING = apkMinSdkTargeting(21);

  private static final Version BUNDLETOOL_VERSION = BundleToolVersion.getCurrentVersion();

  private static final String VALID_CERT_DIGEST =
      "96:C7:EC:89:3E:69:2A:25:BA:4D:EE:C1:84:E8:33:3F:34:7D:6D:12:26:A1:C1:AA:70:A2:8A:DB:75:3E:02:0A";

  private static final BundleModule BASE_MODULE =
      new BundleModuleBuilder("base")
          .addFile("dex/classes.dex")
          .setManifest(androidManifest("com.test.app"))
          .build();

  private static final AppBundle APP_BUNDLE = new AppBundleBuilder().addModule(BASE_MODULE).build();

  @Test
  public void minSdkVersionInOutputTargeting_getsSetToL() throws Exception {
    BundleModule bundleModule =
        new BundleModuleBuilder("testModule").setManifest(androidManifest("com.test.app")).build();

    ImmutableList<ModuleSplit> moduleSplits =
        ModuleSplitter.createForTest(
                bundleModule,
                AppBundle.buildFromModules(
                    ImmutableList.of(bundleModule, BASE_MODULE),
                    BundleConfig.getDefaultInstance(),
                    BundleMetadata.builder().build()),
                BUNDLETOOL_VERSION)
            .splitModule();

    assertThat(moduleSplits).hasSize(1);
    ModuleSplit masterSplit = moduleSplits.get(0);
    assertThat(masterSplit.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(masterSplit.getVariantTargeting()).isEqualTo(lPlusVariantTargeting());
    assertThat(masterSplit.isMasterSplit()).isTrue();
    assertThat(masterSplit.getApkTargeting()).isEqualTo(apkMinSdkTargeting(21));
  }

  @Test
  public void rPlusSigningConfigWithRPlusVariant_minSdkVersionInOutputTargetingGetsSetToR()
      throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule").setManifest(androidManifest("com.test.app")).build();

    ImmutableList<ModuleSplit> moduleSplits =
        ModuleSplitter.createNoStamp(
                testModule,
                BUNDLETOOL_VERSION,
                APP_BUNDLE,
                ApkGenerationConfiguration.builder()
                    .setMinSdkForAdditionalVariantWithV3Rotation(ANDROID_R_API_VERSION)
                    .build(),
                variantMinSdkTargeting(Versions.ANDROID_R_API_VERSION),
                ImmutableSet.of("base", "testModule"))
            .splitModule();

    assertThat(moduleSplits.stream().map(ModuleSplit::getApkTargeting).distinct())
        .containsExactly(apkMinSdkTargeting(Versions.ANDROID_R_API_VERSION));
  }

  @Test
  public void rPlusSigningConfigWithDefaultVariant_minSdkVersionInOutputTargetingNotSetToR()
      throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule").setManifest(androidManifest("com.test.app")).build();

    ImmutableList<ModuleSplit> moduleSplits =
        ModuleSplitter.createNoStamp(
                testModule,
                BUNDLETOOL_VERSION,
                APP_BUNDLE,
                ApkGenerationConfiguration.builder()
                    .setMinSdkForAdditionalVariantWithV3Rotation(ANDROID_R_API_VERSION)
                    .build(),
                VariantTargeting.getDefaultInstance(),
                ImmutableSet.of("base", "testModule"))
            .splitModule();

    assertThat(moduleSplits.stream().map(ModuleSplit::getApkTargeting))
        .doesNotContain(apkMinSdkTargeting(Versions.ANDROID_R_API_VERSION));
  }

  @Test
  public void defaultSigningConfigWithRPlusVariant_minSdkVersionInOutputTargetingNotSetToR()
      throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule").setManifest(androidManifest("com.test.app")).build();

    ImmutableList<ModuleSplit> moduleSplits =
        ModuleSplitter.createNoStamp(
                testModule,
                BUNDLETOOL_VERSION,
                APP_BUNDLE,
                ApkGenerationConfiguration.getDefaultInstance(),
                variantMinSdkTargeting(Versions.ANDROID_R_API_VERSION),
                ImmutableSet.of("base", "testModule"))
            .splitModule();

    assertThat(moduleSplits.stream().map(ModuleSplit::getApkTargeting))
        .doesNotContain(apkMinSdkTargeting(Versions.ANDROID_R_API_VERSION));
  }

  @Test
  public void testSplittingOnDensityAndLanguage_inSeparateDirectories() throws Exception {
    BundleModule bundleModule =
        new BundleModuleBuilder("testModule")
            .addFile("res/drawable-xhdpi/image.jpg")
            .addFile("res/drawable-hdpi/image.jpg")
            .addFile("res/drawable/image.jpg")
            .setResourceTable(
                resourceTable(
                    pkg(
                        USER_PACKAGE_OFFSET,
                        "com.test.app",
                        type(
                            0x01,
                            "drawable",
                            entry(
                                0x01,
                                "image",
                                fileReference("res/drawable-xhdpi/image.jpg", XHDPI),
                                fileReference("res/drawable-hdpi/image.jpg", HDPI),
                                fileReference(
                                    "res/drawable/image.jpg", Configuration.getDefaultInstance()))),
                        type(
                            0x02,
                            "string",
                            entry(
                                0x01,
                                "welcome_label",
                                value("Welcome", Configuration.getDefaultInstance()),
                                value("Willkommen", locale("de")))))))
            .setManifest(androidManifest("com.test.app"))
            .build();

    List<ModuleSplit> splits = createAbiDensityAndLanguageSplitter(bundleModule).splitModule();
    assertThat(splits.stream().map(ModuleSplit::getSplitType).distinct().collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    assertThat(
            splits.stream()
                .map(ModuleSplit::getVariantTargeting)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(lPlusVariantTargeting());
    Map<String, ModuleSplit> splitsBySuffix = Maps.uniqueIndex(splits, ModuleSplit::getSuffix);

    // 7 density splits (ldpi, mdpi, tvdpi, hdpi, xhdpi, xxhdpi, xxxhdpi) + 1 language split
    // (german)
    // + 1 master split = 9
    assertThat(splitsBySuffix.keySet())
        .containsExactly("ldpi", "mdpi", "tvdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi", "de", "");

    ImmutableSet<DensityAlias> densities =
        ImmutableSet.of(
            DensityAlias.LDPI,
            DensityAlias.MDPI,
            DensityAlias.TVDPI,
            DensityAlias.HDPI,
            DensityAlias.XHDPI,
            DensityAlias.XXHDPI,
            DensityAlias.XXXHDPI);

    assertThat(splitsBySuffix.get("ldpi").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkDensityTargeting(
                    DensityAlias.LDPI,
                    Sets.difference(densities, ImmutableSet.of(DensityAlias.LDPI)))));
    assertThat(splitsBySuffix.get("mdpi").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkDensityTargeting(
                    DensityAlias.MDPI,
                    Sets.difference(densities, ImmutableSet.of(DensityAlias.MDPI)))));
    assertThat(splitsBySuffix.get("tvdpi").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkDensityTargeting(
                    DensityAlias.TVDPI,
                    Sets.difference(densities, ImmutableSet.of(DensityAlias.TVDPI)))));
    assertThat(splitsBySuffix.get("hdpi").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkDensityTargeting(
                    DensityAlias.HDPI,
                    Sets.difference(densities, ImmutableSet.of(DensityAlias.HDPI)))));
    assertThat(splitsBySuffix.get("xhdpi").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkDensityTargeting(
                    DensityAlias.XHDPI,
                    Sets.difference(densities, ImmutableSet.of(DensityAlias.XHDPI)))));
    assertThat(splitsBySuffix.get("xxhdpi").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkDensityTargeting(
                    DensityAlias.XXHDPI,
                    Sets.difference(densities, ImmutableSet.of(DensityAlias.XXHDPI)))));
    assertThat(splitsBySuffix.get("xxxhdpi").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkDensityTargeting(
                    DensityAlias.XXXHDPI,
                    Sets.difference(densities, ImmutableSet.of(DensityAlias.XXXHDPI)))));
    assertThat(splitsBySuffix.get("de").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeApkTargeting(DEFAULT_MASTER_SPLIT_SDK_TARGETING, apkLanguageTargeting("de")));
    assertThat(splitsBySuffix.get("").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(DEFAULT_MASTER_SPLIT_SDK_TARGETING);
  }

  @Test
  public void testSplittingOnDensityAndLanguage_DensityPreferred() throws Exception {
    // This tests that we output only one dimension for splits and resources already split on
    // density are not split further on language.
    // Pure language targeted resources are going to be split on language though.
    BundleModule bundleModule =
        new BundleModuleBuilder("testModule")
            .addFile("res/drawable-hdpi/image.jpg")
            .addFile("res/drawable-fr-hdpi/image.jpg")
            .addFile("res/drawable/image.jpg")
            .setResourceTable(
                resourceTable(
                    pkg(
                        USER_PACKAGE_OFFSET,
                        "com.test.app",
                        type(
                            0x01,
                            "drawable",
                            entry(
                                0x01,
                                "image",
                                fileReference(
                                    "res/drawable-fr-hdpi/image.jpg",
                                    mergeConfigs(locale("fr"), HDPI)),
                                fileReference("res/drawable-hdpi/image.jpg", HDPI),
                                fileReference(
                                    "res/drawable/image.jpg", Configuration.getDefaultInstance()))),
                        type(
                            0x02,
                            "strings",
                            entry(
                                0x01,
                                "welcome_label",
                                value("Hello", Configuration.getDefaultInstance()),
                                value("Bienvenue", locale("fr")))))))
            .setManifest(androidManifest("com.test.app"))
            .build();

    List<ModuleSplit> splits = createAbiDensityAndLanguageSplitter(bundleModule).splitModule();
    assertThat(splits.stream().map(ModuleSplit::getSplitType).distinct().collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    assertThat(
            splits.stream()
                .map(ModuleSplit::getVariantTargeting)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(lPlusVariantTargeting());

    Map<String, ModuleSplit> splitsBySuffix = Maps.uniqueIndex(splits, ModuleSplit::getSuffix);
    // 7 density splits (ldpi, mdpi, tvdpi, hdpi, xhdpi, xxhdpi, xxxhdpi) + 1 language split
    // + 1 master split = 9 splits
    assertThat(splitsBySuffix.keySet())
        .containsExactly("ldpi", "mdpi", "tvdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi", "fr", "");

    ImmutableSet<DensityAlias> densities =
        ImmutableSet.of(
            DensityAlias.LDPI,
            DensityAlias.MDPI,
            DensityAlias.TVDPI,
            DensityAlias.HDPI,
            DensityAlias.XHDPI,
            DensityAlias.XXHDPI,
            DensityAlias.XXXHDPI);
    assertThat(splitsBySuffix.get("ldpi").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkDensityTargeting(
                    DensityAlias.LDPI,
                    Sets.difference(densities, ImmutableSet.of(DensityAlias.LDPI)))));
    assertThat(splitsBySuffix.get("mdpi").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkDensityTargeting(
                    DensityAlias.MDPI,
                    Sets.difference(densities, ImmutableSet.of(DensityAlias.MDPI)))));
    assertThat(splitsBySuffix.get("tvdpi").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkDensityTargeting(
                    DensityAlias.TVDPI,
                    Sets.difference(densities, ImmutableSet.of(DensityAlias.TVDPI)))));
    assertThat(splitsBySuffix.get("hdpi").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkDensityTargeting(
                    DensityAlias.HDPI,
                    Sets.difference(densities, ImmutableSet.of(DensityAlias.HDPI)))));
    assertThat(splitsBySuffix.get("xhdpi").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkDensityTargeting(
                    DensityAlias.XHDPI,
                    Sets.difference(densities, ImmutableSet.of(DensityAlias.XHDPI)))));
    assertThat(splitsBySuffix.get("xxhdpi").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkDensityTargeting(
                    DensityAlias.XXHDPI,
                    Sets.difference(densities, ImmutableSet.of(DensityAlias.XXHDPI)))));
    assertThat(splitsBySuffix.get("xxxhdpi").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkDensityTargeting(
                    DensityAlias.XXXHDPI,
                    Sets.difference(densities, ImmutableSet.of(DensityAlias.XXXHDPI)))));

    assertThat(splitsBySuffix.get("fr").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeApkTargeting(DEFAULT_MASTER_SPLIT_SDK_TARGETING, apkLanguageTargeting("fr")));
    assertThat(splitsBySuffix.get("").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(DEFAULT_MASTER_SPLIT_SDK_TARGETING);
  }

  @Ignore("Re-enable when we support generating multiple dimension targeting splits.")
  @Test
  public void testSplittingOnDensityAndLanguage_inSameDirectory() throws Exception {
    BundleModule bundleModule =
        new BundleModuleBuilder("testModule")
            .addFile("res/drawable-xhdpi/image.jpg")
            .addFile("res/drawable-hdpi/image.jpg")
            .addFile("res/drawable-fr-hdpi/image.jpg")
            .addFile("res/drawable/image.jpg")
            .setResourceTable(
                resourceTable(
                    pkg(
                        USER_PACKAGE_OFFSET,
                        "com.test.app",
                        type(
                            0x01,
                            "drawable",
                            entry(
                                0x01,
                                "image",
                                fileReference("res/drawable-xhdpi/image.jpg", XHDPI),
                                fileReference(
                                    "res/drawable-fr-hdpi/image.jpg",
                                    mergeConfigs(locale("fr"), HDPI)),
                                fileReference("res/drawable-hdpi/image.jpg", HDPI),
                                fileReference(
                                    "res/drawable/image.jpg",
                                    Configuration.getDefaultInstance()))))))
            .setManifest(androidManifest("com.test.app"))
            .build();

    List<ModuleSplit> splits = createAbiDensityAndLanguageSplitter(bundleModule).splitModule();
    assertThat(splits.stream().map(ModuleSplit::getSplitType).distinct().collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    assertThat(
            splits.stream()
                .map(ModuleSplit::getVariantTargeting)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(lPlusVariantTargeting());

    Map<String, ModuleSplit> splitsBySuffix = Maps.uniqueIndex(splits, ModuleSplit::getSuffix);
    // 7 density splits (ldpi, mdpi, tvdpi, hdpi, xhdpi, xxhdpi, xxxhdpi) x 2 language splits
    // (one default, one French) + 1 master split = 15
    assertThat(splitsBySuffix.keySet())
        .containsExactly(
            "ldpi",
            "fr_ldpi",
            "mdpi",
            "fr_mdpi",
            "tvdpi",
            "fr_tvdpi",
            "hdpi",
            "fr_hdpi",
            "xhdpi",
            "fr_xhdpi",
            "xxhdpi",
            "fr_xxhdpi",
            "xxxhdpi",
            "fr_xxxhdpi",
            "");

    ImmutableSet<DensityAlias> densities =
        ImmutableSet.of(
            DensityAlias.LDPI,
            DensityAlias.MDPI,
            DensityAlias.TVDPI,
            DensityAlias.HDPI,
            DensityAlias.XHDPI,
            DensityAlias.XXHDPI,
            DensityAlias.XXXHDPI);
    assertThat(splitsBySuffix.get("ldpi").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkDensityTargeting(
                    DensityAlias.LDPI,
                    Sets.difference(densities, ImmutableSet.of(DensityAlias.LDPI)))));
    assertThat(splitsBySuffix.get("fr_ldpi").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkLanguageTargeting("fr"),
                apkDensityTargeting(
                    DensityAlias.LDPI,
                    Sets.difference(densities, ImmutableSet.of(DensityAlias.LDPI)))));
    assertThat(splitsBySuffix.get("mdpi").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkDensityTargeting(
                    DensityAlias.MDPI,
                    Sets.difference(densities, ImmutableSet.of(DensityAlias.MDPI)))));
    assertThat(splitsBySuffix.get("fr_mdpi").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkLanguageTargeting("fr"),
                apkDensityTargeting(
                    DensityAlias.MDPI,
                    Sets.difference(densities, ImmutableSet.of(DensityAlias.MDPI)))));
    assertThat(splitsBySuffix.get("tvdpi").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkDensityTargeting(
                    DensityAlias.TVDPI,
                    Sets.difference(densities, ImmutableSet.of(DensityAlias.TVDPI)))));
    assertThat(splitsBySuffix.get("fr_tvdpi").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkLanguageTargeting("fr"),
                apkDensityTargeting(
                    DensityAlias.TVDPI,
                    Sets.difference(densities, ImmutableSet.of(DensityAlias.TVDPI)))));
    assertThat(splitsBySuffix.get("hdpi").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkDensityTargeting(
                    DensityAlias.HDPI,
                    Sets.difference(densities, ImmutableSet.of(DensityAlias.HDPI)))));
    assertThat(splitsBySuffix.get("fr_hdpi").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkLanguageTargeting("fr"),
                apkDensityTargeting(
                    DensityAlias.HDPI,
                    Sets.difference(densities, ImmutableSet.of(DensityAlias.HDPI)))));
    assertThat(splitsBySuffix.get("xhdpi").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkDensityTargeting(
                    DensityAlias.XHDPI,
                    Sets.difference(densities, ImmutableSet.of(DensityAlias.XHDPI)))));
    assertThat(splitsBySuffix.get("fr_xhdpi").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkLanguageTargeting("fr"),
                apkDensityTargeting(
                    DensityAlias.XHDPI,
                    Sets.difference(densities, ImmutableSet.of(DensityAlias.XHDPI)))));
    assertThat(splitsBySuffix.get("xxhdpi").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkDensityTargeting(
                    DensityAlias.XXHDPI,
                    Sets.difference(densities, ImmutableSet.of(DensityAlias.XXHDPI)))));
    assertThat(splitsBySuffix.get("fr_xxhdpi").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkLanguageTargeting("fr"),
                apkDensityTargeting(
                    DensityAlias.XXHDPI,
                    Sets.difference(densities, ImmutableSet.of(DensityAlias.XXHDPI)))));
    assertThat(splitsBySuffix.get("xxxhdpi").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkDensityTargeting(
                    DensityAlias.XXXHDPI,
                    Sets.difference(densities, ImmutableSet.of(DensityAlias.XXXHDPI)))));
    assertThat(splitsBySuffix.get("fr_xxxhdpi").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkLanguageTargeting("fr"),
                apkDensityTargeting(
                    DensityAlias.XXXHDPI,
                    Sets.difference(densities, ImmutableSet.of(DensityAlias.XXXHDPI)))));
    assertThat(splitsBySuffix.get("").getApkTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(DEFAULT_MASTER_SPLIT_SDK_TARGETING);
  }

  @Test
  public void masterSplitGetsNonDensityRelatedConfigs() throws Exception {
    ResourceTable table =
        resourceTable(
            pkg(
                USER_PACKAGE_OFFSET,
                "com.test.app",
                type(
                    0x01,
                    "drawable",
                    entry(
                        0x01,
                        "title_image",
                        fileReference("res/drawable-hdpi/title_image.jpg", HDPI),
                        fileReference(
                            "res/drawable/title_image.jpg", Configuration.getDefaultInstance()))),
                type(
                    0x02,
                    "layout",
                    entry(
                        0x01,
                        "title_layout",
                        fileReference(
                            "res/layout/title_layout.xml", Configuration.getDefaultInstance())))));
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("testModule/res/drawable/title_image.jpg")
            .addFile("testModule/res/drawable-hdpi/title_image.jpg")
            .addFile("testModule/res/layout/title_layout.xml")
            .setResourceTable(table)
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableList<ModuleSplit> splits = createAbiAndDensitySplitter(testModule).splitModule();
    assertThat(splits.stream().map(ModuleSplit::getSplitType).distinct().collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    assertThat(
            splits.stream()
                .map(ModuleSplit::getVariantTargeting)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(lPlusVariantTargeting());

    Optional<ResourceTable> masterTable =
        checkAndReturnTheOnlyMasterSplit(splits).getResourceTable();
    assertThat(masterTable).isPresent();
    assertThat(masterTable.get()).doesNotContainResource("com.test.app:drawable/title_image");
    assertThat(masterTable.get())
        .containsResource("com.test.app:layout/title_layout")
        .withConfigSize(1);
  }

  @Test
  public void testSplitsDontHaveBundleConfigFiles() throws Exception {
    BundleModule bundleModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/dict.dat")
            .setManifest(androidManifest("com.test.app"))
            .setNativeConfig(NativeLibraries.getDefaultInstance())
            .setAssetsConfig(Assets.getDefaultInstance())
            .build();

    ImmutableList<ModuleSplit> splits = createAbiAndDensitySplitter(bundleModule).splitModule();
    assertThat(splits.stream().map(ModuleSplit::getSplitType).distinct().collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    assertThat(
            splits.stream()
                .map(ModuleSplit::getVariantTargeting)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(lPlusVariantTargeting());

    assertThat(splits).isNotEmpty();
    for (ModuleSplit split : splits) {
      ImmutableSet<ZipPath> pathEntries =
          split.getEntries().stream().map(ModuleEntry::getPath).collect(toImmutableSet());
      assertThat(pathEntries).doesNotContain(ZipPath.create("native.pb"));
      assertThat(pathEntries).doesNotContain(ZipPath.create("assets.pb"));
    }
  }

  @Test
  public void nativeSplits_areGenerated() throws Exception {
    NativeLibraries nativeConfig =
        nativeLibraries(targetedNativeDirectory("lib/x86", nativeDirectoryTargeting("x86")));
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .setManifest(androidManifest("com.test.app"))
            .setNativeConfig(nativeConfig)
            .addFile("lib/x86/liba.so")
            .build();

    ImmutableList<ModuleSplit> splits = createAbiAndDensitySplitter(testModule).splitModule();
    assertThat(splits.stream().map(ModuleSplit::getSplitType).distinct().collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    assertThat(
            splits.stream()
                .map(ModuleSplit::getVariantTargeting)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(lPlusVariantTargeting());

    assertThat(splits).hasSize(2);
    boolean hasMasterSplit = false;
    boolean hasX86Split = false;
    ApkTargeting x86SplitTargeting =
        mergeApkTargeting(DEFAULT_MASTER_SPLIT_SDK_TARGETING, apkAbiTargeting(AbiAlias.X86));
    for (ModuleSplit split : splits) {
      if (split.getApkTargeting().equals(DEFAULT_MASTER_SPLIT_SDK_TARGETING)) {
        assertThat(split.isMasterSplit()).isTrue();
        hasMasterSplit = true;
      } else if (split.getApkTargeting().equals(x86SplitTargeting)) {
        assertThat(split.isMasterSplit()).isFalse();
        hasX86Split = true;
      } else {
        fail(String.format("Unexpected split targeting: %s", split.getApkTargeting()));
      }
    }
    assertThat(hasMasterSplit).isTrue();
    assertThat(hasX86Split).isTrue();
  }

  @Test
  public void nativeSplits_lPlusTargeting_withAbiAndUncompressNativeLibsSplitter()
      throws Exception {
    NativeLibraries nativeConfig =
        nativeLibraries(targetedNativeDirectory("lib/x86", nativeDirectoryTargeting("x86")));
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .setManifest(androidManifest("com.test.app"))
            .setNativeConfig(nativeConfig)
            .addFile("lib/x86/liba.so")
            .build();

    ModuleSplitter moduleSplitter =
        ModuleSplitter.createNoStamp(
            testModule,
            BUNDLETOOL_VERSION,
            APP_BUNDLE,
            ApkGenerationConfiguration.builder()
                .setOptimizationDimensions(ImmutableSet.of(ABI))
                .setEnableUncompressedNativeLibraries(true)
                .build(),
            lPlusVariantTargeting(),
            ImmutableSet.of("base", "testModule"));

    List<ModuleSplit> splits = moduleSplitter.splitModule();
    // Base + X86 Split
    assertThat(splits).hasSize(2);
    assertThat(splits.stream().map(ModuleSplit::getSplitType).distinct().collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    assertThat(splits.stream().map(split -> split.getVariantTargeting()).collect(toImmutableSet()))
        .containsExactly(variantMinSdkTargeting(ANDROID_L_API_VERSION));

    ModuleSplit master = splits.stream().filter(ModuleSplit::isMasterSplit).collect(onlyElement());
    ModuleSplit abi =
        splits.stream().filter(not(ModuleSplit::isMasterSplit)).collect(onlyElement());
    assertThat(master.getAndroidManifest().getExtractNativeLibsValue()).hasValue(true);
    assertThat(abi.findEntry("lib/x86/liba.so").get().getForceUncompressed()).isFalse();
  }

  @Test
  public void nativeSplits_mPlusTargeting_withAbiAndUncompressNativeLibsSplitter()
      throws Exception {
    NativeLibraries nativeConfig =
        nativeLibraries(targetedNativeDirectory("lib/x86", nativeDirectoryTargeting("x86")));
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .setManifest(androidManifest("com.test.app"))
            .setNativeConfig(nativeConfig)
            .addFile("lib/x86/liba.so")
            .build();

    ModuleSplitter moduleSplitter =
        ModuleSplitter.createNoStamp(
            testModule,
            BUNDLETOOL_VERSION,
            APP_BUNDLE,
            ApkGenerationConfiguration.builder()
                .setOptimizationDimensions(ImmutableSet.of(ABI))
                .setEnableUncompressedNativeLibraries(true)
                .build(),
            variantMinSdkTargeting(ANDROID_M_API_VERSION),
            ImmutableSet.of("base", "testModule"));

    List<ModuleSplit> splits = moduleSplitter.splitModule();
    // Base + X86 Split
    assertThat(splits).hasSize(2);
    assertThat(splits.stream().map(ModuleSplit::getSplitType).distinct().collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    assertThat(splits.stream().map(split -> split.getVariantTargeting()).collect(toImmutableSet()))
        .containsExactly(variantMinSdkTargeting(ANDROID_M_API_VERSION));

    ModuleSplit master = splits.stream().filter(ModuleSplit::isMasterSplit).collect(onlyElement());
    ModuleSplit abi =
        splits.stream().filter(not(ModuleSplit::isMasterSplit)).collect(onlyElement());
    assertThat(master.getAndroidManifest().getExtractNativeLibsValue()).hasValue(false);
    assertThat(abi.findEntry("lib/x86/liba.so").get().getForceUncompressed()).isTrue();
  }

  @Test
  public void nativeSplits_mPlusTargeting_disabledUncompressedSplitter() {
    NativeLibraries nativeConfig =
        nativeLibraries(targetedNativeDirectory("lib/x86", nativeDirectoryTargeting("x86")));
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .setManifest(androidManifest("com.test.app"))
            .setNativeConfig(nativeConfig)
            .addFile("lib/x86/liba.so")
            .build();

    ModuleSplitter moduleSplitter =
        ModuleSplitter.createNoStamp(
            testModule,
            BUNDLETOOL_VERSION,
            APP_BUNDLE,
            ApkGenerationConfiguration.builder()
                .setOptimizationDimensions(ImmutableSet.of(ABI))
                .setEnableUncompressedNativeLibraries(false)
                .build(),
            variantMinSdkTargeting(ANDROID_M_API_VERSION),
            ImmutableSet.of("base", "testModule"));

    List<ModuleSplit> splits = moduleSplitter.splitModule();
    assertThat(splits).hasSize(2);

    ModuleSplit master = splits.stream().filter(ModuleSplit::isMasterSplit).collect(onlyElement());
    ModuleSplit abi =
        splits.stream().filter(not(ModuleSplit::isMasterSplit)).collect(onlyElement());
    assertThat(master.getAndroidManifest().getExtractNativeLibsValue()).hasValue(true);
    assertThat(abi.findEntry("lib/x86/liba.so").get().getForceUncompressed()).isFalse();
  }

  @Test
  public void masterSplit_hasAllOtherApkComponents() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .setManifest(androidManifest("com.test.app"))
            .addFile("assets/some_asset.txt")
            .addFile("dex/classes.dex")
            .addFile("root/some_other_file.txt")
            .build();

    ImmutableList<ModuleSplit> splits = createAbiAndDensitySplitter(testModule).splitModule();
    assertThat(splits.stream().map(ModuleSplit::getSplitType).distinct().collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    assertThat(
            splits.stream()
                .map(ModuleSplit::getVariantTargeting)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(lPlusVariantTargeting());

    assertThat(splits).hasSize(1); // the example has nothing to split on.
    ModuleSplit masterSplit = splits.get(0);
    assertThat(masterSplit.getApkTargeting()).isEqualTo(DEFAULT_MASTER_SPLIT_SDK_TARGETING);
    assertThat(masterSplit.isMasterSplit()).isTrue();

    ImmutableSet<String> actualFiles =
        masterSplit.getEntries().stream()
            .map(ModuleEntry::getPath)
            .map(ZipPath::toString)
            .collect(toImmutableSet());
    ImmutableSet<String> expectedFiles =
        ImmutableSet.of("dex/classes.dex", "assets/some_asset.txt", "root/some_other_file.txt");
    assertThat(actualFiles).containsAtLeastElementsIn(expectedFiles);
  }

  @Test
  public void textureCompressionFormatAsset_splitting_and_merging() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/main#tcf_etc1/image.jpg")
            .addFile("assets/main#tcf_s3tc/image.jpg")
            .addFile("assets/other#tcf_etc1/image.jpg")
            .addFile("assets/other#tcf_s3tc/image.jpg")
            .addFile("assets/other#tcf_atc/image.jpg")
            .addFile("dex/classes.dex")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/main#tcf_etc1",
                        assetsDirectoryTargeting(
                            textureCompressionTargeting(ETC1_RGB8, ImmutableSet.of(S3TC)))),
                    targetedAssetsDirectory(
                        "assets/main#tcf_s3tc",
                        assetsDirectoryTargeting(
                            textureCompressionTargeting(S3TC, ImmutableSet.of(ETC1_RGB8)))),
                    targetedAssetsDirectory(
                        "assets/other#tcf_etc1",
                        assetsDirectoryTargeting(
                            textureCompressionTargeting(ETC1_RGB8, ImmutableSet.of(S3TC, ATC)))),
                    targetedAssetsDirectory(
                        "assets/other#tcf_s3tc",
                        assetsDirectoryTargeting(
                            textureCompressionTargeting(S3TC, ImmutableSet.of(ETC1_RGB8, ATC)))),
                    targetedAssetsDirectory(
                        "assets/other#tcf_atc",
                        assetsDirectoryTargeting(
                            textureCompressionTargeting(ATC, ImmutableSet.of(ETC1_RGB8, S3TC))))))
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableList<ModuleSplit> splits =
        createTextureCompressionFormatSplitter(testModule).splitModule();

    // expected 6 splits: etc1_rgb8 (with 2 sets of alternatives), s3tc (2 sets of alternatives),
    // atc and the master split.
    assertThat(splits).hasSize(6);
    assertThat(splits.stream().map(ModuleSplit::getSplitType).distinct().collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    assertThat(
            splits.stream()
                .map(ModuleSplit::getVariantTargeting)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(lPlusVariantTargeting());

    ImmutableList<ModuleSplit> defaultSplits =
        getSplitsWithTargetingEqualTo(splits, DEFAULT_MASTER_SPLIT_SDK_TARGETING);
    assertThat(defaultSplits).hasSize(1);
    assertThat(extractPaths(defaultSplits.get(0).getEntries())).containsExactly("dex/classes.dex");

    ImmutableList<ModuleSplit> mainEtc1Splits =
        getSplitsWithTargetingEqualTo(
            splits,
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkTextureTargeting(
                    textureCompressionTargeting(ETC1_RGB8, ImmutableSet.of(S3TC)))));
    assertThat(mainEtc1Splits).hasSize(1);
    assertThat(extractPaths(mainEtc1Splits.get(0).getEntries()))
        .containsExactly("assets/main/image.jpg");

    ImmutableList<ModuleSplit> mainS3tcSplits =
        getSplitsWithTargetingEqualTo(
            splits,
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkTextureTargeting(
                    textureCompressionTargeting(S3TC, ImmutableSet.of(ETC1_RGB8)))));
    assertThat(mainS3tcSplits).hasSize(1);
    assertThat(extractPaths(mainS3tcSplits.get(0).getEntries()))
        .containsExactly("assets/main/image.jpg");

    ImmutableList<ModuleSplit> otherEtc1Splits =
        getSplitsWithTargetingEqualTo(
            splits,
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkTextureTargeting(
                    textureCompressionTargeting(ETC1_RGB8, ImmutableSet.of(ATC, S3TC)))));
    assertThat(otherEtc1Splits).hasSize(1);
    assertThat(extractPaths(otherEtc1Splits.get(0).getEntries()))
        .containsExactly("assets/other/image.jpg");

    ImmutableList<ModuleSplit> otherS3tcSplits =
        getSplitsWithTargetingEqualTo(
            splits,
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkTextureTargeting(
                    textureCompressionTargeting(S3TC, ImmutableSet.of(ETC1_RGB8, ATC)))));
    assertThat(otherS3tcSplits).hasSize(1);
    assertThat(extractPaths(otherS3tcSplits.get(0).getEntries()))
        .containsExactly("assets/other/image.jpg");

    ImmutableList<ModuleSplit> otherAtcSplits =
        getSplitsWithTargetingEqualTo(
            splits,
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkTextureTargeting(
                    textureCompressionTargeting(ATC, ImmutableSet.of(ETC1_RGB8, S3TC)))));
    assertThat(otherAtcSplits).hasSize(1);
    assertThat(extractPaths(otherAtcSplits.get(0).getEntries()))
        .containsExactly("assets/other/image.jpg");
  }

  @Test
  public void nativeSplits_pPlusTargeting_withDexCompressionSplitter() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("dex/classes.dex")
            .setManifest(androidManifest("com.test.app"))
            .build();

    ModuleSplitter moduleSplitter =
        ModuleSplitter.createNoStamp(
            testModule,
            BUNDLETOOL_VERSION,
            APP_BUNDLE,
            ApkGenerationConfiguration.builder().setEnableDexCompressionSplitter(true).build(),
            variantMinSdkTargeting(ANDROID_Q_API_VERSION),
            ImmutableSet.of("base", "testModule"));

    List<ModuleSplit> splits = moduleSplitter.splitModule();
    assertThat(splits).hasSize(1);
    ModuleSplit moduleSplit = Iterables.getOnlyElement(splits);
    assertThat(moduleSplit.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(moduleSplit.getVariantTargeting())
        .isEqualTo(variantMinSdkTargeting(ANDROID_Q_API_VERSION));
    assertThat(moduleSplit.findEntry("dex/classes.dex").get().getForceUncompressed()).isTrue();
  }

  @Test
  public void nativeSplits_withSparseEncodingSplitter_withSdk32Variant() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule").setManifest(androidManifest("com.test.app")).build();

    ModuleSplitter moduleSplitter =
        ModuleSplitter.createNoStamp(
            testModule,
            BUNDLETOOL_VERSION,
            APP_BUNDLE,
            ApkGenerationConfiguration.builder().setEnableSparseEncodingVariant(true).build(),
            variantMinSdkTargeting(ANDROID_S_V2_API_VERSION),
            ImmutableSet.of("base", "testModule"));

    ImmutableList<ModuleSplit> splits = moduleSplitter.splitModule();
    assertThat(splits).hasSize(1);
    ModuleSplit moduleSplit = Iterables.getOnlyElement(splits);
    assertThat(moduleSplit.getVariantTargeting())
        .isEqualTo(variantMinSdkTargeting(ANDROID_S_V2_API_VERSION));
    assertThat(moduleSplit.getSparseEncoding()).isTrue();
  }

  @Test
  public void nativeSplits_withSparseEncodingSplitter_withSdk21Variant() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule").setManifest(androidManifest("com.test.app")).build();

    ModuleSplitter moduleSplitter =
        ModuleSplitter.createNoStamp(
            testModule,
            BUNDLETOOL_VERSION,
            APP_BUNDLE,
            ApkGenerationConfiguration.builder().setEnableSparseEncodingVariant(true).build(),
            variantMinSdkTargeting(ANDROID_L_API_VERSION),
            ImmutableSet.of("base", "testModule"));

    ImmutableList<ModuleSplit> splits = moduleSplitter.splitModule();
    assertThat(splits).hasSize(1);
    ModuleSplit moduleSplit = Iterables.getOnlyElement(splits);
    assertThat(moduleSplit.getVariantTargeting())
        .isEqualTo(variantMinSdkTargeting(ANDROID_L_API_VERSION));
    assertThat(moduleSplit.getSparseEncoding()).isFalse();
  }

  @Test
  public void nativeSplits_lPlusTargeting_withDexCompressionSplitter() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("dex/classes.dex")
            .setManifest(androidManifest("com.test.app"))
            .build();

    ModuleSplitter moduleSplitter =
        ModuleSplitter.createNoStamp(
            testModule,
            BUNDLETOOL_VERSION,
            APP_BUNDLE,
            ApkGenerationConfiguration.builder().setEnableDexCompressionSplitter(true).build(),
            lPlusVariantTargeting(),
            ImmutableSet.of("base", "testModule"));

    List<ModuleSplit> splits = moduleSplitter.splitModule();
    assertThat(splits).hasSize(1);
    ModuleSplit moduleSplit = Iterables.getOnlyElement(splits);
    assertThat(moduleSplit.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(moduleSplit.getVariantTargeting())
        .isEqualTo(variantMinSdkTargeting(ANDROID_L_API_VERSION));
    assertThat(moduleSplit.findEntry("dex/classes.dex").get().getForceUncompressed()).isFalse();
  }

  @Test
  public void assetsLanguageSplitting() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/languages#lang_cz/pack.pak")
            .addFile("assets/languages#lang_fr/pack.pak")
            .addFile("dex/classes.dex")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/languages#lang_cz",
                        assetsDirectoryTargeting(languageTargeting("cz"))),
                    targetedAssetsDirectory(
                        "assets/languages#lang_fr",
                        assetsDirectoryTargeting(languageTargeting("fr")))))
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableList<ModuleSplit> splits =
        createAbiDensityAndLanguageSplitter(testModule).splitModule();
    assertThat(splits).hasSize(3); // FR, CZ and the master split.
    assertThat(splits.stream().map(ModuleSplit::getSplitType).distinct().collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    assertThat(
            splits.stream()
                .map(ModuleSplit::getVariantTargeting)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(lPlusVariantTargeting());

    ImmutableList<ModuleSplit> masterSplits =
        getSplitsWithTargetingEqualTo(splits, DEFAULT_MASTER_SPLIT_SDK_TARGETING);
    assertThat(masterSplits).hasSize(1);
    assertThat(extractPaths(masterSplits.get(0).getEntries())).containsExactly("dex/classes.dex");
    ImmutableList<ModuleSplit> czSplits =
        getSplitsWithTargetingEqualTo(
            splits,
            mergeApkTargeting(DEFAULT_MASTER_SPLIT_SDK_TARGETING, apkLanguageTargeting("cz")));
    assertThat(czSplits).hasSize(1);
    assertThat(extractPaths(czSplits.get(0).getEntries()))
        .containsExactly("assets/languages#lang_cz/pack.pak");
    ImmutableList<ModuleSplit> frSplits =
        getSplitsWithTargetingEqualTo(
            splits,
            mergeApkTargeting(DEFAULT_MASTER_SPLIT_SDK_TARGETING, apkLanguageTargeting("fr")));
    assertThat(frSplits).hasSize(1);
    assertThat(extractPaths(frSplits.get(0).getEntries()))
        .containsExactly("assets/languages#lang_fr/pack.pak");
  }

  @Test
  public void assetsAndResourceLanguageSplitting() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/languages#lang_cz/pack.pak")
            .addFile("assets/languages#lang_fr/pack.pak")
            .addFile("assets/languages/pack.pak")
            .addFile("dex/classes.dex")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/languages#lang_cz",
                        assetsDirectoryTargeting(languageTargeting("cz"))),
                    targetedAssetsDirectory(
                        "assets/languages#lang_fr",
                        assetsDirectoryTargeting(languageTargeting("fr"))),
                    targetedAssetsDirectory(
                        "assets/languages",
                        assetsDirectoryTargeting(alternativeLanguageTargeting("cz", "fr")))))
            .setResourceTable(
                getWelcomeLabel(
                    value("Welcome", Configuration.getDefaultInstance()),
                    value("Vítejte", locale("cz")),
                    value("Bienvenue", locale("fr"))))
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableList<ModuleSplit> splits =
        createAbiDensityAndLanguageSplitter(testModule).splitModule();
    // FR, CZ, non-CZ-or-FR and the master split.
    assertThat(splits).hasSize(4);
    assertThat(splits.stream().map(ModuleSplit::getSplitType).distinct().collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    assertThat(
            splits.stream()
                .map(ModuleSplit::getVariantTargeting)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(lPlusVariantTargeting());

    ImmutableList<ModuleSplit> masterSplits =
        getSplitsWithTargetingEqualTo(splits, DEFAULT_MASTER_SPLIT_SDK_TARGETING);
    assertThat(masterSplits).hasSize(1);
    assertThat(extractPaths(masterSplits.get(0).getEntries())).containsExactly("dex/classes.dex");
    assertThat(masterSplits.get(0).getResourceTable())
        .hasValue(getWelcomeLabel(value("Welcome", Configuration.getDefaultInstance())));

    ImmutableList<ModuleSplit> czSplits =
        getSplitsWithTargetingEqualTo(
            splits,
            mergeApkTargeting(DEFAULT_MASTER_SPLIT_SDK_TARGETING, apkLanguageTargeting("cz")));
    assertThat(czSplits).hasSize(1);
    assertThat(extractPaths(czSplits.get(0).getEntries()))
        .containsExactly("assets/languages#lang_cz/pack.pak");
    assertThat(czSplits.get(0).getResourceTable())
        .hasValue(getWelcomeLabel(value("Vítejte", locale("cz"))));

    ImmutableList<ModuleSplit> frSplits =
        getSplitsWithTargetingEqualTo(
            splits,
            mergeApkTargeting(DEFAULT_MASTER_SPLIT_SDK_TARGETING, apkLanguageTargeting("fr")));
    assertThat(frSplits).hasSize(1);
    assertThat(extractPaths(frSplits.get(0).getEntries()))
        .containsExactly("assets/languages#lang_fr/pack.pak");
    assertThat(frSplits.get(0).getResourceTable())
        .hasValue(getWelcomeLabel(value("Bienvenue", locale("fr"))));

    ImmutableList<ModuleSplit> nonCzNonFrSplits =
        getSplitsWithTargetingEqualTo(
            splits,
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING, apkAlternativeLanguageTargeting("cz", "fr")));
    assertThat(nonCzNonFrSplits).hasSize(1);
    assertThat(extractPaths(nonCzNonFrSplits.get(0).getEntries()))
        .containsExactly("assets/languages/pack.pak");
    assertThat(nonCzNonFrSplits.get(0).getResourceTable()).isEmpty();
  }

  private ResourceTable getWelcomeLabel(ConfigValue... values) {
    return resourceTable(
        pkg(
            USER_PACKAGE_OFFSET,
            "com.test.app",
            type(0x01, "string", entry(0x01, "welcome_label", values))));
  }

  @Test
  public void deviceTierAsset_splitting_and_merging() {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/main#tier_0/image.jpg")
            .addFile("assets/main#tier_1/image.jpg")
            .addFile("dex/classes.dex")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/main#tier_0",
                        assetsDirectoryTargeting(
                            deviceTierTargeting(
                                /* value= */ 0, /* alternatives= */ ImmutableList.of(1)))),
                    targetedAssetsDirectory(
                        "assets/main#tier_1",
                        assetsDirectoryTargeting(
                            deviceTierTargeting(
                                /* value= */ 1, /* alternatives= */ ImmutableList.of(0))))))
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableList<ModuleSplit> splits = createDeviceTierSplitter(testModule).splitModule();

    // expected 3 splits: low tier, medium tier and the master split.
    assertThat(splits).hasSize(3);
    assertThat(splits.stream().map(ModuleSplit::getSplitType).distinct().collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    assertThat(
            splits.stream()
                .map(ModuleSplit::getVariantTargeting)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(lPlusVariantTargeting());

    ImmutableList<ModuleSplit> defaultSplits =
        getSplitsWithTargetingEqualTo(splits, DEFAULT_MASTER_SPLIT_SDK_TARGETING);
    assertThat(defaultSplits).hasSize(1);
    assertThat(extractPaths(defaultSplits.get(0).getEntries())).containsExactly("dex/classes.dex");

    ImmutableList<ModuleSplit> lowTierSplits =
        getSplitsWithTargetingEqualTo(
            splits,
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkDeviceTierTargeting(
                    deviceTierTargeting(/* value= */ 0, /* alternatives= */ ImmutableList.of(1)))));
    assertThat(lowTierSplits).hasSize(1);
    assertThat(extractPaths(lowTierSplits.get(0).getEntries()))
        .containsExactly("assets/main#tier_0/image.jpg");

    ImmutableList<ModuleSplit> mediumTierSplits =
        getSplitsWithTargetingEqualTo(
            splits,
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkDeviceTierTargeting(
                    deviceTierTargeting(/* value= */ 1, /* alternatives= */ ImmutableList.of(0)))));
    assertThat(mediumTierSplits).hasSize(1);
    assertThat(extractPaths(mediumTierSplits.get(0).getEntries()))
        .containsExactly("assets/main#tier_1/image.jpg");
  }

  @Test
  public void countrySetAsset_splitting_and_merging() {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/main#countries_latam/image.jpg")
            .addFile("assets/main#countries_sea/image.jpg")
            .addFile("dex/classes.dex")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/main#countries_latam",
                        assetsDirectoryTargeting(
                            countrySetTargeting(
                                ImmutableList.of("latam"), ImmutableList.of("sea")))),
                    targetedAssetsDirectory(
                        "assets/main#countries_sea",
                        assetsDirectoryTargeting(
                            countrySetTargeting(
                                ImmutableList.of("sea"), ImmutableList.of("latam"))))))
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableList<ModuleSplit> splits = createCountrySetSplitter(testModule).splitModule();

    // expected 3 splits: latam, sea and the master split.
    assertThat(splits).hasSize(3);
    assertThat(splits.stream().map(ModuleSplit::getSplitType).distinct().collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    assertThat(
            splits.stream()
                .map(ModuleSplit::getVariantTargeting)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(lPlusVariantTargeting());

    ImmutableList<ModuleSplit> defaultSplits =
        getSplitsWithTargetingEqualTo(splits, DEFAULT_MASTER_SPLIT_SDK_TARGETING);
    assertThat(defaultSplits).hasSize(1);
    assertThat(extractPaths(defaultSplits.get(0).getEntries())).containsExactly("dex/classes.dex");

    ImmutableList<ModuleSplit> latamSplits =
        getSplitsWithTargetingEqualTo(
            splits,
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkCountrySetTargeting(
                    countrySetTargeting(ImmutableList.of("latam"), ImmutableList.of("sea")))));
    assertThat(latamSplits).hasSize(1);
    assertThat(extractPaths(latamSplits.get(0).getEntries()))
        .containsExactly("assets/main#countries_latam/image.jpg");

    ImmutableList<ModuleSplit> seaSplits =
        getSplitsWithTargetingEqualTo(
            splits,
            mergeApkTargeting(
                DEFAULT_MASTER_SPLIT_SDK_TARGETING,
                apkCountrySetTargeting(
                    countrySetTargeting(ImmutableList.of("sea"), ImmutableList.of("latam")))));
    assertThat(seaSplits).hasSize(1);
    assertThat(extractPaths(seaSplits.get(0).getEntries()))
        .containsExactly("assets/main#countries_sea/image.jpg");
  }

  @Test
  public void targetsPreLOnlyInManifest_throws() throws Exception {
    int preL = 20;
    BundleModule bundleModule =
        new BundleModuleBuilder("testModule")
            .setManifest(androidManifest("com.test.app", withMaxSdkVersion(preL)))
            .build();

    CommandExecutionException exception =
        assertThrows(
            CommandExecutionException.class,
            () ->
                ModuleSplitter.createForTest(
                        bundleModule,
                        AppBundle.buildFromModules(
                            ImmutableList.of(bundleModule, BASE_MODULE),
                            BundleConfig.getDefaultInstance(),
                            BundleMetadata.builder().build()),
                        BUNDLETOOL_VERSION)
                    .splitModule());

    assertThat(exception)
        .hasMessageThat()
        .contains("does not target devices on Android L or above");
  }

  /**
   * Tests that modules belonging to same variant with potentially same splitId, are resolved to
   * different splitIds.
   */
  @Test
  public void resolvesSplitIdSuffixes_singleVariant() throws Exception {
    // Note: this test will be extended once we add support for new directory groups.
    ModuleSplit assetsSplit1 =
        ModuleSplit.builder()
            .setModuleName(BundleModuleName.create("base"))
            .setEntries(ImmutableList.of())
            .setApkTargeting(
                apkTextureTargeting(textureCompressionTargeting(ATC, ImmutableSet.of(S3TC))))
            .setMasterSplit(false)
            .setAndroidManifest(AndroidManifest.create(androidManifest("com.test.app")))
            .setVariantTargeting(VariantTargeting.getDefaultInstance())
            .build();
    ModuleSplit assetsSplit2 =
        ModuleSplit.builder()
            .setModuleName(BundleModuleName.create("base"))
            .setEntries(ImmutableList.of())
            .setApkTargeting(
                apkTextureTargeting(textureCompressionTargeting(ATC, ImmutableSet.of(ETC1_RGB8))))
            .setMasterSplit(false)
            .setAndroidManifest(AndroidManifest.create(androidManifest("com.test.app")))
            .setVariantTargeting(VariantTargeting.getDefaultInstance())
            .build();
    BundleModule bundleModule =
        new BundleModuleBuilder("base").setManifest(androidManifest("com.test.app")).build();

    ModuleSplitter moduleSplitter =
        ModuleSplitter.createForTest(
            bundleModule,
            AppBundle.buildFromModules(
                ImmutableList.of(bundleModule),
                BundleConfig.getDefaultInstance(),
                BundleMetadata.builder().build()),
            BUNDLETOOL_VERSION);
    assetsSplit1 = moduleSplitter.writeSplitIdInManifest(assetsSplit1);
    assetsSplit2 = moduleSplitter.writeSplitIdInManifest(assetsSplit2);

    assertThat(assetsSplit1.getAndroidManifest().getSplitId()).hasValue("config.atc");
    assertThat(assetsSplit2.getAndroidManifest().getSplitId()).hasValue("config.atc_2");
  }

  /**
   * Tests that modules belonging to different variants with potentially same splitId, aren't
   * resolved to different splitIds.
   */
  @Test
  public void resolvesSplitIdSuffixes_multipleVariants() throws Exception {
    ModuleSplit assetsSplit1 =
        ModuleSplit.builder()
            .setModuleName(BundleModuleName.create("base"))
            .setEntries(ImmutableList.of())
            .setApkTargeting(
                apkTextureTargeting(textureCompressionTargeting(ATC, ImmutableSet.of(S3TC))))
            .setMasterSplit(false)
            .setAndroidManifest(AndroidManifest.create(androidManifest("com.test.app")))
            .setVariantTargeting(VariantTargeting.getDefaultInstance())
            .build();
    ModuleSplit assetsSplit2 =
        ModuleSplit.builder()
            .setModuleName(BundleModuleName.create("base"))
            .setEntries(ImmutableList.of())
            .setApkTargeting(
                apkTextureTargeting(textureCompressionTargeting(ATC, ImmutableSet.of(ETC1_RGB8))))
            .setMasterSplit(false)
            .setAndroidManifest(AndroidManifest.create(androidManifest("com.test.app")))
            .setVariantTargeting(lPlusVariantTargeting())
            .build();

    BundleModule bundleModule =
        new BundleModuleBuilder("base").setManifest(androidManifest("com.test.app")).build();

    ModuleSplitter moduleSplitter =
        ModuleSplitter.createForTest(
            bundleModule,
            AppBundle.buildFromModules(
                ImmutableList.of(bundleModule),
                BundleConfig.getDefaultInstance(),
                BundleMetadata.builder().build()),
            BUNDLETOOL_VERSION);
    assetsSplit1 = moduleSplitter.writeSplitIdInManifest(assetsSplit1);
    assetsSplit2 = moduleSplitter.writeSplitIdInManifest(assetsSplit2);

    assertThat(assetsSplit1.getAndroidManifest().getSplitId()).hasValue("config.atc");
    assertThat(assetsSplit2.getAndroidManifest().getSplitId()).hasValue("config.atc");
  }

  @Test
  public void splitNameRemovedForInstalledSplit() throws Exception {
    XmlNode manifest =
        androidManifest(
            "com.test.app",
            withMainActivity("MainActivity"),
            withSplitNameActivity("FooActivity", "foo"));
    BundleModule bundleModule = new BundleModuleBuilder("testModule").setManifest(manifest).build();

    ImmutableList<ModuleSplit> moduleSplits =
        ModuleSplitter.createForTest(
                bundleModule,
                AppBundle.buildFromModules(
                    ImmutableList.of(bundleModule, BASE_MODULE),
                    BundleConfig.getDefaultInstance(),
                    BundleMetadata.builder().build()),
                BUNDLETOOL_VERSION)
            .splitModule();

    assertThat(moduleSplits).hasSize(1);
    ModuleSplit masterSplit = moduleSplits.get(0);
    ImmutableList<XmlElement> activities =
        masterSplit
            .getAndroidManifest()
            .getManifestRoot()
            .getElement()
            .getChildElement("application")
            .getChildrenElements(ACTIVITY_ELEMENT_NAME)
            .map(XmlProtoElement::getProto)
            .collect(toImmutableList());
    assertThat(activities).hasSize(2);
    XmlElement activityElement = activities.get(1);
    assertThat(activityElement.getAttributeList())
        .containsExactly(
            xmlAttribute(ANDROID_NAMESPACE_URI, "name", NAME_RESOURCE_ID, "FooActivity"));
  }

  @Test
  public void splitNameNotRemovedForInstantSplit() throws Exception {
    XmlNode manifest =
        androidManifest(
            "com.test.app",
            withMainActivity("MainActivity"),
            withSplitNameActivity("FooActivity", "testModule"));
    BundleModule bundleModule = new BundleModuleBuilder("testModule").setManifest(manifest).build();

    ImmutableList<ModuleSplit> moduleSplits =
        ModuleSplitter.createNoStamp(
                bundleModule,
                BUNDLETOOL_VERSION,
                APP_BUNDLE,
                ApkGenerationConfiguration.builder().setForInstantAppVariants(true).build(),
                lPlusVariantTargeting(),
                ImmutableSet.of("base", "testModule"))
            .splitModule();

    assertThat(moduleSplits).hasSize(1);
    ModuleSplit masterSplit = moduleSplits.get(0);
    ImmutableList<XmlElement> activities =
        masterSplit
            .getAndroidManifest()
            .getManifestRoot()
            .getElement()
            .getChildElement("application")
            .getChildrenElements(ACTIVITY_ELEMENT_NAME)
            .map(XmlProtoElement::getProto)
            .collect(toImmutableList());
    assertThat(activities).hasSize(2);
    XmlElement activityElement = activities.get(1);
    assertThat(activityElement.getAttributeList())
        .containsExactly(
            xmlAttribute(ANDROID_NAMESPACE_URI, "name", NAME_RESOURCE_ID, "FooActivity"),
            xmlAttribute(ANDROID_NAMESPACE_URI, "splitName", SPLIT_NAME_RESOURCE_ID, "testModule"));
  }

  @Test
  public void applicationElementAdded() throws Exception {
    XmlNode manifest = androidManifest("com.test.app", clearApplication());
    checkState(
        !AndroidManifest.create(manifest).hasApplicationElement(),
        "Expected manifest with no <application> element.");
    BundleModule bundleModule = new BundleModuleBuilder("testModule").setManifest(manifest).build();

    ImmutableList<ModuleSplit> moduleSplits =
        ModuleSplitter.createForTest(
                bundleModule,
                AppBundle.buildFromModules(
                    ImmutableList.of(bundleModule, BASE_MODULE),
                    BundleConfig.getDefaultInstance(),
                    BundleMetadata.builder().build()),
                BUNDLETOOL_VERSION)
            .splitModule();

    assertThat(moduleSplits).hasSize(1);
    ModuleSplit masterSplit = moduleSplits.get(0);
    assertThat(
            masterSplit
                .getAndroidManifest()
                .getManifestRoot()
                .getElement()
                .getOptionalChildElement(APPLICATION_ELEMENT_NAME))
        .isPresent();
  }

  @Test
  public void nonInstantActivityRemovedForInstantManifest() throws Exception {
    XmlNode manifest =
        androidManifest(
            "com.test.app",
            withMainActivity("MainActivity"),
            withSplitNameActivity("FooActivity", "onDemandModule"));
    BundleModule bundleModule =
        new BundleModuleBuilder("onDemandModule").setManifest(manifest).build();

    ImmutableList<ModuleSplit> moduleSplits =
        ModuleSplitter.createNoStamp(
                bundleModule,
                BUNDLETOOL_VERSION,
                APP_BUNDLE,
                ApkGenerationConfiguration.builder().setForInstantAppVariants(true).build(),
                lPlusVariantTargeting(),
                ImmutableSet.of("base"))
            .splitModule();

    assertThat(moduleSplits).hasSize(1);
    ModuleSplit masterSplit = moduleSplits.get(0);
    ImmutableList<XmlElement> activities =
        masterSplit
            .getAndroidManifest()
            .getManifestRoot()
            .getElement()
            .getChildElement("application")
            .getChildrenElements(ACTIVITY_ELEMENT_NAME)
            .map(XmlProtoElement::getProto)
            .collect(toImmutableList());
    assertThat(activities).hasSize(1);
    XmlElement activityElement = activities.get(0);
    assertThat(activityElement.getAttributeList())
        .containsExactly(
            xmlAttribute(ANDROID_NAMESPACE_URI, "name", NAME_RESOURCE_ID, "MainActivity"));
  }

  @Test
  public void instantManifestChanges_addsMinSdkVersion() throws Exception {
    BundleModule bundleModule =
        new BundleModuleBuilder("testModule")
            .setManifest(androidManifest("com.test.app", withInstant(true)))
            .build();

    ImmutableList<ModuleSplit> moduleSplits =
        ModuleSplitter.createNoStamp(
                bundleModule,
                BUNDLETOOL_VERSION,
                APP_BUNDLE,
                ApkGenerationConfiguration.builder().setForInstantAppVariants(true).build(),
                lPlusVariantTargeting(),
                ImmutableSet.of("base", "testModule"))
            .splitModule();

    assertThat(moduleSplits).hasSize(1);
    ModuleSplit masterSplit = moduleSplits.get(0);
    assertThat(masterSplit.getVariantTargeting()).isEqualTo(lPlusVariantTargeting());
    assertThat(masterSplit.isMasterSplit()).isTrue();
    assertThat(masterSplit.getApkTargeting()).isEqualTo(apkMinSdkTargeting(21));
    assertThat(masterSplit.getSplitType()).isEqualTo(SplitType.INSTANT);
    assertThat(masterSplit.getAndroidManifest().getTargetSandboxVersion()).hasValue(2);
    assertThat(masterSplit.getAndroidManifest().getMinSdkVersion()).hasValue(21);
  }

  @Test
  public void instantManifestChanges_keepsMinSdkVersion() throws Exception {
    int minSdk = 22;
    BundleModule bundleModule =
        new BundleModuleBuilder("testModule")
            .setManifest(
                androidManifest("com.test.app", withMinSdkVersion(minSdk), withInstant(true)))
            .build();

    ImmutableList<ModuleSplit> moduleSplits =
        ModuleSplitter.createNoStamp(
                bundleModule,
                BUNDLETOOL_VERSION,
                APP_BUNDLE,
                ApkGenerationConfiguration.builder().setForInstantAppVariants(true).build(),
                lPlusVariantTargeting(),
                ImmutableSet.of("base", "testModule"))
            .splitModule();

    assertThat(moduleSplits).hasSize(1);
    ModuleSplit masterSplit = moduleSplits.get(0);
    assertThat(masterSplit.getVariantTargeting()).isEqualTo(lPlusVariantTargeting());
    assertThat(masterSplit.isMasterSplit()).isTrue();
    assertThat(masterSplit.getApkTargeting()).isEqualTo(apkMinSdkTargeting(minSdk));
    assertThat(masterSplit.getSplitType()).isEqualTo(SplitType.INSTANT);
    assertThat(masterSplit.getAndroidManifest().getTargetSandboxVersion()).hasValue(2);
    assertThat(masterSplit.getAndroidManifest().getMinSdkVersion()).hasValue(minSdk);
  }

  @Test
  public void instantManifestChanges_updatesMinSdkVersion() throws Exception {
    BundleModule bundleModule =
        new BundleModuleBuilder("testModule")
            .setManifest(androidManifest("com.test.app", withMinSdkVersion(19), withInstant(true)))
            .build();

    ImmutableList<ModuleSplit> moduleSplits =
        ModuleSplitter.createNoStamp(
                bundleModule,
                BUNDLETOOL_VERSION,
                APP_BUNDLE,
                ApkGenerationConfiguration.builder().setForInstantAppVariants(true).build(),
                lPlusVariantTargeting(),
                ImmutableSet.of("base", "testModule"))
            .splitModule();

    assertThat(moduleSplits).hasSize(1);
    ModuleSplit masterSplit = moduleSplits.get(0);
    assertThat(masterSplit.getVariantTargeting()).isEqualTo(lPlusVariantTargeting());
    assertThat(masterSplit.isMasterSplit()).isTrue();
    assertThat(masterSplit.getApkTargeting()).isEqualTo(apkMinSdkTargeting(21));
    assertThat(masterSplit.getSplitType()).isEqualTo(SplitType.INSTANT);
    assertThat(masterSplit.getAndroidManifest().getTargetSandboxVersion()).hasValue(2);
    assertThat(masterSplit.getAndroidManifest().getMinSdkVersion()).hasValue(21);
  }

  @Test
  public void instantManifestChanges_updatesMinSdkVersionFromBaseModule_flagEnabled()
      throws Exception {
    BundleModule bundleModule =
        new BundleModuleBuilder("testModule")
            .setManifest(androidManifest("com.test.app", withMinSdkVersion(19), withInstant(true)))
            .build();
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .addFile("dex/classes.dex")
            .setManifest(androidManifest("com.test.app", withMinSdkVersion(23), withInstant(true)))
            .build();
    AppBundle appBundle =
        new AppBundleBuilder().addModule(baseModule).addModule(bundleModule).build();

    ImmutableList<ModuleSplit> moduleSplits =
        ModuleSplitter.createNoStamp(
                bundleModule,
                BUNDLETOOL_VERSION,
                appBundle,
                ApkGenerationConfiguration.builder()
                    .setForInstantAppVariants(true)
                    .setEnableBaseModuleMinSdkAsDefaultTargeting(true)
                    .build(),
                lPlusVariantTargeting(),
                ImmutableSet.of("base", "testModule"))
            .splitModule();

    assertThat(moduleSplits).hasSize(1);
    ModuleSplit masterSplit = moduleSplits.get(0);
    assertThat(masterSplit.getVariantTargeting()).isEqualTo(lPlusVariantTargeting());
    assertThat(masterSplit.isMasterSplit()).isTrue();
    assertThat(masterSplit.getApkTargeting()).isEqualTo(apkMinSdkTargeting(23));
    assertThat(masterSplit.getSplitType()).isEqualTo(SplitType.INSTANT);
    assertThat(masterSplit.getAndroidManifest().getTargetSandboxVersion()).hasValue(2);
    assertThat(masterSplit.getAndroidManifest().getMinSdkVersion()).hasValue(21);
  }

  @Test
  public void instantManifestChanges_keepsMinSdkVersion_flagDisabled() throws Exception {
    BundleModule bundleModule =
        new BundleModuleBuilder("testModule")
            .setManifest(androidManifest("com.test.app", withMinSdkVersion(19), withInstant(true)))
            .build();
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .addFile("dex/classes.dex")
            .setManifest(androidManifest("com.test.app", withMinSdkVersion(23), withInstant(true)))
            .build();
    AppBundle appBundle =
        new AppBundleBuilder().addModule(baseModule).addModule(bundleModule).build();

    ImmutableList<ModuleSplit> moduleSplits =
        ModuleSplitter.createNoStamp(
                bundleModule,
                BUNDLETOOL_VERSION,
                appBundle,
                ApkGenerationConfiguration.builder().setForInstantAppVariants(true).build(),
                lPlusVariantTargeting(),
                ImmutableSet.of("base", "testModule"))
            .splitModule();

    assertThat(moduleSplits).hasSize(1);
    ModuleSplit masterSplit = moduleSplits.get(0);
    assertThat(masterSplit.getVariantTargeting()).isEqualTo(lPlusVariantTargeting());
    assertThat(masterSplit.isMasterSplit()).isTrue();
    assertThat(masterSplit.getApkTargeting()).isEqualTo(apkMinSdkTargeting(21));
    assertThat(masterSplit.getSplitType()).isEqualTo(SplitType.INSTANT);
    assertThat(masterSplit.getAndroidManifest().getTargetSandboxVersion()).hasValue(2);
    assertThat(masterSplit.getAndroidManifest().getMinSdkVersion()).hasValue(21);
  }

  @Test
  public void applyMasterManifestMutators_singleVariant() throws Exception {
    ModuleSplit masterSplit =
        createModuleSplitBuilder().setVariantTargeting(lPlusVariantTargeting()).build();

    ModuleSplit nonMasterSplit =
        createModuleSplitBuilder()
            .setMasterSplit(false)
            .setApkTargeting(apkAbiTargeting(AbiAlias.X86))
            .setVariantTargeting(lPlusVariantTargeting())
            .addMasterManifestMutator(withExtractNativeLibs(true))
            .build();

    ImmutableList<ModuleSplit> moduleSplits =
        ModuleSplitter.applyMasterManifestMutators(ImmutableList.of(masterSplit, nonMasterSplit));

    ImmutableMap<Boolean, ModuleSplit> moduleSplitMap =
        Maps.uniqueIndex(moduleSplits, ModuleSplit::isMasterSplit);

    assertThat(moduleSplitMap.get(true).getAndroidManifest().getExtractNativeLibsValue().get())
        .isTrue();
    assertThat(applyManifestMutators(masterSplit, ImmutableList.of(withExtractNativeLibs(true))))
        .isEqualTo(moduleSplitMap.get(true));

    assertThat(moduleSplitMap.get(false)).isEqualTo(nonMasterSplit);
  }

  @Test
  public void applyMasterManifestMutators_multipleVariants_throws() throws Exception {
    ModuleSplit masterSplit =
        createModuleSplitBuilder().setVariantTargeting(lPlusVariantTargeting()).build();

    ModuleSplit nonMasterSplit =
        createModuleSplitBuilder()
            .setMasterSplit(false)
            .setApkTargeting(apkAbiTargeting(AbiAlias.X86))
            .setVariantTargeting(variantMinSdkTargeting(ANDROID_M_API_VERSION))
            .addMasterManifestMutator(withExtractNativeLibs(true))
            .build();

    Throwable exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                ModuleSplitter.applyMasterManifestMutators(
                    ImmutableList.of(masterSplit, nonMasterSplit)));
    assertThat(exception)
        .hasMessageThat()
        .contains("Expected same variant targeting across all splits.");
  }

  @Test
  public void addingLibraryPlaceholders_baseModule() throws Exception {
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .addFile("dex/classes.dex")
            .setManifest(androidManifest("com.test.app"))
            .build();

    ModuleSplitter moduleSplitter =
        ModuleSplitter.createNoStamp(
            baseModule,
            BUNDLETOOL_VERSION,
            APP_BUNDLE,
            ApkGenerationConfiguration.builder()
                .setAbisForPlaceholderLibs(
                    ImmutableSet.of(toAbi(AbiAlias.X86), toAbi(AbiAlias.ARM64_V8A)))
                .build(),
            lPlusVariantTargeting(),
            ImmutableSet.of("base"));

    ImmutableList<ModuleSplit> splits = moduleSplitter.splitModule();
    assertThat(splits).hasSize(1);
    ModuleSplit masterSplit = splits.get(0);
    assertThat(masterSplit.isMasterSplit()).isTrue();
    assertThat(extractPaths(masterSplit.getEntries()))
        .containsExactly(
            "dex/classes.dex", "lib/x86/libplaceholder.so", "lib/arm64-v8a/libplaceholder.so");
  }

  @Test
  public void addingLibraryPlaceholders_featureModule_noAction() throws Exception {
    BundleModule baseModule =
        new BundleModuleBuilder("feature")
            .addFile("dex/classes.dex")
            .setManifest(androidManifest("com.test.app"))
            .build();

    ModuleSplitter moduleSplitter =
        ModuleSplitter.createNoStamp(
            baseModule,
            BUNDLETOOL_VERSION,
            APP_BUNDLE,
            ApkGenerationConfiguration.builder()
                .setAbisForPlaceholderLibs(
                    ImmutableSet.of(toAbi(AbiAlias.X86), toAbi(AbiAlias.ARM64_V8A)))
                .build(),
            lPlusVariantTargeting(),
            ImmutableSet.of("base", "feature"));

    ImmutableList<ModuleSplit> splits = moduleSplitter.splitModule();
    assertThat(splits).hasSize(1);
    ModuleSplit masterSplit = splits.get(0);
    assertThat(masterSplit.isMasterSplit()).isTrue();
    assertThat(extractPaths(masterSplit.getEntries())).containsExactly("dex/classes.dex");
  }

  @Test
  public void wholeResourcePinning_allConfigsInMaster() throws Exception {
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .setResourceTable(
                resourceTable(
                    pkg(
                        USER_PACKAGE_OFFSET,
                        "com.test.app",
                        type(
                            0x01,
                            "string",
                            entry(
                                0x0001,
                                "welcome_label",
                                value("Welcome", Configuration.getDefaultInstance()),
                                value("Willkommen", locale("de")),
                                value("Здравствуйте", locale("ru"))),
                            entry(
                                0x0002,
                                "goodbye_label",
                                value("Goodbye", Configuration.getDefaultInstance()),
                                value("Auf Wiedersehen", locale("de")),
                                value("До свидания", locale("ru")))))))
            .setManifest(androidManifest("com.test.app"))
            .build();
    ModuleSplitter moduleSplitter =
        ModuleSplitter.createNoStamp(
            baseModule,
            BUNDLETOOL_VERSION,
            APP_BUNDLE,
            ApkGenerationConfiguration.builder()
                .setOptimizationDimensions(ImmutableSet.of(LANGUAGE))
                .setMasterPinnedResourceIds(ImmutableSet.of(ResourceId.create(0x7f010001)))
                .build(),
            lPlusVariantTargeting(),
            ImmutableSet.of("base"));

    ImmutableList<ModuleSplit> splits = moduleSplitter.splitModule();

    Map<String, ModuleSplit> splitsBySuffix = Maps.uniqueIndex(splits, ModuleSplit::getSuffix);
    assertThat(splitsBySuffix.keySet()).containsExactly("", "de", "ru");

    assertThat(splitsBySuffix.get("").getResourceTable().get())
        .containsResource("com.test.app:string/welcome_label")
        .onlyWithConfigs(Configuration.getDefaultInstance(), locale("de"), locale("ru"));
    assertThat(splitsBySuffix.get("de").getResourceTable().get())
        .doesNotContainResource("com.test.app:string/welcome_label");
    assertThat(splitsBySuffix.get("ru").getResourceTable().get())
        .doesNotContainResource("com.test.app:string/welcome_label");

    assertThat(splitsBySuffix.get("").getResourceTable().get())
        .containsResource("com.test.app:string/goodbye_label")
        .onlyWithConfigs(Configuration.getDefaultInstance());
    assertThat(splitsBySuffix.get("de").getResourceTable().get())
        .containsResource("com.test.app:string/goodbye_label")
        .onlyWithConfigs(locale("de"));
    assertThat(splitsBySuffix.get("ru").getResourceTable().get())
        .containsResource("com.test.app:string/goodbye_label")
        .onlyWithConfigs(locale("ru"));
  }

  @Test
  public void wholeResourcePinning_langResourcePinnedByName() throws Exception {
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .setResourceTable(
                resourceTable(
                    pkg(
                        USER_PACKAGE_OFFSET,
                        "com.test.app",
                        type(
                            0x01,
                            "string",
                            entry(
                                0x0001,
                                "welcome_label",
                                value("Welcome", Configuration.getDefaultInstance()),
                                value("Willkommen", locale("de")),
                                value("Здравствуйте", locale("ru"))),
                            entry(
                                0x0002,
                                "goodbye_label",
                                value("Goodbye", Configuration.getDefaultInstance()),
                                value("Auf Wiedersehen", locale("de")),
                                value("До свидания", locale("ru")))))))
            .setManifest(androidManifest("com.test.app"))
            .build();
    ModuleSplitter moduleSplitter =
        ModuleSplitter.createNoStamp(
            baseModule,
            BUNDLETOOL_VERSION,
            APP_BUNDLE,
            ApkGenerationConfiguration.builder()
                .setOptimizationDimensions(ImmutableSet.of(LANGUAGE))
                .setMasterPinnedResourceNames(ImmutableSet.of("welcome_label"))
                .build(),
            lPlusVariantTargeting(),
            ImmutableSet.of("base"));

    ImmutableList<ModuleSplit> splits = moduleSplitter.splitModule();

    Map<String, ModuleSplit> splitsBySuffix = Maps.uniqueIndex(splits, ModuleSplit::getSuffix);
    assertThat(splitsBySuffix.keySet()).containsExactly("", "de", "ru");

    assertThat(splitsBySuffix.get("").getResourceTable().get())
        .containsResource("com.test.app:string/welcome_label")
        .onlyWithConfigs(Configuration.getDefaultInstance(), locale("de"), locale("ru"));
    assertThat(splitsBySuffix.get("de").getResourceTable().get())
        .doesNotContainResource("com.test.app:string/welcome_label");
    assertThat(splitsBySuffix.get("ru").getResourceTable().get())
        .doesNotContainResource("com.test.app:string/welcome_label");

    assertThat(splitsBySuffix.get("").getResourceTable().get())
        .containsResource("com.test.app:string/goodbye_label")
        .onlyWithConfigs(Configuration.getDefaultInstance());
    assertThat(splitsBySuffix.get("de").getResourceTable().get())
        .containsResource("com.test.app:string/goodbye_label")
        .onlyWithConfigs(locale("de"));
    assertThat(splitsBySuffix.get("ru").getResourceTable().get())
        .containsResource("com.test.app:string/goodbye_label")
        .onlyWithConfigs(locale("ru"));
  }

  @Test
  public void manifestResourcePinning_langResourceWithDefaultConfig_notPinned() throws Exception {
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .setResourceTable(
                resourceTable(
                    pkg(
                        USER_PACKAGE_OFFSET,
                        "com.test.app",
                        type(
                            0x01,
                            "string",
                            entry(
                                0x0001,
                                "welcome_label",
                                value("Welcome", Configuration.getDefaultInstance()),
                                value("Willkommen", locale("de")),
                                value("Здравствуйте", locale("ru")))))))
            .setManifest(
                androidManifest(
                    "com.test.app", withMetadataResource("reference-to-resource", 0x7f010001)))
            .build();
    ModuleSplitter moduleSplitter =
        ModuleSplitter.createNoStamp(
            baseModule,
            BUNDLETOOL_VERSION,
            APP_BUNDLE,
            ApkGenerationConfiguration.builder()
                .setOptimizationDimensions(ImmutableSet.of(LANGUAGE))
                .setBaseManifestReachableResources(ImmutableSet.of(ResourceId.create(0x7f010001)))
                .build(),
            lPlusVariantTargeting(),
            ImmutableSet.of("base"));

    ImmutableList<ModuleSplit> splits = moduleSplitter.splitModule();

    Map<String, ModuleSplit> splitsBySuffix = Maps.uniqueIndex(splits, ModuleSplit::getSuffix);
    assertThat(splitsBySuffix.keySet()).containsExactly("", "de", "ru");

    assertThat(splitsBySuffix.get("").getResourceTable().get())
        .containsResource("com.test.app:string/welcome_label")
        .onlyWithConfigs(Configuration.getDefaultInstance());
    assertThat(splitsBySuffix.get("de").getResourceTable().get())
        .containsResource("com.test.app:string/welcome_label")
        .onlyWithConfigs(locale("de"));
    assertThat(splitsBySuffix.get("ru").getResourceTable().get())
        .containsResource("com.test.app:string/welcome_label")
        .onlyWithConfigs(locale("ru"));
  }

  @Test
  public void manifestResourcePinning_langResourceWithoutDefaultConfig_pinned() throws Exception {
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .setResourceTable(
                resourceTable(
                    pkg(
                        USER_PACKAGE_OFFSET,
                        "com.test.app",
                        type(
                            0x01,
                            "string",
                            entry(
                                0x0001,
                                "welcome_label",
                                value("Welcome", locale("en")),
                                value("Willkommen", locale("de")),
                                value("Здравствуйте", locale("ru"))),
                            entry(
                                0x0002,
                                "goodbye_label",
                                value("Goodbye", locale("en")),
                                value("Auf Wiedersehen", locale("de")),
                                value("До свидания", locale("ru")))))))
            .setManifest(
                androidManifest(
                    "com.test.app", withMetadataResource("reference-to-resource", 0x7f010001)))
            .build();
    ModuleSplitter moduleSplitter =
        ModuleSplitter.createNoStamp(
            baseModule,
            BUNDLETOOL_VERSION,
            APP_BUNDLE,
            ApkGenerationConfiguration.builder()
                .setOptimizationDimensions(ImmutableSet.of(LANGUAGE))
                .setBaseManifestReachableResources(ImmutableSet.of(ResourceId.create(0x7f010001)))
                .build(),
            lPlusVariantTargeting(),
            ImmutableSet.of("base"));

    ImmutableList<ModuleSplit> splits = moduleSplitter.splitModule();

    Map<String, ModuleSplit> splitsBySuffix = Maps.uniqueIndex(splits, ModuleSplit::getSuffix);
    assertThat(splitsBySuffix.keySet()).containsExactly("en", "de", "ru", "");

    assertThat(splitsBySuffix.get("").getResourceTable().get())
        .containsResource("com.test.app:string/welcome_label")
        .onlyWithConfigs(locale("en"), locale("de"), locale("ru"));
    assertThat(splitsBySuffix.get("en").getResourceTable().get())
        .doesNotContainResource("com.test.app:string/welcome_label");
    assertThat(splitsBySuffix.get("de").getResourceTable().get())
        .doesNotContainResource("com.test.app:string/welcome_label");
    assertThat(splitsBySuffix.get("ru").getResourceTable().get())
        .doesNotContainResource("com.test.app:string/welcome_label");
  }

  @Test
  public void testModuleSplitter_baseSplit_addsStamp() throws Exception {
    String stampSource = "https://www.example.com";
    StampType stampType = StampType.STAMP_TYPE_DISTRIBUTION_APK;
    BundleModule bundleModule =
        new BundleModuleBuilder("base").setManifest(androidManifest("com.test.app")).build();
    ModuleSplitter moduleSplitter =
        ModuleSplitter.create(
            bundleModule,
            BUNDLETOOL_VERSION,
            APP_BUNDLE,
            ApkGenerationConfiguration.getDefaultInstance(),
            lPlusVariantTargeting(),
            ImmutableSet.of("base"),
            Optional.of(stampSource),
            stampType);

    List<ModuleSplit> splits = moduleSplitter.splitModule();

    // Base split
    assertThat(splits).hasSize(1);
    ModuleSplit baseSplit = getOnlyElement(splits);
    assertThat(baseSplit.getAndroidManifest().getMetadataValue(STAMP_TYPE_METADATA_KEY))
        .hasValue(stampType.toString());
    assertThat(baseSplit.getAndroidManifest().getMetadataValue(STAMP_SOURCE_METADATA_KEY))
        .hasValue(stampSource);
  }

  @Test
  public void testModuleSplitter_nativeSplit_addsNoStamp() throws Exception {
    String stampSource = "https://www.example.com";
    StampType stampType = StampType.STAMP_TYPE_DISTRIBUTION_APK;
    NativeLibraries nativeConfig =
        nativeLibraries(targetedNativeDirectory("lib/x86", nativeDirectoryTargeting("x86")));
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .setManifest(androidManifest("com.test.app"))
            .setNativeConfig(nativeConfig)
            .addFile("lib/x86/liba.so")
            .build();
    ModuleSplitter moduleSplitter =
        ModuleSplitter.create(
            testModule,
            BUNDLETOOL_VERSION,
            APP_BUNDLE,
            ApkGenerationConfiguration.builder()
                .setOptimizationDimensions(ImmutableSet.of(ABI))
                .setEnableUncompressedNativeLibraries(true)
                .build(),
            lPlusVariantTargeting(),
            ImmutableSet.of("base", "testModule"),
            Optional.of(stampSource),
            stampType);

    List<ModuleSplit> splits = moduleSplitter.splitModule();

    // Base + x86 splits
    assertThat(splits).hasSize(2);
    ModuleSplit x86Split =
        splits.stream()
            .filter(split -> split.getApkTargeting().hasAbiTargeting())
            .findFirst()
            .get();
    assertThat(x86Split.getAndroidManifest().getMetadataValue(STAMP_TYPE_METADATA_KEY)).isEmpty();
    assertThat(x86Split.getAndroidManifest().getMetadataValue(STAMP_SOURCE_METADATA_KEY)).isEmpty();
  }

  @Test
  public void pinSpecIsCopied() {
    NativeLibraries nativeConfig =
        nativeLibraries(targetedNativeDirectory("lib/x86", nativeDirectoryTargeting("x86")));
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .setManifest(androidManifest("com.test.app"))
            .setNativeConfig(nativeConfig)
            .addFile("lib/x86/liba.so")
            .addFile("assets/com.android.hints.pins.txt")
            .build();
    ImmutableList<ModuleSplit> splits = createAbiAndDensitySplitter(testModule).splitModule();
    assertThat(splits).hasSize(2);
    assertThat(
            splits.stream()
                .map(
                    (s) ->
                        s.getEntries().stream()
                            .filter((e) -> e.getPath().endsWith(PinSpecInjector.PIN_SPEC_NAME))
                            .count())
                .distinct())
        .containsExactly(1L);
  }

  @Test
  public void binaryArtProfileIsCopied() {
    NativeLibraries nativeConfig =
        nativeLibraries(targetedNativeDirectory("lib/x86", nativeDirectoryTargeting("x86")));
    BundleModule testModule =
        new BundleModuleBuilder(BundleModuleName.BASE_MODULE_NAME.getName())
            .setManifest(androidManifest("com.test.app"))
            .setNativeConfig(nativeConfig)
            .addFile("lib/x86/liba.so")
            .addFile("assets/some.txt")
            .build();
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(BASE_MODULE)
            .addMetadataFile(
                BinaryArtProfilesInjector.METADATA_NAMESPACE,
                BinaryArtProfilesInjector.BINARY_ART_PROFILE_NAME,
                ByteSource.wrap(new byte[] {1, 2, 3}))
            .build();
    ImmutableList<ModuleSplit> splits =
        createAbiAndDensitySplitter(testModule, appBundle).splitModule();
    assertThat(splits).hasSize(2);
    assertThat(
            splits.stream()
                .flatMap(
                    (s) ->
                        s.getEntries().stream()
                            .filter(
                                (e) ->
                                    e.getPath()
                                        .equals(ZipPath.create("assets/dexopt/baseline.prof"))))
                .count())
        .isEqualTo(1L);
  }

  @Test
  public void bundleHasRuntimeEnabledSdkDeps_sdkRuntimeVariant_baseModule_usesSdkLibrary() {
    RuntimeEnabledSdkConfig runtimeEnabledSdkConfig =
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk")
                    .setVersionMajor(1)
                    .setVersionMinor(2)
                    .setCertificateDigest(VALID_CERT_DIGEST))
            .build();
    NativeLibraries nativeConfig =
        nativeLibraries(targetedNativeDirectory("lib/x86", nativeDirectoryTargeting("x86")));
    BundleModule testModule =
        new BundleModuleBuilder("base")
            .setManifest(androidManifest("com.test.app"))
            .setRuntimeEnabledSdkConfig(runtimeEnabledSdkConfig)
            .setNativeConfig(nativeConfig)
            .addFile("lib/x86/liba.so")
            .build();
    AppBundle appBundle = new AppBundleBuilder().addModule(testModule).build();
    ModuleSplitter moduleSplitter =
        ModuleSplitter.createNoStamp(
            testModule,
            BUNDLETOOL_VERSION,
            appBundle,
            ApkGenerationConfiguration.builder()
                .setOptimizationDimensions(ImmutableSet.of(ABI))
                .setEnableUncompressedNativeLibraries(true)
                .build(),
            sdkRuntimeVariantTargeting(),
            ImmutableSet.of("base"));

    ImmutableList<ModuleSplit> splits = moduleSplitter.splitModule();

    assertThat(splits).hasSize(2);
    ModuleSplit mainSplit = splits.stream().filter(ModuleSplit::isMasterSplit).findAny().get();
    assertThat(mainSplit.getAndroidManifest().getUsesSdkLibraryElements()).hasSize(1);
    ModuleSplit configSplit =
        splits.stream().filter(split -> !split.isMasterSplit()).findAny().get();
    assertThat(configSplit.getAndroidManifest().getUsesSdkLibraryElements()).isEmpty();
  }

  @Test
  public void bundleHasRuntimeEnabledSdkDeps_sdkRuntimeVariant_featureModule_noUsesSdkLibraryTag() {
    RuntimeEnabledSdkConfig runtimeEnabledSdkConfig =
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk")
                    .setVersionMajor(1)
                    .setVersionMinor(2)
                    .setCertificateDigest(VALID_CERT_DIGEST))
            .build();
    BundleModule testModule =
        new BundleModuleBuilder("feature")
            .setManifest(androidManifest("com.test.app"))
            .setRuntimeEnabledSdkConfig(runtimeEnabledSdkConfig)
            .build();
    AppBundle appBundle =
        new AppBundleBuilder().addModule(BASE_MODULE).addModule(testModule).build();
    ModuleSplitter moduleSplitter =
        ModuleSplitter.createNoStamp(
            testModule,
            BUNDLETOOL_VERSION,
            appBundle,
            ApkGenerationConfiguration.getDefaultInstance(),
            sdkRuntimeVariantTargeting(),
            ImmutableSet.of("base", "feature"));

    ImmutableList<ModuleSplit> splits = moduleSplitter.splitModule();

    assertThat(splits).hasSize(1);
    assertThat(splits.get(0).getAndroidManifest().getUsesSdkLibraryElements()).isEmpty();
  }

  @Test
  public void bundleHasRuntimeEnabledSdkDeps_notSdkRuntimeVariant_baseModule_noUsesSdkLibraryTag() {
    RuntimeEnabledSdkConfig runtimeEnabledSdkConfig =
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk")
                    .setVersionMajor(1)
                    .setVersionMinor(2)
                    .setCertificateDigest(VALID_CERT_DIGEST))
            .build();
    BundleModule testModule =
        new BundleModuleBuilder("base")
            .setManifest(androidManifest("com.test.app"))
            .setRuntimeEnabledSdkConfig(runtimeEnabledSdkConfig)
            .build();
    AppBundle appBundle = new AppBundleBuilder().addModule(testModule).build();
    ModuleSplitter moduleSplitter =
        ModuleSplitter.createNoStamp(
            testModule,
            BUNDLETOOL_VERSION,
            appBundle,
            ApkGenerationConfiguration.getDefaultInstance(),
            lPlusVariantTargeting(),
            ImmutableSet.of("base"));

    ImmutableList<ModuleSplit> splits = moduleSplitter.splitModule();

    assertThat(splits).hasSize(1);
    assertThat(splits.get(0).getAndroidManifest().getUsesSdkLibraryElements()).isEmpty();
  }

  @Test
  public void hasReSdkDeps_sdkRuntimeVariant_mainSplitOfBaseModule_removesElementRequiredBySdk() {
    RuntimeEnabledSdkConfig runtimeEnabledSdkConfig =
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk")
                    .setVersionMajor(1)
                    .setVersionMinor(2)
                    .setCertificateDigest(VALID_CERT_DIGEST))
            .build();
    BundleModule testModule =
        new BundleModuleBuilder("base")
            .setManifest(
                androidManifest(
                    "com.test.app",
                    withMinSdkVersion(SDK_SANDBOX_MIN_VERSION),
                    withRequiredByPrivacySandboxElement(
                        PERMISSION_ELEMENT_NAME, /* requiredByPrivacySandboxSdkValue= */ true),
                    withRequiredByPrivacySandboxElement(
                        PROPERTY_ELEMENT_NAME, /* requiredByPrivacySandboxSdkValue= */ false)))
            .setRuntimeEnabledSdkConfig(runtimeEnabledSdkConfig)
            .build();
    AppBundle appBundle = new AppBundleBuilder().addModule(testModule).build();
    ModuleSplitter moduleSplitter =
        ModuleSplitter.createNoStamp(
            testModule,
            BUNDLETOOL_VERSION,
            appBundle,
            ApkGenerationConfiguration.getDefaultInstance(),
            sdkRuntimeVariantTargeting(),
            ImmutableSet.of("base"));

    ImmutableList<ModuleSplit> splits = moduleSplitter.splitModule();

    assertThat(splits).hasSize(1);
    assertThat(splits.get(0).getAndroidManifest().getManifestRoot().getProto())
        .isEqualTo(
            androidManifest(
                "com.test.app",
                withMinSdkVersion(SDK_SANDBOX_MIN_VERSION),
                withUsesSdkLibraryElement(
                    "com.test.sdk",
                    encodeSdkMajorAndMinorVersion(/* versionMajor= */ 1, /* versionMinor= */ 2),
                    VALID_CERT_DIGEST),
                manifestElement -> manifestElement.getOrCreateChildElement(PROPERTY_ELEMENT_NAME)));
  }

  @Test
  public void hasReSdkDeps_nonSdkRuntimeVariant_mainSplitOfBaseModule_removesRequiredBySdkAttrs() {
    RuntimeEnabledSdkConfig runtimeEnabledSdkConfig =
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk")
                    .setVersionMajor(1)
                    .setVersionMinor(2)
                    .setCertificateDigest(VALID_CERT_DIGEST))
            .build();
    BundleModule testModule =
        new BundleModuleBuilder("base")
            .setManifest(
                androidManifest(
                    "com.test.app",
                    withRequiredByPrivacySandboxElement(
                        PERMISSION_ELEMENT_NAME, /* requiredByPrivacySandboxSdkValue= */ true),
                    withRequiredByPrivacySandboxElement(
                        PROPERTY_ELEMENT_NAME, /* requiredByPrivacySandboxSdkValue= */ false)))
            .setRuntimeEnabledSdkConfig(runtimeEnabledSdkConfig)
            .build();
    AppBundle appBundle = new AppBundleBuilder().addModule(testModule).build();
    ModuleSplitter moduleSplitter =
        ModuleSplitter.createNoStamp(
            testModule,
            BUNDLETOOL_VERSION,
            appBundle,
            ApkGenerationConfiguration.getDefaultInstance(),
            lPlusVariantTargeting(),
            ImmutableSet.of("base"));

    ImmutableList<ModuleSplit> splits = moduleSplitter.splitModule();

    assertThat(splits).hasSize(1);
    assertThat(splits.get(0).getAndroidManifest().getManifestRoot().getProto())
        .isEqualTo(
            androidManifest(
                "com.test.app",
                manifestElement -> manifestElement.getOrCreateChildElement(PERMISSION_ELEMENT_NAME),
                manifestElement -> manifestElement.getOrCreateChildElement(PROPERTY_ELEMENT_NAME)));
  }

  @Test
  public void hasNoReSdkDeps_doesNotModifyRequiredByPrivacySandboxSdkAttributes() {
    XmlNode androidManifest =
        androidManifest(
            "com.test.app",
            withRequiredByPrivacySandboxElement(
                PERMISSION_ELEMENT_NAME, /* requiredByPrivacySandboxSdkValue= */ true),
            withRequiredByPrivacySandboxElement(
                PROPERTY_ELEMENT_NAME, /* requiredByPrivacySandboxSdkValue= */ false));
    BundleModule testModule = new BundleModuleBuilder("base").setManifest(androidManifest).build();
    AppBundle appBundle = new AppBundleBuilder().addModule(testModule).build();
    ModuleSplitter moduleSplitter =
        ModuleSplitter.createNoStamp(
            testModule,
            BUNDLETOOL_VERSION,
            appBundle,
            ApkGenerationConfiguration.getDefaultInstance(),
            lPlusVariantTargeting(),
            ImmutableSet.of("base"));

    ImmutableList<ModuleSplit> splits = moduleSplitter.splitModule();

    assertThat(splits).hasSize(1);
    assertThat(splits.get(0).getAndroidManifest().getManifestRoot().getProto())
        .isEqualTo(androidManifest);
  }

  @Test
  public void sdkRuntimeVariant_overridesMinSdkVersion() {
    BundleModule testModule =
        new BundleModuleBuilder("base")
            .setManifest(androidManifest("com.test.app", withMinSdkVersion(ANDROID_L_API_VERSION)))
            .build();
    AppBundle appBundle = new AppBundleBuilder().addModule(testModule).build();
    ModuleSplitter moduleSplitter =
        ModuleSplitter.createNoStamp(
            testModule,
            BUNDLETOOL_VERSION,
            appBundle,
            ApkGenerationConfiguration.getDefaultInstance(),
            sdkRuntimeVariantTargeting(),
            ImmutableSet.of("base"));

    ImmutableList<ModuleSplit> splits = moduleSplitter.splitModule();

    assertThat(splits).hasSize(1);
    assertThat(splits.get(0).getAndroidManifest().getManifestRoot().getProto())
        .isEqualTo(androidManifest("com.test.app", withMinSdkVersion(SDK_SANDBOX_MIN_VERSION)));
  }

  @Test
  public void bundleHasdSdkDeps_nonSdkRuntimeVariant_baseModule_mainSplit_sdkTableConfigInjected() {
    RuntimeEnabledSdkConfig runtimeEnabledSdkConfig =
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk")
                    .setVersionMajor(1)
                    .setVersionMinor(2)
                    .setCertificateDigest(VALID_CERT_DIGEST))
            .build();
    BundleModule testModule =
        new BundleModuleBuilder("base")
            .setManifest(androidManifest("com.test.app"))
            .setRuntimeEnabledSdkConfig(runtimeEnabledSdkConfig)
            .build();
    AppBundle appBundle = new AppBundleBuilder().addModule(testModule).build();
    ModuleSplitter moduleSplitter =
        ModuleSplitter.createNoStamp(
            testModule,
            BUNDLETOOL_VERSION,
            appBundle,
            ApkGenerationConfiguration.getDefaultInstance(),
            VariantTargeting.getDefaultInstance(),
            ImmutableSet.of("base"));

    ImmutableList<ModuleSplit> splits = moduleSplitter.splitModule();

    ModuleSplit mainSplit = splits.stream().filter(ModuleSplit::isMasterSplit).findAny().get();
    assertThat(
            mainSplit.getEntries().stream()
                .filter(
                    entry ->
                        entry
                            .getPath()
                            .equals(
                                ZipPath.create(
                                    RuntimeEnabledSdkTableInjector
                                        .RUNTIME_ENABLED_SDK_TABLE_FILE_PATH))))
        .hasSize(1);
  }

  private ModuleSplit checkAndReturnTheOnlyMasterSplit(List<ModuleSplit> splits) {
    int masterSplitsFound = 0;
    ModuleSplit masterSplit = null;
    for (ModuleSplit split : splits) {
      if (split.getApkTargeting().equals(DEFAULT_MASTER_SPLIT_SDK_TARGETING)) {
        masterSplit = split;
        masterSplitsFound++;
      }
    }
    assertThat(masterSplitsFound).isEqualTo(1);
    return masterSplit;
  }

  private static ModuleSplitter createTextureCompressionFormatSplitter(BundleModule module) {
    return ModuleSplitter.createNoStamp(
        module,
        BUNDLETOOL_VERSION,
        APP_BUNDLE,
        withTcfSuffixStripping(
            withOptimizationDimensions(ImmutableSet.of(TEXTURE_COMPRESSION_FORMAT))),
        lPlusVariantTargeting(),
        ImmutableSet.of(module.getName().getName()));
  }

  private static ModuleSplitter createDeviceTierSplitter(BundleModule module) {
    return ModuleSplitter.createNoStamp(
        module,
        BUNDLETOOL_VERSION,
        APP_BUNDLE,
        withOptimizationDimensions(ImmutableSet.of(DEVICE_TIER)),
        lPlusVariantTargeting(),
        ImmutableSet.of(module.getName().getName()));
  }

  private static ModuleSplitter createCountrySetSplitter(BundleModule module) {
    return ModuleSplitter.createNoStamp(
        module,
        BUNDLETOOL_VERSION,
        APP_BUNDLE,
        withOptimizationDimensions(ImmutableSet.of(COUNTRY_SET)),
        lPlusVariantTargeting(),
        ImmutableSet.of(module.getName().getName()));
  }

  private static ModuleSplitter createAbiAndDensitySplitter(
      BundleModule module, AppBundle appBundle) {
    return ModuleSplitter.createNoStamp(
        module,
        BUNDLETOOL_VERSION,
        appBundle,
        withOptimizationDimensions(ImmutableSet.of(ABI, SCREEN_DENSITY)),
        lPlusVariantTargeting(),
        ImmutableSet.of(module.getName().getName()));
  }

  private static ModuleSplitter createAbiAndDensitySplitter(BundleModule module) {
    return createAbiAndDensitySplitter(module, APP_BUNDLE);
  }

  private static ModuleSplitter createAbiDensityAndLanguageSplitter(BundleModule module) {
    return ModuleSplitter.createNoStamp(
        module,
        BUNDLETOOL_VERSION,
        APP_BUNDLE,
        withOptimizationDimensions(ImmutableSet.of(ABI, SCREEN_DENSITY, LANGUAGE)),
        lPlusVariantTargeting(),
        ImmutableSet.of(module.getName().getName()));
  }

  private static ApkGenerationConfiguration withOptimizationDimensions(
      ImmutableSet<OptimizationDimension> optimizationDimensions) {
    return ApkGenerationConfiguration.builder()
        .setOptimizationDimensions(optimizationDimensions)
        .build();
  }

  private static ApkGenerationConfiguration withTcfSuffixStripping(
      ApkGenerationConfiguration apkGenerationConfiguration) {
    return apkGenerationConfiguration.toBuilder()
        .setSuffixStrippings(
            ImmutableMap.of(
                TEXTURE_COMPRESSION_FORMAT,
                SuffixStripping.newBuilder().setEnabled(true).setDefaultSuffix("etc1").build()))
        .build();
  }
}
