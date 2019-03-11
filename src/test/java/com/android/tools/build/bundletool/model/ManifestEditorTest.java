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

import static com.android.tools.build.bundletool.model.AndroidManifest.ACTIVITY_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.HAS_CODE_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.IS_FEATURE_SPLIT_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.MAX_SDK_VERSION_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.MIN_SDK_VERSION_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.NAME_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.NAME_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.PROVIDER_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SERVICE_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SPLIT_NAME_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SPLIT_NAME_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.TARGET_SANDBOX_VERSION_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.VALUE_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.VERSION_CODE_RESOURCE_ID;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMainActivity;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withOnDemandAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitNameActivity;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitNameProvider;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitNameService;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.xmlAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.xmlBooleanAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.xmlDecimalIntegerAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.xmlElement;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.xmlNode;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.aapt.Resources.XmlAttribute;
import com.android.aapt.Resources.XmlElement;
import com.android.aapt.Resources.XmlNode;
import com.android.tools.build.bundletool.TestData;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoAttribute;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.protobuf.TextFormat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ManifestEditor}. */
@RunWith(JUnit4.class)
public class ManifestEditorTest {

  private static final String ANDROID_NAMESPACE_URI = "http://schemas.android.com/apk/res/android";

  @Test
  public void setMinSdkVersion_nonExistingElement_created() throws Exception {
    AndroidManifest androidManifest = AndroidManifest.create(xmlNode(xmlElement("manifest")));

    AndroidManifest editedManifest = androidManifest.toEditor().setMinSdkVersion(123).save();

    XmlNode editedManifestRoot = editedManifest.getManifestRoot().getProto();
    assertThat(editedManifestRoot.hasElement()).isTrue();
    XmlElement manifestElement = editedManifestRoot.getElement();
    assertThat(manifestElement.getName()).isEqualTo("manifest");
    assertThat(manifestElement.getChildList())
        .containsExactly(
            xmlNode(
                xmlElement(
                    "uses-sdk",
                    xmlDecimalIntegerAttribute(
                        ANDROID_NAMESPACE_URI,
                        "minSdkVersion",
                        MIN_SDK_VERSION_RESOURCE_ID,
                        123))));
  }

  @Test
  public void setMaxSdkVersion_nonExistingAttribute_created() throws Exception {
    AndroidManifest androidManifest =
        AndroidManifest.create(xmlNode(xmlElement("manifest", xmlNode(xmlElement("uses-sdk")))));

    AndroidManifest editedManifest = androidManifest.toEditor().setMaxSdkVersion(123).save();

    XmlNode editedManifestRoot = editedManifest.getManifestRoot().getProto();
    assertThat(editedManifestRoot.hasElement()).isTrue();
    XmlElement manifestElement = editedManifestRoot.getElement();
    assertThat(manifestElement.getName()).isEqualTo("manifest");
    assertThat(manifestElement.getChildList())
        .containsExactly(
            xmlNode(
                xmlElement(
                    "uses-sdk",
                    xmlDecimalIntegerAttribute(
                        ANDROID_NAMESPACE_URI,
                        "maxSdkVersion",
                        MAX_SDK_VERSION_RESOURCE_ID,
                        123))));
  }

  @Test
  public void setMinSdkVersion_existingAttribute_adjusted() throws Exception {
    AndroidManifest androidManifest =
        AndroidManifest.create(
            xmlNode(
                xmlElement(
                    "manifest",
                    xmlNode(
                        xmlElement(
                            "uses-sdk",
                            xmlDecimalIntegerAttribute(
                                ANDROID_NAMESPACE_URI,
                                "minSdkVersion",
                                MIN_SDK_VERSION_RESOURCE_ID,
                                1))))));

    AndroidManifest editedManifest = androidManifest.toEditor().setMinSdkVersion(123).save();

    XmlNode editedManifestRoot = editedManifest.getManifestRoot().getProto();
    assertThat(editedManifestRoot.hasElement()).isTrue();
    XmlElement manifestElement = editedManifestRoot.getElement();
    assertThat(manifestElement.getName()).isEqualTo("manifest");
    assertThat(manifestElement.getChildList())
        .containsExactly(
            xmlNode(
                xmlElement(
                    "uses-sdk",
                    xmlDecimalIntegerAttribute(
                        ANDROID_NAMESPACE_URI,
                        "minSdkVersion",
                        MIN_SDK_VERSION_RESOURCE_ID,
                        123))));
  }

