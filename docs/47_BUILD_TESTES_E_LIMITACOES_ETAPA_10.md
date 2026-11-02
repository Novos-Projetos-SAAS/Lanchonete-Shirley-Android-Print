# Build, testes e limitações — Etapa 10

Data: 31/10/2026.

## Verificações realizadas

- Revisão estática dos modelos, armazenamento, API, serviço, ViewModel e Compose UI.
- Teste unitário adicionado para decisões de reconciliação: confirmado, seguro antes do envio, revisão manual depois do envio e espera.
- Busca estática confirmou que a lease pendente é gravada apenas pelo `SecureTokenStore`, cifrado com Android Keystore.
- UI usa componentes Material, confirma ações críticas e mantém alvos de toque mínimos de 48 dp nos controles adicionados.

## Build não executável neste host

Android Studio, Android SDK, Gradle, Gradle Wrapper, ADB e Kotlinc não estão disponíveis. Portanto, nenhum APK, teste JVM, instrumentado ou emulador foi executado. O código não deve ser considerado compilado até o gate abaixo passar em uma estação Android.

## Gate futuro

1. Abrir no Android Studio com SDK 36/JDK 17+ e gerar o wrapper.
2. Executar `./gradlew test lint assembleDebug`.
3. Executar testes instrumentados em aparelho API 31+.
4. Validar telefone pequeno/tablet, retrato/paisagem, fonte máxima, TalkBack e temas claro/escuro.
5. Cumprir `docs/HOMOLOGACAO_FISICA.md` com impressoras reais 58/80 mm.

## Restrições respeitadas

Nenhuma migration, seed, banco, `.env`, credencial, instalação em aparelho, deploy, commit ou push foi executado.
