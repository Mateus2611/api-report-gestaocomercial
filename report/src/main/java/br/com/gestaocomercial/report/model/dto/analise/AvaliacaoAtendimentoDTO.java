package br.com.gestaocomercial.report.model.dto.analise;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AvaliacaoAtendimentoDTO(
        Integer vendaId,
        String clienteNome,
        String tipoCliente,
        LocalDate dataVenda,
        BigDecimal valorFinal,
        Float notaAvaliacao,
        String classificacao
) {}
