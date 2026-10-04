# Roadmap

Quando o usuário disser **"siga adiante"**, pegue o **primeiro item não feito** da lista "A fazer",
seguindo as regras do `CLAUDE.md`. Se o item precisar de uma decisão do usuário ou de uma prévia
visual, faça a pergunta ou a prévia e pare. Ao terminar, mova o item para "Feito".

## Em revisão (branches enviadas, PR ainda não mesclado)
Ordem de merge: `feature/kingdom-members` → `cleanup/coin-data` → `fix/header-and-live-names`
→ `art/coin-textures`. (A última contém todas; pode ser um PR só.)

## A fazer
1. [ ] **Testar no jogo** (só o usuário consegue): alinhamento dos slots, lista de membros, funis,
       seletor de quantidade, cabeçalho e painel da cunhagem, moedas novas, ícone e texturas da Casa de Câmbio,
       fornalha acendendo no mundo (chamas e luz) e a barra de progresso da tela da fornalha.
       Corrigir o que o usuário reportar (ele manda captura de tela).
2. [ ] **Fazer o merge dos PRs** (usuário). Depois: atualizar o `main` local e apagar as branches
       antigas (**só com autorização**).
3. [ ] **Apagar arquivos sem uso** em `src/main/resources/assets/crownscoins/`:
       `textures/item/overlay/`, `textures/item/coin/`, `models/item/overlay/`, `models/item/coin/`
       (~500 KB, nenhum item os referencia). **Precisa de autorização explícita antes de apagar.**
5. [ ] **Nome do reino nas moedas antigas:** hoje cada moeda guarda uma cópia do nome. Decidir com o
       usuário se renomear o reino deve atualizar as moedas já cunhadas.
6. [ ] **Fornalha mostra só 9 dos 27 slots da arca** (a arte tem grade 3×3). Precisa de rolagem e arte
       nova. **Prévia antes.**
7. [ ] **Testes de jogo automáticos (GameTest)** para funis, quebra de bloco e Shift+clique. É um
       projeto próprio (estruturas de teste); avaliar o custo antes.
8. [ ] **Instalar o GitHub CLI** (usuário): `winget install GitHub.cli` e `gh auth login`, para o Claude
       criar PRs sozinho.

## Feito
- [x] Correções da revisão: Shift+clique, pepitas ao quebrar, codecs de salvamento, textos traduzíveis.
- [x] Membros do reino (adicionar/remover na engrenagem) e trava de acesso.
- [x] Sem slots escondidos; modo "prensa compacta" removido.
- [x] Interfaces: slots a 20 px e painéis com textura; fundos para Câmbio e Fundar Reino.
- [x] Funis (pepitas entram por cima/lados, moedas prontas saem por baixo).
- [x] Seletor de quantidade (1, 8, 64, Tudo).
- [x] Economia: fundir devolve o custo; valores por reino removidos.
- [x] Testes automáticos (24) e limpeza do `CoinData`.
- [x] Cabeçalho limpo, nomes atualizando ao vivo, 36 moedas novas, item 3D da Casa de Câmbio.
- [x] Texturas do bloco da Casa de Câmbio redesenhadas (madeira escura, ferro e ouro). Originais em `designs/backup_currency_exchange_block_v1/`.
- [x] Painel de cunhagem refeito (caixas 28 px, contagem na caixa, botões 1/8/64/Tudo sem sobreposição), placas texturizadas e centralizadas no cabeçalho, texto de status removido da tela de cunhagem.
- [x] Fornalha: barra de progresso real e alinhada, painéis de carvão calmos (sem faíscas), bloco acende com chamas animadas e luz enquanto funde.
- [x] Arquivos de instruções: `CLAUDE.md`, `ROADMAP.md` e lista de comandos permitidos em `.claude/settings.json`.
