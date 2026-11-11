# OpenJDK 25 Android ARM64 por GitHub Actions

Estado: workflow preparado localmente, não publicado nem executado. Não foi compilado um runtime novo. Não há alteração no APK nem no runtime atual. Nenhuma instalação no Windows é necessária.

## Viabilidade e custo

O Amethyst já compila aarch64 em runner Ubuntu. O job oficial de referência iniciou em 2026-08-14 12:04:35 UTC e terminou com sucesso às 12:31:00: aproximadamente 26 minutos. Isso é evidência de viabilidade do build upstream, não uma medição do nosso build modificado.

- Workflow oficial: https://github.com/AngelAuraMC/angelauramc-openjdk-build/blob/7a0266e745d9b4acf400afa189b58e672900f710/.github/workflows/build.yml
- Execução oficial: https://github.com/AngelAuraMC/angelauramc-openjdk-build/actions/runs/31798797699
- Scripts e requisitos: https://github.com/AngelAuraMC/angelauramc-openjdk-build/tree/7a0266e745d9b4acf400afa189b58e672900f710

Segundo as páginas oficiais consultadas em 11/11/2026, runners Linux padrão de repositórios públicos têm 4 CPUs, 16 GB RAM e 14 GB SSD anunciados; o uso desses runners é gratuito. Jobs hospedados têm limite de 6 horas. A documentação atual lista 500 MB de armazenamento de artefatos no GitHub Free e uso incluído de computação em créditos para repositórios privados; não presumir a antiga cota de 2.000 minutos. Planos e limites podem mudar.

- Recursos: https://docs.github.com/en/actions/reference/runners/github-hosted-runners
- Limites: https://docs.github.com/en/actions/reference/limits

Escolha preparada: `ubuntu-24.04` x64 padrão, apenas ARM64 como destino, quatro jobs de compilação, timeout de 120 minutos, sem matriz, cache, release ou runner pago. O workflow recusa repositório privado e exige confirmação manual `COMPILAR`. Essa trava não consulta o saldo ou as políticas da conta: antes da primeira execução autorizada, conferir Billing/Actions e o armazenamento já ocupado. Retenção de artefato de 3 dias e limite local de 400 MiB reduzem armazenamento, mas não garantem gratuidade se a conta já estiver acima da cota. Não habilitar orçamento pago para esta prova.

Estimativa de planejamento, ainda sem medição: 30–90 minutos, até 4 tarefas paralelas e 22–30 GiB de disco livre para NDK, JDK, fontes, objetos, imagens e empacotamento. O workflow exige 22 GiB livres e 12 GiB de RAM total antes de instalar dependências. **Os 14 GB anunciados não garantem esse espaço.** Se a VM entregar menos, o job para sem iniciar os downloads grandes; não há limpeza automática de ferramentas do runner. A falha de preflight requer revisar os números reais antes de qualquer nova execução. Não trocar automaticamente para runner pago. Há também verificação de 2 GiB livres entre fases, sem garantia contra esgotamento dentro de uma compilação.

## Conteúdo permitido para um futuro repositório dedicado

Após autorização de publicação, copiar somente:

1. `.github/workflows/openjdk25-android.yml`
2. `tools/openjdk25/build_ci.py`
3. `tools/openjdk25/openmc-runtime-home.patch`
4. Este documento e uma licença explícita para os arquivos originais da automação.

Não publicar o projeto inteiro, vídeo, APK Anvil-MC, fontes decompiladas, APK do nosso app, chaves, credenciais, diretórios `.tools` ou arquivos pessoais. O checkout esparso não impede publicação acidental por `git add`; manter um repositório separado com essa lista de arquivos. Nada foi enviado nesta etapa. A automação não tem gatilhos push, pull_request ou agendados.

## Revisões e integridade

O script fixa Amethyst `7a0266e745d9b4acf400afa189b58e672900f710`, OpenJDK `c4deee6ead2e27dbd926e3965b2e0311ddbd6b3f` e termux-elf-cleaner `f9273bcb37bf4c187123438566657fa5ac40cd51`. O commit do OpenJDK foi selecionado pelo histórico anterior ao início do build oficial; não há prova de que seja exatamente o commit baixado naquela execução, pois o upstream clona uma branch móvel. Por isso deve-se substituir o runtime inteiro por um conjunto novo coerente, nunca só misturar uma libjvm de outra revisão com módulos antigos.

JDK de bootstrap: Temurin 24.0.2+12, OpenJDK Linux x64 oficial do Eclipse Adoptium, SHA-256 publicado pela API Adoptium. Ele é colocado no caminho `jdk-24.0.2` esperado pelo script upstream; não é redistribuído no runtime. NDK: r28 exato, Google, SHA-1 publicado pelo projeto Android (verificação legada), e SHA-256 adicional registrado no artefato. Fontes Freetype 2.10.0 e CUPS 2.2.4 vêm das URLs upstream. Seus SHA-256 foram calculados nesta investigação a partir de HTTPS e fixados; **não equivalem a uma assinatura independente dos mantenedores**. Os valores e URLs ficam no script. Revisões Git identificam fontes, mas não são atestações de identidade ou assinatura do build.

