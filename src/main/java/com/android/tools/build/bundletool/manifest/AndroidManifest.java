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

package com.android.tools.build.bundletool.manifest;

import static com.android.tools.build.bundletool.manifest.ProtoXmlHelper.NO_NAMESPACE_URI;
import static com.android.tools.build.bundletool.manifest.ProtoXmlHelper.findAttribute;
import static com.android.tools.build.bundletool.manifest.ProtoXmlHelper.findAttributeIgnoringNamespace;
import static com.android.tools.build.bundletool.manifest.ProtoXmlHelper.findAttributeWithName;
import static com.android.tools.build.bundletool.manifest.ProtoXmlHelper.findAttributeWithResourceId;
import static com.android.tools.build.bundletool.manifest.ProtoXmlHelper.findElementFromDirectChildren;
import static com.android.tools.build.bundletool.manifest.ProtoXmlHelper.findElementsFromDirectChildren;
import static com.android.tools.build.bundletool.manifest.ProtoXmlHelper.getAttributeValueAsDecimalInteger;
import static com.android.tools.build.bundletool.manifest.ProtoXmlHelper.getExactlyOneElement;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.aapt.Resources.XmlAttribute;
import com.android.aapt.Resources.XmlElement;
import com.android.aapt.Resources.XmlNamespace;
import com.android.aapt.Resources.XmlNode;
import com.android.tools.build.bundletool.exceptions.ValidationException;
import com.android.tools.build.bundletool.exceptions.manifest.ManifestFusingException.FusingMissingIncludeAttribute;
import com.android.tools.build.bundletool.exceptions.manifest.ManifestSdkTargetingException.MaxSdkInvalidException;
import com.android.tools.build.bundletool.exceptions.manifest.ManifestSdkTargetingException.MinSdkInvalidException;
import com.android.tools.build.bundletool.exceptions.manifest.ManifestValidationException;
import com.android.tools.build.bundletool.exceptions.manifest.ManifestVersionException.VersionCodeInvalidException;
import com.android.tools.build.bundletool.exceptions.manifest.ManifestVersionException.VersionCodeMissingException;
import com.android.tools.build.bundletool.version.Version;
import com.google.auto.value.AutoValue;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;

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

  public static final String DEBUGGABLE_ATTRIBUTE_NAME = "debuggable";
  public static final String EXTRACT_NATIVE_LIBS_ATTRIBUTE_NAME = "extractNativeLibs";
  public static final String GL_VERSION_ATTRIBUTE_NAME = "glEsVersion";
  public static final String MAX_SDK_VERSION_ATTRIBUTE_NAME = "maxSdkVersion";
  public static final String MIN_SDK_VERSION_ATTRIBUTE_NAME = "minSdkVersion";

  public static final int DEBUGGABLE_RESOURCE_ID = 0x0101000f;
  public static final int EXTRACT_NATIVE_LIBS_RESOURCE_ID = 0x10104ea;
  public static final int HAS_CODE_RESOURCE_ID = 0x101000c;
  public static final int MAX_SDK_VERSION_RESOURCE_ID = 0x01010271;
  public static final int MIN_SDK_VERSION_RESOURCE_ID = 0x0101020c;
  public static final int NAME_RESOURCE_ID = 0x01010003;
  public static final int VALUE_RESOURCE_ID = 0x01010024;
  public static final int VERSION_CODE_RESOURCE_ID = 0x0101021b;

  public static final String META_DATA_KEY_FUSED_MODULE_NAMES =
      "com.android.dynamic.apk.fused.modules";

  public abstract XmlNode getManifestRoot();

  /**
   * Creates a proto representation of the manifest.
   *
   * @param manifestRoot the parsed proto of the root of the manifest
   */
  public static AndroidManifest create(XmlNode manifestRoot) {
    return new AutoValue_AndroidManifest(manifestRoot);
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

  private static XmlNode createMinimalManifestTag() {
    return XmlNode.newBuilder()
        .setElement(
            XmlElement.newBuilder()
                .setName("manifest")
                .addNamespaceDeclaration(
                    XmlNamespace.newBuilder().setPrefix("android").setUri(ANDROID_NAMESPACE)))
        .build();
  }

  public boolean getEffectiveApplicationDebuggable() {
    return getApplicationDebuggable().orElse(false);
  }

  /**
   * Extracts value of the {@code <application android:debuggable>} attribute.
   *
   * @return An optional containing the value of the {@code debuggable} attribute if set, or an
   *     empty optional if not set.
   */
  public Optional<Boolean> getApplicationDebuggable() {
    return ProtoXmlHelper.getFirstElement(getManifestRoot(), APPLICATION_ELEMENT_NAME)
        .flatMap(el -> findAttributeWithResourceId(el, DEBUGGABLE_RESOURCE_ID))
        .map(ProtoXmlHelper::getAttributeValueAsBoolean);
  }

  public Optional<Integer> getMinSdkVersion() {
    return getUsesSdkAttribute(MIN_SDK_VERSION_ATTRIBUTE_NAME, MinSdkInvalidException::new);
  }

  public int getEffectiveMinSdkVersion() {
    return getMinSdkVersion().orElse(1);
  }

  public Optional<Integer> getMaxSdkVersion() {
    return getUsesSdkAttribute(MAX_SDK_VERSION_ATTRIBUTE_NAME, MaxSdkInvalidException::new);
  }

  private Optional<Integer> getUsesSdkAttribute(
      String attributeName, Function<XmlAttribute, ManifestValidationException> wrongTypeHandler) {
    return ProtoXmlHelper.getFirstElement(getManifestRoot(), USES_SDK_ELEMENT_NAME)
        .flatMap(usesSdk -> findAttribute(usesSdk, ANDROID_NAMESPACE, attributeName))
        .map(a -> getAttributeValueAsDecimalInteger(a, wrongTypeHandler));
  }

  public Optional<Boolean> getHasCode() {
    return ProtoXmlHelper.getFirstElement(getManifestRoot(), "application")
        .flatMap(el -> findAttributeWithResourceId(el, HAS_CODE_RESOURCE_ID))
        .map(ProtoXmlHelper::getAttributeValueAsBoolean);
  }

  public Optional<Boolean> getIsFeatureSplit() {
    return ProtoXmlHelper.findAttribute(
            getManifestRoot().getElement(), ANDROID_NAMESPACE, "isFeatureSplit")
        .map(ProtoXmlHelper::getAttributeValueAsBoolean);
  }

  public Optional<Boolean> getIsModuleIncludedInFusing(Version bundleToolVersion) {
    XmlElement manifest = getExactlyOneElement(getManifestRoot(), "manifest");

    return findElementFromDirectChildren(manifest, "module", DISTRIBUTION_NAMESPACE)
        .flatMap(module -> findElementFromDirectChildren(module, "fusing", DISTRIBUTION_NAMESPACE))
        .map(
            fusing -> {
              if (bundleToolVersion.isOlderThan(Version.of("0.3.4-dev"))) {
                return findAttributeIgnoringNamespace(fusing, "include")
                    .orElseThrow(() -> new FusingMissingIncludeAttribute(getSplitId()));
              } else {
                return findAttribute(fusing, DISTRIBUTION_NAMESPACE, "include")
                    .orElseThrow(() -> new FusingMissingIncludeAttribute(getSplitId()));
              }
            })
        .map(ProtoXmlHelper::getAttributeValueAsBoolean);
  }

  public Optional<String> getConfigForSplit() {
    return findAttributeWithName(getManifestRoot().getElement(), "configForSplit")
        .map(XmlAttribute::getValue);
  }

  public String getPackageName() {
    XmlElement manifest = getExactlyOneElement(getManifestRoot(), "manifest");
    return findAttributeWithName(manifest, "package").get().getValue();
  }

  public int getVersionCode() {
    XmlElement manifest = getExactlyOneElement(getManifestRoot(), "manifest");
    Optional<XmlAttribute> maybeVersionCode =
        findAttributeWithResourceId(manifest, VERSION_CODE_RESOURCE_ID);
    XmlAttribute versionCode =
        maybeVersionCode.orElseThrow(() -> new VersionCodeMissingException());
    return getAttributeValueAsDecimalInteger(versionCode, VersionCodeInvalidException::new);
  }

  public Optional<String> getSplitId() {
    XmlElement manifest = getExactlyOneElement(getManifestRoot(), "manifest");
    return findAttributeWithName(manifest, "split").map(XmlAttribute::getValue);
  }

  public Collection<String> getUsesSplits() {
    return ProtoXmlHelper.findElements(getManifestRoot(), "uses-split")
        .map(
            elem ->
                findAttribute(elem, ANDROID_NAMESPACE, "name")
                    .<ValidationException>orElseThrow(
                        () ->
                            new ValidationException(
                                "<uses-split> element is missing the 'android:name' attribute.")))
        .map(XmlAttribute::getValue)
        .collect(toImmutableList());
  }

  public Optional<Boolean> isOnDemandModule(Version bundleToolVersion) {
    XmlElement manifest = getExactlyOneElement(getManifestRoot(), "manifest");
    Optional<XmlElement> moduleElement =
        findElementFromDirectChildren(manifest, "module", DISTRIBUTION_NAMESPACE);

    return moduleElement
        .flatMap(
            el -> {
              if (bundleToolVersion.isOlderThan(Version.of("0.3.4-dev"))) {
                return findAttributeIgnoringNamespace(el, "onDemand");
              } else {
                return findAttribute(el, DISTRIBUTION_NAMESPACE, "onDemand");
              }
            })
        .map(ProtoXmlHelper::getAttributeValueAsBoolean);
  }

  public Optional<Boolean> isInstantModule() {
    XmlElement manifest = getExactlyOneElement(getManifestRoot(), "manifest");
    Optional<XmlElement> moduleElement =
        findElementFromDirectChildren(manifest, "module", DISTRIBUTION_NAMESPACE);
    return moduleElement
        .flatMap(el -> findAttribute(el, DISTRIBUTION_NAMESPACE, "instant"))
        .map(ProtoXmlHelper::getAttributeValueAsBoolean);
  }

  /**
   * Extracts the 'android:extractNativeLibs' value from the {@code <application>} tag.
   *
   * @return An optional containing the value of the 'extractNativeLibs' attribute if set, or an
   *     empty optional if not set.
   */
  public Optional<Boolean> getExtractNativeLibsValue() {
    return ProtoXmlHelper.getFirstElement(getManifestRoot(), "application")
        .flatMap(el -> findAttributeWithResourceId(el, EXTRACT_NATIVE_LIBS_RESOURCE_ID))
        .map(ProtoXmlHelper::getAttributeValueAsBoolean);
  }

  /**
   * Extracts names of the fused modules.
   *
   * @return names of the fused modules, or empty list if the information is not present in the
   *     manifest
   */
  public ImmutableList<String> getFusedModuleNames() {
    Optional<XmlElement> application =
        findElementFromDirectChildren(
            getManifestRoot().getElement(), APPLICATION_ELEMENT_NAME, NO_NAMESPACE_URI);
    if (!application.isPresent()) {
      return ImmutableList.of();
    }

    ImmutableList<String> values =
        findElementsFromDirectChildren(application.get(), META_DATA_ELEMENT_NAME, NO_NAMESPACE_URI)
            // Find <meta-data> with the right 'name' attribute.
            .filter(
                metadataElem -> {
                  String attrName =
                      findAttributeWithResourceId(metadataElem, NAME_RESOURCE_ID)
                          .map(XmlAttribute::getValue)
                          .orElse("");
                  return attrName.equals(META_DATA_KEY_FUSED_MODULE_NAMES);
                })
            // Extract the raw attribute value.
            // The value is expected to be stored as 'android:value', not 'android:resource'.
            .map(
                metadataElem ->
                    findAttributeWithResourceId(metadataElem, VALUE_RESOURCE_ID)
                        .orElseThrow(
                            () ->
                                new ValidationException(
                                    "<meta-data> element is missing the 'android:value'"
                                        + " attribute:\n"
                                        + metadataElem)))
            .map(XmlAttribute::getValue)
            .collect(toImmutableList());

    switch (values.size()) {
      case 0:
        return ImmutableList.of();
      case 1:
        String rawValue = Iterables.getOnlyElement(values);
        return ImmutableList.copyOf(COMMA_SPLITTER.split(rawValue));
      default:
        throw ValidationException.builder()
            .withMessage(
                "Found multiple <meta-data> elements for key '%s', expected at most one.",
                META_DATA_KEY_FUSED_MODULE_NAMES)
            .build();
    }
  }

  public ManifestEditor toEditor() {
    return new ManifestEditor(getManifestRoot());
  }
}
