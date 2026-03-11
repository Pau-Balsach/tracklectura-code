# 📖 TrackLectura

Aplicación de escritorio para registrar y analizar tus hábitos de lectura. Permite llevar un seguimiento de sesiones, páginas leídas, velocidad lectora y progreso por libro, con sincronización en la nube mediante Supabase.

## Características

- Registro de sesiones de lectura (páginas, tiempo, velocidad)
- Estadísticas y gráficas por libro
- Sincronización en la nube con Supabase
- Modo offline (sin conexión)
- Exportación a CSV e imagen
- Tema claro / oscuro
- Búsqueda de portadas automática

## Requisitos

- Java 17 o superior
- Cuenta en [Supabase](https://supabase.com) (gratuita)

## Configuración de credenciales

Las credenciales de Supabase **no están incluidas en el repositorio**. La primera vez que ejecutes la aplicación, ve a Ajustes e introduce:

- **Supabase URL**: la URL de tu proyecto (ej. `https://xxxxx.supabase.co`)
- **Supabase Anon Key**: la clave pública de tu proyecto

Estos valores se guardan cifrados en local en `config.properties`, que está excluido del repositorio mediante `.gitignore`.

## Compilar y ejecutar

```bash
javac -cp lib/* -d out src/**/*.java
java -cp out:lib/* main.TrackerApp
```

## Licencia

MIT License — ver archivo [LICENSE](LICENSE)
