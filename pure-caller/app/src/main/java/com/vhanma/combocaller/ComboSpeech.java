package com.vhanma.combocaller;

import java.util.ArrayList;
import java.util.List;

final class ComboSpeech {
    private ComboSpeech() {}

    static String speak(String combo, int id, boolean expanded) {
        if (id > 400 || !expanded) return literal(combo);
        return expand(combo);
    }

    private static String literal(String s) {
        return clean(s.replace("/L/", " slip left ")
                .replace("/R/", " slip right ")
                .replace("pivote", " pivot ")
                .replace("-", ", ")
                .replace("{", " roll ")
                .replace("}", " ")
                .replace("1", " one ").replace("2", " two ")
                .replace("3", " three ").replace("4", " four ")
                .replace("5", " five ").replace("6", " six "));
    }

    private static String expand(String code) {
        String n = code.replace("/L/", "-SL-")
                .replace("/R/", "-SR-")
                .replace("pivote", "-PIVOT-")
                .replace("{", "-ROLL-").replace("}", "-")
                .replaceAll("-+", "-");
        List<String> out = new ArrayList<>();
        for (String token : n.split("-")) parse(token.trim(), out);
        return clean(join(out));
    }

    private static void parse(String t, List<String> out) {
        if (t.isEmpty()) return;
        if (t.equals("SL")) { out.add("slip left"); return; }
        if (t.equals("SR")) { out.add("slip right"); return; }
        if (t.equals("ROLL")) { out.add("roll"); return; }
        if (t.equals("PIVOT")) { out.add("pivot"); return; }
        if (t.equals("pb")) { out.add("pull back"); return; }
        if (t.equals("bs")) { out.add("back step"); return; }
        if (t.equals("rs")) { out.add("right step"); return; }
        if (t.equals("ls")) { out.add("left step"); return; }
        if (t.equals("sp")) { out.add("switch step"); return; }
        int i = 0;
        while (i < t.length()) {
            if (t.startsWith("pb", i)) { out.add("pull back"); i += 2; continue; }
            if (t.startsWith("pccw", i)) { out.add("counterclockwise pivot"); i += 4; continue; }
            if (t.startsWith("bp", i)) { out.add("rear foot pivot"); i += 2; continue; }
            if (t.startsWith("bs", i)) { out.add("back step"); i += 2; continue; }
            if (t.startsWith("rs", i)) { out.add("right step"); i += 2; continue; }
            if (t.startsWith("ls", i)) { out.add("left step"); i += 2; continue; }
            if (t.startsWith("sp", i)) { out.add("switch step"); i += 2; continue; }
            char c = t.charAt(i);
            if (c >= '1' && c <= '6') {
                String move = move(c); i++;
                boolean body = false, feint = false, tap = false, pivot = false;
                while (i < t.length()) {
                    char m = t.charAt(i);
                    if (m == 'b') body = true;
                    else if (m == 'f') feint = true;
                    else if (m == 't') tap = true;
                    else if (m == 'p') pivot = true;
                    else break;
                    i++;
                }
                if (body) move += " to body";
                if (feint) move = "feint " + move;
                if (tap) move = "light " + move;
                out.add(move);
                if (pivot) out.add("lead foot pivot");
            } else { i++; }
        }
    }

    private static String move(char c) {
        switch (c) {
            case '1': return "jab";
            case '2': return "cross";
            case '3': return "lead hook";
            case '4': return "rear hook";
            case '5': return "lead uppercut";
            case '6': return "rear uppercut";
            default: return String.valueOf(c);
        }
    }

    private static String join(List<String> list) {
        StringBuilder b = new StringBuilder();
        for (String s : list) { if (b.length() > 0) b.append(", "); b.append(s); }
        return b.toString();
    }

    private static String clean(String s) {
        return s.replaceAll("\\s+", " ").replaceAll("\\s*,\\s*", ", ").trim();
    }
}
