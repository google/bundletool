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

import static com.android.tools.build.bundletool.model.BundleModule.ASSETS_DIRECTORY;
import static com.android.tools.build.bundletool.model.BundleModule.DEX_DIRECTORY;
import static com.android.tools.build.bundletool.model.BundleModule.LIB_DIRECTORY;
import static com.android.tools.build.bundletool.model.BundleModule.RESOURCES_DIRECTORY;
import static com.android.tools.build.bundletool.model.BundleModule.ROOT_DIRECTORY;
import static com.android.tools.build.bundletool.utils.ResourcesUtils.SCREEN_DENSITY_TO_PROTO_VALUE_MAP;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.MoreCollectors.toOptional;

import com.android.aapt.Resources.ResourceTable;
import com.android.bundle.Files.Assets;
import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.Targeting.AbiTargeting;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.GraphicsApi;
import com.android.bundle.Targeting.GraphicsApiTargeting;
import com.android.bundle.Targeting.OpenGlVersion;
import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.bundle.Targeting.TextureCompressionFormatTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.manifest.AndroidManifest;
import com.android.tools.build.bundletool.utils.ResourcesUtils;
import com.android.tools.build.bundletool.utils.Versions;
import com.google.auto.value.AutoValue;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Int32Value;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Stream;
import javax.annotation.CheckReturnValue;

/** A module split is a subset of a bundle module. */
@AutoValue
public abstract class ModuleSplit {

  /** Returns the targeting of the APK represented by this instance. */
  public abstract ApkTargeting getApkTargeting();

  /** Returns the targeting of the Variant this instance belongs to. */
  public abstract VariantTargeting getVariantTargeting();

  /** Whether this ModuleSplit instance represents a standalone APK. */
  public abstract boolean isStandalone();

  /**
   * Returns AppBundle's ZipEntries copied to be included in this split.
   *
   * @return entries representing files copied from the bundle in this split. They exclude special
   *     files such as: manifest and resources which are generated explicitly. The keys are paths
   *     inside the module, as opposed to the original bundle's ZipEntry names.
   */
  public abstract ImmutableList<ModuleEntry> getEntries();

  public abstract Optional<ResourceTable> getResourceTable();

  public abstract Optional<AndroidManifest> getAndroidManifest();

  public abstract BundleModuleName getModuleName();

  public abstract boolean isMasterSplit();

  public abstract Optional<NativeLibraries> getNativeConfig();

  public abstract Optional<Assets> getAssetsConfig();

  public abstract Builder toBuilder();

  /** Returns true iff this is split of the base module. */
  public boolean isBaseModuleSplit() {
    return getModuleName().getName().equals(BundleModuleName.BASE_MODULE_NAME);
  }

  /**
   * Returns the split suffix base name based on the targeting.
   *
   * <p>The split suffix cannot contain dashes as they are not allowed by the Android Framework. We
   * replace them with underscores instead.
   */
  public String getSuffix() {
    StringJoiner suffixJoiner = new StringJoiner("_");

    // The dimensions below should be ordered by their priority.

    AbiTargeting abiTargeting = getApkTargeting().getAbiTargeting();
    if (!abiTargeting.getValueList().isEmpty()) {
      abiTargeting
          .getValueList()
          .forEach(
              value ->
                  suffixJoiner.add(
                      AbiName.fromProto(value.getAlias()).getPlatformName().replace('-', '_')));
    } else if (!abiTargeting.getAlternativesList().isEmpty()) {
      suffixJoiner.add("other_abis");
    }

    getApkTargeting().getLanguageTargeting().getValueList().forEach(suffixJoiner::add);

    getApkTargeting()
        .getScreenDensityTargeting()
        .getValueList()
        .forEach(
            value ->
                suffixJoiner.add(
                    SCREEN_DENSITY_TO_PROTO_VALUE_MAP
                        .inverse()
                        .get(value.getDensityAlias())
                        .replace('-', '_')));

    GraphicsApiTargeting graphicsApiTargeting = getApkTargeting().getGraphicsApiTargeting();
    if (!graphicsApiTargeting.getValueList().isEmpty()) {
      graphicsApiTargeting
          .getValueList()
          .forEach(value -> suffixJoiner.add(formatGraphicsApi(value)));
    } else if (!graphicsApiTargeting.getAlternativesList().isEmpty()) {
      suffixJoiner.add("other_gl");
    }

    TextureCompressionFormatTargeting textureFormatTargeting =
        getApkTargeting().getTextureCompressionFormatTargeting();
    if (!textureFormatTargeting.getValueList().isEmpty()) {
      textureFormatTargeting
          .getValueList()
          .forEach(value -> suffixJoiner.add(value.getAlias().name().toLowerCase()));
    } else if (!textureFormatTargeting.getAlternativesList().isEmpty()) {
      suffixJoiner.add("other_tcf");
    }

    return suffixJoiner.toString();
  }

