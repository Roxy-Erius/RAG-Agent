import { useEffect, useState } from 'react';
import { Card, Descriptions, Input, Table, Tag, Typography } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { adminApi, type LogEvent } from '../lib/api';

interface Query {
  level?: string;
  category?: string;
  logger?: string;
  q?: string;
}

/**
 * 把 grep 式命令解析成查询条件：
 *   level=ERROR category=AUTH logger=ChatService 超时
 *   grep 超时 level=ERROR
 * key=value / key:value 作为过滤；其余词作为关键词；grep 前缀忽略。
 */
function parseQuery(cmd: string): Query {
  const out: Query = {};
  const words: string[] = [];
  for (let tok of cmd.trim().split(/\s+/)) {
    if (!tok) continue;
    tok = tok.replace(/^["']|["']$/g, ''); // 去掉包裹的引号
    if (!tok) continue;
    const m = tok.match(/^(level|category|logger)[=:](.+)$/i);
    if (m) {
      out[m[1].toLowerCase() as keyof Query] = m[2];
    } else if (tok.toLowerCase() === 'grep') {
      /* 忽略 grep 前缀 */
    } else {
      words.push(tok);
    }
  }
  if (words.length) out.q = words.join(' ');
  return out;
}

function describeQuery(q: Query): string {
  const parts = [
    q.level && `level=${q.level}`,
    q.category && `category=${q.category}`,
    q.logger && `logger=${q.logger}`,
    q.q && `关键词="${q.q}"`,
  ].filter(Boolean);
  return parts.length ? parts.join(' · ') : '全部';
}

const HINT =
  'level=ERROR · category=AUTH(ERROR/AUTH/ORDER/CHAT/CART) · logger=ChatService · 其余词=关键词(跨字段模糊匹配 内容/级别/分类/来源) · 支持 grep 前缀，如 grep ERROR';

export function LogsPage() {
  const [cmd, setCmd] = useState('');
  const [query, setQuery] = useState<Query>({});
  const [data, setData] = useState<LogEvent[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(50);
  const [loading, setLoading] = useState(false);

  const load = async (p: number, s: number, q: Query) => {
    setLoading(true);
    try {
      const res = await adminApi.logs({ page: p, size: s, ...q });
      setData(res.items);
      setTotal(res.total);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load(1, 50, {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const runQuery = () => {
    const q = parseQuery(cmd);
    setQuery(q);
    setPage(1);
    load(1, size, q);
  };

  const columns: ColumnsType<LogEvent> = [
    {
      title: '时间',
      dataIndex: 'ts',
      width: 180,
      render: (v: string) => (v ? new Date(v).toLocaleString() : '-'),
    },
    {
      title: '级别',
      dataIndex: 'level',
      width: 90,
      render: (v: string) => <Tag color={v === 'ERROR' ? 'red' : 'blue'}>{v}</Tag>,
    },
    { title: '分类', dataIndex: 'category', width: 100 },
    { title: '来源', dataIndex: 'logger', width: 200, ellipsis: true },
    { title: '内容', dataIndex: 'message', ellipsis: true },
  ];

  return (
    <Card title="日志（ERROR + 关键动作）">
      <Input
        size="large"
        allowClear
        prefix={<SearchOutlined />}
        placeholder="grep 式查询，如：level=ERROR category=AUTH 超时"
        value={cmd}
        onChange={(e) => setCmd(e.target.value)}
        onPressEnter={runQuery}
        style={{ marginBottom: 8 }}
      />
      <Typography.Paragraph type="secondary" style={{ fontSize: 12, marginBottom: 4 }}>
        {HINT}
      </Typography.Paragraph>
      <Typography.Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 12 }}>
        当前条件：<Typography.Text strong>{describeQuery(query)}</Typography.Text>（共 {total} 条）
      </Typography.Text>

      <Table<LogEvent>
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={data}
        expandable={{
          // 点击行展开：查看完整内容（长日志不再被省略号截断）
          expandedRowRender: (r) => (
            <Descriptions size="small" column={1} bordered>
              <Descriptions.Item label="时间">{new Date(r.ts).toLocaleString()}</Descriptions.Item>
              <Descriptions.Item label="级别">
                <Tag color={r.level === 'ERROR' ? 'red' : 'blue'}>{r.level}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="分类">{r.category}</Descriptions.Item>
              <Descriptions.Item label="来源">{r.logger || '-'}</Descriptions.Item>
              <Descriptions.Item label="线程">{r.thread || '-'}</Descriptions.Item>
              <Descriptions.Item label="完整内容">
                <Typography.Text style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
                  {r.message || '-'}
                </Typography.Text>
              </Descriptions.Item>
            </Descriptions>
          ),
        }}
        pagination={{
          current: page,
          pageSize: size,
          total,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (p, s) => {
            setPage(p);
            setSize(s);
            load(p, s, query);
          },
        }}
      />
    </Card>
  );
}
