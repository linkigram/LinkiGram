#!/usr/bin/env python3
"""Check the LRC.RED / Binimum / Unison / BetterLyrics providers in OnlineLyrics.

Part 1 is a source contract (wiring, latch count, cache version, priorities).
Part 2 compiles the production request methods with Java fakes and feeds them
offline fixtures, so URL building and candidate matching are exercised without
a device, an Android SDK or a network.
"""
import argparse
import os
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source_path = root / 'TMessagesProj/src/main/java/app/exteraless/player/OnlineLyrics.java'
source = source_path.read_text(encoding='utf-8')


def fail(message):
    raise AssertionError(message)


# --- part 1: wiring -------------------------------------------------------
fetch_start = source.index('private static FetchOutcome fetchAllSources(')
fetch_end = source.index('private static java.util.concurrent.Future<?> collectProvider(', fetch_start)
fetch = source[fetch_start:fetch_end]
latch = re.search(r'new CountDownLatch\((\d+)\)', fetch)
if not latch:
    fail('CountDownLatch not found')
providers = re.findall(r'collectProvider\(\(\) -> (request\w+)\(query(?:, [^)]*)?\)', fetch)
if int(latch.group(1)) != len(providers):
    fail('latch %s != %d providers' % (latch.group(1), len(providers)))
for name in ('requestLrcRed', 'requestBinimum', 'requestUnison', 'requestBetterLyrics'):
    if name not in providers:
        fail('%s is not fetched' % name)
    if ('private static Found %s(Query q)' % name) not in source:
        fail('%s is not implemented' % name)
version = re.search(r'CACHE_VERSION = (\d+);', source)
if not version or int(version.group(1)) < 12:
    fail('CACHE_VERSION must invalidate caches written before the new providers')
priority = source[source.index('private static int providerPriority('):source.index('private static void dedupeIdentical(')]
for name in ('LRC_RED_NAME', 'BINIMUM_NAME', 'UNISON_NAME', 'BETTER_LYRICS_NAME'):
    if ('startsWith(%s)' % name) not in priority:
        fail('%s missing from providerPriority' % name)
print('source contract: %d providers, latch %s, cache v%s' % (len(providers), latch.group(1), version.group(1)))
workers = (root / 'TMessagesProj/src/main/java/app/exteraless/player/LyricsWorkers.java').read_text(encoding='utf-8')
pool = re.search(r'create\((\d+), "lyrics-provider"\)', workers)
if not pool or int(pool.group(1)) < len(providers):
    fail('LyricsWorkers pool is smaller than one provider batch; tasks would be rejected')
print('worker pool: %s threads for %d providers' % (pool.group(1), len(providers)))

# --- part 2: production methods against fixtures --------------------------
start = source.index('    private static Found requestLrcRed(')
end = source.index('    private static String wordKey(', start)
methods = source[start:end]

parser = argparse.ArgumentParser()
parser.add_argument('--json-jar', type=Path, required=True)
parser.add_argument('--live-dir', type=Path, help='optional directory of real .ttml responses to structure-check')
args = parser.parse_args()
jar = args.json_jar.resolve()
javac = shutil.which('javac')
java = os.environ.get('JAVA') or (str(Path(javac).with_name('java.exe' if os.name == 'nt' else 'java')) if javac else 'java')

