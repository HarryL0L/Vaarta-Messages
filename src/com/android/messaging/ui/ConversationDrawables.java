/*
 * Copyright (C) 2015 The Android Open Source Project
 * Copyright (C) 2024 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.messaging.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

import androidx.core.content.res.ResourcesCompat;

import com.android.messaging.Factory;
import com.android.messaging.R;
import com.android.messaging.util.ImageUtils;
import com.android.messaging.util.UiUtils;

/**
 * A singleton cache that holds tinted drawable resources for displaying messages, such as
 * message bubbles, audio attachments etc.
 */
public class ConversationDrawables {
    private static ConversationDrawables sInstance;

    // Cache the color filtered bubble drawables so that we don't need to create a
    // new one for each ConversationMessageView.
    private Drawable mAudioPlayButtonDrawable;
    private Drawable mAudioPauseButtonDrawable;
    private Drawable mIncomingAudioProgressBackgroundDrawable;
    private Drawable mOutgoingAudioProgressBackgroundDrawable;
    private Drawable mAudioProgressForegroundDrawable;
    private Drawable mFastScrollThumbDrawable;
    private Drawable mFastScrollThumbPressedDrawable;
    private Drawable mFastScrollPreviewDrawableLeft;
    private Drawable mFastScrollPreviewDrawableRight;
    private final Context mContext;
    private int mIncomingBubbleColor;
    private int mOutgoingBubbleColor;
    private float mBubbleCornerRadius;
    private float mBubbleCornerRadiusGrouped;
    private int mIncomingErrorBubbleColor;
    private int mIncomingAudioButtonColor;
    private int mSelectedBubbleColor;
    private int mThemeColor;

    public static ConversationDrawables get() {
        if (sInstance == null) {
            sInstance = new ConversationDrawables(Factory.get().getApplicationContext());
        }
        return sInstance;
    }

    private ConversationDrawables(final Context context) {
        mContext = context;
        // Pre-create all the drawables.
        updateDrawables();
    }

    public int getConversationThemeColor() {
        return mThemeColor;
    }

    public void updateDrawables() {
        final Resources resources = mContext.getResources();
        final Resources.Theme theme = mContext.getTheme();

        mAudioPlayButtonDrawable = ResourcesCompat.getDrawable(resources,
                R.drawable.ic_play_light, theme);
        mAudioPauseButtonDrawable = ResourcesCompat.getDrawable(resources,
                R.drawable.ic_pause_light, theme);
        mIncomingAudioProgressBackgroundDrawable = ResourcesCompat.getDrawable(resources,
                R.drawable.audio_progress_bar_background_incoming, theme);
        mOutgoingAudioProgressBackgroundDrawable = ResourcesCompat.getDrawable(resources,
                R.drawable.audio_progress_bar_background_outgoing, theme);
        mAudioProgressForegroundDrawable = ResourcesCompat.getDrawable(resources,
                R.drawable.audio_progress_bar_progress, theme);
        mFastScrollThumbDrawable = ResourcesCompat.getDrawable(resources,
                R.drawable.fastscroll_thumb, theme);
        mFastScrollThumbPressedDrawable = ResourcesCompat.getDrawable(resources,
                R.drawable.fastscroll_thumb_pressed, theme);
        mFastScrollPreviewDrawableLeft = ResourcesCompat.getDrawable(resources,
                R.drawable.fastscroll_preview_left, theme);
        mFastScrollPreviewDrawableRight = ResourcesCompat.getDrawable(resources,
                R.drawable.fastscroll_preview_right, theme);
        mIncomingBubbleColor = resources.getColor(R.color.message_bubble_color_incoming, theme);
        mOutgoingBubbleColor = resources.getColor(R.color.message_bubble_color_outgoing, theme);
        mBubbleCornerRadius = resources.getDimension(R.dimen.message_bubble_corner_radius);
        mBubbleCornerRadiusGrouped =
                resources.getDimension(R.dimen.message_bubble_corner_radius_grouped);
        mIncomingErrorBubbleColor =
                resources.getColor(R.color.message_error_bubble_color_incoming, theme);
        mIncomingAudioButtonColor =
                resources.getColor(R.color.message_audio_button_color_incoming, theme);
        mSelectedBubbleColor = resources.getColor(R.color.message_bubble_color_selected, theme);
        mThemeColor = resources.getColor(R.color.primary_color, theme);
    }

