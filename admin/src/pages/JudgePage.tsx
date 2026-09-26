import { useEffect, useState } from 'react';
import { Alert, Button, Card, Drawer, Table, Tag, Typography } from 'antd';
import ReactECharts from 'echarts-for-react';
import type { ColumnsType } from 'antd/es/table';
import { adminApi, type JudgeRun, type JudgeCase } from '../lib/api';

/** 分数配色：>=4.5 绿 / >=3.5 蓝 / 否则红 */
function scoreColor(v: number): string {
  if (v >= 4.5) return 'green';
  if (v >= 3.5) return 'blue';
  return 'red';
}

function fmt(v: number): string {
  return v == null ? '-' : v.toFixed(2);
}

export function JudgePage() {
  const [runs, setRuns] = useState<JudgeRun[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [cases, setCases] = useState<JudgeCase[]>([]);
  const [title, setTitle] = useState('');

  const load = async () => {
    setLoading(true);
    try {
      const res = await adminApi.judgeRuns({ page: 1, size: 50 });
      setRuns(res.items);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const openCases = async (run: JudgeRun) => {
    setTitle(`评测 #${run.id}　${run.note || ''}`);
    setCases([]);
    setOpen(true);
    try {
      setCases(await adminApi.judgeCases(run.id));
    } catch (e) {
      console.warn('加载用例失败', e);
    }
  };

  // 趋势：旧 → 新（接口返回是倒序）。用柱形图，点少时更直观
  const asc = [...runs].reverse();
  const option = {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { data: ['忠实度', '相关性', '语气'], top: 0, itemGap: 24 },
    grid: { left: 8, right: 24, top: 48, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      data: asc.map((r) => `${r.runAt.slice(5, 10)} ${r.runAt.slice(11, 16)}`),
      axisLabel: { hideOverlap: true },
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 5,
      splitLine: { lineStyle: { type: 'dashed', color: '#eee' } },
    },
    series: [
      { name: '忠实度', type: 'bar', barMaxWidth: 26, label: { show: true, position: 'top', formatter: (p: { value: number }) => p.value.toFixed(2) }, data: asc.map((r) => r.avgFaithfulness) },
      { name: '相关性', type: 'bar', barMaxWidth: 26, label: { show: true, position: 'top', formatter: (p: { value: number }) => p.value.toFixed(2) }, data: asc.map((r) => r.avgRelevance) },
      { name: '语气', type: 'bar', barMaxWidth: 26, label: { show: true, position: 'top', formatter: (p: { value: number }) => p.value.toFixed(2) }, data: asc.map((r) => r.avgTone) },
    ],
  };

  const runColumns: ColumnsType<JudgeRun> = [
    { title: '时间', dataIndex: 'runAt', width: 170, render: (v: string) => new Date(v).toLocaleString() },
    { title: '备注', dataIndex: 'note', ellipsis: true, render: (v?: string) => v || '-' },
    { title: '用例', dataIndex: 'caseCount', width: 70 },
    {
      title: '忠实度',
      dataIndex: 'avgFaithfulness',
      width: 90,
      render: (v: number) => <Tag color={scoreColor(v)}>{fmt(v)}</Tag>,
    },
    {
      title: '相关性',
      dataIndex: 'avgRelevance',
      width: 90,
      render: (v: number) => <Tag color={scoreColor(v)}>{fmt(v)}</Tag>,
    },
    {
      title: '语气',
      dataIndex: 'avgTone',
      width: 90,
      render: (v: number) => <Tag color={scoreColor(v)}>{fmt(v)}</Tag>,
    },
    {
      title: '操作',
      width: 80,
      render: (_, r) => (
        <Button size="small" onClick={() => openCases(r)}>
          查看
        </Button>
      ),
    },
  ];

  const caseColumns: ColumnsType<JudgeCase> = [
    { title: '用例', dataIndex: 'query', width: 260, ellipsis: true },
    { title: '回复', dataIndex: 'reply', ellipsis: true },
    {
      title: '忠实度',
      dataIndex: 'faithfulness',
      width: 80,
      render: (v: number) => <Tag color={scoreColor(v)}>{v}</Tag>,
    },
    {
      title: '相关性',
      dataIndex: 'relevance',
      width: 80,
      render: (v: number) => <Tag color={scoreColor(v)}>{v}</Tag>,
    },
    { title: '语气', dataIndex: 'tone', width: 70, render: (v: number) => <Tag color={scoreColor(v)}>{v}</Tag> },
  ];

  return (
    <>
      {runs.length === 0 && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="还没有评测记录"
          description="运行 mvn -f server/pom.xml test -Dtest=LLMJudgeAgentEvalTest 后会写入这里，可对比每次 prompt/rubric 迭代的进步与退步。"
        />
      )}

      <Card title="分数趋势（每次迭代）" loading={loading}>
        <ReactECharts option={option} style={{ height: 320 }} />
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          纵轴 0~5 分。三根柱分别是被测 Agent 的忠实度 / 相关性 / 语气平均分，横轴为运行时间。
        </Typography.Text>
      </Card>

      <Card title="评测运行" style={{ marginTop: 16 }}>
        <Table<JudgeRun>
          rowKey="id"
          size="small"
          loading={loading}
          columns={runColumns}
          dataSource={runs}
          pagination={{ pageSize: 20, showTotal: (t) => `共 ${t} 次` }}
        />
      </Card>

      <Drawer open={open} onClose={() => setOpen(false)} title={title} width={900}>
        <Table<JudgeCase>
          rowKey="id"
          size="small"
          columns={caseColumns}
          dataSource={cases}
          expandable={{
            expandedRowRender: (c) => (
              <div style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
                <Typography.Text type="secondary">回复：</Typography.Text>
                <div>{c.reply}</div>
              </div>
            ),
          }}
          pagination={false}
        />
      </Drawer>
    </>
  );
}
