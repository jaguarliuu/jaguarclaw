export interface ChartPreset {
  label: string
  spec: object
}

export const CHART_PRESETS: Record<string, ChartPreset> = {
  bar: {
    label: '柱状图',
    spec: {
      tooltip: {},
      xAxis: { type: 'category', data: ['一月', '二月', '三月', '四月', '五月', '六月'] },
      yAxis: { type: 'value' },
      series: [{ name: '销量', data: [120, 200, 150, 80, 70, 110], type: 'bar', itemStyle: { borderRadius: [3, 3, 0, 0] } }],
    },
  },
  line: {
    label: '折线图',
    spec: {
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: ['一月', '二月', '三月', '四月', '五月', '六月'] },
      yAxis: { type: 'value' },
      series: [{ name: '数值', data: [120, 200, 150, 80, 70, 110], type: 'line', smooth: true }],
    },
  },
  area: {
    label: '面积图',
    spec: {
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: ['一月', '二月', '三月', '四月', '五月', '六月'] },
      yAxis: { type: 'value' },
      series: [{ name: '数值', data: [120, 200, 150, 80, 70, 110], type: 'line', smooth: true, areaStyle: { opacity: 0.3 } }],
    },
  },
  pie: {
    label: '饼图',
    spec: {
      tooltip: { trigger: 'item' },
      series: [{
        type: 'pie', radius: ['40%', '70%'],
        data: [
          { value: 1048, name: '直接访问' },
          { value: 735, name: '邮件营销' },
          { value: 580, name: '联盟广告' },
          { value: 484, name: '视频广告' },
          { value: 300, name: '搜索引擎' },
        ],
      }],
    },
  },
  scatter: {
    label: '散点图',
    spec: {
      tooltip: {},
      xAxis: {},
      yAxis: {},
      series: [{
        type: 'scatter', symbolSize: 14,
        data: [[10.0, 8.04], [8.07, 6.95], [13.0, 7.58], [9.05, 8.81], [11.0, 8.33],
               [14.0, 9.96], [13.4, 7.24], [10.0, 4.26], [8.0, 10.84], [11.5, 5.68]],
      }],
    },
  },
  bar_h: {
    label: '条形图',
    spec: {
      tooltip: {},
      xAxis: { type: 'value' },
      yAxis: { type: 'category', data: ['巴西', '印尼', '美国', '印度', '中国'] },
      series: [{ type: 'bar', data: [18203, 23489, 29034, 104970, 131744], itemStyle: { borderRadius: [0, 3, 3, 0] } }],
    },
  },
  radar: {
    label: '雷达图',
    spec: {
      radar: {
        indicator: [
          { name: '销售', max: 100 }, { name: '管理', max: 100 },
          { name: '技术', max: 100 }, { name: '客服', max: 100 },
          { name: '研发', max: 100 }, { name: '市场', max: 100 },
        ],
      },
      series: [{ type: 'radar', data: [{ value: [80, 70, 90, 65, 85, 75], name: '评分' }] }],
    },
  },
  funnel: {
    label: '漏斗图',
    spec: {
      tooltip: { trigger: 'item' },
      series: [{
        type: 'funnel', left: '10%', width: '80%',
        data: [
          { value: 100, name: '展示' }, { value: 80, name: '点击' },
          { value: 60, name: '访问' }, { value: 40, name: '咨询' },
          { value: 20, name: '转化' },
        ],
      }],
    },
  },
  gauge: {
    label: '仪表盘',
    spec: {
      series: [{
        type: 'gauge',
        progress: { show: true, width: 18 },
        axisLine: { lineStyle: { width: 18 } },
        axisTick: { show: false },
        splitLine: { length: 15 },
        axisLabel: { distance: 25 },
        detail: { valueAnimation: true, fontSize: 30 },
        data: [{ value: 72, name: '完成率 %' }],
      }],
    },
  },
  heatmap: {
    label: '热力图',
    spec: {
      tooltip: { position: 'top' },
      grid: { height: '55%', top: '5%' },
      xAxis: { type: 'category', data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'] },
      yAxis: { type: 'category', data: ['早', '上', '中', '下', '晚'] },
      visualMap: { min: 0, max: 10, calculable: true, orient: 'horizontal', left: 'center', bottom: '2%' },
      series: [{
        type: 'heatmap', label: { show: true },
        data: [
          [0, 0, 5], [0, 1, 1], [0, 2, 0], [0, 3, 0], [0, 4, 2],
          [1, 0, 4], [1, 1, 9], [1, 2, 2], [1, 3, 3], [1, 4, 1],
          [2, 0, 1], [2, 1, 5], [2, 2, 8], [2, 3, 4], [2, 4, 0],
          [3, 0, 3], [3, 1, 7], [3, 2, 6], [3, 3, 2], [3, 4, 4],
          [4, 0, 0], [4, 1, 2], [4, 2, 3], [4, 3, 7], [4, 4, 5],
          [5, 0, 8], [5, 1, 6], [5, 2, 4], [5, 3, 1], [5, 4, 3],
          [6, 0, 2], [6, 1, 3], [6, 2, 5], [6, 3, 9], [6, 4, 7],
        ],
      }],
    },
  },
}
