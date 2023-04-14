/*
 * Copyright (C) 2023 The Android Open Source Project
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
public class NestedTargetingValidatorTest {

  @Test
  public void validateAllModules_noTargeting_ok() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img/image.jpg")
            .addFile("assets/img/image2.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b").setManifest(androidManifest("com.test.app")).build();

    new NestedTargetingValidator()
        .validateAllModules(/* modules= */ ImmutableList.of(moduleA, moduleB));
  }

  @Test
  public void validateAllModules_noNestedTargeting_ok() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img#countries_latam/image.jpg")
            .addFile("assets/img#countries_sea/image.jpg")
            .addFile("assets/img/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b").setManifest(androidManifest("com.test.app")).build();

    new NestedTargetingValidator()
        .validateAllModules(/* modules= */ ImmutableList.of(moduleA, moduleB));
  }

  @Test
  public void validateAllModules_multipleDirectoriesWithSameTargeting_throws() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/texture#countries_latam#tcf_astc/image.jpg")
            .addFile("assets/texture#tcf_astc#countries_latam/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new NestedTargetingValidator()
                    .validateAllModules(/* modules= */ ImmutableList.of(moduleA)));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Found multiple directories using same targeting values in module 'a'. Directory"
                + " 'assets/texture' is targeted on following dimension values some of which vary"
                + " only in nesting order: '[#countries_latam#tcf_astc,"
                + " #tcf_astc#countries_latam]'.");
  }

  @Test
  public void validateAllModules_differentNestedTargetingOnDifferentFolders_throws() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/texture1#countries_latam#tcf_astc/image.jpg")
            .addFile("assets/texture1#countries_latam/image.jpg")
            .addFile("assets/texture1/image.jpg")
            .addFile("assets/texture2#countries_sea#tcf_astc/image.jpg")
            .addFile("assets/texture2#countries_sea/image.jpg")
            .addFile("assets/texture2/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new NestedTargetingValidator()
                    .validateAllModules(/* modules= */ ImmutableList.of(moduleA)));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Found directories targeted on different set of targeting dimensions in module 'a'."
                + " Please make sure all directories are targeted on same set of targeting"
                + " dimensions in same order.");
  }

  @Test
  public void validateAllModules_directoryTargetedOnDifferentSetOfTargetingDimension_throws() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/texture1#countries_latam#tcf_astc/image.jpg")
            .addFile("assets/texture1#countries_latam#tier_1/image.jpg")
            .addFile("assets/texture1/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new NestedTargetingValidator()
                    .validateAllModules(/* modules= */ ImmutableList.of(moduleA)));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Found directory targeted on different set of targeting dimensions in module 'a'."
                + " Targeting Used: '[, #countries_latam#tcf_astc, #countries_latam#tier_1]'."
                + " Please make sure all directories are targeted on same set of targeting"
                + " dimensions in same order.");
  }

  @Test
  public void validateAllModules_directoryTargetedOnDifferentOrderOfTargetingDimension_throws() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/texture1#countries_latam#tcf_astc/image.jpg")
            .addFile("assets/texture1#tcf_pvrtc#countries_latam/image.jpg")
            .addFile("assets/texture1#countries_latam/image.jpg")
            .addFile("assets/texture1/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new NestedTargetingValidator()
                    .validateAllModules(/* modules= */ ImmutableList.of(moduleA)));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Found directory targeted on different order of targeting dimensions in module 'a'."
                + " Targeting Used: '[, #countries_latam, #countries_latam#tcf_astc,"
                + " #tcf_pvrtc#countries_latam]'. Please make sure all directories are targeted on"
                + " same set of targeting dimensions in same order.");
  }

  @Test
  public void validateAllModules_folderNotTargetingAllCartesianProductOfDimensionValues_throws() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/texture1#countries_latam#tcf_astc/image.jpg")
            .addFile("assets/texture1#countries_latam#tcf_pvrtc/image.jpg")
            .addFile("assets/texture1#countries_sea#tcf_astc/image.jpg")
            .addFile("assets/texture1#countries_sea#tcf_pvrtc/image.jpg")
            .addFile("assets/texture1/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new NestedTargetingValidator()
                    .validateAllModules(/* modules= */ ImmutableList.of(moduleA)));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Module 'a' uses nested targeting but does not define targeting for all of the points"
                + " in the cartesian product of dimension values used.");
  }

  @Test
  public void validateAllModules_validNestedTargeting_succeeds() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/texture1#countries_latam#tcf_astc/image.jpg")
            .addFile("assets/texture1#countries_latam#tcf_pvrtc/image.jpg")
            .addFile("assets/texture1#countries_latam/image.jpg")
            .addFile("assets/texture1#countries_sea#tcf_astc/image.jpg")
            .addFile("assets/texture1#countries_sea#tcf_pvrtc/image.jpg")
            .addFile("assets/texture1#countries_sea/image.jpg")
            .addFile("assets/texture1#tcf_astc/image.jpg")
            .addFile("assets/texture1#tcf_pvrtc/image.jpg")
            .addFile("assets/texture1/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();

    new NestedTargetingValidator().validateAllModules(/* modules= */ ImmutableList.of(moduleA));
  }

  @Test
  public void validateAllModules_differentModulesWithDifferentNestedTargeting_throws() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/texture1#countries_latam#tcf_astc/image.jpg")
            .addFile("assets/texture1#countries_latam#tcf_pvrtc/image.jpg")
            .addFile("assets/texture1#countries_latam/image.jpg")
            .addFile("assets/texture1#countries_sea#tcf_astc/image.jpg")
            .addFile("assets/texture1#countries_sea#tcf_pvrtc/image.jpg")
            .addFile("assets/texture1#countries_sea/image.jpg")
            .addFile("assets/texture1#tcf_astc/image.jpg")
            .addFile("assets/texture1#tcf_pvrtc/image.jpg")
            .addFile("assets/texture1/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b")
            .addFile("assets/texture1#countries_latam#tcf_astc/image.jpg")
            .addFile("assets/texture1#countries_latam#tcf_pvrtc/image.jpg")
            .addFile("assets/texture1#countries_latam/image.jpg")
            .addFile("assets/texture1#tcf_astc/image.jpg")
            .addFile("assets/texture1#tcf_pvrtc/image.jpg")
            .addFile("assets/texture1/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new NestedTargetingValidator()
                    .validateAllModules(/* modules= */ ImmutableList.of(moduleA, moduleB)));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Found different nested targeting across different modules. Please make sure all"
                + " modules use same nested targeting.");
  }

  @Test
  public void validateAllModules_differentModulesWithSameNestedTargeting_succeeds() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/texture1#countries_latam#tcf_astc/image.jpg")
            .addFile("assets/texture1#countries_latam#tcf_pvrtc/image.jpg")
            .addFile("assets/texture1#countries_latam/image.jpg")
            .addFile("assets/texture1#tcf_astc/image.jpg")
            .addFile("assets/texture1#tcf_pvrtc/image.jpg")
            .addFile("assets/texture1/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b")
            .addFile("assets/texture1#countries_latam#tcf_astc/image.jpg")
            .addFile("assets/texture1#countries_latam#tcf_pvrtc/image.jpg")
            .addFile("assets/texture1#countries_latam/image.jpg")
            .addFile("assets/texture1#tcf_astc/image.jpg")
            .addFile("assets/texture1#tcf_pvrtc/image.jpg")
            .addFile("assets/texture1/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();

    new NestedTargetingValidator()
        .validateAllModules(/* modules= */ ImmutableList.of(moduleA, moduleB));
  }

  @Test
  public void validateAllModules_modulesWithAndWithoutNestedTargeting_succeeds() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/texture1/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b")
            .addFile("assets/texture1#countries_latam#tcf_astc/image.jpg")
            .addFile("assets/texture1#countries_latam#tcf_pvrtc/image.jpg")
            .addFile("assets/texture1#countries_latam/image.jpg")
            .addFile("assets/texture1#tcf_astc/image.jpg")
            .addFile("assets/texture1#tcf_pvrtc/image.jpg")
            .addFile("assets/texture1/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();

    new NestedTargetingValidator()
        .validateAllModules(/* modules= */ ImmutableList.of(moduleA, moduleB));
  }
}
