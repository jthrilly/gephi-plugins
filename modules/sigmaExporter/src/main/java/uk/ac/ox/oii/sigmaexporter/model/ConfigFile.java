/*
 Copyright Scott A. Hale, 2016
 */
package uk.ac.ox.oii.sigmaexporter.model;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.prefs.Preferences;

public class ConfigFile implements Serializable{

    private static final long serialVersionUID = 1L;

    /** Edge styles understood by the viewer, written to sigma.drawingProperties.defaultEdgeType. */
    public static final List<String> EDGE_TYPES =
            Collections.unmodifiableList(Arrays.asList("line", "curve", "arrow", "curvedArrow"));
    public static final String DEFAULT_EDGE_TYPE = "curve";

    /** Preference keys for the options added in 1.0.0. */
    public static final String PREF_EDGE_TYPE = "sigma.defaultEdgeType";
    public static final String PREF_SEARCH_FULLTEXT = "search.fulltext";
    
    private final String type;
    private final String version;
    
    private String data;
    
    private HashMap<String,Object> logo;
    private HashMap<String,Object> text;
    private HashMap<String,Object> legend;
    private HashMap<String,Object> features;
    private HashMap<String,Object> informationPanel;
    
    private HashMap<String,HashMap<String,Object>> sigma;

    private HashMap<String,Object> search;

    public ConfigFile() {
        this.type = "network";
        this.data = "data.json";
        this.version = "1.0";
        this.logo = new HashMap<String,Object>();
        this.text = new HashMap<String,Object>();
        this.legend = new HashMap<String,Object>();
        this.features = new HashMap<String,Object>();
        this.informationPanel = new HashMap<String,Object>();
        
        this.sigma = new HashMap<String,HashMap<String,Object>>();
        this.search = new HashMap<String,Object>();
        
        setDefaults();
    }
    
    public void setDefaults() {
       
        logo.put("file","");
        logo.put("link","");
        logo.put("text","");
        
        text.put("title","");
        text.put("intro","");
        text.put("more","");
        
        legend.put("nodeLabel","");
        legend.put("edgeLabel","");
        legend.put("colorLabel","");
        
        features.put("search","true");
        features.put("hoverBehavior","default");
        features.put("groupSelectorAttribute",false);
        
        informationPanel.put("groupByEdgeDirection",false);
        informationPanel.put("imageAttribute",false);
        
        //sigma.put("drawingProperties", new HashMap<String,String>());
        //sigma.put("graphProperties", new HashMap<String,String>());
        //sigma.put("mouseProperties", new HashMap<String,String>());
        HashMap<String,Object> map = new HashMap<String,Object>();
        map.put("defaultLabelColor", "#000");
        map.put("defaultLabelSize", 14);
        map.put("defaultLabelBGColor", "#ddd");
        map.put("defaultHoverLabelBGColor", "#002147");
        map.put("defaultLabelHoverColor", "#fff");
        map.put("labelThreshold", 10);
        map.put("defaultEdgeType", "curve");
        map.put("hoverFontStyle", "bold");
        map.put("fontStyle", "bold");
        map.put("activeFontStyle", "bold");
        sigma.put("drawingProperties", map);
        
        map = new HashMap<String,Object>();
        map.put("minRatio", 0.75);
        map.put("maxRatio", 20);
        sigma.put("mouseProperties", map);

        map = new HashMap<String,Object>();
        map.put("minNodeSize", 1);
        map.put("maxNodeSize", 7);
        map.put("minEdgeSize", 0.2);
        map.put("maxEdgeSize", 0.5);
        sigma.put("graphProperties", map);

        search.put("fulltext", false);
    }
    
    

    public HashMap<String, Object> getLogo() {
        return logo;
    }

    public void setLogo(HashMap<String, Object> logo) {
        this.logo = logo;
    }

    public HashMap<String, Object> getText() {
        return text;
    }

    public void setText(HashMap<String, Object> text) {
        this.text = text;
    }

    public HashMap<String, Object> getLegend() {
        return legend;
    }

    public void setLegend(HashMap<String, Object> legend) {
        this.legend = legend;
    }

    public HashMap<String, Object> getFeatures() {
        return features;
    }

    public void setFeatures(HashMap<String, Object> features) {
        this.features = features;
    }

    public HashMap<String, Object> getInformationPanel() {
        return informationPanel;
    }

    public void setInformationPanel(HashMap<String, Object> informationPanel) {
        this.informationPanel = informationPanel;
    }

    public HashMap<String, HashMap<String, Object>> getSigma() {
        return sigma;
    }

    public void setSigma(HashMap<String, HashMap<String, Object>> sigma) {
        this.sigma = sigma;
    }

    public HashMap<String, Object> getSearch() {
        return search;
    }

    public void setSearch(HashMap<String, Object> search) {
        this.search = search;
    }

    /** Search all node attributes in the viewer, not only labels. Written as search.fulltext. */
    public void setSearchFulltext(boolean fulltext) {
        search.put("fulltext", fulltext);
    }

    public boolean isSearchFulltext() {
        return Boolean.TRUE.equals(search.get("fulltext"));
    }

    /**
     * Sets sigma.drawingProperties.defaultEdgeType. Unknown values fall back to
     * {@link #DEFAULT_EDGE_TYPE}.
     */
    public void setDefaultEdgeType(String edgeType) {
        sigma.get("drawingProperties").put("defaultEdgeType", normalizeEdgeType(edgeType));
    }

    public String getDefaultEdgeType() {
        return String.valueOf(sigma.get("drawingProperties").get("defaultEdgeType"));
    }

    public static String normalizeEdgeType(String edgeType) {
        return EDGE_TYPES.contains(edgeType) ? edgeType : DEFAULT_EDGE_TYPE;
    }

    public void readFromPrefs(Preferences props) {
        legend.put("nodeLabel",props.get("legend.node",""));
        legend.put("edgeLabel",props.get("legend.edge",""));
        legend.put("colorLabel",props.get("legend.color",""));
        
        features.put("search",Boolean.valueOf(props.get("features.search","true")));
        
        String hover = props.get("features.hoverBehavior","default").toLowerCase();
        if (hover.indexOf("none")!=-1) hover="default";
        features.put("hoverBehavior",hover);
        
        String group = props.get("features.groupSelectAttribute",null);
        if (group==null || group.indexOf("None")!=-1) features.put("groupSelectorAttribute",false);
        else features.put("groupSelectorAttribute",group);        
        
        String img = props.get("informationPanel.imageAttribute",null);
        if (img==null || img.indexOf("None")!=-1) informationPanel.put("imageAttribute",false);
        else informationPanel.put("imageAttribute",img);   
        
        informationPanel.put("groupByEdgeDirection",Boolean.valueOf(props.get("informationPanel.groupByEdgeDirection","false")));
        
        text.put("intro",props.get("text.intro",""));
        text.put("more",props.get("text.more",""));
        text.put("title",props.get("text.title",""));
        
        logo.put("file",props.get("logo.file",""));
        logo.put("link",props.get("logo.link",""));
        logo.put("text",props.get("logo.author",""));

        setDefaultEdgeType(props.get(PREF_EDGE_TYPE, DEFAULT_EDGE_TYPE));
        setSearchFulltext(Boolean.parseBoolean(props.get(PREF_SEARCH_FULLTEXT, "false")));
    }
    
    
    
}
