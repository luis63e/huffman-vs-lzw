# Código Java

Implementaciones de Huffman canónico y LZW, con pruebas JUnit y experimentos de rendimiento.

## Compilar y probar

Requiere Java 17 o superior y Ant 1.10 o superior. Ejecutá desde esta carpeta:

```bash
ant test
ant jar
```

También podés abrir `code/` en NetBeans como proyecto Java con Ant. Las bibliotecas de prueba están incluidas en `lib/`.

## Usar el programa

```bash
java -jar dist/HuffmanVsLZW.jar demo
java -jar dist/HuffmanVsLZW.jar compress huffman entrada.txt salida.huf
java -jar dist/HuffmanVsLZW.jar compress lzw entrada.txt salida.lzw
java -jar dist/HuffmanVsLZW.jar decompress salida.lzw restaurado.txt
java -jar dist/HuffmanVsLZW.jar stats entrada.txt
```

La descompresión detecta el formato automáticamente. LZW también admite variantes de `lzw-9` a `lzw-16`.

Códigos de salida: `0` correcto, `1` error de ejecución y `2` uso incorrecto.

## Experimentos

```bash
../data/download-corpora.sh
ant benchmark
# Ejecución rápida de los experimentos E1 y E2:
ant benchmark -Dbench.args="--quick --only e1,e2"
```

E1 mide el efecto del tamaño de entrada; E2, la entropía; E3 compara los corpus Canterbury y Silesia. Los resultados se guardan en `../results/`.
