#!/usr/bin/env node
'use strict';

const fs = require('fs');
const path = require('path');
const os = require('os');

// ─── CDN / Output config ──────────────────────────────────────────────────────
const CHART_OUTPUT_DIR = process.env.CHART_OUTPUT_DIR ||
  path.join(os.tmpdir(), 'jaguarclaw-charts');

const ECHARTS_CDN = process.env.ECHARTS_CDN_URL ||
  'https://cdn.jsdelivr.net/npm/echarts@5/dist/echarts.min.js';

const ECHARTS_WORDCLOUD_CDN = process.env.ECHARTS_WORDCLOUD_CDN_URL ||
  'https://cdn.jsdelivr.net/npm/echarts-wordcloud@2/dist/echarts-wordcloud.min.js';

const MERMAID_CDN = process.env.MERMAID_CDN_URL ||
  'https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js';

// ─── Tool classification ──────────────────────────────────────────────────────
const ECHARTS_TOOLS = new Set([
  'generate_line_chart', 'generate_area_chart',
  'generate_bar_chart', 'generate_column_chart',
  'generate_pie_chart', 'generate_scatter_chart',
  'generate_radar_chart', 'generate_funnel_chart',
  'generate_treemap_chart', 'generate_histogram_chart',
  'generate_sankey_chart', 'generate_boxplot_chart',
  'generate_violin_chart', 'generate_dual_axes_chart',
  'generate_network_graph', 'generate_word_cloud_chart',
  'generate_liquid_chart',
]);

const MERMAID_TOOLS = new Set([
  'generate_mind_map', 'generate_organization_chart',
  'generate_flow_diagram', 'generate_fishbone_diagram',
]);

const MAP_TOOLS = new Set([
  'generate_district_map', 'generate_pin_map', 'generate_path_map',
]);

// ─── Utilities ────────────────────────────────────────────────────────────────
function ensureOutputDir() {
  if (!fs.existsSync(CHART_OUTPUT_DIR)) {
    fs.mkdirSync(CHART_OUTPUT_DIR, { recursive: true });
  }
}

function outputFilePath(tool) {
  ensureOutputDir();
  return path.join(CHART_OUTPUT_DIR, `chart-${tool}-${Date.now()}.html`);
}

