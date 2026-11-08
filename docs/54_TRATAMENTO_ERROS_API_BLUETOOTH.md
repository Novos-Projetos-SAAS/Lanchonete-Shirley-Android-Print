# Tratamento de erros da API e Bluetooth

Data: 08/11/2026.

## Implementação

- `PrintApi` interpreta `status`, `code`, `message`, `request_id` e `details` com kotlinx.serialization.
- Corpo inválido, mensagem com aparência de stack e metadados fora do formato recebem fallback por status; corpo bruto nunca é apresentado.
- O operador recebe mensagens diferentes para timeout, DNS/conexão, pareamento inválido, permissão e comunicação Bluetooth.
- `MainViewModel` mantém pareamento e configuração quando a falha é recuperável e orienta novo pareamento em 401.
- O serviço usa mensagens seguras sem tokens e conserva backoff/reconciliação.
- Estados `FALHA_ANTES_ENVIO` e `RESULTADO_INDETERMINADO`, lease persistida e pausa por confirmação pendente não foram alterados. Não existe reimpressão automática após a fronteira `startSending`.

## Testes e impedimento

Foram adicionados três testes unitários para envelope JSON, resposta inválida/técnica, 401 de pareamento, timeout, conexão e Bluetooth. Os quatro testes existentes de `JobReconciliation` continuam como proteção explícita contra duplicidade.

Comandos tentados:

- `gradlew.bat testDebugUnitTest`;
- `gradlew.bat --no-daemon testDebugUnitTest`.

Ambos pararam antes da configuração com `java.io.IOException: Unable to establish loopback connection`. Java 21 está disponível, mas `ANDROID_HOME` e `ANDROID_SDK_ROOT` não estão configurados. Assim, os testes novos não foram executados, não houve `lint`, APK, emulador ou homologação física.

Próximo passo: abrir no Android Studio, instalar/selecionar SDK 36, sincronizar o projeto e executar `gradlew testDebugUnitTest lintDebug assembleDebug`. Depois validar pareamento expirado, perda de rede e impressora desligada em aparelho real, mantendo a matriz física 53.
