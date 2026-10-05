# Assets do app

Coloque aqui os mesmos arquivos que a versão desktop (`desktop/calc.py`) usa,
com **exatamente** estes nomes:

| Arquivo           | Para que serve                                                        | Obrigatório? |
|-------------------|-----------------------------------------------------------------------|--------------|
| `diddy.png.jpeg`  | imagem de fundo                                                       | não (sem ele o app desenha um degradê cinza, igual ao Python) |
| `epstien.mp3`     | música de fundo em loop (volume 35 %)                                 | não (sem ele fica mudo) |
| `image.png`       | imagem que aparece de surpresa (fade in, pausa, fade out)             | não (sem ele o susto só toca o som)                   |
| `call.mp3`        | som tocado no instante em que a imagem começa a aparecer              | não (sem ele fica mudo) |

Qualquer formato que o Android decodifica serve (JPEG/PNG/WebP para imagens,
MP3/OGG/WAV/M4A/AAC/FLAC para áudio) — o decodificador lê o conteúdo, não a extensão.
**Atenção com áudio:** o nome do arquivo de som precisa terminar em uma das extensões
listadas em `noCompress` no `app/build.gradle.kts` (`mp3`, `ogg`, `wav`, `m4a`, `aac`,
`flac`, `mp4`); caso contrário o APK comprime o arquivo e o player não consegue abri-lo
(fica mudo, com um aviso `not found` no logcat). Se quiser outra extensão, acrescente-a
àquela lista. Depois de trocar os arquivos, basta gerar o APK de novo
(`./gradlew assembleRelease` ou um push no GitHub, que o workflow compila sozinho).

Para mudar os nomes dos arquivos, os tempos do fade ou o intervalo entre as
aparições, edite as constantes em
`app/src/main/kotlin/io/github/w3bray/calc/Config.kt`.
