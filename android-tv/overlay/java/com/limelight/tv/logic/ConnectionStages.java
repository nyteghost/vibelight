package com.limelight.tv.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Real connection stages as reported by the Android core and moonlight-common-c.
 * No progress percentages are computed; the UI shows which named stages have
 * completed. Unknown stage names are shown verbatim rather than guessed.
 */
public final class ConnectionStages {
    public enum State { PENDING, ACTIVE, DONE, FAILED }

    public static final class Step {
        public final String key, label;
        public State state = State.PENDING;
        Step(String key, String label) { this.key = key; this.label = label; }
    }

    private final List<Step> steps = new ArrayList<>();
    private final String appName;
    private long generation;

    public ConnectionStages(String appName) {
        this.appName = appName == null ? "" : appName;
        steps.add(new Step("launch", "Verifying pairing and starting " + (this.appName.isEmpty() ? "the app" : this.appName)));
        steps.add(new Step("network", "Finding the PC on the network"));
        steps.add(new Step("rtsp", "Negotiating the stream"));
        steps.add(new Step("streams", "Opening video, audio and input"));
    }

    public List<Step> steps() { return steps; }

    public long generation() { return generation; }

    /** Invalidates earlier callbacks (cancel, retry). */
    public long invalidate() { return ++generation; }

    /** Maps a stage name from the core to one of the displayed steps. */
    public String keyFor(String stage) {
        if (stage == null) return null;
        if (stage.equals(appName)) return "launch";
        String s = stage.toLowerCase(Locale.ROOT);
        if (s.contains("platform") || s.contains("name resolution")) return "network";
        if (s.contains("rtsp")) return "rtsp";
        if (s.contains("stream")) return "streams";
        return null;
    }

    public void starting(String stage) {
        String key = keyFor(stage);
        if (key == null) return;
        boolean before = true;
        for (Step st : steps) {
            if (st.key.equals(key)) { if (st.state != State.DONE) st.state = State.ACTIVE; before = false; }
            else if (before && st.state != State.FAILED) st.state = State.DONE;
        }
    }

    public void complete(String stage) {
        String key = keyFor(stage);
        if (key == null) return;
        // The "streams" step spans several core stages; it is only done on connectionStarted.
        if (key.equals("streams")) return;
        for (Step st : steps) if (st.key.equals(key)) st.state = State.DONE;
    }

    public void connected() { for (Step st : steps) st.state = State.DONE; }

    public void failed(String stage) {
        String key = keyFor(stage);
        for (Step st : steps) {
            if ((key != null && st.key.equals(key)) || (key == null && st.state == State.ACTIVE)) st.state = State.FAILED;
        }
    }

    /** Human label for an arbitrary core stage name, verbatim when unknown. */
    public String describeStage(String stage) {
        String key = keyFor(stage);
        if (key != null) for (Step st : steps) if (st.key.equals(key)) return st.label;
        return stage == null ? "Unknown stage" : stage;
    }
}