  @Test
  public void doNotSetFeatureSplit_forBaseSplit() throws Exception {
    AndroidManifest androidManifest =
        AndroidManifest.create(xmlNode(xmlElement("manifest", xmlNode(xmlElement("application")))));
    AndroidManifest editedManifest =
        androidManifest.toEditor().setSplitIdForFeatureSplit("").save();

    XmlNode editedManifestRoot = editedManifest.getManifestRoot().getProto();
    assertThat(editedManifestRoot.hasElement()).isTrue();
    XmlElement manifestElement = editedManifestRoot.getElement();
    assertThat(manifestElement.getName()).isEqualTo("manifest");
    assertThat(manifestElement.getChildList()).containsExactly(xmlNode(xmlElement("application")));
    // no split attribute and no isFeatureSplit attribute
    assertThat(manifestElement.getAttributeList()).isEmpty();
  }

  @Test
  public void setFeatureSplit_forNonBaseSplit() throws Exception {
    AndroidManifest androidManifest =
        AndroidManifest.create(xmlNode(xmlElement("manifest", xmlNode(xmlElement("application")))));
    AndroidManifest editedManifest =
        androidManifest.toEditor().setSplitIdForFeatureSplit("feature1").save();

    XmlNode manifestRoot = editedManifest.getManifestRoot().getProto();
    assertThat(manifestRoot.hasElement()).isTrue();
    XmlElement manifestElement = manifestRoot.getElement();
    assertThat(manifestElement.getName()).isEqualTo("manifest");
    assertThat(manifestElement.getChildList()).containsExactly(xmlNode(xmlElement("application")));
    assertThat(manifestElement.getAttributeList())
        .containsExactly(
            xmlBooleanAttribute(
                ANDROID_NAMESPACE_URI, "isFeatureSplit", IS_FEATURE_SPLIT_RESOURCE_ID, true),
            xmlAttribute("split", "feature1"));
  }

  /** Tests regression where we didn't handle properly manifests with already populated split id. */
  @Test
  public void setFeatureSplit_forDynamicSplit_duplicateSplitId() throws Exception {
    AndroidManifest androidManifest =
        AndroidManifest.create(
            xmlNode(
                xmlElement(
                    "manifest",
                    ImmutableList.of(
                        xmlAttribute("split", "feature1"),
                        xmlAttribute("package", "com.test.app"),
                        xmlAttribute("split", "feature1")),
                    xmlNode(xmlElement("application")))));
    AndroidManifest editedManifest =
        androidManifest.toEditor().setSplitIdForFeatureSplit("feature1").save();

    XmlNode manifestRoot = editedManifest.getManifestRoot().getProto();
    assertThat(manifestRoot.hasElement()).isTrue();
    XmlElement manifestElement = manifestRoot.getElement();
    assertThat(manifestElement.getName()).isEqualTo("manifest");
    assertThat(manifestElement.getChildList()).containsExactly(xmlNode(xmlElement("application")));
    assertThat(manifestElement.getAttributeList())
        .containsExactly(
            xmlBooleanAttribute(
                ANDROID_NAMESPACE_URI, "isFeatureSplit", IS_FEATURE_SPLIT_RESOURCE_ID, true),
            xmlAttribute("split", "feature1"),
            xmlAttribute("package", "com.test.app"),
            xmlAttribute("split", "feature1"));
  }

