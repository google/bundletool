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

import static com.android.tools.build.bundletool.model.AndroidManifest.DISTRIBUTION_NAMESPACE_URI;
import static com.android.tools.build.bundletool.model.AndroidManifest.SDK_SANDBOX_MIN_VERSION;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.USER_PACKAGE_OFFSET;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.entry;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.pkg;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.resourceTable;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.type;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.aapt.Resources.ConfigValue;
import com.android.aapt.Resources.FileReference;
import com.android.aapt.Resources.Item;
import com.android.aapt.Resources.Reference;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.Value;
import com.android.aapt.Resources.XmlAttribute;
import com.android.aapt.Resources.XmlElement;
import com.android.aapt.Resources.XmlNode;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdk;
import com.android.tools.build.bundletool.model.BundleModule.ModuleType;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link SdkModuleToAppBundleModuleConverter}. */
@RunWith(JUnit4.class)
public final class SdkModuleToAppBundleModuleConverterTest {

  private static final String PACKAGE_NAME = "com.test.sdk";
  private static final int NEW_PACKAGE_ID = 0x82;

  @Test
  public void convert_modifiesModuleName_modifiesManifest_setsIsSdkDependencyModule() {
    BundleModule sdkModule =
        new BundleModuleBuilder("base")
            .setManifest(androidManifest(PACKAGE_NAME, withMinSdkVersion(SDK_SANDBOX_MIN_VERSION)))
            .build();

    BundleModule modifiedModule =
        new SdkModuleToAppBundleModuleConverter(
                PACKAGE_NAME, sdkModule, RuntimeEnabledSdk.getDefaultInstance())
            .convert();

    // Verify that module name was modified.
    assertThat(modifiedModule.getName()).isNotEqualTo(sdkModule.getName());
    assertThat(modifiedModule.getName().getName()).isEqualTo("comtestsdk");

    // Verify the manifest.
    AndroidManifest androidManifest = modifiedModule.getAndroidManifest();
    assertThat(androidManifest.getIsFeatureSplit()).hasValue(true);
    assertThat(androidManifest.getSplitId()).hasValue("comtestsdk");
    assertThat(androidManifest.getIsModuleIncludedInFusing()).hasValue(true);
    assertThat(
            androidManifest
                .getManifestElement()
                .getOptionalChildElement(DISTRIBUTION_NAMESPACE_URI, "module"))
        .isPresent();

    // Verify that module type is updated.
    assertThat(modifiedModule.getModuleType()).isEqualTo(ModuleType.SDK_DEPENDENCY_MODULE);
  }

  @Test
  public void convert_remapsResourceIdsInResourceTable() {
    BundleModule sdkModule =
        new BundleModuleBuilder("base")
            .setManifest(androidManifest(PACKAGE_NAME, withMinSdkVersion(SDK_SANDBOX_MIN_VERSION)))
            .setResourceTable(resourceTable(pkg(USER_PACKAGE_OFFSET, PACKAGE_NAME)))
            .build();

    BundleModule modifiedModule =
        new SdkModuleToAppBundleModuleConverter(
                PACKAGE_NAME,
                sdkModule,
                RuntimeEnabledSdk.newBuilder().setResourcesPackageId(NEW_PACKAGE_ID).build())
            .convert();

    assertThat(modifiedModule.getResourceTable().get().getPackage(0).getPackageId().getId())
        .isEqualTo(NEW_PACKAGE_ID);
  }

  @Test
  public void convert_removesLastDexFile() {
    BundleModule sdkModule =
        new BundleModuleBuilder("base")
            .setManifest(androidManifest(PACKAGE_NAME, withMinSdkVersion(SDK_SANDBOX_MIN_VERSION)))
            .addFile("dex/classes.dex")
            .addFile("dex/classes2.dex")
            .build();

    BundleModule modifiedModule =
        new SdkModuleToAppBundleModuleConverter(
                PACKAGE_NAME, sdkModule, RuntimeEnabledSdk.getDefaultInstance())
            .convert();

    assertThat(modifiedModule.getEntry(ZipPath.create("dex/classes.dex"))).isPresent();
    assertThat(modifiedModule.getEntry(ZipPath.create("dex/classes2.dex"))).isEmpty();
  }

  @Test
  public void convert_remapsResourceIdsInXmlResources() throws Exception {
    String xmlResourcePath = "res/layout/main.xml";
    ResourceTable resourceTable = resourceTableWithFileReferences(xmlResourcePath);
    BundleModule sdkModule =
        new BundleModuleBuilder("base")
            .setManifest(androidManifest(PACKAGE_NAME, withMinSdkVersion(SDK_SANDBOX_MIN_VERSION)))
            .setResourceTable(resourceTable)
            .addFile(
                xmlResourcePath, xmlNodeWithResourceReference(USER_PACKAGE_OFFSET).toByteArray())
            .build();

    BundleModule modifiedModule =
        new SdkModuleToAppBundleModuleConverter(
                PACKAGE_NAME,
                sdkModule,
                RuntimeEnabledSdk.newBuilder().setResourcesPackageId(NEW_PACKAGE_ID).build())
            .convert();

    assertThat(
            XmlNode.parseFrom(
                modifiedModule
                    .getEntry(ZipPath.create(xmlResourcePath))
                    .get()
                    .getContent()
                    .openStream()))
        .isEqualTo(xmlNodeWithResourceReference(NEW_PACKAGE_ID));
  }

  private static ResourceTable resourceTableWithFileReferences(String path) {
    return resourceTable(
        pkg(
            USER_PACKAGE_OFFSET,
            "pkg",
            type(
                /* id= */ 1,
                "type1",
                entry(
                    /* id= */ 1,
                    "entry1",
                    ConfigValue.newBuilder()
                        .setValue(
                            Value.newBuilder()
                                .setItem(
                                    Item.newBuilder()
                                        .setFile(
                                            FileReference.newBuilder()
                                                .setType(FileReference.Type.PROTO_XML)
                                                .setPath(path)
                                                .build())))
                        .build()))));
  }

  private static XmlNode xmlNodeWithResourceReference(int packageId) {
    return XmlNode.newBuilder()
        .setElement(
            XmlElement.newBuilder()
                .addAttribute(
                    XmlAttribute.newBuilder()
                        .setCompiledItem(Item.newBuilder().setRef(reference(packageId)))))
        .build();
  }

  private static Reference reference(int packageId) {
    int typeId = 1;
    int entryId = 2;
    return Reference.newBuilder().setId(0x1000000 * packageId + 0x10000 * typeId + entryId).build();
  }
}
