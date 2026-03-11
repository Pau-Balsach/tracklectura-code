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
mkdir out
javac -cp lib/* -d out (Get-ChildItem -Recurse -Filter *.java | Select-Object -ExpandProperty FullName)
java -cp "out;lib/*" main.TrackerApp
```

**Linux / macOS**
```bash
mkdir -p out
javac -cp "lib/*" -d out $(find . -name "*.java")
java -cp "out:lib/*" main.TrackerApp
```

## Licencia

MIT License — ver archivo [LICENSE](LICENSE)
