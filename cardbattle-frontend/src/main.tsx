import React from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './pages/App';
import './styles/app.css';
import './styles/themes.css';
import './styles/skins.css';

createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
