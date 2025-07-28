import React, { useState, useEffect } from 'react'
import axios from 'axios'
import './App.css'

function App() {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(false)

  // Configuración de axios para conectar al backend
  const api = axios.create({
    baseURL: 'http://localhost:8080',
    headers: {
      'Content-Type': 'application/json',
    },
  })

  const testConnection = async () => {
    setLoading(true)
    try {
      // Ejemplo de llamada a un endpoint del backend
      const response = await api.get('/api/users') // Ajusta según tus endpoints
      setData(response.data)
      console.log('Conexión exitosa:', response.data)
    } catch (error) {
      console.error('Error conectando al backend:', error)
      setData({ error: 'Error conectando al backend' })
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="App">
      <header className="App-header">
        <h1>CRASA Frontend</h1>
        <p>Conectando con Spring Boot Backend</p>
        
        <button 
          onClick={testConnection}
          disabled={loading}
          style={{
            padding: '10px 20px',
            fontSize: '16px',
            backgroundColor: '#007bff',
            color: 'white',
            border: 'none',
            borderRadius: '5px',
            cursor: loading ? 'not-allowed' : 'pointer'
          }}
        >
          {loading ? 'Conectando...' : 'Probar Conexión'}
        </button>

        {data && (
          <div style={{ marginTop: '20px', textAlign: 'left' }}>
            <h3>Respuesta del Backend:</h3>
            <pre style={{ 
              backgroundColor: '#f8f9fa', 
              padding: '10px', 
              borderRadius: '5px',
              overflow: 'auto',
              maxWidth: '500px'
            }}>
              {JSON.stringify(data, null, 2)}
            </pre>
          </div>
        )}
      </header>
    </div>
  )
}

export default App 