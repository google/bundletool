/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.build.bundletool.io;

import static com.android.bundle.Targeting.Abi.AbiAlias.ARMEABI;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86;
import static com.android.tools.build.bundletool.testing.DeviceFactory.lDeviceWithAbis;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withFusedModuleNames;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstant;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withOnDemandAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitId;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkDensityTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantSdkTargeting;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static java.util.function.Predicate.isEqual;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.ApkSet;
import com.android.bundle.Commands.ModuleMetadata;
import com.android.bundle.Commands.SplitApkMetadata;
import com.android.bundle.Commands.StandaloneApkMetadata;
import com.android.bundle.Commands.Variant;
import com.android.bundle.Config.Compression;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.ModuleTargeting;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.io.ApkSetBuilderFactory.ApkSetBuilder;
import com.android.tools.build.bundletool.model.Aapt2Command;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.ApkListener;
import com.android.tools.build.bundletool.model.ApkModifier;
import com.android.tools.build.bundletool.model.ApkModifier.ApkDescription.ApkType;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.GeneratedApks;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.testing.Aapt2Helper;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class ApkSerializerManagerTest {

  private static final String BASE_MODULE_NAME = "base";
  private static final String FEATURE_MODULE_NAME = "feature";
  private static final boolean MASTER_SPLIT = true;
  private static final boolean NOT_MASTER_SPLIT = false;

  private static final ApkModifier VERSION_CODE_MODIFIER =
      new ApkModifier() {
        @Override
        public AndroidManifest modifyManifest(
            AndroidManifest manifest, ApkDescription apkDescription) {
          return manifest
              .toEditor()
              .setVersionCode(1000 + apkDescription.getVariantNumber())
              .save();
        }
      };

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  private Path outputDir;
  private SplitApkSerializer splitApkSerializer;
  private StandaloneApkSerializer standaloneApkSerializer;
  private ApkSetBuilder apkSetBuilder;
  private final Aapt2Command aapt2Command = Aapt2Helper.getAapt2Command();

  @Before
  public void setUp() throws Exception {
    outputDir = tmp.newFolder("output").toPath();
    splitApkSerializer =
        new SplitApkSerializer(
            new ApkPathManager(),
            aapt2Command,
            /* signingConfig= */ Optional.empty(),
            Compression.getDefaultInstance());
    standaloneApkSerializer =
        new StandaloneApkSerializer(
            new ApkPathManager(),
            aapt2Command,
            /* signingConfig= */ Optional.empty(),
            Compression.getDefaultInstance());
    apkSetBuilder =
        ApkSetBuilderFactory.createApkSetBuilder(
            splitApkSerializer, standaloneApkSerializer, outputDir);
  }

  @Test
  public void testGenerateAllVariants_splitApks() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                BASE_MODULE_NAME,
                bundleModuleBuilder -> {
                  bundleModuleBuilder.setManifest(androidManifest("com.test.app"));
                })
            .addModule(
                FEATURE_MODULE_NAME,
                bundleModuleBuilder ->
                    bundleModuleBuilder.setManifest(
                        androidManifest("com.test.app", withOnDemandAttribute(true))))
            .build();

    ModuleSplit baseMasterSplit =
        createModuleSplit(
            BASE_MODULE_NAME,
            /* splitId= */ "",
            MASTER_SPLIT,
            ApkTargeting.getDefaultInstance(),
            variantSdkTargeting(sdkVersionFrom(21)));
    ModuleSplit baseX86Split =
        createModuleSplit(
            BASE_MODULE_NAME,
            /* splitId= */ "x86",
            NOT_MASTER_SPLIT,
            apkAbiTargeting(X86),
            variantSdkTargeting(sdkVersionFrom(21)));
    ModuleSplit baseHdpiSplit =
        createModuleSplit(
            BASE_MODULE_NAME,
            /* splitId= */ "hdpi",
            NOT_MASTER_SPLIT,
            apkDensityTargeting(DensityAlias.HDPI),
            variantSdkTargeting(sdkVersionFrom(21)));

    ModuleSplit featureMasterSplit =
        createModuleSplit(
            FEATURE_MODULE_NAME,
            /* splitId= */ FEATURE_MODULE_NAME,
            MASTER_SPLIT,
            ApkTargeting.getDefaultInstance(),
            variantSdkTargeting(sdkVersionFrom(21)));
    ModuleSplit featureHdpiSplit =
        createModuleSplit(
            FEATURE_MODULE_NAME,
            /* splitId= */ "feature.hdpi",
            NOT_MASTER_SPLIT,
            apkDensityTargeting(DensityAlias.HDPI),
            variantSdkTargeting(sdkVersionFrom(21)));

    GeneratedApks generatedApks =
        GeneratedApks.builder()
            .setSplitApks(
                ImmutableList.of(
                    baseMasterSplit,
                    baseHdpiSplit,
                    baseX86Split,
                    featureMasterSplit,
                    featureHdpiSplit))
            .build();

    ApkSerializerManager apkSerializerManager = createApkSerializerManager(appBundle);
    ImmutableList<Variant> variants = apkSerializerManager.serializeApks(generatedApks);

    assertThat(variants).hasSize(1);
    Variant variant = variants.get(0);
    assertThat(variant.getTargeting()).isEqualTo(variantSdkTargeting(sdkVersionFrom(21)));
    assertThat(variant.getApkSetList()).hasSize(2);
    Map<ModuleMetadata, ApkSet> moduleToApkSet =
        Maps.uniqueIndex(variant.getApkSetList(), ApkSet::getModuleMetadata);

    ModuleMetadata expectedBaseMetadata =
        ModuleMetadata.newBuilder()
            .setName(BASE_MODULE_NAME)
            .setOnDemand(false)
            .setTargeting(ModuleTargeting.getDefaultInstance())
            .build();
    assertThat(moduleToApkSet).containsKey(expectedBaseMetadata);
    assertThat(moduleToApkSet.get(expectedBaseMetadata).getApkDescriptionList())
        .containsExactly(
            ApkDescription.newBuilder()
                .setPath("splits/base-master.apk")
                .setTargeting(ApkTargeting.getDefaultInstance())
                .setSplitApkMetadata(
                    SplitApkMetadata.newBuilder().setIsMasterSplit(true).setSplitId(""))
                .build(),
            ApkDescription.newBuilder()
                .setPath("splits/base-x86.apk")
                .setTargeting(apkAbiTargeting(X86))
                .setSplitApkMetadata(
                    SplitApkMetadata.newBuilder().setIsMasterSplit(false).setSplitId("x86"))
                .build(),
            ApkDescription.newBuilder()
                .setPath("splits/base-hdpi.apk")
                .setTargeting(apkDensityTargeting(DensityAlias.HDPI))
                .setSplitApkMetadata(
                    SplitApkMetadata.newBuilder().setIsMasterSplit(false).setSplitId("hdpi"))
                .build());

    ModuleMetadata expectedFeatureMetadata =
        ModuleMetadata.newBuilder()
            .setName(FEATURE_MODULE_NAME)
            .setOnDemand(true)
            .setTargeting(ModuleTargeting.getDefaultInstance())
            .build();
    assertThat(moduleToApkSet).containsKey(expectedFeatureMetadata);
    assertThat(moduleToApkSet.get(expectedFeatureMetadata).getApkDescriptionList())
        .containsExactly(
            ApkDescription.newBuilder()
                .setPath("splits/feature-master.apk")
                .setTargeting(ApkTargeting.getDefaultInstance())
                .setSplitApkMetadata(
                    SplitApkMetadata.newBuilder()
                        .setIsMasterSplit(true)
                        .setSplitId(FEATURE_MODULE_NAME))
                .build(),
            ApkDescription.newBuilder()
                .setPath("splits/feature-hdpi.apk")
                .setTargeting(apkDensityTargeting(DensityAlias.HDPI))
                .setSplitApkMetadata(
                    SplitApkMetadata.newBuilder()
                        .setIsMasterSplit(false)
                        .setSplitId("feature.hdpi"))
                .build());
  }

  @Test
  public void testGenerateAllVariants_splitApksMultipleVariants() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                BASE_MODULE_NAME,
                bundleModuleBuilder -> {
                  bundleModuleBuilder.setManifest(androidManifest("com.test.app"));
                })
            .build();

    VariantTargeting lPlusVariantTargeting =
        variantSdkTargeting(sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(23)));
    VariantTargeting mPlusVariantTargeting =
        variantSdkTargeting(sdkVersionFrom(23), ImmutableSet.of(sdkVersionFrom(21)));
    ModuleSplit baseMasterSplit =
        setManifestExtractNativeLibs(
            createModuleSplit(
                BASE_MODULE_NAME,
                /* splitId= */ "",
                MASTER_SPLIT,
                ApkTargeting.getDefaultInstance(),
                lPlusVariantTargeting),
            /* extractNativeLibs= */ true);
    ModuleSplit otherMasterSplit =
        setManifestExtractNativeLibs(
            createModuleSplit(
                BASE_MODULE_NAME,
                /* splitId= */ "",
                MASTER_SPLIT,
                ApkTargeting.getDefaultInstance(),
                mPlusVariantTargeting),
            /* extractNativeLibs= */ false);
    ModuleSplit baseX86Split =
        setManifestExtractNativeLibs(
            createModuleSplit(
                BASE_MODULE_NAME,
                /* splitId= */ "x86",
                NOT_MASTER_SPLIT,
                apkAbiTargeting(X86),
                lPlusVariantTargeting),
            /* extractNativeLibs= */ true);
    ModuleSplit otherBaseX86Split =
        setManifestExtractNativeLibs(
            createModuleSplit(
                BASE_MODULE_NAME,
                /* splitId= */ "x86",
                NOT_MASTER_SPLIT,
                apkAbiTargeting(X86),
                mPlusVariantTargeting),
            /* extractNativeLibs= */ false);
    ModuleSplit baseHdpiSplit =
        createModuleSplit(
            BASE_MODULE_NAME,
            /* splitId= */ "hdpi",
            NOT_MASTER_SPLIT,
            apkDensityTargeting(DensityAlias.HDPI),
            lPlusVariantTargeting);
    ModuleSplit otherbaseHdpiSplit =
        createModuleSplit(
            BASE_MODULE_NAME,
            /* splitId= */ "hdpi",
            NOT_MASTER_SPLIT,
            apkDensityTargeting(DensityAlias.HDPI),
            mPlusVariantTargeting);

    GeneratedApks generatedApks =
        GeneratedApks.builder()
            .setSplitApks(
                ImmutableList.of(
                    baseMasterSplit,
                    otherMasterSplit,
                    baseX86Split,
                    otherBaseX86Split,
                    baseHdpiSplit,
                    otherbaseHdpiSplit))
            .build();

    ApkSerializerManager apkSerializerManager = createApkSerializerManager(appBundle);
    ImmutableList<Variant> variants = apkSerializerManager.serializeApks(generatedApks);

    assertThat(variants).hasSize(2);
    Map<VariantTargeting, Variant> targetingVariantMap =
        Maps.uniqueIndex(variants, Variant::getTargeting);

    assertThat(targetingVariantMap).containsKey(lPlusVariantTargeting);
    Variant lPlusVariant = targetingVariantMap.get(lPlusVariantTargeting);
    assertThat(lPlusVariant.getApkSetList()).hasSize(1);

    assertThat(targetingVariantMap).containsKey(mPlusVariantTargeting);
    Variant mPlusVariant = targetingVariantMap.get(mPlusVariantTargeting);
    assertThat(mPlusVariant.getApkSetList()).hasSize(1);

    ApkDescription baseMasterDescription =
        ApkDescription.newBuilder()
            .setPath("splits/base-master.apk")
            .setTargeting(ApkTargeting.getDefaultInstance())
            .setSplitApkMetadata(
                SplitApkMetadata.newBuilder().setIsMasterSplit(true).setSplitId(""))
            .build();

    ApkDescription otherBaseMasterDescription =
        ApkDescription.newBuilder()
            .setPath("splits/base-master_2.apk")
            .setTargeting(ApkTargeting.getDefaultInstance())
            .setSplitApkMetadata(
                SplitApkMetadata.newBuilder().setIsMasterSplit(true).setSplitId(""))
            .build();

    ApkDescription baseX86Description =
        ApkDescription.newBuilder()
            .setPath("splits/base-x86.apk")
            .setTargeting(apkAbiTargeting(X86))
            .setSplitApkMetadata(
                SplitApkMetadata.newBuilder().setIsMasterSplit(false).setSplitId("x86"))
            .build();

    ApkDescription otherBaseX86Description =
        ApkDescription.newBuilder()
            .setPath("splits/base-x86_2.apk")
            .setTargeting(apkAbiTargeting(X86))
            .setSplitApkMetadata(
                SplitApkMetadata.newBuilder().setIsMasterSplit(false).setSplitId("x86"))
            .build();

    ApkDescription baseHdpiDescription =
        ApkDescription.newBuilder()
            .setPath("splits/base-hdpi.apk")
            .setTargeting(apkDensityTargeting(DensityAlias.HDPI))
            .setSplitApkMetadata(
                SplitApkMetadata.newBuilder().setIsMasterSplit(false).setSplitId("hdpi"))
            .build();

    List<ApkDescription> lPlusApkDescriptions = lPlusVariant.getApkSet(0).getApkDescriptionList();
    assertThat(lPlusApkDescriptions).hasSize(3);
    // LPlus variant can contain either of base-master.apk or base-master_2.apk. This depends on the
    // order of concurrent serialization of module splits to disk.
    assertThat(lPlusApkDescriptions)
        .containsAnyOf(baseMasterDescription, otherBaseMasterDescription);
    assertThat(lPlusApkDescriptions).containsAnyOf(baseX86Description, otherBaseX86Description);
    assertThat(lPlusApkDescriptions).contains(baseHdpiDescription);

    List<ApkDescription> mPlusApkDescriptions = mPlusVariant.getApkSet(0).getApkDescriptionList();
    assertThat(mPlusApkDescriptions).hasSize(3);
    assertThat(mPlusApkDescriptions)
        .containsAnyOf(baseMasterDescription, otherBaseMasterDescription);
    assertThat(mPlusApkDescriptions).containsAnyOf(baseX86Description, otherBaseX86Description);
    assertThat(mPlusApkDescriptions).contains(baseHdpiDescription);

    ImmutableList<ApkDescription> apkDescriptions =
        ImmutableList.<ApkDescription>builder()
            .addAll(lPlusApkDescriptions)
            .addAll(mPlusApkDescriptions)
            .build();

    assertThat(apkDescriptions)
        .containsExactly(
            baseMasterDescription,
            otherBaseMasterDescription,
            baseX86Description,
            otherBaseX86Description,
            baseHdpiDescription,
            baseHdpiDescription);
  }

  @Test
  public void testGenerateAllVariants_instantApks() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                BASE_MODULE_NAME,
                bundleModuleBuilder -> {
                  bundleModuleBuilder.setManifest(
                      androidManifest("com.test.app", withInstant(true)));
                })
            .build();

    ModuleSplit baseMasterSplit =
        createInstantModuleSplit(
            BASE_MODULE_NAME,
            /* splitId= */ "",
            MASTER_SPLIT,
            ApkTargeting.getDefaultInstance(),
            variantSdkTargeting(sdkVersionFrom(21)));
    ModuleSplit baseX86Split =
        createInstantModuleSplit(
            BASE_MODULE_NAME,
            /* splitId= */ "x86",
            NOT_MASTER_SPLIT,
            apkAbiTargeting(X86),
            variantSdkTargeting(sdkVersionFrom(21)));
    ModuleSplit baseHdpiSplit =
        createInstantModuleSplit(
            BASE_MODULE_NAME,
            /* splitId= */ "hdpi",
            NOT_MASTER_SPLIT,
            apkDensityTargeting(DensityAlias.HDPI),
            variantSdkTargeting(sdkVersionFrom(21)));

    GeneratedApks generatedApks =
        GeneratedApks.builder()
            .setInstantApks(ImmutableList.of(baseMasterSplit, baseHdpiSplit, baseX86Split))
            .build();

    ApkSerializerManager apkSerializerManager = createApkSerializerManager(appBundle);
    ImmutableList<Variant> variants = apkSerializerManager.serializeApks(generatedApks);

    assertThat(variants).hasSize(1);
    Variant variant = variants.get(0);
    assertThat(variant.getTargeting()).isEqualTo(variantSdkTargeting(sdkVersionFrom(21)));
    assertThat(variant.getApkSetList()).hasSize(1);
    ApkSet apkSet = variant.getApkSet(0);
    assertThat(apkSet.getModuleMetadata())
        .isEqualTo(
            ModuleMetadata.newBuilder()
                .setName(BASE_MODULE_NAME)
                .setIsInstant(true)
                .setTargeting(ModuleTargeting.getDefaultInstance())
                .build());
    assertThat(apkSet.getApkDescriptionList())
        .containsExactly(
            ApkDescription.newBuilder()
                .setPath("instant/instant-base-master.apk")
                .setTargeting(ApkTargeting.getDefaultInstance())
                .setInstantApkMetadata(
                    SplitApkMetadata.newBuilder().setIsMasterSplit(true).setSplitId(""))
                .build(),
            ApkDescription.newBuilder()
                .setPath("instant/instant-base-x86.apk")
                .setTargeting(apkAbiTargeting(X86))
                .setInstantApkMetadata(
                    SplitApkMetadata.newBuilder().setIsMasterSplit(false).setSplitId("x86"))
                .build(),
            ApkDescription.newBuilder()
                .setPath("instant/instant-base-hdpi.apk")
                .setTargeting(apkDensityTargeting(DensityAlias.HDPI))
                .setInstantApkMetadata(
                    SplitApkMetadata.newBuilder().setIsMasterSplit(false).setSplitId("hdpi"))
                .build());
  }

  @Test
  public void testGenerateAllVariants_standaloneApks() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                BASE_MODULE_NAME,
                bundleModuleBuilder -> {
                  bundleModuleBuilder.setManifest(androidManifest("com.test.app"));
                })
            .addModule(
                FEATURE_MODULE_NAME,
                bundleModuleBuilder ->
                    bundleModuleBuilder.setManifest(
                        androidManifest("com.test.app", withOnDemandAttribute(true))))
            .build();

    ModuleSplit fusedSplit =
        createFusedSplit("base,feature", apkAbiTargeting(X86), variantAbiTargeting(X86));

    GeneratedApks generatedApks =
        GeneratedApks.builder().setStandaloneApks(ImmutableList.of(fusedSplit)).build();

    ApkSerializerManager apkSerializerManager = createApkSerializerManager(appBundle);
    ImmutableList<Variant> variants = apkSerializerManager.serializeApks(generatedApks);

    assertThat(variants).hasSize(1);
    Variant variant = variants.get(0);
    assertThat(variant.getTargeting()).isEqualTo(variantAbiTargeting(X86));
    assertThat(variant.getApkSetList()).hasSize(1);
    ApkSet apkSet = variant.getApkSet(0);
    assertThat(apkSet.getModuleMetadata())
        .isEqualTo(
            ModuleMetadata.newBuilder()
                .setName(BASE_MODULE_NAME)
                .setOnDemand(false)
                .setTargeting(ModuleTargeting.getDefaultInstance())
                .build());
    assertThat(apkSet.getApkDescriptionList()).hasSize(1);
    ApkDescription apkDescription = apkSet.getApkDescription(0);
    assertThat(apkDescription)
        .isEqualTo(
            ApkDescription.newBuilder()
                .setPath("standalones/standalone-x86.apk")
                .setStandaloneApkMetadata(
                    StandaloneApkMetadata.newBuilder()
                        .addFusedModuleName(BASE_MODULE_NAME)
                        .addFusedModuleName(FEATURE_MODULE_NAME))
                .setTargeting(apkAbiTargeting(X86))
                .build());
  }

  @Test
  public void testGenerateAllVariants_splitAndStandaloneApks() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                BASE_MODULE_NAME,
                bundleModuleBuilder -> {
                  bundleModuleBuilder.setManifest(androidManifest("com.test.app"));
                })
            .addModule(
                FEATURE_MODULE_NAME,
                bundleModuleBuilder ->
                    bundleModuleBuilder.setManifest(
                        androidManifest("com.test.app", withOnDemandAttribute(true))))
            .build();

    VariantTargeting fusedVariantTargeting =
        mergeVariantTargeting(
            variantSdkTargeting(
                SdkVersion.getDefaultInstance(), ImmutableSet.of(sdkVersionFrom(21))),
            variantAbiTargeting(X86));
    ModuleSplit fusedSplit =
        createFusedSplit("base,feature", apkAbiTargeting(X86), fusedVariantTargeting);

    VariantTargeting splitVariantTargeting =
        variantSdkTargeting(sdkVersionFrom(21), ImmutableSet.of(SdkVersion.getDefaultInstance()));
    ModuleSplit baseMasterSplit =
        createModuleSplit(
            BASE_MODULE_NAME,
            /* splitId= */ "",
            MASTER_SPLIT,
            ApkTargeting.getDefaultInstance(),
            splitVariantTargeting);
    ModuleSplit featureMasterSplit =
        createModuleSplit(
            FEATURE_MODULE_NAME,
            FEATURE_MODULE_NAME,
            MASTER_SPLIT,
            ApkTargeting.getDefaultInstance(),
            splitVariantTargeting);

    GeneratedApks generatedApks =
        GeneratedApks.builder()
            .setSplitApks(ImmutableList.of(baseMasterSplit, featureMasterSplit))
            .setStandaloneApks(ImmutableList.of(fusedSplit))
            .build();

    ApkSerializerManager apkSerializerManager = createApkSerializerManager(appBundle);
    ImmutableList<Variant> variants = apkSerializerManager.serializeApks(generatedApks);

    assertThat(variants).hasSize(2);
    Map<VariantTargeting, Variant> targetingVariantMap =
        Maps.uniqueIndex(variants, Variant::getTargeting);

    assertThat(targetingVariantMap).containsKey(fusedVariantTargeting);
    Variant fusedVariant = targetingVariantMap.get(fusedVariantTargeting);
    assertThat(fusedVariant.getApkSetList()).hasSize(1);
    ApkSet fusedApkSet = fusedVariant.getApkSet(0);
    assertThat(fusedApkSet.getModuleMetadata())
        .isEqualTo(
            ModuleMetadata.newBuilder()
                .setName(BASE_MODULE_NAME)
                .setOnDemand(false)
                .setTargeting(ModuleTargeting.getDefaultInstance())
                .build());
    assertThat(fusedApkSet.getApkDescriptionList()).hasSize(1);
    ApkDescription apkDescription = fusedApkSet.getApkDescription(0);
    assertThat(apkDescription)
        .isEqualTo(
            ApkDescription.newBuilder()
                .setPath("standalones/standalone-x86.apk")
                .setStandaloneApkMetadata(
                    StandaloneApkMetadata.newBuilder()
                        .addFusedModuleName(BASE_MODULE_NAME)
                        .addFusedModuleName(FEATURE_MODULE_NAME))
                .setTargeting(apkAbiTargeting(X86))
                .build());

    assertThat(targetingVariantMap).containsKey(splitVariantTargeting);
    Variant splitVariant = targetingVariantMap.get(splitVariantTargeting);
    assertThat(splitVariant.getApkSetList()).hasSize(2);

    Map<ModuleMetadata, ApkSet> moduleToApkSet =
        Maps.uniqueIndex(splitVariant.getApkSetList(), ApkSet::getModuleMetadata);

    ModuleMetadata expectedBaseMetadata =
        ModuleMetadata.newBuilder()
            .setName(BASE_MODULE_NAME)
            .setOnDemand(false)
            .setTargeting(ModuleTargeting.getDefaultInstance())
            .build();
    assertThat(moduleToApkSet).containsKey(expectedBaseMetadata);
    assertThat(moduleToApkSet.get(expectedBaseMetadata).getApkDescriptionList())
        .containsExactly(
            ApkDescription.newBuilder()
                .setPath("splits/base-master.apk")
                .setTargeting(ApkTargeting.getDefaultInstance())
                .setSplitApkMetadata(
                    SplitApkMetadata.newBuilder().setIsMasterSplit(true).setSplitId(""))
                .build());

    ModuleMetadata expectedFeatureMetadata =
        ModuleMetadata.newBuilder()
            .setName(FEATURE_MODULE_NAME)
            .setOnDemand(true)
            .setTargeting(ModuleTargeting.getDefaultInstance())
            .build();
    assertThat(moduleToApkSet).containsKey(expectedFeatureMetadata);
    assertThat(moduleToApkSet.get(expectedFeatureMetadata).getApkDescriptionList())
        .containsExactly(
            ApkDescription.newBuilder()
                .setPath("splits/feature-master.apk")
                .setTargeting(ApkTargeting.getDefaultInstance())
                .setSplitApkMetadata(
                    SplitApkMetadata.newBuilder()
                        .setIsMasterSplit(true)
                        .setSplitId(FEATURE_MODULE_NAME))
                .build());
  }

  @Test
  public void orderOfVariants_instantBeforeStandalonesBeforeSplits() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                BASE_MODULE_NAME,
                module -> module.setManifest(androidManifest("com.test.app", withInstant(true))))
            .build();

    ApkSerializerManager apkSerializerManager =
        createApkSerializerManager(appBundle, ApkListener.NO_OP, VERSION_CODE_MODIFIER);

    ModuleSplit splitApk1 = createMasterModuleSplit(BASE_MODULE_NAME);
    ModuleSplit splitApk2 = createConfigModuleSplit(BASE_MODULE_NAME, "x86", apkAbiTargeting(X86));
    ModuleSplit splitApk3 =
        createConfigModuleSplit(BASE_MODULE_NAME, "armeabi", apkAbiTargeting(ARMEABI));

    ModuleSplit instantApk1 = makeInstant(splitApk1);
    ModuleSplit instantApk2 = makeInstant(splitApk2);
    ModuleSplit instantApk3 = makeInstant(splitApk3);

    ModuleSplit standaloneApk1 =
        createFusedSplit(BASE_MODULE_NAME, apkAbiTargeting(ARMEABI), variantAbiTargeting(ARMEABI));
    ModuleSplit standaloneApk2 =
        createFusedSplit(BASE_MODULE_NAME, apkAbiTargeting(X86), variantAbiTargeting(X86));

    GeneratedApks generatedApks =
        GeneratedApks.builder()
            .setInstantApks(ImmutableList.of(instantApk1, instantApk2, instantApk3))
            .setSplitApks(ImmutableList.of(splitApk1, splitApk2, splitApk3))
            .setStandaloneApks(ImmutableList.of(standaloneApk1, standaloneApk2))
            .build();

    ImmutableList<Variant> variants = apkSerializerManager.serializeApks(generatedApks);

    assertThat(variants).hasSize(4);
    // Instant APKs first.
    assertThat(isInstantSplit(variants.get(0))).isTrue();
    assertThat(variants.get(0).getVariantNumber()).isEqualTo(0);
    assertThat(
            getApksForModule(variants.get(0), BASE_MODULE_NAME)
                .map(this::extractVersionCode)
                .allMatch(isEqual(1000)))
        .isTrue();
    // Then standalone APKs.
    assertThat(isStandaloneVariant(variants.get(1))).isTrue();
    assertThat(variants.get(1).getVariantNumber()).isEqualTo(1);
    assertThat(
            getApksForModule(variants.get(1), BASE_MODULE_NAME)
                .map(this::extractVersionCode)
                .allMatch(isEqual(1001)))
        .isTrue();
    assertThat(isStandaloneVariant(variants.get(2))).isTrue();
    assertThat(variants.get(2).getVariantNumber()).isEqualTo(2);
    assertThat(
            getApksForModule(variants.get(2), BASE_MODULE_NAME)
                .map(this::extractVersionCode)
                .allMatch(isEqual(1002)))
        .isTrue();
    // Then split APKs.
    assertThat(isSplitVariant(variants.get(3))).isTrue();
    assertThat(
            getApksForModule(variants.get(3), BASE_MODULE_NAME)
                .map(this::extractVersionCode)
                .allMatch(isEqual(1003)))
        .isTrue();
  }

  @Test
  public void apkModifier_allSplitsFromSameVariantHaveSameVariantNumber() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                BASE_MODULE_NAME, module -> module.setManifest(androidManifest("com.test.app")))
            .addModule(
                FEATURE_MODULE_NAME, module -> module.setManifest(androidManifest("com.test.app")))
            .build();

    ApkSerializerManager apkSerializerManager =
        createApkSerializerManager(appBundle, ApkListener.NO_OP, VERSION_CODE_MODIFIER);

    // The following splits are all parts of the same variant.
    ModuleSplit splitApk1 = createMasterModuleSplit(BASE_MODULE_NAME);
    ModuleSplit splitApk2 = createConfigModuleSplit(BASE_MODULE_NAME, "x86", apkAbiTargeting(X86));
    ModuleSplit splitApk3 =
        createConfigModuleSplit(BASE_MODULE_NAME, "armeabi", apkAbiTargeting(ARMEABI));
    ModuleSplit splitApk4 = createMasterModuleSplit(FEATURE_MODULE_NAME);
    ModuleSplit splitApk5 =
        createConfigModuleSplit(FEATURE_MODULE_NAME, "feature_x86", apkAbiTargeting(X86));
    ModuleSplit splitApk6 =
        createConfigModuleSplit(FEATURE_MODULE_NAME, "feature_armeabi", apkAbiTargeting(ARMEABI));

    GeneratedApks generatedApks =
        GeneratedApks.builder()
            .setSplitApks(
                ImmutableList.of(splitApk1, splitApk2, splitApk3, splitApk4, splitApk5, splitApk6))
            .build();

    ImmutableList<Variant> variants = apkSerializerManager.serializeApks(generatedApks);

    assertThat(variants).hasSize(1);
    assertThat(getApksForModule(variants.get(0), BASE_MODULE_NAME).map(this::extractVersionCode))
        .containsExactly(1000, 1000, 1000);
    assertThat(getApksForModule(variants.get(0), FEATURE_MODULE_NAME).map(this::extractVersionCode))
        .containsExactly(1000, 1000, 1000);
  }

  @Test
  public void apkModifier_modificationVariesBasedOnApkType() throws IOException {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(androidManifest("com.test.app")))
            .build();

    ApkSerializerManager apkSerializerManager =
        createApkSerializerManager(
            appBundle,
            ApkListener.NO_OP,
            new ApkModifier() {
              @Override
              public AndroidManifest modifyManifest(
                  AndroidManifest manifest, ApkDescription apkDescription) {
                return manifest
                    .toEditor()
                    .setVersionCode(
                        (apkDescription.getApkType() == ApkType.STANDALONE ? 1000 : 2000)
                            + apkDescription.getVariantNumber())
                    .save();
              }
            });

    // The following splits are all parts of the same variant.
    ModuleSplit splitApk1 = createMasterModuleSplit("base");
    ModuleSplit splitApk2 = createConfigModuleSplit("base", "x86", apkAbiTargeting(X86));
    ModuleSplit splitApk3 = createConfigModuleSplit("base", "armeabi", apkAbiTargeting(ARMEABI));
    // Each standalone APK has its own variant.
    ModuleSplit standaloneApk1 =
        createFusedSplit("base", apkAbiTargeting(X86), variantAbiTargeting(X86));
    ModuleSplit standaloneApk2 =
        createFusedSplit("base", apkAbiTargeting(ARMEABI), variantAbiTargeting(ARMEABI));

    GeneratedApks generatedApks =
        GeneratedApks.builder()
            .setSplitApks(ImmutableList.of(splitApk1, splitApk2, splitApk3))
            .setStandaloneApks(ImmutableList.of(standaloneApk1, standaloneApk2))
            .build();

    ImmutableList<Variant> variants = apkSerializerManager.serializeApks(generatedApks);

    assertThat(variants).hasSize(3);
    // First two variants are standalone APKs.
    assertThat(getApksForModule(variants.get(0), "base").map(this::extractVersionCode))
        .containsExactly(1000);
    assertThat(getApksForModule(variants.get(1), "base").map(this::extractVersionCode))
        .containsExactly(1001);
    // Last variant is split APKs.
    assertThat(getApksForModule(variants.get(2), "base").map(this::extractVersionCode))
        .containsExactly(2002, 2002, 2002);
  }

  @Test
  public void apkModifier_targetingPresentInApkDescription() throws IOException {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(androidManifest("com.test.app")))
            .build();

    Collection<ApkModifier.ApkDescription> apkDescriptions = new ArrayList<>();
    ApkSerializerManager apkSerializerManager =
        createApkSerializerManager(
            appBundle,
            ApkListener.NO_OP,
            new ApkModifier() {
              @Override
              public AndroidManifest modifyManifest(
                  AndroidManifest manifest, ApkDescription apkDescription) {
                apkDescriptions.add(apkDescription);
                return manifest;
              }
            });

    ModuleSplit splitApk1 = createMasterModuleSplit("base");
    ModuleSplit splitApk2 = createConfigModuleSplit("base", "x86", apkAbiTargeting(X86));
    ModuleSplit splitApk3 = createConfigModuleSplit("base", "armeabi", apkAbiTargeting(ARMEABI));
    ModuleSplit standaloneApk1 =
        createFusedSplit("base", apkAbiTargeting(X86), variantAbiTargeting(X86));
    ModuleSplit standaloneApk2 =
        createFusedSplit("base", apkAbiTargeting(ARMEABI), variantAbiTargeting(ARMEABI));

    GeneratedApks generatedApks =
        GeneratedApks.builder()
            .setSplitApks(ImmutableList.of(splitApk1, splitApk2, splitApk3))
            .setStandaloneApks(ImmutableList.of(standaloneApk1, standaloneApk2))
            .build();

    apkSerializerManager.serializeApks(generatedApks);

    assertThat(apkDescriptions.stream().map(ApkModifier.ApkDescription::getVariantTargeting))
        .containsExactly(
            // Standalone APKs.
            variantAbiTargeting(AbiAlias.X86),
            variantAbiTargeting(AbiAlias.ARMEABI),
            // Split APKs.
            variantSdkTargeting(21),
            variantSdkTargeting(21),
            variantSdkTargeting(21));

    assertThat(apkDescriptions.stream().map(ApkModifier.ApkDescription::getApkTargeting))
        .containsExactly(
            // Standalone APKs.
            apkAbiTargeting(AbiAlias.X86),
            apkAbiTargeting(AbiAlias.ARMEABI),
            // Split APKs.
            ApkTargeting.getDefaultInstance(),
            apkAbiTargeting(AbiAlias.X86),
            apkAbiTargeting(AbiAlias.ARMEABI));
  }

  @Test
  public void firstVariantNumber_notZero() throws IOException {
    final int firstVariantNumber = 10;

    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(androidManifest("com.test.app")))
            .build();

    ApkSerializerManager apkSerializerManager =
        createApkSerializerManager(
            appBundle, ApkListener.NO_OP, ApkModifier.NO_OP, firstVariantNumber);

    ModuleSplit splitApk = createMasterModuleSplit("base");

    GeneratedApks generatedApks =
        GeneratedApks.builder().setSplitApks(ImmutableList.of(splitApk)).build();

    ImmutableList<Variant> variants = apkSerializerManager.serializeApks(generatedApks);

    assertThat(variants).hasSize(1);
    assertThat(variants.get(0).getVariantNumber()).isEqualTo(firstVariantNumber);
  }

  @Test
  public void serializeApks_apkCreationHandlerInvoked() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(androidManifest("com.test.app")))
            .build();
    ModuleSplit masterSplitApk = createMasterModuleSplit("base");
    ModuleSplit configSplitApk = createConfigModuleSplit("base", "x86", apkAbiTargeting(X86));
    ModuleSplit standaloneApk =
        createFusedSplit("base", apkAbiTargeting(X86), variantAbiTargeting(X86));
    ModuleSplit instantApk = makeInstant(masterSplitApk);
    GeneratedApks generatedApks =
        GeneratedApks.builder()
            .setSplitApks(ImmutableList.of(masterSplitApk, configSplitApk))
            .setStandaloneApks(ImmutableList.of(standaloneApk))
            .setInstantApks(ImmutableList.of(instantApk))
            .build();

    ApkListener apkListener = Mockito.mock(ApkListener.class);
    ApkSerializerManager apkSerializerManager =
        createApkSerializerManager(appBundle, apkListener, ApkModifier.NO_OP);

    ImmutableList<Variant> variants = apkSerializerManager.serializeApks(generatedApks);

    ArgumentCaptor<ApkDescription> apkDescArg = ArgumentCaptor.forClass(ApkDescription.class);
    verify(apkListener, times(4)).onApkFinalized(apkDescArg.capture());
    assertThat(apkDescArg.getAllValues()).containsExactlyElementsIn(getApkDescriptions(variants));
  }

  @Test
  public void serializeApksForDevice_apkCreationHandlerInvoked() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(androidManifest("com.test.app")))
            .build();
    ModuleSplit masterSplitApk = createMasterModuleSplit("base");
    ModuleSplit configSplitApk = createConfigModuleSplit("base", "x86", apkAbiTargeting(X86));
    GeneratedApks generatedApks =
        GeneratedApks.builder()
            .setSplitApks(ImmutableList.of(masterSplitApk, configSplitApk))
            .build();

    ApkListener apkListener = Mockito.mock(ApkListener.class);
    ApkSerializerManager apkSerializerManager =
        createApkSerializerManager(appBundle, apkListener, ApkModifier.NO_OP);

    ImmutableList<Variant> variants =
        apkSerializerManager.serializeApksForDevice(generatedApks, lDeviceWithAbis("x86"));

    ArgumentCaptor<ApkDescription> apkDescArg = ArgumentCaptor.forClass(ApkDescription.class);
    verify(apkListener, times(2)).onApkFinalized(apkDescArg.capture());

    assertThat(apkDescArg.getAllValues()).containsExactlyElementsIn(getApkDescriptions(variants));
  }

  @Test
  public void serializeUniversalApk_apkCreationHandlerInvoked() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(androidManifest("com.test.app")))
            .build();
    ModuleSplit standaloneApk =
        createFusedSplit("base", apkAbiTargeting(X86), variantAbiTargeting(X86));
    GeneratedApks generatedApks =
        GeneratedApks.builder().setStandaloneApks(ImmutableList.of(standaloneApk)).build();

    ApkListener apkListener = Mockito.mock(ApkListener.class);
    ApkSerializerManager apkSerializerManager =
        createApkSerializerManager(appBundle, apkListener, ApkModifier.NO_OP);

    ImmutableList<Variant> variants = apkSerializerManager.serializeUniversalApk(generatedApks);

    ArgumentCaptor<ApkDescription> apkDescArg = ArgumentCaptor.forClass(ApkDescription.class);
    verify(apkListener).onApkFinalized(apkDescArg.capture());
    assertThat(apkDescArg.getAllValues()).containsExactlyElementsIn(getApkDescriptions(variants));
  }

  private static ImmutableList<ApkDescription> getApkDescriptions(ImmutableList<Variant> variants) {
    return variants
        .stream()
        .flatMap(variant -> variant.getApkSetList().stream())
        .flatMap(apkSet -> apkSet.getApkDescriptionList().stream())
        .collect(toImmutableList());
  }

  private Stream<File> getApksForModule(Variant variant, String moduleName) {
    return variant.getApkSetList().stream()
        .filter(apkSet -> apkSet.getModuleMetadata().getName().equals(moduleName))
        .flatMap(apkSet -> apkSet.getApkDescriptionList().stream())
        .map(apkDescription -> outputDir.resolve(apkDescription.getPath()).toFile());
  }

  private static boolean isStandaloneVariant(Variant variant) {
    return variant.getApkSetList().stream()
        .flatMap(apkSet -> apkSet.getApkDescriptionList().stream())
        .allMatch(apkDescription -> apkDescription.hasStandaloneApkMetadata());
  }

  private static boolean isSplitVariant(Variant variant) {
    return variant.getApkSetList().stream()
        .flatMap(apkSet -> apkSet.getApkDescriptionList().stream())
        .allMatch(apkDescription -> apkDescription.hasSplitApkMetadata());
  }

  private static boolean isInstantSplit(Variant variant) {
    return variant.getApkSetList().stream()
        .flatMap(apkSet -> apkSet.getApkDescriptionList().stream())
        .allMatch(apkDescription -> apkDescription.hasInstantApkMetadata());
  }

  private static ModuleSplit createFusedSplit(
      String fusedModuleNames, ApkTargeting apkTargeting, VariantTargeting variantTargeting) {
    return ModuleSplit.builder()
        .setAndroidManifest(
            AndroidManifest.create(
                androidManifest("com.test.app", withFusedModuleNames(fusedModuleNames))))
        .setEntries(ImmutableList.of())
        .setMasterSplit(false)
        .setSplitType(SplitType.STANDALONE)
        .setModuleName(BundleModuleName.create(BASE_MODULE_NAME))
        .setApkTargeting(apkTargeting)
        .setVariantTargeting(variantTargeting)
        .build();
  }

  private static ModuleSplit createMasterModuleSplit(String moduleName) {
    return createModuleSplit(
        moduleName,
        moduleName.equals(BASE_MODULE_NAME) ? "" : moduleName,
        true,
        ApkTargeting.getDefaultInstance(),
        variantSdkTargeting(sdkVersionFrom(21)));
  }

  private static ModuleSplit createConfigModuleSplit(
      String moduleName, String splitId, ApkTargeting apkTargeting) {
    return createModuleSplit(
        moduleName, splitId, false, apkTargeting, variantSdkTargeting(sdkVersionFrom(21)));
  }

  private static ModuleSplit createModuleSplit(
      String moduleName,
      String splitId,
      boolean isMasterSplit,
      ApkTargeting apkTargeting,
      VariantTargeting variantTargeting) {
    return ModuleSplit.builder()
        .setAndroidManifest(
            AndroidManifest.create(androidManifest("com.test.app", withSplitId(splitId))))
        .setEntries(ImmutableList.of())
        .setMasterSplit(isMasterSplit)
        .setModuleName(BundleModuleName.create(moduleName))
        .setApkTargeting(apkTargeting)
        .setVariantTargeting(variantTargeting)
        .build();
  }

  private static ModuleSplit createInstantModuleSplit(
      String moduleName,
      String splitId,
      boolean isMasterSplit,
      ApkTargeting apkTargeting,
      VariantTargeting variantTargeting) {
    return ModuleSplit.builder()
        .setAndroidManifest(
            AndroidManifest.create(androidManifest("com.test.app", withSplitId(splitId))))
        .setEntries(ImmutableList.of())
        .setMasterSplit(isMasterSplit)
        .setSplitType(SplitType.INSTANT)
        .setModuleName(BundleModuleName.create(moduleName))
        .setApkTargeting(apkTargeting)
        .setVariantTargeting(variantTargeting)
        .build();
  }

  private static ModuleSplit setManifestExtractNativeLibs(
      ModuleSplit moduleSplit, boolean extractNativeLibs) {
    return moduleSplit
        .toBuilder()
        .setAndroidManifest(
            moduleSplit
                .getAndroidManifest()
                .toEditor()
                .setExtractNativeLibsValue(extractNativeLibs)
                .save())
        .build();
  }

  private int extractVersionCode(File apk) {
    Path protoApkPath = tmp.getRoot().toPath().resolve("proto.apk");
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

  private ModuleSplit makeInstant(ModuleSplit moduleSplit) {
    return moduleSplit.toBuilder().setSplitType(SplitType.INSTANT).build();
  }

  private ApkSerializerManager createApkSerializerManager(AppBundle appBundle) {
    return createApkSerializerManager(appBundle, ApkListener.NO_OP, ApkModifier.NO_OP);
  }

  private ApkSerializerManager createApkSerializerManager(
      AppBundle appBundle, ApkListener apkListener, ApkModifier apkModifier) {
    return createApkSerializerManager(
        appBundle, apkListener, apkModifier, /* firstVariantNumber= */ 0);
  }

  private ApkSerializerManager createApkSerializerManager(
      AppBundle appBundle,
      ApkListener apkListener,
      ApkModifier apkModifier,
      int firstVariantNumber) {
    return new ApkSerializerManager(
        appBundle,
        apkSetBuilder,
        newDirectExecutorService(),
        apkListener,
        apkModifier,
        firstVariantNumber);
  }
}
