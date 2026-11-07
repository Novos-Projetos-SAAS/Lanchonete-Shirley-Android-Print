# Matriz de homologação física Android/ESC-POS

Data de preparação: 06/11/2026. Status geral: **PENDENTE DE HARDWARE**.

Preencha aparelho, Android, impressora, firmware, largura, codificação, data, resultado e evidência em cada caso. Não aprove produção apenas por teste em emulador.

| Caso | Resultado esperado | Status |
|---|---|---|
| Pareamento SPP Classic | conecta uma vez e persiste somente credencial cifrada | PENDENTE |
| Bobina 58 mm | texto legível, alinhado e sem corte lateral | PENDENTE |
| Bobina 80 mm | largura de 48 colunas correta | PENDENTE |
| CP860 e CP850 | `ç`, acentos e símbolos monetários legíveis | PENDENTE |
| Corte habilitado/desabilitado | comando emitido somente quando compatível | PENDENTE |
| Bluetooth desligado | UI informa erro recuperável, sem ACK falso | PENDENTE |
| Permissão negada | turno não inicia e orientação é clara | PENDENTE |
| Telefone offline | reconecta com backoff, sem duplicar impressão | PENDENTE |
| Falha antes do stream | job pode ser reenfileirado conforme limite | PENDENTE |
| Falha após `iniciar-envio` | fica `RESULTADO_INDETERMINADO`, sem retry automático | PENDENTE |
| ACK perdido | reinício reconcilia o mesmo resultado, sem reimpressão | PENDENTE |
| App encerrado/reaberto | pending ACK/lease são recuperados do Keystore | PENDENTE |
| Telefone reiniciado | operador retoma turno conscientemente | PENDENTE |
| Tela bloqueada | serviço mantém processamento e wake lock sem travar UI | PENDENTE |
| Otimização de bateria | comportamento e instruções ao operador registrados | PENDENTE |
| Sem papel/tampa aberta | erro visível; nenhuma confirmação de sucesso | PENDENTE |
| Fora de alcance | falha conservadora conforme fronteira de envio | PENDENTE |
| Desvincular/reparear | servidor revoga antes da limpeza local | PENDENTE |
| TalkBack | controles, estados e alertas têm nomes compreensíveis | PENDENTE |
| Fonte máxima | conteúdo não fica inacessível | PENDENTE |
| Retrato/paisagem | ações principais permanecem utilizáveis | PENDENTE |
| Tema claro/escuro | contraste e estados permanecem legíveis | PENDENTE |

## Critério de aprovação

Todos os casos críticos de duplicidade, fronteira Bluetooth, ACK, revogação e impressão legível devem passar em aparelho e impressora reais. Registre qualquer caso não aplicável com justificativa técnica e modelo do equipamento.

