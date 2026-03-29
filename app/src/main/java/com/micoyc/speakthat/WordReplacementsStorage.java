/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat;

import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JSON codec for {@code word_replacements} SharedPreferences, with migration from the legacy
 * {@code from:to|from:to} flat string format.
 */
public final class WordReplacementsStorage {

    private static final String TAG = "WordReplacementsStorage";

    public static final class WordReplacement {
        private final String from;
        private final String to;

        public WordReplacement(String from, String to) {
            this.from = from != null ? from : "";
            this.to = to != null ? to : "";
        }

        public String getFrom() {
            return from;
        }

        public String getTo() {
            return to;
        }
    }

    private WordReplacementsStorage() {
    }

    /**
     * True when non-empty and not a valid word-replacements JSON array (see {@link #parseJson}).
     * Strings that look like JSON arrays but fail parsing are not considered legacy.
     */
    public static boolean isLegacyFormat(String raw) {
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        if (!looksLikeJsonArray(raw)) {
            return true;
        }
        return !isValidWordReplacementsJson(raw);
    }

    private static boolean looksLikeJsonArray(String raw) {
        return raw.trim().startsWith("[");
    }

    /**
     * Returns true if {@code raw} is a JSON array and each element is an object with {@code from}/{@code to} keys (strings).
     */
    public static boolean isValidWordReplacementsJson(String raw) {
        if (raw == null || raw.isEmpty() || !looksLikeJsonArray(raw)) {
            return false;
        }
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                if (!(arr.get(i) instanceof JSONObject)) {
                    return false;
                }
                JSONObject o = arr.getJSONObject(i);
                if (!o.has("from")) {
                    return false;
                }
                // "to" may be absent (treated as "")
            }
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    public static List<WordReplacement> parseLegacy(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<WordReplacement> out = new ArrayList<>();
        String[] pairs = raw.split("\\|");
        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }
            String[] parts = pair.split(":", 2);
            if (parts.length == 2) {
                out.add(new WordReplacement(parts[0], parts[1]));
            }
        }
        return out;
    }

    /**
     * Parses the JSON array format. On failure logs and returns an empty list (does not fall back to legacy).
     */
    public static List<WordReplacement> parseJson(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            JSONArray arr = new JSONArray(raw);
            List<WordReplacement> out = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                Object el = arr.get(i);
                if (!(el instanceof JSONObject)) {
                    Log.w(TAG, "parseJson: skipping non-object at index " + i);
                    continue;
                }
                JSONObject o = (JSONObject) el;
                String from = o.optString("from", "");
                String to = o.optString("to", "");
                if (from.isEmpty()) {
                    continue;
                }
                out.add(new WordReplacement(from, to));
            }
            return out;
        } catch (JSONException e) {
            Log.e(TAG, "parseJson: invalid JSON array", e);
            return Collections.emptyList();
        }
    }

    public static String toJson(List<WordReplacement> items) {
        JSONArray arr = new JSONArray();
        if (items != null) {
            for (WordReplacement w : items) {
                if (w == null || w.getFrom().isEmpty()) {
                    continue;
                }
                try {
                    JSONObject o = new JSONObject();
                    o.put("from", w.getFrom());
                    o.put("to", w.getTo());
                    arr.put(o);
                } catch (JSONException e) {
                    Log.e(TAG, "toJson: failed to serialize entry", e);
                }
            }
        }
        return arr.toString();
    }

    /**
     * Reads prefs, migrates legacy flat string to JSON once, returns ordered list.
     */
    public static List<WordReplacement> loadWithAutoMigrate(SharedPreferences prefs, String key) {
        String raw = prefs.getString(key, "");
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        if (looksLikeJsonArray(raw)) {
            if (isValidWordReplacementsJson(raw)) {
                return parseJson(raw);
            }
            Log.e(TAG, "loadWithAutoMigrate: value looks like JSON but is invalid; leaving prefs unchanged");
            return Collections.emptyList();
        }
        List<WordReplacement> list = parseLegacy(raw);
        String json = toJson(list);
        prefs.edit().putString(key, json).apply();
        Log.i(TAG, "Migrated word_replacements from legacy format to JSON (" + list.size() + " swaps)");
        return list;
    }

    /**
     * Normalizes an imported string: legacy pipe/colon format becomes JSON; valid JSON array is re-serialized canonically.
     * Invalid JSON that starts with '[' is returned unchanged.
     */
    public static String normalizeImportedValue(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        if (looksLikeJsonArray(raw)) {
            if (isValidWordReplacementsJson(raw)) {
                return toJson(parseJson(raw));
            }
            Log.w(TAG, "normalizeImportedValue: invalid JSON array, storing value unchanged");
            return raw;
        }
        return toJson(parseLegacy(raw));
    }

    /**
     * Counts swaps for summaries without writing prefs.
     */
    public static int countSwaps(String raw) {
        if (raw == null || raw.isEmpty()) {
            return 0;
        }
        if (looksLikeJsonArray(raw) && isValidWordReplacementsJson(raw)) {
            try {
                JSONArray arr = new JSONArray(raw);
                int n = 0;
                for (int i = 0; i < arr.length(); i++) {
                    if (arr.get(i) instanceof JSONObject) {
                        JSONObject o = arr.getJSONObject(i);
                        if (!o.optString("from", "").isEmpty()) {
                            n++;
                        }
                    }
                }
                return n;
            } catch (JSONException e) {
                return 0;
            }
        }
        return parseLegacy(raw).size();
    }
}
