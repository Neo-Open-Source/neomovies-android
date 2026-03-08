import React from 'react'
import { createRoot } from 'react-dom/client'
import '@jetbrains/ring-ui-built/components/style.css'
import './styles.css'
import App from './App'
import { BrowserRouter } from 'react-router-dom'

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>
)
