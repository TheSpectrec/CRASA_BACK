# CRASA Frontend

Frontend React para conectar con el backend Spring Boot de CRASA.

## Instalación

```bash
npm install
```

## Desarrollo

```bash
npm run dev
```

El frontend se ejecutará en `http://localhost:3000`

## Construcción

```bash
npm run build
```

## Configuración

El frontend está configurado para conectarse al backend en `http://localhost:8080`.

### Proxy Configuration

El archivo `vite.config.js` incluye un proxy que redirige las peticiones `/api/*` al backend:

```javascript
proxy: {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true,
    rewrite: (path) => path.replace(/^\/api/, '')
  }
}
```

## Endpoints disponibles

El frontend puede conectarse a todos los endpoints del backend:

- `/api/users` - Gestión de usuarios
- `/api/products` - Gestión de productos
- `/api/customers` - Gestión de clientes
- `/api/ventas` - Gestión de ventas
- Y todos los demás endpoints disponibles en el backend

## CORS

El backend ya está configurado para permitir conexiones desde `http://localhost:3000`. 