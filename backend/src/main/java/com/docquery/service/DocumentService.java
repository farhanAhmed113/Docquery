package com.docquery.service;

import com.docquery.model.Chunk;
import com.docquery.model.Document;
import com.docquery.model.User;
import com.docquery.repository.ChunkRepository;
import com.docquery.repository.DocumentRepository;
import com.docquery.util.PdfTextExtractor;
import com.docquery.util.TextChunker;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final PdfTextExtractor pdfTextExtractor;
    private final TextChunker textChunker;
    private final EmbeddingService embeddingService;

    public DocumentService(DocumentRepository documentRepository,
                            ChunkRepository chunkRepository,
                            PdfTextExtractor pdfTextExtractor,
                            TextChunker textChunker,
                            EmbeddingService embeddingService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.pdfTextExtractor = pdfTextExtractor;
        this.textChunker = textChunker;
        this.embeddingService = embeddingService;
    }

    public Document uploadAndProcess(User owner, MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Only PDF files are supported");
        }

        PdfTextExtractor.ExtractedText extracted = pdfTextExtractor.extract(file.getBytes());

        Document document = new Document();
        document.setOwner(owner);
        document.setFileName(filename);
        document.setPageCount(extracted.pageCount);
        document.setStatus(Document.ProcessingStatus.PROCESSING);
        document.setChunkCount(0);
        document = documentRepository.save(document);

        try {
            List<String> textChunks = textChunker.chunk(extracted.text);
            if (textChunks.isEmpty()) {
                throw new IllegalArgumentException("No extractable text found in this PDF (it may be scanned/image-only)");
            }

            int index = 0;
            for (String chunkText : textChunks) {
                double[] vector = embeddingService.embed(chunkText);

                Chunk chunk = new Chunk();
                chunk.setDocument(document);
                chunk.setChunkIndex(index++);
                chunk.setContent(chunkText);
                chunk.setEmbedding(embeddingService.toJson(vector));
                chunkRepository.save(chunk);
            }

            document.setChunkCount(textChunks.size());
            document.setStatus(Document.ProcessingStatus.READY);
            return documentRepository.save(document);

        } catch (Exception e) {
            document.setStatus(Document.ProcessingStatus.FAILED);
            documentRepository.save(document);
            throw e;
        }
    }

    public List<Document> listForUser(User user) {
        return documentRepository.findByOwnerOrderByUploadedAtDesc(user);
    }

    public Document getOwned(Long documentId, User user) {
        return documentRepository.findByIdAndOwner(documentId, user)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
    }

    public void deleteDocument(Long documentId, User user) {
        Document document = getOwned(documentId, user);
        chunkRepository.deleteByDocument(document);
        documentRepository.delete(document);
    }
}
