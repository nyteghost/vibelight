package com.limelight.tv.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The grouped, searchable settings model. Every stored setting reuses the existing
 * Moonlight preference key, so the stock "All settings" screen stays in sync.
 */
public final class SettingsCatalog {
    public enum Group {
        GENERAL("General"), STREAMING("Streaming"), DISPLAY("Display & HDR"), AUDIO("Audio"),
        CONTROLS("Controls"), NETWORK("Network"), ACCESSIBILITY("Accessibility"),
        ADVANCED("Advanced"), UPDATES("Updates"), ABOUT("About");
        public final String label;
        Group(String label) { this.label = label; }
    }

    public enum Type { TOGGLE, CHOICE, PROFILE, TEXT, ACTION }

    public static final class Item {
        public final Group group;
        public final Type type;
        public final String id, title, summary, keywords;
        public final String key;            // preference key (TOGGLE / CHOICE / TEXT)
        public final List<String> values;   // stored values (CHOICE)
        public final List<String> labels;   // shown labels (CHOICE)
        public final String defaultValue;

        Item(Group g, Type t, String id, String title, String summary, String keywords, String key,
             List<String> values, List<String> labels, String def) {
            this.group = g; this.type = t; this.id = id; this.title = title; this.summary = summary;
            this.keywords = keywords; this.key = key; this.values = values; this.labels = labels; this.defaultValue = def;
        }

        String haystack() {
            return (title + " " + summary + " " + keywords + " " + group.label).toLowerCase(Locale.ROOT);
        }
    }

    public static final String KEY_REDUCE_EFFECTS = "vibe_reduce_effects";
    public static final String KEY_DISPLAY_NAME = "vibe_display_name";
    public static final String KEY_PANEL_COMBO = "vibe_combo_opens_panel";

    private static List<String> l(String... s) { return Collections.unmodifiableList(Arrays.asList(s)); }

    private static Item toggle(Group g, String key, String title, String summary, String kw, boolean def) {
        return new Item(g, Type.TOGGLE, key, title, summary, kw, key, null, null, String.valueOf(def));
    }
    private static Item choice(Group g, String key, String title, String summary, String kw, List<String> v, List<String> lab, String def) {
        return new Item(g, Type.CHOICE, key, title, summary, kw, key, v, lab, def);
    }
    private static Item action(Group g, String id, String title, String summary, String kw) {
        return new Item(g, Type.ACTION, id, title, summary, kw, null, null, null, null);
    }

