/*
 * Copyright (C) 2019 The Android Open Source Project
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
import static com.android.bundle.Targeting.Sanitizer.SanitizerAlias.HWADDRESS;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkSanitizerTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeApkTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeLibraries;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedNativeDirectory;
import static com.android.tools.build.bundletool.testing.TestUtils.extractPaths;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.bundle.Files.NativeLibraries;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SanitizerNativeLibrariesSplitterTest {

  @Test
  public void splittingBySanitizer() throws Exception {
    NativeLibraries nativeConfig =
        nativeLibraries(
            targetedNativeDirectory("lib/arm64-v8a", nativeDirectoryTargeting(ARM64_V8A)),
            targetedNativeDirectory(
                "lib/arm64-v8a-hwasan", nativeDirectoryTargeting(ARM64_V8A, HWADDRESS)));
    BundleModule bundleModule =
        new BundleModuleBuilder("testModule")
            .setNativeConfig(nativeConfig)
            .addFile("lib/arm64-v8a/libtest.so")
            .addFile("lib/arm64-v8a-hwasan/libtest.so")
            .setManifest(androidManifest("com.test.app"))
            .build();
    SanitizerNativeLibrariesSplitter sanitizerNativeLibrariesSplitter =
        new SanitizerNativeLibrariesSplitter();
    ModuleSplit mainSplit =
        ModuleSplit.forNativeLibraries(bundleModule).toBuilder()
            .setApkTargeting(apkAbiTargeting(ARM64_V8A))
            .setMasterSplit(false)
            .build();
    ImmutableCollection<ModuleSplit> splits = sanitizerNativeLibrariesSplitter.split(mainSplit);

    assertThat(splits).hasSize(2);

    ModuleSplit nonHwasanSplit = splits.asList().get(1);
    assertThat(nonHwasanSplit.getApkTargeting()).isEqualTo(apkAbiTargeting(ARM64_V8A));
    assertThat(extractPaths(nonHwasanSplit.getEntries()))
        .containsExactly("lib/arm64-v8a/libtest.so");

    ModuleSplit hwasanSplit = splits.asList().get(0);
    assertThat(hwasanSplit.getApkTargeting())
        .isEqualTo(mergeApkTargeting(apkAbiTargeting(ARM64_V8A), apkSanitizerTargeting(HWADDRESS)));
    assertThat(extractPaths(hwasanSplit.getEntries()))
        .containsExactly("lib/arm64-v8a-hwasan/libtest.so");
  }

  @Test
  public void splittingNoSanitizerLib() throws Exception {
    NativeLibraries nativeConfig =
        nativeLibraries(
            targetedNativeDirectory("lib/arm64-v8a", nativeDirectoryTargeting(ARM64_V8A)),
            targetedNativeDirectory(
                "lib/arm64-v8a-hwasan", nativeDirectoryTargeting(ARM64_V8A, HWADDRESS)));
    BundleModule bundleModule =
        new BundleModuleBuilder("testModule")
            .setNativeConfig(nativeConfig)
            .addFile("lib/arm64-v8a/libtest.so")
            .setManifest(androidManifest("com.test.app"))
            .build();
    SanitizerNativeLibrariesSplitter sanitizerNativeLibrariesSplitter =
        new SanitizerNativeLibrariesSplitter();
    ModuleSplit mainSplit =
        ModuleSplit.forNativeLibraries(bundleModule).toBuilder()
            .setApkTargeting(apkAbiTargeting(ARM64_V8A))
            .setMasterSplit(false)
            .build();
    ImmutableCollection<ModuleSplit> splits = sanitizerNativeLibrariesSplitter.split(mainSplit);

    assertThat(splits).hasSize(1);

    ModuleSplit nonHwasanSplit = splits.asList().get(0);
    assertThat(nonHwasanSplit.getApkTargeting()).isEqualTo(apkAbiTargeting(ARM64_V8A));
    assertThat(extractPaths(nonHwasanSplit.getEntries()))
        .containsExactly("lib/arm64-v8a/libtest.so");
  }
}
