# Roadmap

Quando o usuÃ¡rio disser **"siga adiante"**, pegue o **primeiro item nÃ£o feito** da lista "A fazer",
seguindo as regras do `CLAUDE.md`. Se o item precisar de uma decisÃ£o do usuÃ¡rio ou de uma prÃ©via
visual, faÃ§a a pergunta ou a prÃ©via e pare. Ao terminar, mova o item para "Feito".

## Em revisÃ£o (branches enviadas, PR ainda nÃ£o mesclado)
Ordem de merge: `feature/kingdom-members` â†’ `cleanup/coin-data` â†’ `fix/header-and-live-names`
â†’ `art/coin-textures`. (A Ãºltima contÃ©m todas; pode ser um PR sÃ³.)

## A fazer
1. [ ] **Testar no jogo** (sÃ³ o usuÃ¡rio consegue): alinhamento dos slots, lista de membros, funis,
       seletor de quantidade, cabeÃ§alho e painel da cunhagem, moedas novas, Ã­cone e texturas da Casa de CÃ¢mbio,
       fornalha acendendo no mundo (chamas e luz) e a barra de progresso da tela da fornalha.
       Corrigir o que o usuÃ¡rio reportar (ele manda captura de tela).
2. [ ] **Fazer o merge dos PRs** (usuÃ¡rio). Depois: atualizar o `main` local e apagar as branches
       antigas (**sÃ³ com autorizaÃ§Ã£o**).
3. [ ] **Apagar arquivos sem uso** em `src/main/resources/assets/crownscoins/`:
       `textures/item/overlay/`, `textures/item/coin/`, `models/item/overlay/`, `models/item/coin/`
       (~500 KB, nenhum item os referencia). **Precisa de autorizaÃ§Ã£o explÃ­cita antes de apagar.**
5. [ ] **Nome do reino nas moedas antigas:** hoje cada moeda guarda uma cÃ³pia do nome. Decidir com o
       usuÃ¡rio se renomear o reino deve atualizar as moedas jÃ¡ cunhadas.
6. [ ] **Fornalha mostra sÃ³ 9 dos 27 slots da arca** (a arte tem grade 3Ã—3). Precisa de rolagem e arte
       nova. **PrÃ©via antes.**
7. [ ] **Testes de jogo automÃ¡ticos (GameTest)** para funis, quebra de bloco e Shift+clique. Ã‰ um
       projeto prÃ³prio (estruturas de teste); avaliar o custo antes.
8. [ ] **Instalar o GitHub CLI** (usuÃ¡rio): `winget install GitHub.cli` e `gh auth login`, para o Claude
       criar PRs sozinho.

## Feito
- [x] CorreÃ§Ãµes da revisÃ£o: Shift+clique, pepitas ao quebrar, codecs de salvamento, textos traduzÃ­veis.
- [x] Membros do reino (adicionar/remover na engrenagem) e trava de acesso.
- [x] Sem slots escondidos; modo "prensa compacta" removido.
- [x] Interfaces: slots a 20 px e painÃ©is com textura; fundos para CÃ¢mbio e Fundar Reino.
- [x] Funis (pepitas entram por cima/lados, moedas prontas saem por baixo).
- [x] Seletor de quantidade (1, 8, 64, Tudo).
- [x] Economia: fundir devolve o custo; valores por reino removidos.
- [x] Testes automÃ¡ticos (24) e limpeza do `CoinData`.
- [x] CabeÃ§alho limpo, nomes atualizando ao vivo, 36 moedas novas, item 3D da Casa de CÃ¢mbio.
- [x] Texturas do bloco da Casa de CÃ¢mbio redesenhadas (madeira escura, ferro e ouro). Originais em `designs/backup_currency_exchange_block_v1/`.
- [x] Painel de cunhagem refeito (caixas 28 px, contagem na caixa, botÃµes 1/8/64/Tudo sem sobreposiÃ§Ã£o), placas texturizadas e centralizadas no cabeÃ§alho, texto de status removido da tela de cunhagem.
- [x] Fornalha: barra de progresso real e alinhada, painÃ©is de carvÃ£o calmos (sem faÃ­scas), bloco acende com chamas animadas e luz enquanto funde.
- [x] Arquivos de instruÃ§Ãµes: `CLAUDE.md`, `ROADMAP.md` e lista de comandos permitidos em `.claude/settings.json`.