- NDK r28 e hash oficial: https://github.com/android/ndk/wiki/Home/24fe2d7ee3591346e0e8ae615977a15c0a4fba40
- Bootstrap: https://api.adoptium.net/v3/assets/version/24.0.2+12?architecture=x64&image_type=jdk&os=linux&heap_size=normal&jvm_impl=hotspot&vendor=eclipse

A automação usa os scripts oficiais fixados, sem chmod 777. Remove o reset que apagaria o patch local e o tratamento que ignorava falhas de aplicação. Dois hunks Android foram rebaseados explicitamente: manter a lógica moderna de X11 headless e acrescentar a condição Android; adaptar o fallback aarch64 de SYS_gettid ao bloco atual. O script verifica as âncoras e falha se mudarem. Aplica então o patch Android completo e o patch `OPENMC_RUNTIME_HOME` com `--check --unidiff-zero`. A opção é necessária porque esses diffs têm hunks sem contexto. Nenhum `.rej` é aceito silenciosamente.

Licenças: conservar GPLv2, Classpath Exception, Assembly Exception e avisos de cada módulo do OpenJDK; conservar licenças Freetype, CUPS, elf-cleaner e NOTICE do NDK. O artefato inclui fontes modificadas do OpenJDK, fontes e scripts upstream, pequenas fontes das dependências, patch, driver de build, procedência e checksums. A disponibilidade de fontes/licenças deve acompanhar qualquer futura redistribuição, sem depender da retenção temporária do Actions. Revisar os termos específicos antes de distribuição pública de binários. As dependências apt usam os repositórios autenticados da imagem Ubuntu; suas versões não são fixadas, portanto não se promete reprodução byte a byte.

## Artefato esperado e integração posterior

O upload inclui `openmc-jre25-android-arm64.tar.xz`, `libc++_shared.so` do mesmo NDK r28, NOTICE, fontes correspondentes, `native-dependencies.txt`, `provenance.json` e `SHA256SUMS`. Falhas geram logs separados quando disponíveis. A verificação estática exige ELF64 little-endian AArch64, módulos, licença e strings do patch no libjvm. A lista de DT_NEEDED é exportada para revisão; a disponibilidade de todas as dependências e o carregamento real ainda precisam de teste Android. Não executar o binário Android no runner Linux como prova da JVM.

Após build autorizado e revisão do resultado:

1. Baixar somente o runtime, procedência, checksums, libc++ e NOTICE para W:. Os arquivos de fonte podem continuar disponíveis remotamente, respeitando as obrigações de distribuição. Conferir o digest do artefato exibido no Actions e todos os arquivos escolhidos contra SHA256SUMS. Hashes publicados pelo próprio build detectam corrupção, não tornam um build comprometido confiável.
2. Conferir logs, commits, versão em `release`, módulos e bibliotecas; conservar a versão atual para retorno.
3. Adaptar `tools/prepare_runtime.py` para um novo hash explicitamente aprovado e uma área de staging. **O script atual aceita apenas o arquivo antigo e copia libc++ do NDK r30; não rodá-lo sem adaptação.** Usar libc++ do NDK r28 e seu NOTICE fornecidos no novo artefato. Empacotar dados e todas as bibliotecas do mesmo runtime.
4. Atualizar `RuntimeInstaller.kt`: hoje a validação fixa `JAVA_VERSION="25.0.5"` e módulos com 126.800.736 bytes. Usar versão, tamanho e hash medidos do novo artefato e um identificador de build para invalidar os dados antigos no aparelho. Não simplesmente remover validações. A referência atual a `/files/jre25` pode permanecer; os dados precisam ser reinstalados quando mudar o identificador.
5. Compilar o app com as ferramentas existentes em W:, preservando 1 GiB em C:. Verificar assinatura, CRC e conteúdo. Conservar launcher nativo e logs. Não colocar código ELF executável em filesDir.
6. No S23 FE, exigir `JVM_READY`, versão e java.home esperados, saída 0, repetição sem extração desnecessária e depois um JAR de teste. Somente após isso testar Minecraft, comandos, parada e memória.

## Verificações realizadas nesta preparação

Leitura dos scripts oficiais, metadados da execução bem-sucedida e limites publicados do GitHub. Verificação sintática Python e aplicação local dos dois patches nos 41 arquivos públicos afetados, usando diretórios temporários em W:. O patch Android original falhou em dois hunks; após o rebase, ambos os estágios passaram. Nenhum build, upload, publicação, workflow ou teste Android foi executado. YAML revisado manualmente, ainda sem validação do serviço GitHub. O runtime e o APK existentes permanecem inalterados.

Não foi necessário escolher outro runtime: a rota upstream por Actions é viável com o preflight de recursos. Não há evidência nesta investigação de um runtime alternativo pronto que garanta resolver o layout específico do aplicativo; não substituir por um binário aleatório ou por OpenJDK Linux ARM64/glibc.
