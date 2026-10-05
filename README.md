# Calc (short for calculator btw)

Uma "calculadora" para Android que, não importa o que você digite, responde **67**
e faz um fade-in de uma imagem com um som em cima de tudo. O overlay só some
quando você aperta **C**.

Este repositório tem duas versões do mesmo app:

| Pasta      | O que é                                                                  |
|------------|---------------------------------------------------------------------------|
| `app/`     | **App Android nativo (Kotlin)** — gera o APK. Mesma lógica, mesmos nomes de arquivos, mesmas constantes da versão desktop. |
| `desktop/` | O script original em Python/pygame (`desktop/calc.py`), preservado como referência. |

## Baixar o APK

Você não precisa instalar nada no computador: o GitHub Actions compila o APK sozinho.

1. **Build de qualquer branch / PR:** abra a aba **Actions** → workflow **Build APK** →
   clique no run → seção **Artifacts** → baixe `Calc-apk` (vem zipado, dentro está o `Calc.apk`).
2. **Último build da `main`:** a cada push na `main` o workflow atualiza a pré-release
   **`latest`**, com link fixo:
   `https://github.com/w3bray/Calc/releases/download/latest/Calc.apk`
3. **Versão numerada:** crie uma tag `v1.0`, `v1.1`, ... (`git tag v1.0 && git push origin v1.0`)
   e o workflow publica uma Release com o `Calc.apk` anexado.

No celular, abra o `Calc.apk`, permita "instalar de fontes desconhecidas" para o app
que você usou para baixar (Chrome, Arquivos...) e instale. Todos os builds são
assinados com a mesma chave (`keystore/calc.jks`), então uma versão nova instala por
cima da anterior sem precisar desinstalar.

## Colocar suas imagens e sons

Os arquivos ficam em **`app/src/main/assets/`** com os mesmos nomes que o `calc.py` usa:

| Arquivo          | Uso                                         |
|------------------|---------------------------------------------|
| `diddy.png.jpeg` | imagem de fundo (sem ela: degradê cinza, igual ao Python) |
| `epstien.mp3`    | música de fundo em loop, volume 35 %        |
| `image.png`      | imagem que aparece em fade ao apertar `=` (um placeholder já vem incluído) |
| `call.mp3`       | som tocado no instante em que o fade começa |

Nenhum é obrigatório: se faltar, o app registra um aviso no logcat (`[image] not found`,
`[music] not found`, ...) e segue, exatamente como os `load_*_safe` do Python. Depois de
colocar os arquivos, faça um push: o workflow gera um APK novo com eles embutidos.
Detalhes em [`app/src/main/assets/README.md`](app/src/main/assets/README.md).

## Compilar no seu computador (opcional)

Requisitos: JDK 17+ e o Android SDK (platform 34 + build-tools 34.0.0; o Android
Studio instala tudo). Com `ANDROID_HOME` apontando para o SDK:

```bash
./gradlew assembleRelease
# APK em: app/build/outputs/apk/release/app-release.apk
```

Para instalar direto num aparelho com depuração USB ligada:

```bash
./gradlew installRelease
```

## Como o Python virou Kotlin

| Python (`desktop/calc.py`)                     | Android (`app/src/main/kotlin/io/github/w3bray/calc/`)              |
|------------------------------------------------|---------------------------------------------------------------------|
| bloco de constantes no topo                    | `Config.kt` (mesmos nomes: `BG_IMAGE`, `OVERLAY_TARGET_ALPHA`, ...)  |
| `load_image_safe`, `load_music_safe`, `load_sound_safe` | `Media.kt` (imagens) e `CalcAudio.kt` (música + efeito)      |
| `pygame.mixer.music` em loop a 35 %            | `MediaPlayer` em loop, `setVolume(0.35)`; pausa quando o app vai pro fundo |
| `overlay_sfx.play()` no `trigger_overlay()`    | `CalcView.Listener.onOverlayTriggered()` → `CalcAudio.playOverlaySound()` |
| `Button`, `grid`, `btn_w`/`btn_h`, `draw_display` | `CalcView.kt` — mesmas fórmulas de layout, aplicadas ao tamanho real da tela (área segura, fora das barras do sistema) |
| `MOUSEBUTTONDOWN` dispara no *press*           | `ACTION_DOWN` dispara no toque, não no soltar                       |
| `handle_keydown` (Enter, `=`, Backspace, Esc)  | `onKeyDown` com o mesmo mapeamento, para teclado físico             |
| fade de `overlay_alpha` por `dt` a cada frame  | mesmo cálculo em `onDraw`, redesenhando a 60 fps até chegar em 245  |
| `smoothscale` para 420x620 (estica)            | `Config.BACKGROUND_FIT` / `OVERLAY_FIT` = `COVER` (preenche a tela sem distorcer; `STRETCH` reproduz o comportamento do Python) |

Diferenças de propósito: o layout é fluido (a grade ocupa a tela toda em vez de uma
janela fixa de 420x620), a tela fica travada em retrato, os botões ganham um leve
destaque enquanto pressionados, e a música pausa quando o app sai de primeiro plano.
O app não usa nenhuma biblioteca externa (só o framework Android + Kotlin), por isso
o APK é pequeno.

## Assinatura

`keystore/calc.jks` é uma chave gerada só para este projeto (senha `calc-67-67`,
alias `calc`). Ela está no repositório de propósito, para que o CI e qualquer clone
gerem APKs com a mesma assinatura. Não use essa chave para publicar na Play Store;
se quiser trocar, defina as variáveis `CALC_KEYSTORE_FILE`, `CALC_KEYSTORE_PASSWORD`,
`CALC_KEY_ALIAS` e `CALC_KEY_PASSWORD` (por exemplo via GitHub Secrets) e o
`app/build.gradle.kts` usa a sua.

## Estrutura

```
.
├── .github/workflows/build-apk.yml   # CI: compila, publica artifact + releases
├── app/
│   ├── build.gradle.kts              # módulo Android (minSdk 21, targetSdk 34)
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/                   # <- suas imagens e sons vão aqui
│       ├── kotlin/io/github/w3bray/calc/
│       │   ├── Config.kt             # constantes (iguais ao Python)
│       │   ├── Media.kt              # loader de imagens à prova de arquivo faltando
│       │   ├── CalcAudio.kt          # música de fundo + som do overlay
│       │   ├── CalcView.kt           # desenho, layout, toque, teclado, fade
│       │   └── MainActivity.kt       # ciclo de vida, edge-to-edge, áudio
│       └── res/                      # ícone, tema, strings
├── desktop/calc.py                   # versão original pygame
├── keystore/calc.jks                 # chave de assinatura compartilhada
├── build.gradle.kts, settings.gradle.kts, gradle.properties
└── gradlew, gradle/wrapper/          # Gradle 8.7 (baixa sozinho)
```
