import { useEffect, useState } from 'react';
import { Card, Input, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { adminApi, type AdminBehavior } from '../lib/api';

export function BehaviorsPage() {
  const [data, setData] = useState<AdminBehavior[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [loading, setLoading] = useState(false);

  const load = async (p: number, s: number, actionType?: string) => {
    setLoading(true);
    try {
      const res = await adminApi.behaviors({ page: p, size: s, actionType });
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

  const columns: ColumnsType<AdminBehavior> = [
    { title: 'ID', dataIndex: 'id', width: 90 },
    { title: '用户 ID', dataIndex: 'userId', width: 100 },
    { title: '商品 ID', dataIndex: 'productId', width: 160 },
    {
      title: '行为',
      dataIndex: 'actionType',
      width: 140,
      render: (v: string) => <Tag color="blue">{v}</Tag>,
    },
    {
      title: '时间',
      dataIndex: 'createdAt',
      render: (v?: string) => (v ? new Date(v).toLocaleString() : '-'),
    },
  ];

  return (
    <Card
      title="用户行为"
      extra={
        <Input.Search
          placeholder="按行为类型过滤，如 view / add_cart"
          allowClear
          style={{ width: 260 }}
          onSearch={(v) => {
            setPage(1);
            load(1, size, v || undefined);
          }}
        />
      }
    >
      <Table<AdminBehavior>
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
