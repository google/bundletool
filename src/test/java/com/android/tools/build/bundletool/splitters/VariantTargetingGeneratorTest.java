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

import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_M_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_Q_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_S_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_S_V2_API_VERSION;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withTargetSdkVersion;
import static com.android.tools.build.bundletool.testing.TargetingUtils.lPlusVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeLibraries;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkRuntimeVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedNativeDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantMinSdkTargeting;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Config.UncompressDexFiles.UncompressedDexTargetSdk;
import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdk;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class VariantTargetingGeneratorTest {

  private static final XmlNode ANDROID_MANIFEST = androidManifest("com.test.app");
  private static final NativeLibraries NATIVE_CONFIG =
      nativeLibraries(targetedNativeDirectory("lib/x86", nativeDirectoryTargeting("x86")));
  private static final BundleModule SINGLE_LIBRARY_MODULE =
      new BundleModuleBuilder("testModule")
          .setManifest(ANDROID_MANIFEST)
          .setNativeConfig(NATIVE_CONFIG)
          .addFile("lib/x86/lib.so")
          .build();
  private static final BundleModule SINGLE_DEX_MODULE =
      new BundleModuleBuilder("testModule2")
          .setManifest(ANDROID_MANIFEST)
          .addFile("dex/classes.dex")
          .build();
  private static final AppBundle APP_BUNDLE =
      new AppBundleBuilder().addModule(SINGLE_LIBRARY_MODULE).addModule(SINGLE_DEX_MODULE).build();

  @Test
  public void generateVariantTargetings_combinesVariantTargetingsFromMultipleModules() {
    VariantTargetingGenerator variantTargetingGenerator =
        new VariantTargetingGenerator(
            new PerModuleVariantTargetingGenerator(), new SdkRuntimeVariantGenerator(APP_BUNDLE));
    ApkGenerationConfiguration apkGenerationConfiguration =
        ApkGenerationConfiguration.builder()
            .setEnableDexCompressionSplitter(true)
            .setEnableUncompressedNativeLibraries(true)
            .build();

    ImmutableSet<VariantTargeting> splits =
        variantTargetingGenerator.generateVariantTargetings(
            ImmutableList.of(SINGLE_LIBRARY_MODULE, SINGLE_DEX_MODULE), apkGenerationConfiguration);

    assertThat(splits)
        .comparingExpectedFieldsOnly()
        .containsExactly(
            // L+ variant is always generated.
            lPlusVariantTargeting(),
            // M+ variant generated for the module with the native library.
            variantMinSdkTargeting(ANDROID_M_API_VERSION),
            // Q+ variant generated for the module with the dex file.
            variantMinSdkTargeting(ANDROID_Q_API_VERSION));
  }

  @Test
  public void generateVariantTargetings_generatesSVariantForUncompressedDex() {
    VariantTargetingGenerator variantTargetingGenerator =
        new VariantTargetingGenerator(
            new PerModuleVariantTargetingGenerator(), new SdkRuntimeVariantGenerator(APP_BUNDLE));
    ApkGenerationConfiguration apkGenerationConfiguration =
        ApkGenerationConfiguration.builder()
            .setEnableDexCompressionSplitter(true)
            .setDexCompressionSplitterForTargetSdk(UncompressedDexTargetSdk.SDK_31)
            .build();

    ImmutableSet<VariantTargeting> splits =
        variantTargetingGenerator.generateVariantTargetings(
            ImmutableList.of(SINGLE_LIBRARY_MODULE, SINGLE_DEX_MODULE), apkGenerationConfiguration);

    assertThat(splits)
        .comparingExpectedFieldsOnly()
        .containsExactly(
            // L+ variant is always generated.
            lPlusVariantTargeting(),
            // S+ variant generated for the module with the dex file.
            variantMinSdkTargeting(ANDROID_S_API_VERSION));
  }

  @Test
  public void
      generateVariantTargetings_generatesSdkRuntimeVariant_generatesSVariantForUncompressedDex() {
    RuntimeEnabledSdk runtimeEnabledSdk =
        RuntimeEnabledSdk.newBuilder()
            .setPackageName("com.test.sdk")
            .setVersionMajor(1234)
            .setVersionMinor(123)
            .setCertificateDigest("AA:BB:CC")
            .build();
    VariantTargetingGenerator variantTargetingGenerator =
        new VariantTargetingGenerator(
            new PerModuleVariantTargetingGenerator(),
            new SdkRuntimeVariantGenerator(
                APP_BUNDLE.toBuilder()
                    .setRuntimeEnabledSdkDependencies(
                        ImmutableMap.of("com.test.sdk", runtimeEnabledSdk))
                    .build()));
    ApkGenerationConfiguration apkGenerationConfiguration =
        ApkGenerationConfiguration.builder()
            .setDexCompressionSplitterForTargetSdk(UncompressedDexTargetSdk.SDK_31)
            .setEnableDexCompressionSplitter(true)
            .build();

    ImmutableSet<VariantTargeting> splits =
        variantTargetingGenerator.generateVariantTargetings(
            ImmutableList.of(SINGLE_DEX_MODULE), apkGenerationConfiguration);

    assertThat(splits)
        .comparingExpectedFieldsOnly()
        .containsExactly(
            // L+ variant is always generated.
            lPlusVariantTargeting(),
            // S+ variant generated for the module with the dex file.
            variantMinSdkTargeting(ANDROID_S_API_VERSION),
            // SDK Runtime variant generated for base module, which contains RuntimeEnabledSdkConfig
            sdkRuntimeVariantTargeting());
  }

  @Test
  public void generateVariantTargetings_generatesSdkRuntimeVariant() {
    RuntimeEnabledSdk runtimeEnabledSdk =
        RuntimeEnabledSdk.newBuilder()
            .setPackageName("com.test.sdk")
            .setVersionMajor(1234)
            .setVersionMinor(123)
            .setCertificateDigest("AA:BB:CC")
            .build();
    VariantTargetingGenerator variantTargetingGenerator =
        new VariantTargetingGenerator(
            new PerModuleVariantTargetingGenerator(),
            new SdkRuntimeVariantGenerator(
                APP_BUNDLE.toBuilder()
                    .setRuntimeEnabledSdkDependencies(
                        ImmutableMap.of("com.test.sdk", runtimeEnabledSdk))
                    .build()));
    ApkGenerationConfiguration apkGenerationConfiguration =
        ApkGenerationConfiguration.builder()
            .setEnableDexCompressionSplitter(true)
            .setEnableUncompressedNativeLibraries(true)
            .build();

    ImmutableSet<VariantTargeting> splits =
        variantTargetingGenerator.generateVariantTargetings(
            ImmutableList.of(SINGLE_LIBRARY_MODULE), apkGenerationConfiguration);

    assertThat(splits)
        .comparingExpectedFieldsOnly()
        .containsExactly(
            // L+ variant is always generated.
            lPlusVariantTargeting(),
            // M+ variant generated for the module with the native library.
            variantMinSdkTargeting(ANDROID_M_API_VERSION),
            // SDK Runtime variant generated for base module, which contains RuntimeEnabledSdkConfig
            sdkRuntimeVariantTargeting());
  }

  @Test
  public void
      generateVariantTargetings_combinesVariantTargetingsFromMultipleModules_withSparseEncoding() {
    VariantTargetingGenerator variantTargetingGenerator =
        new VariantTargetingGenerator(
            new PerModuleVariantTargetingGenerator(), new SdkRuntimeVariantGenerator(APP_BUNDLE));
    ApkGenerationConfiguration apkGenerationConfiguration =
        ApkGenerationConfiguration.builder().setEnableSparseEncodingVariant(true).build();

    ImmutableSet<VariantTargeting> splits =
        variantTargetingGenerator.generateVariantTargetings(
            ImmutableList.of(getModuleWithTargetingSdk26()), apkGenerationConfiguration);

    assertThat(splits)
        .comparingExpectedFieldsOnly()
        .containsExactly(
            // L+ variant is always generated.
            lPlusVariantTargeting(),
            // S_V2+ variant generated for the module with sparse encoding
            variantMinSdkTargeting(ANDROID_S_V2_API_VERSION));
  }

  @Test
  public void generateVariantTargetings_generatesSdkRuntimeVariant_withSparseEncoding() {
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .setManifest(androidManifest("com.test.app", withTargetSdkVersion("O.fingerprint")))
            .build();
    RuntimeEnabledSdk runtimeEnabledSdk =
        RuntimeEnabledSdk.newBuilder()
            .setPackageName("com.test.sdk")
            .setVersionMajor(1234)
            .setVersionMinor(123)
            .setCertificateDigest("AA:BB:CC")
            .build();
    VariantTargetingGenerator variantTargetingGenerator =
        new VariantTargetingGenerator(
            new PerModuleVariantTargetingGenerator(),
            new SdkRuntimeVariantGenerator(
                APP_BUNDLE.toBuilder()
                    .setRawModules(ImmutableSet.of(baseModule, getModuleWithTargetingSdk26()))
                    .setRuntimeEnabledSdkDependencies(
                        ImmutableMap.of("com.test.sdk", runtimeEnabledSdk))
                    .build()));
    ApkGenerationConfiguration apkGenerationConfiguration =
        ApkGenerationConfiguration.builder().setEnableSparseEncodingVariant(true).build();

    ImmutableSet<VariantTargeting> splits =
        variantTargetingGenerator.generateVariantTargetings(
            ImmutableList.of(baseModule, getModuleWithTargetingSdk26()),
            apkGenerationConfiguration);

    assertThat(splits)
        .comparingExpectedFieldsOnly()
        .containsExactly(
            // L+ variant is always generated.
            lPlusVariantTargeting(),
            // S_V2+ variant generated for the module with sparse encoding
            variantMinSdkTargeting(ANDROID_S_V2_API_VERSION),
            // SDK Runtime variant generated for base module, which contains RuntimeEnabledSdkConfig
            sdkRuntimeVariantTargeting());
  }

  private BundleModule getModuleWithTargetingSdk26() {
    return new BundleModuleBuilder("testModule")
        .setManifest(androidManifest("com.test.app", withTargetSdkVersion("O.fingerprint")))
        .build();
  }
}
