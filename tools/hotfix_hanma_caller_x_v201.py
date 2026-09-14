from pathlib import Path

root = Path('hanma-caller-x')
java = root / 'app/src/main/java/com/vhanma/hanmacallerx/MainActivity.java'
manifest = root / 'app/src/main/AndroidManifest.xml'
gradle = root / 'app/build.gradle'

s = java.read_text(encoding='utf-8')
s = s.replace(
    '        tts = new TextToSpeech(this, this);\n        loadAllDrills();\n        showSetup();',
    '        loadAllDrills();\n        showSetup();\n        handler.post(this::initTtsSafely);'
)
old_on_init = '''    @Override public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true;
            tts.setLanguage(Locale.US);
            tts.setSpeechRate(speechRate);
            tts.setPitch(1.0f);
        }
    }
'''
new_on_init = '''    private void initTtsSafely() {
        if (isFinishing() || isDestroyed() || tts != null) return;
        try {
            tts = new TextToSpeech(getApplicationContext(), this);
        } catch (Throwable error) {
            ttsReady = false;
            Toast.makeText(this, "Voice engine unavailable. Change Android Text-to-speech settings, then restart.", Toast.LENGTH_LONG).show();
        }
    }

    @Override public void onInit(int status) {
        handler.post(() -> finishTtsInit(status));
    }

    private void finishTtsInit(int status) {
        if (status != TextToSpeech.SUCCESS || tts == null || isFinishing() || isDestroyed()) {
            ttsReady = false;
            return;
        }
        try {
            int languageResult = tts.setLanguage(Locale.US);
            tts.setSpeechRate(speechRate);
            tts.setPitch(1.0f);
            ttsReady = languageResult != TextToSpeech.LANG_MISSING_DATA
                    && languageResult != TextToSpeech.LANG_NOT_SUPPORTED;
            if (!ttsReady) Toast.makeText(this, "English voice data is missing or unsupported.", Toast.LENGTH_LONG).show();
        } catch (Throwable error) {
            ttsReady = false;
            Toast.makeText(this, "Voice engine failed to initialize.", Toast.LENGTH_LONG).show();
        }
    }
'''
if old_on_init not in s:
    raise SystemExit('Expected TextToSpeech onInit block was not found')
s = s.replace(old_on_init, new_on_init)

old_compute = '                seeds.computeIfAbsent(d.library, k -> new ArrayList<>()).add(d);'
new_compute = '''                List<Drill> group = seeds.get(d.library);
                if (group == null) {
                    group = new ArrayList<>();
                    seeds.put(d.library, group);
                }
                group.add(d);'''
if old_compute not in s:
    raise SystemExit('Expected computeIfAbsent call was not found')
s = s.replace(old_compute, new_compute)

old_default = '            libraryCounts.put(d.library, libraryCounts.getOrDefault(d.library, 0) + 1);'
new_default = '''            Integer previous = libraryCounts.get(d.library);
            libraryCounts.put(d.library, previous == null ? 1 : previous + 1);'''
if old_default not in s:
    raise SystemExit('Expected getOrDefault call was not found')
s = s.replace(old_default, new_default)

s = s.replace(
    '        if (!ttsReady) {\n            Toast.makeText(this, "Voice engine is still loading.", Toast.LENGTH_SHORT).show();',
    '        if (!ttsReady || tts == null) {\n            Toast.makeText(this, "Voice engine is still loading or unavailable.", Toast.LENGTH_SHORT).show();'
)
java.write_text(s, encoding='utf-8')

m = manifest.read_text(encoding='utf-8')
if 'android.intent.action.TTS_SERVICE' not in m:
    m = m.replace(
        '<manifest xmlns:android="http://schemas.android.com/apk/res/android">',
        '<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <queries>\n'
        '        <intent>\n'
        '            <action android:name="android.intent.action.TTS_SERVICE" />\n'
        '        </intent>\n'
        '    </queries>'
    )
manifest.write_text(m, encoding='utf-8')

g = gradle.read_text(encoding='utf-8')
g = g.replace('versionCode 20', 'versionCode 21')
g = g.replace("versionName '2.0-HanmaArsenal'", "versionName '2.0.1-Hotfix'")
gradle.write_text(g, encoding='utf-8')

for needle, haystack in [
    ('initTtsSafely', s),
    ('handler.post(() -> finishTtsInit(status))', s),
    ('android.intent.action.TTS_SERVICE', m),
    ('versionCode 21', g),
]:
    if needle not in haystack:
        raise SystemExit(f'Hotfix verification failed: {needle}')
if 'computeIfAbsent' in s or 'getOrDefault' in s:
    raise SystemExit('API 24 Map methods remain in the minSdk 23 launch path')
print('Hanma Caller X v2.0.1 startup hotfix applied')
