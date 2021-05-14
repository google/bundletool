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
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.utils.files.FilePreconditions;
import com.android.tools.build.bundletool.transparency.CodeTransparencyFactory;
import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
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

  static final int MIN_RSA_KEY_LENGTH = 3072;

  private static final Flag<Path> BUNDLE_LOCATION_FLAG = Flag.path("bundle");

  private static final Flag<Path> OUTPUT_FLAG = Flag.path("output");

  private static final Flag<Path> KEYSTORE_FLAG = Flag.path("ks");

  private static final Flag<String> KEY_ALIAS_FLAG = Flag.string("ks-key-alias");

  private static final Flag<Password> KEYSTORE_PASSWORD_FLAG = Flag.password("ks-pass");

  private static final Flag<Password> KEY_PASSWORD_FLAG = Flag.password("key-pass");

  public abstract Path getBundlePath();

  public abstract Path getOutputPath();

  public abstract SignerConfig getSignerConfig();

  public static AddTransparencyCommand.Builder builder() {
    return new AutoValue_AddTransparencyCommand.Builder();
  }

  /** Builder for the {@link AddTransparencyCommand}. */
  @AutoValue.Builder
  public abstract static class Builder {

    /** Sets the path to the input bundle. Must have the extension ".aab". */
    public abstract AddTransparencyCommand.Builder setBundlePath(Path bundlePath);

    /**
     * Sets the path to the output bundle. Must have the extension ".aab". If the output file
     * already exists, it can not be overwritten.
     */
    public abstract AddTransparencyCommand.Builder setOutputPath(Path bundlePath);

    /** Sets code transparency signer configuration. */
    public abstract AddTransparencyCommand.Builder setSignerConfig(SignerConfig signerConfig);

    public abstract AddTransparencyCommand build();
  }

  public static AddTransparencyCommand fromFlags(ParsedFlags flags) {
    Path keystorePath = KEYSTORE_FLAG.getRequiredValue(flags);
    String keyAlias = KEY_ALIAS_FLAG.getRequiredValue(flags);
    Optional<Password> keystorePassword = KEYSTORE_PASSWORD_FLAG.getValue(flags);
    Optional<Password> keyPassword = KEY_PASSWORD_FLAG.getValue(flags);
    SignerConfig signerConfig =
        SignerConfig.extractFromKeystore(keystorePath, keyAlias, keystorePassword, keyPassword);
    AddTransparencyCommand.Builder addTransparencyCommandBuilder =
        AddTransparencyCommand.builder()
            .setBundlePath(BUNDLE_LOCATION_FLAG.getRequiredValue(flags))
            .setOutputPath(OUTPUT_FLAG.getRequiredValue(flags))
            .setSignerConfig(signerConfig);
    flags.checkNoUnknownFlags();
    return addTransparencyCommandBuilder.build();
  }

  public void execute() {
    validateInputs();

    try (ZipFile bundleZip = new ZipFile(getBundlePath().toFile())) {
      AppBundle inputBundle = AppBundle.buildFromZip(bundleZip);
      if (inputBundle.hasSharedUserId()) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Transparency can not be added because `sharedUserId` attribute is specified in"
                    + " one of the manifests.")
            .build();
      }
      String jsonText =
          toJsonText(CodeTransparencyFactory.createCodeTransparencyMetadata(inputBundle));
      AppBundle.Builder bundleBuilder = inputBundle.toBuilder();
      bundleBuilder.setBundleMetadata(
          inputBundle.getBundleMetadata().toBuilder()
              .addFile(
                  BundleMetadata.BUNDLETOOL_NAMESPACE,
                  BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME,
                  toBytes(createJwsToken(jsonText)))
              .build());
      new AppBundleSerializer().writeToDisk(bundleBuilder.build(), getOutputPath());
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

  public static CommandHelp help() {
    return CommandHelp.builder()
        .setCommandName(COMMAND_NAME)
        .setCommandDescription(
            CommandDescription.builder()
                .setShortDescription(
                    "Generates code transparency file and adds it to the output bundle.")
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
                .setExampleValue("path/to/bundle_with_transparency.aab")
                .setDescription("Path to where the output bundle should be written.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(KEYSTORE_FLAG.getName())
                .setExampleValue("path/to/keystore")
                .setDescription(
                    "Path to the keystore that should be used to sign the code transparency file.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(KEY_ALIAS_FLAG.getName())
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
        .build();
  }

  private String createJwsToken(String payload) throws JoseException {
    JsonWebSignature jws = new JsonWebSignature();
    jws.setAlgorithmHeaderValue(RSA_USING_SHA256);
    jws.setCertificateChainHeaderValue(
        getSignerConfig().getCertificates().toArray(new X509Certificate[0]));
    jws.setPayload(payload);
    jws.setKey(getSignerConfig().getPrivateKey());
    return jws.getCompactSerialization();
  }

  private static String toJsonText(CodeTransparency codeTransparency)
      throws InvalidProtocolBufferException {
    return JsonFormat.printer().print(codeTransparency);
  }

  private static ByteSource toBytes(String content) {
    return CharSource.wrap(content).asByteSource(Charset.defaultCharset());
  }

  private void validateInputs() {
    FilePreconditions.checkFileHasExtension("AAB file", getBundlePath(), ".aab");
    FilePreconditions.checkFileExistsAndReadable(getBundlePath());
    FilePreconditions.checkFileHasExtension("AAB file", getOutputPath(), ".aab");
    FilePreconditions.checkFileDoesNotExist(getOutputPath());
    Preconditions.checkArgument(
        getSignerConfig().getPrivateKey().getAlgorithm().equals(RsaKeyUtil.RSA),
        "Transparency signing key must be an RSA key, but %s key was provided.",
        getSignerConfig().getPrivateKey().getAlgorithm());
    int keyLength = ((RSAPrivateKey) getSignerConfig().getPrivateKey()).getModulus().bitLength();
    Preconditions.checkArgument(
        keyLength >= MIN_RSA_KEY_LENGTH,
        "Minimum required key length is %s bits, but %s bit key was provided.",
        MIN_RSA_KEY_LENGTH,
        keyLength);
  }
}

