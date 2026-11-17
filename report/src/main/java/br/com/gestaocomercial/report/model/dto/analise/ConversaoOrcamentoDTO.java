package br.com.gestaocomercial.report.model.dto.analise;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ConversaoOrcamentoDTO(
        Integer orcamentoId,
        String clienteNome,
        LocalDate dataCriacao,
        LocalDate dataValidade,
        String statusOrcamento,
        BigDecimal valorTotal,
        BigDecimal desconto,
        boolean convertidoEmVenda,
        Integer vendaId,
        String statusPagamentoVenda
) {}
