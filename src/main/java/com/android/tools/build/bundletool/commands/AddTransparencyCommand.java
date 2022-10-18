/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.tools.build.bundletool.transparency.CodeTransparencyCryptoUtils.getX509Certificates;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;
import static org.jose4j.jws.AlgorithmIdentifiers.RSA_USING_SHA256;

import com.android.bundle.CodeTransparencyOuterClass.CodeTransparency;
import com.android.tools.build.bundletool.commands.CommandHelp.CommandDescription;
import com.android.tools.build.bundletool.commands.CommandHelp.FlagDescription;
import com.android.tools.build.bundletool.flags.Flag;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.io.AppBundleSerializer;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.Password;
import com.android.tools.build.bundletool.model.SignerConfig;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.utils.files.FilePreconditions;
import com.android.tools.build.bundletool.transparency.BundleTransparencyCheckUtils;
import com.android.tools.build.bundletool.transparency.CodeTransparencyFactory;
import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.keys.RsaKeyUtil;
import org.jose4j.lang.JoseException;
import org.jose4j.lang.UncheckedJoseException;

/** Command to add a Code Transparency File to Android App Bundle. */
@AutoValue
public abstract class AddTransparencyCommand {

  public static final String COMMAND_NAME = "add-transparency";

  /** Mode to run {@link AddTransparencyCommand} against. */
  public enum Mode {
    DEFAULT,
    GENERATE_CODE_TRANSPARENCY_FILE,
    INJECT_SIGNATURE;

    final String getLowerCaseName() {
      return Ascii.toLowerCase(name());
    }
  }

  /**
   * Defines a command behaviour when some APKs generated from the input App Bundle requires dex
   * merging.
   */
  public enum DexMergingChoice {
    ASK_IN_CONSOLE,
    CONTINUE,
    REJECT;

    final String getLowerCaseName() {
      return Ascii.toLowerCase(name());
    }
  }

  static final int MIN_RSA_KEY_LENGTH = 3072;

  private static final Flag<Mode> MODE_FLAG = Flag.enumFlag("mode", Mode.class);

  private static final Flag<Path> BUNDLE_LOCATION_FLAG = Flag.path("bundle");

  private static final Flag<Path> OUTPUT_FLAG = Flag.path("output");

  private static final Flag<Path> KEYSTORE_FLAG = Flag.path("ks");

  private static final Flag<String> KEY_ALIAS_FLAG = Flag.string("ks-key-alias");

  private static final Flag<Password> KEYSTORE_PASSWORD_FLAG = Flag.password("ks-pass");

  private static final Flag<Password> KEY_PASSWORD_FLAG = Flag.password("key-pass");

  private static final Flag<Path> TRANSPARENCY_KEY_CERTIFICATE_LOCATION_FLAG =
      Flag.path("transparency-key-certificate");

  private static final Flag<Path> TRANSPARENCY_SIGNATURE_LOCATION_FLAG =
      Flag.path("transparency-signature");

  private static final Flag<DexMergingChoice> DEX_MERGING_CHOICE_FLAG =
      Flag.enumFlag("dex-merging-choice", DexMergingChoice.class);

  private static final Flag<Boolean> ALLOW_SHARED_USER_ID_FLAG =
      Flag.booleanFlag("allow-shared-user-id");

  public abstract Mode getMode();

  public abstract Path getBundlePath();

  public abstract Path getOutputPath();

  public abstract DexMergingChoice getDexMergingChoice();

  public abstract Optional<SignerConfig> getSignerConfig();

  public abstract ImmutableList<X509Certificate> getTransparencyKeyCertificates();

  public abstract Optional<Path> getTransparencySignaturePath();

  public abstract Optional<Boolean> getAllowSharedUserId();

  public static AddTransparencyCommand.Builder builder() {
    return new AutoValue_AddTransparencyCommand.Builder()
        .setMode(Mode.DEFAULT)
        .setDexMergingChoice(DexMergingChoice.ASK_IN_CONSOLE)
        .setTransparencyKeyCertificates(ImmutableList.of());
  }

