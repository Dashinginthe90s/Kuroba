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
package com.github.adamantcheese.chan.ui.adapter;

import android.view.View;
import android.view.ViewGroup;

import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.ui.view.MultiImageView;
import com.github.adamantcheese.chan.ui.view.ViewPagerAdapter;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.List;

public class ImageViewerAdapter
        extends ViewPagerAdapter {
    private final List<PostImage> images;
    private final Loadable loadable;
    private final MultiImageView.Callback multiImageViewCallback;

    private List<MultiImageView> loadedViews = new ArrayList<>(3);
    private List<ModeChange> pendingModeChanges = new ArrayList<>();

    public ImageViewerAdapter(
            List<PostImage> images, Loadable loadable, MultiImageView.Callback multiImageViewCallback
    ) {
        this.images = images;
        this.loadable = loadable;
        this.multiImageViewCallback = multiImageViewCallback;
    }

    @Override
    public View getView(int position, ViewGroup parent) {
        PostImage postImage = images.get(position);
        MultiImageView view = new MultiImageView(parent.getContext());
        view.bindPostImage(postImage, multiImageViewCallback, images.get(0) == postImage); // hacky but good enough

        loadedViews.add(view);

        return view;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        super.destroyItem(container, position, object);

        //noinspection SuspiciousMethodCalls
        loadedViews.remove(object);
    }

    @Override
    public int getCount() {
        return images.size();
    }

    public MultiImageView find(PostImage postImage) {
        for (MultiImageView view : loadedViews) {
            if (view.getPostImage() == postImage) {
                return view;
            }
        }
        return null;
    }

    @Override
    public void finishUpdate(ViewGroup container) {
        for (ModeChange change : pendingModeChanges) {
            MultiImageView view = find(change.postImage);
            if (view == null || view.getWindowToken() == null) {
                Logger.w(this, "finishUpdate setMode view still not found");
            } else {
                view.setMode(change.mode, change.center);
            }
        }
        pendingModeChanges.clear();
    }

    public void setMode(final PostImage postImage, MultiImageView.Mode mode, boolean center) {
        MultiImageView view = find(postImage);
        if (view == null || view.getWindowToken() == null) {
            pendingModeChanges.add(new ModeChange(mode, postImage, center));
        } else {
            view.setMode(mode, center);
        }
    }

    public void setVolume(PostImage postImage, boolean muted) {
        // It must be loaded, or the user is not able to click the menu item.
        MultiImageView view = find(postImage);
        if (view != null) {
            view.setVolume(muted);
        }
    }

    public MultiImageView.Mode getMode(PostImage postImage) {
        MultiImageView view = find(postImage);
        if (view == null) {
            Logger.w(this, "getMode view not found");
            return null;
        } else {
            return view.getMode();
        }
    }

    public void toggleTransparency(PostImage postImage) {
        MultiImageView view = find(postImage);
        if (view != null) {
            view.toggleTransparency();
        }
    }

    public void onImageSaved(PostImage postImage) {
        MultiImageView view = find(postImage);
        if (view != null) {
            view.setImageAlreadySaved();
        }
    }

    private static class ModeChange {
        public MultiImageView.Mode mode;
        public PostImage postImage;
        public boolean center;

        private ModeChange(MultiImageView.Mode mode, PostImage postImage, boolean center) {
            this.mode = mode;
            this.postImage = postImage;
            this.center = center;
        }
    }
}
