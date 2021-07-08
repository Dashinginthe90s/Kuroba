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
package com.github.adamantcheese.chan.core.site.loader;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.utils.JavaUtils.NoDeleteArrayList;

public class ChanLoaderResponse {
    // Op Post that is created new each time.
    // Used to later copy members like image count to the real op on the main thread.
    public final Post.Builder op;
    public final NoDeleteArrayList<Post> posts = new NoDeleteArrayList<>();

    public ChanLoaderResponse(Post.Builder op) {
        this.op = op;
    }
}
