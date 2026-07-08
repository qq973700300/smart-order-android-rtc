package com.example.test5.aigc;

import java.util.ArrayList;
import java.util.List;

/**
 * 字幕流式合并（对齐 Web setHistoryMsg，SubtitleMode=0 / 非数字人场景）。
 */
public final class SubtitleTracker {

    private static final int MAX_LINES = 12;

    private static final class Line {
        String userId;
        String text;
        boolean definite;
        boolean paragraph;
        boolean fromBot;
    }

    private final List<Line> lines = new ArrayList<>();
    private String botUserId = "";
    private String botLabel = "店员小哇";
    private String userLabel = "我";

    public void setBotUserId(String botUserId) {
        this.botUserId = botUserId != null ? botUserId : "";
    }

    public void clear() {
        lines.clear();
    }

    public void update(String userId, String text, boolean definite, boolean paragraph, boolean fromBot) {
        boolean startNew = lines.isEmpty();
        if (!lines.isEmpty()) {
            Line last = lines.get(lines.size() - 1);
            if (!userId.equals(last.userId)) {
                startNew = true;
            } else if (fromBot) {
                // SubtitleMode=0：AI 一句结束看 definite
                startNew = last.definite;
            } else {
                startNew = last.paragraph;
            }
        }

        if (startNew) {
            if (text == null || text.isEmpty()) {
                return;
            }
            Line line = new Line();
            line.userId = userId;
            line.text = text;
            line.definite = definite;
            line.paragraph = paragraph;
            line.fromBot = fromBot;
            lines.add(line);
        } else {
            Line last = lines.get(lines.size() - 1);
            if (text != null && !text.isEmpty()) {
                last.text = text;
            }
            last.definite = definite;
            last.paragraph = paragraph;
            last.fromBot = fromBot;
        }

        while (lines.size() > MAX_LINES) {
            lines.remove(0);
        }
    }

    public static final class DualLatest {
        public final String userText;
        public final String botText;

        public DualLatest(String userText, String botText) {
            this.userText = userText != null ? userText : "";
            this.botText = botText != null ? botText : "";
        }
    }

    /** 分别取最近一句用户话、最近一句 AI 话，可同时展示。 */
    public DualLatest renderDualLatest() {
        String user = "";
        String bot = "";
        for (Line line : lines) {
            if (line.text == null || line.text.isEmpty()) {
                continue;
            }
            if (isBotLine(line)) {
                bot = line.text;
            } else {
                user = line.text;
            }
        }
        return new DualLatest(user, bot);
    }

    public String renderLatest() {
        if (lines.isEmpty()) {
            return "";
        }
        Line line = lines.get(lines.size() - 1);
        return formatSpeaker(line) + "：" + line.text;
    }

    public String render() {
        StringBuilder sb = new StringBuilder();
        for (Line line : lines) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(formatSpeaker(line)).append(": ").append(line.text);
        }
        return sb.toString();
    }

    private boolean isBotLine(Line line) {
        return line.fromBot || (!botUserId.isEmpty() && botUserId.equals(line.userId))
                || line.userId.contains("voiceChat_");
    }

    private String formatSpeaker(Line line) {
        if (isBotLine(line)) {
            return botLabel;
        }
        return userLabel;
    }
}
