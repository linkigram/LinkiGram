"""Exercise production bitmap/render-node routing and capture cache invalidation with fakes."""
from pathlib import Path
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
text = (root / "TMessagesProj/src/main/java/org/telegram/ui/Components/blur3/drawable/BlurredBackgroundDrawable.java").read_text()
start = text.index("    private Impl resolveImpl() {")
end = text.index("    /** The implementation drawing", start)
routing = text[start:end]
start = text.index("        private void syncBitmapSource() {")
end = text.index("        /** How much the lens", start)
cache = text[start:end]
assert "syncBitmapSource();\n            if (!renderNode.hasDisplayList() || invalidated)" in text
java = r'''
public class Check {
 static class Build {static class VERSION {static int SDK_INT=36;}static class VERSION_CODES {static final int Q=29;}}
 interface GlassSource {}
 static class GlassSourceColor implements GlassSource {}
 static class GlassSourceBitmap implements GlassSource {Bitmap b;Matrix m=new Matrix();Bitmap getBitmap(){return b;}Matrix getMatrix(){return m;}}
 static class Bitmap {int gen=1;boolean recycled;int getGenerationId(){return gen;}boolean isRecycled(){return recycled;}}
 static class Matrix {int value;void set(Matrix m){value=m.value;}public boolean equals(Object o){return o instanceof Matrix&&value==((Matrix)o).value;}}
 static class Color {static int alpha(int c){return c>>>24;}}
 static class BlurredBackgroundDrawableViewFactory {interface DrawableOwner extends GlassSource {}}
 static class Impl {boolean liquid;void setLiquidGlassEnabled(boolean v){liquid=v;}void discard(){}}
 static class ImplColor extends Impl {}
 static class ImplBitmap extends Impl {}
 GlassSource source,resolvedFor;boolean resolvedOpaque,implInvalidated=true,liquid=true;int backgroundColor=0x44000000;
 Impl impl,renderNodeImpl;ImplColor colorImpl=new ImplColor();ImplBitmap bitmapImpl=new ImplBitmap();
 Impl createRenderNodeImpl(){return new Impl();}void invalidateDisplayList(){}
 GlassSource getUnwrappedSource(){return source;}GlassSource pickSource(){return source;}
 boolean isLiquidGlassEffectEnabled(){return liquid;}
 Impl directImpl(GlassSource s){return s instanceof GlassSourceColor?colorImpl:bitmapImpl;}
 Bitmap recordedBitmap;int recordedBitmapGeneration;Matrix recordedBitmapMatrix=new Matrix();boolean invalidated;
ROUTING
CACHE
 static int checks;static void check(boolean b){if(!b)throw new AssertionError("check "+checks);checks++;}
 public static void main(String[] args){
  Check c=new Check();GlassSourceBitmap s=new GlassSourceBitmap();s.b=new Bitmap();c.source=s;
  check(c.resolveImpl()==c.renderNodeImpl&&c.renderNodeImpl.liquid);
  c.liquid=false;c.implInvalidated=true;check(c.resolveImpl()==c.bitmapImpl&&!c.renderNodeImpl.liquid);
  c.liquid=true;c.implInvalidated=true;Build.VERSION.SDK_INT=32;check(c.resolveImpl()==c.bitmapImpl);
  Build.VERSION.SDK_INT=36;c.source=new GlassSourceColor();c.implInvalidated=true;check(c.resolveImpl()==c.colorImpl);
  c.source=s;c.syncBitmapSource();check(c.invalidated);c.invalidated=false;
  c.syncBitmapSource();check(!c.invalidated);s.b.gen++;c.syncBitmapSource();check(c.invalidated);c.invalidated=false;
  s.m.value++;c.syncBitmapSource();check(c.invalidated);c.invalidated=false;
  s.b=new Bitmap();c.syncBitmapSource();check(c.invalidated);c.invalidated=false;
  s.b.recycled=true;c.syncBitmapSource();check(c.invalidated);c.invalidated=false;
  s.b=null;c.syncBitmapSource();check(c.invalidated);c.invalidated=false;
  c.syncBitmapSource();check(!c.invalidated);
  System.out.println("PASS: "+checks+" bitmap glass routing/cache checks; fallback, new/reused/recycled capture, transform");
 }
}
'''.replace("ROUTING", routing).replace("CACHE", cache)
with tempfile.TemporaryDirectory(prefix="bitmap-glass-") as tmp:
    file = Path(tmp) / "Check.java"
    file.write_text(java)
    subprocess.run([shutil.which("javac"), "-d", tmp, str(file)], check=True)
    subprocess.run([shutil.which("java"), "-cp", tmp, "Check"], check=True)
