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

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withFusingAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withIsolatedSplits;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.USER_PACKAGE_OFFSET;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.entry;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.pkg;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.resourceTable;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.type;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.ResourceTable;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.android.tools.build.bundletool.testing.ResourceTableBuilder;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ResourceTableValidatorTest {

  @Test
  public void validateModule_validWithoutResources_succeeds() throws Exception {
    BundleModule module =
        new BundleModuleBuilder("module")
            .addFile("assets/file.txt")
            .setManifest(androidManifest("com.test.app"))
            .build();

    new ResourceTableValidator().validateModule(module);
  }

  @Test
  public void validateModule_validWithResources_succeeds() throws Exception {
    BundleModule module =
        new BundleModuleBuilder("module")
            .addFile("res/drawable/icon.png")
            .setResourceTable(
                new ResourceTableBuilder()
                    .addPackage("com.test.app")
                    .addDrawableResource("icon", "res/drawable/icon.png")
                    .build())
            .setManifest(androidManifest("com.test.app"))
            .build();

    new ResourceTableValidator().validateModule(module);
  }

  @Test
  public void validateModule_nonReferencedFile_throws() throws Exception {
    BundleModule module =
        new BundleModuleBuilder("module")
            .setResourceTable(
                new ResourceTableBuilder()
                    .addPackage("com.test.app")
                    .addDrawableResource("icon", "res/drawable/icon.png")
                    .build())
            .setManifest(androidManifest("com.test.app"))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ResourceTableValidator().validateModule(module));

    assertThat(exception)
        .hasMessageThat()
        .contains("contains references to non-existing files: [res/drawable/icon.png]");
  }

  @Test
  public void validateModule_nonExistingFile_throws() throws Exception {
    BundleModule module =
        new BundleModuleBuilder("module")
            .setResourceTable(ResourceTable.getDefaultInstance())
            .addFile("res/drawable/icon.png")
            .setManifest(androidManifest("com.test.app"))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ResourceTableValidator().validateModule(module));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "contains resource files that are not referenced from the resource table: "
                + "[res/drawable/icon.png]");
  }

  @Test
  public void validateModule_nonExistingResourceTable_throws() throws Exception {
    BundleModule module =
        new BundleModuleBuilder("module")
            .addFile("res/drawable/icon.png")
            .setManifest(androidManifest("com.test.app"))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ResourceTableValidator().validateModule(module));

    assertThat(exception)
        .hasMessageThat()
        .contains("contains resource files but is missing a resource table");
  }

  @Test
  public void validateModule_fileOutsideRes_throws() throws Exception {
    BundleModule module =
        new BundleModuleBuilder("module")
            .addFile("assets/icon.png")
            .setResourceTable(
                new ResourceTableBuilder()
                    .addPackage("com.test.app")
                    .addDrawableResource("icon", "assets/icon.png")
                    .build())
            .setManifest(androidManifest("com.test.app"))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ResourceTableValidator().validateModule(module));

    assertThat(exception)
        .hasMessageThat()
        .contains("references file 'assets/icon.png' outside of the 'res/' directory");
  }

  @Test
  public void duplicateResourceId_sameModule_throws() throws Exception {
    BundleModule module =
        new BundleModuleBuilder("base")
            .setResourceTable(
                resourceTable(
                    pkg(
                        USER_PACKAGE_OFFSET,
                        "com.test.app",
                        type(0x01, "drawable", entry(0x0002, "logo"), entry(0x0002, "logo2")))))
            .setManifest(androidManifest("com.test.app"))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ResourceTableValidator().checkResourceIds(ImmutableList.of(module)));

    assertThat(exception).hasMessageThat().contains("Duplicate resource");
  }

  @Test
  public void duplicateResourceId_differentModule_throws() throws Exception {
    BundleModule firstModule =
        new BundleModuleBuilder("base")
            .setResourceTable(
                resourceTable(
                    pkg(
                        USER_PACKAGE_OFFSET,
                        "com.test.app",
                        type(0x01, "drawable", entry(0x0002, "logo")))))
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule secondModule =
        new BundleModuleBuilder("secondModule")
            .setResourceTable(
                resourceTable(
                    pkg(
                        USER_PACKAGE_OFFSET,
                        "com.test.app",
                        type(0x01, "drawable", entry(0x0002, "logo")))))
            .setManifest(androidManifest("com.test.app"))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new ResourceTableValidator()
                    .checkResourceIds(ImmutableList.of(firstModule, secondModule)));

    assertThat(exception).hasMessageThat().contains("Duplicate resource");
  }

  @Test
  public void duplicateResourceId_isolatedSplits_sameModule_throws() throws Exception {
    BundleModule module =
        new BundleModuleBuilder("base")
            .setResourceTable(
                resourceTable(
                    pkg(
                        USER_PACKAGE_OFFSET,
                        "com.test.app",
                        type(0x01, "drawable", entry(0x0002, "logo"), entry(0x0002, "logo2")))))
            .setManifest(androidManifest("com.test.app", withIsolatedSplits(true)))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ResourceTableValidator().checkResourceIds(ImmutableList.of(module)));

    assertThat(exception).hasMessageThat().contains("Duplicate resource");
  }

  @Test
  public void duplicateResourceId_isolatedSplits_differentModule_doesNotThrow() throws Exception {
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .setResourceTable(
                resourceTable(
                    pkg(
                        USER_PACKAGE_OFFSET,
                        "com.test.app",
                        type(0x01, "drawable", entry(0x0002, "logo")))))
            .setManifest(androidManifest("com.test.app", withIsolatedSplits(true)))
            .build();
    BundleModule secondModule =
        new BundleModuleBuilder("secondModule")
            .setResourceTable(
                resourceTable(
                    pkg(
                        USER_PACKAGE_OFFSET,
                        "com.test.app",
                        type(0x01, "drawable", entry(0x0002, "logo")))))
            .setManifest(androidManifest("com.test.app", withFusingAttribute(false)))
            .build();

    new ResourceTableValidator().checkResourceIds(ImmutableList.of(baseModule, secondModule));
  }

  @Test
  public void duplicateResourceId_isolatedSplits_sameModuleWithoutFusing_throws() throws Exception {
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .setManifest(androidManifest("com.test.app", withIsolatedSplits(true)))
            .build();
    BundleModule secondModule =
        new BundleModuleBuilder("secondModule")
            .setResourceTable(
                resourceTable(
                    pkg(
                        USER_PACKAGE_OFFSET,
                        "com.test.app",
                        type(0x01, "drawable", entry(0x0002, "logo"), entry(0x0002, "logo2")))))
            .setManifest(androidManifest("com.test.app", withFusingAttribute(false)))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new ResourceTableValidator()
                    .checkResourceIds(ImmutableList.of(baseModule, secondModule)));

    assertThat(exception).hasMessageThat().contains("Duplicate resource");
  }

  @Test
  public void duplicateResourceId_isolatedSplits_differentModuleWithFusing_throws()
      throws Exception {
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .setResourceTable(
                resourceTable(
                    pkg(
                        USER_PACKAGE_OFFSET,
                        "com.test.app",
                        type(0x01, "drawable", entry(0x0002, "logo")))))
            .setManifest(androidManifest("com.test.app", withIsolatedSplits(true)))
            .build();
    BundleModule secondModule =
        new BundleModuleBuilder("secondModule")
            .setResourceTable(
                resourceTable(
                    pkg(
                        USER_PACKAGE_OFFSET,
                        "com.test.app",
                        type(0x01, "drawable", entry(0x0002, "logo")))))
            .setManifest(androidManifest("com.test.app", withFusingAttribute(true)))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new ResourceTableValidator()
                    .checkResourceIds(ImmutableList.of(baseModule, secondModule)));

    assertThat(exception).hasMessageThat().contains("Duplicate resource");
  }

  @Test
  public void noResourceTable_doesNotThrow() throws Exception {
    BundleModule module =
        new BundleModuleBuilder("base")
            .setResourceTable(resourceTable())
            .setManifest(androidManifest("com.test.app"))
            .build();

    new ResourceTableValidator().checkResourceIds(ImmutableList.of(module));
  }

  @Test
  public void nonDuplicateResources_sameModule_doesNotThrow() throws Exception {
    BundleModule module =
        new BundleModuleBuilder("base")
            .setResourceTable(
                resourceTable(
                    pkg(
                        USER_PACKAGE_OFFSET,
                        "com.test.app",
                        type(0x01, "drawable", entry(0x0002, "logo"), entry(0x0003, "logo2")))))
            .setManifest(androidManifest("com.test.app"))
            .build();

    new ResourceTableValidator().checkResourceIds(ImmutableList.of(module));
  }

  @Test
  public void nonDuplicateResources_differentModule_doesNotThrow() throws Exception {
    BundleModule firstModule =
        new BundleModuleBuilder("base")
            .setResourceTable(
                resourceTable(
                    pkg(
                        USER_PACKAGE_OFFSET,
                        "com.test.app",
                        type(0x01, "drawable", entry(0x0002, "logo")))))
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule secondModule =
        new BundleModuleBuilder("secondModule")
            .setResourceTable(
                resourceTable(
                    pkg(
                        USER_PACKAGE_OFFSET,
                        "com.test.app",
                        type(0x01, "drawable", entry(0x0003, "logo")))))
            .setManifest(androidManifest("com.test.app"))
            .build();

    new ResourceTableValidator().checkResourceIds(ImmutableList.of(firstModule, secondModule));
  }
}


