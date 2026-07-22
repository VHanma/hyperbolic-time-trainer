package com.vhanma.freqsight;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import java.nio.ByteBuffer;
import java.util.Arrays;

final class FrameAnalyzer implements ImageAnalysis.Analyzer {
    interface Listener {
        void onCalibrationProgress(int current, int total);
        void onCalibrationComplete();
        void onOrientationChanged(boolean portrait);
        void onFrame(Bitmap bitmap, DataModels.VisualEvent metrics, FilterMode mode);
        void onVisualEvent(DataModels.VisualEvent event);
    }

    private static final int LONG_SIDE = 240;
    private static final int SHORT_SIDE = 180;
    private static final int N = LONG_SIDE * SHORT_SIDE;
    private final SensorHub sensors;
    private final Listener listener;
    private final float[] baselineMean = new float[N];
    private final float[] baselineM2 = new float[N];
    private final float[] previousGray = new float[N];
    private final float[] previousPreviousGray = new float[N];
    private final float[] temporalAverage = new float[N];
    private final float[] motionTrail = new float[N];
    private final float[] flickerMap = new float[N];
    private final float[] persistenceMap = new float[N];
    private final int[] argb = new int[N];
    private final int[] filtered = new int[N];
    private boolean hasPrevious;
    private boolean hasPreviousPrevious;
    private boolean baselineReady;
    private boolean calibrating;
    private boolean watching;
    private boolean glyphMode;
    private boolean portrait;
    private boolean orientationKnown;
    private int outW = LONG_SIDE;
    private int outH = SHORT_SIDE;
    private int calibrationFrames = 150;
    private int calibrationCount;
    private long frameNumber;
    private FilterMode filter = FilterMode.RAW;
    private long lastUiFrame;
    private long lastEvent;
    private DataModels.VisualEvent latestEvent;
    private float[] currentGrayScratch;

    FrameAnalyzer(SensorHub sensors, Listener listener) {
        this.sensors = sensors;
        this.listener = listener;
    }

    void beginCalibration(int frames) {
        calibrationFrames = Math.max(60, frames);
        calibrationCount = 0;
        Arrays.fill(baselineMean, 0f);
        Arrays.fill(baselineM2, 0f);
        Arrays.fill(persistenceMap, 0f);
        calibrating = true;
        baselineReady = false;
        watching = false;
        hasPrevious = false;
        hasPreviousPrevious = false;
    }

    boolean isBaselineReady() { return baselineReady; }
    void setWatching(boolean watching) { this.watching = watching; }
    void setGlyphMode(boolean glyphMode) { this.glyphMode = glyphMode; }
    void setFilter(FilterMode mode) { this.filter = mode == null ? FilterMode.RAW : mode; }
    FilterMode getFilter() { return filter; }
    DataModels.VisualEvent latestEvent() { return latestEvent; }

    @Override public void analyze(ImageProxy image) {
        try {
            frameNumber++;
            updateOrientation(image.getImageInfo().getRotationDegrees());
            extractArgbAndGray(image);
            float[] gray = currentGrayScratch;
            if (gray == null) return;
            if (calibrating) updateBaseline(gray);
            DataModels.VisualEvent metrics = computeMetrics(gray);
            latestEvent = metrics;
            applyFilter(gray, metrics);

            long now = System.currentTimeMillis();
            if (now - lastUiFrame > 90) {
                lastUiFrame = now;
                Bitmap preview = Bitmap.createBitmap(filtered, outW, outH, Bitmap.Config.ARGB_8888);
                if (listener != null) listener.onFrame(preview, metrics, filter);
            }

            if (watching && baselineReady && !metrics.phoneMovement && now - lastEvent > 850) {
                float eventStrength = Math.max(metrics.differenceScore,
                        Math.max(metrics.smokeScore, Math.max(metrics.shadowScore, metrics.reflectionScore)));
                if (eventStrength > 0.16f || metrics.glyphCandidate) {
                    lastEvent = now;
                    metrics.rawFrame = Bitmap.createBitmap(argb, outW, outH, Bitmap.Config.ARGB_8888);
                    metrics.processedFrame = Bitmap.createBitmap(filtered, outW, outH, Bitmap.Config.ARGB_8888);
                    if (listener != null) listener.onVisualEvent(metrics);
                }
            }

            if (hasPrevious) {
                System.arraycopy(previousGray, 0, previousPreviousGray, 0, N);
                hasPreviousPrevious = true;
            }
            System.arraycopy(gray, 0, previousGray, 0, N);
            hasPrevious = true;
        } catch (Throwable ignored) {
        } finally {
            image.close();
        }
    }

