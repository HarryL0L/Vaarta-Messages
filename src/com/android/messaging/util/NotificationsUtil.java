/*
 * Copyright (C) 2019-2025 The LineageOS Project
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

package com.android.messaging.util;

import android.content.pm.PackageManager;
import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;

public final class NotificationsUtil {
    public static final String DEFAULT_CHANNEL_ID = "messaging_channel";
    public static final String CONVERSATION_GROUP_NAME = "conversation_group";

    private static final String SETUP_PREFS = "setup";
    private static final String PREF_ASKED_FOR_NOTIFICATIONS = "asked_for_notifications";

    private NotificationsUtil() {
    }

    /** Whether the app may post notifications. */
    public static boolean canPostNotifications(Context context) {
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Whether the user still has to be asked for permission to show notifications. As an
     * ordinary installed app, unlike a preinstalled one, this app starts without it, and
     * a messaging app that cannot notify is of little use. It is asked only once.
     */
    public static boolean shouldAskForNotifications(Context context) {
        return !canPostNotifications(context)
                && !context.getSharedPreferences(SETUP_PREFS, Context.MODE_PRIVATE)
                        .getBoolean(PREF_ASKED_FOR_NOTIFICATIONS, false);
    }

    public static void setAskedForNotifications(Context context) {
        context.getSharedPreferences(SETUP_PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(PREF_ASKED_FOR_NOTIFICATIONS, true).apply();
    }

    public static void createNotificationChannel(Context context, String id,
            int titleResId, int priority, String groupId) {
        String title = context.getString(titleResId);
        createNotificationChannel(context, id, title, priority, groupId);
    }

    public static void createNotificationChannel(Context context, String id,
            String title, int priority, String groupId) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel existing = manager.getNotificationChannel(id);
        if (existing != null) {
            // The channel is named after the conversation; keep that name up to date.
            if (title != null && !title.contentEquals(String.valueOf(existing.getName()))) {
                existing.setName(title);
                manager.createNotificationChannel(existing);
            }
            return;
        }

        NotificationChannel newChannel = new NotificationChannel(id, title, priority);
        newChannel.enableLights(true);
        if (groupId != null) {
            newChannel.setGroup(groupId);
        }
        manager.createNotificationChannel(newChannel);
    }

    public static void deleteNotificationChannel(Context context, String id) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.deleteNotificationChannel(id);
    }

    public static void createNotificationChannelGroup(Context context, String id,
            int titleResId) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannelGroup existing = manager.getNotificationChannelGroup(id);
        if (existing != null) {
            return;
        }

        String title = context.getString(titleResId);
        NotificationChannelGroup newChannelGroup = new NotificationChannelGroup(id, title);
        manager.createNotificationChannelGroup(newChannelGroup);
    }

    public static NotificationChannel getNotificationChannel(Context context, String id) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        return manager.getNotificationChannel(id);
    }
}
