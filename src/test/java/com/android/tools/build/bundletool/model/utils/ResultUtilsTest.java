/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.build.bundletool.model.utils;

import static com.android.tools.build.bundletool.model.utils.ResultUtils.readTableOfContents;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createApkDescription;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createApksArchiveFile;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createApksDirectory;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createInstantApkSet;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createMasterApkDescription;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createSplitApkSet;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createStandaloneApkSet;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createSystemApkSet;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createVariant;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.lPlusVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantSdkTargeting;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Commands.Variant;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ResultUtilsTest {

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();
  private Path tmpDir;

  @Before
  public void setUp() throws Exception {
    tmpDir = tmp.getRoot().toPath();
  }

  @Test
  public void emptyBuildApksResult_readTableOfContents() throws Exception {

    Path apksArchiveFile =
        createApksArchiveFile(BuildApksResult.getDefaultInstance(), tmpDir.resolve("bundle.apks"));

    BuildApksResult buildApksResult = readTableOfContents(apksArchiveFile);
    assertThat(buildApksResult).isEqualTo(BuildApksResult.getDefaultInstance());
  }

  @Test
  public void emptyBuildApksResult_inDirectory_readTableOfContents() throws Exception {

    Path apksDirectory = createApksDirectory(BuildApksResult.getDefaultInstance(), tmpDir);

    BuildApksResult buildApksResult = readTableOfContents(apksDirectory);
    assertThat(buildApksResult).isEqualToDefaultInstance();
  }

  @Test
  public void buildApksResult_readTableOfContents() throws Exception {
    ZipPath apkLBase = ZipPath.create("apkL-base.apk");
    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .addVariant(
                createVariant(
                    VariantTargeting.getDefaultInstance(),
                    createSplitApkSet(
                        "base",
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkLBase))))
            .build();

    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    BuildApksResult buildApksResult = readTableOfContents(apksArchiveFile);
    assertThat(buildApksResult).isEqualTo(tableOfContentsProto);
  }

  @Test
  public void buildApksResult_inDirectory_readTableOfContents() throws Exception {
    ZipPath apkLBase = ZipPath.create("apkL-base.apk");
    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .addVariant(
                createVariant(
                    lPlusVariantTargeting(),
                    createSplitApkSet(
                        "base",
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkLBase))))
            .build();

    Path apksDirectory = createApksDirectory(tableOfContentsProto, tmpDir);

    BuildApksResult buildApksResult = readTableOfContents(apksDirectory);
    assertThat(buildApksResult).isEqualTo(tableOfContentsProto);
  }

  @Test
  public void filterInstantApkVariant() throws Exception {
    Variant standaloneVariant = createStandaloneVariant();
    Variant splitVariant = createSplitVariant();
    Variant instantVariant = createInstantVariant();
    BuildApksResult apksResult =
        BuildApksResult.newBuilder()
            .addVariant(standaloneVariant)
            .addVariant(splitVariant)
            .addVariant(instantVariant)
            .build();

    assertThat(ResultUtils.instantApkVariants(apksResult)).containsExactly(instantVariant);
  }

  @Test
  public void filterSplitApkVariant() throws Exception {
    Variant standaloneVariant = createStandaloneVariant();
    Variant splitVariant = createSplitVariant();
    Variant instantVariant = createInstantVariant();
    BuildApksResult apksResult =
        BuildApksResult.newBuilder()
            .addVariant(standaloneVariant)
            .addVariant(splitVariant)
            .addVariant(instantVariant)
            .build();

    assertThat(ResultUtils.splitApkVariants(apksResult)).containsExactly(splitVariant);
  }

  @Test
  public void filterStandaloneApkVariant() throws Exception {
    Variant standaloneVariant = createStandaloneVariant();
    Variant splitVariant = createSplitVariant();
    Variant instantVariant = createInstantVariant();
    BuildApksResult apksResult =
        BuildApksResult.newBuilder()
            .addVariant(standaloneVariant)
            .addVariant(splitVariant)
            .addVariant(instantVariant)
            .build();

    assertThat(ResultUtils.standaloneApkVariants(apksResult)).containsExactly(standaloneVariant);
  }

  @Test
  public void filterSystemApkVariant() throws Exception {
    Variant systemVariant = createSystemVariant();
    BuildApksResult apksResult = BuildApksResult.newBuilder().addVariant(systemVariant).build();

    assertThat(ResultUtils.systemApkVariants(apksResult)).containsExactly(systemVariant);
  }

  @Test
  public void isInstantApkVariantTrue() throws Exception {
    Variant variant = createInstantVariant();

    assertThat(ResultUtils.isInstantApkVariant(variant)).isTrue();
    assertThat(ResultUtils.isSplitApkVariant(variant)).isFalse();
    assertThat(ResultUtils.isStandaloneApkVariant(variant)).isFalse();
    assertThat(ResultUtils.isSystemApkVariant(variant)).isFalse();
  }

  @Test
  public void isStandaloneApkVariantTrue() throws Exception {
    Variant variant = createStandaloneVariant();

    assertThat(ResultUtils.isStandaloneApkVariant(variant)).isTrue();
    assertThat(ResultUtils.isSplitApkVariant(variant)).isFalse();
    assertThat(ResultUtils.isInstantApkVariant(variant)).isFalse();
    assertThat(ResultUtils.isSystemApkVariant(variant)).isFalse();
  }

  @Test
  public void isSplitApkVariantTrue() throws Exception {
    Variant variant = createSplitVariant();

    assertThat(ResultUtils.isSplitApkVariant(variant)).isTrue();
    assertThat(ResultUtils.isStandaloneApkVariant(variant)).isFalse();
    assertThat(ResultUtils.isInstantApkVariant(variant)).isFalse();
    assertThat(ResultUtils.isSystemApkVariant(variant)).isFalse();
  }

  @Test
  public void isSystemApkVariantTrue() throws Exception {
    Variant variant = createSystemVariant();

    assertThat(ResultUtils.isSplitApkVariant(variant)).isFalse();
    assertThat(ResultUtils.isStandaloneApkVariant(variant)).isFalse();
    assertThat(ResultUtils.isInstantApkVariant(variant)).isFalse();
    assertThat(ResultUtils.isSystemApkVariant(variant)).isTrue();
  }

  private Variant createInstantVariant() {
    Path apkLBase = ZipPath.create("instant/apkL-base.apk");
    Path apkLFeature = ZipPath.create("instant/apkL-feature.apk");
    Path apkLOther = ZipPath.create("instant/apkL-other.apk");
    return createVariant(
        variantSdkTargeting(sdkVersionFrom(21), ImmutableSet.of(SdkVersion.getDefaultInstance())),
        createInstantApkSet("base", ApkTargeting.getDefaultInstance(), apkLBase),
        createInstantApkSet("feature", ApkTargeting.getDefaultInstance(), apkLFeature),
        createInstantApkSet("other", ApkTargeting.getDefaultInstance(), apkLOther));
  }

  private Variant createSplitVariant() {
    ZipPath apkL = ZipPath.create("splits/apkL.apk");
    ZipPath apkLx86 = ZipPath.create("splits/apkL-x86.apk");
    return createVariant(
        variantSdkTargeting(sdkVersionFrom(21)),
        createSplitApkSet(
            "base",
            createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkL),
            createApkDescription(
                apkAbiTargeting(AbiAlias.X86, ImmutableSet.of()),
                apkLx86,
                /* isMasterSplit= */ false)));
  }

  private Variant createStandaloneVariant() {
    Path apkPreL = ZipPath.create("apkPreL.apk");
    return createVariant(
        variantSdkTargeting(sdkVersionFrom(15), ImmutableSet.of(SdkVersion.getDefaultInstance())),
        createStandaloneApkSet(ApkTargeting.getDefaultInstance(), apkPreL));
  }

  private Variant createSystemVariant() {
    Path systemApk = ZipPath.create("system.apk");
    return createVariant(
        variantSdkTargeting(sdkVersionFrom(15), ImmutableSet.of(SdkVersion.getDefaultInstance())),
        createSystemApkSet(ApkTargeting.getDefaultInstance(), systemApk));
  }
}