    private void updateOrientation(int rotation) {
        boolean nextPortrait = rotation == 90 || rotation == 270;
        if (!orientationKnown || nextPortrait != portrait) {
            orientationKnown = true;
            portrait = nextPortrait;
            outW = portrait ? SHORT_SIDE : LONG_SIDE;
            outH = portrait ? LONG_SIDE : SHORT_SIDE;
            baselineReady = false;
            calibrating = false;
            watching = false;
            hasPrevious = false;
            hasPreviousPrevious = false;
            calibrationCount = 0;
            Arrays.fill(baselineMean, 0f);
            Arrays.fill(baselineM2, 0f);
            Arrays.fill(temporalAverage, 0f);
            Arrays.fill(motionTrail, 0f);
            Arrays.fill(flickerMap, 0f);
            Arrays.fill(persistenceMap, 0f);
            if (listener != null) listener.onOrientationChanged(portrait);
        }
    }

    private void extractArgbAndGray(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        if (planes.length < 3) return;
        ByteBuffer yBuf = planes[0].getBuffer();
        ByteBuffer uBuf = planes[1].getBuffer();
        ByteBuffer vBuf = planes[2].getBuffer();
        int yRowStride = planes[0].getRowStride();
        int yPixelStride = planes[0].getPixelStride();
        int uRowStride = planes[1].getRowStride();
        int uPixelStride = planes[1].getPixelStride();
        int vRowStride = planes[2].getRowStride();
        int vPixelStride = planes[2].getPixelStride();
        int srcW = image.getWidth(), srcH = image.getHeight();
        int rotation = image.getImageInfo().getRotationDegrees();
        float[] gray = new float[N];
        for (int oy = 0; oy < outH; oy++) {
            for (int ox = 0; ox < outW; ox++) {
                float nx = (ox + 0.5f) / outW;
                float ny = (oy + 0.5f) / outH;
                int sx, sy;
                if (rotation == 90) { sx = (int) (ny * srcW); sy = (int) ((1f - nx) * srcH); }
                else if (rotation == 270) { sx = (int) ((1f - ny) * srcW); sy = (int) (nx * srcH); }
                else if (rotation == 180) { sx = (int) ((1f - nx) * srcW); sy = (int) ((1f - ny) * srcH); }
                else { sx = (int) (nx * srcW); sy = (int) (ny * srcH); }
                sx = clamp(sx, 0, srcW - 1); sy = clamp(sy, 0, srcH - 1);
                int yIndex = sy * yRowStride + sx * yPixelStride;
                int uvX = sx / 2, uvY = sy / 2;
                int uIndex = uvY * uRowStride + uvX * uPixelStride;
                int vIndex = uvY * vRowStride + uvX * vPixelStride;
                int Y = yBuf.get(yIndex) & 0xff;
                int U = (uBuf.get(uIndex) & 0xff) - 128;
                int V = (vBuf.get(vIndex) & 0xff) - 128;
                int c = Math.max(0, Y - 16);
                int r = clamp((298 * c + 409 * V + 128) >> 8, 0, 255);
                int g = clamp((298 * c - 100 * U - 208 * V + 128) >> 8, 0, 255);
                int b = clamp((298 * c + 516 * U + 128) >> 8, 0, 255);
                int idx = oy * outW + ox;
                outArgb(idx, Color.rgb(r, g, b));
                gray[idx] = Y / 255f;
                if (!hasPrevious) {
                    temporalAverage[idx] = gray[idx];
                    motionTrail[idx] = 0;
                    flickerMap[idx] = 0;
                } else {
                    temporalAverage[idx] = temporalAverage[idx] * 0.94f + gray[idx] * 0.06f;
                    float motion = Math.abs(gray[idx] - previousGray[idx]);
                    motionTrail[idx] = Math.max(motion, motionTrail[idx] * 0.92f);
                    float flicker = hasPreviousPrevious ? Math.abs(gray[idx] - 2f * previousGray[idx] + previousPreviousGray[idx]) : motion;
                    flickerMap[idx] = flickerMap[idx] * 0.82f + flicker * 0.18f;
                }
            }
        }
        currentGrayScratch = gray;
    }

