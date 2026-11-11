# Testes pendentes no Android ARM64

O usuário executou o APK de diagnóstico no Galaxy S23 FE. O teste confirmou que `lib/modules` existe em `files/jre25/lib/modules` (126.800.736 bytes), mas a `libjvm.so` carregada do diretório nativo do APK infere outro caminho. A VM encerrou com `Failed setting boot class path` e código 1. O APK atual ainda contém a mesma biblioteca e **não deve ser considerado uma correção**. Nenhum servidor Minecraft foi validado.

## Prova da JVM

1. Executar este teste **somente após** compilar e empacotar uma `libjvm.so` adaptada do mesmo build do runtime. Usar um dispositivo ARM64 com Android 10 ou superior e espaço suficiente para o APK e os dados do runtime. Instalar o APK debug somente com autorização do dono do dispositivo.
2. Abrir o app e tocar em **Testar JVM Android**, sem selecionar um servidor. O app deverá extrair apenas dados do OpenJDK, manter os binários no diretório de bibliotecas nativas do APK e iniciar o launcher num processo filho.
3. Confirmar no console a linha `JVM_READY version=25.0.5`, saída com código 0 e retorno ao estado parado. Conferir `java.home` e os caminhos diagnósticos. Se falhar, registrar a mensagem do console, a versão do Android e o ABI; investigar também `logcat` com autorização.
4. Repetir o teste para conferir a reutilização da instalação privada e verificar que a rotação da tela preserva o estado e os logs.

## Prova do servidor

1. Obter um JAR de servidor de fonte oficial, compatível com Java 25. O app não distribui JAR do Minecraft. Aceitar a licença/EULA aplicável antes de iniciar o servidor; não habilitar `eula=true` automaticamente.
2. Importar o JAR, iniciar e confirmar logs reais de inicialização. Enviar um comando inofensivo pelo console e confirmar sua resposta. Parar com o botão **Parar** e verificar encerramento limpo; testar o prazo de parada separadamente.
3. Verificar limites de memória, reinício após falha, rede local e comportamento ao enviar o app para segundo plano. O app ainda não oferece serviço em primeiro plano nem garantia de execução contínua.

O teste inicial da JVM foi executado e falhou conforme descrito. As provas de JVM corrigida e de servidor continuam pendentes. Compilação, assinatura e inspeção estática do APK não substituem esses testes.
