# Crowns & Coins

Um mod de reinos e moedas para Minecraft. Crie seu reino, escolha um dos 10 brasões centrais, vincule uma Casa da Moeda e cunhe moedas de bronze, ferro ou ouro com dois símbolos laterais.

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
3. Se ainda não fizer parte de um reino, preencha o nome do reino, a moeda, os valores e escolha um dos 10 brasões.
4. Com a Casa da Moeda vinculada, escolha bronze, ferro ou ouro em seu painel de 25 símbolos; ao trocar de painel, as escolhas laterais são limpas.
5. Preencha os dois espaços de símbolo, confira a prévia e confirme a cunhagem. O brasão central é obrigatório e sempre vem do reino.
6. Arraste o lingote correspondente do seu inventário para o encaixe redondo de **LINGOTE**. O mod consome um lingote desse espaço e entrega a moeda personalizada no seu inventário; ao fechar a mesa, qualquer lingote restante volta para você.

Todas as regras importantes são verificadas pelo servidor: reino, vinculação da Casa da Moeda, distância, brasão, metal, os dois símbolos e lingote.

## Para desenvolvedores

Com Java 25 instalado, execute `gradlew.bat build`. O arquivo final será criado em `build/libs/crownscoins-1.0.0.jar`.

## Licença

All Rights Reserved.
