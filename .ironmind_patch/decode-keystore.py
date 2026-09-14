from pathlib import Path
import base64
import hashlib
import re

source = Path('.ironmind_patch/ironmind-v4.keystore.b64').read_text(encoding='utf-8')
clean = re.sub(r'[^A-Za-z0-9+/=]', '', source)
clean += '=' * ((4 - len(clean) % 4) % 4)
raw = base64.b64decode(clean, validate=False)
Path('ironmind-v4.keystore').write_bytes(raw)
print(f'Decoded signing keystore: {len(raw)} bytes')
print(f'SHA-256: {hashlib.sha256(raw).hexdigest()}')
