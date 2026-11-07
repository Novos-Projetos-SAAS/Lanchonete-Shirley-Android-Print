# Handoff

Data: 06/11/2026. Projeto: `Lanchonete-Shirley-Android-Print`.

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
