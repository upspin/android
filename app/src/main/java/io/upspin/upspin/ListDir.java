package io.upspin.upspin;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
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

import go.gobind.Gobind;
import go.gobind.Gobind.Client;
import go.gobind.Gobind.ClientConfig;
import go.gobind.Gobind.DirEntry;

public class ListDir extends AppCompatActivity implements DirEntryAdapter.DirEntryClick {
    private RecyclerView mRecyclerView;
    private DirEntryAdapter mAdapter;
    private LinearLayoutManager mLayoutManager;
    private Client mClient;
    private CollapsingToolbarLayout mToolbarLayout;
    private ClientConfig clientConfig;

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
                Snackbar.make(view, "Upload not implemented yet", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
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
        String userNetAddr = prefs.getString(getString(R.string.userserver_key), "").trim();

        if (pubKey.length() > 0 && pubKey.charAt(pubKey.length() - 1) != '\n') {
            pubKey = pubKey + "\n";
        }
        ClientConfig cfg = Gobind.NewClientConfig();
        cfg.setUserName(userName);
        cfg.setPublicKey(pubKey);
        cfg.setPrivateKey(privKey);
        cfg.setDirNetAddr(dirNetAddr);
        cfg.setStoreNetAddr(storeNetAddr);
        cfg.setUserNetAddr(userNetAddr);

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

        if (clientConfig == null) {
            clientConfig = cfg;
            // Fall through to refresh.
        } else {
            if (cfg.equals(clientConfig)) {
                // No changes, no need to refresh.
                return;
            }
            // Fall through to refresh.
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
                String currDir = mToolbarLayout.getTitle().toString();
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

        mToolbarLayout.setTitle(currentDir);
        // Disable or enable the back button depending whether we're at the root or not, respectively.
        boolean backButton = !currentDir.equals(clientConfig.getUserName());
        b.setDisplayHomeAsUpEnabled(backButton);
        b.setHomeButtonEnabled(backButton);
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
                if (entry != null) {
                    mAdapter.showEntries(dir, entry);
                    setCurrentDir(dir);
                } else {
                    if (opException != null) {
                        Snackbar.make(getCurrentFocus(), "Error in Glob:" + opException.getMessage(),
                                Snackbar.LENGTH_LONG).setAction("Action", null).show();
                    }
                }
            }
        }.execute();
    }

    private void refresh() {
        Log.i("Refresh", "Refreshing mobile client");

        // This does not do networking operations hence it is suitable for the main thread.
        try {
            mClient = Gobind.NewClient(clientConfig);
        } catch (java.lang.Exception e) {
            showModalErrorMessage(e.getLocalizedMessage(), "Error Connecting Client");
            return;
        }

        // List the contents of the user's root.
        if (mClient != null) {
            navigateToDir(clientConfig.getUserName());
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
}
