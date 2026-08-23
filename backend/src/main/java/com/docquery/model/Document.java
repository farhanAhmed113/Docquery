package com.docquery.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User owner;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private int pageCount;

    @Column(nullable = false)
    private int chunkCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcessingStatus status = ProcessingStatus.PROCESSING;

    @Column(nullable = false, updatable = false)
    private Instant uploadedAt = Instant.now();

    public enum ProcessingStatus {
        PROCESSING, READY, FAILED
    }
}
