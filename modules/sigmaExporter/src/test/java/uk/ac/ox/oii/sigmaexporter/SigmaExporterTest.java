package uk.ac.ox.oii.sigmaexporter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gephi.graph.api.Edge;
import org.gephi.graph.api.GraphFactory;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.ac.ox.oii.sigmaexporter.model.ConfigFile;

class SigmaExporterTest {

    @TempDir
    Path tmp;

    private static GraphModel sampleGraph() {
        GraphModel model = GraphModel.Factory.newInstance();
        GraphFactory f = model.factory();
        Graph g = model.getGraph();
        Node a = f.newNode("a");
        a.setLabel("A </script> node");
        Node b = f.newNode("b");
        b.setLabel("B");
        Node c = f.newNode("c");
        c.setLabel("C");
        g.addNode(a);
        g.addNode(b);
        g.addNode(c);
        Edge directed = f.newEdge("ab", a, b, 0, 1.0, true);
        Edge undirected = f.newEdge("bc", b, c, 0, 2.0, false);
        g.addEdge(directed);
        g.addEdge(undirected);
        return model;
    }

    private SigmaExporter exporter(ConfigFile cfg, boolean inline) {
        SigmaExporter ex = new SigmaExporter();
        ex.setConfigFile(cfg, tmp.toString(), false);
        ex.setInlineData(inline);
        return ex;
    }

    private static JsonObject read(Path p) throws IOException {
        return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
    }

    @Test
    void writesDirectedFlagPerEdge() throws IOException {
        assertTrue(exporter(new ConfigFile(), false).exportTo(tmp.toFile(), sampleGraph()));
        JsonArray edges = read(tmp.resolve("network/data.json")).getAsJsonArray("edges");
        assertEquals(2, edges.size());
        Map<String, Boolean> directedById = new HashMap<>();
        for (JsonElement e : edges) {
            JsonObject o = e.getAsJsonObject();
            directedById.put(o.get("id").getAsString(), o.get("directed").getAsBoolean());
        }
        assertEquals(Boolean.TRUE, directedById.get("ab"));
        assertEquals(Boolean.FALSE, directedById.get("bc"));
    }

    @Test
    void defaultsKeepCurveAndFulltextOff() throws IOException {
        exporter(new ConfigFile(), false).exportTo(tmp.toFile(), sampleGraph());
        JsonObject cfg = read(tmp.resolve("network/config.json"));
        assertEquals("curve", cfg.getAsJsonObject("sigma").getAsJsonObject("drawingProperties").get("defaultEdgeType").getAsString());
        assertFalse(cfg.getAsJsonObject("search").get("fulltext").getAsBoolean());
        // Inline data off: index.html untouched
        String html = Files.readString(tmp.resolve("network/index.html"));
        assertFalse(html.contains("ivis-config"));
        assertFalse(html.contains("ivis-data"));
    }

    @Test
    void writesEdgeStyleAndFulltext() throws IOException {
        ConfigFile cfg = new ConfigFile();
        cfg.setDefaultEdgeType("curvedArrow");
        cfg.setSearchFulltext(true);
        exporter(cfg, false).exportTo(tmp.toFile(), sampleGraph());
        JsonObject out = read(tmp.resolve("network/config.json"));
        assertEquals("curvedArrow", out.getAsJsonObject("sigma").getAsJsonObject("drawingProperties").get("defaultEdgeType").getAsString());
        assertTrue(out.getAsJsonObject("search").get("fulltext").getAsBoolean());
        // Existing keys are still there
        assertEquals("network", out.get("type").getAsString());
        assertEquals("data.json", out.get("data").getAsString());
        assertTrue(out.getAsJsonObject("features").has("hoverBehavior"));
    }

    @Test
    void unknownEdgeStyleFallsBackToCurve() {
        ConfigFile cfg = new ConfigFile();
        cfg.setDefaultEdgeType("zigzag");
        assertEquals("curve", cfg.getDefaultEdgeType());
        for (String t : ConfigFile.EDGE_TYPES) {
            cfg.setDefaultEdgeType(t);
            assertEquals(t, cfg.getDefaultEdgeType());
        }
    }

    @Test
    void readsNewOptionsFromPreferences() {
        InMemoryPreferences prefs = new InMemoryPreferences();
        ConfigFile cfg = new ConfigFile();
        cfg.readFromPrefs(prefs);
        assertEquals("curve", cfg.getDefaultEdgeType());
        assertFalse(cfg.isSearchFulltext());

        prefs.put(ConfigFile.PREF_EDGE_TYPE, "arrow");
        prefs.put(ConfigFile.PREF_SEARCH_FULLTEXT, "true");
        cfg.readFromPrefs(prefs);
        assertEquals("arrow", cfg.getDefaultEdgeType());
        assertTrue(cfg.isSearchFulltext());
    }

    @Test
    void inlineDataEmbedsBothFilesBeforeHeadClose() throws IOException {
        ConfigFile cfg = new ConfigFile();
        cfg.setDefaultEdgeType("arrow");
        exporter(cfg, true).exportTo(tmp.toFile(), sampleGraph());
        Path net = tmp.resolve("network");
        assertTrue(Files.isRegularFile(net.resolve("config.json")));
        assertTrue(Files.isRegularFile(net.resolve("data.json")));

        String html = Files.readString(net.resolve("index.html"));
        String config = embedded(html, "ivis-config");
        String data = embedded(html, "ivis-data");
        assertEquals(read(net.resolve("config.json")), JsonParser.parseString(config));
        assertEquals(read(net.resolve("data.json")), JsonParser.parseString(data));
        assertFalse(config.contains("</"));
        assertFalse(data.contains("</"));

        int head = html.toLowerCase().indexOf("</head>");
        assertTrue(html.indexOf("id=\"ivis-config\"") < head);
        assertTrue(html.indexOf("id=\"ivis-data\"") < head);
        // The node label containing </script> survives the round trip
        boolean found = false;
        for (JsonElement n : JsonParser.parseString(data).getAsJsonObject().getAsJsonArray("nodes")) {
            found |= "A </script> node".equals(n.getAsJsonObject().get("label").getAsString());
        }
        assertTrue(found);
    }

    private static String embedded(String html, String id) {
        Matcher m = Pattern.compile("<script type=\"application/json\" id=\"" + id + "\">(.*?)</script>", Pattern.DOTALL).matcher(html);
        assertTrue(m.find(), "missing " + id);
        String json = m.group(1);
        assertFalse(m.find(), "duplicate " + id);
        return json;
    }

    /** Minimal Preferences backed by a map, so tests don't touch the user's preference store. */
    static class InMemoryPreferences extends java.util.prefs.AbstractPreferences {
        private final Map<String, String> values = new HashMap<>();

        InMemoryPreferences() {
            super(null, "");
        }

        @Override protected void putSpi(String key, String value) { values.put(key, value); }
        @Override protected String getSpi(String key) { return values.get(key); }
        @Override protected void removeSpi(String key) { values.remove(key); }
        @Override protected void removeNodeSpi() { }
        @Override protected String[] keysSpi() { return values.keySet().toArray(new String[0]); }
        @Override protected String[] childrenNamesSpi() { return new String[0]; }
        @Override protected java.util.prefs.AbstractPreferences childSpi(String name) { return new InMemoryPreferences(); }
        @Override protected void syncSpi() { }
        @Override protected void flushSpi() { }
    }
}