  /** Builder for the {@link AddTransparencyCommand}. */
  @AutoValue.Builder
  public abstract static class Builder {

    /** Sets the mode to run the command against. */
    public abstract Builder setMode(Mode mode);

    /** Sets the path to the input bundle. Must have the extension ".aab". */
    public abstract Builder setBundlePath(Path bundlePath);

    /**
     * Sets the path to the output file. If the output file already exists, it can not be
     * overwritten.
     */
    public abstract Builder setOutputPath(Path bundlePath);

    /** Sets code transparency signer configuration. */
    public abstract Builder setSignerConfig(SignerConfig signerConfig);

    /** Sets the public key certificate of the code transparency key. */
    public abstract Builder setTransparencyKeyCertificates(
        List<X509Certificate> transparencyKeyCertificates);

    /** Sets path to the file containing code transparency signature. */
    public abstract Builder setTransparencySignaturePath(Path transparencySignaturePath);

    /**
     * Sets how command should behave when dex merging is required for some APKs generated from the
     * input App Bundle.
     */
    public abstract Builder setDexMergingChoice(DexMergingChoice value);

    public abstract Builder setAllowSharedUserId(Boolean value);

    public abstract AddTransparencyCommand build();
  }

  public static AddTransparencyCommand fromFlags(ParsedFlags flags) {
    Mode mode = MODE_FLAG.getValue(flags).orElse(Mode.DEFAULT);
    switch (mode) {
      case DEFAULT:
        return fromFlagsInDefaultMode(flags);
      case GENERATE_CODE_TRANSPARENCY_FILE:
        return fromFlagsInGenerateGenerateCodeTransparencyFileMode(flags);
      case INJECT_SIGNATURE:
        return fromFlagsInInjectSignatureMode(flags);
    }
    throw new IllegalStateException("Unrecognized value of --mode flag.");
  }

  private static AddTransparencyCommand fromFlagsInDefaultMode(ParsedFlags flags) {
    Path keystorePath = KEYSTORE_FLAG.getRequiredValue(flags);
    String keyAlias = KEY_ALIAS_FLAG.getRequiredValue(flags);
    Optional<Password> keystorePassword = KEYSTORE_PASSWORD_FLAG.getValue(flags);
    Optional<Password> keyPassword = KEY_PASSWORD_FLAG.getValue(flags);
    SignerConfig signerConfig =
        SignerConfig.extractFromKeystore(keystorePath, keyAlias, keystorePassword, keyPassword);
    Optional<Boolean> allowSharedUserId = ALLOW_SHARED_USER_ID_FLAG.getValue(flags);
    AddTransparencyCommand.Builder addTransparencyCommandBuilder =
        AddTransparencyCommand.builder()
            .setMode(Mode.DEFAULT)
            .setBundlePath(BUNDLE_LOCATION_FLAG.getRequiredValue(flags))
            .setOutputPath(OUTPUT_FLAG.getRequiredValue(flags))
            .setDexMergingChoice(
                DEX_MERGING_CHOICE_FLAG.getValue(flags).orElse(DexMergingChoice.ASK_IN_CONSOLE))
            .setSignerConfig(signerConfig);
    allowSharedUserId.ifPresent(addTransparencyCommandBuilder::setAllowSharedUserId);
    flags.checkNoUnknownFlags();
    return addTransparencyCommandBuilder.build();
  }

  private static AddTransparencyCommand fromFlagsInGenerateGenerateCodeTransparencyFileMode(
      ParsedFlags flags) {
    Optional<Boolean> allowSharedUserId = ALLOW_SHARED_USER_ID_FLAG.getValue(flags);
    AddTransparencyCommand.Builder addTransparencyCommandBuilder =
        AddTransparencyCommand.builder()
            .setMode(Mode.GENERATE_CODE_TRANSPARENCY_FILE)
            .setBundlePath(BUNDLE_LOCATION_FLAG.getRequiredValue(flags))
            .setOutputPath(OUTPUT_FLAG.getRequiredValue(flags))
            .setTransparencyKeyCertificates(
                getX509Certificates(
                    TRANSPARENCY_KEY_CERTIFICATE_LOCATION_FLAG.getRequiredValue(flags)));
    allowSharedUserId.ifPresent(addTransparencyCommandBuilder::setAllowSharedUserId);
    flags.checkNoUnknownFlags();
    return addTransparencyCommandBuilder.build();
  }

