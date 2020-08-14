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
package com.android.tools.build.bundletool.model;

import static com.android.tools.build.bundletool.model.WearApkLocator.WEAR_APK_1_0_METADATA_KEY;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMetadataResource;
import static com.android.tools.build.bundletool.testing.TestUtils.createModuleEntryForFile;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.ConfigurationOuterClass.Configuration;
import com.android.aapt.Resources;
import com.android.aapt.Resources.ConfigValue;
import com.android.aapt.Resources.Item;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.Value;
import com.android.aapt.Resources.XmlAttribute;
import com.android.aapt.Resources.XmlElement;
import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.TestData;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.testing.ResourceTableBuilder;
import com.android.tools.build.bundletool.testing.ResourcesUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class WearApkLocatorTest {

  private static final String PACKAGE_NAME = "com.test.app";
  private static final String XML_TYPE = "xml";
  private static final String RAW_TYPE = "raw";
  private static final String WEAR_DESC_XML_RES_NAME = "wearable_app_desc";
  private static final String WEAR_APK_RES_NAME = "wearable_app";
  private static final String WEAR_DESC_XML_RES_PATH = "res/xml/wearable_app_desc.xml";
  private static final String WEAR_APK_RES_PATH = "res/raw/wearable_app.apk";

  private final ZipPath wearableApkPath = ZipPath.create("res/raw/wear.apk");

  @Test
  public void wearApkLocationSuccessful() {
    ResourceTable resourceTable = buildResourceTableForEmbeddedWearApk(wearableApkPath);
    AndroidManifest androidManifest = buildAndroidManifestForWearApk(resourceTable);
    ImmutableList<ModuleEntry> entries = buildEntriesForEmbeddedWearApk(wearableApkPath);

    ModuleSplit moduleSplit = createModuleSplit(androidManifest, resourceTable, entries);

    Collection<ZipPath> zipPaths = WearApkLocator.findEmbeddedWearApkPaths(moduleSplit);
    assertThat(zipPaths).containsExactly(wearableApkPath);
  }

  @Test
  public void missingMetadata_noWearApk() {
    ResourceTable resourceTable = buildResourceTableForEmbeddedWearApk(wearableApkPath);
    AndroidManifest androidManifest = AndroidManifest.create(androidManifest(PACKAGE_NAME));
    ImmutableList<ModuleEntry> entries = buildEntriesForEmbeddedWearApk(wearableApkPath);

    ModuleSplit moduleSplit = createModuleSplit(androidManifest, resourceTable, entries);

    Collection<ZipPath> zipPaths = WearApkLocator.findEmbeddedWearApkPaths(moduleSplit);
    assertThat(zipPaths).isEmpty();
  }

  @Test
  public void resourceReferencedInMetadataDoesNotExist_throws() {
    ResourceTable resourceTable = new ResourceTableBuilder().addPackage(PACKAGE_NAME).build();
    AndroidManifest androidManifest =
        AndroidManifest.create(
            androidManifest(
                PACKAGE_NAME, withMetadataResource(WEAR_APK_1_0_METADATA_KEY, 0x7F000000)));
    ImmutableList<ModuleEntry> entries = buildEntriesForEmbeddedWearApk(wearableApkPath);

    ModuleSplit moduleSplit = createModuleSplit(androidManifest, resourceTable, entries);

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> WearApkLocator.findEmbeddedWearApkPaths(moduleSplit));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "Resource 0x7f000000 is referenced in the manifest in the "
                + "'com.google.android.wearable.beta.app' metadata, but was not found in the "
                + "resource table.");
  }

  @Test
  public void moreThanOneXmlDescriptionFile_throws() {
    ResourceTable resourceTable =
        new ResourceTableBuilder()
            .addPackage(PACKAGE_NAME)
            .addFileResourceForMultipleConfigs(
                XML_TYPE,
                WEAR_DESC_XML_RES_NAME,
                ImmutableMap.of(
                    Configuration.newBuilder().setDensity(240).build(),
                    "res/xml-hdpi/wearable_app_desc.xml",
                    Configuration.getDefaultInstance(),
                    WEAR_DESC_XML_RES_PATH))
            .addFileResource(RAW_TYPE, WEAR_APK_RES_NAME, wearableApkPath.toString())
            .build();
    AndroidManifest androidManifest = buildAndroidManifestForWearApk(resourceTable);
    ImmutableList<ModuleEntry> entries = buildEntriesForEmbeddedWearApk(wearableApkPath);

    ModuleSplit moduleSplit = createModuleSplit(androidManifest, resourceTable, entries);

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> WearApkLocator.findEmbeddedWearApkPaths(moduleSplit));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("More than one embedded Wear APK is not supported.");
  }

  @Test
  public void xmlDescriptionResourceNotPointingAtFile_throws() {
    ResourceTable resourceTable =
        new ResourceTableBuilder()
            .addPackage(PACKAGE_NAME)
            .addResource(
                XML_TYPE,
                WEAR_DESC_XML_RES_NAME,
                ConfigValue.newBuilder().setValue(newStringValue(WEAR_DESC_XML_RES_PATH)).build())
            .addFileResource(RAW_TYPE, WEAR_APK_RES_NAME, WEAR_APK_RES_PATH)
            .build();
    AndroidManifest androidManifest = buildAndroidManifestForWearApk(resourceTable);
    ImmutableList<ModuleEntry> entries = buildEntriesForEmbeddedWearApk(wearableApkPath);

    ModuleSplit moduleSplit = createModuleSplit(androidManifest, resourceTable, entries);

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> WearApkLocator.findEmbeddedWearApkPaths(moduleSplit));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("No XML description file path found for Wear APK.");
  }

  @Test
  public void xmlDescriptionFileNotFound_throws() {
    ResourceTable resourceTable =
        new ResourceTableBuilder()
            .addPackage(PACKAGE_NAME)
            .addXmlResource(WEAR_DESC_XML_RES_NAME, WEAR_DESC_XML_RES_PATH)
            .addFileResource(RAW_TYPE, WEAR_APK_RES_NAME, wearableApkPath.toString())
            .build();
    AndroidManifest androidManifest = buildAndroidManifestForWearApk(resourceTable);
    // No description XML file in the entries.
    ImmutableList<ModuleEntry> entries =
        ImmutableList.of(
            createModuleEntryForFile(
                wearableApkPath.toString(), TestData.readBytes("testdata/apk/com.test.app.apk")));

    ModuleSplit moduleSplit = createModuleSplit(androidManifest, resourceTable, entries);

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> WearApkLocator.findEmbeddedWearApkPaths(moduleSplit));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "Wear APK XML description file expected at 'res/xml/wearable_app_desc.xml' but was not "
                + "found.");
  }

  @Test
  public void xmlDescriptionFileWithUnbundledWearApk_returnsEmpty() {
    ResourceTable resourceTable = buildResourceTableForUnbundledWearApk();
    AndroidManifest androidManifest = buildAndroidManifestForWearApk(resourceTable);
    ImmutableList<ModuleEntry> entries = buildEntriesForUnbundledWearApk();

    ModuleSplit moduleSplit = createModuleSplit(androidManifest, resourceTable, entries);

    Collection<ZipPath> wearApkPath = WearApkLocator.findEmbeddedWearApkPaths(moduleSplit);
    assertThat(wearApkPath).isEmpty();
  }

  @Test
  public void xmlDescriptionFileDoesNotDescribeWearApk_throws() {
    ResourceTable resourceTable = buildResourceTableForEmbeddedWearApk(wearableApkPath);
    AndroidManifest androidManifest = buildAndroidManifestForWearApk(resourceTable);
    ImmutableList<ModuleEntry> entries =
        ImmutableList.of(
            createModuleEntryForFile(
                WEAR_DESC_XML_RES_PATH,
                createWearableAppXmlDescription(XmlNode.getDefaultInstance()).toByteArray()));

    ModuleSplit moduleSplit = createModuleSplit(androidManifest, resourceTable, entries);

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> WearApkLocator.findEmbeddedWearApkPaths(moduleSplit));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "The wear APK description file 'res/xml/wearable_app_desc.xml' does not contain "
                + "'unbundled' or 'rawPathResId'.");
  }

  @Test
  public void xmlDescriptionFileNotParseable_throws() {
    ResourceTable resourceTable = buildResourceTableForEmbeddedWearApk(wearableApkPath);
    AndroidManifest androidManifest = buildAndroidManifestForWearApk(resourceTable);
    ImmutableList<ModuleEntry> entries =
        ImmutableList.of(
            createModuleEntryForFile(WEAR_DESC_XML_RES_PATH, "RandomText".getBytes(UTF_8)));

    ModuleSplit moduleSplit = createModuleSplit(androidManifest, resourceTable, entries);

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> WearApkLocator.findEmbeddedWearApkPaths(moduleSplit));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "The wear APK description file 'res/xml/wearable_app_desc.xml' could not be parsed.");
  }

  @Test
  public void embeddedWearApkNotAtDesignedLocation_throws() {
    ResourceTable resourceTable = buildResourceTableForEmbeddedWearApk(wearableApkPath);
    AndroidManifest androidManifest = buildAndroidManifestForWearApk(resourceTable);
    ImmutableList<ModuleEntry> entries =
        ImmutableList.of(
            createModuleEntryForFile(
                WEAR_DESC_XML_RES_PATH,
                createEmbeddedWearableAppXmlDescription(WEAR_APK_RES_NAME).toByteArray()));

    ModuleSplit moduleSplit = createModuleSplit(androidManifest, resourceTable, entries);

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> WearApkLocator.findEmbeddedWearApkPaths(moduleSplit));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("Wear APK expected at location 'res/raw/wear.apk' but was not found.");
  }

  private ResourceTable buildResourceTableForEmbeddedWearApk(ZipPath wearableApkPath) {
    return new ResourceTableBuilder()
        .addPackage(PACKAGE_NAME)
        .addXmlResource(WEAR_DESC_XML_RES_NAME, WEAR_DESC_XML_RES_PATH)
        .addFileResource(RAW_TYPE, WEAR_APK_RES_NAME, wearableApkPath.toString())
        .build();
  }

  private ResourceTable buildResourceTableForUnbundledWearApk() {
    return new ResourceTableBuilder()
        .addPackage(PACKAGE_NAME)
        .addXmlResource(WEAR_DESC_XML_RES_NAME, WEAR_DESC_XML_RES_PATH)
        .build();
  }

  private AndroidManifest buildAndroidManifestForWearApk(ResourceTable resourceTable) {
    return AndroidManifest.create(
        androidManifest(
            PACKAGE_NAME,
            withMetadataResource(
                WEAR_APK_1_0_METADATA_KEY,
                ResourcesUtils.resolveResourceId(
                        resourceTable, PACKAGE_NAME, XML_TYPE, WEAR_DESC_XML_RES_NAME)
                    .get())));
  }

  private ImmutableList<ModuleEntry> buildEntriesForEmbeddedWearApk(ZipPath wearableApkPath) {
    return ImmutableList.of(
        createModuleEntryForFile(
            wearableApkPath.toString(), TestData.readBytes("testdata/apk/com.test.app.apk")),
        createModuleEntryForFile(
            WEAR_DESC_XML_RES_PATH,
            createEmbeddedWearableAppXmlDescription(WEAR_APK_RES_NAME).toByteArray()));
  }

  private ImmutableList<ModuleEntry> buildEntriesForUnbundledWearApk() {
    return ImmutableList.of(
        createModuleEntryForFile(
            WEAR_DESC_XML_RES_PATH, createUnbundledWearableAppXmlDescription().toByteArray()));
  }

  private static ModuleSplit createModuleSplit(
      AndroidManifest androidManifest,
      ResourceTable resourceTable,
      ImmutableList<ModuleEntry> entries) {
    return ModuleSplit.builder()
        .setAndroidManifest(androidManifest)
        .setResourceTable(resourceTable)
        .setEntries(entries)
        .setModuleName(BundleModuleName.BASE_MODULE_NAME)
        .setApkTargeting(ApkTargeting.getDefaultInstance())
        .setVariantTargeting(VariantTargeting.getDefaultInstance())
        .setMasterSplit(true)
        .build();
  }

  /**
   * Equivalent to the following XML:
   *
   * <pre>
   * <wearableApp package=PACKAGE_NAME>
   *   <rawPathResId>{rawPathResIdValue}</rawPathResId>
   * </wearableApp>
   * </pre>
   */
  private static XmlNode createEmbeddedWearableAppXmlDescription(String rawPathResIdValue) {
    return createWearableAppXmlDescription(
        XmlNode.newBuilder()
            .setElement(
                XmlElement.newBuilder()
                    .setName("rawPathResId")
                    .addChild(XmlNode.newBuilder().setText(rawPathResIdValue)))
            .build());
  }

  /**
   * Equivalent to the following XML:
   *
   * <pre>
   * <wearableApp package=PACKAGE_NAME>
   *   <unbundled></unbundled>
   * </wearableApp>
   * </pre>
   */
  private static XmlNode createUnbundledWearableAppXmlDescription() {
    return createWearableAppXmlDescription(
        XmlNode.newBuilder().setElement(XmlElement.newBuilder().setName("unbundled")).build());
  }

  private static XmlNode createWearableAppXmlDescription(XmlNode child) {
    return XmlNode.newBuilder()
        .setElement(
            XmlElement.newBuilder()
                .setName("wearableApp")
                .addAttribute(XmlAttribute.newBuilder().setName("package").setValue(PACKAGE_NAME))
                .addChild(child))
        .build();
  }

  private static Value newStringValue(String value) {
    return Value.newBuilder()
        .setItem(Item.newBuilder().setStr(Resources.String.newBuilder().setValue(value)))
        .build();
  }
}
