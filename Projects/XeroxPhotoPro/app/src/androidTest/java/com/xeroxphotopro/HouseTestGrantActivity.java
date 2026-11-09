package com.xeroxphotopro;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;

/** Test-only picker stand-in granting access to synthetic exports. */
public class HouseTestGrantActivity extends Activity {
  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    Uri tree = DocumentsContract.buildTreeDocumentUri(
        "com.xeroxphotopro.test.house-documents", "root");
    grantUriPermission("com.xeroxphotopro", tree, Intent.FLAG_GRANT_READ_URI_PERMISSION
        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
    finish();
  }
}
