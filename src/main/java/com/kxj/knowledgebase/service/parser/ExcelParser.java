package com.kxj.knowledgebase.service.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel 文档解析器（支持 .xlsx 和 .xls）
 */
@Slf4j
@Component
public class ExcelParser implements DocumentParser {

    @Override
    public ParseResult parse(InputStream inputStream, String fileName) {
        log.info("[开始解析 Excel 文档: {}]", fileName);

        String extension = getFileExtension(fileName);

        try {
            Workbook workbook;
            if ("xlsx".equalsIgnoreCase(extension)) {
                workbook = new XSSFWorkbook(inputStream);
            } else if ("xls".equalsIgnoreCase(extension)) {
                workbook = new HSSFWorkbook(inputStream);
            } else {
                return ParseResult.error("不支持的 Excel 格式: " + extension);
            }

            ParseResult result = parseWorkbook(workbook, fileName);
            workbook.close();

            log.info("[Excel 解析完成: {}, 共 {} 个 sheet, {} 字符]",
                    fileName, result.getTotalPages(),
                    result.getText() != null ? result.getText().length() : 0);
            return result;

        } catch (Exception e) {
            log.error("[Excel 解析失败: {}]", fileName, e);
            return ParseResult.error("Excel 解析失败: " + e.getMessage());
        }
    }

    private ParseResult parseWorkbook(Workbook workbook, String fileName) {
        StringBuilder fullText = new StringBuilder();
        List<ParseResult.PageContent> pages = new ArrayList<>();
        Map<String, String> metadata = new HashMap<>();

        int sheetCount = workbook.getNumberOfSheets();
        metadata.put("sheetCount", String.valueOf(sheetCount));

        for (int i = 0; i < sheetCount; i++) {
            Sheet sheet = workbook.getSheetAt(i);
            String sheetName = sheet.getSheetName();

            StringBuilder sheetText = new StringBuilder();
            sheetText.append("Sheet: ").append(sheetName).append("\n");

            // 遍历所有行
            for (Row row : sheet) {
                StringBuilder rowText = new StringBuilder();

                for (Cell cell : row) {
                    String cellValue = getCellValue(cell);
                    if (!cellValue.isEmpty()) {
                        rowText.append(cellValue).append("\t");
                    }
                }

                String rowStr = rowText.toString().trim();
                if (!rowStr.isEmpty()) {
                    sheetText.append(rowStr).append("\n");
                }
            }

            String sheetContent = sheetText.toString().trim();
            if (!sheetContent.isEmpty()) {
                pages.add(ParseResult.PageContent.builder()
                        .pageNumber(i + 1)
                        .title(sheetName)
                        .text(sheetContent)
                        .charCount(sheetContent.length())
                        .build());

                fullText.append(sheetContent).append("\n\n");
            }
        }

        return ParseResult.builder()
                .success(true)
                .text(fullText.toString().trim())
                .pages(pages)
                .metadata(metadata)
                .totalPages(sheetCount)
                .build();
    }

    private String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                }
                yield String.valueOf(cell.getNumericCellValue());
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1);
        }
        return "";
    }

    @Override
    public String getSupportedExtension() {
        return "xlsx";
    }

    @Override
    public boolean supports(String extension) {
        return "xlsx".equalsIgnoreCase(extension) || "xls".equalsIgnoreCase(extension);
    }
}