  @Test
  public void removeSplitAndIsFeatureSplit_forBaseSplit() throws Exception {
    AndroidManifest androidManifest =
        AndroidManifest.create(
            xmlNode(
                xmlElement(
                    "manifest",
                    ImmutableList.of(
                        xmlAttribute("split", "differentSplit"),
                        xmlBooleanAttribute(
                            ANDROID_NAMESPACE_URI,
                            "isFeatureSplit",
                            IS_FEATURE_SPLIT_RESOURCE_ID,
                            true)),
                    xmlNode(xmlElement("application")))));

    AndroidManifest editedManifest =
        androidManifest.toEditor().setSplitIdForFeatureSplit("").save();

    XmlNode manifestRoot = editedManifest.getManifestRoot().getProto();
    assertThat(manifestRoot.hasElement()).isTrue();
    XmlElement manifestElement = manifestRoot.getElement();
    assertThat(manifestElement.getName()).isEqualTo("manifest");
    assertThat(manifestElement.getChildList()).containsExactly(xmlNode(xmlElement("application")));
    assertThat(manifestElement.getAttributeList()).isEmpty();
  }

  @Test
  public void setFeatureSplitWithExistingSplit_forNonBaseSplit() throws Exception {
    AndroidManifest androidManifest =
        AndroidManifest.create(
            xmlNode(
                xmlElement(
                    "manifest",
                    xmlAttribute("split", "differentSplit"),
                    xmlNode(xmlElement("application")))));

    AndroidManifest editedManifest =
        androidManifest.toEditor().setSplitIdForFeatureSplit("feature1").save();

    XmlNode manifestRoot = editedManifest.getManifestRoot().getProto();
    assertThat(manifestRoot.hasElement()).isTrue();
    XmlElement manifestElement = manifestRoot.getElement();
    assertThat(manifestElement.getName()).isEqualTo("manifest");
    assertThat(manifestElement.getChildList()).containsExactly(xmlNode(xmlElement("application")));
    assertThat(manifestElement.getAttributeList())
        .containsExactly(
            xmlBooleanAttribute(
                ANDROID_NAMESPACE_URI, "isFeatureSplit", IS_FEATURE_SPLIT_RESOURCE_ID, true),
            xmlAttribute("split", "feature1"));
  }

  @Test
  public void setFeatureSplit_isFeatureAttributePresent() throws Exception {
    AndroidManifest androidManifest =
        AndroidManifest.create(
            xmlNode(
                xmlElement(
                    "manifest",
                    xmlBooleanAttribute(
                        ANDROID_NAMESPACE_URI,
                        "isFeatureSplit",
                        IS_FEATURE_SPLIT_RESOURCE_ID,
                        false),
                    xmlNode(xmlElement("application")))));
    AndroidManifest editedManifest =
        androidManifest.toEditor().setSplitIdForFeatureSplit("feature1").save();

    XmlNode manifestRoot = editedManifest.getManifestRoot().getProto();
    assertThat(manifestRoot.hasElement()).isTrue();
    XmlElement manifestElement = manifestRoot.getElement();
    assertThat(manifestElement.getName()).isEqualTo("manifest");
    assertThat(manifestElement.getChildList()).containsExactly(xmlNode(xmlElement("application")));
    assertThat(manifestElement.getAttributeList())
        .containsExactly(
            xmlBooleanAttribute(
                ANDROID_NAMESPACE_URI, "isFeatureSplit", IS_FEATURE_SPLIT_RESOURCE_ID, true),
            xmlAttribute("split", "feature1"));
  }

  @Test
  public void setFeatureSplit_isOnDemandAttributeCopied() throws Exception {
    AndroidManifest manifest =
        AndroidManifest.create(androidManifest("com.test.app", withOnDemandAttribute(true)));
    assertThat(manifest.getOnDemandAttribute().map(XmlProtoAttribute::getValueAsBoolean))
        .hasValue(true);

    AndroidManifest editedManifest =
        manifest.toEditor().setSplitIdForFeatureSplit("feature1").save();
    assertThat(editedManifest.getOnDemandAttribute().map(XmlProtoAttribute::getValueAsBoolean))
        .hasValue(true);
  }

