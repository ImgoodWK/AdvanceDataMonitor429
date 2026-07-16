import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import './styles/global.css';
import './styles/bold-styles.css';
import './styles/bold-styles-batch2.css';
import './styles/layout-batch3.css';
import './styles/bold-styles-batch3.css';
import './styles/bold-styles-batch4.css';
import './styles/bold-styles-batch5.css';
import './styles/effects-motion.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>
);
