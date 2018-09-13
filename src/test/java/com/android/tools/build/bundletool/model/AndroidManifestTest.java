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

import static com.android.tools.build.bundletool.model.AndroidManifest.DEBUGGABLE_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.HAS_CODE_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.IS_FEATURE_SPLIT_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.NAME_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.RESOURCE_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.VALUE_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.VERSION_CODE_RESOURCE_ID;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withFeatureCondition;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withFusingAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withLegacyFusingAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withLegacyOnDemand;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkCondition;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withOnDemand;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withTargetSandboxVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withUnsupportedCondition;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withUsesSplit;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.xmlAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.xmlBooleanAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.xmlDecimalIntegerAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.xmlElement;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.xmlNode;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.xmlResourceReferenceAttribute;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.XmlNode;
import com.android.tools.build.bundletool.TestData;
import com.android.tools.build.bundletool.exceptions.ValidationException;
import com.android.tools.build.bundletool.exceptions.manifest.ManifestFusingException.FusingMissingIncludeAttribute;
import com.android.tools.build.bundletool.exceptions.manifest.ManifestVersionException.VersionCodeMissingException;
import com.android.tools.build.bundletool.utils.xmlproto.UnexpectedAttributeTypeException;
import com.android.tools.build.bundletool.utils.xmlproto.XmlProtoAttributeBuilder;
import com.android.tools.build.bundletool.utils.xmlproto.XmlProtoElement;
import com.android.tools.build.bundletool.utils.xmlproto.XmlProtoElementBuilder;
import com.android.tools.build.bundletool.utils.xmlproto.XmlProtoNode;
import com.android.tools.build.bundletool.version.BundleToolVersion;
import com.android.tools.build.bundletool.version.Version;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.TextFormat;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link AndroidManifest}. */
@RunWith(JUnit4.class)
public class AndroidManifestTest {

  private static final String ANDROID_NAMESPACE_URI = "http://schemas.android.com/apk/res/android";
  private static final String DISTRIBUTION_NAMESPACE_URI =
      "http://schemas.android.com/apk/distribution";

  private static final Version CURRENT_VERSION = BundleToolVersion.getCurrentVersion();
  private static final Version BUNDLE_TOOL_0_3_4 = Version.of("0.3.4");
  private static final Version BUNDLE_TOOL_0_3_3 = Version.of("0.3.3");

  @Test
  public void getApplicationDebuggable_absent() {
    AndroidManifest androidManifest =
        AndroidManifest.create(xmlNode(xmlElement("manifest", xmlNode(xmlElement("application")))));
    assertThat(androidManifest.getApplicationDebuggable()).isEmpty();
    assertThat(androidManifest.getEffectiveApplicationDebuggable()).isFalse();
  }

  @Test
  public void getApplicationDebuggable_presentFalse() {
    AndroidManifest androidManifest =
        AndroidManifest.create(
            xmlNode(
                xmlElement(
                    "manifest",
                    xmlNode(
                        xmlElement(
                            "application",
                            xmlBooleanAttribute(
                                ANDROID_NAMESPACE_URI,
                                "debuggable",
                                DEBUGGABLE_RESOURCE_ID,
                                false))))));
    assertThat(androidManifest.getApplicationDebuggable()).hasValue(false);
    assertThat(androidManifest.getEffectiveApplicationDebuggable()).isFalse();
  }

  @Test
  public void getApplicationDebuggable_presentTrue() {
    AndroidManifest androidManifest =
        AndroidManifest.create(
            xmlNode(
                xmlElement(
                    "manifest",
                    xmlNode(
                        xmlElement(
                            "application",
                            xmlBooleanAttribute(
                                ANDROID_NAMESPACE_URI,
                                "debuggable",
                                DEBUGGABLE_RESOURCE_ID,
                                true))))));
    assertThat(androidManifest.getApplicationDebuggable()).hasValue(true);
    assertThat(androidManifest.getEffectiveApplicationDebuggable()).isTrue();
  }

  // There are tests only for getMinSdkVersion. The getMaxSdkVersion, getTargetSdkVersion methods
  // rely on the same underlying implementation.