harness = r'''import java.util.*; import org.json.*;
import java.util.regex.Pattern;
class LyricProvidersCheck {
 static final String LRC_RED_SEARCH="https://lrc.red/search.json?q=", LRC_RED_TRACK="https://lrc.red/s/";
 static final String BINIMUM="https://lyrics-api.binimum.org/getLyrics", UNISON="https://unison.betterlyrics.org/lyrics", BETTER_LYRICS="https://api.betterlyrics.org/getLyrics";
 static final String LRC_RED_NAME="LRC.RED", BINIMUM_NAME="Binimum", UNISON_NAME="Unison", BETTER_LYRICS_NAME="BetterLyrics";
 static final String LINKIGRAM_UA="LinkiGram/test";
 static final int MAX_DURATION_DIFF=3, MAX_FALLBACK_DURATION_DIFF=5;
 static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
 static class TextUtils { static boolean isEmpty(String s) { return s==null||s.trim().isEmpty(); } }
 static class Query {
  String title, artist, album, originalTitle; int duration;
  Query(String a,String t,String al,int d){artist=a;title=t;album=al;originalTitle=t;duration=d;}
 }
 static class Found { Lyrics lyrics; Found(Lyrics l){lyrics=l;} }
 static class Lyrics {
  static final int SOURCE_ONLINE=1;
  ArrayList<Line> lines=new ArrayList<>(); String provider; static String lastRaw, lastProvider;
  static class Word { long time;int start,end; }
  static class Line { long time;String text; Line(long t,String s){time=t;text=s;} }
  static Lyrics parse(String raw,int source,String provider){
   if(raw==null) return null;
   lastRaw=raw; lastProvider=provider;
   Lyrics l=new Lyrics(); l.provider=provider; l.lines.add(new Line(1000,"line"));
   return l;
  }
 }
 static final HashMap<String,String> responses = new HashMap<>();
 static String lastUserAgent, lastReferer;
 static String get(String url){ return get(url,null,null); }
 static String get(String url,String referer,String userAgent){
  lastUserAgent=userAgent; lastReferer=referer;
  String body=responses.get(url);
  if(body==null) throw new IllegalStateException("lyrics http 404 "+url);
  return body;
 }
 static String optString(JSONObject o,String key){
  if(o==null||o.isNull(key)) return null;
  String value=o.optString(key,null);
  return value==null||value.isEmpty()?null:value;
 }
''' + methods + r'''
 static String enc(String s) throws Exception { return java.net.URLEncoder.encode(s,"UTF-8").replace("+","%20"); }
 public static void main(String[] args) throws Exception {
  Query q=new Query("Rick Astley","Never Gonna Give You Up","Whenever You Need Somebody",213);
  String search=LRC_RED_SEARCH+enc("Rick Astley Never Gonna Give You Up");
  responses.put(search,"{\"hits\":[" +
    "{\"isrc\":\"WRONG\",\"title\":\"Another Song\",\"artist\":\"Rick Astley\",\"duration\":213}," +
    "{\"isrc\":\"FAR\",\"title\":\"Never Gonna Give You Up (Live)\",\"artist\":\"Rick Astley\",\"duration\":400}," +
    "{\"isrc\":\"GOOD\",\"title\":\"Never Gonna Give You Up (2022 Remaster)\",\"artist\":\"Rick Astley\",\"duration\":213}]}");
  String ttml=LRC_RED_TRACK+"GOOD.ttml";
  responses.put(ttml,"<tt><body><p begin=\"1s\"><span begin=\"1s\">hi</span></p></body></tt>");
  Found found=requestLrcRed(q);
  if(found==null) throw new AssertionError("lrc.red should match the duration-compatible hit");
  if(!ttml.equals("https://lrc.red/s/GOOD.ttml") || Lyrics.lastProvider==null || !Lyrics.lastProvider.equals(LRC_RED_NAME)) throw new AssertionError("lrc.red provider");
  if(!Lyrics.lastRaw.equals("<tt><body><p begin=\"1s\"><span begin=\"1s\">hi</span></p></body></tt>")) throw new AssertionError("lrc.red ttml body");
  if(lastUserAgent==null || !lastUserAgent.equals(LINKIGRAM_UA)) throw new AssertionError("lrc.red must identify LinkiGram");
  if(found.lyrics==null || found.lyrics.provider==null || !found.lyrics.provider.equals(LRC_RED_NAME)) throw new AssertionError("lrc.red wrapper");

  responses.clear();
  responses.put(search,"{\"hits\":[{\"isrc\":\"FAR\",\"title\":\"Never Gonna Give You Up\",\"artist\":\"Rick Astley\",\"duration\":400}]}");
  if(requestLrcRed(q)!=null) throw new AssertionError("lrc.red must reject a far duration");
  responses.clear();
  responses.put(search,"{\"hits\":[{\"isrc\":\"GOOD\",\"title\":\"Never Gonna Give You Up\",\"artist\":\"Rick Astley\",\"duration\":213}]}");
  if(requestLrcRed(q)!=null) throw new AssertionError("lrc.red must fail closed when the ttml is missing");

  Query bin=new Query("Rammstein","Sonne",null,273);
  String binUrl=BINIMUM+"?track="+enc("Sonne")+"&artist="+enc("Rammstein");
  responses.clear();
  responses.put(binUrl,"{\"results\":[" +
    "{\"track_name\":\"Sonne (Single Version)\",\"artist_name\":\"Rammstein\",\"duration\":273,\"lyricsUrl\":\"https://lrc.red/s/A.ttml\"}," +
    "{\"track_name\":\"Sonne\",\"artist_name\":\"Rammstein\",\"duration\":240,\"lyricsUrl\":\"https://lrc.red/s/B.ttml\"}]}");
  responses.put("https://lrc.red/s/A.ttml","<tt/>");
  Found b=requestBinimum(bin);
  if(b==null||!BINIMUM_NAME.equals(b.lyrics.provider)) throw new AssertionError("binimum match");
  if(!b.lyrics.provider.equals(Lyrics.lastProvider)) throw new AssertionError("binimum provider passthrough");
  responses.clear();
  responses.put(binUrl,"{\"results\":[]}");
  if(requestBinimum(bin)!=null) throw new AssertionError("binimum empty results");
  if(requestBinimum(new Query(null,"Sonne",null,273))!=null) throw new AssertionError("binimum needs an artist");

  Query uni=new Query("Radiohead","Creep","Pablo Honey",238);
  String uniUrl=UNISON+"?song="+enc("Creep")+"&artist="+enc("Radiohead")+"&album="+enc("Pablo Honey");
  responses.clear();
  responses.put(uniUrl,"{\"success\":false,\"code\":\"NOT_FOUND\"}");
  if(requestUnison(uni)!=null) throw new AssertionError("unison not found");
  responses.clear();
  responses.put(uniUrl,"{\"success\":true,\"data\":{\"song\":\"Creep\",\"artist\":\"Radiohead\",\"lyrics\":\"<tt/>\",\"format\":\"ttml\"}}");
  Found u=requestUnison(uni);
  if(u==null||!UNISON_NAME.equals(u.lyrics.provider)) throw new AssertionError("unison match");
  responses.clear();
  responses.put(uniUrl,"{\"success\":true,\"data\":{\"song\":\"Creep\",\"artist\":\"Stone Temple Pilots\",\"lyrics\":\"<tt/>\"}}");
  if(requestUnison(uni)!=null) throw new AssertionError("unison must reject a different artist");

  Query bl=new Query("Radiohead","Creep","Pablo Honey",238);
  String blUrl=BETTER_LYRICS+"?s="+enc("Creep")+"&a="+enc("Radiohead")+"&al="+enc("Pablo Honey")+"&d=238";
  responses.clear();
  if(requestBetterLyrics(bl)!=null) throw new AssertionError("betterlyrics 401 must not throw");
  responses.put(blUrl,"{\"ttml\":\"<tt/>\"}");
  Found better=requestBetterLyrics(bl);
  if(better==null||!BETTER_LYRICS_NAME.equals(better.lyrics.provider)) throw new AssertionError("betterlyrics match");

  Query noAlbum=new Query("Queen","Bohemian Rhapsody",null,354);
  String noAlbumUrl=BETTER_LYRICS+"?s="+enc("Bohemian Rhapsody")+"&a="+enc("Queen")+"&d=354";
  responses.clear();
  responses.put(noAlbumUrl,"{\"ttml\":\"<tt/>\"}");
  if(requestBetterLyrics(noAlbum)==null) throw new AssertionError("betterlyrics optional album/duration params");

  if(candidateScore("Creep","Radiohead",238,uni)<0) throw new AssertionError("creep score");
  if(candidateScore("Creep","Radiohead",238,uni)<=candidateScore("Creep (Acoustic)","Radiohead",238,uni)) throw new AssertionError("exact title must win");
  if(candidateScore("Creep","Other Artist",238,uni)>=0) throw new AssertionError("artist mismatch");
  if(candidateScore("Creep","Radiohead",400,uni)>=0) throw new AssertionError("duration reject");
  if(candidateScore("Creep","Radiohead",238,uni)<=candidateScore("Creep","Radiohead",236,uni)) throw new AssertionError("closer duration must win");
  if(!normalize("Rick Astley - Never Gonna Give You Up!").equals("rick astley never gonna give you up")) throw new AssertionError("normalize");
  if(getOptional("missing",null,null)!=null) throw new AssertionError("getOptional swallows 4xx");
  System.out.println("lyrics provider fixtures passed");
 }
}'''

