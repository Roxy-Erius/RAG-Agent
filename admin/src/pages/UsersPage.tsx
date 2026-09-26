import { useEffect, useState } from 'react';
import { Card, Input, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { adminApi, type AdminUser } from '../lib/api';

export function UsersPage() {
  const [data, setData] = useState<AdminUser[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [loading, setLoading] = useState(false);

  const load = async (p: number, s: number, q?: string) => {
    setLoading(true);
    try {
      const res = await adminApi.users({ page: p, size: s, q });
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

  const columns: ColumnsType<AdminUser> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '用户名', dataIndex: 'username' },
    {
      title: '角色',
      dataIndex: 'role',
      width: 100,
      render: (r: string) => <Tag color={r === 'ADMIN' ? 'gold' : 'default'}>{r}</Tag>,
    },
    { title: '会话数', dataIndex: 'conversationCount', width: 90 },
    { title: '订单数', dataIndex: 'orderCount', width: 90 },
    {
      title: '注册时间',
      dataIndex: 'createdAt',
      width: 180,
      render: (v?: string) => (v ? new Date(v).toLocaleString() : '-'),
    },
  ];

  return (
    <Card
      title="用户"
      extra={
        <Input.Search
          placeholder="搜索用户名"
          allowClear
          style={{ width: 220 }}
          onSearch={(v) => {
            setPage(1);
            load(1, size, v || undefined);
          }}
        />
      }
    >
      <Table<AdminUser>
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
    </Card>
  );
}
