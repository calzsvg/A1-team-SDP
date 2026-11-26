package engine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Local Survival ranking: the best runs ordered by score, kept in a small text
 * file next to the game (the same place the classic high scores are stored).
 * Each line is {@code score,seconds,level}.
 */
public final class SurvivalRanking {
    /** How many runs the ranking keeps. */
    public static final int MAX_ENTRIES = 10;
    /** File name, stored beside the other save files. */
    public static final String FILE_NAME = "survival_ranking";
    private static final Logger LOGGER = Logger.getLogger(SurvivalRanking.class.getSimpleName());

    /** One finished run. */
    public static final class Entry {
        public final int score, seconds, level;
        public Entry(int score, int seconds, int level) {
            this.score = score; this.seconds = seconds; this.level = level;
        }
    }

    private final File file;
    private final List<Entry> entries = new ArrayList<>();

    /** Opens the ranking stored in the given file; a missing or damaged file gives an empty ranking. */
    public SurvivalRanking(File file) {
        this.file = file;
        load();
    }

    /** Opens the ranking stored beside the game, like the classic high scores. */
    public static SurvivalRanking local() {
        try {
            String jarPath = FileManager.class.getProtectionDomain()
                    .getCodeSource().getLocation().getPath();
            jarPath = URLDecoder.decode(jarPath, "UTF-8");
            return new SurvivalRanking(new File(new File(jarPath).getParent(), FILE_NAME));
        } catch (IOException | RuntimeException e) {
            LOGGER.warning("Couldn't locate the Survival ranking file: " + e.getMessage());
            return new SurvivalRanking(new File(FILE_NAME));
        }
    }

    /** The saved runs, best score first. */
    public List<Entry> entries() { return Collections.unmodifiableList(entries); }

    /**
     * Adds a finished run and saves the ranking. Runs with equal scores keep the order they were
     * set in, so the newer one ranks lower.
     *
     * @return the run's zero-based place in the ranking, or -1 if it did not make the top {@link #MAX_ENTRIES}
     */
    public int record(int score, int seconds, int level) {
        int place = 0;
        while (place < entries.size() && entries.get(place).score >= score) place++;
        if (place >= MAX_ENTRIES) return -1;
        entries.add(place, new Entry(score, seconds, level));
        while (entries.size() > MAX_ENTRIES) entries.remove(entries.size() - 1);
        save();
        return place;
    }

    private void load() {
        entries.clear();
        if (!file.isFile()) return;
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split(",");
                if (parts.length != 3) continue;
                try {
                    int score = Integer.parseInt(parts[0].trim());
                    int seconds = Integer.parseInt(parts[1].trim());
                    int level = Integer.parseInt(parts[2].trim());
                    if (score < 0 || seconds < 0 || level < 1) continue;
                    entries.add(new Entry(score, seconds, level));
                } catch (NumberFormatException e) {
                    // Skip a damaged line and keep the rest.
                }
            }
        } catch (IOException e) {
            LOGGER.warning("Couldn't load the Survival ranking: " + e.getMessage());
        }
        // Stable sort keeps the saved order for equal scores.
        Collections.sort(entries, (a, b) -> Integer.compare(b.score, a.score));
        while (entries.size() > MAX_ENTRIES) entries.remove(entries.size() - 1);
    }

    private void save() {
        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            for (Entry entry : entries) {
                writer.write(entry.score + "," + entry.seconds + "," + entry.level);
                writer.newLine();
            }
        } catch (IOException e) {
            LOGGER.warning("Couldn't save the Survival ranking: " + e.getMessage());
        }
    }
}