  private static AddTransparencyCommand fromFlagsInInjectSignatureMode(ParsedFlags flags) {
    Optional<Boolean> allowSharedUserId = ALLOW_SHARED_USER_ID_FLAG.getValue(flags);
    AddTransparencyCommand.Builder addTransparencyCommandBuilder =
        AddTransparencyCommand.builder()
            .setMode(Mode.INJECT_SIGNATURE)
            .setBundlePath(BUNDLE_LOCATION_FLAG.getRequiredValue(flags))
            .setOutputPath(OUTPUT_FLAG.getRequiredValue(flags))
            .setTransparencySignaturePath(
                TRANSPARENCY_SIGNATURE_LOCATION_FLAG.getRequiredValue(flags))
            .setTransparencyKeyCertificates(
                getX509Certificates(
                    TRANSPARENCY_KEY_CERTIFICATE_LOCATION_FLAG.getRequiredValue(flags)));
    allowSharedUserId.ifPresent(addTransparencyCommandBuilder::setAllowSharedUserId);
    flags.checkNoUnknownFlags();
    return addTransparencyCommandBuilder.build();
  }

  public void execute() {
    validateCommonInputs();

    try (ZipFile bundleZip = new ZipFile(getBundlePath().toFile())) {
      AppBundle inputBundle = AppBundle.buildFromZip(bundleZip);
      Boolean allowSharedUserId = getAllowSharedUserId().orElse(false);
      if (inputBundle.hasSharedUserId() && !allowSharedUserId) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Transparency can not be added because `sharedUserId` attribute is specified in one"
                    + " of the manifests and `allow-shared-user-id` flag is either false or not"
                    + " specified explicitly.")
            .build();
      }
      if (inputBundle.dexMergingEnabled()) {
        DexMergingChoice choice = evaluateDexMergingChoice();
        if (choice.equals(DexMergingChoice.REJECT)) {
          throw InvalidCommandException.builder()
              .withInternalMessage(
                  "'add-transparency' command is rejected because one of generated "
                      + "standalone/universal APKs will require dex merging and it is requested to"
                      + "reject command in this case.")
              .build();
        }
      }
      switch (getMode()) {
        case DEFAULT:
          executeDefaultMode(inputBundle);
          break;
        case GENERATE_CODE_TRANSPARENCY_FILE:
          executeGenerateCodeTransparencyFileMode(inputBundle);
          break;
        case INJECT_SIGNATURE:
          executeInjectSignatureMode(inputBundle);
          break;
      }
    } catch (ZipException e) {
      throw InvalidBundleException.builder()
          .withCause(e)
          .withUserMessage("The App Bundle is not a valid zip file.")
          .build();
    } catch (IOException e) {
      throw new UncheckedIOException("An error occurred when processing the App Bundle.", e);
    } catch (JoseException e) {
      throw new UncheckedJoseException(
          "An error occurred when signing the code transparency file.", e);
    }
  }

  private DexMergingChoice evaluateDexMergingChoice() {
    switch (getDexMergingChoice()) {
      case REJECT:
      case CONTINUE:
        return getDexMergingChoice();
      case ASK_IN_CONSOLE:
        String userDecision =
            System.console()
                .readLine(
                    "You will not be able to verify code transparency for standalone and universal"
                        + " APKs generated from this bundle. Reason: bundletool will merge dex"
                        + " files when generating standalone APKs. This happens for applications"
                        + " with dynamic feature modules that have min sdk below 21 and specify"
                        + " DexMergingStrategy.MERGE_IF_NEEDED.\nWould you like to continue?"
                        + " [yes/no]:");
        return Ascii.equalsIgnoreCase(userDecision, "yes")
            ? DexMergingChoice.CONTINUE
            : DexMergingChoice.REJECT;
    }
    throw new IllegalStateException("Unsupported DexMergingChoice");
  }

  private void executeDefaultMode(AppBundle inputBundle) throws IOException, JoseException {
    validateDefaultModeInputs();
    String jsonText =
        toJsonText(CodeTransparencyFactory.createCodeTransparencyMetadata(inputBundle));
    AppBundle.Builder bundleBuilder = inputBundle.toBuilder();
    bundleBuilder.setBundleMetadata(
        inputBundle.getBundleMetadata().toBuilder()
            .addFile(
                BundleMetadata.BUNDLETOOL_NAMESPACE,
                BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME,
                toBytes(createSignedJwt(jsonText, getSignerConfig().get().getCertificates())))
            .build());
    new AppBundleSerializer().writeToDisk(bundleBuilder.build(), getOutputPath());
  }

  private void executeGenerateCodeTransparencyFileMode(AppBundle inputBundle) throws IOException {
    validateGenerateCodeTransparencyFileModeInputs();
    String codeTransparencyMetadata =
        toJsonText(CodeTransparencyFactory.createCodeTransparencyMetadata(inputBundle));
    Files.write(
        getOutputPath(),
        toBytes(
                createJwtWithoutSignature(
                    codeTransparencyMetadata, getTransparencyKeyCertificates()))
            .read());
  }

  private void executeInjectSignatureMode(AppBundle inputBundle) throws IOException {
    validateInjectSignatureModeInputs();
    String signature =
        BaseEncoding.base64Url().encode(Files.readAllBytes(getTransparencySignaturePath().get()));
    String codeTransparencyMetadata =
        toJsonText(CodeTransparencyFactory.createCodeTransparencyMetadata(inputBundle));
    String transparencyFileWithoutSignature =
        createJwtWithoutSignature(codeTransparencyMetadata, getTransparencyKeyCertificates());
    AppBundle bundleWithTransparency =
        inputBundle.toBuilder()
            .setBundleMetadata(
                inputBundle.getBundleMetadata().toBuilder()
                    .addFile(
                        BundleMetadata.BUNDLETOOL_NAMESPACE,
                        BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME,
                        toBytes(transparencyFileWithoutSignature + "." + signature))
                    .build())
            .build();
    if (!BundleTransparencyCheckUtils.checkTransparency(bundleWithTransparency).verified()) {
      throw CommandExecutionException.builder()
          .withInternalMessage(
              "Code transparency verification failed for the provided public key certificate and"
                  + " signature.")
          .build();
    }
    new AppBundleSerializer().writeToDisk(bundleWithTransparency, getOutputPath());
  }

  public static CommandHelp help() {
    String modeFlagOptions =
        stream(Mode.values()).map(Mode::getLowerCaseName).collect(joining("|"));
    return CommandHelp.builder()
        .setCommandName(COMMAND_NAME)
        .setCommandDescription(
            CommandDescription.builder()
                .setShortDescription(
                    "Generates code transparency file and adds it to the output bundle.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(MODE_FLAG.getName())
                .setExampleValue(modeFlagOptions)
                .setOptional(true)
                .setDescription(
                    "Specifies which mode to run '%s' command against. Acceptable values are '%s'."
                        + " If set to '%s' we generate a signed code transparency file and include"
                        + " it into the output bundle. If set to '%s' we generate unsigned"
                        + " transparency file. If set to '%s' we inject the provided signed"
                        + " transparency file into the bundle. The default value is '%s'.",
                    AddTransparencyCommand.COMMAND_NAME,
                    modeFlagOptions,
                    Mode.DEFAULT.getLowerCaseName(),
                    Mode.GENERATE_CODE_TRANSPARENCY_FILE.getLowerCaseName(),
                    Mode.INJECT_SIGNATURE.getLowerCaseName(),
                    Mode.DEFAULT.getLowerCaseName())
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(BUNDLE_LOCATION_FLAG.getName())
                .setExampleValue("path/to/bundle.aab")
                .setDescription(
                    "Path to the Android App Bundle that we want to add transparency file to.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(OUTPUT_FLAG.getName())
                .setExampleValue("path/to/[bundle_with_transparency.aab|transparency_file.jwe]")
                .setDescription(
                    "Path to where the output file should be written. Must have extension .aab in"
                        + " '%s' and '%s' modes.",
                    Mode.DEFAULT.getLowerCaseName(), Mode.INJECT_SIGNATURE.getLowerCaseName())
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(KEYSTORE_FLAG.getName())
                .setExampleValue("path/to/keystore")
                .setOptional(true)
                .setDescription(
                    "Path to the keystore that should be used to sign the code transparency file.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(KEY_ALIAS_FLAG.getName())
                .setOptional(true)
                .setExampleValue("key-alias")
                .setDescription(
                    "Alias of the key to use in the keystore to sign the code transparency file.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(KEYSTORE_PASSWORD_FLAG.getName())
                .setExampleValue("[pass|file]:value")
                .setOptional(true)
                .setDescription(
                    "Password of the keystore to use to sign the code transparency file. Must "
                        + "be prefixed with either 'pass:' (if the password is passed in clear "
                        + "text, e.g. 'pass:qwerty') or 'file:' (if the password is the first line "
                        + "of a file, e.g. 'file:/tmp/myPassword.txt'). If this flag is not set, "
                        + "the password will be requested on the prompt.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(KEY_PASSWORD_FLAG.getName())
                .setExampleValue("[pass|file]:value")
                .setOptional(true)
                .setDescription(
                    "Password of the key in the keystore to use to sign the code transparency file."
                        + " Must be prefixed with either 'pass:' (if the password is passed"
                        + " in clear text, e.g. 'pass:qwerty') or 'file:' (if the password is the"
                        + " first line of a file, e.g. 'file:/tmp/myPassword.txt'). If this flag"
                        + " is not set, the keystore password will be tried. If that fails, the"
                        + " password will be requested on the prompt.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(TRANSPARENCY_KEY_CERTIFICATE_LOCATION_FLAG.getName())
                .setExampleValue("path/to/certificate.cert")
                .setOptional(true)
                .setDescription(
                    "Path to the file containing the code transparency public key certificate."
                        + " Required in '%s' and '%s' modes. Should not be used in other modes.",
                    Mode.GENERATE_CODE_TRANSPARENCY_FILE.getLowerCaseName(),
                    Mode.INJECT_SIGNATURE.getLowerCaseName())
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(TRANSPARENCY_SIGNATURE_LOCATION_FLAG.getName())
                .setExampleValue("path/to/transparency.signature")
                .setOptional(true)
                .setDescription(
                    "Path to the file containing the code transparency file signature. Required in"
                        + " '%s' mode. Should not be used in other modes.",
                    Mode.INJECT_SIGNATURE.getLowerCaseName())
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(DEX_MERGING_CHOICE_FLAG.getName())
                .setExampleValue(
                    String.format(
                        "%s|%s",
                        DexMergingChoice.CONTINUE.getLowerCaseName(),
                        DexMergingChoice.REJECT.getLowerCaseName()))
                .setOptional(true)
                .setDescription(
                    "Allows to silently respond how 'add-transparency' command should "
                        + "behave if some of generated standalone/universal APKs will require dex "
                        + "merging. '%s' means that 'add-transparency' should add code "
                        + "transparency anyway, but it won't be propagated to these APKs. '%s' "
                        + "means that 'add-transparency' command should fail. By default, if this "
                        + "choice is required user will be asked in terminal.",
                    DexMergingChoice.CONTINUE.getLowerCaseName(),
                    DexMergingChoice.REJECT.getLowerCaseName())
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(ALLOW_SHARED_USER_ID_FLAG.getName())
                .setExampleValue("true|false")
                .setOptional(true)
                .setDescription(
                    "Allows to use `add-transparency` command when AppBundle has `sharedUserId`. If"
                        + " the flag is not provided explicitly then its value considered as false")
                .build())
        .build();
  }

  private String createSignedJwt(String payload, List<X509Certificate> certificates)
      throws JoseException {
    JsonWebSignature jws = createJwsCommon(payload, certificates);
    jws.setKey(getSignerConfig().get().getPrivateKey());
    return jws.getCompactSerialization();
  }

  @VisibleForTesting
  static String createJwtWithoutSignature(String payload, List<X509Certificate> certificates) {
    JsonWebSignature jws = createJwsCommon(payload, certificates);
    return jws.getHeaders().getEncodedHeader() + "." + jws.getEncodedPayload();
  }

  private static JsonWebSignature createJwsCommon(
      String payload, List<X509Certificate> certificates) {
    JsonWebSignature jws = new JsonWebSignature();
    jws.setAlgorithmHeaderValue(RSA_USING_SHA256);
    jws.setCertificateChainHeaderValue(certificates.toArray(new X509Certificate[0]));
    jws.setPayload(payload);
    return jws;
  }

  private static String toJsonText(CodeTransparency codeTransparency)
      throws InvalidProtocolBufferException {
    return JsonFormat.printer().print(codeTransparency);
  }

  private static ByteSource toBytes(String content) {
    return CharSource.wrap(content).asByteSource(Charset.defaultCharset());
  }

  private void validateCommonInputs() {
    FilePreconditions.checkFileHasExtension("AAB file", getBundlePath(), ".aab");
    FilePreconditions.checkFileExistsAndReadable(getBundlePath());
  }

  private void validateDefaultModeInputs() {
    FilePreconditions.checkFileHasExtension("AAB file", getOutputPath(), ".aab");
    FilePreconditions.checkFileDoesNotExist(getOutputPath());
    Preconditions.checkArgument(
        getSignerConfig().get().getPrivateKey().getAlgorithm().equals(RsaKeyUtil.RSA),
        "Transparency signing key must be an RSA key, but %s key was provided.",
        getSignerConfig().get().getPrivateKey().getAlgorithm());
    int keyLength =
        ((RSAPrivateKey) getSignerConfig().get().getPrivateKey()).getModulus().bitLength();
    Preconditions.checkArgument(
        keyLength >= MIN_RSA_KEY_LENGTH,
        "Minimum required key length is %s bits, but %s bit key was provided.",
        MIN_RSA_KEY_LENGTH,
        keyLength);
  }

  private void validateGenerateCodeTransparencyFileModeInputs() {
    FilePreconditions.checkFileDoesNotExist(getOutputPath());
    validateTransparencyKeyCertificate();
  }

  private void validateInjectSignatureModeInputs() {
    FilePreconditions.checkFileHasExtension("AAB file", getOutputPath(), ".aab");
    FilePreconditions.checkFileDoesNotExist(getOutputPath());
    FilePreconditions.checkFileExistsAndReadable(getTransparencySignaturePath().get());
    validateTransparencyKeyCertificate();
  }

  private void validateTransparencyKeyCertificate() {
    Preconditions.checkArgument(
        !getTransparencyKeyCertificates().isEmpty(),
        "Transparency signing key certificates must be provided.");
    X509Certificate leafCertificate = getTransparencyKeyCertificates().get(0);
    Preconditions.checkArgument(
        leafCertificate.getPublicKey().getAlgorithm().equals(RsaKeyUtil.RSA),
        "Transparency signing key must be an RSA key, but %s key was provided.",
        leafCertificate.getPublicKey().getAlgorithm());
    int keyLength = ((RSAPublicKey) leafCertificate.getPublicKey()).getModulus().bitLength();
    Preconditions.checkArgument(
        keyLength >= MIN_RSA_KEY_LENGTH,
        "Minimum required key length is %s bits, but %s bit key was provided.",
        MIN_RSA_KEY_LENGTH,
        keyLength);
  }
}
