# Handoff

Data: 31/10/2026. Projeto: `Lanchonete-Shirley-Android-Print`.

## Etapa 10

- Não reimprimir automaticamente depois de `startSending`; falha nessa região é indeterminada e exige operador.
- Antes de consumir nova fila, resolver `PendingReport` com lease recuperada do Keystore.
- Não mover a lease para `PrintConfigStore`/SharedPreferences em texto legível.
- Estado `running` do serviço é a verdade da UI; `shiftRequested` é apenas intenção persistida.
- Desvinculação deve revogar o backend antes da limpeza local, exceto quando o servidor já responde 401/404.
- Compilação e testes Android não foram executados por ausência do toolchain.

Próximo passo: gerar wrapper em ambiente Android, rodar `test lint assembleDebug`, corrigir qualquer incompatibilidade de API/Compose e somente então iniciar a matriz física.
