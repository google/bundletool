/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.build.bundletool.splitters;

import static com.android.tools.build.bundletool.model.AndroidManifest.ANDROID_NAMESPACE_URI;
import static com.android.tools.build.bundletool.model.AndroidManifest.NO_NAMESPACE_URI;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.xmlCompiledItemAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.xmlElement;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.xmlNode;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.xmlResourceReferenceAttribute;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.entry;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.fileReference;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.pkg;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.reference;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.resourceTable;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.type;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.value;
import static com.google.common.truth.Truth.assertThat;

import com.android.aapt.ConfigurationOuterClass.Configuration;
import com.android.aapt.Resources.Attribute;
import com.android.aapt.Resources.Attribute.Symbol;
import com.android.aapt.Resources.CompoundValue;
import com.android.aapt.Resources.ConfigValue;
import com.android.aapt.Resources.FileReference;
import com.android.aapt.Resources.Id;
import com.android.aapt.Resources.Item;
import com.android.aapt.Resources.Primitive;
import com.android.aapt.Resources.RawString;
import com.android.aapt.Resources.Reference;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.String;
import com.android.aapt.Resources.Style;
import com.android.aapt.Resources.StyledString;
import com.android.aapt.Resources.Value;
import com.android.aapt.Resources.XmlNode;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.ResourceId;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.experimental.theories.Theories;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class ResourceAnalyzerTest {

  private static final Configuration DEFAULT_CONFIG = Configuration.getDefaultInstance();

  @Test
  public void emptyManifest() throws Exception {
    XmlNode manifest = androidManifest("com.app");
    ResourceTable resourceTable =
        resourceTable(
            pkg(
                0x7f,
                "com.test.app",
                type(0x01, "string", entry(0x0099, "not_referenced", value("", DEFAULT_CONFIG)))));
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base", builder -> builder.setManifest(manifest).setResourceTable(resourceTable))
            .build();

    ImmutableSet<ResourceId> resourceIds =
        new ResourceAnalyzer(appBundle).findAllAppResourcesReachableFromBaseManifest();

    assertThat(resourceIds).isEmpty();
  }

  @Test
  public void attributeWithCompiledItem_ref_resolved() throws Exception {
    XmlNode manifest =
        AndroidManifest.create(
                xmlNode(
                    xmlElement(
                        "manifest",
                        xmlNode(
                            xmlElement(
                                "application",
                                xmlResourceReferenceAttribute(
                                    ANDROID_NAMESPACE_URI,
                                    "name",
                                    /* attrResourceId= */ 0x999999,
                                    /* valueResourceId= */ 0x12345678))))))
            .getManifestRoot()
            .getProto();
    ResourceTable resourceTable =
        resourceTable(
            pkg(
                0x12,
                "com.test.app",
                type(0x34, "string", entry(0x5678, "name", value("hello", DEFAULT_CONFIG)))));
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base", builder -> builder.setManifest(manifest).setResourceTable(resourceTable))
            .build();

    ImmutableSet<ResourceId> resourceIds =
        new ResourceAnalyzer(appBundle).findAllAppResourcesReachableFromBaseManifest();

    assertThat(resourceIds).containsExactly(ResourceId.create(0x12345678));
  }

  @Test
  public void attributeWithCompiledItem_refToUnknownResourceInBase_ignored() throws Exception {
    XmlNode manifest =
        AndroidManifest.create(
                xmlNode(
                    xmlElement(
                        "manifest",
                        xmlNode(
                            xmlElement(
                                "application",
                                xmlResourceReferenceAttribute(
                                    ANDROID_NAMESPACE_URI,
                                    "name",
                                    /* attrResourceId= */ 0x999999,
                                    /* valueResourceId= */ 0x12345678))))))
            .getManifestRoot()
            .getProto();
    ResourceTable resourceTable =
        resourceTable(
            pkg(
                0x12,
                "com.test.app.feature",
                type(0x34, "string", entry(0x5678, "name", value("hello", DEFAULT_CONFIG)))));
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", builder -> builder.setManifest(manifest))
            .addModule(
                "feature",
                builder ->
                    builder
                        .setManifest(androidManifest("com.test.app.feature"))
                        .setResourceTable(resourceTable))
            .build();

    ImmutableSet<ResourceId> resourceIds =
        new ResourceAnalyzer(appBundle).findAllAppResourcesReachableFromBaseManifest();

    assertThat(resourceIds).isEmpty();
  }

  @Test
  public void attributeWithCompiledItem_nonRef_ignored() throws Exception {
    XmlNode manifest =
        AndroidManifest.create(
                xmlNode(
                    xmlElement(
                        "manifest",
                        xmlNode(
                            xmlElement(
                                "application",
                                // Attributes of all possible types, except for ref.
                                ImmutableList.of(
                                    // str
                                    xmlCompiledItemAttribute(
                                        NO_NAMESPACE_URI,
                                        "str",
                                        Item.newBuilder()
                                            .setStr(String.getDefaultInstance())
                                            .build()),
                                    // raw_str
                                    xmlCompiledItemAttribute(
                                        NO_NAMESPACE_URI,
                                        "raw_str",
                                        Item.newBuilder()
                                            .setRawStr(RawString.getDefaultInstance())
                                            .build()),
                                    // styled_str
                                    xmlCompiledItemAttribute(
                                        NO_NAMESPACE_URI,
                                        "styled_str",
                                        Item.newBuilder()
                                            .setStyledStr(StyledString.getDefaultInstance())
                                            .build()),
                                    // file
                                    xmlCompiledItemAttribute(
                                        NO_NAMESPACE_URI,
                                        "file",
                                        Item.newBuilder()
                                            .setFile(FileReference.getDefaultInstance())
                                            .build()),
                                    // id
                                    xmlCompiledItemAttribute(
                                        NO_NAMESPACE_URI,
                                        "id",
                                        Item.newBuilder().setId(Id.getDefaultInstance()).build()),
                                    // prim
                                    xmlCompiledItemAttribute(
                                        NO_NAMESPACE_URI,
                                        "prim",
                                        Item.newBuilder()
                                            .setPrim(Primitive.getDefaultInstance())
                                            .build())))))))
            .getManifestRoot()
            .getProto();
    ResourceTable resourceTable =
        resourceTable(
            pkg(
                0x0f,
                "com.test.app",
                type(0x01, "string", entry(0x0099, "not_referenced", value("", DEFAULT_CONFIG)))));
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base", builder -> builder.setManifest(manifest).setResourceTable(resourceTable))
            .build();

    ImmutableSet<ResourceId> resourceIds =
        new ResourceAnalyzer(appBundle).findAllAppResourcesReachableFromBaseManifest();

    assertThat(resourceIds).isEmpty();
  }

  @Test
  public void transitive_item_ref() throws Exception {
    // AndroidManifest --> 0x7f010001 (ref) --> 0x7f020002 (string)
    XmlNode manifest =
        AndroidManifest.create(
                xmlNode(
                    xmlElement(
                        "manifest",
                        xmlNode(
                            xmlElement(
                                "application",
                                xmlResourceReferenceAttribute(
                                    NO_NAMESPACE_URI,
                                    "some_attribute_pointing_to_a_ref",
                                    /* attrResourceId= */ 0x999999,
                                    /* valueResourceId= */ 0x7f010001))))))
            .getManifestRoot()
            .getProto();
    ResourceTable resourceTable =
        resourceTable(
            pkg(
                0x7f,
                "com.test.app",
                type(0x01, "ref", entry(0x0001, "name_ref", reference(0x7f020002, DEFAULT_CONFIG))),
                type(
                    0x02,
                    "string",
                    entry(0x0002, "name_str", value("hello", DEFAULT_CONFIG)),
                    entry(0x0099, "not_referenced", value("", DEFAULT_CONFIG)))));
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base", builder -> builder.setManifest(manifest).setResourceTable(resourceTable))
            .build();

    ImmutableSet<ResourceId> resourceIds =
        new ResourceAnalyzer(appBundle).findAllAppResourcesReachableFromBaseManifest();

    assertThat(resourceIds)
        .containsExactly(ResourceId.create(0x7f010001), ResourceId.create(0x7f020002));
  }

  @Test
  public void transitive_item_xmlFileWithResourceReference() throws Exception {
    // AndroidManifest --> 0x7f010001 (xml file) --> 0x7f020002 (string)
    XmlNode manifest =
        AndroidManifest.create(
                xmlNode(
                    xmlElement(
                        "manifest",
                        xmlNode(
                            xmlElement(
                                "application",
                                xmlResourceReferenceAttribute(
                                    NO_NAMESPACE_URI,
                                    "attr_pointing_to_xml",
                                    /* attrResourceId= */ 0x999999,
                                    /* valueResourceId= */ 0x7f010001))))))
            .getManifestRoot()
            .getProto();
    XmlNode embeddedXmlFile =
        AndroidManifest.create(
                xmlNode(
                    xmlElement(
                        "root",
                        xmlResourceReferenceAttribute(
                            ANDROID_NAMESPACE_URI,
                            "name",
                            /* attrResourceId= */ 0x999999,
                            /* valueResourceId= */ 0x7f020002))))
            .getManifestRoot()
            .getProto();
    ResourceTable resourceTable =
        resourceTable(
            pkg(
                0x7f,
                "com.test.app",
                type(
                    0x01,
                    "file",
                    entry(
                        0x0001,
                        "xml_file",
                        fileReference(
                            "res/xml/embedded.xml", FileReference.Type.PROTO_XML, DEFAULT_CONFIG))),
                type(
                    0x02,
                    "string",
                    entry(0x0002, "name_str", value("hello", DEFAULT_CONFIG)),
                    entry(0x0099, "not_referenced", value("", DEFAULT_CONFIG)))));
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .setManifest(manifest)
                        .setResourceTable(resourceTable)
                        .addFile("res/xml/embedded.xml", embeddedXmlFile.toByteArray()))
            .build();

    ImmutableSet<ResourceId> resourceIds =
        new ResourceAnalyzer(appBundle).findAllAppResourcesReachableFromBaseManifest();

    assertThat(resourceIds)
        .containsExactly(ResourceId.create(0x7f010001), ResourceId.create(0x7f020002));
  }

  @Test
  public void transitive_compoundValue_attribute() throws Exception {
    // AndroidManifest --> 0x7f01000 (attr) --> 0x7f020002 (string)
    //                                      |-> 0x7f020003 (string)

    XmlNode manifest =
        AndroidManifest.create(
                xmlNode(
                    xmlElement(
                        "manifest",
                        xmlNode(
                            xmlElement(
                                "application",
                                xmlResourceReferenceAttribute(
                                    NO_NAMESPACE_URI,
                                    "xml_attribute_confusingly_pointing_to_a_resource_attribute",
                                    /* attrResourceId= */ 0x999999,
                                    /* valueResourceId= */ 0x7f010001))))))
            .getManifestRoot()
            .getProto();
    ResourceTable resourceTable =
        resourceTable(
            pkg(
                0x7f,
                "com.test.app",
                type(
                    0x01,
                    "attr",
                    entry(
                        0x0001,
                        "some_attr",
                        compoundValueAttrWithResourceReferences(0x7f020002, 0x7f020003))),
                type(
                    0x02,
                    "string",
                    entry(0x0002, "str1", value("hello", DEFAULT_CONFIG)),
                    entry(0x0003, "str2", value("hello", DEFAULT_CONFIG)),
                    entry(0x0099, "not_referenced", value("", DEFAULT_CONFIG)))));
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base", builder -> builder.setManifest(manifest).setResourceTable(resourceTable))
            .build();

    ImmutableSet<ResourceId> resourceIds =
        new ResourceAnalyzer(appBundle).findAllAppResourcesReachableFromBaseManifest();

    assertThat(resourceIds)
        .containsExactly(
            ResourceId.create(0x7f010001),
            ResourceId.create(0x7f020002),
            ResourceId.create(0x7f020003));
  }

  @Test
  public void transitive_compoundValue_style() throws Exception {
    // AndroidManifest
    //     --> 0x7f010001 (app_theme) --> 0x7f010002 (parent_theme) --> 0x7f020001 (string)
    //                                |-> 0x7f020002 (string)
    //                                |-> 0x7f020003 (string)
    XmlNode manifest =
        AndroidManifest.create(
                xmlNode(
                    xmlElement(
                        "manifest",
                        xmlNode(
                            xmlElement(
                                "application",
                                xmlResourceReferenceAttribute(
                                    ANDROID_NAMESPACE_URI,
                                    "theme",
                                    /* attrResourceId= */ 0x999999,
                                    /* valueResourceId= */ 0x7f010001))))))
            .getManifestRoot()
            .getProto();
    ResourceTable resourceTable =
        resourceTable(
            pkg(
                0x7f,
                "com.test.app",
                type(
                    0x01,
                    "theme",
                    entry(
                        0x0001,
                        "app_theme",
                        compoundValueStyleWithResourceReferences(
                            /* parentId= */ 0x7f010002,
                            /* referencedResourceIds= */ new int[] {0x7f020002, 0x7f020003})),
                    entry(
                        0x0002,
                        "parent_theme",
                        compoundValueStyleWithResourceReferences(
                            /* parentId= */ null,
                            /* referencedResourceIds= */ new int[] {0x7f020001}))),
                type(
                    0x02,
                    "string",
                    entry(0x0001, "parent_theme_str", value("hello", DEFAULT_CONFIG)),
                    entry(0x0002, "app_theme_str1", value("hello", DEFAULT_CONFIG)),
                    entry(0x0003, "app_theme_str2", value("hello", DEFAULT_CONFIG)),
                    entry(0x0099, "not_referenced", value("", DEFAULT_CONFIG)))));
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base", builder -> builder.setManifest(manifest).setResourceTable(resourceTable))
            .build();

    ImmutableSet<ResourceId> resourceIds =
        new ResourceAnalyzer(appBundle).findAllAppResourcesReachableFromBaseManifest();

    assertThat(resourceIds)
        .containsExactly(
            ResourceId.create(0x7f010001),
            ResourceId.create(0x7f010002),
            ResourceId.create(0x7f020001),
            ResourceId.create(0x7f020002),
            ResourceId.create(0x7f020003));
  }

  @Test
  public void transitive_deepChain() throws Exception {
    // AndroidManifest
    //     --> 0x7f010001 (ref) --> 0x7f010002 (ref) --> 0x7f010003 (ref) --> 0x7f020004 (string)
    XmlNode manifest =
        AndroidManifest.create(
                xmlNode(
                    xmlElement(
                        "manifest",
                        xmlNode(
                            xmlElement(
                                "application",
                                xmlResourceReferenceAttribute(
                                    NO_NAMESPACE_URI,
                                    "attribute_pointint_to_a_ref",
                                    /* attrResourceId= */ 0x999999,
                                    /* valueResourceId= */ 0x7f010001))))))
            .getManifestRoot()
            .getProto();
    ResourceTable resourceTable =
        resourceTable(
            pkg(
                0x7f,
                "com.test.app",
                type(
                    0x01,
                    "ref",
                    entry(0x0001, "ref1", reference(0x7f010002, DEFAULT_CONFIG)),
                    entry(0x0002, "ref2", reference(0x7f010003, DEFAULT_CONFIG)),
                    entry(0x0003, "ref3", reference(0x7f020004, DEFAULT_CONFIG))),
                type(
                    0x02,
                    "string",
                    entry(0x0004, "name_str", value("hello", DEFAULT_CONFIG)),
                    entry(0x0099, "not_referenced", value("", DEFAULT_CONFIG)))));
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base", builder -> builder.setManifest(manifest).setResourceTable(resourceTable))
            .build();

    ImmutableSet<ResourceId> resourceIds =
        new ResourceAnalyzer(appBundle).findAllAppResourcesReachableFromBaseManifest();

    assertThat(resourceIds)
        .containsExactly(
            ResourceId.create(0x7f010001),
            ResourceId.create(0x7f010002),
            ResourceId.create(0x7f010003),
            ResourceId.create(0x7f020004));
  }

  @Test
  public void customManifest_filteredRefs_resolved() throws Exception {
    XmlNode baseManifest =
        AndroidManifest.create(
                xmlNode(
                    xmlElement(
                        "manifest",
                        xmlNode(
                            xmlElement(
                                "application",
                                ImmutableList.of(
                                    xmlResourceReferenceAttribute(
                                        ANDROID_NAMESPACE_URI,
                                        "name",
                                        /* attrResourceId= */ 0x999998,
                                        /* valueResourceId= */ 0x87654321),
                                    xmlResourceReferenceAttribute(
                                        ANDROID_NAMESPACE_URI,
                                        "name2",
                                        /* attrResourceId= */ 0x999999,
                                        /* valueResourceId= */ 0x12345678)))))))
            .getManifestRoot()
            .getProto();
    AndroidManifest customManifest =
        AndroidManifest.create(
            xmlNode(
                xmlElement(
                    "manifest",
                    xmlNode(
                        xmlElement(
                            "application",
                            xmlResourceReferenceAttribute(
                                ANDROID_NAMESPACE_URI,
                                "name2",
                                /* attrResourceId= */ 0x999999,
                                /* valueResourceId= */ 0x12345678))))));
    ResourceTable resourceTable =
        resourceTable(
            pkg(
                0x12,
                "com.test.app",
                type(0x34, "string", entry(0x5678, "name", value("hello", DEFAULT_CONFIG)))),
            pkg(
                0x87,
                "com.test.app",
                type(0x65, "string", entry(0x4321, "name2", value("world", DEFAULT_CONFIG)))));
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder -> builder.setManifest(baseManifest).setResourceTable(resourceTable))
            .build();

    ImmutableSet<ResourceId> resourceIds =
        new ResourceAnalyzer(appBundle)
            .findAllAppResourcesReachableFromManifest(customManifest);

    assertThat(resourceIds).containsExactly(ResourceId.create(0x12345678));
  }

  private static ConfigValue compoundValueAttrWithResourceReferences(int... referencedResourceIds) {
    Attribute.Builder attribute = Attribute.newBuilder();
    for (int referencedResourceId : referencedResourceIds) {
      attribute.addSymbol(
          Symbol.newBuilder().setName(Reference.newBuilder().setId(referencedResourceId)));
    }

    return ConfigValue.newBuilder()
        .setValue(
            Value.newBuilder().setCompoundValue(CompoundValue.newBuilder().setAttr(attribute)))
        .build();
  }

  private static ConfigValue compoundValueStyleWithResourceReferences(
      @Nullable Integer parentId, int[] referencedResourceIds) {
    Style.Builder style = Style.newBuilder();
    if (parentId != null) {
      style.setParent(Reference.newBuilder().setId(parentId));
    }
    for (int referencedResourceId : referencedResourceIds) {
      style.addEntry(
          Style.Entry.newBuilder()
              .setItem(
                  Item.newBuilder().setRef(Reference.newBuilder().setId(referencedResourceId))));
    }

    return ConfigValue.newBuilder()
        .setValue(Value.newBuilder().setCompoundValue(CompoundValue.newBuilder().setStyle(style)))
        .build();
  }
}