with tempfile.TemporaryDirectory() as tmp:
    path = Path(tmp) / 'LyricProvidersCheck.java'
    path.write_text(harness, encoding='utf-8')
    subprocess.run([javac, '-cp', str(jar), '-d', tmp, str(path)], check=True)
    subprocess.run([java, '-cp', os.pathsep.join([str(jar), tmp]), 'LyricProvidersCheck'], check=True)

# --- part 3: real Lyrics/WordLyricsParser over TTML shapes ----------------
# A tiny StAX-backed stand-in for android.util.Xml, so the production TTML
# parser runs unmodified without an Android SDK or a pull-parser dependency.
# Fixtures copy the structural shapes observed from the live providers; the
# words are synthetic, no lyrics text is stored in the repository.
xml_fake = r'''package org.xmlpull.v1;
public interface XmlPullParser {
 int START_TAG=2, END_TAG=3, TEXT=4, END_DOCUMENT=1, DOCDECL=10;
 String FEATURE_PROCESS_NAMESPACES="http://xmlpull.org/v1/doc/features.html#process-namespaces";
 void setFeature(String name, boolean state) throws XmlPullParserException;
 void setInput(java.io.Reader reader) throws XmlPullParserException;
 int next() throws XmlPullParserException, java.io.IOException;
 String getName();
 String getAttributeValue(String namespace, String name);
 String getText();
 int getDepth();
}'''

