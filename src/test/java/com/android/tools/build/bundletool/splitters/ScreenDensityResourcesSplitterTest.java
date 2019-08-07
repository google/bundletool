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

package com.android.tools.build.bundletool.splitters;

import static com.android.tools.build.bundletool.model.ManifestMutator.withSplitsRequired;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.DEFAULT_DENSITY_VALUE;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.HDPI_VALUE;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.LDPI_VALUE;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.MDPI_VALUE;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.TVDPI_VALUE;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.XHDPI_VALUE;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.XXHDPI_VALUE;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.XXXHDPI_VALUE;
import static com.android.tools.build.bundletool.splitters.ScreenDensityResourcesSplitter.DEFAULT_DENSITY_BUCKETS;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.compareManifestMutators;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.HDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.LDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.MDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.TVDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.USER_PACKAGE_OFFSET;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.XHDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.XXHDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.XXXHDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory._560DPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.entry;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.fileReference;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.forDpi;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.mergeConfigs;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.pkg;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.resourceTable;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.sdk;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.type;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkDensityTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assertForNonDefaultSplits;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assertForSingleDefaultSplit;
import static com.android.tools.build.bundletool.testing.TestUtils.extractPaths;
import static com.android.tools.build.bundletool.testing.truth.resources.TruthResourceTable.assertThat;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.MoreCollectors.onlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static junit.framework.TestCase.fail;

