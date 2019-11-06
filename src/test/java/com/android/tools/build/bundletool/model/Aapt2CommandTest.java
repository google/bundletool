package com.android.tools.build.bundletool.model;

import com.android.tools.build.bundletool.TestData;
import com.android.tools.build.bundletool.testing.Aapt2Helper;
import com.google.common.truth.Truth;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@RunWith(JUnit4.class)
public class Aapt2CommandTest {

  @Rule
  public final TemporaryFolder tmp = new TemporaryFolder();
  private Path tmpDir;

  @Before
  public void setUp() {
    tmpDir = tmp.getRoot().toPath();
  }

  @Test
  public void getPackageNameFromApk() throws IOException {
    Aapt2Command aapt2Command = Aapt2Helper.getAapt2Command();
    InputStream is = TestData.openStream("testdata/apk/com.test.app.apk");
    Path apkFile = tmpDir.resolve("test.apk");
    Files.copy(is, apkFile);
    String packageName = aapt2Command.getPackageNameFromApk(apkFile);
    Truth.assertThat(packageName).isEqualTo("com.test.app");
  }
}