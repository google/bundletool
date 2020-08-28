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

package com.android.tools.build.bundletool.splitters;

import static com.android.bundle.Targeting.Abi.AbiAlias.ARMEABI_V7A;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86_64;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apexImageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apexImages;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkMultiAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkMultiAbiTargetingFromAllTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedApexImage;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedApexImageWithBuildInfo;
import static com.android.tools.build.bundletool.testing.TestUtils.extractPaths;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.bundle.Files.ApexImages;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the AbiApexImagesSplitter class. */
@RunWith(JUnit4.class)
public class AbiApexImagesSplitterTest {

  @Test
  public void splittingBySingleAbi_oneImageFile() throws Exception {
    AbiApexImagesSplitter abiApexImagesSplitter = new AbiApexImagesSplitter();
    ImmutableCollection<ModuleSplit> splits =
        abiApexImagesSplitter.split(
            ModuleSplit.forApex(createSingleImageModule("testModule", "x86")));

    ModuleSplit x86Split = Iterables.getOnlyElement(splits.asList());
    assertThat(x86Split.getApkTargeting()).isEqualTo(apkMultiAbiTargeting(X86));
    assertThat(extractPaths(x86Split.getEntries())).containsExactly("apex/x86.img");
  }

  @Test
  public void splittingBySingleAbi_twoImageFiles() throws Exception {
    ApexImages apexConfig =
        apexImages(
            targetedApexImage("apex/x86.img", apexImageTargeting("x86")),
            targetedApexImage("apex/x86_64.img", apexImageTargeting("x86_64")));
    BundleModule bundleModule =
        new BundleModuleBuilder("testModule")
            .addFile("apex/x86.img")
            .addFile("apex/x86_64.img")
            .setApexConfig(apexConfig)
            .setManifest(androidManifest("com.test.app"))
            .build();

    AbiApexImagesSplitter abiApexImagesSplitter = new AbiApexImagesSplitter();
    ImmutableCollection<ModuleSplit> splits =
        abiApexImagesSplitter.split(ModuleSplit.forApex(bundleModule));

    assertThat(splits).hasSize(2);
    ApkTargeting x86Targeting = apkMultiAbiTargeting(X86, ImmutableSet.of(X86_64));
    ApkTargeting x64Targeting = apkMultiAbiTargeting(X86_64, ImmutableSet.of(X86));
    ImmutableMap<ApkTargeting, ModuleSplit> splitsByTargeting =
        Maps.uniqueIndex(splits, ModuleSplit::getApkTargeting);
    assertThat(splitsByTargeting.keySet()).containsExactly(x86Targeting, x64Targeting);
    assertThat(extractPaths(splitsByTargeting.get(x86Targeting).getEntries()))
        .containsExactly("apex/x86.img");
    assertThat(extractPaths(splitsByTargeting.get(x64Targeting).getEntries()))
        .containsExactly("apex/x86_64.img");
  }

  @Test
  public void splittingBySingleAbi_twoImageFilesWithBuildInfo() throws Exception {
    ApexImages apexConfig =
        apexImages(
            targetedApexImageWithBuildInfo(
                "apex/x86.img", "apex/x86.build_info.pb", apexImageTargeting("x86")),
            targetedApexImageWithBuildInfo(
                "apex/x86_64.img", "apex/x86_64.build_info.pb", apexImageTargeting("x86_64")));
    BundleModule bundleModule =
        new BundleModuleBuilder("testModule")
            .addFile("apex/x86.img")
            .addFile("apex/x86.build_info.pb")
            .addFile("apex/x86_64.img")
            .addFile("apex/x86_64.build_info.pb")
            .setApexConfig(apexConfig)
            .setManifest(androidManifest("com.test.app"))
            .build();

    AbiApexImagesSplitter abiApexImagesSplitter = new AbiApexImagesSplitter();
    ImmutableCollection<ModuleSplit> splits =
        abiApexImagesSplitter.split(ModuleSplit.forApex(bundleModule));

    assertThat(splits).hasSize(2);
    ApkTargeting x86Targeting = apkMultiAbiTargeting(X86, ImmutableSet.of(X86_64));
    ApkTargeting x64Targeting = apkMultiAbiTargeting(X86_64, ImmutableSet.of(X86));
    ImmutableMap<ApkTargeting, ModuleSplit> splitsByTargeting =
        Maps.uniqueIndex(splits, ModuleSplit::getApkTargeting);
    assertThat(splitsByTargeting.keySet()).containsExactly(x86Targeting, x64Targeting);
    assertThat(extractPaths(splitsByTargeting.get(x86Targeting).getEntries()))
        .containsExactly("apex/x86.img", "apex/x86.build_info.pb");
    assertThat(extractPaths(splitsByTargeting.get(x64Targeting).getEntries()))
        .containsExactly("apex/x86_64.img", "apex/x86_64.build_info.pb");
  }

