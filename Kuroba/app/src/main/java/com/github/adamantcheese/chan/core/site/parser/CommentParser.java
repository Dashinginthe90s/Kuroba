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
package com.github.adamantcheese.chan.core.site.parser;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.JsonReader;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.manager.ArchivesManager;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.model.PostLinkable.Type;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.site.ExternalSiteArchive;
import com.github.adamantcheese.chan.core.site.ExternalSiteArchive.ArchiveSiteUrlHandler;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4;
import com.github.adamantcheese.chan.ui.text.AbsoluteSizeSpanHashed;
import com.github.adamantcheese.chan.ui.text.ForegroundColorSpanHashed;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.NetUtils;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.adamantcheese.chan.Chan.instance;
import static com.github.adamantcheese.chan.core.site.parser.StyleRule.tagRule;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;

@AnyThread
public class CommentParser {
    private static final String SAVED_REPLY_SELF_SUFFIX = " (Me)";
    private static final String SAVED_REPLY_OTHER_SUFFIX = " (You)";
    private static final String OP_REPLY_SUFFIX = " (OP)";
    private static final String EXTERN_THREAD_LINK_SUFFIX = " \u2192"; // arrow to the right

    private Pattern fullQuotePattern = Pattern.compile("/(\\w+)/\\w+/(\\d+)#p(\\d+)");
    private Pattern quotePattern = Pattern.compile(".*#p(\\d+)");

    // A pattern matching any board links
    private Pattern boardLinkPattern = Pattern.compile("//boards\\.4chan.*?\\.org/(.*?)/");
    //alternate for some sites (formerly 8chan)
    private Pattern boardLinkPattern8Chan = Pattern.compile("/(.*?)/index.html");
    // A pattern matching any board search links
    private Pattern boardSearchPattern = Pattern.compile("//boards\\.4chan.*?\\.org/(.*?)/catalog#s=(.*)");
    // A pattern matching colors for r9k
    private Pattern colorPattern = Pattern.compile("color:#([0-9a-fA-F]+)");

    // The list of rules for this parser, mapping an HTML tag to a list of StyleRules that need to be applied for that tag
    private Map<String, List<StyleRule>> rules = new HashMap<>();

    private static final Typeface mona = Typeface.createFromAsset(getAppContext().getAssets(), "font/mona.ttf");

    public CommentParser() {
        // Required tags.
        rule(tagRule("p"));
        rule(tagRule("div"));
        rule(tagRule("br").just("\n"));
    }

    public CommentParser addDefaultRules() {
        rule(tagRule("a").action(this::handleAnchor));

        rule(tagRule("span").cssClass("deadlink")
                .foregroundColor(StyleRule.ForegroundColor.QUOTE)
                .strikeThrough()
                .action(this::handleDead));
        rule(tagRule("span").cssClass("spoiler").link(Type.SPOILER));
        rule(tagRule("span").cssClass("fortune").action(this::handleFortune));
        rule(tagRule("span").cssClass("abbr").nullify());
        rule(tagRule("span").foregroundColor(StyleRule.ForegroundColor.INLINE_QUOTE).linkify());
        rule(tagRule("span").cssClass("sjis").typeface(mona));

        rule(tagRule("table").action(this::handleTable));

        rule(tagRule("s").link(Type.SPOILER));

        rule(tagRule("strong").bold());
        // these ones are css inline style specific
        rule(tagRule("strong-color: red").bold().foregroundColor(StyleRule.ForegroundColor.RED));
        rule(tagRule("p-font-size:15px-font-weight:bold").bold());

        rule(tagRule("b").bold());

        rule(tagRule("i").italic());
        rule(tagRule("em").italic());

        rule(tagRule("pre").cssClass("prettyprint")
                .monospace()
                .size(sp(12f))
                .backgroundColor(StyleRule.BackgroundColor.CODE));
        return this;
    }

    public void rule(StyleRule rule) {
        List<StyleRule> list = rules.get(rule.tag());
        if (list == null) {
            list = new ArrayList<>(3);
            rules.put(rule.tag(), list);
        }

        list.add(rule);
    }

    /**
     * @param quotePattern The quote pattern to use for quotes within a thread, matching the href of an 'a' element<br>
     *                     Should contain a single matching group that resolves to the post number for the quote
     */
    public void setQuotePattern(Pattern quotePattern) {
        this.quotePattern = quotePattern;
    }