    private void outArgb(int index, int color) {
        if (index >= 0 && index < N) argb[index] = color;
    }

    private void updateBaseline(float[] gray) {
        calibrationCount++;
        for (int i = 0; i < N; i++) {
            float d = gray[i] - baselineMean[i];
            baselineMean[i] += d / calibrationCount;
            baselineM2[i] += d * (gray[i] - baselineMean[i]);
        }
        if (listener != null && calibrationCount % 5 == 0) listener.onCalibrationProgress(calibrationCount, calibrationFrames);
        if (calibrationCount >= calibrationFrames) {
            calibrating = false;
            baselineReady = true;
            if (listener != null) listener.onCalibrationComplete();
        }
    }

    private DataModels.VisualEvent computeMetrics(float[] gray) {
        DataModels.VisualEvent e = new DataModels.VisualEvent();
        e.timestampMs = System.currentTimeMillis();
        e.frameNumber = frameNumber;
        DataModels.SensorSnapshot sensor = sensors.snapshot();
        e.phoneMovement = !sensor.stable;
        if (!baselineReady) return e;

        float shift = estimateGlobalShift(gray);
        e.globalShift = shift;
        if (shift > 2.2f) e.phoneMovement = true;

        double diffSum = 0, smoke = 0, shadow = 0, reflection = 0, condensation = 0;
        double motionX = 0, motionY = 0, motionWeight = 0;
        int diffCount = 0, darkCount = 0, brightCount = 0, textureCount = 0;
        int minX = outW, minY = outH, maxX = 0, maxY = 0;
        for (int y = 1; y < outH - 1; y++) {
            for (int x = 1; x < outW - 1; x++) {
                int i = y * outW + x;
                float variance = calibrationCount > 1 ? baselineM2[i] / (calibrationCount - 1) : 0.002f;
                float threshold = 0.024f + 2.7f * (float) Math.sqrt(Math.max(0.00002, variance));
                float delta = gray[i] - baselineMean[i];
                float ad = Math.abs(delta);
                float normalized = Math.max(0, ad - threshold);
                persistenceMap[i] = Math.max(normalized, persistenceMap[i] * 0.965f);
                diffSum += normalized;
                if (normalized > 0.038f) {
                    diffCount++;
                    minX = Math.min(minX, x); minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                    float temporal = hasPrevious ? Math.abs(gray[i] - previousGray[i]) : 0;
                    motionX += x * temporal; motionY += y * temporal; motionWeight += temporal;
                }
                if (baselineMean[i] > 0.16f && baselineMean[i] < 0.86f) {
                    float smooth = boxAverage(gray, x, y, 2);
                    smoke += Math.max(0, Math.abs(gray[i] - smooth) - 0.012f) + normalized * 0.42f;
                }
                if (delta < -threshold && gray[i] < 0.46f) { shadow += -delta; darkCount++; }
                int color = argb[i];
                int max = Math.max(Color.red(color), Math.max(Color.green(color), Color.blue(color)));
                int min = Math.min(Color.red(color), Math.min(Color.green(color), Color.blue(color)));
                if (delta > threshold && gray[i] > 0.68f && max - min < 55) { reflection += delta; brightCount++; }
                float lap = laplacian(gray, x, y);
                float baseTexture = laplacian(baselineMean, x, y);
                if (lap > baseTexture + 0.065f) { condensation += lap - baseTexture; textureCount++; }
            }
        }
        e.differenceScore = clamp01((float) (diffSum / N * 4.3));
        e.smokeScore = clamp01((float) (smoke / N * 5.5));
        e.shadowScore = clamp01((float) (shadow / Math.max(1, darkCount) * 2.5));
        e.reflectionScore = clamp01((float) (reflection / Math.max(1, brightCount) * 2.5));
        e.condensationScore = clamp01((float) (condensation / Math.max(1, textureCount) * 1.9));
        if (motionWeight > 0.5) {
            e.driftX = (float) (motionX / motionWeight - outW / 2f) / (outW / 2f);
            e.driftY = (float) (motionY / motionWeight - outH / 2f) / (outH / 2f);
        }
        int cx = diffCount > 0 ? (minX + maxX) / 2 : outW / 2;
        int cy = diffCount > 0 ? (minY + maxY) / 2 : outH / 2;
        e.gridColumn = clamp(cx * 5 / outW, 0, 4);
        e.gridRow = clamp(cy * 5 / outH, 0, 4);
        float compactness = diffCount == 0 ? 0 : diffCount / (float) Math.max(1, (maxX-minX+1)*(maxY-minY+1));
        e.glyphCandidate = diffCount > 22 && diffCount < N * 0.22 && compactness > 0.075f && edgeDensity(gray, minX, minY, maxX, maxY) > 0.115f;
        if (e.glyphCandidate) e.glyphBounds = new Rect(Math.max(0,minX), Math.max(0,minY), Math.min(outW,maxX+1), Math.min(outH,maxY+1));
        e.symbolPattern = describePattern(e, diffCount, darkCount, brightCount);
        return e;
    }