    public Drawable getBubbleDrawable(final boolean selected, final boolean incoming,
                                      final boolean isError, final String identifier) {
        return getBubbleDrawable(selected, incoming, isError, false, false);
    }

    /**
     * Builds a message bubble. Bubbles that belong to a run of messages from the same sender
     * get tighter corners on the sender's side where they meet their neighbours, so the run
     * reads as one group.
     *
     * @param clusterWithPrevious the message above is part of the same run
     * @param clusterWithNext the message below is part of the same run
     */
    public Drawable getBubbleDrawable(final boolean selected, final boolean incoming,
            final boolean isError, final boolean clusterWithPrevious,
            final boolean clusterWithNext) {
        final int color;
        if (selected) {
            color = mSelectedBubbleColor;
        } else if (incoming) {
            color = isError ? mIncomingErrorBubbleColor : mIncomingBubbleColor;
        } else {
            color = mOutgoingBubbleColor;
        }

        final float top = clusterWithPrevious ? mBubbleCornerRadiusGrouped : mBubbleCornerRadius;
        final float bottom = clusterWithNext ? mBubbleCornerRadiusGrouped : mBubbleCornerRadius;
        final float r = mBubbleCornerRadius;
        // The sender's side is the start edge for incoming messages and the end edge for
        // outgoing ones; flip it for right-to-left layouts.
        final boolean senderOnLeft = incoming != UiUtils.isRtlMode();

        final GradientDrawable bubble = new GradientDrawable();
        bubble.setShape(GradientDrawable.RECTANGLE);
        bubble.setColor(color);
        // Radii are ordered top-left, top-right, bottom-right, bottom-left.
        if (senderOnLeft) {
            bubble.setCornerRadii(new float[] { top, top, r, r, r, r, bottom, bottom });
        } else {
            bubble.setCornerRadii(new float[] { r, r, top, top, bottom, bottom, r, r });
        }
        return bubble;
    }

    private int getAudioButtonColor(final boolean incoming) {
        return incoming ? mIncomingAudioButtonColor : mThemeColor;
    }

    public Drawable getPlayButtonDrawable(final boolean incoming) {
        return ImageUtils.getTintedDrawable(
                mContext, mAudioPlayButtonDrawable, getAudioButtonColor(incoming));
    }

    public Drawable getPauseButtonDrawable(final boolean incoming) {
        return ImageUtils.getTintedDrawable(
                mContext, mAudioPauseButtonDrawable, getAudioButtonColor(incoming));
    }

    public Drawable getAudioProgressDrawable(final boolean incoming) {
        return ImageUtils.getTintedDrawable(
                mContext, mAudioProgressForegroundDrawable, getAudioButtonColor(incoming));
    }

    public Drawable getAudioProgressBackgroundDrawable(final boolean incoming) {
        return incoming ? mIncomingAudioProgressBackgroundDrawable :
            mOutgoingAudioProgressBackgroundDrawable;
    }

    public Drawable getFastScrollThumbDrawable(final boolean pressed) {
        if (pressed) {
            return ImageUtils.getTintedDrawable(mContext, mFastScrollThumbPressedDrawable,
                    mThemeColor);
        } else {
            return mFastScrollThumbDrawable;
        }
    }

    public Drawable getFastScrollPreviewDrawable(boolean positionRight) {
        Drawable protoDrawable = positionRight ? mFastScrollPreviewDrawableRight :
            mFastScrollPreviewDrawableLeft;
        return ImageUtils.getTintedDrawable(mContext, protoDrawable, mThemeColor);
    }
}
