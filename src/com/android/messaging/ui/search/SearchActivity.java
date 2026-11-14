/*
 * Copyright (C) 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.messaging.ui.search;

import android.database.Cursor;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.messaging.R;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DatabaseHelper;
import com.android.messaging.datamodel.DatabaseHelper.ConversationColumns;
import com.android.messaging.datamodel.DatabaseHelper.MessageColumns;
import com.android.messaging.datamodel.DatabaseHelper.PartColumns;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.util.Dates;
import com.android.messaging.util.RcsBusinessSender;
import com.android.messaging.util.UiUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Searches conversation names and the text of all messages, and opens the conversation a
 * result belongs to.
 */
public class SearchActivity extends AppCompatActivity {
    private static final int MAX_CONVERSATION_RESULTS = 10;
    private static final int MAX_MESSAGE_RESULTS = 200;
    private static final long SEARCH_DELAY_MS = 200;
    // How much text to keep in front of a match when a long message is shortened
    private static final int SNIPPET_LEAD_CHARS = 30;

    private static final String CONVERSATION_QUERY = "SELECT "
            + ConversationColumns._ID + ", "
            + ConversationColumns.NAME + ", "
            + ConversationColumns.SNIPPET_TEXT + ", "
            + ConversationColumns.SORT_TIMESTAMP
            + " FROM " + DatabaseHelper.CONVERSATIONS_TABLE
            + " WHERE " + ConversationColumns.NAME + " LIKE ? ESCAPE '\\'"
            + " ORDER BY " + ConversationColumns.SORT_TIMESTAMP + " DESC"
            + " LIMIT " + MAX_CONVERSATION_RESULTS;

    private static final String MESSAGE_QUERY = "SELECT "
            + "c." + ConversationColumns._ID + ", "
            + "c." + ConversationColumns.NAME + ", "
            + "p." + PartColumns.TEXT + ", "
            + "m." + MessageColumns.RECEIVED_TIMESTAMP
            + " FROM " + DatabaseHelper.PARTS_TABLE + " p"
            + " JOIN " + DatabaseHelper.MESSAGES_TABLE + " m ON p." + PartColumns.MESSAGE_ID
            + " = m." + MessageColumns._ID
            + " JOIN " + DatabaseHelper.CONVERSATIONS_TABLE + " c ON m."
            + MessageColumns.CONVERSATION_ID + " = c." + ConversationColumns._ID
            + " WHERE p." + PartColumns.TEXT + " LIKE ? ESCAPE '\\'"
            + " ORDER BY m." + MessageColumns.RECEIVED_TIMESTAMP + " DESC"
            + " LIMIT " + MAX_MESSAGE_RESULTS;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final ResultAdapter mAdapter = new ResultAdapter();

    private TextView mEmptyView;
    // Incremented for every new query so that results of an older one are dropped
    private int mSearchGeneration;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (UiUtils.redirectToPermissionCheckIfNeeded(this)) {
            return;
        }
        setContentView(R.layout.search_activity);

        findViewById(R.id.search_back).setOnClickListener(v -> finish());

        mEmptyView = findViewById(R.id.search_empty);
        final RecyclerView list = findViewById(R.id.search_results);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(mAdapter);

        final EditText queryView = findViewById(R.id.search_query);
        queryView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(final CharSequence s, final int start, final int count,
                    final int after) {
            }

            @Override
            public void onTextChanged(final CharSequence s, final int start, final int before,
                    final int count) {
            }

