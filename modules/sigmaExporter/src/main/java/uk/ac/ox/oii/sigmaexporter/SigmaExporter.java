/*
 Copyright Scott A. Hale, 2016
 * 
 
 Base on code from 
 Copyright 2008-2016 Gephi
 Authors : Mathieu Bastian <mathieu.bastian@gephi.org>
 Website : http://www.gephi.org

 Portions Copyrighted 2011 Gephi Consortium.
 */
package uk.ac.ox.oii.sigmaexporter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.gephi.graph.api.Column;
import org.gephi.graph.api.Edge;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.Node;
import org.gephi.graph.api.Table;
import org.gephi.io.exporter.spi.Exporter;
import org.gephi.preview.types.EdgeColor;
import org.gephi.project.api.Workspace;
import org.gephi.utils.longtask.spi.LongTask;
import org.gephi.utils.progress.Progress;
import org.gephi.utils.progress.ProgressTicket;
import uk.ac.ox.oii.sigmaexporter.model.ConfigFile;
import uk.ac.ox.oii.sigmaexporter.model.GraphEdge;
import uk.ac.ox.oii.sigmaexporter.model.GraphElement;
import uk.ac.ox.oii.sigmaexporter.model.GraphNode;

public class SigmaExporter implements Exporter, LongTask {

    private ConfigFile config;
    private String path;
    private boolean renumber;
    private boolean inlineData;
    private Workspace workspace;
    private ProgressTicket progress;
    private boolean cancel = false;

    @Override
    public boolean execute() {
        try {
            GraphModel graphModel = workspace.getLookup().lookup(GraphModel.class);
            return exportTo(new File(path), graphModel);
        } catch (Exception e) {
            Logger.getLogger(SigmaExporter.class.getName()).log(Level.SEVERE, null, e);
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            Progress.finish(progress);
        }
    }

    /**
     * Writes the viewer template, config.json and data.json into
     * {@code <dir>/network}. When inline data is on, both JSON documents are
     * also embedded into network/index.html.
     *
     * @return false if the export was cancelled
     */
    public boolean exportTo(File dir, GraphModel graphModel) throws IOException {
        if (!dir.isDirectory()) {
            throw new IOException("Invalid path. Please make sure the specified directory exists. The network will be exported into a new 'network' directory in this directory.");
        }

        //Copy resource template (entries are under network/)
        try (InputStream zipStream = SigmaExporter.class.getResourceAsStream("resources/network.zip")) {
            ZipHandler.extractZip(zipStream, dir.getAbsolutePath());
        }
        Path networkDir = dir.toPath().resolve("network");

        //Gson to handle JSON writing and escape
        String configJson = new GsonBuilder().setPrettyPrinting().create().toJson(config);
        String dataJson = buildDataJson(graphModel);
        if (dataJson == null) {
            return false; //cancelled
        }

        Files.write(networkDir.resolve("config.json"), configJson.getBytes(StandardCharsets.UTF_8));
        Files.write(networkDir.resolve("data.json"), dataJson.getBytes(StandardCharsets.UTF_8));

        if (inlineData) {
            Path index = networkDir.resolve("index.html");
            String html = new String(Files.readAllBytes(index), StandardCharsets.UTF_8);
            html = InlineData.inject(html, configJson, dataJson);
            Files.write(index, html.getBytes(StandardCharsets.UTF_8));
        }
        return !cancel;
    }

