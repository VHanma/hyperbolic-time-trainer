package com.vhanma.freqsight;

public enum FilterMode {
    RAW("Raw camera"),
    GRAYSCALE("Grayscale"),
    NEGATIVE("Negative"),
    HIGH_CONTRAST("High contrast"),
    GAMMA_LOW("Gamma lift"),
    GAMMA_HIGH("Gamma crush"),
    SOBEL("Sobel edges"),
    LAPLACIAN("Laplacian edges"),
    ADAPTIVE_THRESHOLD("Adaptive threshold"),
    BINARY("Binary threshold"),
    DIFFERENCE("Difference map"),
    DIFFERENCE_HEAT("Difference heat"),
    SMOKE_ENHANCE("Smoke enhancement"),
    SHADOW_ISOLATE("Shadow isolation"),
    REFLECTION_ISOLATE("Reflection isolation"),
    CONDENSATION("Condensation texture"),
    RED_CHANNEL("Red channel"),
    GREEN_CHANNEL("Green channel"),
    BLUE_CHANNEL("Blue channel"),
    SATURATION("Saturation view"),
    LOW_LIGHT("Low-light recovery"),
    TEMPORAL_AVERAGE("Temporal average"),
    MOTION_TRAIL("Motion trails"),
    GLYPH_EDGE("Glyph extraction"),
    GRID_REGIONS("Evidence grid");

    public final String label;
    FilterMode(String label) { this.label = label; }
}
