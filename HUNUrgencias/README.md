# Aviso HUN Urgencias - Android

Primera versión de una app Android que vigila el endpoint público de avisos del HUN y notifica cuando aparece el código de paciente indicado.

## Endpoint usado

`https://sigue.navarra.es/GN.Sanidad.Sigue.Movil.WebUI/seguimiento/avisos/2`

## Funcionamiento

- Introducir el código, por ejemplo `LV924`.
- Pulsar **INICIAR VIGILANCIA**.
- La app mantiene un servicio en primer plano y consulta cada 15 segundos.
- Si aparece una coincidencia exacta en el campo `Paciente`, muestra una notificación normal con la ubicación/descripción.
- Evita repetir el mismo aviso usando el campo `Fecha`.
- Pulsar **DETENER** para parar la vigilancia.

Configuración: minSdk 26, targetSdk 35, Java 17.
