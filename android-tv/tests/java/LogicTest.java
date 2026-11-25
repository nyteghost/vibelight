import com.limelight.tv.logic.*;
import java.util.*;

/** Plain-JVM tests for Vibelight TV's Android-independent logic. */
public final class LogicTest {
    private static int checks;
    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    public static void main(String[] args) {
        hostStatus();
        recentGames();
        bitrateProfiles();
        stages();
        panel();
        library();
        settings();
        greeting();
        art();
        System.out.println(checks + " Vibelight TV logic checks passed");
    }

    static void hostStatus() {
        HostStatus s = HostStatus.of(HostStatus.Reachability.UNKNOWN, true, 0, false);
        check(s.kind == HostStatus.Kind.CHECKING && !s.canOpenLibrary, "unknown is checking, not offline");
        s = HostStatus.of(HostStatus.Reachability.OFFLINE, true, 0, false);
        check(s.kind == HostStatus.Kind.OFFLINE && !s.canWake, "offline without MAC cannot wake");
        check(!s.label.toLowerCase().contains("asleep") && !s.detail.toLowerCase().contains("asleep"), "never claims asleep");
        s = HostStatus.of(HostStatus.Reachability.OFFLINE, true, 0, true);
        check(s.kind == HostStatus.Kind.OFFLINE_WAKEABLE && s.canWake && !s.canOpenLibrary, "offline with MAC can wake");
        s = HostStatus.of(HostStatus.Reachability.ONLINE, false, 0, true);
        check(s.kind == HostStatus.Kind.PAIRING_NEEDED && s.canPair && !s.canOpenLibrary, "unpaired needs pairing");
        s = HostStatus.of(HostStatus.Reachability.ONLINE, false, 7, true);
        check(s.kind == HostStatus.Kind.PAIRING_NEEDED && !s.canResume, "unpaired never offers resume");
        s = HostStatus.of(HostStatus.Reachability.ONLINE, true, 7, false);
        check(s.kind == HostStatus.Kind.GAME_RUNNING && s.canResume && s.canOpenLibrary, "running game resumable");
        s = HostStatus.of(HostStatus.Reachability.ONLINE, true, 0, false);
        check(s.kind == HostStatus.Kind.READY && !s.canResume, "ready host");
        check(HostStatus.of(null, true, 0, false).kind == HostStatus.Kind.CHECKING, "null reachability treated as unknown");
    }

    static void recentGames() {
        List<RecentGames.Entry> r = new ArrayList<>();
        r = RecentGames.record(r, "host-a", 1, "Game\tOne\nX\\Y", 100);
        r = RecentGames.record(r, "host-a", 2, "Two", 200);
        r = RecentGames.record(r, "host-b", 1, "Other host", 300);
        r = RecentGames.record(r, "host-a", 1, "Game\tOne\nX\\Y", 400);
        check(r.size() == 3, "one entry per host+app");
        check(r.get(0).appId == 1 && r.get(0).startedAt == 400, "re-recorded entry moves to front");
        List<RecentGames.Entry> back = RecentGames.decode(RecentGames.encode(r));
        check(back.size() == 3 && back.get(0).appName.equals("Game\tOne\nX\\Y"), "escaping round-trips");
        List<RecentGames.Entry> a = RecentGames.forHost(back, "host-a");
        check(a.size() == 2 && a.get(0).appId == 1 && a.get(1).appId == 2, "per-host newest first");
        check(RecentGames.decode("garbage\nhost\tnot-a-number\tx\t5\n" + RecentGames.encode(r)).size() == 3, "corrupt lines skipped");
        check(RecentGames.decode(null).isEmpty() && RecentGames.decode("").isEmpty(), "empty storage");
        check(RecentGames.record(r, "", 9, "x", 5).size() == 3, "missing host not recorded");
        check(RecentGames.record(r, "h", 9, "x", 0).size() == 3, "missing time not recorded");
        List<RecentGames.Entry> many = new ArrayList<>();
        for (int i = 0; i < 40; i++) many = RecentGames.record(many, "h", i, "g" + i, 1000 + i);
        check(many.size() == RecentGames.MAX && many.get(0).appId == 39, "bounded history keeps newest");
    }

    static void bitrateProfiles() {
        check(BitrateProfile.match(35000) == BitrateProfile.LOW_LATENCY, "low latency match");
        check(BitrateProfile.match(50000) == BitrateProfile.BALANCED, "balanced match");
        check(BitrateProfile.match(75000) == BitrateProfile.BEST_PICTURE, "best picture match");
        check(BitrateProfile.match(42000) == null, "custom bitrate");
        check(BitrateProfile.describe(42000).startsWith("Custom"), "custom label");
        check(BitrateProfile.KEY_BITRATE.equals("seekbar_bitrate_kbps"), "reuses core bitrate key");
    }

