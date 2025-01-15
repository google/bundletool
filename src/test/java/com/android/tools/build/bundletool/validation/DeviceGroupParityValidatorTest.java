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
public class DeviceGroupParityValidatorTest {

  @Test
  public void noGroups_ok() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img/image.jpg")
            .addFile("assets/img/image2.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b").setManifest(androidManifest("com.test.app")).build();

    new DeviceGroupParityValidator().validateAllModules(ImmutableList.of(moduleA, moduleB));
  }

  @Test
  public void sameGroups_ok() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img#group_x/image.jpg")
            .addFile("assets/img#group_y/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b")
            .addFile("assets/img#group_x/image.jpg")
            .addFile("assets/img#group_y/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();

    new DeviceGroupParityValidator().validateAllModules(ImmutableList.of(moduleA, moduleB));
  }

  @Test
  public void multipleFilesPerGroupedDirectory_ok() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img#group_x/imageA.jpg")
            .addFile("assets/img#group_x/imageB.jpg")
            .addFile("assets/img#group_y/image1.jpg")
            .addFile("assets/img#group_y/image2.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();

    new DeviceGroupParityValidator().validateAllModules(ImmutableList.of(moduleA));
  }

  @Test
  public void sameGroupsAndNoGroup_ok() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img#group_x/image.jpg")
            .addFile("assets/img#group_y/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b")
            .addFile("assets/img#group_x/image.jpg")
            .addFile("assets/img#group_y/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleC =
        new BundleModuleBuilder("c").setManifest(androidManifest("com.test.app")).build();

    new DeviceGroupParityValidator()
        .validateAllModules(ImmutableList.of(moduleA, moduleB, moduleC));
  }

  @Test
  public void differentGroupsInDifferentModules_throws() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img#group_x/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b")
            .addFile("assets/img#group_x/image.jpg")
            .addFile("assets/img#group_y/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new DeviceGroupParityValidator()
                    .validateAllModules(ImmutableList.of(moduleA, moduleB)));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "All modules with device group targeting must support the same set of groups, but"
                + " module 'a' supports [x] and module 'b' supports [x, y].");
  }

  @Test
  public void differentGroupsInDifferentFolders_throws() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img1#group_x/image.jpg")
            .addFile("assets/img1#group_y/image.jpg")
            .addFile("assets/img2#group_x/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new DeviceGroupParityValidator().validateAllModules(ImmutableList.of(moduleA)));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "All device-group-targeted folders in a module must support the same set of groups, but"
                + " module 'a' supports [x, y] and folder 'assets/img2' supports only [x].");
  }

  @Test
  public void validateAllModules_withNestedTargeting_succeeds() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img1#countries_latam#group_y/image.jpg")
            .addFile("assets/img1#countries_latam#group_x/image.jpg")
            .addFile("assets/img1#countries_latam/image.jpg")
            .addFile("assets/img1#group_y/image.jpg")
            .addFile("assets/img1#group_x/image.jpg")
            .addFile("assets/img1/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();

    new DeviceGroupParityValidator().validateAllModules(ImmutableList.of(moduleA));
  }

  @Test
  public void validateAllModules_withUngroupedDir_throws() {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img1#group_0/image.jpg")
            .addFile("assets/img1/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();

    assertThrows(
        InvalidBundleException.class,
        () -> new DeviceGroupParityValidator().validateAllModules(ImmutableList.of(moduleA)));
  }
}
