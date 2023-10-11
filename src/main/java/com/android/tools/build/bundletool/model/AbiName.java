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

package com.android.tools.build.bundletool.model;

import static com.android.bundle.Targeting.Abi.AbiAlias.UNSPECIFIED_CPU_ARCHITECTURE;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.function.Function.identity;

import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.tools.build.bundletool.model.utils.EnumMapper;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

/** List of ABIs supported by BundleTool. */
public enum AbiName {
  ARMEABI("armeabi", 32),
  ARMEABI_V7A("armeabi-v7a", 32),
  ARM64_V8A("arm64-v8a", 64),
  X86("x86", 32),
  X86_64("x86_64", 64),
  MIPS("mips", 32),
  MIPS64("mips64", 64),
  RISCV64("riscv64", 64);

  private final String platformName;

  private final int bitSize;

  AbiName(String platformName, int bitSize) {
    this.platformName = platformName;
    this.bitSize = bitSize;
  }

  public String getPlatformName() {
    return platformName;
  }

  public int getBitSize() {
    return bitSize;
  }

  public AbiAlias toProto() {
    return ABI_ALIAS_TO_ABI_NAME_MAP.inverse().get(this);
  }

  private static final ImmutableBiMap<AbiAlias, AbiName> ABI_ALIAS_TO_ABI_NAME_MAP =
      EnumMapper.mapByName(
          AbiAlias.class,
          AbiName.class,
          /* ignoreValues= */ ImmutableSet.of(AbiAlias.UNRECOGNIZED, UNSPECIFIED_CPU_ARCHITECTURE));

  private static final ImmutableMap<String, AbiName> PLATFORM_NAME_TO_ABI_NAME_MAP =
      Arrays.stream(AbiName.values()).collect(toImmutableMap(AbiName::getPlatformName, identity()));

  public static AbiName fromProto(AbiAlias abiAlias) {
    return checkNotNull(
        ABI_ALIAS_TO_ABI_NAME_MAP.get(abiAlias), "Unrecognized ABI '%s'.", abiAlias);
  }

  public static Optional<AbiName> fromLibSubDirName(String subDirName) {
    if (subDirName.equals("arm64-v8a-hwasan")) {
      return Optional.of(ARM64_V8A);
    }
    return fromPlatformName(subDirName);
  }

  public static Optional<AbiName> fromPlatformName(String platformAbiName) {
    return Optional.ofNullable(PLATFORM_NAME_TO_ABI_NAME_MAP.get(platformAbiName));
  }

  public static ImmutableSet<String> getAllPlatformAbis() {
    return Stream.of(values()).map(AbiName::getPlatformName).collect(toImmutableSet());
  }
}