  @Test
  public void setHasCode_true() throws Exception {
    AndroidManifest androidManifest =
        AndroidManifest.create(xmlNode(xmlElement("manifest", xmlNode(xmlElement("application")))));
    AndroidManifest editedManifest = androidManifest.toEditor().setHasCode(true).save();

    XmlNode manifestRoot = editedManifest.getManifestRoot().getProto();
    assertThat(manifestRoot.hasElement()).isTrue();
    XmlElement manifestElement = manifestRoot.getElement();
    assertThat(manifestElement.getName()).isEqualTo("manifest");

    assertThat(manifestElement.getChildCount()).isEqualTo(1);
    XmlNode applicationNode = manifestElement.getChild(0);
    assertThat(applicationNode.hasElement()).isTrue();
    XmlElement applicationElement = manifestElement.getChild(0).getElement();
    assertThat(applicationElement.getAttributeList())
        .containsExactly(
            xmlBooleanAttribute(ANDROID_NAMESPACE_URI, "hasCode", HAS_CODE_RESOURCE_ID, true));
    assertThat(applicationElement.getChildList()).isEmpty();
  }

  @Test
  public void setHasCode_false() {
    AndroidManifest androidManifest =
        AndroidManifest.create(xmlNode(xmlElement("manifest", xmlNode(xmlElement("application")))));
    AndroidManifest editedManifest = androidManifest.toEditor().setHasCode(false).save();

    XmlNode manifestRoot = editedManifest.getManifestRoot().getProto();
    assertThat(manifestRoot.hasElement()).isTrue();
    XmlElement manifestElement = manifestRoot.getElement();
    assertThat(manifestElement.getName()).isEqualTo("manifest");

    assertThat(manifestElement.getChildCount()).isEqualTo(1);
    XmlNode applicationNode = manifestElement.getChild(0);
    assertThat(applicationNode.hasElement()).isTrue();
    XmlElement applicationElement = manifestElement.getChild(0).getElement();
    assertThat(applicationElement.getAttributeList())
        .containsExactly(
            xmlBooleanAttribute(ANDROID_NAMESPACE_URI, "hasCode", HAS_CODE_RESOURCE_ID, false));
    assertThat(applicationElement.getChildList()).isEmpty();
  }

  @Test
  public void setPackage() {
    AndroidManifest androidManifest =
        AndroidManifest.create(xmlNode(xmlElement("manifest", xmlNode(xmlElement("application")))));
    AndroidManifest editedManifest = androidManifest.toEditor().setPackage("com.test.app").save();

    XmlNode manifestRoot = editedManifest.getManifestRoot().getProto();
    assertThat(manifestRoot.hasElement()).isTrue();
    XmlElement manifestElement = manifestRoot.getElement();
    assertThat(manifestElement.getName()).isEqualTo("manifest");
    assertThat(manifestElement.getAttributeList())
        .containsExactly(xmlAttribute("package", "com.test.app"));
  }

  @Test
  public void setVersionCode() {
    AndroidManifest androidManifest =
        AndroidManifest.create(xmlNode(xmlElement("manifest", xmlNode(xmlElement("application")))));
    AndroidManifest editedManifest = androidManifest.toEditor().setVersionCode(123).save();

    XmlNode manifestRoot = editedManifest.getManifestRoot().getProto();
    assertThat(manifestRoot.hasElement()).isTrue();
    XmlElement manifestElement = manifestRoot.getElement();
    assertThat(manifestElement.getName()).isEqualTo("manifest");
    assertThat(manifestElement.getAttributeList())
        .containsExactly(
            xmlDecimalIntegerAttribute(
                ANDROID_NAMESPACE_URI, "versionCode", VERSION_CODE_RESOURCE_ID, 123));
  }

  @Test
  public void setConfigForSplit() {
    AndroidManifest androidManifest =
        AndroidManifest.create(xmlNode(xmlElement("manifest", xmlNode(xmlElement("application")))));
    AndroidManifest editedManifest =
        androidManifest.toEditor().setConfigForSplit("feature1").save();

    XmlNode manifestRoot = editedManifest.getManifestRoot().getProto();
    assertThat(manifestRoot.hasElement()).isTrue();
    XmlElement manifestElement = manifestRoot.getElement();
    assertThat(manifestElement.getName()).isEqualTo("manifest");
    assertThat(manifestElement.getAttributeList())
        .containsExactly(xmlAttribute("configForSplit", "feature1"));
  }

