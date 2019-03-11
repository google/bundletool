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
import static com.android.tools.build.bundletool.model.AndroidManifest.NAME_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.SPLIT_NAME_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.ManifestMutator.withExtractNativeLibs;
import static com.android.tools.build.bundletool.model.OptimizationDimension.ABI;
import static com.android.tools.build.bundletool.model.OptimizationDimension.LANGUAGE;
import static com.android.tools.build.bundletool.model.OptimizationDimension.SCREEN_DENSITY;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_L_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_M_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_P_API_VERSION;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstant;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMainActivity;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMaxSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMetadataResource;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitNameActivity;
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
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkDensityTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkGraphicsTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkMinSdkTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkTextureTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assets;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.getSplitsWithTargetingEqualTo;
import static com.android.tools.build.bundletool.testing.TargetingUtils.lPlusVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.languageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeApkTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeAssetsTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeLibraries;
import static com.android.tools.build.bundletool.testing.TargetingUtils.openGlVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.openGlVersionTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedAssetsDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedNativeDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.textureCompressionTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.toAbi;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantMinSdkTargeting;
import static com.android.tools.build.bundletool.testing.TestUtils.extractPaths;
import static com.android.tools.build.bundletool.testing.truth.resources.TruthResourceTable.assertThat;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
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
import com.android.bundle.Files.Assets;
import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.OptimizationDimension;
import com.android.tools.build.bundletool.model.ResourceId;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElement;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.model.version.Version;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
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


  @Test
  public void minSdkVersionInOutputTargeting_getsSetToL() throws Exception {
    BundleModule bundleModule =
        new BundleModuleBuilder("testModule").setManifest(androidManifest("com.test.app")).build();

    ImmutableList<ModuleSplit> moduleSplits =
        new ModuleSplitter(bundleModule, BUNDLETOOL_VERSION).splitModule();

    assertThat(moduleSplits).hasSize(1);
    ModuleSplit masterSplit = moduleSplits.get(0);
    assertThat(masterSplit.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(masterSplit.getVariantTargeting()).isEqualTo(lPlusVariantTargeting());
    assertThat(masterSplit.isMasterSplit()).isTrue();
    assertThat(masterSplit.getApkTargeting()).isEqualTo(apkMinSdkTargeting(21));
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
            splits
                .stream()
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
            splits
                .stream()
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
            splits
                .stream()
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
            splits
                .stream()
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
            splits
                .stream()
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
            splits
                .stream()
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
  public void nativeSplits_64BitLibsDisabled() throws Exception {
    NativeLibraries nativeConfig =
        nativeLibraries(
            targetedNativeDirectory("lib/x86", nativeDirectoryTargeting("x86")),
            targetedNativeDirectory("lib/arm64-v8a", nativeDirectoryTargeting("arm64-v8a")));
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .setManifest(androidManifest("com.test.app"))
            .setNativeConfig(nativeConfig)
            .addFile("lib/x86/liba.so")
            .addFile("lib/arm64-v8a/liba.so")
            .build();

    ModuleSplitter moduleSplitter =
        new ModuleSplitter(
            testModule,
            BUNDLETOOL_VERSION,
            withDisabled64BitLibs(),
            lPlusVariantTargeting(),
            ImmutableSet.of("testModule"));

    ImmutableList<ModuleSplit> splits = moduleSplitter.splitModule();
    ImmutableMap<ApkTargeting, ModuleSplit> toTargetingMap =
        Maps.uniqueIndex(splits, ModuleSplit::getApkTargeting);
    ApkTargeting x86SplitTargeting =
        mergeApkTargeting(DEFAULT_MASTER_SPLIT_SDK_TARGETING, apkAbiTargeting(AbiAlias.X86));
    assertThat(toTargetingMap.keySet())
        .containsExactly(DEFAULT_MASTER_SPLIT_SDK_TARGETING, x86SplitTargeting);
    assertThat(toTargetingMap.get(DEFAULT_MASTER_SPLIT_SDK_TARGETING).isMasterSplit()).isTrue();
    assertThat(toTargetingMap.get(x86SplitTargeting).isMasterSplit()).isFalse();
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
        new ModuleSplitter(
            testModule,
            BUNDLETOOL_VERSION,
            ApkGenerationConfiguration.builder()
                .setOptimizationDimensions(ImmutableSet.of(ABI))
                .setEnableNativeLibraryCompressionSplitter(true)
                .build(),
            lPlusVariantTargeting(),
            ImmutableSet.of("testModule"));

    List<ModuleSplit> splits = moduleSplitter.splitModule();
    // Base + X86 Split
    assertThat(splits).hasSize(2);
    assertThat(splits.stream().map(ModuleSplit::getSplitType).distinct().collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    assertThat(splits.stream().map(split -> split.getVariantTargeting()).collect(toImmutableSet()))
        .containsExactly(variantMinSdkTargeting(ANDROID_L_API_VERSION));
    ModuleSplit x86Split =
        splits.stream()
            .filter(split -> split.getApkTargeting().hasAbiTargeting())
            .findFirst()
            .get();
    assertThat(x86Split.findEntry("lib/x86/liba.so").get().shouldCompress()).isTrue();
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
        new ModuleSplitter(
            testModule,
            BUNDLETOOL_VERSION,
            ApkGenerationConfiguration.builder()
                .setOptimizationDimensions(ImmutableSet.of(ABI))
                .setEnableNativeLibraryCompressionSplitter(true)
                .build(),
            variantMinSdkTargeting(ANDROID_M_API_VERSION),
            ImmutableSet.of("testModule"));

    List<ModuleSplit> splits = moduleSplitter.splitModule();
    // Base + X86 Split
    assertThat(splits).hasSize(2);
    assertThat(splits.stream().map(ModuleSplit::getSplitType).distinct().collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    assertThat(splits.stream().map(split -> split.getVariantTargeting()).collect(toImmutableSet()))
        .containsExactly(variantMinSdkTargeting(ANDROID_M_API_VERSION));
    ModuleSplit x86Split =
        splits.stream()
            .filter(split -> split.getApkTargeting().hasAbiTargeting())
            .findFirst()
            .get();
    assertThat(x86Split.findEntry("lib/x86/liba.so").get().shouldCompress()).isFalse();
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
            splits
                .stream()
                .map(ModuleSplit::getVariantTargeting)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(lPlusVariantTargeting());

    assertThat(splits).hasSize(1); // the example has nothing to split on.
    ModuleSplit masterSplit = splits.get(0);
    assertThat(masterSplit.getApkTargeting()).isEqualTo(DEFAULT_MASTER_SPLIT_SDK_TARGETING);
    assertThat(masterSplit.isMasterSplit()).isTrue();

    ImmutableSet<String> actualFiles =
        masterSplit
            .getEntries()
            .stream()
            .map(ModuleEntry::getPath)
            .map(ZipPath::toString)
            .collect(toImmutableSet());
    ImmutableSet<String> expectedFiles =
        ImmutableSet.of("dex/classes.dex", "assets/some_asset.txt", "root/some_other_file.txt");
    assertThat(actualFiles).containsAllIn(expectedFiles);
  }

  @Test
  public void nativeSplits_pPlusTargeting_withDexCompressionSplitter() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("dex/classes.dex")
            .setManifest(androidManifest("com.test.app"))
            .build();

    ModuleSplitter moduleSplitter =
        new ModuleSplitter(
            testModule,
            BUNDLETOOL_VERSION,
            ApkGenerationConfiguration.builder().setEnableDexCompressionSplitter(true).build(),
            variantMinSdkTargeting(ANDROID_P_API_VERSION),
            ImmutableSet.of("testModule"));

    List<ModuleSplit> splits = moduleSplitter.splitModule();
    assertThat(splits).hasSize(1);
    ModuleSplit moduleSplit = Iterables.getOnlyElement(splits);
    assertThat(moduleSplit.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(moduleSplit.getVariantTargeting())
        .isEqualTo(variantMinSdkTargeting(ANDROID_P_API_VERSION));
    assertThat(moduleSplit.findEntry("dex/classes.dex").get().shouldCompress()).isFalse();
  }

  @Test
  public void nativeSplits_lPlusTargeting_withDexCompressionSplitter() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("dex/classes.dex")
            .setManifest(androidManifest("com.test.app"))
            .build();

    ModuleSplitter moduleSplitter =
        new ModuleSplitter(
            testModule,
            BUNDLETOOL_VERSION,
            ApkGenerationConfiguration.builder().setEnableDexCompressionSplitter(true).build(),
            lPlusVariantTargeting(),
            ImmutableSet.of("testModule"));

    List<ModuleSplit> splits = moduleSplitter.splitModule();
    assertThat(splits).hasSize(1);
    ModuleSplit moduleSplit = Iterables.getOnlyElement(splits);
    assertThat(moduleSplit.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(moduleSplit.getVariantTargeting())
        .isEqualTo(variantMinSdkTargeting(ANDROID_L_API_VERSION));
    assertThat(moduleSplit.findEntry("dex/classes.dex").get().shouldCompress()).isTrue();
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
            splits
                .stream()
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
            splits
                .stream()
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
  public void targetsPreLOnlyInManifest_throws() throws Exception {
    int preL = 20;
    BundleModule bundleModule =
        new BundleModuleBuilder("testModule")
            .setManifest(androidManifest("com.test.app", withMaxSdkVersion(preL)))
            .build();

    CommandExecutionException exception =
        assertThrows(
            CommandExecutionException.class,
            () -> new ModuleSplitter(bundleModule, BUNDLETOOL_VERSION).splitModule());

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

    ModuleSplitter moduleSplitter = new ModuleSplitter(bundleModule, BUNDLETOOL_VERSION);
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
  public void resolvesSplitIdSuffixes_multipeVariants() throws Exception {
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

    ModuleSplitter moduleSplitter = new ModuleSplitter(bundleModule, BUNDLETOOL_VERSION);
    assetsSplit1 = moduleSplitter.writeSplitIdInManifest(assetsSplit1);
    assetsSplit2 = moduleSplitter.writeSplitIdInManifest(assetsSplit2);

    assertThat(assetsSplit1.getAndroidManifest().getSplitId()).hasValue("config.atc");
    assertThat(assetsSplit2.getAndroidManifest().getSplitId()).hasValue("config.atc");
  }

  @Test
  public void splitNameRemovedforInstalledSplit() throws Exception {
    XmlNode manifest =
        androidManifest(
            "com.test.app",
            withMainActivity("MainActivity"),
            withSplitNameActivity("FooActivity", "foo"));
    BundleModule bundleModule = new BundleModuleBuilder("testModule").setManifest(manifest).build();

    ImmutableList<ModuleSplit> moduleSplits =
        new ModuleSplitter(bundleModule, BUNDLETOOL_VERSION).splitModule();

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
  public void splitNameNotRemovedforInstantSplit() throws Exception {
    XmlNode manifest =
        androidManifest(
            "com.test.app",
            withMainActivity("MainActivity"),
            withSplitNameActivity("FooActivity", "testModule"));
    BundleModule bundleModule = new BundleModuleBuilder("testModule").setManifest(manifest).build();

    ImmutableList<ModuleSplit> moduleSplits =
        new ModuleSplitter(
                bundleModule,
                BUNDLETOOL_VERSION,
                ApkGenerationConfiguration.builder().setForInstantAppVariants(true).build(),
                lPlusVariantTargeting(),
                ImmutableSet.of("testModule"))
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
  public void nonInstantActivityRemovedforInstantManifest() throws Exception {
    XmlNode manifest =
        androidManifest(
            "com.test.app",
            withMainActivity("MainActivity"),
            withSplitNameActivity("FooActivity", "onDemandModule"));
    BundleModule bundleModule =
        new BundleModuleBuilder("onDemandModule").setManifest(manifest).build();

    ImmutableList<ModuleSplit> moduleSplits =
        new ModuleSplitter(
                bundleModule,
                BUNDLETOOL_VERSION,
                ApkGenerationConfiguration.builder().setForInstantAppVariants(true).build(),
                lPlusVariantTargeting(),
                ImmutableSet.of())
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
        new ModuleSplitter(
                bundleModule,
                BUNDLETOOL_VERSION,
                ApkGenerationConfiguration.builder().setForInstantAppVariants(true).build(),
                lPlusVariantTargeting(),
                ImmutableSet.of("testModule"))
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
    BundleModule bundleModule =
        new BundleModuleBuilder("testModule")
            .setManifest(androidManifest("com.test.app", withMinSdkVersion(22), withInstant(true)))
            .build();

    ImmutableList<ModuleSplit> moduleSplits =
        new ModuleSplitter(
                bundleModule,
                BUNDLETOOL_VERSION,
                ApkGenerationConfiguration.builder().setForInstantAppVariants(true).build(),
                lPlusVariantTargeting(),
                ImmutableSet.of("testModule"))
            .splitModule();

    assertThat(moduleSplits).hasSize(1);
    ModuleSplit masterSplit = moduleSplits.get(0);
    assertThat(masterSplit.getVariantTargeting()).isEqualTo(lPlusVariantTargeting());
    assertThat(masterSplit.isMasterSplit()).isTrue();
    assertThat(masterSplit.getApkTargeting()).isEqualTo(apkMinSdkTargeting(21));
    assertThat(masterSplit.getSplitType()).isEqualTo(SplitType.INSTANT);
    assertThat(masterSplit.getAndroidManifest().getTargetSandboxVersion()).hasValue(2);
    assertThat(masterSplit.getAndroidManifest().getMinSdkVersion()).hasValue(22);
  }

  @Test
  public void instantManifestChanges_updatesMinSdkVersion() throws Exception {
    BundleModule bundleModule =
        new BundleModuleBuilder("testModule")
            .setManifest(androidManifest("com.test.app", withMinSdkVersion(19), withInstant(true)))
            .build();

    ImmutableList<ModuleSplit> moduleSplits =
        new ModuleSplitter(
                bundleModule,
                BUNDLETOOL_VERSION,
                ApkGenerationConfiguration.builder().setForInstantAppVariants(true).build(),
                lPlusVariantTargeting(),
                ImmutableSet.of("testModule"))
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
        new ModuleSplitter(
            baseModule,
            BUNDLETOOL_VERSION,
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
        new ModuleSplitter(
            baseModule,
            BUNDLETOOL_VERSION,
            ApkGenerationConfiguration.builder()
                .setAbisForPlaceholderLibs(
                    ImmutableSet.of(toAbi(AbiAlias.X86), toAbi(AbiAlias.ARM64_V8A)))
                .build(),
            lPlusVariantTargeting(),
            ImmutableSet.of("feature"));

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
        new ModuleSplitter(
            baseModule,
            BUNDLETOOL_VERSION,
            ApkGenerationConfiguration.builder()
                .setOptimizationDimensions(ImmutableSet.of(LANGUAGE))
                .setMasterPinnedResources(ImmutableSet.of(ResourceId.create(0x7f010001)))
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
        new ModuleSplitter(
            baseModule,
            BUNDLETOOL_VERSION,
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
        new ModuleSplitter(
            baseModule,
            BUNDLETOOL_VERSION,
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

  private static ModuleSplitter createAbiAndDensitySplitter(BundleModule module) {
    return new ModuleSplitter(
        module,
        BUNDLETOOL_VERSION,
        withOptimizationDimensions(ImmutableSet.of(ABI, SCREEN_DENSITY)),
        lPlusVariantTargeting(),
        ImmutableSet.of(module.getName().getName()));
  }

  private static ModuleSplitter createAbiDensityAndLanguageSplitter(BundleModule module) {
    return new ModuleSplitter(
        module,
        BUNDLETOOL_VERSION,
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

  private static ApkGenerationConfiguration withDisabled64BitLibs() {
    return ApkGenerationConfiguration.builder()
        .setInclude64BitLibs(false)
        .setOptimizationDimensions(ImmutableSet.of(ABI))
        .build();
  }
}