    /** Builds data.json for the visible graph. Returns null if cancelled. */
    String buildDataJson(GraphModel graphModel) {
        HashMap<String,String> nodeIdMap = new HashMap<String,String>();
        int nodeId=0;
        EdgeColor colorMixer = new EdgeColor(EdgeColor.Mode.MIXED);
        Graph graph = graphModel.getGraphVisible();
        graph.readLock();
        try {
            //Count the number of tasks (nodes + edges) and start the progress
            int tasks = graph.getNodeCount() + graph.getEdgeCount();
            Progress.start(progress, tasks);

            Table attModel = graphModel.getNodeTable();
            HashSet<GraphElement> jNodes = new HashSet<GraphElement>();
            Node[] nodeArray = graph.getNodes().toArray();
            for (Node n : nodeArray) {
                String id = n.getId().toString();
                String label = n.getLabel();
                float x = n.x();
                float y = n.y();
                float size = n.size();
                String color = "rgb(" + (int) (n.r() * 255) + "," + (int) (n.g() * 255) + "," + (int) (n.b() * 255) + ")";

                if (renumber) {
                   String newId=String.valueOf(nodeId);
                   nodeIdMap.put(id,newId);
                   id=newId;
                   nodeId++;
                }

                GraphNode jNode = new GraphNode(id);
                jNode.setLabel(label);
                jNode.setX(x);
                jNode.setY(y);
                jNode.setSize(size);
                jNode.setColor(color);

                for (Column col : attModel) {
                    String cid = col.getId();
                    if (cid.equalsIgnoreCase("id") || cid.equalsIgnoreCase("label")) {
                        continue;
                    }

                    Object valObj = n.getAttribute(col);
                    if (valObj == null) {
                        continue;
                    }
                    String name = col.getTitle();
                    String val = valObj.toString();
                    jNode.putAttribute(name, val);
                }

                jNodes.add(jNode);

                if (cancel) {
                    return null;
                }
                Progress.progress(progress);
            }

            //Export edges. Progress is incremented at each step.
            HashSet<GraphElement> jEdges = new HashSet<GraphElement>();
            Edge[] edgeArray = graph.getEdges().toArray();
            for (Edge e : edgeArray) {
                String sourceId = e.getSource().getId().toString();
                String targetId = e.getTarget().getId().toString();

                if (renumber) {
                    sourceId = nodeIdMap.get(sourceId);
                    targetId = nodeIdMap.get(targetId);
                }

                GraphEdge jEdge = new GraphEdge(String.valueOf(e.getId()));
                jEdge.setSource(sourceId);
                jEdge.setTarget(targetId);
                jEdge.setDirected(e.isDirected());
                jEdge.setSize(e.getWeight());
                jEdge.setLabel(e.getLabel());

                float r=e.r();
                float g=e.g();
                float b=e.b();

                Iterator<Column> eAttr = e.getAttributeColumns().iterator();
                while (eAttr.hasNext()) {
                    Column col = eAttr.next();
                    if (col.isProperty() || "weight".equalsIgnoreCase(col.getId())) {
                        //isProperty() excludes id, label, but not weight
                        continue;
                    }
                    String name = col.getTitle();
                    Object valObj = e.getAttribute(col);
                    if (valObj == null) {
                        continue;
                    }
                    String val = valObj.toString();
                    jEdge.putAttribute(name, val);
                }

                String color;
                if (e.alpha()!=0) {
                    color = "rgb(" + (int) (r* 255) + "," + (int) (g* 255) + "," + (int) (b* 255) + ")";
                } else {
                    //no colour has been set. Colour will be mix of connected nodes
                    Node n = e.getSource();
                    Color source = new Color(n.r(),n.g(),n.b());
                    n = e.getTarget();
                    Color target = new Color(n.r(),n.g(),n.b());
                    Color result = colorMixer.getColor(null, source, target);
                    color = "rgb(" + result.getRed() + "," + result.getGreen() + "," + result.getBlue() + ")";
                }
                jEdge.setColor(color);

                jEdges.add(jEdge);

                if (cancel) {
                    return null;
                }
                Progress.progress(progress);
            }

            HashMap<String, HashSet<GraphElement>> json = new HashMap<String, HashSet<GraphElement>>();
            json.put("nodes", jNodes);
            json.put("edges", jEdges);
            return new Gson().toJson(json);
        } finally {
            graph.readUnlock();
        }
    }

    public ConfigFile getConfigFile() {
        return config;
    }

    public List<String> getNodeAttributes() {
        List<String> attr = new ArrayList<String>();
        GraphModel graphModel = workspace.getLookup().lookup(GraphModel.class);
        Table attModel = graphModel.getNodeTable();
        for (Column col : attModel) {
            attr.add(col.getTitle());
        }
        return attr;
    }

    public void setConfigFile(ConfigFile cfg, String path, boolean renumber) {
        this.config = cfg;
        this.path = path;
        this.renumber = renumber;
    }

    /** Also embed config.json and data.json into index.html (opens without a web server). */
    public void setInlineData(boolean inlineData) {
        this.inlineData = inlineData;
    }

    public boolean isInlineData() {
        return inlineData;
    }

    @Override
    public void setWorkspace(Workspace wrkspc) {
        this.workspace = wrkspc;
    }

    @Override
    public Workspace getWorkspace() {
        return workspace;
    }

    @Override
    public boolean cancel() {
        cancel = true;
        return true;
    }

    @Override
    public void setProgressTicket(ProgressTicket pt) {
        this.progress = pt;
    }   
}
