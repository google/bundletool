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

package com.android.tools.build.bundletool.io;

import static com.android.tools.build.bundletool.model.BundleModule.MANIFEST_FILENAME;
import static com.android.tools.build.bundletool.model.SdkAsar.SDK_METADATA_FILE_NAME;
import static com.android.tools.build.bundletool.model.utils.BundleParser.SDK_INTERFACE_DESCRIPTORS_FILE_NAME;
import static com.android.tools.build.bundletool.model.utils.BundleParser.SDK_MODULES_FILE_NAME;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.tools.build.bundletool.model.SdkAsar;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.xml.XmlUtils;
import com.google.common.io.ByteSource;
import java.io.IOException;
import java.nio.file.Path;

/** Serializer for SDK ASARs. */
public class SdkAsarSerializer {

  /** Writes an ASAR file to disk at the given location. */
  public static void writeToDisk(SdkAsar asar, Path pathOnDisk) throws IOException {
    ZipBuilder zipBuilder = new ZipBuilder();

    zipBuilder.addFile(
        ZipPath.create(MANIFEST_FILENAME),
        ByteSource.wrap(XmlUtils.documentToString(asar.getManifest()).getBytes(UTF_8)));
    zipBuilder.addFileFromDisk(ZipPath.create(SDK_MODULES_FILE_NAME), asar.getModulesFile());
    zipBuilder.addFileWithProtoContent(
        ZipPath.create(SDK_METADATA_FILE_NAME), asar.getSdkMetadata());
    asar.getSdkInterfaceDescriptors()
        .ifPresent(
            apiDescriptors ->
                zipBuilder.addFile(
                    ZipPath.create(SDK_INTERFACE_DESCRIPTORS_FILE_NAME), apiDescriptors));

    zipBuilder.writeTo(pathOnDisk);
  }

  private SdkAsarSerializer() {}
}
