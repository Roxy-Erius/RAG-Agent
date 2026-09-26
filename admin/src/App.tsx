import type { ReactNode } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { AdminLayout } from './components/AdminLayout';
import { LoginPage } from './pages/LoginPage';
import { OverviewPage } from './pages/OverviewPage';
import { UsersPage } from './pages/UsersPage';
import { ConversationsPage } from './pages/ConversationsPage';
import { OrdersPage } from './pages/OrdersPage';
import { BehaviorsPage } from './pages/BehaviorsPage';
import { LogsPage } from './pages/LogsPage';
import { JudgePage } from './pages/JudgePage';
import { MonitoringPage } from './pages/MonitoringPage';
import { getToken } from './lib/auth';

function RequireAdmin({ children }: { children: ReactNode }) {
  return getToken() ? <>{children}</> : <Navigate to="/login" replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireAdmin>
            <AdminLayout />
          </RequireAdmin>
        }
      >
        <Route path="/" element={<OverviewPage />} />
        <Route path="/users" element={<UsersPage />} />
        <Route path="/conversations" element={<ConversationsPage />} />
        <Route path="/orders" element={<OrdersPage />} />
        <Route path="/behaviors" element={<BehaviorsPage />} />
        <Route path="/logs" element={<LogsPage />} />
        <Route path="/judge" element={<JudgePage />} />
        <Route path="/monitoring" element={<MonitoringPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
