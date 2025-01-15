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

package com.android.tools.build.bundletool.validation;

import static com.android.tools.build.bundletool.model.BundleMetadata.BUNDLETOOL_NAMESPACE;
import static com.android.tools.build.bundletool.model.BundleMetadata.DEVICE_GROUP_CONFIG_JSON_FILE_NAME;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.Optimizations;
import com.android.bundle.Config.SplitDimension;
import com.android.bundle.Config.SplitDimension.Value;
import com.android.bundle.Config.SplitsConfig;
import com.android.bundle.Config.SuffixStripping;
import com.android.bundle.DeviceGroup;
import com.android.bundle.DeviceGroupConfig;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.protobuf.util.JsonFormat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DeviceGroupTargetingValidatorTest {

  @Test
  public void validateBundle_unknownDefaultGroup_throws() throws Exception {
    DeviceGroupConfig deviceGroupConfig =
        DeviceGroupConfig.newBuilder()
            .addDeviceGroups(DeviceGroup.newBuilder().setName("definedA"))
            .addDeviceGroups(DeviceGroup.newBuilder().setName("definedB"))
            .build();
    SplitsConfig unknownDefaultGroup =
        SplitsConfig.newBuilder()
            .addSplitDimension(
                SplitDimension.newBuilder()
                    .setValue(Value.DEVICE_GROUP)
                    .setSuffixStripping(SuffixStripping.newBuilder().setDefaultSuffix("xyz")))
            .build();
    BundleModule moduleA =
        new BundleModuleBuilder("a").setManifest(androidManifest("com.test.app")).build();
    AppBundle appBundle =
        AppBundle.buildFromModules(
            ImmutableList.of(moduleA),
            BundleConfig.newBuilder()
                .setOptimizations(Optimizations.newBuilder().setSplitsConfig(unknownDefaultGroup))
                .build(),
            BundleMetadata.builder()
                .addFile(
                    BUNDLETOOL_NAMESPACE,
                    DEVICE_GROUP_CONFIG_JSON_FILE_NAME,
                    toJson(deviceGroupConfig))
                .build());

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new DeviceGroupTargetingValidator().validateBundle(appBundle));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "default device group [xyz] which is not in the list [definedA, definedB, other]");
  }

  @Test
  public void validateBundle_unknownModuleGroup_throws() throws Exception {
    DeviceGroupConfig deviceGroupConfig =
        DeviceGroupConfig.newBuilder()
            .addDeviceGroups(DeviceGroup.newBuilder().setName("highRam"))
            .addDeviceGroups(DeviceGroup.newBuilder().setName("lowRam"))
            .build();
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img1#group_highRam/image.jpg")
            .addFile("assets/img1#group_mediumRam/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();
    SplitsConfig defaultLowRam =
        SplitsConfig.newBuilder()
            .addSplitDimension(
                SplitDimension.newBuilder()
                    .setValue(Value.DEVICE_GROUP)
                    .setSuffixStripping(SuffixStripping.newBuilder().setDefaultSuffix("lowRam")))
            .build();
    AppBundle appBundle =
        AppBundle.buildFromModules(
            ImmutableList.of(moduleA),
            BundleConfig.newBuilder()
                .setOptimizations(Optimizations.newBuilder().setSplitsConfig(defaultLowRam))
                .build(),
            BundleMetadata.builder()
                .addFile(
                    BUNDLETOOL_NAMESPACE,
                    DEVICE_GROUP_CONFIG_JSON_FILE_NAME,
                    toJson(deviceGroupConfig))
                .build());

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new DeviceGroupTargetingValidator().validateBundle(appBundle));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "'a' refers to device group [mediumRam] which is not in the list"
                + " [highRam, lowRam, other]");
  }

  @Test
  public void validateBundle_noDeviceGroupConfig_succeeds() throws Exception {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img1#group_highRam/image.jpg")
            .addFile("assets/img1#group_lowRam/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();
    AppBundle appBundle =
        AppBundle.buildFromModules(
            ImmutableList.of(moduleA),
            BundleConfig.getDefaultInstance(),
            BundleMetadata.builder().build());

    new DeviceGroupTargetingValidator().validateBundle(appBundle);
  }

  @Test
  public void validateBundle_allGroupsDefined_succeeds() throws Exception {
    DeviceGroupConfig deviceGroupConfig =
        DeviceGroupConfig.newBuilder()
            .addDeviceGroups(DeviceGroup.newBuilder().setName("highRam"))
            .addDeviceGroups(DeviceGroup.newBuilder().setName("lowRam"))
            .build();
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img1#group_highRam/image.jpg")
            .addFile("assets/img1#group_lowRam/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();
    SplitsConfig defaultLowRam =
        SplitsConfig.newBuilder()
            .addSplitDimension(
                SplitDimension.newBuilder()
                    .setValue(Value.DEVICE_GROUP)
                    .setSuffixStripping(SuffixStripping.newBuilder().setDefaultSuffix("lowRam")))
            .build();
    AppBundle appBundle =
        AppBundle.buildFromModules(
            ImmutableList.of(moduleA),
            BundleConfig.newBuilder()
                .setOptimizations(Optimizations.newBuilder().setSplitsConfig(defaultLowRam))
                .build(),
            BundleMetadata.builder()
                .addFile(
                    BUNDLETOOL_NAMESPACE,
                    DEVICE_GROUP_CONFIG_JSON_FILE_NAME,
                    toJson(deviceGroupConfig))
                .build());

    new DeviceGroupTargetingValidator().validateBundle(appBundle);
  }

  @Test
  public void validateBundle_noDefaultGroup_succeeds() throws Exception {
    DeviceGroupConfig deviceGroupConfig =
        DeviceGroupConfig.newBuilder()
            .addDeviceGroups(DeviceGroup.newBuilder().setName("highRam"))
            .addDeviceGroups(DeviceGroup.newBuilder().setName("lowRam"))
            .build();
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img1#group_highRam/image.jpg")
            .addFile("assets/img1#group_lowRam/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();
    AppBundle appBundle =
        AppBundle.buildFromModules(
            ImmutableList.of(moduleA),
            BundleConfig.getDefaultInstance(),
            BundleMetadata.builder()
                .addFile(
                    BUNDLETOOL_NAMESPACE,
                    DEVICE_GROUP_CONFIG_JSON_FILE_NAME,
                    toJson(deviceGroupConfig))
                .build());

    new DeviceGroupTargetingValidator().validateBundle(appBundle);
  }

  @Test
  public void validateBundle_someUnusedGroups_succeeds() throws Exception {
    DeviceGroupConfig deviceGroupConfig =
        DeviceGroupConfig.newBuilder()
            .addDeviceGroups(DeviceGroup.newBuilder().setName("highRam"))
            .addDeviceGroups(DeviceGroup.newBuilder().setName("lowRam"))
            .build();
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img1#group_highRam/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();
    AppBundle appBundle =
        AppBundle.buildFromModules(
            ImmutableList.of(moduleA),
            BundleConfig.getDefaultInstance(),
            BundleMetadata.builder()
                .addFile(
                    BUNDLETOOL_NAMESPACE,
                    DEVICE_GROUP_CONFIG_JSON_FILE_NAME,
                    toJson(deviceGroupConfig))
                .build());

    new DeviceGroupTargetingValidator().validateBundle(appBundle);
  }

  private ByteSource toJson(DeviceGroupConfig config) throws Exception {
    String json = JsonFormat.printer().print(config);
    return CharSource.wrap(json).asByteSource(UTF_8);
  }
}
