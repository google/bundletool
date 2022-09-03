/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.tools.build.bundletool.model.BundleModule.MANIFEST_FILENAME;
import static com.android.tools.build.bundletool.model.SdkAsar.SDK_METADATA_FILE_NAME;
import static com.android.tools.build.bundletool.model.utils.BundleParser.SDK_INTERFACE_DESCRIPTORS_FILE_NAME;
import static com.android.tools.build.bundletool.model.utils.BundleParser.SDK_MODULES_FILE_NAME;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.bundle.SdkMetadataOuterClass.SdkMetadata;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.protobuf.ExtensionRegistry;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Utility methods to read data out of ASAR files in useful formats in tests. */
public final class AsarUtils {

  public static SdkMetadata extractSdkMetadata(ZipFile asarFile) throws Exception {
    ZipEntry metadataEntry = asarFile.getEntry(SDK_METADATA_FILE_NAME);
    InputStream data = asarFile.getInputStream(metadataEntry);
    return SdkMetadata.parseFrom(data, ExtensionRegistry.getEmptyRegistry());
  }

  public static String extractSdkManifest(ZipFile asarFile) throws Exception {
    ZipEntry manifestEntry = asarFile.getEntry(MANIFEST_FILENAME);
    InputStream data = asarFile.getInputStream(manifestEntry);
    return CharStreams.toString(new InputStreamReader(data, UTF_8));
  }

  public static byte[] extractSdkModuleData(ZipFile asarFile) throws Exception {
    ZipEntry moduleEntry = asarFile.getEntry(SDK_MODULES_FILE_NAME);
    InputStream data = asarFile.getInputStream(moduleEntry);
    return ByteStreams.toByteArray(data);
  }

  public static byte[] extractSdkInterfaceDescriptors(ZipFile asarFile) throws Exception {
    ZipEntry moduleEntry = asarFile.getEntry(SDK_INTERFACE_DESCRIPTORS_FILE_NAME);
    InputStream data = asarFile.getInputStream(moduleEntry);
    return ByteStreams.toByteArray(data);
  }

  private AsarUtils() {}
}
