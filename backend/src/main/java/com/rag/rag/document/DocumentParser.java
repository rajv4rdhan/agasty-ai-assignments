package com.rag.rag.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.rag.common.exception.InvalidFileException;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
public class DocumentParser {
    
    private static final Logger log = LoggerFactory.getLogger(DocumentParser.class);
    private static final String OCR_API_URL = "https://api.ocr.space/parse/image";
    
    private final String ocrApiKey;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    public DocumentParser(
            @Value("${ocr.api.key}") String ocrApiKey,
            RestTemplate restTemplate,
            ObjectMapper objectMapper) {
        this.ocrApiKey = ocrApiKey;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }
    
    public List<PageContent> parse(byte[] content, String fileType) {
        return switch (fileType.toLowerCase()) {
            case "pdf" -> parsePdf(content);
            case "docx" -> parseDocx(content);
            case "txt", "md", "markdown" -> parseText(content);
            default -> throw new InvalidFileException("Unsupported file type: " + fileType);
        };
    }
    
    private List<PageContent> parsePdf(byte[] content) {
        try {
            log.info("Parsing PDF using OCR.space API");
            
            // Convert PDF to base64
            String base64Content = Base64.getEncoder().encodeToString(content);
            
            // Prepare request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("apikey", ocrApiKey);
            body.add("base64Image", "data:application/pdf;base64," + base64Content);
            body.add("language", "eng");
            body.add("isOverlayRequired", "false");
            body.add("detectOrientation", "true");
            body.add("scale", "true");
            body.add("OCREngine", "2"); // Engine 2 for better accuracy
            
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
            
            // Call OCR.space API
            ResponseEntity<String> response = restTemplate.exchange(
                OCR_API_URL,
                HttpMethod.POST,
                request,
                String.class
            );
            
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new InvalidFileException("OCR API returned status: " + response.getStatusCode());
            }
            
            // Parse response
            JsonNode root = objectMapper.readTree(response.getBody());
            
            if (root.has("IsErroredOnProcessing") && root.get("IsErroredOnProcessing").asBoolean()) {
                String errorMessage = root.has("ErrorMessage") 
                    ? root.get("ErrorMessage").get(0).asText() 
                    : "Unknown OCR error";
                throw new InvalidFileException("OCR processing failed: " + errorMessage);
            }
            
            List<PageContent> pages = new ArrayList<>();
            JsonNode parsedResults = root.get("ParsedResults");
            
            if (parsedResults != null && parsedResults.isArray()) {
                int pageNum = 1;
                for (JsonNode result : parsedResults) {
                    String text = result.get("ParsedText").asText();
                    if (text != null && !text.isBlank()) {
                        pages.add(new PageContent(text.trim(), pageNum));
                        pageNum++;
                    }
                }
            }
            
            if (pages.isEmpty()) {
                throw new InvalidFileException("No text extracted from PDF");
            }
            
            log.info("Successfully parsed {} pages from PDF using OCR", pages.size());
            return pages;
            
        } catch (Exception e) {
            log.error("Failed to parse PDF with OCR.space: {}", e.getMessage());
            throw new InvalidFileException("Failed to parse PDF: " + e.getMessage());
        }
    }
    
    private List<PageContent> parseDocx(byte[] content) {
        List<PageContent> pages = new ArrayList<>();
        try (ByteArrayInputStream bis = new ByteArrayInputStream(content);
             XWPFDocument document = new XWPFDocument(bis);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            
            String text = extractor.getText();
            if (text != null && !text.isBlank()) {
                pages.add(new PageContent(text.trim(), null));
            }
        } catch (IOException e) {
            throw new InvalidFileException("Failed to parse DOCX: " + e.getMessage());
        }
        return pages;
    }
    
    private List<PageContent> parseText(byte[] content) {
        String text = new String(content).trim();
        if (text.isBlank()) {
            throw new InvalidFileException("File is empty");
        }
        return List.of(new PageContent(text, null));
    }
    
    public record PageContent(String text, Integer pageNumber) {}
}
