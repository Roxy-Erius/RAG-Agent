import { useState } from 'react';
import { Breadcrumb, Button, Layout, Menu, Space } from 'antd';
import {
  AppstoreOutlined,
  TeamOutlined,
  MessageOutlined,
  ShoppingOutlined,
  ThunderboltOutlined,
  FileTextOutlined,
  DashboardOutlined,
  ExperimentOutlined,
  HomeOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
} from '@ant-design/icons';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { clearToken } from '../lib/auth';

const { Header, Sider, Content } = Layout;

const MENU = [
  { key: '/', icon: <AppstoreOutlined />, label: '概览' },
  { key: '/users', icon: <TeamOutlined />, label: '用户' },
  { key: '/conversations', icon: <MessageOutlined />, label: '会话' },
  { key: '/orders', icon: <ShoppingOutlined />, label: '订单' },
  { key: '/behaviors', icon: <ThunderboltOutlined />, label: '行为' },
  { key: '/logs', icon: <FileTextOutlined />, label: '日志' },
  { key: '/judge', icon: <ExperimentOutlined />, label: '评测' },
  { key: '/monitoring', icon: <DashboardOutlined />, label: '监控' },
];

export function AdminLayout() {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [collapsed, setCollapsed] = useState(false);
  const current = MENU.find((m) => m.key === pathname);

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {/* 深色侧边栏（vue-element-admin 风格 #304156） */}
      <Sider
        collapsible
        collapsed={collapsed}
        trigger={null}
        width={210}
        breakpoint="lg"
        collapsedWidth={64}
        style={{ background: '#304156' }}
      >
        <div
          style={{
            height: 56,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#fff',
            fontWeight: 700,
            fontSize: collapsed ? 15 : 16,
            letterSpacing: 1,
            background: '#2b3a4d',
            whiteSpace: 'nowrap',
            overflow: 'hidden',
          }}
        >
          {collapsed ? 'RAG' : 'RAG 导购后台'}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[pathname]}
          items={MENU}
          onClick={(e) => navigate(e.key)}
        />
      </Sider>

      <Layout>
        {/* 白色顶栏 + 面包屑 */}
        <Header
          style={{
            background: '#fff',
            height: 56,
            lineHeight: '56px',
            padding: '0 16px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            borderBottom: '1px solid #e8e8e8',
          }}
        >
          <Space size={12}>
            <Button
              type="text"
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => setCollapsed((v) => !v)}
            />
            <Breadcrumb
              style={{ lineHeight: 'normal' }}
              items={[
                { title: <><HomeOutlined /> 首页</> },
                { title: current?.label ?? '' },
              ]}
            />
          </Space>
          <Button
            icon={<LogoutOutlined />}
            onClick={() => {
              clearToken();
              navigate('/login');
            }}
          >
            退出
          </Button>
        </Header>

        <Content style={{ padding: 16 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
