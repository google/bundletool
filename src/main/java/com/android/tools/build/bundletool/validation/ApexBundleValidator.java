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

package com.android.tools.build.bundletool.validation;

import static com.android.tools.build.bundletool.model.AbiName.ARM64_V8A;
import static com.android.tools.build.bundletool.model.AbiName.ARMEABI_V7A;
import static com.android.tools.build.bundletool.model.AbiName.X86;
import static com.android.tools.build.bundletool.model.AbiName.X86_64;
import static com.android.tools.build.bundletool.model.BundleModule.ABI_SPLITTER;
import static com.android.tools.build.bundletool.model.BundleModule.APEX_DIRECTORY;
import static com.android.tools.build.bundletool.model.BundleModule.APEX_MANIFEST_JSON_PATH;
import static com.android.tools.build.bundletool.model.BundleModule.APEX_MANIFEST_PATH;
import static com.android.tools.build.bundletool.model.BundleModule.APEX_NOTICE_PATH;
import static com.android.tools.build.bundletool.model.BundleModule.APEX_PUBKEY_PATH;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.apex.ApexManifestProto.ApexManifest;
import com.android.bundle.Config.ApexConfig;
import com.android.bundle.Files.ApexImages;
import com.android.bundle.Files.TargetedApexImage;
import com.android.tools.build.bundletool.model.AbiName;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;

/** Validates an APEX bundle. */
public class ApexBundleValidator extends SubValidator {

  private static final ImmutableList<ZipPath> ALLOWED_APEX_FILES_OUTSIDE_APEX_DIRECTORY =
      ImmutableList.of(
          APEX_MANIFEST_PATH, APEX_MANIFEST_JSON_PATH, APEX_NOTICE_PATH, APEX_PUBKEY_PATH);

  // The bundle must contain a system image for at least one of each of these sets.
  private static final ImmutableSet<ImmutableSet<ImmutableSet<AbiName>>> REQUIRED_ONE_OF_ABI_SETS =
      ImmutableSet.of(
          // These 32-bit ABIs must be present.
          ImmutableSet.of(ImmutableSet.of(X86)),
          ImmutableSet.of(ImmutableSet.of(ARMEABI_V7A)),
          // These 64-bit ABIs must be present on their own or with the corresponding 32-bit ABI.
          ImmutableSet.of(ImmutableSet.of(X86_64), ImmutableSet.of(X86_64, X86)),
          ImmutableSet.of(ImmutableSet.of(ARM64_V8A), ImmutableSet.of(ARM64_V8A, ARMEABI_V7A)));

  @Override
  public void validateAllModules(ImmutableList<BundleModule> modules) {
    long numberOfApexModules =
        modules.stream().map(BundleModule::getApexConfig).filter(Optional::isPresent).count();
    if (numberOfApexModules == 0) {
      return;
    }

    if (numberOfApexModules > 1) {
      throw InvalidBundleException.builder()
          .withUserMessage("Multiple APEX modules are not allowed, found %d.", numberOfApexModules)
          .build();
    }

    if (modules.size() > 1) {
      throw InvalidBundleException.builder()
          .withUserMessage("APEX bundles must only contain one module, found %d.", modules.size())
          .build();
    }
  }

