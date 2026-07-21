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
        void onFrame(Bitmap bitmap, DataModels.VisualEvent metrics, FilterMode mode);
        void onVisualEvent(DataModels.VisualEvent event);
    }

    private static final int W = 240;
    private static final int H = 180;
    private static final int N = W * H;
    private final SensorHub sensors;
    private final Listener listener;
    private final float[] baselineMean = new float[N];
    private final float[] baselineM2 = new float[N];
    private final float[] previousGray = new float[N];
    private final float[] temporalAverage = new float[N];
    private final float[] motionTrail = new float[N];
    private final int[] argb = new int[N];
    private final int[] filtered = new int[N];
    private boolean hasPrevious;
    private boolean baselineReady;
    private boolean calibrating;
    private boolean watching;
    private boolean glyphMode;
    private int calibrationFrames = 150;
    private int calibrationCount;
    private long frameNumber;
    private FilterMode filter = FilterMode.RAW;
    private long lastUiFrame;
    private long lastEvent;
    private DataModels.VisualEvent latestEvent;

    FrameAnalyzer(SensorHub sensors, Listener listener) {
        this.sensors = sensors;
        this.listener = listener;
    }

    void beginCalibration(int frames) {
        calibrationFrames = Math.max(60, frames);
        calibrationCount = 0;
        Arrays.fill(baselineMean, 0f);
        Arrays.fill(baselineM2, 0f);
        calibrating = true;
        baselineReady = false;
        watching = false;
        hasPrevious = false;
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
            extractArgbAndGray(image, argb, previousGray, hasPrevious);
            float[] gray = currentGrayScratch;
            if (gray == null) return;

            if (calibrating) updateBaseline(gray);
            DataModels.VisualEvent metrics = computeMetrics(gray);
            latestEvent = metrics;
            applyFilter(gray, metrics);

            long now = System.currentTimeMillis();
            if (now - lastUiFrame > 100) {
                lastUiFrame = now;
                Bitmap preview = Bitmap.createBitmap(filtered, W, H, Bitmap.Config.ARGB_8888);
                if (listener != null) listener.onFrame(preview, metrics, filter);
            }

            if (watching && baselineReady && !metrics.phoneMovement && now - lastEvent > 900) {
                float eventStrength = Math.max(metrics.differenceScore,
                        Math.max(metrics.smokeScore, Math.max(metrics.shadowScore, metrics.reflectionScore)));
                if (eventStrength > 0.18f || metrics.glyphCandidate) {
                    lastEvent = now;
                    metrics.rawFrame = Bitmap.createBitmap(argb, W, H, Bitmap.Config.ARGB_8888);
                    metrics.processedFrame = Bitmap.createBitmap(filtered, W, H, Bitmap.Config.ARGB_8888);
                    if (listener != null) listener.onVisualEvent(metrics);
                }
            }

            System.arraycopy(gray, 0, previousGray, 0, N);
            hasPrevious = true;
        } catch (Throwable ignored) {
        } finally {
            image.close();
        }
    }

    private float[] currentGrayScratch;

    private void extractArgbAndGray(ImageProxy image, int[] outArgb, float[] previous, boolean hasPrev) {
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
        float[] gray = new float[N];
        int rotation = image.getImageInfo().getRotationDegrees();
        for (int oy = 0; oy < H; oy++) {
            for (int ox = 0; ox < W; ox++) {
                float nx = ox / (float) W;
                float ny = oy / (float) H;
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
                int idx = oy * W + ox;
                outArgb[idx] = Color.rgb(r, g, b);
                gray[idx] = Y / 255f;
                if (!hasPrev) {
                    temporalAverage[idx] = gray[idx];
                    motionTrail[idx] = 0;
                } else {
                    temporalAverage[idx] = temporalAverage[idx] * 0.94f + gray[idx] * 0.06f;
                    float motion = Math.abs(gray[idx] - previous[idx]);
                    motionTrail[idx] = Math.max(motion, motionTrail[idx] * 0.92f);
                }
            }
        }
        currentGrayScratch = gray;
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
        int minX = W, minY = H, maxX = 0, maxY = 0;
        for (int y = 1; y < H - 1; y++) {
            for (int x = 1; x < W - 1; x++) {
                int i = y * W + x;
                float variance = calibrationCount > 1 ? baselineM2[i] / (calibrationCount - 1) : 0.002f;
                float threshold = 0.025f + 2.8f * (float) Math.sqrt(Math.max(0.00002, variance));
                float delta = gray[i] - baselineMean[i];
                float ad = Math.abs(delta);
                float normalized = Math.max(0, ad - threshold);
                diffSum += normalized;
                if (normalized > 0.04f) {
                    diffCount++;
                    minX = Math.min(minX, x); minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                    float temporal = hasPrevious ? Math.abs(gray[i] - previousGray[i]) : 0;
                    motionX += x * temporal; motionY += y * temporal; motionWeight += temporal;
                }
                if (baselineMean[i] > 0.20f && baselineMean[i] < 0.82f) {
                    float smooth = localAverage(gray, x, y);
                    smoke += Math.max(0, Math.abs(gray[i] - smooth) - 0.015f) + normalized * 0.35f;
                }
                if (delta < -threshold && gray[i] < 0.42f) { shadow += -delta; darkCount++; }
                int color = argb[i];
                int max = Math.max(Color.red(color), Math.max(Color.green(color), Color.blue(color)));
                int min = Math.min(Color.red(color), Math.min(Color.green(color), Color.blue(color)));
                if (delta > threshold && gray[i] > 0.72f && max - min < 45) { reflection += delta; brightCount++; }
                float lap = Math.abs(4 * gray[i] - gray[i-1] - gray[i+1] - gray[i-W] - gray[i+W]);
                float baseTexture = Math.abs(4 * baselineMean[i] - baselineMean[i-1] - baselineMean[i+1] - baselineMean[i-W] - baselineMean[i+W]);
                if (lap > baseTexture + 0.08f) { condensation += lap - baseTexture; textureCount++; }
            }
        }
        e.differenceScore = clamp01((float) (diffSum / N * 4.0));
        e.smokeScore = clamp01((float) (smoke / N * 5.0));
        e.shadowScore = clamp01((float) (shadow / Math.max(1, darkCount) * 2.5));
        e.reflectionScore = clamp01((float) (reflection / Math.max(1, brightCount) * 2.5));
        e.condensationScore = clamp01((float) (condensation / Math.max(1, textureCount) * 1.8));
        if (motionWeight > 0.5) {
            e.driftX = (float) (motionX / motionWeight - W / 2f) / (W / 2f);
            e.driftY = (float) (motionY / motionWeight - H / 2f) / (H / 2f);
        }
        int cx = diffCount > 0 ? (minX + maxX) / 2 : W / 2;
        int cy = diffCount > 0 ? (minY + maxY) / 2 : H / 2;
        e.gridColumn = clamp(cx * 5 / W, 0, 4);
        e.gridRow = clamp(cy * 5 / H, 0, 4);
        float compactness = diffCount == 0 ? 0 : diffCount / (float) Math.max(1, (maxX-minX+1)*(maxY-minY+1));
        e.glyphCandidate = diffCount > 24 && diffCount < N * 0.20 && compactness > 0.08f && edgeDensity(gray, minX, minY, maxX, maxY) > 0.12f;
        if (e.glyphCandidate) e.glyphBounds = new Rect(Math.max(0,minX), Math.max(0,minY), Math.min(W,maxX+1), Math.min(H,maxY+1));
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
                for (int y = 12; y < H - 12; y += 8) {
                    for (int x = 12; x < W - 12; x += 8) {
                        int bx = x + dx, by = y + dy;
                        if (bx < 0 || bx >= W || by < 0 || by >= H) continue;
                        error += Math.abs(gray[y*W+x] - baselineMean[by*W+bx]);
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
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                int src = argb[i];
                float v = gray[i];
                float base = baselineReady ? baselineMean[i] : v;
                float d = Math.abs(v - base);
                int out;
                switch (filter) {
                    case RAW -> out = src;
                    case GRAYSCALE -> out = gray(v);
                    case NEGATIVE -> out = Color.rgb(255-Color.red(src),255-Color.green(src),255-Color.blue(src));
                    case HIGH_CONTRAST -> out = gray(clamp01((v - 0.5f) * 2.2f + 0.5f));
                    case GAMMA_LOW -> out = gray((float)Math.pow(v, 0.45));
                    case GAMMA_HIGH -> out = gray((float)Math.pow(v, 2.2));
                    case SOBEL -> out = edgeColor(sobel(gray,x,y));
                    case LAPLACIAN -> out = edgeColor(laplacian(gray,x,y));
                    case ADAPTIVE_THRESHOLD -> out = v > localAverage(gray,x,y) + 0.025f ? Color.WHITE : Color.BLACK;
                    case BINARY -> out = v > 0.5f ? Color.WHITE : Color.BLACK;
                    case DIFFERENCE -> out = gray(clamp01(d * 5f));
                    case DIFFERENCE_HEAT -> out = heat(clamp01(d * 5f));
                    case SMOKE_ENHANCE -> out = smokeColor(v, d, localAverage(gray,x,y));
                    case SHADOW_ISOLATE -> out = v < base - 0.04f ? Color.rgb(90,40,180) : Color.BLACK;
                    case REFLECTION_ISOLATE -> out = v > base + 0.05f && v > 0.7f ? Color.rgb(255,245,120) : Color.BLACK;
                    case CONDENSATION -> out = heat(clamp01(Math.abs(laplacian(gray,x,y)) * 2f));
                    case RED_CHANNEL -> out = Color.rgb(Color.red(src),0,0);
                    case GREEN_CHANNEL -> out = Color.rgb(0,Color.green(src),0);
                    case BLUE_CHANNEL -> out = Color.rgb(0,0,Color.blue(src));
                    case SATURATION -> {
                        int max = Math.max(Color.red(src),Math.max(Color.green(src),Color.blue(src)));
                        int min = Math.min(Color.red(src),Math.min(Color.green(src),Color.blue(src)));
                        out = heat((max-min)/255f);
                    }
                    case LOW_LIGHT -> out = Color.rgb(clamp((int)(Color.red(src)*1.8+15),0,255),clamp((int)(Color.green(src)*1.8+15),0,255),clamp((int)(Color.blue(src)*1.8+15),0,255));
                    case TEMPORAL_AVERAGE -> out = gray(temporalAverage[i]);
                    case MOTION_TRAIL -> out = heat(clamp01(motionTrail[i]*5f));
                    case GLYPH_EDGE -> out = glyphPixel(gray,x,y,base);
                    case GRID_REGIONS -> out = ((x % (W/5) < 2) || (y % (H/5) < 2)) ? Color.rgb(90,230,255) : src;
                    default -> out = src;
                }
                filtered[i] = out;
            }
        }
        if (glyphMode && e.glyphBounds != null) drawRect(filtered, e.glyphBounds, Color.MAGENTA);
    }

    private int glyphPixel(float[] gray, int x, int y, float base) {
        float edge = sobel(gray,x,y);
        float diff = Math.abs(gray[y*W+x] - base);
        if (edge > 0.18f && diff > 0.035f) return Color.WHITE;
        if (edge > 0.10f) return Color.rgb(80,220,255);
        return Color.BLACK;
    }

    private float edgeDensity(float[] gray, int minX, int minY, int maxX, int maxY) {
        if (minX >= maxX || minY >= maxY) return 0;
        int count=0, edges=0;
        for (int y=Math.max(1,minY); y<Math.min(H-1,maxY+1); y+=2) {
            for (int x=Math.max(1,minX); x<Math.min(W-1,maxX+1); x+=2) {
                count++; if (sobel(gray,x,y)>0.12f) edges++;
            }
        }
        return edges/(float)Math.max(1,count);
    }

    private float localAverage(float[] a, int x, int y) {
        if (x < 1 || y < 1 || x >= W-1 || y >= H-1) return a[y*W+x];
        int i=y*W+x;
        return (a[i]+a[i-1]+a[i+1]+a[i-W]+a[i+W])/5f;
    }
    private float sobel(float[] a,int x,int y){
        if(x<1||y<1||x>=W-1||y>=H-1)return 0;
        float gx=-a[(y-1)*W+x-1]+a[(y-1)*W+x+1]-2*a[y*W+x-1]+2*a[y*W+x+1]-a[(y+1)*W+x-1]+a[(y+1)*W+x+1];
        float gy=-a[(y-1)*W+x-1]-2*a[(y-1)*W+x]-a[(y-1)*W+x+1]+a[(y+1)*W+x-1]+2*a[(y+1)*W+x]+a[(y+1)*W+x+1];
        return clamp01((float)Math.sqrt(gx*gx+gy*gy));
    }
    private float laplacian(float[] a,int x,int y){if(x<1||y<1||x>=W-1||y>=H-1)return 0;int i=y*W+x;return Math.abs(4*a[i]-a[i-1]-a[i+1]-a[i-W]-a[i+W]);}
    private int edgeColor(float v){return gray(clamp01(v*1.7f));}
    private int gray(float v){int q=clamp((int)(clamp01(v)*255),0,255);return Color.rgb(q,q,q);}
    private int heat(float v){v=clamp01(v);int r=clamp((int)(255*Math.min(1,v*2)),0,255);int g=clamp((int)(255*Math.max(0,1-Math.abs(v*2-1))),0,255);int b=clamp((int)(255*Math.max(0,1-v*2)),0,255);return Color.rgb(r,g,b);}
    private int smokeColor(float v,float d,float local){float haze=clamp01(Math.abs(v-local)*5+d*3);return Color.rgb(clamp((int)(80+haze*175),0,255),clamp((int)(120+haze*135),0,255),clamp((int)(150+haze*105),0,255));}
    private void drawRect(int[] pixels,Rect r,int color){for(int x=r.left;x<r.right;x++){set(pixels,x,r.top,color);set(pixels,x,r.bottom-1,color);}for(int y=r.top;y<r.bottom;y++){set(pixels,r.left,y,color);set(pixels,r.right-1,y,color);}}
    private void set(int[] p,int x,int y,int c){if(x>=0&&x<W&&y>=0&&y<H)p[y*W+x]=c;}
    private static void append(StringBuilder b,String s){if(b.length()>0)b.append(" + ");b.append(s);}
    private static int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}
    private static float clamp01(float v){return Math.max(0f,Math.min(1f,v));}
}