  @Test
  public void getMinSdkVersion_positive() {
    AndroidManifest androidManifest =
        AndroidManifest.create(androidManifest("com.test.app", withMinSdkVersion(123)));
    assertThat(androidManifest.getMinSdkVersion()).hasValue(123);
  }

  @Test
  public void getMinSdkVersion_negative() {
    AndroidManifest androidManifest = AndroidManifest.create(androidManifest("com.test.app"));
    assertThat(androidManifest.getMinSdkVersion()).isEmpty();
  }

  @Test
  public void getTargetSandboxVersion_empty() {
    AndroidManifest androidManifest = AndroidManifest.create(androidManifest("com.test.app"));
    assertThat(androidManifest.getTargetSandboxVersion()).isEmpty();
  }

  @Test
  public void getTargetSandboxVersion_setTo2() {
    AndroidManifest androidManifest =
        AndroidManifest.create(androidManifest("com.test.app", withTargetSandboxVersion(2)));
    assertThat(androidManifest.getTargetSandboxVersion()).hasValue(2);
  }

  @Test
  public void getUsesSplits_positive() {
    AndroidManifest androidManifest =
        AndroidManifest.create(androidManifest("com.test.app", withUsesSplit("parent")));
    assertThat(androidManifest.getUsesSplits()).containsExactly("parent");
  }

  @Test
  public void getUsesSplits_negative() {
    AndroidManifest androidManifest = AndroidManifest.create(androidManifest("com.test.app"));
    assertThat(androidManifest.getUsesSplits()).isEmpty();
  }

  @Test
  public void getUsesSplits_missingNameAttribute_throws() {
    AndroidManifest androidManifest =
        AndroidManifest.create(xmlNode(xmlElement("manifest", xmlNode(xmlElement("uses-split")))));
    ValidationException exception =
        assertThrows(ValidationException.class, () -> androidManifest.getUsesSplits());
    assertThat(exception)
        .hasMessageThat()
        .contains("<uses-split> element is missing the 'android:name' attribute");
  }

  @Test
  public void hasSplitId_positive() {
    AndroidManifest androidManifest =
        AndroidManifest.create(
            xmlNode(
                xmlElement(
                    "manifest",
                    xmlAttribute("split", "config.x86"),
                    xmlNode(xmlElement("application")))));
    assertThat(androidManifest.getSplitId()).hasValue("config.x86");
  }

  @Test
  public void hasCode_negative() {
    AndroidManifest androidManifest =
        AndroidManifest.create(
            xmlNode(
                xmlElement(
                    "manifest",
                    xmlNode(
                        xmlElement(
                            "application",
                            xmlBooleanAttribute(
                                ANDROID_NAMESPACE_URI, "hasCode", HAS_CODE_RESOURCE_ID, false))))));
    assertThat(androidManifest.getHasCode()).hasValue(false);
  }

  @Test
  public void hasCode_positive() {
    AndroidManifest androidManifest =
        AndroidManifest.create(
            xmlNode(
                xmlElement(
                    "manifest",
                    xmlNode(
                        xmlElement(
                            "application",
                            xmlBooleanAttribute(
                                ANDROID_NAMESPACE_URI, "hasCode", HAS_CODE_RESOURCE_ID, true))))));
    assertThat(androidManifest.getHasCode()).hasValue(true);
  }

  @Test
  public void hasCode_emptyMeansTrue() {
    AndroidManifest androidManifest =
        AndroidManifest.create(xmlNode(xmlElement("manifest", xmlNode(xmlElement("application")))));
    assertThat(androidManifest.getHasCode()).isEmpty();
    assertThat(androidManifest.getEffectiveHasCode()).isTrue();
  }

  @Test
  public void isFeatureSplit_negative() {
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
    assertThat(androidManifest.getIsFeatureSplit()).hasValue(false);
  }

  @Test
  public void isFeatureSplit_positive() {
    AndroidManifest androidManifest =
        AndroidManifest.create(
            xmlNode(
                xmlElement(
                    "manifest",
                    xmlBooleanAttribute(
                        ANDROID_NAMESPACE_URI,
                        "isFeatureSplit",
                        IS_FEATURE_SPLIT_RESOURCE_ID,
                        true),
                    xmlNode(xmlElement("application")))));
    assertThat(androidManifest.getIsFeatureSplit()).hasValue(true);
  }