  @Test
  public void setSplitId() {
    AndroidManifest androidManifest =
        AndroidManifest.create(xmlNode(xmlElement("manifest", xmlNode(xmlElement("application")))));
    AndroidManifest editedManifest =
        androidManifest.toEditor().setSplitId("feature1.config.x86").save();

    XmlNode manifestRoot = editedManifest.getManifestRoot().getProto();
    assertThat(manifestRoot.hasElement()).isTrue();
    XmlElement manifestElement = manifestRoot.getElement();
    assertThat(manifestElement.getName()).isEqualTo("manifest");
    assertThat(manifestElement.getAttributeList())
        .containsExactly(xmlAttribute("split", "feature1.config.x86"));
  }

  /** Tests the whole process of editing manifest to catch any unintended changes. */
  @Test
  public void complexManifest_featureSplit() throws Exception {
    XmlNode.Builder xmlNodeBuilder = XmlNode.newBuilder();
    TextFormat.merge(TestData.openReader("testdata/manifest/manifest1.textpb"), xmlNodeBuilder);
    AndroidManifest androidManifest = AndroidManifest.create(xmlNodeBuilder.build());

    XmlNode.Builder expectedXmlNodeBuilder = XmlNode.newBuilder();
    TextFormat.merge(
        TestData.openReader("testdata/manifest/feature_split_manifest1.textpb"),
        expectedXmlNodeBuilder);

    XmlNode generatedManifest =
        androidManifest
            .toEditor()
            .setSplitIdForFeatureSplit("testModule")
            .save()
            .getManifestRoot()
            .getProto();
    assertThat(generatedManifest).isEqualTo(expectedXmlNodeBuilder.build());
  }

  @Test
  public void setExtractNativeLibsValue() throws Exception {
    AndroidManifest manifest = AndroidManifest.create(androidManifest("com.test.app"));

    AndroidManifest editedManifest = manifest.toEditor().setExtractNativeLibsValue(false).save();
    assertThat(editedManifest.getExtractNativeLibsValue()).hasValue(false);

    editedManifest = manifest.toEditor().setExtractNativeLibsValue(true).save();
    assertThat(editedManifest.getExtractNativeLibsValue()).hasValue(true);
  }

  @Test
  public void setFusedModuleNames() throws Exception {
    AndroidManifest androidManifest =
        AndroidManifest.create(xmlNode(xmlElement("manifest", xmlNode(xmlElement("application")))));

    AndroidManifest editedManifest =
        androidManifest.toEditor().setFusedModuleNames(ImmutableList.of("base", "feature")).save();

    assertOnlyMetadataElement(
        editedManifest,
        "com.android.dynamic.apk.fused.modules",
        xmlAttribute(ANDROID_NAMESPACE_URI, "value", VALUE_RESOURCE_ID, "base,feature"));
  }

  @Test
  public void setSplitsRequired() throws Exception {
    AndroidManifest androidManifest =
        AndroidManifest.create(xmlNode(xmlElement("manifest", xmlNode(xmlElement("application")))));

    AndroidManifest editedManifest = androidManifest.toEditor().setSplitsRequired(true).save();

    assertOnlyMetadataElement(
        editedManifest,
        "com.android.vending.splits.required",
        xmlBooleanAttribute(ANDROID_NAMESPACE_URI, "value", VALUE_RESOURCE_ID, true));
  }

  @Test
  public void setSplitsRequired_idempotent() throws Exception {
    AndroidManifest androidManifest =
        AndroidManifest.create(xmlNode(xmlElement("manifest", xmlNode(xmlElement("application")))));

    AndroidManifest editedManifest =
        androidManifest.toEditor().setSplitsRequired(true).setSplitsRequired(true).save();

    assertOnlyMetadataElement(
        editedManifest,
        "com.android.vending.splits.required",
        xmlBooleanAttribute(ANDROID_NAMESPACE_URI, "value", VALUE_RESOURCE_ID, true));
  }

  @Test
  public void setSplitsRequired_lastInvocationWins() throws Exception {
    AndroidManifest androidManifest =
        AndroidManifest.create(xmlNode(xmlElement("manifest", xmlNode(xmlElement("application")))));

    AndroidManifest editedManifest =
        androidManifest.toEditor().setSplitsRequired(true).setSplitsRequired(false).save();

    assertOnlyMetadataElement(
        editedManifest,
        "com.android.vending.splits.required",
        xmlBooleanAttribute(ANDROID_NAMESPACE_URI, "value", VALUE_RESOURCE_ID, false));
  }

