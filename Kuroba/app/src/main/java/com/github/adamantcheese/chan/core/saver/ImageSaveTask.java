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
package com.github.adamantcheese.chan.core.saver;

import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.github.adamantcheese.chan.core.cache.CacheHandler;
import com.github.adamantcheese.chan.core.cache.FileCacheListener;
import com.github.adamantcheese.chan.core.cache.FileCacheV2;
import com.github.adamantcheese.chan.core.cache.downloader.CancelableDownload;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.StringUtils;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.fsaf.file.AbstractFile;
import com.github.k1rakishou.fsaf.file.RawFile;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;

import io.reactivex.Single;
import io.reactivex.functions.Action;
import io.reactivex.subjects.SingleSubject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.core.saver.ImageSaver.BundledDownloadResult.Failure;
import static com.github.adamantcheese.chan.core.saver.ImageSaver.BundledDownloadResult.Success;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppFileProvider;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openIntent;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;

public class ImageSaveTask
        extends FileCacheListener {
    @Inject
    FileCacheV2 fileCacheV2;
    @Inject
    FileManager fileManager;
    @Inject
    CacheHandler cacheHandler;

    private PostImage postImage;
    private AbstractFile destination;
    private boolean share;
    private String subFolder;
    private boolean success = false;
    private SingleSubject<ImageSaver.BundledDownloadResult> imageSaveTaskAsyncResult;

    public ImageSaveTask(PostImage postImage, boolean share) {
        inject(this);

        this.postImage = postImage;
        this.share = share;
        this.imageSaveTaskAsyncResult = SingleSubject.create();
    }

    public void setSubFolder(String boardName) {
        this.subFolder = boardName;
    }

    public String getSubFolder() {
        return subFolder;
    }

    public PostImage getPostImage() {
        return postImage;
    }

    public void setDestination(AbstractFile destination) {
        this.destination = destination;
    }

    public AbstractFile getDestination() {
        return destination;
    }

    public boolean isShareTask() {
        return share;
    }

    public Single<ImageSaver.BundledDownloadResult> run() {
        BackgroundUtils.ensureBackgroundThread();
        Logger.d(this, "ImageSaveTask.run() destination = " + destination.getFullPath());

        @Nullable
        Action onDisposeFunc = null;

        try {
            if (fileManager.exists(destination)) {
                BackgroundUtils.runOnMainThread(() -> {
                    onDestination();
                    onEnd();
                });
            } else {
                CancelableDownload cancelableDownload = fileCacheV2.enqueueNormalDownloadFileRequest(postImage, this);

                onDisposeFunc = () -> {
                    if (cancelableDownload != null) {
                        cancelableDownload.stop();
                    }
                };
            }
        } catch (Exception e) {
            imageSaveTaskAsyncResult.onError(e);
        }

        if (onDisposeFunc != null) {
            return imageSaveTaskAsyncResult.doOnDispose(onDisposeFunc);
        }

        return imageSaveTaskAsyncResult;
    }

    @Override
    public void onSuccess(RawFile file, boolean immediate) {
        BackgroundUtils.ensureMainThread();

        if (copyToDestination(file)) {
            onDestination();
        } else {
            deleteDestination();
        }
    }

    @Override
    public void onFail(Exception exception) {
        BackgroundUtils.ensureMainThread();
        imageSaveTaskAsyncResult.onError(exception);
    }

    @Override
    public void onEnd() {
        BackgroundUtils.ensureMainThread();
        imageSaveTaskAsyncResult.onSuccess(success ? Success : Failure);
    }

    private void deleteDestination() {
        if (fileManager.exists(destination)) {
            if (!fileManager.delete(destination)) {
                Logger.e(this, "Could not delete destination after an interrupt");
            }
        }
    }

    private void onDestination() {
        success = true;
        if (share) {
            try {
                Uri file = FileProvider.getUriForFile(getAppContext(),
                        getAppFileProvider(),
                        new File(destination.getFullPath())
                );

                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType(getAppContext().getContentResolver().getType(file));
                intent.putExtra(Intent.EXTRA_STREAM, file);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                openIntent(intent);
            } catch (Exception e) {
                showToast(getAppContext(), "Failed to share file.");
            }
            return;
        }

        try {
            String[] paths = {destination.getFullPath()};
            MediaScannerConnection.scanFile(getAppContext(), paths, null, null);
        } catch (Exception ignored) {}
    }

    private boolean copyToDestination(RawFile source) {
        try {
            if (share) {
                destination = cacheHandler.duplicateCacheFile(source,
                        StringUtils.fileNameRemoveBadCharacters(postImage.filename),
                        postImage.extension
                );
            } else {
                AbstractFile createdDestinationFile = fileManager.create(destination);
                if (createdDestinationFile == null) {
                    throw new IOException("Could not create destination file, path = " + destination.getFullPath());
                }

                if (fileManager.isDirectory(createdDestinationFile)) {
                    throw new IOException("Destination file is already a directory");
                }

                if (!fileManager.copyFileContents(source, createdDestinationFile)) {
                    throw new IOException("Could not copy source file into destination");
                }
            }
            return true;
        } catch (Throwable e) {
            boolean exists = fileManager.exists(destination);
            boolean canWrite = fileManager.canWrite(destination);

            Logger.e(this,
                    "Error writing to file: (" + destination.getFullPath() + "), " + "exists = " + exists
                            + ", canWrite = " + canWrite,
                    e
            );
        }

        return false;
    }
}
