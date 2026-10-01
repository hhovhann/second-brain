package com.hhovhann.brain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Exact-word search (BM25) to sit next to the embedding search.
 *
 * <p>Embeddings match meaning and miss exact names: "Neo4j", "Gradle 9.7.1" or a commit hash can
 * rank low although the note contains them verbatim. Ranking by words catches those, and the two
 * rankings are fused in {@link #fuse}. Pure code, no model.
 */
final class Keyword {

    private static final double K1 = 1.5;
    private static final double B = 0.75;
    private static final int RRF = 60;
    private static final Set<String> STOP = Set.of(
            "the", "and", "for", "was", "were", "why", "what", "when", "who", "how", "which", "did", "does", "has",
            "have", "had", "are", "is", "its", "it", "this", "that", "with", "from", "into", "not", "any", "all",
            "our", "you", "your", "can", "could", "would", "should", "than", "then", "there", "their", "they");

    private Keyword() {}

    static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        for (String t : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}.]+")) {
            String w = t.replaceAll("^\\.+|\\.+$", "");
            if (w.length() >= 2 && !STOP.contains(w)) {
                out.add(w);
            }
        }
        return out;
    }

    /** Indexes of the notes that share at least one word with the question, best first. */
    static List<Integer> rank(List<Note> notes, String question) {
        List<String> query = tokens(question).stream().distinct().toList();
        List<List<String>> docs = notes.stream().map(n -> tokens(n.title() + " " + n.body() + " " + n.quote())).toList();
        double avg = docs.stream().mapToInt(List::size).average().orElse(1);
        Map<String, Integer> df = new HashMap<>();
        for (List<String> d : docs) {
            d.stream().distinct().forEach(t -> df.merge(t, 1, Integer::sum));
        }
        double[] score = new double[notes.size()];
        for (int i = 0; i < docs.size(); i++) {
            Map<String, Integer> tf = new HashMap<>();
            docs.get(i).forEach(t -> tf.merge(t, 1, Integer::sum));
            for (String q : query) {
                int f = tf.getOrDefault(q, 0);
                if (f == 0) {
                    continue;
                }
                double idf = Math.log(1 + (notes.size() - df.get(q) + 0.5) / (df.get(q) + 0.5));
                score[i] += idf * f * (K1 + 1) / (f + K1 * (1 - B + B * docs.get(i).size() / avg));
            }
        }
        return IntStream.range(0, notes.size()).boxed()
                .filter(i -> score[i] > 0)
                .sorted(Comparator.comparingDouble((Integer i) -> score[i]).reversed())
                .toList();
    }

    /** Reciprocal-rank fusion: a note ranked well by either search rises; one ranked well by both rises most. */
    static List<Integer> fuse(List<Integer> first, List<Integer> second) {
        Map<Integer, Double> score = new LinkedHashMap<>();
        for (List<Integer> ranking : List.of(first, second)) {
            for (int r = 0; r < ranking.size(); r++) {
                score.merge(ranking.get(r), 1.0 / (RRF + r + 1), Double::sum);
            }
        }
        return score.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();
    }
}
