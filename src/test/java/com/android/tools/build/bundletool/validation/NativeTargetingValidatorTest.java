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

package com.android.tools.build.bundletool.validation;

import static com.android.bundle.Targeting.Abi.AbiAlias.MIPS;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86_64;
import static com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias.DXT1;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeLibraries;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedNativeDirectory;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Files.NativeLibraries;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class NativeTargetingValidatorTest {

  @Test
  public void validateModule_valid_succeeds() throws Exception {
    NativeLibraries config =
        nativeLibraries(
            targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86)),
            targetedNativeDirectory("lib/x86_64", nativeDirectoryTargeting(X86_64)),
            targetedNativeDirectory("lib/mips", nativeDirectoryTargeting(MIPS)));
    BundleModule module =
        new BundleModuleBuilder("testModule")
            .setNativeConfig(config)
            .addFile("lib/x86/libX.so")
            .addFile("lib/x86_64/libX.so")
            .addFile("lib/mips/libX.so")
            .setManifest(androidManifest("com.test.app"))
            .build();

    new NativeTargetingValidator().validateModule(module);
  }

  @Test
  public void validateModule_abiTargetingDimensionNotSet_throws() throws Exception {
    NativeLibraries config =
        nativeLibraries(targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(DXT1)));
    BundleModule module =
        new BundleModuleBuilder("testModule")
            .addFile("lib/x86/libX.so")
            .setNativeConfig(config)
            .setManifest(androidManifest("com.test.app"))
            .build();

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new NativeTargetingValidator().validateModule(module));

    assertThat(e)
        .hasMessageThat()
        .contains("Targeted native directory 'lib/x86' does not have the ABI dimension set");
  }

  @Test
  public void validateModule_pathOutsideLib_throws() throws Exception {
    NativeLibraries config =
        nativeLibraries(
            targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86)),
            targetedNativeDirectory("assets/lib/x86_64", nativeDirectoryTargeting(X86_64)));
    BundleModule module =
        new BundleModuleBuilder("testModule")
            .setNativeConfig(config)
            .addFile("lib/x86/libX.so")
            .setManifest(androidManifest("com.test.app"))
            .build();

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new NativeTargetingValidator().validateModule(module));

    assertThat(e).hasMessageThat().contains("directory must be in format 'lib/<directory>'");
  }

  @Test
  public void validateModule_pointDirectlyToLib_throws() throws Exception {
    NativeLibraries config =
        nativeLibraries(targetedNativeDirectory("lib/", nativeDirectoryTargeting(X86_64)));
    BundleModule module =
        new BundleModuleBuilder("testModule")
            .setNativeConfig(config)
            .setManifest(androidManifest("com.test.app"))
            .build();

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new NativeTargetingValidator().validateModule(module));

    assertThat(e).hasMessageThat().contains("directory must be in format 'lib/<directory>'");
  }

  @Test
  public void validateModule_emptyTargetedDirectory_throws() throws Exception {
    NativeLibraries config =
        nativeLibraries(
            targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86)),
            targetedNativeDirectory("lib/x86_64", nativeDirectoryTargeting(X86_64)),
            targetedNativeDirectory("lib/mips", nativeDirectoryTargeting(MIPS)));
    BundleModule module =
        new BundleModuleBuilder("testModule")
            .setNativeConfig(config)
            .addFile("lib/x86/libX.so")
            .addFile("lib/x86_64/libX.so")
            .setManifest(androidManifest("com.test.app"))
            .build();

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new NativeTargetingValidator().validateModule(module));

    assertThat(e).hasMessageThat().contains("Targeted directory 'lib/mips' is empty.");
  }

  @Test
  public void validateModule_directoriesUnderLibNotTargeted_throws() throws Exception {
    NativeLibraries config =
        nativeLibraries(
            targetedNativeDirectory("lib/mips", nativeDirectoryTargeting(MIPS)));
    BundleModule module =
        new BundleModuleBuilder("testModule")
            .addFile("lib/x86/libX.so")
            .addFile("lib/x86_64/libX.so")
            .addFile("lib/mips/libX.so")
            .setNativeConfig(config)
            .setManifest(androidManifest("com.test.app"))
            .build();

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new NativeTargetingValidator().validateModule(module));

    assertThat(e).hasMessageThat()
        .contains("Following native directories are not targeted: [lib/x86, lib/x86_64]");
  }
}