  private static String formatGraphicsApi(GraphicsApi graphicsTargeting) {
    StringJoiner result = new StringJoiner("_");
    if (graphicsTargeting.hasMinOpenGlVersion()) {
      result.add("gl" + formatGlVersion(graphicsTargeting.getMinOpenGlVersion()));
    }
    return result.toString();
  }

  private static String formatGlVersion(OpenGlVersion glVersion) {
    // Will treat missing minor as 0 which is fine.
    return glVersion.getMajor() + "_" + glVersion.getMinor();
  }

  /** Filters out any entries not referenced in the given resource table. */
  public static ImmutableList<ModuleEntry> filterResourceEntries(
      ImmutableList<ModuleEntry> entries, ResourceTable resourceTable) {
    ImmutableSet<ZipPath> referencedPaths = ResourcesUtils.getAllFileReferences(resourceTable);
    return entries
        .stream()
        .filter(entry -> referencedPaths.contains(entry.getPath()))
        .collect(toImmutableList());
  }

  /** Writes the final manifest that reflects the Split ID. */
  @CheckReturnValue
  public ModuleSplit writeSplitIdInManifest(String resolvedSplitIdSuffix) {
    checkArgument(getAndroidManifest().isPresent(), "Missing Android Manifest");
    AndroidManifest moduleManifest = getAndroidManifest().get();
    String splitId = generateSplitId(resolvedSplitIdSuffix);
    AndroidManifest apkManifest;
    if (isMasterSplit()) {
      apkManifest = moduleManifest.toEditor().setSplitIdForFeatureSplit(splitId).save();
    } else {
      apkManifest =
          AndroidManifest.createForConfigSplit(
              moduleManifest.getPackageName(),
              moduleManifest.getVersionCode(),
              splitId,
              getSplitIdForMasterSplit(),
              moduleManifest.getExtractNativeLibsValue());
    }
    return toBuilder().setAndroidManifest(apkManifest).build();
  }

  private String generateSplitId(String resolvedSuffix) {
    String masterSplitId = getSplitIdForMasterSplit();
    if (isMasterSplit()) {
      return masterSplitId;
    }
    StringBuilder splitIdBuilder = new StringBuilder(masterSplitId);
    if (splitIdBuilder.length() > 0) {
      splitIdBuilder.append(".");
    }
    return splitIdBuilder.append("config.").append(resolvedSuffix).toString();
  }

  private String getSplitIdForMasterSplit() {
    return getModuleName().getNameForSplitId();
  }

  /**
   * Creates a builder to construct the {@link ModuleSplit}.
   *
   * <p>Prefer static factory methods when creating {@link ModuleSplit} from {@link BundleModule}.
   */
  public static Builder builder() {
    return new AutoValue_ModuleSplit.Builder().setEntries(ImmutableList.of()).setStandalone(false);
  }

  /**
   * Creates a {@link ModuleSplit} with all entries from the {@link BundleModule} valid directories.
   */
  public static ModuleSplit forModule(BundleModule bundleModule) {
    return fromBundleModule(bundleModule, Predicates.alwaysTrue(), /* setResourceTable= */ true);
  }

  /** Creates a {@link ModuleSplit} only with the resources entries with empty targeting. */
  public static ModuleSplit forResources(BundleModule bundleModule) {
    return fromBundleModule(
        bundleModule,
        entry -> entry.getPath().startsWith(RESOURCES_DIRECTORY),
        /* setResourceTable= */ true);
  }

  /** Creates a {@link ModuleSplit} only with the assets entries with empty targeting. */
  public static ModuleSplit forAssets(BundleModule bundleModule) {
    return fromBundleModule(
        bundleModule,
        entry -> entry.getPath().startsWith(ASSETS_DIRECTORY),
        /* setResourceTable= */ false);
  }

  /** Creates a {@link ModuleSplit} only with the native library entries with empty targeting. */
  public static ModuleSplit forNativeLibraries(BundleModule bundleModule) {
    return fromBundleModule(
        bundleModule,
        entry -> entry.getPath().startsWith(LIB_DIRECTORY),
        /* setResourceTable= */ false);
  }

