# Informe

Informe en LaTeX sobre Huffman y LZW. El PDF entregable está en [`../entrega/Informe_Huffman_vs_LZW.pdf`](../entrega/Informe_Huffman_vs_LZW.pdf).

## Compilar

Requiere Tectonic disponible mediante `../env.sh`. Desde esta carpeta:

```bash
./build.sh
```

El resultado queda en `build/main.pdf`. El script copia los datos de `../results/`; no hace falta repetir los experimentos salvo que quieras actualizar esos datos.

Si faltan resultados, el informe muestra marcas de «Pendiente» o `??`.

## Editar

- `main.tex`: portada, integrantes y docente.
- `sections/`: contenido del informe.
- `refs.bib`: bibliografía.
- `results/` y `build/`: archivos generados; no los edites a mano.

Si repetís los experimentos, revisá también las cifras escritas directamente en el texto.

GitHub Actions actualiza el PDF de `entrega/` cuando cambian `report/` o `results/` en `main`.
