/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License
 */

package com.android.tools.build.bundletool.model.exceptions;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.bundle.Errors.BundleToolError;
import com.android.bundle.Errors.FileTypeDirectoryInBundleError;
import com.android.bundle.Errors.FileTypeFileUsesReservedNameError;
import com.android.bundle.Errors.FileTypeFilesInResourceDirectoryRootError;
import com.android.bundle.Errors.FileTypeInvalidApexImagePathError;
import com.android.bundle.Errors.FileTypeInvalidFileExtensionError;
import com.android.bundle.Errors.FileTypeInvalidFileNameInDirectoryError;
import com.android.bundle.Errors.FileTypeInvalidNativeArchitectureError;
import com.android.bundle.Errors.FileTypeInvalidNativeLibraryPathError;
import com.android.bundle.Errors.FileTypeUnknownFileOrDirectoryFoundInModuleError;
import com.android.bundle.Errors.MandatoryBundleFileMissingError;
import com.android.bundle.Errors.MandatoryModuleFileMissingError;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import java.util.zip.ZipEntry;

/** Thrown if there are errors with files' locations / names. */
public class BundleFileTypesException extends ValidationException {

  @FormatMethod
  protected BundleFileTypesException(@FormatString String message, Object... args) {
    super(String.format(checkNotNull(message), args));
  }

  /** self describing */
  public static class InvalidFileExtensionInDirectoryException extends BundleFileTypesException {

    private final ZipPath invalidFile;
    private final ZipPath directory;
    private final String extensionRequired;

    public InvalidFileExtensionInDirectoryException(
        ZipPath directory, String extensionRequired, ZipPath file) {
      super(
          "Files under %s/ must have %s extension, found '%s'.",
          directory, extensionRequired, file);
      this.directory = directory;
      this.extensionRequired = extensionRequired;
      this.invalidFile = file;
    }

    @Override
    protected void customizeProto(BundleToolError.Builder builder) {
      builder.setFileTypeInvalidFileExtension(
          FileTypeInvalidFileExtensionError.newBuilder()
              .setRequiredExtension(extensionRequired)
              .setBundleDirectory(directory.toString())
              .setInvalidFile(invalidFile.toString()));
    }
  }

  /** self describing */
  public static class InvalidFileNameInDirectoryException extends BundleFileTypesException {

    private final ZipPath directory;
    private final String allowedFileName;
    private final ZipPath file;

    public InvalidFileNameInDirectoryException(
        String allowedFileName, ZipPath directory, ZipPath file) {
      super(
          "Only '%s' is accepted under directory '%s/' but found file '%s'.",
          allowedFileName, directory, file);
      this.allowedFileName = allowedFileName;
      this.directory = directory;
      this.file = file;
    }

    @Override
    protected void customizeProto(BundleToolError.Builder builder) {
      builder.setFileTypeInvalidFileName(
          FileTypeInvalidFileNameInDirectoryError.newBuilder()
              .setInvalidFile(file.toString())
              .addAllowedFileName(allowedFileName)
              .setBundleDirectory(directory.toString()));
    }
  }

  /** self describing */
  public static class InvalidNativeLibraryPathException extends BundleFileTypesException {

    private final ZipPath libDirectory;
    private final ZipPath file;

    public InvalidNativeLibraryPathException(ZipPath libDirectory, ZipPath file) {
      super(
          "Native library files need to have paths in form '%s/<single-directory>/<file>.so'"
              + " but found '%s'.",
          libDirectory, file);
      this.libDirectory = libDirectory;
      this.file = file;
    }

    @Override
    protected void customizeProto(BundleToolError.Builder builder) {
      builder.setFileTypeInvalidNativeLibraryPath(
          FileTypeInvalidNativeLibraryPathError.newBuilder()
              .setInvalidFile(file.toString())
              .setBundleDirectory(libDirectory.toString()));
    }
  }

  /**
   * Exception indicating that a file name in the APEX directory, within the input zip file, has the
   * wrong form. All files in this directory should have depth one and the suffix 'img'.
   */
  public static class InvalidApexImagePathException extends BundleFileTypesException {

    private final ZipPath apexDirectory;
    private final ZipPath file;

    /**
     * @param apexDirectory the parent directory, as a {@code ZipPath}
     * @param file the file that caused the exception, as a {@code ZipPath}
     */
    public InvalidApexImagePathException(ZipPath apexDirectory, ZipPath file) {
      super(
          "APEX image files need to have paths in form '%s/<file>.img' but found '%s'.",
          apexDirectory, file);
      this.apexDirectory = apexDirectory;
      this.file = file;
    }

    @Override
    protected void customizeProto(BundleToolError.Builder builder) {
      builder.setFileTypeInvalidApexImagePath(
          FileTypeInvalidApexImagePathError.newBuilder()
              .setInvalidFile(file.toString())
              .setBundleDirectory(apexDirectory.toString()));
    }
  }

  /**
   * Exception indicating that a file or directory name is not made of native architectures as
   * expected.
   */
  public static class InvalidNativeArchitectureNameException extends BundleFileTypesException {