  @Test
  public void setTargetSandboxVersion() {
    AndroidManifest androidManifest =
        AndroidManifest.create(xmlNode(xmlElement("manifest", xmlNode(xmlElement("application")))));
    AndroidManifest editedManifest = androidManifest.toEditor().setTargetSandboxVersion(2).save();

    XmlNode manifestRoot = editedManifest.getManifestRoot().getProto();
    assertThat(manifestRoot.hasElement()).isTrue();
    XmlElement manifestElement = manifestRoot.getElement();
    assertThat(manifestElement.getName()).isEqualTo("manifest");
    assertThat(manifestElement.getAttributeList())
        .containsExactly(
            xmlDecimalIntegerAttribute(
                ANDROID_NAMESPACE_URI,
                "targetSandboxVersion",
                TARGET_SANDBOX_VERSION_RESOURCE_ID,
                2));
  }

  @Test
  public void removeSplitNameActivity() {
    AndroidManifest manifest =
        AndroidManifest.create(
            androidManifest(
                "com.test.app",
                withMainActivity("MainActivity"),
                withSplitNameActivity("FooActivity", "foo")));
    AndroidManifest editedManifest = manifest.toEditor().removeSplitName().save();

    ImmutableList<XmlElement> activities =
        editedManifest
            .getManifestElement()
            .getChildElement("application")
            .getChildrenElements(ACTIVITY_ELEMENT_NAME)
            .map(XmlProtoElement::getProto)
            .collect(toImmutableList());
    assertThat(activities).hasSize(2);
    XmlElement activityElement = activities.get(1);
    assertThat(activityElement.getAttributeList())
        .containsExactly(
            xmlAttribute(
                ANDROID_NAMESPACE_URI, NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID, "FooActivity"));
  }

  @Test
  public void removeSplitNameService() {
    AndroidManifest manifest =
        AndroidManifest.create(
            androidManifest("com.test.app", withSplitNameService("FooService", "foo")));
    AndroidManifest editedManifest = manifest.toEditor().removeSplitName().save();

    ImmutableList<XmlElement> services =
        editedManifest
            .getManifestElement()
            .getChildElement("application")
            .getChildrenElements(SERVICE_ELEMENT_NAME)
            .map(XmlProtoElement::getProto)
            .collect(toImmutableList());
    assertThat(services).hasSize(1);
    XmlElement serviceElement = Iterables.getOnlyElement(services);
    assertThat(serviceElement.getAttributeList())
        .containsExactly(
            xmlAttribute(
                ANDROID_NAMESPACE_URI, NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID, "FooService"));
  }

  @Test
  public void removeSplitNameProvider() {
    AndroidManifest manifest =
        AndroidManifest.create(
            androidManifest("com.test.app", withSplitNameProvider("FooProvider", "foo")));
    AndroidManifest editedManifest = manifest.toEditor().removeSplitName().save();

    ImmutableList<XmlElement> providers =
        editedManifest
            .getManifestElement()
            .getChildElement("application")
            .getChildrenElements(PROVIDER_ELEMENT_NAME)
            .map(XmlProtoElement::getProto)
            .collect(toImmutableList());
    assertThat(providers).hasSize(1);
    XmlElement providerElement = Iterables.getOnlyElement(providers);
    assertThat(providerElement.getAttributeList())
        .containsExactly(
            xmlAttribute(
                ANDROID_NAMESPACE_URI, NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID, "FooProvider"));
  }

  @Test
  public void removeUnknownSplits() {
    AndroidManifest manifest =
        AndroidManifest.create(
            androidManifest("com.test.app", withSplitNameProvider("FooProvider", "foo")));
    AndroidManifest editedManifest =
        manifest.toEditor().removeUnknownSplitComponents(ImmutableSet.of("bar")).save();

    ImmutableList<XmlElement> providers =
        editedManifest
            .getManifestElement()
            .getChildElement("application")
            .getChildrenElements(PROVIDER_ELEMENT_NAME)
            .map(XmlProtoElement::getProto)
            .collect(toImmutableList());
    assertThat(providers).isEmpty();
  }