function escHtml(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function getTheme(args) {
  return (args.theme || '').toLowerCase() === 'dark' ? 'dark' : '';
}

function getPalette(args) {
  return (args.style && args.style.palette) ? args.style.palette : null;
}

function getBg(args) {
  return (args.style && args.style.backgroundColor) || '#ffffff';
}

// ─── ECharts option builders ──────────────────────────────────────────────────

function buildLineOption(args, isArea) {
  const data = args.data || [];
  const groups = [...new Set(data.map(d => d.group).filter(Boolean))];
  const times = [...new Set(data.map(d => d.time || d.x || ''))];
  const palette = getPalette(args);
  const multiGroup = groups.length > 0;

  const series = multiGroup
    ? groups.map(g => {
        const s = {
          type: 'line', name: g, smooth: true,
          data: times.map(t => {
            const item = data.find(d => (d.time || d.x || '') === t && d.group === g);
            return item != null ? item.value : null;
          }),
        };
        if (isArea) s.areaStyle = {};
        return s;
      })
    : (() => {
        const s = { type: 'line', smooth: true, data: data.map(d => d.value) };
        if (isArea) s.areaStyle = {};
        return [s];
      })();

  const opt = {
    title: args.title ? { text: args.title, left: 'center' } : undefined,
    tooltip: { trigger: 'axis' },
    legend: multiGroup ? { top: args.title ? 32 : 10 } : undefined,
    xAxis: {
      type: 'category',
      data: multiGroup ? times : data.map(d => d.time || d.x || ''),
      name: args.axisXTitle || '',
      axisLabel: { rotate: times.length > 8 ? 30 : 0 },
    },
    yAxis: { type: 'value', name: args.axisYTitle || '' },
    series,
    grid: { left: 60, right: 20, bottom: 50, top: args.title ? 60 : 40, containLabel: true },
  };
  if (palette) opt.color = palette;
  return opt;
}

function buildBarColumnOption(args, isHorizontal) {
  const data = args.data || [];
  const groups = [...new Set(data.map(d => d.group).filter(Boolean))];
  const categories = [...new Set(data.map(d => d.category))];
  const palette = getPalette(args);
  const multiGroup = groups.length > 0;
  const useStack = multiGroup && !args.group;

  const series = multiGroup
    ? groups.map(g => ({
        type: 'bar', name: g,
        stack: useStack ? 'total' : undefined,
        data: categories.map(cat => {
          const item = data.find(d => d.category === cat && d.group === g);
          return item ? item.value : 0;
        }),
      }))
    : [{ type: 'bar', data: data.map(d => d.value) }];

  const catData = multiGroup ? categories : data.map(d => d.category);
  const catAxis = { type: 'category', data: catData, name: args.axisYTitle || '' };
  const valAxis = { type: 'value', name: args.axisXTitle || '' };

  const opt = {
    title: args.title ? { text: args.title, left: 'center' } : undefined,
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: multiGroup ? { top: args.title ? 32 : 10 } : undefined,
    xAxis: isHorizontal ? valAxis : { ...catAxis, name: args.axisXTitle || '' },
    yAxis: isHorizontal ? catAxis : { type: 'value', name: args.axisYTitle || '' },
    series,
    grid: { left: 80, right: 20, bottom: 50, top: args.title ? 60 : 40, containLabel: true },
  };
  if (palette) opt.color = palette;
  return opt;
}

function buildPieOption(args) {
  const data = args.data || [];
  const inner = args.innerRadius ? `${Math.round(args.innerRadius * 100)}%` : 0;
  const palette = getPalette(args);

  const opt = {
    title: args.title ? { text: args.title, left: 'center' } : undefined,
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { orient: 'vertical', left: 'left', top: 'middle' },
    series: [{
      type: 'pie', name: args.title || '',
      radius: [inner, '65%'],
      center: ['60%', '50%'],
      data: data.map(d => ({ name: d.category, value: d.value })),
      label: { formatter: '{b}\n{d}%' },
      emphasis: { itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.3)' } },
    }],
  };
  if (palette) opt.color = palette;
  return opt;
}

function buildScatterOption(args) {
  const data = args.data || [];
  const groups = [...new Set(data.map(d => d.group).filter(Boolean))];
  const palette = getPalette(args);

  const series = groups.length > 0
    ? groups.map(g => ({
        type: 'scatter', name: g,
        data: data.filter(d => d.group === g).map(d => [d.x, d.y]),
      }))
    : [{ type: 'scatter', data: data.map(d => [d.x, d.y]) }];

  const opt = {
    title: args.title ? { text: args.title, left: 'center' } : undefined,
    tooltip: { trigger: 'item' },
    legend: groups.length > 0 ? {} : undefined,
    xAxis: { type: 'value', name: args.axisXTitle || '', scale: true },
    yAxis: { type: 'value', name: args.axisYTitle || '', scale: true },
    series,
    grid: { left: 60, right: 20, bottom: 50, top: args.title ? 50 : 30, containLabel: true },
  };
  if (palette) opt.color = palette;
  return opt;
}

function buildRadarOption(args) {
  const data = args.data || [];
  const groups = [...new Set(data.map(d => d.group).filter(Boolean))];
  const dimNames = [...new Set(data.map(d => d.name))];
  const indicators = dimNames.map(name => {
    const max = Math.max(...data.filter(d => d.name === name).map(d => d.value)) * 1.25;
    return { name, max };
  });
  const palette = getPalette(args);

  const seriesData = groups.length > 0
    ? groups.map(g => ({
        name: g,
        value: dimNames.map(dim => {
          const item = data.find(d => d.name === dim && d.group === g);
          return item ? item.value : 0;
        }),
      }))
    : [{ value: dimNames.map(dim => { const i = data.find(d => d.name === dim); return i ? i.value : 0; }) }];

  const opt = {
    title: args.title ? { text: args.title, left: 'center' } : undefined,
    tooltip: {},
    legend: groups.length > 0 ? { bottom: 0 } : undefined,
    radar: { indicator: indicators, radius: '65%' },
    series: [{ type: 'radar', data: seriesData }],
  };
  if (palette) opt.color = palette;
  return opt;
}

function buildFunnelOption(args) {
  const data = [...(args.data || [])].sort((a, b) => b.value - a.value);
  const palette = getPalette(args);

  const opt = {
    title: args.title ? { text: args.title, left: 'center' } : undefined,
    tooltip: { trigger: 'item' },
    series: [{
      type: 'funnel', left: '10%', width: '80%',
      data: data.map(d => ({ name: d.category, value: d.value })),
      label: { formatter: '{b}: {c}' },
    }],
  };
  if (palette) opt.color = palette;
  return opt;
}

function buildTreemapOption(args) {
  const palette = getPalette(args);
  const opt = {
    title: args.title ? { text: args.title, left: 'center' } : undefined,
    tooltip: { formatter: '{b}: {c}' },
    series: [{
      type: 'treemap',
      data: args.data || [],
      label: { show: true, formatter: '{b}\n{c}' },
      upperLabel: { show: true, height: 24 },
    }],
  };
  if (palette) opt.color = palette;
  return opt;
}

function computeHistBins(values, binNumber) {
  const min = Math.min(...values);
  const max = Math.max(...values);
  const bins = binNumber || Math.ceil(Math.sqrt(values.length)) || 10;
  const w = (max - min) / bins || 1;
  const counts = new Array(bins).fill(0);
  values.forEach(v => {
    const i = Math.min(Math.floor((v - min) / w), bins - 1);
    counts[i]++;
  });
  return counts.map((count, i) => ({
    range: `${(min + i * w).toFixed(1)}-${(min + (i + 1) * w).toFixed(1)}`,
    count,
  }));
}

function buildHistogramOption(args) {
  const raw = args.data || [];
  const values = raw.map(d => typeof d === 'number' ? d : Number(d.value)).filter(v => !isNaN(v));
  const bins = computeHistBins(values, args.binNumber);
  const palette = getPalette(args);

  const opt = {
    title: args.title ? { text: args.title, left: 'center' } : undefined,
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: bins.map(b => b.range), name: args.axisXTitle || '', axisLabel: { rotate: 30 } },
    yAxis: { type: 'value', name: args.axisYTitle || '频数' },
    series: [{ type: 'bar', data: bins.map(b => b.count), barCategoryGap: '2%' }],
    grid: { left: 50, right: 20, bottom: 70, top: args.title ? 50 : 30, containLabel: true },
  };
  if (palette) opt.color = palette;
  return opt;
}

