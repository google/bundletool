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
package com.android.tools.build.bundletool.transparency;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.bundle.CodeTransparencyOuterClass.CodeRelatedFile;
import com.android.bundle.CodeTransparencyOuterClass.CodeTransparency;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.cert.X509Certificate;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwa.AlgorithmConstraints.ConstraintType;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.keys.X509Util;
import org.jose4j.lang.JoseException;

/** Shared utilities for verifying code transparency. */
public final class CodeTransparencyChecker {

  /**
   * Verifies code transparency for the given bundle, and returns {@link TransparencyCheckResult}.
   *
   * @throws InvalidBundleException if an error occurs during verification.
   */
  public static TransparencyCheckResult checkTransparency(
      AppBundle bundle, ByteSource signedTransparencyFile) {
    if (bundle.hasSharedUserId()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Transparency file is present in the bundle, but it can not be verified because"
                  + " `sharedUserId` attribute is specified in one of the manifests.")
          .build();
    }

    JsonWebSignature jws = createJws(signedTransparencyFile);

    if (verifySignature(jws)) {
      return TransparencyCheckResult.createForValidSignature(
        /* certificateThumbprint= */ X509Util.x5tS256(getLeafCertificate(jws)),
        getCodeRelatedFilesFromTransparencyMetadata(jws),
        getCodeRelatedFilesFromBundle(bundle));
    }
    return TransparencyCheckResult.createForInvalidSignature();
  }

  private static JsonWebSignature createJws(ByteSource signedTransparencyFile) {
    JsonWebSignature jws;
    try {
      jws =
          (JsonWebSignature)
              JsonWebSignature.fromCompactSerialization(
                  signedTransparencyFile.asCharSource(Charset.defaultCharset()).read());
      jws.setKey(jws.getLeafCertificateHeaderValue().getPublicKey());
      jws.setAlgorithmConstraints(
          new AlgorithmConstraints(
              ConstraintType.WHITELIST, AlgorithmIdentifiers.RSA_USING_SHA256));
    } catch (JoseException | IOException e) {
      throw InvalidBundleException.builder()
          .withUserMessage("Unable to deserialize JWS from code transparency file.")
          .withCause(e)
          .build();
    }
    return jws;
  }

  private static boolean verifySignature(JsonWebSignature jws) {
    boolean signatureValid;
    try {
      signatureValid = jws.verifySignature();
    } catch (JoseException e) {
      throw InvalidBundleException.builder()
          .withUserMessage("Exception while verifying code transparency signature.")
          .withCause(e)
          .build();
    }
    return signatureValid;
  }

  private static X509Certificate getLeafCertificate(JsonWebSignature jwt) {
    X509Certificate leafCertificate;
    try {
      leafCertificate = jwt.getLeafCertificateHeaderValue();
    } catch (JoseException e) {
      throw InvalidBundleException.builder()
          .withUserMessage("Unable to retrieve certificate header value from JWS.")
          .withCause(e)
          .build();
    }
    return leafCertificate;
  }

  private static ImmutableMap<String, CodeRelatedFile> getCodeRelatedFilesFromTransparencyMetadata(
      JsonWebSignature signedTransparencyFile) {
    CodeTransparency.Builder transparencyMetadata = CodeTransparency.newBuilder();
    try {
      JsonFormat.parser()
          .merge(signedTransparencyFile.getUnverifiedPayload(), transparencyMetadata);
    } catch (IOException e) {
      throw InvalidBundleException.builder()
          .withUserMessage("Unable to parse code transparency file contents.")
          .withCause(e)
          .build();
    }
    return transparencyMetadata.getCodeRelatedFileList().stream()
        .collect(toImmutableMap(CodeRelatedFile::getPath, codeRelatedFile -> codeRelatedFile));
  }

  private static ImmutableMap<String, CodeRelatedFile> getCodeRelatedFilesFromBundle(
      AppBundle bundle) {
    return CodeTransparencyFactory.createCodeTransparencyMetadata(bundle)
        .getCodeRelatedFileList()
        .stream()
        .collect(toImmutableMap(CodeRelatedFile::getPath, codeRelatedFile -> codeRelatedFile));
  }

  private CodeTransparencyChecker() {}
}
