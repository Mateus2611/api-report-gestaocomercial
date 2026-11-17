package br.com.gestaocomercial.report.model.dto.analise;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DesempenhoClienteDTO(
        Integer clienteId,
        String nome,
        String tipo,
        String cidadeUf,
        String statusCliente,
        Long totalOrcamentos,
        Long totalVendasPagas,
        BigDecimal valorTotalComprado,
        BigDecimal ticketMedio,
        Double mediaAvaliacao,
        LocalDate ultimaCompra
) {}