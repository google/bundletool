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
package com.android.tools.build.bundletool.testing;

import static com.android.tools.build.bundletool.testing.truth.resources.TruthResourceTable.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.ConfigurationOuterClass.Configuration;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.Type;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ResourceTableBuilderTest {

  @Test
  public void resourcesOfDifferenTypesInOnePackage() {
    ResourceTable resourceTable =
        new ResourceTableBuilder()
            .addPackage("com.test.app")
            .addDrawableResource("splash", "res/drawable/splash.png")
            .addStringResource("label", "Hello, World!")
            .addXmlResource("home", "res/layout/homescreen.xml")
            .addMipmapResource("icon", "res/mipmap/icon.png")
            .build();

    assertThat(resourceTable.getPackageCount()).isEqualTo(1);
    assertThat(resourceTable)
        .hasPackage(0x7F)
        .withType("drawable")
        .containingResource("splash")
        .onlyWithConfigs(Configuration.getDefaultInstance());
    assertThat(resourceTable)
        .hasPackage(0x7F)
        .withType("string")
        .containingResource("label")
        .onlyWithConfigs(Configuration.getDefaultInstance());
    assertThat(resourceTable)
        .hasPackage(0x7F)
        .withType("xml")
        .containingResource("home")
        .onlyWithConfigs(Configuration.getDefaultInstance());
    assertThat(resourceTable)
        .hasPackage(0x7F)
        .withType("mipmap")
        .containingResource("icon")
        .onlyWithConfigs(Configuration.getDefaultInstance());
  }

  @Test
  public void addResourceWithNoPackage_throws() {
    assertThrows(
        IllegalStateException.class,
        () -> new ResourceTableBuilder().addStringResource("label", "Hello!").build());
  }

  @Test
  public void addPackageIdAlreadyInUse_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ResourceTableBuilder()
                .addPackage("com.test.app")
                .addPackage("com.test.app.split", 0x7F)
                .build());
  }

  @Test
  public void addPackageIncrementsPackageId() {
    ResourceTable resourceTable =
        new ResourceTableBuilder()
            .addPackage("com.test.app")
            .addPackage("com.test.app.split")
            .build();

    assertThat(resourceTable).hasPackage("com.test.app", 0x7F);
    assertThat(resourceTable).hasPackage("com.test.app.split", 0x80);
  }

  @Test
  public void twoPackagesSameResourceName() {
    ResourceTable resourceTable =
        new ResourceTableBuilder()
            .addPackage("com.test.app")
            .addStringResource("label", "Hello")
            .addPackage("com.test.app.split", 0x80)
            .addStringResource("label", "World")
            .build();

    assertThat(resourceTable)
        .hasPackage("com.test.app", 0x7F)
        .withType("string")
        .containingResource("label")
        .withStringValue("Hello");

    assertThat(resourceTable)
        .hasPackage("com.test.app.split", 0x80)
        .withType("string")
        .containingResource("label")
        .withStringValue("World");
  }

  @Test
  public void customFileResource() {
    ResourceTable resourceTable =
        new ResourceTableBuilder()
            .addPackage("com.test.app")
            .addFileResource("raw", "wear_app", "res/raw/wear_app.apk")
            .build();

    assertThat(resourceTable)
        .hasPackage("com.test.app")
        .withType("raw")
        .containingResource("wear_app")
        .onlyWithConfigs(Configuration.getDefaultInstance())
        .withFileReference("res/raw/wear_app.apk");
  }

  @Test
  public void sameTypeIdForSameResourceType() {
    ResourceTable resourceTable =
        new ResourceTableBuilder()
            .addPackage("com.test.app")
            .addStringResource("label", "Hello")
            .addDrawableResource("icon", "res/drawable/icon.png")
            .addStringResource("label2", "Hello2")
            .addDrawableResource("icon2", "res/drawable/icon2.png")
            .build();

    List<Type> types = resourceTable.getPackage(0).getTypeList();

    assertThat(types.stream().map(type -> type.getTypeId().getId()).collect(toList()))
        .containsExactly(1, 2);
    assertThat(types.stream().map(type -> type.getName()).collect(toList()))
        .containsExactly("string", "drawable");
  }

  @Test
  public void typeIdsStartAtOneForAllPackages() {
    ResourceTable resourceTable =
        new ResourceTableBuilder()
            .addPackage("com.test.app")
            .addStringResource("label", "Hello")
            .addPackage("com.test.app.split")
            .addDrawableResource("icon", "res/drawable/icon.png")
            .build();

    assertThat(resourceTable.getPackage(0).getType(0).getTypeId().getId()).isEqualTo(1);
    assertThat(resourceTable.getPackage(1).getType(0).getTypeId().getId()).isEqualTo(1);
  }

  @Test
  public void addDrawableForMultipleDensities() {
    ResourceTable resourceTable =
        new ResourceTableBuilder()
            .addPackage("com.test.app")
            .addDrawableResourceForMultipleDensities(
                "icon",
                ImmutableMap.<Integer, String>builder()
                    .put(240, "res/drawable-hdpi/icon.png")
                    .put(320, "res/drawable-xhdpi/icon.png")
                    .put(480, "res/drawable-xxhdpi/icon.png")
                    .build())
            .build();

    assertThat(resourceTable)
        .hasPackage("com.test.app")
        .withType("drawable")
        .containingResource("icon")
        .onlyWithConfigs(
            Configuration.newBuilder().setDensity(240).build(),
            Configuration.newBuilder().setDensity(320).build(),
            Configuration.newBuilder().setDensity(480).build());
  }

  @Test
  public void addStringForMultipleLocales() {
    ResourceTable resourceTable =
        new ResourceTableBuilder()
            .addPackage("com.test.app")
            .addStringResourceForMultipleLocales(
                "label",
                ImmutableMap.<String, String>builder()
                    .put("en-US", "Hi!")
                    .put("en-GB", "Hello!")
                    .put("en-AU", "G'day!")
                    .build())
            .build();

    assertThat(resourceTable)
        .hasPackage("com.test.app")
        .withType("string")
        .containingResource("label")
        .onlyWithConfigs(
            Configuration.newBuilder().setLocale("en-US").build(),
            Configuration.newBuilder().setLocale("en-GB").build(),
            Configuration.newBuilder().setLocale("en-AU").build());
  }

  @Test
  public void addMipmapResourceForMultipleDensities() {
    ResourceTable resourceTable =
        new ResourceTableBuilder()
            .addPackage("com.test.app")
            .addMipmapResourceForMultipleDensities(
                "icon",
                ImmutableMap.<Integer, String>builder()
                    .put(240, "res/drawable-hdpi/icon.png")
                    .put(320, "res/drawable-xhdpi/icon.png")
                    .put(480, "res/drawable-xxhdpi/icon.png")
                    .build())
            .build();

    assertThat(resourceTable)
        .hasPackage("com.test.app")
        .withType("mipmap")
        .containingResource("icon")
        .onlyWithConfigs(
            Configuration.newBuilder().setDensity(240).build(),
            Configuration.newBuilder().setDensity(320).build(),
            Configuration.newBuilder().setDensity(480).build());
  }
}
