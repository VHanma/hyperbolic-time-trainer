from pathlib import Path

path = Path("app/src/main/java/com/htt/MainActivity.java")
text = path.read_text(encoding="utf-8")

old_category = 'Spinner category = spinner(bank.affirmationCategories().toArray(new String[0]));'
new_category = 'Spinner category = spinner(new String[]{"All combat messages"});'
old_message = 'String message = bank.randomAffirmation(category, fullSourceAffirmations);'
new_message = 'String message = bank.randomAffirmation(fullSourceAffirmations);'

if old_category not in text:
    raise RuntimeError("Passive category API call was not found")
if old_message not in text:
    raise RuntimeError("Passive affirmation API call was not found")

text = text.replace(old_category, new_category, 1)
text = text.replace(old_message, new_message, 1)
path.write_text(text, encoding="utf-8")
print("Aligned Live Forge passive mode with ComboBank API")
