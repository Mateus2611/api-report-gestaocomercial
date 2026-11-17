package br.com.gestaocomercial.report.service;

import br.com.gestaocomercial.report.model.*;
import br.com.gestaocomercial.report.model.dto.*;
import br.com.gestaocomercial.report.model.dto.analise.*;
import br.com.gestaocomercial.report.repository.*;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RelatorioService {

    private final VendaReportRepository vendaRep;
    private final ClienteReportRepository clienteRep;
    private final OrcamentoReportRepository orcamentoRep;
    private final ProdutoReportRepository produtoRep;

    public RelatorioService(VendaReportRepository vendaRep,
                            ClienteReportRepository clienteRep,
                            OrcamentoReportRepository orcamentoRep,
                            ProdutoReportRepository produtoRep) {
        this.vendaRep = vendaRep;
        this.clienteRep = clienteRep;
        this.orcamentoRep = orcamentoRep;
        this.produtoRep = produtoRep;
    }

    public void sincronizarVenda(VendaSyncDTO dto) {
        VendaReport relatorio = vendaRep.findById(dto.id()).orElse(new VendaReport());
        BeanUtils.copyProperties(dto, relatorio);
        vendaRep.save(relatorio);
    }

    public void sincronizarCliente(ClienteSyncDTO dto) {
        ClienteReport relatorio = clienteRep.findById(dto.id()).orElse(new ClienteReport());
        BeanUtils.copyProperties(dto, relatorio);
        clienteRep.save(relatorio);
    }

    public void sincronizarOrcamento(OrcamentoSyncDTO dto) {
        OrcamentoReport relatorio = orcamentoRep.findById(dto.id()).orElse(new OrcamentoReport());
        BeanUtils.copyProperties(dto, relatorio);
        orcamentoRep.save(relatorio);
    }

    public void sincronizarProduto(ProdutoSyncDTO dto) {
        ProdutoReport relatorio = produtoRep.findById(dto.id()).orElse(new ProdutoReport());
        BeanUtils.copyProperties(dto, relatorio);
        produtoRep.save(relatorio);
    }

    public Page<VendaReport> relatorioVendasFiltrado(LocalDate dataInicio, LocalDate dataFim, String statusPagamento, Pageable pageable) {
        Specification<VendaReport> spec = (root, query, cb) -> cb.conjunction();

        if (dataInicio != null && dataFim != null) {
            spec = spec.and((root, query, cb) -> cb.between(root.get("dataCriacao"), dataInicio, dataFim));
        }
        if (statusPagamento != null && !statusPagamento.isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("statusPagamento"), statusPagamento));
        }

        return vendaRep.findAll(spec, pageable);
    }

    public Page<OrcamentoReport> relatorioOrcamentoFiltrado(String status, LocalDate dataCriacao, LocalDate dataValidade, Pageable pageable) {
        Specification<OrcamentoReport> spec = ((root, query, cb) -> cb.conjunction());

        if (dataCriacao != null && dataValidade != null) {
            spec = spec.and((root, query, criteriaBuilder) -> criteriaBuilder.between(root.get("dataCriacao"), dataCriacao, dataValidade));
        }

        if (status != null && !status.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("statusOrcamento"), status));
        }
        return orcamentoRep.findAll(spec, pageable);
    }

    public Page<ClienteReport> relatorioClientesFiltrado(String statusCliente, String cidade, Pageable pageable) {
        Specification<ClienteReport> spec = ((root, query, cb) -> cb.conjunction());

        if (statusCliente != null && !statusCliente.isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("statusCliente"), statusCliente));
        }
        if (cidade != null && !cidade.isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("cidade"), cidade));
        }

        return clienteRep.findAll(spec, pageable);
    }

    private PdfPCell criarCelulaCentralizada(String texto, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(texto != null ? texto : "", font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER); // Centraliza horizontalmente
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);   // Centraliza verticalmente
        cell.setPadding(6f); // Espaçamento interno para respirar
        return cell;
    }

    private CellStyle criarEstiloBaseExcel(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    public AnaliseResponseDTO<VendaDetalhadaDTO> gerarAnaliseVendas(
            LocalDate dataInicio, LocalDate dataFim, String statusPagamento, String cidade) {

        Map<Integer, ClienteReport> clientesMap = clienteRep.findAll().stream()
                .collect(Collectors.toMap(ClienteReport::getId, c -> c));

        List<VendaDetalhadaDTO> lista = vendaRep.findAll().stream()
                .filter(v -> dataInicio == null || (v.getDataCriacao() != null && !v.getDataCriacao().isBefore(dataInicio)))
                .filter(v -> dataFim == null || (v.getDataCriacao() != null && !v.getDataCriacao().isAfter(dataFim)))
                .filter(v -> statusPagamento == null || statusPagamento.isBlank() ||
                        statusPagamento.equalsIgnoreCase(v.getStatusPagamento()))
                .map(v -> {
                    ClienteReport cli = clientesMap.get(v.getClienteId());
                    String nomeCli = cli != null ? cli.getNome() : "Cliente #" + v.getClienteId();
                    String cidCli = cli != null ? cli.getCidade() : "-";
                    return new VendaDetalhadaDTO(
                            v.getId(), v.getOrcamentoId(), v.getClienteId(),
                            nomeCli, cidCli, v.getDataCriacao(),
                            v.getStatusPagamento(), v.getValorFinal(), v.getNotaAvaliacao()
                    );
                })
                .filter(dto -> cidade == null || cidade.isBlank() || cidade.equalsIgnoreCase(dto.clienteCidade()))
                .sorted(Comparator.comparing(VendaDetalhadaDTO::dataCriacao, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        BigDecimal faturamentoTotal = lista.stream()
                .filter(v -> isVendaPaga(v.statusPagamento()))
                .map(v -> v.valorFinal() != null ? v.valorFinal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long qtdPagas = lista.stream().filter(v -> isVendaPaga(v.statusPagamento())).count();
        BigDecimal ticketMedio = qtdPagas > 0
                ? faturamentoTotal.divide(BigDecimal.valueOf(qtdPagas), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("faturamentoTotal", faturamentoTotal);
        kpis.put("ticketMedio", ticketMedio);
        kpis.put("quantidadeVendas", lista.size());
        kpis.put("vendasPagas", qtdPagas);

        return new AnaliseResponseDTO<>(kpis, lista);
    }

    public AnaliseResponseDTO<ConversaoOrcamentoDTO> gerarAnaliseConversaoOrcamentos(
            LocalDate dataInicio, LocalDate dataFim, String statusOrcamento) {

        Map<Integer, VendaReport> vendasPorOrcamento = vendaRep.findAll().stream()
                .filter(v -> v.getOrcamentoId() != null)
                .collect(Collectors.toMap(VendaReport::getOrcamentoId, v -> v, (v1, v2) -> v1));

        List<ConversaoOrcamentoDTO> lista = orcamentoRep.findAll().stream()
                .filter(o -> dataInicio == null || (o.getDataCriacao() != null && !o.getDataCriacao().isBefore(dataInicio)))
                .filter(o -> dataFim == null || (o.getDataCriacao() != null && !o.getDataCriacao().isAfter(dataFim)))
                .filter(o -> statusOrcamento == null || statusOrcamento.isBlank() ||
                        statusOrcamento.equalsIgnoreCase(o.getStatusOrcamento()))
                .map(o -> {
                    VendaReport venda = vendasPorOrcamento.get(o.getId());
                    return new ConversaoOrcamentoDTO(
                            o.getId(), o.getClienteNome(), o.getDataCriacao(), o.getDataValidade(),
                            o.getStatusOrcamento(), o.getValorTotal(), o.getDesconto(),
                            venda != null,
                            venda != null ? venda.getId() : null,
                            venda != null ? venda.getStatusPagamento() : "SEM VENDA"
                    );
                })
                .sorted(Comparator.comparing(ConversaoOrcamentoDTO::dataCriacao, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        BigDecimal totalOrcado = lista.stream()
                .map(o -> o.valorTotal() != null ? o.valorTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal valorPerdido = lista.stream()
                .filter(o -> "EXPIRADO".equalsIgnoreCase(o.statusOrcamento()) || "REPROVADO".equalsIgnoreCase(o.statusOrcamento()))
                .map(o -> o.valorTotal() != null ? o.valorTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long convertidos = lista.stream().filter(ConversaoOrcamentoDTO::convertidoEmVenda).count();
        double taxaConversao = lista.isEmpty() ? 0.0 : (convertidos * 100.0) / lista.size();

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("totalOrcado", totalOrcado);
        kpis.put("taxaConversao", Math.round(taxaConversao * 10.0) / 10.0);
        kpis.put("valorPerdido", valorPerdido);
        kpis.put("totalOrcamentos", lista.size());

        return new AnaliseResponseDTO<>(kpis, lista);
    }

    public AnaliseResponseDTO<DesempenhoClienteDTO> gerarAnaliseClientes(
            String statusCliente, String cidade, Boolean apenasInativos) {

        List<VendaReport> todasVendas = vendaRep.findAll();
        List<OrcamentoReport> todosOrcamentos = orcamentoRep.findAll();
        LocalDate limiteInatividade = LocalDate.now().minusDays(60);

        List<DesempenhoClienteDTO> lista = clienteRep.findAll().stream()
                .filter(c -> statusCliente == null || statusCliente.isBlank() || statusCliente.equalsIgnoreCase(c.getStatusCliente()))
                .filter(c -> cidade == null || cidade.isBlank() || cidade.equalsIgnoreCase(c.getCidade()))
                .map(c -> {
                    long qtdOrcamentos = todosOrcamentos.stream()
                            .filter(o -> Objects.equals(o.getClienteId(), c.getId())).count();

                    List<VendaReport> vendasCli = todasVendas.stream()
                            .filter(v -> Objects.equals(v.getClienteId(), c.getId())).toList();

                    List<VendaReport> vendasPagas = vendasCli.stream()
                            .filter(v -> isVendaPaga(v.getStatusPagamento())).toList();

                    BigDecimal totalComprado = vendasPagas.stream()
                            .map(v -> v.getValorFinal() != null ? v.getValorFinal() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal ticket = vendasPagas.isEmpty() ? BigDecimal.ZERO
                            : totalComprado.divide(BigDecimal.valueOf(vendasPagas.size()), 2, RoundingMode.HALF_UP);

                    Double mediaNota = vendasCli.stream()
                            .filter(v -> v.getNotaAvaliacao() != null)
                            .mapToDouble(VendaReport::getNotaAvaliacao)
                            .average()
                            .orElse(0.0);

                    LocalDate ultimaCompra = vendasPagas.stream()
                            .map(VendaReport::getDataCriacao)
                            .filter(Objects::nonNull)
                            .max(LocalDate::compareTo)
                            .orElse(null);

                    String local = (c.getCidade() != null ? c.getCidade() : "-") + "/" + (c.getEstado() != null ? c.getEstado() : "-");

                    return new DesempenhoClienteDTO(
                            c.getId(), c.getNome(), c.getTipo(), local, c.getStatusCliente(),
                            qtdOrcamentos, (long) vendasPagas.size(), totalComprado, ticket,
                            Math.round(mediaNota * 10.0) / 10.0, ultimaCompra
                    );
                })
                .filter(dto -> apenasInativos == null || !apenasInativos ||
                        (dto.ultimaCompra() == null || dto.ultimaCompra().isBefore(limiteInatividade)))
                .sorted(Comparator.comparing(DesempenhoClienteDTO::valorTotalComprado).reversed())
                .toList();

        long inativos = lista.stream()
                .filter(c -> c.ultimaCompra() == null || c.ultimaCompra().isBefore(limiteInatividade))
                .count();

        BigDecimal receitaCarteira = lista.stream()
                .map(DesempenhoClienteDTO::valorTotalComprado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("totalClientesAnalisados", lista.size());
        kpis.put("clientesSemCompra60Dias", inativos);
        kpis.put("receitaAcumuladaCarteira", receitaCarteira);

        return new AnaliseResponseDTO<>(kpis, lista);
    }

    public AnaliseResponseDTO<AvaliacaoAtendimentoDTO> gerarAnaliseAvaliacoes(
            LocalDate dataInicio, LocalDate dataFim, String classificacao) {

        Map<Integer, ClienteReport> clientesMap = clienteRep.findAll().stream()
                .collect(Collectors.toMap(ClienteReport::getId, c -> c));

        List<AvaliacaoAtendimentoDTO> lista = vendaRep.findAll().stream()
                .filter(v -> v.getNotaAvaliacao() != null)
                .filter(v -> dataInicio == null || (v.getDataCriacao() != null && !v.getDataCriacao().isBefore(dataInicio)))
                .filter(v -> dataFim == null || (v.getDataCriacao() != null && !v.getDataCriacao().isAfter(dataFim)))
                .map(v -> {
                    ClienteReport cli = clientesMap.get(v.getClienteId());
                    String nome = cli != null ? cli.getNome() : "Cliente #" + v.getClienteId();
                    String tipo = cli != null ? cli.getTipo() : "-";
                    String classe = v.getNotaAvaliacao() >= 4.0f ? "PROMOTOR"
                            : (v.getNotaAvaliacao() >= 3.0f ? "NEUTRO" : "DETRATOR");

                    return new AvaliacaoAtendimentoDTO(
                            v.getId(), nome, tipo, v.getDataCriacao(),
                            v.getValorFinal(), v.getNotaAvaliacao(), classe
                    );
                })
                .filter(dto -> classificacao == null || classificacao.isBlank() ||
                        classificacao.equalsIgnoreCase(dto.classificacao()))
                .sorted(Comparator.comparing(AvaliacaoAtendimentoDTO::dataVenda, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        double mediaGeral = lista.stream().mapToDouble(AvaliacaoAtendimentoDTO::notaAvaliacao).average().orElse(0.0);
        long promotores = lista.stream().filter(a -> "PROMOTOR".equals(a.classificacao())).count();
        long detratores = lista.stream().filter(a -> "DETRATOR".equals(a.classificacao())).count();

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("mediaGeral", Math.round(mediaGeral * 100.0) / 100.0);
        kpis.put("totalAvaliacoes", lista.size());
        kpis.put("promotores", promotores);
        kpis.put("detratores", detratores);

        return new AnaliseResponseDTO<>(kpis, lista);
    }

    private boolean isVendaPaga(String status) {
        if (status == null) return false;
        String s = status.trim();
        return "APROVADO".equalsIgnoreCase(s) || "PAGO".equalsIgnoreCase(s) || "CONCLUIDO".equalsIgnoreCase(s);
    }

    public byte[] exportarVendasPdf(List<VendaReport> vendas) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, out);
            document.open();

            PdfPTable headerTable = new PdfPTable(3);
            headerTable.setWidthPercentage(100);
            headerTable.setWidths(new float[]{1f, 2f, 1f});

            try {
                Image logo = Image.getInstance(getClass().getResource("/logo.png"));
                logo.scaleToFit(80, 80);
                PdfPCell logoCell = new PdfPCell(logo);
                logoCell.setBorder(Rectangle.NO_BORDER);
                headerTable.addCell(logoCell);
            } catch (Exception e) {
                PdfPCell emptyCell = new PdfPCell(new Phrase("LOGO AQUI"));
                emptyCell.setBorder(Rectangle.NO_BORDER);
                headerTable.addCell(emptyCell);
            }

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            PdfPCell titleCell = new PdfPCell(new Phrase("Relatório Gerencial de Vendas", titleFont));
            titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            titleCell.setBorder(Rectangle.NO_BORDER);
            headerTable.addCell(titleCell);

            Font dateFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
            String dataGeracao = "Gerado em: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            PdfPCell dateCell = new PdfPCell(new Phrase(dataGeracao, dateFont));
            dateCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            dateCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            dateCell.setBorder(Rectangle.NO_BORDER);
            headerTable.addCell(dateCell);

            document.add(headerTable);
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1f, 1.5f, 2f, 2f, 1.5f, 1.5f});

            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.WHITE);
            String[] headers = {"ID Venda", "ID Cliente", "Status Pagamento", "Valor Final", "Data", "Nota"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                cell.setBackgroundColor(new Color(20, 33, 61));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setPadding(8f);
                table.addCell(cell);
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            BigDecimal totalGeral = BigDecimal.ZERO;
            Font rowFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

            for (VendaReport venda : vendas) {
                table.addCell(criarCelulaCentralizada(String.valueOf(venda.getId()), rowFont));
                table.addCell(criarCelulaCentralizada(String.valueOf(venda.getClienteId()), rowFont));
                table.addCell(criarCelulaCentralizada(venda.getStatusPagamento(), rowFont));

                String valorStr = venda.getValorFinal() != null ? String.format("R$ %.2f", venda.getValorFinal()) : "R$ 0,00";
                table.addCell(criarCelulaCentralizada(valorStr, rowFont));

                String dataStr = venda.getDataCriacao() != null ? venda.getDataCriacao().format(formatter) : "";
                table.addCell(criarCelulaCentralizada(dataStr, rowFont));

                String notaStr = venda.getNotaAvaliacao() != null ? String.valueOf(venda.getNotaAvaliacao()) : "-";
                table.addCell(criarCelulaCentralizada(notaStr, rowFont));

                String statusStr = venda.getStatusPagamento() != null ? venda.getStatusPagamento().trim() : "";
                if (venda.getValorFinal() != null &&
                        ("APROVADO".equalsIgnoreCase(statusStr) || "PAGO".equalsIgnoreCase(statusStr) || "CONCLUIDO".equalsIgnoreCase(statusStr))) {
                    totalGeral = totalGeral.add(venda.getValorFinal());
                }
            }

            String textoTotal = String.format("TOTAL (Aprovadas): R$ %.2f", totalGeral);
            PdfPCell totalCell = new PdfPCell(new Phrase(textoTotal, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
            totalCell.setColspan(6);
            totalCell.setHorizontalAlignment(Element.ALIGN_CENTER); // Centralizado conforme solicitado
            totalCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            totalCell.setBackgroundColor(new Color(236, 240, 241));
            totalCell.setPadding(8f);
            table.addCell(totalCell);

            document.add(table);
            document.close();

            return out.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new RuntimeException("Erro ao gerar PDF", e);
        }
    }

    public byte[] exportarVendasExcel(List<VendaReport> vendas) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Relatório de Vendas");
            sheet.createFreezePane(0, 1);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle centerStyle = criarEstiloBaseExcel(workbook);

            CellStyle currencyStyle = criarEstiloBaseExcel(workbook);
            DataFormat format = workbook.createDataFormat();
            currencyStyle.setDataFormat(format.getFormat("R$ #,##0.00"));

            CellStyle dateStyle = criarEstiloBaseExcel(workbook);
            dateStyle.setDataFormat(format.getFormat("dd/mm/yyyy"));

            Row headerRow = sheet.createRow(0);
            String[] colunas = {"ID Venda", "ID Orçamento", "ID Cliente", "Status Pagamento", "Valor Final", "Data Criação", "Nota"};
            for (int i = 0; i < colunas.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(colunas[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (VendaReport venda : vendas) {
                Row row = sheet.createRow(rowIdx++);

                Cell c0 = row.createCell(0);
                c0.setCellValue(venda.getId());
                c0.setCellStyle(centerStyle);

                Cell c1 = row.createCell(1);
                c1.setCellValue(venda.getOrcamentoId());
                c1.setCellStyle(centerStyle);

                Cell c2 = row.createCell(2);
                c2.setCellValue(venda.getClienteId());
                c2.setCellStyle(centerStyle);

                Cell c3 = row.createCell(3);
                c3.setCellValue(venda.getStatusPagamento());
                c3.setCellStyle(centerStyle);

                Cell valorCell = row.createCell(4);
                valorCell.setCellValue(venda.getValorFinal() != null ? venda.getValorFinal().doubleValue() : 0.0);
                valorCell.setCellStyle(currencyStyle);

                Cell dataCell = row.createCell(5);
                if (venda.getDataCriacao() != null) {
                    dataCell.setCellValue(venda.getDataCriacao());
                    dataCell.setCellStyle(dateStyle);
                } else {
                    dataCell.setCellValue("");
                    dataCell.setCellStyle(centerStyle);
                }

                Cell c6 = row.createCell(6);
                c6.setCellValue(venda.getNotaAvaliacao() != null ? venda.getNotaAvaliacao() : 0.0);
                c6.setCellStyle(centerStyle);
            }

            for (int i = 0; i < colunas.length; i++) sheet.autoSizeColumn(i);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Erro ao gerar arquivo Excel", e);
        }
    }


    public byte[] exportarClientesPdf(List<ClienteReport> clientes) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, out);
            document.open();

            PdfPTable headerTable = new PdfPTable(3);
            headerTable.setWidthPercentage(100);
            headerTable.setWidths(new float[]{1f, 2f, 1f});

            try {
                Image logo = Image.getInstance(getClass().getResource("/logo.png"));
                logo.scaleToFit(80, 80);
                PdfPCell logoCell = new PdfPCell(logo);
                logoCell.setBorder(Rectangle.NO_BORDER);
                headerTable.addCell(logoCell);
            } catch (Exception e) {
                PdfPCell emptyCell = new PdfPCell(new Phrase("LOGO AQUI"));
                emptyCell.setBorder(Rectangle.NO_BORDER);
                headerTable.addCell(emptyCell);
            }

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            PdfPCell titleCell = new PdfPCell(new Phrase("Relatório Gerencial de Clientes", titleFont));
            titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            titleCell.setBorder(Rectangle.NO_BORDER);
            headerTable.addCell(titleCell);

            Font dateFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
            String dataGeracao = "Gerado em: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            PdfPCell dateCell = new PdfPCell(new Phrase(dataGeracao, dateFont));
            dateCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            dateCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            dateCell.setBorder(Rectangle.NO_BORDER);
            headerTable.addCell(dateCell);

            document.add(headerTable);
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);

            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.WHITE);
            String[] headers = {"ID", "Nome", "Tipo", "Status", "Cidade/UF", "Email"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                cell.setBackgroundColor(new Color(20, 33, 61));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setPadding(8f);
                table.addCell(cell);
            }

            Font rowFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
            for (ClienteReport c : clientes) {
                table.addCell(criarCelulaCentralizada(String.valueOf(c.getId()), rowFont));
                table.addCell(criarCelulaCentralizada(c.getNome(), rowFont));
                table.addCell(criarCelulaCentralizada(c.getTipo(), rowFont));
                table.addCell(criarCelulaCentralizada(c.getStatusCliente(), rowFont));

                String localizacao = (c.getCidade() != null ? c.getCidade() : "") + " / " + (c.getEstado() != null ? c.getEstado() : "");
                table.addCell(criarCelulaCentralizada(localizacao, rowFont));

                table.addCell(criarCelulaCentralizada(c.getEmail(), rowFont));
            }

            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar PDF de clientes", e);
        }
    }

    public byte[] exportarClientesExcel(List<ClienteReport> clientes) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Clientes");
            sheet.createFreezePane(0, 1);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle centerStyle = criarEstiloBaseExcel(workbook);

            Row headerRow = sheet.createRow(0);
            String[] colunas = {"ID", "Nome", "Tipo", "Status", "Cidade", "Estado", "Email", "Data Cadastro"};
            for (int i = 0; i < colunas.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(colunas[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (ClienteReport c : clientes) {
                Row row = sheet.createRow(rowIdx++);

                Cell c0 = row.createCell(0);
                c0.setCellValue(c.getId());
                c0.setCellStyle(centerStyle);

                Cell c1 = row.createCell(1);
                c1.setCellValue(c.getNome());
                c1.setCellStyle(centerStyle);

                Cell c2 = row.createCell(2);
                c2.setCellValue(c.getTipo());
                c2.setCellStyle(centerStyle);

                Cell c3 = row.createCell(3);
                c3.setCellValue(c.getStatusCliente());
                c3.setCellStyle(centerStyle);

                Cell c4 = row.createCell(4);
                c4.setCellValue(c.getCidade());
                c4.setCellStyle(centerStyle);

                Cell c5 = row.createCell(5);
                c5.setCellValue(c.getEstado());
                c5.setCellStyle(centerStyle);

                Cell c6 = row.createCell(6);
                c6.setCellValue(c.getEmail());
                c6.setCellStyle(centerStyle);

                Cell c7 = row.createCell(7);
                c7.setCellValue(c.getDataCadastro() != null ? c.getDataCadastro().toString() : "");
                c7.setCellStyle(centerStyle);
            }

            for (int i = 0; i < colunas.length; i++) sheet.autoSizeColumn(i);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Erro ao gerar Excel de clientes", e);
        }
    }


    public byte[] exportarOrcamentosPdf(List<OrcamentoReport> orcamentos) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, out);
            document.open();

            PdfPTable headerTable = new PdfPTable(3);
            headerTable.setWidthPercentage(100);
            headerTable.setWidths(new float[]{1f, 2f, 1f});

            try {
                Image logo = Image.getInstance(getClass().getResource("/logo.png"));
                logo.scaleToFit(80, 80);
                PdfPCell logoCell = new PdfPCell(logo);
                logoCell.setBorder(Rectangle.NO_BORDER);
                headerTable.addCell(logoCell);
            } catch (Exception e) {
                PdfPCell emptyCell = new PdfPCell(new Phrase("LOGO AQUI"));
                emptyCell.setBorder(Rectangle.NO_BORDER);
                headerTable.addCell(emptyCell);
            }

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            PdfPCell titleCell = new PdfPCell(new Phrase("Relatório Gerencial de Orçamentos", titleFont));
            titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            titleCell.setBorder(Rectangle.NO_BORDER);
            headerTable.addCell(titleCell);

            Font dateFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
            String dataGeracao = "Gerado em: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            PdfPCell dateCell = new PdfPCell(new Phrase(dataGeracao, dateFont));
            dateCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            dateCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            dateCell.setBorder(Rectangle.NO_BORDER);
            headerTable.addCell(dateCell);

            document.add(headerTable);
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1f, 2f, 1.5f, 2f, 1.5f, 2f});

            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.WHITE);
            String[] headers = {"ID", "Cliente", "Status", "Valor Total", "Desconto", "Data Criação"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                cell.setBackgroundColor(new Color(20, 33, 61));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setPadding(8f);
                table.addCell(cell);
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            BigDecimal totalAprovado = BigDecimal.ZERO;
            Font rowFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

            for (OrcamentoReport o : orcamentos) {
                table.addCell(criarCelulaCentralizada(String.valueOf(o.getId()), rowFont));
                table.addCell(criarCelulaCentralizada(o.getClienteNome(), rowFont));
                table.addCell(criarCelulaCentralizada(o.getStatusOrcamento(), rowFont));

                String valorStr = o.getValorTotal() != null ? String.format("R$ %.2f", o.getValorTotal()) : "R$ 0,00";
                table.addCell(criarCelulaCentralizada(valorStr, rowFont));

                String descStr = o.getDesconto() != null ? String.format("R$ %.2f", o.getDesconto()) : "R$ 0,00";
                table.addCell(criarCelulaCentralizada(descStr, rowFont));

                String dataStr = o.getDataCriacao() != null ? o.getDataCriacao().format(formatter) : "";
                table.addCell(criarCelulaCentralizada(dataStr, rowFont));

                String statusStr = o.getStatusOrcamento() != null ? o.getStatusOrcamento().trim() : "";
                if (o.getValorTotal() != null && "APROVADO".equalsIgnoreCase(statusStr)) {
                    totalAprovado = totalAprovado.add(o.getValorTotal());
                }
            }

            String textoTotal = String.format("TOTAL (Aprovados): R$ %.2f", totalAprovado);
            PdfPCell totalCell = new PdfPCell(new Phrase(textoTotal, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
            totalCell.setColspan(6);
            totalCell.setHorizontalAlignment(Element.ALIGN_CENTER); // Centralizado conforme solicitado
            totalCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            totalCell.setBackgroundColor(new Color(236, 240, 241));
            totalCell.setPadding(8f);
            table.addCell(totalCell);

            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar PDF de orçamentos", e);
        }
    }

    public byte[] exportarOrcamentosExcel(List<OrcamentoReport> orcamentos) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Orçamentos");
            sheet.createFreezePane(0, 1);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle centerStyle = criarEstiloBaseExcel(workbook);

            CellStyle currencyStyle = criarEstiloBaseExcel(workbook);
            DataFormat format = workbook.createDataFormat();
            currencyStyle.setDataFormat(format.getFormat("R$ #,##0.00"));

            CellStyle dateStyle = criarEstiloBaseExcel(workbook);
            dateStyle.setDataFormat(format.getFormat("dd/mm/yyyy"));

            Row headerRow = sheet.createRow(0);
            String[] colunas = {"ID", "ID Cliente", "Nome Cliente", "Status", "Valor Total", "Desconto", "Data Criação", "Data Validade"};
            for (int i = 0; i < colunas.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(colunas[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (OrcamentoReport o : orcamentos) {
                Row row = sheet.createRow(rowIdx++);

                Cell c0 = row.createCell(0);
                c0.setCellValue(o.getId());
                c0.setCellStyle(centerStyle);

                Cell c1 = row.createCell(1);
                c1.setCellValue(o.getClienteId());
                c1.setCellStyle(centerStyle);

                Cell c2 = row.createCell(2);
                c2.setCellValue(o.getClienteNome());
                c2.setCellStyle(centerStyle);

                Cell c3 = row.createCell(3);
                c3.setCellValue(o.getStatusOrcamento());
                c3.setCellStyle(centerStyle);

                Cell valorCell = row.createCell(4);
                valorCell.setCellValue(o.getValorTotal() != null ? o.getValorTotal().doubleValue() : 0.0);
                valorCell.setCellStyle(currencyStyle);

                Cell descontoCell = row.createCell(5);
                descontoCell.setCellValue(o.getDesconto() != null ? o.getDesconto().doubleValue() : 0.0);
                descontoCell.setCellStyle(currencyStyle);

                Cell dataCriacaoCell = row.createCell(6);
                if (o.getDataCriacao() != null) {
                    dataCriacaoCell.setCellValue(o.getDataCriacao());
                    dataCriacaoCell.setCellStyle(dateStyle);
                } else {
                    dataCriacaoCell.setCellValue("");
                    dataCriacaoCell.setCellStyle(centerStyle);
                }

                Cell dataValidadeCell = row.createCell(7);
                if (o.getDataValidade() != null) {
                    dataValidadeCell.setCellValue(o.getDataValidade());
                    dataValidadeCell.setCellStyle(dateStyle);
                } else {
                    dataValidadeCell.setCellValue("");
                    dataValidadeCell.setCellStyle(centerStyle);
                }
            }

            for (int i = 0; i < colunas.length; i++) sheet.autoSizeColumn(i);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Erro ao gerar Excel de orçamentos", e);
        }
    }

    public byte[] exportarAnaliseVendasPdf(AnaliseResponseDTO<VendaDetalhadaDTO> analise) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String[] headers = {"ID Venda", "Cliente", "Cidade", "Data", "Status", "Valor Final", "Nota"};
        List<String[]> linhas = analise.dados().stream().map(v -> new String[]{
                String.valueOf(v.vendaId()),
                v.clienteNome(),
                v.clienteCidade(),
                v.dataCriacao() != null ? v.dataCriacao().format(fmt) : "-",
                v.statusPagamento(),
                v.valorFinal() != null ? String.format("R$ %.2f", v.valorFinal()) : "R$ 0,00",
                v.notaAvaliacao() != null ? String.valueOf(v.notaAvaliacao()) : "-"
        }).toList();

        String rodape = String.format("Faturamento Total: R$ %.2f  |  Ticket Médio: R$ %.2f  |  Vendas Pagas: %s",
                analise.kpis().get("faturamentoTotal"),
                analise.kpis().get("ticketMedio"),
                analise.kpis().get("vendasPagas"));

        return gerarPdfGenerico("Análise de Desempenho de Vendas", headers, linhas, rodape);
    }

    public byte[] exportarAnaliseVendasExcel(AnaliseResponseDTO<VendaDetalhadaDTO> analise) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String[] headers = {"ID Venda", "ID Orçamento", "Cliente", "Cidade", "Data", "Status", "Valor Final", "Nota"};
        List<Object[]> linhas = analise.dados().stream().map(v -> new Object[]{
                v.vendaId(),
                v.orcamentoId(),
                v.clienteNome(),
                v.clienteCidade(),
                v.dataCriacao() != null ? v.dataCriacao().format(fmt) : "",
                v.statusPagamento(),
                v.valorFinal() != null ? v.valorFinal().doubleValue() : 0.0,
                v.notaAvaliacao() != null ? v.notaAvaliacao() : 0.0
        }).toList();

        return gerarExcelGenerico("Analise Vendas", headers, linhas);
    }

    public byte[] exportarAnaliseConversaoPdf(AnaliseResponseDTO<ConversaoOrcamentoDTO> analise) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String[] headers = {"ID Orç.", "Cliente", "Emissão", "Validade", "Status Orç.", "Valor Total", "Venda Vinculada"};
        List<String[]> linhas = analise.dados().stream().map(o -> new String[]{
                String.valueOf(o.orcamentoId()),
                o.clienteNome(),
                o.dataCriacao() != null ? o.dataCriacao().format(fmt) : "-",
                o.dataValidade() != null ? o.dataValidade().format(fmt) : "-",
                o.statusOrcamento(),
                o.valorTotal() != null ? String.format("R$ %.2f", o.valorTotal()) : "R$ 0,00",
                o.convertidoEmVenda() ? "Venda #" + o.vendaId() + " (" + o.statusPagamentoVenda() + ")" : "Não Convertido"
        }).toList();

        String rodape = String.format("Total Orçado: R$ %.2f  |  Taxa de Conversão: %s%%  |  Valor Perdido: R$ %.2f",
                analise.kpis().get("totalOrcado"),
                analise.kpis().get("taxaConversao"),
                analise.kpis().get("valorPerdido"));

        return gerarPdfGenerico("Funil e Conversão de Orçamentos", headers, linhas, rodape);
    }

    public byte[] exportarAnaliseConversaoExcel(AnaliseResponseDTO<ConversaoOrcamentoDTO> analise) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String[] headers = {"ID Orçamento", "Cliente", "Emissão", "Validade", "Status Orçamento", "Valor Total", "Desconto", "Convertido", "ID Venda", "Status Venda"};
        List<Object[]> linhas = analise.dados().stream().map(o -> new Object[]{
                o.orcamentoId(),
                o.clienteNome(),
                o.dataCriacao() != null ? o.dataCriacao().format(fmt) : "",
                o.dataValidade() != null ? o.dataValidade().format(fmt) : "",
                o.statusOrcamento(),
                o.valorTotal() != null ? o.valorTotal().doubleValue() : 0.0,
                o.desconto() != null ? o.desconto().doubleValue() : 0.0,
                o.convertidoEmVenda() ? "SIM" : "NÃO",
                o.vendaId() != null ? o.vendaId() : "",
                o.statusPagamentoVenda()
        }).toList();

        return gerarExcelGenerico("Conversao Orcamentos", headers, linhas);
    }

    public byte[] exportarAnaliseClientesPdf(AnaliseResponseDTO<DesempenhoClienteDTO> analise) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String[] headers = {"Cliente", "Cidade/UF", "Status", "Orç./Vendas", "Total Comprado", "Ticket Médio", "Última Compra"};
        List<String[]> linhas = analise.dados().stream().map(c -> new String[]{
                c.nome(),
                c.cidadeUf(),
                c.statusCliente(),
                c.totalOrcamentos() + " / " + c.totalVendasPagas(),
                String.format("R$ %.2f", c.valorTotalComprado()),
                String.format("R$ %.2f", c.ticketMedio()),
                c.ultimaCompra() != null ? c.ultimaCompra().format(fmt) : "Sem compra"
        }).toList();

        String rodape = String.format("Clientes Analisados: %s  |  Inativos (+60d): %s  |  Receita Acumulada: R$ %.2f",
                analise.kpis().get("totalClientesAnalisados"),
                analise.kpis().get("clientesSemCompra60Dias"),
                analise.kpis().get("receitaAcumuladaCarteira"));

        return gerarPdfGenerico("Retenção e Desempenho de Clientes", headers, linhas, rodape);
    }

    public byte[] exportarAnaliseClientesExcel(AnaliseResponseDTO<DesempenhoClienteDTO> analise) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String[] headers = {"ID", "Cliente", "Tipo", "Cidade/UF", "Status", "Qtd Orçamentos", "Vendas Pagas", "Total Comprado", "Ticket Médio", "Média Nota", "Última Compra"};
        List<Object[]> linhas = analise.dados().stream().map(c -> new Object[]{
                c.clienteId(),
                c.nome(),
                c.tipo(),
                c.cidadeUf(),
                c.statusCliente(),
                c.totalOrcamentos(),
                c.totalVendasPagas(),
                c.valorTotalComprado().doubleValue(),
                c.ticketMedio().doubleValue(),
                c.mediaAvaliacao(),
                c.ultimaCompra() != null ? c.ultimaCompra().format(fmt) : "Sem compra"
        }).toList();

        return gerarExcelGenerico("Desempenho Clientes", headers, linhas);
    }

    public byte[] exportarAnaliseAvaliacoesPdf(AnaliseResponseDTO<AvaliacaoAtendimentoDTO> analise) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String[] headers = {"ID Venda", "Cliente", "Tipo", "Data Venda", "Valor Venda", "Nota", "Classificação"};
        List<String[]> linhas = analise.dados().stream().map(a -> new String[]{
                String.valueOf(a.vendaId()),
                a.clienteNome(),
                a.tipoCliente(),
                a.dataVenda() != null ? a.dataVenda().format(fmt) : "-",
                a.valorFinal() != null ? String.format("R$ %.2f", a.valorFinal()) : "R$ 0,00",
                String.valueOf(a.notaAvaliacao()),
                a.classificacao()
        }).toList();

        String rodape = String.format("Média Geral: %s  |  Total Avaliações: %s  |  Promotores: %s  |  Detratores: %s",
                analise.kpis().get("mediaGeral"),
                analise.kpis().get("totalAvaliacoes"),
                analise.kpis().get("promotores"),
                analise.kpis().get("detratores"));

        return gerarPdfGenerico("Relatório de Satisfação e Avaliações", headers, linhas, rodape);
    }

    public byte[] exportarAnaliseAvaliacoesExcel(AnaliseResponseDTO<AvaliacaoAtendimentoDTO> analise) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String[] headers = {"ID Venda", "Cliente", "Tipo", "Data Venda", "Valor Final", "Nota", "Classificação"};
        List<Object[]> linhas = analise.dados().stream().map(a -> new Object[]{
                a.vendaId(),
                a.clienteNome(),
                a.tipoCliente(),
                a.dataVenda() != null ? a.dataVenda().format(fmt) : "",
                a.valorFinal() != null ? a.valorFinal().doubleValue() : 0.0,
                a.notaAvaliacao(),
                a.classificacao()
        }).toList();

        return gerarExcelGenerico("Avaliacoes", headers, linhas);
    }

    // --- Motores Internos Reutilizáveis ---

    private byte[] gerarPdfGenerico(String titulo, String[] headers, List<String[]> linhas, String rodapeResumo) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, out);
            document.open();

            PdfPTable headerTable = new PdfPTable(3);
            headerTable.setWidthPercentage(100);
            headerTable.setWidths(new float[]{1f, 2.5f, 1f});

            try {
                Image logo = Image.getInstance(getClass().getResource("/logo.png"));
                logo.scaleToFit(80, 80);
                PdfPCell logoCell = new PdfPCell(logo);
                logoCell.setBorder(Rectangle.NO_BORDER);
                headerTable.addCell(logoCell);
            } catch (Exception e) {
                PdfPCell emptyCell = new PdfPCell(new Phrase("ACRILLSERVICE"));
                emptyCell.setBorder(Rectangle.NO_BORDER);
                headerTable.addCell(emptyCell);
            }

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(20, 33, 61));
            PdfPCell titleCell = new PdfPCell(new Phrase(titulo, titleFont));
            titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            titleCell.setBorder(Rectangle.NO_BORDER);
            headerTable.addCell(titleCell);

            Font dateFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
            String dataGeracao = "Gerado em:\n" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            PdfPCell dateCell = new PdfPCell(new Phrase(dataGeracao, dateFont));
            dateCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            dateCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            dateCell.setBorder(Rectangle.NO_BORDER);
            headerTable.addCell(dateCell);

            document.add(headerTable);
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(headers.length);
            table.setWidthPercentage(100);

            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                cell.setBackgroundColor(new Color(20, 33, 61));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setPadding(7f);
                table.addCell(cell);
            }

            Font rowFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
            for (String[] linha : linhas) {
                for (String valor : linha) {
                    table.addCell(criarCelulaCentralizada(valor, rowFont));
                }
            }

            if (rodapeResumo != null && !rodapeResumo.isBlank()) {
                PdfPCell totalCell = new PdfPCell(new Phrase(rodapeResumo, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
                totalCell.setColspan(headers.length);
                totalCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                totalCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                totalCell.setBackgroundColor(new Color(236, 240, 241));
                totalCell.setPadding(8f);
                table.addCell(totalCell);
            }

            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar PDF da análise: " + titulo, e);
        }
    }

    private byte[] gerarExcelGenerico(String nomeAba, String[] colunas, List<Object[]> linhas) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(nomeAba);
            sheet.createFreezePane(0, 1);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle centerStyle = criarEstiloBaseExcel(workbook);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < colunas.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(colunas[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (Object[] linha : linhas) {
                Row row = sheet.createRow(rowIdx++);
                for (int i = 0; i < linha.length; i++) {
                    Cell cell = row.createCell(i);
                    Object val = linha[i];
                    if (val instanceof Number num) {
                        cell.setCellValue(num.doubleValue());
                    } else {
                        cell.setCellValue(val != null ? val.toString() : "");
                    }
                    cell.setCellStyle(centerStyle);
                }
            }

            for (int i = 0; i < colunas.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Erro ao gerar Excel da análise: " + nomeAba, e);
        }
    }
}