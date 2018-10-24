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

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;

/** Represents a resource id in an APK's resource table. */
@AutoValue
public abstract class ResourceId {

  public static final int MAX_ENTRY_ID = 0xffff;
  public static final int MAX_TYPE_ID = 0xff;
  public static final int MAX_PACKAGE_ID = 0xff;

  private static final int PACKAGE_SHIFT = 24;
  private static final int TYPE_SHIFT = 16;

  public abstract int getPackageId();

  public abstract int getTypeId();

  public abstract int getEntryId();

  public static Builder builder() {
    return new AutoValue_ResourceId.Builder();
  }

  /** Builder for {@link ResourceId}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setPackageId(int value);

    public abstract Builder setTypeId(int value);

    public abstract Builder setEntryId(int value);

    abstract ResourceId autoBuild();

    public ResourceId build() {
      ResourceId resourceId = autoBuild();
      Preconditions.checkState(
          0 <= resourceId.getEntryId() && resourceId.getEntryId() <= MAX_ENTRY_ID,
          "Entry id not in [0, 0xffff]");
      Preconditions.checkState(
          0 < resourceId.getTypeId() && resourceId.getTypeId() <= MAX_TYPE_ID,
          "Type id not in [1, 0xff]");
      Preconditions.checkState(
          0 <= resourceId.getPackageId() && resourceId.getPackageId() <= MAX_PACKAGE_ID,
          "Package id not in [0, 0xff]");
      return resourceId;
    }
  }

  /**
   * Calculates final id in resource table for usage in app.
   *
   * <p>It consists of:
   *
   * <ul>
   *   <li>8 bits for package id
   *   <li>8 bits for type id
   *   <li>16 bits for entry id
   * </ul>
   *
   * <p>For example:
   *
   * <ul>
   *   <li>0x7f - package id
   *   <li>0x0a - type id
   *   <li>0x1234 - entry id
   * </ul>
   *
   * The full id of this entry will be 0x7f0a1234
   */
  public int getFullResourceId() {
    return (getPackageId() << PACKAGE_SHIFT) + (getTypeId() << TYPE_SHIFT) + getEntryId();
  }
}
