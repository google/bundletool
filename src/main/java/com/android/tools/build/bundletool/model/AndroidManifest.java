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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.aapt.Resources.XmlNode;
import com.android.tools.build.bundletool.exceptions.ValidationException;
import com.android.tools.build.bundletool.exceptions.manifest.ManifestFusingException.FusingMissingIncludeAttribute;
import com.android.tools.build.bundletool.exceptions.manifest.ManifestVersionException.VersionCodeMissingException;
import com.android.tools.build.bundletool.utils.xmlproto.XmlProtoAttribute;
import com.android.tools.build.bundletool.utils.xmlproto.XmlProtoElement;
import com.android.tools.build.bundletool.utils.xmlproto.XmlProtoElementBuilder;
import com.android.tools.build.bundletool.utils.xmlproto.XmlProtoNode;
import com.android.tools.build.bundletool.utils.xmlproto.XmlProtoNodeBuilder;
import com.android.tools.build.bundletool.version.Version;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.CheckReturnValue;

/**
 * Represents Android manifest.
 *
 * <p>Implementations may be not thread safe.
 */
@AutoValue
public abstract class AndroidManifest {

  private static final Splitter COMMA_SPLITTER = Splitter.on(',');

  public static final String ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android";
  public static final String DISTRIBUTION_NAMESPACE = "http://schemas.android.com/apk/distribution";

  public static final String APPLICATION_ELEMENT_NAME = "application";
  public static final String META_DATA_ELEMENT_NAME = "meta-data";
  public static final String SUPPORTS_GL_TEXTURE_ELEMENT_NAME = "supports-gl-texture";
  public static final String USES_FEATURE_ELEMENT_NAME = "uses-feature";
  public static final String USES_SDK_ELEMENT_NAME = "uses-sdk";
  public static final String ACTIVITY_ELEMENT_NAME = "activity";
  public static final String SERVICE_ELEMENT_NAME = "service";
  public static final String PROVIDER_ELEMENT_NAME = "provider";

  public static final String DEBUGGABLE_ATTRIBUTE_NAME = "debuggable";
  public static final String EXTRACT_NATIVE_LIBS_ATTRIBUTE_NAME = "extractNativeLibs";
  public static final String GL_VERSION_ATTRIBUTE_NAME = "glEsVersion";
  public static final String MAX_SDK_VERSION_ATTRIBUTE_NAME = "maxSdkVersion";
  public static final String MIN_SDK_VERSION_ATTRIBUTE_NAME = "minSdkVersion";
  public static final String NAME_ATTRIBUTE_NAME = "name";
  public static final String VALUE_ATTRIBUTE_NAME = "value";
  public static final String CONDITION_DEVICE_FEATURE_NAME = "device-feature";
  public static final String CONDITION_MIN_SDK_VERSION_NAME = "min-sdk";
  public static final String SPLIT_NAME_ATTRIBUTE_NAME = "splitName";

  public static final int DEBUGGABLE_RESOURCE_ID = 0x0101000f;
  public static final int EXTRACT_NATIVE_LIBS_RESOURCE_ID = 0x10104ea;
  public static final int HAS_CODE_RESOURCE_ID = 0x101000c;
  public static final int MAX_SDK_VERSION_RESOURCE_ID = 0x01010271;
  public static final int MIN_SDK_VERSION_RESOURCE_ID = 0x0101020c;
  public static final int NAME_RESOURCE_ID = 0x01010003;
  public static final int VALUE_RESOURCE_ID = 0x01010024;
  public static final int RESOURCE_RESOURCE_ID = 0x01010025;
  public static final int VERSION_CODE_RESOURCE_ID = 0x0101021b;
  public static final int IS_FEATURE_SPLIT_RESOURCE_ID = 0x0101055b;
  public static final int GL_ES_VERSION_RESOURCE_ID = 0x01010281;
  public static final int TARGET_SANDBOX_VERSION_RESOURCE_ID = 0x0101054c;
  public static final int SPLIT_NAME_RESOURCE_ID = 0x01010549;

  public static final String META_DATA_KEY_FUSED_MODULE_NAMES =
      "com.android.dynamic.apk.fused.modules";

  public abstract XmlProtoNode getManifestRoot();

  @Memoized
  XmlProtoElement getManifestElement() {
    return getManifestRoot().getElement();
  }

  /**
   * Creates a proto representation of the manifest.
   *
   * @param manifestRoot the parsed proto of the root of the manifest
   */
  public static AndroidManifest create(XmlProtoNode manifestRoot) {
    return new AutoValue_AndroidManifest(manifestRoot);
  }

  public static AndroidManifest create(XmlNode manifestRoot) {
    return create(new XmlProtoNode(manifestRoot));
  }

