package tatar.eljah.recorder;

/**
 * Runtime-only bridge for optional Audiveris ecosystem dependencies.
 *
 * We do not import Audiveris classes directly here to keep Java 8 / Android compatibility.
 * If dependency profile is enabled and jars are present at runtime, this bridge reports availability.
 */
final class AudiverisDependencyBridge {
    private static final String PROXYMUSIC_SCORE_PARTWISE = "org.audiveris.proxymusic.ScorePartwise";

    private AudiverisDependencyBridge() {
    }

    static boolean isProxyMusicAvailable() {
        try {
            Class.forName(PROXYMUSIC_SCORE_PARTWISE);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static String runtimeFlavorSuffix() {
        return isProxyMusicAvailable() ? "+audiveris-dep" : "+builtin";
    }
}