  @Test
  public void isFeatureSplit_empty() {
    AndroidManifest androidManifest =
        AndroidManifest.create(xmlNode(xmlElement("manifest", xmlNode(xmlElement("application")))));
    assertThat(androidManifest.getIsFeatureSplit()).isEmpty();
  }

  @Test
  public void getFeatureSplitId_empty() {
    AndroidManifest androidManifest =
        AndroidManifest.create(xmlNode(xmlElement("manifest", xmlNode(xmlElement("application")))));
    assertThat(androidManifest.getConfigForSplit()).isEmpty();
  }

  @Test
  public void getFeatureSplitId_present() {
    AndroidManifest androidManifest =
        AndroidManifest.create(
            xmlNode(
                xmlElement(
                    "manifest",
                    xmlAttribute("configForSplit", "feature1"),
                    xmlNode(xmlElement("application")))));
    assertThat(androidManifest.getConfigForSplit()).hasValue("feature1");
  }

  @Test
  public void getPackageName() {
    AndroidManifest androidManifest =
        AndroidManifest.create(
            xmlNode(
                xmlElement(
                    "manifest",
                    xmlAttribute("package", "com.test.app"),
                    xmlNode(xmlElement("application")))));
    assertThat(androidManifest.getPackageName()).isEqualTo("com.test.app");
  }

  @Test
  public void getVersionCode() {
    AndroidManifest androidManifest =
        AndroidManifest.create(
            xmlNode(
                xmlElement(
                    "manifest",
                    xmlDecimalIntegerAttribute(
                        ANDROID_NAMESPACE_URI, "versionCode", VERSION_CODE_RESOURCE_ID, 123),
                    xmlNode(xmlElement("application")))));
    assertThat(androidManifest.getVersionCode()).isEqualTo(123);
  }

  @Test
  public void getVersionCode_missing_throws() {
    AndroidManifest androidManifest =
        AndroidManifest.create(xmlNode(xmlElement("manifest", xmlNode(xmlElement("application")))));
    assertThrows(VersionCodeMissingException.class, () -> androidManifest.getVersionCode());
  }

  @Test
  public void getVersionCode_invalid_throws() {
    AndroidManifest androidManifest =
        AndroidManifest.create(
            xmlNode(
                xmlElement(
                    "manifest",
                    xmlAttribute(
                        ANDROID_NAMESPACE_URI, "versionCode", VERSION_CODE_RESOURCE_ID, "bad!"),
                    xmlNode(xmlElement("application")))));
    UnexpectedAttributeTypeException exception =
        assertThrows(
            UnexpectedAttributeTypeException.class, () -> androidManifest.getVersionCode());

    assertThat(exception)
        .hasMessageThat()
        .contains("Attribute 'versionCode' expected to have type 'decimal int' but found:");
  }

  @Test
  public void hasSplitId_negative() {
    AndroidManifest androidManifest =
        AndroidManifest.create(xmlNode(xmlElement("manifest", xmlNode(xmlElement("application")))));
    assertThat(androidManifest.getSplitId()).isEmpty();
  }

  @Test
  public void isOnDemandModule_notSet() {
    // From Bundletool 0.3.4 onwards.
    AndroidManifest manifest = AndroidManifest.create(androidManifest("com.test.app"));
    assertThat(manifest.isOnDemandModule(BUNDLE_TOOL_0_3_4)).isEmpty();
  }

  @Test
  public void isOnDemandModule_false_byDefault_legacy() {
    AndroidManifest manifest = AndroidManifest.create(androidManifest("com.test.app"));
    assertThat(manifest.isOnDemandModule(BUNDLE_TOOL_0_3_3)).isEmpty();
  }

  @Test
  public void isOnDemandModule_true() {
    // From Bundletool 0.3.4 onwards.
    AndroidManifest manifest =
        AndroidManifest.create(androidManifest("com.test.app", withOnDemand(true)));
    assertThat(manifest.isOnDemandModule(BUNDLE_TOOL_0_3_4)).hasValue(true);
  }

