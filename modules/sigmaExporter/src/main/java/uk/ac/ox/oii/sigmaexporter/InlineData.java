/*
 Copyright Oxford Internet Institute, 2026
 */
package uk.ac.ox.oii.sigmaexporter;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
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

    /** Writes a JSON document to a writer, so large data never has to be held as one string. */
    public interface JsonSource {
        void writeTo(Writer out) throws IOException;
    }

    /**
     * Makes JSON text safe to place inside a script element: "&lt;/" becomes
     * "&lt;\/" so nothing can close the element early, "&lt;!" is escaped so
     * "&lt;!--" cannot switch the HTML parser into a comment-like state, and
     * U+2028 / U+2029 are written as escapes. All replacements only occur
     * inside JSON strings, where they are valid escapes that JSON.parse turns
     * back into the original characters.
     */
    public static String escapeForScript(String json) {
        StringWriter out = new StringWriter(json.length() + 16);
        try (Writer w = new ScriptSafeWriter(out)) {
            w.write(json);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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
        StringWriter out = new StringWriter(html.length() + configJson.length() + dataJson.length() + 128);
        try {
            inject(html, configJson, w -> w.write(dataJson), out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString();
    }

    /** Streaming form of {@link #inject(String, String, String)}: the data is written straight to {@code out}. */
    public static void inject(String html, String configJson, JsonSource data, Writer out) throws IOException {
        String cleaned = EXISTING.matcher(html).replaceAll("");
        String lower = cleaned.toLowerCase(Locale.ROOT);
        int at = lower.indexOf("</head>");
        if (at < 0) {
            at = lower.indexOf("<body");
        }
        if (at < 0) {
            at = 0;
        }
        out.write(cleaned, 0, at);
        scriptTag(CONFIG_ID, w -> w.write(configJson), out);
        scriptTag(DATA_ID, data, out);
        out.write(cleaned, at, cleaned.length() - at);
        out.flush();
    }

    private static void scriptTag(String id, JsonSource json, Writer out) throws IOException {
        out.write("<script type=\"application/json\" id=\"" + id + "\">");
        ScriptSafeWriter safe = new ScriptSafeWriter(out);
        json.writeTo(safe);
        safe.finish();
        out.write("</script>\n");
    }

    /** Applies {@link #escapeForScript} to everything written through it, holding back at most one '<'. */
    private static final class ScriptSafeWriter extends Writer {
        private final Writer out;
        private boolean pendingLt;

        ScriptSafeWriter(Writer out) {
            this.out = out;
        }

        @Override
        public void write(int c) throws IOException {
            if (pendingLt) {
                pendingLt = false;
                if (c == '/') {
                    out.write("<\\/");
                    return;
                }
                out.write(c == '!' ? "\\u003c" : "<");
            }
            if (c == '<') {
                pendingLt = true;
            } else if (c == '\u2028') {
                out.write("\\u2028");
            } else if (c == '\u2029') {
                out.write("\\u2029");
            } else {
                out.write(c);
            }
        }

        @Override
        public void write(char[] buf, int off, int len) throws IOException {
            for (int i = off; i < off + len; i++) {
                write(buf[i]);
            }
        }

        @Override
        public void write(String str, int off, int len) throws IOException {
            for (int i = off; i < off + len; i++) {
                write(str.charAt(i));
            }
        }

        /** Writes out a held-back '<' without closing the underlying writer. */
        void finish() throws IOException {
            if (pendingLt) {
                pendingLt = false;
                out.write('<');
            }
        }

        @Override
        public void flush() throws IOException {
            out.flush();
        }

        @Override
        public void close() throws IOException {
            finish();
            out.flush();
        }
    }
}
