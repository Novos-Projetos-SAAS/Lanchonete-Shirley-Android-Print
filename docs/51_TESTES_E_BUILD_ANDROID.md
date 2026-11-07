# Testes e build Android — Etapa 11

Data: 06/11/2026.

## Alterações revisadas

- `WAKE_LOCK` declarado no manifesto.
- `PrintForegroundService` mantém um wake lock parcial somente durante o turno ativo e o libera no encerramento normal, erro e `onDestroy`.
- Teste instrumentado verifica que credencial e lease são recuperáveis pelo armazenamento seguro e não aparecem em texto puro no `SharedPreferences`.
- Teste instrumentado confirma que intenção persistida de turno não simula serviço realmente ativo.
- Reconciliação ganhou regressão para resultado terminal remoto conflitante, que permanece em espera e não reimprime.

## Execução possível neste computador

A revisão estática foi concluída, mas não foi possível executar `gradlew.bat test`, `lint` ou `assembleDebug` porque não foram encontrados:

- Android Studio;
- Android SDK/API 36;
- Platform-Tools/ADB;
- Gradle instalado;
- Gradle Wrapper no repositório;
- Kotlin CLI.

O JDK 21 está disponível. Nenhum APK foi produzido e nenhum teste JUnit/instrumentado foi declarado como executado.

## Próximo gate no Android Studio

1. Abrir este projeto existente, sem recriá-lo.
2. Instalar/selecionar SDK API 36 e aceitar o sync do Gradle.
3. Gerar o wrapper Gradle 8.13 caso o Android Studio não o faça.
4. Executar `gradlew.bat test`, `gradlew.bat lint` e `gradlew.bat assembleDebug`.
5. Instalar apenas o APK aprovado em um aparelho de teste e seguir `53_MATRIZ_HOMOLOGACAO_FISICA.md`.

