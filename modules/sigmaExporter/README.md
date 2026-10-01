## Introduction

A Gephi plugin to create an HTML5 interactive display of your network graph that runs in any modern web browser.

## Plugin Owner's Notes

This plugin contains an export plugin that takes the current graph and creates files to display your network interactively with HTML5. It uses the [open-source sigma.js library](https://github.com/jacomyal/sigma.js). The network data is placed in a ``data.json file`` (using a format equivalent to the [JSONExporter plugin](https://github.com/oxfordinternetinstitute/gephi-plugins/tree/jsonexporter-plugin)) and configuration details (author name, description, etc.) are stored in a ``config.json`` file. Other HTML, CSS, and JavaScript files are copied without modification from a standard template.

Simple customization is possible through manually editing the ``config.json`` file as explained on the [project wiki](https://github.com/oxfordinternetinstitute/gephi-plugins/wiki).

The Java code of this exporter is available under a GPLv3 License.

The HTML5 template was initially created through the [InteractiveVis project](https://github.com/oxfordinternetinstitute/InteractiveVis), and subsequently updated by the [NEXUS: Real Time Data Fusion and Network Analysis for Urban Systems project](http://www.oii.ox.ac.uk/research/projects/?id=149) at the [Oxford Internet Institute, University of Oxford](http://www.oii.ox.ac.uk/).

**Important** By default the exported files must be served from a web server: browsers block a page opened from the file system from loading `config.json` and `data.json`. To open the export by double-clicking `index.html`, tick **Embed data in index.html** in the export dialog (see below). [The HTML/JavaScript/CSS code is available at this repository](https://github.com/oxfordinternetinstitute/InteractiveVis/tree/master/network).

The InteractiveVis project of the Oxford Internet Institute with funding by JISC aimed to allow easy creation of interactive visualisations for geospatial and network data using native web technologies (HTML5, CSS3, and SVG) and allow these visualisations to be self-contained so that they may run entirely offline in ebooks and other media. The project surveyed existing solutions and built the necessary components to fill in missing features and smooth over incompatibilities in between existing libraries. More information about the project is available in the [InteractiveVis repository](https://github.com/oxfordinternetinstitute/InteractiveVis).

The project is maintained by [Scott Hale](http://www.scotthale.net/)

## Requirements

Version 1.0.0 targets Gephi 0.11.x (Java 17), built against `gephi-plugin-parent` 0.11.3. Version 0.9.0 was the last release for Gephi 0.9.

## Export options

Besides the legend, branding and feature settings, the export dialog has:

- **Edge style**: Straight, Curved (default), Straight with arrows or Curved with arrows. Written to `config.json` as `sigma.drawingProperties.defaultEdgeType` (`line`, `curve`, `arrow`, `curvedArrow`).
- **Search all attributes**: off by default. Written to `config.json` as `"search": {"fulltext": true|false}`; when on, the viewer's search matches any node attribute, not only labels.
- **Embed data in index.html (opens without a web server)**: off by default. `config.json` and `data.json` are still written, and both are also embedded in `network/index.html` as `<script type="application/json" id="ivis-config">` and `<script type="application/json" id="ivis-data">` just before `</head>`. `</` is written as `<\/` (and U+2028/U+2029 as escapes) so the data can't end the script element early.

Each edge in `data.json` carries `"directed": true|false`, taken from the Gephi edge, so the viewer can draw arrows only on directed edges of a mixed graph.

All settings are remembered between exports.

## Building

From the repository root:

```
mvn -B package
```

This compiles the plugin, runs the unit tests in `src/test/java` and produces `modules/sigmaExporter/target/sigmaexporter-<version>.nbm`. Run Gephi with the plugin installed with `mvn org.gephi:gephi-maven-plugin:run`.

## Updating the viewer template

The viewer is bundled as `src/main/resources/uk/ac/ox/oii/sigmaexporter/resources/network.zip`, built from the `network/` folder of [InteractiveVis](https://github.com/oxfordinternetinstitute/InteractiveVis). To refresh it:

```
git clone https://github.com/oxfordinternetinstitute/InteractiveVis /path/to/InteractiveVis
modules/sigmaExporter/scripts/update-template.sh /path/to/InteractiveVis
mvn -B package
```

The script zips everything under `network/` (entries stay under a top-level `network/` folder) except `config*.json`, `data/`, `server/`, `index_ukgov.html`, `node_modules/`, any `*.md` file and dotfiles. The exporter writes its own `config.json` and `data.json`. It extracts whatever the zip contains, including nested folders and hashed file names such as `assets/index-abc123.js`, so no Java change is needed when the template's file list changes. Commit the new `network.zip` and note the InteractiveVis commit the script prints.
