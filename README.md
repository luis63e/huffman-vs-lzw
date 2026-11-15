# Huffman vs. LZW

Comparación de dos algoritmos de compresión sin pérdida: Huffman canónico y LZW. Proyecto del curso de Algoritmos de la Universidad de Lima (2026-2).

## Contenido

- [`code/`](code/): código Java, pruebas y experimentos.
- [`data/`](data/): descarga de los corpus de prueba.
- [`results/`](results/): resultados de los experimentos.
- [`report/`](report/): informe en LaTeX.
- [`entrega/`](entrega/): informe en PDF.
- [`presentation/`](presentation/): diapositivas para la exposición.

## Cómo empezar

Necesitás Java 17 o superior y Ant 1.10 o superior.

```bash
cd code
ant test
ant jar
java -jar dist/HuffmanVsLZW.jar demo
```

Para ejecutar los experimentos, descargá primero los datos desde la raíz del repositorio:

```bash
data/download-corpora.sh
cd code
ant benchmark
```

Los README de cada carpeta explican cómo usar el programa, compilar el informe y abrir la presentación.
