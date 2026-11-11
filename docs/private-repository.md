# Preparação do repositório privado

O Git contém código Kotlin/Java/C original, recursos vetoriais, Gradle Wrapper, configurações de build, patches, workflow e documentação. APKs, vídeo, bibliotecas nativas, dados binários do runtime, SDK/NDK, arquivos de referência e caches permanecem locais e ignorados.

O clone não contém um runtime funcional pronto. Restaurar os artefatos verificados pelo procedimento de `openjdk25-github-actions.md` antes de empacotar/testar a JVM; não baixar nem instalar automaticamente durante publicação. O erro conhecido de boot class path continua pendente.

O workflow `openjdk25-android.yml` está incluído, com disparo exclusivamente manual. **Ele permanece bloqueado em repositórios privados**, conforme a proteção de custos preparada anteriormente. Publicar o projeto privado não autoriza retirar essa proteção nem executar o workflow. Caso seja desejado um build privado, revisar primeiro saldo, cotas e orçamento da conta e obter autorização específica. Alternativamente, preparar futuramente um repositório público separado contendo apenas os arquivos permitidos da automação, após autorização.

Nenhum remoto é necessário para o commit local. Não configurar URL de remoto com token embutido. Somente após receber URL e autorização de envio, conferir novamente `git status`, `git ls-files` e exclusões antes de adicionar o remoto e fazer push. `.gitignore` não é um scanner de segredos: novos arquivos devem ser revisados antes de cada commit.

Os dados do runtime são distribuídos separadamente e as licenças de terceiros não são substituídas pela licença MIT do código original. O Gradle Wrapper JAR é uma exceção intencional às exclusões de binários, necessária para o build reproduzível. Caminhos de máquina citados na documentação são exemplos do ambiente de desenvolvimento e não contêm credenciais.
