import { Outlet } from 'react-router-dom';
import { Navbar } from './Navbar';
import { AgentDock } from '../agent/AgentDock';

export function Layout() {
  return (
    <div className="min-h-screen flex flex-col">
      <Navbar />
      <main className="flex-1 max-w-6xl mx-auto w-full px-4 py-5">
        <Outlet />
      </main>
      {/* 全局 Agent 导购浮窗：粘在所有页面之上 */}
      <AgentDock />
    </div>
  );
}