function buildSankeyOption(args) {
  const data = args.data || [];
  const nodeSet = new Set();
  data.forEach(d => { nodeSet.add(d.source); nodeSet.add(d.target); });
  const palette = getPalette(args);

  const opt = {
    title: args.title ? { text: args.title, left: 'center' } : undefined,
    tooltip: { trigger: 'item', triggerOn: 'mousemove' },
    series: [{
      type: 'sankey',
      data: [...nodeSet].map(name => ({ name })),
      links: data.map(d => ({ source: d.source, target: d.target, value: d.value })),
      nodeAlign: args.nodeAlign || 'justify',
      emphasis: { focus: 'adjacency' },
      label: { show: true },
    }],
  };
  if (palette) opt.color = palette;
  return opt;
}

function computeBoxStats(values) {
  const sorted = [...values].sort((a, b) => a - b);
  const n = sorted.length;
  return [sorted[0], sorted[Math.floor(n * 0.25)], sorted[Math.floor(n * 0.5)], sorted[Math.floor(n * 0.75)], sorted[n - 1]];
}

function buildBoxplotOption(args) {
  const data = args.data || [];
  const categories = [...new Set(data.map(d => d.category))];
  const boxData = categories.map(cat =>
    computeBoxStats(data.filter(d => d.category === cat).map(d => d.value))
  );
  const palette = getPalette(args);

  const opt = {
    title: args.title ? { text: args.title, left: 'center' } : undefined,
    tooltip: { trigger: 'item' },
    xAxis: { type: 'category', data: categories, name: args.axisXTitle || '', boundaryGap: true },
    yAxis: { type: 'value', name: args.axisYTitle || '', splitArea: { show: true } },
    series: [{ type: 'boxplot', data: boxData }],
    grid: { left: 60, right: 20, bottom: 50, top: args.title ? 50 : 30, containLabel: true },
  };
  if (palette) opt.color = palette;
  return opt;
}

