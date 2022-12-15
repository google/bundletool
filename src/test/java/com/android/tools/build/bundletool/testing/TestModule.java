/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.google.common.base.Preconditions.checkArgument;

import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.Bundletool;
import com.android.bundle.Devices.DeviceSpec;
import com.android.tools.build.bundletool.commands.BuildApksCommand;
import com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode;
import com.android.tools.build.bundletool.commands.BuildSdkApksCommand;
import com.android.tools.build.bundletool.commands.CommandScoped;
import com.android.tools.build.bundletool.io.AppBundleSerializer;
import com.android.tools.build.bundletool.io.SdkBundleSerializer;
import com.android.tools.build.bundletool.io.TempDirectory;
import com.android.tools.build.bundletool.model.ApkListener;
import com.android.tools.build.bundletool.model.ApkModifier;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.Bundle;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.OptimizationDimension;
import com.android.tools.build.bundletool.model.SdkBundle;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.model.SigningConfigurationProvider;
import com.android.tools.build.bundletool.model.SourceStamp;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListeningExecutorService;
import dagger.Module;
import dagger.Provides;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.zip.ZipFile;
import javax.annotation.Nullable;

/** Dagger module for bundletool tests. */
@Module
public class TestModule {

  private final BuildApksCommand buildApksCommand;
  private final BuildSdkApksCommand buildSdkApksCommand;
  private final Bundle bundle;

  private TestModule(
      BuildApksCommand buildApksCommand, BuildSdkApksCommand buildSdkApksCommand, Bundle bundle) {
    this.buildApksCommand = buildApksCommand;
    this.buildSdkApksCommand = buildSdkApksCommand;
    this.bundle = bundle;
  }

  @Provides
  SdkBundle provideSdkBundle() {
    return (SdkBundle) bundle;
  }

  @Provides
  AppBundle provideAppBundle() {
    return (AppBundle) bundle;
  }

  @Provides
  BundleMetadata provideBundleMetadata() {
    return bundle.getBundleMetadata();
  }

  @Provides
  BuildApksCommand provideBuildApksCommand() {
    return buildApksCommand;
  }

  @Provides
  BuildSdkApksCommand provideBuildSdkApksCommand() {
    return buildSdkApksCommand;
  }

