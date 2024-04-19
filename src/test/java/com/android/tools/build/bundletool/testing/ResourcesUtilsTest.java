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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.ResourceTable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ResourcesUtilsTest {

  @Test
  public void resolveResourceId_successful() {
    ResourceTable resourceTable =
        new ResourceTableBuilder()
            .addPackage("com.test.app", 0x7F)
            // 0x7F000000
            .addXmlResource("layout", "res/xml/layout1.xml")
            .addPackage("com.test.app.split", 0x80)
            // 0x80000000
            .addXmlResource("layout", "res/xml/layout2.xml")
            // 0x80010000
            .addStringResource("hello", "res/string/hello.xml")
            // 0x80010001
            .addStringResource("world", "res/string/world.xml")
            .build();

    assertThat(
            ResourcesUtils.resolveResourceId(
                resourceTable,
                /* packageName= */ "com.test.app",
                /* typeName= */ "xml",
                /* resourceName= */ "layout"))
        .hasValue(0x7F010000);
    assertThat(
            ResourcesUtils.resolveResourceId(
                resourceTable,
                /* packageName= */ "com.test.app.split",
                /* typeName= */ "xml",
                /* resourceName= */ "layout"))
        .hasValue(0x80010000);
    assertThat(
            ResourcesUtils.resolveResourceId(
                resourceTable,
                /* packageName= */ "com.test.app.split",
                /* typeName= */ "string",
                /* resourceName= */ "hello"))
        .hasValue(0x80020000);
    assertThat(
            ResourcesUtils.resolveResourceId(
                resourceTable,
                /* packageName= */ "com.test.app.split",
                /* typeName= */ "string",
                /* resourceName= */ "world"))
        .hasValue(0x80020001);
  }

  @Test
  public void resolveResourceId_multiplePackagesWithSameName_throws() {
    ResourceTable resourceTable =
        new ResourceTableBuilder()
            .addPackage("com.test.app", 0x7F)
            .addXmlResource("layout", "res/xml/layout.xml")
            .addPackage("com.test.app", 0x80)
            .addXmlResource("layout", "res/xml/layout.xml")
            .build();

    assertThrows(
        IllegalStateException.class,
        () ->
            ResourcesUtils.resolveResourceId(
                resourceTable,
                /* packageName= */ "com.test.app",
                /* typeName= */ "xml",
                /* resourceName= */ "layout"));
  }

  @Test
  public void resolveResourceId_noMatchingPackage_returnsEmpty() {
    ResourceTable resourceTable =
        new ResourceTableBuilder()
            .addPackage("com.test.app", 0x7F)
            .addXmlResource("layout", "res/xml/layout.xml")
            .build();

    assertThat(
            ResourcesUtils.resolveResourceId(
                resourceTable,
                /* packageName= */ "com.different.package",
                /* typeName= */ "xml",
                /* resourceName= */ "layout"))
        .isEmpty();
  }

  @Test
  public void resolveResourceId_noMatchingType_returnsEmpty() {
    ResourceTable resourceTable =
        new ResourceTableBuilder()
            .addPackage("com.test.app", 0x7F)
            .addXmlResource("layout", "res/xml/layout.xml")
            .build();

    assertThat(
            ResourcesUtils.resolveResourceId(
                resourceTable,
                /* packageName= */ "com.test.app",
                /* typeName= */ "string",
                /* resourceName= */ "layout"))
        .isEmpty();
  }

  @Test
  public void resolveResourceId_noMatchingResourceName_returnsEmpty() {
    ResourceTable resourceTable =
        new ResourceTableBuilder()
            .addPackage("com.test.app", 0x7F)
            .addXmlResource("layout", "res/xml/layout.xml")
            .build();

    assertThat(
            ResourcesUtils.resolveResourceId(
                resourceTable,
                /* packageName= */ "com.test.app",
                /* typeName= */ "xml",
                /* resourceName= */ "layout2"))
        .isEmpty();
  }
}