  /**
   * Creates a minimal config split manifest.
   *
   * @param packageName the package name of the application
   * @param versionCode the version code of the application
   * @param splitId a split id of this config split
   * @param featureSplitId a split id of the feature split this config split is for
   * @return generated Android Manifest.
   */
  public static AndroidManifest createForConfigSplit(
      String packageName,
      int versionCode,
      String splitId,
      String featureSplitId,
      Optional<Boolean> extractNativeLibs) {
    checkNotNull(splitId);
    checkArgument(!splitId.isEmpty(), "Split Id cannot be empty for config split.");
    checkNotNull(featureSplitId);
    checkNotNull(packageName);

    ManifestEditor editor =
        new ManifestEditor(createMinimalManifestTag())
            .setPackage(packageName)
            .setVersionCode(versionCode)
            .setSplitId(splitId)
            .setConfigForSplit(featureSplitId)
            .setHasCode(false);

    extractNativeLibs.ifPresent(editor::setExtractNativeLibsValue);

    return editor.save();
  }

  private static XmlProtoNode createMinimalManifestTag() {
    return XmlProtoNode.createElementNode(
        XmlProtoElementBuilder.create("manifest")
            .addNamespaceDeclaration("android", ANDROID_NAMESPACE)
            .build());
  }

  public boolean getEffectiveApplicationDebuggable() {
    return getApplicationDebuggable().orElse(false);
  }

  @CheckReturnValue
  public AndroidManifest applyMutators(ImmutableList<ManifestMutator> manifestMutators) {
    XmlProtoNodeBuilder xmlProtoNode = getManifestRoot().toBuilder();
    for (ManifestMutator manifestMutator : manifestMutators) {
      manifestMutator.accept(xmlProtoNode.getElement());
    }
    return AndroidManifest.create(xmlProtoNode.build().getProto());
  }
  /**
   * Extracts value of the {@code <application android:debuggable>} attribute.
   *
   * @return An optional containing the value of the {@code debuggable} attribute if set, or an
   *     empty optional if not set.
   */
  public Optional<Boolean> getApplicationDebuggable() {
    return getManifestElement()
        .getOptionalChildElement(APPLICATION_ELEMENT_NAME)
        .flatMap(app -> app.getAndroidAttribute(DEBUGGABLE_RESOURCE_ID))
        .map(attr -> attr.getValueAsBoolean());
  }

  public Optional<Integer> getMinSdkVersion() {
    return getUsesSdkAttribute(MIN_SDK_VERSION_RESOURCE_ID);
  }

  public int getEffectiveMinSdkVersion() {
    return getMinSdkVersion().orElse(1);
  }

  public Optional<Integer> getMaxSdkVersion() {
    return getUsesSdkAttribute(MAX_SDK_VERSION_RESOURCE_ID);
  }

  public Optional<Integer> getTargetSandboxVersion() {
    return getManifestElement()
        .getAndroidAttribute(TARGET_SANDBOX_VERSION_RESOURCE_ID)
        .map(XmlProtoAttribute::getValueAsDecimalInteger);
  }

  private Optional<Integer> getUsesSdkAttribute(int attributeResId) {
    return getManifestElement()
        .getOptionalChildElement(USES_SDK_ELEMENT_NAME)
        .flatMap(usesSdk -> usesSdk.getAndroidAttribute(attributeResId))
        .map(attribute -> attribute.getValueAsDecimalInteger());
  }

  public Optional<Boolean> getHasCode() {
    return getManifestElement()
        .getOptionalChildElement(APPLICATION_ELEMENT_NAME)
        .flatMap(application -> application.getAndroidAttribute(HAS_CODE_RESOURCE_ID))
        .map(XmlProtoAttribute::getValueAsBoolean);
  }

  public Optional<Boolean> getIsFeatureSplit() {
    return getManifestElement()
        .getAndroidAttribute(IS_FEATURE_SPLIT_RESOURCE_ID)
        .map(XmlProtoAttribute::getValueAsBoolean);
  }

  public Optional<Boolean> getIsModuleIncludedInFusing(Version bundleToolVersion) {
    return getManifestElement()
        .getOptionalChildElement(DISTRIBUTION_NAMESPACE, "module")
        .flatMap(module -> module.getOptionalChildElement(DISTRIBUTION_NAMESPACE, "fusing"))
        .map(
            fusing -> {
              if (bundleToolVersion.isOlderThan(Version.of("0.3.4-dev"))) {
                return fusing
                    .getAttributeIgnoringNamespace("include")
                    .orElseThrow(() -> new FusingMissingIncludeAttribute(getSplitId()));
              } else {
                return fusing
                    .getAttribute(DISTRIBUTION_NAMESPACE, "include")
                    .orElseThrow(() -> new FusingMissingIncludeAttribute(getSplitId()));
              }
            })
        .map(XmlProtoAttribute::getValueAsBoolean);
  }

