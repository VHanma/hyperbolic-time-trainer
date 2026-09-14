from pathlib import Path

path = Path('app/src/main/java/com/htt/MainActivity.java')
source = path.read_text(encoding='utf-8')


def replace_once(old: str, new: str, label: str) -> None:
    global source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f'{label}: expected exactly 1 match, found {count}')
    source = source.replace(old, new, 1)

replace_once(
    '    private static final int MODE_REPORT = 5;\n',
    '    private static final int MODE_REPORT = 5;\n'
    '    private static final int COMBO_REPETITIONS = 5;\n',
    'repeat constant',
)

replace_once(
    '    private int expectedIndex;\n',
    '    private int expectedIndex;\n'
    '    private int comboRepeatIndex;\n'
    '    private boolean currentRepetitionCompleted;\n',
    'repeat state',
)

start_reset = '''            combosCalled = combosCompleted = 0;\n            strobeView.startProfile((String) visual.getSelectedItem());'''
start_reset_replacement = '''            combosCalled = combosCompleted = 0;\n            currentCombo = null;\n            comboRepeatIndex = 0;\n            currentRepetitionCompleted = false;\n            strobeView.startProfile((String) visual.getSelectedItem());'''
if source.count(start_reset) != 2:
    raise RuntimeError(f'session reset blocks: expected 2 matches, found {source.count(start_reset)}')
source = source.replace(start_reset, start_reset_replacement)

replace_once(
    '''            if (sessionEndsMs > System.currentTimeMillis()) {\n                combosCompleted++;\n                done.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);\n            }''',
    '''            if (sessionEndsMs > System.currentTimeMillis() && currentCombo != null && !currentRepetitionCompleted) {\n                currentRepetitionCompleted = true;\n                combosCompleted++;\n                done.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);\n                if (comboText != null) {\n                    comboText.setText(currentCombo.spoken + "\\n\\nREP " + comboRepeatIndex + " / " + COMBO_REPETITIONS + " COMPLETE\\n" + densitySummary());\n                }\n            }''',
    'manual completion guard',
)

replace_once(
    '''        currentCombo = bank.randomCombo(activeCategory);\n        expectedIndex = 0;\n        combosCalled++;\n        String call = currentCombo.spoken;\n        speech.speakCoach(call);\n        if (whisperEnabled) {\n            handler.postDelayed(() -> {\n                if (sessionEndsMs > System.currentTimeMillis()) speech.speakWhisper(bank.matchedAffirmation(currentCombo));\n            }, 220L);\n        }\n        if (fusion && hudView != null) {\n            hudView.setComboPrompt(call, expectedProgress());\n        } else if (comboText != null) {\n            comboText.setText(call + "\\n\\n" + densitySummary());\n        }\n        handler.postDelayed(() -> callComboLoop(fusion), callIntervalMs);\n''',
    '''        if (currentCombo == null || comboRepeatIndex >= COMBO_REPETITIONS) {\n            currentCombo = bank.randomCombo(activeCategory);\n            comboRepeatIndex = 0;\n        }\n        comboRepeatIndex++;\n        expectedIndex = 0;\n        currentRepetitionCompleted = false;\n        combosCalled++;\n        String call = currentCombo.spoken;\n        String repeatLabel = "REP " + comboRepeatIndex + " / " + COMBO_REPETITIONS;\n        speech.speakCoach("Repeat " + comboRepeatIndex + " of " + COMBO_REPETITIONS + ". " + call);\n        if (whisperEnabled) {\n            handler.postDelayed(() -> {\n                if (sessionEndsMs > System.currentTimeMillis()) speech.speakWhisper(bank.matchedAffirmation(currentCombo));\n            }, 220L);\n        }\n        if (fusion && hudView != null) {\n            hudView.setComboPrompt(call, repeatLabel + "  |  " + expectedProgress());\n        } else if (comboText != null) {\n            comboText.setText(call + "\\n\\n" + repeatLabel + "\\n" + densitySummary());\n        }\n        handler.postDelayed(() -> callComboLoop(fusion), callIntervalMs);\n''',
    'five-repeat combo loop',
)

replace_once(
    '''        if (currentMode == MODE_FUSION && currentCombo != null && !currentCombo.expectedPunches.isEmpty()) {\n            String expected = currentCombo.expectedPunches.get(Math.min(expectedIndex, currentCombo.expectedPunches.size() - 1));\n            if (strikeMatches(expected, r.punchType)) {\n                expectedIndex++;\n                if (expectedIndex >= currentCombo.expectedPunches.size()) {\n                    combosCompleted++;\n                    expectedIndex = currentCombo.expectedPunches.size();\n                }\n            }\n            hudView.setComboPrompt(currentCombo.spoken, expectedProgress());\n        }\n''',
    '''        if (currentMode == MODE_FUSION && currentCombo != null && !currentCombo.expectedPunches.isEmpty()) {\n            if (!currentRepetitionCompleted) {\n                String expected = currentCombo.expectedPunches.get(Math.min(expectedIndex, currentCombo.expectedPunches.size() - 1));\n                if (strikeMatches(expected, r.punchType)) {\n                    expectedIndex++;\n                    if (expectedIndex >= currentCombo.expectedPunches.size()) {\n                        currentRepetitionCompleted = true;\n                        combosCompleted++;\n                        expectedIndex = currentCombo.expectedPunches.size();\n                    }\n                }\n            }\n            hudView.setComboPrompt(currentCombo.spoken, "REP " + comboRepeatIndex + " / " + COMBO_REPETITIONS + "  |  " + expectedProgress());\n        }\n''',
    'fusion duplicate-count guard',
)

path.write_text(source, encoding='utf-8')
print('Applied fixed five-repetition combo cycle.')
