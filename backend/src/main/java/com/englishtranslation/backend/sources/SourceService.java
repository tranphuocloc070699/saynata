package com.englishtranslation.backend.sources;

import com.englishtranslation.backend.admin.InvalidAdminRequestException;
import com.englishtranslation.backend.common.ApiException;
import com.englishtranslation.backend.prefill.PrefillItem;
import com.englishtranslation.backend.prefill.PrefillItemRepository;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The Sources page (TASK-108): reads an uploaded book, remembers the candidates (never the text), and queues the
 * picked ones in prefill_item. No LLM call anywhere in here.
 */
@Service
public class SourceService {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    static final int MAX_TITLE = 100;
    static final String PHRASE_LANG = "vi";
    static final String SOURCE_PREFIX = "book:";

    /** One item the admin picked: kind "word" or "phrase" and its text, as the upload listed it. */
    public record Pick(String kind, String text) {
    }

    /** The counts above the table. {@code useful} and {@code rare} count the new words and phrases together. */
    public record Counts(int wordsInFile, int known, int newWords, int newPhrases, int useful, int rare,
            int namesSkipped) {
    }

    /** What the Sources page shows: the counts and the candidates (words, phrases, and skipped names). */
    public record View(long id, String title, String status, int queuedCount, Counts counts,
            List<SourceCandidate> candidates) {
    }

    public record QueueResult(int queued, int alreadyQueued, String source) {
    }

    private final SourceAnalyzer analyzer;
    private final KnownLookup lookup;
    private final SourceUploadRepository uploads;
    private final PrefillItemRepository queue;
    private final Clock clock;

    public SourceService(SourceAnalyzer analyzer, KnownLookup lookup, SourceUploadRepository uploads,
            PrefillItemRepository queue, Clock clock) {
        this.analyzer = analyzer;
        this.lookup = lookup;
        this.uploads = uploads;
        this.queue = queue;
        this.clock = clock;
    }

