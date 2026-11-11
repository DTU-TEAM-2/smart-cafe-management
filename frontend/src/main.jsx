import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import MenuManagement from './menu/MenuManagement.jsx';
import './styles.css';

createRoot(document.getElementById('root')).render(<StrictMode><MenuManagement /></StrictMode>);
