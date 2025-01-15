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

package com.google.android.archive;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.util.Log;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

/** Activity that triggers the reactivation of an app through the app store. */
public class ReactivateActivity extends Activity implements DialogInterface.OnClickListener {

  private static final String TAG = "ReactivateActivity";

  private static final String EXTRA_DATA = "archive.extra.data";

  private final ScheduledExecutorService scheduledExecutorService =
      Executors.newScheduledThreadPool(1);

  private String appStorePackageName;
  private boolean processingError;
  private int retryCount = 0;

  private ScheduledFuture<?> unhibernateFuture;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    handleSplashScreen(this);
    super.onCreate(savedInstanceState);
    appStorePackageName = getAppStorePackageName();
  }

  @Override
  public void onResume() {
    super.onResume();
    // Ensures we don't try to send another intent immediately after the first one failed, instead
    // an error dialog is shown.
    if (processingError) {
      return;
    }
    startUnhibernateActivityForResult();
  }

  /** Returns true if the targeted Store is installed and enabled. */
  private boolean isStoreInstalled() {
    try {
      return getPackageManager().getApplicationInfo(appStorePackageName, 0).enabled;
    } catch (NameNotFoundException e) {
      return false;
    }
  }

  private AlertDialog buildErrorDialog() {
    AlertDialog.Builder dialog =
        new AlertDialog.Builder(this)
            .setTitle("Installation failed")
            .setCancelable(false)
            .setNeutralButton("Close", this)
            .setMessage(
                String.format(
                    "To open %s you must first complete its download from an official app store.",
                    getAppName()));

    if (isStoreInstalled()) {
      dialog.setPositiveButton("Retry", this);
    }

    return dialog.create();
  }

  private String getAppName() {
    return getApplicationInfo().loadLabel(getPackageManager()).toString();
  }

  @Override
  public void onClick(DialogInterface ignored, int buttonType) {
    processingError = false;
    switch (buttonType) {
      case DialogInterface.BUTTON_POSITIVE:
        startUnhibernateActivityForResult();
        break;
      case DialogInterface.BUTTON_NEUTRAL:
      default:
        // Nothing specific, just close the app.
        finish();
        break;
    }
  }

  /**
   * The archived stub is built with the activity flag noHistory, so this can only be triggered if
   * the app store is not installed.
   */
  @Override
  public void onActivityResult(int ignored1, int resultCode, Intent ignored2) {
    if (resultCode == Activity.RESULT_CANCELED) {
      Log.i(
          TAG,
          String.format(
              "Couldn't reach the app store %s to unarchive, retry count: %s",
              appStorePackageName, retryCount));
      if (unhibernateFuture != null && retryCount++ < 3) {
        unhibernateFuture =
            scheduledExecutorService.schedule(this::startUnhibernateActivityForResult, 5, SECONDS);
      } else {
        processingError = true;
        buildErrorDialog().show();
        unhibernateFuture = null;
        retryCount = 0;
      }
    } else {
      // This should not be reachable due to the noHistory activity flag.
      finish();
    }
  }

  /**
   * Getting resource by id does not work because classes.dex is prebuild and
   * reactivation_app_store_package_name resource is added dynamically with the next available id.
   */
  @SuppressLint("DiscouragedApi")
  private String getAppStorePackageName() {
    return getResources()
        .getString(
            getResources()
                .getIdentifier("reactivation_app_store_package_name", "string", getPackageName()));
  }

  @SuppressLint("DiscouragedApi")
  private int getSplashScreenLayoutResId() {
    return getResources()
        .getIdentifier(
            "com_android_vending_archive_splash_screen_layout", "layout", getPackageName());
  }

  private void handleSplashScreen(ReactivateActivity activity) {
    if (VERSION.SDK_INT < VERSION_CODES.S) {
      int splashLayoutResId = getSplashScreenLayoutResId();
      if (splashLayoutResId != 0) {
        activity.setContentView(splashLayoutResId);
      }
    }
  }

  private void startUnhibernateActivityForResult() {
    Log.i(TAG, "Unarchiving package: " + getPackageName());
    Intent intent = new Intent();
    intent.setAction("com.google.android.STORE_ARCHIVE");
    intent.setPackage(appStorePackageName);

    // Forward any attached data to the app store for consideration.
    if (getIntent() != null && getIntent().getData() != null) {
      intent.putExtra(EXTRA_DATA, getIntent().getData());
    }

    try {
      startActivityForResult(intent, /* flags= */ 0);
    } catch (ActivityNotFoundException e) {
      // We handle this case in onActivityResult, because a RESULT_CANCELED is emitted.
    }
  }
}
