import { useEffect, useState } from 'react';
import { Button, Card, Drawer, Input, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { adminApi, type AdminConversation, type AdminMessage } from '../lib/api';

export function ConversationsPage() {
  const [data, setData] = useState<AdminConversation[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [msgs, setMsgs] = useState<AdminMessage[]>([]);
  const [title, setTitle] = useState('');

  const load = async (p: number, s: number, q?: string) => {
    setLoading(true);
    try {
      const res = await adminApi.conversations({ page: p, size: s, q });
      setData(res.items);
      setTotal(res.total);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load(1, 20);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const openMessages = async (c: AdminConversation) => {
    setTitle(c.title || c.conversationId);
    setMsgs([]);
    setOpen(true);
    try {
      setMsgs(await adminApi.messages(c.conversationId));
    } catch (e) {
      console.warn('加载消息失败', e);
    }
  };

  const columns: ColumnsType<AdminConversation> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '会话 ID', dataIndex: 'conversationId', width: 150, render: (v: string) => v.slice(0, 12) },
    { title: '用户', dataIndex: 'username', width: 130, render: (v?: string) => v || '-' },
    { title: '标题', dataIndex: 'title', ellipsis: true },
    { title: '消息数', dataIndex: 'messageCount', width: 90 },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      width: 180,
      render: (v?: string) => (v ? new Date(v).toLocaleString() : '-'),
    },
    {
      title: '操作',
      width: 90,
      render: (_, c) => (
        <Button size="small" onClick={() => openMessages(c)}>
          查看
        </Button>
      ),
    },
  ];

  return (
    <Card
      title="会话"
      extra={
        <Input.Search
          placeholder="搜索标题 / 会话 ID"
          allowClear
          style={{ width: 240 }}
          onSearch={(v) => {
            setPage(1);
            load(1, size, v || undefined);
          }}
        />
      }
    >
      <Table<AdminConversation>
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={data}
        pagination={{
          current: page,
          pageSize: size,
          total,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (p, s) => {
            setPage(p);
            setSize(s);
            load(p, s);
          },
        }}
      />

      <Drawer open={open} onClose={() => setOpen(false)} title={title} width={680}>
        {msgs.length === 0 && <Typography.Text type="secondary">（无消息）</Typography.Text>}
        {msgs.map((m) => (
          <div key={m.id} style={{ marginBottom: 16 }}>
            <Tag color={m.role === 'ai' ? 'blue' : 'green'}>{m.role}</Tag>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {m.createdAt ? new Date(m.createdAt).toLocaleString() : ''}
            </Typography.Text>
            <div style={{ whiteSpace: 'pre-wrap', marginTop: 4 }}>{m.content}</div>
            {m.productIds && (
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                商品: {m.productIds}
              </Typography.Text>
            )}
          </div>
        ))}
      </Drawer>
    </Card>
  );
}
