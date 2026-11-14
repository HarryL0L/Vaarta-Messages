/*
 * Copyright (C) 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.messaging.util;

import android.text.TextUtils;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Makes the senders of RCS business messages readable.
 *
 * This app does not speak RCS, but an RCS client on the same phone may leave copies of
 * business messages in the shared SMS/MMS store. Their sender is the business's agent ID,
 * for example {@code union_bank_of_india_vusd3moo_agent@rbm.goog}, written in base32
 * ({@code ovxgs33ol5rgc3tll5xwmx3jnzsgsyk7oz2xgzbtnvxw6x3...}). This turns such a sender
 * back into a name like "Union Bank Of India".
 */
public final class RcsBusinessSender {
    private static final String AGENT_DOMAIN = "@rbm.goog";
    private static final String AGENT_SUFFIX = "_agent";
    // Shortest base32 text that could hold "x_agent@rbm.goog"
    private static final int MIN_ENCODED_LENGTH = 26;
    private static final int RANDOM_TOKEN_LENGTH = 8;

    private RcsBusinessSender() {
    }

    /**
     * Returns a readable name if {@code sender} is an encoded RCS business agent ID, and
     * {@code sender} itself otherwise.
     */
    public static String displayName(final String sender) {
        final String name = decodeName(sender);
        return name != null ? name : sender;
    }

    /** Returns the business name, or null if {@code sender} is not an encoded agent ID. */
    public static String decodeName(final String sender) {
        if (sender == null || sender.length() < MIN_ENCODED_LENGTH) {
            return null;
        }
        // Tolerate upper case and "=" padding
        final String agentId = decodeBase32(
                sender.toLowerCase(Locale.ROOT).replace("=", ""));
        if (agentId == null || !agentId.endsWith(AGENT_DOMAIN)) {
            return null;
        }
        String name = agentId.substring(0, agentId.length() - AGENT_DOMAIN.length());
        if (name.endsWith(AGENT_SUFFIX)) {
            name = name.substring(0, name.length() - AGENT_SUFFIX.length());
        }
        // Drop the random token that makes the ID unique: "..._vusd3moo"
        final int lastSeparator = name.lastIndexOf('_');
        if (lastSeparator > 0 && name.length() - lastSeparator - 1 == RANDOM_TOKEN_LENGTH) {
            name = name.substring(0, lastSeparator);
        }
        if (TextUtils.isEmpty(name)) {
            return null;
        }

        final StringBuilder pretty = new StringBuilder(name.length());
        for (final String word : name.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (pretty.length() > 0) {
                pretty.append(' ');
            }
            pretty.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return pretty.length() > 0 ? pretty.toString() : null;
    }

    /**
     * Decodes lower-case, unpadded base32. Returns null unless the input is valid base32
     * that decodes to plain printable ASCII.
     */
    private static String decodeBase32(final String encoded) {
        final byte[] decoded = new byte[encoded.length() * 5 / 8];
        int buffer = 0;
        int bits = 0;
        int count = 0;
        for (int i = 0; i < encoded.length(); i++) {
            final char c = encoded.charAt(i);
            final int value;
            if (c >= 'a' && c <= 'z') {
                value = c - 'a';
            } else if (c >= '2' && c <= '7') {
                value = c - '2' + 26;
            } else {
                return null;
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                final int b = (buffer >> bits) & 0xFF;
                if (b < 0x20 || b > 0x7E) {
                    return null;
                }
                decoded[count++] = (byte) b;
            }
        }
        return new String(decoded, 0, count, StandardCharsets.US_ASCII);
    }
}