    private String describePattern(DataModels.VisualEvent e, int diffCount, int darkCount, int brightCount) {
        StringBuilder b = new StringBuilder();
        if (e.glyphCandidate) b.append("text-like edge cluster");
        if (darkCount > 50) append(b, "dark-region change");
        if (brightCount > 30) append(b, "reflection/glare change");
        if (e.smokeScore > 0.14f) {
            String dir = Math.abs(e.driftX) > Math.abs(e.driftY) ? (e.driftX > 0 ? "rightward" : "leftward") : (e.driftY > 0 ? "downward" : "upward");
            append(b, dir + " smoke-flow change");
        }
        if (e.condensationScore > 0.15f) append(b, "condensation-texture change");
        return b.length() == 0 ? "unclassified visual difference" : b.toString();
    }

    private float estimateGlobalShift(float[] gray) {
        if (!baselineReady) return 0;
        int bestDx = 0, bestDy = 0;
        double best = Double.MAX_VALUE;
        for (int dy = -4; dy <= 4; dy += 2) {
            for (int dx = -4; dx <= 4; dx += 2) {
                double error = 0; int count = 0;
                for (int y = 12; y < outH - 12; y += 8) {
                    for (int x = 12; x < outW - 12; x += 8) {
                        int bx = x + dx, by = y + dy;
                        if (bx < 0 || bx >= outW || by < 0 || by >= outH) continue;
                        error += Math.abs(gray[y*outW+x] - baselineMean[by*outW+bx]);
                        count++;
                    }
                }
                error /= Math.max(1, count);
                if (error < best) { best = error; bestDx = dx; bestDy = dy; }
            }
        }
        return (float) Math.sqrt(bestDx*bestDx + bestDy*bestDy);
    }

