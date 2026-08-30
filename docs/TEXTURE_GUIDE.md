# Crowns & Coins — guia de texturas

Os modelos já apontam para estes ficheiros PNG, ainda a criar:

- `assets/crownscoins/textures/item/iron_coin.png`
- `assets/crownscoins/textures/item/copper_coin.png`
- `assets/crownscoins/textures/item/gold_coin.png`
- `assets/crownscoins/textures/block/mint_house_front.png`
- `assets/crownscoins/textures/block/mint_house_back.png`
- `assets/crownscoins/textures/block/mint_house_side.png`
- `assets/crownscoins/textures/block/mint_house_top.png`
- `assets/crownscoins/textures/block/mint_house_bottom.png`

## Moedas

Crie cada moeda em pixel art de 16×16 pixels, com fundo transparente e sem
anti-aliasing. A silhueta deve ser circular, com borda dentada/entalhada e uma
coroa central em relevo, seguindo a prévia aprovada. Deixe os cantos
transparentes para que a moeda tenha uma leitura redonda no inventário.

Use a mesma composição nas três moedas: contorno escuro de 1 pixel, anel
metálico, pequenos pontos/marcas em volta e coroa no centro. Mude apenas a
paleta: prata frio para ferro, cobre queimado para cobre e ouro amarelo vivo
para ouro. Reserve os realces mais claros para a borda superior-esquerda e as
sombras para a inferior-direita, para reproduzir o efeito de relevo da prévia.

Quando o sistema de crestas dinâmicas for implementado, mantenha estes três
PNGs como base metálica. O símbolo do reino deve ser aplicado como uma camada
ou renderização adicional no centro, sem substituir a paleta nem a borda da
moeda.

## Mint House

O bloco usa um cubo com uma face frontal distinta. Faça a frente como uma
prensa/casa da moeda: moldura de ferro escuro, placa de cobre e um emblema de
coroa dourado. Use laterais de metal rebitado, topo de placas metálicas e base
escura. Mantenha todas as texturas a 16×16 pixels e use bordas de alto
contraste para que o bloco seja reconhecível à distância.
