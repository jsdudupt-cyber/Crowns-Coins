# Crowns & Coins: instruções para o Claude

Mod de Minecraft para **NeoForge 26.2** e **Java 25**. Responda em **português simples** e explique
qualquer erro antes de mexer no código.

## O que o mod faz
- Reinos e moedas personalizadas. Estação de dois blocos: **Fornalha de Moedas** (esquerda) e
  **Linha de Cunhagem** (direita).
- Fornalha: pepitas viram moedas-base, que vão automaticamente para a **Arca Interna** (27 slots).
  Custo: 2 pepitas de cobre, 5 de ferro, 7 de ouro. Usa as pepitas **originais** do Minecraft.
- Linha de Cunhagem: usa as moedas-base da arca para cunhar uma moeda com um dos **12 desenhos**,
  em quantidade 1, 8, 64 ou tudo. A moeda pertence a um reino e tem o nome da moeda do reino.
- Metais: cobre, ferro (aparece como "prata") e ouro. Valores fixos: 1, 20 e 500.
- Casa de Câmbio: troca 20 de cobre por 1 de ferro, 25 de ferro por 1 de ouro, e funde moedas
  devolvendo as pepitas do custo de cunhar.
- Só membros do reino usam a estação. O fundador gerencia membros e renomeia reino e moeda.

## Regras fixas (não quebrar)
1. **Interfaces:** as telas da fornalha e da cunhagem têm tamanho fixo **480×360**. Não mova molduras,
   slots, hotbar nem botões **sem mostrar uma prévia antes e receber aprovação**. As posições visuais e
   as clicáveis devem continuar alinhadas (as mesmas constantes no código e na textura).
2. **Preserve as artes** enviadas pelo jogador. Ao alterar uma textura, mantenha os slots no mesmo
   lugar. Antes de sobrescrever uma arte, guarde uma cópia em `designs/backup_*`.
3. **Nunca** use `git reset --hard`, `git clean` nem `git push --force`. **Nunca apague arquivos sem
   pedir autorização.** Prefira mover para uma pasta de backup.
4. Faça **uma alteração por vez**. Para mudança visual, mostre a prévia antes de aplicar.
5. Não confie no cliente: o servidor valida tudo (quantidades, reino, membros).

## Rotina padrão de uma tarefa
1. `git status` e leia os arquivos envolvidos. Confirme o estado antes de editar.
2. Edite.
3. Compile e teste: `.\gradlew.bat build` (roda os testes automáticos junto).
4. Se a rede mudou (pacotes), suba `NetworkHandler.NETWORK_VERSION`.
5. Commit com mensagem clara. Termine com a linha `Co-Authored-By` que o ambiente indicar.
6. Envie a branch (`git push origin <branch>`). O push pede confirmação.
7. PR: use `gh pr create` se o `gh` estiver instalado; senão, dê o link
   `https://github.com/jsdudupt-cyber/Crowns-Coins/pull/new/<branch>` com título e texto prontos.

## Comandos úteis (Windows)
```powershell
$env:JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT"   # se o download do Gradle falhar por certificado
.\gradlew.bat build        # compila e roda os testes
.\gradlew.bat runClient    # abre o jogo de teste (pasta run/)
```
O Gradle precisa do Java 25 (toolchain já configurado no `build.gradle`).

## Mapa do código (src/main/java/com/crownscoins)
- `block/` `MintHouseBlock` (2 blocos), `MintHouseBlockEntity` (arca + entrada da fornalha + funis).
- `menu/` menus no servidor; `MintHouseLayout` e `MintFurnaceMenu` guardam as coordenadas dos slots.
- `client/` telas. `MintHouseScreen` (cunhagem), `MintFurnaceScreen`, `CurrencyExchangeScreen`,
  `KingdomCreationScreen`.
- `kingdom/` `Kingdom`, `KingdomSavedData` (um reino por jogador, nomes únicos).
- `coin/CoinData` dados da moeda (reino, nome, material, valor, desenho).
- `network/` pacotes. Versão atual em `NetworkHandler.NETWORK_VERSION`.
- Texturas das telas: `src/main/resources/assets/crownscoins/textures/gui/`. Fontes em `designs/`.
- Testes: `src/test/java`.

## O que o Claude NÃO consegue fazer
- Ver a janela do Minecraft. Peça ao usuário uma **captura de tela** para conferir visuais.
- Fazer merge de PR nem apagar arquivos sem autorização explícita do usuário.

Veja o que falta em `ROADMAP.md`.