  @Test
  public void removeUnknownSplits_keepsKnownSplits() {
    AndroidManifest manifest =
        AndroidManifest.create(
            androidManifest("com.test.app", withSplitNameProvider("FooProvider", "foo")));
    AndroidManifest editedManifest =
        manifest.toEditor().removeUnknownSplitComponents(ImmutableSet.of("foo")).save();

    ImmutableList<XmlElement> providers =
        editedManifest
            .getManifestElement()
            .getChildElement("application")
            .getChildrenElements(PROVIDER_ELEMENT_NAME)
            .map(XmlProtoElement::getProto)
            .collect(toImmutableList());
    assertThat(providers).hasSize(1);
    XmlElement providerElement = Iterables.getOnlyElement(providers);
    assertThat(providerElement.getAttributeList())
        .containsExactly(
            xmlAttribute(
                ANDROID_NAMESPACE_URI, NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID, "FooProvider"),
            xmlAttribute(
                ANDROID_NAMESPACE_URI, SPLIT_NAME_ATTRIBUTE_NAME, SPLIT_NAME_RESOURCE_ID, "foo"));
  }

  @Test
  public void removeUnknownSplits_keepsBaseSplits() {
    AndroidManifest manifest =
        AndroidManifest.create(androidManifest("com.test.app", withMainActivity("MainActivity")));
    AndroidManifest editedManifest =
        manifest.toEditor().removeUnknownSplitComponents(ImmutableSet.of()).save();

    ImmutableList<XmlElement> activities =
        editedManifest
            .getManifestElement()
            .getChildElement("application")
            .getChildrenElements(ACTIVITY_ELEMENT_NAME)
            .map(XmlProtoElement::getProto)
            .collect(toImmutableList());
    assertThat(activities).hasSize(1);
  }

  @Test
  public void addMetadataString() {
    AndroidManifest androidManifest =
        AndroidManifest.create(xmlNode(xmlElement("manifest", xmlNode(xmlElement("application")))));
    AndroidManifest editedManifest =
        androidManifest.toEditor().addMetaDataString("hello", "world").save();

    assertThat(editedManifest.getMetadataValue("hello")).hasValue("world");
  }

  @Test
  public void addMetadataInteger() {
    AndroidManifest androidManifest =
        AndroidManifest.create(xmlNode(xmlElement("manifest", xmlNode(xmlElement("application")))));
    AndroidManifest editedManifest =
        androidManifest.toEditor().addMetaDataInteger("hello", 123).save();

    assertThat(editedManifest.getMetadataValueAsInteger("hello")).hasValue(123);
  }

  @Test
  public void addMetadataResourceReference() {
    AndroidManifest androidManifest =
        AndroidManifest.create(xmlNode(xmlElement("manifest", xmlNode(xmlElement("application")))));
    AndroidManifest editedManifest =
        androidManifest.toEditor().addMetaDataResourceId("hello", 123).save();

    assertThat(editedManifest.getMetadataResourceId("hello")).hasValue(123);
  }

  private static void assertOnlyMetadataElement(
      AndroidManifest manifest, String name, XmlAttribute valueAttr) {
    XmlNode manifestRoot = manifest.getManifestRoot().getProto();
    XmlElement manifestElement = manifestRoot.getElement();
    assertThat(manifestElement.getName()).isEqualTo("manifest");
    assertThat(manifestElement.getChildCount()).isEqualTo(1);
    XmlNode applicationNode = manifestElement.getChild(0);
    XmlElement applicationElement = applicationNode.getElement();
    assertThat(applicationElement.getName()).isEqualTo("application");
    assertThat(applicationElement.getChildCount()).isEqualTo(1);
    XmlNode metadataNode = applicationElement.getChild(0);
    XmlElement metadataElement = metadataNode.getElement();
    assertThat(metadataElement.getName()).isEqualTo("meta-data");
    assertThat(metadataElement.getAttributeList())
        .containsExactly(
            xmlAttribute(ANDROID_NAMESPACE_URI, "name", NAME_RESOURCE_ID, name), valueAttr);
  }
}
