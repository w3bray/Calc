# Calc (short for calculator btw)

Uma calculadora para Android que funciona como uma calculadora normal... até que, de
tempos em tempos e sem aviso, uma imagem aparece em fade por cima de tudo, com um som,
fica alguns segundos e some em fade out. Depois a calculadora segue como se nada tivesse
acontecido. O botão Voltar não fecha o app.

Este repositório tem duas versões:

| Pasta      | O que é                                                                  |
|------------|---------------------------------------------------------------------------|
| `app/`     | **App Android nativo (Kotlin)** — gera o APK. Calculadora de verdade + sustos aleatórios. |
| `desktop/` | O script original em Python/pygame (`desktop/calc.py`), a versão "sempre dá 67", preservada como referência. |

## Baixar o APK

Você não precisa instalar nada no computador: o GitHub Actions compila o APK sozinho.

1. **Último build da `main`** (link fixo, atualizado a cada push na `main`):
   `https://github.com/w3bray/Calc/releases/download/latest/Calc.apk`
2. **Build de qualquer branch / PR:** todo push dispara um run; aba **Actions** → workflow
   **Build APK** → clique no run → seção **Artifacts** → baixe `Calc-apk` (vem zipado, dentro
   está o `Calc.apk`) e `unit-test-report` (relatório dos testes).
3. **Versão numerada:** crie uma tag `v1.0`, `v1.1`, ... (`git tag v1.1 && git push origin v1.1`)
   e o workflow publica uma Release com o `Calc.apk` anexado.

No celular, abra o `Calc.apk`, permita "instalar de fontes desconhecidas" para o app
que você usou para baixar (Chrome, Arquivos...) e instale. Todos os builds são
assinados com a mesma chave (`keystore/calc.jks`), então uma versão nova instala por
cima da anterior sem precisar desinstalar.

## O que o app faz

- **Calculadora normal**: `+ - * /`, parênteses, decimais, menos unário, multiplicação
  implícita (`2(3)`), precedência correta. A aritmética é **exata** (frações, arredondadas
  uma única vez para 15 dígitos na hora de mostrar): `0.1+0.2` = `0.3` e `1/3*3-1` = `0`.
  Regras de uma calculadora de celular: zero à esquerda é substituído, só um ponto por
  número, operador digitado em cima de outro substitui, `)` só fecha o que está aberto,
  operador no fim é ignorado e parêntese aberto é fechado automaticamente ao apertar `=`.
  Depois de `=`, um dígito começa uma conta nova e um operador continua a partir do
  resultado **com o valor exato** (`1/3 = × 3 =` dá `1`). Resultados muito grandes ou
  pequenos aparecem em notação científica (`1E+58`) e continuam calculáveis. Divisão por
  zero e expressões inválidas mostram `Erro` (ou `Error` fora do português); a próxima
  tecla limpa. `=` com só um `(` ou um operador na tela não faz nada.
- **Susto**: depois de um tempo aleatório entre 20 e 80 segundos de uso (e de novo depois
  de cada susto), `image.png` faz fade in de 1 s, fica 3 s, faz fade out de 1 s, e
  `call.mp3` toca no instante em que o fade in começa. A contagem só anda enquanto o app
  está na frente e **continua de onde parou** quando você sai e volta (mesmo se o Android
  matar o processo), então várias contas rápidas somam até o susto chegar; se o tempo
  venceu enquanto o app estava fechado, ele aparece uns 3 s depois de reabrir. `C` manda a
  imagem embora na hora (com fade out). Enquanto a imagem está na tela, os botões continuam
  funcionando.
- **Voltar não fecha o app** (botão ou gesto, inclusive com o "voltar preditivo" do
  Android 13+). Home e a tela de apps recentes funcionam.
- **Música de fundo** (`epstien.mp3`) em loop a 35 %, pausada quando o app sai de
  primeiro plano (uma imagem no meio do fade é cancelada e o som cortado).
- Assets ausentes são apenas registrados no logcat e ignorados; sem fundo, o app desenha
  o mesmo degradê cinza do script Python.

Todos esses números ficam em `app/src/main/kotlin/io/github/w3bray/calc/Config.kt`:

