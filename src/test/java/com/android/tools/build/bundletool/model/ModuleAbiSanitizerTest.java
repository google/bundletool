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

package com.android.tools.build.bundletool.model;

import static com.android.bundle.Targeting.Abi.AbiAlias.MIPS;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86_64;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeLibraries;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedNativeDirectory;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.bundle.Files.NativeLibraries;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ModuleAbiSanitizerTest {

  @Test
  public void noNativeLibraries_moduleUnchanged() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/file.txt")
            .setManifest(androidManifest("com.test.app"))
            .build();

    BundleModule sanitizedModule = new ModuleAbiSanitizer().sanitize(testModule);

    assertThat(sanitizedModule).isEqualTo(testModule);
  }

  @Test
  public void consistentNativeLibraries_moduleUnchanged() throws Exception {
    NativeLibraries nativeConfig =
        nativeLibraries(
            targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86)),
            targetedNativeDirectory("lib/x86_64", nativeDirectoryTargeting(X86_64)),
            targetedNativeDirectory("lib/mips", nativeDirectoryTargeting(MIPS)));
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("lib/x86/lib1.so")
            .addFile("lib/x86/lib2.so")
            .addFile("lib/x86/lib3.so")
            .addFile("lib/x86_64/lib1.so")
            .addFile("lib/x86_64/lib2.so")
            .addFile("lib/x86_64/lib3.so")
            .addFile("lib/mips/lib1.so")
            .addFile("lib/mips/lib2.so")
            .addFile("lib/mips/lib3.so")
            .setNativeConfig(nativeConfig)
            .setManifest(androidManifest("com.test.app"))
            .build();

    BundleModule sanitizedModule = new ModuleAbiSanitizer().sanitize(testModule);

    assertThat(sanitizedModule.getNativeConfig().get())
        .isEqualTo(testModule.getNativeConfig().get());
    assertThat(sanitizedModule.getEntries()).containsExactlyElementsIn(testModule.getEntries());
    // Sanity check that nothing else changed as well.
    assertThat(sanitizedModule).isEqualTo(testModule);
  }

  @Test
  public void inconsistentNativeLibraries_libFilesFilteredAwayAndNativeTargetingAdjusted()
      throws Exception {
    NativeLibraries nativeConfig =
        nativeLibraries(
            targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86)),
            targetedNativeDirectory("lib/x86_64", nativeDirectoryTargeting(X86_64)),
            targetedNativeDirectory("lib/mips", nativeDirectoryTargeting(MIPS)));
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("lib/x86/lib1.so")
            .addFile("lib/x86/lib2.so")
            .addFile("lib/x86/lib3.so")
            .addFile("lib/x86_64/lib1.so")
            .addFile("lib/x86_64/lib2.so")
            .addFile("lib/mips/lib1.so")
            .setNativeConfig(nativeConfig)
            .setManifest(androidManifest("com.test.app"))
            .build();

    BundleModule sanitizedModule = new ModuleAbiSanitizer().sanitize(testModule);

    assertThat(
            sanitizedModule.getEntries().stream()
                .map(ModuleEntry::getPath)
                .filter(entryPath -> entryPath.startsWith(ZipPath.create("lib")))
                .map(ZipPath::toString))
        .containsExactly("lib/x86/lib1.so", "lib/x86/lib2.so", "lib/x86/lib3.so");
    assertThat(sanitizedModule.getNativeConfig().get())
        .isEqualTo(
            nativeLibraries(targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86))));
  }
}
