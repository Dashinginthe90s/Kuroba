/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.ui.helper;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.cache.FileCacheListener;
import com.github.adamantcheese.chan.core.cache.FileCacheV2;
import com.github.adamantcheese.chan.core.cache.downloader.CancelableDownload;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.widget.CancellableToast;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.IOUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.fsaf.file.RawFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getClipboardContent;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;

public class ImagePickDelegate {
    private static final int IMAGE_PICK_RESULT = 2;
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;
    private static final String DEFAULT_FILE_NAME = "file";

    @Inject
    FileManager fileManager;
    @Inject
    FileCacheV2 fileCacheV2;

    private Activity activity;
    private ImagePickCallback callback;
    private Uri uri;
    private String fileName = "";
    private boolean success = false;

    @Nullable
    private CancelableDownload cancelableDownload;

    public ImagePickDelegate(Activity activity) {
        this.activity = activity;
        inject(this);
    }

    public void pick(ImagePickCallback callback, boolean longPressed) {
        BackgroundUtils.ensureMainThread();

        if (this.callback == null) {
            this.callback = callback;

            if (longPressed) {
                pickRemoteFile(callback);
            } else {
                pickLocalFile(callback);
            }
        }
    }

    private void pickLocalFile(ImagePickCallback callback) {
        PackageManager pm = getAppContext().getPackageManager();

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");

        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
        List<Intent> intents = new ArrayList<>(resolveInfos.size());

        for (ResolveInfo info : resolveInfos) {
            Intent newIntent = new Intent(Intent.ACTION_GET_CONTENT);
            newIntent.addCategory(Intent.CATEGORY_OPENABLE);
            newIntent.setPackage(info.activityInfo.packageName);
            newIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            newIntent.setType("*/*");

            intents.add(newIntent);
        }

        if (!intents.isEmpty()) {
            if (intents.size() == 1 || !ChanSettings.allowFilePickChooser.get()) {
                activity.startActivityForResult(intents.get(0), IMAGE_PICK_RESULT);
            } else {
                Intent chooser = Intent.createChooser(intents.remove(intents.size() - 1),
                        getString(R.string.image_pick_delegate_select_file_picker)
                );

                chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, intents.toArray(new Intent[0]));
                activity.startActivityForResult(chooser, IMAGE_PICK_RESULT);
            }
        } else {
            showToast(activity, R.string.open_file_picker_failed, Toast.LENGTH_LONG);
            callback.onFilePickError(false);
            reset();
        }
    }

    private void pickRemoteFile(ImagePickCallback callback) {
        CancellableToast toast = new CancellableToast();
        toast.showToast(activity, R.string.image_url_get_attempt);
        HttpUrl clipboardURL;
        try {
            //this is converted to a string again later, but this is an easy way of catching if the clipboard item is a URL
            clipboardURL = HttpUrl.get(getClipboardContent());
        } catch (Exception exception) {
            toast.showToast(activity, getString(R.string.image_url_get_failed, exception.getMessage()));
            callback.onFilePickError(true);
            reset();

            return;
        }

        HttpUrl finalClipboardURL = clipboardURL;
        if (cancelableDownload != null) {
            cancelableDownload.cancel();
            cancelableDownload = null;
        }

        cancelableDownload = fileCacheV2.enqueueNormalDownloadFileRequest(clipboardURL, new FileCacheListener() {
            @Override
            public void onSuccess(RawFile file, boolean immediate) {
                toast.showToast(activity, R.string.image_url_get_success);
                Uri imageURL = Uri.parse(finalClipboardURL.toString());
                callback.onFilePicked(imageURL.getLastPathSegment(), new File(file.getFullPath()));
            }

            @Override
            public void onNotFound() {
                onFail(new IOException("Not found"));
            }

            @Override
            public void onFail(Exception exception) {
                String message = getString(R.string.image_url_get_failed, exception.getMessage());

                toast.showToast(activity, message);
                callback.onFilePickError(true);
            }

            @Override
            public void onEnd() {
                reset();
            }
        });
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (callback == null || requestCode != IMAGE_PICK_RESULT) {
            return;
        }

        boolean ok = false;
        boolean canceled = false;

        if (resultCode == Activity.RESULT_OK && data != null) {
            uri = getUriOrNull(data);
            if (uri != null) {
                Cursor returnCursor = activity.getContentResolver().query(uri, null, null, null, null);
                if (returnCursor != null) {
                    int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex > -1 && returnCursor.moveToFirst()) {
                        fileName = returnCursor.getString(nameIndex);
                    }

                    returnCursor.close();
                }

                if (TextUtils.isEmpty(fileName)) {
                    // As per the comment on OpenableColumns.DISPLAY_NAME:
                    // If this is not provided then the name should default to the last segment of the file's URI.
                    fileName = uri.getLastPathSegment();
                    fileName = TextUtils.isEmpty(fileName) ? DEFAULT_FILE_NAME : fileName;
                }

                BackgroundUtils.backgroundService.execute(this::doFilePicked);
                ok = true;
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            canceled = true;
        }

        if (!ok) {
            callback.onFilePickError(canceled);
            reset();
        }
    }

    @Nullable
    private Uri getUriOrNull(Intent intent) {
        if (intent.getData() != null) return intent.getData();

        ClipData clipData = intent.getClipData();
        if (clipData != null && clipData.getItemCount() > 0) {
            return clipData.getItemAt(0).getUri();
        }

        return null;
    }

    private void doFilePicked() {
        RawFile cacheFile = fileManager.fromRawFile(getPickFile());

        try (ParcelFileDescriptor fileDescriptor = activity.getContentResolver().openFileDescriptor(uri, "r")) {
            if (fileDescriptor == null) {
                throw new IOException("Couldn't open file descriptor for uri = " + uri);
            }

            try (InputStream is = new FileInputStream(fileDescriptor.getFileDescriptor());
                 OutputStream os = fileManager.getOutputStream(cacheFile)) {
                if (os == null) {
                    throw new IOException(
                            "Could not get OutputStream from the cacheFile, cacheFile = " + cacheFile.getFullPath());
                }

                success = IOUtils.copy(is, os, MAX_FILE_SIZE);
            } catch (Exception e) {
                Logger.e(this, "Error copying file from the file descriptor", e);
            }
        } catch (Exception ignored) {
        }

        if (!success) {
            if (!fileManager.delete(cacheFile)) {
                Logger.e(this, "Could not delete picked_file after copy fail");
            }
        }

        BackgroundUtils.runOnMainThread(() -> {
            if (success) {
                callback.onFilePicked(fileName, new File(cacheFile.getFullPath()));
            } else {
                callback.onFilePickError(false);
            }
            reset();
        });
    }

    public File getPickFile() {
        File cacheFile = new File(getAppContext().getCacheDir(), "picked_file");
        try {
            if (!cacheFile.exists()) cacheFile.createNewFile(); //ensure the file exists for writing to
        } catch (Exception ignored) {
        }
        return cacheFile;
    }

    private void reset() {
        callback = null;
        success = false;
        fileName = "";
        uri = null;
    }

    public void onDestroy() {
        if (cancelableDownload != null) {
            cancelableDownload.cancel();
            cancelableDownload = null;
        }
        reset();
    }

    public interface ImagePickCallback {
        void onFilePicked(String fileName, File file);

        void onFilePickError(boolean canceled);
    }
}
