package com.xeroxphotopro;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Instrumentation-only destination, never included in the delivered application. */
public class HouseTestDocumentsProvider extends DocumentsProvider {
  @Override
  public boolean onCreate() { return true; }

  @Override
  public Cursor queryRoots(String[] projection) {
    MatrixCursor cursor = new MatrixCursor(projection == null ? new String[] {
        Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE,
        Root.COLUMN_FLAGS} : projection);
    cursor.newRow().add(Root.COLUMN_ROOT_ID, "root").add(Root.COLUMN_DOCUMENT_ID, "root")
        .add(Root.COLUMN_TITLE, "House test exports")
        .add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE | Root.FLAG_LOCAL_ONLY);
    return cursor;
  }

  @Override
  public Cursor queryDocument(String id, String[] projection) {
    MatrixCursor cursor = cursor(projection);
    add(cursor, id);
    return cursor;
  }

  @Override
  public Cursor queryChildDocuments(String parent, String[] projection, String sortOrder) {
    MatrixCursor cursor = cursor(projection);
    File[] children = file(parent).listFiles();
    if (children != null) {
      for (File child : children) { add(cursor, child.getName()); }
    }
    return cursor;
  }

  @Override
  public ParcelFileDescriptor openDocument(String id, String mode, CancellationSignal signal)
      throws FileNotFoundException {
    return ParcelFileDescriptor.open(file(id), ParcelFileDescriptor.parseMode(mode));
  }

  @Override
  public String createDocument(String parent, String mimeType, String name)
      throws FileNotFoundException {
    if (!"root".equals(parent)) { throw new IllegalArgumentException("Invalid parent"); }
    try {
      if (!file(name).createNewFile()) { throw new IOException("Document already exists"); }
      return name;
    } catch (IOException error) {
      FileNotFoundException failure = new FileNotFoundException("Cannot create fixture document");
      failure.initCause(error);
      throw failure;
    }
  }

  @Override
  public void deleteDocument(String id) throws FileNotFoundException {
    if ("root".equals(id) || !file(id).delete()) {
      throw new FileNotFoundException("Cannot delete fixture document");
    }
  }

  @Override
  public boolean isChildDocument(String parent, String id) {
    return "root".equals(parent) && file(id).exists();
  }

  private File file(String id) {
    if (id == null || !id.matches("[A-Za-z0-9._-]+") || id.startsWith(".")) {
      throw new IllegalArgumentException("Invalid fixture identifier");
    }
    File directory = new File(getContext().getFilesDir(), "house-export-fixture");
    if (!directory.isDirectory() && !directory.mkdirs()) {
      throw new IllegalStateException("Cannot create fixture directory");
    }
    return "root".equals(id) ? directory : new File(directory, id);
  }

  private MatrixCursor cursor(String[] projection) {
    return new MatrixCursor(projection == null ? new String[] {
        Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_FLAGS} : projection);
  }

  private void add(MatrixCursor cursor, String id) {
    File document = file(id);
    cursor.newRow().add(Document.COLUMN_DOCUMENT_ID, id).add(Document.COLUMN_DISPLAY_NAME, id)
        .add(Document.COLUMN_MIME_TYPE, "root".equals(id) ? Document.MIME_TYPE_DIR : "image/jpeg")
        .add(Document.COLUMN_SIZE, document.length())
        .add(Document.COLUMN_FLAGS, "root".equals(id) ? Document.FLAG_DIR_SUPPORTS_CREATE
            : Document.FLAG_SUPPORTS_WRITE | Document.FLAG_SUPPORTS_DELETE);
  }
}
