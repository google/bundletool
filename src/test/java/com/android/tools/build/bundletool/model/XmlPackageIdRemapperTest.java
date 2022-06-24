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

import static com.android.tools.build.bundletool.model.AndroidManifest.ANDROID_NAMESPACE_URI;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.xmlAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.xmlDecimalIntegerAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.xmlElement;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.xmlNamespace;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.xmlNode;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.USER_PACKAGE_OFFSET;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.entry;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.pkg;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.resourceTable;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.type;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.ConfigValue;
import com.android.aapt.Resources.Entry;
import com.android.aapt.Resources.FileReference;
import com.android.aapt.Resources.Item;
import com.android.aapt.Resources.Reference;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.Value;
import com.android.aapt.Resources.XmlAttribute;
import com.android.aapt.Resources.XmlElement;
import com.android.aapt.Resources.XmlNode;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link XmlPackageIdRemapper}. */
@RunWith(JUnit4.class)
public final class XmlPackageIdRemapperTest {

  private static final String PACKAGE_NAME = "com.test.sdk";
  private static final String MODULE_NAME = "comTestSdk";
  private static final int NEW_PACKAGE_ID = 0x82;
  private static final XmlPackageIdRemapper xmlPackageIgRemapper =
      new XmlPackageIdRemapper(NEW_PACKAGE_ID);

  @Test
  public void moduleHasNoResourceTable_noChange() {
    BundleModule module =
        new BundleModuleBuilder(MODULE_NAME).setManifest(androidManifest(MODULE_NAME)).build();

    BundleModule remappedModule = xmlPackageIgRemapper.remap(module);

    assertThat(remappedModule).isEqualTo(module);
  }

  @Test
  public void noResourceIdsToRemap_noChange() {
    BundleModule module =
        new BundleModuleBuilder(MODULE_NAME)
            .setManifest(androidManifestWithoutResourceIds())
            .setResourceTable(ResourceTable.getDefaultInstance())
            .build();

    BundleModule remappedModule = xmlPackageIgRemapper.remap(module);

    assertThat(remappedModule).isEqualTo(module);
  }

  @Test
  public void remapInXmlResources_resourceTableReferencesXmlFile_fileNotPresentInModule_noChange() {
    BundleModule module =
        new BundleModuleBuilder(MODULE_NAME)
            .setManifest(androidManifestWithoutResourceIds())
            .setResourceTable(
                resourceTableWithFileReferences(ImmutableList.of("res/layout/main.xml")))
            .build();

    BundleModule remappedModule = xmlPackageIgRemapper.remap(module);

    assertThat(remappedModule).isEqualTo(module);
  }

  @Test
  public void remapInXmlResources_badXmlFileFormat_throws() {
    String xmlResourcePath = "res/layout/main.xml";
    BundleModule module =
        new BundleModuleBuilder(MODULE_NAME)
            .setManifest(androidManifestWithoutResourceIds())
            .setResourceTable(resourceTableWithFileReferences(ImmutableList.of(xmlResourcePath)))
            .addFile(xmlResourcePath, new byte[] {1, 2, 3})
            .build();

    Throwable e =
        assertThrows(CommandExecutionException.class, () -> xmlPackageIgRemapper.remap(module));
    assertThat(e).hasMessageThat().contains("Error parsing XML file 'res/layout/main.xml'");
  }

  @Test
  public void remapInXmlResources_success() {
    ImmutableList<String> xmlResourcePaths =
        ImmutableList.of("res/layout/main.xml", "res/layout/info.xml");
    BundleModule module =
        new BundleModuleBuilder(MODULE_NAME)
            .setManifest(androidManifestWithoutResourceIds())
            .setResourceTable(resourceTableWithFileReferences(xmlResourcePaths))
            .addFile(
                "res/layout/main.xml",
                xmlNodeWithResourceReferences(USER_PACKAGE_OFFSET).toByteArray())
            .addFile(
                "res/layout/info.xml",
                xmlNodeWithResourceReferences(USER_PACKAGE_OFFSET).toByteArray())
            // entry that is not referenced in the resource table, and should not be modified.
            .addFile("res/raw/raw.xml", new byte[] {1, 2, 3})
            .build();

    BundleModule modifiedModule = xmlPackageIgRemapper.remap(module);

    assertThat(modifiedModule)
        .isEqualTo(
            new BundleModuleBuilder(MODULE_NAME)
                .setManifest(androidManifestWithoutResourceIds())
                .setResourceTable(resourceTableWithFileReferences(xmlResourcePaths))
                .addFile(
                    "res/layout/main.xml",
                    xmlNodeWithResourceReferences(NEW_PACKAGE_ID).toByteArray())
                .addFile(
                    "res/layout/info.xml",
                    xmlNodeWithResourceReferences(NEW_PACKAGE_ID).toByteArray())
                .addFile("res/raw/raw.xml", new byte[] {1, 2, 3})
                .build());
  }

