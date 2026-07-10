import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App.jsx'

import "./styles/common.css";
import "./styles/auth.css";
import "./styles/posts.css";
import "./styles/user.css";

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
