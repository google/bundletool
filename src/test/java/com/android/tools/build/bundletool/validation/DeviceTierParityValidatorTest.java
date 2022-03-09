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

package com.android.tools.build.bundletool.validation;

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DeviceTierParityValidatorTest {

  @Test
  public void noTiers_ok() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img/image.jpg")
            .addFile("assets/img/image2.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b").setManifest(androidManifest("com.test.app")).build();

    new DeviceTierParityValidator().validateAllModules(ImmutableList.of(moduleA, moduleB));
  }

  @Test
  public void sameTiers_ok() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img#tier_0/image.jpg")
            .addFile("assets/img#tier_1/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b")
            .addFile("assets/img#tier_0/image.jpg")
            .addFile("assets/img#tier_1/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();

    new DeviceTierParityValidator().validateAllModules(ImmutableList.of(moduleA, moduleB));
  }

  @Test
  public void multipleFilesPerTieredDirectory_ok() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img#tier_0/imageA.jpg")
            .addFile("assets/img#tier_0/imageB.jpg")
            .addFile("assets/img#tier_1/image1.jpg")
            .addFile("assets/img#tier_1/image2.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();

    new DeviceTierParityValidator().validateAllModules(ImmutableList.of(moduleA));
  }

  @Test
  public void sameTiersAndNoTier_ok() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img#tier_0/image.jpg")
            .addFile("assets/img#tier_1/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b")
            .addFile("assets/img#tier_0/image.jpg")
            .addFile("assets/img#tier_1/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleC =
        new BundleModuleBuilder("c").setManifest(androidManifest("com.test.app")).build();

    new DeviceTierParityValidator().validateAllModules(ImmutableList.of(moduleA, moduleB, moduleC));
  }

  @Test
  public void differentTiersInDifferentModules_throws() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img#tier_0/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b")
            .addFile("assets/img#tier_0/image.jpg")
            .addFile("assets/img#tier_1/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new DeviceTierParityValidator()
                    .validateAllModules(ImmutableList.of(moduleA, moduleB)));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "All modules with device tier targeting must support the same set of tiers, but module"
                + " 'a' supports [0] and module 'b' supports [0, 1].");
  }

  @Test
  public void differentTiersInDifferentFolders_throws() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img1#tier_0/image.jpg")
            .addFile("assets/img1#tier_1/image.jpg")
            .addFile("assets/img2#tier_0/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new DeviceTierParityValidator().validateAllModules(ImmutableList.of(moduleA)));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "All device-tier-targeted folders in a module must support the same set of tiers, but"
                + " module 'a' supports [0, 1] and folder 'assets/img2' supports only [0].");
  }

  @Test
  public void tierNumbersNotStartingFromZero_throws() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img1#tier_1/image.jpg")
            .addFile("assets/img1#tier_2/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new DeviceTierParityValidator().validateAllModules(ImmutableList.of(moduleA)));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "All modules with device tier targeting must support the same contiguous"
                + " range of tier values starting from 0, but module 'a' supports [1, 2].");
  }

  @Test
  public void tierNumbersNotContiguous_throws() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img1#tier_0/image.jpg")
            .addFile("assets/img1#tier_2/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new DeviceTierParityValidator().validateAllModules(ImmutableList.of(moduleA)));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "All modules with device tier targeting must support the same contiguous"
                + " range of tier values starting from 0, but module 'a' supports [0, 2].");
  }
}