    /**
     * @param fullQuotePattern The quote pattern to use for quotes linking outside a thread, matching the href of an 'a' element<br>
     *                         Should contain three matching groups that resolve to the board code, op number, and post number
     */
    public void setFullQuotePattern(Pattern fullQuotePattern) {
        this.fullQuotePattern = fullQuotePattern;
    }

    public CharSequence handleTag(
            PostParser.Callback callback,
            @NonNull Theme theme,
            Post.Builder post,
            String tag,
            CharSequence text,
            Element element
    ) {

        List<StyleRule> rules = this.rules.get(tag);
        if (rules != null) {
            for (int i = 0; i < 2; i++) {
                boolean highPriority = i == 0;
                for (StyleRule rule : rules) {
                    if (rule.highPriority() == highPriority && rule.applies(element)) {
                        return rule.apply(theme, callback, post, text, element);
                    }
                }
            }
        }

        // Unknown tag, return the text;
        return text;
    }

    private CharSequence handleAnchor(
            @NonNull Theme theme, PostParser.Callback callback, Post.Builder post, CharSequence text, Element anchor
    ) {
        CommentParser.Link handlerLink = matchAnchor(post, text, anchor, callback);
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();

        if (handlerLink != null) {
            addReply(theme, callback, post, handlerLink, spannableStringBuilder);
        }

        return spannableStringBuilder.length() > 0 ? spannableStringBuilder : null;
    }

    private void addReply(
            @NonNull Theme theme,
            PostParser.Callback callback,
            Post.Builder post,
            Link handlerLink,
            SpannableStringBuilder spannableStringBuilder
    ) {
        if (handlerLink.type == Type.THREAD && !handlerLink.key.toString().contains(EXTERN_THREAD_LINK_SUFFIX)) {
            handlerLink.key = TextUtils.concat(handlerLink.key, EXTERN_THREAD_LINK_SUFFIX);
        }

        if (handlerLink.type == Type.ARCHIVE && (
                (handlerLink.value instanceof ThreadLink && ((ThreadLink) handlerLink.value).postId == -1)
                        || handlerLink.value instanceof ResolveLink) && !handlerLink.key.toString()
                .contains(EXTERN_THREAD_LINK_SUFFIX)) {
            handlerLink.key = TextUtils.concat(handlerLink.key, EXTERN_THREAD_LINK_SUFFIX);
        }

        if (handlerLink.type == Type.QUOTE) {
            int postNo = (int) handlerLink.value;
            post.addReplyTo(postNo);

            // Append (OP) when it's a reply to OP
            if (postNo == post.opId && !handlerLink.key.toString().contains(OP_REPLY_SUFFIX)) {
                handlerLink.key = TextUtils.concat(handlerLink.key, OP_REPLY_SUFFIX);
            }

            // Append (You) when it's a reply to a saved reply, (Me) if it's a self reply
            if (callback.isSaved(postNo)) {
                if (post.isSavedReply) {
                    if (!handlerLink.key.toString().contains(SAVED_REPLY_SELF_SUFFIX)) {
                        handlerLink.key = TextUtils.concat(handlerLink.key, SAVED_REPLY_SELF_SUFFIX);
                    }
                } else {
                    if (!handlerLink.key.toString().contains(SAVED_REPLY_OTHER_SUFFIX)) {
                        handlerLink.key = TextUtils.concat(handlerLink.key, SAVED_REPLY_OTHER_SUFFIX);
                    }
                }
            }
        }

        SpannableString res = new SpannableString(handlerLink.key);
        PostLinkable pl = new PostLinkable(theme, handlerLink.key, handlerLink.value, handlerLink.type);
        res.setSpan(pl, 0, res.length(), (250 << Spanned.SPAN_PRIORITY_SHIFT) & Spanned.SPAN_PRIORITY);
        post.addLinkable(pl);

        spannableStringBuilder.append(res);
    }