    /** Reads the file, finds the candidates and saves them. The file name is only used when the title is empty. */
    public View upload(String title, String fileName, byte[] bytes) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".markdown"))) {
            throw new InvalidAdminRequestException("Upload a .txt or .md file.");
        }
        if (bytes.length == 0) {
            throw new InvalidAdminRequestException("The file is empty.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "file_too_large",
                    "That file is too big. The limit is 5 MB.");
        }
        String text = decode(bytes);
        String cleanTitle = cleanTitle(title, fileName);

        SourceAnalyzer.Analysis analysis = analyzer.analyze(text, lookup);
        int newWords = (int) analysis.candidates().stream().filter(c -> SourceCandidate.WORD.equals(c.kind())).count();
        int newPhrases = (int) analysis.candidates().stream().filter(c -> SourceCandidate.PHRASE.equals(c.kind())).count();
        String lines = analysis.candidates().stream().map(SourceCandidate::toLine).collect(Collectors.joining("\n"));
        SourceUpload saved = uploads.save(new SourceUpload(cleanTitle, analysis.wordsInFile(), analysis.known(),
                newWords, newPhrases, analysis.namesSkipped(), lines, clock.instant()));
        return view(saved);
    }

    @Transactional(readOnly = true)
    public View get(long id) {
        return view(find(id));
    }

    /**
     * Queues the picked words (kind word) and phrases (kind sentence, language vi) with source "book:title". Only
     * items that are in the upload can be picked. Items already in the queue are skipped. Afterwards the snippets are
     * deleted and the upload can not be queued again (409 already_queued).
     */
    @Transactional
    public QueueResult queue(long id, List<Pick> picks) {
        SourceUpload upload = find(id);
        if (SourceUpload.QUEUED.equals(upload.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "already_queued", "This upload was added to the queue already.");
        }
        if (picks == null || picks.isEmpty()) {
            throw new InvalidAdminRequestException("Pick at least one word or phrase.");
        }
        List<SourceCandidate> candidates = parse(upload.getCandidates());
        Map<String, SourceCandidate> byKey = candidates.stream()
                .filter(c -> !"name".equals(c.kind()))
                .collect(Collectors.toMap(c -> c.kind() + ":" + c.text(), c -> c, (a, b) -> a));

        String source = SOURCE_PREFIX + upload.getTitle();
        Set<String> seen = new HashSet<>();
        int queued = 0;
        int already = 0;
        for (Pick pick : picks) {
            SourceCandidate candidate = pick == null || pick.kind() == null || pick.text() == null ? null
                    : byKey.get(pick.kind() + ":" + pick.text());
            if (candidate == null) {
                throw new InvalidAdminRequestException("Not in this upload: " + (pick == null ? "" : pick.text()) + ".");
            }
            if (!seen.add(candidate.kind() + ":" + candidate.text())) {
                continue;
            }
            boolean phrase = SourceCandidate.PHRASE.equals(candidate.kind());
            String kind = phrase ? PrefillItem.SENTENCE : PrefillItem.WORD;
            String lang = phrase ? PHRASE_LANG : "";
            if (queue.findByKindAndTextAndTargetLang(kind, candidate.text(), lang).isPresent()) {
                already++;
                continue;
            }
            PrefillItem item = new PrefillItem(kind, candidate.text(), lang, source, priorityFor(candidate),
                    clock.instant());
            queue.save(item);
            queued++;
        }
        String cleared = candidates.stream().map(c -> c.withoutSnippet().toLine()).collect(Collectors.joining("\n"));
        upload.markQueued(cleared, queued, clock.instant());
        uploads.save(upload);
        return new QueueResult(queued, already, source);
    }

    /**
     * Same scale as the pipeline (round(zipf * 100), about 300-600), plus 5 for each time the word was seen in the
     * book (at most 20 times), so the words a book uses most are answered first.
     */
    static int priorityFor(SourceCandidate candidate) {
        return (int) Math.round(candidate.zipf() * 100) + 5 * Math.min(candidate.times(), 20);
    }

    private SourceUpload find(long id) {
        return uploads.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No upload with id " + id + "."));
    }

    private View view(SourceUpload upload) {
        List<SourceCandidate> candidates = parse(upload.getCandidates());
        long useful = candidates.stream().filter(c -> !"name".equals(c.kind()) && c.useful()).count();
        long rare = candidates.stream().filter(c -> !"name".equals(c.kind()) && !c.useful()).count();
        Counts counts = new Counts(upload.getWordsInFile(), upload.getKnown(), upload.getNewWords(),
                upload.getNewPhrases(), (int) useful, (int) rare, upload.getNamesSkipped());
        return new View(upload.getId(), upload.getTitle(), upload.getStatus(), upload.getQueuedCount(), counts,
                candidates);
    }

    private static List<SourceCandidate> parse(String lines) {
        List<SourceCandidate> out = new ArrayList<>();
        for (String line : lines.split("\n")) {
            if (!line.isBlank()) {
                out.add(SourceCandidate.fromLine(line));
            }
        }
        return out;
    }

    private static String decode(byte[] bytes) {
        for (byte b : bytes) {
            if (b == 0) {
                throw new InvalidAdminRequestException("That file is not text.");
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            // An old Latin-1 file: every byte is a character, so this never fails.
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    private static String cleanTitle(String title, String fileName) {
        String value = title == null ? "" : title.strip();
        if (value.isEmpty() && fileName != null) {
            int dot = fileName.lastIndexOf('.');
            value = (dot >= 0 ? fileName.substring(0, dot) : fileName).strip();
        }
        value = value.replaceAll("\\s+", " ");
        if (value.isEmpty()) {
            throw new InvalidAdminRequestException("Give the source a title.");
        }
        return value.length() > MAX_TITLE ? value.substring(0, MAX_TITLE) : value;
    }
}
