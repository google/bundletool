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

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


@RunWith(JUnit4.class)
public class ApkComponentTest {

  @Test
  public void apkComponent_resource() {
    assertThat(ApkComponent.fromEntryName("resources.arsc")).isEqualTo(ApkComponent.RESOURCES);
    assertThat(ApkComponent.fromEntryName("res/raw/song01.ogg")).isEqualTo(ApkComponent.RESOURCES);
    assertThat(ApkComponent.fromEntryName("res/drawable-anydpi/splash.xml"))
        .isEqualTo(ApkComponent.RESOURCES);
  }

  @Test
  public void apkComponent_code() {
    assertThat(ApkComponent.fromEntryName("classes4.dex")).isEqualTo(ApkComponent.DEX);
  }

  @Test
  public void apkComponent_assets() {
    assertThat(ApkComponent.fromEntryName("assets/media/song.mp3")).isEqualTo(ApkComponent.ASSETS);
  }

  @Test
  public void apkComponent_nativeLibs() {
    assertThat(ApkComponent.fromEntryName("lib/armeabi/libnoname.so"))
        .isEqualTo(ApkComponent.NATIVE_LIBS);
  }

  @Test
  public void apkComponent_other() {
    assertThat(ApkComponent.fromEntryName("META-INF/CERT.RSA")).isEqualTo(ApkComponent.OTHER);
    assertThat(ApkComponent.fromEntryName("com/lib/cfg.properties")).isEqualTo(ApkComponent.OTHER);
    assertThat(ApkComponent.fromEntryName("AndroidManifest.xml")).isEqualTo(ApkComponent.OTHER);
  }
}
