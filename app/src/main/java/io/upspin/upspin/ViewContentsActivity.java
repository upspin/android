package io.upspin.upspin;

import android.annotation.SuppressLint;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.MenuItem;
import android.support.v4.app.NavUtils;
import android.webkit.WebView;

import java.util.Locale;

/**
 */
public class ViewContentsActivity extends AppCompatActivity {
    // Constants used with Intent when starting this activity.
    public static final String FILENAME = "fileName";  // The name of the file we're loading.
    public static final String DATA = "data";  // The contents of the file.
    private WebView mWebView;
    private String mFileName;
    private byte[] mFileContents;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_contents);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            mFileName = extras.getString(FILENAME);
            mFileContents = extras.getByteArray(DATA);
        }

        mWebView = (WebView)findViewById(R.id.webview);

        String mimeType = "text/plain";
        String encoding = null;
        String extension = "";
        String data = "[no contents]";
        int extensionPos = mFileName.lastIndexOf('.');
        if (extensionPos >= 0) {
            extension = mFileName.substring(extensionPos+1);
        }
        switch (extension) {
            case "gif":
            case "png":
                mimeType = "image/" + extension;
                encoding = "base64";
                data = Base64.encodeToString(mFileContents, Base64.DEFAULT);
                break;
            case "jpg":
            case "jpeg":
                mimeType = "image/jpeg";
                encoding = "base64";
                data = Base64.encodeToString(mFileContents, Base64.DEFAULT);
                break;
            case "html":
                mimeType = "text/html";
                encoding = "utf-8";
                data = new String(mFileContents);
                break;
            case "txt":
            default:
                mimeType = "text/plain";
                encoding = "utf-8";
                data = new String(mFileContents);
                break;
        }

        Log.i("LoadData", data);
        mWebView.loadData(data, mimeType, encoding);

        actionBar.setTitle(mFileName);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            // This ID represents the Home or Up button.
            NavUtils.navigateUpFromSameTask(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
