// Copyright 2016 The Upspin Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.
package io.upspin.upspin;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import go.gobind.Gobind.DirEntry;

/**
 * DirEntryAdapter is a recycler view adapter for displaying cards on the UI that contain directory
 * entries. That is, it shows the contents of a given directory.
 */
public class DirEntryAdapter extends RecyclerView.Adapter<DirEntryAdapter.ViewHolder> implements View.OnClickListener {
    // Interface for sending click events to the users of this adapter. The click captures any
    // click on the card and does not differentiate where in the card it was clicked or the type of
    // click.
    public interface DirEntryClick {
        void onClick(DirEntry de);
    }

    private static final int KB = 1024;
    private static final int MB = 1024 * KB;
    private static final int GB = 1024 * MB;
    private static final int TB = 1024 * GB;

    private String mCurrDir;
    private final ArrayList<DirEntry> mDataset = new ArrayList<DirEntry>();
    private DirEntryClick mOnClickListener;

    // ViewHolder holds the views of a single dir entry after the view has been inflated. This is
    // the standard Android view holder pattern.
    public static class ViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        public TextView mPathView;
        public TextView mSizeView;
        public TextView mDateView;
        public ImageView mImageView;
        public DirEntry mDirEntry;
        private static final DateFormat mDateFmt = DateFormat.getDateTimeInstance();

        public ViewHolder(View v, View.OnClickListener onClickListener) {
            super(v);
            v.setTag(this);
            v.setOnClickListener(onClickListener);
            mPathView = (TextView) v.findViewById(R.id.path);
            mImageView = (ImageView) v.findViewById(R.id.image);
            mSizeView = (TextView) v.findViewById(R.id.size);
            mDateView = (TextView) v.findViewById(R.id.date);
        }

        public void setDirEntry(DirEntry de) {
            mDirEntry = de;
            if (de == null) {
                return;
            }
            if (de.getIsDir()) {
                mImageView.setImageResource(R.drawable.ic_folder_black_48dp);
                mSizeView.setText(""); // Don't hide or make invisible, it's a pain later.
            } else {
                String n = de.getName();
                switch (n.substring(n.length() - 4, n.length())) {
                    case ".gif":
                        mImageView.setImageResource(R.drawable.ic_gif_black_48dp);
                        break;
                    case ".txt":
                        mImageView.setImageResource(R.drawable.ic_description_black_48dp);
                        break;
                    case ".png":
                    case ".jpg":
                    case ".bmp":
                        mImageView.setImageResource(R.drawable.ic_image_black_48dp);
                        break;
                    default:
                        mImageView.setImageResource(R.drawable.ic_help_black_48dp);
                }
                mSizeView.setText(formatSize(de.getSize()));
            }
            Date d = new Date(de.getLastModified() * 1000);
            mDateView.setText(mDateFmt.format(d));
        }

        private String formatSize(long size) {
            if (size < 1) {
                return "0 bytes";
            } else if (size < 2) {
                return "1 byte";
            } else if (size < KB) {
                return String.format(Locale.US, "%d bytes", size);
            } else if (size < MB) {
                return String.format(Locale.US, "%d kB", size/KB);
            } else if (size < GB) {
                return String.format(Locale.US, "%d MB", size/MB);
            } else if (size < TB) {
                return String.format(Locale.US, "%d GB", size/GB);
            } else {
                return String.format(Locale.US, "%d TB", size/TB);
            }
        }

        public DirEntry getDirEntry() {
            return mDirEntry;
        }
    }

    public void showEntries(String currDir, DirEntry entries) {
        mCurrDir = currDir;
        mDataset.clear();
        for (DirEntry d = entries; d != null; d = d.getNext()) {
            mDataset.add(d);
        }
        notifyDataSetChanged();
    }

    public DirEntryAdapter(DirEntryClick onClickListener) {
        mOnClickListener = onClickListener;
    }

    // Create new views (invoked by the layout manager).
    @Override
    public DirEntryAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                         int viewType) {
        // create a new view
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.content_list_dir, parent, false);
        // set the view's size, margins, paddings and layout parameters
        // ...
        ViewHolder vh = new ViewHolder(v, this);
        return vh;
    }

    @Override
    public void onClick(View view) {
        ViewHolder vh = (ViewHolder) view.getTag();
        if (mOnClickListener != null) {
            mOnClickListener.onClick(vh.getDirEntry());
        }
    }

    // Replace the contents of a view (invoked by the layout manager).
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element

        DirEntry de = mDataset.get(position);
        holder.setDirEntry(de);

        String fullName = de.getName();
        String subPath = fullName.substring(mCurrDir.length()+1);
        holder.mPathView.setText(subPath);
    }

    // Return the size of the dataset (invoked by the layout manager).
    @Override
    public int getItemCount() {
        return mDataset.size();
    }
}