    public static List<Item> all() {
        List<Item> a = new ArrayList<>();
        a.add(new Item(Group.GENERAL, Type.TEXT, KEY_DISPLAY_NAME, "Your name", "Shown in the greeting on this device only", "greeting name personal", KEY_DISPLAY_NAME, null, null, ""));
        a.add(action(Group.GENERAL, "classic", "Classic dashboard", "The previous Vibelight TV home screen, kept for recovery", "old previous fallback recovery"));
        a.add(action(Group.GENERAL, "pcs", "Pair & manage PCs (advanced)", "The full Moonlight PC manager", "pair unpair remove delete host"));

        a.add(new Item(Group.STREAMING, Type.PROFILE, "profile", "Streaming quality profile", "Changes bitrate only; applies to the next stream", "quality bitrate low latency balanced best picture", BitrateProfile.KEY_BITRATE, null, null, null));
        a.add(choice(Group.STREAMING, "list_resolution", "Resolution", "Streaming resolution", "resolution 4k 1080p 720p 1440p",
                l("1280x720", "1920x1080", "2560x1440", "3840x2160"), l("720p", "1080p", "1440p", "4K"), "3840x2160"));
        a.add(choice(Group.STREAMING, "list_fps", "Frame rate", "Frames per second", "fps frame rate 60 120 30",
                l("30", "60", "90", "120"), l("30 FPS", "60 FPS", "90 FPS", "120 FPS"), "60"));
        a.add(choice(Group.STREAMING, "video_format", "Video codec", "Automatic picks HEVC when the decoder supports it", "codec hevc h264 h.265 av1",
                l("auto", "forceh265", "neverh265", "forceav1"), l("Automatic", "Always HEVC", "Always H.264", "Always AV1"), "auto"));
        a.add(choice(Group.STREAMING, "frame_pacing", "Frame pacing", "Trade latency against smoothness", "pacing smooth latency",
                l("latency", "balanced", "cap-fps", "smoothness"), l("Lowest latency", "Balanced", "Balanced with FPS limit", "Smoothest"), "latency"));
        a.add(toggle(Group.STREAMING, "checkbox_enable_sops", "Optimize game settings", "Let the PC adjust game settings for this stream", "sops optimize", true));

        a.add(toggle(Group.DISPLAY, "checkbox_enable_hdr", "HDR", "Requests HDR10. Needs an HDR display, HEVC Main10 decoder and HDR on the PC", "hdr hdr10 10-bit", false));
        a.add(toggle(Group.DISPLAY, "checkbox_full_range", "Full-range video", "Experimental; only if blacks look washed out", "range color", false));
        a.add(toggle(Group.DISPLAY, "checkbox_stretch_video", "Stretch to full screen", "Ignores the aspect ratio", "stretch aspect", false));
        a.add(toggle(Group.DISPLAY, "checkbox_reduce_refresh_rate", "Allow refresh rate reduction", "Lets the TV match a lower stream frame rate", "refresh rate hz", false));
        a.add(action(Group.DISPLAY, "display-check", "Display capabilities", "What this TV and decoder report", "hdr decoder 4k supported"));

        a.add(choice(Group.AUDIO, "list_audio_config", "Audio channels", "Surround needs a receiver or TV that supports it", "audio surround stereo 5.1 7.1",
                l("2", "51", "71"), l("Stereo", "5.1 surround", "7.1 surround"), "2"));
        a.add(toggle(Group.AUDIO, "checkbox_host_audio", "Play audio on the PC", "Also keep sound playing on the PC", "speaker pc audio", false));

        a.add(toggle(Group.CONTROLS, "checkbox_multi_controller", "Automatic controller detection", "Report each connected controller to the PC", "gamepad controller multiple", true));
        a.add(toggle(Group.CONTROLS, "checkbox_usb_driver", "Xbox USB controller driver", "Built-in driver for wired Xbox controllers", "xbox usb", true));
        a.add(toggle(Group.CONTROLS, "checkbox_mouse_emulation", "Mouse emulation", "Hold Start to control the mouse with a controller", "mouse emulation", true));
        a.add(toggle(Group.CONTROLS, "checkbox_flip_face_buttons", "Flip face buttons", "Swap A/B and X/Y", "nintendo face buttons swap", false));
        a.add(toggle(Group.CONTROLS, KEY_PANEL_COMBO, "Quit combo opens Vibelight panel", "Start + Back + LB + RB opens the in-stream panel instead of disconnecting at once", "combo panel menu quit", true));
        a.add(action(Group.CONTROLS, "controllers", "Connected controllers", "Controllers Android currently reports", "gamepad connected"));

        a.add(action(Group.NETWORK, "connection-check", "Connection check", "Real tests of this device's network and your PC", "network test latency ports check"));

        a.add(toggle(Group.ACCESSIBILITY, KEY_REDUCE_EFFECTS, "Reduce effects", "Turns off motion, scaling and animated rings", "motion animation reduce effects accessibility", false));
        a.add(toggle(Group.ACCESSIBILITY, "checkbox_disable_warnings", "Hide connection warnings", "Hide poor-connection notices during streams", "warnings", false));

        a.add(toggle(Group.ADVANCED, "checkbox_enable_perf_overlay", "Statistics overlay", "Measured FPS, decode time, frame loss and RTT while streaming", "stats statistics overlay performance", false));
        a.add(toggle(Group.ADVANCED, "checkbox_unlock_fps", "Unlock all frame rates", "Offer frame rates the display may not support", "unlock fps", false));
        a.add(action(Group.ADVANCED, "all-settings", "All settings", "Every Moonlight streaming, input and on-screen control option", "advanced stock moonlight everything"));

        a.add(action(Group.UPDATES, "updates", "Beta updates", "Check, verify and install a newer Vibelight TV beta", "update upgrade version"));

        a.add(action(Group.ABOUT, "about", "About Vibelight TV", "Version, licences and source", "version license gpl source about"));
        return a;
    }

    public static List<Item> inGroup(List<Item> items, Group g) {
        List<Item> out = new ArrayList<>();
        for (Item i : items) if (i.group == g) out.add(i);
        return out;
    }

    /** Every query word must match the title, summary, keywords or group. */
    public static List<Item> search(List<Item> items, String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return new ArrayList<>(items);
        List<Item> out = new ArrayList<>();
        outer:
        for (Item i : items) {
            String h = i.haystack();
            for (String w : q.split("\\s+")) if (!w.isEmpty() && !h.contains(w)) continue outer;
            out.add(i);
        }
        return out;
    }
}
