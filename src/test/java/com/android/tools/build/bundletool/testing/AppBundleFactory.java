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

package com.android.tools.build.bundletool.testing;

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstant;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMaxSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.HDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.LDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.createResourceTable;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.fileReference;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeLibraries;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedNativeDirectory;

import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.tools.build.bundletool.model.AppBundle;
import java.io.IOException;

/** A collection of convenience creators of different types of {@link AppBundle} used in testing. */
public class AppBundleFactory {

  public static AppBundle createLdpiHdpiAppBundle() throws IOException {
    return new AppBundleBuilder()
        .addModule(
            "base",
            builder ->
                builder
                    .addFile("res/drawable-ldpi/image.jpg")
                    .addFile("res/drawable-hdpi/image.jpg")
                    .setResourceTable(
                        createResourceTable(
                            "image",
                            fileReference("res/drawable-ldpi/image.jpg", LDPI),
                            fileReference("res/drawable-hdpi/image.jpg", HDPI)))
                    .setManifest(androidManifest("com.test.app")))
        .build();
  }

  public static AppBundle createX86AppBundle() throws Exception {
    return new AppBundleBuilder()
        .addModule(
            "base",
            builder ->
                builder
                    .setManifest(androidManifest("com.test.app"))
                    // Add some native libraries.
                    .addFile("lib/x86/libsome.so")
                    .setNativeConfig(
                        nativeLibraries(
                            targetedNativeDirectory(
                                "lib/x86", nativeDirectoryTargeting(AbiAlias.X86)))))
        .build();
  }

  public static AppBundle createMaxSdkBundle(int maxSdkVersion) throws Exception {
    return new AppBundleBuilder()
        .addModule(
            "base",
            module ->
                module.setManifest(androidManifest("com.app", withMaxSdkVersion(maxSdkVersion))))
        .build();
  }

  public static AppBundle createMinSdkBundle(int minSdkVersion) throws Exception {
    return new AppBundleBuilder()
        .addModule(
            "base",
            module ->
                module.setManifest(androidManifest("com.app", withMinSdkVersion(minSdkVersion))))
        .build();
  }

  /** Creates a bundle that has its base module marked as instant. */
  public static AppBundle createInstantBundle() throws Exception {
    return new AppBundleBuilder()
        .addModule(
            "base", module -> module.setManifest(androidManifest("com.app", withInstant(true))))
        .build();
  }

  public static AppBundle createMinMaxSdkAppBundle(int minSdkVersion, int maxSdkVersion)
      throws Exception {
    return new AppBundleBuilder()
        .addModule(
            "base",
            module ->
                module.setManifest(
                    androidManifest(
                        "com.app",
                        withMinSdkVersion(minSdkVersion),
                        withMaxSdkVersion(maxSdkVersion))))
        .build();
  }
}