  @Test
  public void isOnDemandModule_true_legacy() {
    AndroidManifest manifest =
        AndroidManifest.create(androidManifest("com.test.app", withLegacyOnDemand(true)));
    assertThat(manifest.isOnDemandModule(BUNDLE_TOOL_0_3_3)).hasValue(true);

    AndroidManifest newManifest =
        AndroidManifest.create(androidManifest("com.test.app", withOnDemand(true)));
    assertThat(newManifest.isOnDemandModule(BUNDLE_TOOL_0_3_3)).hasValue(true);
  }

  @Test
  public void isOnDemandModule_false() {
    // From Bundletool 0.3.4 onwards.
    AndroidManifest manifest =
        AndroidManifest.create(androidManifest("com.test.app", withOnDemand(false)));
    assertThat(manifest.isOnDemandModule(BUNDLE_TOOL_0_3_4)).hasValue(false);
  }

  @Test
  public void isOnDemandModule_false_legacy() {
    AndroidManifest manifest =
        AndroidManifest.create(androidManifest("com.test.app", withLegacyOnDemand(false)));
    assertThat(manifest.isOnDemandModule(BUNDLE_TOOL_0_3_3)).hasValue(false);

    AndroidManifest newManifest =
        AndroidManifest.create(androidManifest("com.test.app", withOnDemand(false)));
    assertThat(newManifest.isOnDemandModule(BUNDLE_TOOL_0_3_3)).hasValue(false);
  }

  @Test
  public void isOnDemandModule_old_namespace_returnsEmpty() {
    // From Bundletool 0.3.4 onwards.
    AndroidManifest manifest =
        AndroidManifest.create(androidManifest("com.test.app", withLegacyOnDemand(false)));
    assertThat(manifest.isOnDemandModule(BUNDLE_TOOL_0_3_4)).isEmpty();
  }

  @Test
  public void getIsIncludedInFusing_true() {
    // From Bundletool 0.3.4 onwards.
    AndroidManifest manifest =
        AndroidManifest.create(androidManifest("com.test.app", withFusingAttribute(true)));
    assertThat(manifest.getIsModuleIncludedInFusing(BUNDLE_TOOL_0_3_4)).hasValue(true);
  }

  @Test
  public void getIsIncludedInFusing_legacy_true() {
    AndroidManifest manifest =
        AndroidManifest.create(androidManifest("com.test.app", withLegacyFusingAttribute(true)));
    assertThat(manifest.getIsModuleIncludedInFusing(BUNDLE_TOOL_0_3_3)).hasValue(true);

    AndroidManifest newManifest =
        AndroidManifest.create(androidManifest("com.test.app", withFusingAttribute(true)));
    assertThat(newManifest.getIsModuleIncludedInFusing(BUNDLE_TOOL_0_3_3)).hasValue(true);
  }

  @Test
  public void getIsIncludedInFusing_false() {
    // From Bundletool 0.3.4 onwards.
    AndroidManifest androidManifest =
        AndroidManifest.create(androidManifest("com.test.app", withFusingAttribute(false)));
    assertThat(androidManifest.getIsModuleIncludedInFusing(BUNDLE_TOOL_0_3_4)).hasValue(false);
  }

  @Test
  public void getIsIncludedInFusing_no_namespace_throws() {
    // From Bundletool 0.3.4 onwards.
    AndroidManifest androidManifest =
        AndroidManifest.create(androidManifest("com.test.app", withLegacyFusingAttribute(false)));
    assertThrows(
        FusingMissingIncludeAttribute.class,
        () -> androidManifest.getIsModuleIncludedInFusing(BUNDLE_TOOL_0_3_4));
  }

  @Test
  public void getIsIncludedInFusing_legacy_false() {
    AndroidManifest manifest =
        AndroidManifest.create(androidManifest("com.test.app", withLegacyFusingAttribute(false)));
    assertThat(manifest.getIsModuleIncludedInFusing(BUNDLE_TOOL_0_3_3)).hasValue(false);

    AndroidManifest newManifest =
        AndroidManifest.create(androidManifest("com.test.app", withFusingAttribute(false)));
    assertThat(newManifest.getIsModuleIncludedInFusing(BUNDLE_TOOL_0_3_3)).hasValue(false);
  }

