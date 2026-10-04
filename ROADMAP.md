# Roadmap

Quando o usuário disser **"siga adiante"**, pegue o **primeiro item não feito** da lista "A fazer",
seguindo as regras do `CLAUDE.md`. Se o item precisar de uma decisão do usuário ou de uma prévia
visual, faça a pergunta ou a prévia e pare. Ao terminar, mova o item para "Feito".

## Em revisão (branches enviadas, PR ainda não mesclado)
Ordem de merge: `feature/kingdom-members` → `cleanup/coin-data` → `fix/header-and-live-names`
→ `art/coin-textures` → `feature/mint-house-protection`. (Cada uma contém as anteriores; a última
sozinha serve como PR único com tudo.)

## Foco atual: grupo de amigos
O mod é para um **grupo pequeno de amigos** que jogam juntos. Tudo o que é de servidor grande ou
público (proteção contra roubo, publicação, testes automáticos) fica para depois, na seção
"Depois". As travas já existem como opções no arquivo de configuração, desligadas por padrão.

## A fazer (em ordem)
1. [ ] **Testar no jogo** (só o usuário consegue): alinhamento dos slots, lista de membros, funis,
       seletor de quantidade, cabeçalho e painel da cunhagem, moedas novas, ícone e texturas da Casa de Câmbio,
       fornalha acendendo no mundo (chamas e luz), barra de progresso e os comandos `/crownscoins`.
       Corrigir o que o usuário reportar (ele manda captura de tela).
2. [ ] **Fazer o merge dos PRs** (usuário). Depois: atualizar o `main` local e apagar as branches
       antigas (**só com autorização**).
3. [x] **Sons e partículas** (feito, falta o usuário ouvir e aprovar): fornalha crepitando com fumaça
       e chamas, "ding" a cada moeda-base, martelada com faíscas ao cunhar (sem som de ignição).
4. [x] **Arca na fornalha com rolagem** (feito, falta o usuário testar): 3 páginas de 9 slots, setas ao lado da grade,
       indicador "1/3" e roda do mouse. Só visualização; as moedas se mexem na Linha de Cunhagem.
5. [x] **Conquistas simples** (feito, falta o usuário ver): Casa da Moeda, Nasce um Reino, Primeira Cunhagem, Amigos na Corte, O Dinheiro Circula e Troca Justa.
6. [ ] **Apagar arquivos sem uso** em `src/main/resources/assets/crownscoins/`:
       `textures/item/overlay/`, `textures/item/coin/`, `models/item/overlay/`, `models/item/coin/`
       (~500 KB, nenhum item os referencia). **Precisa de autorização explícita antes de apagar.**
7. [ ] **Nome do reino nas moedas antigas:** hoje cada moeda guarda uma cópia do nome. Decidir com o
       usuário se renomear o reino deve atualizar as moedas já cunhadas.
8. [ ] **Mais uso para as moedas** (decisão dos amigos): troca com aldeões ou barril de loja.
       Já existem `/crownscoins balance` e `/crownscoins pay`.

## Depois (se virar servidor grande ou público)
- Revisar os padrões da configuração (ligar `protectMintHouse`, desligar `allowHopperOutput`).
- Configurar o resto: custos das pepitas, velocidade da fornalha e tamanho da arca.
- Testes de jogo automáticos (GameTest) para funis, quebra de bloco e Shift+clique.
- Instalar o GitHub CLI (`winget install GitHub.cli` e `gh auth login`) para o Claude criar PRs.
- Publicação: README com capturas, ícone e logo, licença, build automático no GitHub, versão estável.

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
