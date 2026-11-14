/*
 * Copyright (C) 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.messaging.util;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Makes the senders of RCS business messages readable.
 *
 * This app does not speak RCS, but an RCS client on the same phone may leave copies of
 * business messages in the shared SMS/MMS store. Their sender looks like
 * {@code ovxgs33ol5rgc3tll5xw...@bot.rcs.google.com}. The part before the "@" is base32 for
 * three fields joined by "_;_": the business's agent ID, its display name and its brand
 * colour, for example
 * {@code union_bank_of_india_vusd3moo_agent__AT__rbm.goog_;_Union Bank Of India_;_#DA251C}.
 * This class turns such a sender back into the display name.
 */
public final class RcsBusinessSender {
    private static final String FIELD_SEPARATOR = "_;_";
    private static final String ENCODED_AT = "__AT__";
    private static final String AGENT_SUFFIX = "_agent";
    // Long enough that ordinary sender names and numbers are never considered
    private static final int MIN_ENCODED_LENGTH = 24;
    private static final int RANDOM_TOKEN_LENGTH = 8;
    // name_with_underscores, optionally followed by all or part of "@domain"
    private static final Pattern AGENT_ID_PATTERN =
            Pattern.compile("[a-z][a-z0-9]*(_[a-z0-9]*)+(@[a-z0-9.-]*)?");

    private RcsBusinessSender() {
    }

    /**
     * Returns a readable name if {@code sender} is an encoded RCS business sender, and
     * {@code sender} itself otherwise.
     */
    public static String displayName(final String sender) {
        final String name = decodeName(sender);
        return name != null ? name : sender;
    }

    /** Returns the business name, or null if {@code sender} is not an encoded sender. */
    public static String decodeName(final String sender) {
        if (sender == null || sender.length() < MIN_ENCODED_LENGTH) {
            return null;
        }
        // Only the leading run of base32 characters is encoded; "@bot.rcs.google.com" or
        // padding may follow it.
        final String lower = sender.trim().toLowerCase(Locale.ROOT);
        int encodedLength = 0;
        while (encodedLength < lower.length() && isBase32(lower.charAt(encodedLength))) {
            encodedLength++;
        }
        if (encodedLength < MIN_ENCODED_LENGTH) {
            return null;
        }
        final String decoded = decodeBase32(lower.substring(0, encodedLength));
        if (decoded == null) {
            return null;
        }

        final String[] fields = decoded.split(FIELD_SEPARATOR, -1);
        final String agentId = fields[0].replace(ENCODED_AT, "@");
        // Random text almost never decodes to readable text shaped like an agent ID, so
        // this check is what tells an encoded sender from an ordinary one.
        if (!AGENT_ID_PATTERN.matcher(agentId).matches()) {
            return null;
        }
        if (fields.length > 1 && !fields[1].trim().isEmpty()) {
            // The business's own display name
            return fields[1].trim();
        }
        return nameFromAgentId(agentId);
    }

    /** Rebuilds a name from an agent ID when the sender carries no display name. */
    private static String nameFromAgentId(final String agentId) {
        String name = agentId;
        final int at = name.indexOf('@');
        if (at >= 0) {
            name = name.substring(0, at);
        }
        if (name.endsWith(AGENT_SUFFIX)) {
            name = name.substring(0, name.length() - AGENT_SUFFIX.length());
        } else {
            // A cut-off ID may end in part of the word "agent"
            final int lastSeparator = name.lastIndexOf('_');
            if (lastSeparator >= 0
                    && AGENT_SUFFIX.startsWith(name.substring(lastSeparator))) {
                name = name.substring(0, lastSeparator);
            }
        }
        // Drop the random token that makes the ID unique: "..._vusd3moo"
        final int lastSeparator = name.lastIndexOf('_');
        if (lastSeparator > 0) {
            final String last = name.substring(lastSeparator + 1);
            if (last.length() <= RANDOM_TOKEN_LENGTH && last.matches(".*[0-9].*")) {
                name = name.substring(0, lastSeparator);
            }
        }

        final StringBuilder pretty = new StringBuilder(name.length());
        for (final String word : name.split("[_.-]+")) {
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

    private static boolean isBase32(final char c) {
        return (c >= 'a' && c <= 'z') || (c >= '2' && c <= '7');
    }

    /**
     * Decodes lower-case, unpadded base32. Returns null unless the result is readable
     * text: valid UTF-8 without control characters.
     */
    private static String decodeBase32(final String encoded) {
        final byte[] bytes = new byte[encoded.length() * 5 / 8];
        int buffer = 0;
        int bits = 0;
        int count = 0;
        for (int i = 0; i < encoded.length(); i++) {
            final char c = encoded.charAt(i);
            final int value = c >= 'a' ? c - 'a' : c - '2' + 26;
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                final int b = (buffer >> bits) & 0xFF;
                if (b < 0x20 || b == 0x7F) {
                    return null;
                }
                bytes[count++] = (byte) b;
            }
        }
        final String text = new String(bytes, 0, count, StandardCharsets.UTF_8);
        // Invalid UTF-8 decodes to the replacement character
        return text.indexOf('�') >= 0 ? null : text;
    }
}
