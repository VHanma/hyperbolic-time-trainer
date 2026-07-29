from pathlib import Path
import base64
import gzip

root = Path(__file__).resolve().parents[1]
app = root / "hanma-caller-x"
parts_dir = root / "hanma-caller-x-v210"
java = app / "app/src/main/java/com/vhanma/hanmacallerx/MainActivity.java"
gradle = app / "app/build.gradle"

parts = sorted(parts_dir.glob("payload.part*"))
if len(parts) != 7:
    raise SystemExit(f"Expected 7 context-engine payload parts, found {len(parts)}")

payload = "".join(p.read_text(encoding="utf-8").strip() for p in parts)
source = gzip.decompress(base64.b64decode(payload)).decode("utf-8")

old_combo_details = 'call.details = metadata + "\\n\\nENTRY: " + d.setup + "\\nEXIT: " + c.exit + "\\nSTYLE INTENT: " + styleFocus(d.library);'
new_combo_details = 'call.details = metadata + "\\n\\nCOMBINATION: " + d.sequence + "\\nENTRY: " + d.setup + "\\nEXIT: " + c.exit + "\\nSTYLE INTENT: " + styleFocus(d.library);'
if old_combo_details not in source:
    raise SystemExit("Combo Caller details block was not found")
source = source.replace(old_combo_details, new_combo_details)
java.write_text(source, encoding="utf-8")

g = gradle.read_text(encoding="utf-8")
g = g.replace("versionCode 22", "versionCode 23")
g = g.replace(
    "versionName '2.0.2-StagedStartup'",
    "versionName '2.1.0-ContextEngine'",
)
gradle.write_text(g, encoding="utf-8")

checks = [
    "private CombatContext buildContext",
    "private ModeCall composeModeCall",
    "private Drill chooseDrillForMode",
    "SITUATION: ",
    "COMBINATION: ",
    "No opponent return. Own your balance after impact",
    "OPPONENT STATE:",
    "WHY IT FITS:",
    "ENTRY STYLE:",
    "smoke_mode",
]
for needle in checks:
    if needle not in source:
        raise SystemExit("Context engine verification failed: " + needle)

if "versionCode 23" not in g or "2.1.0-ContextEngine" not in g:
    raise SystemExit("Version update failed")

print("Hanma Caller X v2.1.0 context engine applied")
