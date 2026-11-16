"""Execute production delta selection/download/fallback methods with Android/HTTP fakes."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

from make_apk_delta import make_patch


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--json-jar", required=True)
    args = p.parse_args()
    root = Path(__file__).resolve().parents[1]
    directory = root / "TMessagesProj/src/main/java/app/nimarkogram/messenger/updater"
    source = (directory / "NimarkoUpdater.java").read_text()
    methods = source[source.index("    private static JSONObject releaseAsset("):source.index("    private static HttpURLConnection openApkConnection(String link) throws Exception")]
    files = {
        "android/content/Context.java": '''package android.content; import java.io.*; public class Context {
public android.content.pm.ApplicationInfo info=new android.content.pm.ApplicationInfo(); public File cache;
public android.content.pm.ApplicationInfo getApplicationInfo(){return info;}
public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();}
public File getCacheDir(){return cache;} }''',
        "android/content/pm/ApplicationInfo.java": "package android.content.pm; public class ApplicationInfo {public String sourceDir; public String[] splitSourceDirs;}",
        "android/content/pm/PackageInfo.java": 'package android.content.pm; public class PackageInfo {public long code=2; public String versionName="2";}',
        "android/content/pm/PackageManager.java": "package android.content.pm; public class PackageManager { public PackageInfo getPackageArchiveInfo(String p,int f){return new PackageInfo();} }",
        "androidx/core/content/pm/PackageInfoCompat.java": "package androidx.core.content.pm; public class PackageInfoCompat {public static long getLongVersionCode(android.content.pm.PackageInfo p){return p.code;}}",
        "Check.java": r'''
package app.nimarkogram.messenger.updater;
import android.content.Context; import org.json.*; import java.io.*; import java.net.*; import java.util.*;
public class Check {
 static final long MAX_APK_BYTES=ApkDelta.MAX_BYTES; static final int MAX_RESPONSE_BYTES=2*1024*1024;
 static final java.util.regex.Pattern SHA256_PATTERN=java.util.regex.Pattern.compile("[0-9a-fA-F]{64}");
 static JSONObject deltaRelease; static String deltaApkUrl="https://test/full",version="2",expectedSha256;
 static long expectedSizeBytes; static int versionCode,downloadGeneration=7,dlRealProgress,dlShownProgress,lastNotifProgress;
 static boolean downloadCanceled,downloadPaused,badSigner; static HttpURLConnection activeConnection;
 static final Object downloadBindingLock=new Object();
 static byte[] manifest,patch; static String mode="ok"; static int patchRequests;
 static class FileLog {static void d(String s){} static void e(Exception e){} }
 static class NimarkoUpdateConfig {static void setPausedDownloadSha256(String s){} static void setUpdateDownloadingProgress(int p){} }
 static int getCurrentVersionCode(){return 1;}
 static void showProgressNotification(Context c,int p){}
 static String trustedSignerFingerprint(File f){return badSigner?null:"trusted";}
 static String sha256OfFile(File f) throws Exception {return hex(java.security.MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(f.toPath())));}
 static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format("%02x",v&255));return s.toString();}
 static boolean isHttps(String s){return s.startsWith("https://");}
 static HttpURLConnection openApkConnection(String u) throws Exception{return openApkConnection(u,0);}
 static HttpURLConnection openApkConnection(String u,long offset) throws Exception {
  if(!isHttps(u))throw new IOException();
  byte[] data=u.endsWith("manifest")?manifest:patch;
  if(!u.endsWith("manifest"))patchRequests++;
  final long start=offset; final byte[] bytes=data;
  if(mode.equals("http"))throw new IOException("HTTP 404");
  return new HttpURLConnection(new URL(u)) {
   public void disconnect(){} public void connect(){} public boolean usingProxy(){return false;}
   public int getResponseCode(){return start>0?206:200;}
   public String getHeaderField(String n){return mode.equals("range")?"bytes 0-1/2":"bytes "+start+"-"+(bytes.length-1)+"/"+bytes.length;}
   public InputStream getInputStream(){
    return new ByteArrayInputStream(bytes,(int)start,bytes.length-(int)start) {
     int reads;
     public synchronized int read(byte[] b,int o,int l){
      int n=super.read(b,o,l);
      if(!u.endsWith("manifest") && ++reads>=2) {
       if(mode.equals("pause"))downloadPaused=true;
       if(mode.equals("cancel"))downloadCanceled=true;
      }
      return n;
     }
    };
   }
  };
 }
METHODS
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static JSONObject asset(String name,String url,long size){return new JSONObject().put("name",name).put("browser_download_url",url).put("size",size);}
 public static void main(String[] args) throws Exception {
  File base=new File(args[0]),patchInput=new File(args[1]),target=new File(args[2]),folder=new File(args[3]);
  Context context=new Context(); context.info.sourceDir=base.getPath(); context.cache=folder;
  patch=java.nio.file.Files.readAllBytes(patchInput.toPath()); expectedSizeBytes=target.length();
  JSONObject entry=new JSONObject().put("name","patch").put("size",patch.length).put("sha256",sha256OfFile(patchInput)).put("fromSize",base.length()).put("fromSha256",sha256OfFile(base));
  JSONObject metadata=new JSONObject().put("format","linkigram-delta").put("version",1)
   .put("target",new JSONObject().put("name","full").put("size",target.length()).put("sha256",sha256OfFile(target)).put("versionName","2").put("versionCode",2))
   .put("patches",new JSONArray().put(entry));
  manifest=metadata.toString().getBytes("UTF-8");
  deltaRelease=new JSONObject().put("assets",new JSONArray().put(asset("linkigram-delta.json","https://test/manifest",manifest.length)).put(asset("full",deltaApkUrl,target.length())).put(asset("patch","https://test/patch",patch.length)));
  File out=new File(folder,"update.apk");
  check(tryDeltaUpdate(context,deltaApkUrl,out,7),"delta not selected");
  check(sha256OfFile(out).equals(sha256OfFile(target)),"wrong restored target"); out.delete();
  expectedSha256=null; badSigner=true;
  check(!tryDeltaUpdate(context,deltaApkUrl,out,7)&&!out.exists(),"bad signer must fall back"); badSigner=false;
  mode="http"; check(!tryDeltaUpdate(context,deltaApkUrl,out,7),"404 must fall back"); mode="ok";
  context.info.splitSourceDirs=new String[]{"split.apk"}; int before=patchRequests;
  check(!tryDeltaUpdate(context,deltaApkUrl,out,7)&&patchRequests==before,"splits must fall back");context.info.splitSourceDirs=null;
  check(!tryDeltaUpdate(context,"https://test/other",out,7),"metadata bound to another APK");
  expectedSha256="0".repeat(64); check(!tryDeltaUpdate(context,deltaApkUrl,out,7),"target hash conflict"); expectedSha256=null;
  metadata.getJSONObject("target").put("versionCode",1);manifest=metadata.toString().getBytes("UTF-8");
  check(!tryDeltaUpdate(context,deltaApkUrl,out,7),"rollback must fall back");
  metadata.getJSONObject("target").put("versionCode",2);manifest=metadata.toString().getBytes("UTF-8");
  byte[] good=patch;patch=patch.clone();patch[patch.length/2]^=1;
  check(!tryDeltaUpdate(context,deltaApkUrl,out,7)&&!out.exists(),"corrupt patch must fall back");patch=good;
  mode="pause";
  try {tryDeltaUpdate(context,deltaApkUrl,out,7);throw new AssertionError("pause swallowed");}catch(InterruptedIOException expected){}
  check(!out.exists(),"pause created candidate");downloadPaused=false;mode="ok";
  check(new File(folder,"ota-"+entry.getString("sha256")+".lgdelta").length()>0,"pause did not preserve patch bytes");
  check(tryDeltaUpdate(context,deltaApkUrl,out,7),"resume failed");out.delete();
  mode="cancel";
  try {tryDeltaUpdate(context,deltaApkUrl,out,7);throw new AssertionError("cancel swallowed");}catch(InterruptedIOException expected){}
  check(!out.exists(),"cancel created candidate");downloadCanceled=false;mode="ok";
  check(!new File(folder,"ota-"+entry.getString("sha256")+".lgdelta").exists(),"cancel did not remove patch bytes");
  File cached=new File(folder,"partial");java.nio.file.Files.write(cached.toPath(),new byte[]{1,2});
  mode="range";try {downloadDeltaFile(context,"https://test/patch",cached,patch.length,7);throw new AssertionError("bad range accepted");}catch(IOException expected){}
  check(!new File(folder,"delta-result.tmp").exists(),"temporary reconstruction leaked");
  System.out.println("PASS: production delta updater selection, reconstruction, fallback, signer, rollback, hash, split, pause/resume, cancel, range");
 }
}
'''.replace("METHODS", methods),
    }
    with tempfile.TemporaryDirectory(prefix="delta-updater-") as temp:
        folder = Path(temp)
        for name, content in files.items():
            file = folder / name
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text(content)
        import random
        base = random.Random(37).randbytes(1024 * 1024)
        target = base[:10000] + random.Random(38).randbytes(100000) + base[10000:]
        (folder / "base").write_bytes(base)
        (folder / "target").write_bytes(target)
        make_patch(base, target, folder / "patch")
        home = os.environ.get("JAVA_HOME")
        javac = str(Path(home) / "bin/javac") if home else shutil.which("javac")
        java = str(Path(home) / "bin/java") if home else shutil.which("java")
        subprocess.run([javac, "-cp", args.json_jar, "-d", temp, *map(str, folder.rglob("*.java")), str(directory / "ApkDelta.java")], check=True)
        cache = folder / "cache"
        cache.mkdir()
        subprocess.run([java, "-cp", temp + os.pathsep + args.json_jar, "app.nimarkogram.messenger.updater.Check",
                        str(folder / "base"), str(folder / "patch"), str(folder / "target"), str(cache)], check=True)


if __name__ == "__main__":
    main()