  @Test
  public void getIsIncludedInFusing_missingElements_emptyOptional() {
    // From Bundletool 0.3.4 onwards.
    AndroidManifest androidManifest = AndroidManifest.create(androidManifest("com.test.app"));
    assertThat(androidManifest.getIsModuleIncludedInFusing(BUNDLE_TOOL_0_3_4)).isEmpty();
  }

  @Test
  public void getIsIncludedInFusing_missingElements_legacy_emptyOptional() {
    AndroidManifest androidManifest = AndroidManifest.create(androidManifest("com.test.app"));
    assertThat(androidManifest.getIsModuleIncludedInFusing(BUNDLE_TOOL_0_3_3)).isEmpty();
  }

  @Test
  public void getIsIncludedInFusing_missingIncludeAttribute_forBase_throws() {
    // From Bundletool 0.3.4 onwards.
    AndroidManifest androidManifest =
        AndroidManifest.create(
            xmlNode(
                xmlElement(
                    "manifest",
                    xmlNode(
                        xmlElement(
                            DISTRIBUTION_NAMESPACE_URI,
                            "module",
                            xmlNode(xmlElement(DISTRIBUTION_NAMESPACE_URI, "fusing")))))));
    FusingMissingIncludeAttribute exception =
        assertThrows(
            FusingMissingIncludeAttribute.class,
            () -> androidManifest.getIsModuleIncludedInFusing(BUNDLE_TOOL_0_3_4));
    assertThat(exception)
        .hasMessageThat()
        .contains("<fusing> element is missing the 'include' attribute");
  }

  @Test
  public void getIsIncludedInFusing_missingIncludeAttribute_forBase_legacy_throws() {
    AndroidManifest androidManifest =
        AndroidManifest.create(
            xmlNode(
                xmlElement(
                    "manifest",
                    xmlNode(
                        xmlElement(
                            DISTRIBUTION_NAMESPACE_URI,
                            "module",
                            xmlNode(xmlElement(DISTRIBUTION_NAMESPACE_URI, "fusing")))))));
    FusingMissingIncludeAttribute exception =
        assertThrows(
            FusingMissingIncludeAttribute.class,
            () -> androidManifest.getIsModuleIncludedInFusing(BUNDLE_TOOL_0_3_3));
    assertThat(exception)
        .hasMessageThat()
        .contains("<fusing> element is missing the 'include' attribute");
  }

  @Test
  public void getIsIncludedInFusing_missingIncludeAttribute_forSplit_throws() {
    // From Bundletool 0.3.4 onwards.
    AndroidManifest androidManifest =
        AndroidManifest.create(
            xmlNode(
                xmlElement(
                    "manifest",
                    xmlAttribute("split", "feature1"),
                    xmlNode(
                        xmlElement(
                            DISTRIBUTION_NAMESPACE_URI,
                            "module",
                            xmlNode(xmlElement(DISTRIBUTION_NAMESPACE_URI, "fusing")))))));
    FusingMissingIncludeAttribute exception =
        assertThrows(
            FusingMissingIncludeAttribute.class,
            () -> androidManifest.getIsModuleIncludedInFusing(BUNDLE_TOOL_0_3_4));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("<fusing> element is missing the 'include' attribute (split: 'feature1').");
  }

  @Test
  public void getIsIncludedInFusing_missingIncludeAttribute_forSplit_legacy_throws() {
    AndroidManifest androidManifest =
        AndroidManifest.create(
            xmlNode(
                xmlElement(
                    "manifest",
                    xmlAttribute("split", "feature1"),
                    xmlNode(
                        xmlElement(
                            DISTRIBUTION_NAMESPACE_URI,
                            "module",
                            xmlNode(xmlElement(DISTRIBUTION_NAMESPACE_URI, "fusing")))))));
    FusingMissingIncludeAttribute exception =
        assertThrows(
            FusingMissingIncludeAttribute.class,
            () -> androidManifest.getIsModuleIncludedInFusing(BUNDLE_TOOL_0_3_3));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("<fusing> element is missing the 'include' attribute (split: 'feature1').");
  }

