import { useState } from 'react';
import { Button, Card, Space, Typography } from 'antd';
import { ReloadOutlined, ExportOutlined } from '@ant-design/icons';

const GRAFANA = 'http://localhost:3000';
// kiosk：隐藏 Grafana 导航，纯看板；refresh：自动刷新
const DASH_URL = `${GRAFANA}/d/rag-agent?kiosk&refresh=10s&from=now-1h&to=now`;

/**
 * 性能监控页：直接嵌入 Grafana 看板。
 * 系统指标(JVM/HTTP/连接池) + 业务指标(RAG 检索耗时/LLM 首token/SSE 连接) 都在这。
 */
export function MonitoringPage() {
  const [k, setK] = useState(0);

  return (
    <Card
      title="性能监控（Grafana）"
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => setK((v) => v + 1)}>
            重载
          </Button>
          <Button
            type="link"
            icon={<ExportOutlined />}
            href={GRAFANA}
            target="_blank"
            rel="noreferrer"
          >
            新窗口打开 Grafana
          </Button>
        </Space>
      }
    >
      <Typography.Paragraph type="secondary" style={{ marginTop: 0 }}>
        数据链路：后端埋点 → Actuator 暴露 → Prometheus 采集 → Grafana 展示。含系统指标（JVM / 接口
        QPS·延迟 / 连接池）与业务指标（RAG 检索耗时 / LLM 首 token 延迟 / SSE 活跃连接 / 加购成功率）。
      </Typography.Paragraph>
      <iframe
        key={k}
        src={DASH_URL}
        title="Grafana Dashboard"
        style={{ width: '100%', height: 1320, border: 0 }}
      />
    </Card>
  );
}
