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

package com.android.tools.build.bundletool.validation;

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.Optimizations;
import com.android.bundle.Config.SplitDimension;
import com.android.bundle.Config.SplitDimension.Value;
import com.android.bundle.Config.SplitsConfig;
import com.android.bundle.Config.SuffixStripping;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CountrySetParityValidatorTest {

  @Test
  public void validateAllModules_noCountrySets_ok() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img/image.jpg")
            .addFile("assets/img/image2.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b").setManifest(androidManifest("com.test.app")).build();

    new CountrySetParityValidator().validateAllModules(ImmutableList.of(moduleA, moduleB));
  }

  @Test
  public void validateAllModules_sameCountrySets_ok() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img#countries_latam/image.jpg")
            .addFile("assets/img#countries_sea/image.jpg")
            .addFile("assets/img/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b")
            .addFile("assets/img#countries_latam/image.jpg")
            .addFile("assets/img#countries_sea/image.jpg")
            .addFile("assets/img/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();

    new CountrySetParityValidator().validateAllModules(ImmutableList.of(moduleA, moduleB));
  }

  @Test
  public void validateAllModules_multipleFilesPerCountrySetDirectory_ok() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img#countries_latam/imageA.jpg")
            .addFile("assets/img#countries_latam/imageB.jpg")
            .addFile("assets/img#countries_sea/image1.jpg")
            .addFile("assets/img#countries_sea/image2.jpg")
            .addFile("assets/img/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();

    new CountrySetParityValidator().validateAllModules(ImmutableList.of(moduleA));
  }

  @Test
  public void validateAllModules_sameCountrySetsAndNoCountrySet_ok() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img#countries_latam/image.jpg")
            .addFile("assets/img#countries_sea/image.jpg")
            .addFile("assets/img/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b")
            .addFile("assets/img#countries_latam/image.jpg")
            .addFile("assets/img#countries_sea/image.jpg")
            .addFile("assets/img/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleC =
        new BundleModuleBuilder("c").setManifest(androidManifest("com.test.app")).build();

    new CountrySetParityValidator().validateAllModules(ImmutableList.of(moduleA, moduleB, moduleC));
  }

  @Test
  public void validateAllModules_differentCountrySetsInDifferentModules_throws() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img#countries_latam/image.jpg")
            .addFile("assets/img/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b")
            .addFile("assets/img#countries_latam/image.jpg")
            .addFile("assets/img#countries_sea/image.jpg")
            .addFile("assets/img/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new CountrySetParityValidator()
                    .validateAllModules(ImmutableList.of(moduleA, moduleB)));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "All modules with country set targeting must support the same country sets, but module"
                + " 'a' supports [latam] (with fallback directories) and module 'b' supports"
                + " [latam, sea] (with fallback directories).");
  }

  @Test
  public void validateAllModules_noFallback_throws() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img#countries_latam/image.jpg")
            .addFile("assets/img#countries_sea/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new CountrySetParityValidator().validateAllModules(ImmutableList.of(moduleA)));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Module 'a' targets content based on country set but doesn't have fallback folders"
                + " (folders without #countries suffixes). Fallback folders will be used to"
                + " generate split for rest of the countries which are not targeted by existing"
                + " country sets. Please add missing folders and try again.");
  }

  @Test
  public void validateBundle_moduleWithFallbackCountrySet_succeeds() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img1#countries_latam/image.jpg")
            .addFile("assets/img1#countries_sea/image.jpg")
            .addFile("assets/img1/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();
    AppBundle appBundle =
        AppBundle.buildFromModules(
            ImmutableList.of(moduleA),
            BundleConfig.newBuilder()
                .setOptimizations(
                    Optimizations.newBuilder()
                        .setSplitsConfig(
                            SplitsConfig.newBuilder()
                                .addSplitDimension(
                                    SplitDimension.newBuilder()
                                        .setValue(Value.COUNTRY_SET)
                                        .setSuffixStripping(
                                            SuffixStripping.newBuilder().setDefaultSuffix("")))))
                .build(),
            BundleMetadata.builder().build());

    new CountrySetParityValidator().validateBundle(appBundle);
  }

  @Test
  public void validateBundle_moduleWithoutFallbackCountrySet_throws() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img1#countries_latam/image.jpg")
            .addFile("assets/img1#countries_sea/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();
    AppBundle appBundle =
        AppBundle.buildFromModules(
            ImmutableList.of(moduleA),
            BundleConfig.newBuilder()
                .setOptimizations(
                    Optimizations.newBuilder()
                        .setSplitsConfig(
                            SplitsConfig.newBuilder()
                                .addSplitDimension(
                                    SplitDimension.newBuilder()
                                        .setValue(Value.COUNTRY_SET)
                                        .setSuffixStripping(
                                            SuffixStripping.newBuilder().setDefaultSuffix("")))))
                .build(),
            BundleMetadata.builder().build());

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new CountrySetParityValidator().validateBundle(appBundle));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Module 'a' targets content based on country set but doesn't have fallback folders"
                + " (folders without #countries suffixes). Fallback folders will be used to"
                + " generate split for rest of the countries which are not targeted by existing"
                + " country sets. Please add missing folders and try again.");
  }

  @Test
  public void validateBundle_moduleWithDefaultCountrySetFolders_succeeds() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img1#countries_latam/image.jpg")
            .addFile("assets/img1#countries_sea/image.jpg")
            .addFile("assets/img1/image.jpg")
            .addFile("assets/img2#countries_latam/image.jpg")
            .addFile("assets/img2#countries_sea/image.jpg")
            .addFile("assets/img2/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();
    AppBundle appBundle =
        AppBundle.buildFromModules(
            ImmutableList.of(moduleA),
            BundleConfig.newBuilder()
                .setOptimizations(
                    Optimizations.newBuilder()
                        .setSplitsConfig(
                            SplitsConfig.newBuilder()
                                .addSplitDimension(
                                    SplitDimension.newBuilder()
                                        .setValue(Value.COUNTRY_SET)
                                        .setSuffixStripping(
                                            SuffixStripping.newBuilder().setDefaultSuffix("sea")))))
                .build(),
            BundleMetadata.builder().build());

    new CountrySetParityValidator().validateBundle(appBundle);
  }

  @Test
  public void validateBundle_moduleWithoutDefaultCountrySetFolders_throws() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img1#countries_latam/image.jpg")
            .addFile("assets/img1#countries_sea/image.jpg")
            .addFile("assets/img2#countries_latam/image.jpg")
            .addFile("assets/img2#countries_sea/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();
    AppBundle appBundle =
        AppBundle.buildFromModules(
            ImmutableList.of(moduleA),
            BundleConfig.newBuilder()
                .setOptimizations(
                    Optimizations.newBuilder()
                        .setSplitsConfig(
                            SplitsConfig.newBuilder()
                                .addSplitDimension(
                                    SplitDimension.newBuilder()
                                        .setValue(Value.COUNTRY_SET)
                                        .setSuffixStripping(
                                            SuffixStripping.newBuilder()
                                                .setDefaultSuffix("europe")))))
                .build(),
            BundleMetadata.builder().build());

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new CountrySetParityValidator().validateBundle(appBundle));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "When a standalone or universal APK is built, the country set folders corresponding to"
                + " country set 'europe' will be used, but module 'a' has no such folders. Either"
                + " add missing folders or change the configuration for the COUNTRY_SET"
                + " optimization to specify a default suffix corresponding to country set to use"
                + " in the standalone and universal APKs.");
  }

  @Test
  public void validateBundle_modulesWithAndWithoutCountrySet_succeeds() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img1#countries_latam/image.jpg")
            .addFile("assets/img1#countries_sea/image.jpg")
            .addFile("assets/img1/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b")
            .addFile("assets/img2/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();
    AppBundle appBundle =
        AppBundle.buildFromModules(
            ImmutableList.of(moduleA, moduleB),
            BundleConfig.newBuilder()
                .setOptimizations(
                    Optimizations.newBuilder()
                        .setSplitsConfig(
                            SplitsConfig.newBuilder()
                                .addSplitDimension(
                                    SplitDimension.newBuilder()
                                        .setValue(Value.COUNTRY_SET)
                                        .setSuffixStripping(
                                            SuffixStripping.newBuilder()
                                                .setDefaultSuffix("latam")))))
                .build(),
            BundleMetadata.builder().build());

    new CountrySetParityValidator().validateBundle(appBundle);
  }
}