  @Provides
  @CommandScoped
  TempDirectory provideTempDirectory() {
    return new TempDirectory();
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for the TestModule. */
  public static class Builder {
    private static final BundleConfig DEFAULT_BUNDLE_CONFIG =
        BundleConfig.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .build();

    private static final BundleMetadata DEFAULT_BUNDLE_METADATA = BundleMetadata.builder().build();

    @Nullable private TempDirectory tempDirectory;
    @Nullable private Path outputDirectory;
    @Nullable private Path bundlePath;
    @Nullable private Bundle bundle;
    private BundleConfig bundleConfig = DEFAULT_BUNDLE_CONFIG;
    @Nullable private SigningConfiguration signingConfig;
    @Nullable private SigningConfigurationProvider signingConfigProvider;
    @Nullable private ApkModifier apkModifier;
    @Nullable private ApkListener apkListener;
    @Nullable private Integer firstVariantNumber;
    @Nullable private ListeningExecutorService executorService;
    @Nullable private Path outputPath;
    @Nullable private ApkBuildMode apkBuildMode;
    @Nullable private String[] moduleNames;
    @Nullable private DeviceSpec deviceSpec;
    @Nullable private Boolean fuseOnlyDeviceMatchingModules;
    @Nullable private Consumer<BuildApksCommand.Builder> buildApksCommandSetter;
    @Nullable private OptimizationDimension[] optimizationDimensions;
    @Nullable private PrintStream printStream;
    @Nullable private Boolean localTestingEnabled;
    @Nullable private SourceStamp sourceStamp;
    private BundleMetadata bundleMetadata = DEFAULT_BUNDLE_METADATA;

    public Builder withAppBundle(AppBundle appBundle) {
      this.bundle = appBundle;

      // If not set, set a default BundleConfig with the latest bundletool version.
      if (appBundle.getBundleConfig().equals(BundleConfig.getDefaultInstance())) {
        this.bundle = appBundle.toBuilder().setBundleConfig(DEFAULT_BUNDLE_CONFIG).build();
      }
      return this;
    }

    /**
     * Note: this actually merges the given BundleConfig with the default BundleConfig to allow
     * clients to specify only partial BundleConfig in their tests.
     */
    public Builder withBundleConfig(BundleConfig.Builder bundleConfig) {
      this.bundleConfig = this.bundleConfig.toBuilder().mergeFrom(bundleConfig.build()).build();
      return this;
    }

    public Builder withBundleMetadata(BundleMetadata bundleMetadata) {
      this.bundleMetadata = bundleMetadata;
      return this;
    }

    public Builder withSigningConfig(SigningConfiguration signingConfig) {
      this.signingConfig = signingConfig;
      return this;
    }

    public Builder withSigningConfigProvider(SigningConfigurationProvider signingConfigProvider) {
      this.signingConfigProvider = signingConfigProvider;
      return this;
    }

    public Builder withBundletoolVersion(String bundletoolVersion) {
      this.bundleConfig =
          this.bundleConfig.toBuilder()
              .mergeFrom(
                  BundleConfig.newBuilder()
                      .setBundletool(Bundletool.newBuilder().setVersion(bundletoolVersion))
                      .build())
              .build();
      return this;
    }

    public Builder withApkModifier(ApkModifier apkModifier) {
      this.apkModifier = apkModifier;
      return this;
    }

    public Builder withApkListener(ApkListener apkListener) {
      this.apkListener = apkListener;
      return this;
    }

    public Builder withFirstVariantNumber(int firstVariantNumber) {
      this.firstVariantNumber = firstVariantNumber;
      return this;
    }

    public Builder withExecutorService(ListeningExecutorService executorService) {
      this.executorService = executorService;
      return this;
    }

    public Builder withBundlePath(Path bundlePath) {
      this.bundlePath = bundlePath;
      return this;
    }

    public Builder withOutputPath(Path outputPath) {
      this.outputPath = outputPath;
      return this;
    }

    public Builder withApkBuildMode(ApkBuildMode apkBuildMode) {
      this.apkBuildMode = apkBuildMode;
      return this;
    }

    public Builder withFuseOnlyDeviceMatchingModules(boolean enabled) {
      this.fuseOnlyDeviceMatchingModules = enabled;
      return this;
    }

    public Builder withModules(String... moduleNames) {
      this.moduleNames = moduleNames;
      return this;
    }

    public Builder withDeviceSpec(DeviceSpec deviceSpec) {
      this.deviceSpec = deviceSpec;
      return this;
    }

    public Builder withOptimizationDimensions(OptimizationDimension... optimizationDimensions) {
      this.optimizationDimensions = optimizationDimensions;
      return this;
    }

    public Builder withCustomBuildApksCommandSetter(
        Consumer<BuildApksCommand.Builder> buildApksCommandSetter) {
      this.buildApksCommandSetter = buildApksCommandSetter;
      return this;
    }

    public Builder withOutputPrintStream(PrintStream printStream) {
      this.printStream = printStream;
      return this;
    }

    public Builder withLocalTestingEnabled(boolean enabled) {
      this.localTestingEnabled = enabled;
      return this;
    }

    public Builder withSourceStamp(SourceStamp sourceStamp) {
      this.sourceStamp = sourceStamp;
      return this;
    }

    public Builder withSdkBundle(SdkBundle sdkBundle) {
      this.bundle = sdkBundle;
      return this;
    }

    public TestModule build() {
      try {
        if (tempDirectory == null) {
          tempDirectory = new TempDirectory();
        }
        if (outputDirectory == null) {
          outputDirectory = tempDirectory.getPath();
        }

        checkArgument(
            bundle == null || bundlePath == null,
            "Cannot call both withAppBundle() and withBundlePath().");
        if (bundle == null) {
          // The default Bundle provided will be an AppBundle.
          if (bundlePath != null) {
            bundle = AppBundle.buildFromZip(new ZipFile(bundlePath.toFile()));
          } else {
            bundle =
                new AppBundleBuilder()
                    .setBundleConfig(bundleConfig)
                    .addModule("base", module -> module.setManifest(androidManifest("com.package")))
                    .build();
          }
        } else {
          if (!bundleConfig.equals(DEFAULT_BUNDLE_CONFIG)) {
            if (bundle instanceof AppBundle) {
              BundleConfig newBundleConfig =
                  ((AppBundle) bundle)
                      .getBundleConfig().toBuilder().mergeFrom(bundleConfig).build();
              bundle = ((AppBundle) bundle).toBuilder().setBundleConfig(newBundleConfig).build();
            }
          }
        }
        if (!bundleMetadata.equals(DEFAULT_BUNDLE_METADATA)) {
          if (bundle instanceof AppBundle) {
            bundle = ((AppBundle) bundle).toBuilder().setBundleMetadata(bundleMetadata).build();
          } else {
            bundle = ((SdkBundle) bundle).toBuilder().setBundleMetadata(bundleMetadata).build();
          }
        }
        if (bundlePath == null) {
          if (bundle instanceof AppBundle) {
            bundlePath = tempDirectory.getPath().resolve("bundle.aab");
            new AppBundleSerializer().writeToDisk((AppBundle) bundle, bundlePath);
          } else {
            bundlePath = tempDirectory.getPath().resolve("bundle.asb");
            new SdkBundleSerializer().writeToDisk((SdkBundle) bundle, bundlePath);
          }
        }
        if (outputPath == null) {
          outputPath = outputDirectory.resolve("bundle.apks");
        }

        BuildApksCommand.Builder command =
            BuildApksCommand.builder()
                .setAapt2Command(Aapt2Helper.getAapt2Command())
                .setBundlePath(bundlePath)
                .setOutputFile(outputPath);

        BuildSdkApksCommand.Builder sdkCommand =
            BuildSdkApksCommand.builder()
                .setAapt2Command(Aapt2Helper.getAapt2Command())
                .setSdkBundlePath(bundlePath)
                .setOutputFile(outputPath);

        if (signingConfig != null) {
          command.setSigningConfiguration(signingConfig);
          sdkCommand.setSigningConfiguration(signingConfig);
        }
        if (signingConfigProvider != null) {
          command.setSigningConfigurationProvider(signingConfigProvider);
        }
        if (apkModifier != null) {
          command.setApkModifier(apkModifier);
          sdkCommand.setApkModifier(apkModifier);
        }
        if (apkListener != null) {
          command.setApkListener(apkListener);
          sdkCommand.setApkListener(apkListener);
        }
        if (firstVariantNumber != null) {
          command.setFirstVariantNumber(firstVariantNumber);
          sdkCommand.setFirstVariantNumber(firstVariantNumber);
        }
        if (executorService != null) {
          command.setExecutorService(executorService);
          sdkCommand.setExecutorService(executorService);
        }
        if (apkBuildMode != null) {
          command.setApkBuildMode(apkBuildMode);
        }
        if (moduleNames != null) {
          command.setModules(ImmutableSet.copyOf(moduleNames));
        }
        if (deviceSpec != null) {
          command.setDeviceSpec(deviceSpec);
        }
        if (fuseOnlyDeviceMatchingModules != null) {
          command.setFuseOnlyDeviceMatchingModules(fuseOnlyDeviceMatchingModules);
        }
        if (optimizationDimensions != null) {
          command.setOptimizationDimensions(ImmutableSet.copyOf(optimizationDimensions));
        }
        if (printStream != null) {
          command.setOutputPrintStream(printStream);
        }
        if (localTestingEnabled != null) {
          command.setLocalTestingMode(localTestingEnabled);
        }
        if (sourceStamp != null) {
          command.setSourceStamp(sourceStamp);
        }
        if (buildApksCommandSetter != null) {
          buildApksCommandSetter.accept(command);
        }

        return new TestModule(command.build(), sdkCommand.build(), bundle);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
