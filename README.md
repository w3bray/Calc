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
3. **Versão numerada:** crie uma tag igual ao `versionName` do `app/build.gradle.kts`
   (hoje `1.2`: `git tag v1.2 && git push origin v1.2`) e o workflow publica uma Release com o
   `Calc.apk` anexado. Ao mudar o comportamento do app, suba `versionCode` e `versionName`.

No celular, abra o `Calc.apk`, permita "instalar de fontes desconhecidas" para o app
que você usou para baixar (Chrome, Arquivos...) e instale. Todos os builds são
assinados com a mesma chave (`keystore/calc.jks`), então uma versão nova instala por
cima da anterior sem precisar desinstalar.

## O que o app faz

- **Visual**: teclas de vidro sobre o fundo escurecido, operadores em laranja (o operador
  ativo fica destacado), `=` em degradê laranja→rosa com brilho, display em cartão de vidro
  com prévia do resultado enquanto você digita (`= 24`) e a conta anterior acima do
  resultado. Toques têm animação e vibração leve. Cores e tamanhos ficam no `Config.kt`.
- **Calculadora normal**: `+ - * /`, parênteses, decimais, menos unário, multiplicação
  implícita (`2(3)`), precedência correta. A aritmética é **exata** (frações, arredondadas
  uma única vez para 15 dígitos na hora de mostrar): `0.1+0.2` = `0.3` e `1/3*3-1` = `0`.
  Regras de uma calculadora de celular: zero à esquerda é substituído, só um ponto por
  número, operador digitado em cima de outro substitui (o `-` depois de um operador vira
  sinal negativo: `5+-3` = `2`), `)` só fecha o que está aberto,
  operador no fim é ignorado e parêntese aberto é fechado automaticamente ao apertar `=`.
  Depois de `=`, um dígito começa uma conta nova e um operador continua a partir do
  resultado **com o valor exato** (`1/3 = × 3 =` dá `1`). Resultados muito grandes ou
  pequenos aparecem em notação científica (`1E+58`) e continuam calculáveis. Divisão por
  zero e expressões inválidas mostram `Erro` (ou `Error` fora do português); a próxima
  tecla limpa. `=` com só um `(` ou um operador na tela não faz nada.
- **Susto**: depois de um tempo aleatório entre 20 e 80 segundos de uso (e de novo depois
  de cada susto), `image.png` aparece com opacidade total por **8 segundos** (fade in de
  0,5 s, 7 s na tela, fade out de 0,5 s) e `call.mp3` toca junto, começando com a imagem e
  parando quando ela some. A contagem só anda enquanto o app
  está na frente e **continua de onde parou** quando você sai e volta (o tempo restante é
  salvo ao sair, e sobrevive até o Android encerrar o app em segundo plano), então várias
  contas rápidas somam até o susto chegar; se faltavam menos de 3 s quando você saiu, ele
  aparece uns 3 s depois de reabrir. `C` manda a imagem embora na hora (com fade out).
  Enquanto a imagem está na tela, os botões continuam funcionando.
- **Voltar não fecha o app** (botão ou gesto, inclusive com o "voltar preditivo" do
  Android 13+). Home e a tela de apps recentes funcionam.
- **Música de fundo** (`epstien.mp3`) em loop a 35 %, pausada quando o app sai de
  primeiro plano (uma imagem no meio do fade é cancelada e o som cortado).
- Assets ausentes são apenas registrados no logcat e ignorados; sem fundo, o app desenha
  o mesmo degradê cinza do script Python.

Todos esses números ficam em `app/src/main/kotlin/io/github/w3bray/calc/Config.kt`:

| Constante                                           | Padrão      | Efeito |
|-----------------------------------------------------|-------------|--------|
| `OVERLAY_FADE_IN_TIME` / `OVERLAY_HOLD_TIME` / `OVERLAY_FADE_OUT_TIME` | 0,5 s / 7 s / 0,5 s | duração de cada fase da imagem (8 s no total; o som para junto) |
| `OVERLAY_TARGET_ALPHA`                              | 255         | opacidade máxima (0 a 255; 255 = 100 %) |
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
| `image.png`      | a imagem do susto                           |
| `call.mp3`       | som tocado no instante em que o fade in começa |

Nenhum é obrigatório. Depois de colocar os arquivos, faça um push: o workflow gera um
APK novo com eles embutidos. Detalhes em
[`app/src/main/assets/README.md`](app/src/main/assets/README.md).

## Compilar e testar no seu computador (opcional)

Requisitos: JDK 17+ e o Android SDK (platform 36 + build-tools 35.0.0; o Android
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
│   ├── build.gradle.kts              # módulo Android (minSdk 21, targetSdk 36)
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
└── gradlew, gradle/wrapper/          # Gradle 8.13 (baixa sozinho)
```

## Publicar para amigos pela Play Store (sem avisos)

APK instalado fora da Play Store mostra aviso do Chrome ("arquivo pode ser nocivo") e, desde
30/09/2026 no Brasil, o Play Protect bloqueia apps de desenvolvedores não verificados. Pelo
**teste interno** da Play Store seus amigos instalam pela própria loja, sem aviso nenhum
(até 100 testadores, sem revisão do Google):

1. Crie a conta de desenvolvedor em https://play.google.com/console (taxa única de US$ 25 e
   verificação de identidade).
2. **Criar app** → nome `Calc`, tipo App, gratuito.
3. Menu **Testar e lançar → Teste interno → Criar nova versão**. Aceite o *Play App Signing*
   (o Google guarda a chave final do app) e envie o `Calc.aab`.
4. Na aba **Testadores**, crie uma lista com os e-mails Google dos amigos, salve e publique a
   versão. Copie o **link de participação** e mande para eles: cada um abre o link, aceita
   e instala pela Play Store.
5. Atualizações: suba `versionCode` no `app/build.gradle.kts`, faça push e envie o novo
   `Calc.aab` (artifact `Calc-aab` do Actions, ou anexado às releases) numa nova versão.

O `.aab` precisa ser assinado com a **chave de envio privada** (veja abaixo), nunca com a
chave pública do repositório.

## Assinatura

`keystore/calc.jks` é uma chave gerada só para este projeto (senha `calc-67-67`,
alias `calc`). Ela está no repositório de propósito, só para builds locais e de teste.
Como ela é pública, **nunca** a use para a Play Store nem para cadastro de desenvolvedor:
para isso existe a chave de envio privada (`calc-upload.jks`, alias `upload`), que fica
fora do repositório e entra no CI pelos segredos abaixo.

Para assinar com uma chave sua:
- **No CI**: crie em Settings → Secrets and variables → Actions os segredos
  `CALC_KEYSTORE_B64` (o arquivo `.jks`/`.p12` em base64: `base64 -w0 minha.jks`),
  `CALC_KEYSTORE_PASSWORD`, `CALC_KEY_ALIAS` e `CALC_KEY_PASSWORD`. O workflow decodifica
  o arquivo e repassa as variáveis ao Gradle; segredos vazios são ignorados e a chave do
  repositório continua sendo usada.
- **Localmente**: exporte `CALC_KEYSTORE_FILE` (caminho relativo à raiz do projeto ou
  absoluto), `CALC_KEYSTORE_PASSWORD`, `CALC_KEY_ALIAS` e `CALC_KEY_PASSWORD` antes de rodar
  o Gradle.

Lembre que APKs assinados com chaves diferentes não instalam um por cima do outro.
