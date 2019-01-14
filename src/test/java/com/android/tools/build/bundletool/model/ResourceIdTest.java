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

package com.android.tools.build.bundletool.model;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ResourceIdTest {

  @Test
  public void getFullResourceId() {
    assertThat(
            ResourceId.builder()
                .setPackageId(0xff)
                .setTypeId(0xff)
                .setEntryId(0xffff)
                .build()
                .getFullResourceId())
        .isEqualTo(0xffffffff);
    assertThat(
            ResourceId.builder()
                .setPackageId(0x12)
                .setTypeId(0x34)
                .setEntryId(0x5678)
                .build()
                .getFullResourceId())
        .isEqualTo(0x12345678);
  }

  @Test
  public void createFromFullResourceId() {
    assertThat(ResourceId.create(0xffffffff))
        .isEqualTo(
            ResourceId.builder().setPackageId(0xff).setTypeId(0xff).setEntryId(0xffff).build());
    assertThat(ResourceId.create(0x12345678))
        .isEqualTo(
            ResourceId.builder().setPackageId(0x12).setTypeId(0x34).setEntryId(0x5678).build());
  }

  @Test
  public void badPackageId_throws() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> createFilledResourceId().setPackageId(0x100).build());
    assertThat(exception).hasMessageThat().contains("Package id not in");

    exception =
        assertThrows(
            IllegalStateException.class, () -> createFilledResourceId().setPackageId(-1).build());
    assertThat(exception).hasMessageThat().contains("Package id not in");
  }

  @Test
  public void badTypeId_throws() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> createFilledResourceId().setTypeId(0x100).build());
    assertThat(exception).hasMessageThat().contains("Type id not in");

    exception =
        assertThrows(
            IllegalStateException.class, () -> createFilledResourceId().setTypeId(0).build());
    assertThat(exception).hasMessageThat().contains("Type id not in");
  }

  @Test
  public void badEntryId_throws() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> createFilledResourceId().setEntryId(0x10000).build());
    assertThat(exception).hasMessageThat().contains("Entry id not in");

    exception =
        assertThrows(
            IllegalStateException.class, () -> createFilledResourceId().setEntryId(-1).build());
    assertThat(exception).hasMessageThat().contains("Entry id not in");
  }

  @Test
  public void idNotSet_throws() {
    assertThrows(IllegalStateException.class, () -> ResourceId.builder().build());
  }

  private ResourceId.Builder createFilledResourceId() {
    return ResourceId.builder().setEntryId(0x0000).setTypeId(0xff).setPackageId(0xff);
  }
}
