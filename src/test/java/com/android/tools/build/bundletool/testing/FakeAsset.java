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

package com.android.tools.build.bundletool.testing;

/** Fake representation of an asset in an SDK bundle for tests */
public class FakeAsset {
  private final String assetName;
  private final byte[] assetContent;

  public FakeAsset(String assetName, byte[] assetContent) {
    this.assetName = assetName;
    this.assetContent = assetContent;
  }

  public String getAssetName() {
    return assetName;
  }

  public byte[] getAssetContent() {
    return assetContent;
  }
}
