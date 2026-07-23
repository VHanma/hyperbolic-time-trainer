package com.vhanma.freqsight;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class SymbolDictionary {
    private final Map<String,String> meanings = new LinkedHashMap<>();

    SymbolDictionary() {
        meanings.put("clear jar", "eye / lens / witness");
        meanings.put("three black jars", "absorbers / dark anchors / three");
        meanings.put("smoke", "medium / screen / breath");
        meanings.put("plastic box", "chamber / boundary");
        meanings.put("phone camera", "witness / reader");
        meanings.put("steady light", "source / lamp");
        meanings.put("circle with center", "eye / focus / witness");
        meanings.put("three repeated marks", "three / triad / repeated anchor");
        meanings.put("closed rectangle", "boundary / chamber / enclosure");
        meanings.put("radiating bright mark", "source / lamp / emission");
        meanings.put("inward spiral", "inward flow / gathering / contraction");
        meanings.put("outward spiral", "outward flow / release / expansion");
        meanings.put("cross", "crossing / intersection / plus");
        meanings.put("triangle", "three-point geometry / direction / stability");
        meanings.put("parallel lines", "pair / channel / repetition");
        meanings.put("spiral", "flow / cycle / rotation");
        meanings.put("double spiral", "paired flow / mirrored rotation");
        meanings.put("wave", "oscillation / signal / repetition");
        meanings.put("sine wave", "periodic waveform / tone-like pattern");
        meanings.put("square", "boundary / four-part geometry");
        meanings.put("pentagon", "five-part geometry");
        meanings.put("hexagon", "six-part geometry / lattice");
        meanings.put("star", "radiating geometry / source-like mark");
        meanings.put("ankh-like", "loop-and-stem cluster / ancient-style form");
        meanings.put("rune-like", "angular line cluster / ancient-style form");
        meanings.put("eye-like", "witness / focus / lens");
        meanings.put("arrow-like", "direction / movement vector");
        meanings.put("equals-like", "pair / equivalence / repeated bars");
        meanings.put("plus-like", "intersection / addition / center");
        meanings.put("minus-like", "single bar / reduction / horizontal direction");
    }

    String meaning(String key) { return meanings.getOrDefault(key.toLowerCase(Locale.US), "unmapped symbol cluster"); }

    String userObjectRendering(DataModels.VisualEvent visual) {
        StringBuilder b = new StringBuilder();
        if (visual.shadowScore > 0.18f) append(b, "dark anchors changed");
        if (visual.smokeScore > 0.16f) append(b, "breath-medium flow changed");
        if (visual.reflectionScore > 0.16f) append(b, "lens/source reflection changed");
        if (visual.condensationScore > 0.16f) append(b, "chamber-surface texture changed");
        return b.length() == 0 ? "unmapped environmental pattern" : b.toString();
    }

    private static void append(StringBuilder b, String s) { if (b.length() > 0) b.append("; "); b.append(s); }
}