  public Optional<String> getConfigForSplit() {
    return getManifestElement()
        .getAttribute("configForSplit")
        .map(XmlProtoAttribute::getValueAsString);
  }

  public String getPackageName() {
    return getManifestElement()
        .getAttribute("package")
        .orElseThrow(() -> new ValidationException("Package name not found in the manifest."))
        .getValueAsString();
  }

  public int getVersionCode() {
    return getManifestElement()
        .getAndroidAttribute(VERSION_CODE_RESOURCE_ID)
        .orElseThrow(() -> new VersionCodeMissingException())
        .getValueAsDecimalInteger();
  }

  public Optional<String> getSplitId() {
    return getManifestElement().getAttribute("split").map(XmlProtoAttribute::getValueAsString);
  }

  public Optional<Integer> getTitleRefId() {
    return getManifestElement()
        .getOptionalChildElement(DISTRIBUTION_NAMESPACE, "module")
        .flatMap(module -> module.getAttribute(DISTRIBUTION_NAMESPACE, "title"))
        .map(XmlProtoAttribute::getValueAsRefId);
  }

  public ImmutableList<String> getUsesSplits() {
    return getManifestElement()
        .getChildrenElements("uses-split")
        .map(
            usesSplit ->
                usesSplit
                    .getAndroidAttribute(NAME_RESOURCE_ID)
                    .<ValidationException>orElseThrow(
                        () ->
                            new ValidationException(
                                "<uses-split> element is missing the 'android:name' attribute.")))
        .map(XmlProtoAttribute::getValueAsString)
        .collect(toImmutableList());
  }

  public Optional<Boolean> isOnDemandModule(Version bundleToolVersion) {
    return getManifestElement()
        .getOptionalChildElement(DISTRIBUTION_NAMESPACE, "module")
        .flatMap(
            module -> {
              if (bundleToolVersion.isOlderThan(Version.of("0.3.4-dev"))) {
                return module.getAttributeIgnoringNamespace("onDemand");
              } else {
                return module.getAttribute(DISTRIBUTION_NAMESPACE, "onDemand");
              }
            })
        .map(XmlProtoAttribute::getValueAsBoolean);
  }

  public Optional<Boolean> isInstantModule() {
    return getManifestElement()
        .getOptionalChildElement(DISTRIBUTION_NAMESPACE, "module")
        .flatMap(module -> module.getAttribute(DISTRIBUTION_NAMESPACE, "instant"))
        .map(XmlProtoAttribute::getValueAsBoolean);
  }

  public ImmutableList<DeviceFeatureCondition> getDeviceFeatureConditions() {
    return getModuleConditions().getDeviceFeatureConditions();
  }

  /** Currently only <dist:device-feature> condition is supported. We reject all other values. */
  public ModuleConditions getModuleConditions() {
    ImmutableList<XmlProtoElement> conditionElements = getModuleConditionElements();

    ModuleConditions.Builder moduleConditions = ModuleConditions.builder();
    for (XmlProtoElement conditionElement : conditionElements) {
      if (!conditionElement.getNamespaceUri().equals(DISTRIBUTION_NAMESPACE)) {
        throw ValidationException.builder()
            .withMessage(
                "Invalid namespace found in the module condition element. "
                    + "Expected '%s'; found '%s'.",
                DISTRIBUTION_NAMESPACE, conditionElement.getNamespaceUri())
            .build();
      }
      switch (conditionElement.getName()) {
        case CONDITION_DEVICE_FEATURE_NAME:
          moduleConditions.addDeviceFeatureCondition(parseDeviceFeatureCondition(conditionElement));
          break;
        case CONDITION_MIN_SDK_VERSION_NAME:
          moduleConditions.setMinSdkVersion(parseMinSdkVersionCondition(conditionElement));
          break;
        default:
          throw new ValidationException(
              String.format("Unrecognized module condition: '%s'", conditionElement.getName()));
      }
    }
    return moduleConditions.build();
  }

  private ImmutableList<XmlProtoElement> getModuleConditionElements() {
    return getManifestElement()
        .getOptionalChildElement(DISTRIBUTION_NAMESPACE, "module")
        .flatMap(module -> module.getOptionalChildElement(DISTRIBUTION_NAMESPACE, "conditions"))
        .map(conditions -> conditions.getChildrenElements().collect(toImmutableList()))
        .orElse(ImmutableList.of());
  }

  private DeviceFeatureCondition parseDeviceFeatureCondition(XmlProtoElement conditionElement) {
    return DeviceFeatureCondition.create(
        conditionElement
            .getAttribute(DISTRIBUTION_NAMESPACE, NAME_ATTRIBUTE_NAME)
            .orElseThrow(
                () ->
                    new ValidationException(
                        "Missing required 'name' attribute in the 'device-feature' condition "
                            + "element."))
            .getValueAsString());
  }