            @Override
            public void afterTextChanged(final Editable s) {
                scheduleSearch(s.toString().trim());
            }
        });
        queryView.requestFocus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
        mExecutor.shutdownNow();
    }

    private void scheduleSearch(final String query) {
        final int generation = ++mSearchGeneration;
        mHandler.removeCallbacksAndMessages(null);
        if (query.isEmpty()) {
            showResults(new ArrayList<>(), query);
            return;
        }
        mHandler.postDelayed(() -> mExecutor.execute(() -> {
            final List<Result> results = search(query);
            mHandler.post(() -> {
                if (generation == mSearchGeneration && !isDestroyed()) {
                    showResults(results, query);
                }
            });
        }), SEARCH_DELAY_MS);
    }

    private void showResults(final List<Result> results, final String query) {
        mAdapter.setResults(results, query);
        mEmptyView.setVisibility(
                results.isEmpty() && !query.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** Runs on the search thread. */
    private static List<Result> search(final String query) {
        final List<Result> results = new ArrayList<>();
        final String[] args = { "%" + escapeLike(query) + "%" };
        try {
            readResults(CONVERSATION_QUERY, args, results);
            readResults(MESSAGE_QUERY, args, results);
        } catch (final RuntimeException e) {
            // Leave whatever was found; a failed search must not take the app down.
        }
        return results;
    }

    private static void readResults(final String sql, final String[] args,
            final List<Result> results) {
        try (Cursor cursor = DataModel.get().getDatabase().rawQuery(sql, args)) {
            while (cursor != null && cursor.moveToNext()) {
                results.add(new Result(cursor.getString(0), cursor.getString(1),
                        cursor.getString(2), cursor.getLong(3)));
            }
        }
    }

    private static String escapeLike(final String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static final class Result {
        final String conversationId;
        final String name;
        final String text;
        final long timestamp;

        Result(final String conversationId, final String name, final String text,
                final long timestamp) {
            this.conversationId = conversationId;
            this.name = name == null ? "" : RcsBusinessSender.displayName(name);
            this.text = text == null ? "" : text;
            this.timestamp = timestamp;
        }
    }

    private final class ResultAdapter extends RecyclerView.Adapter<ResultHolder> {
        private List<Result> mResults = new ArrayList<>();
        private String mQuery = "";

        void setResults(final List<Result> results, final String query) {
            mResults = results;
            mQuery = query;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ResultHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                final int viewType) {
            return new ResultHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.search_result_item_view, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull final ResultHolder holder, final int position) {
            holder.bind(mResults.get(position), mQuery);
        }

        @Override
        public int getItemCount() {
            return mResults.size();
        }
    }

    private final class ResultHolder extends RecyclerView.ViewHolder {
        private final TextView mName;
        private final TextView mTime;
        private final TextView mText;

        ResultHolder(final View itemView) {
            super(itemView);
            mName = itemView.findViewById(R.id.search_result_name);
            mTime = itemView.findViewById(R.id.search_result_time);
            mText = itemView.findViewById(R.id.search_result_text);
        }

        void bind(final Result result, final String query) {
            mName.setText(highlight(result.name, query, false));
            mTime.setText(result.timestamp > 0
                    ? Dates.getConversationTimeString(result.timestamp) : "");
            mText.setText(highlight(result.text, query, true));
            mText.setVisibility(TextUtils.isEmpty(result.text) ? View.GONE : View.VISIBLE);
            itemView.findViewById(R.id.search_result_card).setOnClickListener(v ->
                    UIIntents.get().launchConversationActivity(SearchActivity.this,
                            result.conversationId, null));
        }

        /**
         * Marks the first occurrence of the query. A long text is cut so that the match is
         * still visible in the two lines a result has.
         */
        private CharSequence highlight(final String text, final String query,
                final boolean shorten) {
            String shown = text.replace('\n', ' ');
            int match = shown.toLowerCase(Locale.getDefault())
                    .indexOf(query.toLowerCase(Locale.getDefault()));
            if (match < 0 || match + query.length() > shown.length()) {
                return shown;
            }
            if (shorten && match > SNIPPET_LEAD_CHARS) {
                final int cut = match - SNIPPET_LEAD_CHARS;
                shown = "…" + shown.substring(cut);
                match = match - cut + 1;
            }
            final SpannableString spannable = new SpannableString(shown);
            final int end = match + query.length();
            spannable.setSpan(new ForegroundColorSpan(getColor(R.color.m3_primary)), match, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new StyleSpan(Typeface.BOLD), match, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return spannable;
        }
    }
}