| Constante                                           | Padrão      | Efeito |
|-----------------------------------------------------|-------------|--------|
| `OVERLAY_FADE_IN_TIME` / `OVERLAY_HOLD_TIME` / `OVERLAY_FADE_OUT_TIME` | 1 s / 3 s / 1 s | duração de cada fase da imagem |
| `OVERLAY_TARGET_ALPHA`                              | 245         | opacidade máxima (0 a 255) |
| `PRANK_MIN_INTERVAL_SECONDS` / `PRANK_MAX_INTERVAL_SECONDS` | 20 / 80 | intervalo aleatório entre sustos |
| `PRANK_ENABLED`                                     | `true`      | `false` desliga os sustos automáticos |
| `PRANK_ON_EQUALS_CHANCE`                            | `0`         | chance (0 a 1) de o `=` também disparar a imagem, além dos sustos aleatórios; `1` dispara em todo `=` (o resultado continua sendo o verdadeiro e a imagem some sozinha) |
| `BACK_BUTTON_CLOSES_APP`                            | `false`     | `true` volta a deixar o botão Voltar fechar o app |
| `MUSIC_VOLUME`                                      | `0.35`      | volume da música de fundo |
| `RESULT_PRECISION_DIGITS`                           | `15`        | dígitos significativos mostrados (a conta em si é exata) |
| `RESULT_MAX_PLAIN_DIGITS` / `RESULT_MIN_PLAIN_EXPONENT` | `15` / `-6` | a partir de quando um resultado vira notação científica (`1E+15`, `1E-7`) |
| `MAX_EXPR_LENGTH`                                   | `60`        | tamanho máximo da expressão digitada |
| `BACKGROUND_FIT` / `OVERLAY_FIT`                    | `COVER`     | como as imagens preenchem a tela (`STRETCH` estica como o Python fazia) |

## Colocar suas imagens e sons

Os arquivos ficam em **`app/src/main/assets/`** com os mesmos nomes que o `calc.py` usa:

| Arquivo          | Uso                                         |
|------------------|---------------------------------------------|
| `diddy.png.jpeg` | imagem de fundo (sem ela: degradê cinza)    |
| `epstien.mp3`    | música de fundo em loop, volume 35 %        |
| `image.png`      | a imagem do susto (um placeholder já vem incluído) |
| `call.mp3`       | som tocado no instante em que o fade in começa |

Nenhum é obrigatório. Depois de colocar os arquivos, faça um push: o workflow gera um
APK novo com eles embutidos. Detalhes em
[`app/src/main/assets/README.md`](app/src/main/assets/README.md).

## Compilar e testar no seu computador (opcional)

Requisitos: JDK 17+ e o Android SDK (platform 34 + build-tools 34.0.0; o Android
Studio instala tudo). Com `ANDROID_HOME` apontando para o SDK:

```bash
./gradlew test              # testes unitários do avaliador e das regras de digitação
./gradlew assembleRelease   # APK em app/build/outputs/apk/release/app-release.apk
./gradlew installRelease    # instala num aparelho com depuração USB ligada
```

## Estrutura

```
.
├── .github/workflows/build-apk.yml   # CI: testa, compila, publica artifact + releases
├── app/
│   ├── build.gradle.kts              # módulo Android (minSdk 21, targetSdk 34)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/               # <- suas imagens e sons vão aqui
│       │   ├── kotlin/io/github/w3bray/calc/
│       │   │   ├── Config.kt         # todos os ajustes (tempos, intervalos, arquivos)
│       │   │   ├── Evaluator.kt      # avaliador de expressões (frações exatas)
│       │   │   ├── CalcInput.kt      # regras de digitação de uma calculadora normal
│       │   │   ├── CalcView.kt       # desenho, toque, teclado, fade da imagem, timer do susto
│       │   │   ├── CalcAudio.kt      # música de fundo + som do susto
│       │   │   ├── Media.kt          # loader de imagens à prova de arquivo faltando
│       │   │   └── MainActivity.kt   # ciclo de vida, edge-to-edge, botão Voltar
│       │   └── res/                  # ícone, tema, strings (en / pt)
│       └── test/kotlin/...           # testes JUnit de Evaluator e CalcInput
├── desktop/calc.py                   # versão original pygame
├── keystore/calc.jks                 # chave de assinatura compartilhada
├── build.gradle.kts, settings.gradle.kts, gradle.properties
└── gradlew, gradle/wrapper/          # Gradle 8.7 (baixa sozinho)
```

## Assinatura

`keystore/calc.jks` é uma chave gerada só para este projeto (senha `calc-67-67`,
alias `calc`). Ela está no repositório de propósito, para que o CI e qualquer clone
gerem APKs com a mesma assinatura. Não use essa chave para publicar na Play Store;
se quiser trocar, defina as variáveis `CALC_KEYSTORE_FILE`, `CALC_KEYSTORE_PASSWORD`,
`CALC_KEY_ALIAS` e `CALC_KEY_PASSWORD` (por exemplo via GitHub Secrets) e o
`app/build.gradle.kts` usa a sua.