  public static ModuleSplit forCode(BundleModule bundleModule) {
    return fromBundleModule(
        bundleModule,
        entry -> entry.getPath().startsWith(DEX_DIRECTORY),
        /* setResourceTable= */ false);
  }

  public static ModuleSplit forRoot(BundleModule bundleModule) {
    return fromBundleModule(
        bundleModule,
        entry -> entry.getPath().startsWith(ROOT_DIRECTORY),
        /* setResourceTable= */ false);
  }

  /**
   * Creates a {@link ModuleSplit} with entries from the Bundle Module satisfying the predicate.
   *
   * <p>The created instance is not standalone thus its variant targets L+ devices initially.
   */
  private static ModuleSplit fromBundleModule(
      BundleModule bundleModule,
      Predicate<ModuleEntry> entriesPredicate,
      boolean setResourceTable) {
    ModuleSplit.Builder splitBuilder =
        builder()
            .setModuleName(bundleModule.getName())
            .setEntries(
                bundleModule
                    .getEntries()
                    .stream()
                    .filter(entriesPredicate)
                    .collect(toImmutableList()))
            .setAndroidManifest(bundleModule.getAndroidManifest())
            // Initially each split is master split.
            .setMasterSplit(true)
            .setStandalone(false)
            .setApkTargeting(ApkTargeting.getDefaultInstance())
            .setVariantTargeting(lPlusVariantTargeting());

    bundleModule.getNativeConfig().ifPresent(splitBuilder::setNativeConfig);
    bundleModule.getAssetsConfig().ifPresent(splitBuilder::setAssetsConfig);
    if (setResourceTable) {
      bundleModule.getResourceTable().ifPresent(splitBuilder::setResourceTable);
    }
    return splitBuilder.build();
  }

  /** Returns all {@link ModuleEntry} that have a relative module path under a given path. */
  public Stream<ModuleEntry> findEntriesUnderPath(String path) {
    return getEntries().stream().filter(entry -> entry.getPath().startsWith(path));
  }

  /**
   * Returns all {@link ModuleEntry} living directly under a given relative module directory path.
   *
   * <p>Entries inside subdirectories relative to the given directory are not returned.
   */
  public Stream<ModuleEntry> findEntriesInsideDirectory(String directory) {
    return getEntries()
        .stream()
        .filter(entry -> entry.getPath().getParent().equals(ZipPath.create(directory)));
  }

  /** Returns the {@link ModuleEntry} associated with the given path, or empty if not found. */
  public Optional<ModuleEntry> findEntry(String path) {
    return getEntries()
        .stream()
        .filter(entry -> entry.getPath().equals(ZipPath.create(path)))
        .collect(toOptional());
  }

  private static VariantTargeting lPlusVariantTargeting() {
    return VariantTargeting.newBuilder()
        .setSdkVersionTargeting(
            SdkVersionTargeting.newBuilder()
                .addValue(
                    SdkVersion.newBuilder()
                        .setMin(Int32Value.newBuilder().setValue(Versions.ANDROID_L_API_VERSION))))
        .build();
  }

  /** Builder for {@link ModuleSplit}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setModuleName(BundleModuleName moduleName);

    public abstract Builder setMasterSplit(boolean isMasterSplit);

    public abstract Builder setNativeConfig(NativeLibraries nativeConfig);

    public abstract Builder setAssetsConfig(Assets assetsConfig);

    public abstract Builder setApkTargeting(ApkTargeting targeting);

    public abstract Builder setVariantTargeting(VariantTargeting targeting);

    public abstract Builder setStandalone(boolean isStandalone);

    public abstract Builder setEntries(List<ModuleEntry> entries);

    public abstract Builder setResourceTable(ResourceTable resourceTable);

    public abstract Builder setAndroidManifest(AndroidManifest androidManifest);

    protected abstract ModuleSplit autoBuild();

    public ModuleSplit build() {
      ModuleSplit moduleSplit = autoBuild();
      if (moduleSplit.isMasterSplit()) {
        checkState(
            moduleSplit
                .getApkTargeting()
                .toBuilder()
                .clearSdkVersionTargeting()
                .build()
                .equals(ApkTargeting.getDefaultInstance()),
            "Master split cannot have any targeting other than SDK version.");
      }
      return moduleSplit;
    }
  }
}
