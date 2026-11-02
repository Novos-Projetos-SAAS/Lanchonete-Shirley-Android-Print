# Confiabilidade do app Android — Etapa 10

Data: 31/10/2026.

## Implementado

- Serviço real, turno solicitado e estado persistido são separados; uma flag antiga não faz a UI alegar que o serviço está ativo.
- O envio é protegido por mutex e o encerramento aguarda a operação corrente.
- Falha de ACK é tentada três vezes; depois, metadados ficam persistidos e a lease permanece cifrada no Android Keystore.
- Ao retomar, o app repete o ACK de modo idempotente e consulta o estado do job antes de continuar.
- Depois de iniciar o envio Bluetooth, nenhuma falha autoriza reimpressão automática; o operador recebe alerta indeterminado persistente.
- SSE acelera a descoberta, polling a cada 15 s reconcilia e erros usam backoff exponencial até 60 s.
- `401` interrompe o agente e pede novo pareamento; desvinculação remota só limpa o aparelho após sucesso ou credencial já inválida.
- Bluetooth é reavaliado no `onResume`; iniciar turno fica desabilitado sem permissão, aparelho ou Bluetooth.
- Teste local não cria pedido/job e pede confirmação explícita antes de enviar bytes.

## Segurança

- credencial do dispositivo e lease pendente usam Android Keystore;
- preferências comuns guardam apenas metadados não secretos;
- URL exige HTTPS fora de hosts de desenvolvimento;
- nenhum segredo, banco ou URL de produção foi adicionado ao código.

## Limite essencial

Sucesso no socket Bluetooth significa somente que bytes foram aceitos pelo stream; não prova impressão em papel. Essa distinção permanece explícita na UI e deve ser validada fisicamente.