import com.android.aapt.ConfigurationOuterClass.Configuration;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.StringPool;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.ScreenDensity;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ResourceId;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.model.version.Version;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.android.tools.build.bundletool.testing.ResourceTableBuilder;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.truth.Truth8;
import com.google.protobuf.ByteString;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class ScreenDensityResourcesSplitterTest {

  private static final Predicate<ResourceId> NO_RESOURCES_PINNED_TO_MASTER =
      Predicates.alwaysFalse();
  private static final Predicate<ResourceId> NO_LOW_DENSITY_CONFIG_PINNED_TO_MASTER =
      Predicates.alwaysFalse();

  private final ScreenDensityResourcesSplitter splitter =
      new ScreenDensityResourcesSplitter(
          BundleToolVersion.getCurrentVersion(),
          NO_RESOURCES_PINNED_TO_MASTER,
          NO_LOW_DENSITY_CONFIG_PINNED_TO_MASTER);

  @DataPoints("bundleFeatureEnabled")
  public static final ImmutableSet<Boolean> BUNDLE_FEATURE_ENABLED_DATA_POINTS =
      ImmutableSet.of(false, true);

  @Test
  public void noResourceTable_noResourceSplits() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("lib/x86/liba.so")
            .setManifest(androidManifest("com.test.app"))
            .build();
    assertThat(splitter.split(ModuleSplit.forResources(testModule)))
        .containsExactly(ModuleSplit.forResources(testModule));
  }

  @Test
  public void noDensityResources_noDensitySplits() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("res/drawable/test.jpg")
            .addFile("a/a/a.jpg")
            .setResourceTable(
                resourceTable(
                    pkg(
                        USER_PACKAGE_OFFSET,
                        "com.test.app",
                        type(
                            0x01,
                            "drawable",
                            entry(
                                0x01,
                                "test",
                                fileReference(
                                    "res/drawable/test.jpg",
                                    Configuration.getDefaultInstance())),
                            entry(
                                0x01,
                                "a",
                                fileReference(
                                    "a/a/a.jpg",
                                    Configuration.getDefaultInstance()))
                        ))))
            .setManifest(androidManifest("com.test.app"))
            .build();

    ModuleSplit resourcesModule = ModuleSplit.forResources(testModule);

    ImmutableCollection<ModuleSplit> splits = splitter.split(resourcesModule);
    assertThat(splits).hasSize(1);

    ModuleSplit baseSplit = splits.iterator().next();
    assertThat(baseSplit.getResourceTable().get()).containsResource("com.test.app:drawable/test");
    assertThat(baseSplit.getResourceTable().get()).containsResource("com.test.app:drawable/a");
    assertThat(baseSplit.findEntry("res/drawable/test.jpg")).isPresent();
    assertThat(baseSplit.findEntry("a/a/a.jpg")).isPresent();
  }

  @Test
  public void allSplitsPresentWithResourceTable() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("res/drawable-mdpi/image.jpg")
            .addFile("res/drawable-hdpi/image.jpg")
            .addFile("a/a/a.jpg")
            .addFile("b/b/b.jpg")
            .setResourceTable(
                new ResourceTableBuilder()
                    .addPackage("com.test.app")
                    .addDrawableResourceForMultipleDensities(
                        "image",
                        ImmutableMap.of(
                            LDPI_VALUE,
                            "res/drawable-ldpi/image.jpg",
                            MDPI_VALUE,
                            "res/drawable-mdpi/image.jpg"))
                    .addDrawableResourceForMultipleDensities(
                        "test",
                        ImmutableMap.of(
                            LDPI_VALUE,
                            "a/a/a.jpg",
                            MDPI_VALUE,
                            "b/b/b.jpg"))
                    .build())
            .setManifest(androidManifest("com.test.app"))
            .build();
    ImmutableSet<DensityAlias> densities =
        ImmutableSet.of(
            DensityAlias.LDPI,
            DensityAlias.MDPI,
            DensityAlias.TVDPI,
            DensityAlias.HDPI,
            DensityAlias.XHDPI,
            DensityAlias.XXHDPI,
            DensityAlias.XXXHDPI);
    ImmutableCollection<ModuleSplit> densitySplits =
        splitter.split(ModuleSplit.forResources(testModule));
    for (ModuleSplit resourceSplit : densitySplits) {
      assertThat(resourceSplit.getResourceTable().isPresent()).isTrue();
    }
    List<ApkTargeting> targeting =
        densitySplits.stream().map(split -> split.getApkTargeting()).collect(Collectors.toList());
    assertThat(targeting)
        .ignoringRepeatedFieldOrder()
        .containsExactly(
            ApkTargeting.getDefaultInstance(),
            apkDensityTargeting(
                DensityAlias.LDPI, Sets.difference(densities, ImmutableSet.of(DensityAlias.LDPI))),
            apkDensityTargeting(
                ImmutableSet.of(DensityAlias.MDPI),
                Sets.difference(densities, ImmutableSet.of(DensityAlias.MDPI))),
            apkDensityTargeting(
                ImmutableSet.of(DensityAlias.HDPI),
                Sets.difference(densities, ImmutableSet.of(DensityAlias.HDPI))),
            apkDensityTargeting(
                ImmutableSet.of(DensityAlias.XHDPI),
                Sets.difference(densities, ImmutableSet.of(DensityAlias.XHDPI))),
            apkDensityTargeting(
                ImmutableSet.of(DensityAlias.XXHDPI),
                Sets.difference(densities, ImmutableSet.of(DensityAlias.XXHDPI))),
            apkDensityTargeting(
                ImmutableSet.of(DensityAlias.XXXHDPI),
                Sets.difference(densities, ImmutableSet.of(DensityAlias.XXXHDPI))),
            apkDensityTargeting(
                ImmutableSet.of(DensityAlias.TVDPI),
                Sets.difference(densities, ImmutableSet.of(DensityAlias.TVDPI))));
  }

  @Test
  public void mipmapsNotIncludedInConfigSplits() throws Exception {
    ResourceTable table =
        resourceTable(
            pkg(
                USER_PACKAGE_OFFSET,
                "com.test.app",
                type(
                    0x01,
                    "mipmap",
                    entry(
                        0x01,
                        "launcher_icon",
                        fileReference("res/mipmap-hdpi/launcher_icon.png", HDPI),
                        fileReference(
                            "res/mipmap/launcher_icon.png", Configuration.getDefaultInstance())),
                    entry(
                        0x02,
                        "a",
                        fileReference("a/a/a.png", HDPI),
                        fileReference(
                            "a/b/a.png", Configuration.getDefaultInstance()))
                    ),
                type(
                    0x02,
                    "drawable",
                    entry(
                        0x01,
                        "image",
                        fileReference("res/drawable-hdpi/image.jpg", HDPI),
                        fileReference("res/drawable-xhdpi/image.jpg", XHDPI)),
                    entry(
                        0x02,
                        "a",
                        fileReference("b/a/a.jpg", HDPI),
                        fileReference("b/b/a.jpg", XHDPI))
                    )
            )
        );

    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("res/mipmap/launcher_icon.png")
            .addFile("res/mipmap-hdpi/launcher_icon.png")
            .addFile("res/drawable-hdpi/image.jpg")
            .addFile("res/drawable-xhdpi/image.jpg")
            .addFile("a/a/a.jpg")
            .addFile("a/b/a.jpg")
            .addFile("b/a/a.jpg")
            .addFile("b/b/a.jpg")
            .setResourceTable(table)
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableCollection<ModuleSplit> allSplits =
        splitter.split(ModuleSplit.forResources(testModule));
    assertThat(allSplits).hasSize(DEFAULT_DENSITY_BUCKETS.size() + 1);

    assertForSingleDefaultSplit(
        allSplits,
        defaultSplit -> {
          assertThat(defaultSplit.getResourceTable()).isPresent();
          ResourceTable defaultResourceTable = defaultSplit.getResourceTable().get();
          assertThat(defaultResourceTable)
              .containsResource("com.test.app:mipmap/launcher_icon")
              .withConfigSize(2)
              .withDensity(DEFAULT_DENSITY_VALUE)
              .withDensity(HDPI_VALUE);
          assertThat(defaultResourceTable)
              .containsResource("com.test.app:mipmap/a")
              .withConfigSize(2)
              .withDensity(DEFAULT_DENSITY_VALUE)
              .withDensity(HDPI_VALUE);
        });

    assertForNonDefaultSplits(
        allSplits,
        densitySplit -> {
          assertThat(densitySplit.getResourceTable()).isPresent();
          ResourceTable splitResourceTable = densitySplit.getResourceTable().get();
          assertThat(splitResourceTable)
              .doesNotContainResource("com.test.app:mipmap/launcher_icon");
        });
  }

  @Test
  public void preservesSourcePool() throws Exception {
    StringPool sourcePool =
        StringPool.newBuilder().setData(ByteString.copyFrom(new byte[] {'x'})).build();
    ResourceTable table =
        new ResourceTableBuilder()
                .addPackage("com.test.app")
                .addDrawableResourceForMultipleDensities(
                    "image", ImmutableMap.of(
                        MDPI_VALUE, "res/drawable-mdpi/image.jpg",
                        HDPI_VALUE, "a/a/a.jpg"
                    ))
                .build()
                .toBuilder()
                .setSourcePool(sourcePool)
                .build();
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("res/drawable-mdpi/test.jpg")
            .addFile("a/a/a.jpg")
            .setResourceTable(table)
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableCollection<ModuleSplit> densitySplits =
        splitter.split(ModuleSplit.forResources(testModule));
    assertThat(densitySplits).hasSize(DEFAULT_DENSITY_BUCKETS.size() + 1);

    for (ModuleSplit densitySplit : densitySplits) {
      assertThat(densitySplit.getResourceTable()).isPresent();
      assertThat(densitySplit.getResourceTable().get().getSourcePool()).isEqualTo(sourcePool);
    }
  }

  @Test
  public void picksTheResourceForExactDensity() throws Exception {
    ResourceTable table =
        new ResourceTableBuilder()
            .addPackage("com.test.app")
            .addDrawableResourceForMultipleDensities(
                "image",
                ImmutableMap.<Integer, String>builder()
                    .put(LDPI_VALUE, "res/drawable-ldpi/image.jpg")
                    .put(MDPI_VALUE, "res/drawable-mdpi/image.jpg")
                    .put(TVDPI_VALUE, "res/drawable-tvdpi/image.jpg")
                    .put(HDPI_VALUE, "res/drawable-hdpi/image.jpg")
                    .put(XHDPI_VALUE, "res/drawable-xhdpi/image.jpg")
                    .put(XXHDPI_VALUE, "res/drawable-xxhdpi/image.jpg")
                    .put(XXXHDPI_VALUE, "res/drawable-xxxhdpi/image.jpg")
                    .build())
            .addDrawableResourceForMultipleDensities(
                "a",
                ImmutableMap.<Integer, String>builder()
                    .put(LDPI_VALUE, "a/a/a.jpg")
                    .put(MDPI_VALUE, "a/b/a.jpg")
                    .put(TVDPI_VALUE, "a/c/a.jpg")
                    .put(HDPI_VALUE, "a/d/a.jpg")
                    .put(XHDPI_VALUE, "a/e/a.jpg")
                    .put(XXHDPI_VALUE, "a/f/a.jpg")
                    .put(XXXHDPI_VALUE, "a/g/a.jpg")
                    .build())
            .build();

    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("res/drawable-ldpi/image.jpg")
            .addFile("res/drawable-mdpi/image.jpg")
            .addFile("res/drawable-tvdpi/image.jpg")
            .addFile("res/drawable-hdpi/image.jpg")
            .addFile("res/drawable-xhdpi/image.jpg")
            .addFile("res/drawable-xxhdpi/image.jpg")
            .addFile("res/drawable-xxxhdpi/image.jpg")
            .addFile("a/a/a.jpg")
            .addFile("a/b/a.jpg")
            .addFile("a/c/a.jpg")
            .addFile("a/d/a.jpg")
            .addFile("a/e/a.jpg")
            .addFile("a/f/a.jpg")
            .addFile("a/g/a.jpg")
            .setResourceTable(table)
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableSet<DensityAlias> densities =
        ImmutableSet.of(
            DensityAlias.LDPI,
            DensityAlias.MDPI,
            DensityAlias.TVDPI,
            DensityAlias.HDPI,
            DensityAlias.XHDPI,
            DensityAlias.XXHDPI,
            DensityAlias.XXXHDPI);
    ImmutableCollection<ModuleSplit> densitySplits =
        splitter.split(ModuleSplit.forResources(testModule));
    assertThat(densitySplits).hasSize(DEFAULT_DENSITY_BUCKETS.size() + 1);
    assertThat(
            densitySplits.stream().map(split -> split.getApkTargeting()).collect(toImmutableSet()))
        .ignoringRepeatedFieldOrder()
        .containsExactly(
            ApkTargeting.getDefaultInstance(),
            apkDensityTargeting(
                DensityAlias.LDPI, Sets.difference(densities, ImmutableSet.of(DensityAlias.LDPI))),
            apkDensityTargeting(
                ImmutableSet.of(DensityAlias.MDPI),
                Sets.difference(densities, ImmutableSet.of(DensityAlias.MDPI))),
            apkDensityTargeting(
                ImmutableSet.of(DensityAlias.HDPI),
                Sets.difference(densities, ImmutableSet.of(DensityAlias.HDPI))),
            apkDensityTargeting(
                ImmutableSet.of(DensityAlias.XHDPI),
                Sets.difference(densities, ImmutableSet.of(DensityAlias.XHDPI))),
            apkDensityTargeting(
                ImmutableSet.of(DensityAlias.XXHDPI),
                Sets.difference(densities, ImmutableSet.of(DensityAlias.XXHDPI))),
            apkDensityTargeting(
                ImmutableSet.of(DensityAlias.XXXHDPI),
                Sets.difference(densities, ImmutableSet.of(DensityAlias.XXXHDPI))),
            apkDensityTargeting(
                ImmutableSet.of(DensityAlias.TVDPI),
                Sets.difference(densities, ImmutableSet.of(DensityAlias.TVDPI))));

    for (ModuleSplit densitySplit : densitySplits) {
      assertThat(densitySplit.getResourceTable().isPresent()).isTrue();
      ResourceTable splitResourceTable = densitySplit.getResourceTable().get();

      // we are not verifying the default split in this test.
      if (densitySplit.getApkTargeting().equals(ApkTargeting.getDefaultInstance())) {
        continue;
      }

      assertThat(densitySplit.getApkTargeting().hasScreenDensityTargeting()).isTrue();
      switch (densitySplit
          .getApkTargeting()
          .getScreenDensityTargeting()
          .getValue(0)
          .getDensityAlias()) {
        case LDPI:
          assertThat(splitResourceTable)
              .containsResource("com.test.app:drawable/image")
              .onlyWithConfigs(LDPI);
          assertThat(splitResourceTable)
              .containsResource("com.test.app:drawable/a")
              .onlyWithConfigs(LDPI);
          break;
        case MDPI:
          assertThat(splitResourceTable)
              .containsResource("com.test.app:drawable/image")
              .onlyWithConfigs(MDPI);
          assertThat(splitResourceTable)
              .containsResource("com.test.app:drawable/a")
              .onlyWithConfigs(MDPI);
          break;
        case TVDPI:
          assertThat(splitResourceTable)
              .containsResource("com.test.app:drawable/image")
              .onlyWithConfigs(TVDPI);
          assertThat(splitResourceTable)
              .containsResource("com.test.app:drawable/a")
              .onlyWithConfigs(TVDPI);
          break;
        case HDPI:
          assertThat(splitResourceTable)
              .containsResource("com.test.app:drawable/image")
              .onlyWithConfigs(HDPI);
          assertThat(splitResourceTable)
              .containsResource("com.test.app:drawable/a")
              .onlyWithConfigs(HDPI);
          break;
        case XHDPI:
          assertThat(splitResourceTable)
              .containsResource("com.test.app:drawable/image")
              .onlyWithConfigs(XHDPI);
          assertThat(splitResourceTable)
              .containsResource("com.test.app:drawable/a")
              .onlyWithConfigs(XHDPI);
          break;
        case XXHDPI:
          assertThat(splitResourceTable)
              .containsResource("com.test.app:drawable/image")
              .onlyWithConfigs(XXHDPI);
          assertThat(splitResourceTable)
              .containsResource("com.test.app:drawable/a")
              .onlyWithConfigs(XXHDPI);
          break;
        case XXXHDPI:
          assertThat(splitResourceTable)
              .containsResource("com.test.app:drawable/image")
              .onlyWithConfigs(XXXHDPI);
          assertThat(splitResourceTable)
              .containsResource("com.test.app:drawable/a")
              .onlyWithConfigs(XXXHDPI);
          break;
        default:
          break;
      }
    }
  }

  @Test
  public void twoDensitiesInSameBucket() throws Exception {
    ResourceTable table =
        resourceTable(
            pkg(
                USER_PACKAGE_OFFSET,
                "com.test.app",
                type(
                    0x01,
                    "drawable",
                    entry(
                        0x01,
                        "image",
                        fileReference("res/drawable-xxhdpi/image.png", XXHDPI),
                        fileReference("res/drawable-560dpi/image.png", _560DPI),
                        fileReference("res/drawable-xxxhdpi/image.png", XXXHDPI)),
                    entry(
                        0x02,
                        "a",
                        fileReference("a/a/a.png", XXHDPI),
                        fileReference("a/b/a.png", _560DPI),
                        fileReference("a/c/a", XXXHDPI))
                )));
    BundleModule module =
        new BundleModuleBuilder("base")
            .addFile("res/drawable-xxhdpi/image.png")
            .addFile("res/drawable-560dpi/image.png")
            .addFile("res/drawable-xxxhdpi/image.png")
            .addFile("a/a/a.png")
            .addFile("a/b/a.png")
            .addFile("a/c/a.png")
            .setResourceTable(table)
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableCollection<ModuleSplit> densitySplits =
        splitter.split(ModuleSplit.forResources(module));

    ModuleSplit xxxhdpiSplit =
        findModuleSplitWithScreenDensityTargeting(
            densitySplits,
            ScreenDensity.newBuilder().setDensityAlias(DensityAlias.XXXHDPI).build());
    assertThat(xxxhdpiSplit.getResourceTable()).isPresent();
    ResourceTable resourceTable = xxxhdpiSplit.getResourceTable().get();

    assertThat(resourceTable)
        .containsResource("com.test.app:drawable/image")
        .withConfigSize(2)
        .withDensity(XXXHDPI_VALUE)
        .withDensity(560);
    assertThat(resourceTable)
        .containsResource("com.test.app:drawable/a")
        .withConfigSize(2)
        .withDensity(XXXHDPI_VALUE)
        .withDensity(560);
  }

  /**
   * An edge case where xxxhdpi split capturing devices from 527dpi and above should capture 512dpi
   * resources and above.
   *
   * <p>The 512dpi threshold depends on what other dpi values are available. Here, it's driven by
   * the presence of 560dpi config value. A 527dpi device prefers 512dpi over 560dpi.
   *
   * <p>The 512dpi resource should also be present in the xxhdpi split. It targets all devices up to
   * 526dpi.
   */
  @Test
  public void densityBucket_neighbouringResources_edgeCase() throws Exception {
    ResourceTable table =
        resourceTable(
            pkg(
                USER_PACKAGE_OFFSET,
                "com.test.app",
                type(
                    0x01,
                    "drawable",
                    entry(
                        0x01,
                        "image",
                        fileReference("res/drawable-mdpi/image.png", MDPI),
                        fileReference("res/drawable-512dpi/image.png", forDpi(512)),
                        fileReference("res/drawable-560dpi/image.png", _560DPI),
                        fileReference("res/drawable-xxxhdpi/image.png", XXXHDPI)),
                    entry(
                        0x02,
                        "a",
                        fileReference("a/a/a.png", MDPI),
                        fileReference("a/b/a.png", forDpi(512)),
                        fileReference("a/c/a.png", _560DPI),
                        fileReference("a/d/a/image.png", XXXHDPI))
                )));
    BundleModule module =
        new BundleModuleBuilder("base")
            .addFile("res/drawable-mdpi/image.png")
            .addFile("res/drawable-512dpi/image.png")
            .addFile("res/drawable-560dpi/image.png")
            .addFile("res/drawable-xxxhdpi/image.png")
            .addFile("a/a/a.png")
            .addFile("a/b/a.png")
            .addFile("a/c/a.png")
            .addFile("a/d/a.png")
            .setResourceTable(table)
            .setManifest(androidManifest("com.test.app"))
            .build();

    ScreenDensityResourcesSplitter splitter =
        new ScreenDensityResourcesSplitter(
            ImmutableSet.of(DensityAlias.XXHDPI, DensityAlias.XXXHDPI),
            BundleToolVersion.getCurrentVersion(),
            NO_RESOURCES_PINNED_TO_MASTER,
            NO_LOW_DENSITY_CONFIG_PINNED_TO_MASTER);
    ImmutableCollection<ModuleSplit> densitySplits =
        splitter.split(ModuleSplit.forResources(module));

    ModuleSplit xxxhdpiSplit =
        findModuleSplitWithScreenDensityTargeting(
            densitySplits,
            ScreenDensity.newBuilder().setDensityAlias(DensityAlias.XXXHDPI).build());
    assertThat(xxxhdpiSplit.getResourceTable()).isPresent();
    ResourceTable xxxHdpiResourceTable = xxxhdpiSplit.getResourceTable().get();

    assertThat(xxxHdpiResourceTable)
        .containsResource("com.test.app:drawable/image")
        .withConfigSize(3)
        .withDensity(XXXHDPI_VALUE)
        .withDensity(560)
        .withDensity(512);
    assertThat(xxxHdpiResourceTable)
        .containsResource("com.test.app:drawable/a")
        .withConfigSize(3)
        .withDensity(XXXHDPI_VALUE)
        .withDensity(560)
        .withDensity(512);

    ModuleSplit xxhdpiSplit =
        findModuleSplitWithScreenDensityTargeting(
            densitySplits, ScreenDensity.newBuilder().setDensityAlias(DensityAlias.XXHDPI).build());
    Truth8.assertThat(xxhdpiSplit.getResourceTable()).isPresent();
    ResourceTable xxHdpiResourceTable = xxhdpiSplit.getResourceTable().get();

    assertThat(xxHdpiResourceTable)
        .containsResource("com.test.app:drawable/image")
        .withConfigSize(2)
        .withDensity(MDPI_VALUE)
        .withDensity(512);
    assertThat(xxHdpiResourceTable)
        .containsResource("com.test.app:drawable/a")
        .withConfigSize(2)
        .withDensity(MDPI_VALUE)
        .withDensity(512);
  }

  @Test
  public void complexDensitySplit() throws Exception {
    ResourceTable table =
        resourceTable(
            pkg(
                USER_PACKAGE_OFFSET,
                "com.test.app",
                type(
                    0x01,
                    "mipmap",
                    entry(
                        0x01,
                        "launcher_icon",
                        fileReference("res/mipmap-hdpi/launcher_icon.png", HDPI),
                        fileReference(
                            "res/mipmap/launcher_icon.png", Configuration.getDefaultInstance())),
                    entry(
                        0x02,
                        "a",
                        fileReference("a/a/a.png", HDPI),
                        fileReference(
                            "a/b/a.png", Configuration.getDefaultInstance()))
                ),
                type(
                    0x02,
                    "drawable",
                    entry(
                        0x01,
                        "title_image",
                        fileReference("res/drawable-hdpi/title_image.jpg", HDPI),
                        fileReference("res/drawable-xhdpi/title_image.jpg", XHDPI)),
                    entry(
                        0x02,
                        "a",
                        fileReference("b/a/a.jpg", HDPI),
                        fileReference("b/b/a.jpg", XHDPI))
                )));
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("res/mipmap/launcher_icon.png")
            .addFile("res/mipmap-hdpi/launcher_icon.png")
            .addFile("res/drawable-hdpi/title_image.jpg")
            .addFile("res/drawable-xhdpi/title_image.jpg")
            .addFile("a/a/a.jpg")
            .addFile("a/b/a.jpg")
            .addFile("b/a/a.jpg")
            .addFile("b/b/a.jpg")
            .setResourceTable(table)
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableCollection<ModuleSplit> allSplits =
        splitter.split(ModuleSplit.forResources(testModule));

    assertThat(allSplits).hasSize(DEFAULT_DENSITY_BUCKETS.size() + 1);

    assertForSingleDefaultSplit(
        allSplits,
        defaultSplit -> {
          assertThat(defaultSplit.getResourceTable()).isPresent();
          ResourceTable resourceTable = defaultSplit.getResourceTable().get();
          assertThat(resourceTable)
              .containsResource("com.test.app:mipmap/launcher_icon")
              .withConfigSize(2)
              .withDensity(HDPI_VALUE)
              .withDensity(DEFAULT_DENSITY_VALUE);
          assertThat(resourceTable)
              .containsResource("com.test.app:mipmap/a")
              .withConfigSize(2)
              .withDensity(HDPI_VALUE)
              .withDensity(DEFAULT_DENSITY_VALUE);
          assertThat(resourceTable).doesNotContainResource("com.test.app:drawable/title_image");
        });

    assertForNonDefaultSplits(
        allSplits,
        densitySplit -> {
          assertThat(densitySplit.getResourceTable()).isPresent();
          ResourceTable resourceTable = densitySplit.getResourceTable().get();

          assertThat(resourceTable).hasPackage("com.test.app").withNoType("mipmap");
          assertThat(resourceTable)
              .containsResource("com.test.app:drawable/title_image")
              .withConfigSize(1);
          assertThat(resourceTable)
              .containsResource("com.test.app:drawable/a")
              .withConfigSize(1);
          assertThat(densitySplit.getApkTargeting().hasScreenDensityTargeting()).isTrue();
          switch (densitySplit
              .getApkTargeting()
              .getScreenDensityTargeting()
              .getValue(0)
              .getDensityAlias()) {
            case LDPI:
            case MDPI:
            case TVDPI:
            case HDPI:
              assertThat(resourceTable)
                  .containsResource("com.test.app:drawable/title_image")
                  .onlyWithConfigs(HDPI);
              assertThat(resourceTable)
                  .containsResource("com.test.app:drawable/a")
                  .onlyWithConfigs(HDPI);
              break;
            case XHDPI:
            case XXHDPI:
            case XXXHDPI:
              assertThat(resourceTable)
                  .containsResource("com.test.app:drawable/title_image")
                  .onlyWithConfigs(XHDPI);
              assertThat(resourceTable)
                  .containsResource("com.test.app:drawable/a")
                  .onlyWithConfigs(XHDPI);
              break;
            default:
              fail(String.format("Unexpected targeting: %s", densitySplit.getApkTargeting()));
              break;
          }
        });
  }

  @Test
  public void defaultDensityWithAlternatives() throws Exception {
    ResourceTable table =
        resourceTable(
            pkg(
                USER_PACKAGE_OFFSET,
                "com.test.app",
                type(
                    0x01,
                    "drawable",
                    entry(
                        0x01,
                        "image",
                        fileReference("res/drawable/image.jpg", Configuration.getDefaultInstance()),
                        fileReference("res/drawable-hdpi/image.jpg", HDPI)),
                    entry(
                        0x02,
                        "a",
                        fileReference("a/a/a.jpg", Configuration.getDefaultInstance()),
                        fileReference("a/b/a.jpg", HDPI))
                )));
    BundleModule module =
        new BundleModuleBuilder("base")
            .addFile("res/drawable/image.jpg")
            .addFile("res/drawable-hdpi/image.jpg")
            .addFile("a/a/a.jpg")
            .addFile("a/b/a.jpg")
            .setResourceTable(table)
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableCollection<ModuleSplit> splits = splitter.split(ModuleSplit.forResources(module));

    // Master split: Resource not present.
    ModuleSplit masterSplit = findModuleSplitWithDefaultTargeting(splits);
    assertThat(masterSplit.getResourceTable().get())
        .doesNotContainResource("com.test.app:drawable/image");

    // MDPI split: default resource present.
    ModuleSplit mdpiSplit = findModuleSplitWithScreenDensityTargeting(splits, DensityAlias.MDPI);
    assertThat(mdpiSplit.getResourceTable().get())
        .containsResource("com.test.app:drawable/image")
        .onlyWithConfigs(Configuration.getDefaultInstance());
    assertThat(mdpiSplit.getResourceTable().get())
        .containsResource("com.test.app:drawable/a")
        .onlyWithConfigs(Configuration.getDefaultInstance());

    // HDPI split: hdpi resource present.
    ModuleSplit hdpiSplit = findModuleSplitWithScreenDensityTargeting(splits, DensityAlias.HDPI);
    assertThat(hdpiSplit.getResourceTable().get())
        .containsResource("com.test.app:drawable/image")
        .onlyWithConfigs(HDPI);
    assertThat(hdpiSplit.getResourceTable().get())
        .containsResource("com.test.app:drawable/a")
        .onlyWithConfigs(HDPI);
  }

  /** Before 0.4.0, all default densities ended up in the base regardless of alternatives. */
  @Test
  public void defaultDensityWithAlternatives_before_0_4_0() throws Exception {
    ResourceTable table =
        resourceTable(
            pkg(
                USER_PACKAGE_OFFSET,
                "com.test.app",
                type(
                    0x01,
                    "drawable",
                    entry(
                        0x01,
                        "image",
                        fileReference("res/drawable/image.jpg", Configuration.getDefaultInstance()),
                        fileReference("res/drawable-hdpi/image.jpg", HDPI)),
                    entry(
                        0x02,
                        "a",
                        fileReference("a/a/a.jpg", Configuration.getDefaultInstance()),
                        fileReference("a/b/a.jpg", HDPI))
                )));
    BundleModule module =
        new BundleModuleBuilder("base")
            .addFile("res/drawable/image.jpg")
            .addFile("res/drawable-hdpi/image.jpg")
            .addFile("a/a/a.jpg")
            .addFile("a/b/a.jpg")
            .setResourceTable(table)
            .setManifest(androidManifest("com.test.app"))
            .build();

    ScreenDensityResourcesSplitter splitter =
        new ScreenDensityResourcesSplitter(
            Version.of("0.3.3"),
            NO_RESOURCES_PINNED_TO_MASTER,
            NO_LOW_DENSITY_CONFIG_PINNED_TO_MASTER);
    ImmutableCollection<ModuleSplit> splits = splitter.split(ModuleSplit.forResources(module));

    // Master split: Resource present with default targeting.
    ModuleSplit masterSplit = findModuleSplitWithDefaultTargeting(splits);
    assertThat(masterSplit.getResourceTable().get())
        .containsResource("com.test.app:drawable/image")
        .onlyWithConfigs(Configuration.getDefaultInstance());
    assertThat(masterSplit.getResourceTable().get())
        .containsResource("com.test.app:drawable/a")
        .onlyWithConfigs(Configuration.getDefaultInstance());

    // MDPI split: hdpi resource present.
    ModuleSplit mdpiSplit = findModuleSplitWithScreenDensityTargeting(splits, DensityAlias.MDPI);
    assertThat(mdpiSplit.getResourceTable().get())
        .containsResource("com.test.app:drawable/image")
        .onlyWithConfigs(HDPI);
    assertThat(mdpiSplit.getResourceTable().get())
        .containsResource("com.test.app:drawable/a")
        .onlyWithConfigs(HDPI);

    // HDPI split: hdpi resource present.
    ModuleSplit hdpiSplit = findModuleSplitWithScreenDensityTargeting(splits, DensityAlias.HDPI);
    assertThat(hdpiSplit.getResourceTable().get())
        .containsResource("com.test.app:drawable/image")
        .onlyWithConfigs(HDPI);
    assertThat(hdpiSplit.getResourceTable().get())
        .containsResource("com.test.app:drawable/a")
        .onlyWithConfigs(HDPI);
  }

  @Test
  public void defaultDensityResourceWithoutAlternatives() throws Exception {
    ResourceTable table =
        resourceTable(
            pkg(
                USER_PACKAGE_OFFSET,
                "com.test.app",
                type(
                    0x01,
                    "drawable",
                    entry(
                        0x01,
                        "image",
                        fileReference(
                            "res/drawable/image.jpg", Configuration.getDefaultInstance())),
                    entry(
                        0x02,
                        "a",
                        fileReference(
                            "a/a/a.jpg", Configuration.getDefaultInstance()))
                )));
    BundleModule module =
        new BundleModuleBuilder("base")
            .addFile("res/drawable/image.jpg")
            .addFile("a/a/a.jpg")
            .setResourceTable(table)
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableCollection<ModuleSplit> splits = splitter.split(ModuleSplit.forResources(module));

    // No config split because the resource has no alternatives so ends up in the master split.
    assertThat(splits).hasSize(1);

    ModuleSplit masterSplit = findModuleSplitWithDefaultTargeting(splits);
    assertThat(masterSplit.getResourceTable().get())
        .containsResource("com.test.app:drawable/image")
        .onlyWithConfigs(Configuration.getDefaultInstance());
    assertThat(masterSplit.getResourceTable().get())
        .containsResource("com.test.app:drawable/a")
        .onlyWithConfigs(Configuration.getDefaultInstance());
    assertThat(masterSplit.findEntry("res/drawable/image.jpg")).isPresent();
    assertThat(masterSplit.findEntry("a/a/a.jpg")).isPresent();
  }

  /**
   * Before 0.4.0, non-default density resources without alternatives ended up in config splits
   * instead of in the master split.
   */
  @Test
  @Theory
  public void nonDefaultDensityResourceWithoutAlternatives_inMasterSince_0_4_0(
      @FromDataPoints("bundleFeatureEnabled") boolean bundleFeatureEnabled) throws Exception {
    ResourceTable table =
        resourceTable(
            pkg(
                USER_PACKAGE_OFFSET,
                "com.test.app",
                type(
                    0x01,
                    "drawable",
                    entry(0x01, "image", fileReference("res/drawable-hdpi/image.jpg", HDPI)),
                    entry(0x02, "a", fileReference("a/a/a.jpg", HDPI))
                )));
    BundleModule module =
        new BundleModuleBuilder("base")
            .addFile("res/drawable-hdpi/image.jpg")
            .addFile("a/a/a.jpg")
            .setResourceTable(table)
            .setManifest(androidManifest("com.test.app"))
            .build();

    ScreenDensityResourcesSplitter splitter =
        new ScreenDensityResourcesSplitter(
            Version.of(bundleFeatureEnabled ? "0.4.0" : "0.3.3"),
            NO_RESOURCES_PINNED_TO_MASTER,
            NO_LOW_DENSITY_CONFIG_PINNED_TO_MASTER);
    ImmutableCollection<ModuleSplit> splits = splitter.split(ModuleSplit.forResources(module));

    if (bundleFeatureEnabled) {
      // No config split because the resource has no alternatives so ends up in the master split.
      assertThat(splits).hasSize(1);

      ModuleSplit masterSplit = findModuleSplitWithDefaultTargeting(splits);
      assertThat(masterSplit.getResourceTable().get())
          .containsResource("com.test.app:drawable/image")
          .onlyWithConfigs(HDPI);
      assertThat(masterSplit.getResourceTable().get())
          .containsResource("com.test.app:drawable/a")
          .onlyWithConfigs(HDPI);
      assertThat(masterSplit.findEntry("res/drawable-hdpi/image.jpg")).isPresent();
      assertThat(masterSplit.findEntry("a/a/a.jpg")).isPresent();

    } else {
      // 1 base + 7 config splits
      assertThat(splits).hasSize(8);

      // The resource is not present in the base.
      ModuleSplit masterSplit = findModuleSplitWithDefaultTargeting(splits);
      assertThat(masterSplit.getResourceTable().get())
          .doesNotContainResource("com.test.app:drawable/image");
      assertThat(masterSplit.getResourceTable().get())
          .doesNotContainResource("com.test.app:drawable/a");

      // The resource is present in all config splits.
      assertForNonDefaultSplits(
          splits,
          densitySplit -> {
            assertThat(densitySplit.getResourceTable().get())
                .containsResource("com.test.app:drawable/image")
                .onlyWithConfigs(HDPI);
            assertThat(densitySplit.findEntry("res/drawable-hdpi/image.jpg")).isPresent();
            assertThat(densitySplit.findEntry("a/a/a.jpg")).isPresent();
          });
    }
  }

  @Test
  public void manifestMutatorToRequireSplits_notRegistered_whenNoDensityResources()
      throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("res/drawable/test.jpg")
            .addFile("a/a/a.jpg")
            .setResourceTable(
                resourceTable(
                    pkg(
                        USER_PACKAGE_OFFSET,
                        "com.test.app",
                        type(
                            0x01,
                            "drawable",
                            entry(
                                0x01,
                                "test",
                                fileReference(
                                    "res/drawable/test.jpg",
                                    Configuration.getDefaultInstance())),
                            entry(
                                0x02,
                                "a",
                                fileReference(
                                    "a/a/a.jpg",
                                    Configuration.getDefaultInstance())))
                        )))
            .setManifest(androidManifest("com.test.app"))
            .build();

    ModuleSplit resourcesModule = ModuleSplit.forResources(testModule);

    ImmutableCollection<ModuleSplit> splits = splitter.split(resourcesModule);
    assertThat(splits).hasSize(1);
    assertThat(splits.asList().get(0).getMasterManifestMutators()).isEmpty();
  }

  @Test
  public void manifestMutatorToRequireSplits_registered_whenDensityResourcesPresent()
      throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("res/drawable-mdpi/image.jpg")
            .addFile("res/drawable-hdpi/image.jpg")
            .addFile("a/a/a.jpg")
            .addFile("a/b/a.jpg")
            .setResourceTable(
                new ResourceTableBuilder()
                    .addPackage("com.test.app")
                    .addDrawableResourceForMultipleDensities(
                        "image",
                        ImmutableMap.of(
                            MDPI_VALUE,
                            "res/drawable-ldpi/image.jpg",
                            HDPI_VALUE,
                            "res/drawable-dpi/image.jpg"
                        ))
                    .addDrawableResourceForMultipleDensities(
                        "a",
                        ImmutableMap.of(
                            MDPI_VALUE,
                            "a/a/a.jpg",
                            HDPI_VALUE,
                            "a/b/a.jpg"
                        ))
                    .build())
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableCollection<ModuleSplit> densitySplits =
        splitter.split(ModuleSplit.forResources(testModule));

    ImmutableList<ModuleSplit> configSplits =
        densitySplits.stream().filter(split -> !split.isMasterSplit()).collect(toImmutableList());
    assertThat(configSplits).isNotEmpty();
    for (ModuleSplit configSplit : configSplits) {
      assertThat(
              compareManifestMutators(
                  configSplit.getMasterManifestMutators(), withSplitsRequired(true)))
          .isTrue();
    }
  }

  @Test
  public void resourcesPinnedToMaster_splittingSupressed() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("res/drawable-mdpi/image.jpg")
            .addFile("res/drawable-hdpi/image.jpg")
            .addFile("res/drawable-mdpi/image2.jpg")
            .addFile("res/drawable-hdpi/image2.jpg")
            .addFile("a/a/a.jpg")
            .addFile("a/b/a.jpg")
            .setResourceTable(
                new ResourceTableBuilder()
                    .addPackage("com.test.app")
                    .addDrawableResourceForMultipleDensities(
                        "image",
                        ImmutableMap.of(
                            /* mdpi */ 160, "res/drawable-mdpi/image.jpg",
                            /* hdpi */ 240, "res/drawable-hdpi/image.jpg"))
                    .addDrawableResourceForMultipleDensities(
                        "image2",
                        ImmutableMap.of(
                            /* mdpi */ 160, "res/drawable-mdpi/image2.jpg",
                            /* hdpi */ 240, "res/drawable-hdpi/image2.jpg"))
                    .addDrawableResourceForMultipleDensities(
                        "a",
                        ImmutableMap.of(
                            /* mdpi */ 160, "a/a/a.jpg",
                            /* hdpi */ 240, "a/b/a.jpg"))
                    .build())
            .setManifest(androidManifest("com.test.app"))
            .build();

    Predicate<ResourceId> masterResourcesPredicate =
        resourceId -> resourceId.getFullResourceId() == 0x7f010000;
    ScreenDensityResourcesSplitter splitter =
        new ScreenDensityResourcesSplitter(
            BundleToolVersion.getCurrentVersion(),
            masterResourcesPredicate,
            NO_LOW_DENSITY_CONFIG_PINNED_TO_MASTER);

    ImmutableCollection<ModuleSplit> densitySplits =
        splitter.split(ModuleSplit.forResources(testModule));

    ImmutableList<ModuleSplit> configSplits =
        densitySplits.stream().filter(split -> !split.isMasterSplit()).collect(toImmutableList());
    assertThat(configSplits).isNotEmpty();
    for (ModuleSplit configSplit : configSplits) {
      assertThat(extractPaths(configSplit.getEntries()))
          .doesNotContain("res/drawable-mdpi/image.jpg");
      assertThat(extractPaths(configSplit.getEntries()))
          .doesNotContain("res/drawable-hdpi/image.jpg");
    }
    ModuleSplit masterSplit =
        densitySplits.stream().filter(split -> split.isMasterSplit()).collect(onlyElement());
    assertThat(extractPaths(masterSplit.getEntries()))
        .containsExactly("res/drawable-mdpi/image.jpg", "res/drawable-hdpi/image.jpg");
  }

  @Test
  public void lowestDensityConfigsPinnedToMaster() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("a/a/a.jpg")
            .addFile("a/b/a.jpg")
            .addFile("res/drawable-mdpi/image2.jpg")
            .addFile("res/drawable-hdpi/image2.jpg")
            .setResourceTable(
                new ResourceTableBuilder()
                    .addPackage("com.test.app")
                    .addDrawableResourceForMultipleDensities(
                        "image",
                        ImmutableMap.of(
                            /* mdpi */ 160, "a/a/a.jpg",
                            /* hdpi */ 240, "a/b/a.jpg"))
                    .addDrawableResourceForMultipleDensities(
                        "image2",
                        ImmutableMap.of(
                            /* mdpi */ 160, "res/drawable-mdpi/image2.jpg",
                            /* hdpi */ 240, "res/drawable-hdpi/image2.jpg"))
                    .build())
            .setManifest(androidManifest("com.test.app"))
            .build();

    Predicate<ResourceId> pinnedLowDensityResourcesPredicate =
        resourceId -> resourceId.getFullResourceId() == 0x7f010000;
    ScreenDensityResourcesSplitter splitter =
        new ScreenDensityResourcesSplitter(
            BundleToolVersion.getCurrentVersion(),
            NO_RESOURCES_PINNED_TO_MASTER,
            pinnedLowDensityResourcesPredicate);

    ImmutableCollection<ModuleSplit> densitySplits =
        splitter.split(ModuleSplit.forResources(testModule));

    ModuleSplit masterSplit =
        densitySplits.stream().filter(split -> split.isMasterSplit()).collect(onlyElement());
    assertThat(extractPaths(masterSplit.getEntries()))
        .containsExactly("a/a/a.jpg");

    ImmutableList<ModuleSplit> configSplits =
        densitySplits.stream().filter(split -> !split.isMasterSplit()).collect(toImmutableList());
    assertThat(configSplits).isNotEmpty();
    for (ModuleSplit configSplit : configSplits) {
      assertThat(extractPaths(configSplit.getEntries()))
          .doesNotContain("a/a/a.jpg");
    }
  }

  @Test
  public void lowestDensityConfigsPinnedToMaster_mixedConfigsInSameDensityBucket()
      throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("res/drawable-ldpi/image.jpg")
            .addFile("res/drawable-ldpi-v21/image.jpg")
            .addFile("res/drawable-ldpi-v24/image.jpg")
            .addFile("res/drawable-xxhdpi/image.jpg")
            .addFile("res/drawable-xxhdpi-v21/image.jpg")
            .addFile("res/drawable-xxhdpi-v24/image.jpg")
            .addFile("a/a21/a.jpg")
            .addFile("a/a24/a.jpg")
            .setResourceTable(
                new ResourceTableBuilder()
                    .addPackage("com.test.app")
                    .addFileResourceForMultipleConfigs(
                        "drawable",
                        "image",
                        ImmutableMap.<Configuration, String>builder()
                            .put(mergeConfigs(LDPI), "res/drawable-ldpi/image.jpg")
                            .put(mergeConfigs(LDPI, sdk(21)), "res/drawable-ldpi-v21/image.jpg")
                            .put(mergeConfigs(LDPI, sdk(24)), "res/drawable-ldpi-v24/image.jpg")
                            .put(mergeConfigs(MDPI, sdk(21)), "a/a21/a.jpg")
                            .put(mergeConfigs(MDPI, sdk(24)), "a/a24/a.jpg")
                            .put(mergeConfigs(XXXHDPI), "res/drawable-xxhdpi/image.jpg")
                            .put(mergeConfigs(XXHDPI, sdk(21)), "res/drawable-xxhdpi-v21/image.jpg")
                            .put(mergeConfigs(XXHDPI, sdk(24)), "res/drawable-xxhdpi-v24/image.jpg")
                            .build())
                    .build())
            .setManifest(androidManifest("com.test.app"))
            .build();

    // 0x7f010000 is the "drawable/image" resource.
    Predicate<ResourceId> pinnedLowDensityResourcesPredicate =
        resourceId -> resourceId.getFullResourceId() == 0x7f010000;
    ScreenDensityResourcesSplitter splitter =
        new ScreenDensityResourcesSplitter(
            BundleToolVersion.getCurrentVersion(),
            NO_RESOURCES_PINNED_TO_MASTER,
            pinnedLowDensityResourcesPredicate);

    ImmutableCollection<ModuleSplit> densitySplits =
        splitter.split(ModuleSplit.forResources(testModule));

    ModuleSplit masterSplit =
        densitySplits.stream().filter(split -> split.isMasterSplit()).collect(onlyElement());
    assertThat(extractPaths(masterSplit.getEntries()))
        .containsExactly(
            "res/drawable-ldpi/image.jpg",
            "res/drawable-ldpi-v21/image.jpg",
            "res/drawable-ldpi-v24/image.jpg");

    ImmutableList<ModuleSplit> configSplits =
        densitySplits.stream().filter(split -> !split.isMasterSplit()).collect(toImmutableList());
    assertThat(configSplits).isNotEmpty();
    for (ModuleSplit configSplit : configSplits) {
      assertThat(extractPaths(configSplit.getEntries()))
          .containsNoneOf(
              "res/drawable-ldpi/image.jpg",
              "res/drawable-ldpi-v21/image.jpg",
              "res/drawable-ldpi-v24/image.jpg");
    }
  }

  @Test
  public void lowestDensityConfigsPinnedToMaster_masterCoversRangeOfDensities() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            // Pinned resource, just for lowest and highest densities.
            .addFile("res/drawable-ldpi/image.jpg")
            .addFile("res/drawable-xxxhdpi/image.jpg")
            // Non-pinned resource for all densities (to make sure no density split is empty).
            .addFile("res/drawable-ldpi/other.jpg")
            .addFile("res/drawable-mdpi/other.jpg")
            .addFile("res/drawable-tvdpi/other.jpg")
            .addFile("res/drawable-xhdpi/other.jpg")
            .addFile("res/drawable-xxhdpi/other.jpg")
            .addFile("res/drawable-xxxhdpi/other.jpg")
            .addFile("a/a/a.jpg")
            .addFile("a/b/a.jpg")
            .setResourceTable(
                new ResourceTableBuilder()
                    .addPackage("com.test.app")
                    .addDrawableResourceForMultipleDensities(
                        "image",
                        ImmutableMap.of(
                            /* ldpi */ 120, "res/drawable-ldpi/image.jpg",
                            /* xxxhdpi */ 640, "res/drawable-xxxhdpi/image.jpg"))
                    .addDrawableResourceForMultipleDensities(
                        "other",
                        ImmutableMap.<Integer, String>builder()
                            .put(/* ldpi */ 120, "res/drawable-ldpi/other.jpg")
                            .put(/* mdpi */ 160, "res/drawable-mdpi/other.jpg")
                            .put(/* tvdpi */ 213, "res/drawable-tvdpi/other.jpg")
                            .put(/* hdpi */ 240, "res/drawable-hdpi/other.jpg")
                            .put(/* xhdpi */ 320, "res/drawable-xhdpi/other.jpg")
                            .put(/* xxhdpi */ 480, "res/drawable-xxhdpi/other.jpg")
                            .put(/* xxxhdpi */ 640, "res/drawable-xxxhdpi/image.jpg")
                            .build())
                    .addDrawableResourceForMultipleDensities(
                        "a",
                        ImmutableMap.of(
                            /* ldpi */ 120, "a/a/a.jpg",
                            /* xxxhdpi */ 640, "a/b/a.jpg"))
                    .build())
            .setManifest(androidManifest("com.test.app"))
            .build();

    // 0x7f010000 is the "drawable/image" resource.
    Predicate<ResourceId> pinnedLowDensityResourcesPredicate =
        resourceId -> resourceId.getFullResourceId() == 0x7f010000;
    ScreenDensityResourcesSplitter splitter =
        new ScreenDensityResourcesSplitter(
            BundleToolVersion.getCurrentVersion(),
            NO_RESOURCES_PINNED_TO_MASTER,
            pinnedLowDensityResourcesPredicate);

    ImmutableCollection<ModuleSplit> densitySplits =
        splitter.split(ModuleSplit.forResources(testModule));

    ImmutableList<ModuleSplit> configSplits =
        densitySplits.stream().filter(split -> !split.isMasterSplit()).collect(toImmutableList());
    assertThat(configSplits).isNotEmpty();
    for (ModuleSplit configSplit : configSplits) {
      DensityAlias targetDensity =
          configSplit.getApkTargeting().getScreenDensityTargeting().getValue(0).getDensityAlias();
      switch (targetDensity) {
        case LDPI:
        case MDPI:
          // Devices <= MDPI are covered by the LDPI config.
          assertThat(extractPaths(configSplit.getEntries()))
              .doesNotContain("res/drawable-xxxhdpi/image.jpg");
          assertThat(extractPaths(configSplit.getEntries()))
              .doesNotContain("a/b/a.jpg");
          break;

        default:
          // Devices > MDPI are covered by the XXXHDPI config.
          assertThat(extractPaths(configSplit.getEntries()))
              .contains("res/drawable-xxxhdpi/image.jpg");
          assertThat(extractPaths(configSplit.getEntries()))
              .contains("a/b/a.jpg");
      }
    }
  }

  private static ModuleSplit findModuleSplitWithScreenDensityTargeting(
      ImmutableCollection<ModuleSplit> moduleSplits, DensityAlias densityAlias) {
    return findModuleSplitWithScreenDensityTargeting(
        moduleSplits, ScreenDensity.newBuilder().setDensityAlias(densityAlias).build());
  }

  private static ModuleSplit findModuleSplitWithScreenDensityTargeting(
      ImmutableCollection<ModuleSplit> moduleSplits, ScreenDensity density) {
    return moduleSplits.stream()
        .filter(
            split ->
                split.getApkTargeting().getScreenDensityTargeting().getValueCount() > 0
                    && density.equals(
                        split.getApkTargeting().getScreenDensityTargeting().getValue(0)))
        .collect(onlyElement());
  }

  private static ModuleSplit findModuleSplitWithDefaultTargeting(
      ImmutableCollection<ModuleSplit> moduleSplits) {
    return moduleSplits.stream()
        .filter(split -> split.getApkTargeting().equals(ApkTargeting.getDefaultInstance()))
        .collect(onlyElement());
  }
}
