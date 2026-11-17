package br.com.gestaocomercial.report.controller;

import br.com.gestaocomercial.report.model.ClienteReport;
import br.com.gestaocomercial.report.model.OrcamentoReport;
import br.com.gestaocomercial.report.model.VendaReport;
import br.com.gestaocomercial.report.model.dto.analise.*;
import br.com.gestaocomercial.report.service.RelatorioService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/relatorios")
public class RelatorioController {

    private final RelatorioService relatorioService;

    public RelatorioController(RelatorioService relatorioService) {
        this.relatorioService = relatorioService;
    }

    @GetMapping("/analises/vendas")
    public ResponseEntity<AnaliseResponseDTO<VendaDetalhadaDTO>> analiseVendas(
            @RequestParam(required = false) LocalDate dataInicio,
            @RequestParam(required = false) LocalDate dataFim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cidade) {
        return ResponseEntity.ok(relatorioService.gerarAnaliseVendas(dataInicio, dataFim, status, cidade));
    }

    @GetMapping("/analises/conversao-orcamentos")
    public ResponseEntity<AnaliseResponseDTO<ConversaoOrcamentoDTO>> analiseConversaoOrcamentos(
            @RequestParam(required = false) LocalDate dataInicio,
            @RequestParam(required = false) LocalDate dataFim,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(relatorioService.gerarAnaliseConversaoOrcamentos(dataInicio, dataFim, status));
    }

    @GetMapping("/analises/desempenho-clientes")
    public ResponseEntity<AnaliseResponseDTO<DesempenhoClienteDTO>> analiseClientes(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cidade,
            @RequestParam(required = false) Boolean apenasInativos) {
        return ResponseEntity.ok(relatorioService.gerarAnaliseClientes(status, cidade, apenasInativos));
    }

    @GetMapping("/analises/avaliacoes")
    public ResponseEntity<AnaliseResponseDTO<AvaliacaoAtendimentoDTO>> analiseAvaliacoes(
            @RequestParam(required = false) LocalDate dataInicio,
            @RequestParam(required = false) LocalDate dataFim,
            @RequestParam(required = false) String classificacao) {
        return ResponseEntity.ok(relatorioService.gerarAnaliseAvaliacoes(dataInicio, dataFim, classificacao));
    }

