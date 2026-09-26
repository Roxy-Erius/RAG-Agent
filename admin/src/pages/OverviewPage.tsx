import { useEffect, useState } from 'react';
import { Card, Col, Row, Spin, Statistic } from 'antd';
import ReactECharts from 'echarts-for-react';
import { adminApi, type Overview, type TimeseriesPoint } from '../lib/api';

export function OverviewPage() {
  const [ov, setOv] = useState<Overview | null>(null);
  const [ts, setTs] = useState<TimeseriesPoint[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      try {
        const [o, t] = await Promise.all([adminApi.overview(), adminApi.timeseries(30)]);
        setOv(o);
        setTs(t);
      } catch (e) {
        console.warn('加载概览失败', e);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  if (loading || !ov) return <Spin />;

  const option = {
    tooltip: { trigger: 'axis' },
    legend: { data: ['新增用户', '新增会话', '新增订单'], top: 0, itemGap: 24 },
    // containLabel: true → 轴标签算进边距，避免与图表/网格线重叠
    grid: { left: 8, right: 20, top: 48, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: ts.map((p) => p.date),
      axisLabel: { hideOverlap: true, formatter: (v: string) => v.slice(5) }, // 只显示 MM-DD
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { type: 'dashed', color: '#eee' } },
    },
    series: [
      { name: '新增用户', type: 'line', smooth: true, symbol: 'none', data: ts.map((p) => p.newUsers) },
      { name: '新增会话', type: 'line', smooth: true, symbol: 'none', data: ts.map((p) => p.conversations) },
      { name: '新增订单', type: 'line', smooth: true, symbol: 'none', data: ts.map((p) => p.orders) },
    ],
  };

  const cards = [
    { title: '用户数', value: ov.userCount, today: ov.todayNewUsers },
    { title: '会话数', value: ov.conversationCount, today: ov.todayConversations },
    { title: '消息数', value: ov.messageCount },
    { title: '订单数', value: ov.orderCount, today: ov.todayOrders },
    { title: 'GMV (元)', value: ov.gmv, precision: 2 },
    { title: '商品数', value: ov.productCount },
    { title: '行为数', value: ov.behaviorCount },
  ];

  return (
    <>
      <Row gutter={[16, 16]}>
        {cards.map((c) => (
          <Col key={c.title} xs={12} sm={8} lg={6} xl={5}>
            <Card>
              <Statistic
                title={c.title}
                value={c.value}
                precision={c.precision}
                suffix={c.today ? <span style={{ fontSize: 12, color: '#999' }}>今日 +{c.today}</span> : undefined}
              />
            </Card>
          </Col>
        ))}
      </Row>
      <Card title="近 30 天活跃量" style={{ marginTop: 16 }}>
        <ReactECharts option={option} style={{ height: 400 }} />
      </Card>
    </>
  );
}
