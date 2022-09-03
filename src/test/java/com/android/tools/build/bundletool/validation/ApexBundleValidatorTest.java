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

package com.android.tools.build.bundletool.validation;

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.apex.ApexManifestProto.ApexManifest;
import com.android.bundle.Config.ApexConfig;
import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.SupportedAbiSet;
import com.android.bundle.Files.ApexImages;
import com.android.bundle.Files.TargetedApexImage;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ApexBundleValidatorTest {
  private static final String PKG_NAME = "com.test.app";
  private static final String APEX_MANIFEST_PATH = "root/apex_manifest.pb";
  private static final byte[] APEX_MANIFEST =
      ApexManifest.newBuilder().setName("com.test.app").build().toByteArray();
  private static final ApexImages APEX_CONFIG =
      ApexImages.newBuilder()
          .addImage(TargetedApexImage.newBuilder().setPath("apex/x86_64.img"))
          .addImage(TargetedApexImage.newBuilder().setPath("apex/x86.img"))
          .addImage(TargetedApexImage.newBuilder().setPath("apex/armeabi-v7a.img"))
          .addImage(TargetedApexImage.newBuilder().setPath("apex/arm64-v8a.img"))
          .build();

  @Test
  public void validateModule_validApexModule_succeeds() throws Exception {
    BundleModule apexModule = validApexModule();

    new ApexBundleValidator().validateModule(apexModule);
  }

  @Test
  public void validateModule_unexpectedFile_throws() throws Exception {
    BundleModule apexModule =
        new BundleModuleBuilder("apexTestModule")
            .setManifest(androidManifest(PKG_NAME))
            .setApexConfig(APEX_CONFIG)
            .addFile(APEX_MANIFEST_PATH, APEX_MANIFEST)
            .addFile("apex/x86_64.img")
            .addFile("apex/x86.img")
            .addFile("apex/armeabi-v7a.img")
            .addFile("apex/arm64-v8a.img")
            .addFile("root/unexpected.txt")
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ApexBundleValidator().validateModule(apexModule));

    assertThat(exception).hasMessageThat().contains("Unexpected file in APEX bundle");
  }

  @Test
  public void validateModule_missingApexManifest_throws() throws Exception {
    BundleModule apexModule =
        new BundleModuleBuilder("apexTestModule")
            .setManifest(androidManifest(PKG_NAME))
            .setApexConfig(APEX_CONFIG)
            .addFile("apex/x86_64.img")
            .addFile("apex/x86.img")
            .addFile("apex/armeabi-v7a.img")
            .addFile("apex/arm64-v8a.img")
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ApexBundleValidator().validateModule(apexModule));

    assertThat(exception).hasMessageThat().contains("Missing expected file in APEX bundle");
  }

  @Test
  public void validateModule_missingPackageFromApexManifest_throws() throws Exception {
    BundleModule apexModule =
        new BundleModuleBuilder("apexTestModule")
            .setManifest(androidManifest(PKG_NAME))
            .setApexConfig(APEX_CONFIG)
            .addFile(APEX_MANIFEST_PATH, ApexManifest.getDefaultInstance().toByteArray())
            .addFile("apex/x86_64.img")
            .addFile("apex/x86.img")
            .addFile("apex/armeabi-v7a.img")
            .addFile("apex/arm64-v8a.img")
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ApexBundleValidator().validateModule(apexModule));

    assertThat(exception).hasMessageThat().contains("APEX manifest must have a package name");
  }

  @Test
  public void validateModule_untargetedImageFile_throws() throws Exception {
    BundleModule apexModule =
        new BundleModuleBuilder("apexTestModule")
            .setManifest(androidManifest(PKG_NAME))
            .setApexConfig(APEX_CONFIG)
            .addFile(APEX_MANIFEST_PATH, APEX_MANIFEST)
            .addFile("apex/x86_64.img")
            .addFile("apex/x86.img")
            .addFile("apex/armeabi-v7a.img")
            .addFile("apex/arm64-v8a.img")
            .addFile("apex/x86_64.x86.img")
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ApexBundleValidator().validateModule(apexModule));

    assertThat(exception).hasMessageThat().contains("Found APEX image files that are not targeted");
  }

  private static BundleConfig bundleConfigWithSupportedAbis(
      ImmutableSet<ImmutableSet<String>> setOfAbis) {
    ImmutableSet<SupportedAbiSet> supportedAbiSets =
        setOfAbis.stream()
            .map(abis -> SupportedAbiSet.newBuilder().addAllAbi(abis).build())
            .collect(toImmutableSet());
    return BundleConfig.newBuilder()
        .setApexConfig(ApexConfig.newBuilder().addAllSupportedAbiSet(supportedAbiSets).build())
        .build();
  }

  @Test
  public void validateModule_singleAbiWithSupportedAbis_succeeds() throws Exception {
    ApexImages apexConfig =
        ApexImages.newBuilder()
            .addImage(TargetedApexImage.newBuilder().setPath("apex/arm64-v8a.img"))
            .build();
    BundleConfig bundleConfig =
        bundleConfigWithSupportedAbis(ImmutableSet.of(ImmutableSet.of("arm64-v8a")));
    BundleModule apexModule =
        new BundleModuleBuilder("apexTestModule", bundleConfig)
            .setManifest(androidManifest(PKG_NAME))
            .setApexConfig(apexConfig)
            .addFile(APEX_MANIFEST_PATH, APEX_MANIFEST)
            .addFile("apex/arm64-v8a.img")
            .build();

    new ApexBundleValidator().validateModule(apexModule);
  }

  @Test
  public void validateModule_imageFilesMismatchWithSupportedAbis_throws() throws Exception {
    ApexImages apexConfig =
        ApexImages.newBuilder()
            .addImage(TargetedApexImage.newBuilder().setPath("apex/x86_64.img"))
            .build();
    BundleConfig bundleConfig =
        bundleConfigWithSupportedAbis(ImmutableSet.of(ImmutableSet.of("arm64-v8a")));
    BundleModule apexModule =
        new BundleModuleBuilder("apexTestModule", bundleConfig)
            .setManifest(androidManifest(PKG_NAME))
            .setApexConfig(apexConfig)
            .addFile(APEX_MANIFEST_PATH, APEX_MANIFEST)
            .addFile("apex/x86_64.img")
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ApexBundleValidator().validateModule(apexModule));

    assertThat(exception).hasMessageThat().contains("it should match with APEX image files");
  }

  @Test
  public void validateModule_wrongAbiNamesInApexSupportedAbis() throws Exception {
    ApexImages apexConfig =
        ApexImages.newBuilder()
            .addImage(TargetedApexImage.newBuilder().setPath("apex/arm64-v8a.img"))
            .build();
    BundleConfig bundleConfig =
        bundleConfigWithSupportedAbis(ImmutableSet.of(ImmutableSet.of("arm64-v8a", "invalid_abi")));
    BundleModule apexModule =
        new BundleModuleBuilder("apexTestModule", bundleConfig)
            .setManifest(androidManifest(PKG_NAME))
            .setApexConfig(apexConfig)
            .addFile(APEX_MANIFEST_PATH, APEX_MANIFEST)
            .addFile("apex/arm64-v8a.img")
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ApexBundleValidator().validateModule(apexModule));

    assertThat(exception).hasMessageThat().contains("Unrecognized ABI 'invalid_abi'");
  }

  @Test
  public void validateModule_missingTargetedImageFile_throws() throws Exception {
    ApexImages apexConfig =
        APEX_CONFIG
            .toBuilder()
            .addImage(TargetedApexImage.newBuilder().setPath("apex/x86_64.x86.img"))
            .build();
    BundleModule apexModule =
        new BundleModuleBuilder("apexTestModule")
            .setManifest(androidManifest(PKG_NAME))
            .setApexConfig(apexConfig)
            .addFile(APEX_MANIFEST_PATH, APEX_MANIFEST)
            .addFile("apex/x86_64.img")
            .addFile("apex/x86.img")
            .addFile("apex/armeabi-v7a.img")
            .addFile("apex/arm64-v8a.img")
            // x86_64.x86 missing.
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ApexBundleValidator().validateModule(apexModule));

    assertThat(exception).hasMessageThat().contains("Targeted APEX image files are missing");
  }

  @Test
  public void validateModule_imageFilesTargetSameSetOfAbis_throws() throws Exception {
    ApexImages apexConfig =
        APEX_CONFIG.toBuilder()
            .addImage(TargetedApexImage.newBuilder().setPath("apex/x86.armeabi-v7a.x86_64.img"))
            .addImage(TargetedApexImage.newBuilder().setPath("apex/x86_64.x86.armeabi-v7a.img"))
            .build();
    BundleModule apexModule =
        new BundleModuleBuilder("apexTestModule")
            .setManifest(androidManifest(PKG_NAME))
            .setApexConfig(apexConfig)
            .addFile(APEX_MANIFEST_PATH, APEX_MANIFEST)
            .addFile("apex/x86_64.img")
            .addFile("apex/x86.img")
            .addFile("apex/armeabi-v7a.img")
            .addFile("apex/arm64-v8a.img")
            .addFile("apex/x86.armeabi-v7a.x86_64.img")
            .addFile("apex/x86_64.x86.armeabi-v7a.img")
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ApexBundleValidator().validateModule(apexModule));

    assertThat(exception)
        .hasMessageThat()
        .contains("Every APEX image file must target a unique set of architectures");
  }

  @Test
  public void validateModule_singleton32BitAbiMissing_throws() throws Exception {
    ApexImages apexConfig =
        ApexImages.newBuilder()
            .addImage(TargetedApexImage.newBuilder().setPath("apex/x86_64.img"))
            .addImage(TargetedApexImage.newBuilder().setPath("apex/x86.img"))
            .addImage(TargetedApexImage.newBuilder().setPath("apex/arm64-v8a.img"))
            .addImage(TargetedApexImage.newBuilder().setPath("apex/x86_64.x86.img"))
            .addImage(TargetedApexImage.newBuilder().setPath("apex/x86_64.armeabi-v7a.img"))
            .build();
    BundleModule apexModule =
        new BundleModuleBuilder("apexTestModule")
            .setManifest(androidManifest(PKG_NAME))
            .setApexConfig(apexConfig)
            .addFile(APEX_MANIFEST_PATH, APEX_MANIFEST)
            .addFile("apex/x86_64.img")
            .addFile("apex/x86.img")
            .addFile("apex/arm64-v8a.img")
            // armeabi-v7a is missing.
            .addFile("apex/x86_64.x86.img")
            .addFile("apex/x86_64.armeabi-v7a.img")
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ApexBundleValidator().validateModule(apexModule));

    assertThat(exception).hasMessageThat().contains("APEX bundle must contain one of");
  }

  @Test
  public void validateModule_singleton64BitAbiMissing_doesNotThrow() throws Exception {
    ApexImages apexConfig =
        ApexImages.newBuilder()
            .addImage(TargetedApexImage.newBuilder().setPath("apex/x86_64.img"))
            .addImage(TargetedApexImage.newBuilder().setPath("apex/x86.img"))
            .addImage(TargetedApexImage.newBuilder().setPath("apex/armeabi-v7a.img"))
            .addImage(TargetedApexImage.newBuilder().setPath("apex/x86_64.x86.img"))
            .addImage(TargetedApexImage.newBuilder().setPath("apex/arm64-v8a.armeabi-v7a.img"))
            .build();
    BundleModule apexModule =
        new BundleModuleBuilder("apexTestModule")
            .setManifest(androidManifest(PKG_NAME))
            .setApexConfig(apexConfig)
            .addFile(APEX_MANIFEST_PATH, APEX_MANIFEST)
            .addFile("apex/x86_64.img")
            .addFile("apex/x86.img")
            .addFile("apex/armeabi-v7a.img")
            // arm64-v8a is missing (but arm64-v8a.armeabi-v7a is present).
            .addFile("apex/x86_64.x86.img")
            .addFile("apex/arm64-v8a.armeabi-v7a.img")
            .build();

    new ApexBundleValidator().validateModule(apexModule);
  }

  @Test
  public void validateModule_singleton64BitAbiAnd64With32Missing_throws() throws Exception {
    ApexImages apexConfig =
        ApexImages.newBuilder()
            .addImage(TargetedApexImage.newBuilder().setPath("apex/x86_64.img"))
            .addImage(TargetedApexImage.newBuilder().setPath("apex/x86.img"))
            .addImage(TargetedApexImage.newBuilder().setPath("apex/armeabi-v7a.img"))
            .addImage(TargetedApexImage.newBuilder().setPath("apex/x86_64.x86.img"))
            .addImage(TargetedApexImage.newBuilder().setPath("apex/x86_64.armeabi-v7a.img"))
            .build();
    BundleModule apexModule =
        new BundleModuleBuilder("apexTestModule")
            .setManifest(androidManifest(PKG_NAME))
            .setApexConfig(apexConfig)
            .addFile(APEX_MANIFEST_PATH, APEX_MANIFEST)
            .addFile("apex/x86_64.img")
            .addFile("apex/x86.img")
            .addFile("apex/armeabi-v7a.img")
            // Both arm64-v8a and arm64-v8a.armeabi-v7a are missing.
            .addFile("apex/x86_64.x86.img")
            .addFile("apex/x86_64.armeabi-v7a.img")
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ApexBundleValidator().validateModule(apexModule));

    assertThat(exception).hasMessageThat().contains("APEX bundle must contain one of");
  }

  @Test
  public void validateAllModules_singleApexModule_succeeds() throws Exception {
    BundleModule apexModule = validApexModule();

    new ApexBundleValidator().validateAllModules(ImmutableList.of(apexModule));
  }

  @Test
  public void validateAllModules_multipleApexModules_throws() throws Exception {
    BundleModule apexModule = validApexModule();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new ApexBundleValidator()
                    .validateAllModules(ImmutableList.of(apexModule, apexModule)));

    assertThat(exception)
        .hasMessageThat()
        .contains("Multiple APEX modules are not allowed, found 2.");
  }

  @Test
  public void validateAllModules_ApexModuleWithAnother_throws() throws Exception {
    BundleModule apexModule = validApexModule();
    BundleModule anotherModule =
        new BundleModuleBuilder("anotherModule").setManifest(androidManifest(PKG_NAME)).build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new ApexBundleValidator()
                    .validateAllModules(ImmutableList.of(apexModule, anotherModule)));

    assertThat(exception)
        .hasMessageThat()
        .contains("APEX bundles must only contain one module, found 2.");
  }

  private BundleModule validApexModule() throws IOException {
    return new BundleModuleBuilder("apexTestModule")
        .setManifest(androidManifest(PKG_NAME))
        .setApexConfig(APEX_CONFIG)
        .addFile(APEX_MANIFEST_PATH, APEX_MANIFEST)
        .addFile("apex/x86_64.img")
        .addFile("apex/x86.img")
        .addFile("apex/armeabi-v7a.img")
        .addFile("apex/arm64-v8a.img")
        .build();
  }
}
