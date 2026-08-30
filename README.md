# Crowns & Coins

Um mod de reinos e moedas para Minecraft. Crie seu reino, comece com a Coroa Real como brasão padrão, vincule uma Casa da Moeda e cunhe moedas de cobre, ferro ou ouro com dois símbolos laterais.

## Requisitos

- Minecraft `26.2`
- NeoForge `26.2.0.48-beta`
- O mesmo arquivo do mod no cliente e no servidor, quando jogar em multiplayer

## Instalação

1. Instale o NeoForge na versão indicada acima.
2. Baixe `crownscoins-1.0.0.jar` na página de Releases ou gere-o pelo projeto.
3. Coloque o arquivo `.jar` na pasta `mods` da sua instalação do Minecraft.
4. Em servidores, coloque exatamente o mesmo arquivo também na pasta `mods` do servidor.

## Como usar

1. Crie a **Casa da Moeda** na bancada: ferro, cobre e ouro nos cantos/laterais e uma bancada de trabalho no centro.
2. Coloque a Casa da Moeda no mundo e interaja com ela.
3. Se ainda não fizer parte de um reino, preencha o nome do reino, o nome inicial da moeda e escolha o brasão. A **Coroa Real** já vem selecionada como padrão, mas pode ser trocada.
4. Com a Casa da Moeda vinculada, escolha cobre, ferro ou ouro em seu painel de 25 mini-moedas. Cada uma mostra a coroa fixa e o símbolo que você está escolhendo; ao trocar de painel, as escolhas laterais são limpas.
5. Escolha os dois símbolos, confira a prévia e confirme a cunhagem. O brasão do reino é obrigatório e fica no centro da moeda.
6. Arraste o lingote correspondente do seu inventário para o encaixe redondo de **LINGOTE**. O mod consome um lingote desse espaço e entrega a moeda personalizada no seu inventário; ao fechar a mesa, qualquer lingote restante volta para você.
7. O fundador também pode abrir a aba **Moeda do reino** para alterar o nome que aparecerá nas novas moedas do seu reino.

A economia é fixa e igual para todos os reinos: **1 cobre = 1**, **10 cobres = 1 ferro** e **15 ferros = 1 ouro** (logo, 1 ouro vale 150 cobres). Cada reino pode escolher o próprio nome de moeda, mas não alterar essas proporções.

Todas as regras importantes são verificadas pelo servidor: reino, vinculação da Casa da Moeda, distância, brasão, metal, os dois símbolos, lingote, fundador e nome da moeda.

## Para desenvolvedores

Com Java 25 instalado, execute `gradlew.bat build`. O arquivo final será criado em `build/libs/crownscoins-1.0.0.jar`.

## Licença

All Rights Reserved.
