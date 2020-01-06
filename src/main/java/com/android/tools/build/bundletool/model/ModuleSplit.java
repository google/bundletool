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

import static com.android.tools.build.bundletool.model.BundleModule.APEX_DIRECTORY;
import static com.android.tools.build.bundletool.model.BundleModule.ASSETS_DIRECTORY;
import static com.android.tools.build.bundletool.model.BundleModule.DEX_DIRECTORY;
import static com.android.tools.build.bundletool.model.BundleModule.LIB_DIRECTORY;
import static com.android.tools.build.bundletool.model.BundleModule.RESOURCES_DIRECTORY;
import static com.android.tools.build.bundletool.model.BundleModule.ROOT_DIRECTORY;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.SCREEN_DENSITY_TO_PROTO_VALUE_MAP;
import static com.android.tools.build.bundletool.model.utils.TargetingNormalizer.normalizeApkTargeting;
import static com.android.tools.build.bundletool.model.utils.TargetingNormalizer.normalizeVariantTargeting;
import static com.android.tools.build.bundletool.model.utils.TargetingProtoUtils.lPlusVariantTargeting;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.MoreCollectors.toOptional;

import com.android.aapt.Resources.ResourceTable;
import com.android.bundle.Files.ApexImages;
import com.android.bundle.Files.Assets;
import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.Targeting.Abi;
import com.android.bundle.Targeting.AbiTargeting;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.GraphicsApi;
import com.android.bundle.Targeting.GraphicsApiTargeting;
import com.android.bundle.Targeting.LanguageTargeting;
import com.android.bundle.Targeting.MultiAbi;
import com.android.bundle.Targeting.MultiAbiTargeting;
import com.android.bundle.Targeting.OpenGlVersion;
import com.android.bundle.Targeting.Sanitizer;
import com.android.bundle.Targeting.Sanitizer.SanitizerAlias;
import com.android.bundle.Targeting.SanitizerTargeting;
import com.android.bundle.Targeting.TextureCompressionFormatTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.bundle.Targeting.VulkanVersion;
import com.android.tools.build.bundletool.model.BundleModule.ModuleType;
import com.android.tools.build.bundletool.model.utils.ResourcesUtils;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.errorprone.annotations.Immutable;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Stream;
import javax.annotation.CheckReturnValue;

