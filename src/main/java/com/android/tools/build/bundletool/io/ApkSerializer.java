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

import com.android.bundle.Commands.ApkDescription;
import com.android.tools.build.bundletool.model.ApkListener;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/** Serializes APKs on disk. */
public abstract class ApkSerializer {
  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final ApkListener apkListener;
  private final boolean verbose;

  ApkSerializer(Optional<ApkListener> apkListener, boolean verbose) {
    this.apkListener = apkListener.orElse(ApkListener.NO_OP);
    this.verbose = verbose;
  }

  public ApkDescription serialize(Path apkPath, ModuleSplit moduleSplit) {
    return serialize(apkPath.getParent(), apkPath.getFileName().toString(), moduleSplit);
  }

  public ApkDescription serialize(
      Path outputDirectory, String apkRelativePath, ModuleSplit moduleSplit) {
    ZipPath relativePath = ZipPath.create(apkRelativePath);
    return serialize(outputDirectory, ImmutableMap.of(relativePath, moduleSplit)).get(relativePath);
  }

  public abstract ImmutableMap<ZipPath, ApkDescription> serialize(
      Path outputDirectory, ImmutableMap<ZipPath, ModuleSplit> splitsByRelativePath);

  protected void notifyApkSerialized(
      ApkDescription apkDescription, ModuleSplit.SplitType splitType) {
    apkListener.onApkFinalized(apkDescription);

    if (verbose) {
      System.out.printf(
          "INFO: [%s] '%s' of type '%s' was written to disk.%n",
          LocalDateTime.now(ZoneId.systemDefault()).format(DATE_FORMATTER),
          apkDescription.getPath(),
          splitType);
    }
  }
}
