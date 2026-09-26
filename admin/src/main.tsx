import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { ConfigProvider, App as AntApp } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import 'antd/dist/reset.css';
import App from './App';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#409eff',   // vue-element-admin 主色
          colorBgLayout: '#f0f2f5',  // 灰色内容底
          borderRadius: 4,
        },
        components: {
          // 深色侧边栏配色（对齐 #304156 风格）
          Menu: {
            darkItemBg: '#304156',
            darkSubMenuItemBg: '#263445',
            darkItemSelectedBg: '#263445',
            darkItemColor: '#bfcbd9',
            darkItemSelectedColor: '#409eff',
            darkItemHoverBg: '#263445',
          },
        },
      }}
    >
      <AntApp>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </AntApp>
    </ConfigProvider>
  </React.StrictMode>
);
