# OpenJDK 25 no Android: diretório dos módulos

## Evidência do Galaxy S23 FE

- O launcher está em `ApplicationInfo.nativeLibraryDir` e carrega `libjvm.so` desse diretório.
- `files/jre25/lib/modules` existe e tem 126.800.736 bytes.
- O OpenJDK usa o local real da biblioteca para deduzir seu diretório interno; o `lib/modules` inferido não existe (`errno=2`).
- A VM encerra com `Failed setting boot class path` antes do autoteste imprimir `JVM_READY`.

## Decisão técnica

Manter **todo código nativo executável** no diretório de bibliotecas do APK e **todos os dados do runtime** em `files/jre25`. Adaptar a versão Android do OpenJDK 25 para aceitar `OPENMC_RUNTIME_HOME` como raiz de dados durante `os::init_system_properties_values()`, antes de `set_boot_path()`. Aceitar `OPENMC_NATIVE_DIR` como diretório de bibliotecas nativas. O launcher existente define os dois valores; `-Djava.home` permanece apenas como propriedade Java e não resolve a inicialização antecipada.

O patch preparado está em `tools/openjdk25/openmc-runtime-home.patch`. Ele foi comparado com `openjdk/jdk25u` e analisado sintaticamente por `git apply --numstat`; **a aplicação e a compilação do OpenJDK ainda não foram verificadas** porque as fontes e o ambiente Linux de compilação não estão instalados. Aplicá-lo à árvore de fontes Android **depois** dos patches do projeto Amethyst, com `git apply --check` antes da alteração. Conservar o build anterior em W: até que o novo passe nos testes.

## Alternativas descartadas

| Opção | Resultado |
|---|---|
| Ajustar apenas `JAVA_HOME` ou `-Djava.home` | A inicialização do boot path usa a localização da biblioteca antes de ler essa propriedade. |
| Carregar por um vínculo simbólico em `files/jre25` | O linker informa o caminho físico; o vínculo não muda o diretório inferido. |
| Copiar `libjvm.so` para `filesDir` e usar `dlopen` | O Android restringe o mapeamento executável de código gravável pelo app para target API 29+. |
| Criar `lib/modules` na pasta nativa do APK | A pasta instalada pelo gerenciador de pacotes não é gravável pelo aplicativo. |
| Interceptar `dladdr`, alterar SELinux ou permissões do sistema | Frágil e incompatível com o objetivo de segurança. |

## Requisitos para o build do runtime

O projeto Amethyst/FCL para JRE 25 documenta **NDK r28**, **JDK 24 de bootstrap**, fontes do OpenJDK 25 e ferramentas de build Linux. Neste computador há somente NDK r30, JDK 21 e `wsl.exe` sem WSL instalado. O usuário autorizou preparar as dependências, mas condicionou a instalação do WSL: se ela exigir privilégio administrativo, reinicialização ou alteração importante no Windows, interromper. Habilitar o subsistema Linux é uma mudança global do Windows, então essa etapa foi interrompida sem instalar nada. C: tem pouca margem acima de 1 GiB; os artefatos Gradle foram direcionados para W:. Um ambiente Linux existente e autorizado, inclusive remoto, pode substituir WSL sem alterar o Windows.

## Critérios de aceitação

Alternativa preparada sem WSL: [build manual no GitHub Actions](openjdk25-github-actions.md), com workflow, revisões fixadas, rebase Android verificado e instruções de integração. Ainda não foi publicado nem executado.

1. Verificar procedência, hashes e licenças das fontes e ferramentas; aplicar o patch com `git apply --check`.
2. Compilar `libjvm.so` Android ARM64 a partir do mesmo release e conjunto de patches do runtime empacotado; não misturar builds incompatíveis.
3. Confirmar arquitetura ELF AArch64, dependências nativas e licença do binário modificado.
4. Atualizar o APK preservando a interface e o restante do projeto; verificar CRC e assinatura.
5. No S23 FE, o autoteste deve mostrar `JVM_READY`, `java.version` esperado e `java.home` apontando para `files/jre25`; sem esse teste a JVM não é considerada funcional.
