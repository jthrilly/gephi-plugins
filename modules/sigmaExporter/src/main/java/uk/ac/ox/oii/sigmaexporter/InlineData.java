/*
 Copyright Oxford Internet Institute, 2026
 */
package uk.ac.ox.oii.sigmaexporter;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Embeds config.json and data.json into the exported index.html so the viewer
 * can open from the file system without a web server. The viewer reads
 * {@code <script type="application/json" id="ivis-config">} and
 * {@code id="ivis-data"}.
 */
public final class InlineData {

    public static final String CONFIG_ID = "ivis-config";
    public static final String DATA_ID = "ivis-data";

    private static final Pattern EXISTING = Pattern.compile(
            "[ \\t]*<script type=\"application/json\" id=\"(?:" + CONFIG_ID + "|" + DATA_ID + ")\">.*?</script>\\r?\\n?",
            Pattern.DOTALL);

    private InlineData() {
    }

    /**
     * Makes JSON text safe to place inside a script element: "&lt;/" becomes
     * "&lt;\/" so nothing can close the element early, "&lt;!--" is escaped so it
     * cannot switch the HTML parser into a comment-like state, and U+2028 /
     * U+2029 are written as escapes. All replacements only occur inside JSON
     * strings, where they are valid escapes that JSON.parse turns back into the
     * original characters.
     */
    public static String escapeForScript(String json) {
        StringBuilder out = new StringBuilder(json.length() + 16);
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '<' && i + 1 < json.length() && json.charAt(i + 1) == '/') {
                out.append("<\\/");
                i++;
            } else if (c == '<' && json.startsWith("<!--", i)) {
                out.append("\\u003c");
            } else if (c == ' ') {
                out.append("\\u2028");
            } else if (c == ' ') {
                out.append("\\u2029");
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Returns {@code html} with both JSON blocks inserted just before the first
     * {@code </head>} (case-insensitive). Blocks from an earlier injection are
     * removed first. If there is no {@code </head>}, they go before {@code <body}
     * or, failing that, at the start of the document.
     */
    public static String inject(String html, String configJson, String dataJson) {
        String cleaned = EXISTING.matcher(html).replaceAll("");
        String block = scriptTag(CONFIG_ID, configJson) + scriptTag(DATA_ID, dataJson);
        String lower = cleaned.toLowerCase(Locale.ROOT);
        int at = lower.indexOf("</head>");
        if (at < 0) {
            at = lower.indexOf("<body");
        }
        if (at < 0) {
            at = 0;
        }
        return cleaned.substring(0, at) + block + cleaned.substring(at);
    }

    private static String scriptTag(String id, String json) {
        return "<script type=\"application/json\" id=\"" + id + "\">"
                + escapeForScript(json)
                + "</script>\n";
    }
}
