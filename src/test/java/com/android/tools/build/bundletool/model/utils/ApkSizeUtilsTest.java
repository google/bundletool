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

import static com.android.bundle.Targeting.Abi.AbiAlias.X86;
import static com.android.tools.build.bundletool.model.utils.ApkSizeUtils.getVariantCompressedSizeByApkPaths;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createApkDescription;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createApksArchiveFile;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createMasterApkDescription;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createSplitApkSet;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createVariant;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createVariantForSingleSplitApk;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantSdkTargeting;
import static com.google.common.truth.Truth.assertThat;

import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Commands.Variant;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.tools.build.bundletool.io.ZipBuilder;
import com.android.tools.build.bundletool.io.ZipBuilder.EntryOption;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ApkSizeUtilsTest {

  private static final byte[] TEST_BYTES = new byte[100];
  @Rule public final TemporaryFolder tmp = new TemporaryFolder();
  private Path tmpDir;

  @Before
  public void setUp() {
    tmpDir = tmp.getRoot().toPath();
  }

  @Test
  public void oneLVariant() throws Exception {
    ZipPath apkOne = ZipPath.create("apk_one.apk");
    ImmutableList<Variant> variants =
        ImmutableList.of(
            createVariantForSingleSplitApk(
                variantSdkTargeting(sdkVersionFrom(21)),
                ApkTargeting.getDefaultInstance(),
                apkOne));

    Path apksArchiveFile =
        createApksArchiveFile(
            BuildApksResult.newBuilder().addAllVariant(variants).build(),
            tmpDir.resolve("bundle.apks"));

    ImmutableMap<String, Long> sizeByApkPaths =
        getVariantCompressedSizeByApkPaths(variants, apksArchiveFile);
    assertThat(sizeByApkPaths.keySet()).containsExactly("apk_one.apk");

    assertThat(sizeByApkPaths.get("apk_one.apk")).isAtLeast(1L);
  }

  @Test
  public void oneLVariant_multipleModules() throws Exception {
    ZipPath baseApk = ZipPath.create("base.apk");
    ZipPath baseX86Apk = ZipPath.create("base-x86.apk");
    ZipPath featureApk = ZipPath.create("feature.apk");
    ZipPath featureX86Apk = ZipPath.create("feature-x86.apk");
    ImmutableList<Variant> variants =
        ImmutableList.of(
            createVariant(
                variantSdkTargeting(sdkVersionFrom(21)),
                createSplitApkSet(
                    "base",
                    createMasterApkDescription(ApkTargeting.getDefaultInstance(), baseApk),
                    createApkDescription(apkAbiTargeting(X86), baseX86Apk, false)),
                createSplitApkSet(
                    "feature",
                    createMasterApkDescription(ApkTargeting.getDefaultInstance(), featureApk),
                    createApkDescription(apkAbiTargeting(X86), featureX86Apk, false))));

    Path apksArchiveFile =
        createApksArchiveFile(
            BuildApksResult.newBuilder().addAllVariant(variants).build(),
            tmpDir.resolve("bundle.apks"));

    ImmutableMap<String, Long> sizeByApkPaths =
        getVariantCompressedSizeByApkPaths(variants, apksArchiveFile);
    assertThat(sizeByApkPaths.keySet())
        .containsExactly("base.apk", "base-x86.apk", "feature.apk", "feature-x86.apk");

    assertThat(sizeByApkPaths.get("base.apk")).isAtLeast(1L);
    assertThat(sizeByApkPaths.get("base-x86.apk")).isAtLeast(1L);
    assertThat(sizeByApkPaths.get("feature.apk")).isAtLeast(1L);
    assertThat(sizeByApkPaths.get("feature-x86.apk")).isAtLeast(1L);
  }

  @Test
  public void multipleVariants() throws Exception {
    ZipPath apkOne = ZipPath.create("apk_one.apk");
    ZipPath apkTwo = ZipPath.create("apk_two.apk");
    ImmutableList<Variant> variants =
        ImmutableList.of(
            createVariantForSingleSplitApk(
                variantSdkTargeting(sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(23))),
                ApkTargeting.getDefaultInstance(),
                apkOne),
            createVariantForSingleSplitApk(
                variantSdkTargeting(sdkVersionFrom(23), ImmutableSet.of(sdkVersionFrom(21))),
                ApkTargeting.getDefaultInstance(),
                apkTwo));

    Path apksArchiveFile =
        createApksArchiveFile(
            BuildApksResult.newBuilder().addAllVariant(variants).build(),
            tmpDir.resolve("bundle.apks"));

    ImmutableMap<String, Long> sizeByApkPaths =
        getVariantCompressedSizeByApkPaths(variants, apksArchiveFile);
    assertThat(sizeByApkPaths.keySet()).containsExactly("apk_one.apk", "apk_two.apk");

    long apkOneSize = sizeByApkPaths.get("apk_one.apk");
    long apkTwoSize = sizeByApkPaths.get("apk_two.apk");
    assertThat(apkOneSize).isAtLeast(1L);
    assertThat(apkTwoSize).isAtLeast(1L);
  }

  @Test
  public void multipleVariants_withUncompressedEntries() throws Exception {
    ZipPath apkOne = ZipPath.create("apk_one.apk");
    ZipPath apkTwo = ZipPath.create("apk_two.apk");
    ImmutableList<Variant> variants =
        ImmutableList.of(
            createVariantForSingleSplitApk(
                variantSdkTargeting(sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(23))),
                ApkTargeting.getDefaultInstance(),
                apkOne),
            createVariantForSingleSplitApk(
                variantSdkTargeting(sdkVersionFrom(23), ImmutableSet.of(sdkVersionFrom(21))),
                ApkTargeting.getDefaultInstance(),
                apkTwo));

    ZipBuilder archiveBuilder = new ZipBuilder();
    archiveBuilder.addFileWithContent(ZipPath.create(apkOne.toString()), TEST_BYTES);
    archiveBuilder.addFileWithContent(
        ZipPath.create(apkTwo.toString()),
        TEST_BYTES,
        EntryOption.UNCOMPRESSED); // APK stored uncompressed in the APKs zip.
    archiveBuilder.addFileWithProtoContent(
        ZipPath.create("toc.pb"), BuildApksResult.newBuilder().addAllVariant(variants).build());
    Path apksArchiveFile = archiveBuilder.writeTo(tmpDir.resolve("bundle.apks"));

    ImmutableMap<String, Long> sizeByApkPaths =
        getVariantCompressedSizeByApkPaths(variants, apksArchiveFile);
    assertThat(sizeByApkPaths.keySet()).containsExactly("apk_one.apk", "apk_two.apk");

    long apkOneSize = sizeByApkPaths.get("apk_one.apk");
    long apkTwoSize = sizeByApkPaths.get("apk_two.apk");
    // Apks should have size at least 20. Gzip Header(10 bytes), deflated data (Min 2 Bytes for
    // empty file), 8 bytes footer.
    assertThat(apkOneSize).isGreaterThan(20L);
    assertThat(apkTwoSize).isGreaterThan(20L);
    assertThat(apkOneSize).isEqualTo(apkTwoSize);
  }

  @Test
  public void multipleVariants_selectOneVariant() throws Exception {
    ZipPath apkOne = ZipPath.create("apk_one.apk");
    ZipPath apkTwo = ZipPath.create("apk_two.apk");
    Variant lVariant =
        createVariantForSingleSplitApk(
            variantSdkTargeting(sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(23))),
            ApkTargeting.getDefaultInstance(),
            apkOne);
    Variant mVariant =
        createVariantForSingleSplitApk(
            variantSdkTargeting(sdkVersionFrom(23), ImmutableSet.of(sdkVersionFrom(21))),
            ApkTargeting.getDefaultInstance(),
            apkTwo);
    ImmutableList<Variant> variants = ImmutableList.of(lVariant, mVariant);

    Path apksArchiveFile =
        createApksArchiveFile(
            BuildApksResult.newBuilder().addAllVariant(variants).build(),
            tmpDir.resolve("bundle.apks"));

    ImmutableMap<String, Long> sizeByApkPaths =
        getVariantCompressedSizeByApkPaths(ImmutableList.of(lVariant), apksArchiveFile);
    assertThat(sizeByApkPaths.keySet()).containsExactly("apk_one.apk");

    assertThat(sizeByApkPaths.get("apk_one.apk")).isAtLeast(1L);
  }
}
