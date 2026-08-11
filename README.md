# PdfKit Pro

Aplicación Android para leer, convertir, firmar, unir, escanear y editar archivos PDF. Todo el procesamiento ocurre **en tu dispositivo**: sin publicidad, sin cuentas y sin enviar documentos a servidores.

**Versión actual:** 1.1.3

## Características

| Herramienta | Descripción |
|---|---|
| **Leer PDF** | Visor con zoom, búsqueda de texto, ir a página, selección/copia de texto y recuerdo de la última página leída |
| **PDF a imagen** | Convierte páginas a JPG o PNG, con rango personalizado |
| **Firmar PDF** | Firma dibujada, iniciales, texto y fecha sobre el documento |
| **Rotar páginas** | Gira páginas individualmente (90°, 180°) y guarda el PDF completo |
| **Reordenar páginas** | Cambia el orden de las páginas y genera un documento nuevo |
| **Eliminar páginas** | Marca páginas (incluye detección de páginas en blanco) y genera un PDF nuevo |
| **Unir PDFs** | Combina varios archivos en uno solo, con orden configurable |
| **Escanear a PDF** | Escaneo con detección de bordes (ML Kit) o imágenes desde galería |

### Visor PDF

- Menú de herramientas (⋮): descargar copia, firmar, convertir a imagen, rotar, reordenar y eliminar páginas
- **Descargar copia** del documento abierto en `Descargas/PdfKit Pro`
- Zoom, búsqueda, ir a página y copia de texto seleccionado

### Otras funciones

- Archivos recientes con nombre, tamaño y fecha
- Abrir PDFs desde el administrador de archivos o con **Abrir con / Compartir**
- Guardar sobre el original (si hay permiso de escritura) o como copia en Descargas
- Resultados en `Descargas/PdfKit Pro` e imágenes en `Imágenes/PdfKit Pro`
- Pantalla **Acerca de** con versión, datos del desarrollador y créditos open source
- Interfaz en español con Jetpack Compose y Material 3

## Privacidad

- No se requiere permiso de Internet
- Los PDFs e imágenes no se suben a ningún servidor
- Los metadatos de recientes se guardan solo en el dispositivo (DataStore)

Consulta la [política de privacidad](PRIVACY_POLICY.md) para más detalle.

## Licencia

Este proyecto está bajo la licencia [MIT](LICENSE).

## Requisitos

- Android 7.0 (API 24) o superior
- Android Studio con soporte para Compose

## Compilar el proyecto

```bash
git clone https://github.com/hhuezo/PdfKitPro.git
cd PdfKitPro
./gradlew assembleDebug
```

El APK de debug se genera en `app/build/outputs/apk/debug/`.

### Firma de release (opcional)

Añade estas propiedades en `local.properties`:

```properties
RELEASE_STORE_FILE=/ruta/a/tu/keystore.jks
RELEASE_STORE_PASSWORD=tu_contraseña
RELEASE_KEY_ALIAS=tu_alias
RELEASE_KEY_PASSWORD=tu_contraseña
```

Luego:

```bash
./gradlew assembleRelease
```

## Stack tecnológico

- **Kotlin** + **Jetpack Compose** (Material 3)
- **[PdfBox Android](https://github.com/TomRoush/PdfBox-Android)** — manipulación de PDF
- **ML Kit Document Scanner** — escaneo de documentos
- **DataStore Preferences** — archivos recientes
- **AndroidX Activity, Lifecycle, ExifInterface**

## Estructura del proyecto

```
app/src/main/java/com/hhuezo/pdfconverter/
├── MainActivity.kt          # Navegación principal
├── data/                    # Repositorio de recientes
├── pdf/                     # Lógica PDF (unir, firmar, rotar, reordenar…)
├── ui/
│   ├── about/               # Acerca de / créditos
│   ├── home/                # Inicio y recientes
│   ├── reader/              # Lector PDF
│   ├── tools/               # Pantalla de herramientas
│   ├── sign/ merge/ scan/   # Flujos por herramienta
│   ├── rotate/ reorder/ …   # Edición de páginas
│   └── theme/               # Colores y tipografía
└── util/                    # Guardado, permisos, utilidades
```

## Permisos

| Permiso | Uso |
|---|---|
| Cámara | Escanear documentos |
| Almacenamiento (API ≤ 28) | Guardar archivos en dispositivos antiguos |

El acceso a PDFs e imágenes se realiza mediante el selector de archivos del sistema (SAF), sin permisos amplios de almacenamiento en versiones recientes de Android.

## Contacto

- **Repositorio:** [github.com/hhuezo/PdfKitPro](https://github.com/hhuezo/PdfKitPro)
- **Issues:** [github.com/hhuezo/PdfKitPro/issues](https://github.com/hhuezo/PdfKitPro/issues)
- **Autor:** [Hugo Alexander Huezo Barahona](https://github.com/hhuezo)
- **LinkedIn:** [hugo-alexander-huezo-barahona](https://www.linkedin.com/in/hugo-alexander-huezo-barahona-721b89213/)

---

**Application ID:** `com.hhuezo.pdfconverter`