  @Test
  public void getFusedModuleNames_missing() {
    AndroidManifest androidManifest =
        AndroidManifest.create(
            xmlNode(
                xmlElement(
                    "manifest",
                    xmlNode(
                        xmlElement(
                            "application",
                            xmlNode(
                                xmlElement(
                                    "meta-data",
                                    ImmutableList.of(
                                        xmlAttribute(ANDROID_NAMESPACE_URI, "name", "plain"),
                                        xmlAttribute(ANDROID_NAMESPACE_URI, "value", "v1")))),
                            metadataWithValue("via android:value", "v2"),
                            metadataWithResourceRef("via android:resource", 3))))));

    assertThat(androidManifest.getFusedModuleNames()).isEmpty();
  }

  @Test
  public void getFusedModuleNames_present() {
    AndroidManifest androidManifest =
        AndroidManifest.create(
            xmlNode(
                xmlElement(
                    "manifest",
                    xmlNode(
                        xmlElement(
                            "application",
                            metadataWithValue(
                                "com.android.dynamic.apk.fused.modules", "base,feature"))))));
    assertThat(androidManifest.getFusedModuleNames()).containsExactly("base", "feature");
  }

  @Test
  public void getFusedModuleNames_multiple_throws() {
    AndroidManifest androidManifest =
        AndroidManifest.create(
            xmlNode(
                xmlElement(
                    "manifest",
                    xmlNode(
                        xmlElement(
                            "application",
                            metadataWithValue("com.android.dynamic.apk.fused.modules", "value1"),
                            metadataWithValue(
                                "com.android.dynamic.apk.fused.modules", "value2"))))));
    ValidationException exception =
        assertThrows(ValidationException.class, () -> androidManifest.getFusedModuleNames());
    assertThat(exception).hasMessageThat().contains("multiple <meta-data> elements for key");
  }

  @Test
  public void twoEqualProtos_objectsEqual() {
    AndroidManifest androidManifest1 =
        AndroidManifest.create(
            xmlNode(
                xmlElement(
                    "manifest",
                    xmlAttribute("split", "config.mips"),
                    xmlNode(xmlElement("application")))));

    AndroidManifest androidManifest2 =
        AndroidManifest.create(
            xmlNode(
                xmlElement(
                    "manifest",
                    xmlAttribute("split", "config.mips"),
                    xmlNode(xmlElement("application")))));
    assertThat(androidManifest1).isEqualTo(androidManifest2);
    assertThat(androidManifest1.hashCode()).isEqualTo(androidManifest2.hashCode());
  }

  @Test
  public void twoDifferentProtos_objectsNotEqual() {
    AndroidManifest androidManifest1 =
        AndroidManifest.create(
            xmlNode(
                xmlElement(
                    "manifest",
                    xmlAttribute("split", "config.mips"),
                    xmlNode(xmlElement("application")))));

    AndroidManifest androidManifest2 =
        AndroidManifest.create(
            xmlNode(
                xmlElement(
                    "manifest",
                    xmlAttribute("split", "config.x86"),
                    xmlNode(xmlElement("application")))));
    assertThat(androidManifest1).isNotEqualTo(androidManifest2);
    assertThat(androidManifest1.hashCode()).isNotEqualTo(androidManifest2.hashCode());
  }

