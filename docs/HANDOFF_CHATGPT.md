# Handoff

Data: 08/11/2026. Projeto: `Lanchonete-Shirley-Android-Print`.

## Padronização de erros — handoff prioritário

- `PrintApiException` preserva status/código/referência/detalhes seguros; usar `operatorMessage` para UI e serviço.
- Não exibir `Throwable.message` diretamente ao operador.
- Não alterar a fronteira `startSending` nem permitir retry automático de resultado indeterminado.
- Três testes novos cobrem parser/fallback/rede/Bluetooth, mas o Gradle não iniciou por bloqueio de loopback; SDK Android não configurado.
- Próximo gate no Android Studio: `testDebugUnitTest lintDebug assembleDebug`, depois matriz física 53.
- Relatório: `54_TRATAMENTO_ERROS_API_BLUETOOTH.md`.

## Etapa 11

- Backend/TEST concluídos em 40/10 e contratos Android aprovados nas 52 integrações.
- Wake lock parcial existe somente durante o turno e é liberado em todos os caminhos de encerramento.
- Testes novos cobrem segredo cifrado, diferença entre turno solicitado e serviço vivo e resultado remoto conflitante.
- Não houve compilação local: toolchain Android ausente e wrapper inexistente.
- Você deve abrir o projeto no Android Studio, sincronizar API 36/Gradle, rodar `test lint assembleDebug` e então preencher a matriz física 53.

Não considerar o aplicativo homologado nem distribuir APK antes desses gates.

## Etapa 10

- Não reimprimir automaticamente depois de `startSending`; falha nessa região é indeterminada e exige operador.
- Antes de consumir nova fila, resolver `PendingReport` com lease recuperada do Keystore.
- Não mover a lease para `PrintConfigStore`/SharedPreferences em texto legível.
- Estado `running` do serviço é a verdade da UI; `shiftRequested` é apenas intenção persistida.
- Desvinculação deve revogar o backend antes da limpeza local, exceto quando o servidor já responde 401/404.
- Compilação e testes Android não foram executados por ausência do toolchain.

Próximo passo: gerar wrapper em ambiente Android, rodar `test lint assembleDebug`, corrigir qualquer incompatibilidade de API/Compose e somente então iniciar a matriz física.