  @Test
  public void splittingByMultipleAbi_multipleImageFiles() throws Exception {
    ApexImages apexConfig =
        apexImages(
            targetedApexImage("apex/x86_64.x86.img", apexImageTargeting("x86_64", "x86")),
            targetedApexImage(
                "apex/x86_64.armeabi-v7a.img", apexImageTargeting("x86_64", "armeabi-v7a")),
            targetedApexImage("apex/x86_64.img", apexImageTargeting("x86_64")),
            targetedApexImage("apex/x86.armeabi-v7a.img", apexImageTargeting("x86", "armeabi-v7a")),
            targetedApexImage("apex/x86.img", apexImageTargeting("x86")),
            targetedApexImage("apex/armeabi-v7a.img", apexImageTargeting("armeabi-v7a")));
    BundleModule bundleModule =
        new BundleModuleBuilder("testModule")
            .addFile("apex/x86_64.x86.img")
            .addFile("apex/x86_64.armeabi-v7a.img")
            .addFile("apex/x86_64.img")
            .addFile("apex/x86.armeabi-v7a.img")
            .addFile("apex/x86.img")
            .addFile("apex/armeabi-v7a.img")
            .setApexConfig(apexConfig)
            .setManifest(androidManifest("com.test.app"))
            .build();

    AbiApexImagesSplitter abiApexImagesSplitter = new AbiApexImagesSplitter();
    ImmutableCollection<ModuleSplit> splits =
        abiApexImagesSplitter.split(ModuleSplit.forApex(bundleModule));

    assertThat(splits).hasSize(6);
    ImmutableSet<AbiAlias> x64X86Set = ImmutableSet.of(X86, X86_64);
    ImmutableSet<AbiAlias> x64ArmSet = ImmutableSet.of(ARMEABI_V7A, X86_64);
    ImmutableSet<AbiAlias> x64Set = ImmutableSet.of(X86_64);
    ImmutableSet<AbiAlias> x86ArmSet = ImmutableSet.of(ARMEABI_V7A, X86);
    ImmutableSet<AbiAlias> x86Set = ImmutableSet.of(X86);
    ImmutableSet<AbiAlias> armSet = ImmutableSet.of(ARMEABI_V7A);
    ImmutableSet<ImmutableSet<AbiAlias>> allTargeting =
        ImmutableSet.of(armSet, x86ArmSet, x64ArmSet, x86Set, x64X86Set, x64Set);
    ApkTargeting x64X86Targeting = apkMultiAbiTargetingFromAllTargeting(x64X86Set, allTargeting);
    ApkTargeting x64ArmTargeting = apkMultiAbiTargetingFromAllTargeting(x64ArmSet, allTargeting);
    ApkTargeting a64Targeting = apkMultiAbiTargetingFromAllTargeting(x64Set, allTargeting);
    ApkTargeting x86ArmTargeting = apkMultiAbiTargetingFromAllTargeting(x86ArmSet, allTargeting);
    ApkTargeting x86Targeting = apkMultiAbiTargetingFromAllTargeting(x86Set, allTargeting);
    ApkTargeting armTargeting = apkMultiAbiTargetingFromAllTargeting(armSet, allTargeting);
    ImmutableMap<ApkTargeting, ModuleSplit> splitsByTargeting =
        Maps.uniqueIndex(splits, ModuleSplit::getApkTargeting);
    assertThat(splitsByTargeting.keySet())
        .containsExactly(
            x64X86Targeting,
            x64ArmTargeting,
            a64Targeting,
            x86ArmTargeting,
            x86Targeting,
            armTargeting);
    assertThat(extractPaths(splitsByTargeting.get(x64X86Targeting).getEntries()))
        .containsExactly("apex/x86_64.x86.img");
    assertThat(extractPaths(splitsByTargeting.get(x64ArmTargeting).getEntries()))
        .containsExactly("apex/x86_64.armeabi-v7a.img");
    assertThat(extractPaths(splitsByTargeting.get(a64Targeting).getEntries()))
        .containsExactly("apex/x86_64.img");
    assertThat(extractPaths(splitsByTargeting.get(x86ArmTargeting).getEntries()))
        .containsExactly("apex/x86.armeabi-v7a.img");
    assertThat(extractPaths(splitsByTargeting.get(x86Targeting).getEntries()))
        .containsExactly("apex/x86.img");
    assertThat(extractPaths(splitsByTargeting.get(armTargeting).getEntries()))
        .containsExactly("apex/armeabi-v7a.img");
  }

  /** Creates a minimal module with one apex image file targeted at the given cpu architecture. */
  private static BundleModule createSingleImageModule(String moduleName, String architecture)
      throws Exception {
    String relativeFilePath = "apex/" + architecture + ".img";
    ApexImages apexConfig =
        apexImages(targetedApexImage(relativeFilePath, apexImageTargeting(architecture)));

    return new BundleModuleBuilder(moduleName)
        .addFile(relativeFilePath)
        .setApexConfig(apexConfig)
        .setManifest(androidManifest("com.test.app"))
        .build();
  }
}