function buildDualAxesOption(args) {
  const categories = args.categories || [];
  const seriesDefs = args.series || [];
  const palette = getPalette(args);

  const series = seriesDefs.map((s, i) => ({
    type: s.type === 'column' ? 'bar' : 'line',
    name: s.name || s.axisYTitle || `Series ${i + 1}`,
    yAxisIndex: i > 0 ? 1 : 0,
    data: s.data || [],
    smooth: s.type === 'line',
  }));

  const opt = {
    title: args.title ? { text: args.title, left: 'center' } : undefined,
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
    legend: { data: series.map(s => s.name), top: args.title ? 32 : 10 },
    xAxis: { type: 'category', data: categories, name: args.axisXTitle || '', axisPointer: { type: 'shadow' } },
    yAxis: [
      { type: 'value', name: seriesDefs[0] && seriesDefs[0].axisYTitle || '', position: 'left' },
      { type: 'value', name: seriesDefs[1] && seriesDefs[1].axisYTitle || '', position: 'right', splitLine: { show: false } },
    ],
    series,
    grid: { left: 70, right: 70, bottom: 50, top: args.title ? 70 : 50, containLabel: true },
  };
  if (palette) opt.color = palette;
  return opt;
}

function buildNetworkOption(args) {
  const nodeData = (args.data && args.data.nodes) ? args.data.nodes : [];
  const edgeData = (args.data && args.data.edges) ? args.data.edges : [];
  const palette = getPalette(args);

  const opt = {
    title: args.title ? { text: args.title, left: 'center' } : undefined,
    tooltip: { trigger: 'item' },
    series: [{
      type: 'graph', layout: 'force', roam: true,
      data: nodeData.map(n => ({ id: n.name, name: n.name, symbolSize: 32, label: { show: true } })),
      links: edgeData.map(e => ({
        source: e.source, target: e.target,
        label: e.name ? { show: true, formatter: e.name } : { show: false },
      })),
      force: { repulsion: 300, edgeLength: [60, 160] },
      lineStyle: { color: 'source', curveness: 0.1 },
    }],
  };
  if (palette) opt.color = palette;
  return opt;
}

function buildWordCloudOption(args) {
  const data = args.data || [];
  const palette = getPalette(args);

  const opt = {
    title: args.title ? { text: args.title, left: 'center' } : undefined,
    tooltip: { show: true },
    series: [{
      type: 'wordCloud',
      shape: 'circle',
      left: 'center', top: 'center',
      width: '90%', height: '90%',
      sizeRange: [14, 64],
      rotationRange: [-60, 60], rotationStep: 30,
      gridSize: 8,
      data: data.map(d => ({ name: d.text, value: d.value })),
    }],
  };
  if (palette) opt.color = palette;
  return opt;
}

function buildLiquidOption(args) {
  const pct = typeof args.percent === 'number' ? Math.max(0, Math.min(1, args.percent)) : 0.5;
  const color = (args.style && args.style.color) || '#3ba272';

  return {
    title: args.title ? { text: args.title, left: 'center' } : undefined,
    series: [{
      type: 'gauge',
      startAngle: 90, endAngle: -270,
      pointer: { show: false },
      progress: { show: true, overlap: false, roundCap: true, clip: false,
        itemStyle: { color } },
      axisLine: { lineStyle: { width: 20, color: [[1, '#E6EBF8']] } },
      splitLine: { show: false }, axisTick: { show: false }, axisLabel: { show: false },
      data: [{ value: Math.round(pct * 100), name: args.title || '' }],
      detail: {
        offsetCenter: [0, '8%'], fontSize: 40, fontWeight: 'bold',
        formatter: '{value}%', color: '#333',
      },
      title: { offsetCenter: [0, '38%'], fontSize: 14, color: '#666' },
    }],
  };
}

// ─── Venn SVG ─────────────────────────────────────────────────────────────────

