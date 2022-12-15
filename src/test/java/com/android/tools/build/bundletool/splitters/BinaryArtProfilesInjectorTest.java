/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.tools.build.bundletool.splitters.BinaryArtProfilesInjector.BINARY_ART_PROFILE_METADATA_NAME;
import static com.android.tools.build.bundletool.splitters.BinaryArtProfilesInjector.BINARY_ART_PROFILE_NAME;
import static com.android.tools.build.bundletool.splitters.BinaryArtProfilesInjector.LEGACY_METADATA_NAMESPACE;
import static com.android.tools.build.bundletool.splitters.BinaryArtProfilesInjector.METADATA_NAMESPACE;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.google.common.collect.MoreCollectors.toOptional;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.io.ByteSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BinaryArtProfilesInjectorTest {
  private static final byte[] BINARY_ART_PROFILE_CONTENT = {1, 2, 3, 4};
  private static final byte[] BINARY_ART_PROFILE_METADATA_CONTENT = {4, 3, 2, 1};

  @Test
  public void mainSplitOfTheBaseModule_artProfileInjected() {
    AppBundle appBundle =
        createAppBundleBuilder()
            .addMetadataFile(
                METADATA_NAMESPACE,
                BINARY_ART_PROFILE_NAME,
                ByteSource.wrap(BINARY_ART_PROFILE_CONTENT))
            .addMetadataFile(
                METADATA_NAMESPACE,
                BINARY_ART_PROFILE_METADATA_NAME,
                ByteSource.wrap(BINARY_ART_PROFILE_METADATA_CONTENT))
            .build();

    BinaryArtProfilesInjector injector = new BinaryArtProfilesInjector(appBundle);

    ModuleSplit moduleSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.BASE_MODULE_NAME)
            .setMasterSplit(true)
            .addEntry(
                ModuleEntry.builder()
                    .setPath(ZipPath.create("some.bin"))
                    .setContent(ByteSource.wrap(new byte[] {10, 9, 8}))
                    .build())
            .build();
    ModuleSplit result = injector.inject(moduleSplit);

    ZipPath apkProfilePath = ZipPath.create("assets/dexopt/baseline.prof");
    ZipPath apkProfileMetadataPath = ZipPath.create("assets/dexopt/baseline.profm");
    assertThat(result.findEntry(apkProfilePath).map(ModuleEntry::getForceUncompressed))
        .hasValue(true);
    assertThat(result.findEntry(apkProfileMetadataPath).map(ModuleEntry::getForceUncompressed))
        .hasValue(true);

    assertThat(getEntryContent(result, apkProfilePath)).hasValue(BINARY_ART_PROFILE_CONTENT);
    assertThat(getEntryContent(result, apkProfileMetadataPath))
        .hasValue(BINARY_ART_PROFILE_METADATA_CONTENT);
    assertThat(getEntryContent(result, ZipPath.create("some.bin"))).hasValue(new byte[] {10, 9, 8});
  }

  @Test
  public void standalone_artProfileInjected() {
    AppBundle appBundle =
        createAppBundleBuilder()
            .addMetadataFile(
                METADATA_NAMESPACE,
                BINARY_ART_PROFILE_NAME,
                ByteSource.wrap(BINARY_ART_PROFILE_CONTENT))
            .addMetadataFile(
                METADATA_NAMESPACE,
                BINARY_ART_PROFILE_METADATA_NAME,
                ByteSource.wrap(BINARY_ART_PROFILE_METADATA_CONTENT))
            .build();

    BinaryArtProfilesInjector injector = new BinaryArtProfilesInjector(appBundle);

    ModuleSplit moduleSplit =
        createModuleSplitBuilder()
            .setSplitType(SplitType.STANDALONE)
            .setModuleName(BundleModuleName.BASE_MODULE_NAME)
            .setMasterSplit(false)
            .addEntry(
                ModuleEntry.builder()
                    .setPath(ZipPath.create("some.bin"))
                    .setContent(ByteSource.wrap(new byte[] {10, 9, 8}))
                    .build())
            .build();
    ModuleSplit result = injector.inject(moduleSplit);

    assertThat(getEntryContent(result, ZipPath.create("assets/dexopt/baseline.prof")))
        .hasValue(BINARY_ART_PROFILE_CONTENT);
    assertThat(getEntryContent(result, ZipPath.create("assets/dexopt/baseline.profm")))
        .hasValue(BINARY_ART_PROFILE_METADATA_CONTENT);
    assertThat(getEntryContent(result, ZipPath.create("some.bin"))).hasValue(new byte[] {10, 9, 8});
  }

  @Test
  public void mainSplitOfTheBaseModule_legacyNamespace_artProfileInjected() {
    AppBundle appBundle =
        createAppBundleBuilder()
            .addMetadataFile(
                LEGACY_METADATA_NAMESPACE,
                BINARY_ART_PROFILE_NAME,
                ByteSource.wrap(BINARY_ART_PROFILE_CONTENT))
            .addMetadataFile(
                LEGACY_METADATA_NAMESPACE,
                BINARY_ART_PROFILE_METADATA_NAME,
                ByteSource.wrap(BINARY_ART_PROFILE_METADATA_CONTENT))
            .build();

    BinaryArtProfilesInjector injector = new BinaryArtProfilesInjector(appBundle);

    ModuleSplit moduleSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.BASE_MODULE_NAME)
            .setMasterSplit(true)
            .build();
    ModuleSplit result = injector.inject(moduleSplit);

    assertThat(getEntryContent(result, ZipPath.create("assets/dexopt/baseline.prof")))
        .hasValue(BINARY_ART_PROFILE_CONTENT);
    assertThat(getEntryContent(result, ZipPath.create("assets/dexopt/baseline.profm")))
        .hasValue(BINARY_ART_PROFILE_METADATA_CONTENT);
  }

  @Test
  public void mainSplitOfTheBaseModule_onlyProfile_injected() {
    AppBundle appBundle =
        createAppBundleBuilder()
            .addMetadataFile(
                METADATA_NAMESPACE,
                BINARY_ART_PROFILE_NAME,
                ByteSource.wrap(BINARY_ART_PROFILE_CONTENT))
            .build();

    BinaryArtProfilesInjector injector = new BinaryArtProfilesInjector(appBundle);

    ModuleSplit moduleSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.BASE_MODULE_NAME)
            .setMasterSplit(true)
            .build();
    ModuleSplit result = injector.inject(moduleSplit);

    assertThat(getEntryContent(result, ZipPath.create("assets/dexopt/baseline.prof")))
        .hasValue(BINARY_ART_PROFILE_CONTENT);
    assertThat(getEntryContent(result, ZipPath.create("assets/dexopt/baseline.profm"))).isEmpty();
  }

  @Test
  public void configSplitOfTheBaseModule_skipped() {
    AppBundle appBundle =
        createAppBundleBuilder()
            .addMetadataFile(
                METADATA_NAMESPACE,
                BINARY_ART_PROFILE_NAME,
                ByteSource.wrap(BINARY_ART_PROFILE_CONTENT))
            .build();

    BinaryArtProfilesInjector injector = new BinaryArtProfilesInjector(appBundle);

    ModuleSplit moduleSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.BASE_MODULE_NAME)
            .setMasterSplit(false)
            .build();
    ModuleSplit result = injector.inject(moduleSplit);

    assertThat(getEntryContent(result, ZipPath.create("assets/dexopt/baseline.prof"))).isEmpty();
    assertThat(getEntryContent(result, ZipPath.create("assets/dexopt/baseline.profm"))).isEmpty();
  }

  @Test
  public void mainSplitOfOtherModule_skipped() {
    AppBundle appBundle =
        createAppBundleBuilder()
            .addMetadataFile(
                METADATA_NAMESPACE,
                BINARY_ART_PROFILE_NAME,
                ByteSource.wrap(BINARY_ART_PROFILE_CONTENT))
            .build();

    BinaryArtProfilesInjector injector = new BinaryArtProfilesInjector(appBundle);

    ModuleSplit moduleSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create("a"))
            .setMasterSplit(true)
            .build();
    ModuleSplit result = injector.inject(moduleSplit);

    assertThat(getEntryContent(result, ZipPath.create("assets/dexopt/baseline.prof"))).isEmpty();
    assertThat(getEntryContent(result, ZipPath.create("assets/dexopt/baseline.profm"))).isEmpty();
  }

  @Test
  public void noBinaryArtProfiles_doNotModifySplit() {
    AppBundle appBundle = createAppBundleBuilder().build();

    BinaryArtProfilesInjector injector = new BinaryArtProfilesInjector(appBundle);

    ModuleSplit moduleSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.BASE_MODULE_NAME)
            .setMasterSplit(true)
            .build();

    assertThat(injector.inject(moduleSplit)).isSameInstanceAs(moduleSplit);
  }

  private Optional<byte[]> getEntryContent(ModuleSplit split, ZipPath zipPath) {
    return split.getEntries().stream()
        .filter(entry -> entry.getPath().equals(zipPath))
        .map(
            entry -> {
              try {
                return entry.getContent().read();
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            })
        .collect(toOptional());
  }

  private static AppBundleBuilder createAppBundleBuilder() {
    return new AppBundleBuilder()
        .addModule(
            new BundleModuleBuilder("base").setManifest(androidManifest("com.test.app")).build());
  }

  private static ModuleSplit.Builder createModuleSplitBuilder() {
    return ModuleSplit.builder()
        .setApkTargeting(ApkTargeting.getDefaultInstance())
        .setVariantTargeting(VariantTargeting.getDefaultInstance())
        .setAndroidManifest(AndroidManifest.create(androidManifest("com.test.app")))
        .setSplitType(SplitType.SPLIT);
  }
}