    private void applyFilter(float[] gray, DataModels.VisualEvent e) {
        float minV = 1f, maxV = 0f, mean = 0f;
        for (int i = 0; i < N; i++) { minV = Math.min(minV, gray[i]); maxV = Math.max(maxV, gray[i]); mean += gray[i]; }
        mean /= N;
        for (int y = 0; y < outH; y++) {
            for (int x = 0; x < outW; x++) {
                int i = y * outW + x;
                int src = argb[i];
                float v = gray[i];
                float base = baselineReady ? baselineMean[i] : v;
                float d = Math.abs(v - base);
                float local = boxAverage(gray, x, y, 2);
                int out;
                switch (filter) {
                    case RAW -> out = src;
                    case GRAYSCALE -> out = gray(v);
                    case NEGATIVE -> out = Color.rgb(255-Color.red(src),255-Color.green(src),255-Color.blue(src));
                    case HIGH_CONTRAST -> out = gray(clamp01((v - 0.5f) * 2.25f + 0.5f));
                    case LOCAL_CONTRAST -> out = gray(clamp01((v - local) * 3.1f + 0.5f));
                    case GAMMA_LOW -> out = gray((float)Math.pow(v, 0.42));
                    case GAMMA_HIGH -> out = gray((float)Math.pow(v, 2.35));
                    case DENOISE -> out = gray(boxAverage(gray,x,y,2));
                    case UNSHARP -> out = gray(clamp01(v + (v-local)*2.1f));
                    case SOBEL -> out = edgeColor(sobel(gray,x,y));
                    case HORIZONTAL_EDGES -> out = edgeColor(Math.abs(gradientY(gray,x,y)));
                    case VERTICAL_EDGES -> out = edgeColor(Math.abs(gradientX(gray,x,y)));
                    case LAPLACIAN -> out = edgeColor(laplacian(gray,x,y));
                    case EMBOSS -> out = gray(clamp01(0.5f + emboss(gray,x,y)*1.8f));
                    case ADAPTIVE_THRESHOLD -> out = v > local + 0.022f ? Color.WHITE : Color.BLACK;
                    case BINARY -> out = v > mean ? Color.WHITE : Color.BLACK;
                    case DIFFERENCE -> out = gray(clamp01(d * 5.5f));
                    case DIFFERENCE_HEAT -> out = heat(clamp01(d * 5.5f));
                    case STABLE_DIFFERENCE -> out = heat(clamp01(persistenceMap[i] * 5f - motionTrail[i] * 1.5f));
                    case FLICKER_MAP -> out = heat(clamp01(flickerMap[i] * 9f));
                    case PERSISTENCE_MAP -> out = heat(clamp01(persistenceMap[i] * 6f));
                    case SMOKE_ENHANCE -> out = smokeColor(v, d, local);
                    case VAPOR_DENSITY -> out = vaporColor(v,d,localVariance(gray,x,y,2));
                    case SHADOW_ISOLATE -> out = v < base - 0.035f ? Color.rgb(105,45,195) : Color.BLACK;
                    case SHADOW_GEOMETRY -> out = shadowGeometry(gray,x,y,base);
                    case REFLECTION_ISOLATE -> out = v > base + 0.045f && v > 0.68f ? Color.rgb(255,245,120) : Color.BLACK;
                    case GLARE_SUPPRESS -> out = glareSuppress(src,v);
                    case REFRACTION_MAP -> out = refractionColor(gray,x,y,d);
                    case MIRROR_SYMMETRY -> out = symmetryColor(gray,x,y);
                    case CONDENSATION -> out = heat(clamp01(laplacian(gray,x,y) * 2.2f));
                    case CHROMA_ANOMALY -> out = chromaColor(src);
                    case RED_CHANNEL -> out = Color.rgb(Color.red(src),0,0);
                    case GREEN_CHANNEL -> out = Color.rgb(0,Color.green(src),0);
                    case BLUE_CHANNEL -> out = Color.rgb(0,0,Color.blue(src));
                    case RGB_SPLIT -> out = rgbSplit(x,y);
                    case SATURATION -> {
                        int max = Math.max(Color.red(src),Math.max(Color.green(src),Color.blue(src)));
                        int min = Math.min(Color.red(src),Math.min(Color.green(src),Color.blue(src)));
                        out = heat((max-min)/255f);
                    }
                    case FALSE_COLOR -> out = falseColor(clamp01((v-minV)/Math.max(0.01f,maxV-minV)));
                    case LOW_LIGHT -> out = Color.rgb(clamp((int)(Color.red(src)*1.9+12),0,255),clamp((int)(Color.green(src)*1.9+12),0,255),clamp((int)(Color.blue(src)*1.9+12),0,255));
                    case TEMPORAL_AVERAGE -> out = gray(temporalAverage[i]);
                    case MOTION_TRAIL -> out = heat(clamp01(motionTrail[i]*5.5f));
                    case GLYPH_EDGE -> out = glyphPixel(gray,x,y,base);
                    case GRID_REGIONS -> out = ((x % Math.max(1,outW/5) < 2) || (y % Math.max(1,outH/5) < 2)) ? Color.rgb(90,230,255) : src;
                    default -> out = src;
                }
                filtered[i] = out;
            }
        }
        if (glyphMode && e.glyphBounds != null) drawRect(filtered, e.glyphBounds, Color.MAGENTA);
    }