function buildVennSvg(args, width, height) {
  const data = args.data || [];
  const allSets = [...new Set(data.flatMap(d => d.sets || []))];
  const n = allSets.length;
  const cx = width / 2;
  const cy = height / 2 + 10;
  const r = Math.min(width, height) * 0.27;
  const FILLS = ['rgba(70,130,220,0.35)', 'rgba(220,70,70,0.35)', 'rgba(70,200,130,0.35)', 'rgba(220,180,60,0.35)'];
  const STROKES = ['#4682dc', '#dc4646', '#46c882', '#dcb43c'];

  const centers =
    n <= 1 ? [{ x: cx, y: cy }]
    : n === 2 ? [{ x: cx - r * 0.42, y: cy }, { x: cx + r * 0.42, y: cy }]
    : n === 3 ? [{ x: cx, y: cy - r * 0.42 }, { x: cx - r * 0.42, y: cy + r * 0.32 }, { x: cx + r * 0.42, y: cy + r * 0.32 }]
    : [{ x: cx - r * 0.4, y: cy - r * 0.4 }, { x: cx + r * 0.4, y: cy - r * 0.4 },
       { x: cx - r * 0.4, y: cy + r * 0.4 }, { x: cx + r * 0.4, y: cy + r * 0.4 }];

  const circles = allSets.map((s, i) => {
    const p = centers[i] || { x: cx, y: cy };
    return `<circle cx="${p.x}" cy="${p.y}" r="${r}" fill="${FILLS[i % FILLS.length]}" stroke="${STROKES[i % STROKES.length]}" stroke-width="1.5"/>`;
  }).join('\n  ');

  const setLabels = allSets.map((s, i) => {
    const p = centers[i] || { x: cx, y: cy };
    const lx = p.x + (p.x - cx) * 0.2;
    const ly = p.y < cy ? p.y - r - 8 : p.y + r + 18;
    return `<text x="${lx}" y="${ly}" text-anchor="middle" font-size="13" font-weight="600" fill="#333">${escHtml(s)}</text>`;
  }).join('\n  ');

  const valueLabels = data.map(d => {
    let vx = cx, vy = cy;
    if (d.sets && d.sets.length === 1) {
      const idx = allSets.indexOf(d.sets[0]);
      if (idx >= 0) {
        const p = centers[idx];
        vx = p.x + (p.x - cx) * 0.55;
        vy = p.y + (p.y - cy) * 0.55;
      }
    }
    const lbl = d.label || String(d.value);
    return `<text x="${vx}" y="${vy + 5}" text-anchor="middle" font-size="13" fill="#222">${escHtml(lbl)}</text>`;
  }).join('\n  ');

  const titleEl = args.title
    ? `<text x="${cx}" y="20" text-anchor="middle" font-size="15" font-weight="600" fill="#333">${escHtml(args.title)}</text>`
    : '';

  return `<svg width="${width}" height="${height}" xmlns="http://www.w3.org/2000/svg" style="display:block;">
  ${titleEl}
  ${circles}
  ${setLabels}
  ${valueLabels}
</svg>`;
}

// ─── Mermaid diagram builders ─────────────────────────────────────────────────

function toMermaidId(str) {
  return 'n' + String(str).replace(/[^a-zA-Z0-9]/g, '_');
}

