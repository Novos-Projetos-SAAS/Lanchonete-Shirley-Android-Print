# Shirley Impressão

Aplicativo Android nativo que consome a fila durável do backend e envia comandas ESC/POS por Bluetooth Classic SPP. Ele não contém credenciais, URL fixa de produção ou conexão com banco.

## Estado da Etapa 10

O app agora separa turno solicitado de serviço realmente ativo, persiste ACK pendente com lease cifrado pelo Android Keystore, reconcilia o job antes de continuar e nunca reimprime automaticamente depois da fronteira de envio. Também possui desvinculação segura, atualização de Bluetooth ao retomar, backoff de rede e aviso indeterminado que exige conferência manual.

Leia `docs/46_CONFIABILIDADE_APP_ANDROID_ETAPA_10.md`, `docs/47_BUILD_TESTES_E_LIMITACOES_ETAPA_10.md`, `docs/STATUS_ATUAL.md` e `docs/HANDOFF_CHATGPT.md`.

## Abrir e compilar

Abra esta pasta no Android Studio, instale Android SDK 36 e use JDK 17+. O projeto usa AGP 8.13.2, Kotlin/Compose e Compose BOM 2026.09.00. Um Gradle Wrapper binário não foi gerado nesta máquina porque Gradle/Android SDK não estavam instalados; o Android Studio pode sincronizar o projeto e gerar o wrapper antes do CI.

O fluxo de uso é: gerar código no painel web → informar URL HTTPS/código → escolher dispositivo já pareado no Android → configurar 58/80 mm, CP860/CP850 e corte → imprimir teste → iniciar turno.

## Garantias e limites

- segredo cifrado com Android Keystore;
- foreground service iniciado/encerrado pelo operador;
- SSE acelera, polling REST reconcilia;
- ACK pendente e alerta crítico sobrevivem à reinicialização;
- falha após início do envio nunca causa retry automático;
- suporte inicial somente a SPP/RFCOMM ESC/POS;
- BLE e protocolos proprietários exigem outro `PrinterTransport`;
- confirmação do stream não é prova de papel impresso.

Leia a documentação 40–45 em `../Lanchonete-Shirley-Backend/docs/`, principalmente o roteiro de homologação física.
