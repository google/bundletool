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

import static com.android.bundle.Targeting.Abi.AbiAlias.X86;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.LDPI_VALUE;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.MDPI_VALUE;
import static com.android.tools.build.bundletool.testing.DeviceFactory.abis;
import static com.android.tools.build.bundletool.testing.DeviceFactory.density;
import static com.android.tools.build.bundletool.testing.DeviceFactory.locales;
import static com.android.tools.build.bundletool.testing.DeviceFactory.mergeSpecs;
import static com.android.tools.build.bundletool.testing.DeviceFactory.sdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.TargetingUtils.abiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeLibraries;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedNativeDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.toAbi;
import static com.android.tools.build.bundletool.testing.TargetingUtils.toScreenDensity;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantMinSdkTargeting;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.bundle.Targeting.Abi;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.AbiTargeting;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.model.version.Version;
import com.android.tools.build.bundletool.optimizations.ApkOptimizations;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.android.tools.build.bundletool.testing.ResourceTableBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class ShardedApksGeneratorTest {
  private static final Version BUNDLETOOL_VERSION = BundleToolVersion.getCurrentVersion();
  private static final BundleMetadata DEFAULT_METADATA = BundleMetadata.builder().build();
  private static final ApkOptimizations DEFAULT_APK_OPTIMIZATIONS =
      ApkOptimizations.getDefaultOptimizationsForVersion(BUNDLETOOL_VERSION);

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();
  private Path tmpDir;

  @Before
  public void setUp() throws Exception {
    tmpDir = tmp.getRoot().toPath();
  }

  @DataPoints("standaloneSplitTypes")
  public static final ImmutableSet<SplitType> STANDALONE_SPLIT_TYPES =
      ImmutableSet.of(SplitType.STANDALONE, SplitType.SYSTEM);

  @Test
  @Theory
  public void simpleMultipleModules(
      @FromDataPoints("standaloneSplitTypes") SplitType standaloneSplitType) throws Exception {

    ImmutableList<BundleModule> bundleModule =
        ImmutableList.of(
            new BundleModuleBuilder("base")
                .addFile("assets/leftover.txt")
                .setManifest(androidManifest("com.test.app"))
                .build(),
            new BundleModuleBuilder("test")
                .addFile("assets/test.txt")
                .setManifest(androidManifest("com.test.app"))
                .build());

    ImmutableList<ModuleSplit> moduleSplits =
        generateModuleSplits(bundleModule, standaloneSplitType, /* generate64BitShards= */ true);

    assertThat(moduleSplits).hasSize(1);
    ModuleSplit moduleSplit = moduleSplits.get(0);
    assertThat(moduleSplit.getSplitType()).isEqualTo(standaloneSplitType);
    assertThat(getEntriesPaths(moduleSplit))
        .containsExactly("assets/test.txt", "assets/leftover.txt");
    assertThat(moduleSplit.getVariantTargeting()).isEqualTo(variantMinSdkTargeting(1));
  }

  @Test
  @Theory
  public void singleModule_withNativeLibsAndDensity(
      @FromDataPoints("standaloneSplitTypes") SplitType standaloneSplitType) throws Exception {

    ImmutableList<BundleModule> bundleModule =
        ImmutableList.of(
            new BundleModuleBuilder("base")
                .addFile("lib/x86/libsome.so")
                .addFile("lib/x86_64/libsome.so")
                .setNativeConfig(
                    nativeLibraries(
                        targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86)),
                        targetedNativeDirectory(
                            "lib/x86_64", nativeDirectoryTargeting(AbiAlias.X86_64))))
                // Add some density-specific resources.
                .addFile("res/drawable-ldpi/image.jpg")
                .addFile("res/drawable-mdpi/image.jpg")
                .setResourceTable(
                    new ResourceTableBuilder()
                        .addPackage("com.test.app")
                        .addDrawableResourceForMultipleDensities(
                            "image",
                            ImmutableMap.of(
                                LDPI_VALUE,
                                "res/drawable-ldpi/image.jpg",
                                MDPI_VALUE,
                                "res/drawable-mdpi/image.jpg"))
                        .build())
                .setManifest(androidManifest("com.test.app"))
                .build());

    ImmutableList<ModuleSplit> moduleSplits =
        generateModuleSplits(bundleModule, standaloneSplitType, /* generate64BitShards= */ true);

    assertThat(moduleSplits).hasSize(14); // 7 (density), 2 (abi) splits
    assertThat(moduleSplits.stream().map(ModuleSplit::getSplitType).collect(toImmutableSet()))
        .containsExactly(standaloneSplitType);
  }

  @Test
  @Theory
  public void singleModule_withNativeLibsAndDensity_64bitNativeLibsDisabled(
      @FromDataPoints("standaloneSplitTypes") SplitType standaloneSplitType) throws Exception {

    ImmutableList<BundleModule> bundleModule =
        ImmutableList.of(
            new BundleModuleBuilder("base")
                .addFile("lib/x86/libsome.so")
                .addFile("lib/x86_64/libsome.so")
                .setNativeConfig(
                    nativeLibraries(
                        targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86)),
                        targetedNativeDirectory(
                            "lib/x86_64", nativeDirectoryTargeting(AbiAlias.X86_64))))
                // Add some density-specific resources.
                .addFile("res/drawable-ldpi/image.jpg")
                .addFile("res/drawable-mdpi/image.jpg")
                .setResourceTable(
                    new ResourceTableBuilder()
                        .addPackage("com.test.app")
                        .addDrawableResourceForMultipleDensities(
                            "image",
                            ImmutableMap.of(
                                LDPI_VALUE,
                                "res/drawable-ldpi/image.jpg",
                                MDPI_VALUE,
                                "res/drawable-mdpi/image.jpg"))
                        .build())
                .setManifest(androidManifest("com.test.app"))
                .build());

    ImmutableList<ModuleSplit> moduleSplits =
        generateModuleSplits(bundleModule, standaloneSplitType, /* generate64BitShards= */ false);

    assertThat(moduleSplits).hasSize(7); // 7 (density), 1 (abi) split
    // Verify that the only ABI is x86.
    ImmutableSet<Abi> abiTargetings =
        moduleSplits.stream()
            .map(ModuleSplit::getApkTargeting)
            .map(ApkTargeting::getAbiTargeting)
            .map(AbiTargeting::getValueList)
            .flatMap(List::stream)
            .collect(toImmutableSet());
    assertThat(abiTargetings).containsExactly(toAbi(X86));
    // And ABI has no alternatives.
    ImmutableSet<Abi> abiAlternatives =
        moduleSplits.stream()
            .map(ModuleSplit::getApkTargeting)
            .map(ApkTargeting::getAbiTargeting)
            .map(AbiTargeting::getAlternativesList)
            .flatMap(List::stream)
            .collect(toImmutableSet());
    assertThat(abiAlternatives).isEmpty();
    assertThat(moduleSplits.stream().map(ModuleSplit::getSplitType).collect(toImmutableSet()))
        .containsExactly(standaloneSplitType);
  }

  @Test
  public void singleModule_withNativeLibsAndDensityWithDeviceSpec_64bitNativeLibsDisabled()
      throws Exception {

    ImmutableList<BundleModule> bundleModule =
        ImmutableList.of(
            new BundleModuleBuilder("base")
                .addFile("lib/x86/libsome.so")
                .addFile("lib/x86_64/libsome.so")
                .setNativeConfig(
                    nativeLibraries(
                        targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86)),
                        targetedNativeDirectory(
                            "lib/x86_64", nativeDirectoryTargeting(AbiAlias.X86_64))))
                // Add some density-specific resources.
                .addFile("res/drawable-ldpi/image.jpg")
                .addFile("res/drawable-mdpi/image.jpg")
                .setResourceTable(
                    new ResourceTableBuilder()
                        .addPackage("com.test.app")
                        .addDrawableResourceForMultipleDensities(
                            "image",
                            ImmutableMap.of(
                                LDPI_VALUE,
                                "res/drawable-ldpi/image.jpg",
                                MDPI_VALUE,
                                "res/drawable-mdpi/image.jpg"))
                        .build())
                .setManifest(androidManifest("com.test.app"))
                .build());

    ImmutableList<ModuleSplit> moduleSplits =
        new ShardedApksGenerator(
                tmpDir, BUNDLETOOL_VERSION, SplitType.SYSTEM, /* generate64BitShards= */ false)
            .generateSystemSplits(
                bundleModule,
                DEFAULT_METADATA,
                DEFAULT_APK_OPTIMIZATIONS,
                Optional.of(
                    mergeSpecs(
                        sdkVersion(28),
                        abis("x86"),
                        density(DensityAlias.MDPI),
                        locales("en-US"))));

    assertThat(moduleSplits).hasSize(1); // 1 (density), 1 (abi) split
    ModuleSplit moduleSplit = moduleSplits.get(0);
    assertThat(moduleSplit.getApkTargeting().getAbiTargeting()).isEqualTo(abiTargeting(X86));
    assertThat(moduleSplit.getApkTargeting().getScreenDensityTargeting().getValueList())
        .containsExactly(toScreenDensity(DensityAlias.MDPI));
    assertThat(moduleSplits.stream().map(ModuleSplit::getSplitType).collect(toImmutableSet()))
        .containsExactly(SplitType.SYSTEM);
  }

  private ImmutableList<ModuleSplit> generateModuleSplits(
      ImmutableList<BundleModule> bundleModule,
      SplitType standaloneSplitType,
      boolean generate64BitShards) {
    if (standaloneSplitType.equals(SplitType.STANDALONE)) {
      return new ShardedApksGenerator(
              tmpDir, BUNDLETOOL_VERSION, standaloneSplitType, generate64BitShards)
          .generateSplits(bundleModule, DEFAULT_METADATA, DEFAULT_APK_OPTIMIZATIONS);
    } else {
      return new ShardedApksGenerator(
              tmpDir, BUNDLETOOL_VERSION, standaloneSplitType, generate64BitShards)
          .generateSystemSplits(
              bundleModule, DEFAULT_METADATA, DEFAULT_APK_OPTIMIZATIONS, Optional.empty());
    }
  }

  private static ImmutableSet<String> getEntriesPaths(ModuleSplit moduleSplit) {
    return moduleSplit.getEntries().stream()
        .map(moduleEntry -> moduleEntry.getPath().toString())
        .collect(toImmutableSet());
  }
}