/** A module split is a subset of a bundle module. */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class ModuleSplit {

  private static final Joiner MULTI_ABI_SUFFIX_JOINER = Joiner.on('.');

  /** The split type being represented by this split. */
  public enum SplitType {
    STANDALONE,
    SYSTEM,
    SPLIT,
    INSTANT,
    ASSET_SLICE,
  }

  /**
   * Returns the targeting of the APK represented by this instance.
   *
   * <p>Order of repeated all repeated fields is guaranteed to be deterministic.
   */
  public abstract ApkTargeting getApkTargeting();

  /**
   * Returns the targeting of the Variant this instance belongs to.
   *
   * <p>Order of repeated all repeated fields is guaranteed to be deterministic.
   */
  public abstract VariantTargeting getVariantTargeting();

  /** Whether this ModuleSplit instance represents a standalone, split, instant or system apk. */
  public abstract SplitType getSplitType();

  /**
   * Returns AppBundle's ZipEntries copied to be included in this split.
   *
   * @return entries representing files copied from the bundle in this split. They exclude special
   *     files such as: manifest and resources which are generated explicitly. The keys are paths
   *     inside the module, as opposed to the original bundle's ZipEntry names.
   */
  public abstract ImmutableList<ModuleEntry> getEntries();

  public abstract Optional<ResourceTable> getResourceTable();

  public abstract AndroidManifest getAndroidManifest();

  public abstract ImmutableList<ManifestMutator> getMasterManifestMutators();

  public abstract BundleModuleName getModuleName();

  public abstract boolean isMasterSplit();

  public abstract Optional<NativeLibraries> getNativeConfig();

  public abstract Optional<Assets> getAssetsConfig();

  /** The module APEX configuration - what system images it contains and with what targeting. */
  public abstract Optional<ApexImages> getApexConfig();

  public abstract Builder toBuilder();

  /** Returns true iff this is split of the base module. */
  public boolean isBaseModuleSplit() {
    return getModuleName().equals(BundleModuleName.BASE_MODULE_NAME);
  }

  /**
   * Returns the split suffix base name based on the targeting.
   *
   * <p>The split suffix cannot contain dashes as they are not allowed by the Android Framework. We
   * replace them with underscores instead.
   */
  public String getSuffix() {
    if (isMasterSplit()) {
      return "";
    }

    StringJoiner suffixJoiner = new StringJoiner("_");

    // The dimensions below should be ordered by their priority.

    AbiTargeting abiTargeting = getApkTargeting().getAbiTargeting();
    if (!abiTargeting.getValueList().isEmpty()) {
      abiTargeting.getValueList().forEach(value -> suffixJoiner.add(formatAbi(value)));
    } else if (!abiTargeting.getAlternativesList().isEmpty()) {
      suffixJoiner.add("other_abis");
    }

    MultiAbiTargeting multiAbiTargeting = getApkTargeting().getMultiAbiTargeting();
    for (MultiAbi value : multiAbiTargeting.getValueList()) {
      suffixJoiner.add(
          MULTI_ABI_SUFFIX_JOINER.join(
              value.getAbiList().stream().map(ModuleSplit::formatAbi).collect(toImmutableList())));
    }
    // Alternatives without values are not supported for MultiAbiTargeting.

    SanitizerTargeting sanitizerTargeting = getApkTargeting().getSanitizerTargeting();
    for (Sanitizer sanitizer : sanitizerTargeting.getValueList()) {
      if (sanitizer.getAlias().equals(SanitizerAlias.HWADDRESS)) {
        suffixJoiner.add("hwasan");
      } else {
        throw new IllegalArgumentException("Unknown sanitizer");
      }
    }

    LanguageTargeting languageTargeting = getApkTargeting().getLanguageTargeting();
    if (!languageTargeting.getValueList().isEmpty()) {
      languageTargeting.getValueList().forEach(suffixJoiner::add);
    } else if (!languageTargeting.getAlternativesList().isEmpty()) {
      suffixJoiner.add("other_lang");
    }

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
      suffixJoiner.add("other_gfx");
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

  private static String formatAbi(Abi abi) {
    return AbiName.fromProto(abi.getAlias()).getPlatformName().replace('-', '_');
  }

  private static String formatGraphicsApi(GraphicsApi graphicsTargeting) {
    StringJoiner result = new StringJoiner("_");
    if (graphicsTargeting.hasMinOpenGlVersion()) {
      result.add("gl" + formatGlVersion(graphicsTargeting.getMinOpenGlVersion()));
    } else if (graphicsTargeting.hasMinVulkanVersion()) {
      result.add("vk" + formatVulkanVersion(graphicsTargeting.getMinVulkanVersion()));
    }
    return result.toString();
  }

  private static String formatVulkanVersion(VulkanVersion vulkanVersion) {
    // Will treat missing minor as 0 which is fine.
    return vulkanVersion.getMajor() + "_" + vulkanVersion.getMinor();
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

  /** Removes the {@code splitName} attribute from elements in the manifest. */
  @CheckReturnValue
  public ModuleSplit removeSplitName() {
    AndroidManifest apkManifest = getAndroidManifest().toEditor().removeSplitName().save();
    return toBuilder().setAndroidManifest(apkManifest).build();
  }

  public ModuleSplit removeUnknownSplitComponents(ImmutableSet<String> knownSplits) {
    AndroidManifest apkManifest =
        getAndroidManifest().toEditor().removeUnknownSplitComponents(knownSplits).save();
    return toBuilder().setAndroidManifest(apkManifest).build();
  }

  /** Writes the final manifest that reflects the Split ID. */
  @CheckReturnValue
  public ModuleSplit writeSplitIdInManifest(String resolvedSplitIdSuffix) {
    AndroidManifest moduleManifest = getAndroidManifest();
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
    return new AutoValue_ModuleSplit.Builder()
        .setEntries(ImmutableList.of())
        .setSplitType(SplitType.SPLIT);
  }

  /**
   * Creates a {@link ModuleSplit} with all entries from the {@link BundleModule} valid directories.
   *
   * <p>The generated ModuleSplit has empty APK targeting and default L+ variant targeting.
   */
  public static ModuleSplit forModule(BundleModule bundleModule) {
    return forModule(bundleModule, lPlusVariantTargeting());
  }

  /**
   * Creates a {@link ModuleSplit} with all entries from the {@link BundleModule} valid directories
   * with empty APK targeting and given variant targeting.
   */
  public static ModuleSplit forModule(
      BundleModule bundleModule, VariantTargeting variantTargeting) {
    return fromBundleModule(
        bundleModule, Predicates.alwaysTrue(), /* setResourceTable= */ true, variantTargeting);
  }

  /**
   * Creates a {@link ModuleSplit} only with the resources entries with empty APK targeting and
   * default L+ variant targeting.
   */
  public static ModuleSplit forResources(BundleModule bundleModule) {
    return forResources(bundleModule, lPlusVariantTargeting());
  }

  /**
   * Creates a {@link ModuleSplit} only with the resources entries with empty APK targeting and
   * given variant targeting.
   */
  public static ModuleSplit forResources(
      BundleModule bundleModule, VariantTargeting variantTargeting) {
    return fromBundleModule(
        bundleModule,
        entry -> entry.getPath().startsWith(RESOURCES_DIRECTORY),
        /* setResourceTable= */ true,
        variantTargeting);
  }

  /**
   * Creates a {@link ModuleSplit} only with the assets entries with empty APK targeting and default
   * L+ variant targeting.
   */
  public static ModuleSplit forAssets(BundleModule bundleModule) {
    return forAssets(bundleModule, lPlusVariantTargeting());
  }

  /**
   * Creates a {@link ModuleSplit} only with the assets entries with empty APK targeting and given
   * variant targeting.
   */
  public static ModuleSplit forAssets(
      BundleModule bundleModule, VariantTargeting variantTargeting) {
    return fromBundleModule(
        bundleModule,
        entry -> entry.getPath().startsWith(ASSETS_DIRECTORY),
        /* setResourceTable= */ false,
        variantTargeting);
  }

  /**
   * Creates a {@link ModuleSplit} only with the native library entries with empty APK targeting and
   * default L+ variant targeting.
   */
  public static ModuleSplit forNativeLibraries(BundleModule bundleModule) {
    return forNativeLibraries(bundleModule, lPlusVariantTargeting());
  }

  /**
   * Creates a {@link ModuleSplit} only with the native library entries with empty APK targeting and
   * given variant targeting.
   */
  public static ModuleSplit forNativeLibraries(
      BundleModule bundleModule, VariantTargeting variantTargeting) {
    return fromBundleModule(
        bundleModule,
        entry -> entry.getPath().startsWith(LIB_DIRECTORY),
        /* setResourceTable= */ false,
        variantTargeting);
  }

  /**
   * Creates a {@link ModuleSplit} only with the dex files with empty APK targeting and default L+
   * variant targeting.
   */
  public static ModuleSplit forDex(BundleModule bundleModule) {
    return forDex(bundleModule, lPlusVariantTargeting());
  }

  /**
   * Creates a {@link ModuleSplit} only with the dex files with empty APK targeting and given
   * variant targeting.
   */
  public static ModuleSplit forDex(BundleModule bundleModule, VariantTargeting variantTargeting) {
    return fromBundleModule(
        bundleModule,
        entry -> entry.getPath().startsWith(DEX_DIRECTORY),
        /* setResourceTable= */ false,
        variantTargeting);
  }

  public static ModuleSplit forRoot(BundleModule bundleModule) {
    return forRoot(bundleModule, lPlusVariantTargeting());
  }

  public static ModuleSplit forRoot(BundleModule bundleModule, VariantTargeting variantTargeting) {
    return fromBundleModule(
        bundleModule,
        entry -> entry.getPath().startsWith(ROOT_DIRECTORY),
        /* setResourceTable= */ false,
        variantTargeting);
  }

  /**
   * Creates a {@link ModuleSplit} only with the apex image entries with empty APK targeting and
   * default L+ variant targeting.
   */
  public static ModuleSplit forApex(BundleModule bundleModule) {
    return fromBundleModule(
        bundleModule,
        entry -> entry.getPath().startsWith(APEX_DIRECTORY),
        /* setResourceTable= */ false,
        lPlusVariantTargeting());
  }

  /**
   * Creates a {@link ModuleSplit} with entries from the Bundle Module satisfying the predicate with
   * a given variant targeting.
   *
   * <p>The created instance is not standalone thus its variant targets L+ devices initially.
   */
  private static ModuleSplit fromBundleModule(
      BundleModule bundleModule,
      Predicate<ModuleEntry> entriesPredicate,
      boolean setResourceTable,
      VariantTargeting variantTargeting) {
    ModuleSplit.Builder splitBuilder =
        builder()
            .setModuleName(bundleModule.getName())
            .setEntries(
                bundleModule.getEntries().stream()
                    .filter(entriesPredicate)
                    .collect(toImmutableList()))
            .setAndroidManifest(bundleModule.getAndroidManifest())
            // Initially each split is master split.
            .setMasterSplit(true)
            .setSplitType(getSplitTypeFromModuleType(bundleModule.getModuleType()))
            .setApkTargeting(ApkTargeting.getDefaultInstance())
            .setVariantTargeting(variantTargeting);

    bundleModule.getNativeConfig().ifPresent(splitBuilder::setNativeConfig);
    bundleModule.getAssetsConfig().ifPresent(splitBuilder::setAssetsConfig);
    bundleModule.getApexConfig().ifPresent(splitBuilder::setApexConfig);
    if (setResourceTable) {
      bundleModule.getResourceTable().ifPresent(splitBuilder::setResourceTable);
    }
    return splitBuilder.build();
  }

  private static SplitType getSplitTypeFromModuleType(ModuleType moduleType) {
    switch (moduleType) {
      case FEATURE_MODULE:
        return SplitType.SPLIT;
      case ASSET_MODULE:
        return SplitType.ASSET_SLICE;
    }
    throw new IllegalStateException();
  }

  @Memoized
  Multimap<ZipPath, ModuleEntry> getEntriesByDirectory() {
    return Multimaps.index(getEntries(), entry -> entry.getPath().getParent());
  }

  /** Returns all {@link ModuleEntry} that are directly inside the specified directory. */
  public Stream<ModuleEntry> getEntriesInDirectory(ZipPath directory) {
    checkArgument(directory.getNameCount() > 0, "ZipPath '%s' is empty", directory);
    return getEntriesByDirectory().get(directory).stream();
  }

  /**
   * Returns all {@link ModuleEntry} that have a relative module path under a given path.
   *
   * <p>Note: Consider using {@link #getEntriesByDirectory()} for performance, unless a recursive
   * search is truly needed.
   */
  public Stream<ModuleEntry> findEntriesUnderPath(String path) {
    ZipPath zipPath = ZipPath.create(path);
    return findEntriesUnderPath(zipPath);
  }

  /**
   * Returns all {@link ModuleEntry} that have a relative module path under a given path.
   *
   * <p>Note: Consider using {@link #getEntriesByDirectory()} for performance, unless a recursive
   * search is truly needed.
   */
  public Stream<ModuleEntry> findEntriesUnderPath(ZipPath zipPath) {
    return getEntriesByDirectory().asMap().entrySet().stream()
        .filter(dirAndEntries -> dirAndEntries.getKey().startsWith(zipPath))
        .flatMap(dirAndEntries -> dirAndEntries.getValue().stream());
  }

  /** Returns the {@link ModuleEntry} associated with the given path, or empty if not found. */
  public Optional<ModuleEntry> findEntry(ZipPath path) {
    return getEntriesInDirectory(path.getParent())
        .filter(entry -> entry.getPath().equals(path))
        .collect(toOptional());
  }

  /** Returns the {@link ModuleEntry} associated with the given path, or empty if not found. */
  public Optional<ModuleEntry> findEntry(String path) {
    return findEntry(ZipPath.create(path));
  }

  public boolean isApex() {
    return getApexConfig().isPresent();
  }

  /** Builder for {@link ModuleSplit}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setModuleName(BundleModuleName moduleName);

    public abstract Builder setMasterSplit(boolean isMasterSplit);

    public abstract Builder setNativeConfig(NativeLibraries nativeConfig);

    public abstract Builder setAssetsConfig(Assets assetsConfig);

    /**
     * Sets the module APEX configuration - what system images it contains and with what targeting.
     */
    public abstract Builder setApexConfig(ApexImages apexConfig);

    protected abstract ApkTargeting getApkTargeting();

    public abstract Builder setApkTargeting(ApkTargeting targeting);

    protected abstract VariantTargeting getVariantTargeting();

    public abstract Builder setVariantTargeting(VariantTargeting targeting);

    public abstract Builder setSplitType(SplitType splitType);

    public abstract Builder setEntries(List<ModuleEntry> entries);

    public abstract Builder setResourceTable(ResourceTable resourceTable);

    public abstract Builder setAndroidManifest(AndroidManifest androidManifest);

    abstract ImmutableList.Builder<ManifestMutator> masterManifestMutatorsBuilder();

    public Builder addMasterManifestMutator(ManifestMutator manifestMutator) {
      masterManifestMutatorsBuilder().add(manifestMutator);
      return this;
    }

    protected abstract ModuleSplit autoBuild();

    public ModuleSplit build() {
      ModuleSplit moduleSplit =
          this.setApkTargeting(normalizeApkTargeting(getApkTargeting()))
              .setVariantTargeting(normalizeVariantTargeting(getVariantTargeting()))
              .autoBuild();
      // For system splits the master split is formed by fusing Screen Density, Abi, Language
      // splits, hence it might have Abi, Screen Density, Language targeting set.
      if (moduleSplit.isMasterSplit() && !moduleSplit.getSplitType().equals(SplitType.SYSTEM)) {
        checkState(
            moduleSplit.getApkTargeting().toBuilder()
                .clearSdkVersionTargeting()
                // TCF can be set on standalone/universal APKs: if suffix stripping was enabled,
                // a default targeting suffix was used.
                .clearTextureCompressionFormatTargeting()
                .build()
                .equals(ApkTargeting.getDefaultInstance()),
            "Master split cannot have any targeting other than SDK version or Texture"
                + "Compression Format.");
      }
      return moduleSplit;
    }
  }
}
