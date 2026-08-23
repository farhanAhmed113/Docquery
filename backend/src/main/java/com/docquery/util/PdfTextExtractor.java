package com.docquery.util;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PdfTextExtractor {

    public static class ExtractedText {
        public final String text;
        public final int pageCount;

        public ExtractedText(String text, int pageCount) {
            this.text = text;
            this.pageCount = pageCount;
        }
    }

    public ExtractedText extract(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            return new ExtractedText(text, document.getNumberOfPages());
        }
    }
}