  @Test
  public void settingEmptySplitIdThrows() {
    AndroidManifest moduleManifest = AndroidManifest.create(androidManifest("com.package.test"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AndroidManifest.createForConfigSplit(
                moduleManifest.getPackageName(),
                moduleManifest.getVersionCode(),
                "",
                "feature1",
                Optional.empty()));
  }

  @Test
  public void configSplitPropertiesSet() {
    AndroidManifest moduleManifest = AndroidManifest.create(androidManifest("com.package.test"));
    AndroidManifest configManifest =
        AndroidManifest.createForConfigSplit(
            moduleManifest.getPackageName(),
            moduleManifest.getVersionCode(),
            "x86",
            "feature1",
            Optional.of(false));

    assertThat(configManifest.getPackageName()).isEqualTo("com.package.test");
    assertThat(configManifest.getVersionCode()).isEqualTo(1);
    assertThat(configManifest.getHasCode()).hasValue(false);
    assertThat(configManifest.getSplitId()).hasValue("x86");
    assertThat(configManifest.getConfigForSplit()).hasValue("feature1");
    assertThat(configManifest.isOnDemandModule(CURRENT_VERSION)).isEmpty();
    assertThat(configManifest.getIsFeatureSplit()).isEmpty();
    assertThat(configManifest.getExtractNativeLibsValue()).hasValue(false);
  }

  @Test
  public void configSplit_noExtraElementsFromModuleSplit() throws Exception {
    XmlNode.Builder xmlNodeBuilder = XmlNode.newBuilder();
    TextFormat.merge(TestData.openReader("testdata/manifest/manifest1.textpb"), xmlNodeBuilder);
    AndroidManifest androidManifest = AndroidManifest.create(xmlNodeBuilder.build());

    XmlNode.Builder expectedXmlNodeBuilder = XmlNode.newBuilder();
    TextFormat.merge(
        TestData.openReader("testdata/manifest/config_split_manifest1.textpb"),
        expectedXmlNodeBuilder);

    AndroidManifest configManifest =
        AndroidManifest.createForConfigSplit(
            androidManifest.getPackageName(),
            androidManifest.getVersionCode(),
            "testModule.config.hdpi",
            "testModule",
            Optional.empty());

    XmlProtoNode generatedManifest = configManifest.getManifestRoot();
    assertThat(generatedManifest.getProto()).isEqualTo(expectedXmlNodeBuilder.build());
  }

  @Test
  public void getMetadataResourceId_resourceAttributePresent() {
    AndroidManifest androidManifest =
        AndroidManifest.create(
            xmlNode(
                xmlElement(
                    "manifest",
                    xmlNode(
                        xmlElement("application", metadataWithResourceRef("metadata-key", 123))))));

    assertThat(androidManifest.getMetadataResourceId("metadata-key")).hasValue(123);
  }

  @Test
  public void getMetadataResourceId_resourceAttributeAbsent() {
    AndroidManifest androidManifest =
        AndroidManifest.create(
            xmlNode(
                xmlElement(
                    "manifest",
                    xmlNode(xmlElement("application", metadataWithValue("metadata-key", "123"))))));

    ValidationException exception =
        assertThrows(
            ValidationException.class, () -> androidManifest.getMetadataResourceId("metadata-key"));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Missing expected attribute 'android:resource' for <meta-data> element 'metadata-key'");
  }

  @Test
  public void getMetadataValue() {
    AndroidManifest androidManifest =
        AndroidManifest.create(
            xmlNode(
                xmlElement(
                    "manifest",
                    xmlNode(xmlElement("application", metadataWithValue("metadata-key", "123"))))));
    assertThat(androidManifest.getMetadataValue("metadata-key")).hasValue("123");
  }

  @Test
  public void getMetadataValueAsInteger() {
    AndroidManifest androidManifest =
        AndroidManifest.create(
            xmlNode(
                xmlElement(
                    "manifest",
                    xmlNode(
                        xmlElement(
                            "application", metadataWithValueAsInteger("metadata-key", 123))))));
    assertThat(androidManifest.getMetadataValueAsInteger("metadata-key")).hasValue(123);
  }

  @Test
  public void getModuleConditions_returnsAllConditions() {
    AndroidManifest androidManifest =
        AndroidManifest.create(
            androidManifest(
                "com.test.app",
                withFeatureCondition("android.hardware.camera.ar"),
                withMinSdkCondition(24)));

    assertThat(androidManifest.getModuleConditions())
        .isEqualTo(
            ModuleConditions.builder()
                .addDeviceFeatureCondition(
                    DeviceFeatureCondition.create("android.hardware.camera.ar"))
                .setMinSdkVersion(24)
                .build());
  }

  @Test
  public void getDeviceFeatureConditions_returnsAllConditions() {
    AndroidManifest androidManifest =
        AndroidManifest.create(
            androidManifest(
                "com.test.app",
                withFeatureCondition("android.hardware.camera.ar"),
                withFeatureCondition("android.software.vr.mode"),
                withMinSdkVersion(24)));

    assertThat(androidManifest.getDeviceFeatureConditions())
        .containsExactly(
            DeviceFeatureCondition.create("android.hardware.camera.ar"),
            DeviceFeatureCondition.create("android.software.vr.mode"));
  }

  @Test
  public void moduleConditions_unsupportedCondition_throws() throws Exception {
    AndroidManifest androidManifest =
        AndroidManifest.create(
            androidManifest(
                "com.test.app", withFusingAttribute(false), withUnsupportedCondition()));

    Throwable exception =
        assertThrows(ValidationException.class, () -> androidManifest.getModuleConditions());
    assertThat(exception)
        .hasMessageThat()
        .contains("Unrecognized module condition: 'unsupportedCondition'");
  }

  @Test
  public void moduleConditions_missingNameOfFeature_throws() throws Exception {
    // Name attribute doesn't use distribution namespace.
    XmlProtoElement badCondition =
        XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "device-feature")
            .addAttribute(
                XmlProtoAttributeBuilder.create("name")
                    .setValueAsString("android.hardware.camera.ar"))
            .build();

    AndroidManifest androidManifest = createAndroidManifestWithConditions(badCondition);

    Throwable exception =
        assertThrows(ValidationException.class, () -> androidManifest.getModuleConditions());
    assertThat(exception)
        .hasMessageThat()
        .contains("Missing required 'name' attribute in the 'device-feature' condition element.");
  }

