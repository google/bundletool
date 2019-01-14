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

import static com.android.bundle.Targeting.Abi.AbiAlias.ARM64_V8A;
import static com.android.bundle.Targeting.Abi.AbiAlias.ARMEABI_V7A;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86_64;
import static com.android.tools.build.bundletool.model.ManifestMutator.withSplitsRequired;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.compareManifestMutators;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.lPlusVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeLibraries;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedNativeDirectory;
import static com.android.tools.build.bundletool.testing.TestUtils.extractPaths;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AbiNativeLibrariesSplitterTest {

  @Test
  public void splittingByAbi() throws Exception {
    AbiNativeLibrariesSplitter abiNativeLibrariesSplitter = new AbiNativeLibrariesSplitter();
    ImmutableCollection<ModuleSplit> splits =
        abiNativeLibrariesSplitter.split(
            ModuleSplit.forNativeLibraries(
                createSingleLibraryModule("testModule", "x86", "lib/x86/libnoname.so")));

    assertThat(splits).hasSize(1);
    ModuleSplit x86Split = splits.asList().get(0);
    assertThat(x86Split.getApkTargeting()).isEqualTo(apkAbiTargeting(X86));
    assertThat(x86Split.getVariantTargeting()).isEqualTo(lPlusVariantTargeting());
    assertThat(extractPaths(x86Split.getEntries())).containsExactly("lib/x86/libnoname.so");
  }

  @Test
  public void splittingByMultipleAbis_64BitDisabled() throws Exception {
    NativeLibraries nativeConfig =
        nativeLibraries(
            targetedNativeDirectory("lib/armeabi-v7a", nativeDirectoryTargeting(ARMEABI_V7A)),
            targetedNativeDirectory("lib/arm64-v8a", nativeDirectoryTargeting(ARM64_V8A)),
            targetedNativeDirectory("lib/x86_64", nativeDirectoryTargeting(X86_64)));
    BundleModule bundleModule =
        new BundleModuleBuilder("testModule")
            .setNativeConfig(nativeConfig)
            .addFile("lib/armeabi-v7a/libtest.so")
            .addFile("lib/arm64-v8a/libtest.so")
            .addFile("lib/x86_64/libtest.so")
            .setManifest(androidManifest("com.test.app"))
            .build();

    AbiNativeLibrariesSplitter abiNativeLibrariesSplitter =
        new AbiNativeLibrariesSplitter(/* include64BitLibs= */ false);

    ImmutableCollection<ModuleSplit> splits =
        abiNativeLibrariesSplitter.split(ModuleSplit.forNativeLibraries(bundleModule));

    assertThat(splits).hasSize(1);
    ModuleSplit abiSplit = splits.asList().get(0);
    assertThat(abiSplit.getApkTargeting())
        .isEqualTo(apkAbiTargeting(ARMEABI_V7A, ImmutableSet.of()));
    assertThat(extractPaths(abiSplit.getEntries())).containsExactly("lib/armeabi-v7a/libtest.so");
  }

  @Test
  public void splittingByMultipleAbis_64BitDisabled_no32BitLibs_throws() throws Exception {
    NativeLibraries nativeConfig =
        nativeLibraries(
            targetedNativeDirectory("lib/arm64-v8a", nativeDirectoryTargeting(ARM64_V8A)),
            targetedNativeDirectory("lib/x86_64", nativeDirectoryTargeting(X86_64)));
    BundleModule bundleModule =
        new BundleModuleBuilder("testModule")
            .setNativeConfig(nativeConfig)
            .addFile("lib/arm64-v8a/libtest.so")
            .addFile("lib/x86_64/libtest.so")
            .setManifest(androidManifest("com.test.app"))
            .build();

    AbiNativeLibrariesSplitter abiNativeLibrariesSplitter =
        new AbiNativeLibrariesSplitter(/* include64BitLibs= */ false);

    CommandExecutionException exception =
        assertThrows(
            CommandExecutionException.class,
            () -> abiNativeLibrariesSplitter.split(ModuleSplit.forNativeLibraries(bundleModule)));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Generation of 64-bit native libraries is "
                + "disabled, but App Bundle contains only 64-bit native libraries.");
  }

  @Test
  public void splittingByMultipleAbis() throws Exception {
    NativeLibraries nativeConfig =
        nativeLibraries(
            targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86)),
            targetedNativeDirectory("lib/x86_64", nativeDirectoryTargeting(X86_64)));

    BundleModule bundleModule =
        new BundleModuleBuilder("testModule")
            .addFile("lib/x86/libtest.so")
            .addFile("lib/x86_64/libtest.so")
            .setNativeConfig(nativeConfig)
            .setManifest(androidManifest("com.test.app"))
            .build();

    AbiNativeLibrariesSplitter abiNativeLibrariesSplitter = new AbiNativeLibrariesSplitter();
    ImmutableCollection<ModuleSplit> splits =
        abiNativeLibrariesSplitter.split(ModuleSplit.forNativeLibraries(bundleModule));

    assertThat(splits).hasSize(2);
    assertThat(splits.stream().map(ModuleSplit::getApkTargeting).collect(toList()))
        .containsExactly(
            apkAbiTargeting(X86, ImmutableSet.of(X86_64)),
            apkAbiTargeting(X86_64, ImmutableSet.of(X86)));
    for (ModuleSplit split : splits) {
      if (split
          .getApkTargeting()
          .equals(apkAbiTargeting(ImmutableSet.of(X86), ImmutableSet.of(X86_64)))) {
        assertThat(extractPaths(split.getEntries())).containsExactly("lib/x86/libtest.so");
      }
      if (split
          .getApkTargeting()
          .equals(apkAbiTargeting(ImmutableSet.of(X86_64), ImmutableSet.of(X86)))) {
        assertThat(extractPaths(split.getEntries())).containsExactly("lib/x86_64/libtest.so");
      }
      assertThat(split.getVariantTargeting()).isEqualTo(lPlusVariantTargeting());
    }
  }

  @Test
  public void neverProducesMasterSplit() throws Exception {
    ModuleSplit inputSplit =
        ModuleSplit.forNativeLibraries(
            createSingleLibraryModule("testModule", "x86", "lib/x86/libnoname.so"));
    assertThat(inputSplit.isMasterSplit()).isTrue();

    ImmutableCollection<ModuleSplit> splits = new AbiNativeLibrariesSplitter().split(inputSplit);

    assertThat(splits).hasSize(1);
    assertThat(splits.asList().get(0).isMasterSplit()).isFalse();
  }

  @Test
  public void nativeSplitIdEqualsToModuleName_base() throws Exception {
    AbiNativeLibrariesSplitter abiNativeLibrariesSplitter = new AbiNativeLibrariesSplitter();
    ImmutableCollection<ModuleSplit> splits =
        abiNativeLibrariesSplitter.split(
            ModuleSplit.forNativeLibraries(createSingleLibraryModule("base", "x86")));

    assertThat(splits).hasSize(1);
    ModuleSplit x86Split = splits.asList().get(0);
    assertThat(
            x86Split
                .writeSplitIdInManifest(x86Split.getSuffix())
                .getAndroidManifest()
                .getSplitId()
                .get())
        .isEqualTo("config.x86");
  }

  @Test
  public void masterSplitIdEqualsToModuleName_nonBase() throws Exception {
    AbiNativeLibrariesSplitter abiNativeLibrariesSplitter = new AbiNativeLibrariesSplitter();
    ImmutableCollection<ModuleSplit> splits =
        abiNativeLibrariesSplitter.split(
            ModuleSplit.forNativeLibraries(createSingleLibraryModule("testModule", "armeabi-v7a")));

    assertThat(splits).hasSize(1);
    ModuleSplit armSplit = splits.asList().get(0);
    assertThat(
            armSplit
                .writeSplitIdInManifest(armSplit.getSuffix())
                .getAndroidManifest()
                .getSplitId()
                .get())
        .isEqualTo("testModule.config.armeabi_v7a");
  }

  @Test
  public void generatesSplitWithUnusedEntries() throws Exception {
    AbiNativeLibrariesSplitter abiNativeLibrariesSplitter = new AbiNativeLibrariesSplitter();

    BundleModule module =
        new BundleModuleBuilder("testModule")
            .addFile("lib/x86_64/libsome.so")
            .addFile("assets/leftover.txt")
            .addFile("dex/classes.dex")
            .setNativeConfig(
                nativeLibraries(
                    targetedNativeDirectory(
                        "lib/x86_64", nativeDirectoryTargeting(AbiAlias.X86_64))))
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableCollection<ModuleSplit> splits =
        abiNativeLibrariesSplitter.split(ModuleSplit.forModule(module));

    // x86_64 split and leftover split.
    assertThat(splits).hasSize(2);
    Map<ApkTargeting, ModuleSplit> splitByTargeting =
        Maps.uniqueIndex(splits, ModuleSplit::getApkTargeting);

    assertThat(splitByTargeting).containsKey(apkAbiTargeting(AbiAlias.X86_64));
    assertThat(extractPaths(splitByTargeting.get(apkAbiTargeting(AbiAlias.X86_64)).getEntries()))
        .containsExactly("lib/x86_64/libsome.so");

    assertThat(splitByTargeting).containsKey(ApkTargeting.getDefaultInstance());
    ModuleSplit leftOverSplit = splitByTargeting.get(ApkTargeting.getDefaultInstance());
    assertThat(leftOverSplit.isMasterSplit()).isTrue();
    assertThat(extractPaths(leftOverSplit.getEntries()))
        .containsExactly("dex/classes.dex", "assets/leftover.txt");
  }

  @Test
  public void manifestMutatorToRequireSplits_registered_whenAbisPresent() throws Exception {
    ModuleSplit inputSplit =
        ModuleSplit.forNativeLibraries(
            createSingleLibraryModule("testModule", "x86", "lib/x86/libnoname.so"));

    ImmutableCollection<ModuleSplit> splits = new AbiNativeLibrariesSplitter().split(inputSplit);

    assertThat(splits).hasSize(1);
    assertThat(
            compareManifestMutators(
                splits.asList().get(0).getMasterManifestMutators(), withSplitsRequired(true)))
        .isTrue();
  }

  @Test
  public void manifestMutatorToRequireSplits_notRegistered_whenNoAbis() throws Exception {
    ModuleSplit inputSplit =
        ModuleSplit.forNativeLibraries(
            new BundleModuleBuilder("testModule")
                .setManifest(androidManifest("com.test.app"))
                .build());

    ImmutableCollection<ModuleSplit> splits = new AbiNativeLibrariesSplitter().split(inputSplit);

    assertThat(splits).hasSize(1);
    assertThat(splits.asList().get(0).getMasterManifestMutators()).isEmpty();
  }

  /** Creates a minimal module with one native library targeted at the given cpu architecture. */
  private static BundleModule createSingleLibraryModule(
      String moduleName, String architecture, String relativeFilePath) throws Exception {
    NativeLibraries nativeConfig =
        nativeLibraries(
            targetedNativeDirectory("lib/" + architecture, nativeDirectoryTargeting(architecture)));

    return new BundleModuleBuilder(moduleName)
        .addFile(relativeFilePath)
        .setNativeConfig(nativeConfig)
        .setManifest(androidManifest("com.test.app"))
        .build();
  }

  private static BundleModule createSingleLibraryModule(String moduleName, String architecture)
      throws Exception {
    return createSingleLibraryModule(
        moduleName, architecture, "lib/" + architecture + "/libnoname.so");
  }

}
