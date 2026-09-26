# Red de Alimentos Solidarios — Mobile (Android)

Aplicación móvil Android de la plataforma **Red de Alimentos Solidarios**, desarrollada en **Kotlin**, que consume el backend en Django.

## Descripción

Plataforma para facilitar la donación de alimentos: los donantes registran alimentos disponibles, los beneficiarios consultan y solicitan donaciones, y los administradores validan, gestionan y registran las entregas.

## Roles del sistema

| Rol | Puede... |
|---|---|
| Donante | Registrar alimentos disponibles y su cantidad |
| Beneficiario | Consultar donaciones y realizar solicitudes |
| Administrador | Validar donaciones, gestionar solicitudes y registrar entregas |

## Tecnologías

- **Kotlin** (Android nativo)
- Retrofit (o similar) para consumo del API REST
- Backend en Django

## Requisitos previos

- Android Studio (última versión estable)
- SDK de Android configurado
- Backend corriendo (local o desplegado) — ver repo `backend`

## Instalación

```bash
git clone <url-del-repo>
```

Abre el proyecto en Android Studio y deja que sincronice las dependencias de Gradle.

## Configuración

En `local.properties` o en tu archivo de constantes de red, define la URL base del backend:

```properties
API_BASE_URL=http://10.0.2.2:8000/api/   # 10.0.2.2 apunta al localhost desde el emulador
```

Ajusta la URL si el backend está desplegado en un servidor.

## Ejecutar la app

1. Conecta un dispositivo físico o inicia un emulador desde Android Studio.
2. Ejecuta el proyecto (`Run ▶`).

## Estructura del proyecto

```
app/src/main/
├── java/.../ui/          # Activities y Fragments por pantalla
├── java/.../network/     # Cliente Retrofit, servicios de API
├── java/.../model/       # Modelos de datos (Usuario, Donacion, Solicitud, etc.)
├── java/.../auth/        # Manejo de login y JWT
└── res/layout/           # XMLs de las pantallas
```

## Funcionalidades principales

- Login según rol (Donante, Beneficiario, Administrador)
- Registro de donaciones con foto, lugar de recogida y fecha límite
- Consulta y solicitud de donaciones disponibles para beneficiarios
- Panel de administrador: validar donaciones, gestionar solicitudes, registrar entregas
- Notificaciones dentro de la app cuando cambia el estado de una donación o solicitud

## Autores

- Miguel Angel Gomez

## Licencia

Este proyecto está bajo la licencia MIT — ver el archivo [LICENSE](LICENSE) para más detalles.