    private CharSequence handleFortune(
            Theme theme, PostParser.Callback callback, Post.Builder builder, CharSequence text, Element span
    ) {
        // html looks like <span class="fortune" style="color:#0893e1"><br><br><b>Your fortune:</b>
        String style = span.attr("style");
        if (!TextUtils.isEmpty(style)) {
            style = style.replace(" ", "");

            Matcher matcher = colorPattern.matcher(style);
            if (matcher.find()) {
                int hexColor = Integer.parseInt(matcher.group(1), 16);
                if (hexColor >= 0 && hexColor <= 0xffffff) {
                    text = span(text,
                            new ForegroundColorSpanHashed(0xff000000 + hexColor),
                            new StyleSpan(Typeface.BOLD)
                    );
                }
            }
        }

        return text;
    }

    public CharSequence handleTable(
            Theme theme, PostParser.Callback callback, Post.Builder builder, CharSequence text, Element table
    ) {
        List<CharSequence> parts = new ArrayList<>();
        Elements tableRows = table.getElementsByTag("tr");
        for (int i = 0; i < tableRows.size(); i++) {
            Element tableRow = tableRows.get(i);
            if (tableRow.text().length() > 0) {
                Elements tableDatas = tableRow.getElementsByTag("td");
                for (int j = 0; j < tableDatas.size(); j++) {
                    Element tableData = tableDatas.get(j);

                    SpannableString tableDataPart = new SpannableString(tableData.text());
                    if (tableData.getElementsByTag("b").size() > 0) {
                        tableDataPart.setSpan(new StyleSpan(Typeface.BOLD), 0, tableDataPart.length(), 0);
                        tableDataPart.setSpan(new UnderlineSpan(), 0, tableDataPart.length(), 0);
                    }

                    parts.add(tableDataPart);

                    if (j < tableDatas.size() - 1) parts.add(": ");
                }

                if (i < tableRows.size() - 1) parts.add("\n");
            }
        }

        // Overrides the text (possibly) parsed by child nodes.
        return span(TextUtils.concat(parts.toArray(new CharSequence[0])),
                new ForegroundColorSpanHashed(getAttrColor(theme.resValue, R.attr.post_inline_quote_color)),
                new AbsoluteSizeSpanHashed(sp(12f))
        );
    }

    public CharSequence handleDead(
            Theme theme, PostParser.Callback callback, Post.Builder builder, CharSequence text, Element deadlink
    ) {
        //crossboard thread links in the OP are likely not thread links, so just let them error out on the parseInt
        try {
            if (!(builder.board.site instanceof Chan4)) return text; //4chan only
            int postNo = Integer.parseInt(deadlink.text().substring(2));
            List<ExternalSiteArchive> boards = instance(ArchivesManager.class).archivesForBoard(builder.board);
            if (!boards.isEmpty()) {
                PostLinkable newLinkable = new PostLinkable(theme,
                        text,
                        // if the deadlink is in an external archive, set a resolve link
                        // if the deadlink is in any other site, we don't have enough info to properly link to stuff, so
                        // we assume that deadlinks in an OP are previous threads
                        // and any deadlinks in other posts are deleted posts in the same thread
                        builder.board.site instanceof ExternalSiteArchive
                                ? new ResolveLink(builder.board.site,
                                builder.board.code,
                                postNo
                        )
                                : new ThreadLink(builder.board.code,
                                        builder.op ? postNo : builder.opId,
                                        builder.op ? -1 : postNo
                                ),
                        Type.ARCHIVE
                );
                text = span(text, newLinkable);
                builder.addLinkable(newLinkable);
            }
        } catch (Exception ignored) {
        }
        return text;
    }

