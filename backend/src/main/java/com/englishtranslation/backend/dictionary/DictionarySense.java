package com.englishtranslation.backend.dictionary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One meaning of a bundled word. examples and usage are JSON text, the same lists the app reads from
 * dictionary.sqlite. The image fields and the media row are not part of the admin entry; an edit keeps them.
 */
@Entity
@Table(name = "dictionary_sense")
public class DictionarySense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "word_id", nullable = false)
    private DictionaryWord word;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "pos", nullable = false, length = 32)
    private String pos;

    @Column(name = "cefr", nullable = false, length = 8)
    private String cefr;

    @Column(name = "simple_definition", nullable = false, columnDefinition = "TEXT")
    private String simpleDefinition;

    @Column(name = "vietnamese", nullable = false, columnDefinition = "TEXT")
    private String vietnamese;

    @Column(name = "examples_json", nullable = false, columnDefinition = "TEXT")
    private String examplesJson;

    @Column(name = "usage_json", nullable = false, columnDefinition = "TEXT")
    private String usageJson;

    @Column(name = "image_type", nullable = false, length = 16)
    private String imageType;

    @Column(name = "image_query", nullable = false)
    private String imageQuery;

    @Column(name = "media_id")
    private Long mediaId;

    protected DictionarySense() {
    }

    /** A new sense added in the admin has no picture: image_type "none". */
    public DictionarySense(DictionaryWord word, int position) {
        this.word = word;
        this.position = position;
        this.imageType = "none";
        this.imageQuery = "";
    }

    public int getPosition() {
        return position;
    }

    public String getPos() {
        return pos;
    }

    public String getCefr() {
        return cefr;
    }

    public String getSimpleDefinition() {
        return simpleDefinition;
    }

    public String getVietnamese() {
        return vietnamese;
    }

    public String getExamplesJson() {
        return examplesJson;
    }

    public String getUsageJson() {
        return usageJson;
    }

    /** Replaces the text fields an admin can edit. The picture fields stay. */
    public void setText(String pos, String cefr, String simpleDefinition, String vietnamese, String examplesJson,
            String usageJson) {
        this.pos = pos;
        this.cefr = cefr;
        this.simpleDefinition = simpleDefinition;
        this.vietnamese = vietnamese;
        this.examplesJson = examplesJson;
        this.usageJson = usageJson;
    }
}
