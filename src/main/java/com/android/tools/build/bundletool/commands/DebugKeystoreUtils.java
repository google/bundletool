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

package com.android.tools.build.bundletool.commands;

import com.android.tools.build.bundletool.model.Password;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore.PasswordProtection;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

/** Utility methods related to debug keystore. */
public final class DebugKeystoreUtils {

  private static final String ANDROID_SDK_HOME = "ANDROID_SDK_HOME";
  private static final String HOME = "HOME";
  private static final String USER_HOME = "user.home";

  private static final String DEBUG_KEY_ALIAS = "AndroidDebugKey";
  private static final String ANDROID_DOT_DIR = ".android";
  private static final String DEBUG_KEYSTORE_FILENAME = "debug.keystore";

  public static final Password DEBUG_KEY_PASSWORD =
      new Password(() -> new PasswordProtection("android".toCharArray()));

  public static final LoadingCache<SystemEnvironmentProvider, Optional<Path>> DEBUG_KEYSTORE_CACHE =
      CacheBuilder.newBuilder().build(CacheLoader.from(DebugKeystoreUtils::getDebugKeystorePath));

  public static Optional<SigningConfiguration> getDebugSigningConfiguration(
      SystemEnvironmentProvider provider) {
    try {
      return DEBUG_KEYSTORE_CACHE
          .get(provider)
          .map(
              keystorePath ->
                  SigningConfiguration.extractFromKeystore(
                      keystorePath,
                      DEBUG_KEY_ALIAS,
                      Optional.of(DEBUG_KEY_PASSWORD),
                      Optional.of(DEBUG_KEY_PASSWORD)));
    } catch (ExecutionException e) {
      return Optional.empty();
    }
  }

  /**
   * Returns the path to the debug keystore if it exists.
   *
   * <p>The path resolution logic is based on AOSP resolution rules.
   */
  private static Optional<Path> getDebugKeystorePath(SystemEnvironmentProvider envProvider) {
    return Stream.of(
            envProvider.getVariable(ANDROID_SDK_HOME),
            envProvider.getProperty(USER_HOME),
            envProvider.getVariable(HOME))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .map(Paths::get)
        .filter(Files::isDirectory)
        .map(path -> path.resolve(ANDROID_DOT_DIR).resolve(DEBUG_KEYSTORE_FILENAME))
        .filter(Files::exists)
        .filter(Files::isReadable)
        .findFirst();
  }

  private DebugKeystoreUtils() {}
}
