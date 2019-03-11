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

import static com.android.tools.build.bundletool.size.SizeUtils.addSizes;
import static com.android.tools.build.bundletool.size.SizeUtils.subtractSizes;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.bundle.SizesOuterClass.Sizes;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link SizeUtils}. */
@RunWith(JUnit4.class)
public class SizeUtilsTest {

  @Test
  public void addingSizes() {
    Sizes a = Sizes.newBuilder().setDiskSize(1).setDownloadSize(2).build();
    Sizes b = Sizes.newBuilder().setDiskSize(3).setDownloadSize(5).build();

    assertThat(addSizes(a, b))
        .isEqualTo(Sizes.newBuilder().setDiskSize(4).setDownloadSize(7).build());
  }

  @Test
  public void subtractingSizes() {
    Sizes a = Sizes.newBuilder().setDiskSize(4).setDownloadSize(6).build();
    Sizes b = Sizes.newBuilder().setDiskSize(1).setDownloadSize(2).build();

    assertThat(subtractSizes(a, b))
        .isEqualTo(Sizes.newBuilder().setDiskSize(3).setDownloadSize(4).build());
  }
}
