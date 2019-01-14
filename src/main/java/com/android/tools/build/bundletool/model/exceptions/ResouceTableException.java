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

package com.android.tools.build.bundletool.model.exceptions;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toList;

import com.android.bundle.Errors.BundleToolError;
import com.android.bundle.Errors.ResourceTableMissingError;
import com.android.bundle.Errors.ResourceTableReferencesFilesOutsideResError;
import com.android.bundle.Errors.ResourceTableReferencesMissingFilesError;
import com.android.bundle.Errors.ResourceTableUnreferencedFilesError;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

/** Thrown when bundletool validation detects an issue with the resource table of a module. */
public class ResouceTableException extends ValidationException {

  @FormatMethod
  protected ResouceTableException(@FormatString String message, Object... args) {
    super(String.format(checkNotNull(message), args));
  }

  /**
   * Resource table missing but there are references in {@code /res} (e.g. references {@code
   * /raw/img.png})
   */
  public static class ResourceTableMissingException extends ResouceTableException {

    private final String moduleName;

    public ResourceTableMissingException(String moduleName) {
      super("Module '%s' is missing resource table but contains resource files.", moduleName);
      this.moduleName = moduleName;
    }

    @Override
    protected void customizeProto(BundleToolError.Builder builder) {
      builder.setResourceTableMissing(
          ResourceTableMissingError.newBuilder().setModuleName(moduleName));
    }
  }
  /**
   * Resource table references a file outside of {@code /res} (e.g. references {@code /raw/img.png})
   */
  public static class ReferencesFileOutsideOfResException extends ResouceTableException {

    private final String moduleName;
    private final ZipPath referencedFile;

    public ReferencesFileOutsideOfResException(
        String moduleName, ZipPath referencedFile, ZipPath resourcesDir) {
      super(
          "Resource table of module '%s' references file '%s' outside of the '%s/' directory.",
          moduleName, referencedFile, resourcesDir);
      this.moduleName = moduleName;
      this.referencedFile = referencedFile;
    }

    @Override
    protected void customizeProto(BundleToolError.Builder builder) {
      builder.setResourceTableReferencesFilesOutsideRes(
          ResourceTableReferencesFilesOutsideResError.newBuilder()
              .setModuleName(moduleName)
              .setFilePath(referencedFile.toString()));
    }
  }

  /** Files in {@code /res} that are not referenced by the resource table. */
  public static class UnreferencedResourcesException extends ResouceTableException {

    private final String moduleName;
    private final ImmutableSet<ZipPath> nonReferencedFiles;

    public UnreferencedResourcesException(
        String moduleName, ImmutableSet<ZipPath> nonReferencedFiles) {
      super(
          "Module '%s' contains resource files that are not referenced from the resource "
              + "table: %s",
          moduleName, nonReferencedFiles);
      this.moduleName = moduleName;
      this.nonReferencedFiles = nonReferencedFiles;
    }

    @Override
    protected void customizeProto(BundleToolError.Builder builder) {
      builder.setResouceTableUnreferencedFiles(
          ResourceTableUnreferencedFilesError.newBuilder()
              .setModuleName(moduleName)
              .addAllFilePath(nonReferencedFiles.stream().map(Object::toString).collect(toList())));
    }
  }

  /** Resource table references a file that cannot be found in the bundle. */
  public static class ReferencesMissingFilesException extends ResouceTableException {

    private final String moduleName;
    private final ImmutableSet<ZipPath> missingFiles;

    public ReferencesMissingFilesException(
        String moduleName, ImmutableSet<ZipPath> nonExistingFiles) {
      super(
          "Resource table of module '%s' contains references to non-existing files: %s",
          moduleName, nonExistingFiles);
      this.moduleName = moduleName;
      this.missingFiles = nonExistingFiles;
    }

    @Override
    protected void customizeProto(BundleToolError.Builder builder) {
      builder.setResouceTableReferencesMissingFiles(
          ResourceTableReferencesMissingFilesError.newBuilder()
              .setModuleName(moduleName)
              .addAllFilePath(missingFiles.stream().map(Object::toString).collect(toList())));
    }
  }
}