xml_exception_fake = r'''package org.xmlpull.v1;
public class XmlPullParserException extends Exception {
 public XmlPullParserException(String message) { super(message); }
}'''

android_xml_fake = r'''package android.util;
import javax.xml.stream.*;
import java.io.Reader;
public final class Xml {
 public static org.xmlpull.v1.XmlPullParser newPullParser() { return new Pull(); }
 static final class Pull implements org.xmlpull.v1.XmlPullParser {
  private XMLStreamReader reader;
  private int depth = 0;
  private int lastDepth = 0;
  public void setFeature(String name, boolean state) { }
  public void setInput(Reader input) throws org.xmlpull.v1.XmlPullParserException {
   try { reader = XMLInputFactory.newInstance().createXMLStreamReader(input); }
   catch (XMLStreamException e) { throw new org.xmlpull.v1.XmlPullParserException(e.toString()); }
  }
  public int next() throws java.io.IOException {
   try {
    while (true) {
     int event = reader.next();
     switch (event) {
      case XMLStreamConstants.START_ELEMENT: depth++; lastDepth = depth; return START_TAG;
      case XMLStreamConstants.END_ELEMENT: lastDepth = depth; depth--; return END_TAG;
      case XMLStreamConstants.CHARACTERS:
      case XMLStreamConstants.CDATA:
      case XMLStreamConstants.SPACE:
      case XMLStreamConstants.ENTITY_REFERENCE: return TEXT;
      case XMLStreamConstants.END_DOCUMENT: return END_DOCUMENT;
      case XMLStreamConstants.DTD: return DOCDECL;
      default: break;
     }
    }
   } catch (XMLStreamException e) { throw new java.io.IOException(e.toString()); }
  }
  public String getName() { return reader.getLocalName(); }
  public String getAttributeValue(String namespace, String name) { return reader.getAttributeValue(namespace, name); }
  public String getText() { return reader.getText(); }
  public int getDepth() { return lastDepth; }
 }
}'''

textutils_fake = r'''package android.text;
public final class TextUtils {
 public static boolean isEmpty(CharSequence value) { return value == null || value.length() == 0; }
}'''

ttml_main = r'''import app.exteraless.player.Lyrics;
public class TtmlCheck {
 static Lyrics parse(String path) throws Exception {
  String text = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)), "UTF-8");
  return Lyrics.parse(text, Lyrics.SOURCE_ONLINE, "test");
 }
 static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
 static void check(String path, String expectedFirst) throws Exception {
  Lyrics lyrics = parse(path);
  require(lyrics != null, path + ": no lyrics parsed");
  require(lyrics.synced, path + ": not synced");
  require(lyrics.lines.size() >= 2, path + ": too few lines");
  require(lyrics.lines.get(0).text.contains(expectedFirst), path + ": first line " + lyrics.lines.get(0).text);
  long previous = -1;
  for (Lyrics.Line line : lyrics.lines) {
   require(line.time >= previous, path + ": line times are not sorted");
   previous = line.time;
   require(!line.words.isEmpty(), path + ": line without word timing: " + line.text);
   for (Lyrics.Word word : line.words) {
    require(word.time > 0, path + ": word without time");
    require(word.start == 0 || word.start > 0 && word.start < word.end && word.end <= line.text.length(), path + ": word range");
   }
  }
 }
 static void checkStructure(String path) throws Exception {
  Lyrics lyrics = parse(path);
  require(lyrics != null, path + ": no lyrics parsed");
  require(lyrics.synced, path + ": not synced");
  require(lyrics.lines.size() >= 2, path + ": too few lines");
  int words = 0;
  long previous = -1;
  for (Lyrics.Line line : lyrics.lines) {
   require(line.time >= previous, path + ": line times are not sorted");
   previous = line.time;
   words += line.words.size();
  }
  require(words >= lyrics.lines.size(), path + ": word timing lost");
  System.out.println(path + ": " + lyrics.lines.size() + " lines, " + words + " words");
 }
 public static void main(String[] args) throws Exception {
  check(args[0], "alpha");
  check(args[1], "bravo");
  check(args[2], "charlie");
  for (int i = 3; i < args.length; i++) checkStructure(args[i]);
  System.out.println("ttml fixtures passed");
 }
}'''

