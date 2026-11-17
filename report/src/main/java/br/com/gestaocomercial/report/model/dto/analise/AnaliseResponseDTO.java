package br.com.gestaocomercial.report.model.dto.analise;

import java.util.List;
import java.util.Map;

public record AnaliseResponseDTO<T>(
        Map<String, Object> kpis,
        List<T> dados
) {}