  @Override
  public void validateModule(BundleModule module) {
    if (module.findEntriesUnderPath(APEX_DIRECTORY).count() == 0) {
      return;
    }

    Optional<ModuleEntry> apexManifest = module.getEntry(APEX_MANIFEST_PATH);
    if (apexManifest.isPresent()) {
      validateApexManifest(apexManifest.get());
    } else {
      apexManifest = module.getEntry(APEX_MANIFEST_JSON_PATH);
      if (!apexManifest.isPresent()) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Missing expected file in APEX bundle: '%s' or '%s'.",
                APEX_MANIFEST_PATH, APEX_MANIFEST_JSON_PATH)
            .build();
      }
      validateApexManifestJson(apexManifest.get());
    }

    ImmutableSet.Builder<String> apexImagesBuilder = ImmutableSet.builder();
    ImmutableSet.Builder<String> apexBuildInfosBuilder = ImmutableSet.builder();
    ImmutableSet.Builder<String> apexFileNamesBuilder = ImmutableSet.builder();
    for (ModuleEntry entry : module.getEntries()) {
      ZipPath path = entry.getPath();
      if (path.startsWith(APEX_DIRECTORY)) {
        if (path.getFileName().toString().endsWith(BundleModule.APEX_IMAGE_SUFFIX)) {
          apexImagesBuilder.add(path.toString());
          apexFileNamesBuilder.add(path.getFileName().toString());
        } else if (path.getFileName().toString().endsWith(BundleModule.BUILD_INFO_SUFFIX)) {
          apexBuildInfosBuilder.add(path.toString());
        } else {
          throw InvalidBundleException.builder()
              .withUserMessage(
                  "Unexpected file in apex directory of bundle: '%s'.", entry.getPath())
              .build();
        }
      } else if (!ALLOWED_APEX_FILES_OUTSIDE_APEX_DIRECTORY.contains(path)) {
        throw InvalidBundleException.builder()
            .withUserMessage("Unexpected file in APEX bundle: '%s'.", entry.getPath())
            .build();
      }
    }

    ImmutableSet<String> apexBuildInfos = apexBuildInfosBuilder.build();
    ImmutableSet<String> apexImages = apexImagesBuilder.build();
    ImmutableSet<ImmutableSet<AbiName>> allAbiNameSets =
        apexFileNamesBuilder.build().stream()
            .map(ApexBundleValidator::abiNamesFromFile)
            .collect(toImmutableSet());
    if (allAbiNameSets.size() != apexImages.size()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Every APEX image file must target a unique set of architectures, "
                  + "but found multiple files that target the same set of architectures.")
          .build();
    }

    ImmutableSet<String> expectedImages =
        apexBuildInfos.stream()
            .map(f -> f.replace(BundleModule.BUILD_INFO_SUFFIX, BundleModule.APEX_IMAGE_SUFFIX))
            .collect(toImmutableSet());
    if (!apexBuildInfos.isEmpty() && !expectedImages.equals(apexImages)) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "If APEX build info is provided then one must be provided for each APEX image file:\n"
                  + " Expected %s\n"
                  + " Found %s.",
              expectedImages, apexImages)
          .build();
    }

    // When bundle config declares supported ABIs, the bundle should have the exact list of images.
    ImmutableSet<ImmutableSet<AbiName>> supportedAbis = declaredSupportedAbis(module);
    if (!supportedAbis.isEmpty() && !supportedAbis.equals(allAbiNameSets)) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "If supported_abi_set is set in config then it should match with APEX image files:\n"
                  + " Expected %s\n"
                  + " Found %s.",
              supportedAbis, allAbiNameSets)
          .build();
    }

    // When config doesn't declare supported abi list, the bundle should have all required abis.
    if (supportedAbis.isEmpty()
        && REQUIRED_ONE_OF_ABI_SETS.stream()
            .anyMatch(oneOf -> oneOf.stream().noneMatch(allAbiNameSets::contains))) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "APEX bundle must contain one of %s.",
              Joiner.on(" and one of ").join(REQUIRED_ONE_OF_ABI_SETS))
          .build();
    }

    module.getApexConfig().ifPresent(targeting -> validateTargeting(apexImages, targeting));
  }

  private static ImmutableSet<ImmutableSet<AbiName>> declaredSupportedAbis(BundleModule module) {
    final Optional<ApexConfig> apexConfig = module.getBundleApexConfig();
    if (!apexConfig.isPresent()) {
      return ImmutableSet.of();
    }

    return apexConfig.get().getSupportedAbiSetList().stream()
        .map(
            abiSet ->
                abiSet.getAbiList().stream()
                    .map(
                        abi ->
                            AbiName.fromPlatformName(abi)
                                .orElseThrow(
                                    () ->
                                        InvalidBundleException.builder()
                                            .withUserMessage(
                                                "Unrecognized ABI '%s' in"
                                                    + " config.supported_abi_set.",
                                                abi)
                                            .build()))
                    .collect(toImmutableSet()))
        .collect(toImmutableSet());
  }

  private static ImmutableSet<AbiName> abiNamesFromFile(String fileName) {
    ImmutableList<String> tokens = ImmutableList.copyOf(ABI_SPLITTER.splitToList(fileName));

    // We assume that the validity of each file name was already confirmed
    return tokens.stream()
        // Do not include the suffix "img".
        .limit(tokens.size() - 1)
        .map(AbiName::fromPlatformName)
        .map(Optional::get)
        .collect(toImmutableSet());
  }

  private static void validateTargeting(ImmutableSet<String> allImages, ApexImages targeting) {
    ImmutableSet<String> targetedImages =
        targeting.getImageList().stream().map(TargetedApexImage::getPath).collect(toImmutableSet());

    ImmutableSet<String> untargetedImages =
        Sets.difference(allImages, targetedImages).immutableCopy();
    if (!untargetedImages.isEmpty()) {
      throw InvalidBundleException.builder()
          .withUserMessage("Found APEX image files that are not targeted: %s", untargetedImages)
          .build();
    }

    ImmutableSet<String> missingTargetedImages =
        Sets.difference(targetedImages, allImages).immutableCopy();
    if (!missingTargetedImages.isEmpty()) {
      throw InvalidBundleException.builder()
          .withUserMessage("Targeted APEX image files are missing: %s", missingTargetedImages)
          .build();
    }
  }

  private static void validateApexManifest(ModuleEntry entry) {
    try (InputStream inputStream = entry.getContent().openStream()) {
      ApexManifest apexManifest =
          ApexManifest.parseFrom(inputStream, ExtensionRegistry.getEmptyRegistry());
      if (apexManifest.getName().isEmpty()) {
        throw InvalidBundleException.builder()
            .withUserMessage("APEX manifest must have a package name.")
            .build();
      }
    } catch (InvalidProtocolBufferException e) {
      throw InvalidBundleException.builder()
          .withUserMessage("Couldn't parse APEX manifest")
          .withCause(e)
          .build();
    } catch (IOException e) {
      throw new UncheckedIOException("Couldn't read APEX manifest.", e);
    }
  }

  private static void validateApexManifestJson(ModuleEntry entry) {
    try (BufferedReader reader = entry.getContent().asCharSource(UTF_8).openBufferedStream()) {
      JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
      JsonElement element = json.get("name");
      if (element == null || element.getAsString().isEmpty()) {
        throw InvalidBundleException.builder()
            .withUserMessage("APEX manifest must have a package name.")
            .build();
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Couldn't read APEX manifest.", e);
    }
  }
}