lrc_red_fixture = (
    '<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" '
    'xmlns:lrc="http://lrc.red/lyric-ttml-internal" lrc:timing="Word" xml:lang="en">'
    '<head><metadata><ttm:agent type="person" xml:id="v1"><ttm:name type="full">Tester</ttm:name>'
    '</ttm:agent></metadata></head><body dur="3:33.107"><div begin="18.834" end="34.908" lrc:songPart="Verse">'
    '<p begin="18.834" end="22.088" lrc:key="L1" ttm:agent="v1">'
    '<span begin="18.834" end="19.086">alpha</span> <span begin="19.086" end="19.336">beta</span>'
    '<span ttm:role="x-bg"><span begin="19.336" end="19.600">(ooh</span>'
    '<span begin="19.600" end="19.900">ooh)</span></span></p>'
    '<p begin="22.100" end="25.000" lrc:key="L2" ttm:agent="v1">'
    '<span begin="22.100" end="22.600">gamma</span> <span begin="22.600" end="23.100">delta</span></p>'
    '</div></body></tt>')

better_lyrics_fixture = (
    '<tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" '
    'xmlns:ttm="http://www.w3.org/ns/ttml#metadata" itunes:timing="Word" xml:lang="en">'
    '<head><metadata><ttm:agent type="person" xml:id="v1"><ttm:name type="full">Tester</ttm:name>'
    '</ttm:agent></metadata></head><body dur="3:58.640"><div begin="19.764" end="59.300" itunes:songPart="Verse">'
    '<p begin="0:19.764" end="0:23.000"><span begin="0:19.764" end="0:20.100">bravo</span>'
    '<span begin="0:20.100" end="0:20.500">here</span></p>'
    '<p begin="0:23.000" end="0:26.000"><span begin="0:23.000" end="0:23.500">we</span>'
    '<span begin="0:23.500" end="0:24.000">go</span></p>'
    '</div></body></tt>')

unison_fixture = (
    '<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" '
    'xmlns:ttp="http://www.w3.org/ns/ttml#parameter" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" '
    'ttp:timeBase="media" itunes:timing="Word"><head><metadata><ttm:title>Fixture</ttm:title></metadata></head>'
    '<body dur="4:18.718"><div><p begin="0:21.124" end="0:24.000">'
    '<span begin="0:21.124" end="0:21.700">charlie</span> <span begin="0:21.700" end="0:22.200">knows</span></p>'
    '<p end="0:27.000"><span begin="0:24.500" end="0:25.000">no</span>'
    '<span begin="0:25.000" end="0:25.400">begin</span></p></div></body></tt>')

with tempfile.TemporaryDirectory() as tmp:
    src = Path(tmp) / 'src'
    out = src / 'out'
    out.mkdir(parents=True)
    files = {
        'org/xmlpull/v1/XmlPullParser.java': xml_fake,
        'org/xmlpull/v1/XmlPullParserException.java': xml_exception_fake,
        'android/util/Xml.java': android_xml_fake,
        'android/text/TextUtils.java': textutils_fake,
        'TtmlCheck.java': ttml_main,
    }
    for name, text in files.items():
        p = src / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(text, encoding='utf-8')
    fixtures = []
    for name, text in (('lrc_red.ttml', lrc_red_fixture), ('better_lyrics.ttml', better_lyrics_fixture),
                       ('unison.ttml', unison_fixture)):
        p = src / name
        p.write_text(text, encoding='utf-8')
        fixtures.append(str(p))
    if args.live_dir:
        for p in sorted(args.live_dir.glob('*.ttml')):
            fixtures.append(str(p))
    player = root / 'TMessagesProj/src/main/java/app/exteraless/player'
    subprocess.run([javac, '-cp', str(jar), '-d', str(out),
                    str(player / 'Lyrics.java'), str(player / 'WordLyricsParser.java')]
                   + [str(src / n) for n in files], check=True)
    subprocess.run([java, '-cp', os.pathsep.join([str(jar), str(out)]), 'TtmlCheck'] + fixtures, check=True)