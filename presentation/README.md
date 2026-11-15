# Presentación

Diapositivas interactivas para exponer la comparación entre Huffman y LZW.

[Ver presentación en línea](https://luis63e.github.io/huffman-vs-lzw/presentation/).

## Abrir en tu equipo

Desde la raíz del repositorio:

```bash
python3 -m http.server 8000 --directory presentation
```

Abrí <http://localhost:8000/>. No abras `index.html` directamente: el navegador bloquea las diapositivas con `file://`.

## Controles

| Tecla | Acción |
|---|---|
| Flecha derecha o espacio | Avanzar |
| Flecha izquierda | Diapositiva anterior |
| `P` | Modo presentador, con notas y temporizador |
| `F` | Pantalla completa |
| Retroceso | Volver de la diapositiva de detalle |

## Editar

Las diapositivas están en `composition/index.html`. Si modificás el bloque JSON de configuración, copiá el cambio también a `index.html`.

No requiere instalación ni conexión a Internet. Para validar cambios:

```bash
npx --yes hyperframes@0.9.41 lint presentation/composition
```

Ejecutá la validación desde la raíz del repositorio.
