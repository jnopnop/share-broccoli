package problems.autocompletesystem;

import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class AutocompleteSystem {
    private static final int TOP = 3;
    private final Map<String, Pair<Integer, AtomicInteger>> freqMap = new HashMap<>();
    private final List<String> index = new ArrayList<>();
    private final Trie trie = new Trie();
    private final Comparator<Integer> rankedComparator = (l, r) -> {
        String left = index.get(l);
        String right = index.get(r);
        int freqL = freqMap.get(left).getValue().get();
        int freqR = freqMap.get(right).getValue().get();
        if (freqL != freqR) {
            return freqR - freqL;
        }
        return left.compareTo(right);
    };

    private Trie typeAheadTrie;
    private StringBuilder input;

    public AutocompleteSystem(String[] sentences, int[] times) {
        for (int i = 0; i < times.length; i++) {
            insert(sentences[i], times[i]);
        }

        this.input = new StringBuilder();
        this.typeAheadTrie = trie;
    }

    public List<String> input(char c) {
        if (c == '#') {
            insert(input.toString(), 1);
            input = new StringBuilder();
            typeAheadTrie = trie;
            return List.of();
        }

        input.append(c);
        if (typeAheadTrie == null) {
            return List.of();
        }

        Pair<Trie, Set<Integer>> next = typeAheadTrie.advance(c);
        if (next == null || next.getValue().isEmpty()) {
            typeAheadTrie = null;
            return List.of();
        }

        typeAheadTrie = next.getKey();
        return buildResults(next.getValue());
    }

    private List<String> buildResults(Set<Integer> sentenceIndices) {
        PriorityQueue<Integer> topQueue = new PriorityQueue<>(rankedComparator.reversed());
        for (Integer i: sentenceIndices) {
            if (topQueue.size() < TOP) {
                topQueue.offer(i);
                continue;
            }
            if (rankedComparator.compare(topQueue.peek(), i) > 0) {
                topQueue.poll();
                topQueue.offer(i);
            }
        }
        String[] result = new String[topQueue.size()];
        for (int i = topQueue.size() - 1; !topQueue.isEmpty(); i--) {
            int sentenceIndex = topQueue.poll();
            result[i] = index.get(sentenceIndex);
        }
        return Arrays.asList(result);
    }

    private void insert(String s, int newFreq) {
        Pair<Integer, AtomicInteger> indexToFreq = freqMap.get(s);
        if (indexToFreq == null) {
            int sentenceIndex = index.size();
            index.add(s);
            indexToFreq = Pair.of(sentenceIndex, new AtomicInteger(newFreq));
            freqMap.put(s, indexToFreq);
        } else {
            indexToFreq.getValue().incrementAndGet();
        }

        trie.add(s, indexToFreq.getKey());
    }

    private static class Trie {
        private final Map<Character, Pair<Trie, Set<Integer>>> children = new HashMap<>();

        public Pair<Trie, Set<Integer>> advance(char c) {
            return children.get(c);
        }

        public void add(CharSequence s, int sentenceIndex) {
            add(s, 0, sentenceIndex);
        }

        private void add(CharSequence s, int i, int sentenceIndex) {
            Trie curr = this;
            while (i < s.length()) {
                char c = s.charAt(i);
                curr.children.putIfAbsent(c, Pair.of(new Trie(), new HashSet<>()));
                var trieToSentences = curr.children.get(c);
                trieToSentences.getValue().add(sentenceIndex);
                curr = trieToSentences.getKey();
                i++;
            }
        }
    }
}