  @Test
  public void moduleConditions_missingMinSdkValue_throws() {
    // Value attribute doesn't use distribution namespace.
    XmlProtoElement badCondition =
        XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "min-sdk")
            .addAttribute(XmlProtoAttributeBuilder.create("value").setValueAsDecimalInteger(26))
            .build();
    AndroidManifest androidManifest = createAndroidManifestWithConditions(badCondition);

    Throwable exception =
        assertThrows(ValidationException.class, () -> androidManifest.getModuleConditions());
    assertThat(exception)
        .hasMessageThat()
        .contains("Missing required 'value' attribute in the 'min-sdk' condition element.");
  }

  private static AndroidManifest createAndroidManifestWithConditions(
      XmlProtoElement... conditions) {
    XmlProtoElementBuilder conditionsBuilder =
        XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "conditions");
    for (XmlProtoElement condition : conditions) {
      conditionsBuilder.addChildElement(condition.toBuilder());
    }

    return AndroidManifest.create(
        XmlProtoNode.createElementNode(
            XmlProtoElementBuilder.create("manifest")
                .addChildElement(
                    XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "module")
                        .addChildElement(conditionsBuilder))
                .build()));
  }

  private XmlNode metadataWithValue(String key, String value) {
    return xmlNode(
        xmlElement(
            "meta-data",
            ImmutableList.of(
                xmlAttribute(ANDROID_NAMESPACE_URI, "name", NAME_RESOURCE_ID, key),
                xmlAttribute(ANDROID_NAMESPACE_URI, "value", VALUE_RESOURCE_ID, value))));
  }

  private XmlNode metadataWithValueAsInteger(String key, int value) {
    return xmlNode(
        xmlElement(
            "meta-data",
            ImmutableList.of(
                xmlAttribute(ANDROID_NAMESPACE_URI, "name", NAME_RESOURCE_ID, key),
                xmlDecimalIntegerAttribute(
                    ANDROID_NAMESPACE_URI, "value", VALUE_RESOURCE_ID, value))));
  }

  private XmlNode metadataWithResourceRef(String key, int resourceIdValue) {
    return xmlNode(
        xmlElement(
            "meta-data",
            ImmutableList.of(
                xmlAttribute(ANDROID_NAMESPACE_URI, "name", NAME_RESOURCE_ID, key),
                xmlResourceReferenceAttribute(
                    ANDROID_NAMESPACE_URI, "resource", RESOURCE_RESOURCE_ID, resourceIdValue))));
  }
}
