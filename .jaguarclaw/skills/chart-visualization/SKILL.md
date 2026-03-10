---
name: chart-visualization
description: This skill should be used when the user wants to visualize data. It intelligently selects the most suitable chart type from 26 available options, extracts parameters based on detailed specifications, and generates a chart image using a JavaScript script.
dependency:
  nodejs: ">=18.0.0"
---

# Chart Visualization Skill

This skill provides a comprehensive workflow for transforming data into visual charts. It handles chart selection, parameter extraction, and image generation.

## Workflow

To visualize data, follow these steps:

### 1. Intelligent Chart Selection
Analyze the user's data features to determine the most appropriate chart type. Use the following guidelines (and consult `references/` for detailed specs):

- **Time Series**: Use `generate_line_chart` (trends) or `generate_area_chart` (accumulated trends). Use `generate_dual_axes_chart` for two different scales.
- **Comparisons**: Use `generate_bar_chart` (categorical) or `generate_column_chart`. Use `generate_histogram_chart` for frequency distributions.
- **Part-to-Whole**: Use `generate_pie_chart` or `generate_treemap_chart` (hierarchical).
- **Relationships & Flow**: Use `generate_scatter_chart` (correlation), `generate_sankey_chart` (flow), or `generate_venn_chart` (overlap).
- **Maps**: Use `generate_district_map` (regions), `generate_pin_map` (points), or `generate_path_map` (routes).
- **Hierarchies & Trees**: Use `generate_organization_chart` or `generate_mind_map`.
- **Specialized**:
    - `generate_radar_chart`: Multi-dimensional comparison.
    - `generate_funnel_chart`: Process stages.
    - `generate_liquid_chart`: Percentage/Progress.
    - `generate_word_cloud_chart`: Text frequency.
    - `generate_boxplot_chart` or `generate_violin_chart`: Statistical distribution.
    - `generate_network_graph`: Complex node-edge relationships.
    - `generate_fishbone_diagram`: Cause-effect analysis.
    - `generate_flow_diagram`: Process flow.
    - `generate_spreadsheet`: Tabular data or pivot tables for structured data display and cross-tabulation.

### 2. Parameter Extraction
Once a chart type is selected, read the corresponding file in the `references/` directory (e.g., `references/generate_line_chart.md`) to identify the required and optional fields.
Extract the data from the user's input and map it to the expected `args` format.

### 3. Chart Generation
Invoke the `scripts/generate.js` script with a JSON payload. The script generates a self-contained HTML file locally — **no network connection required**.

**Payload Format:**
```json
{
  "tool": "generate_chart_type_name",
  "args": {
    "data": [...],
    "title": "...",
    "theme": "...",
    "style": { ... }
  }
}
```

**Execution Command:**
```bash
node ./scripts/generate.js '<payload_json>'
```

The script writes an HTML file to the system temp directory (or `CHART_OUTPUT_DIR` if set) and prints the **absolute file path** to stdout.

**Renderer used per chart type:**
- Standard charts (line, bar, column, pie, scatter, radar, funnel, treemap, histogram, sankey, boxplot, violin, dual_axes, network_graph, word_cloud, liquid) → **ECharts 5** (loaded from CDN, override via `ECHARTS_CDN_URL` env var)
- Diagrams (mind_map, organization_chart, flow_diagram, fishbone_diagram) → **Mermaid 11** (loaded from CDN, override via `MERMAID_CDN_URL` env var)
- Venn chart → inline SVG, no external dependency
- Maps (district_map, pin_map, path_map) → offline not supported; HTML shows a notice + raw data

### 4. Result Return
The script outputs the absolute path of the generated HTML file.
Return the following to the user:
- The file path (formatted as a clickable link if possible, e.g. `file:///path/to/chart.html`).
- A brief description of the chart type and data used.
- The complete `args` (specification) used for generation.

## Reference Material
Detailed specifications for each chart type are located in the `references/` directory. Consult these files to ensure the `args` passed to the script match the expected schema.
