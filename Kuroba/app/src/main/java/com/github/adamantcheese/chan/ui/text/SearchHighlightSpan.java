package com.github.adamantcheese.chan.ui.text;

/**
 * This is basically just a rename of ChanHighlightSpan, so that removing spans is easy.
 * 40% alpha, and don't change the foreground color, matching the emulator's highlighting
 */
public class SearchHighlightSpan
        extends ChanHighlightSpan {
    public SearchHighlightSpan() {
        super((byte) 102, false);
    }
}
