/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.build.bundletool.size;

import com.android.bundle.SizesOuterClass.Sizes;

/** Utilities for computations using {@link Sizes} proto. */
public class SizeUtils {

  public static Sizes addSizes(Sizes sizeA, Sizes sizeB) {
    return Sizes.newBuilder()
        .setDiskSize(sizeA.getDiskSize() + sizeB.getDiskSize())
        .setDownloadSize(sizeA.getDownloadSize() + sizeB.getDownloadSize())
        .build();
  }

  public static Sizes subtractSizes(Sizes sizeA, Sizes sizeB) {
    return Sizes.newBuilder()
        .setDiskSize(sizeA.getDiskSize() - sizeB.getDiskSize())
        .setDownloadSize(sizeA.getDownloadSize() - sizeB.getDownloadSize())
        .build();
  }

  public static Sizes sizes(long diskSize, long downloadSize) {
    return Sizes.newBuilder().setDiskSize(diskSize).setDownloadSize(downloadSize).build();
  }

  private SizeUtils() {}
}
