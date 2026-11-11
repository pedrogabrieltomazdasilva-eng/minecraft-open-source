# Licenças e dependências

A licença MIT na raiz cobre o código original deste projeto. Não substitui licenças de terceiros, a EULA do Minecraft ou os termos de ferramentas. O nome Minecraft é usado para identificar compatibilidade; este projeto não é um produto oficial da Mojang/Microsoft.

| Componente | Uso / situação |
| --- | --- |
| Gradle Wrapper 8.11.1 | Bootstrap oficial do build. Scripts preservam cabeçalhos Apache-2.0; o JAR do wrapper é a única exceção à exclusão de JARs. https://github.com/gradle/gradle/blob/v8.11.1/LICENSE |
| Android Gradle Plugin 8.9.1 | Dependência de build obtida de Google Maven, Apache-2.0. Não está vendorizada. |
| Kotlin Gradle Plugin 2.1.20 | Dependência de build, Apache-2.0. Não está vendorizada. https://github.com/JetBrains/kotlin/blob/v2.1.20/license/LICENSE.txt |
| Android SDK / NDK | Ferramentas locais excluídas do Git. Sujeitas aos termos do Google e avisos dos componentes. O NOTICE do sysroot usado no protótipo fica nos assets. |
| OpenJDK / Temurin | Binários excluídos. GPLv2 e exceções aplicáveis, além de avisos dos módulos; preservar fontes correspondentes e avisos ao redistribuir runtime modificado. O patch local é contribuição original; o OpenJDK resultante mantém sua licença upstream. |
| Amethyst / FCL, Freetype, CUPS e termux-elf-cleaner | Fontes baixadas apenas pelo build remoto preparado. Revisões, hashes e origem em `tools/openjdk25/build_ci.py`. Conservar e revisar as licenças específicas dos arquivos de cada revisão, inclusive binários de override upstream, antes de distribuir o runtime. |
| Minecraft e softwares/mods de servidor | Nenhum JAR, mod ou plugin é incluído neste repositório. Aplicam-se as respectivas licenças e a EULA oficial; seleção no assistente não concede direitos de distribuição. |

Esta preparação não conclui auditoria jurídica nem verifica a licença individual de cada binário de override do Amethyst. Essa revisão continua sendo um requisito antes de empacotar/distribuir um runtime novo. Os arquivos do Anvil-MC e a mídia de referência estão excluídos do versionamento.