    public Link matchAnchor(Post.Builder post, CharSequence text, Element anchor, PostParser.Callback callback) {
        String href = anchor.attr("href");
        //gets us something like /board/ or /thread/postno#quoteno
        //hacky fix for 4chan having two domains but the same API
        if (href.matches("//boards\\.4chan.*?\\.org/(.*?)/thread/(\\d*?)#p(\\d*)")) {
            href = href.substring(2);
            href = href.substring(href.indexOf('/'));
        }

        Type t;
        Object value;

        Matcher externalMatcher = fullQuotePattern.matcher(href);
        if (externalMatcher.matches()) {
            String board = externalMatcher.group(1);
            int threadId = Integer.parseInt(externalMatcher.group(2));
            String postNo = externalMatcher.group(3);
            int postId = postNo == null ? -1 : Integer.parseInt(postNo);

            if (board.equals(post.board.code) && callback.isInternal(postId)) {
                //link to post in same thread with post number (>>post)
                t = Type.QUOTE;
                value = postId;
            } else {
                //link to post not in same thread with post number (>>post or >>>/board/post)
                //in the case of an archive, set the type to be an archive link
                t = post.board.site instanceof ExternalSiteArchive ? Type.ARCHIVE : Type.THREAD;
                value = new ThreadLink(board, threadId, postId);
                if (href.contains("post") && post.board.site instanceof ExternalSiteArchive) {
                    // this is an archive post link that needs to be resolved into a threadlink
                    value = new ResolveLink(post.board.site, board, threadId);
                }
            }
        } else {
            Matcher quoteMatcher = quotePattern.matcher(href);
            if (quoteMatcher.matches()) {
                //link to post backup???
                t = Type.QUOTE;
                value = Integer.parseInt(quoteMatcher.group(1));
            } else {
                Matcher boardLinkMatcher = boardLinkPattern.matcher(href);
                Matcher boardLinkMatcher8Chan = boardLinkPattern8Chan.matcher(href);
                Matcher boardSearchMatcher = boardSearchPattern.matcher(href);
                if (boardLinkMatcher.matches() || boardLinkMatcher8Chan.matches()) {
                    //board link
                    t = Type.BOARD;
                    value = boardLinkMatcher.matches() ? boardLinkMatcher.group(1) : boardLinkMatcher8Chan.group(1);
                } else if (boardSearchMatcher.matches()) {
                    //search link
                    String board = boardSearchMatcher.group(1);
                    String search;
                    try {
                        search = URLDecoder.decode(boardSearchMatcher.group(2), "US-ASCII");
                    } catch (UnsupportedEncodingException e) {
                        search = boardSearchMatcher.group(2);
                    }
                    t = Type.SEARCH;
                    value = new SearchLink(board, search);
                } else {
                    //normal link
                    t = Type.LINK;
                    value = href;
                }
            }
        }

        Link link = new Link();
        link.type = t;
        link.key = text;
        link.value = value;
        return link;
    }

    public SpannableString span(CharSequence text, Object... additionalSpans) {
        SpannableString result = new SpannableString(text);
        int l = result.length();

        if (additionalSpans != null && additionalSpans.length > 0) {
            for (Object additionalSpan : additionalSpans) {
                if (additionalSpan != null) {
                    result.setSpan(additionalSpan, 0, l, 0);
                }
            }
        }

        return result;
    }

    public static class Link {
        public Type type;
        public CharSequence key;
        public Object value;
    }

    public static class ThreadLink {
        public String board;
        public int threadId;
        public int postId;

        public ThreadLink(String board, int threadId, int postId) {
            this.board = board;
            this.threadId = threadId;
            this.postId = postId;
        }
    }

    // this should only ever be for Archives
    public static class ResolveLink {
        public Board board;
        public int postId;

        public ResolveLink(Site site, String boardCode, int postId) {
            this.board = Board.fromSiteNameCode(site, "", boardCode);
            this.postId = postId;
        }

        public void resolve(@NonNull ResolveCallback callback, @NonNull ResolveParser parser) {
            NetUtils.makeJsonRequest(((ExternalSiteArchive.ArchiveEndpoints) board.site.endpoints()).resolvePost(board.code,
                    postId
            ), new NetUtils.JsonResult<ThreadLink>() {
                @Override
                public void onJsonFailure(Exception e) {
                    callback.onProcessed(null);
                }

                @Override
                public void onJsonSuccess(ThreadLink result) {
                    callback.onProcessed(result);
                }
            }, parser, 5000);
        }

        public interface ResolveCallback {
            void onProcessed(@Nullable ThreadLink result);
        }

        public static class ResolveParser
                implements NetUtils.JsonParser<ThreadLink> {
            private ResolveLink sourceLink;

            public ResolveParser(ResolveLink source) {
                sourceLink = source;
            }

            @Override
            public ThreadLink parse(JsonReader reader) {
                return ((ArchiveSiteUrlHandler) sourceLink.board.site.resolvable()).resolveToThreadLink(sourceLink,
                        reader
                );
            }
        }
    }

    public static class SearchLink {
        public String board;
        public String search;

        public SearchLink(String board, String search) {
            this.board = board;
            this.search = search;
        }
    }
}
