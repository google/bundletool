/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.build.bundletool.shards;

import static com.android.bundle.Targeting.Abi.AbiAlias.ARMEABI_V7A;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86_64;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apexImageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apexImages;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkMultiAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkMultiAbiTargetingFromAllTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedApexImage;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantMinSdkTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantMultiAbiTargetingFromAllTargeting;
import static com.android.tools.build.bundletool.testing.TestUtils.extractPaths;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.bundle.Files.ApexImages;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.tools.build.bundletool.commands.BuildApksModule;
import com.android.tools.build.bundletool.commands.CommandScoped;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.android.tools.build.bundletool.testing.TestModule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import dagger.Component;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class StandaloneApexApksGeneratorTest {

  @Inject StandaloneApexApksGenerator standaloneApexApksGenerator;

  @Before
  public void setUp() {
    TestComponent.useTestModule(this, TestModule.builder().build());
  }

  @Test
  public void shardApexModule_oneApexImage() throws Exception {
    ApexImages apexConfig =
        apexImages(targetedApexImage("apex/x86.img", apexImageTargeting("x86")));
    BundleModule apexModule =
        new BundleModuleBuilder("base")
            .addFile("apex/x86.img")
            .addFile("root/apex_manifest.json")
            .setManifest(androidManifest("com.test.app"))
            .setApexConfig(apexConfig)
            .build();

    ImmutableList<ModuleSplit> shards =
        standaloneApexApksGenerator.generateStandaloneApks(ImmutableList.of(apexModule));

    assertThat(shards).hasSize(1);
    assertThat(shards.get(0).getApkTargeting()).isEqualTo(apkMultiAbiTargeting(X86));
    assertThat(extractPaths(shards.get(0).getEntries()))
        .containsExactly("apex/x86.img", "root/apex_manifest.json");
  }

  @Test
  public void shardApexModule() throws Exception {
    ApexImages apexConfig =
        apexImages(
            targetedApexImage("apex/x86_64.x86.img", apexImageTargeting("x86_64", "x86")),
            targetedApexImage(
                "apex/x86_64.armeabi-v7a.img", apexImageTargeting("x86_64", "armeabi-v7a")),
            targetedApexImage("apex/x86_64.img", apexImageTargeting("x86_64")),
            targetedApexImage("apex/x86.armeabi-v7a.img", apexImageTargeting("x86", "armeabi-v7a")),
            targetedApexImage("apex/x86.img", apexImageTargeting("x86")),
            targetedApexImage("apex/armeabi-v7a.img", apexImageTargeting("armeabi-v7a")));
    BundleModule apexModule =
        new BundleModuleBuilder("base")
            .addFile("root/apex_manifest.json")
            .addFile("apex/x86_64.x86.img")
            .addFile("apex/x86_64.armeabi-v7a.img")
            .addFile("apex/x86_64.img")
            .addFile("apex/x86.armeabi-v7a.img")
            .addFile("apex/x86.img")
            .addFile("apex/armeabi-v7a.img")
            .setManifest(androidManifest("com.test.app"))
            .setApexConfig(apexConfig)
            .build();

    ImmutableList<ModuleSplit> shards =
        standaloneApexApksGenerator.generateStandaloneApks(ImmutableList.of(apexModule));

    assertThat(shards).hasSize(6);
    assertThat(shards.stream().map(ModuleSplit::getSplitType).distinct().collect(toImmutableSet()))
        .containsExactly(SplitType.STANDALONE);

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
        Maps.uniqueIndex(shards, ModuleSplit::getApkTargeting);
    assertThat(splitsByTargeting.keySet())
        .containsExactly(
            x64X86Targeting,
            x64ArmTargeting,
            a64Targeting,
            x86ArmTargeting,
            x86Targeting,
            armTargeting);
    assertThat(shards.stream().map(ModuleSplit::getVariantTargeting).collect(toImmutableSet()))
        .containsExactly(
            mergeVariantTargeting(
                variantMultiAbiTargetingFromAllTargeting(x64X86Set, allTargeting),
                variantMinSdkTargeting(1)),
            mergeVariantTargeting(
                variantMultiAbiTargetingFromAllTargeting(x64ArmSet, allTargeting),
                variantMinSdkTargeting(1)),
            mergeVariantTargeting(
                variantMultiAbiTargetingFromAllTargeting(x64Set, allTargeting),
                variantMinSdkTargeting(1)),
            mergeVariantTargeting(
                variantMultiAbiTargetingFromAllTargeting(x86ArmSet, allTargeting),
                variantMinSdkTargeting(1)),
            mergeVariantTargeting(
                variantMultiAbiTargetingFromAllTargeting(x86Set, allTargeting),
                variantMinSdkTargeting(1)),
            mergeVariantTargeting(
                variantMultiAbiTargetingFromAllTargeting(armSet, allTargeting),
                variantMinSdkTargeting(1)));

    assertThat(extractPaths(splitsByTargeting.get(x64X86Targeting).getEntries()))
        .containsExactly("root/apex_manifest.json", "apex/x86_64.x86.img");
    assertThat(extractPaths(splitsByTargeting.get(x64ArmTargeting).getEntries()))
        .containsExactly("root/apex_manifest.json", "apex/x86_64.armeabi-v7a.img");
    assertThat(extractPaths(splitsByTargeting.get(a64Targeting).getEntries()))
        .containsExactly("root/apex_manifest.json", "apex/x86_64.img");
    assertThat(extractPaths(splitsByTargeting.get(x86ArmTargeting).getEntries()))
        .containsExactly("root/apex_manifest.json", "apex/x86.armeabi-v7a.img");
    assertThat(extractPaths(splitsByTargeting.get(x86Targeting).getEntries()))
        .containsExactly("root/apex_manifest.json", "apex/x86.img");
    assertThat(extractPaths(splitsByTargeting.get(armTargeting).getEntries()))
        .containsExactly("root/apex_manifest.json", "apex/armeabi-v7a.img");
  }

  @CommandScoped
  @Component(modules = {BuildApksModule.class, TestModule.class})
  interface TestComponent {
    void inject(StandaloneApexApksGeneratorTest test);

    static void useTestModule(StandaloneApexApksGeneratorTest testInstance, TestModule testModule) {
      DaggerStandaloneApexApksGeneratorTest_TestComponent.builder()
          .testModule(testModule)
          .build()
          .inject(testInstance);
    }
  }
}