    static void stages() {
        ConnectionStages c = new ConnectionStages("Desktop");
        check(c.steps().size() == 4, "four visible steps");
        c.starting("Desktop");
        check(c.steps().get(0).state == ConnectionStages.State.ACTIVE, "launch active");
        c.complete("Desktop");
        c.starting("name resolution");
        check(c.steps().get(0).state == ConnectionStages.State.DONE && c.steps().get(1).state == ConnectionStages.State.ACTIVE, "network active after launch");
        c.starting("RTSP handshake");
        check(c.steps().get(1).state == ConnectionStages.State.DONE && c.steps().get(2).state == ConnectionStages.State.ACTIVE, "rtsp active");
        c.starting("video stream initialization");
        c.complete("video stream initialization");
        check(c.steps().get(3).state == ConnectionStages.State.ACTIVE, "streams step stays active until connected");
        c.failed("video stream establishment");
        check(c.steps().get(3).state == ConnectionStages.State.FAILED, "failure marks the right step");
        check(c.describeStage("something new").equals("something new"), "unknown stages shown verbatim");
        ConnectionStages d = new ConnectionStages("Game");
        d.starting("Game"); d.connected();
        for (ConnectionStages.Step s : d.steps()) check(s.state == ConnectionStages.State.DONE, "connected completes all");
        long g = d.generation();
        check(d.invalidate() == g + 1, "invalidate bumps generation");
        ConnectionStages e = new ConnectionStages("Game");
        e.starting("Game"); e.failed("unrecognized");
        check(e.steps().get(0).state == ConnectionStages.State.FAILED, "unrecognized failure marks the active step");
    }

    static void panel() {
        check(PanelGeometry.width(1920, 560, 320) == 560, "1080p uses design width");
        check(PanelGeometry.width(1280, 560, 320) <= 1280 / 3, "never more than a third");
        check(PanelGeometry.width(800, 560, 320) == 266, "small screen clamps to a third");
        check(PanelGeometry.width(3840, 1120, 640) == 1120, "4K density scaled design width");
        for (int w = 1; w < 5000; w += 37) check(PanelGeometry.width(w, 560, 320) * 3 <= w, "third rule at width " + w);
        check(PanelGeometry.width(0, 560, 320) == 0, "zero width");
    }

    static void library() {
        List<Library.Game> g = Arrays.asList(new Library.Game(3, "Zelda", false), new Library.Game(1, "alpha Racer", true),
                new Library.Game(2, "Beta Quest", false), new Library.Game(4, "Desktop", false));
        List<Library.Game> byName = Library.view(g, "", Library.Sort.NAME, null);
        check(byName.get(0).name.equals("alpha Racer") && byName.get(3).name.equals("Zelda"), "case-insensitive A-Z");
        check(Library.view(g, "  QUEST beta ", Library.Sort.NAME, null).size() == 1, "multi-word search");
        check(Library.view(g, "nothing", Library.Sort.NAME, null).isEmpty(), "no match");
        List<RecentGames.Entry> rec = Arrays.asList(new RecentGames.Entry("h", 3, "Zelda", 50), new RecentGames.Entry("h", 2, "Beta Quest", 90));
        List<Library.Game> recent = Library.view(g, "", Library.Sort.RECENT, rec);
        check(recent.get(0).id == 2 && recent.get(1).id == 3 && recent.get(2).name.equals("alpha Racer"), "recent first, then A-Z");
    }

    static void settings() {
        List<SettingsCatalog.Item> all = SettingsCatalog.all();
        Set<SettingsCatalog.Group> groups = EnumSet.noneOf(SettingsCatalog.Group.class);
        for (SettingsCatalog.Item i : all) groups.add(i.group);
        check(groups.size() == SettingsCatalog.Group.values().length, "every group has at least one item");
        Set<String> ids = new HashSet<>();
        for (SettingsCatalog.Item i : all) check(ids.add(i.id), "unique id " + i.id);
        check(!SettingsCatalog.search(all, "reduce effects").isEmpty(), "reduce effects searchable");
        check(!SettingsCatalog.search(all, "connection check").isEmpty(), "connection check searchable");
        check(!SettingsCatalog.search(all, "hevc").isEmpty(), "codec searchable");
        check(!SettingsCatalog.search(all, "controller").isEmpty(), "controller options searchable");
        check(!SettingsCatalog.search(all, "bitrate").isEmpty(), "profiles searchable");
        check(SettingsCatalog.search(all, "zzzz-none").isEmpty(), "no results");
        check(SettingsCatalog.search(all, "").size() == all.size(), "empty search shows all");
        for (SettingsCatalog.Item i : all) {
            if (i.type == SettingsCatalog.Type.CHOICE) {
                check(i.values.size() == i.labels.size() && i.values.contains(i.defaultValue), "choice integrity " + i.id);
            }
        }
        boolean reuses = false;
        for (SettingsCatalog.Item i : all) if ("checkbox_enable_hdr".equals(i.key)) reuses = true;
        check(reuses, "reuses existing HDR key");
    }

    static void greeting() {
        check(Greeting.forHour(8, null).equals("Good morning"), "morning");
        check(Greeting.forHour(13, " Mark ").equals("Good afternoon, Mark"), "afternoon with name");
        check(Greeting.forHour(23, "").equals("Good evening"), "evening");
        check(Greeting.forHour(2, null).equals("Good evening"), "night");
    }

    static void art() {
        check(ArtBudget.memoryBytes(512L << 20) == 48L << 20, "memory clamp high");
        check(ArtBudget.memoryBytes(16L << 20) == 8L << 20, "memory clamp low");
        check(ArtBudget.sampleSize(600, 800) == 1, "box art not downsampled");
        check(ArtBudget.sampleSize(3000, 4000) == 4, "large art downsampled");
        for (int e = 100; e < 9000; e += 113) check(Math.max(e, e / 2) / ArtBudget.sampleSize(e, e / 2) < ArtBudget.MAX_EDGE_PX * 2, "decoded edge bounded " + e);
    }
}
