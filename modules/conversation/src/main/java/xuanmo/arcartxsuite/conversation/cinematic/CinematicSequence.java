package xuanmo.arcartxsuite.conversation.cinematic;

import java.util.List;

/** A sequence has an explicit end and cues sorted by server tick. */
public record CinematicSequence(String id, String name, long durationTicks, List<Cue> cues) {
    public CinematicSequence {
        cues = List.copyOf(cues);
    }

    public int actionCount() {
        return cues.size();
    }

    public record Cue(long tick, CinematicAction action) {
    }
}
