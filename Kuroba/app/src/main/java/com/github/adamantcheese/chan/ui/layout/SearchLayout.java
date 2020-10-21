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
package com.github.adamantcheese.chan.ui.layout;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.github.adamantcheese.chan.R;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.hideKeyboard;
import static com.github.adamantcheese.chan.utils.AndroidUtils.requestKeyboardFocus;

public class SearchLayout
        extends LinearLayout {
    private EditText searchView;
    private ImageView clearButton;

    public SearchLayout(Context context) {
        super(context);
    }

    public SearchLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SearchLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setCallback(final SearchLayoutCallback callback) {
        searchView = new EditText(getContext());
        searchView.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_ACTION_DONE);
        searchView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        searchView.setHint(getString(R.string.search_hint));
        searchView.setHintTextColor(getAttrColor(getContext(), android.R.attr.textColorHint));
        searchView.setTextColor(getAttrColor(getContext(), android.R.attr.textColorPrimary));
        searchView.setSingleLine(true);
        searchView.setBackgroundResource(0);
        searchView.setPadding(0, 0, 0, 0);
        clearButton = new ImageView(getContext());
        searchView.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearButton.setAlpha(s.length() == 0 ? 0.0f : 1.0f);
                callback.onSearchEntered(s.toString());
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        searchView.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(searchView);
                callback.onSearchEntered(getText());
                return true;
            }
            return false;
        });
        searchView.setOnFocusChangeListener((view, focused) -> {
            if (!focused) {
                view.postDelayed(() -> hideKeyboard(view), 100);
            } else {
                view.postDelayed(() -> {
                    searchView.setSelection(searchView.getText().length());
                    requestKeyboardFocus(view);
                }, 100);
            }
        });
        LinearLayout.LayoutParams searchViewParams = new LinearLayout.LayoutParams(0, dp(getContext(), 36), 1);
        searchViewParams.gravity = Gravity.CENTER_VERTICAL;
        addView(searchView, searchViewParams);
        searchView.setFocusable(true);
        //searchView.requestFocus();

        clearButton.setAlpha(0f);
        clearButton.setImageResource(R.drawable.ic_clear_white_24dp);
        clearButton.getDrawable().setTint(getAttrColor(getContext(), android.R.attr.textColorPrimary));
        clearButton.setScaleType(ImageView.ScaleType.CENTER);
        clearButton.setOnClickListener(v -> {
            searchView.setText("");
            requestKeyboardFocus(searchView);
        });
        addView(clearButton, dp(getContext(), 48), MATCH_PARENT);
    }

    public void setText(String text) {
        searchView.setText(text);
    }

    public String getText() {
        return searchView.getText().toString();
    }

    public void setCatalogSearchColors() {
        searchView.setTextColor(Color.WHITE);
        searchView.setHintTextColor(0x88ffffff);
        clearButton.getDrawable().setTintList(null);
    }

    public interface SearchLayoutCallback {
        void onSearchEntered(String entered);
    }

    public void openKeyboard() {
        searchView.requestFocus();
    }
}
