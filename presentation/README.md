# Presentación

Diapositivas para exponer la comparación entre Huffman y LZW en cinco partes, una por persona: 2 diapositivas y 1 a 2 minutos cada una.

| Parte | Tema | Diapositivas |
|---|---|---|
| 1 | Contexto | Portada, la idea |
| 2 | Huffman | Frecuencias, árbol |
| 3 | LZW | Diccionario, ejemplo |
| 4 | Experimentos | Metodología, tiempo |
| 5 | Resultados y decisión | Tamaño, conclusión |

Cada diapositiva avanza con una sola tecla. El modo presentador (`P`) muestra un guion breve para cada expositor.

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

Las diapositivas están en `composition/index.html`.

- **Nombres de los expositores:** cambiá la lista `SPEAKERS` al final del archivo; la portada y el pie de cada diapositiva se actualizan solos.
- **Guion del presentador:** está en el bloque JSON al inicio de `<body>`. Si lo modificás, copiá el bloque también a `index.html`.

No requiere instalación ni conexión a Internet. Para validar cambios, desde la raíz del repositorio:

```bash
python3 presentation/check-deck.py
npx --yes hyperframes@0.9.41 lint presentation/composition
```

El primer comando comprueba que haya 10 diapositivas, 2 por expositor, y que las dos copias del JSON coincidan.