    private final ZipPath fileOrDirectory;

    /**
     * Creates an InvalidNativeArchitectureNameException instance for a file.
     *
     * @param file the file that caused the exception, as a {@code ZipPath}
     */
    public static InvalidNativeArchitectureNameException createForFile(ZipPath file) {
      return new InvalidNativeArchitectureNameException(file, "file");
    }

    /**
     * Creates an InvalidNativeArchitectureNameException instance for a directory.
     *
     * @param directory the directory that caused the exception, as a {@code ZipPath}
     */
    public static InvalidNativeArchitectureNameException createForDirectory(ZipPath directory) {
      return new InvalidNativeArchitectureNameException(directory, "directory");
    }

    private InvalidNativeArchitectureNameException(ZipPath fileOrDirectory, String type) {
      super("Unrecognized native architecture for %s '%s'.", type, fileOrDirectory);
      this.fileOrDirectory = fileOrDirectory;
    }

    @Override
    protected void customizeProto(BundleToolError.Builder builder) {
      builder.setFileTypeInvalidNativeArchitecture(
          FileTypeInvalidNativeArchitectureError.newBuilder()
              .setInvalidArchitectureDirectory(fileOrDirectory.toString()));
    }
  }

  /** self describing */
  public static class FilesInResourceDirectoryRootException extends BundleFileTypesException {

    private final ZipPath resourcesDirectory;
    private final ZipPath file;

    public FilesInResourceDirectoryRootException(ZipPath resourcesDirectory, ZipPath file) {
      super(
          "The %s/ directory cannot contain files directly, found '%s'.", resourcesDirectory, file);
      this.resourcesDirectory = resourcesDirectory;
      this.file = file;
    }

    @Override
    protected void customizeProto(BundleToolError.Builder builder) {
      builder.setFileTypeFileInResourceDirectoryRoot(
          FileTypeFilesInResourceDirectoryRootError.newBuilder()
              .setInvalidFile(file.toString())
              .setResourceDirectory(resourcesDirectory.toString()));
    }
  }

  /** self describing */
  public static class UnknownFileOrDirectoryFoundInModuleException
      extends BundleFileTypesException {

    private final ZipPath file;

    public UnknownFileOrDirectoryFoundInModuleException(ZipPath file) {
      super("Module files can be only in pre-defined directories, but found '%s'.", file);
      this.file = file;
    }

    @Override
    protected void customizeProto(BundleToolError.Builder builder) {
      builder.setFileTypeUnknownFileOrDirectoryInModule(
          FileTypeUnknownFileOrDirectoryFoundInModuleError.newBuilder()
              .setInvalidFile(file.toString()));
    }
  }

  /** self describing */
  public static class FileUsesReservedNameException extends BundleFileTypesException {

    private final ZipPath file;

    public FileUsesReservedNameException(ZipPath file, ZipPath nameUnderRoot) {
      super("File '%s' uses reserved file or directory name '%s'.", file, nameUnderRoot);
      this.file = file;
    }

    @Override
    protected void customizeProto(BundleToolError.Builder builder) {
      builder.setFileTypeFileUsesReservedName(
          FileTypeFileUsesReservedNameError.newBuilder().setInvalidFile(file.toString()));
    }
  }

  /** self describing */
  public static class MandatoryBundleFileMissingException extends BundleFileTypesException {

    private final ZipPath file;

    public MandatoryBundleFileMissingException(ZipPath file) {
      super(
          "The archive doesn't seem to be an App Bundle, it is missing required file '%s'.", file);
      this.file = file;
    }

    @Override
    protected void customizeProto(BundleToolError.Builder builder) {
      builder.setMandatoryBundleFileMissing(
          MandatoryBundleFileMissingError.newBuilder().setMissingFile(file.toString()));
    }
  }

  /** self describing */
  public static class MandatoryModuleFileMissingException extends BundleFileTypesException {

    private final String moduleName;
    private final ZipPath file;

    public MandatoryModuleFileMissingException(String moduleName, ZipPath file) {
      super("Module '%s' is missing mandatory file '%s'.", moduleName, file);
      this.moduleName = moduleName;
      this.file = file;
    }

    @Override
    protected void customizeProto(BundleToolError.Builder builder) {
      builder.setMandatoryModuleFileMissing(
          MandatoryModuleFileMissingError.newBuilder()
              .setModuleName(moduleName)
              .setMissingFile(file.toString()));
    }
  }

  /** self describing */
  public static class DirectoryInBundleException extends BundleFileTypesException {

    private final ZipEntry directory;

    public DirectoryInBundleException(ZipEntry directory) {
      super(
          "The App Bundle zip file contains directory zip entry '%s' which is not allowed.",
          directory.getName());
      this.directory = directory;
    }

    @Override
    protected void customizeProto(BundleToolError.Builder builder) {
      builder.setFileTypeDirectoryInBundle(
          FileTypeDirectoryInBundleError.newBuilder().setInvalidDirectory(directory.getName()));
    }
  }
}
