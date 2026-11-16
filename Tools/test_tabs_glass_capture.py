"""Run the production bottom-bar capture callback against lifecycle fakes.

This checks invalidation routing, not Android RenderNode rendering.
"""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


root = Path(__file__).resolve().parents[1]
source = (root / "TMessagesProj/src/main/java/org/telegram/ui/MainTabsActivity.java").read_text()
start = source.index("    private void blur3_onFragmentGlassCaptured() {")
end = source.index("\n    /**", start)
method = source[start:end]
dialogs = (root / "TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java").read_text()
start = dialogs.index("    @Override\n    public void setParentGlass(")
end = dialogs.index("\n    private final ArrayList<RectF> iBlur3Positions", start)
subscription = dialogs[start:end]
capture = dialogs[dialogs.index("    private void invalidateBlurCaptures(int flags)"):]
assert capture.index("onParentGlassCaptured.run();") > capture.index("scrollableViewNoiseSuppressor.invalidateResultRenderNodes(")
java = r"""
public class Check {
    @interface Nullable {}
    static class RectF {}
    static class Parent { public void setParentGlass(RectF position, Runnable callback) {} }
    static class Engine { int resets; void invalidate() { resets++; } }
    static class DialogsProbe extends Parent {
        Runnable onParentGlassCaptured;
        Engine glassEngine = new Engine();
        View fragmentView = new View();
SUBSCRIPTION
    }
    static class Build {
        static class VERSION { static int SDK_INT = 36; }
        static class VERSION_CODES { static final int S = 31; }
    }
    static class Source { int resets; void invalidateRenderer() { resets++; } }
    static class Background {
        boolean stale = true;
        int redraws;
        void invalidateDisplayList() { stale = false; }
        void invalidateSelf() {
            if (stale) throw new AssertionError("redraw requested before clearing cached fill");
            redraws++;
        }
    }
    static class View { int redraws; void invalidate() { redraws++; } }
    Source iBlur3SourceTabGlass = new Source(), iBlur3SourceTabFrosted = new Source();
    Background tabsViewBackground = new Background();
    View tabsView = new View();
    int sourceUpdates;
    void blur3_invalidateBlur() {
        if (iBlur3SourceTabGlass.resets == 0) throw new AssertionError("source not invalidated");
        sourceUpdates++;
    }
    static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    public static void main(String[] args) {
        Check c = new Check();
        c.blur3_onFragmentGlassCaptured();
        check(c.iBlur3SourceTabGlass.resets == 1 && c.iBlur3SourceTabFrosted.resets == 1);
        check(c.sourceUpdates == 1 && !c.tabsViewBackground.stale);
        check(c.tabsViewBackground.redraws == 1 && c.tabsView.redraws == 1);
        c.tabsViewBackground.stale = true;
        c.blur3_onFragmentGlassCaptured();
        check(!c.tabsViewBackground.stale && c.tabsView.redraws == 2);
        c.tabsViewBackground = null;
        c.tabsView = null;
        c.blur3_onFragmentGlassCaptured();
        check(c.sourceUpdates == 3);
        c.iBlur3SourceTabGlass = null;
        c.blur3_onFragmentGlassCaptured();
        check(c.sourceUpdates == 3);
        Build.VERSION.SDK_INT = 24;
        c.blur3_onFragmentGlassCaptured();
        check(c.sourceUpdates == 3);
        DialogsProbe dialogs = new DialogsProbe();
        Runnable callback = () -> {};
        dialogs.setParentGlass(new RectF(), callback);
        check(dialogs.onParentGlassCaptured == callback && dialogs.glassEngine.resets == 1);
        check(dialogs.fragmentView.redraws == 1);
        dialogs.setParentGlass(new RectF(), callback);
        check(dialogs.glassEngine.resets == 1); // no capture/redraw feedback loop
        dialogs.setParentGlass(null, callback);
        check(dialogs.onParentGlassCaptured == null && dialogs.glassEngine.resets == 2);
        dialogs.fragmentView = null;
        dialogs.setParentGlass(new RectF(), callback);
        check(dialogs.glassEngine.resets == 3);
        System.out.println("PASS: capture invalidates cached bar and schedules redraw; repeat/null/old SDK");
        System.out.println("PASS: Dialogs subscribes its custom capture pipeline once, clears callback, handles missing view");
    }
METHOD
}
""".replace("METHOD", method).replace("SUBSCRIPTION", subscription)
home = os.environ.get("JAVA_HOME")
javac = str(Path(home) / "bin/javac") if home else shutil.which("javac")
runtime = str(Path(home) / "bin/java") if home else shutil.which("java")
with tempfile.TemporaryDirectory(prefix="tabs-capture-") as tmp:
    path = Path(tmp) / "Check.java"
    path.write_text(java)
    subprocess.run([javac, str(path)], check=True)
    subprocess.run([runtime, "-cp", tmp, "Check"], check=True)