function mEsc(str) {
  return String(str).replace(/"/g, "'").replace(/[<>]/g, ' ').trim();
}

function buildFlowMermaid(args) {
  const { nodes = [], edges = [] } = args.data || {};
  let mmd = 'flowchart TD\n';
  nodes.forEach(n => { mmd += `  ${toMermaidId(n.name)}["${mEsc(n.name)}"]\n`; });
  edges.forEach(e => {
    const lbl = e.name ? `|"${mEsc(e.name)}"|` : '';
    mmd += `  ${toMermaidId(e.source)} -->${lbl} ${toMermaidId(e.target)}\n`;
  });
  return mmd;
}

function renderMindNode(node, depth) {
  const indent = '  '.repeat(depth + 1);
  const tag = depth === 0 ? `root((${mEsc(node.name)}))` : `(${mEsc(node.name)})`;
  let out = `${indent}${tag}\n`;
  (node.children || []).forEach(c => { out += renderMindNode(c, depth + 1); });
  return out;
}

function buildMindMapMermaid(args) {
  return 'mindmap\n' + renderMindNode(args.data || { name: 'Root' }, 0);
}

function renderOrgNode(node, parentId, lines) {
  const id = `${toMermaidId(node.name)}_${Math.floor(Math.random() * 9000 + 1000)}`;
  const lbl = node.description
    ? `["${mEsc(node.name)}\n${mEsc(node.description)}"]`
    : `["${mEsc(node.name)}"]`;
  lines.push(`  ${id}${lbl}`);
  if (parentId) lines.push(`  ${parentId} --> ${id}`);
  (node.children || []).forEach(c => renderOrgNode(c, id, lines));
}

function buildOrgChartMermaid(args) {
  const lines = ['graph TD'];
  renderOrgNode(args.data || { name: 'Root' }, null, lines);
  return lines.join('\n');
}

function buildFishboneMermaid(args) {
  return 'mindmap\n' + renderMindNode(args.data || { name: 'Root Cause' }, 0);
}

// ─── HTML assemblers ──────────────────────────────────────────────────────────

function echartsHtml(tool, args) {
  const W = args.width || 720;
  const H = args.height || 460;
  const bg = getBg(args);
  const theme = getTheme(args);
  const themeArg = theme ? `'${theme}'` : 'undefined';

  let option;
  let extraSrc = '';

  switch (tool) {
    case 'generate_line_chart':      option = buildLineOption(args, false);      break;
    case 'generate_area_chart':      option = buildLineOption(args, true);       break;
    case 'generate_bar_chart':       option = buildBarColumnOption(args, true);  break;
    case 'generate_column_chart':    option = buildBarColumnOption(args, false); break;
    case 'generate_pie_chart':       option = buildPieOption(args);              break;
    case 'generate_scatter_chart':   option = buildScatterOption(args);          break;
    case 'generate_radar_chart':     option = buildRadarOption(args);            break;
    case 'generate_funnel_chart':    option = buildFunnelOption(args);           break;
    case 'generate_treemap_chart':   option = buildTreemapOption(args);          break;
    case 'generate_histogram_chart': option = buildHistogramOption(args);        break;
    case 'generate_sankey_chart':    option = buildSankeyOption(args);           break;
    case 'generate_boxplot_chart':   option = buildBoxplotOption(args);          break;
    case 'generate_violin_chart':    option = buildBoxplotOption(args);          break;
    case 'generate_dual_axes_chart': option = buildDualAxesOption(args);         break;
    case 'generate_network_graph':   option = buildNetworkOption(args);          break;
    case 'generate_word_cloud_chart':
      option = buildWordCloudOption(args);
      extraSrc = `\n<script src="${ECHARTS_WORDCLOUD_CDN}"></script>`;
      break;
    case 'generate_liquid_chart': option = buildLiquidOption(args); break;
    default: option = {};
  }

  const optJson = JSON.stringify(option, null, 2);

  return `<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>${escHtml(args.title || tool)}</title>
<style>*{box-sizing:border-box;margin:0;padding:0}body{background:${bg};display:flex;align-items:center;justify-content:center;min-height:100vh;}</style>
<script src="${ECHARTS_CDN}"></script>${extraSrc}
</head>
<body>
<div id="chart" style="width:${W}px;height:${H}px;"></div>
<script>
(function(){
  var el = document.getElementById('chart');
  var chart = echarts.init(el, ${themeArg});
  chart.setOption(${optJson});
  window.addEventListener('resize', function(){ chart.resize(); });
})();
</script>
</body>
</html>`;
}

function vennHtml(args) {
  const W = args.width || 640;
  const H = args.height || 420;
  const bg = getBg(args);
  const svg = buildVennSvg(args, W, H);
  return `<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="UTF-8">
<title>${escHtml(args.title || 'Venn Chart')}</title>
<style>*{margin:0;padding:0}body{background:${bg};display:flex;align-items:center;justify-content:center;min-height:100vh;}</style>
</head>
<body>${svg}</body>
</html>`;
}

function mermaidHtml(tool, args) {
  const W = args.width || 720;
  const bg = getBg(args);
  const mTheme = (args.theme || '').toLowerCase() === 'dark' ? 'dark' : 'default';

  let mmd;
  switch (tool) {
    case 'generate_flow_diagram':       mmd = buildFlowMermaid(args);     break;
    case 'generate_mind_map':           mmd = buildMindMapMermaid(args);  break;
    case 'generate_organization_chart': mmd = buildOrgChartMermaid(args); break;
    case 'generate_fishbone_diagram':   mmd = buildFishboneMermaid(args); break;
    default: mmd = 'graph TD\n  A[Unknown]';
  }

  // Encode diagram as base64 to avoid any JS escaping issues
  const mmdB64 = Buffer.from(mmd).toString('base64');

  return `<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>${escHtml(args.title || tool)}</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{background:${bg};display:flex;flex-direction:column;align-items:center;padding:24px;font-family:-apple-system,sans-serif;min-height:100vh;}
h2{margin-bottom:16px;font-size:16px;color:#333;font-weight:600;}
#diagram{max-width:${W}px;width:100%;}
#diagram svg{max-width:100%;height:auto;}
#err{color:#c0392b;white-space:pre-wrap;font-size:13px;max-width:${W}px;}
</style>
<script src="${MERMAID_CDN}"></script>
</head>
<body>
${args.title ? `<h2>${escHtml(args.title)}</h2>` : ''}
<div id="diagram"></div>
<div id="err"></div>
<script>
mermaid.initialize({ startOnLoad: false, theme: '${mTheme}', securityLevel: 'loose' });
var code = atob('${mmdB64}');
mermaid.render('mermaid-out', code).then(function(result) {
  var parser = new DOMParser();
  var doc = parser.parseFromString(result.svg, 'image/svg+xml');
  document.getElementById('diagram').appendChild(doc.documentElement);
}).catch(function(err) {
  var errEl = document.getElementById('err');
  var msg = document.createTextNode('Diagram error: ' + err.message);
  errEl.appendChild(msg);
});
</script>
</body>
</html>`;
}

function mapUnsupportedHtml(tool, args) {
  const dataStr = JSON.stringify(args.data || args, null, 2);
  return `<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="UTF-8">
<title>Map Chart</title>
<style>body{font-family:-apple-system,sans-serif;padding:32px;max-width:720px;margin:0 auto;background:#fafafa;}</style>
</head>
<body>
<div style="padding:18px 20px;background:#fff8e1;border:1px solid #ffe082;border-radius:8px;color:#795548;margin-bottom:20px;">
<strong>&#9888; 地图图表暂不支持离线渲染</strong><br>
地图类型需要在线地理数据，离线环境下无法渲染。如需地图展示，请配置可访问的地理数据服务。
</div>
<pre id="data-pre" style="background:#fff;border:1px solid #e0e0e0;border-radius:6px;padding:16px;font-size:13px;overflow-x:auto;line-height:1.6;"></pre>
<script>document.getElementById('data-pre').textContent = ${JSON.stringify(dataStr)};</script>
</body>
</html>`;
}

// ─── Main ─────────────────────────────────────────────────────────────────────

async function main() {
  if (process.argv.length < 3) {
    process.stderr.write('Usage: node generate.js <spec_json_or_file>\n');
    process.exit(1);
  }

  const specArg = process.argv[2];
  let spec;
  try {
    spec = fs.existsSync(specArg)
      ? JSON.parse(fs.readFileSync(specArg, 'utf-8'))
      : JSON.parse(specArg);
  } catch (e) {
    process.stderr.write('Error parsing spec: ' + e.message + '\n');
    process.exit(1);
  }

  const specs = Array.isArray(spec) ? spec : [spec];

  for (const item of specs) {
    const tool = item.tool;
    const args = item.args || {};

    if (!tool) {
      process.stderr.write("Error: 'tool' field missing\n");
      continue;
    }

    let html;
    if (tool === 'generate_venn_chart') {
      html = vennHtml(args);
    } else if (ECHARTS_TOOLS.has(tool)) {
      html = echartsHtml(tool, args);
    } else if (MERMAID_TOOLS.has(tool)) {
      html = mermaidHtml(tool, args);
    } else if (MAP_TOOLS.has(tool)) {
      html = mapUnsupportedHtml(tool, args);
    } else {
      process.stderr.write("Error: Unknown tool '" + tool + "'\n");
      continue;
    }

    const outFile = outputFilePath(tool);
    fs.writeFileSync(outFile, html, 'utf-8');
    process.stdout.write(outFile + '\n');
  }
}

main().catch(function(err) {
  process.stderr.write(err.message + '\n');
  process.exit(1);
});

module.exports = { echartsHtml, mermaidHtml, vennHtml, mapUnsupportedHtml };