    @GetMapping("/vendas")
    public ResponseEntity<Page<VendaReport>> listarVendasPaginadas(
            @RequestParam(required = false) LocalDate dataInicio,
            @RequestParam(required = false) LocalDate dataFim,
            @RequestParam(required = false) String status,
            @PageableDefault(page = 0, size = 10, sort = "dataCriacao", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<VendaReport> pagina = relatorioService.relatorioVendasFiltrado(dataInicio, dataFim, status, pageable);
        return ResponseEntity.ok(pagina);
    }

    @GetMapping("/vendas/exportar/pdf")
    public ResponseEntity<byte[]> exportarPdfVendas(
            @RequestParam(required = false) LocalDate dataInicio,
            @RequestParam(required = false) LocalDate dataFim,
            @RequestParam(required = false) String status,
            @PageableDefault(page = 0, size = 50, sort = "dataCriacao", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<VendaReport> pagina = relatorioService.relatorioVendasFiltrado(dataInicio, dataFim, status, pageable);
        byte[] pdf = relatorioService.exportarVendasPdf(pagina.getContent());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=vendas.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/vendas/exportar/excel")
    public ResponseEntity<byte[]> exportarExcelVendas(
            @RequestParam(required = false) LocalDate dataInicio,
            @RequestParam(required = false) LocalDate dataFim,
            @RequestParam(required = false) String status,
            @PageableDefault(page = 0, size = 50, sort = "dataCriacao", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<VendaReport> pagina = relatorioService.relatorioVendasFiltrado(dataInicio, dataFim, status, pageable);
        byte[] excel = relatorioService.exportarVendasExcel(pagina.getContent());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=vendas.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }


    @GetMapping("/clientes")
    public ResponseEntity<Page<ClienteReport>> listarClientesPaginados(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cidade,
            @PageableDefault(page = 0, size = 10, sort = "nome", direction = Sort.Direction.ASC) Pageable pageable) {

        Page<ClienteReport> pagina = relatorioService.relatorioClientesFiltrado(status, cidade, pageable);
        return ResponseEntity.ok(pagina);
    }

    @GetMapping("/clientes/exportar/pdf")
    public ResponseEntity<byte[]> exportarPdfClientes(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cidade,
            @PageableDefault(page = 0, size = 50, sort = "nome", direction = Sort.Direction.ASC) Pageable pageable) {

        Page<ClienteReport> pagina = relatorioService.relatorioClientesFiltrado(status, cidade, pageable);
        byte[] pdf = relatorioService.exportarClientesPdf(pagina.getContent());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=clientes.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/clientes/exportar/excel")
    public ResponseEntity<byte[]> exportarExcelClientes(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cidade,
            @PageableDefault(page = 0, size = 50, sort = "nome", direction = Sort.Direction.ASC) Pageable pageable) {

        Page<ClienteReport> pagina = relatorioService.relatorioClientesFiltrado(status, cidade, pageable);
        byte[] excel = relatorioService.exportarClientesExcel(pagina.getContent());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=clientes.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    @GetMapping("/orcamentos")
    public ResponseEntity<Page<OrcamentoReport>> listarOrcamentosPaginados(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate dataInicio,
            @RequestParam(required = false) LocalDate dataFim,
            @PageableDefault(page = 0, size = 10, sort = "dataCriacao", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<OrcamentoReport> pagina = relatorioService.relatorioOrcamentoFiltrado(status, dataInicio, dataFim, pageable);
        return ResponseEntity.ok(pagina);
    }

    @GetMapping("/orcamentos/exportar/pdf")
    public ResponseEntity<byte[]> exportarPdfOrcamentos(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate dataInicio,
            @RequestParam(required = false) LocalDate dataFim,
            @PageableDefault(page = 0, size = 50, sort = "dataCriacao", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<OrcamentoReport> pagina = relatorioService.relatorioOrcamentoFiltrado(status, dataInicio, dataFim, pageable);
        byte[] pdf = relatorioService.exportarOrcamentosPdf(pagina.getContent());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=orcamentos.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/orcamentos/exportar/excel")
    public ResponseEntity<byte[]> exportarExcelOrcamentos(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate dataInicio,
            @RequestParam(required = false) LocalDate dataFim,
            @PageableDefault(page = 0, size = 50, sort = "dataCriacao", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<OrcamentoReport> pagina = relatorioService.relatorioOrcamentoFiltrado(status, dataInicio, dataFim, pageable);
        byte[] excel = relatorioService.exportarOrcamentosExcel(pagina.getContent());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=orcamentos.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    @GetMapping("/analises/vendas/exportar/pdf")
    public ResponseEntity<byte[]> exportarAnaliseVendasPdf(
            @RequestParam(required = false) LocalDate dataInicio,
            @RequestParam(required = false) LocalDate dataFim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cidade) {
        var analise = relatorioService.gerarAnaliseVendas(dataInicio, dataFim, status, cidade);
        return responderArquivo(relatorioService.exportarAnaliseVendasPdf(analise), "analise-vendas.pdf", MediaType.APPLICATION_PDF);
    }

    @GetMapping("/analises/vendas/exportar/excel")
    public ResponseEntity<byte[]> exportarAnaliseVendasExcel(
            @RequestParam(required = false) LocalDate dataInicio,
            @RequestParam(required = false) LocalDate dataFim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cidade) {
        var analise = relatorioService.gerarAnaliseVendas(dataInicio, dataFim, status, cidade);
        return responderArquivo(relatorioService.exportarAnaliseVendasExcel(analise), "analise-vendas.xlsx",
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @GetMapping("/analises/conversao-orcamentos/exportar/pdf")
    public ResponseEntity<byte[]> exportarAnaliseConversaoPdf(
            @RequestParam(required = false) LocalDate dataInicio,
            @RequestParam(required = false) LocalDate dataFim,
            @RequestParam(required = false) String status) {
        var analise = relatorioService.gerarAnaliseConversaoOrcamentos(dataInicio, dataFim, status);
        return responderArquivo(relatorioService.exportarAnaliseConversaoPdf(analise), "analise-conversao-orcamentos.pdf", MediaType.APPLICATION_PDF);
    }

    @GetMapping("/analises/conversao-orcamentos/exportar/excel")
    public ResponseEntity<byte[]> exportarAnaliseConversaoExcel(
            @RequestParam(required = false) LocalDate dataInicio,
            @RequestParam(required = false) LocalDate dataFim,
            @RequestParam(required = false) String status) {
        var analise = relatorioService.gerarAnaliseConversaoOrcamentos(dataInicio, dataFim, status);
        return responderArquivo(relatorioService.exportarAnaliseConversaoExcel(analise), "analise-conversao-orcamentos.xlsx",
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @GetMapping("/analises/desempenho-clientes/exportar/pdf")
    public ResponseEntity<byte[]> exportarAnaliseClientesPdf(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cidade,
            @RequestParam(required = false) Boolean apenasInativos) {
        var analise = relatorioService.gerarAnaliseClientes(status, cidade, apenasInativos);
        return responderArquivo(relatorioService.exportarAnaliseClientesPdf(analise), "analise-desempenho-clientes.pdf", MediaType.APPLICATION_PDF);
    }

    @GetMapping("/analises/desempenho-clientes/exportar/excel")
    public ResponseEntity<byte[]> exportarAnaliseClientesExcel(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cidade,
            @RequestParam(required = false) Boolean apenasInativos) {
        var analise = relatorioService.gerarAnaliseClientes(status, cidade, apenasInativos);
        return responderArquivo(relatorioService.exportarAnaliseClientesExcel(analise), "analise-desempenho-clientes.xlsx",
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @GetMapping("/analises/avaliacoes/exportar/pdf")
    public ResponseEntity<byte[]> exportarAnaliseAvaliacoesPdf(
            @RequestParam(required = false) LocalDate dataInicio,
            @RequestParam(required = false) LocalDate dataFim,
            @RequestParam(required = false) String classificacao) {
        var analise = relatorioService.gerarAnaliseAvaliacoes(dataInicio, dataFim, classificacao);
        return responderArquivo(relatorioService.exportarAnaliseAvaliacoesPdf(analise), "analise-avaliacoes.pdf", MediaType.APPLICATION_PDF);
    }

    @GetMapping("/analises/avaliacoes/exportar/excel")
    public ResponseEntity<byte[]> exportarAnaliseAvaliacoesExcel(
            @RequestParam(required = false) LocalDate dataInicio,
            @RequestParam(required = false) LocalDate dataFim,
            @RequestParam(required = false) String classificacao) {
        var analise = relatorioService.gerarAnaliseAvaliacoes(dataInicio, dataFim, classificacao);
        return responderArquivo(relatorioService.exportarAnaliseAvaliacoesExcel(analise), "analise-avaliacoes.xlsx",
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    private ResponseEntity<byte[]> responderArquivo(byte[] conteudo, String nomeArquivo, MediaType mediaType) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + nomeArquivo)
                .contentType(mediaType)
                .body(conteudo);
    }
}