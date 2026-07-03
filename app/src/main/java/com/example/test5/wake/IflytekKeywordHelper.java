package com.example.test5.wake;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import com.example.test5.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 从 assets/aikit_resources/ivw/keyword1.txt 或 string-array 读取唤醒词。 */
public final class IflytekKeywordHelper {

    private static final String TAG = "IflytekKeyword";
    private static final String KEYWORD_ASSET = "aikit_resources/ivw/keyword1.txt";

    private IflytekKeywordHelper() {
    }

    public static String[] loadKeywords(Context context) {
        String[] fromAsset = loadFromAsset(context.getAssets());
        if (fromAsset.length > 0) {
            return fromAsset;
        }
        return context.getResources().getStringArray(R.array.wake_keywords);
    }

    private static String[] loadFromAsset(AssetManager assets) {
        List<String> keywords = new ArrayList<>();
        try (InputStream in = assets.open(KEYWORD_ASSET);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim();
                if (word.endsWith(";")) {
                    word = word.substring(0, word.length() - 1).trim();
                }
                if (!word.isEmpty()) {
                    keywords.add(word);
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "读取 " + KEYWORD_ASSET + " 失败，使用默认 wake_keywords", e);
        }
        return keywords.toArray(new String[0]);
    }
}
