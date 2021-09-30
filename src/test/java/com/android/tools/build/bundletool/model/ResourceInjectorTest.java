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

package com.android.tools.build.bundletool.model;

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.TargetingUtils.lPlusVariantTargeting;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.Entry;
import com.android.aapt.Resources.EntryId;
import com.android.aapt.Resources.Package;
import com.android.aapt.Resources.PackageId;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.Type;
import com.android.aapt.Resources.TypeId;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ResourceInjectorTest {

  private static final String PACKAGE_NAME = "com.example.app";

  @Test
  public void missingResourceTable_resourceTableCreated() {
    ModuleSplit moduleSplit =
        ModuleSplit.builder()
            .setModuleName(BundleModuleName.create("base"))
            .setApkTargeting(ApkTargeting.getDefaultInstance())
            .setVariantTargeting(lPlusVariantTargeting())
            .setMasterSplit(true)
            .setAndroidManifest(AndroidManifest.create(androidManifest(PACKAGE_NAME)))
            .build();
    ResourceInjector resourceInjector = ResourceInjector.fromModuleSplit(moduleSplit);
    ResourceId resourceId =
        resourceInjector.addResource(/* entryType= */ "xml", Entry.getDefaultInstance());

    ResourceTable expected =
        ResourceTable.newBuilder()
            .addPackage(
                Package.newBuilder()
                    .setPackageName(PACKAGE_NAME)
                    .setPackageId(PackageId.newBuilder().setId(0x7f))
                    .addType(
                        Type.newBuilder()
                            .setTypeId(TypeId.newBuilder().setId(1))
                            .setName("xml")
                            .addEntry(
                                Entry.newBuilder().setEntryId(EntryId.newBuilder().setId(0)))))
            .build();

    assertThat(resourceInjector.build()).isEqualTo(expected);
    assertThat(resourceId.getFullResourceId()).isEqualTo(0x7f010000);
  }

  @Test
  public void missingXmlType_typeCreated() {
    ResourceTable.Builder resourceTable =
        ResourceTable.newBuilder()
            .addPackage(
                Package.newBuilder()
                    .setPackageId(PackageId.newBuilder().setId(0x12))
                    .setPackageName(PACKAGE_NAME));
    ResourceInjector resourceInjector = new ResourceInjector(resourceTable, PACKAGE_NAME);
    ResourceId resourceId =
        resourceInjector.addResource(/* entryType= */ "xml", Entry.getDefaultInstance());

    ResourceTable expected =
        ResourceTable.newBuilder()
            .addPackage(
                Package.newBuilder()
                    .setPackageId(PackageId.newBuilder().setId(0x12))
                    .setPackageName(PACKAGE_NAME)
                    .addType(
                        Type.newBuilder()
                            .setTypeId(TypeId.newBuilder().setId(0x01))
                            .setName("xml")
                            .addEntry(
                                Entry.newBuilder().setEntryId(EntryId.newBuilder().setId(0x0000)))))
            .build();

    assertThat(resourceInjector.build()).isEqualTo(expected);
    assertThat(resourceId.getFullResourceId()).isEqualTo(0x12010000);
  }

  @Test
  public void multipleTypes_entryCreatedInMatchedClass() {
    ResourceTable.Builder resourceTable =
        ResourceTable.newBuilder()
            .addPackage(
                Package.newBuilder()
                    .setPackageId(PackageId.newBuilder().setId(0x12))
                    .setPackageName(PACKAGE_NAME)
                    .addType(
                        Type.newBuilder()
                            .setTypeId(TypeId.newBuilder().setId(0x01))
                            .setName("drawable")
                            .addEntry(
                                Entry.newBuilder().setEntryId(EntryId.newBuilder().setId(0x0000))))
                    .addType(
                        Type.newBuilder()
                            .setTypeId(TypeId.newBuilder().setId(0x02))
                            .setName("xml")
                            .addEntry(
                                Entry.newBuilder()
                                    .setEntryId(EntryId.newBuilder().setId(0x0000)))));
    ResourceInjector resourceInjector = new ResourceInjector(resourceTable, PACKAGE_NAME);
    ResourceId resourceId =
        resourceInjector.addResource(/* entryType= */ "xml", Entry.getDefaultInstance());

    ResourceTable expected =
        ResourceTable.newBuilder()
            .addPackage(
                Package.newBuilder()
                    .setPackageId(PackageId.newBuilder().setId(0x12))
                    .setPackageName(PACKAGE_NAME)
                    .addType(
                        Type.newBuilder()
                            .setTypeId(TypeId.newBuilder().setId(0x01))
                            .setName("drawable")
                            .addEntry(
                                Entry.newBuilder().setEntryId(EntryId.newBuilder().setId(0x0000))))
                    .addType(
                        Type.newBuilder()
                            .setTypeId(TypeId.newBuilder().setId(0x02))
                            .setName("xml")
                            .addEntry(
                                Entry.newBuilder().setEntryId(EntryId.newBuilder().setId(0x0000)))
                            .addEntry(
                                Entry.newBuilder().setEntryId(EntryId.newBuilder().setId(0x0001)))))
            .build();

    assertThat(resourceInjector.build()).isEqualTo(expected);
    assertThat(resourceId.getFullResourceId()).isEqualTo(0x12020001);
  }

  @Test
  public void noFreeEntryId_throws() {
    ResourceTable.Builder resourceTable =
        ResourceTable.newBuilder()
            .addPackage(
                Package.newBuilder()
                    .setPackageId(PackageId.newBuilder().setId(0x12))
                    .setPackageName(PACKAGE_NAME)
                    .addType(
                        Type.newBuilder()
                            .setTypeId(TypeId.newBuilder().setId(0x01))
                            .setName("drawable")
                            .addEntry(
                                Entry.newBuilder().setEntryId(EntryId.newBuilder().setId(0x0000))))
                    .addType(
                        Type.newBuilder()
                            .setTypeId(TypeId.newBuilder().setId(0x02))
                            .setName("layout")
                            .addEntry(
                                Entry.newBuilder()
                                    .setEntryId(EntryId.newBuilder().setId(0xffff)))));
    ResourceInjector resourceInjector = new ResourceInjector(resourceTable, PACKAGE_NAME);
    assertThrows(
        CommandExecutionException.class,
        () -> resourceInjector.addResource(/* entryType= */ "layout", Entry.getDefaultInstance()));
  }

  @Test
  public void noFreeTypeId_throws() {
    ResourceTable.Builder resourceTable =
        ResourceTable.newBuilder()
            .addPackage(
                Package.newBuilder()
                    .setPackageId(PackageId.newBuilder().setId(0x12))
                    .setPackageName(PACKAGE_NAME)
                    .addType(
                        Type.newBuilder()
                            .setTypeId(TypeId.newBuilder().setId(0xff))
                            .setName("drawable")));
    ResourceInjector resourceInjector = new ResourceInjector(resourceTable, PACKAGE_NAME);
    assertThrows(
        CommandExecutionException.class,
        () -> resourceInjector.addResource(/* entryType= */ "layout", Entry.getDefaultInstance()));
  }
}
