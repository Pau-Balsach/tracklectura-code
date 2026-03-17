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

## 🚀 Descarga e Instalación

La forma más sencilla de usar TrackLectura en Windows es descargando el instalador oficial desde nuestra sección de lanzamientos:

👉 **[Descargar TrackLectura v1.3.8 (.exe)](https://github.com/tracklectura/tracklectura-code/releases/latest/download/TrackLectura-1.3.8.exe)**

> **Nota:** Si prefieres no instalar el programa en tu equipo o utilizas otro sistema operativo (Linux / macOS), puedes usar los comandos de compilación y ejecución que se detallan más abajo.

## Requisitos

- Java 17 o superior

## 🛠️ Compilar y ejecutar (Alternativa a la instalación)

Si no deseas instalar el programa usando el ejecutable o estás en un sistema operativo diferente a Windows, puedes compilar y ejecutar el código fuente directamente usando la terminal. 

**Windows (PowerShell)**
```powershell
if (!(Test-Path out)) { mkdir out }
$libs = (Get-ChildItem lib/*.jar | Select-Object -ExpandProperty FullName) -join ';'
javac -cp "$libs;out" -d out (Get-ChildItem -Path src -Recurse -Filter *.java | Select-Object -ExpandProperty FullName)
java -cp "$libs;out" main.TrackerApp
```

**Linux / macOS**

```bash

mkdir -p out
javac -cp "lib/*:out" -d out $(find src -name "*.java")
java -cp "lib/*:out" main.TrackerApp

```

## Documentación

- [Manual en Español](docs/manual_es.md)

- [Manual in English](docs/manual_en.md)

- [Manuel en Français](docs/manual_fr.md)



## Licencia



MIT License — ver archivo [LICENSE](LICENSE)
