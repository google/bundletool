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

package com.android.tools.build.bundletool.validation;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BundleFilesValidatorTest {

  @Test
  public void validateAssetsFile_valid_success() throws Exception {
    ZipPath assetsFile = ZipPath.create("assets/anything.dat");

    new BundleFilesValidator().validateModuleFile(assetsFile);
  }

  @Test
  public void validateDexFile_valid_success() throws Exception {
    ZipPath dexFile = ZipPath.create("dex/classes.dex");

    new BundleFilesValidator().validateModuleFile(dexFile);
  }

  @Test
  public void validateDexFile_nonDexExtension_throws() throws Exception {
    ZipPath nonDexFile = ZipPath.create("dex/classes.dat");

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new BundleFilesValidator().validateModuleFile(nonDexFile));

    assertThat(e)
        .hasMessageThat()
        .contains("Files under dex/ must have .dex extension, found 'dex/classes.dat'.");
  }

  @Test
  public void validateDexFile_badFileName_throws() throws Exception {
    ZipPath nonDexFile = ZipPath.create("dex/bad-name.dex");

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new BundleFilesValidator().validateModuleFile(nonDexFile));

    assertThat(e)
        .hasMessageThat()
        .contains("Files under dex/ must match the 'classes[0-9]*.dex' pattern");
  }

  @Test
  public void validateDexFile_badDirectoryStructure_throws() throws Exception {
    ZipPath nonDexFile = ZipPath.create("dex/extra-dir/classes.dex");

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new BundleFilesValidator().validateModuleFile(nonDexFile));

    assertThat(e).hasMessageThat().contains("The dex/ directory cannot contain directories");
  }

  @Test
  public void validateLibFile_valid_success() throws Exception {
    ZipPath libFile = ZipPath.create("lib/x86/foo.so");

    new BundleFilesValidator().validateModuleFile(libFile);
  }

  @Test
  public void validateWrapSh_valid_success() throws Exception {
    ZipPath libFile = ZipPath.create("lib/x86/wrap.sh");

    new BundleFilesValidator().validateModuleFile(libFile);
  }

  @Test
  public void validateLibFile_directoryStructureTooShallow_throws() throws Exception {
    ZipPath libFileDirectlyInLib = ZipPath.create("lib/libX.so");

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new BundleFilesValidator().validateModuleFile(libFileDirectlyInLib));

    assertThat(e).hasMessageThat().contains("paths in form 'lib/<single-directory>/<file>.so'");
  }

  @Test
  public void validateLibFile_directoryStructureTooDeep_throws() throws Exception {
    ZipPath libFileTooDeep = ZipPath.create("lib/x86/other-dir/libX.so");

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new BundleFilesValidator().validateModuleFile(libFileTooDeep));

    assertThat(e).hasMessageThat().contains("paths in form 'lib/<single-directory>/<file>.so'");
  }

  @Test
  public void validateLibFile_nonSoExtension_throws() throws Exception {
    ZipPath nonSoFile = ZipPath.create("lib/x86/libX.dat");

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new BundleFilesValidator().validateModuleFile(nonSoFile));

    assertThat(e).hasMessageThat().contains("Files under lib/ must have .so extension");
  }

  @Test
  public void validateLibFile_unknownAbi_throws() throws Exception {
    ZipPath unknownAbiPath = ZipPath.create("lib/sparc/libX.so");

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new BundleFilesValidator().validateModuleFile(unknownAbiPath));

    assertThat(e).hasMessageThat().contains("Unrecognized native architecture for directory");
  }

  @Test
  public void assetsInRootDirectory_valid() throws Exception {
    ZipPath assetsFileInRoot =
        ZipPath.create(
            "root/assets/org/apache/commons/math3/exception/util/LocalizedFormats_fr.properties");

    new BundleFilesValidator().validateModuleFile(assetsFileInRoot);
  }

  @Test
  public void validateManifestDirectory_validXmlExtension_success() throws Exception {
    ZipPath validFile = ZipPath.create("manifest/AndroidManifest.xml");

    new BundleFilesValidator().validateModuleFile(validFile);
  }

  @Test
  public void validateManifestDirectory_otherFile_throws() throws Exception {
    ZipPath nonPbFile = ZipPath.create("manifest/AndroidManifest.dat");

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new BundleFilesValidator().validateModuleFile(nonPbFile));

    assertThat(e)
        .hasMessageThat()
        .contains(
            "Only 'AndroidManifest.xml' is accepted under directory 'manifest/' but found file "
                + "'manifest/AndroidManifest.dat'");
  }

  @Test
  public void validateResourceFile_valid_success() throws Exception {
    ZipPath validFile = ZipPath.create("res/values-en-rUS/strings.xml");

    new BundleFilesValidator().validateModuleFile(validFile);
  }

  @Test
  public void validateResourceFile_directlyInRes_success() throws Exception {
    ZipPath directFile = ZipPath.create("res/no-subdirectory.png");

    new BundleFilesValidator().validateModuleFile(directFile);
  }

  @Test
  public void validateRootFile_valid_success() throws Exception {
    ZipPath rootFile = ZipPath.create("root/anything.dat");

    new BundleFilesValidator().validateModuleFile(rootFile);
  }

  @Test
  public void validateRootFile_clashesWithReservedApkFile_throws() throws Exception {
    String[] restrictedFiles =
        new String[] {
          "AndroidManifest.xml",
          "resources.arsc",
          "resources.pb",
          "classes.dex",
          "classes2.dex",
          "classes999.dex"
        };

    for (String restrictedFile : restrictedFiles) {
      ZipPath rootFile = ZipPath.create("root/" + restrictedFile);

      InvalidBundleException e =
          assertThrows(
              InvalidBundleException.class,
              () -> new BundleFilesValidator().validateModuleFile(rootFile));

      assertWithMessage("restrictedFile='%s'", restrictedFile)
          .that(e)
          .hasMessageThat()
          .contains("uses reserved file or directory name");
    }
  }

  @Test
  public void validateRootFile_clashesWithReservedApkDirectory_throws() throws Exception {
    for (String restrictedDir :
        new String[] {
          /*"assets",*/
          "lib", "res"
        }) {
      ZipPath rootFile = ZipPath.create("root/" + restrictedDir + "/file");

      InvalidBundleException e =
          assertThrows(
              InvalidBundleException.class,
              () -> new BundleFilesValidator().validateModuleFile(rootFile));

      assertWithMessage("restrictedDir='%s'", restrictedDir)
          .that(e)
          .hasMessageThat()
          .contains("uses reserved file or directory name");
    }
  }

  @Test
  public void validateApexFile_valid_success() throws Exception {
    ZipPath apexFile = ZipPath.create("apex/x86.img");

    new BundleFilesValidator().validateModuleFile(apexFile);
  }

  @Test
  public void validateApexFile_validMultipleAbi_success() throws Exception {
    ZipPath apexFile = ZipPath.create("apex/x86.arm64-v8a.img");

    new BundleFilesValidator().validateModuleFile(apexFile);
  }

  @Test
  public void validateApexFile_directoryStructureTooDeep_throws() throws Exception {
    ZipPath apexFileTooDeep = ZipPath.create("apex/some-dir/x86.img");

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new BundleFilesValidator().validateModuleFile(apexFileTooDeep));

    assertThat(e).hasMessageThat().contains("paths in form 'apex/<file>.img'");
  }

  @Test
  public void validateApexFile_nonImgExtension_throws() throws Exception {
    ZipPath nonImgFile = ZipPath.create("apex/x86.dat");

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new BundleFilesValidator().validateModuleFile(nonImgFile));

    assertThat(e).hasMessageThat().contains("Files under apex/ must have .img extension");
  }

  @Test
  public void validateApexFile_unknownAbi_throws() throws Exception {
    ZipPath nonImgFile = ZipPath.create("apex/sparc.img");

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new BundleFilesValidator().validateModuleFile(nonImgFile));

    assertThat(e).hasMessageThat().contains("Unrecognized native architecture for file");
  }

  @Test
  public void validateApexFile_unknownMultipleAbi_throws() throws Exception {
    ZipPath nonImgFile = ZipPath.create("apex/x86.sparc.img");

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new BundleFilesValidator().validateModuleFile(nonImgFile));

    assertThat(e).hasMessageThat().contains("Unrecognized native architecture for file");
  }

  @Test
  public void validateApexFile_repeatingAbis_throws() throws Exception {
    ZipPath repeatingAbisFile = ZipPath.create("apex/x86.x86_64.x86.img");

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new BundleFilesValidator().validateModuleFile(repeatingAbisFile));

    assertThat(e).hasMessageThat().contains("Repeating architectures in APEX system image file");
  }

  @Test
  public void validateOtherFile_inModuleRoot_throws() throws Exception {
    ZipPath otherFile = ZipPath.create("in-root.txt");

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new BundleFilesValidator().validateModuleFile(otherFile));

    assertThat(e).hasMessageThat().contains("Module files can be only in pre-defined directories");
  }

  @Test
  public void validateOtherFile_inUnknownDirectory_throws() throws Exception {
    ZipPath otherFile = ZipPath.create("unrecognized/path.txt");

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new BundleFilesValidator().validateModuleFile(otherFile));

    assertThat(e).hasMessageThat().contains("Module files can be only in pre-defined directories");
  }
}
