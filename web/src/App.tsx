import { Routes, Route } from 'react-router-dom';
import { Layout } from './components/layout/Layout';
import { RequireAuth } from './components/layout/RequireAuth';
import { WelcomePage } from './pages/WelcomePage';
import { HomePage } from './pages/HomePage';
import { ProductDetailPage } from './pages/ProductDetailPage';
import { CartPage } from './pages/CartPage';
import { OrdersPage } from './pages/OrdersPage';
import { LoginPage } from './pages/LoginPage';

function App() {
  return (
    <Routes>
      {/* 入口 */}
      <Route path="/" element={<WelcomePage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route element={<Layout />}>
        <Route path="/home" element={<HomePage />} />
        <Route path="/product/:id" element={<ProductDetailPage />} />
        <Route path="/cart" element={<RequireAuth anon><CartPage /></RequireAuth>} />
        <Route path="/orders" element={<RequireAuth><OrdersPage /></RequireAuth>} />
      </Route>
    </Routes>
  );
}

export default App;
