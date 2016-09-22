// Copyright 2016 The Upspin Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.
package io.upspin.upspin;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.OpenableColumns;
import android.support.annotation.Nullable;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.util.Pair;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import go.gobind.Gobind;
import go.gobind.Gobind.Client;
import go.gobind.Gobind.ClientConfig;
import go.gobind.Gobind.DirEntry;

/**
 * ListDir is an Activity for listing and navigating the contents of a user directory.
 */
public class ListDir extends AppCompatActivity implements DirEntryAdapter.DirEntryClick {
    private static final int OPEN_FILE_FROM_OTHER_APP = 1;
    private RecyclerView mRecyclerView;
    private DirEntryAdapter mAdapter;
    private LinearLayoutManager mLayoutManager;
    private Client mClient;
    private CollapsingToolbarLayout mToolbarLayout;
    private ClientConfig mClientConfig;
    private String mCurrDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_list_dir);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        mToolbarLayout = (CollapsingToolbarLayout) findViewById(R.id.toolbar_layout);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // ACTION_GET_CONTENT is the intent to choose a file via the system's file
                // browser.
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);

                // Filter to only show results that can be "opened", such as a
                // file (as opposed to a list of contacts or timezones)
                intent.addCategory(Intent.CATEGORY_OPENABLE);

                // Filter to show all files.
                intent.setType("*/*");

                startActivityForResult(intent, OPEN_FILE_FROM_OTHER_APP);
            }
        });

        mRecyclerView = (RecyclerView) findViewById(R.id.recycler);

        // use this setting to improve performance if you know that changes
        // in content do not change the layout size of the RecyclerView
        mRecyclerView.setHasFixedSize(true);

        // use a linear layout manager
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);

        mAdapter = new DirEntryAdapter(this);
        mRecyclerView.setAdapter(mAdapter);
    }

    private void showModalErrorMessage(String error, String title) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(error).setTitle(title).setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private ClientConfig reloadPreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String userName = prefs.getString(getString(R.string.username_key), "").trim();
        String pubKey = prefs.getString(getString(R.string.pubkey_key), "").trim();
        String privKey = prefs.getString(getString(R.string.privkey_key), "").trim();
        String dirNetAddr = prefs.getString(getString(R.string.dirserver_key), "").trim();
        String storeNetAddr = prefs.getString(getString(R.string.storeserver_key), "").trim();
        String userNetAddr = prefs.getString(getString(R.string.keyserver_key), "").trim();

        if (pubKey.length() > 0 && pubKey.charAt(pubKey.length() - 1) != '\n') {
            pubKey = pubKey + "\n";
        }
        ClientConfig cfg = Gobind.NewClientConfig();
        cfg.setUserName(userName);
        cfg.setPublicKey(pubKey);
        cfg.setPrivateKey(privKey);
        cfg.setDirNetAddr(dirNetAddr);
        cfg.setStoreNetAddr(storeNetAddr);
        cfg.setKeyNetAddr(userNetAddr);

        return cfg;
    }

    // Refreshes the client if configuration changes have happened or
    // if the client does not yet exist. If configuration variables are not yet set,
    // it launches the settings activity.
    private void refreshIfNeeded() {
        ClientConfig cfg = reloadPreferences();

        if (cfg.getUserName().equals("") ||
                cfg.getPublicKey().equals("") ||
                cfg.getPrivateKey().equals("")) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return;
        }

        if (mClientConfig != null && cfg.equals(mClientConfig)) {
            // No changes, no need to refresh.
            return;
        }
        refresh();
    }

    @Override
    public void onStart() {
        super.onStart();
        refreshIfNeeded();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_list_dir, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch (id) {
            case android.R.id.home:
                String currDir = getCurrDir();
                int i = currDir.lastIndexOf("/");
                navigateToDir(currDir.substring(0, i)); // This can't fail because back is disabled at the root.
                break;
            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_refresh:
                refresh();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Forces the move to de, setting the action bar title and back button if necessary.
    public void setCurrentDir(String currentDir) {
        ActionBar b = getSupportActionBar();
        b.setDisplayShowTitleEnabled(true);

        mCurrDir = currentDir;
        mToolbarLayout.setTitle(currentDir);
        // Disable or enable the back button depending whether we're at the root or not, respectively.
        boolean backButton = !currentDir.equals(mClientConfig.getUserName());
        b.setDisplayHomeAsUpEnabled(backButton);
        b.setHomeButtonEnabled(backButton);
    }

    @Nullable
    public String getCurrDir() {
        return mCurrDir;
    }

    public void navigateToDir(final String dir) {
        new AsyncTask<Void, Void, DirEntry>() {
            private java.lang.Exception opException = null;

            @Override
            protected DirEntry doInBackground(Void... params) {
                DirEntry entry = null;
                try {
                    entry = mClient.Glob(dir + "/*");
                } catch (java.lang.Exception e) {
                    opException = e;
                    return null;
                }
                return entry;
            }

            @Override
            public void onPostExecute(DirEntry entry) {
                if (opException != null) {
                    Snackbar.make(getCurrentFocus(), "Error in Glob:" + opException.getMessage(),
                            Snackbar.LENGTH_LONG).setAction("Action", null).show();
                    return;
                }
                mAdapter.showEntries(dir, entry);
                setCurrentDir(dir);
            }
        }.execute();
    }

    private void refresh() {
        Log.i("Refresh", "Refreshing mobile client");

        mClientConfig = reloadPreferences();

        // This does not do networking operations hence it is suitable for the main thread.
        try {
            mClient = Gobind.NewClient(mClientConfig);
        } catch (java.lang.Exception e) {
            showModalErrorMessage(e.getLocalizedMessage(), "Error Connecting Client");
            return;
        }

        // List the contents of the user's current dir or root if none.
        if (mClient != null) {
            String currDir = getCurrDir();
            if (currDir == null) {
                navigateToDir(mClientConfig.getUserName());
            } else {
                navigateToDir(currDir);
            }
        }
    }

    @Override
    public void onClick(Gobind.DirEntry de) {
        if (de.getIsDir()) {
            navigateToDir(de.getName());
        } else {
            fetchDataAndShowContents(de.getName());
        }
    }

    private void fetchDataAndShowContents(final String fullFilename) {
        new AsyncTask<Void, Void, byte[]>() {
            private java.lang.Exception opException = null;

            @Override
            protected byte[] doInBackground(Void... voids) {
                // TODO: show a spinner while loading.
                byte[] data = null;
                try {
                    data = mClient.Get(fullFilename);
                } catch (java.lang.Exception e) {
                    opException = e;
                    return null;
                }
                return data;
            }

            @Override
            public void onPostExecute(byte[] data) {
                if (data != null) {
                    // TODO: figure out the backstack here so when the ViewContentsActivity is done,
                    // we return to the directory where we were, not the root.
                    Intent intent = new Intent(ListDir.this, ViewContentsActivity.class);
                    intent.putExtra(ViewContentsActivity.FILENAME, fullFilename);
                    intent.putExtra(ViewContentsActivity.DATA, data);
                    startActivity(intent);
                } else {
                    if (opException != null) {
                        Snackbar.make(getCurrentFocus(),
                                "Error in Get:" + opException.getMessage(), Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
                    }
                }
            }
        }.execute();
    }

    /**
     * @param uri Uri from where to read the contents.
     * @return All contents pointer to by the Uri.
     */
    private byte[] readAll(Uri uri) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            BufferedInputStream is = new BufferedInputStream(getContentResolver().openInputStream(uri));
            // Read in chunks of 1MB.
            byte[] data = new byte[1024 * 1024];
            int n;
            while (-1 != (n = is.read(data))) {
                out.write(data, 0, n);
            }
        } catch (FileNotFoundException e) {
            Snackbar.make(getCurrentFocus(),
                    "Error in Open:" + e.getMessage(), Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            Log.e("Open", "error: " + e.getMessage());
        } catch (IOException e) {
            Snackbar.make(getCurrentFocus(),
                    "Error in Read:" + e.getMessage(), Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            Log.e("Read", "error: " + e.getMessage());
        }
        return out.toByteArray();
    }

    /**
     * @param uri The URI to check information for
     * @return A pair with the URI's display name and size. iI size is unknown, -1 is returned.
     */
    private Pair<String, Long> getUriInfo(Uri uri) {
        // The query, since it only applies to a single document, will only return
        // one row. There's no need to filter, sort, or select fields, since we want
        // all fields for one document.
        Cursor cursor = getContentResolver().query(uri, null, null, null, null, null);

        Pair<String, Long> p = Pair.create("", -1L);
        try {
            // moveToFirst() returns false if the cursor has 0 rows.  Very handy for
            // "if there's anything to look at, look at it" conditionals.
            if (cursor != null && cursor.moveToFirst()) {
                // This is provider-specific, and might not necessarily be a file name.
                String displayName = cursor.getString(
                        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));

                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                // If the size is unknown, the value stored is null.
                // The storage API allows for remote files, whose size might not be locally known.
                long size = -1L;
                if (!cursor.isNull(sizeIndex)) {
                    // Technically the column stores an int, but cursor.getString()
                    // will do the conversion automatically.
                    size = cursor.getLong(sizeIndex);
                    p = Pair.create(displayName, size);
                } else {
                    p = Pair.create(displayName, -1L);
                }
            }
        } finally {
            cursor.close();
        }
        return p;
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        // The ACTION_GET_CONTENT intent was sent with the request code
        // OPEN_FILE_FROM_OTHER_APP.
        if (requestCode == OPEN_FILE_FROM_OTHER_APP && resultCode == AppCompatActivity.RESULT_OK) {
            // The document selected by the user won't be returned in the intent.
            // Instead, a URI to that document will be contained in the return intent
            // provided to this method as a parameter.
            if (resultData != null) {
                final Uri uri = resultData.getData();
                new AsyncTask<Void, Void, Void>() {
                    private java.lang.Exception opException = null;

                    @Override
                    protected Void doInBackground(Void... voids) {
                        Pair<String, Long> metaData = getUriInfo(uri);
                        // Re-create a filename in the current directory.
                        String fname = getCurrDir() + "/" + metaData.first;
                        // TODO: Use the size to choose between reading all into memory and the streaming API.
                        byte[] data = readAll(uri);
                        try {
                            String ref = mClient.Put(fname, data);
                            Log.i("ListDir.Put", "ref: " + ref + " fname:" + fname);
                        } catch (Exception e) {
                            opException = e;
                            Log.e("ListDir.Put", "error: " + e.getMessage());
                        }
                        return null;
                    }

                    @Override
                    public void onPostExecute(Void voids) {
                        if (opException != null) {
                            Snackbar.make(getCurrentFocus(),
                                    "Error in Put:" + opException.getMessage(), Snackbar.LENGTH_LONG)
                                    .setAction("Action", null).show();
                        } else {
                            // Issue a "soft" refresh (without restarting the client).
                            navigateToDir(getCurrDir());
                        }
                    }
                }.execute();
            }
        }
    }
}