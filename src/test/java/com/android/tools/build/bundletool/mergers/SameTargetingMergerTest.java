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

package com.android.tools.build.bundletool.mergers;

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ModuleSplitUtils.createModuleSplitBuilder;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.USER_PACKAGE_OFFSET;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.entry;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.pkg;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.resourceTable;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.type;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.lPlusVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeLibraries;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedNativeDirectory;
import static com.android.tools.build.bundletool.testing.TestUtils.extractPaths;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.InMemoryModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SameTargetingMergerTest {

  private static final byte[] DUMMY_CONTENT = new byte[1];

  private static final AndroidManifest DEFAULT_MANIFEST =
      AndroidManifest.create(androidManifest("com.test.app"));

  @Test
  public void sameSplitTargeting_oneGroup() throws Exception {
    ModuleSplit moduleSplit =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile("assets/some_asset.txt", DUMMY_CONTENT)))
            .setApkTargeting(ApkTargeting.getDefaultInstance())
            .build();
    ModuleSplit moduleSplit2 =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile("assets/some_other_asset.txt", DUMMY_CONTENT)))
            .setApkTargeting(ApkTargeting.getDefaultInstance())
            .build();

    ImmutableCollection<ModuleSplit> splits =
        new SameTargetingMerger().merge(ImmutableList.of(moduleSplit, moduleSplit2));

    assertThat(splits).hasSize(1);
    ModuleSplit masterSplit = splits.iterator().next();
    assertThat(extractPaths(masterSplit.getEntries()))
        .containsExactly("assets/some_asset.txt", "assets/some_other_asset.txt");
    assertThat(masterSplit.getApkTargeting()).isEqualToDefaultInstance();
    assertThat(masterSplit.getVariantTargeting()).isEqualTo(lPlusVariantTargeting());
    assertThat(masterSplit.getAndroidManifest()).isEqualTo(DEFAULT_MANIFEST);
    assertThat(masterSplit.getResourceTable()).isEmpty();
  }

  @Test
  public void sameSplitTargeting_multipleGroups() throws Exception {
    ImmutableSet<AbiAlias> abis =
        ImmutableSet.of(AbiAlias.X86, AbiAlias.ARMEABI_V7A, AbiAlias.MIPS);
    ModuleSplit moduleSplit =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile("testModule/lib/x86/liba.so", DUMMY_CONTENT)))
            .setMasterSplit(false)
            .setApkTargeting(apkAbiTargeting(ImmutableSet.of(AbiAlias.X86), abis))
            .build();
    ModuleSplit moduleSplit2 =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile("testModule/lib/armv7-eabi/liba.so", DUMMY_CONTENT)))
            .setMasterSplit(false)
            .setApkTargeting(apkAbiTargeting(ImmutableSet.of(AbiAlias.ARMEABI), abis))
            .build();
    ModuleSplit moduleSplit3 =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile("testModule/lib/mips/liba.so", DUMMY_CONTENT)))
            .setMasterSplit(false)
            .setApkTargeting(apkAbiTargeting(ImmutableSet.of(AbiAlias.MIPS), abis))
            .build();
    ModuleSplit moduleSplit4 =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile("testModule/lib/x86/libb.so", DUMMY_CONTENT)))
            .setMasterSplit(false)
            .setApkTargeting(apkAbiTargeting(ImmutableSet.of(AbiAlias.X86), abis))
            .build();
    ModuleSplit moduleSplit5 =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile("testModule/lib/armv7-eabi/libb.so", DUMMY_CONTENT)))
            .setMasterSplit(false)
            .setApkTargeting(apkAbiTargeting(ImmutableSet.of(AbiAlias.ARMEABI), abis))
            .build();
    ModuleSplit moduleSplit6 =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile("testModule/lib/mips/libb.so", DUMMY_CONTENT)))
            .setMasterSplit(false)
            .setApkTargeting(apkAbiTargeting(ImmutableSet.of(AbiAlias.MIPS), abis))
            .build();

    SameTargetingMerger merger = new SameTargetingMerger();
    ImmutableCollection<ModuleSplit> splits =
        merger.merge(
            ImmutableList.of(
                moduleSplit, moduleSplit2, moduleSplit3, moduleSplit4, moduleSplit5, moduleSplit6));

    assertThat(splits).hasSize(3);
    assertThat(splits.stream().map(ModuleSplit::getApkTargeting).collect(toImmutableList()))
        .ignoringRepeatedFieldOrder()
        .containsExactly(
            apkAbiTargeting(ImmutableSet.of(AbiAlias.MIPS), abis),
            apkAbiTargeting(ImmutableSet.of(AbiAlias.X86), abis),
            apkAbiTargeting(ImmutableSet.of(AbiAlias.ARMEABI), abis));
    assertThat(
            splits
                .stream()
                .map(ModuleSplit::getVariantTargeting)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(lPlusVariantTargeting());
  }

  @Test
  public void isMasterSplit_mergesTrue() throws Exception {
    ModuleSplit moduleSplit = createModuleSplitBuilder().setMasterSplit(true).build();
    ModuleSplit moduleSplit2 = createModuleSplitBuilder().setMasterSplit(true).build();

    ImmutableCollection<ModuleSplit> splits =
        new SameTargetingMerger().merge(ImmutableList.of(moduleSplit, moduleSplit2));

    assertThat(splits).hasSize(1);
    ModuleSplit mergedSplit = splits.iterator().next();
    assertThat(mergedSplit.isMasterSplit()).isTrue();
  }

  @Test
  public void isMasterSplit_mergesFalse() throws Exception {
    ModuleSplit moduleSplit = createModuleSplitBuilder().setMasterSplit(false).build();
    ModuleSplit moduleSplit2 = createModuleSplitBuilder().setMasterSplit(false).build();

    ImmutableCollection<ModuleSplit> splits =
        new SameTargetingMerger().merge(ImmutableList.of(moduleSplit, moduleSplit2));

    assertThat(splits).hasSize(1);
    ModuleSplit mergedSplit = splits.iterator().next();
    assertThat(mergedSplit.isMasterSplit()).isFalse();
  }

  /**
   * At the moment, the merger doesn't support merging two different resource tables so we check if
   * we reject such cases.
   */
  @Test
  public void mergerRejects_conflictingResourceTables() throws Exception {
    ModuleSplit split1 =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile(
                        "testModule/res/drawable/image1.jpg", DUMMY_CONTENT)))
            .setResourceTable(
                resourceTable(
                    pkg(
                        USER_PACKAGE_OFFSET,
                        "com.test.app",
                        type(0x01, "drawable", entry(0x01, "res/drawable/image1.jpg")))))
            .build();
    ModuleSplit split2 =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile(
                        "testModule/res/drawable/image2.jpg", DUMMY_CONTENT)))
            .setResourceTable(
                resourceTable(
                    pkg(
                        USER_PACKAGE_OFFSET,
                        "com.test.app",
                        type(0x01, "drawable", entry(0x01, "res/drawable/image2.jpg")))))
            .build();

    Throwable exception =
        assertThrows(
            IllegalStateException.class,
            () -> new SameTargetingMerger().merge(ImmutableList.of(split1, split2)));

    assertThat(exception).hasMessageThat().contains("two distinct resource tables");
  }

  /**
   * At the moment, the merger doesn't support merging two different android manifests so we check
   * that we reject cases where manifest is different within a set chosen for unification.
   */
  @Test
  public void mergerRejects_conflictingManifests() throws Exception {
    AndroidManifest manifest1 = AndroidManifest.create(androidManifest("com.test.app1"));
    AndroidManifest manifest2 = AndroidManifest.create(androidManifest("com.test.app2"));
    ModuleSplit split1 = createModuleSplitBuilder().setAndroidManifest(manifest1).build();
    ModuleSplit split2 = createModuleSplitBuilder().setAndroidManifest(manifest2).build();

    Throwable exception =
        assertThrows(
            IllegalStateException.class,
            () -> new SameTargetingMerger().merge(ImmutableList.of(split1, split2)));

    assertThat(exception).hasMessageThat().contains("two distinct manifests");
  }

  @Test
  public void mergerRejects_conflictingNativeConfigs() throws Exception {
    ModuleSplit split1 =
        createModuleSplitBuilder()
            .setNativeConfig(
                nativeLibraries(
                    targetedNativeDirectory("lib/mips", nativeDirectoryTargeting(AbiAlias.MIPS))))
            .build();
    ModuleSplit split2 =
        createModuleSplitBuilder()
            .setNativeConfig(
                nativeLibraries(
                    targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(AbiAlias.X86))))
            .build();
    Throwable exception =
        assertThrows(
            IllegalStateException.class,
            () -> new SameTargetingMerger().merge(ImmutableList.of(split1, split2)));

    assertThat(exception).hasMessageThat().contains("two distinct native configs");
  }

  @Test
  public void mergerRejects_conflictingModuleNames() throws Exception {
    ModuleSplit split1 =
        createModuleSplitBuilder().setModuleName(BundleModuleName.create("module1")).build();
    ModuleSplit split2 =
        createModuleSplitBuilder().setModuleName(BundleModuleName.create("module2")).build();

    Throwable exception =
        assertThrows(
            IllegalStateException.class,
            () -> new SameTargetingMerger().merge(ImmutableList.of(split1, split2)));

    assertThat(exception).hasMessageThat().contains("two distinct module names");
  }

  @Test
  public void mergerRejects_conflictingIsMasterSplitValues() throws Exception {
    ModuleSplit split1 = createModuleSplitBuilder().setMasterSplit(true).build();
    ModuleSplit split2 = createModuleSplitBuilder().setMasterSplit(false).build();

    Throwable exception =
        assertThrows(
            IllegalStateException.class,
            () -> new SameTargetingMerger().merge(ImmutableList.of(split1, split2)));

    assertThat(exception).hasMessageThat().contains("conflicting isMasterSplit flag values");
  }

  @Test
  public void mergingFromMultipleVariants_throws() {
    ModuleSplit split1 =
        createModuleSplitBuilder().setVariantTargeting(lPlusVariantTargeting()).build();
    ModuleSplit split2 =
        createModuleSplitBuilder()
            .setVariantTargeting(VariantTargeting.getDefaultInstance())
            .build();

    Throwable exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> new SameTargetingMerger().merge(ImmutableList.of(split1, split2)));
    assertThat(exception)
        .hasMessageThat()
        .contains("SameTargetingMerger doesn't support merging splits from different variants.");
  }
}