    private int glyphPixel(float[] a, int x, int y, float base) {
        float edge = sobel(a,x,y);
        float diff = Math.abs(a[y*outW+x] - base);
        if (edge > 0.18f && diff > 0.032f) return Color.WHITE;
        if (edge > 0.10f) return Color.rgb(80,220,255);
        return Color.BLACK;
    }

    private int shadowGeometry(float[] a,int x,int y,float base){
        float v=a[y*outW+x]; if(v>=base-0.025f)return Color.BLACK;
        float edge=sobel(a,x,y); return Color.rgb(clamp((int)(40+edge*215),0,255),0,clamp((int)(100+edge*155),0,255));
    }
    private int glareSuppress(int src,float v){
        if(v<0.72f)return src; float scale=0.72f/Math.max(0.01f,v);
        return Color.rgb(clamp((int)(Color.red(src)*scale),0,255),clamp((int)(Color.green(src)*scale),0,255),clamp((int)(Color.blue(src)*scale),0,255));
    }
    private int refractionColor(float[] a,int x,int y,float d){
        float gx=Math.abs(gradientX(a,x,y)),gy=Math.abs(gradientY(a,x,y));
        return heat(clamp01((gx+gy)*0.9f+d*3.0f));
    }
    private int symmetryColor(float[] a,int x,int y){
        int mirrorX=outW-1-x; float mismatch=Math.abs(a[y*outW+x]-a[y*outW+mirrorX]);
        return heat(clamp01(mismatch*4f));
    }
    private int chromaColor(int src){
        int r=Color.red(src),g=Color.green(src),b=Color.blue(src);int max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b));
        float c=(max-min)/255f;return falseColor(c);
    }
    private int rgbSplit(int x,int y){
        int r=Color.red(argb[index(clamp(x-2,0,outW-1),y)]);int g=Color.green(argb[index(x,y)]);int b=Color.blue(argb[index(clamp(x+2,0,outW-1),y)]);return Color.rgb(r,g,b);
    }
    private int vaporColor(float v,float d,float variance){
        float density=clamp01(Math.abs(v-boxAverage(currentGrayScratchSafe(),0,0,0))*0f + d*3f + variance*18f + Math.abs(v-0.5f)*0.25f);
        return Color.rgb(clamp((int)(40+density*160),0,255),clamp((int)(90+density*150),0,255),clamp((int)(130+density*125),0,255));
    }
    private float[] currentGrayScratchSafe(){return currentGrayScratch==null?baselineMean:currentGrayScratch;}
    private float localVariance(float[] a,int x,int y,int radius){float m=boxAverage(a,x,y,radius),s=0;int c=0;for(int yy=Math.max(0,y-radius);yy<=Math.min(outH-1,y+radius);yy++)for(int xx=Math.max(0,x-radius);xx<=Math.min(outW-1,x+radius);xx++){float q=a[yy*outW+xx]-m;s+=q*q;c++;}return s/Math.max(1,c);}
    private float boxAverage(float[] a,int x,int y,int radius){if(radius<=0)return a[index(clamp(x,0,outW-1),clamp(y,0,outH-1))];float s=0;int c=0;for(int yy=Math.max(0,y-radius);yy<=Math.min(outH-1,y+radius);yy++)for(int xx=Math.max(0,x-radius);xx<=Math.min(outW-1,x+radius);xx++){s+=a[yy*outW+xx];c++;}return s/Math.max(1,c);}
    private float sobel(float[] a,int x,int y){float gx=gradientX(a,x,y),gy=gradientY(a,x,y);return clamp01((float)Math.sqrt(gx*gx+gy*gy));}
    private float gradientX(float[] a,int x,int y){if(x<1||y<1||x>=outW-1||y>=outH-1)return 0;return -a[(y-1)*outW+x-1]+a[(y-1)*outW+x+1]-2*a[y*outW+x-1]+2*a[y*outW+x+1]-a[(y+1)*outW+x-1]+a[(y+1)*outW+x+1];}
    private float gradientY(float[] a,int x,int y){if(x<1||y<1||x>=outW-1||y>=outH-1)return 0;return -a[(y-1)*outW+x-1]-2*a[(y-1)*outW+x]-a[(y-1)*outW+x+1]+a[(y+1)*outW+x-1]+2*a[(y+1)*outW+x]+a[(y+1)*outW+x+1];}
    private float emboss(float[] a,int x,int y){if(x<1||y<1||x>=outW-1||y>=outH-1)return 0;return a[(y+1)*outW+x+1]-a[(y-1)*outW+x-1];}
    private float laplacian(float[] a,int x,int y){if(x<1||y<1||x>=outW-1||y>=outH-1)return 0;int i=y*outW+x;return Math.abs(4*a[i]-a[i-1]-a[i+1]-a[i-outW]-a[i+outW]);}
    private float edgeDensity(float[] a,int minX,int minY,int maxX,int maxY){if(minX>=maxX||minY>=maxY)return 0;int count=0,edges=0;for(int y=Math.max(1,minY);y<Math.min(outH-1,maxY+1);y+=2)for(int x=Math.max(1,minX);x<Math.min(outW-1,maxX+1);x+=2){count++;if(sobel(a,x,y)>0.12f)edges++;}return edges/(float)Math.max(1,count);}
    private int edgeColor(float v){return gray(clamp01(v*1.65f));}
    private int gray(float v){int q=clamp((int)(clamp01(v)*255),0,255);return Color.rgb(q,q,q);}
    private int heat(float v){v=clamp01(v);int r=clamp((int)(255*Math.min(1,v*2)),0,255);int g=clamp((int)(255*Math.max(0,1-Math.abs(v*2-1))),0,255);int b=clamp((int)(255*Math.max(0,1-v*2)),0,255);return Color.rgb(r,g,b);}
    private int falseColor(float v){v=clamp01(v);float h=(1f-v)*250f;return Color.HSVToColor(new float[]{h,0.95f,1f});}
    private int smokeColor(float v,float d,float local){float haze=clamp01(Math.abs(v-local)*5.5f+d*3.5f);return Color.rgb(clamp((int)(70+haze*185),0,255),clamp((int)(115+haze*140),0,255),clamp((int)(155+haze*100),0,255));}
    private void drawRect(int[] pixels,Rect r,int color){for(int x=r.left;x<r.right;x++){set(pixels,x,r.top,color);set(pixels,x,r.bottom-1,color);}for(int y=r.top;y<r.bottom;y++){set(pixels,r.left,y,color);set(pixels,r.right-1,y,color);}}
    private void set(int[] p,int x,int y,int c){if(x>=0&&x<outW&&y>=0&&y<outH)p[y*outW+x]=c;}
    private int index(int x,int y){return y*outW+x;}
    private static void append(StringBuilder b,String s){if(b.length()>0)b.append(" + ");b.append(s);}
    private static int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}
    private static float clamp01(float v){return Math.max(0f,Math.min(1f,v));}
}