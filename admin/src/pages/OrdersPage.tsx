import { useEffect, useState } from 'react';
import { Card, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { adminApi, type AdminOrder } from '../lib/api';

export function OrdersPage() {
  const [data, setData] = useState<AdminOrder[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [loading, setLoading] = useState(false);

  const load = async (p: number, s: number) => {
    setLoading(true);
    try {
      const res = await adminApi.orders({ page: p, size: s });
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

  const columns: ColumnsType<AdminOrder> = [
    { title: '订单号', dataIndex: 'orderId', width: 200 },
    { title: '用户 ID', dataIndex: 'userId', width: 90, render: (v?: number) => v ?? '-' },
    {
      title: '金额 (元)',
      dataIndex: 'totalAmount',
      width: 110,
      render: (v: number) => v?.toFixed(2),
    },
    { title: '件数', dataIndex: 'itemCount', width: 80 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (s: string) => <Tag color={s === 'paid' ? 'green' : 'orange'}>{s}</Tag>,
    },
    {
      title: '下单时间',
      dataIndex: 'createdAt',
      width: 180,
      render: (v?: string) => (v ? new Date(v).toLocaleString() : '-'),
    },
  ];

  return (
    <Card title="订单">
      <Table<AdminOrder>
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