  private int parseMinSdkVersionCondition(XmlProtoElement conditionElement) {
    return conditionElement
        .getAttribute(DISTRIBUTION_NAMESPACE, VALUE_ATTRIBUTE_NAME)
        .orElseThrow(
            () ->
                new ValidationException(
                    "Missing required 'value' attribute in the 'min-sdk' condition element."))
        .getValueAsDecimalInteger();
  }

  /**
   * Extracts the 'android:extractNativeLibs' value from the {@code <application>} tag.
   *
   * @return An optional containing the value of the 'extractNativeLibs' attribute if set, or an
   *     empty optional if not set.
   */
  public Optional<Boolean> getExtractNativeLibsValue() {
    return getManifestElement()
        .getOptionalChildElement(APPLICATION_ELEMENT_NAME)
        .flatMap(app -> app.getAndroidAttribute(EXTRACT_NATIVE_LIBS_RESOURCE_ID))
        .map(XmlProtoAttribute::getValueAsBoolean);
  }

  /**
   * Extracts names of the fused modules.
   *
   * @return names of the fused modules, or empty list if the information is not present in the
   *     manifest
   */
  public ImmutableList<String> getFusedModuleNames() {
    return getMetadataValue(META_DATA_KEY_FUSED_MODULE_NAMES)
        .map(rawValue -> ImmutableList.copyOf(COMMA_SPLITTER.split(rawValue)))
        .orElse(ImmutableList.of());
  }

  /** Returns the string value specified in the meta-data of the given name, if it exists. */
  public Optional<String> getMetadataValue(String metadataName) {
    return getMetadataAttributeWithName(metadataName).map(XmlProtoAttribute::getValueAsString);
  }

  /** Returns the string value specified in the meta-data of the given name, if it exists. */
  public Optional<Integer> getMetadataValueAsInteger(String metadataName) {
    return getMetadataAttributeWithName(metadataName)
        .map(XmlProtoAttribute::getValueAsDecimalInteger);
  }

  private Optional<XmlProtoAttribute> getMetadataAttributeWithName(String metadataName) {
    return getMetadataElement(metadataName)
        .map(
            metadataElement ->
                metadataElement
                    .getAndroidAttribute(VALUE_RESOURCE_ID)
                    .orElseThrow(
                        () ->
                            ValidationException.builder()
                                .withMessage(
                                    "Missing expected attribute 'android:value' for <meta-data> "
                                        + "element '%s'.",
                                    metadataName)
                                .build()));
  }

  /** Returns the resource ID specified in the meta-data of the given name, if it exists. */
  public Optional<Integer> getMetadataResourceId(String metadataName) {
    return getMetadataElement(metadataName)
        .map(
            metadataElement ->
                metadataElement
                    .getAndroidAttribute(RESOURCE_RESOURCE_ID)
                    .orElseThrow(
                        () ->
                            ValidationException.builder()
                                .withMessage(
                                    "Missing expected attribute 'android:resource' for <meta-data> "
                                        + "element '%s'.",
                                    metadataName)
                                .build()))
        .map(XmlProtoAttribute::getValueAsRefId);
  }

  /**
   * Returns the <meta-data> XML element with the given "android:name" value if present.
   *
   * <p>Throws an {@link ValidationException} if there is more than one meta-data element with this
   * name.
   */
  private Optional<XmlProtoElement> getMetadataElement(String name) {
    ImmutableList<XmlProtoElement> metadataElements =
        getMetadataElements()
            .filter(
                metadataElement ->
                    metadataElement
                        .getAndroidAttribute(NAME_RESOURCE_ID)
                        .map(XmlProtoAttribute::getValueAsString)
                        .orElse("")
                        .equals(name))
            .collect(toImmutableList());

    switch (metadataElements.size()) {
      case 0:
        return Optional.empty();
      case 1:
        return Optional.of(metadataElements.get(0));
      default:
        throw ValidationException.builder()
            .withMessage(
                "Found multiple <meta-data> elements for key '%s', expected at most one.", name)
            .build();
    }
  }

  /** Returns a stream of the <meta-data> XML elements under the <application> tag. */
  private Stream<XmlProtoElement> getMetadataElements() {
    return getManifestElement()
        .getOptionalChildElement(APPLICATION_ELEMENT_NAME)
        .map(applicationElement -> applicationElement.getChildrenElements(META_DATA_ELEMENT_NAME))
        .orElse(Stream.of());
  }

  public ManifestEditor toEditor() {
    return new ManifestEditor(getManifestRoot());
  }
}
