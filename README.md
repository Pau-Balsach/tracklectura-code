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

## Code signing policy

Free code signing provided by SignPath.io, certificate by SignPath Foundation.

- Committers and reviewers: [tracklectura](https://github.com/tracklectura)
- Approvers: [tracklectura](https://github.com/tracklectura)

Privacy policy: This program will not transfer any information to other networked
systems unless specifically requested by the user or the person installing or
operating it.

## Requisitos

- Java 17 o superior

## Compilar y ejecutar

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
