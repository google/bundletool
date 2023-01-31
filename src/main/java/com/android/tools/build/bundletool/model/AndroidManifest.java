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

import static com.android.tools.build.bundletool.model.ModuleDeliveryType.ALWAYS_INITIAL_INSTALL;
import static com.android.tools.build.bundletool.model.ModuleDeliveryType.CONDITIONAL_INITIAL_INSTALL;
import static com.android.tools.build.bundletool.model.ModuleDeliveryType.NO_INITIAL_INSTALL;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_T_API_VERSION;
import static com.android.tools.build.bundletool.model.version.VersionGuardedFeature.NAMESPACE_ON_INCLUDE_ATTRIBUTE_REQUIRED;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;
import static com.google.common.collect.Streams.stream;
import static java.util.function.Function.identity;

import com.android.aapt.Resources.XmlNode;
import com.android.tools.build.bundletool.model.BundleModule.ModuleType;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoAttribute;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElement;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElementBuilder;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoNode;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.model.version.Version;
import com.android.tools.build.bundletool.model.version.VersionGuardedFeature;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.common.primitives.Ints;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Represents Android manifest.
 *
 * <p>Implementations may be not thread safe.
 */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class AndroidManifest {

  private static final Splitter COMMA_SPLITTER = Splitter.on(',');

  public static final String ANDROID_NAMESPACE_URI = "http://schemas.android.com/apk/res/android";
  public static final String DISTRIBUTION_NAMESPACE_URI =
      "http://schemas.android.com/apk/distribution";
  public static final String TOOLS_NAMESPACE_URI = "http://schemas.android.com/tools";
  public static final String NO_NAMESPACE_URI = "";

  public static final String APPLICATION_ELEMENT_NAME = "application";
  public static final String META_DATA_ELEMENT_NAME = "meta-data";
  public static final String USES_SDK_ELEMENT_NAME = "uses-sdk";
  public static final String ACTIVITY_ELEMENT_NAME = "activity";
  public static final String FRAME_LAYOUT_ELEMENT_NAME = "FrameLayout";
  public static final String IMAGE_VIEW_ELEMENT_NAME = "ImageView";
  public static final String ACTIVITY_ALIAS_ELEMENT_NAME = "activity-alias";
  public static final String INTENT_FILTER_ELEMENT_NAME = "intent-filter";
  public static final String SERVICE_ELEMENT_NAME = "service";
  public static final String RECEIVER_ELEMENT_NAME = "receiver";
  public static final String PROVIDER_ELEMENT_NAME = "provider";
  public static final String SUPPORTS_GL_TEXTURE_ELEMENT_NAME = "supports-gl-texture";
  public static final String ACTION_ELEMENT_NAME = "action";
  public static final String CATEGORY_ELEMENT_NAME = "category";
  public static final String PERMISSION_ELEMENT_NAME = "permission";
  public static final String PERMISSION_GROUP_ELEMENT_NAME = "permission-group";
  public static final String PERMISSION_TREE_ELEMENT_NAME = "permission-tree";
  public static final String PROPERTY_ELEMENT_NAME = "property";
  public static final String USES_FEATURE_ELEMENT_NAME = "uses-feature";
  public static final String MODULE_ELEMENT_NAME = "module";
  public static final String DELIVERY_ELEMENT_NAME = "delivery";
  public static final String INSTALL_TIME_ELEMENT_NAME = "install-time";
  public static final String REMOVABLE_ELEMENT_NAME = "removable";
  public static final String FUSING_ELEMENT_NAME = "fusing";
  public static final String STYLE_ELEMENT_NAME = "style";

  public static final String DEBUGGABLE_ATTRIBUTE_NAME = "debuggable";
  public static final String EXTRACT_NATIVE_LIBS_ATTRIBUTE_NAME = "extractNativeLibs";
  public static final String ICON_ATTRIBUTE_NAME = "icon";
  public static final String ROUND_ICON_ATTRIBUTE_NAME = "roundIcon";
  public static final String BANNER_ATTRIBUTE_NAME = "banner";
  public static final String MAX_SDK_VERSION_ATTRIBUTE_NAME = "maxSdkVersion";
  public static final String MIN_SDK_VERSION_ATTRIBUTE_NAME = "minSdkVersion";
  public static final String TARGET_SDK_VERSION_ATTRIBUTE_NAME = "targetSdkVersion";
  public static final String NAME_ATTRIBUTE_NAME = "name";
  public static final String VALUE_ATTRIBUTE_NAME = "value";
  public static final String CODE_ATTRIBUTE_NAME = "code";
  public static final String EXCLUDE_ATTRIBUTE_NAME = "exclude";
  public static final String THEME_ATTRIBUTE_NAME = "theme";
  public static final String COUNTRY_ELEMENT_NAME = "country";
  public static final String CONDITION_DEVICE_FEATURE_NAME = "device-feature";
  public static final String CONDITION_MIN_SDK_VERSION_NAME = "min-sdk";
  public static final String CONDITION_MAX_SDK_VERSION_NAME = "max-sdk";
  public static final String CONDITION_USER_COUNTRIES_NAME = "user-countries";
  public static final String CONDITION_DEVICE_GROUPS_NAME = "device-groups";
  public static final String DEVICE_GROUP_ELEMENT_NAME = "device-group";
  public static final String SPLIT_NAME_ATTRIBUTE_NAME = "splitName";
  public static final String VERSION_NAME_ATTRIBUTE_NAME = "versionName";
  public static final String INSTALL_LOCATION_ATTRIBUTE_NAME = "installLocation";
  public static final String IS_SPLIT_REQUIRED_ATTRIBUTE_NAME = "isSplitRequired";
  public static final String SHARED_USER_ID_ATTRIBUTE_NAME = "sharedUserId";
  public static final String SHARED_USER_LABEL_ATTRIBUTE_NAME = "sharedUserLabel";
  public static final String DESCRIPTION_ATTRIBUTE_NAME = "description";
  public static final String HAS_FRAGILE_USER_DATA_ATTRIBUTE_NAME = "hasFragileUserData";
  public static final String IS_GAME_ATTRIBUTE_NAME = "isGame";
  public static final String LABEL_ATTRIBUTE_NAME = "label";
  public static final String ALLOW_BACKUP_ATTRIBUTE_NAME = "allowBackup";
  public static final String FULL_BACKUP_CONTENT_ATTRIBUTE_NAME = "fullBackupContent";
  public static final String FULL_BACKUP_ONLY_ATTRIBUTE_NAME = "fullBackupOnly";
  public static final String BACKUP_AGENT_ATTRIBUTE_NAME = "backupAgent";
  public static final String DATA_EXTRACTION_RULES_ATTRIBUTE_NAME = "dataExtractionRules";
  public static final String EXPORTED_ATTRIBUTE_NAME = "exported";
  public static final String LOCALE_CONFIG_ATTRIBUTE_NAME = "localeConfig";
  public static final String SDK_LIBRARY_ELEMENT_NAME = "sdk-library";
  public static final String SDK_VERSION_MAJOR_ATTRIBUTE_NAME = "versionMajor";
  public static final String SDK_PATCH_VERSION_ATTRIBUTE_NAME =
      "com.android.vending.sdk.version.patch";
  public static final String SDK_PROVIDER_CLASS_NAME_ATTRIBUTE_NAME =
      "android.sdksandbox.PROPERTY_SDK_PROVIDER_CLASS_NAME";
  public static final String COMPAT_SDK_PROVIDER_CLASS_NAME_ATTRIBUTE_NAME =
      "android.sdksandbox.PROPERTY_COMPAT_SDK_PROVIDER_CLASS_NAME";
  public static final String REQUIRED_ATTRIBUTE_NAME = "required";
  public static final int SDK_SANDBOX_MIN_VERSION = ANDROID_T_API_VERSION;
  public static final String USES_SDK_LIBRARY_ELEMENT_NAME = "uses-sdk-library";
  public static final String CERTIFICATE_DIGEST_ATTRIBUTE_NAME = "certDigest";
  public static final String TARGET_SANDBOX_VERSION_ATTRIBUTE_NAME = "targetSandboxVersion";
  public static final String REQUIRED_ACCOUNT_TYPE_ATTRIBUTE_NAME = "requiredAccountType";
  public static final String RESTRICTED_ACCOUNT_TYPE_ATTRIBUTE_NAME = "restrictedAccountType";
  public static final String LARGE_HEAP_ATTRIBUTE_NAME = "largeHeap";
  public static final String INCLUDE_ATTRIBUTE_NAME = "include";
  public static final String REQUIRED_BY_PRIVACY_SANDBOX_SDK_ATTRIBUTE_NAME =
      "requiredByPrivacySandboxSdk";
  public static final String LAYOUT_WIDTH_ATTRIBUTE_NAME = "layout_width";
  public static final String LAYOUT_HEIGHT_ATTRIBUTE_NAME = "layout_height";
  public static final String LAYOUT_GRAVITY_ATTRIBUTE_NAME = "layout_gravity";
  public static final String ANIMATE_LAYOUT_CHANGES_ATTRIBUTE_NAME = "animateLayoutChanges";
  public static final String FITS_SYSTEM_WINDOWS_ATTRIBUTE_NAME = "fitsSystemWindows";
  public static final String SRC_ATTRIBUTE_NAME = "src";
  public static final String APP_COMPONENT_FACTORY_ATTRIBUTE_NAME = "appComponentFactory";
  public static final String AUTHORITIES_ATTRIBUTE_NAME = "authorities";

  public static final String LEANBACK_FEATURE_NAME = "android.software.leanback";
  public static final String TOUCHSCREEN_FEATURE_NAME = "android.hardware.touchscreen";

  public static final String MODULE_TYPE_FEATURE_VALUE = "feature";
  public static final String MODULE_TYPE_ASSET_VALUE = "asset-pack";
  public static final String MODULE_TYPE_ML_VALUE = "ml-pack";

  /** <meta-data> name that specifies native library for native activity */
  public static final String NATIVE_ACTIVITY_LIB_NAME = "android.app.lib_name";

  public static final int DEBUGGABLE_RESOURCE_ID = 0x0101000f;
  public static final int EXTRACT_NATIVE_LIBS_RESOURCE_ID = 0x10104ea;
  public static final int REQUIRED_RESOURCE_ID = 0x0101028e;
  public static final int HAS_CODE_RESOURCE_ID = 0x101000c;
  public static final int ICON_RESOURCE_ID = 0x01010002;
  public static final int ROUND_ICON_RESOURCE_ID = 0x0101052C;
  public static final int BANNER_RESOURCE_ID = 0x010103f2;
  public static final int MAX_SDK_VERSION_RESOURCE_ID = 0x01010271;
  public static final int MIN_SDK_VERSION_RESOURCE_ID = 0x0101020c;
  public static final int TARGET_SDK_VERSION_RESOURCE_ID = 0x01010270;
  public static final int NAME_RESOURCE_ID = 0x01010003;
  public static final int VALUE_RESOURCE_ID = 0x01010024;
  public static final int RESOURCE_RESOURCE_ID = 0x01010025;
  public static final int VERSION_CODE_RESOURCE_ID = 0x0101021b;
  public static final int VERSION_CODE_MAJOR_RESOURCE_ID = 0x01010576;
  public static final int VERSION_NAME_RESOURCE_ID = 0x0101021c;
  public static final int IS_FEATURE_SPLIT_RESOURCE_ID = 0x0101055b;
  public static final int TARGET_SANDBOX_VERSION_RESOURCE_ID = 0x0101054c;
  public static final int SPLIT_NAME_RESOURCE_ID = 0x01010549;
  public static final int INSTALL_LOCATION_RESOURCE_ID = 0x010102b7;
  public static final int IS_SPLIT_REQUIRED_RESOURCE_ID = 0x01010591;
  public static final int THEME_RESOURCE_ID = 0x01010000;
  public static final int ISOLATED_SPLITS_ID = 0x0101054b;
  public static final int SHARED_USER_ID_RESOURCE_ID = 0x0101000b;
  public static final int SHARED_USER_LABEL_RESOURCE_ID = 0x01010261;
  public static final int DESCRIPTION_RESOURCE_ID = 0x01010020;
  public static final int HAS_FRAGILE_USER_DATA_RESOURCE_ID = 0x0101059a;
  public static final int IS_GAME_RESOURCE_ID = 0x010103f4;
  public static final int LABEL_RESOURCE_ID = 0x01010001;
  public static final int ALLOW_BACKUP_RESOURCE_ID = 0x01010280;
  public static final int FULL_BACKUP_CONTENT_RESOURCE_ID = 0x010104eb;
  public static final int FULL_BACKUP_ONLY_RESOURCE_ID = 0x01010473;
  public static final int BACKUP_AGENT_RESOURCE_ID = 0x0101027f;
  public static final int DATA_EXTRACTION_RULES_RESOURCE_ID = 0x0101063e;
  public static final int EXPORTED_RESOURCE_ID = 0x01010010;
  public static final int LOCALE_CONFIG_RESOURCE_ID = 0x01df000e;
  public static final int VERSION_MAJOR_RESOURCE_ID = 0x01010577;
  public static final int CERTIFICATE_DIGEST_RESOURCE_ID = 0x01010548;
  public static final int REQUIRED_ACCOUNT_TYPE_RESOURCE_ID = 0x010103d6;
  public static final int RESTRICTED_ACCOUNT_TYPE_RESOURCE_ID = 0x010103d5;
  public static final int LARGE_HEAP_RESOURCE_ID = 0x0101035a;
  public static final int DRAWABLE_RESOURCE_ID = 0x01010199;
  public static final int DEVICE_NO_ACTION_BAR_FULL_SCREEN_THEME = 0x0103012a;
  public static final int LAYOUT_WIDTH_RESOURCE_ID = 0x010100f4;
  public static final int LAYOUT_HEIGHT_RESOURCE_ID = 0x010100f5;
  public static final int LAYOUT_GRAVITY_RESOURCE_ID = 0x010100b3;
  public static final int ANIMATE_LAYOUT_CHANGES_RESOURCE_ID = 0x010102f2;
  public static final int FITS_SYSTEM_WINDOWS_RESOURCE_ID = 0x010100dd;
  public static final int SRC_RESOURCE_ID = 0x01010119;
  public static final int APP_COMPONENT_FACTORY_RESOURCE_ID = 0x0101057a;
  public static final int AUTHORITIES_RESOURCE_ID = 0x01010018;

  // Matches the value of android.os.Build.VERSION_CODES.CUR_DEVELOPMENT, used when turning
  // a manifest attribute which references a prerelease API version (e.g., "Q") into an integer.
  public static final int DEVELOPMENT_SDK_VERSION = 10_000;

  public static final String META_DATA_KEY_FUSED_MODULE_NAMES =
      "com.android.dynamic.apk.fused.modules";

  /**
   * Boolean <meta-data> entry indicating whether the app is capable of running without any splits
   * other than the base master split.
   *
   * <p>Written in master split of the base module.
   */
  public static final String META_DATA_KEY_SPLITS_REQUIRED = "com.android.vending.splits.required";

  public static final String META_DATA_GMS_VERSION = "com.google.android.gms.version";
  public static final String USES_FEATURE_HARDWARE_WATCH_NAME = "android.hardware.type.watch";

  public static final String MAIN_ACTION_NAME = "android.intent.action.MAIN";
  public static final String LAUNCHER_CATEGORY_NAME = "android.intent.category.LAUNCHER";
  public static final String LEANBACK_LAUNCHER_CATEGORY_NAME =
      "android.intent.category.LEANBACK_LAUNCHER";

  public abstract XmlProtoNode getManifestRoot();

  abstract Version getBundleToolVersion();

  @Memoized
  public XmlProtoElement getManifestElement() {
    return getManifestRoot().getElement();
  }

  @Memoized
  public Optional<ManifestDeliveryElement> getManifestDeliveryElement() {
    return ManifestDeliveryElement.fromManifestElement(
        getManifestElement(),
        /* isFastFollowAllowed= */ getModuleType().equals(ModuleType.ASSET_MODULE));
  }

  @Memoized
  public Optional<ManifestDeliveryElement> getInstantManifestDeliveryElement() {
    return ManifestDeliveryElement.instantFromManifestElement(
        getManifestElement(),
        /* isFastFollowAllowed= */ getModuleType().equals(ModuleType.ASSET_MODULE));
  }

  /**
   * Creates a proto representation of the manifest.
   *
   * @param manifestRoot the parsed proto of the root of the manifest
   */
  public static AndroidManifest create(XmlProtoNode manifestRoot, Version bundleToolVersion) {
    return new AutoValue_AndroidManifest(manifestRoot, bundleToolVersion);
  }

  public static AndroidManifest create(XmlNode manifestRoot, Version bundleToolVersion) {
    return create(new XmlProtoNode(manifestRoot), bundleToolVersion);
  }

  @VisibleForTesting
  public static AndroidManifest create(XmlNode manifestRoot) {
    return create(manifestRoot, BundleToolVersion.getCurrentVersion());
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
      Optional<Integer> versionCode,
      String splitId,
      String featureSplitId,
      Optional<Boolean> extractNativeLibs) {
    checkNotNull(splitId);
    checkArgument(!splitId.isEmpty(), "Split Id cannot be empty for config split.");
    checkNotNull(featureSplitId);
    checkNotNull(packageName);

    ManifestEditor editor =
        new ManifestEditor(createMinimalManifestTag(), BundleToolVersion.getCurrentVersion())
            .setPackage(packageName)
            .setSplitId(splitId)
            .setHasCode(false);

    if (!featureSplitId.isEmpty()) {
      editor.setConfigForSplit(featureSplitId);
    }

    versionCode.ifPresent(editor::setVersionCode);
    extractNativeLibs.ifPresent(editor::setExtractNativeLibsValue);

    return editor.save();
  }

  public static XmlProtoNode createMinimalManifestTag() {
    return XmlProtoNode.createElementNode(
        XmlProtoElementBuilder.create("manifest")
            .addNamespaceDeclaration("android", ANDROID_NAMESPACE_URI)
            .build());
  }

  public boolean getEffectiveApplicationDebuggable() {
    return getApplicationDebuggable().orElse(false);
  }

  @CheckReturnValue
  public AndroidManifest applyMutators(ImmutableList<ManifestMutator> manifestMutators) {
    ManifestEditor manifestEditor = toEditor();
    for (ManifestMutator manifestMutator : manifestMutators) {
      manifestMutator.accept(manifestEditor);
    }
    return manifestEditor.save();
  }

  /**
   * Extracts value of the {@code <application android:debuggable>} attribute.
   *
   * @return An optional containing the value of the {@code debuggable} attribute if set, or an
   *     empty optional if not set.
   */
  public Optional<Boolean> getApplicationDebuggable() {
    return getApplicationAttributeAsBoolean(DEBUGGABLE_RESOURCE_ID);
  }

  public ImmutableListMultimap<String, XmlProtoElement> getActivitiesByName() {
    return stream(getManifestElement().getOptionalChildElement(APPLICATION_ELEMENT_NAME))
        .flatMap(app -> app.getChildrenElements(ACTIVITY_ELEMENT_NAME))
        .filter(activity -> activity.getAndroidAttribute(NAME_RESOURCE_ID).isPresent())
        .collect(
            toImmutableListMultimap(
                activity -> activity.getAndroidAttribute(NAME_RESOURCE_ID).get().getValueAsString(),
                identity()));
  }

  public Optional<Integer> getMinSdkVersion() {
    return getUsesSdkAttribute(MIN_SDK_VERSION_RESOURCE_ID);
  }

  public Optional<Integer> getTargetingSdkVersion() {
    return getUsesSdkAttribute(TARGET_SDK_VERSION_RESOURCE_ID);
  }

  public int getEffectiveTargetingSdkVersion() {
    return getTargetingSdkVersion().orElse(getEffectiveMinSdkVersion());
  }

  public int getEffectiveMinSdkVersion() {
    return getMinSdkVersion().orElse(1);
  }

  public Optional<Integer> getMaxSdkVersion() {
    return getUsesSdkAttribute(MAX_SDK_VERSION_RESOURCE_ID);
  }

  /** Returns SDK level range this {@link AndroidManifest} declares as supported. */
  public Range<Integer> getSdkRange() {
    Optional<Integer> maxSdkVersion = getMaxSdkVersion();
    if (maxSdkVersion.isPresent()) {
      return Range.closed(getEffectiveMinSdkVersion(), maxSdkVersion.get());
    } else {
      return Range.atLeast(getEffectiveMinSdkVersion());
    }
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
        .map(
            attribute ->
                isSdkCodename(attribute.getValueAsString())
                    ? DEVELOPMENT_SDK_VERSION
                    : attribute.getValueAsDecimalInteger());
  }

  public ImmutableList<String> getSupportsGlTextures() {
    return getManifestElement()
        .getChildrenElements("supports-gl-texture")
        .map(
            supportsGlTextures ->
                supportsGlTextures
                    .getAndroidAttribute(NAME_RESOURCE_ID)
                    .orElseThrow(
                        () ->
                            InvalidBundleException.createWithUserMessage(
                                "<supports-gl-texture> element is missing the 'android:name'"
                                    + " attribute.")))
        .map(XmlProtoAttribute::getValueAsString)
        .collect(toImmutableList());
  }

  private static boolean isSdkCodename(String sdkVersion) {
    if (sdkVersion.isEmpty()) {
      return false;
    }
    // Codename version can be of the form "[codename]" or "[codename].[fingerprint]".
    int dotIndex = sdkVersion.indexOf('.');
    String codename = dotIndex != -1 ? sdkVersion.substring(0, dotIndex) : sdkVersion;
    return Ints.tryParse(codename) == null;
  }

  public boolean hasApplicationElement() {
    return getManifestElement().getOptionalChildElement(APPLICATION_ELEMENT_NAME).isPresent();
  }

  public Optional<Boolean> getHasCode() {
    return getApplicationAttributeAsBoolean(HAS_CODE_RESOURCE_ID);
  }

  public boolean getEffectiveHasCode() {
    return getHasCode().orElse(true);
  }

  public Optional<Boolean> getIsFeatureSplit() {
    return getManifestElement()
        .getAndroidAttribute(IS_FEATURE_SPLIT_RESOURCE_ID)
        .map(XmlProtoAttribute::getValueAsBoolean);
  }

  private static ModuleType getModuleTypeFromAttributeValue(String value) {
    switch (value) {
      case MODULE_TYPE_FEATURE_VALUE:
        return ModuleType.FEATURE_MODULE;
      case MODULE_TYPE_ASSET_VALUE:
        return ModuleType.ASSET_MODULE;
      case MODULE_TYPE_ML_VALUE:
        return ModuleType.ML_MODULE;
      default:
        throw InvalidBundleException.builder()
            .withUserMessage("Found invalid type attribute %s for <module> element.", value)
            .build();
    }
  }

  public Optional<ModuleType> getOptionalModuleType() {
    Optional<String> typeAttributeValue =
        getManifestElement()
            .getOptionalChildElement(DISTRIBUTION_NAMESPACE_URI, "module")
            .flatMap(module -> module.getAttribute(DISTRIBUTION_NAMESPACE_URI, "type"))
            .map(XmlProtoAttribute::getValueAsString);
    return typeAttributeValue.map(AndroidManifest::getModuleTypeFromAttributeValue);
  }

  public ModuleType getModuleType() {
    // If the module type is not defined in the manifest, default to feature module for backwards
    // compatibility.
    return getOptionalModuleType().orElse(ModuleType.FEATURE_MODULE);
  }

  public Optional<Boolean> getIsModuleIncludedInFusing() {
    return getManifestElement()
        .getOptionalChildElement(DISTRIBUTION_NAMESPACE_URI, "module")
        .flatMap(module -> module.getOptionalChildElement(DISTRIBUTION_NAMESPACE_URI, "fusing"))
        .map(
            fusing -> {
              if (NAMESPACE_ON_INCLUDE_ATTRIBUTE_REQUIRED.enabledForVersion(
                  getBundleToolVersion())) {
                return fusing
                    .getAttribute(DISTRIBUTION_NAMESPACE_URI, "include")
                    .orElseThrow(this::createFusingMissingIncludeAttributeException);
              } else {
                return fusing
                    .getAttributeIgnoringNamespace("include")
                    .orElseThrow(this::createFusingMissingIncludeAttributeException);
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
        .orElseThrow(
            () ->
                InvalidBundleException.createWithUserMessage(
                    "Package name not found in the manifest."))
        .getValueAsString();
  }

  /**
   * Returns the version code.
   *
   * <p>Note: Version code is not present for non-upfront asset slices.
   */
  public Optional<Integer> getVersionCode() {
    return getManifestElement()
        .getAndroidAttribute(VERSION_CODE_RESOURCE_ID)
        .map(XmlProtoAttribute::getValueAsDecimalInteger);
  }

  /** Returns the version name. */
  public Optional<String> getVersionName() {
    return getManifestElement()
        .getAndroidAttribute(VERSION_NAME_RESOURCE_ID)
        .map(XmlProtoAttribute::getValueAsString);
  }

  /** Returns the value of isolatedSplits attribute. */
  public Optional<Boolean> getIsolatedSplits() {
    return getManifestElement()
        .getAndroidAttribute(ISOLATED_SPLITS_ID)
        .map(XmlProtoAttribute::getValueAsBoolean);
  }

  public Optional<String> getSplitId() {
    return getManifestElement().getAttribute("split").map(XmlProtoAttribute::getValueAsString);
  }

  public Optional<Integer> getTitleRefId() {
    return getManifestElement()
        .getOptionalChildElement(DISTRIBUTION_NAMESPACE_URI, "module")
        .flatMap(module -> module.getAttribute(DISTRIBUTION_NAMESPACE_URI, "title"))
        .map(XmlProtoAttribute::getValueAsRefId);
  }

  public ImmutableList<String> getUsesSplits() {
    return getManifestElement()
        .getChildrenElements("uses-split")
        .map(
            usesSplit ->
                usesSplit
                    .getAndroidAttribute(NAME_RESOURCE_ID)
                    .orElseThrow(
                        () ->
                            InvalidBundleException.createWithUserMessage(
                                "<uses-split> element is missing the 'android:name' attribute.")))
        .map(XmlProtoAttribute::getValueAsString)
        .collect(toImmutableList());
  }

  public Optional<XmlProtoAttribute> getOnDemandAttribute() {
    return getManifestElement()
        .getOptionalChildElement(DISTRIBUTION_NAMESPACE_URI, "module")
        .flatMap(
            module -> {
              if (VersionGuardedFeature.NAMESPACE_ON_INCLUDE_ATTRIBUTE_REQUIRED.enabledForVersion(
                  getBundleToolVersion())) {
                return module.getAttribute(DISTRIBUTION_NAMESPACE_URI, "onDemand");
              } else {
                return module.getAttributeIgnoringNamespace("onDemand");
              }
            });
  }

  /**
   * Returns whether the module delivery settings are explicitly declared.
   *
   * <p>This can be done either in the old syntax by specifying dist:onDemand attribute value, or in
   * the new syntax by populating the <dist:delivery> element.
   */
  public boolean isDeliveryTypeDeclared() {
    if (getManifestDeliveryElement().isPresent()) {
      return getManifestDeliveryElement().get().isWellFormed();
    }

    // Legacy syntax.
    return getOnDemandAttribute().isPresent();
  }

  public Optional<Boolean> isInstantModule() {
    if (getInstantManifestDeliveryElement().isPresent()) {
      if (!getModuleType().equals(ModuleType.ASSET_MODULE)) {
        throw InvalidBundleException.builder()
            .withUserMessage("Instant-delivery element is only supported for asset packs.")
            .build();
      }
      return getInstantManifestDeliveryElement().map(ManifestDeliveryElement::isWellFormed);
    }
    return getInstantAttribute();
  }

  public Optional<Boolean> getInstantAttribute() {
    return getManifestElement()
        .getOptionalChildElement(DISTRIBUTION_NAMESPACE_URI, "module")
        .flatMap(module -> module.getAttribute(DISTRIBUTION_NAMESPACE_URI, "instant"))
        .map(XmlProtoAttribute::getValueAsBoolean);
  }

  /**
   * Extracts the 'android:extractNativeLibs' value from the {@code <application>} tag.
   *
   * @return An optional containing the value of the 'extractNativeLibs' attribute if set, or an
   *     empty optional if not set.
   */
  public Optional<Boolean> getExtractNativeLibsValue() {
    return getApplicationAttributeAsBoolean(EXTRACT_NATIVE_LIBS_RESOURCE_ID);
  }

  /** Returns the string value of the 'installLocation' attribute if set. */
  public Optional<String> getInstallLocationValue() {
    return getManifestElement()
        .getAndroidAttribute(INSTALL_LOCATION_RESOURCE_ID)
        .map(XmlProtoAttribute::getValueAsString);
  }

  /**
   * Returns whether the app explicitly defined native activities via searching for all activities
   * that have 'android.app.lib_name' <meta-data>.
   */
  public boolean hasExplicitlyDefinedNativeActivities() {
    return stream(getManifestElement().getOptionalChildElement(APPLICATION_ELEMENT_NAME))
        .flatMap(app -> app.getChildrenElements(ACTIVITY_ELEMENT_NAME))
        .flatMap(activity -> activity.getChildrenElements(META_DATA_ELEMENT_NAME))
        .anyMatch(
            meta ->
                meta.getAndroidAttribute(NAME_RESOURCE_ID)
                    .filter(name -> NATIVE_ACTIVITY_LIB_NAME.equals(name.getValueAsString()))
                    .isPresent());
  }

  public boolean hasLocaleConfig() {
    return getManifestElement()
        .getOptionalChildElement(APPLICATION_ELEMENT_NAME)
        .flatMap(app -> app.getAndroidAttribute(LOCALE_CONFIG_RESOURCE_ID))
        .isPresent();
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

  /** Returns the integer value specified in the meta-data of the given name, if it exists. */
  public Optional<Integer> getMetadataValueAsInteger(String metadataName) {
    return getMetadataAttributeWithName(metadataName)
        .map(XmlProtoAttribute::getValueAsDecimalInteger);
  }

  /** Returns the boolean value specified in the meta-data of the given name, if it exists. */
  public Optional<Boolean> getMetadataValueAsBoolean(String metadataName) {
    return getMetadataAttributeWithName(metadataName).map(XmlProtoAttribute::getValueAsBoolean);
  }

  private Optional<XmlProtoAttribute> getMetadataAttributeWithName(String metadataName) {
    return getMetadataElement(metadataName)
        .map(
            metadataElement ->
                metadataElement
                    .getAndroidAttribute(VALUE_RESOURCE_ID)
                    .orElseThrow(
                        () ->
                            InvalidBundleException.builder()
                                .withUserMessage(
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
                            InvalidBundleException.builder()
                                .withUserMessage(
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
  public Optional<XmlProtoElement> getMetadataElement(String name) {
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
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Found multiple <meta-data> elements for key '%s', expected at most one.", name)
            .build();
    }
  }

  /** Returns the <uses-feature> XML elements with the given "android:name" value. */
  public ImmutableList<XmlProtoElement> getUsesFeatureElement(String name) {
    return getManifestElement()
        .getChildrenElements(USES_FEATURE_ELEMENT_NAME)
        .filter(
            metadataElement ->
                metadataElement
                    .getAndroidAttribute(NAME_RESOURCE_ID)
                    .map(XmlProtoAttribute::getValueAsString)
                    .orElse("")
                    .equals(name))
        .collect(toImmutableList());
  }

  public ModuleDeliveryType getModuleDeliveryType() {
    if (getManifestDeliveryElement().isPresent()) {
      ManifestDeliveryElement manifestDeliveryElement = getManifestDeliveryElement().get();
      if (manifestDeliveryElement.hasInstallTimeElement()) {
        return manifestDeliveryElement.hasModuleConditions()
            ? CONDITIONAL_INITIAL_INSTALL
            : ALWAYS_INITIAL_INSTALL;
      } else {
        return NO_INITIAL_INSTALL;
      }
    }

    // Handling legacy on-demand attribute value.
    if (getOnDemandAttribute().map(XmlProtoAttribute::getValueAsBoolean).orElse(false)) {
      return NO_INITIAL_INSTALL;
    }

    // Legacy onDemand attribute is equal to false or for base module: no delivery information.
    return ALWAYS_INITIAL_INSTALL;
  }

  public ModuleDeliveryType getInstantModuleDeliveryType() {
    if (getInstantManifestDeliveryElement().isPresent()) {
      ManifestDeliveryElement instantManifestDeliveryElement =
          getInstantManifestDeliveryElement().get();
      if (instantManifestDeliveryElement.hasInstallTimeElement()) {
        return instantManifestDeliveryElement.hasModuleConditions()
            ? CONDITIONAL_INITIAL_INSTALL
            : ALWAYS_INITIAL_INSTALL;
      } else {
        return NO_INITIAL_INSTALL;
      }
    }

    // Handling dist:instant attribute value.
    return NO_INITIAL_INSTALL;
  }

  /** Returns a list of the <permission> XML elements. */
  public ImmutableList<XmlProtoElement> getPermissions() {
    return getManifestElement()
        .getChildrenElements(PERMISSION_ELEMENT_NAME)
        .collect(toImmutableList());
  }

  /** Returns a list of the <permission-group> XML elements. */
  public ImmutableList<XmlProtoElement> getPermissionGroups() {
    return getManifestElement()
        .getChildrenElements(PERMISSION_GROUP_ELEMENT_NAME)
        .collect(toImmutableList());
  }

  /** Returns a list of the <permission-tree> XML elements. */
  public ImmutableList<XmlProtoElement> getPermissionTrees() {
    return getManifestElement()
        .getChildrenElements(PERMISSION_TREE_ELEMENT_NAME)
        .collect(toImmutableList());
  }

  public boolean isHeadless() {
    return !hasMainActivity() && !hasMainTvActivity();
  }

  public boolean hasMainActivity() {
    return getActivities().stream()
        .flatMap(activity -> activity.getChildrenElements(INTENT_FILTER_ELEMENT_NAME))
        .anyMatch(
            intent ->
                hasChildWithNameAttribute(intent, ACTION_ELEMENT_NAME, MAIN_ACTION_NAME)
                    && hasChildWithNameAttribute(
                        intent, CATEGORY_ELEMENT_NAME, LAUNCHER_CATEGORY_NAME));
  }

  public boolean hasMainTvActivity() {
    return getActivities().stream()
        .flatMap(activity -> activity.getChildrenElements(INTENT_FILTER_ELEMENT_NAME))
        .anyMatch(
            intent ->
                hasChildWithNameAttribute(intent, ACTION_ELEMENT_NAME, MAIN_ACTION_NAME)
                    && hasChildWithNameAttribute(
                        intent, CATEGORY_ELEMENT_NAME, LEANBACK_LAUNCHER_CATEGORY_NAME));
  }

  private ImmutableList<XmlProtoElement> getActivities() {
    return stream(getManifestElement().getOptionalChildElement(APPLICATION_ELEMENT_NAME))
        .flatMap(app -> app.getChildrenElements(ACTIVITY_ELEMENT_NAME))
        .collect(toImmutableList());
  }

  private static boolean hasChildWithNameAttribute(
      XmlProtoElement element, String childElementName, String nameAttributeValue) {
    return element
        .getChildrenElements(childElementName)
        .anyMatch(
            category ->
                category
                    .getAttribute(ANDROID_NAMESPACE_URI, NAME_ATTRIBUTE_NAME)
                    .map(XmlProtoAttribute::getValueAsString)
                    .orElse("")
                    .equals(nameAttributeValue));
  }

  /** Returns a stream of the <meta-data> XML elements under the <application> tag. */
  private Stream<XmlProtoElement> getMetadataElements() {
    return getManifestElement()
        .getOptionalChildElement(APPLICATION_ELEMENT_NAME)
        .map(applicationElement -> applicationElement.getChildrenElements(META_DATA_ELEMENT_NAME))
        .orElse(Stream.of());
  }

  private InvalidBundleException createFusingMissingIncludeAttributeException() {
    return InvalidBundleException.builder()
        .withUserMessage(
            "<fusing> element is missing the 'include' attribute%s.",
            getSplitId().map(id -> " (split: '" + id + "')").orElse("base"))
        .build();
  }

  public ManifestEditor toEditor() {
    return new ManifestEditor(getManifestRoot(), getBundleToolVersion());
  }

  public boolean hasSharedUserId() {
    return getManifestElement()
        .getAttribute(ANDROID_NAMESPACE_URI, SHARED_USER_ID_ATTRIBUTE_NAME)
        .isPresent();
  }

  private Optional<XmlProtoAttribute> getApplicationAttribute(int resourceId) {
    return getManifestElement()
        .getOptionalChildElement(APPLICATION_ELEMENT_NAME)
        .flatMap(app -> app.getAndroidAttribute(resourceId));
  }

  private Optional<Boolean> getApplicationAttributeAsBoolean(int resourceId) {
    return getApplicationAttribute(resourceId).map(XmlProtoAttribute::getValueAsBoolean);
  }

  public boolean hasApplicationAttribute(int resourceId) {
    return getApplicationAttribute(resourceId).isPresent();
  }

  public ImmutableList<XmlProtoElement> getSdkLibraryElements() {
    return getApplicationElementChildElements(SDK_LIBRARY_ELEMENT_NAME);
  }

  /**
   * Gets the SDK patch version if it is set in the AndroidManifest. If there are multiple
   * <property> elements with android:name={@value SDK_PATCH_VERSION_ATTRIBUTE_NAME}, return the
   * first one.
   */
  public Optional<Integer> getSdkPatchVersionProperty() {
    return getPropertyValue(SDK_PATCH_VERSION_ATTRIBUTE_NAME)
        .map(XmlProtoAttribute::getValueAsInteger);
  }

  /**
   * Gets the platform SDK provider class name if it is set in the AndroidManifest. If there are
   * multiple <property> elements with android:name={@value SDK_PROVIDER_CLASS_NAME_ATTRIBUTE_NAME},
   * return the first one.
   */
  public Optional<String> getSdkProviderClassNameProperty() {
    return getPropertyValue(SDK_PROVIDER_CLASS_NAME_ATTRIBUTE_NAME)
        .map(XmlProtoAttribute::getValueAsString);
  }

  /**
   * Gets the compat SDK provider class name if it is set in the AndroidManifest. If there are
   * multiple <property> elements with android:name={@value
   * COMPAT_SDK_PROVIDER_CLASS_NAME_ATTRIBUTE_NAME}, return the first one.
   */
  public Optional<String> getCompatSdkProviderClassNameProperty() {
    return getPropertyValue(COMPAT_SDK_PROVIDER_CLASS_NAME_ATTRIBUTE_NAME)
        .map(XmlProtoAttribute::getValueAsString);
  }

  /** Gets the AppComponentFactory class name if it is set in the AndroidManifest. */
  public Optional<String> getAppComponentFactoryAttribute() {
    return getApplicationAttribute(APP_COMPONENT_FACTORY_RESOURCE_ID)
        .map(XmlProtoAttribute::getValueAsString);
  }

  /** Gets the Authorities class name if it is set in the AndroidManifest. */
  public Optional<String> getAuthoritiesAttribute() {
    return getApplicationAttribute(AUTHORITIES_RESOURCE_ID)
        .map(XmlProtoAttribute::getValueAsString);
  }

  private Optional<XmlProtoAttribute> getPropertyValue(String attributeName) {
    return getApplicationElementChildElements(PROPERTY_ELEMENT_NAME).stream()
        .filter(element -> element.getAndroidAttribute(NAME_RESOURCE_ID).isPresent())
        .filter(
            element ->
                element
                    .getAndroidAttribute(NAME_RESOURCE_ID)
                    .get()
                    .getValueAsString()
                    .equals(attributeName))
        .map(
            element ->
                element
                    .getAndroidAttribute(VALUE_RESOURCE_ID)
                    .orElseThrow(
                        () ->
                            InvalidBundleException.createWithUserMessage(
                                "<property> element found with"
                                    + " android:name='"
                                    + attributeName
                                    + "' but no"
                                    + " 'android:value'.")))
        .findFirst();
  }

  public ImmutableList<XmlProtoElement> getUsesSdkLibraryElements() {
    return getApplicationElementChildElements(USES_SDK_LIBRARY_ELEMENT_NAME);
  }

  private ImmutableList<XmlProtoElement> getApplicationElementChildElements(
      String childElementName) {
    return getManifestRoot()
        .getElement()
        .getChildElement(APPLICATION_ELEMENT_NAME)
        .getChildrenElements()
        .filter(element -> element.getName().equals(childElementName))
        .collect(toImmutableList());
  }

  /** Checks whether any component elements are declared. */
  public boolean hasComponents() {
    ImmutableSet<String> componentNames =
        ImmutableSet.of(
            ACTIVITY_ELEMENT_NAME,
            SERVICE_ELEMENT_NAME,
            PROVIDER_ELEMENT_NAME,
            RECEIVER_ELEMENT_NAME);
    return stream(getManifestElement().getOptionalChildElement(APPLICATION_ELEMENT_NAME))
        .flatMap(app -> app.getChildrenElements())
        .anyMatch(component -> componentNames.contains(component.getName()));
  }

  public Optional<XmlProtoAttribute> getIconAttribute() {
    return getManifestRoot()
        .getElement()
        .getChildElement(APPLICATION_ELEMENT_NAME)
        .getAndroidAttribute(ICON_RESOURCE_ID);
  }

  public Optional<XmlProtoAttribute> getRoundIconAttribute() {
    return getManifestRoot()
        .getElement()
        .getChildElement(APPLICATION_ELEMENT_NAME)
        .getAndroidAttribute(ROUND_ICON_RESOURCE_ID);
  }
}