  @Test
  public void remapInAndroidManifest() {
    BundleModule module =
        new BundleModuleBuilder(MODULE_NAME)
            .setManifest(
                androidManifestWithResourceId(
                    USER_PACKAGE_OFFSET, /* typeId= */ 1, /* entryId= */ 2))
            .setResourceTable(ResourceTable.getDefaultInstance())
            .build();

    BundleModule remappedModule = xmlPackageIgRemapper.remap(module);

    assertThat(remappedModule)
        .isEqualTo(
            new BundleModuleBuilder(MODULE_NAME)
                .setManifest(
                    androidManifestWithResourceId(
                        NEW_PACKAGE_ID, /* typeId= */ 1, /* entryId= */ 2))
                .setResourceTable(ResourceTable.getDefaultInstance())
                .build());
  }

  private static ResourceTable resourceTableWithFileReferences(ImmutableList<String> paths) {
    int[] entryId = {0};
    ImmutableList.Builder<Entry> entries = ImmutableList.builder();
    paths.forEach(
        path ->
            entries.add(
                entry(
                    ++entryId[0],
                    "entry" + entryId[0],
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
                        .build())));
    return resourceTable(
        pkg(
            USER_PACKAGE_OFFSET,
            "pkg",
            type(/* id= */ 1, "type1", entries.build().toArray(new Entry[0]))));
  }

  private static XmlNode xmlNodeWithResourceReferences(int packageId) {
    int typeId = 1;
    int entryId = 1;
    return XmlNode.newBuilder()
        .setElement(
            XmlElement.newBuilder()
                .addAttribute(
                    XmlAttribute.newBuilder()
                        .setCompiledItem(
                            Item.newBuilder().setRef(reference(packageId, typeId++, entryId++))))
                .addAttribute(
                    XmlAttribute.newBuilder()
                        .setCompiledItem(
                            Item.newBuilder().setRef(reference(packageId, typeId++, entryId++))))
                .addChild(
                    XmlNode.newBuilder()
                        .setElement(
                            XmlElement.newBuilder()
                                .addAttribute(XmlAttribute.newBuilder().setName("attributeName"))
                                .addChild(
                                    XmlNode.newBuilder()
                                        .setElement(
                                            XmlElement.newBuilder()
                                                .addAttribute(
                                                    XmlAttribute.newBuilder()
                                                        .setCompiledItem(
                                                            Item.newBuilder()
                                                                .setRef(
                                                                    reference(
                                                                        packageId, typeId++,
                                                                        entryId++))))))
                                .addChild(
                                    XmlNode.newBuilder()
                                        .setElement(
                                            XmlElement.newBuilder()
                                                .addAttribute(
                                                    XmlAttribute.newBuilder()
                                                        .setCompiledItem(
                                                            Item.newBuilder()
                                                                .setRef(
                                                                    reference(
                                                                        packageId, typeId++,
                                                                        entryId++)))))))))
        .build();
  }

  private static Reference reference(int packageId, int typeId, int entryId) {
    return Reference.newBuilder().setId(resourceId(packageId, typeId, entryId)).build();
  }

  private static XmlNode androidManifestWithoutResourceIds() {
    return xmlNode(
        xmlElement(
            "manifest",
            ImmutableList.of(xmlNamespace("android", ANDROID_NAMESPACE_URI)),
            /* attributes= */ ImmutableList.of()));
  }

  private static XmlNode androidManifestWithResourceId(int packageId, int typeId, int entryId) {
    return xmlNode(
        xmlElement(
            "manifest",
            ImmutableList.of(xmlNamespace("android", ANDROID_NAMESPACE_URI)),
            ImmutableList.of(
                xmlAttribute("package", PACKAGE_NAME),
                xmlDecimalIntegerAttribute(
                    ANDROID_NAMESPACE_URI,
                    "versionCode",
                    resourceId(packageId, typeId, entryId),
                    1))));
  }

  private static int resourceId(int packageId, int typeId, int entryId) {
    return 0x1000000 * packageId + 0x10000 * typeId + entryId;
  }
}
