package uk.ac.ox.oii.sigmaexporter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class InlineDataTest {

    private static final String HTML = "<!doctype html>\n<html>\n<head>\n<title>t</title>\n</head>\n<body></body>\n</html>\n";

    @Test
    void escapesClosingTagsCommentsAndLineSeparators() {
        String json = "{\"label\":\"</script><!--x\u2028y\u2029z\"}";
        String escaped = InlineData.escapeForScript(json);
        assertFalse(escaped.contains("</"));
        assertFalse(escaped.contains("<!--"));
        assertFalse(escaped.contains("\u2028"));
        assertFalse(escaped.contains("\u2029"));
        assertEquals("{\"label\":\"<\\/script>\\u003c!--x\\u2028y\\u2029z\"}", escaped);
        // Still the same JSON value once parsed
        assertEquals(JsonParser.parseString(json), JsonParser.parseString(escaped));
    }

    @Test
    void injectsBothBlocksJustBeforeHeadClose() {
        String out = InlineData.inject(HTML, "{\"a\":1}", "{\"nodes\":[],\"edges\":[]}");
        String expected = "<!doctype html>\n<html>\n<head>\n<title>t</title>\n"
                + "<script type=\"application/json\" id=\"ivis-config\">{\"a\":1}</script>\n"
                + "<script type=\"application/json\" id=\"ivis-data\">{\"nodes\":[],\"edges\":[]}</script>\n"
                + "</head>\n<body></body>\n</html>\n";
        assertEquals(expected, out);
    }

    @Test
    void injectionIsIdempotentAndCaseInsensitive() {
        String upper = HTML.replace("</head>", "</HEAD>");
        String once = InlineData.inject(upper, "{\"a\":1}", "{}");
        String twice = InlineData.inject(once, "{\"a\":2}", "{}");
        assertEquals(1, count(twice, "id=\"ivis-config\""));
        assertEquals(1, count(twice, "id=\"ivis-data\""));
        assertTrue(twice.contains("{\"a\":2}"));
        assertTrue(twice.indexOf("ivis-data") < twice.indexOf("</HEAD>"));
    }

    @Test
    void hostileValuesCannotCloseTheScriptEarly() {
        String data = "{\"label\":\"</script><script>alert(1)</script>\"}";
        String out = InlineData.inject(HTML, "{}", data);
        // Only the two closing tags we wrote, plus none from the data
        assertEquals(2, count(out, "</script>"));
    }

    private static int count(String s, String sub) {
        int n = 0;
        for (int i = s.indexOf(sub); i >= 0; i = s.indexOf(sub, i + 1)) {
            n++;
        }
        return n;
    }
}
