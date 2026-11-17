package br.com.gestaocomercial.report.model.dto.analise;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VendaDetalhadaDTO(
        Integer vendaId,
        Integer orcamentoId,
        Integer clienteId,
        String clienteNome,
        String clienteCidade,
        LocalDate dataCriacao,
        String statusPagamento,
        BigDecimal valorFinal,
        Float notaAvaliacao
) {}
