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

package com.android.tools.build.bundletool.device.activitymanager;

import static com.android.tools.build.bundletool.device.AdbServer.ADB_TIMEOUT_MS;

import com.android.tools.build.bundletool.device.AdbShellCommandTask;
import com.android.tools.build.bundletool.device.Device;
import com.android.tools.build.bundletool.device.activitymanager.ResourceConfigParser.ResourceConfigHandler;
import com.android.tools.build.bundletool.model.utils.Versions;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Runs and stores the activity manager shell command results. */
public class ActivityManagerRunner {

  private final Device device;

  private Supplier<ImmutableList<String>> activityManagerCommandResult =
      Suppliers.memoize(
          () -> {
            int apiLevel = getDevice().getVersion().getApiLevel();
            if (apiLevel < Versions.ANDROID_L_API_VERSION) {
              return ImmutableList.of();
            }
            return new AdbShellCommandTask(getDevice(), ACTIVITY_MANAGER_CONFIG_COMMAND)
                .execute(ADB_TIMEOUT_MS, TimeUnit.MILLISECONDS);
          });

  private static final String ACTIVITY_MANAGER_CONFIG_COMMAND = "am get-config";
  private static final String ABI_LINE_PREFIX = "abi: ";
  private static final String RESOURCE_CONFIG_LINE_PREFIX = "config: ";

  public ActivityManagerRunner(Device device) {
    this.device = device;
  }

  /** Returns a list of locales or empty list if they couldn't be detected. */
  public ImmutableList<String> getDeviceLocales() {
    return activityManagerCommandResult.get().stream()
        .filter(line -> line.startsWith(RESOURCE_CONFIG_LINE_PREFIX))
        .findFirst()
        .map(this::parseResourceConfig)
        .orElse(ImmutableList.of());
  }

  /** Returns a list of ABIs or empty list if they couldn't be detected. */
  public ImmutableList<String> getDeviceAbis() {
    return activityManagerCommandResult.get().stream()
        .filter(line -> line.startsWith(ABI_LINE_PREFIX))
        .findFirst()
        .map(AbiStringParser::parseAbiLine)
        .orElse(ImmutableList.of());
  }

  private ImmutableList<String> parseResourceConfig(String configLine) {
    String resourceConfig = configLine.substring(RESOURCE_CONFIG_LINE_PREFIX.length());
    return ResourceConfigParser.parseDeviceConfig(resourceConfig, new LocaleExtractor());
  }

  private Device getDevice() {
    return device;
  }

  private static class LocaleExtractor implements ResourceConfigHandler<ImmutableList<String>> {

    private final ImmutableList.Builder<String> locales = ImmutableList.builder();

    @Override
    public void onLocale(String locale) {
      locales.add(locale);
    }

    @Override
    public ImmutableList<String> getOutput() {
      return locales.build();
    }
  }
}
