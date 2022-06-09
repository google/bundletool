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
package com.android.tools.build.bundletool.model;

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.Package;
import com.android.aapt.Resources.PackageId;
import com.android.aapt.Resources.ResourceTable;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ResourceIdRemapper}. */
@RunWith(JUnit4.class)
public final class ResourceIdRemapperTest {

  @Test
  public void moduleHasNoResourceTable_noChange() {
    BundleModule module =
        new BundleModuleBuilder("comTestSdk").setManifest(androidManifest("comTestSdk")).build();

    BundleModule remappedModule =
        ResourceIdRemapper.remapResourceIds(module, /* newResourcesPackageId= */ 3);
    assertThat(remappedModule).isEqualTo(module);
  }

  @Test
  public void resourceTableSpecifiesMultiplePackages_throws() {
    BundleModule module =
        new BundleModuleBuilder("comTestSdk")
            .setManifest(androidManifest("comTestSdk"))
            .setResourceTable(
                ResourceTable.newBuilder()
                    .addPackage(Package.newBuilder().setPackageId(PackageId.newBuilder().setId(1)))
                    .addPackage(Package.newBuilder().setPackageId(PackageId.newBuilder().setId(2)))
                    .build())
            .build();

    Throwable e =
        assertThrows(
            IllegalArgumentException.class,
            () -> ResourceIdRemapper.remapResourceIds(module, /* newResourcesPackageId= */ 3));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "Module 'comTestSdk' contains resource table with 2 'package' entries, but only 1 entry"
                + " is allowed.");
  }

  @Test
  public void packageIdUpdatedInResourceTable() {
    BundleModule module =
        new BundleModuleBuilder("comTestSdk")
            .setManifest(androidManifest("comTestSdk"))
            .setResourceTable(
                ResourceTable.newBuilder()
                    .addPackage(Package.newBuilder().setPackageId(PackageId.newBuilder().setId(1)))
                    .build())
            .build();

    module = ResourceIdRemapper.remapResourceIds(module, /* newResourcesPackageId= */ 2);

    assertThat(module.getResourceTable().get().getPackage(0).getPackageId().getId()).isEqualTo(2);
  }
}
