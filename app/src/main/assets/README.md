# Assets do app

Coloque aqui os mesmos arquivos que a versão desktop (`desktop/calc.py`) usa,
com **exatamente** estes nomes:

| Arquivo           | Para que serve                                   | Obrigatório? |
|-------------------|--------------------------------------------------|--------------|
| `diddy.png.jpeg`  | imagem de fundo                                  | não (sem ele o app desenha um degradê cinza, igual ao Python) |
| `epstien.mp3`     | música de fundo em loop (volume 35 %)            | não (sem ele fica mudo) |
| `image.png`       | imagem que aparece em fade ao apertar `=`        | não (um placeholder já está incluído; troque pelo seu) |
| `call.mp3`        | som tocado no instante em que o fade começa      | não (sem ele fica mudo) |

Qualquer formato que o Android decodifica serve (JPEG/PNG/WebP para imagens,
MP3/OGG/WAV/M4A/FLAC para áudio) — o app lê o conteúdo, não a extensão. Depois de
trocar os arquivos, basta gerar o APK de novo (`./gradlew assembleRelease` ou um
push no GitHub, que o workflow compila sozinho).

Para mudar os nomes dos arquivos, edite as constantes em
`app/src/main/kotlin/io/github/w3bray/calc/Config.kt`.
